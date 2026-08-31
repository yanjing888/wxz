package com.wuxiaozhi.service;

import com.wuxiaozhi.config.AppProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class UvcCameraCaptureService {

    private static final Logger log = LoggerFactory.getLogger(UvcCameraCaptureService.class);

    private final AppProperties.UvcCamera config;
    private final FileStorageService fileStorageService;
    private final HttpClient httpClient = HttpClient.newBuilder().build();
    private Process helperProcess;

    public UvcCameraCaptureService(AppProperties appProperties, FileStorageService fileStorageService) {
        this.config = appProperties.getUvcCamera();
        this.fileStorageService = fileStorageService;
    }

    @PostConstruct
    public void start() {
        if (!config.isEnabled()) {
            log.info("UVC camera capture is disabled");
            return;
        }
        Thread starter = new Thread(() -> startHelperIfNeeded(false), "uvc-helper-start");
        starter.setDaemon(true);
        starter.start();
    }

    @PreDestroy
    public void stop() {
        if (helperProcess != null && helperProcess.isAlive()) {
            helperProcess.destroy();
        }
    }

    public Map<String, Object> captureToUpload() {
        if (!config.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "UVC 成像服务未启用");
        }
        startHelperIfNeeded(true);

        URI uri = URI.create(baseUrl() + "/capture.jpg"
                + "?index=" + config.getIndex()
                + "&backend=" + encode(config.getBackend())
                + "&width=" + config.getWidth()
                + "&height=" + config.getHeight()
                + "&fps=" + config.getFps());
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMillis(config.getCaptureTimeoutMs()))
                .GET()
                .build();

        try {
            return captureOnce(request);
        } catch (ResponseStatusException e) {
            log.warn("UVC capture failed, restarting helper and retrying once: {}", e.getReason());
            restartHelper();
            startHelperIfNeeded(true);
            return captureOnce(request);
        }
    }

    private Map<String, Object> captureOnce(HttpRequest request) {
        try {
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                throw unavailable(errorMessage(response.body(), "未检测到 UVC 相机，请检查蓝色 USB 线或确认相机未被其他软件占用"));
            }
            byte[] image = response.body();
            String url = fileStorageService.storeImageBytes(image, ".jpg");
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("url", url);
            body.put("fileName", url.substring(url.lastIndexOf('/') + 1));
            body.put("label", config.getLabel());
            body.put("cameraIndex", config.getIndex());
            body.put("width", parseIntHeader(response, "X-Frame-Width", config.getWidth()));
            body.put("height", parseIntHeader(response, "X-Frame-Height", config.getHeight()));
            return body;
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw unavailable("获取 UVC 成像失败：" + friendlyMessage(e));
        }
    }

    private synchronized void restartHelper() {
        if (helperProcess != null && helperProcess.isAlive()) {
            helperProcess.destroy();
            try {
                if (!helperProcess.waitFor(1500L, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                    helperProcess.destroyForcibly();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                helperProcess.destroyForcibly();
            }
        }
        helperProcess = null;
    }

    private synchronized void startHelperIfNeeded(boolean required) {
        if (isHelperHealthy()) {
            return;
        }
        restartHelper();

        Path script = resolveScriptPath();
        if (!Files.isRegularFile(script)) {
            String message = "UVC 采集脚本不存在：" + script;
            if (required) {
                throw unavailable(message);
            }
            log.warn(message);
            return;
        }

        List<String> command = List.of(
                config.getPythonCommand(),
                script.toString(),
                "--host", config.getHost(),
                "--port", String.valueOf(config.getPort()),
                "--index", String.valueOf(config.getIndex()),
                "--backend", config.getBackend(),
                "--width", String.valueOf(config.getWidth()),
                "--height", String.valueOf(config.getHeight()),
                "--fps", String.valueOf(config.getFps())
        );
        ProcessBuilder builder = new ProcessBuilder(command)
                .directory(script.getParent().toFile())
                .redirectErrorStream(true);
        builder.environment().putIfAbsent("PYTHONIOENCODING", "utf-8");

        try {
            helperProcess = builder.start();
            drainHelperLog(helperProcess);
            long deadline = System.currentTimeMillis() + config.getStartupTimeoutMs();
            while (System.currentTimeMillis() < deadline) {
                if (isHelperHealthy()) {
                    log.info("UVC capture helper started at {}", baseUrl());
                    return;
                }
                if (!helperProcess.isAlive()) {
                    break;
                }
                Thread.sleep(200L);
            }
        } catch (Exception e) {
            if (required) {
                throw unavailable("UVC 采集服务启动失败：" + friendlyMessage(e));
            }
            log.warn("UVC capture helper did not start: {}", friendlyMessage(e));
            return;
        }

        String message = "UVC 采集服务未就绪，请确认 Python 和 opencv-python 已安装";
        if (required) {
            throw unavailable(message);
        }
        log.warn(message);
    }

    private boolean isHelperHealthy() {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl() + "/health"))
                    .timeout(Duration.ofMillis(800L))
                    .GET()
                    .build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode() == 200;
        } catch (Exception ignored) {
            return false;
        }
    }

    private Path resolveScriptPath() {
        Path path = Path.of(config.getScriptPath());
        if (path.isAbsolute()) {
            return path.normalize();
        }
        return Path.of(System.getProperty("user.dir")).resolve(path).normalize();
    }

    private void drainHelperLog(Process process) {
        Thread thread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.info("[uvc-helper] {}", line);
                }
            } catch (IOException ignored) {
            }
        }, "uvc-helper-log");
        thread.setDaemon(true);
        thread.start();
    }

    private String baseUrl() {
        return "http://" + config.getHost() + ":" + config.getPort();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static int parseIntHeader(HttpResponse<?> response, String name, int fallback) {
        return response.headers().firstValue(name)
                .map(value -> {
                    try {
                        return Integer.parseInt(value);
                    } catch (NumberFormatException e) {
                        return fallback;
                    }
                })
                .orElse(fallback);
    }

    private static String errorMessage(byte[] body, String fallback) {
        if (body == null || body.length == 0) {
            return fallback;
        }
        String text = new String(body, StandardCharsets.UTF_8).trim();
        if (text.isBlank()) {
            return fallback;
        }
        int marker = text.indexOf("\"message\"");
        if (marker >= 0) {
            int colon = text.indexOf(':', marker);
            int firstQuote = text.indexOf('"', colon + 1);
            int secondQuote = firstQuote >= 0 ? text.indexOf('"', firstQuote + 1) : -1;
            if (firstQuote >= 0 && secondQuote > firstQuote) {
                return text.substring(firstQuote + 1, secondQuote);
            }
        }
        return text.length() > 180 ? text.substring(0, 180) : text;
    }

    private static ResponseStatusException unavailable(String message) {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, message);
    }

    private static String friendlyMessage(Exception e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }
}
