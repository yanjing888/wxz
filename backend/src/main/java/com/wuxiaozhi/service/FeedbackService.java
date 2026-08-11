package com.wuxiaozhi.service;

import com.wuxiaozhi.dto.SubmitMessageFeedbackRequest;
import com.wuxiaozhi.dto.TeacherFeedbackItemDto;
import com.wuxiaozhi.dto.TeacherOverviewDto;
import com.wuxiaozhi.dto.TeacherReportItemDto;
import com.wuxiaozhi.dto.TeacherStudentItemDto;
import com.wuxiaozhi.entity.ChatMessage;
import com.wuxiaozhi.entity.LabSession;
import com.wuxiaozhi.entity.MessageFeedback;
import com.wuxiaozhi.entity.User;
import com.wuxiaozhi.entity.UserRole;
import com.wuxiaozhi.repository.ChatMessageRepository;
import com.wuxiaozhi.repository.LabSessionRepository;
import com.wuxiaozhi.repository.MessageFeedbackRepository;
import com.wuxiaozhi.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class FeedbackService {

    public static final String HELPFUL = "HELPFUL";
    public static final String NOT_HELPFUL = "NOT_HELPFUL";

    private final MessageFeedbackRepository feedbackRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final LabSessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final LabSessionService labSessionService;

    public FeedbackService(MessageFeedbackRepository feedbackRepository,
                           ChatMessageRepository chatMessageRepository,
                           LabSessionRepository sessionRepository,
                           UserRepository userRepository,
                           LabSessionService labSessionService) {
        this.feedbackRepository = feedbackRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.sessionRepository = sessionRepository;
        this.userRepository = userRepository;
        this.labSessionService = labSessionService;
    }

    @Transactional
    public MessageFeedback submitFeedback(Long sessionId, Long messageId, Long userId, SubmitMessageFeedbackRequest req) {
        labSessionService.getSession(sessionId, userId);
        ChatMessage message = chatMessageRepository.findById(messageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "消息不存在"));
        if (!message.getSessionId().equals(sessionId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "消息与会话不匹配");
        }
        if (!"ai".equalsIgnoreCase(message.getRole())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "只能评价 AI 回复");
        }
        String rating = normalizeRating(req.getRating());
        LabSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "会话不存在"));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在"));

        MessageFeedback feedback = feedbackRepository.findByMessageIdAndUserId(messageId, userId)
                .orElseGet(MessageFeedback::new);
        feedback.setMessageId(messageId);
        feedback.setSessionId(sessionId);
        feedback.setUserId(userId);
        feedback.setRating(rating);
        feedback.setExperimentCode(session.getExperimentCode());
        feedback.setExperimentName(session.getExperimentName());
        feedback.setStepId(message.getStepId());
        feedback.setStudentName(firstNonBlank(session.getStudentName(), user.getDisplayName(), user.getUsername()));
        feedback.setStudentClass(firstNonBlank(session.getStudentClass(), user.getStudentClass(), ""));
        feedback.setUserQuestion(findPreviousUserQuestion(sessionId, message));
        feedback.setAiReply(message.getText() != null ? message.getText() : "");
        if (feedback.getId() == null) {
            feedback.setProcessed(false);
        }
        return feedbackRepository.save(feedback);
    }

    public List<TeacherFeedbackItemDto> listForTeacher(User teacher, String rating, Boolean processed, String experimentCode) {
        String managedClass = managedClass(teacher);
        return feedbackRepository.searchForTeacher(managedClass, blankToNull(rating), processed, blankToNull(experimentCode))
                .stream()
                .map(this::toFeedbackItem)
                .toList();
    }

    @Transactional
    public TeacherFeedbackItemDto markProcessed(Long feedbackId, User teacher) {
        MessageFeedback feedback = feedbackRepository.findById(feedbackId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "反馈不存在"));
        assertTeacherCanView(teacher, feedback.getStudentClass());
        feedback.setProcessed(true);
        return toFeedbackItem(feedbackRepository.save(feedback));
    }

    public Map<Long, String> ratingsForSession(Long userId, Long sessionId) {
        return feedbackRepository.findByUserIdAndSessionIdOrderByCreatedAtDesc(userId, sessionId).stream()
                .collect(Collectors.toMap(MessageFeedback::getMessageId, MessageFeedback::getRating, (a, b) -> b));
    }

    private TeacherFeedbackItemDto toFeedbackItem(MessageFeedback feedback) {
        TeacherFeedbackItemDto dto = new TeacherFeedbackItemDto();
        dto.setId(feedback.getId());
        dto.setMessageId(feedback.getMessageId());
        dto.setSessionId(feedback.getSessionId());
        dto.setUserId(feedback.getUserId());
        dto.setStudentName(feedback.getStudentName());
        dto.setStudentClass(feedback.getStudentClass());
        dto.setExperimentCode(feedback.getExperimentCode());
        dto.setExperimentName(feedback.getExperimentName());
        dto.setStepId(feedback.getStepId());
        dto.setRating(feedback.getRating());
        dto.setUserQuestion(feedback.getUserQuestion());
        dto.setAiReply(feedback.getAiReply());
        dto.setProcessed(feedback.isProcessed());
        dto.setCreatedAt(feedback.getCreatedAt());
        return dto;
    }

    private String findPreviousUserQuestion(Long sessionId, ChatMessage aiMessage) {
        return chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId).stream()
                .filter(m -> "user".equalsIgnoreCase(m.getRole()))
                .filter(m -> m.getCreatedAt().isBefore(aiMessage.getCreatedAt())
                        || (m.getCreatedAt().equals(aiMessage.getCreatedAt()) && m.getId() < aiMessage.getId()))
                .max(Comparator.comparing(ChatMessage::getCreatedAt).thenComparing(ChatMessage::getId))
                .map(ChatMessage::getText)
                .orElse("");
    }

    private String normalizeRating(String rating) {
        if (NOT_HELPFUL.equalsIgnoreCase(rating)) {
            return NOT_HELPFUL;
        }
        if (HELPFUL.equalsIgnoreCase(rating)) {
            return HELPFUL;
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "评价类型无效");
    }

    private void assertTeacherCanView(User teacher, String studentClass) {
        String managedClass = managedClass(teacher);
        if (managedClass.isBlank()) {
            return;
        }
        if (studentClass == null || !managedClass.equals(studentClass)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权查看该班级反馈");
        }
    }

    static String managedClass(User teacher) {
        return teacher.getStudentClass() != null ? teacher.getStudentClass().trim() : "";
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }
}
