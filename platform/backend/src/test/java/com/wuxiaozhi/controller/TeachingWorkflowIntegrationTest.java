package com.wuxiaozhi.controller;

import com.wuxiaozhi.support.MySqlIntegrationTestSupport;

import com.fasterxml.jackson.databind.*;
import com.wuxiaozhi.entity.*;
import com.wuxiaozhi.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.*;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
@TestPropertySource(properties={"wuxiaozhi.test.database-id=teaching-workflow","spring.jpa.hibernate.ddl-auto=create-drop","wuxiaozhi.jwt.secret=teaching-workflow-test-secret-long-enough-for-hs256"})
class TeachingWorkflowIntegrationTest extends MySqlIntegrationTestSupport {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired LabSessionRepository sessions;
    @Autowired StudentExperimentAssignmentRepository assignments;
    @Autowired StudentExperimentProgressRepository progress;
    @Autowired StudentClassMembershipRepository memberships;
    String token(String username) throws Exception {
        return json.readTree(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("username",username,"password","test-secret")))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("token").asText();
    }
    User teacher(String name,String cls){User t=new User();t.setUsername(name);t.setDisplayName(name);t.setPasswordHash(encoder.encode("test-secret"));t.setRole("TEACHER");t.setStudentClass(cls);return users.save(t);}
    JsonNode call(String method,String path,String token,Object body,int status) throws Exception {
        var request=switch(method){case "POST"->post(path);case "PUT"->put(path);case "DELETE"->delete(path);case "PATCH"->patch(path);default->get(path);};
        request.header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON);
        if(body!=null)request.content(json.writeValueAsString(body));
        var result=mvc.perform(request).andExpect(status().is(status)).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);return result.isBlank()?json.nullNode():json.readTree(result);
    }
    @Test void oneAccountTwoClassesOneCurrentTaskAndIndependentReports() throws Exception {
        teacher("workflow-admin","");teacher("workflow-teacher-b","实验二班");
        String admin=token("workflow-admin"),teacherB=token("workflow-teacher-b");
        for(String cls:List.of("实验一班","实验二班"))call("POST","/api/teacher/students/import",admin,Map.of("className",cls,"defaultPassword","test-secret","students",List.of(Map.of("username","workflow-student","displayName","测试同学","studentClass",cls))),200);
        User student=users.findByUsername("workflow-student").orElseThrow();
        assertThat(student.getStudentClass()).isEqualTo("实验一班");
        var roster=call("GET","/api/teacher/students",admin,null,200);
        assertThat(roster.findValuesAsText("username").stream().filter("workflow-student"::equals)).hasSize(2);
        call("POST","/api/teacher/students/assignments/bulk",admin,Map.of("userIds",List.of(student.getId()),"experimentCodes",List.of("newton_rings"),"className","实验一班","mode","append","instructions","测量两次并说明误差"),200);
        call("POST","/api/teacher/students/assignments/bulk",admin,Map.of("userIds",List.of(student.getId()),"experimentCodes",List.of("air_wedge_thickness"),"className","实验二班","mode","append"),200);
        call("POST","/api/teacher/students/assignments/bulk",admin,Map.of("userIds",List.of(student.getId()),"experimentCodes",List.of("newton_rings"),"className","实验二班","mode","append"),409);
        String login=token("workflow-student");
        var queue=call("GET","/api/student/experiments/progress",login,null,200);
        assertThat(queue.findValuesAsText("taskState")).containsExactly("CURRENT","QUEUED");
        call("POST","/api/sessions",login,Map.of("experimentCode","air_wedge_thickness","studentName","测试同学","studentClass","伪造班级"),409);
        call("POST","/api/student/experiments/air_wedge_thickness/progress/simulation",login,null,409);
        var lab=call("POST","/api/sessions",login,Map.of("experimentCode","newton_rings","studentName","测试同学","studentClass","伪造班级"),200);
        Long id=lab.get("id").asLong();assertThat(lab.get("studentClass").asText()).isEqualTo("实验一班");
        call("POST","/api/student/experiments/newton_rings/progress/report",login,Map.of("sessionId",id,"sections",Map.of("results","未结束实操的报告")),409);
        completeProcessFixture(id);
        call("POST","/api/sessions/"+id+"/finish",login,null,200);
        queue=call("GET","/api/student/experiments/progress",login,null,200);assertThat(queue.findValuesAsText("taskState")).containsExactly("REPORT_PENDING","CURRENT");
        call("PUT","/api/student/experiments/newton_rings/progress/report/draft",login,Map.of("sessionId",id,"sections",Map.of("results","来自实验室的草稿")),200);
        var restored=call("GET","/api/student/experiments/newton_rings/progress",login,null,200);assertThat(restored.at("/reportSections/results").asText()).isEqualTo("来自实验室的草稿");assertThat(restored.get("reportCompleted").asBoolean()).isFalse();
        assertThat(call("GET","/api/teacher/reports?experimentCode=newton_rings&className=实验一班",admin,null,200).size()).isZero();
        call("POST","/api/student/experiments/newton_rings/progress/report",login,Map.of("sessionId",id,"sections",Map.of("results","正式提交的报告")),200);
        call("GET","/api/teacher/reports/"+id,teacherB,null,403);
        var submitted=call("GET","/api/teacher/reports?experimentCode=newton_rings&className=实验一班",admin,null,200);assertThat(submitted.size()).isEqualTo(1);
        var graded = call("PATCH","/api/teacher/reports/"+id+"/grade",admin,Map.of("score",90.0,"comment","数据完整"),200);
        assertThat(graded.get("teacherScore").asDouble()).isEqualTo(90.0);
        // Retried submissions and document exports must not erase a teacher's grade.
        call("POST","/api/student/experiments/newton_rings/progress/report",login,Map.of("sessionId",id,"sections",Map.of("results","不得覆盖")),200);
        restored=call("GET","/api/student/experiments/newton_rings/progress",login,null,200);assertThat(restored.get("gradingCompleted").asBoolean()).isTrue();assertThat(restored.at("/reportSections/results").asText()).isEqualTo("正式提交的报告");
        call("PUT","/api/student/experiments/newton_rings/progress/report/draft",login,Map.of("sessionId",id,"sections",Map.of("results","不得覆盖")),409);
        call("DELETE","/api/teacher/students/"+student.getId()+"/experiments/newton_rings",admin,null,409);
        var second=call("POST","/api/sessions",login,Map.of("experimentCode","air_wedge_thickness","studentName","测试同学","studentClass","实验一班"),200);
        Long secondId=second.get("id").asLong();assertThat(second.get("studentClass").asText()).isEqualTo("实验二班");
        completeProcessFixture(secondId);
        call("POST","/api/sessions/"+secondId+"/finish",login,null,200);
        call("POST","/api/student/experiments/air_wedge_thickness/progress/report",login,Map.of("sessionId",id,"sections",Map.of("results","串实验的报告")),409);
        call("POST","/api/student/experiments/air_wedge_thickness/progress/report",login,Map.of("sessionId",secondId,"sections",Map.of("results","第二份报告")),200);
        assertThat(call("GET","/api/teacher/reports",admin,null,200).size()).isEqualTo(2);
        assertThat(call("GET","/api/teacher/reports?className=实验二班",teacherB,null,200).size()).isEqualTo(1);
        assertThat(call("GET","/api/teacher/classroom?experimentCode=newton_rings&className=实验二班",admin,null,200).get("students").size()).isZero();
        // Legacy 9/10 is presented as 90/100 without rewriting its stored value.
        var savedProgress = progress.findByUserIdAndExperimentCode(student.getId(), "newton_rings").orElseThrow();
        savedProgress.setTeacherScore(9.0);savedProgress.setTeacherScoreMax(10.0);progress.save(savedProgress);
        assertThat(call("GET", "/api/teacher/reports/" + id, admin, null, 200).get("teacherScore").asDouble()).isEqualTo(90.0);
        assertThat(call("GET", "/api/student/experiments/newton_rings/progress", login, null, 200).get("teacherScore").asDouble()).isEqualTo(90.0);
        assertThat(progress.findById(savedProgress.getId()).orElseThrow().getTeacherScore()).isEqualTo(9.0);
        // A new low score really means 5/100, not 5/10.
        assertThat(call("PATCH", "/api/teacher/reports/" + id + "/grade", admin, Map.of("score", 5.0), 200).get("teacherScore").asDouble()).isEqualTo(5.0);
        assertThat(progress.findById(savedProgress.getId()).orElseThrow().getTeacherScoreMax()).isEqualTo(100.0);
        call("PATCH", "/api/teacher/reports/" + id + "/grade", admin, Map.of("score", 101.0), 400);
        call("PATCH", "/api/teacher/reports/" + id + "/grade", admin, Map.of("score", -1.0), 400);
    }

    @Test void legacyMissingClassesAreGroupedWithoutChangingSavedHistory() throws Exception {
        teacher("legacy-admin", "");
        teacher("legacy-teacher-a", "兼容一班");
        teacher("legacy-teacher-b", "兼容二班");
        String admin = token("legacy-admin"), teacherA = token("legacy-teacher-a"), teacherB = token("legacy-teacher-b");
        User student = new User();
        student.setUsername("legacy-student");student.setDisplayName("旧记录同学");student.setStudentClass("兼容一班");
        student.setPasswordHash(encoder.encode("test-secret"));student.setRole("STUDENT");student = users.save(student);
        var assignment = new StudentExperimentAssignment();
        assignment.setUserId(student.getId());assignment.setExperimentCode("newton_rings");
        assignment.setAssignedByUserId(users.findByUsername("legacy-admin").orElseThrow().getId());assignments.save(assignment);
        LabSession blank = new LabSession();
        blank.setUserId(student.getId());blank.setStudentName(student.getDisplayName());blank.setExperimentCode("newton_rings");
        blank.setExperimentName("牛顿环");blank.setStatus("FINISHED");blank = sessions.save(blank);
        LabSession named = new LabSession();
        named.setUserId(student.getId());named.setStudentName(student.getDisplayName());named.setExperimentCode("newton_rings");
        named.setExperimentName("牛顿环");named.setStudentClass("兼容一班");named = sessions.save(named);
        StudentExperimentProgress submitted = new StudentExperimentProgress();
        submitted.setUserId(student.getId());submitted.setExperimentCode("newton_rings");submitted.setReportCompleted(true);
        submitted.setReportSessionId(blank.getId());submitted.setReportSectionsJson("{\"results\":\"旧报告\"}");progress.save(submitted);
        long userId = student.getId();
        var rows = call("GET", "/api/teacher/classroom?experimentCode=newton_rings", admin, null, 200).get("students");
        var ownRows = java.util.stream.StreamSupport.stream(rows.spliterator(), false).filter(row -> row.get("userId").asLong() == userId).toList();
        assertThat(ownRows).hasSize(1);
        assertThat(ownRows.get(0).get("studentClass").asText()).isEqualTo("兼容一班");
        var reports = call("GET", "/api/teacher/reports?experimentCode=newton_rings&className=兼容一班", teacherA, null, 200);
        assertThat(reports.findValuesAsText("studentClass")).contains("兼容一班");
        assertThat(call("GET", "/api/teacher/reports/" + blank.getId(), teacherA, null, 200).get("studentClass").asText()).isEqualTo("兼容一班");
        call("GET", "/api/teacher/reports/" + blank.getId(), teacherB, null, 403);
        assertThat(sessions.findById(blank.getId()).orElseThrow().getStudentClass()).isNull();

        // An explicit historical class must not be replaced by today's account class.
        named.setStudentClass("兼容二班");sessions.save(named);
        rows = call("GET", "/api/teacher/classroom?experimentCode=newton_rings", admin, null, 200).get("students");
        ownRows = java.util.stream.StreamSupport.stream(rows.spliterator(), false).filter(row -> row.get("userId").asLong() == userId).toList();
        assertThat(ownRows.stream().map(row -> row.get("studentClass").asText())).containsExactlyInAnyOrder("兼容一班", "兼容二班");

        // Multiple memberships with no saved assignment class are ambiguous.
        StudentClassMembership secondClass = new StudentClassMembership();
        secondClass.setUserId(userId);secondClass.setClassName("兼容二班");memberships.save(secondClass);
        assignments.delete(assignment);
        rows = call("GET", "/api/teacher/classroom?experimentCode=newton_rings", admin, null, 200).get("students");
        ownRows = java.util.stream.StreamSupport.stream(rows.spliterator(), false).filter(row -> row.get("userId").asLong() == userId).toList();
        assertThat(ownRows.stream().map(row -> row.get("studentClass").asText())).contains("未分班");
        call("GET", "/api/teacher/reports/" + blank.getId(), teacherA, null, 403);
    }
}
