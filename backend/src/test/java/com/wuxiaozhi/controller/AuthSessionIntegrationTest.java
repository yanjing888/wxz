package com.wuxiaozhi.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuxiaozhi.entity.ChatMessage;
import com.wuxiaozhi.entity.LabSession;
import com.wuxiaozhi.entity.StudentExperimentAssignment;
import com.wuxiaozhi.entity.User;
import com.wuxiaozhi.repository.ChatMessageRepository;
import com.wuxiaozhi.repository.LabSessionRepository;
import com.wuxiaozhi.repository.StudentExperimentAssignmentRepository;
import com.wuxiaozhi.repository.UserRepository;
import com.wuxiaozhi.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:auth-session-test;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "wuxiaozhi.jwt.secret=auth-session-test-secret-must-be-at-least-32-bytes",
        "wuxiaozhi.jwt.expiration-ms=86400000"
})
class AuthSessionIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    LabSessionRepository labSessionRepository;

    @Autowired
    ChatMessageRepository chatMessageRepository;

    @Autowired
    AuthService authService;

    @Autowired
    StudentExperimentAssignmentRepository assignmentRepository;

    @BeforeEach
    void cleanDb() {
        chatMessageRepository.deleteAll();
        labSessionRepository.deleteAll();
        assignmentRepository.deleteAll();
        userRepository.deleteAll();
        authService.ensureDefaultUsers();
    }

    @Test
    void registerCreatesUserWithDisplayNameOnly() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "student001",
                                  "password": "secret123",
                                  "displayName": "Student Wang"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("student001"))
                .andExpect(jsonPath("$.displayName").value("Student Wang"))
                .andExpect(jsonPath("$.studentClass").value(""));

        User user = userRepository.findByUsername("student001").orElseThrow();
        assertThat(user.getDisplayName()).isEqualTo("Student Wang");
        assertThat(user.getStudentClass()).isBlank();
    }

    @Test
    void registerAllowsSingleCharacterPassword() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "student002",
                                  "password": "1",
                                  "displayName": "Student Li"
                                }
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void defaultUserCanLogin() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "test01",
                                  "password": "test01"
                                }
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void userCanLoginWithDisplayName() throws Exception {
        saveUser("student003", "secret123", "王同学", "");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "王同学",
                                  "password": "secret123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("student003"))
                .andExpect(jsonPath("$.displayName").value("王同学"));
    }

    @Test
    void resetPasswordAllowsLoginWithNewPassword() throws Exception {
        saveUser("student001", "oldpass1", "王同学", "物理一班");

        mockMvc.perform(patch("/api/auth/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "student001",
                                  "newPassword": "newpass1"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "student001",
                                  "password": "newpass1"
                                }
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void sessionStartRequiresLogin() throws Exception {
        mockMvc.perform(post("/api/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "experimentCode": "newton_rings",
                                  "studentName": "未登录学生",
                                  "studentClass": "物理一班"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loggedInSessionBelongsToCurrentUser() throws Exception {
        saveUser("student001", "secret123", "王同学", "物理一班");
        JsonNode auth = login("student001", "secret123");

        mockMvc.perform(post("/api/sessions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + auth.get("token").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "experimentCode": "newton_rings",
                                  "studentName": "王同学",
                                  "studentClass": "物理一班"
                                }
                                """))
                .andExpect(status().isOk());

        LabSession session = labSessionRepository.findAll().get(0);
        assertThat(session.getUserId()).isEqualTo(auth.get("userId").asLong());
    }

    @Test
    void listSessionsReturnsOnlyCurrentUserConversations() throws Exception {
        saveUser("studentA", "secret123", "Student A", "");
        saveUser("studentB", "secret123", "Student B", "");
        JsonNode authA = login("studentA", "secret123");
        JsonNode authB = login("studentB", "secret123");

        long aNewton = startSession(authA, "newton_rings", "Student A").get("id").asLong();
        long aTensile = startSession(authA, "tensile_steel", "Student A").get("id").asLong();
        startSession(authA, "newton_rings", "Student A");
        long bNewton = startSession(authB, "newton_rings", "Student B").get("id").asLong();

        saveChatMessage(aNewton, "A newton question");
        saveChatMessage(aNewton, "How do I read the rings?");
        saveChatMessage(aTensile, "A tensile question");
        saveChatMessage(bNewton, "B newton question");

        String response = mockMvc.perform(get("/api/sessions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + authA.get("token").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].studentName").value("Student A"))
                .andExpect(jsonPath("$[1].studentName").value("Student A"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode list = objectMapper.readTree(response);
        assertThat(list.findValuesAsText("id"))
                .containsExactlyInAnyOrder(String.valueOf(aNewton), String.valueOf(aTensile));
        assertThat(historyTitleFor(list, aNewton))
                .contains("A newton")
                .contains("How do I");
    }

    @Test
    void latestActiveSessionReturnsUnfinishedSessionForExperiment() throws Exception {
        saveUser("studentA", "secret123", "Student A", "");
        JsonNode auth = login("studentA", "secret123");

        long finishedId = startSession(auth, "newton_rings", "Student A").get("id").asLong();
        mockMvc.perform(post("/api/sessions/{sessionId}/finish", finishedId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + auth.get("token").asText()))
                .andExpect(status().isOk());
        JsonNode active = startSession(auth, "newton_rings", "Student A");

        mockMvc.perform(get("/api/sessions/latest")
                        .queryParam("experimentCode", "newton_rings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + auth.get("token").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(active.get("id").asLong()))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void assistPersistsReadableChatMessages() throws Exception {
        saveUser("studentA", "secret123", "Student A", "");
        JsonNode auth = login("studentA", "secret123");
        long sessionId = startSession(auth, "newton_rings", "Student A").get("id").asLong();

        mockMvc.perform(post("/api/sessions/{sessionId}/assist", sessionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + auth.get("token").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userMessage": "How should I start this step?"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/sessions/{sessionId}/messages", sessionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + auth.get("token").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].role").value("user"))
                .andExpect(jsonPath("$[0].text").value("How should I start this step?"))
                .andExpect(jsonPath("$[1].role").value("ai"));
    }

    private void saveUser(String username, String password, String displayName, String studentClass) {
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setDisplayName(displayName);
        user.setStudentClass(studentClass);
        userRepository.save(user);
        assignExperiments(user.getId(), "newton_rings", "tensile_steel");
    }

    private void assignExperiments(Long userId, String... experimentCodes) {
        for (String code : experimentCodes) {
            StudentExperimentAssignment assignment = new StudentExperimentAssignment();
            assignment.setUserId(userId);
            assignment.setExperimentCode(code);
            assignment.setAssignedByUserId(userId);
            assignmentRepository.save(assignment);
        }
    }

    private JsonNode startSession(JsonNode auth, String experimentCode, String studentName) throws Exception {
        String body = """
                {
                  "experimentCode": "%s",
                  "studentName": "%s",
                  "studentClass": ""
                }
                """.formatted(experimentCode, studentName);
        String json = mockMvc.perform(post("/api/sessions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + auth.get("token").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(json);
    }

    private void saveChatMessage(long sessionId, String text) {
        ChatMessage message = new ChatMessage();
        message.setSessionId(sessionId);
        message.setRole("user");
        message.setStepId(1);
        message.setText(text);
        message.setImageUrl("");
        chatMessageRepository.save(message);
    }

    private String historyTitleFor(JsonNode list, long sessionId) {
        for (JsonNode item : list) {
            if (item.get("id").asLong() == sessionId) {
                return item.get("historyTitle").asText();
            }
        }
        return "";
    }

    private JsonNode login(String username, String password) throws Exception {
        String body = """
                {
                  "username": "%s",
                  "password": "%s"
                }
                """.formatted(username, password);
        String json = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(json);
    }
}
