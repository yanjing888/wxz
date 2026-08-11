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

    private final UserRepository userRepository;
    private final LabSessionRepository sessionRepository;
    private final MessageFeedbackRepository feedbackRepository;
    private final StudentExperimentAssignmentRepository assignmentRepository;
    private final StudentExperimentProgressRepository progressRepository;
    private final CorrectionLogRepository correctionLogRepository;
    private final SessionDataLogRepository sessionDataLogRepository;
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
        List<com.wuxiaozhi.entity.LabSession> sessions = managedClass.isBlank()
                ? sessionRepository.findByStatusOrderByStartTimeDesc("FINISHED")
                : sessionRepository.findByStatusAndStudentClassOrderByStartTimeDesc("FINISHED", managedClass);
        return sessions.stream()
                .filter(s -> experimentCode == null || experimentCode.isBlank()
                        || experimentCode.equals(s.getExperimentCode()))
                .map(this::toReportItem)
                .toList();
    }

    public Map<String, Object> getReport(User teacher, Long sessionId) {
        com.wuxiaozhi.entity.LabSession session = getSessionForTeacher(teacher, sessionId);
        return labSessionService.buildReportData(session.getId());
    }

    public byte[] getReportDocx(User teacher, Long sessionId) throws Exception {
        Map<String, Object> data = getReport(teacher, sessionId);
        return reportService.generateDocx(data);
    }

    /**
     * 课堂态势：当前班级进行中的实验会话 + 预习就绪，供教师优先介入。
     */
    public TeacherClassroomDto classroom(User teacher, String experimentCode) {
        String managedClass = FeedbackService.managedClass(teacher);
        List<LabSession> activeSessions = managedClass.isBlank()
                ? sessionRepository.findByStatusOrderByStartTimeDesc("ACTIVE")
                : sessionRepository.findByStatusAndStudentClassOrderByStartTimeDesc("ACTIVE", managedClass);

        String filterCode = experimentCode != null ? experimentCode.trim() : "";
        if (!filterCode.isBlank()) {
            activeSessions = activeSessions.stream()
                    .filter(s -> filterCode.equals(s.getExperimentCode()))
                    .toList();
        }

        Set<Long> userIds = activeSessions.stream().map(LabSession::getUserId).collect(Collectors.toSet());
        // 未开课但未就绪的学生：同班已分配该实验
        String focusCode = !filterCode.isBlank()
                ? filterCode
                : activeSessions.stream().map(LabSession::getExperimentCode).findFirst().orElse("");
        Map<Long, Boolean> readyByUser = new HashMap<>();
        if (!focusCode.isBlank() && !userIds.isEmpty()) {
            progressRepository.findByExperimentCodeAndUserIdIn(focusCode, userIds)
                    .forEach(p -> readyByUser.put(p.getUserId(), p.isPreLabCompleted()));
        }

        ExperimentConfig focusExp = null;
        if (!focusCode.isBlank()) {
            try {
                focusExp = experimentConfigService.getByCode(focusCode);
            } catch (RuntimeException ignored) {
                focusExp = null;
            }
        }

        List<TeacherClassroomStudentDto> rows = new ArrayList<>();
        for (LabSession session : activeSessions) {
            rows.add(toClassroomRow(session, readyByUser.getOrDefault(session.getUserId(), false)));
        }

        // 补未开始会话但未就绪的已分配学生（仅当指定了实验）
        if (!focusCode.isBlank()) {
            Set<Long> activeUserIds = rows.stream().map(TeacherClassroomStudentDto::getUserId).collect(Collectors.toSet());
            List<User> classStudents = managedClass.isBlank()
                    ? userRepository.findByRoleOrderByDisplayNameAsc(UserRole.STUDENT)
                    : userRepository.findByRoleAndStudentClassOrderByDisplayNameAsc(UserRole.STUDENT, managedClass);
            Map<Long, List<String>> assigned = assignmentRepository.findByUserIdIn(
                            classStudents.stream().map(User::getId).toList()).stream()
                    .collect(Collectors.groupingBy(
                            StudentExperimentAssignment::getUserId,
                            Collectors.mapping(StudentExperimentAssignment::getExperimentCode, Collectors.toList())));
            Map<Long, Boolean> allReady = progressRepository
                    .findByExperimentCodeAndUserIdIn(focusCode,
                            classStudents.stream().map(User::getId).toList()).stream()
                    .collect(Collectors.toMap(StudentExperimentProgress::getUserId,
                            StudentExperimentProgress::isPreLabCompleted, (a, b) -> a || b));
            for (User student : classStudents) {
                if (activeUserIds.contains(student.getId())) {
                    continue;
                }
                List<String> codes = assigned.getOrDefault(student.getId(), List.of());
                if (!codes.contains(focusCode)) {
                    continue;
                }
                boolean ready = allReady.getOrDefault(student.getId(), false);
                if (ready) {
                    continue;
                }
                TeacherClassroomStudentDto idle = new TeacherClassroomStudentDto();
                idle.setUserId(student.getId());
                idle.setStudentName(student.getDisplayName() != null ? student.getDisplayName() : student.getUsername());
                idle.setStudentClass(safeClass(student.getStudentClass()));
                idle.setExperimentCode(focusCode);
                idle.setExperimentName(focusExp != null ? focusExp.getName() : focusCode);
                idle.setStatus("NOT_STARTED");
                idle.setPreLabCompleted(false);
                idle.setPriority("medium");
                idle.setPriorityReason("未完成进门就绪");
                rows.add(idle);
            }
        }

        rows.sort(Comparator
                .comparingInt((TeacherClassroomStudentDto r) -> priorityRank(r.getPriority()))
                .thenComparing(TeacherClassroomStudentDto::getStudentName, Comparator.nullsLast(String::compareTo)));

        TeacherClassroomDto dto = new TeacherClassroomDto();
        dto.setExperimentCode(focusCode);
        dto.setExperimentName(focusExp != null ? focusExp.getName() : focusCode);
        dto.setManagedClass(managedClass);
        dto.setActiveCount((int) rows.stream().filter(r -> "ACTIVE".equals(r.getStatus())).count());
        dto.setHighPriorityCount((int) rows.stream().filter(r -> "high".equals(r.getPriority())).count());
        dto.setNotReadyCount((int) rows.stream().filter(r -> !r.isPreLabCompleted()).count());
        dto.setDataIssueCount((int) rows.stream().filter(TeacherClassroomStudentDto::isDataIssue).count());
        dto.setStudents(rows);
        return dto;
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
            minutes = Math.max(0, Duration.between(session.getStartTime(), LocalDateTime.now()).toMinutes());
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

        List<SessionDataLog> dataLogs = sessionDataLogRepository.findBySessionIdOrderByCreatedAtAsc(session.getId());
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
        return row;
    }

    private int priorityRank(String priority) {
        if ("high".equals(priority)) return 0;
        if ("medium".equals(priority)) return 1;
        return 2;
    }

    /**
     * 教师报告预评：基于过程报告上下文调用 report-review。
     * AI 只给建议分档与批注，不直接终裁成绩。
     */
    public AiToolInvokeResponse reviewReport(User teacher, Long sessionId) {
        getSessionForTeacher(teacher, sessionId);
        Map<String, Object> report = labSessionService.buildReportData(sessionId);

        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("action", "review");
        inputs.put("tool_code", "report-review");
        inputs.put("tool_name", "报告批改");
        inputs.put("sessionId", sessionId);
        inputs.put("experiment_name", stringOrEmpty(report.get("experimentName")));
        inputs.put("experimentName", stringOrEmpty(report.get("experimentName")));
        inputs.put("student_name", stringOrEmpty(report.get("studentName")));
        inputs.put("student_class", stringOrEmpty(report.get("studentClass")));
        inputs.put("help_count", report.get("helpCount"));
        inputs.put("error_point_count", report.get("errorPointCount"));
        inputs.put("report_context", report);
        inputs.put("data_logs_json", writeJson(report.get("dataLogEntries")));
        inputs.put("corrections_json", writeJson(report.get("corrections")));
        inputs.put("step_summaries_json", writeJson(report.get("stepSummaries")));
        inputs.put("query",
                "请作为大学物理实验教师，对学生实验报告做预评（非终裁）。按以下维度给出："
                        + "1) 完整性；2) 数据可信度；3) 误差分析；4) 结论质量；5) 思考题质量。"
                        + "输出建议分档或分数区间、可编辑批注要点，并标记「疑似空套模板/数据异常」风险。"
                        + "不要直接给出最终成绩，强调需教师确认。");

        AiToolDefinition tool = aiToolCatalogService.requireTool("report-review");
        String workflowKey = aiToolCatalogService.resolveWorkflowKey(tool);
        return difyService.invokeTool(workflowKey, inputs, "teacher-review-" + teacher.getId(), null);
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

    public List<TeacherFeedbackItemDto> listFeedback(User teacher, String rating, Boolean processed, String experimentCode) {
        return feedbackService.listForTeacher(teacher, rating, processed, experimentCode);
    }

    public TeacherFeedbackItemDto markFeedbackProcessed(User teacher, Long feedbackId) {
        return feedbackService.markProcessed(feedbackId, teacher);
    }

    public List<TeacherStudentItemDto> listStudents(User teacher) {
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
                .map(u -> toStudentItem(u, codesByUser.getOrDefault(u.getId(), List.of())))
                .toList();
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
        }
        return result;
    }

    @Transactional
    public TeacherStudentItemDto assignExperiments(User teacher, Long userId, AssignExperimentsRequest req) {
        User student = requireStudentForTeacher(teacher, userId);
        List<String> codes = normalizeExperimentCodes(req.getExperimentCodes());
        assignmentRepository.deleteByUserId(student.getId());
        for (String code : codes) {
            StudentExperimentAssignment assignment = new StudentExperimentAssignment();
            assignment.setUserId(student.getId());
            assignment.setExperimentCode(code);
            assignment.setAssignedByUserId(teacher.getId());
            assignmentRepository.save(assignment);
        }
        return toStudentItem(student, codes);
    }

    @Transactional
    public int bulkAssignExperiments(User teacher, BulkAssignExperimentsRequest req) {
        List<String> codes = normalizeExperimentCodes(req.getExperimentCodes());
        int count = 0;
        for (Long userId : req.getUserIds()) {
            User student = requireStudentForTeacher(teacher, userId);
            assignmentRepository.deleteByUserId(student.getId());
            for (String code : codes) {
                StudentExperimentAssignment assignment = new StudentExperimentAssignment();
                assignment.setUserId(student.getId());
                assignment.setExperimentCode(code);
                assignment.setAssignedByUserId(teacher.getId());
                assignmentRepository.save(assignment);
            }
            count++;
        }
        return count;
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

    private TeacherReportItemDto toReportItem(com.wuxiaozhi.entity.LabSession session) {
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
        return dto;
    }

    private String safeClass(String value) {
        return value != null ? value : "";
    }
}
