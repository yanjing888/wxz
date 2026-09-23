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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

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

        String keyword = config.getNameKeyword() == null ? "" : config.getNameKeyword().trim();
        if (keyword.isBlank()) {
            throw unavailable("未配置电子显微镜设备关键字，无法获取成像");
        }

        StringBuilder uriBuilder = new StringBuilder(baseUrl())
                .append("/capture.jpg?backend=").append(encode(config.getBackend()))
                .append("&width=").append(config.getWidth())
                .append("&height=").append(config.getHeight())
                .append("&fps=").append(config.getFps())
                .append("&name_keyword=").append(encode(keyword));
        URI uri = URI.create(uriBuilder.toString());
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
                throw unavailable(errorMessage(response.body(), "电子显微镜未连接或无法成像，请检查 USB 连接并关闭占用相机的软件"));
            }
            byte[] image = response.body();
            String cameraName = response.headers().firstValue("X-Camera-Name").orElse("");
            if (isBuiltinCameraName(cameraName)) {
                throw unavailable("获取成像仅支持电子显微镜，检测到笔记本内置摄像头「" + cameraName + "」，已拒绝");
            }
            if (!cameraName.isBlank() && !isMicroscopeCameraName(cameraName, config.getNameKeyword())) {
                throw unavailable("获取成像仅支持电子显微镜，当前设备为「" + cameraName + "」");
            }
            String url = fileStorageService.storeImageBytes(image, ".jpg");
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("url", url);
            body.put("fileName", url.substring(url.lastIndexOf('/') + 1));
            body.put("label", config.getLabel());
            body.put("width", parseIntHeader(response, "X-Frame-Width", config.getWidth()));
            body.put("height", parseIntHeader(response, "X-Frame-Height", config.getHeight()));
            int cameraIndex = parseIntHeader(response, "X-Camera-Index", -1);
            body.put("cameraIndex", cameraIndex);
            if (!cameraName.isBlank()) {
                body.put("cameraName", cameraName);
            }
            log.info("Microscope capture saved from {} (index {})", cameraName.isBlank() ? "UVC" : cameraName, cameraIndex);
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
        releaseStalePortListeners();
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

        String pythonCommand = resolvePythonCommand(required);
        if (pythonCommand == null) {
            return;
        }

        String keyword = config.getNameKeyword() == null ? "UVC" : config.getNameKeyword().trim();
        if (keyword.isBlank()) {
            keyword = "UVC";
        }

        List<String> command = new ArrayList<>(List.of(
                pythonCommand,
                script.toString(),
                "--host", config.getHost(),
                "--port", String.valueOf(config.getPort()),
                "--backend", config.getBackend(),
                "--width", String.valueOf(config.getWidth()),
                "--height", String.valueOf(config.getHeight()),
                "--fps", String.valueOf(config.getFps()),
                "--warmup", String.valueOf(config.getWarmupFrames()),
                "--name-keyword", keyword
        ));
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
                    log.info("Microscope UVC helper started at {}", baseUrl());
                    return;
                }
                if (!helperProcess.isAlive()) {
                    log.warn("UVC helper exited early with code {}", helperExitCode(helperProcess));
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

        String message = buildStartupFailureMessage(script, pythonCommand);
        if (required) {
            throw unavailable(message);
        }
        log.warn(message);
    }

    private String buildStartupFailureMessage(Path script, String pythonCommand) {
        StringBuilder sb = new StringBuilder();
        sb.append("电子显微镜采集服务未就绪。");
        sb.append("请确认已安装 Python 与 opencv-python、pygrabber，");
        sb.append("且端口 ").append(config.getPort()).append(" 未被占用。");
        sb.append(" 脚本：").append(script);
        sb.append("，Python：").append(pythonCommand);
        return sb.toString();
    }

    private String resolvePythonCommand(boolean required) {
        List<String> candidates = new ArrayList<>();
        if (config.getPythonCommand() != null && !config.getPythonCommand().isBlank()) {
            candidates.add(config.getPythonCommand().trim());
        }
        candidates.add("python");
        candidates.add("py");
        candidates.add("python3");

        for (String command : candidates) {
            if (canRunPython(command)) {
                if (!command.equals(config.getPythonCommand())) {
                    log.info("UVC helper will use Python command: {}", command);
                }
                return command;
            }
        }

        String message = "未找到可用的 Python，请安装 Python 3 并确保 python 或 py 命令可用";
        if (required) {
            throw unavailable(message);
        }
        log.warn(message);
        return null;
    }

    private boolean canRunPython(String command) {
        try {
            Process process = new ProcessBuilder(command, "--version").redirectErrorStream(true).start();
            boolean finished = process.waitFor(4, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private void releaseStalePortListeners() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            releaseWindowsPortListeners();
        }
        try {
            Thread.sleep(400L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void releaseWindowsPortListeners() {
        int port = config.getPort();
        Long keepPid = helperProcess != null && helperProcess.isAlive() ? helperProcess.pid() : null;
        try {
            Process process = new ProcessBuilder("cmd", "/c", "netstat -ano")
                    .redirectErrorStream(true)
                    .start();
            String output;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                output = reader.lines().reduce("", (a, b) -> a + b + "\n");
            }
            process.waitFor(4, TimeUnit.SECONDS);

            String portToken = ":" + port;
            for (String line : output.split("\\R")) {
                if (!line.contains(portToken) || !line.contains("LISTENING")) {
                    continue;
                }
                String trimmed = line.trim();
                String[] parts = trimmed.split("\\s+");
                if (parts.length < 5) {
                    continue;
                }
                String pidText = parts[parts.length - 1];
                try {
                    long pid = Long.parseLong(pidText);
                    if (keepPid != null && pid == keepPid) {
                        continue;
                    }
                    new ProcessBuilder("taskkill", "/F", "/PID", pidText)
                            .redirectErrorStream(true)
                            .start()
                            .waitFor(3, TimeUnit.SECONDS);
                    log.info("Released stale UVC listener on port {} (pid={})", port, pid);
                } catch (NumberFormatException ignored) {
                    // skip malformed netstat rows
                }
            }
        } catch (Exception e) {
            log.debug("Could not release stale listeners on port {}: {}", port, e.getMessage());
        }
    }

    private static int helperExitCode(Process process) {
        try {
            return process.exitValue();
        } catch (IllegalThreadStateException e) {
            return -1;
        }
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
        Path configured = Path.of(config.getScriptPath() == null ? "" : config.getScriptPath());
        if (configured.isAbsolute()) {
            return configured.normalize();
        }
        Path cwd = Path.of(System.getProperty("user.dir"));
        List<Path> candidates = List.of(
                cwd.resolve(configured),
                cwd.resolve("backend").resolve(configured),
                cwd.resolve("backend/scripts/uvc_capture_server.py"),
                cwd.resolve("scripts/uvc_capture_server.py")
        );
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                return candidate.normalize();
            }
        }
        return cwd.resolve(configured).normalize();
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

    private static boolean isBuiltinCameraName(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("fhd")
                || lower.contains("integrated")
                || lower.contains("built-in")
                || lower.contains("built in")
                || lower.contains("windows hello")
                || lower.contains("facial")
                || lower.contains("realtek")
                || lower.contains("lenovo camera")
                || lower.contains("hp hd");
    }

    private static boolean isMicroscopeCameraName(String name, String keyword) {
        if (name == null || name.isBlank()) {
            return false;
        }
        String kw = keyword == null ? "UVC" : keyword.trim();
        if (kw.isBlank()) {
            kw = "UVC";
        }
        return name.toLowerCase(java.util.Locale.ROOT).contains(kw.toLowerCase(java.util.Locale.ROOT));
    }

    private static ResponseStatusException unavailable(String message) {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, message);
    }

    private static String friendlyMessage(Exception e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }
}
