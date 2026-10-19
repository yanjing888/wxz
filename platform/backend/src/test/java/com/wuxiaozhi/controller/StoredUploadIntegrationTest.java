package com.wuxiaozhi.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.wuxiaozhi.config.AppProperties;
import com.wuxiaozhi.config.DifyProperties;
import com.wuxiaozhi.dto.SaveStudentFileRequest;
import com.wuxiaozhi.entity.User;
import com.wuxiaozhi.repository.StoredUploadRepository;
import com.wuxiaozhi.repository.UserRepository;
import com.wuxiaozhi.service.*;
import com.wuxiaozhi.support.MySqlIntegrationTestSupport;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "wuxiaozhi.test.database-id=stored-uploads", "spring.jpa.hibernate.ddl-auto=create-drop",
        "wuxiaozhi.auth.demo-users-enabled=false", "wuxiaozhi.lab.bootstrap-enabled=false",
        "wuxiaozhi.jwt.secret=upload-test-secret-at-least-thirty-two-bytes"
})
class StoredUploadIntegrationTest extends MySqlIntegrationTestSupport {
    static final byte[] IMAGE = png();
    static final Path LEGACY = legacyFixture();
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired FileStorageService files;
    @Autowired StoredUploadRepository uploads;
    @Autowired StudentFileService studentFiles;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired AppProperties properties;
    @Autowired com.wuxiaozhi.repository.LabSessionRepository sessions;
    Long loggedInUserId;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("wuxiaozhi.upload.dir", () -> LEGACY.toString());
    }

    static byte[] png() {
        try {
            var image = new BufferedImage(80, 60, BufferedImage.TYPE_INT_RGB);
            var bytes = new ByteArrayOutputStream();
            ImageIO.write(image, "png", bytes);
            return bytes.toByteArray();
        } catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }
    static Path legacyFixture() {
        try {
            Path directory = Files.createTempDirectory(Path.of("target"), "legacy-upload-test-");
            Files.write(directory.resolve("old-question.png"), IMAGE);
            return directory;
        } catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }
    @AfterAll static void cleanup() throws Exception {
        Files.deleteIfExists(LEGACY.resolve("old-question.png"));
        Files.delete(LEGACY);
    }
    String token() throws Exception {
        User user = new User();
        user.setUsername("upload-" + UUID.randomUUID());
        user.setDisplayName("上传测试"); user.setRole("STUDENT");
        user.setPasswordHash(encoder.encode("upload-test-password")); users.save(user);
        loggedInUserId = user.getId();
        String body = mvc.perform(post("/api/auth/login").contentType("application/json")
                .content(json.writeValueAsBytes(Map.of("username", user.getUsername(), "password", "upload-test-password"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).path("token").asText();
    }

    @Test void legacyMigrationKeepsUrlsAndWorksWithoutTheOriginalDirectory() throws Exception {
        assertThat(uploads.existsById("old-question.png")).isTrue();
        assertThat(files.importLegacyUploads()).isZero();
        Files.delete(LEGACY.resolve("old-question.png"));
        mvc.perform(get("/uploads/old-question.png?history=1"))
                .andExpect(status().isOk()).andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(IMAGE));
        AppProperties moved = new AppProperties();
        moved.getUpload().setDir(LEGACY.resolve("new-machine-without-uploads").toString());
        var afterMove = new FileStorageService(moved, uploads);
        assertThat(afterMove.importLegacyUploads()).isZero();
        assertThat(afterMove.read("/uploads/old-question.png").getData()).isEqualTo(IMAGE);
        assertThat(Files.exists(LEGACY.resolve("new-machine-without-uploads"))).isFalse();
    }

    @Test void uploadsAndGeneratedImagesAreReturnedFromMysqlWithoutDiskWrites() throws Exception {
        long before;
        try (var paths = Files.list(LEGACY)) { before = paths.count(); }
        var upload = new MockMultipartFile("file", "question.png", "image/png", IMAGE);
        String result = mvc.perform(multipart("/api/upload").file(upload).header("Authorization", "Bearer " + token()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String url = json.readTree(result).path("url").asText();
        mvc.perform(get(url)).andExpect(status().isOk()).andExpect(content().bytes(IMAGE));
        assertThat(files.size(url)).isEqualTo(IMAGE.length);
        String marked = files.storeImageBytes(IMAGE, ".png");
        assertThat(files.read(marked).getData()).isEqualTo(IMAGE);
        byte[] largeImage = new byte[2 * 1024 * 1024];
        System.arraycopy(IMAGE, 0, largeImage, 0, IMAGE.length);
        String largeUrl = files.storeImageBytes(largeImage, ".png");
        assertThat(files.read(largeUrl).getData()).isEqualTo(largeImage);
        try (var paths = Files.list(LEGACY)) { assertThat(paths.count()).isEqualTo(before); }
        mvc.perform(get("/uploads/missing-image.png")).andExpect(status().isNotFound());
        assertThatThrownBy(() -> files.read("/uploads/../config/application.yml")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> files.storeImageBytes(new byte[20 * 1024 * 1024 + 1], ".jpg"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void attachmentsKeepSizeAndContentAfterRemovingALibraryEntry() throws Exception {
        byte[] csv = "x,y\n1,2\n".getBytes(StandardCharsets.UTF_8);
        String url = files.storeAttachment(new MockMultipartFile("file", "data.csv", "text/csv", csv));
        mvc.perform(get(url)).andExpect(status().isOk()).andExpect(content().contentType("text/csv"))
                .andExpect(content().bytes(csv));
        var request = new SaveStudentFileRequest();
        request.setUrl(url); request.setFileName("data.csv");
        var entry = studentFiles.save(123L, request);
        assertThat(entry.getSizeBytes()).isEqualTo(csv.length);
        studentFiles.delete(123L, entry.getId());
        assertThat(studentFiles.list(123L, null)).isEmpty();
        // Shared chat/report links remain valid.
        mvc.perform(get(url)).andExpect(status().isOk()).andExpect(content().bytes(csv));
    }

    @Test void capturingAnImageForTheComposerDoesNotPersistIt() throws Exception {
        String auth = token();
        var session = new com.wuxiaozhi.entity.LabSession();
        session.setUserId(loggedInUserId); session.setExperimentCode("newton_rings");
        session.setExperimentName("牛顿环"); session.setStudentName("测试学生");
        session = sessions.save(session);
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 3, BufferedImage.TYPE_INT_RGB), "jpeg", bytes);
        byte[] jpeg = bytes.toByteArray();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/devices/microscope-01/captures", exchange -> {
            String requestId = json.readTree(exchange.getRequestBody()).path("requestId").asText();
            byte[] reply = json.writeValueAsBytes(Map.of(
                    "apiVersion", "1", "requestId", requestId, "deviceId", "microscope-01",
                    "kind", "image", "contentType", "image/jpeg", "capturedAt", "2026-10-19T11:00:00+08:00",
                    "dataBase64", java.util.Base64.getEncoder().encodeToString(jpeg), "width", 2, "height", 3));
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, reply.length); exchange.getResponseBody().write(reply); exchange.close();
        });
        var device = properties.getAcquisition().getDevices().get("microscope");
        String oldBase = device.getBaseUrl();
        server.start();
        try {
            device.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
            long before = uploads.count();
            for (int i = 0; i < 2; i++) {
                String result = mvc.perform(post("/api/sessions/" + session.getId() + "/ccd-capture")
                        .header("Authorization", "Bearer " + auth))
                        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
                var payload = json.readTree(result);
                assertThat(payload.has("url")).isFalse();
                assertThat(payload.path("previewDataUrl").asText())
                        .isEqualTo("data:image/jpeg;base64," + java.util.Base64.getEncoder().encodeToString(jpeg));
            }
            assertThat(uploads.count()).isEqualTo(before);
        } finally { device.setBaseUrl(oldBase); server.stop(0); }
    }

    @Test void difyReceivesTheDatabaseImageAsMultipartBytes() throws Exception {
        String url = files.storeImageBytes(IMAGE, ".png");
        var uploaded = new AtomicReference<byte[]>();
        var invoked = new AtomicReference<com.fasterxml.jackson.databind.JsonNode>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/files/upload", exchange -> {
            uploaded.set(exchange.getRequestBody().readAllBytes());
            byte[] reply = "{\"id\":\"database-image-id\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, reply.length); exchange.getResponseBody().write(reply); exchange.close();
        });
        server.createContext("/v1/workflows/run", exchange -> {
            invoked.set(json.readTree(exchange.getRequestBody()));
            byte[] reply = "{\"data\":{\"status\":\"succeeded\",\"outputs\":{\"text\":\"image received\"}}}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, reply.length); exchange.getResponseBody().write(reply); exchange.close();
        });
        server.createContext("/v1/chat-messages", exchange -> {
            invoked.set(json.readTree(exchange.getRequestBody()));
            byte[] reply = "{\"answer\":\"image received\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, reply.length); exchange.getResponseBody().write(reply); exchange.close();
        });
        server.start();
        try {
            var properties = new DifyProperties();
            properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/v1");
            properties.setWorkflows(Map.of("ocr", "test-key"));
            properties.setAppMode("workflow");
            var dify = new DifyService(properties, json, files);
            assertThat(dify.invokeTool("ocr", Map.of(), "student", url).isFromDify()).isTrue();
            assertThat(invoked.get().path("inputs").path("image").path("upload_file_id").asText()).isEqualTo("database-image-id");
            properties.setAppMode("chat");
            assertThat(dify.invokeTool("ocr", Map.of("query", "分析图片"), "student", url).isFromDify()).isTrue();
            assertThat(invoked.get().path("files").get(0).path("upload_file_id").asText()).isEqualTo("database-image-id");
            assertThat(new String(uploaded.get(), StandardCharsets.ISO_8859_1))
                    .contains(new String(IMAGE, StandardCharsets.ISO_8859_1));
        } finally { server.stop(0); }
    }
}
