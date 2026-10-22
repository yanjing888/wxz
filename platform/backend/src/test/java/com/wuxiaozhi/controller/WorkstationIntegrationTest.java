package com.wuxiaozhi.controller;

import com.wuxiaozhi.support.MySqlIntegrationTestSupport;

import com.fasterxml.jackson.databind.*;
import com.wuxiaozhi.dto.EnvCheckResponse;
import com.wuxiaozhi.entity.*;
import com.wuxiaozhi.repository.*;
import com.wuxiaozhi.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
@TestPropertySource(properties={"wuxiaozhi.test.database-id=workstations","spring.jpa.hibernate.ddl-auto=create-drop","wuxiaozhi.jwt.secret=workstation-test-secret-long-enough-for-hs256","wuxiaozhi.workstations.scheduling-enabled=false","wuxiaozhi.lab.bootstrap-enabled=false","wuxiaozhi.lab.ip-stream-template=ws://{host}/ws/hdl/hlsram/live0.flv","wuxiaozhi.upload.dir=./.codex-logs/workstation-test-uploads"})
class WorkstationIntegrationTest extends MySqlIntegrationTestSupport {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired LabSessionRepository sessions;
    @Autowired WorkstationUseRepository uses;
    @Autowired EnvCheckLogRepository logs;
    @Autowired WorkstationService workstations;
    @Autowired com.wuxiaozhi.config.LabDeploymentProperties deployment;
    @Autowired LabWorkstationRepository stations;
    @MockBean DifyService dify;
    String admin,student,other,wrongClass,classTeacher;
    User pupil,peer,outsider;
    Long stationId,sessionId,peerSession;
    String terminalKey;
    private static final String CODE="newton_rings";
    @BeforeEach void setup() throws Exception {
        String tag=UUID.randomUUID().toString().substring(0,8);
        var manager=user("admin"+tag,"","ADMIN");admin=token(manager);
        pupil=user("student"+tag,"工位测试一班","STUDENT");student=token(pupil);
        peer=user("peer"+tag,"工位测试一班","STUDENT");other=token(peer);
        outsider=user("outsider"+tag,"工位测试二班","STUDENT");wrongClass=token(outsider);
        classTeacher=token(user("teacher"+tag,"工位测试二班","TEACHER"));
        workstations.closeLesson(manager.getId(),CODE);
        stationId=createStation(tag,"USB","");
        sessionId=session(pupil,CODE,"工位测试一班").getId();peerSession=session(peer,CODE,"工位测试一班").getId();
        call("POST","/api/teacher/workstations/lesson?experimentCode="+CODE,admin,null,Map.of("className","工位测试一班"),200);
        String code=call("POST",adminBase()+"/pair-code",admin,null,null,200).get("code").asText();
        terminalKey=call("POST","/api/admin/workstations/pair",admin,null,Map.of("code",code),200).get("terminalToken").asText();
        call("POST","/api/admin/workstations/pair",admin,null,Map.of("code",code),400);
    }
    User user(String name,String cls,String role){var u=new User();u.setUsername(name);u.setDisplayName(name);u.setStudentClass(cls);u.setRole(role);u.setPasswordHash(encoder.encode("test-secret"));return users.save(u);}
    LabSession session(User u,String exp,String cls){var s=new LabSession();s.setUserId(u.getId());s.setStudentName(u.getDisplayName());s.setStudentClass(cls);s.setExperimentCode(exp);s.setExperimentName(exp);return sessions.save(s);}
    String token(User user) throws Exception{return call("POST","/api/auth/login",null,null,Map.of("username",user.getUsername(),"password","test-secret"),200).get("token").asText();}
    String base(){return "/api/teacher/workstations/"+stationId;}
    String adminBase(){return "/api/admin/workstations/"+stationId;}
    JsonNode call(String method,String path,String token,String terminal,Object body,int expected) throws Exception {
        MockHttpServletRequestBuilder req=switch(method){case "POST"->post(path);case "DELETE"->delete(path);case "PUT"->put(path);case "PATCH"->patch(path);default->get(path);};
        if(token!=null)req.header("Authorization","Bearer "+token);if(terminal!=null)req.header("X-Terminal-Token",terminal);
        req.contentType(MediaType.APPLICATION_JSON);if(body!=null)req.content(json.writeValueAsString(body));
        String raw=mvc.perform(req).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        return raw.isBlank()?json.nullNode():json.readTree(raw);
    }
    Long createStation(String code,String type,String stream) throws Exception {
        return call("POST","/api/admin/workstations?experimentCode="+CODE,admin,null,Map.of("code",code,"name","工位 "+code,"capacity",2,"enabled",true,"cameraCode","cam-"+code,"cameraType",type,"streamUrl",stream),200).get("id").asLong();
    }
    @Test void simpleBindingsNeedNoLessonAndProtectClassScope() throws Exception {
        Long roomId=stations.findById(stationId).orElseThrow().getRoomId();
        var manager=user("binding-admin-"+UUID.randomUUID(),"","ADMIN");
        workstations.closeLesson(manager.getId(),CODE);
        String teacher=token(user("binding-teacher-"+UUID.randomUUID(),"工位测试一班","TEACHER"));
        String path=base()+"/bindings";
        call("PUT",path,student,null,Map.of("className","工位测试一班","studentIds",List.of(pupil.getId())),403);
        call("PUT",path,classTeacher,null,Map.of("className","工位测试一班","studentIds",List.of(pupil.getId())),403);
        call("PUT",path,teacher,null,Map.of("className","工位测试一班","studentIds",List.of(outsider.getId())),400);
        call("PUT",path,teacher,null,Map.of("className","工位测试一班","studentIds",List.of(pupil.getId(),peer.getId())),200);
        var overview=call("GET","/api/teacher/workstations/overview",teacher,null,null,200);
        var row=java.util.stream.StreamSupport.stream(overview.get("stations").spliterator(),false).filter(s->s.get("id").asLong()==stationId).findFirst().orElseThrow();
        assertThat(row.get("members")).hasSize(2);
        assertThat(row.get("bindingCount").asInt()).isEqualTo(2);
        var hidden=call("GET","/api/teacher/workstations/overview",classTeacher,null,null,200);
        var hiddenRow=java.util.stream.StreamSupport.stream(hidden.get("stations").spliterator(),false).filter(s->s.get("id").asLong()==stationId).findFirst().orElseThrow();
        assertThat(hiddenRow.get("members")).isEmpty();assertThat(hiddenRow.get("canBind").asBoolean()).isFalse();
        call("PUT",path,classTeacher,null,Map.of("className","工位测试二班","studentIds",List.of()),403);
        Long second=createStation("binding-"+UUID.randomUUID().toString().substring(0,8),"USB","");
        call("PUT","/api/teacher/workstations/"+second+"/bindings",teacher,null,Map.of("className","工位测试一班","studentIds",List.of(pupil.getId())),409);
        var candidates=call("GET","/api/teacher/workstations/binding-students?roomId="+roomId,teacher,null,null,200);
        assertThat(java.util.stream.StreamSupport.stream(candidates.get("students").spliterator(),false).anyMatch(s->s.get("id").asLong()==outsider.getId())).isFalse();
        call("PUT",path,teacher,null,Map.of("className","工位测试一班","studentIds",List.of(peer.getId())),200);
        call("PUT","/api/teacher/workstations/"+second+"/bindings",teacher,null,Map.of("className","工位测试一班","studentIds",List.of(pupil.getId())),200);
        call("DELETE","/api/teacher/workstations/bindings?roomId="+roomId,classTeacher,null,null,403);
        call("DELETE","/api/teacher/workstations/bindings?roomId="+roomId,teacher,null,null,200);
        call("PUT",path,teacher,null,Map.of("className","工位测试一班","studentIds",List.of(pupil.getId())),200);
        call("PUT",path,teacher,null,Map.of("className","工位测试一班","studentIds",List.of()),200);
    }
    @Test void pairingOccupancyGroupAndFinishKeepIndividualHistory() throws Exception {
        call("POST","/api/workstations/claim",student,null,Map.of("sessionId",sessionId),409);
        Long invalid=session(outsider,CODE,"工位测试二班").getId();
        call("POST","/api/workstations/claim",wrongClass,terminalKey,Map.of("sessionId",invalid),403);
        Long otherExperiment=session(pupil,"tensile","工位测试一班").getId();
        call("POST","/api/workstations/claim",student,terminalKey,Map.of("sessionId",otherExperiment),409);
        call("POST","/api/workstations/claim",student,terminalKey,Map.of("sessionId",peerSession),403);
        var claimed=call("POST","/api/workstations/claim",student,terminalKey,Map.of("sessionId",sessionId),200);
        assertThat(call("POST","/api/workstations/claim",student,terminalKey,Map.of("sessionId",sessionId),200).get("id")).isEqualTo(claimed.get("id"));
        call("POST","/api/workstations/claim",other,terminalKey,Map.of("sessionId",peerSession),409);
        call("POST",adminBase()+"/pair-code",admin,null,null,409);
        call("POST",base()+"/members",admin,null,Map.of("sessionId",peerSession),200);
        Long third=session(user("third"+UUID.randomUUID(),"工位测试一班","STUDENT"),CODE,"工位测试一班").getId();
        call("POST",base()+"/members",admin,null,Map.of("sessionId",third),409);
        assertThat(uses.findByStationIdAndEndedAtIsNull(stationId)).hasSize(2);
        // Browser/teacher refresh does not release students.
        call("GET","/api/workstations/terminal",student,terminalKey,null,200);
        assertThat(uses.findByStationIdAndEndedAtIsNull(stationId)).hasSize(2);
        completeProcessFixture(sessionId);
        call("POST","/api/sessions/"+sessionId+"/finish",student,null,null,200);
        assertThat(uses.findByStationIdAndEndedAtIsNull(stationId)).extracting(WorkstationUse::getUserId).containsExactly(peer.getId());
        call("DELETE","/api/teacher/workstations/lesson?experimentCode="+CODE,admin,null,null,200);
        assertThat(uses.findByStationIdAndEndedAtIsNull(stationId)).isEmpty();
        assertThat(sessions.findById(peerSession).orElseThrow().getStatus()).isEqualTo("ACTIVE");
        assertThat(call("GET",base()+"/history",admin,null,null,200).size()).isEqualTo(2);
    }
    @Test void deviceAndCameraPermissionsAndSingleUseTerminalCredential() throws Exception {
        call("GET","/api/teacher/workstations?experimentCode="+CODE,student,null,null,403);
        call("POST",adminBase()+"/pair-code",classTeacher,null,null,403);
        var scope=call("GET","/api/teacher/workstations?experimentCode="+CODE,classTeacher,null,null,200);
        assertThat(scope.get("otherClassInUse").asBoolean()).isTrue();
        call("GET","/api/workstations/"+stationId+"/frame",classTeacher,null,null,503);
        call("POST",base()+"/members",classTeacher,null,Map.of("sessionId",sessionId),403);
        String code=call("POST",adminBase()+"/pair-code",admin,null,null,200).get("code").asText();
        var replacement=call("POST","/api/admin/workstations/pair",admin,null,Map.of("code",code),200);
        call("GET","/api/workstations/terminal",student,terminalKey,null,409);
        call("GET","/api/workstations/terminal",student,replacement.get("terminalToken").asText(),null,200);
    }
    static byte[] jpeg(int color) throws Exception {
        var image=new BufferedImage(20,20,BufferedImage.TYPE_INT_RGB);for(int y=0;y<20;y++)for(int x=0;x<20;x++)image.setRGB(x,y,color);
        var out=new ByteArrayOutputStream();ImageIO.write(image,"jpg",out);return out.toByteArray();
    }
    @Test void usbStreamsAreIsolatedAndInspectionRetainsCaptureTimeStudent() throws Exception {
        call("POST","/api/workstations/claim",student,terminalKey,Map.of("sessionId",sessionId),200);
        String key=call("POST",adminBase()+"/publisher-key",admin,null,null,200).get("publisherKey").asText();
        String endpoint="/api/workstation-agent/"+stationId+"/frame";byte[] image=jpeg(0x00FF00);
        mvc.perform(post(endpoint).contentType(MediaType.IMAGE_JPEG).content(image)).andExpect(status().isForbidden());
        mvc.perform(post(endpoint).header("X-Station-Key",key).contentType(MediaType.IMAGE_JPEG).content(new byte[]{1,2,3})).andExpect(status().isBadRequest());
        mvc.perform(post(endpoint).header("X-Station-Key",key).contentType(MediaType.IMAGE_JPEG).content(image)).andExpect(status().isOk());
        call("GET","/api/workstations/"+stationId+"/frame",other,terminalKey,null,403);
        var bytes=mvc.perform(get("/api/workstations/"+stationId+"/frame").header("Authorization","Bearer "+admin)).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andReturn().getResponse().getContentAsByteArray();
        assertThat(bytes).isEqualTo(image);
        Long second=createStation("second-"+UUID.randomUUID().toString().substring(0,6),"USB","");
        call("GET","/api/workstations/"+second+"/frame",admin,null,null,503);
        Long oldUse=uses.findByStationIdAndEndedAtIsNull(stationId).get(0).getId();
        when(dify.envCheck(anyMap(),anyString(),anyString())).thenAnswer(invocation->{
            workstations.releaseByTeacher(users.findByUsername(json.readTree(mvc.perform(get("/api/auth/me").header("Authorization","Bearer "+admin)).andReturn().getResponse().getContentAsString()).get("username").asText()).orElseThrow().getId(),stationId);
            var response=new EnvCheckResponse();response.setLevel("L0");response.setSummary("测试巡检");response.setFromDify(true);return response;
        });
        call("POST","/api/workstations/"+stationId+"/inspect",admin,null,null,200);
        var log=logs.findTop20ByWorkstationIdOrderByCreatedAtDesc(stationId).get(0);
        assertThat(log.getSessionId()).isEqualTo(sessionId);assertThat(log.getWorkstationUseId()).isEqualTo(oldUse);assertThat(log.getCapturedAt()).isNotNull();assertThat(log.getSnapshotUrl()).startsWith("/uploads/");
        assertThat(uses.findById(oldUse).orElseThrow().getEndedAt()).isNotNull();
        String rotated=call("POST",adminBase()+"/publisher-key",admin,null,null,200).get("publisherKey").asText();
        assertThat(rotated).isNotEqualTo(key);
        mvc.perform(post(endpoint).header("X-Station-Key",key).contentType(MediaType.IMAGE_JPEG).content(image)).andExpect(status().isForbidden());
        call("GET","/api/workstations/"+stationId+"/frame",admin,null,null,503);
    }
    @Test void duplicateCameraAddressRejectedAndOneStudentCannotOccupyTwoStations() throws Exception {
        String stream="http://127.0.0.1/"+UUID.randomUUID()+".flv";
        createStation("network-"+UUID.randomUUID().toString().substring(0,6),"NETWORK",stream);
        call("POST","/api/admin/workstations?experimentCode="+CODE,admin,null,Map.of("code","dup","name","重复","capacity",1,"enabled",true,"cameraCode","dup-"+UUID.randomUUID(),"cameraType","NETWORK","streamUrl",stream),409);
        Long second=createStation("concurrent-"+UUID.randomUUID().toString().substring(0,6),"USB","");
        String code=call("POST","/api/admin/workstations/"+second+"/pair-code",admin,null,null,200).get("code").asText();
        String secondKey=call("POST","/api/admin/workstations/pair",admin,null,Map.of("code",code),200).get("terminalToken").asText();
        ExecutorService pool=Executors.newFixedThreadPool(2);var gate=new CountDownLatch(1);
        try {
            var tasks=List.of(terminalKey,secondKey).stream().map(key->pool.submit(()->{gate.await();try{workstations.claim(pupil.getId(),key,sessionId);return 200;}catch(org.springframework.web.server.ResponseStatusException e){return e.getStatusCode().value();}})).toList();
            gate.countDown();assertThat(List.of(tasks.get(0).get(10,TimeUnit.SECONDS),tasks.get(1).get(10,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,409);
            assertThat(uses.findByUserIdAndEndedAtIsNull(pupil.getId())).hasSize(1);
        }finally{pool.shutdownNow();}
    }
    @Test void studentsConfirmOwnIdentityWithGroupCodeAndLessonExpiry() throws Exception {
        call("POST","/api/workstations/claim",student,terminalKey,Map.of("sessionId",sessionId),200);
        var terminal=call("GET","/api/workstations/terminal",student,terminalKey,null,200);
        assertThat(terminal.get("station").has("streamUrl")).isFalse();
        assertThat(terminal.get("station").has("occupants")).isFalse();
        String code=call("POST","/api/workstations/group-code",student,terminalKey,null,200).get("code").asText();
        call("POST","/api/workstations/join-group",other,null,Map.of("code",code,"sessionId",sessionId),403);
        call("POST","/api/workstations/join-group",other,null,Map.of("code",code,"sessionId",peerSession),200);
        var mine=call("GET","/api/workstations/my-seat",other,null,null,200);
        assertThat(mine.get("use").get("userId").asLong()).isEqualTo(peer.getId());
        assertThat(mine.get("station").get("id").asLong()).isEqualTo(stationId);
        call("DELETE","/api/workstations/my-seat",other,null,null,200);
        assertThat(uses.findByStationIdAndEndedAtIsNull(stationId)).hasSize(1);
        call("DELETE",base()+"/occupancy",admin,null,null,200);
        call("POST","/api/workstations/claim",other,terminalKey,Map.of("sessionId",peerSession),200);
        call("POST","/api/workstations/join-group",student,null,Map.of("code",code,"sessionId",sessionId),400);
        call("DELETE","/api/teacher/workstations/lesson?experimentCode="+CODE,admin,null,null,200);
        assertThat(call("GET","/api/workstations/my-seat",student,null,null,200).isEmpty()).isTrue();
        call("POST","/api/workstations/join-group",other,null,Map.of("code",code,"sessionId",peerSession),400);
    }
    @Test void physicalRoomCanHostDifferentExperimentsWithoutMovingDevices() throws Exception {
        Long room=call("POST","/api/admin/workstations/rooms",admin,null,Map.of("name","物理楼 302"),200).get("id").asLong();
        String path="/api/teacher/workstations/lesson?roomId="+room+"&experimentCode=";
        call("POST",path+CODE,admin,null,Map.of("className","工位测试一班"),200);
        call("POST",path+"air_wedge_thickness",admin,null,Map.of("className","工位测试一班"),409);
        call("DELETE",path+CODE,admin,null,null,200);
        var next=call("POST",path+"air_wedge_thickness",admin,null,Map.of("className","工位测试一班"),200);
        assertThat(next.get("lessonExperimentCode").asText()).isEqualTo("air_wedge_thickness");
        assertThat(next.get("room").get("id").asLong()).isEqualTo(room);
        call("DELETE",path+"air_wedge_thickness",admin,null,null,200);
        var sameClassTeacher=token(user("same-teacher"+UUID.randomUUID(),"工位测试一班","TEACHER"));
        call("DELETE","/api/teacher/workstations/lesson?experimentCode="+CODE,admin,null,null,200);
        call("GET","/api/workstations/"+stationId+"/frame",sameClassTeacher,null,null,503);
        call("GET","/api/workstations/"+stationId+"/stream",student,null,null,403);
    }
    @Test void explicitDeploymentBootstrapRegistersCameraOnlyOnce() {
        boolean enabled=deployment.isBootstrapEnabled();String name=deployment.getBootstrapRoomName(),code=deployment.getBootstrapStationCode(),url=deployment.getInitialStreamUrl();
        try {
            deployment.setBootstrapEnabled(true);deployment.setBootstrapRoomName("联调房间"+UUID.randomUUID());deployment.setBootstrapStationCode("01");deployment.setInitialStreamUrl("ws://127.0.0.1/"+UUID.randomUUID()+".flv");
            long before=stations.count();workstations.initializeRooms();assertThat(stations.count()).isEqualTo(before+1);
            var camera=stations.findAll().stream().filter(s->"deployment-camera".equals(s.getCameraCode())).findFirst().orElseThrow();
            camera.setName("管理员修改后的名称");stations.save(camera);
            workstations.initializeRooms();assertThat(stations.count()).isEqualTo(before+1);
            assertThat(stations.findById(camera.getId()).orElseThrow().getName()).isEqualTo("管理员修改后的名称");
        }finally{deployment.setBootstrapEnabled(enabled);deployment.setBootstrapRoomName(name);deployment.setBootstrapStationCode(code);deployment.setInitialStreamUrl(url);}
    }
    @Test void minimalCameraConfigurationAndViewingNeedNoLessonOrStudentBinding() throws Exception {
        Long room=call("POST","/api/admin/workstations/rooms",admin,null,Map.of("name","最小巡视测试室"),200).get("id").asLong();
        var saved=call("POST","/api/admin/workstations/simple?roomId="+room,admin,null,Map.of("code","01","name","1 号工位","address","192.168.10.20:80","enabled",true),200);
        Long id=saved.get("id").asLong();
        assertThat(call("GET","/api/admin/workstations/"+id+"/config",admin,null,null,200).get("streamUrl").asText()).isEqualTo("ws://192.168.10.20:80/ws/hdl/hlsram/live0.flv");
        var overview=call("GET","/api/teacher/workstations/overview",classTeacher,null,null,200);
        var row=java.util.stream.StreamSupport.stream(overview.get("stations").spliterator(),false).filter(s->s.get("id").asLong()==id).findFirst().orElseThrow();
        assertThat(row.get("canView").asBoolean()).isTrue();assertThat(row.has("occupants")).isFalse();assertThat(row.has("streamUrl")).isFalse();
        assertThat(row.get("streamConfigured").asBoolean()).isTrue();
        call("GET","/api/teacher/workstations/overview",student,null,null,403);
        call("POST","/api/admin/workstations/simple?roomId="+room,classTeacher,null,Map.of("code","02","name","2 号工位","address","192.168.10.21","enabled",true),403);
        call("POST","/api/admin/workstations/simple?roomId="+room,admin,null,Map.of("code","02","name","2 号工位","address","rtsp://192.168.10.21/live","enabled",true),400);
        var upstream=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
        byte[] bytes={'F','L','V',1,5,0,0,0,9};
        upstream.createContext("/live.flv",exchange->{exchange.sendResponseHeaders(200,bytes.length);try(var out=exchange.getResponseBody()){out.write(bytes);}});upstream.start();
        try {
            String address="http://127.0.0.1:"+upstream.getAddress().getPort()+"/live.flv";
            call("POST","/api/admin/workstations/simple?roomId="+room+"&id="+id,admin,null,Map.of("code","01","name","1 号工位","address",address,"enabled",true),200);
            var result=mvc.perform(get("/api/workstations/"+id+"/stream").header("Authorization","Bearer "+classTeacher)).andExpect(request().asyncStarted()).andReturn();
            result.getAsyncResult(5000);
            assertThat(mvc.perform(asyncDispatch(result)).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray()).isEqualTo(bytes);
            call("POST","/api/admin/workstations/simple?roomId="+room+"&id="+id,admin,null,Map.of("code","01","name","1 号工位","address",address,"enabled",false),200);
            call("GET","/api/workstations/"+id+"/stream",classTeacher,null,null,409);
        }finally{upstream.stop(0);}
    }
}
