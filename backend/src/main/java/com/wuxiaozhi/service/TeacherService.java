package com.wuxiaozhi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuxiaozhi.config.AiToolDefinition;
import com.wuxiaozhi.dto.*;
import com.wuxiaozhi.dto.experiment.ExperimentConfig;
import com.wuxiaozhi.dto.experiment.StepConfig;
import com.wuxiaozhi.entity.*;
import com.wuxiaozhi.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.BufferedReader;
import java.io.StringReader;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TeacherService {

    private static final String[][] STUDENT_REPORT_SECTIONS = {
            {"purpose", "1. 实验目的"},
            {"principle", "2. 实验原理"},
            {"apparatus", "3. 实验仪器"},
            {"procedure", "4. 实验步骤"},
            {"data", "5. 数据与处理"},
            {"results", "6. 实验结果"},
            {"discussion", "7. 分析与讨论"}
    };

    private final UserRepository userRepository;
    private final LabSessionRepository sessionRepository;
    private final MessageFeedbackRepository feedbackRepository;
    private final StudentExperimentAssignmentRepository assignmentRepository;
    private final StudentExperimentProgressRepository progressRepository;
    private final CorrectionLogRepository correctionLogRepository;
    private final SessionDataLogRepository sessionDataLogRepository;
    private final EnvCheckLogRepository envCheckLogRepository;
    private final LabSessionService labSessionService;
    private final ReportService reportService;
    private final FeedbackService feedbackService;
    private final ExperimentConfigService experimentConfigService;
    private final AiToolCatalogService aiToolCatalogService;
    private final DifyService difyService;
    private final ObjectMapper objectMapper;
    private final PasswordEncoder passwordEncoder;

    public TeacherService(UserRepository userRepository,
                          LabSessionRepository sessionRepository,
                          MessageFeedbackRepository feedbackRepository,
                          StudentExperimentAssignmentRepository assignmentRepository,
                          StudentExperimentProgressRepository progressRepository,
                          CorrectionLogRepository correctionLogRepository,
                          SessionDataLogRepository sessionDataLogRepository,
                          EnvCheckLogRepository envCheckLogRepository,
                          LabSessionService labSessionService,
                          ReportService reportService,
                          FeedbackService feedbackService,
                          ExperimentConfigService experimentConfigService,
                          AiToolCatalogService aiToolCatalogService,
                          DifyService difyService,
                          ObjectMapper objectMapper,
                          PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.feedbackRepository = feedbackRepository;
        this.assignmentRepository = assignmentRepository;
        this.progressRepository = progressRepository;
        this.correctionLogRepository = correctionLogRepository;
        this.sessionDataLogRepository = sessionDataLogRepository;
        this.envCheckLogRepository = envCheckLogRepository;
        this.labSessionService = labSessionService;
        this.reportService = reportService;
        this.feedbackService = feedbackService;
        this.experimentConfigService = experimentConfigService;
        this.aiToolCatalogService = aiToolCatalogService;
        this.difyService = difyService;
        this.objectMapper = objectMapper;
        this.passwordEncoder = passwordEncoder;
    }

    public User requireTeacher(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在"));
        if (!UserRole.TEACHER.equalsIgnoreCase(user.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "需要教师权限");
        }
        return user;
    }

    public TeacherOverviewDto overview(User teacher) {
        String managedClass = FeedbackService.managedClass(teacher);
        long studentCount = managedClass.isBlank()
                ? userRepository.countByRole(UserRole.STUDENT)
                : userRepository.countByRoleAndStudentClass(UserRole.STUDENT, managedClass);
        long finishedCount = managedClass.isBlank()
                ? sessionRepository.countByStatus("FINISHED")
                : sessionRepository.countByStatusAndStudentClass("FINISHED", managedClass);
        long feedbackCount = feedbackRepository.countForTeacherClass(managedClass);
        long notHelpfulCount = managedClass.isBlank()
                ? feedbackRepository.countByRating(FeedbackService.NOT_HELPFUL)
                : feedbackRepository.countByRatingAndStudentClass(FeedbackService.NOT_HELPFUL, managedClass);
        long unprocessedCount = managedClass.isBlank()
                ? feedbackRepository.countByProcessedFalse()
                : feedbackRepository.countByProcessedFalseAndStudentClass(managedClass);
        return new TeacherOverviewDto(
                studentCount,
                finishedCount,
                finishedCount,
                feedbackCount,
                notHelpfulCount,
                unprocessedCount,
                managedClass
        );
    }

    public List<TeacherReportItemDto> listReports(User teacher, String experimentCode) {
        String managedClass = FeedbackService.managedClass(teacher);
        String filterCode = experimentCode != null ? experimentCode.trim() : "";
        List<LabSession> sessions = managedClass.isBlank()
                ? sessionRepository.findByStatusOrderByStartTimeDesc("FINISHED")
                : sessionRepository.findByStatusAndStudentClassOrderByStartTimeDesc("FINISHED", managedClass);
        List<LabSession> finished = sessions.stream()
                .filter(s -> filterCode.isBlank() || filterCode.equals(s.getExperimentCode()))
                .toList();

        Map<Long, LabSession> latestByUser = new LinkedHashMap<>();
        for (LabSession session : finished) {
            if (session.getUserId() != null) {
                latestByUser.putIfAbsent(session.getUserId(), session);
            }
        }

        Map<Long, StudentExperimentProgress> progressByUser = Map.of();
        if (!filterCode.isBlank()) {
            Set<Long> userIds = new LinkedHashSet<>(latestByUser.keySet());
            List<StudentExperimentAssignment> assigned = assignmentRepository.findByExperimentCode(filterCode);
            assigned.stream().map(StudentExperimentAssignment::getUserId).forEach(userIds::add);
            if (!userIds.isEmpty()) {
                progressByUser = progressRepository.findByExperimentCodeAndUserIdIn(filterCode, userIds).stream()
                        .collect(Collectors.toMap(StudentExperimentProgress::getUserId, p -> p, (a, b) -> a));
            }
            for (StudentExperimentProgress progress : progressByUser.values()) {
                if (!progress.isReportCompleted()) {
                    continue;
                }
                LabSession preferred = null;
                if (progress.getReportSessionId() != null) {
                    preferred = sessionRepository.findById(progress.getReportSessionId()).orElse(null);
                }
                if (preferred == null) {
                    preferred = latestByUser.get(progress.getUserId());
                }
                if (preferred == null) {
                    preferred = sessionRepository
                            .findFirstByUserIdAndExperimentCodeOrderByStartTimeDesc(progress.getUserId(), filterCode)
                            .orElse(null);
                }
                if (preferred != null) {
                    latestByUser.put(progress.getUserId(), preferred);
                }
            }
        }

        Map<Long, StudentExperimentProgress> progressLookup = progressByUser;
        List<TeacherReportItemDto> items = latestByUser.values().stream()
                .map(session -> toReportItem(session, progressLookup.get(session.getUserId())))
                .toList();
        if (filterCode.isBlank()) {
            return items;
        }
        return items.stream().filter(TeacherReportItemDto::isReportCompleted).toList();
    }

    public Map<String, Object> getReport(User teacher, Long sessionId) {
        LabSession session = getSessionForTeacher(teacher, sessionId);
        Map<String, Object> data = labSessionService.buildReportData(session.getId());
        progressRepository.findByUserIdAndExperimentCode(session.getUserId(), session.getExperimentCode())
                .ifPresent(progress -> {
                    data.put("reportCompleted", progress.isReportCompleted());
                    data.put("studentReportSections", readReportSections(progress.getReportSectionsJson()));
                    data.put("aiReviewScore", progress.getAiReviewScore());
                    data.put("aiReviewComment", progress.getAiReviewComment());
                    data.put("aiReviewJson", parseJsonObject(progress.getAiReviewJson()));
                    data.put("teacherScore", progress.getTeacherScore());
                    data.put("teacherComment", progress.getTeacherComment());
                    data.put("gradingCompleted", Boolean.TRUE.equals(progress.getGradingCompleted()));
                    data.put("gradingAt", progress.getGradingAt());
                });
        return data;
    }

    public byte[] getReportDocx(User teacher, Long sessionId) throws Exception {
        Map<String, Object> data = getReport(teacher, sessionId);
        Map<String, String> studentSections = readReportSectionsFromReport(data.get("studentReportSections"));
        if (hasStudentReportText(studentSections)) {
            return reportService.generateStudentReportDocx(data, studentSectionPayload(studentSections));
        }
        return reportService.generateDocx(data);
    }

    /**
     * 课堂态势：每个学生一行。已结束实验显示为已完成，不再把多次「新建对话」拆成重复卡片。
     */
    public TeacherClassroomDto classroom(User teacher, String experimentCode) {
        String managedClass = FeedbackService.managedClass(teacher);
        String filterCode = experimentCode != null ? experimentCode.trim() : "";
        if (filterCode.isBlank()) {
            return classroomFromActiveSessions(teacher, managedClass, "");
        }

        ExperimentConfig focusExp = null;
        try {
            focusExp = experimentConfigService.getByCode(filterCode);
        } catch (RuntimeException ignored) {
            focusExp = null;
        }

        Set<Long> assignedIds = assignmentRepository.findByExperimentCode(filterCode).stream()
                .map(StudentExperimentAssignment::getUserId)
                .collect(Collectors.toSet());
        List<User> classStudents = managedClass.isBlank()
                ? userRepository.findByRoleOrderByDisplayNameAsc(UserRole.STUDENT)
                : userRepository.findByRoleAndStudentClassOrderByDisplayNameAsc(UserRole.STUDENT, managedClass);
        Set<Long> classIds = classStudents.stream().map(User::getId).collect(Collectors.toCollection(LinkedHashSet::new));
        Set<Long> visibleIds = new LinkedHashSet<>(assignedIds);
        visibleIds.retainAll(classIds);
        if (!classIds.isEmpty()) {
            sessionRepository.findByExperimentCodeAndUserIdInOrderByStartTimeDesc(filterCode, classIds).stream()
                    .map(LabSession::getUserId)
                    .filter(Objects::nonNull)
                    .forEach(visibleIds::add);
        }
        List<User> students = classStudents.stream()
                .filter(u -> visibleIds.contains(u.getId()))
                .toList();

        List<Long> userIds = students.stream().map(User::getId).toList();
        List<LabSession> sessions = userIds.isEmpty()
                ? List.of()
                : sessionRepository.findByExperimentCodeAndUserIdInOrderByStartTimeDesc(filterCode, userIds);
        Map<Long, List<LabSession>> sessionsByUser = sessions.stream()
                .collect(Collectors.groupingBy(LabSession::getUserId));
        Map<Long, Boolean> readyByUser = userIds.isEmpty()
                ? Map.of()
                : progressRepository.findByExperimentCodeAndUserIdIn(filterCode, userIds).stream()
                .collect(Collectors.toMap(StudentExperimentProgress::getUserId,
                        StudentExperimentProgress::isPreLabCompleted, (a, b) -> a || b));
        Map<Long, StudentExperimentProgress> progressByUser = userIds.isEmpty()
                ? Map.of()
                : progressRepository.findByExperimentCodeAndUserIdIn(filterCode, userIds).stream()
                .collect(Collectors.toMap(StudentExperimentProgress::getUserId, p -> p, (a, b) -> a));

        List<TeacherClassroomStudentDto> rows = new ArrayList<>();
        for (User student : students) {
            LabSession canonical = pickCanonicalSession(sessionsByUser.getOrDefault(student.getId(), List.of()));
            boolean ready = readyByUser.getOrDefault(student.getId(), false);
            TeacherClassroomStudentDto row;
            if (canonical != null) {
                row = toClassroomRow(canonical, ready);
                row.setStudentName(displayName(student, canonical.getStudentName()));
                row.setStudentClass(safeClass(student.getStudentClass()));
            } else {
                row = idleStudentRow(student, filterCode, focusExp, ready);
            }
            StudentExperimentProgress progress = progressByUser.get(student.getId());
            if (progress != null) {
                row.setReportCompleted(progress.isReportCompleted());
            }
            if ("FINISHED".equals(row.getStatus()) && !row.isDataIssue()) {
                row.setPriority("normal");
                row.setPriorityReason(row.isReportCompleted() ? "实验与报告已完成" : "实验已结束");
            }
            rows.add(row);
        }

        disambiguateStudentNames(rows, students);
        enrichEnvLogStats(rows);

        rows.sort(Comparator
                .comparingInt((TeacherClassroomStudentDto r) -> priorityRank(r.getPriority()))
                .thenComparing(TeacherClassroomStudentDto::getStudentName, Comparator.nullsLast(String::compareTo)));

        TeacherClassroomDto dto = new TeacherClassroomDto();
        dto.setExperimentCode(filterCode);
        dto.setExperimentName(focusExp != null ? focusExp.getName() : filterCode);
        dto.setManagedClass(managedClass);
        dto.setActiveCount((int) rows.stream().filter(r -> "ACTIVE".equals(r.getStatus())).count());
        dto.setHighPriorityCount((int) rows.stream().filter(r -> "high".equals(r.getPriority())).count());
        dto.setNotReadyCount((int) rows.stream().filter(r -> !r.isPreLabCompleted()).count());
        dto.setDataIssueCount((int) rows.stream().filter(TeacherClassroomStudentDto::isDataIssue).count());
        dto.setStudents(rows);
        return dto;
    }

    private TeacherClassroomDto classroomFromActiveSessions(User teacher, String managedClass, String filterCode) {
        List<LabSession> activeSessions = managedClass.isBlank()
                ? sessionRepository.findByStatusOrderByStartTimeDesc("ACTIVE")
                : sessionRepository.findByStatusAndStudentClassOrderByStartTimeDesc("ACTIVE", managedClass);
        if (!filterCode.isBlank()) {
            activeSessions = activeSessions.stream()
                    .filter(s -> filterCode.equals(s.getExperimentCode()))
                    .toList();
        }
        Map<Long, LabSession> unique = new LinkedHashMap<>();
        for (LabSession session : activeSessions) {
            if (session.getUserId() != null) {
                unique.putIfAbsent(session.getUserId(), session);
            }
        }
        List<TeacherClassroomStudentDto> rows = unique.values().stream()
                .map(session -> toClassroomRow(session, false))
                .toList();
        enrichEnvLogStats(rows);
        TeacherClassroomDto dto = new TeacherClassroomDto();
        dto.setManagedClass(managedClass);
        dto.setActiveCount(rows.size());
        dto.setStudents(new ArrayList<>(rows));
        return dto;
    }

    private LabSession pickCanonicalSession(List<LabSession> sessions) {
        if (sessions == null || sessions.isEmpty()) {
            return null;
        }
        Optional<LabSession> finished = sessions.stream()
                .filter(s -> "FINISHED".equals(s.getStatus()))
                .max(Comparator.comparing(LabSession::getStartTime, Comparator.nullsLast(Comparator.naturalOrder())));
        if (finished.isPresent()) {
            return finished.get();
        }
        return sessions.stream()
                .filter(s -> "ACTIVE".equals(s.getStatus()))
                .max(Comparator.comparing(LabSession::getStartTime, Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(sessions.stream()
                        .max(Comparator.comparing(LabSession::getStartTime, Comparator.nullsLast(Comparator.naturalOrder())))
                        .orElse(null));
    }

    private TeacherClassroomStudentDto idleStudentRow(User student, String experimentCode, ExperimentConfig exp,
                                                      boolean preLabCompleted) {
        TeacherClassroomStudentDto idle = new TeacherClassroomStudentDto();
        idle.setUserId(student.getId());
        idle.setStudentName(displayName(student, ""));
        idle.setStudentClass(safeClass(student.getStudentClass()));
        idle.setExperimentCode(experimentCode);
        idle.setExperimentName(exp != null ? exp.getName() : experimentCode);
        idle.setStatus("NOT_STARTED");
        idle.setPreLabCompleted(preLabCompleted);
        idle.setPriority(preLabCompleted ? "normal" : "medium");
        idle.setPriorityReason(preLabCompleted ? "尚未开始实验" : "未完成进门就绪");
        return idle;
    }

    private String displayName(User student, String fallback) {
        if (student.getDisplayName() != null && !student.getDisplayName().isBlank()) {
            return student.getDisplayName();
        }
        if (student.getUsername() != null && !student.getUsername().isBlank()) {
            return student.getUsername();
        }
        return fallback != null ? fallback : "";
    }

    private TeacherClassroomStudentDto toClassroomRow(LabSession session, boolean preLabCompleted) {
        TeacherClassroomStudentDto row = new TeacherClassroomStudentDto();
        row.setUserId(session.getUserId());
        row.setStudentName(session.getStudentName());
        row.setStudentClass(session.getStudentClass());
        row.setExperimentCode(session.getExperimentCode());
        row.setExperimentName(session.getExperimentName());
        row.setSessionId(session.getId());
        row.setStatus(session.getStatus());
        row.setActiveStep(session.getActiveStep());
        row.setHelpCount(session.getHelpCount());
        row.setErrorPointCount(session.getErrorPointCount());
        row.setPreLabCompleted(preLabCompleted);
        row.setStartTime(session.getStartTime());

        long minutes = 0;
        if (session.getStartTime() != null) {
            LocalDateTime end = "FINISHED".equals(session.getStatus()) && session.getEndTime() != null
                    ? session.getEndTime()
                    : LocalDateTime.now();
            minutes = Math.max(0, Duration.between(session.getStartTime(), end).toMinutes());
        }
        row.setMinutesOnSession(minutes);

        String stepTitle = "";
        try {
            ExperimentConfig exp = experimentConfigService.getByCode(session.getExperimentCode());
            StepConfig step = exp.getSteps() != null ? exp.getSteps().get(String.valueOf(session.getActiveStep())) : null;
            if (step != null && step.getTitle() != null) {
                stepTitle = step.getTitle();
            }
        } catch (RuntimeException ignored) {
            // keep empty
        }
        row.setStepTitle(stepTitle);

        List<CorrectionLog> corrections = correctionLogRepository.findBySessionIdOrderByCreatedAtAsc(session.getId());
        if (corrections != null && !corrections.isEmpty()) {
            LinkedHashSet<String> types = new LinkedHashSet<>();
            for (CorrectionLog log : corrections) {
                if (log.getErrorType() != null && !log.getErrorType().isBlank()) {
                    types.add(log.getErrorType().trim());
                }
            }
            List<String> list = new ArrayList<>(types);
            if (list.size() > 3) {
                list = list.subList(list.size() - 3, list.size());
            }
            row.setRecentCorrectionTypes(list);
        }

        List<SessionDataLog> dataLogs = sessionDataLogRepository.findOfficialBySessionIdOrderByCreatedAtAsc(session.getId());
        if (dataLogs != null && !dataLogs.isEmpty()) {
            SessionDataLog last = dataLogs.get(dataLogs.size() - 1);
            String validation = last.getValidationJson();
            boolean issue = false;
            if (validation != null && !validation.isBlank()) {
                String lower = validation.toLowerCase(Locale.ROOT);
                issue = lower.contains("error") || lower.contains("fail") || validation.contains("不通过")
                        || validation.contains("\"level\":\"error\"");
            }
            row.setDataIssue(issue);
            row.setLastDataValidation(issue ? "数据校验异常" : "最近提交正常");
        }

        String priority = "normal";
        String reason = "正常推进";
        if (row.isDataIssue() || session.getErrorPointCount() >= 3 || session.getHelpCount() >= 6) {
            priority = "high";
            reason = row.isDataIssue() ? "数据异常，建议介入" : "纠错/求助偏多";
        } else if (!preLabCompleted || (minutes >= 25 && session.getActiveStep() <= 1) || session.getHelpCount() >= 3) {
            priority = "medium";
            if (!preLabCompleted) {
                reason = "未完成进门就绪";
            } else if (minutes >= 25 && session.getActiveStep() <= 1) {
                reason = "长时间停留在前几步";
            } else {
                reason = "求助偏多，可关注";
            }
        }
        row.setPriority(priority);
        row.setPriorityReason(reason);
        row.setCameraActive(labSessionService.isCameraActiveEffective(session));
        row.setEnvCheckEnabled(session.isEnvCheckEnabled());
        return row;
    }

    private int priorityRank(String priority) {
        if ("high".equals(priority)) return 0;
        if ("medium".equals(priority)) return 1;
        return 2;
    }

    /**
     * 教师报告预评：基于过程报告上下文调用 report-review。
     * AI 只给建议分与批注，不直接终裁成绩。
     */
    public AiToolInvokeResponse reviewReport(User teacher, Long sessionId) {
        Map<String, Object> report = getReport(teacher, sessionId);
        String experimentCode = stringOrEmpty(report.get("experimentCode"));
        String experimentName = stringOrEmpty(report.get("experimentName"));
        String userId = "teacher-review-" + teacher.getId();

        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("experiment_name", experimentName);
        inputs.put("student_name", stringOrEmpty(report.get("studentName")));
        inputs.put("student_class", stringOrEmpty(report.get("studentClass")));
        if (!experimentCode.isBlank()) {
            try {
                ExperimentConfig exp = experimentConfigService.getByCode(experimentCode);
                String spec = experimentConfigService.buildExperimentSpec(exp);
                if (spec != null && !spec.isBlank()) {
                    inputs.put("experiment_spec", spec);
                }
            } catch (RuntimeException ignored) {
                // unknown experiment code
            }
        }
        try {
            byte[] docx = getReportDocx(teacher, sessionId);
            String safeName = (experimentName.isBlank() ? "实验报告" : experimentName).replaceAll("[\\\\/:*?\"<>|]", "_");
            String fileId = difyService.uploadDocument(docx, safeName + ".docx", userId, "report-review");
            inputs.put("student_report_file", difyService.documentFileRef(fileId));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "学生报告文件上传失败，无法预评");
        }
        inputs.put("query",
                "请对照本实验的目的与步骤，阅读学生提交的实验报告文件，做预评（非终裁）。"
                        + "按完整性、数据可信度、误差分析、结论质量、思考题与讨论五维评分，"
                        + "输出建议分数（10 分制，一位小数）和可编辑评语。强调需教师确认后才作为正式成绩。");

        AiToolDefinition tool = aiToolCatalogService.requireTool("report-review");
        String workflowKey = aiToolCatalogService.resolveWorkflowKey(tool);
        AiToolInvokeResponse resp = difyService.invokeTool(workflowKey, inputs, userId, null);
        persistAiReview(sessionId, resp);
        return resp;
    }

    @Transactional
    public Map<String, Object> completeGrade(User teacher, Long sessionId, CompleteGradeRequest request) {
        LabSession session = getSessionForTeacher(teacher, sessionId);
        StudentExperimentProgress progress = progressRepository
                .findByUserIdAndExperimentCode(session.getUserId(), session.getExperimentCode())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到该学生的实验进度"));
        progress.setTeacherScore(request.getScore());
        progress.setTeacherComment(request.getComment() == null ? "" : request.getComment().trim());
        progress.setGradingCompleted(true);
        progress.setGradingAt(LocalDateTime.now());
        progress.setUpdatedAt(LocalDateTime.now());
        progressRepository.save(progress);
        return getReport(teacher, sessionId);
    }

    private String stringOrEmpty(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String writeJson(Object value) {
        if (value == null) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "[]";
        }
    }

    private Object parseJsonObject(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }

    private void persistAiReview(Long sessionId, AiToolInvokeResponse resp) {
        if (resp == null || !resp.isFromDify()) {
            return;
        }
        sessionRepository.findById(sessionId).ifPresent(session ->
                progressRepository.findByUserIdAndExperimentCode(session.getUserId(), session.getExperimentCode())
                        .ifPresent(progress -> {
                            if (resp.getScore() != null) {
                                progress.setAiReviewScore(resp.getScore());
                            }
                            if (resp.getComment() != null && !resp.getComment().isBlank()) {
                                progress.setAiReviewComment(resp.getComment());
                            }
                            if (resp.getData() != null && !resp.getData().isEmpty()) {
                                progress.setAiReviewJson(writeJson(resp.getData()));
                            }
                            progress.setUpdatedAt(LocalDateTime.now());
                            progressRepository.save(progress);
                        }));
    }

    private String buildStudentReportText(String experimentName, Map<String, String> sections) {
        StringBuilder sb = new StringBuilder();
        if (experimentName != null && !experimentName.isBlank()) {
            sb.append(experimentName.trim()).append(" — 实验报告\n\n");
        }
        for (String[] def : STUDENT_REPORT_SECTIONS) {
            String text = htmlToPlain(sections.getOrDefault(def[0], "")).trim();
            if (!text.isBlank()) {
                sb.append("## ").append(def[1].replaceFirst("^\\d+\\.\\s*", "")).append("\n\n")
                        .append(text).append("\n\n");
            }
        }
        return sb.toString().trim();
    }

    private String htmlToPlain(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        String text = html
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</(p|div|h[1-6]|li|tr)>", "\n")
                .replaceAll("(?i)<li[^>]*>", "- ")
                .replaceAll("<[^>]+>", " ")
                .replace("&nbsp;", " ")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&");
        return text.replaceAll("[ \\t]+", " ").replaceAll("\\n{3,}", "\n\n").trim();
    }

    public List<TeacherFeedbackItemDto> listFeedback(User teacher, String rating, Boolean processed, String experimentCode) {
        return feedbackService.listForTeacher(teacher, rating, processed, experimentCode);
    }

    public TeacherFeedbackItemDto markFeedbackProcessed(User teacher, Long feedbackId, String rating) {
        return feedbackService.markProcessed(feedbackId, teacher, rating);
    }

    public List<TeacherStudentItemDto> listStudents(User teacher, String experimentCode) {
        String managedClass = FeedbackService.managedClass(teacher);
        List<User> students = managedClass.isBlank()
                ? userRepository.findByRoleOrderByDisplayNameAsc(UserRole.STUDENT)
                : userRepository.findByRoleAndStudentClassOrderByDisplayNameAsc(UserRole.STUDENT, managedClass);
        Map<Long, List<String>> codesByUser = assignmentRepository.findByUserIdIn(
                        students.stream().map(User::getId).toList()).stream()
                .collect(Collectors.groupingBy(
                        StudentExperimentAssignment::getUserId,
                        Collectors.mapping(StudentExperimentAssignment::getExperimentCode, Collectors.toList())
                ));
        return students.stream()
                .filter(u -> {
                    if (experimentCode == null || experimentCode.isBlank()) {
                        return true;
                    }
                    return codesByUser.getOrDefault(u.getId(), List.of()).contains(experimentCode.trim());
                })
                .map(u -> toStudentItem(u, codesByUser.getOrDefault(u.getId(), List.of())))
                .toList();
    }

    @Transactional
    public TeacherStudentItemDto createStudent(User teacher, CreateStudentRequest req) {
        ImportStudentRow row = new ImportStudentRow();
        row.setUsername(req.getUsername());
        row.setDisplayName(req.getDisplayName());
        row.setPassword(req.getPassword());
        row.setStudentClass(req.getStudentClass());

        ImportStudentsRequest importReq = new ImportStudentsRequest();
        importReq.setStudents(List.of(row));
        importReq.setExperimentCodes(req.getExperimentCodes());
        importReq.setAssignMode(req.isAppendExperiments() ? "append" : "replace");

        ImportStudentsResult result = importStudents(teacher, importReq);
        if (result.getCreated() + result.getUpdated() == 0) {
            String detail = result.getErrors().isEmpty() ? "创建学生失败" : result.getErrors().get(0);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, detail);
        }
        User student = userRepository.findByUsername(req.getUsername().trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "创建学生失败"));
        List<String> codes = assignmentRepository.findByUserIdOrderByExperimentCodeAsc(student.getId()).stream()
                .map(StudentExperimentAssignment::getExperimentCode)
                .toList();
        return toStudentItem(student, codes);
    }

    @Transactional
    public ImportStudentsResult importStudents(User teacher, ImportStudentsRequest req) {
        List<ImportStudentRow> rows = new ArrayList<>();
        if (req.getCsv() != null && !req.getCsv().isBlank()) {
            rows.addAll(parseCsv(req.getCsv()));
        }
        if (req.getStudents() != null) {
            rows.addAll(req.getStudents());
        }
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请提供学生名单");
        }

        String defaultPassword = req.getDefaultPassword() != null && !req.getDefaultPassword().isBlank()
                ? req.getDefaultPassword()
                : "123456";
        String managedClass = FeedbackService.managedClass(teacher);
        ImportStudentsResult result = new ImportStudentsResult();
        List<Long> affectedUserIds = new ArrayList<>();

        for (ImportStudentRow row : rows) {
            String username = row.getUsername() != null ? row.getUsername().trim() : "";
            String displayName = row.getDisplayName() != null ? row.getDisplayName().trim() : "";
            if (username.isBlank() || displayName.isBlank()) {
                result.setSkipped(result.getSkipped() + 1);
                result.getErrors().add("跳过无效行：账号或姓名为空");
                continue;
            }

            String studentClass = row.getStudentClass() != null && !row.getStudentClass().isBlank()
                    ? row.getStudentClass().trim()
                    : managedClass;
            String password = row.getPassword() != null && !row.getPassword().isBlank()
                    ? row.getPassword().trim()
                    : defaultPassword;
            if (password.length() < 6) {
                result.setSkipped(result.getSkipped() + 1);
                result.getErrors().add(username + "：密码至少 6 位");
                continue;
            }

            Optional<User> existing = userRepository.findByUsername(username);
            if (existing.isPresent()) {
                User user = existing.get();
                if (!UserRole.STUDENT.equalsIgnoreCase(user.getRole())) {
                    result.setSkipped(result.getSkipped() + 1);
                    result.getErrors().add(username + "：账号已存在且非学生");
                    continue;
                }
                if (!managedClass.isBlank() && !managedClass.equals(safeClass(user.getStudentClass()))) {
                    result.setSkipped(result.getSkipped() + 1);
                    result.getErrors().add(username + "：不属于当前管理班级");
                    continue;
                }
                user.setDisplayName(displayName);
                if (!studentClass.isBlank()) {
                    user.setStudentClass(studentClass);
                }
                userRepository.save(user);
                result.setUpdated(result.getUpdated() + 1);
                affectedUserIds.add(user.getId());
                continue;
            }

            User user = new User();
            user.setUsername(username);
            user.setPasswordHash(passwordEncoder.encode(password));
            user.setDisplayName(displayName);
            user.setStudentClass(studentClass);
            user.setRole(UserRole.STUDENT);
            userRepository.save(user);
            result.setCreated(result.getCreated() + 1);
            affectedUserIds.add(user.getId());
        }

        List<String> experimentCodes = normalizeExperimentCodes(req.getExperimentCodes());
        if (!experimentCodes.isEmpty() && !affectedUserIds.isEmpty()) {
            boolean append = !"replace".equalsIgnoreCase(req.getAssignMode());
            BulkAssignExperimentsRequest assignReq = new BulkAssignExperimentsRequest();
            assignReq.setUserIds(affectedUserIds.stream().distinct().toList());
            assignReq.setExperimentCodes(experimentCodes);
            assignReq.setMode(append ? "append" : "replace");
            result.setAssigned(bulkAssignExperiments(teacher, assignReq));
            result.setUserIds(affectedUserIds.stream().distinct().toList());
        }
        return result;
    }

    @Transactional
    public TeacherStudentItemDto assignExperiments(User teacher, Long userId, AssignExperimentsRequest req) {
        User student = requireStudentForTeacher(teacher, userId);
        List<String> codes = normalizeExperimentCodes(req.getExperimentCodes());
        replaceExperiments(student.getId(), codes, teacher.getId());
        return toStudentItem(student, codes);
    }

    @Transactional
    public int bulkAssignExperiments(User teacher, BulkAssignExperimentsRequest req) {
        List<String> codes = normalizeExperimentCodes(req.getExperimentCodes());
        boolean append = "append".equalsIgnoreCase(req.getMode());
        int count = 0;
        for (Long userId : req.getUserIds()) {
            User student = requireStudentForTeacher(teacher, userId);
            if (append) {
                appendExperiments(student.getId(), codes, teacher.getId());
            } else {
                replaceExperiments(student.getId(), codes, teacher.getId());
            }
            count++;
        }
        return count;
    }

    @Transactional
    public void unassignExperiment(User teacher, Long userId, String experimentCode) {
        requireStudentForTeacher(teacher, userId);
        if (experimentCode == null || experimentCode.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "实验 code 不能为空");
        }
        assignmentRepository.deleteByUserIdAndExperimentCode(userId, experimentCode.trim());
    }

    private void replaceExperiments(Long userId, List<String> codes, Long assignedByUserId) {
        assignmentRepository.deleteByUserId(userId);
        for (String code : codes) {
            StudentExperimentAssignment assignment = new StudentExperimentAssignment();
            assignment.setUserId(userId);
            assignment.setExperimentCode(code);
            assignment.setAssignedByUserId(assignedByUserId);
            assignmentRepository.save(assignment);
        }
    }

    private void appendExperiments(Long userId, List<String> codes, Long assignedByUserId) {
        for (String code : codes) {
            if (assignmentRepository.existsByUserIdAndExperimentCode(userId, code)) {
                continue;
            }
            StudentExperimentAssignment assignment = new StudentExperimentAssignment();
            assignment.setUserId(userId);
            assignment.setExperimentCode(code);
            assignment.setAssignedByUserId(assignedByUserId);
            assignmentRepository.save(assignment);
        }
    }

    public List<EnvCheckLog> getEnvLogsForTeacher(User teacher, Long sessionId) {
        getSessionForTeacher(teacher, sessionId);
        return labSessionService.getEnvCheckLogs(sessionId);
    }

    @Transactional
    public TeacherClassroomStudentDto setEnvCheckEnabled(User teacher, Long sessionId, boolean enabled) {
        LabSession session = getSessionForTeacher(teacher, sessionId);
        session.setEnvCheckEnabled(enabled);
        sessionRepository.save(session);
        return toClassroomRow(session, false);
    }

    public EnvCheckResponse triggerEnvCheckForTeacher(User teacher, Long sessionId, EnvCheckRequest req) {
        LabSession session = getSessionForTeacher(teacher, sessionId);
        EnvCheckRequest body = req != null ? req : new EnvCheckRequest();
        return labSessionService.envCheck(session.getId(), body);
    }

    private void enrichEnvLogStats(List<TeacherClassroomStudentDto> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        List<Long> sessionIds = rows.stream()
                .map(TeacherClassroomStudentDto::getSessionId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (sessionIds.isEmpty()) {
            return;
        }
        Map<Long, List<EnvCheckLog>> logsBySession = envCheckLogRepository
                .findBySessionIdInOrderByCreatedAtDesc(sessionIds).stream()
                .collect(Collectors.groupingBy(EnvCheckLog::getSessionId));
        for (TeacherClassroomStudentDto row : rows) {
            if (row.getSessionId() == null) {
                continue;
            }
            List<EnvCheckLog> logs = logsBySession.getOrDefault(row.getSessionId(), List.of());
            row.setEnvLogCount(logs.size());
            row.setLatestEnvLevel(logs.isEmpty() ? null : logs.get(0).getLevel());
        }
    }

    public com.wuxiaozhi.entity.LabSession getSessionForTeacher(User teacher, Long sessionId) {
        com.wuxiaozhi.entity.LabSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "会话不存在"));
        String managedClass = FeedbackService.managedClass(teacher);
        if (!managedClass.isBlank()) {
            String sessionClass = session.getStudentClass() != null ? session.getStudentClass().trim() : "";
            if (!managedClass.equals(sessionClass)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权查看该班级会话");
            }
        }
        return session;
    }

    private User requireStudentForTeacher(User teacher, Long userId) {
        User student = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "学生不存在"));
        if (!UserRole.STUDENT.equalsIgnoreCase(student.getRole())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "目标用户不是学生");
        }
        String managedClass = FeedbackService.managedClass(teacher);
        if (!managedClass.isBlank() && !managedClass.equals(safeClass(student.getStudentClass()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权管理该学生");
        }
        return student;
    }

    private List<String> normalizeExperimentCodes(List<String> rawCodes) {
        if (rawCodes == null || rawCodes.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String code : rawCodes) {
            if (code == null || code.isBlank()) {
                continue;
            }
            String trimmed = code.trim();
            try {
                experimentConfigService.getByCode(trimmed);
            } catch (ResponseStatusException ex) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "未知实验：" + trimmed);
            }
            normalized.add(trimmed);
        }
        return new ArrayList<>(normalized);
    }

    private TeacherStudentItemDto toStudentItem(User user, List<String> codes) {
        List<String> names = codes.stream()
                .map(code -> {
                    try {
                        return experimentConfigService.getByCode(code).getName();
                    } catch (ResponseStatusException ex) {
                        return code;
                    }
                })
                .toList();
        return new TeacherStudentItemDto(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                safeClass(user.getStudentClass()),
                new ArrayList<>(codes),
                new ArrayList<>(names)
        );
    }

    private List<ImportStudentRow> parseCsv(String csv) {
        List<ImportStudentRow> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new StringReader(csv.trim()))) {
            String line;
            boolean first = true;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                if (first && looksLikeHeader(trimmed)) {
                    first = false;
                    continue;
                }
                first = false;
                String[] parts = trimmed.split(",", -1);
                if (parts.length < 2) {
                    continue;
                }
                ImportStudentRow row = new ImportStudentRow();
                row.setUsername(parts[0].trim());
                row.setDisplayName(parts[1].trim());
                if (parts.length > 2) {
                    row.setPassword(parts[2].trim());
                }
                if (parts.length > 3) {
                    row.setStudentClass(parts[3].trim());
                }
                rows.add(row);
            }
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CSV 格式无效");
        }
        return rows;
    }

    private boolean looksLikeHeader(String line) {
        String lower = line.toLowerCase(Locale.ROOT);
        return lower.contains("username") || lower.contains("账号") || lower.contains("学号");
    }

    private TeacherReportItemDto toReportItem(LabSession session, StudentExperimentProgress progress) {
        TeacherReportItemDto dto = new TeacherReportItemDto();
        dto.setSessionId(session.getId());
        dto.setUserId(session.getUserId());
        dto.setStudentName(session.getStudentName());
        dto.setStudentClass(session.getStudentClass());
        dto.setExperimentCode(session.getExperimentCode());
        dto.setExperimentName(session.getExperimentName());
        dto.setStatus(session.getStatus());
        dto.setStartTime(session.getStartTime());
        dto.setEndTime(session.getEndTime());
        dto.setHelpCount(session.getHelpCount());
        dto.setErrorPointCount(session.getErrorPointCount());
        dto.setReportCompleted(progress != null && progress.isReportCompleted());
        dto.setAiReviewScore(progress != null ? progress.getAiReviewScore() : null);
        dto.setTeacherScore(progress != null ? progress.getTeacherScore() : null);
        dto.setGradingCompleted(progress != null && Boolean.TRUE.equals(progress.getGradingCompleted()));
        return dto;
    }

    private void disambiguateStudentNames(List<TeacherClassroomStudentDto> rows, List<User> students) {
        Map<String, Long> counts = new HashMap<>();
        for (TeacherClassroomStudentDto row : rows) {
            String name = row.getStudentName() == null ? "" : row.getStudentName();
            counts.merge(name, 1L, Long::sum);
        }
        Map<Long, User> byId = students.stream().collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));
        for (TeacherClassroomStudentDto row : rows) {
            String name = row.getStudentName() == null ? "" : row.getStudentName();
            if (counts.getOrDefault(name, 0L) <= 1) {
                continue;
            }
            User user = byId.get(row.getUserId());
            if (user == null || user.getUsername() == null || user.getUsername().isBlank()) {
                continue;
            }
            if (name.equals(user.getUsername())) {
                row.setStudentName(name + " #" + row.getUserId());
            } else {
                row.setStudentName(name + "（" + user.getUsername() + "）");
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> readReportSectionsFromReport(Object raw) {
        if (!(raw instanceof Map<?, ?> map) || map.isEmpty()) {
            return Map.of();
        }
        Map<String, String> sections = new LinkedHashMap<>();
        map.forEach((k, v) -> {
            if (k != null && v != null) {
                sections.put(String.valueOf(k), String.valueOf(v));
            }
        });
        return sections;
    }

    private boolean hasStudentReportText(Map<String, String> sections) {
        return sections != null && sections.values().stream().anyMatch(v -> v != null && !v.isBlank());
    }

    private List<Map<String, String>> studentSectionPayload(Map<String, String> sections) {
        List<Map<String, String>> payload = new ArrayList<>();
        for (String[] def : STUDENT_REPORT_SECTIONS) {
            Map<String, String> item = new LinkedHashMap<>();
            String html = sections.getOrDefault(def[0], "");
            item.put("label", def[1]);
            item.put("contentHtml", html);
            item.put("content", html);
            payload.add(item);
        }
        return payload;
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> readReportSections(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            Map<?, ?> raw = objectMapper.readValue(json, Map.class);
            Map<String, String> sections = new LinkedHashMap<>();
            raw.forEach((k, v) -> {
                if (k != null && v != null) {
                    sections.put(String.valueOf(k), String.valueOf(v));
                }
            });
            return sections;
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String safeClass(String value) {
        return value != null ? value : "";
    }
}
