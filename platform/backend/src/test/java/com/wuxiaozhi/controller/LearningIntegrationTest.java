package com.wuxiaozhi.controller;

import com.wuxiaozhi.support.MySqlIntegrationTestSupport;

import com.fasterxml.jackson.databind.*;
import com.wuxiaozhi.entity.*;
import com.wuxiaozhi.repository.*;
import com.wuxiaozhi.service.DifyService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @Transactional
@TestPropertySource(properties={"wuxiaozhi.test.database-id=learning","spring.jpa.hibernate.ddl-auto=create-drop","wuxiaozhi.jwt.secret=learning-test-secret-long-enough-for-hs256","wuxiaozhi.workstations.scheduling-enabled=false","wuxiaozhi.lab.bootstrap-enabled=false","logging.level.root=WARN","logging.level.org.springframework=WARN","logging.level.org.hibernate=WARN"})
class LearningIntegrationTest extends MySqlIntegrationTestSupport {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired StudentExperimentAssignmentRepository assignments;
    @Autowired StudentExperimentProgressRepository progress;
    @Autowired LabSessionRepository sessions;
    @Autowired SessionDataLogRepository data;
    @Autowired StudentClassMembershipRepository memberships;
    @MockBean DifyService dify;
    User pupil,peer,outsider,teacher;
    String studentToken,teacherToken,otherToken,cls,otherClass;
    final String code="newton_rings";
    @BeforeEach void setup() throws Exception {
        String tag=UUID.randomUUID().toString().substring(0,8);cls="学习班"+tag;otherClass="其他班"+tag;
        pupil=user("learn-a-"+tag,cls,UserRole.STUDENT);peer=user("learn-b-"+tag,cls,UserRole.STUDENT);outsider=user("learn-c-"+tag,otherClass,UserRole.STUDENT);teacher=user("learn-t-"+tag,cls,UserRole.TEACHER);
        studentToken=token(pupil);teacherToken=token(teacher);otherToken=token(outsider);
        assign(pupil,code,cls);assign(peer,code,cls);assign(outsider,code,otherClass);
    }
    User user(String name,String className,String role){var user=new User();user.setUsername(name);user.setDisplayName(name);user.setStudentClass(className);user.setRole(role);user.setPasswordHash(encoder.encode("learning-test"));return users.save(user);}
    void assign(User user,String experiment,String className){var a=new StudentExperimentAssignment();a.setUserId(user.getId());a.setAssignedByUserId(teacher.getId());a.setExperimentCode(experiment);a.setClassName(className);assignments.save(a);}
    String token(User user) throws Exception {return call("POST","/api/auth/login",null,Map.of("username",user.getUsername(),"password","learning-test"),200).get("token").asText();}
    JsonNode call(String method,String path,String token,Object body,int status) throws Exception {
        var req=switch(method){case "POST"->post(path);case "PUT"->put(path);default->get(path);};if(token!=null)req.header("Authorization","Bearer "+token);req.contentType(MediaType.APPLICATION_JSON);if(body!=null)req.content(json.writeValueAsString(body));
        String raw=mvc.perform(req).andExpect(status().is(status)).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);return raw.isBlank()?json.nullNode():json.readTree(raw);
    }
    LabSession finish(User user,String experiment,String className){var s=new LabSession();s.setUserId(user.getId());s.setExperimentCode(experiment);s.setExperimentName(experiment);s.setStudentClass(className);s.setStudentName(user.getDisplayName());s.setStatus("FINISHED");s.setEndTime(LocalDateTime.now());return sessions.save(s);}
    StudentExperimentProgress grade(User user,LabSession session){var p=new StudentExperimentProgress();p.setUserId(user.getId());p.setExperimentCode(code);p.setPreLabCompleted(true);p.setSimulationCompleted(true);p.setReportCompleted(true);p.setReportSessionId(session.getId());p.setGradingCompleted(true);p.setTeacherScore(8.5);p.setTeacherScoreMax(10.0);p.setTeacherComment("请补充误差分析");p.setGradingAt(LocalDateTime.now());p.setAiReviewScore(99.0);p.setAiReviewScoreMax(100.0);return progress.save(p);}
    void log(LabSession session,int step,Boolean official,String validation){var d=new SessionDataLog();d.setSessionId(session.getId());d.setStepId(step);d.setValuesJson("{\"x\":1}");d.setValidationJson(validation);d.setOfficialData(official);data.save(d);}
    @Test void realEvidenceDeduplicatedPointsAndReflectionRevision() throws Exception {
        var session=finish(pupil,code,cls);var p=grade(pupil,session);
        finish(pupil,code,cls); // Repeating the same experiment does not double its completion award.
        log(session,1,true,"{\"ok\":false}");log(session,1,true,"{\"ok\":true}");log(session,2,false,"{\"ok\":false}");log(session,3,true,null);
        var dashboard=call("GET","/api/student/learning",studentToken,null,200);var summary=dashboard.get("summary");var record=summary.get("records").get(0);
        assertThat(summary.get("totalPoints").asInt()).isEqualTo(86);assertThat(summary.get("finishedCount").asInt()).isEqualTo(1);assertThat(summary.get("averageScore").asDouble()).isEqualTo(85);
        assertThat(summary.get("dimensions").get(2).get("value").asDouble()).isEqualTo(100);
        assertThat(dashboard.get("leaderboard")).hasSize(2);assertThat(dashboard.toString()).doesNotContain(outsider.getUsername());
        var request=Map.of("content","补充读数与回程误差分析，并说明改进方法。","feedbackRevision",record.get("feedbackRevision").asText());
        call("PUT","/api/student/learning/"+code+"/reflection",studentToken,request,200);
        call("PUT","/api/student/learning/"+code+"/reflection",studentToken,request,200);
        var updated=call("GET","/api/student/learning",studentToken,null,200).get("summary");assertThat(updated.get("totalPoints").asInt()).isEqualTo(96);assertThat(updated.get("records").get(0).get("reflectionCurrent").asBoolean()).isTrue();
        var detail=call("GET","/api/teacher/learning/students/"+pupil.getId()+"?className="+cls,teacherToken,null,200);assertThat(detail.get("records").get(0).get("reflection").asText()).contains("误差分析");
        p.setTeacherScore(7.0);p.setTeacherComment("请重新检查数据");p.setGradingAt(LocalDateTime.now());progress.saveAndFlush(p);
        call("PUT","/api/student/learning/"+code+"/reflection",studentToken,request,409);
        var changed=call("GET","/api/student/learning",studentToken,null,200).get("summary");assertThat(changed.get("totalPoints").asInt()).isEqualTo(81);assertThat(changed.get("records").get(0).get("reflectionCurrent").asBoolean()).isFalse();
    }
    @Test void classScopeRolesAndMissingEvidenceAreEnforced() throws Exception {
        call("GET","/api/student/learning",null,null,401);call("GET","/api/student/learning",teacherToken,null,403);call("GET","/api/teacher/learning",studentToken,null,403);
        call("GET","/api/student/learning?className="+otherClass,studentToken,null,403);call("GET","/api/teacher/learning?className="+otherClass,teacherToken,null,403);
        call("GET","/api/teacher/learning/students/"+outsider.getId()+"?className="+cls,teacherToken,null,403);
        call("PUT","/api/student/learning/"+code+"/reflection",studentToken,Map.of("content","订正","feedbackRevision","missing"),409);
        var summary=call("GET","/api/student/learning",studentToken,null,200).get("summary");assertThat(summary.get("totalPoints").asInt()).isZero();assertThat(summary.get("averageScore").isNull()).isTrue();assertThat(summary.get("dimensions").get(2).get("value").isNull()).isTrue();
        var p=new StudentExperimentProgress();p.setUserId(pupil.getId());p.setExperimentCode(code);p.setTeacherScore(99.0);p.setAiReviewScore(100.0);p.setTeacherComment("未发布评语");progress.save(p);
        var empty=call("GET","/api/student/learning",studentToken,null,200);assertThat(empty.toString()).doesNotContain("未发布评语");assertThat(empty.get("summary").get("averageScore").isNull()).isTrue();assertThat(empty.get("selfRank").asInt()).isZero();
        var noAssignments=user("empty-"+UUID.randomUUID(),cls,UserRole.STUDENT);var emptyDashboard=call("GET","/api/student/learning",token(noAssignments),null,200);assertThat(emptyDashboard.get("summary").get("records")).isEmpty();assertThat(emptyDashboard.get("summary").get("dimensions").get(0).get("value").isNull()).isTrue();
    }
    @Test void tiesAndClassSnapshotsStayConsistent() throws Exception {
        grade(pupil,finish(pupil,code,cls));grade(peer,finish(peer,code,cls));
        var membership=new StudentClassMembership();membership.setUserId(pupil.getId());membership.setClassName(otherClass);membership.setAddedByUserId(teacher.getId());memberships.save(membership);
        assign(pupil,"air_wedge_thickness",otherClass);finish(pupil,"air_wedge_thickness",otherClass);
        var board=call("GET","/api/student/learning",studentToken,null,200).get("leaderboard");assertThat(board.get(0).get("rank").asInt()).isEqualTo(1);assertThat(board.get(1).get("rank").asInt()).isEqualTo(1);
        var teacherDetail=call("GET","/api/teacher/learning/students/"+pupil.getId()+"?className="+cls,teacherToken,null,200);assertThat(teacherDetail.get("records")).hasSize(1);assertThat(teacherDetail.get("totalPoints").asInt()).isEqualTo(76);
        var own=call("GET","/api/student/learning",studentToken,null,200);assertThat(own.get("summary").get("records")).hasSize(2);assertThat(own.get("summary").get("totalPoints").asInt()).isEqualTo(96);
        assertThat(board.get(1).get("username").asText()).contains("***");
        var report=call("GET","/api/teacher/learning",teacherToken,null,200);assertThat(report.get("studentCount").asInt()).isEqualTo(2);assertThat(report.get("leaderboard").get(0).get("username").asText()).doesNotContain("***");
    }
    @Test void legacyClassFallbackAndReportsFromAnotherClassRemainSeparated() throws Exception {
        var legacy=finish(peer,code,null);grade(peer,legacy);
        var legacyDetail=call("GET","/api/teacher/learning/students/"+peer.getId()+"?className="+cls,teacherToken,null,200);assertThat(legacyDetail.get("gradedCount").asInt()).isEqualTo(1);
        var outside=finish(pupil,code,otherClass);var p=grade(pupil,outside);p.setTeacherComment("其他班报告评语");progress.save(p);
        finish(pupil,code,cls);
        var scoped=call("GET","/api/teacher/learning/students/"+pupil.getId()+"?className="+cls,teacherToken,null,200);
        assertThat(scoped.get("gradedCount").asInt()).isZero();assertThat(scoped.toString()).doesNotContain("其他班报告评语");assertThat(scoped.get("records").get(0).get("submitted").asBoolean()).isFalse();
    }
    @Test void teacherStatisticsAndDetailsUseTheSelectedExperiment() throws Exception {
        grade(pupil,finish(pupil,code,cls));
        assign(pupil,"air_wedge_thickness",cls);finish(pupil,"air_wedge_thickness",cls);
        var newton=call("GET","/api/teacher/learning?className="+cls+"&experimentCode="+code,teacherToken,null,200);
        assertThat(newton.get("studentCount").asInt()).isEqualTo(2);assertThat(newton.get("leaderboard").get(0).get("points").asInt()).isEqualTo(76);
        var air=call("GET","/api/teacher/learning?className="+cls+"&experimentCode=air_wedge_thickness",teacherToken,null,200);
        assertThat(air.get("studentCount").asInt()).isEqualTo(1);assertThat(air.get("leaderboard").get(0).get("points").asInt()).isEqualTo(20);
        var detail=call("GET","/api/teacher/learning/students/"+pupil.getId()+"?className="+cls+"&experimentCode=air_wedge_thickness",teacherToken,null,200);
        assertThat(detail.get("records")).hasSize(1);assertThat(detail.get("records").get(0).get("code").asText()).isEqualTo("air_wedge_thickness");assertThat(detail.get("averageScore").isNull()).isTrue();
        var personal=call("GET","/api/student/learning",studentToken,null,200);assertThat(personal.get("summary").get("totalPoints").asInt()).isEqualTo(96);
        call("GET","/api/teacher/learning?experimentCode=does-not-exist",teacherToken,null,400);
        call("GET","/api/teacher/learning?className="+otherClass+"&experimentCode="+code,teacherToken,null,403);
    }
}
