package com.wuxiaozhi.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuxiaozhi.entity.LabSession;
import com.wuxiaozhi.entity.User;
import com.wuxiaozhi.repository.LabSessionRepository;
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
    AuthService authService;

    @BeforeEach
    void cleanDb() {
        labSessionRepository.deleteAll();
        userRepository.deleteAll();
        authService.ensureDefaultUser();
    }

    @Test
    void registerIsNotPublic() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "student001",
                                  "password": "secret123",
                                  "displayName": "王同学",
                                  "studentClass": "物理一班"
                                }
                                """))
                .andExpect(status().isNotFound());
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

    private void saveUser(String username, String password, String displayName, String studentClass) {
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setDisplayName(displayName);
        user.setStudentClass(studentClass);
        userRepository.save(user);
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
