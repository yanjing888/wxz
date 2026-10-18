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
    private final ClassRosterService roster;
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
                          PasswordEncoder passwordEncoder, ClassRosterService roster) {
        this.userRepository = userRepository;
        this.roster=roster;
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
        long studentCount = roster.students(teacher,null).size();
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
                listReports(teacher,null).size(),
                feedbackCount,
                notHelpfulCount,
                unprocessedCount,
                managedClass
        );
    }

    public List<TeacherReportItemDto> listReports(User teacher,String experimentCode) {return listReports(teacher,experimentCode,null);}
    public List<TeacherReportItemDto> listReports(User teacher,String experimentCode,String className) {
        String scope=roster.scope(teacher,className),code=experimentCode==null?"":experimentCode.trim();
        Map<String,TeacherReportItemDto> items=new LinkedHashMap<>();
        for(LabSession session:sessionRepository.findByStatusOrderByStartTimeDesc("FINISHED")){
            String cls=resolvedSessionClass(session);
            if((!scope.isBlank()&&!scope.equals(cls))||(!code.isBlank()&&!code.equals(session.getExperimentCode())))continue;
            var progress=progressRepository.findByUserIdAndExperimentCode(session.getUserId(),session.getExperimentCode()).orElse(null);
            if(progress==null||!progress.isReportCompleted())continue;
            if(progress.getReportSessionId()!=null&&!session.getId().equals(progress.getReportSessionId()))continue;
            String key=session.getUserId()+"|"+session.getExperimentCode()+"|"+cls;
            TeacherReportItemDto item=toReportItem(session,progress);item.setStudentClass(cls);items.putIfAbsent(key,item);
        }
        return new ArrayList<>(items.values());
    }

    public Map<String, Object> getReport(User teacher, Long sessionId) {
        LabSession session = getSessionForTeacher(teacher, sessionId);
        Map<String, Object> data = labSessionService.buildReportData(session.getId());
        data.put("studentClass", resolvedSessionClass(session));
        progressRepository.findByUserIdAndExperimentCode(session.getUserId(), session.getExperimentCode())
                .filter(progress->progress.getReportSessionId()==null||sessionId.equals(progress.getReportSessionId()))
                .ifPresent(progress -> {
                    data.put("reportCompleted", progress.isReportCompleted());
                    data.put("studentReportSections", readReportSections(progress.getReportSectionsJson()));
                    data.put("aiReviewScore", progress.aiReviewScoreAsPercent());
                    data.put("aiReviewComment", progress.getAiReviewComment());
                    data.put("aiReviewJson", parseJsonObject(progress.getAiReviewJson()));
                    data.put("teacherScore", progress.teacherScoreAsPercent());
                    data.put("maxScore", 100);
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
    public TeacherClassroomDto classroom(User teacher,String experimentCode){return classroom(teacher,experimentCode,null);}
    public TeacherClassroomDto classroom(User teacher,String experimentCode,String className) {
        String scope=roster.scope(teacher,className),code=experimentCode==null?"":experimentCode.trim();
        if(code.isBlank())return classroomFromActiveSessions(teacher,scope,"");
        ExperimentConfig config=experimentConfigService.getByCode(code);
        List<User> students=roster.students(teacher,scope);
        Map<Long,User> byId=students.stream().collect(Collectors.toMap(User::getId,u->u));
        List<LabSession> history=byId.isEmpty()?List.of():sessionRepository.findByExperimentCodeAndUserIdInOrderByStartTimeDesc(code,byId.keySet());
        Map<Long,String> historyClasses = new HashMap<>();
        history.forEach(session -> historyClasses.put(session.getId(), resolvedSessionClass(session)));
        Map<String,User> contexts=new LinkedHashMap<>();
        for(var a:assignmentRepository.findByExperimentCode(code)){
            User u=byId.get(a.getUserId());if(u==null)continue;
            String cls=assignmentClass(a,u);if(scope.isBlank()||scope.equals(cls))contexts.put(u.getId()+"|"+cls,u);
        }
        for(var session:history){User u=byId.get(session.getUserId());String cls=historyClasses.get(session.getId());if(u!=null&&(scope.isBlank()||scope.equals(cls)))contexts.putIfAbsent(u.getId()+"|"+cls,u);}
        List<TeacherClassroomStudentDto> rows=new ArrayList<>();
        for(var context:contexts.entrySet()){
            User u=context.getValue();String cls=context.getKey().substring(context.getKey().indexOf('|')+1);
            var matching=history.stream().filter(h->u.getId().equals(h.getUserId())&&cls.equals(historyClasses.get(h.getId()))).toList();
            var canonical=pickCanonicalSession(matching);
            var progress=progressRepository.findByUserIdAndExperimentCode(u.getId(),code).orElse(null);
            boolean ready=progress!=null&&(progress.isPreLabCompleted()||progress.isSimulationCompleted());
            var row=canonical==null?idleStudentRow(u,code,config,ready):toClassroomRow(canonical,ready);
            row.setUsername(u.getUsername());row.setStudentName(displayName(u,canonical==null?u.getDisplayName():canonical.getStudentName()));row.setStudentClass(cls);
            row.setReportCompleted(progress!=null&&progress.isReportCompleted()&&(progress.getReportSessionId()==null||canonical!=null&&canonical.getId().equals(progress.getReportSessionId())));
            if("FINISHED".equals(row.getStatus())&&!row.isDataIssue()){row.setPriority("normal");row.setPriorityReason(row.isReportCompleted()?"实验与报告已完成":"实操已结束，待提交报告");}
            rows.add(row);
        }
        disambiguateStudentNames(rows,students);enrichEnvLogStats(rows);
        rows.sort(Comparator.comparing(TeacherClassroomStudentDto::getStudentClass).thenComparing(TeacherClassroomStudentDto::getStudentName));
        var dto=new TeacherClassroomDto();dto.setExperimentCode(code);dto.setExperimentName(config.getName());dto.setManagedClass(scope);dto.setStudents(rows);
        dto.setActiveCount((int)rows.stream().filter(r->"ACTIVE".equals(r.getStatus())).count());dto.setNotReadyCount((int)rows.stream().filter(r->!r.isPreLabCompleted()).count());dto.setHighPriorityCount((int)rows.stream().filter(r->"high".equals(r.getPriority())).count());dto.setDataIssueCount((int)rows.stream().filter(TeacherClassroomStudentDto::isDataIssue).count());return dto;
    }
    private String assignmentClass(StudentExperimentAssignment a,User u){return a.getClassName()==null||a.getClassName().isBlank()?ClassRosterService.classKey(u.getStudentClass()):a.getClassName();}

    /** Read-only compatibility for legacy sessions that did not save a class. */
    private String resolvedSessionClass(LabSession session) {
        String saved = ClassRosterService.classKey(session.getStudentClass());
        if (!"未分班".equals(saved)) return saved;
        var student = userRepository.findById(session.getUserId()).orElse(null);
        if (student == null) return saved;
        var assignment = assignmentRepository.findByUserIdAndExperimentCode(session.getUserId(), session.getExperimentCode()).orElse(null);
        if (assignment != null && assignment.getClassName() != null && !assignment.getClassName().isBlank()
                && !"未分班".equals(assignment.getClassName().trim())) return assignment.getClassName().trim();
        var classes = roster.classes(student);
        return classes.size() == 1 ? classes.iterator().next() : saved;
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
        if (!Boolean.TRUE.equals(report.get("reportCompleted"))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "请先提交实验报告，再进行预评");
        }
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
        AiToolDefinition tool = aiToolCatalogService.requireTool("report-review");
        String workflowKey = aiToolCatalogService.resolveWorkflowKey(tool);
        AiToolInvokeResponse resp = difyService.invokeTool(workflowKey, inputs, userId, null);
        if (resp == null || !resp.isFromDify() || resp.getScore() == null || resp.getComment() == null || resp.getComment().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "报告预评未完成，请检查Workflow配置或稍后重试");
        }
        persistAiReview(sessionId, resp);
        return resp;
    }

    @Transactional
    public Map<String, Object> completeGrade(User teacher, Long sessionId, CompleteGradeRequest request) {
        LabSession session = getSessionForTeacher(teacher, sessionId);
        StudentExperimentProgress progress = progressRepository
                .findByUserIdAndExperimentCode(session.getUserId(), session.getExperimentCode())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到该学生的实验进度"));
        if(!progress.isReportCompleted()||progress.getReportSessionId()!=null&&!sessionId.equals(progress.getReportSessionId()))throw new ResponseStatusException(HttpStatus.CONFLICT,"只能批改当前已正式提交的报告");
        progress.setTeacherScore(request.getScore());
        progress.setTeacherScoreMax(100.0);
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
                                progress.setAiReviewScoreMax(100.0);
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

    public List<String> listClasses(User teacher){return roster.visibleClasses(teacher);}
    public List<TeacherStudentItemDto> listStudents(User teacher,String experimentCode){return listStudents(teacher,experimentCode,null);}
    public List<TeacherStudentItemDto> listStudents(User teacher,String experimentCode,String className){
        String scope=roster.scope(teacher,className);List<TeacherStudentItemDto> rows=new ArrayList<>();
        var students=roster.students(teacher,scope);var classesByUser=roster.classMap(students);
        for(User u:students){
            var assigned=assignmentRepository.findByUserIdOrderByAssignedAtAscIdAsc(u.getId());
            for(String cls:classesByUser.get(u.getId())){
                if(!scope.isBlank()&&!scope.equals(cls))continue;
                var codes=assigned.stream().filter(a->cls.equals(assignmentClass(a,u))).map(StudentExperimentAssignment::getExperimentCode).toList();
                if(experimentCode!=null&&!experimentCode.isBlank()&&!codes.contains(experimentCode.trim()))continue;
                var item=toStudentItem(u,codes);item.setStudentClass(cls);rows.add(item);
            }
        }
        rows.sort(Comparator.comparing(TeacherStudentItemDto::getStudentClass).thenComparing(TeacherStudentItemDto::getUsername));return rows;
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
    public ImportStudentsResult importStudents(User teacher,ImportStudentsRequest req){
        List<ImportStudentRow> rows=new ArrayList<>();if(req.getCsv()!=null&&!req.getCsv().isBlank())rows.addAll(parseCsv(req.getCsv()));if(req.getStudents()!=null)rows.addAll(req.getStudents());
        if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"请提供学生名单");
        List<String> codes=normalizeExperimentCodes(req.getExperimentCodes());
        var result=new ImportStudentsResult();List<Long> affected=new ArrayList<>();
        for(var row:rows){
            String username=stringOrEmpty(row.getUsername()).trim(),name=stringOrEmpty(row.getDisplayName()).trim();
            String cls=stringOrEmpty(row.getStudentClass()).trim();if(cls.isBlank())cls=stringOrEmpty(req.getClassName()).trim();if(cls.isBlank())cls=FeedbackService.managedClass(teacher);
            if(username.isBlank()||name.isBlank()||cls.isBlank()||username.length()>64||name.length()>64||cls.length()>64){result.setSkipped(result.getSkipped()+1);result.getErrors().add(username+"：请填写有效的学号、姓名和教学班（不超过64字）");continue;}
            try{roster.scope(teacher,cls);}catch(ResponseStatusException e){result.setSkipped(result.getSkipped()+1);result.getErrors().add(username+"：无权导入该教学班");continue;}
            var existing=userRepository.findByUsername(username);User user;
            if(existing.isPresent()){
                user=existing.get();if(!UserRole.STUDENT.equalsIgnoreCase(user.getRole())||!name.equals(user.getDisplayName())){result.setSkipped(result.getSkipped()+1);result.getErrors().add(username+"：账号已存在，请核对姓名和身份；未覆盖原账号");continue;}
                if(user.getStudentClass()==null||user.getStudentClass().isBlank()){user.setStudentClass(cls);userRepository.save(user);}
                result.setUpdated(result.getUpdated()+1);
            }else{
                String password=stringOrEmpty(row.getPassword()).trim();if(password.isBlank())password=stringOrEmpty(req.getDefaultPassword());if(password.isBlank())password="123456";
                if(password.length()<6){result.setSkipped(result.getSkipped()+1);result.getErrors().add(username+"：密码至少6位");continue;}
                user=new User();user.setUsername(username);user.setDisplayName(name);user.setStudentClass(cls);user.setPasswordHash(passwordEncoder.encode(password));user.setRole(UserRole.STUDENT);userRepository.save(user);result.setCreated(result.getCreated()+1);
            }
            roster.join(teacher,user,cls);affected.add(user.getId());
            if(!codes.isEmpty()){
                applyAssignments(teacher,user,codes,"replace".equalsIgnoreCase(req.getAssignMode()),cls,null,null);result.setAssigned(result.getAssigned()+1);
            }
        }
        result.setUserIds(affected.stream().distinct().toList());return result;
    }

    @Transactional
    public TeacherStudentItemDto assignExperiments(User teacher,Long userId,AssignExperimentsRequest req){
        User student=requireStudentForTeacher(teacher,userId);
        String cls=applyAssignments(teacher,student,normalizeExperimentCodes(req.getExperimentCodes()),true,req.getClassName(),req.getInstructions(),req.getReportDueAt());
        return listStudents(teacher,null,cls).stream().filter(r->r.getUserId().equals(userId)).findFirst().orElseThrow();
    }

    @Transactional
    public int bulkAssignExperiments(User teacher,BulkAssignExperimentsRequest req){
        List<String> codes=normalizeExperimentCodes(req.getExperimentCodes());
        for(Long userId:new LinkedHashSet<>(req.getUserIds()))applyAssignments(teacher,requireStudentForTeacher(teacher,userId),codes,!"append".equalsIgnoreCase(req.getMode()),req.getClassName(),req.getInstructions(),req.getReportDueAt());
        return new LinkedHashSet<>(req.getUserIds()).size();
    }

    private String applyAssignments(User teacher,User student,List<String> codes,boolean replace,String requested,String instructions,LocalDateTime due){
        String cls=roster.scope(teacher,requested);
        if(cls.isBlank()){
            var classes=roster.classes(student);if(classes.size()!=1)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"该学生有多个教学班，请先选择本次分配的班级");cls=classes.iterator().next();
        }
        if(!roster.member(student,cls))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"学生尚未加入该教学班");
        if(instructions!=null&&instructions.length()>4000)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"本班要求最多4000字");
        var existing=assignmentRepository.findByUserIdOrderByAssignedAtAscIdAsc(student.getId());
        // Validate the entire change before deleting anything.
        for(var assignment:existing){
            String owner=assignmentClass(assignment,student);
            if(codes.contains(assignment.getExperimentCode())&&!cls.equals(owner))throw new ResponseStatusException(HttpStatus.CONFLICT,student.getDisplayName()+"已在“"+owner+"”分配此实验，不能跨班重复分配同一实验");
            if(replace&&cls.equals(owner)&&!codes.contains(assignment.getExperimentCode()))requireUnstarted(assignment);
        }
        if(replace)for(var assignment:existing)if(cls.equals(assignmentClass(assignment,student))&&!codes.contains(assignment.getExperimentCode()))assignmentRepository.delete(assignment);
        for(String code:codes){
            var assignment=existing.stream().filter(x->code.equals(x.getExperimentCode())).findFirst().orElse(null);
            if(assignment==null){assignment=new StudentExperimentAssignment();assignment.setUserId(student.getId());assignment.setExperimentCode(code);assignment.setAssignedByUserId(teacher.getId());}
            assignment.setClassName(cls);if(instructions!=null)assignment.setInstructions(instructions.trim());if(due!=null)assignment.setReportDueAt(due);assignmentRepository.save(assignment);
        }
        return cls;
    }
    private void requireUnstarted(StudentExperimentAssignment assignment){
        if(!sessionRepository.findByUserIdAndExperimentCodeOrderByStartTimeDesc(assignment.getUserId(),assignment.getExperimentCode()).isEmpty())throw new ResponseStatusException(HttpStatus.CONFLICT,"该实验已有实操记录，不能移除；其报告和历史记录需要保留");
    }
    @Transactional
    public void unassignExperiment(User teacher,Long userId,String experimentCode){
        User student=requireStudentForTeacher(teacher,userId);var assignment=assignmentRepository.findByUserIdAndExperimentCode(userId,experimentCode).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"分配不存在"));
        roster.scope(teacher,assignmentClass(assignment,student));requireUnstarted(assignment);assignmentRepository.delete(assignment);
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
            String sessionClass = resolvedSessionClass(session);
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
        if (!managedClass.isBlank() && !roster.member(student,managedClass)) {
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
        dto.setAiReviewScore(progress != null ? progress.aiReviewScoreAsPercent() : null);
        dto.setTeacherScore(progress != null ? progress.teacherScoreAsPercent() : null);
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
