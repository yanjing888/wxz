package com.wuxiaozhi.controller;
import com.fasterxml.jackson.databind.*;
import com.wuxiaozhi.entity.*;
import com.wuxiaozhi.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
@TestPropertySource(properties={"spring.datasource.url=jdbc:h2:mem:admin;DB_CLOSE_DELAY=-1;MODE=MySQL","spring.jpa.hibernate.ddl-auto=create-drop","wuxiaozhi.jwt.secret=admin-integration-test-secret-32-bytes-long","WXZ_ADMIN_USERNAME=admin","WXZ_ADMIN_PASSWORD=admin","wuxiaozhi.workstations.scheduling-enabled=false"})
class AdminIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired AdminAuditLogRepository audit;
    String login(String username,String password,int expected) throws Exception {return call("POST","/api/auth/login",null,Map.of("username",username,"password",password),expected).path("token").asText();}
    JsonNode call(String method,String path,String token,Object body,int expected) throws Exception {
        var req=switch(method){case "POST"->post(path);case "PUT"->put(path);case "PATCH"->patch(path);default->get(path);};
        req.contentType(MediaType.APPLICATION_JSON);if(token!=null)req.header("Authorization","Bearer "+token);if(body!=null)req.content(json.writeValueAsString(body));
        String raw=mvc.perform(req).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);return raw.isBlank()?json.nullNode():json.readTree(raw);
    }
    Map<String,Object> student(String name){return new LinkedHashMap<>(Map.of("username",name,"displayName","测试学生","role","STUDENT","studentClass","物理一班","classes",List.of("物理二班"),"enabled",true,"password","initial"));}
    @Test void administratorsOwnAccountsPasswordsAndDeviceRegistry() throws Exception {
        String admin=login("admin","admin",200);assertThat(call("GET","/api/auth/me",admin,null,200).get("role").asText()).isEqualTo("ADMIN");
        String teacher=login("teacher01","teacher01",200),studentToken=login("test01","test01",200);
        call("GET","/api/admin/users",null,null,401);call("GET","/api/admin/users",teacher,null,403);call("GET","/api/admin/users",studentToken,null,403);
        call("POST","/api/auth/register",null,Map.of("username","forged-admin","password","secret123","displayName","伪造","role","ADMIN"),403);
        assertThat(users.findByUsername("forged-admin")).isEmpty();
        var data=student("managed-"+UUID.randomUUID().toString().substring(0,8));
        Long id=call("POST","/api/admin/users",admin,data,200).get("id").asLong();
        assertThat(call("GET","/api/admin/users?className=物理二班",admin,null,200).get("users").findValuesAsText("username")).contains((String)data.get("username"));
        String oldToken=login((String)data.get("username"),"initial",200);
        call("PATCH","/api/auth/password",null,Map.of("username",data.get("username"),"newPassword","hijack"),401);
        call("PATCH","/api/admin/users/"+id+"/password",teacher,Map.of("password","hijack"),403);
        call("PATCH","/api/admin/users/"+id+"/password",admin,Map.of("password","next-password"),200);
        call("GET","/api/auth/me",oldToken,null,401);
        String newToken=login((String)data.get("username"),"next-password",200);
        data.remove("password");data.put("enabled",false);call("PUT","/api/admin/users/"+id,admin,data,200);
        call("GET","/api/auth/me",newToken,null,401);login((String)data.get("username"),"next-password",403);
        assertThat(audit.findTop100ByOrderByCreatedAtDesc().stream().map(AdminAuditLog::getSummary)).noneMatch(s->s.contains("next-password")||s.contains("initial"));
        data.put("enabled",true);call("PUT","/api/admin/users/"+id,admin,data,200);
        String studentLogin=login((String)data.get("username"),"next-password",200);
        data.put("role","TEACHER");data.put("classes",List.of());
        call("PUT","/api/admin/users/"+id,admin,data,200);
        call("GET","/api/auth/me",studentLogin,null,401);
        String teacherLogin=login((String)data.get("username"),"next-password",200);
        assertThat(call("GET","/api/auth/me",teacherLogin,null,200).get("role").asText()).isEqualTo("TEACHER");
        call("GET","/api/admin/users",teacherLogin,null,403);
        Long adminId=users.findByUsername("admin").orElseThrow().getId();
        call("PUT","/api/admin/users/"+adminId,admin,Map.of("username","admin","displayName","管理员","role","ADMIN","enabled",false),409);
        var station=Map.of("code","unconfigured","name","未接摄像头工位","capacity",1,"enabled",true,"cameraType","NETWORK");
        call("POST","/api/admin/workstations?experimentCode=newton_rings",teacher,station,403);
        Long stationId=call("POST","/api/admin/workstations?experimentCode=newton_rings",admin,station,200).get("id").asLong();
        call("GET","/api/workstations/"+stationId+"/frame",studentToken,null,403);
        var teacherDashboard=call("GET","/api/teacher/workstations?experimentCode=newton_rings",teacher,null,200);
        assertThat(teacherDashboard.get("canManageDevices").asBoolean()).isFalse();
        assertThat(call("GET","/api/admin/workstations?experimentCode=newton_rings",admin,null,200).get("canManageDevices").asBoolean()).isTrue();
        String code=call("POST","/api/admin/workstations/"+stationId+"/pair-code",admin,null,200).get("code").asText();
        call("POST","/api/admin/workstations/pair",studentToken,Map.of("code",code),403);
        assertThat(call("POST","/api/admin/workstations/pair",admin,Map.of("code",code),200).get("terminalToken").asText()).isNotBlank();
        call("PUT","/api/admin/workstations/room?experimentCode=newton_rings",admin,Map.of("name","物理楼 301 牛顿环实验室"),200);
    }
}
