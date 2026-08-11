package com.wuxiaozhi.service;

import com.wuxiaozhi.dto.*;
import com.wuxiaozhi.entity.StudentExperimentAssignment;
import com.wuxiaozhi.entity.User;
import com.wuxiaozhi.entity.UserRole;
import com.wuxiaozhi.repository.LabSessionRepository;
import com.wuxiaozhi.repository.MessageFeedbackRepository;
import com.wuxiaozhi.repository.StudentExperimentAssignmentRepository;
import com.wuxiaozhi.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TeacherService {

    private final UserRepository userRepository;
    private final LabSessionRepository sessionRepository;
    private final MessageFeedbackRepository feedbackRepository;
    private final StudentExperimentAssignmentRepository assignmentRepository;
    private final LabSessionService labSessionService;
    private final ReportService reportService;
    private final FeedbackService feedbackService;
    private final ExperimentConfigService experimentConfigService;
    private final PasswordEncoder passwordEncoder;

    public TeacherService(UserRepository userRepository,
                          LabSessionRepository sessionRepository,
                          MessageFeedbackRepository feedbackRepository,
                          StudentExperimentAssignmentRepository assignmentRepository,
                          LabSessionService labSessionService,
                          ReportService reportService,
                          FeedbackService feedbackService,
                          ExperimentConfigService experimentConfigService,
                          PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.feedbackRepository = feedbackRepository;
        this.assignmentRepository = assignmentRepository;
        this.labSessionService = labSessionService;
        this.reportService = reportService;
        this.feedbackService = feedbackService;
        this.experimentConfigService = experimentConfigService;
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
