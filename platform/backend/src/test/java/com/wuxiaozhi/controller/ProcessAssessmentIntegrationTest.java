package com.wuxiaozhi.controller;

import com.fasterxml.jackson.databind.*;
import com.wuxiaozhi.entity.*;
import com.wuxiaozhi.repository.*;
import com.wuxiaozhi.support.MySqlIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
@TestPropertySource(properties = {"wuxiaozhi.test.database-id=process-assessment", "spring.jpa.hibernate.ddl-auto=create-drop",
        "wuxiaozhi.jwt.secret=process-assessment-test-secret-long-enough-for-hs256"})
class ProcessAssessmentIntegrationTest extends MySqlIntegrationTestSupport {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired StudentExperimentAssignmentRepository assignments;
    @Autowired StepAssessmentRepository assessments;
    @Autowired SessionDataLogRepository dataLogs;
    @Autowired StoredUploadRepository uploads;
    @Autowired ChatMessageRepository messages;

    void addChatImage(long sessionId, int step) {
        var message = new ChatMessage(); message.setSessionId(sessionId); message.setStepId(step);
        message.setRole("user"); message.setImageUrl("/uploads/old-chat-photo.jpg"); messages.saveAndFlush(message);
    }

    User user(String name, String role, String cls) {
        User user = new User(); user.setUsername(name); user.setDisplayName(name); user.setRole(role);
        user.setStudentClass(cls); user.setPasswordHash(encoder.encode("test-secret")); return users.save(user);
    }
    String token(User user) throws Exception {
        return call("POST", "/api/auth/login", null, Map.of("username", user.getUsername(), "password", "test-secret"), 200).get("token").asText();
    }
    JsonNode call(String method, String path, String token, Object body, int expected) throws Exception {
        var request = switch (method) { case "POST" -> post(path); case "PATCH" -> patch(path); default -> get(path); };
        request.contentType(MediaType.APPLICATION_JSON);
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (body != null) request.content(json.writeValueAsBytes(body));
        String result = mvc.perform(request).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        return result.isBlank() ? json.nullNode() : json.readTree(result);
    }
    JsonNode complete(long id, int step, String student) throws Exception {
        return imageSubmit(id, step, student, "已完成操作", 200, photo());
    }
    org.springframework.mock.web.MockMultipartFile photo() {
        return new org.springframework.mock.web.MockMultipartFile("images", "experiment.png", "image/png", Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII="));
    }
    List<List<String>> table() { return List.of(List.of("测量项", "读数 / mm"), List.of("位置 1", "12.35")); }
    Map<String, Object> reviewBody(JsonNode state, int step, double score, boolean passed) {
        JsonNode row = state.get("steps").get(step - 1);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("score", score); body.put("passed", passed); body.put("comment", passed ? "操作规范" : "请重新调整光路后复查");
        body.put("checkedCriteria", json.convertValue(row.get("criteria"), List.class));
        body.put("version", row.at("/record/version").isNumber() ? row.at("/record/version").asLong() : null);
        return body;
    }
    JsonNode imageSubmit(long id, int step, String student, String note, int expected, org.springframework.mock.web.MockMultipartFile... images) throws Exception {
        return submitEvidence(id, step, student, Map.of("note", note, "table", table()), expected, images);
    }
    JsonNode submitEvidence(long id, int step, String student, Map<String, Object> body, int expected, org.springframework.mock.web.MockMultipartFile... images) throws Exception {
        var request = multipart("/api/sessions/" + id + "/steps/" + step + "/complete")
                .file(new org.springframework.mock.web.MockMultipartFile("submission", "submission.json", "application/json", json.writeValueAsBytes(body)));
        request.header("Authorization", "Bearer " + student);
        for (var image : images) request.file(image);
        String result = mvc.perform(request).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        return json.readTree(result);
    }
    @Test void imagesAreSavedWithSubmissionSupplementedAndPreservedAcrossRevisions() throws Exception {
        User teacher = user("image-teacher", "TEACHER", "图片测试班");
        User pupil = user("image-student", "STUDENT", "图片测试班");
        User outsider = user("image-other-student", "STUDENT", "其他班");
        var assignment = new StudentExperimentAssignment(); assignment.setUserId(pupil.getId()); assignment.setExperimentCode("newton_rings");
        assignment.setAssignedByUserId(teacher.getId()); assignment.setClassName("图片测试班"); assignments.save(assignment);
        String student = token(pupil), staff = token(teacher), otherStudent = token(outsider);
        long id = call("POST", "/api/sessions", student, Map.of("experimentCode", "newton_rings", "studentName", "图片测试学生"), 200).get("id").asLong();
        byte[] png = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=");
        var photo = new org.springframework.mock.web.MockMultipartFile("images", "光路.png", "image/png", png);
        long initial = uploads.count();
        addChatImage(id, 1);
        imageSubmit(id, 1, student, "没有图片", 409);
        submitEvidence(id, 1, student, Map.of("table", List.of(List.of("表头"), List.of(" "))), 409, photo);
        submitEvidence(id, 1, student, Map.of("table", List.of(List.of(" "), List.of("12"))), 409, photo);
        submitEvidence(id, 1, student, Map.of("table", List.of(List.of("表头"), List.of("12", "13"))), 409, photo);
        imageSubmit(id, 2, student, "未解锁", 409, photo);
        imageSubmit(id, 1, otherStudent, "越权", 404, photo);
        imageSubmit(id, 1, student, "格式错误", 409, new org.springframework.mock.web.MockMultipartFile("images", "note.txt", "text/plain", "not an image".getBytes()));
        assertThat(uploads.count()).isEqualTo(initial);
        JsonNode state = imageSubmit(id, 1, student, "光路已调整", 200, photo, photo, photo, photo, photo, photo, photo);
        JsonNode evidence = json.readTree(state.at("/steps/0/record/evidenceJson").asText());
        assertThat(evidence.get("images")).hasSize(7);
        assertThat(evidence.get("table")).isEqualTo(json.valueToTree(table()));
        assertThat(evidence.toString()).doesNotContain("old-chat-photo");
        String firstUrl = evidence.get("images").get(0).asText();
        mvc.perform(get(firstUrl)).andExpect(status().isOk()).andExpect(content().contentType("image/png")).andExpect(content().bytes(png));
        Map<String, Object> oldReview = reviewBody(state, 1, 18, true);
        var updatedTable = List.of(List.of("测量项", "读数 / mm"), List.of("位置 1", "12.36"));
        state = submitEvidence(id, 1, student, Map.of("note", "补充装置照片", "table", updatedTable), 200, photo);
        evidence = json.readTree(state.at("/steps/0/record/evidenceJson").asText());
        assertThat(evidence.get("images")).hasSize(8);
        assertThat(evidence.get("table")).isEqualTo(json.valueToTree(updatedTable));
        assertThat(evidence.get("images").get(0).asText()).isEqualTo(firstUrl);
        String review = "/api/teacher/sessions/" + id + "/steps/1/review";
        call("POST", review, staff, oldReview, 409);
        JsonNode queue = call("GET", "/api/teacher/process", staff, null, 200);
        assertThat(queue.findValuesAsText("username")).contains(pupil.getUsername());
        assertThat(queue.toString()).contains(firstUrl);
        state = call("POST", review, staff, reviewBody(state, 1, 12, false), 200);
        state = imageSubmit(id, 1, student, "已整改，重新提交照片", 200, photo);
        JsonNode resubmitted = json.readTree(state.at("/steps/0/record/evidenceJson").asText());
        assertThat(resubmitted.get("images")).hasSize(1);
        assertThat(resubmitted.get("images").get(0).asText()).isNotEqualTo(firstUrl);
        JsonNode history = json.readTree(state.at("/steps/0/record/historyJson").asText());
        assertThat(history).hasSize(4);
        assertThat(history.get(1).get("action").asText()).isEqualTo("SUPPLEMENTED");
        assertThat(json.readTree(history.get(0).get("evidence").asText()).get("images").get(0).asText()).isEqualTo(firstUrl);
        assertThat(json.readTree(history.get(0).get("evidence").asText()).get("table")).isEqualTo(json.valueToTree(table()));
        assertThat(json.readTree(history.get(1).get("evidence").asText()).get("table")).isEqualTo(json.valueToTree(updatedTable));
        assertThat(uploads.count()).isEqualTo(initial + 9);
    }
    @Test void imagesAloneAllowProgressAndFinishingButMissingImagesAreRejected() throws Exception {
        User teacher = user("optional-table-teacher", "TEACHER", "选填测试班");
        User pupil = user("optional-table-student", "STUDENT", "选填测试班");
        var assignment = new StudentExperimentAssignment(); assignment.setUserId(pupil.getId()); assignment.setExperimentCode("newton_rings");
        assignment.setAssignedByUserId(teacher.getId()); assignment.setClassName("选填测试班"); assignments.save(assignment);
        String student = token(pupil);
        long id = call("POST", "/api/sessions", student, Map.of("experimentCode", "newton_rings", "studentName", "选填测试学生"), 200).get("id").asLong();
        JsonNode rejected = call("POST", "/api/sessions/" + id + "/steps/1/complete", student, Map.of("table", table()), 409);
        assertThat(rejected.toString()).contains("必填项", "检查图片");
        JsonNode state = submitEvidence(id, 1, student, Map.of("note", "只提交图片"), 200, photo());
        assertThat(json.readTree(state.at("/steps/0/record/evidenceJson").asText()).get("table").isNull()).isTrue();
        assertThat(state.at("/steps/1/accessible").asBoolean()).isTrue();
        assertThat(state.at("/steps/0/canReview").asBoolean()).isTrue();
        for (int step = 2; step <= 5; step++) state = submitEvidence(id, step, student, Map.of("table", List.of()), 200, photo());
        assertThat(state.get("completedCount").asInt()).isEqualTo(5);
        call("POST", "/api/sessions/" + id + "/finish", student, null, 200);
    }
    @Test void strictProgressRevisionEvidencePermissionsAndFinalGrades() throws Exception {
        User teacher = user("process-teacher", "TEACHER", "实验一班");
        User otherTeacher = user("process-outsider-teacher", "TEACHER", "实验二班");
        User pupil = user("process-student", "STUDENT", "实验一班");
        User outsider = user("process-other-student", "STUDENT", "实验二班");
        var assignment = new StudentExperimentAssignment(); assignment.setUserId(pupil.getId());
        assignment.setExperimentCode("newton_rings"); assignment.setAssignedByUserId(teacher.getId()); assignment.setClassName("实验一班"); assignments.save(assignment);
        String student = token(pupil), staff = token(teacher), otherStaff = token(otherTeacher), otherStudent = token(outsider);
        long id = call("POST", "/api/sessions", student, Map.of("experimentCode", "newton_rings", "studentName", "过程测试学生"), 200).get("id").asLong();
        String base = "/api/sessions/" + id;
        addChatImage(id, 1);
        call("PATCH", base + "/step?stepId=2", student, null, 409);
        call("PATCH", base + "/step?stepId=0", student, null, 409);
        call("POST", base + "/finish", student, null, 409);
        call("POST", base + "/steps/3/complete", student, Map.of(), 409);
        call("POST", base + "/assist", student, Map.of("stepId", 3, "userMessage", "怎么测量"), 409);
        call("POST", base + "/data", student, Map.of("stepId", 3, "values", Map.of("ring_m", "10")), 409);
        call("GET", base + "/process", otherStudent, null, 404);
        call("POST", base + "/steps/1/complete", otherStudent, Map.of(), 404);
        call("GET", "/api/teacher/process", student, null, 403);

        var evidence = new SessionDataLog(); evidence.setSessionId(id); evidence.setStepId(1);
        evidence.setValuesJson("{\"observation\":\"original\"}"); dataLogs.saveAndFlush(evidence);
        JsonNode state = complete(id, 1, student);
        assertThat(state.at("/steps/0/status").asText()).isEqualTo("PENDING");
        assertThat(state.get("score").isNull()).isTrue();
        assertThat(state.at("/steps/1/accessible").asBoolean()).isTrue();
        call("PATCH", base + "/step?stepId=2", student, null, 200);
        call("PATCH", base + "/step?stepId=3", student, null, 409);
        call("POST", base + "/steps/1/complete", student, Map.of("note", "已完成操作", "table", table()), 200);
        assertThat(json.readTree(assessments.findByUserIdAndExperimentCodeAndStepId(pupil.getId(), "newton_rings", 1).orElseThrow().getHistoryJson())).hasSize(1);
        dataLogs.delete(evidence);
        assertThat(assessments.findByUserIdAndExperimentCodeAndStepId(pupil.getId(), "newton_rings", 1).orElseThrow().getEvidenceJson()).contains("original");
        var body = reviewBody(state, 1, 12, false);
        String review = "/api/teacher/sessions/" + id + "/steps/1/review";
        call("POST", review, student, body, 403);
        call("POST", review, otherStaff, body, 403);
        assertThat(call("GET", "/api/teacher/process", otherStaff, null, 200)).isEmpty();
        call("GET", "/api/teacher/process?className=实验一班", otherStaff, null, 403);
        var invalid = new LinkedHashMap<>(body); invalid.put("score", 21); call("POST", review, staff, invalid, 409);
        invalid = new LinkedHashMap<>(body); invalid.put("comment", ""); call("POST", review, staff, invalid, 409);
        state = call("POST", review, staff, body, 200);
        assertThat(state.at("/steps/0/status").asText()).isEqualTo("REVISION");
        call("PATCH", base + "/step?stepId=2", student, null, 200);
        call("POST", review, staff, body, 409); // stale teacher screen
        state = complete(id, 1, student);
        assertThat(json.readTree(state.at("/steps/0/record/evidenceJson").asText()).get("images")).hasSize(1);
        assertThat(state.at("/steps/0/record/evidenceJson").asText()).doesNotContain("old-chat-photo");
        var pass = reviewBody(state, 1, 18, true);
        invalid = new LinkedHashMap<>(pass); invalid.put("checkedCriteria", List.of()); call("POST", review, staff, invalid, 409);
        state = call("POST", review, staff, pass, 200);
        assertThat(state.at("/steps/1/accessible").asBoolean()).isTrue();
        assertThat(json.readTree(state.at("/steps/0/record/historyJson").asText())).hasSize(4);
        call("POST", review, staff, reviewBody(state, 1, 20, true), 409); // no duplicate score
        call("PATCH", base + "/step?stepId=2", student, null, 200);

        long nextId = call("POST", "/api/sessions", student, Map.of("experimentCode", "newton_rings", "studentName", "过程测试学生"), 200).get("id").asLong();
        state = call("GET", "/api/sessions/" + nextId + "/process", student, null, 200);
        assertThat(state.at("/steps/0/record/score").asDouble()).isEqualTo(18);
        // A teacher cannot grade an unsubmitted step, even when chat contains an image.
        addChatImage(nextId, 2);
        call("POST", "/api/teacher/sessions/" + nextId + "/steps/2/review", staff, reviewBody(state, 2, 25, true), 409);
        assertThat(state.at("/steps/2/accessible").asBoolean()).isFalse();
        call("PATCH", "/api/sessions/" + nextId + "/step?stepId=3", student, null, 409);
        state = complete(nextId, 2, student);
        assertThat(state.at("/steps/2/accessible").asBoolean()).isTrue();
        for (int step = 3; step <= 5; step++) {
            state = complete(nextId, step, student);
        }
        assertThat(state.get("score").isNull()).isTrue();
        assertThat(state.get("completedCount").asInt()).isEqualTo(5);
        call("POST", "/api/sessions/" + nextId + "/finish", student, null, 200);
        call("POST", "/api/student/experiments/newton_rings/progress/report", student, Map.of("sessionId", nextId, "sections", Map.of("results", "实验结果")), 200);
        JsonNode graded = call("PATCH", "/api/teacher/reports/" + nextId + "/grade", staff, Map.of("score", 80, "comment", "报告评价"), 200);
        assertThat(graded.get("overallScore").isNull()).isTrue();
        // Review, correction and re-submission remain available after finishing the experiment.
        state = call("POST", "/api/teacher/sessions/" + nextId + "/steps/5/review", staff, reviewBody(state, 5, 5, false), 200);
        assertThat(state.at("/steps/4/canSubmit").asBoolean()).isTrue();
        state = complete(nextId, 5, student);
        for (int step = 2; step <= 5; step++) {
            double max = state.get("steps").get(step - 1).get("maxScore").asDouble();
            state = call("POST", "/api/teacher/sessions/" + nextId + "/steps/" + step + "/review", staff, reviewBody(state, step, max, true), 200);
        }
        assertThat(state.get("score").asDouble()).isEqualTo(98);
        JsonNode progress = call("GET", "/api/student/experiments/newton_rings/progress", student, null, 200);
        assertThat(progress.get("overallScore").asDouble()).isEqualTo(87.2);
        assertThat(progress.at("/processAssessment/score").asDouble()).isEqualTo(98);
        assertThat(call("GET", "/api/ai/tools", student, null, 200).findValuesAsText("code")).doesNotContain("instrument-reading");
    }
}
