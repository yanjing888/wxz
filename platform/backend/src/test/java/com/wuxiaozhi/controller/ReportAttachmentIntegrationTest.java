package com.wuxiaozhi.controller;

import com.fasterxml.jackson.databind.*;
import com.wuxiaozhi.dto.AiToolInvokeResponse;
import com.wuxiaozhi.entity.*;
import com.wuxiaozhi.repository.*;
import com.wuxiaozhi.service.DifyService;
import com.wuxiaozhi.support.MySqlIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import java.time.LocalDateTime;
import java.io.ByteArrayOutputStream;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
@TestPropertySource(properties = {"wuxiaozhi.test.database-id=report-attachment", "spring.jpa.hibernate.ddl-auto=create-drop",
        "wuxiaozhi.jwt.secret=report-attachment-test-secret-long-enough-for-hs256"})
class ReportAttachmentIntegrationTest extends MySqlIntegrationTestSupport {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired StudentExperimentAssignmentRepository assignments;
    @Autowired LabSessionRepository sessions;
    @Autowired StudentExperimentProgressRepository progress;
    @Autowired StepAssessmentRepository assessments;
    @MockBean DifyService dify;
    User user(String name, String role, String cls) {
        var u = new User(); u.setUsername(name); u.setDisplayName(name); u.setRole(role); u.setStudentClass(cls);
        u.setPasswordHash(encoder.encode("test-secret")); return users.save(u);
    }
    String token(User user) throws Exception {
        return call("POST", "/api/auth/login", null, Map.of("username", user.getUsername(), "password", "test-secret"), 200).get("token").asText();
    }
    JsonNode call(String method, String path, String token, Object body, int expected) throws Exception {
        var req = switch (method) { case "POST" -> post(path); case "PATCH" -> patch(path); default -> get(path); };
        req.contentType(MediaType.APPLICATION_JSON);
        if (token != null) req.header("Authorization", "Bearer " + token);
        if (body != null) req.content(json.writeValueAsBytes(body));
        String result = mvc.perform(req).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        return result.isBlank() ? json.nullNode() : json.readTree(result);
    }
    LabSession session(User pupil, User teacher, String state) {
        var assignment = new StudentExperimentAssignment(); assignment.setUserId(pupil.getId()); assignment.setExperimentCode("newton_rings");
        assignment.setAssignedByUserId(teacher.getId()); assignment.setClassName(pupil.getStudentClass()); assignments.saveAndFlush(assignment);
        var s = new LabSession(); s.setUserId(pupil.getId()); s.setStudentName(pupil.getDisplayName()); s.setStudentClass(pupil.getStudentClass());
        s.setExperimentCode("newton_rings"); s.setExperimentName("牛顿环实验"); s.setStatus(state);
        s.setStartTime(LocalDateTime.now().minusDays(2)); if (state.equals("FINISHED")) s.setEndTime(LocalDateTime.now().minusDays(1));
        return sessions.saveAndFlush(s);
    }
    JsonNode upload(String token, long sessionId, String filename, byte[] bytes, int expected) throws Exception {
        var request = multipart("/api/student/experiments/newton_rings/report-file")
                .file(new MockMultipartFile("file", filename, "application/octet-stream", bytes))
                .param("sessionId", String.valueOf(sessionId)).header("Authorization", "Bearer " + token);
        String response = mvc.perform(request).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        return json.readTree(response);
    }
    @Test void fileSubmissionTimestampOriginalDownloadScoresAndProcessDetailAreAuthorized() throws Exception {
        var teacher = user("file-teacher", "TEACHER", "文件一班"); var otherTeacher = user("file-other-teacher", "TEACHER", "文件二班");
        var pupil = user("file-pupil", "STUDENT", "文件一班"); var outsider = user("file-outsider", "STUDENT", "文件二班");
        var finished = session(pupil, teacher, "FINISHED"); var active = session(outsider, otherTeacher, "ACTIVE");
        String staff = token(teacher), student = token(pupil), other = token(outsider), otherStaff = token(otherTeacher);
        byte[] pdf = "%PDF-1.4\n1 0 obj<</Type/Catalog>>endobj\n%%EOF".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        upload(other, finished.getId(), "report.pdf", pdf, 404);
        upload(other, active.getId(), "report.pdf", pdf, 409);
        upload(student, finished.getId(), "report.exe", pdf, 400);
        upload(student, finished.getId(), "report.pdf", "not pdf".getBytes(), 400);
        upload(student, finished.getId(), "report.docx", pdf, 400);
        JsonNode submitted = upload(student, finished.getId(), "个人实验报告.pdf", pdf, 200);
        assertThat(submitted.get("reportCompleted").asBoolean()).isTrue();
        assertThat(submitted.get("reportFileName").asText()).isEqualTo("个人实验报告.pdf");
        String submittedAt = submitted.get("reportSubmittedAt").asText();
        assertThat(LocalDateTime.parse(submittedAt)).isAfter(finished.getEndTime());
        upload(student, finished.getId(), "report.pdf", pdf, 409);
        String file = "/api/teacher/reports/" + finished.getId() + "/file";
        mvc.perform(get(file).header("Authorization", "Bearer " + staff)).andExpect(status().isOk()).andExpect(content().bytes(pdf)).andExpect(content().contentType("application/pdf"));
        call("GET", file, otherStaff, null, 403); call("GET", file, student, null, 403);
        mvc.perform(get("/api/student/experiments/newton_rings/report-file").param("sessionId", finished.getId().toString()).header("Authorization", "Bearer " + student)).andExpect(status().isOk()).andExpect(content().bytes(pdf));
        call("GET", "/api/student/experiments/newton_rings/report-file?sessionId=" + finished.getId(), other, null, 404);
        var ai = new AiToolInvokeResponse(); ai.setFromDify(true); ai.setScore(85.0); ai.setComment("文件预评");
        when(dify.uploadDocument(any(), eq("个人实验报告.pdf"), anyString(), eq("report-review"))).thenReturn("file-id");
        when(dify.documentFileRef("file-id")).thenReturn(Map.of("type", "document"));
        when(dify.invokeTool(anyString(), anyMap(), anyString(), isNull())).thenReturn(ai);
        call("POST", "/api/teacher/reports/" + finished.getId() + "/ai-review", staff, null, 200);
        verify(dify).uploadDocument(aryEq(pdf), eq("个人实验报告.pdf"), anyString(), eq("report-review"));
        call("PATCH", "/api/teacher/reports/" + finished.getId() + "/grade", staff, Map.of("score", 0, "comment", "零分也需显示"), 200);
        var list = call("GET", "/api/teacher/reports?experimentCode=newton_rings", staff, null, 200);
        var item = list.get(0);
        assertThat(item.get("teacherScore").asDouble()).isZero(); assertThat(item.get("aiReviewScore").asDouble()).isEqualTo(85);
        assertThat(LocalDateTime.parse(item.get("reportSubmittedAt").asText()).withNano(0)).isEqualTo(LocalDateTime.parse(submittedAt).withNano(0));
        assertThat(item.get("processScore").isNull()).isTrue();
        int[] scores = {20, 25, 35, 10, 10};
        for (int step = 1; step <= 5; step++) {
            var record = new StepAssessment(); record.setUserId(pupil.getId()); record.setSessionId(finished.getId()); record.setExperimentCode("newton_rings"); record.setStepId(step);
            record.setStatus("PASSED"); record.setSubmittedAt(LocalDateTime.now()); record.setScore((double) scores[step - 1]);
            record.setEvidenceJson("{\"images\":[\"/uploads/evidence.png\"],\"table\":[[\"读数\"],[\"12.35\"]]}"); assessments.saveAndFlush(record);
        }
        list = call("GET", "/api/teacher/reports?experimentCode=newton_rings", staff, null, 200);
        assertThat(list.get(0).get("processScore").asDouble()).isEqualTo(100);
        String gradesPath = "/api/teacher/evaluation/exports/grades?experimentCode=newton_rings";
        byte[] excel = mvc.perform(get(gradesPath).header("Authorization", "Bearer " + staff)).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (var workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(excel))) {
            var sheet = workbook.getSheetAt(0);
            assertThat(sheet.getLastRowNum()).isEqualTo(1);
            assertThat(sheet.getRow(1).getCell(1).getStringCellValue()).isEqualTo(pupil.getUsername());
            assertThat(sheet.getRow(1).getCell(7).getNumericCellValue()).isZero();
            assertThat(sheet.getRow(1).getCell(9).getNumericCellValue()).isEqualTo(100);
        }
        String zipPath = "/api/teacher/evaluation/exports/reports?experimentCode=newton_rings";
        var archiveRequest = mvc.perform(get(zipPath).header("Authorization", "Bearer " + staff)).andExpect(request().asyncStarted()).andReturn();
        archiveRequest.getAsyncResult(10000);
        byte[] archive = mvc.perform(asyncDispatch(archiveRequest)).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (var zip = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(archive), java.nio.charset.StandardCharsets.UTF_8)) {
            assertThat(zip.getNextEntry().getName()).contains("文件一班/", "个人实验报告.pdf");
            assertThat(zip.readAllBytes()).isEqualTo(pdf);
            assertThat(zip.getNextEntry()).isNull();
        }
        call("GET", gradesPath, student, null, 403);
        call("GET", zipPath, otherStaff, null, 409);
        String detailPath = "/api/teacher/sessions/" + finished.getId() + "/process";
        var detail = call("GET", detailPath, staff, null, 200);
        mvc.perform(get("/teacher/process/" + finished.getId())).andExpect(status().isOk()).andExpect(forwardedUrl("/app/index.html"));
        assertThat(detail.at("/assessment/steps")).hasSize(5);
        assertThat(detail.at("/assessment/steps/0/record/evidenceJson").asText()).contains("evidence.png", "12.35");
        call("GET", detailPath, otherStaff, null, 403); call("GET", detailPath, student, null, 403);
        assignments.delete(assignments.findByUserIdAndExperimentCode(pupil.getId(), "newton_rings").orElseThrow());
        byte[] unassignedExcel = mvc.perform(get(gradesPath).header("Authorization", "Bearer " + staff)).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (var workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(unassignedExcel))) {
            assertThat(workbook.getSheetAt(0).getLastRowNum()).isZero();
        }
        call("GET", zipPath, staff, null, 409);
    }
    @Test void wordReportIsStoredWithoutConvertingOrAutoGeneratingContent() throws Exception {
        var teacher = user("word-teacher", "TEACHER", ""); var pupil = user("word-pupil", "STUDENT", "Word班");
        var finished = session(pupil, teacher, "FINISHED");
        byte[] bytes;
        try (var doc = new XWPFDocument(); var output = new ByteArrayOutputStream()) { doc.createParagraph().createRun().setText("学生独立撰写的报告"); doc.write(output); bytes = output.toByteArray(); }
        String student = token(pupil);
        var result = upload(student, finished.getId(), "报告.docx", bytes, 200);
        assertThat(result.get("reportSections")).isEmpty();
        mvc.perform(get("/api/student/experiments/newton_rings/report-file").param("sessionId", finished.getId().toString()).header("Authorization", "Bearer " + student)).andExpect(status().isOk()).andExpect(content().bytes(bytes));
        var second = user("word-pdf-pupil", "STUDENT", "PDF班"); var secondSession = session(second, teacher, "FINISHED");
        byte[] pdf = "%PDF-1.4\n%%EOF".getBytes();
        upload(token(second), secondSession.getId(), "原始报告.pdf", pdf, 200);
        var request = mvc.perform(get("/api/teacher/evaluation/exports/reports?experimentCode=newton_rings")
                .header("Authorization", "Bearer " + token(teacher))).andExpect(request().asyncStarted()).andReturn();
        request.getAsyncResult(10000);
        var archive = mvc.perform(asyncDispatch(request)).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        Map<String, byte[]> entries = new HashMap<>();
        try (var zip = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(archive), java.nio.charset.StandardCharsets.UTF_8)) {
            java.util.zip.ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) entries.put(entry.getName(), zip.readAllBytes());
        }
        assertThat(entries.keySet()).anyMatch(name -> name.startsWith("Word班/") && name.endsWith("报告.docx"));
        assertThat(entries.keySet()).anyMatch(name -> name.startsWith("PDF班/") && name.endsWith("原始报告.pdf"));
        assertThat(entries.values()).anySatisfy(content -> assertThat(content).isEqualTo(bytes));
        assertThat(entries.values()).anySatisfy(content -> assertThat(content).isEqualTo(pdf));
    }
}
