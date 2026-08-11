package com.wuxiaozhi.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuxiaozhi.dto.StudentProfileDto;
import com.wuxiaozhi.entity.LabSession;
import com.wuxiaozhi.entity.SessionDataLog;
import com.wuxiaozhi.repository.CorrectionLogRepository;
import com.wuxiaozhi.repository.LabSessionRepository;
import com.wuxiaozhi.repository.SessionDataLogRepository;
import com.wuxiaozhi.repository.StudentExperimentAssignmentRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/** 学习档案聚合：把分散在会话、数据日志与纠错记录里的信息汇成一份可视化概览 */
@Service
public class StudentProfileService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final int TIMELINE_LIMIT = 10;

    private final LabSessionRepository sessionRepository;
    private final SessionDataLogRepository dataLogRepository;
    private final CorrectionLogRepository correctionLogRepository;
    private final StudentExperimentAssignmentRepository assignmentRepository;
    private final ObjectMapper objectMapper;

    public StudentProfileService(LabSessionRepository sessionRepository,
                                 SessionDataLogRepository dataLogRepository,
                                 CorrectionLogRepository correctionLogRepository,
                                 StudentExperimentAssignmentRepository assignmentRepository,
                                 ObjectMapper objectMapper) {
        this.sessionRepository = sessionRepository;
        this.dataLogRepository = dataLogRepository;
        this.correctionLogRepository = correctionLogRepository;
        this.assignmentRepository = assignmentRepository;
        this.objectMapper = objectMapper;
    }

    public StudentProfileDto summary(Long userId) {
        List<LabSession> sessions = sessionRepository.findByUserIdOrderByStartTimeDesc(userId);
        StudentProfileDto dto = new StudentProfileDto();
        dto.setAssignedCount(assignmentRepository.findByUserIdOrderByExperimentCodeAsc(userId).size());
        dto.setStartedCount(sessions.size());

        int validationTotal = 0;
        int validationPassed = 0;
        long minutes = 0;

        for (LabSession session : sessions) {
            if ("FINISHED".equalsIgnoreCase(session.getStatus())) {
                dto.setFinishedCount(dto.getFinishedCount() + 1);
            }
            dto.setHelpCount(dto.getHelpCount() + session.getHelpCount());
            dto.setTutViewCount(dto.getTutViewCount() + session.getTutViewCount());

            int corrections = correctionLogRepository.findBySessionIdOrderByCreatedAtAsc(session.getId()).size();
            dto.setCorrectionCount(dto.getCorrectionCount() + corrections);

            List<SessionDataLog> logs = dataLogRepository.findBySessionIdOrderByCreatedAtAsc(session.getId());
            dto.setDataSubmitCount(dto.getDataSubmitCount() + logs.size());
            for (SessionDataLog log : logs) {
                validationTotal += 1;
                if (isValidationPassed(log.getValidationJson())) {
                    validationPassed += 1;
                }
            }

            if (session.getEndTime() != null) {
                minutes += Math.max(0, Duration.between(session.getStartTime(), session.getEndTime()).toMinutes());
            }

            if (dto.getTimeline().size() < TIMELINE_LIMIT) {
                dto.getTimeline().add(toTimelineItem(session, corrections, logs.size()));
            }
        }

        dto.setLabMinutes(minutes);
        dto.getCapabilities().addAll(buildCapabilities(dto, validationTotal, validationPassed));
        return dto;
    }

    private List<StudentProfileDto.CapabilityDto> buildCapabilities(StudentProfileDto dto,
                                                                    int validationTotal, int validationPassed) {
        int sessions = Math.max(1, dto.getStartedCount());

        double avgCorrections = (double) dto.getCorrectionCount() / sessions;
        int operation = clamp(100 - (int) Math.round(avgCorrections * 15));

        int data = validationTotal == 0 ? 0 : clamp((int) Math.round(100.0 * validationPassed / validationTotal));

        int report = dto.getStartedCount() == 0
                ? 0
                : clamp((int) Math.round(100.0 * dto.getFinishedCount() / dto.getStartedCount()));

        double avgTutViews = (double) dto.getTutViewCount() / sessions;
        int theory = clamp(100 - (int) Math.round(avgTutViews * 10));

        double avgHelp = (double) dto.getHelpCount() / sessions;
        int autonomy = clamp(100 - (int) Math.round(avgHelp * 5));

        return List.of(
                new StudentProfileDto.CapabilityDto("operation", "操作规范",
                        dto.getStartedCount() == 0 ? 0 : operation,
                        String.format("平均每次实验触发 %.1f 次纠错", avgCorrections)),
                new StudentProfileDto.CapabilityDto("data", "数据质量", data,
                        validationTotal == 0 ? "尚无提交数据" : validationPassed + "/" + validationTotal + " 次校验通过"),
                new StudentProfileDto.CapabilityDto("report", "实验完成度", report,
                        dto.getFinishedCount() + "/" + dto.getStartedCount() + " 次会话正常结束"),
                new StudentProfileDto.CapabilityDto("theory", "理论掌握",
                        dto.getStartedCount() == 0 ? 0 : theory,
                        String.format("平均每次查阅教程 %.1f 次", avgTutViews)),
                new StudentProfileDto.CapabilityDto("autonomy", "独立操作",
                        dto.getStartedCount() == 0 ? 0 : autonomy,
                        String.format("平均每次求助 %.1f 次", avgHelp))
        );
    }

    private StudentProfileDto.TimelineItemDto toTimelineItem(LabSession session, int corrections, int dataCount) {
        StudentProfileDto.TimelineItemDto item = new StudentProfileDto.TimelineItemDto();
        item.setSessionId(session.getId());
        item.setExperimentCode(session.getExperimentCode());
        item.setExperimentName(session.getExperimentName());
        item.setStatus(session.getStatus());
        item.setStartTime(session.getStartTime() != null ? session.getStartTime().format(TS) : "");
        item.setEndTime(session.getEndTime() != null ? session.getEndTime().format(TS) : "");
        item.setCorrectionCount(corrections);
        item.setDataSubmitCount(dataCount);
        return item;
    }

    private boolean isValidationPassed(String json) {
        if (json == null || json.isBlank()) {
            return true;
        }
        try {
            Map<String, Object> validation = objectMapper.readValue(json, new TypeReference<>() {});
            return Boolean.TRUE.equals(validation.get("ok"));
        } catch (Exception e) {
            return false;
        }
    }

    private int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }
}
