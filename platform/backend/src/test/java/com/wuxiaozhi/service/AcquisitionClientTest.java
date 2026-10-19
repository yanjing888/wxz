package com.wuxiaozhi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.wuxiaozhi.config.AppProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.*;

class AcquisitionClientTest {
    private final ObjectMapper json = new ObjectMapper();
    private final AppProperties properties = new AppProperties();
    private HttpServer server;
    private HttpHandler handler;
    private int responseStatus;
    private Consumer<ObjectNode> mutate;
    private final AtomicInteger calls = new AtomicInteger();
    private String authorization;
    private String method;
    private String path;

    @BeforeEach
    void startCollector() throws Exception {
        device().setApiToken("unit-test-token");
        responseStatus = 200;
        mutate = body -> {};
        var image = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 3, BufferedImage.TYPE_INT_RGB), "jpeg", image);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        device().setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        handler = exchange -> {
            calls.incrementAndGet();
            authorization = exchange.getRequestHeaders().getFirst("Authorization");
            method = exchange.getRequestMethod();
            path = exchange.getRequestURI().getPath();
            String requestId = json.readTree(exchange.getRequestBody()).path("requestId").asText();
            ObjectNode result = json.createObjectNode().put("apiVersion", "1")
                    .put("requestId", requestId).put("deviceId", "microscope-01")
                    .put("capturedAt", "2026-10-18T10:00:00Z").put("kind", "image")
                    .put("contentType", "image/jpeg").put("width", 2).put("height", 3)
                    .put("deviceName", "测试显微镜")
                    .put("dataBase64", Base64.getEncoder().encodeToString(image.toByteArray()));
            mutate.accept(result);
            byte[] bytes = json.writeValueAsString(result).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(responseStatus, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        };
        server.createContext("/", handler);
        server.start();
    }

    @AfterEach
    void stopCollector() { server.stop(0); }

    private AppProperties.AcquisitionDevice device() { return properties.getAcquisition().getDevices().get("microscope"); }

    private AcquisitionClient client() { return new AcquisitionClient(properties, json); }

    @Test
    void capturesOverHttpWithoutPersistingPreview() {
        var result = client().capturePreview();
        assertThat(result).doesNotContainKey("url");
        assertThat(result.get("previewDataUrl").toString()).startsWith("data:image/jpeg;base64,");
        assertThat(result)
                .containsEntry("cameraName", "测试显微镜").containsEntry("width", 2)
                .containsEntry("deviceId", "microscope-01");
        assertThat(result.get("requestId")).isNotNull();
        assertThat(method).isEqualTo("POST");
        assertThat(path).isEqualTo("/api/v1/devices/microscope-01/captures");
        assertThat(authorization).isEqualTo("Bearer unit-test-token");
    }

    @Test
    void offlineCollectorDoesNotStartProcessesOrReturnDemoData() {
        server.stop(0);
        assertThatThrownBy(() -> client().capturePreview()).isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("采集服务");
    }

    @Test
    void mismatchedRequestCannotBeSaved() {
        mutate = body -> body.put("requestId", "other-request");
        assertThatThrownBy(() -> client().capturePreview()).hasMessageContaining("不匹配");
    }

    @Test
    void mismatchedDeviceCannotBeSaved() {
        mutate = body -> body.put("deviceId", "other-device");
        assertThatThrownBy(() -> client().capturePreview()).hasMessageContaining("不匹配");
    }

    @Test
    void invalidImageCannotBeSaved() {
        mutate = body -> body.put("dataBase64", Base64.getEncoder().encodeToString("not-an-image".getBytes()));
        assertThatThrownBy(() -> client().capturePreview()).hasMessageContaining("有效 JPEG");
    }

    @Test
    void busyDeviceIsNotRetried() {
        responseStatus = 409;
        assertThatThrownBy(() -> client().capturePreview()).hasMessageContaining("正在采集");
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void authenticationFailureExplainsConfigurationMismatch() {
        responseStatus = 401;
        assertThatThrownBy(() -> client().capturePreview()).hasMessageContaining("密钥不匹配");
    }

    @Test
    void wrongDimensionsCannotBeSaved() {
        mutate = body -> body.put("width", 900);
        assertThatThrownBy(() -> client().capturePreview()).hasMessageContaining("尺寸");
    }

    @Test
    void disabledAcquisitionDoesNotCallCollector() {
        properties.getAcquisition().setEnabled(false);
        assertThatThrownBy(() -> client().capturePreview()).hasMessageContaining("未启用");
        assertThat(calls.get()).isZero();
    }

    @Test
    void usesConfiguredEndpointWithProxyPrefixAndDevicePlaceholder() {
        device().setBaseUrl(device().getBaseUrl() + "/collector/");
        device().setCapturePath("/custom-api/{deviceId}/image");
        client().capturePreview();
        assertThat(path).isEqualTo("/collector/custom-api/microscope-01/image");
    }

    @Test
    void supportsFixedEndpointWithoutDeviceInPath() {
        device().setCapturePath("/instrument/capture");
        client().capturePreview();
        assertThat(path).isEqualTo("/instrument/capture");
    }

    @Test
    void rejectsAnAbsoluteUrlInThePathFieldBeforeCallingCollector() {
        device().setCapturePath("https://wrong-server/capture");
        assertThatThrownBy(() -> client().capturePreview()).hasMessageContaining("接口路径必须");
        assertThat(calls.get()).isZero();
    }

    @Test
    void experimentSelectsItsOwnServiceAddressDeviceAndToken() throws Exception {
        var other = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var secondaryCalls = new AtomicInteger();
        other.createContext("/", exchange -> { secondaryCalls.incrementAndGet(); handler.handle(exchange); });
        other.start();
        try {
            var second = new AppProperties.AcquisitionDevice();
            second.setBaseUrl("http://127.0.0.1:" + other.getAddress().getPort());
            second.setCapturePath("/other/{deviceId}/capture");
            second.setDeviceId("camera-02");
            second.setApiToken("second-service-token");
            properties.getAcquisition().getDevices().put("other-camera", second);
            properties.getAcquisition().getExperiments().put("other_experiment", "other-camera");
            mutate = body -> body.put("deviceId", "camera-02");
            var result = client().capturePreview("other_experiment");
            assertThat(secondaryCalls.get()).isEqualTo(1);
            assertThat(path).isEqualTo("/other/camera-02/capture");
            assertThat(authorization).isEqualTo("Bearer second-service-token");
            assertThat(result).containsEntry("deviceId", "camera-02");
        } finally { other.stop(0); }
    }

    @Test
    void missingMappedDeviceDoesNotSilentlyUseDefaultDevice() {
        properties.getAcquisition().getExperiments().put("newton_rings", "missing");
        assertThatThrownBy(() -> client().capturePreview("newton_rings")).hasMessageContaining("未配置");
        assertThat(calls.get()).isZero();
    }

    @Test
    void disabledDeviceIsNotCalled() {
        device().setEnabled(false);
        assertThatThrownBy(() -> client().capturePreview()).hasMessageContaining("设备未启用");
        assertThat(calls.get()).isZero();
    }
}
