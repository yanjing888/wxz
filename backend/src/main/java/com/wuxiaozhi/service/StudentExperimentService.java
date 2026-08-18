package com.wuxiaozhi.service;

import com.wuxiaozhi.dto.ExperimentProgressDto;
import com.wuxiaozhi.dto.ExperimentStepProgressDto;
import com.wuxiaozhi.dto.experiment.ExperimentConfig;
import com.wuxiaozhi.entity.LabSession;
import com.wuxiaozhi.entity.StudentExperimentProgress;
import com.wuxiaozhi.repository.LabSessionRepository;
import com.wuxiaozhi.repository.SessionDataLogRepository;
import com.wuxiaozhi.repository.StudentExperimentAssignmentRepository;
import com.wuxiaozhi.repository.StudentExperimentProgressRepository;
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

    private static final List<String> STEP_KEYS = List.of("lab", "data", "report", "recap");
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

    public StudentExperimentService(ExperimentConfigService experimentConfigService,
                                    StudentExperimentAssignmentRepository assignmentRepository,
                                    StudentExperimentProgressRepository progressRepository,
                                    LabSessionRepository sessionRepository,
                                    SessionDataLogRepository sessionDataLogRepository,
                                    StudentExperimentAccessService accessService) {
        this.experimentConfigService = experimentConfigService;
        this.assignmentRepository = assignmentRepository;
        this.progressRepository = progressRepository;
        this.sessionRepository = sessionRepository;
        this.sessionDataLogRepository = sessionDataLogRepository;
        this.accessService = accessService;
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
        requireAssigned(userId, experimentCode);
        StudentExperimentProgress progress = progressRepository.findByUserIdAndExperimentCode(userId, experimentCode)
                .orElseGet(() -> newProgress(userId, experimentCode));
        progress.setReportCompleted(true);
        progress.setUpdatedAt(LocalDateTime.now());
        progressRepository.save(progress);
        return getProgress(userId, experimentCode);
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
                && !sessionDataLogRepository.findBySessionIdOrderByCreatedAtAsc(dataSessionId).isEmpty();

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
        dto.setReportCompleted(reportDone);
        dto.setRecapCompleted(recapDone);
        dto.setSteps(buildSteps(statuses));
        return dto;
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
