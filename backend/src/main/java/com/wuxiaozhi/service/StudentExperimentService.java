package com.wuxiaozhi.service;

import com.wuxiaozhi.dto.CompleteReportRequest;
import com.wuxiaozhi.dto.ExperimentProgressDto;
import com.wuxiaozhi.dto.ExperimentStepProgressDto;
import com.wuxiaozhi.dto.experiment.ExperimentConfig;
import com.wuxiaozhi.entity.LabSession;
import com.wuxiaozhi.entity.StudentExperimentProgress;
import com.wuxiaozhi.repository.LabSessionRepository;
import com.wuxiaozhi.repository.SessionDataLogRepository;
import com.wuxiaozhi.repository.StudentExperimentAssignmentRepository;
import com.wuxiaozhi.repository.StudentExperimentProgressRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class StudentExperimentService {

    private static final Logger log = LoggerFactory.getLogger(StudentExperimentService.class);
    private static final List<String> STEP_KEYS = List.of("lab", "data", "report", "recap");
    private static final List<String> REPORT_SECTION_KEYS = List.of(
            "purpose", "principle", "apparatus", "procedure", "data", "results", "discussion"
    );
    private static final Map<String, String> STEP_LABELS = Map.of(
            "lab", "实验",
            "data", "数据",
            "report", "报告",
            "recap", "复盘"
    );

    private final ExperimentConfigService experimentConfigService;
    private final StudentExperimentAssignmentRepository assignmentRepository;
    private final StudentExperimentProgressRepository progressRepository;
    private final LabSessionRepository sessionRepository;
    private final SessionDataLogRepository sessionDataLogRepository;
    private final StudentExperimentAccessService accessService;
    private final ObjectMapper objectMapper;

    public StudentExperimentService(ExperimentConfigService experimentConfigService,
                                    StudentExperimentAssignmentRepository assignmentRepository,
                                    StudentExperimentProgressRepository progressRepository,
                                    LabSessionRepository sessionRepository,
                                    SessionDataLogRepository sessionDataLogRepository,
                                    StudentExperimentAccessService accessService,
                                    ObjectMapper objectMapper) {
        this.experimentConfigService = experimentConfigService;
        this.assignmentRepository = assignmentRepository;
        this.progressRepository = progressRepository;
        this.sessionRepository = sessionRepository;
        this.sessionDataLogRepository = sessionDataLogRepository;
        this.accessService = accessService;
        this.objectMapper = objectMapper;
    }

    public List<ExperimentProgressDto> listAssignedProgress(Long userId) {
        Set<String> codes = accessService.assignedCodes(userId);
        if (codes.isEmpty()) {
            return List.of();
        }
        Map<String, StudentExperimentProgress> progressByCode = new LinkedHashMap<>();
        progressRepository.findByUserIdAndExperimentCodeIn(userId, codes)
                .forEach(item -> progressByCode.put(item.getExperimentCode(), item));
        List<ExperimentProgressDto> result = new ArrayList<>();
        for (String code : codes) {
            ExperimentConfig config = experimentConfigService.getByCode(code);
            result.add(buildProgress(userId, config, progressByCode.get(code)));
        }
        return result;
    }

    public ExperimentProgressDto getProgress(Long userId, String experimentCode) {
        requireAssigned(userId, experimentCode);
        ExperimentConfig config = experimentConfigService.getByCode(experimentCode);
        StudentExperimentProgress progress = progressRepository.findByUserIdAndExperimentCode(userId, experimentCode)
                .orElse(null);
        return buildProgress(userId, config, progress);
    }

    @Transactional
    public ExperimentProgressDto completePreLab(Long userId, String experimentCode) {
        requireAssigned(userId, experimentCode);
        StudentExperimentProgress progress = progressRepository.findByUserIdAndExperimentCode(userId, experimentCode)
                .orElseGet(() -> newProgress(userId, experimentCode));
        progress.setPreLabCompleted(true);
        progress.setUpdatedAt(LocalDateTime.now());
        progressRepository.save(progress);
        return getProgress(userId, experimentCode);
    }

    @Transactional
    public ExperimentProgressDto completeRecap(Long userId, String experimentCode) {
        requireAssigned(userId, experimentCode);
        StudentExperimentProgress progress = progressRepository.findByUserIdAndExperimentCode(userId, experimentCode)
                .orElseGet(() -> newProgress(userId, experimentCode));
        progress.setRecapCompleted(true);
        progress.setUpdatedAt(LocalDateTime.now());
        progressRepository.save(progress);
        return getProgress(userId, experimentCode);
    }

    @Transactional
    public ExperimentProgressDto completeReport(Long userId, String experimentCode) {
        return completeReport(userId, experimentCode, null);
    }

    @Transactional
    public ExperimentProgressDto completeReport(Long userId, String experimentCode, CompleteReportRequest request) {
        requireAssigned(userId, experimentCode);
        StudentExperimentProgress progress = progressRepository.findByUserIdAndExperimentCode(userId, experimentCode)
                .orElseGet(() -> newProgress(userId, experimentCode));
        progress.setReportCompleted(true);
        if (request != null) {
            if (request.getSessionId() != null) {
                progress.setReportSessionId(request.getSessionId());
            }
            writeReportSections(progress, request.getSections());
        }
        progress.setUpdatedAt(LocalDateTime.now());
        progressRepository.save(progress);
        return getProgress(userId, experimentCode);
    }

    /** 保存报告正文，不改变「是否已提交」标记。导出 Word 或补传草稿时使用。 */
    @Transactional
    public void saveReportBody(Long userId, String experimentCode, Long sessionId, Map<String, String> sections) {
        if (userId == null || experimentCode == null || experimentCode.isBlank()) {
            return;
        }
        if (sections == null || sections.isEmpty() || !hasReportText(sections)) {
            return;
        }
        StudentExperimentProgress progress = progressRepository.findByUserIdAndExperimentCode(userId, experimentCode)
                .orElseGet(() -> newProgress(userId, experimentCode));
        if (sessionId != null) {
            progress.setReportSessionId(sessionId);
        }
        writeReportSections(progress, sections);
        progress.setUpdatedAt(LocalDateTime.now());
        progressRepository.save(progress);
    }

    public static Map<String, String> sectionsFromDocxItems(List<Map<String, String>> items) {
        Map<String, String> map = new LinkedHashMap<>();
        if (items == null) {
            return map;
        }
        for (int i = 0; i < REPORT_SECTION_KEYS.size(); i++) {
            String html = "";
            if (i < items.size() && items.get(i) != null) {
                Map<String, String> item = items.get(i);
                html = firstNonBlank(item.get("contentHtml"), item.get("content"));
            }
            map.put(REPORT_SECTION_KEYS.get(i), html);
        }
        return map;
    }

    public void markRecapCompleted(Long userId, String experimentCode) {
        completeRecap(userId, experimentCode);
    }

    private ExperimentProgressDto buildProgress(Long userId, ExperimentConfig config, StudentExperimentProgress progress) {
        String code = config.getCode();
        boolean reportDone = progress != null && progress.isReportCompleted();
        boolean recapDone = progress != null && progress.isRecapCompleted();
        boolean dataEnabled = config.getDataCollection() != null && config.getDataCollection().isEnabled();

        LabSession active = sessionRepository
                .findFirstByUserIdAndExperimentCodeAndStatusOrderByStartTimeDesc(userId, code, "ACTIVE")
                .orElse(null);
        LabSession finished = sessionRepository
                .findFirstByUserIdAndExperimentCodeAndStatusOrderByStartTimeDesc(userId, code, "FINISHED")
                .orElse(null);
        boolean labDone = finished != null;
        Long dataSessionId = finished != null ? finished.getId() : (active != null ? active.getId() : null);
        boolean dataSubmitted = dataSessionId != null
                && !sessionDataLogRepository.findOfficialBySessionIdOrderByCreatedAtAsc(dataSessionId).isEmpty();

        Map<String, String> statuses = new LinkedHashMap<>();
        if (labDone) {
            statuses.put("lab", "done");
        } else if (active != null) {
            statuses.put("lab", "in_progress");
        } else {
            statuses.put("lab", "available");
        }
        if (!dataEnabled) {
            statuses.put("data", "done");
        } else if (dataSubmitted) {
            statuses.put("data", "done");
        } else {
            statuses.put("data", "available");
        }
        statuses.put("report", reportDone ? "done" : "available");
        statuses.put("recap", recapDone ? "done" : "available");

        String currentStep = resolveCurrentStep(labDone, dataEnabled, dataSubmitted, reportDone, recapDone);

        ExperimentProgressDto dto = new ExperimentProgressDto();
        dto.setExperimentCode(code);
        dto.setExperimentName(config.getName());
        dto.setCurrentStep(currentStep);
        dto.setActiveSessionId(active != null ? active.getId() : null);
        dto.setFinishedSessionId(finished != null ? finished.getId() : null);
        dto.setDataCollectionEnabled(dataEnabled);
        dto.setDataSubmitted(dataSubmitted);
        dto.setPreLabCompleted(progress != null && progress.isPreLabCompleted());
        dto.setLabCompleted(labDone);
        dto.setReportCompleted(reportDone);
        dto.setRecapCompleted(recapDone);
        dto.setReportSections(readReportSections(progress));
        dto.setSteps(buildSteps(statuses));
        return dto;
    }

    private Map<String, String> readReportSections(StudentExperimentProgress progress) {
        if (progress == null || progress.getReportSectionsJson() == null || progress.getReportSectionsJson().isBlank()) {
            return Map.of();
        }
        try {
            Map<?, ?> raw = objectMapper.readValue(progress.getReportSectionsJson(), Map.class);
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

    private void writeReportSections(StudentExperimentProgress progress, Map<String, String> sections) {
        if (sections == null || sections.isEmpty()) {
            return;
        }
        try {
            progress.setReportSectionsJson(objectMapper.writeValueAsString(sections));
        } catch (Exception e) {
            log.warn("保存报告正文失败 userId={} experiment={}", progress.getUserId(), progress.getExperimentCode(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "报告正文保存失败");
        }
    }

    private static boolean hasReportText(Map<String, String> sections) {
        return sections.values().stream().anyMatch(v -> v != null && !v.replaceAll("<[^>]+>", " ").isBlank());
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return b != null ? b : "";
    }

    private String resolveCurrentStep(boolean labDone, boolean dataEnabled, boolean dataSubmitted,
                                      boolean reportDone, boolean recapDone) {
        if (!labDone) return "lab";
        if (dataEnabled && !dataSubmitted) return "data";
        if (!reportDone) return "report";
        if (!recapDone) return "recap";
        return "done";
    }

    private List<ExperimentStepProgressDto> buildSteps(Map<String, String> statuses) {
        List<ExperimentStepProgressDto> steps = new ArrayList<>();
        for (String key : STEP_KEYS) {
            if (!statuses.containsKey(key)) {
                continue;
            }
            ExperimentStepProgressDto step = new ExperimentStepProgressDto();
            step.setKey(key);
            step.setLabel(STEP_LABELS.get(key));
            step.setStatus(statuses.get(key));
            steps.add(step);
        }
        return steps;
    }

    private StudentExperimentProgress newProgress(Long userId, String experimentCode) {
        StudentExperimentProgress progress = new StudentExperimentProgress();
        progress.setUserId(userId);
        progress.setExperimentCode(experimentCode);
        progress.setPreLabCompleted(false);
        progress.setReportCompleted(false);
        progress.setRecapCompleted(false);
        return progress;
    }

    private void requireAssigned(Long userId, String experimentCode) {
        if (!accessService.isAssigned(userId, experimentCode)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "该实验未分配给您，请联系教师");
        }
    }
}
