package com.wuxiaozhi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wuxiaozhi.entity.*;
import com.wuxiaozhi.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SimulationResultServiceTest {
    final ObjectMapper mapper = new ObjectMapper();
    final StudentExperimentProgressRepository progress = mock(StudentExperimentProgressRepository.class);
    final UserRepository users = mock(UserRepository.class);
    final StudentExperimentAccessService access = mock(StudentExperimentAccessService.class);
    final ClassRosterService roster = mock(ClassRosterService.class);
    final SimulationResultService service = new SimulationResultService(progress, users, access, roster, mapper);

    ObjectNode submission() throws Exception {
        return (ObjectNode) mapper.readTree("""
          {"version":3,"revision":1000,"startedAt":500,"guidePassed":true,"quizDone":true,"quizConfirmed":true,
           "installed":["sample","objective","mirror","lamp","eyepiece","camera"],
           "answers":[{"id":"orientation","selected":[1]},{"id":"beams","selected":[0]},
           {"id":"focus","selected":[0,1,2]},{"id":"exposure","selected":[1]},
           {"id":"illumination","selected":[0,1,2]},{"id":"drum","selected":[1]},
           {"id":"dark","selected":[2]},{"id":"operation","selected":[0,1,2]}],
           "challenge":{"durationSeconds":30,"edits":12,"optics":{"lightOn":true,"cameraOn":true,
           "videoConnected":true,"monitorOn":true,"lampPower":100,"lampPosition":0,"mirrorAngle":45,
           "reticle":50,"focus":50,"center":0,"baseMirrorFolded":true,"cameraFocus":50,"aperture":70,
           "exposure":100,"reading":25,"ringOrigin":25,"focusTarget":50,"reticleTarget":50,"cameraFocusTarget":50}}}
          """);
    }
    @Test void rejectsUnconfirmedQuizAndOnlyGeneratesReportAfterChallenge() throws Exception {
        var input = submission();
        input.put("quizConfirmed", false);
        assertThrows(ResponseStatusException.class, () -> service.normalize(input));
        input.put("quizConfirmed", true);
        assertTrue(service.normalize(input).path("reportReady").asBoolean());
        input.putNull("challenge");
        assertFalse(service.normalize(input).path("reportReady").asBoolean());
    }
    @Test void recomputesScoresFromAnswersAndOptics() throws Exception {
        var input = submission();
        assertEquals(100, service.normalize(input).path("totalScore").asInt());
        ((ObjectNode) input.path("answers").get(0)).putArray("selected").add(0);
        ((ObjectNode) input.path("challenge").path("optics")).put("cameraOn", false);
        input.put("totalScore",100); input.put("passed",true);
        var actual = service.normalize(input);
        assertEquals(35,actual.path("knowledgeScore").asInt());
        assertEquals(50,actual.path("challengeScore").asInt());
        assertFalse(actual.path("passed").asBoolean());
    }
    @Test void quizOnlyHasNoInventedTotalAndRejectsDuplicates() throws Exception {
        var input=submission(); input.putNull("challenge");
        assertTrue(service.normalize(input).path("totalScore").isNull());
        ((ObjectNode)input.path("answers").get(1)).put("id","orientation");
        assertThrows(ResponseStatusException.class,()->service.normalize(input));
    }
    @Test void savesFailedResultsAndIgnoresLateRetry() throws Exception {
        var user=new User(); user.setId(1L);
        var p=new StudentExperimentProgress(); p.setUserId(1L); p.setExperimentCode("newton_rings");
        when(users.lockById(1L)).thenReturn(Optional.of(user));
        when(progress.findByUserIdAndExperimentCode(1L,"newton_rings")).thenReturn(Optional.of(p));
        when(access.assignmentClass(1L,"newton_rings")).thenReturn("A班");
        var input=submission(); input.putNull("challenge");
        service.save(1L,"newton_rings",input);
        assertNotNull(p.getSimulationResultJson()); assertFalse(p.isSimulationCompleted());
        service.save(1L,"newton_rings",input);
        verify(progress,times(1)).save(p);
        input.put("revision",2000); input.set("challenge",submission().get("challenge"));
        service.save(1L,"newton_rings",input);
        assertTrue(p.isSimulationCompleted());
    }
    @Test void multiClassStudentCannotLeakOtherClassResult() throws Exception {
        User teacher=new User(), student=new User(); student.setId(1L);
        when(roster.scope(teacher,null)).thenReturn("A班");
        when(roster.students(teacher,"A班")).thenReturn(List.of(student));
        var p=new StudentExperimentProgress();p.setUserId(1L);
        var saved=service.normalize(submission());saved.put("studentClass","B班");p.setSimulationResultJson(saved.toString());
        when(progress.findByExperimentCodeAndUserIdIn("newton_rings",List.of(1L))).thenReturn(List.of(p));
        assertTrue(service.list(teacher,"newton_rings",null).isEmpty());
        assertThrows(ResponseStatusException.class,()->service.detail(teacher,"newton_rings",1L));
    }
}
