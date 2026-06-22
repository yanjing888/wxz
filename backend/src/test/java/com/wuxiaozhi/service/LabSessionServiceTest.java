package com.wuxiaozhi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuxiaozhi.dto.AssistRequest;
import com.wuxiaozhi.dto.AssistResponse;
import com.wuxiaozhi.dto.EnvCheckResponse;
import com.wuxiaozhi.dto.experiment.ExperimentConfig;
import com.wuxiaozhi.dto.experiment.StepConfig;
import com.wuxiaozhi.entity.EnvCheckLog;
import com.wuxiaozhi.entity.LabSession;
import com.wuxiaozhi.repository.CorrectionLogRepository;
import com.wuxiaozhi.repository.EnvCheckLogRepository;
import com.wuxiaozhi.repository.LabSessionRepository;
import com.wuxiaozhi.repository.SessionDataLogRepository;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LabSessionServiceTest {

    @Test
    void envCheckCountsL2AsSevereWarning() {
        LabSessionRepository sessionRepository = mock(LabSessionRepository.class);
        EnvCheckLogRepository envCheckLogRepository = mock(EnvCheckLogRepository.class);
        DifyService difyService = mock(DifyService.class);

        LabSession session = new LabSession();
        session.setId(12L);
        session.setUserId(0L);
        session.setExperimentCode("general");
        session.setExperimentName("通用实验");
        session.setStudentName("学生");
        session.setLabL3Count(0);

        EnvCheckResponse response = new EnvCheckResponse();
        response.setLevel("L2");
        response.setSummary("严重风险");
        response.setSuggestion("立即处理");

        when(sessionRepository.findById(12L)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any(LabSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(envCheckLogRepository.save(any(EnvCheckLog.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(difyService.envCheck(any(), eq("guest-12"), eq(""))).thenReturn(response);

        LabSessionService service = new LabSessionService(
                sessionRepository,
                mock(CorrectionLogRepository.class),
                envCheckLogRepository,
                mock(SessionDataLogRepository.class),
                mock(ExperimentConfigService.class),
                difyService,
                mock(DifyRetrieveService.class),
                mock(KnowledgeMapService.class),
                mock(DataValidationService.class),
                new ObjectMapper(),
                mock(PlatformTransactionManager.class)
        );

        service.envCheck(12L, null);

        assertThat(session.getLabL3Count()).isEqualTo(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void assistAddsKnowledgeMapFieldsToDifyInputs() {
        LabSessionRepository sessionRepository = mock(LabSessionRepository.class);
        DifyService difyService = mock(DifyService.class);
        ExperimentConfigService experimentConfigService = mock(ExperimentConfigService.class);
        KnowledgeMapService knowledgeMapService = new KnowledgeMapService();
        knowledgeMapService.load();

        LabSession session = new LabSession();
        session.setId(33L);
        session.setUserId(0L);
        session.setExperimentCode("newton_rings");
        session.setExperimentName("牛顿环实验");
        session.setStudentName("学生");
        session.setActiveStep(1);

        StepConfig step = new StepConfig();
        step.setTitle("仪器检查与光路调节");
        step.setDesc("检查光路。");
        step.setCorrectionMode("vision");

        ExperimentConfig experiment = new ExperimentConfig();
        experiment.setCode("newton_rings");
        experiment.setName("牛顿环实验");
        experiment.setCategory("optics");
        experiment.setSteps(Map.of("1", step));

        AssistResponse response = new AssistResponse();
        response.setType("text_assist");
        response.setFeedback("ok");
        response.setMarks(List.of());

        when(sessionRepository.findById(33L)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any(LabSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(experimentConfigService.getByCode("newton_rings")).thenReturn(experiment);
        when(difyService.assist(eq("text-assist"), any(Map.class), eq("guest-33"), eq(experiment), eq(1), eq(false), eq(null)))
                .thenReturn(response);

        LabSessionService service = new LabSessionService(
                sessionRepository,
                mock(CorrectionLogRepository.class),
                mock(EnvCheckLogRepository.class),
                mock(SessionDataLogRepository.class),
                experimentConfigService,
                difyService,
                mock(DifyRetrieveService.class),
                knowledgeMapService,
                mock(DataValidationService.class),
                new ObjectMapper(),
                mock(PlatformTransactionManager.class)
        );

        AssistRequest request = new AssistRequest();
        request.setUserMessage("这一步怎么做？");
        service.assist(33L, request);

        org.mockito.ArgumentCaptor<Map<String, Object>> captor = org.mockito.ArgumentCaptor.forClass((Class) Map.class);
        verify(difyService).assist(eq("text-assist"), captor.capture(), eq("guest-33"), eq(experiment), eq(1), eq(false), eq(null));

        Map<String, Object> inputs = new LinkedHashMap<>(captor.getValue());
        assertThat(inputs).containsEntry("experiment_code", "newton_rings");
        assertThat(inputs).containsEntry("step_id", "1");
        assertThat(inputs).containsEntry("teaching_dataset", "teaching");
        assertThat(inputs).containsEntry("rules_dataset", "correction_rules");
        assertThat(inputs).containsEntry("teaching_doc", "newton_rings/teaching-knowledge.md");
        assertThat(inputs).containsEntry("rules_doc", "newton_rings/visual-rules.md");
        assertThat(inputs).containsEntry("teaching_section", "newton_rings.step.1.teaching");
        assertThat(inputs).containsEntry("rules_section", "newton_rings.step.1.rules");
        assertThat(String.valueOf(inputs.get("retrieval_tags"))).contains("newton_rings", "step:1", "光路调节");
        assertThat((List<String>) inputs.get("retrieval_tags_list")).contains("newton_rings", "step:1", "光路调节");
    }
}
