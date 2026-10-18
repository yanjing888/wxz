package com.wuxiaozhi.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuxiaozhi.config.AppProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** HTTP contract client. Does not know the collector's language, driver or process lifecycle. */
@Service
public class AcquisitionClient {
    private final AppProperties.Acquisition config;
    private final FileStorageService storage;
    private final ObjectMapper json;
    private final Map<Long, HttpClient> clients = new ConcurrentHashMap<>();

    public AcquisitionClient(AppProperties properties, FileStorageService storage, ObjectMapper json) {
        this.config = properties.getAcquisition();
        this.storage = storage;
        this.json = json;
    }

    public Map<String, Object> captureToUpload() {
        return captureToUpload(null);
    }

    public Map<String, Object> captureToUpload(String experimentCode) {
        if (!config.isEnabled()) {
            throw unavailable("仪器采集服务未启用");
        }
        String profile = experimentCode == null ? config.getDefaultDevice()
                : config.getExperiments().getOrDefault(experimentCode, config.getDefaultDevice());
        AppProperties.AcquisitionDevice device = config.getDevices().get(profile);
        if (device == null) throw unavailable("未配置该实验使用的采集设备，请检查 lab.yml 中的设备配置");
        if (!device.isEnabled()) throw unavailable("该采集设备未启用");
        String deviceId = device.getDeviceId();
        if (deviceId == null || !deviceId.matches("[A-Za-z0-9_-]{1,64}")) {
            throw unavailable("未正确配置采集设备编号");
        }
        String requestId = UUID.randomUUID().toString();
        try {
            String base = device.getBaseUrl() == null ? "" : device.getBaseUrl().trim().replaceAll("/+$", "");
            URI origin = URI.create(base);
            if ((!"http".equalsIgnoreCase(origin.getScheme()) && !"https".equalsIgnoreCase(origin.getScheme()))
                    || origin.getHost() == null || origin.getRawQuery() != null || origin.getRawFragment() != null) {
                throw unavailable("采集服务地址必须是有效的 HTTP 或 HTTPS 地址，不含查询参数或片段");
            }
            String path = device.getCapturePath() == null ? "" : device.getCapturePath().trim();
            if (!path.startsWith("/") || path.startsWith("//")) {
                throw unavailable("采集接口路径必须以 / 开头，请将 IP 和端口填写在 base-url 中");
            }
            URI uri = URI.create(base + path.replace("{deviceId}", deviceId));
            if (uri.getRawFragment() != null) throw unavailable("采集接口路径不能包含片段标识 #");
            HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofMillis(Math.max(1, device.getCaptureTimeoutMs())))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of("requestId", requestId))));
            if (device.getApiToken() != null && !device.getApiToken().isBlank()) {
                builder.header("Authorization", "Bearer " + device.getApiToken());
            }
            // No automatic retry: an HTTP timeout does not imply the hardware command was not executed.
            HttpClient http = clients.computeIfAbsent(Math.max(1, device.getConnectTimeoutMs()), timeout ->
                    HttpClient.newBuilder().connectTimeout(Duration.ofMillis(timeout)).build());
            HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 401 || response.statusCode() == 403) {
                throw unavailable("采集服务密钥不匹配，请检查平台与采集服务配置");
            }
            if (response.statusCode() == 409) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "设备正在采集，请稍后重试");
            }
            if (response.statusCode() != 200) {
                String message = "设备离线或采集失败，请检查采集服务及设备连接";
                try {
                    String detail = json.readTree(response.body()).path("message").asText("");
                    if (!detail.isBlank()) message = detail.substring(0, Math.min(detail.length(), 240));
                } catch (Exception ignored) { }
                throw unavailable(message);
            }
            JsonNode result = json.readTree(response.body());
            if (!"1".equals(result.path("apiVersion").asText())
                    || !requestId.equals(result.path("requestId").asText())
                    || !deviceId.equals(result.path("deviceId").asText())
                    || !"image".equals(result.path("kind").asText())
                    || !"image/jpeg".equals(result.path("contentType").asText())) {
                throw unavailable("采集服务返回的数据与本次请求不匹配");
            }
            OffsetDateTime.parse(result.path("capturedAt").asText());
            String encoded = result.path("dataBase64").asText();
            if (encoded.length() > 16 * 1024 * 1024) throw unavailable("采集图像超过 12MB");
            byte[] image = Base64.getDecoder().decode(encoded);
            if (image.length < 3 || image[0] != (byte) 0xff || image[1] != (byte) 0xd8) {
                throw unavailable("采集服务未返回有效 JPEG 图像");
            }
            var decoded = ImageIO.read(new ByteArrayInputStream(image));
            if (decoded == null || decoded.getWidth() != result.path("width").asInt()
                    || decoded.getHeight() != result.path("height").asInt()) {
                throw unavailable("采集图像尺寸与结果不一致");
            }
            String url = storage.storeImageBytes(image, ".jpg");
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("url", url);
            body.put("fileName", url.substring(url.lastIndexOf('/') + 1));
            body.put("label", device.getLabel());
            body.put("width", decoded.getWidth());
            body.put("height", decoded.getHeight());
            body.put("cameraName", result.path("deviceName").asText());
            body.put("deviceId", deviceId);
            body.put("requestId", requestId);
            body.put("capturedAt", result.path("capturedAt").asText());
            return body;
        } catch (ResponseStatusException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw unavailable("采集请求已中断");
        } catch (java.io.IOException e) {
            throw unavailable("无法连接或读取采集服务，请检查采集服务是否启动及网络连接");
        } catch (Exception e) {
            throw unavailable("采集服务配置或返回的数据无效，请检查服务日志");
        }
    }

    private static ResponseStatusException unavailable(String message) {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, message);
    }
}
