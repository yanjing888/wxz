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
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
@TestPropertySource(properties = {"wuxiaozhi.test.database-id=teaching-context", "spring.jpa.hibernate.ddl-auto=create-drop",
        "wuxiaozhi.jwt.secret=teaching-context-test-secret-long-enough-for-hs256"})
class TeacherTeachingContextIntegrationTest extends MySqlIntegrationTestSupport {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired StudentExperimentAssignmentRepository assignments;
    @Autowired StudentClassMembershipRepository memberships;
    private static final String CONTEXT = "/api/teacher/teaching-context?experimentCode=newton_rings";
    private static final String QUEUE = "/api/teacher/process?experimentCode=newton_rings";

    User user(String name, String role, String cls) {
        var user = new User(); user.setUsername(name); user.setDisplayName(name); user.setRole(role);
        user.setStudentClass(cls); user.setPasswordHash(encoder.encode("test-secret")); return users.save(user);
    }
    String token(User user) throws Exception {
        return call("POST", "/api/auth/login", null, Map.of("username", user.getUsername(), "password", "test-secret"), 200).get("token").asText();
    }
    JsonNode call(String method, String path, String token, Object body, int expected) throws Exception {
        var request = switch (method) { case "POST" -> post(path); case "PUT" -> put(path); default -> get(path); };
        request.contentType(MediaType.APPLICATION_JSON);
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (body != null) request.content(json.writeValueAsBytes(body));
        String result = mvc.perform(request).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        return result.isBlank() ? json.nullNode() : json.readTree(result);
    }
    @Test void explicitClassScopesOnlyProcessAndIsStoredPerTeacherAndExperiment() throws Exception {
        User teacher = user("context-teacher", "TEACHER", "");
        User restricted = user("context-restricted", "TEACHER", "实验一班");
        User first = user("context-first", "STUDENT", "实验一班");
        User second = user("context-second", "STUDENT", "实验二班");
        user("context-unassigned", "STUDENT", "未分配班级");
        var membership = new StudentClassMembership(); membership.setUserId(first.getId());
        membership.setClassName("额外教学班"); membership.setAddedByUserId(teacher.getId()); memberships.saveAndFlush(membership);
        for (User pupil : new User[] { first, second }) {
            var assignment = new StudentExperimentAssignment(); assignment.setUserId(pupil.getId()); assignment.setExperimentCode("newton_rings");
            assignment.setAssignedByUserId(teacher.getId()); assignment.setClassName(pupil.getStudentClass()); assignments.saveAndFlush(assignment);
            call("POST", "/api/sessions", token(pupil), Map.of("experimentCode", "newton_rings", "studentName", pupil.getDisplayName()), 200);
        }
        String staff = token(teacher), limited = token(restricted);
        JsonNode initial = call("GET", CONTEXT, staff, null, 200);
        assertThat(initial.get("className").asText()).isEmpty();
        assertThat(initial.get("classes")).hasSize(2);
        assertThat(initial.get("classes").toString()).contains("实验一班", "实验二班").doesNotContain("未分配班级", "额外教学班");
        call("PUT", CONTEXT, staff, Map.of("className", "未分配班级"), 403);
        call("PUT", CONTEXT, staff, Map.of("className", "额外教学班"), 403);
        assertThat(call("GET", QUEUE, staff, null, 200)).isEmpty();
        JsonNode classroom = call("GET", "/api/teacher/classroom?experimentCode=newton_rings", staff, null, 200);
        assertThat(classroom.findValuesAsText("username")).contains(first.getUsername(), second.getUsername());
        JsonNode reports = call("GET", "/api/teacher/reports?experimentCode=newton_rings", staff, null, 200);
        JsonNode feedback = call("GET", "/api/teacher/feedback?experimentCode=newton_rings", staff, null, 200);

        call("PUT", CONTEXT, staff, Map.of("className", "实验一班"), 200);
        assertThat(call("GET", CONTEXT, staff, null, 200).get("className").asText()).isEqualTo("实验一班");
        assertThat(call("GET", QUEUE, staff, null, 200).findValuesAsText("username")).containsExactly(first.getUsername());
        call("PUT", CONTEXT, staff, Map.of("className", "实验二班"), 200);
        assertThat(call("GET", QUEUE, staff, null, 200).findValuesAsText("username")).containsExactly(second.getUsername());
        call("GET", QUEUE + "&className=实验一班", staff, null, 409);
        assertThat(call("GET", CONTEXT, limited, null, 200).get("className").asText()).isEmpty();
        assertThat(call("GET", QUEUE, limited, null, 200)).isEmpty();
        call("PUT", CONTEXT, limited, Map.of("className", "实验二班"), 403);
        call("PUT", CONTEXT, staff, Map.of("className", "不存在的班级"), 403);
        String other = "/api/teacher/teaching-context?experimentCode=tensile_steel";
        assertThat(call("GET", other, staff, null, 200).get("className").asText()).isEmpty();
        assertThat(call("GET", other, staff, null, 200).get("classes")).isEmpty();
        call("PUT", other, staff, Map.of("className", "实验一班"), 403);
        var otherAssignment = new StudentExperimentAssignment(); otherAssignment.setUserId(first.getId());
        otherAssignment.setExperimentCode("tensile_steel"); otherAssignment.setClassName("实验一班");
        otherAssignment.setAssignedByUserId(teacher.getId()); assignments.saveAndFlush(otherAssignment);
        call("PUT", other, staff, Map.of("className", "实验一班"), 200);
        assertThat(call("GET", other, staff, null, 200).get("classes")).hasSize(1);
        assertThat(call("GET", CONTEXT, staff, null, 200).get("className").asText()).isEqualTo("实验二班");
        assertThat(call("GET", "/api/teacher/classroom?experimentCode=newton_rings", staff, null, 200).findValuesAsText("username"))
                .contains(first.getUsername(), second.getUsername());
        call("PUT", CONTEXT, staff, Map.of("className", ""), 200);
        assertThat(call("GET", QUEUE, staff, null, 200)).isEmpty();
        assertThat(call("GET", "/api/teacher/classroom?experimentCode=newton_rings", staff, null, 200).findValuesAsText("username"))
                .contains(first.getUsername(), second.getUsername());
        assertThat(call("GET", "/api/teacher/reports?experimentCode=newton_rings", staff, null, 200)).isEqualTo(reports);
        assertThat(call("GET", "/api/teacher/feedback?experimentCode=newton_rings", staff, null, 200)).isEqualTo(feedback);
        call("GET", CONTEXT, token(first), null, 403);
        call("PUT", CONTEXT, staff, Map.of("className", "实验二班"), 200);
        assignments.delete(assignments.findByUserIdAndExperimentCode(second.getId(), "newton_rings").orElseThrow());
        JsonNode removed = call("GET", CONTEXT, staff, null, 200);
        assertThat(removed.get("classes")).hasSize(1);
        assertThat(removed.get("className").asText()).isEmpty();
        assertThat(call("GET", QUEUE, staff, null, 200)).isEmpty();
    }
}
