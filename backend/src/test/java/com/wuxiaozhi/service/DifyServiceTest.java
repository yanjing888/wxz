package com.wuxiaozhi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuxiaozhi.config.DifyProperties;
import com.wuxiaozhi.dto.AssistResponse;
import com.wuxiaozhi.dto.EnvCheckResponse;
import com.wuxiaozhi.dto.experiment.ExperimentConfig;
import com.wuxiaozhi.dto.experiment.StepConfig;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DifyServiceTest {

    @Test
    void reportReviewUsesWorkflowAndReadsTwoScalarOutputsAsPercent() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        AtomicReference<com.fasterxml.jackson.databind.JsonNode> request = new AtomicReference<>();
        AtomicInteger chatCalls = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/workflows/run", exchange -> {
            request.set(mapper.readTree(exchange.getRequestBody()));
            byte[] body = mapper.writeValueAsBytes(Map.of("data", Map.of("status", "succeeded", "outputs",
                    Map.of("score", 86.5, "comment", "数据记录完整，建议补充不确定度分析。"))));
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(200, body.length);exchange.getResponseBody().write(body);exchange.close();
        });
        server.createContext("/v1/chat-messages", exchange -> {chatCalls.incrementAndGet();exchange.sendResponseHeaders(500, -1);exchange.close();});
        server.start();
        try {
            DifyProperties properties = new DifyProperties();
            properties.setAppMode("chat"); // Other tools remain Chatflows.
            properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/v1");
            properties.setWorkflows(Map.of("report-review", "app-report-key"));
            DifyService service = new DifyService(properties, mapper, mock(FileStorageService.class));
            Map<String,Object> file = Map.of("type", "document", "transfer_method", "local_file", "upload_file_id", "file-123");
            var response = service.invokeTool("report-review", Map.of("student_report_file", file, "experiment_spec", "实验要求"), "teacher-1");
            assertThat(response.isFromDify()).isTrue();
            assertThat(response.getScore()).isEqualTo(86.5);
            assertThat(response.getMaxScore()).isEqualTo(100.0);
            assertThat(response.getComment()).contains("不确定度");
            assertThat(response.getData()).containsOnlyKeys("score", "comment");
            assertThat(request.get().path("response_mode").asText()).isEqualTo("blocking");
            assertThat(request.get().path("inputs").path("student_report_file").path("upload_file_id").asText()).isEqualTo("file-123");
            assertThat(request.get().has("query")).isFalse();
            assertThat(request.get().has("conversation_id")).isFalse();
            assertThat(chatCalls.get()).isZero();
        } finally {server.stop(0);}
    }

    @Test
    void reportReviewRejectsInvalidMarksOrFailedWorkflows() throws Exception {
        AtomicReference<String> responseBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/workflows/run", exchange -> {
            byte[] body = responseBody.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(200, body.length);exchange.getResponseBody().write(body);exchange.close();
        });
        server.start();
        try {
            DifyProperties properties = new DifyProperties();
            properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/v1");
            properties.setWorkflows(Map.of("report-review", "app-report-key"));
            DifyService service = new DifyService(properties, new ObjectMapper(), mock(FileStorageService.class));
            for (String outputs : List.of("{\"score\":101,\"comment\":\"comment\"}", "{\"score\":-1,\"comment\":\"comment\"}",
                    "{\"score\":80,\"comment\":\"\"}", "{\"score\":\"80\",\"comment\":\"comment\"}", "{\"comment\":\"comment\"}")) {
                responseBody.set("{\"data\":{\"status\":\"succeeded\",\"outputs\":" + outputs + "}}");
                assertThat(service.invokeTool("report-review", Map.of(), "teacher-1").isFromDify()).isFalse();
            }
            responseBody.set("{\"data\":{\"status\":\"failed\",\"outputs\":{\"score\":80,\"comment\":\"comment\"}}}");
            assertThat(service.invokeTool("report-review", Map.of(), "teacher-1").isFromDify()).isFalse();
            for (int score : List.of(0, 5, 100)) {
                responseBody.set("{\"data\":{\"status\":\"succeeded\",\"outputs\":{\"score\":" + score + ",\"comment\":\"comment\"}}}");
                assertThat(service.invokeTool("report-review", Map.of(), "teacher-1").getScore()).isEqualTo((double) score);
            }
        } finally {server.stop(0);}
    }

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
        assertThat(response.getFeedback()).contains("AI 助教暂时不可用");
        assertThat(response.getFeedback()).contains("不能可靠回答");
        assertThat(response.getFeedback()).doesNotContain("Dify");
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
        assertThat(response.getFeedback()).contains("AI 助教暂时不可用");
        assertThat(response.getFeedback()).contains("不能可靠回答");
        assertThat(response.getFeedback()).doesNotContain("Dify");
        assertThat(response.getFeedback()).doesNotContain("智能体服务暂时不可用");
        assertThat(response.getFeedback()).doesNotContain("检查光路。");
        assertThat(streamed.toString()).contains("AI 助教暂时不可用");
        assertThat(streamed.toString()).contains("不能可靠回答");
        assertThat(streamed.toString()).doesNotContain("Dify");
        assertThat(streamed.toString()).doesNotContain("智能体服务暂时不可用");
        assertThat(streamed.toString()).doesNotContain("检查光路。");
    }

    @Test
    void envCheckDoesNotReturnRandomMockWhenDifyUnavailable() {
        DifyService service = unavailableDifyService();

        EnvCheckResponse response = service.envCheck(Map.of(), "user-1", null);

        assertThat(response.isFromDify()).isFalse();
        assertThat(response.getLevel()).isEqualTo("NA");
        assertThat(response.getSummary()).contains("安全巡检暂时不可用");
        assertThat(response.getSummary()).contains("不能完成本次安全巡检");
        assertThat(response.getSummary()).doesNotContain("Dify");
        assertThat(response.getSummary()).doesNotContain("示意");
    }

    @Test
    void statusIsUnavailableWhenConfiguredButBaseUrlIsBlank() {
        DifyProperties properties = new DifyProperties();
        properties.setBaseUrl("");
        properties.setApiKey("app-key");
        DifyService service = new DifyService(properties, new ObjectMapper(), mock(FileStorageService.class));

        Map<String, Object> status = service.status();

        assertThat(status).containsEntry("configured", true);
        assertThat(status).containsEntry("reachable", false);
        assertThat(status).containsEntry("available", false);
        assertThat((String) status.get("reason")).contains("Dify base URL");
        assertThat(workflowStatus(status, "env-check")).containsEntry("available", false);
    }

    @Test
    void statusIsAvailableOnlyAfterDifyResponds() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/parameters", exchange -> {
            String auth = exchange.getRequestHeaders().getFirst("Authorization");
            byte[] body = "{}".getBytes();
            int code = "Bearer app-key".equals(auth) ? 200 : 401;
            exchange.sendResponseHeaders(code, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            DifyProperties properties = new DifyProperties();
            properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/v1");
            properties.setApiKey("app-key");
            DifyService service = new DifyService(properties, new ObjectMapper(), mock(FileStorageService.class));

            Map<String, Object> status = service.status();

            assertThat(status).containsEntry("configured", true);
            assertThat(status).containsEntry("reachable", true);
            assertThat(status).containsEntry("available", true);
            assertThat(workflowStatus(status, "env-check")).containsEntry("available", true);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void envCheckWorkflowStatusUsesEnvCheckApiKey() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/parameters", exchange -> {
            String auth = exchange.getRequestHeaders().getFirst("Authorization");
            byte[] body = "{}".getBytes();
            int code = "Bearer env-key".equals(auth) ? 200 : 401;
            exchange.sendResponseHeaders(code, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            DifyProperties properties = new DifyProperties();
            properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/v1");
            properties.setApiKey("text-key");
            properties.setWorkflows(Map.of("env-check", "env-key"));
            DifyService service = new DifyService(properties, new ObjectMapper(), mock(FileStorageService.class));

            Map<String, Object> status = service.status();

            assertThat(workflowStatus(status, "text-assist")).containsEntry("available", false);
            assertThat(workflowStatus(status, "env-check")).containsEntry("available", true);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void formatStreamWorkflowErrorExplainsLocalhostManifestFailure() {
        DifyService service = unavailableDifyService();
        String msg = service.formatStreamWorkflowError(
                "Reached maximum retries for URL http://127.0.0.1:8082/api/public/experiments/newton_rings");
        assertThat(msg).contains("AI 助教配置异常");
        assertThat(msg).contains("无法读取当前实验配置");
        assertThat(msg).doesNotContain("Dify");
        assertThat(msg).doesNotContain("127.0.0.1:8082");
        assertThat(msg).doesNotContain("WXZ_BACKEND_BASE_URL");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> workflowStatus(Map<String, Object> status, String workflowKey) {
        return ((Map<String, Map<String, Object>>) status.get("workflowStatuses")).get(workflowKey);
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
