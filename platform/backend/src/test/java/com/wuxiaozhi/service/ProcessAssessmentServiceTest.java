package com.wuxiaozhi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuxiaozhi.dto.experiment.*;
import com.wuxiaozhi.entity.*;
import com.wuxiaozhi.repository.*;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProcessAssessmentServiceTest {
    @Test void onlyExplicitCheckpointsBlockProgressUntilReview() throws Exception {
        var repository = mock(StepAssessmentRepository.class);
        var config = mock(ExperimentConfigService.class);
        var checkpoint = new StepConfig(); checkpoint.setAssessmentBlocking(true);
        var next = new StepConfig();
        var experiment = new ExperimentConfig(); experiment.setSteps(Map.of("1", checkpoint, "2", next));
        when(config.getByCode("test")).thenReturn(experiment);
        var row = new StepAssessment(); row.setStepId(1); row.setStatus("PENDING"); row.setSubmittedAt(LocalDateTime.now());
        row.setEvidenceJson("{\"images\":[\"/uploads/checkpoint.png\"],\"table\":[[\"Reading\"],[\"1\"]]}");
        when(repository.findByUserIdAndExperimentCodeOrderByStepIdAsc(1L, "test")).thenReturn(List.of(row));
        var storage = mock(FileStorageService.class);
        when(storage.store(any())).thenReturn("/uploads/new-evidence.png");
        var service = new ProcessAssessmentService(repository, config, mock(SessionDataLogRepository.class), new ObjectMapper().findAndRegisterModules(), mock(UserRepository.class), mock(LabSessionRepository.class), storage);
        var session = new LabSession(); session.setId(1L); session.setUserId(1L); session.setExperimentCode("test");
        assertThatThrownBy(() -> service.requireAccessible(session, 2)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        row.setStatus("PASSED"); service.requireAccessible(session, 2);
        row.setStatus("REVISION"); checkpoint.setAssessmentBlocking(false); service.requireAccessible(session, 2);
        row.setSubmittedAt(null);
        assertThatThrownBy(() -> service.requireAccessible(session, 2)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        // An active legacy approval with no evidence must allow a real submission.
        row.setId(1L); row.setStatus("PASSED"); row.setSubmittedAt(LocalDateTime.now()); row.setEvidenceJson("{}");
        when(repository.findByUserIdAndExperimentCodeAndStepId(1L, "test", 1)).thenReturn(Optional.of(row));
        var image = new org.springframework.mock.web.MockMultipartFile("images", "evidence.png", "image/png", new byte[]{1});
        service.complete(session, 1, "", List.of(image), List.of(List.of("Reading"), List.of("1")));
        assertThat(row.getStatus()).isEqualTo("PENDING");
        service.requireAccessible(session, 2);
    }
    @Test void selfCompletionUnlocksOptionalStepWithoutInventingAGrade() {
        var repository = mock(StepAssessmentRepository.class);
        var config = mock(ExperimentConfigService.class);
        var data = mock(SessionDataLogRepository.class);
        var first = new StepConfig(); first.setTitle("准备"); first.setAssessmentRequired(false);
        var second = new StepConfig(); second.setTitle("测量"); second.setAssessmentMaxScore(30.0);
        var experiment = new ExperimentConfig(); experiment.setSteps(Map.of("1", first, "2", second));
        when(config.getByCode("test")).thenReturn(experiment);
        List<StepAssessment> rows = new ArrayList<>();
        when(repository.findByUserIdAndExperimentCodeOrderByStepIdAsc(1L, "test")).thenAnswer(i -> rows);
        when(repository.findByUserIdAndExperimentCodeAndStepId(1L, "test", 1)).thenAnswer(i -> rows.stream().filter(r -> r.getStepId() == 1).findFirst());
        when(repository.saveAndFlush(any())).thenAnswer(i -> { StepAssessment row = i.getArgument(0); row.setId(1L); if (!rows.contains(row)) rows.add(row); return row; });
        var service = new ProcessAssessmentService(repository, config, data, new ObjectMapper().findAndRegisterModules(), mock(UserRepository.class), mock(LabSessionRepository.class), mock(FileStorageService.class));
        var session = new LabSession(); session.setId(1L); session.setUserId(1L); session.setExperimentCode("test");
        assertThatThrownBy(() -> service.requireAccessible(session, 2)).hasMessageContaining("请先提交");
        var rowEvidence = new StepAssessment(); rowEvidence.setId(1L); rowEvidence.setStepId(1); rowEvidence.setEvidenceJson("{\"images\":[\"/uploads/test.png\"]}"); rows.add(rowEvidence);
        var result = service.complete(session, 1, "准备完成", List.of(), List.of(List.of("记录"), List.of("准备完成")));
        assertThat(rows.get(0).getStatus()).isEqualTo("COMPLETED");
        assertThat(rows.get(0).getSubmittedAt()).isNotNull();
        assertThat(result.get("completedCount")).isEqualTo(1);
        assertThat(result.get("score")).isNull();
        assertThat(result.get("maxScore")).isEqualTo(30.0);
        service.requireAccessible(session, 2);
        service.complete(session, 1, "重复点击");
        verify(repository, times(1)).saveAndFlush(any());
        var graded = new StepAssessment(); graded.setStepId(2); graded.setStatus("PASSED"); graded.setScore(24.0); graded.setSubmittedAt(LocalDateTime.now()); rows.add(graded);
        assertThat(service.summary(session).get("score")).isEqualTo(80.0);
        experiment.setProcessScoreWeight(25.0);
        assertThat(service.overallScore(service.summary(session), 100.0, "test")).isEqualTo(95.0);
    }
}
