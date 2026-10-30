package com.wuxiaozhi.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wuxiaozhi.entity.*;
import com.wuxiaozhi.repository.*;
import com.wuxiaozhi.security.JwtUtil;
import com.wuxiaozhi.service.ExperimentConfigService;
import com.wuxiaozhi.support.MySqlIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest @AutoConfigureMockMvc
@TestPropertySource(properties = {"wuxiaozhi.test.database-id=experiment-management", "spring.jpa.hibernate.ddl-auto=create-drop", "wuxiaozhi.jwt.secret=experiment-management-isolated-test-secret-long-enough"})
class ExperimentManagementIntegrationTest extends MySqlIntegrationTestSupport {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired ExperimentDefinitionRepository definitions;
    @Autowired ExperimentConfigService configs;
    @Autowired JwtUtil jwt;
    String token(String role) {
        var u = new User(); u.setUsername("management-" + role); u.setDisplayName(role); u.setRole(role); u.setPasswordHash("unused-test-hash");
        users.saveAndFlush(u); return "Bearer " + jwt.generateToken(u.getId(), u.getUsername(), role, u.getAuthVersion());
    }
    @Test void teacherCanPersistTutorialAndUploadMediaButStudentCannotManage() throws Exception {
        String teacher = token("TEACHER"), student = token("STUDENT"), path = "/api/teacher/experiment-management/newton_rings";
        mvc.perform(get(path).header("Authorization", student)).andExpect(status().isForbidden());
        var response = mvc.perform(get(path).header("Authorization", teacher)).andExpect(status().isOk()).andReturn().getResponse();
        var body = (ObjectNode) json.readTree(response.getContentAsByteArray());
        var step = (ObjectNode) body.path("config").path("steps").path("1");
        step.put("imageRequired", false); step.put("tableRequired", true);
        ((ObjectNode) step.path("tut")).put("videoUrl", "/uploads/teaching.mp4");
        mvc.perform(put(path).header("Authorization", student).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body))).andExpect(status().isForbidden());
        mvc.perform(put(path).header("Authorization", teacher).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body))).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(0));
        assertThat(definitions.findById("newton_rings")).isPresent();
        assertThat(configs.getByCode("newton_rings").getSteps().get("1").getImageRequired()).isFalse();
        mvc.perform(get(path).header("Authorization", teacher)).andExpect(status().isOk()).andExpect(jsonPath("$.config.steps.1.tableRequired").value(true));
        mvc.perform(put(path).header("Authorization", teacher).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body))).andExpect(status().isConflict());
        byte[] mp4 = new byte[]{0,0,0,20,'f','t','y','p','i','s','o','m',0,0,0,0,0,0,0,0};
        var file = new MockMultipartFile("file", "demo.mp4", "video/mp4", mp4);
        mvc.perform(multipart("/api/teacher/experiment-management/media").file(file).header("Authorization", student)).andExpect(status().isForbidden());
        var uploaded = mvc.perform(multipart("/api/teacher/experiment-management/media").file(file).header("Authorization", teacher)).andExpect(status().isOk()).andReturn().getResponse();
        String url = json.readTree(uploaded.getContentAsByteArray()).path("url").asText();
        mvc.perform(get(url)).andExpect(status().isOk()).andExpect(content().contentType("video/mp4")).andExpect(content().bytes(mp4));

        var newConfig = json.createObjectNode(); newConfig.put("name", "新建光学实验"); newConfig.put("category", "optics"); newConfig.put("teachingContent", "教师维护的实验目的与原理");
        var newSteps = newConfig.putObject("steps"); newSteps.putObject("1").put("title", "实验准备").put("imageRequired", false);
        var createBody = json.createObjectNode(); createBody.set("config", newConfig);
        mvc.perform(post("/api/teacher/experiment-management").header("Authorization", student).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(createBody))).andExpect(status().isForbidden());
        var createdResponse = mvc.perform(post("/api/teacher/experiment-management").header("Authorization", teacher).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(createBody))).andExpect(status().isOk()).andReturn().getResponse();
        var created = json.readTree(createdResponse.getContentAsByteArray()); String createdCode = created.path("config").path("code").asText();
        assertThat(createdCode).startsWith("exp_"); assertThat(definitions.findById(createdCode)).isPresent();
        mvc.perform(get("/api/teacher/experiment-management/" + createdCode).header("Authorization", teacher)).andExpect(status().isOk()).andExpect(jsonPath("$.teachingGuide").value("教师维护的实验目的与原理"));
        assertThat(configs.listAll()).extracting(com.wuxiaozhi.dto.experiment.ExperimentConfig::getCode).contains(createdCode);
    }
}
