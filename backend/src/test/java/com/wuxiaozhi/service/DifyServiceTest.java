package com.wuxiaozhi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuxiaozhi.config.DifyProperties;
import com.wuxiaozhi.dto.AssistResponse;
import com.wuxiaozhi.dto.EnvCheckResponse;
import com.wuxiaozhi.dto.experiment.ExperimentConfig;
import com.wuxiaozhi.dto.experiment.StepConfig;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DifyServiceTest {

    @Test
    void assistDoesNotFallbackToStepMockWhenDifyUnavailable() {
        DifyService service = unavailableDifyService();
        ExperimentConfig experiment = experimentWithStepMockText();

        AssistResponse response = service.assist(
                "text-assist",
                Map.of("query", "这一步怎么做？"),
                "user-1",
                experiment,
                1,
                false,
                null
        );

        assertThat(response.isFromDify()).isFalse();
        assertThat(response.getFeedback()).contains("暂时无法连接Dify服务");
        assertThat(response.getFeedback()).contains("不能可靠回答");
        assertThat(response.getFeedback()).doesNotContain("智能体服务暂时不可用");
        assertThat(response.getFeedback()).doesNotContain("检查光路。");
    }

    @Test
    void streamAssistDoesNotStreamStepMockWhenDifyUnavailable() {
        DifyService service = unavailableDifyService();
        ExperimentConfig experiment = experimentWithStepMockText();
        StringBuilder streamed = new StringBuilder();

        AssistResponse response = service.streamAssist(
                "text-assist",
                Map.of("query", "这一步怎么做？"),
                "user-1",
                experiment,
                1,
                false,
                null,
                streamed::append,
                marks -> {},
                () -> {}
        );

        assertThat(response.isFromDify()).isFalse();
        assertThat(response.getFeedback()).contains("暂时无法连接Dify服务");
        assertThat(response.getFeedback()).contains("不能可靠回答");
        assertThat(response.getFeedback()).doesNotContain("智能体服务暂时不可用");
        assertThat(response.getFeedback()).doesNotContain("检查光路。");
        assertThat(streamed.toString()).contains("暂时无法连接Dify服务");
        assertThat(streamed.toString()).contains("不能可靠回答");
        assertThat(streamed.toString()).doesNotContain("智能体服务暂时不可用");
        assertThat(streamed.toString()).doesNotContain("检查光路。");
    }

    @Test
    void envCheckDoesNotReturnRandomMockWhenDifyUnavailable() {
        DifyService service = unavailableDifyService();

        EnvCheckResponse response = service.envCheck(Map.of(), "user-1", null);

        assertThat(response.isFromDify()).isFalse();
        assertThat(response.getLevel()).isEqualTo("NA");
        assertThat(response.getSummary()).contains("暂时无法连接Dify服务");
        assertThat(response.getSummary()).contains("不能完成本次安全巡检");
        assertThat(response.getSummary()).doesNotContain("示意");
    }

    private DifyService unavailableDifyService() {
        DifyProperties properties = new DifyProperties();
        properties.setApiKey("");
        properties.setWorkflows(new LinkedHashMap<>());
        return new DifyService(properties, new ObjectMapper(), mock(FileStorageService.class));
    }

    private ExperimentConfig experimentWithStepMockText() {
        StepConfig step = new StepConfig();
        step.setTitle("仪器检查与光路调节");
        step.setDesc("检查光路。");

        ExperimentConfig experiment = new ExperimentConfig();
        experiment.setCode("newton_rings");
        experiment.setName("牛顿环实验");
        experiment.setSteps(Map.of("1", step));
        return experiment;
    }
}
