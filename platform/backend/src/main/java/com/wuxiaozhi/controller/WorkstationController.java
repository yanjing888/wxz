package com.wuxiaozhi.controller;

import com.wuxiaozhi.dto.*;
import com.wuxiaozhi.entity.*;
import com.wuxiaozhi.security.AuthSupport;
import com.wuxiaozhi.service.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
public class WorkstationController {
    private final WorkstationService service;
    private final WorkstationFrames frames;
    private final WorkstationInspectionService inspections;
    private final WorkstationVideoService video;
    private final WorkstationBindingService bindings;
    public WorkstationController(WorkstationService service,WorkstationFrames frames,WorkstationInspectionService inspections,WorkstationVideoService video,WorkstationBindingService bindings){this.service=service;this.frames=frames;this.inspections=inspections;this.video=video;this.bindings=bindings;}
    private Long uid(Authentication auth){return AuthSupport.currentUserId(auth);}
    public record GroupRequest(@NotBlank @Size(max=32) String code,@NotNull @Positive Long sessionId){}
    public record LessonRequest(@NotBlank @Size(max=64) String className){}
    public record PairRequest(@NotBlank @Size(max=32) String code){}
    public record SessionRequest(@NotNull @Positive Long sessionId){}
    public record EnabledRequest(boolean enabled){}
    public record RoomRequest(@NotBlank @Size(max=100) String name){}
    @GetMapping("/api/teacher/workstations/overview")
    public Map<String,Object> overview(Authentication a){return bindings.overview(uid(a));}
    public record BindingRequest(@Size(max=64) String className,@NotNull @Size(max=8) List<@NotNull @Positive Long> studentIds){}
    @GetMapping("/api/teacher/workstations/binding-students")
    public Map<String,Object> bindingStudents(@RequestParam Long roomId,Authentication a){return bindings.candidates(uid(a),roomId);}
    @PutMapping("/api/teacher/workstations/{id}/bindings")
    public void bind(@PathVariable Long id,@Valid @RequestBody BindingRequest req,Authentication a){bindings.save(uid(a),id,req.className(),req.studentIds());}
    @DeleteMapping("/api/teacher/workstations/bindings")
    public void clearBindings(@RequestParam Long roomId,Authentication a){bindings.clearRoom(uid(a),roomId);}
    @PostMapping("/api/admin/workstations/simple")
    public Map<String,Object> saveSimple(@RequestParam Long roomId,@RequestParam(required=false) Long id,@Valid @RequestBody SimpleWorkstationRequest req,Authentication a){return service.saveSimple(uid(a),roomId,id,req);}
    @PostMapping("/api/admin/workstations/deployment-camera")
    public Map<String,Object> importCamera(@RequestParam Long roomId,@Valid @RequestBody RoomRequest req,Authentication a){return service.importDeploymentCamera(uid(a),roomId,req.name());}
    @GetMapping(value="/api/workstations/{id}/stream",produces="video/x-flv")
    public ResponseEntity<org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody> stream(@PathVariable Long id,Authentication a){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Accel-Buffering","no").body(video.stream(uid(a),id));}
    @GetMapping("/api/workstations/rooms")
    public List<LabRoom> rooms(Authentication a){return service.listRooms(uid(a));}
    @PostMapping("/api/admin/workstations/rooms")
    public LabRoom createRoom(@Valid @RequestBody RoomRequest req,Authentication a){return service.createRoom(uid(a),req.name());}
    @GetMapping("/api/workstations/my-seat")
    public Map<String,Object> mySeat(Authentication a){return service.mySeat(uid(a));}
    @DeleteMapping("/api/workstations/my-seat")
    public void leaveSeat(Authentication a){service.leaveMySeat(uid(a));}
    @PostMapping("/api/workstations/group-code")
    public Map<String,Object> groupCode(@RequestHeader(value="X-Terminal-Token",required=false) String token,Authentication a){return service.groupCode(uid(a),token);}
    @PostMapping("/api/workstations/join-group")
    public WorkstationUse joinGroup(@Valid @RequestBody GroupRequest req,Authentication a){return service.joinGroup(uid(a),req.code(),req.sessionId());}
    @GetMapping("/api/teacher/workstations")
    public Map<String,Object> dashboard(@RequestParam(required=false) String experimentCode,@RequestParam(required=false) Long roomId,Authentication a){return service.dashboard(uid(a),experimentCode,roomId);}
    @GetMapping("/api/admin/workstations")
    public Map<String,Object> adminDashboard(@RequestParam(required=false) String experimentCode,@RequestParam(required=false) Long roomId,Authentication a){return service.dashboard(uid(a),experimentCode,roomId);}
    @PutMapping("/api/admin/workstations/room")
    public LabRoom renameRoom(@RequestParam(required=false) String experimentCode,@RequestParam(required=false) Long roomId,@Valid @RequestBody RoomRequest req,Authentication a){return service.renameRoom(uid(a),experimentCode,roomId,req.name());}
    @PostMapping("/api/admin/workstations")
    public Map<String,Object> create(@RequestParam(required=false) String experimentCode,@RequestParam(required=false) Long roomId,@Valid @RequestBody WorkstationRequest req,Authentication a){return service.saveStation(uid(a),experimentCode,roomId,null,req);}
    @GetMapping("/api/admin/workstations/{id}/config")
    public Map<String,Object> config(@PathVariable Long id,Authentication a){return service.config(uid(a),id);}
    @PutMapping("/api/admin/workstations/{id}")
    public Map<String,Object> update(@PathVariable Long id,@RequestParam(required=false) String experimentCode,@RequestParam(required=false) Long roomId,@Valid @RequestBody WorkstationRequest req,Authentication a){return service.saveStation(uid(a),experimentCode,roomId,id,req);}
    @PostMapping("/api/teacher/workstations/lesson")
    public Map<String,Object> start(@RequestParam(required=false) String experimentCode,@RequestParam(required=false) Long roomId,@Valid @RequestBody LessonRequest req,Authentication a){return service.startLesson(uid(a),experimentCode,roomId,req.className());}
    @DeleteMapping("/api/teacher/workstations/lesson")
    public void close(@RequestParam(required=false) String experimentCode,@RequestParam(required=false) Long roomId,Authentication a){service.closeLesson(uid(a),experimentCode,roomId);}
    @PostMapping("/api/admin/workstations/{id}/pair-code")
    public Map<String,Object> pairCode(@PathVariable Long id,Authentication a){return service.pairCode(uid(a),id);}
    @PostMapping("/api/admin/workstations/{id}/publisher-key")
    public Map<String,Object> publisherKey(@PathVariable Long id,Authentication a){return service.publisherKey(uid(a),id);}
    @PostMapping("/api/teacher/workstations/{id}/members")
    public WorkstationUse addMember(@PathVariable Long id,@Valid @RequestBody SessionRequest req,Authentication a){return service.addGroupMember(uid(a),id,req.sessionId());}
    @DeleteMapping("/api/teacher/workstations/{id}/occupancy")
    public void releaseTeacher(@PathVariable Long id,Authentication a){service.releaseByTeacher(uid(a),id);}
    @GetMapping("/api/teacher/workstations/{id}/history")
    public List<WorkstationUse> history(@PathVariable Long id,Authentication a){return service.history(uid(a),id);}
    @GetMapping("/api/teacher/workstations/{id}/logs")
    public List<EnvCheckLog> logs(@PathVariable Long id,Authentication a){return service.logs(uid(a),id);}
    @PatchMapping("/api/teacher/workstations/{id}/inspection")
    public void enableInspection(@PathVariable Long id,@RequestBody EnabledRequest req,Authentication a){service.inspectionEnabled(uid(a),id,req.enabled());}
    @PostMapping("/api/workstations/{id}/inspect")
    public EnvCheckResponse inspect(@PathVariable Long id,@RequestHeader(value="X-Terminal-Token",required=false) String token,Authentication a){service.cameraAccess(uid(a),service.station(id),token);return inspections.inspect(id);}
    @GetMapping("/api/workstations/{id}/frame")
    public ResponseEntity<byte[]> frame(@PathVariable Long id,@RequestHeader(value="X-Terminal-Token",required=false) String token,Authentication a) {
        var s=service.station(id);service.cameraAccess(uid(a),s,token);var frame=frames.capture(s);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.IMAGE_JPEG).header("X-Captured-At",frame.capturedAt().toString()).body(frame.bytes());
    }
    @PostMapping("/api/admin/workstations/pair")
    public Map<String,Object> pair(@Valid @RequestBody PairRequest req,Authentication a){return service.pair(uid(a),req.code());}
    @GetMapping("/api/workstations/terminal")
    public Map<String,Object> terminal(@RequestHeader(value="X-Terminal-Token",required=false) String token,Authentication a){return service.terminalState(uid(a),token);}
    @PostMapping("/api/workstations/claim")
    public WorkstationUse claim(@RequestHeader(value="X-Terminal-Token",required=false) String token,@Valid @RequestBody SessionRequest req,Authentication a){return service.claim(uid(a),token,req.sessionId());}
    @DeleteMapping("/api/workstations/claim")
    public void release(@RequestHeader(value="X-Terminal-Token",required=false) String token,Authentication a){service.release(uid(a),token);}
    /** 此接口不使用学生登录，采集密钥由管理员单独生成。 */
    @PostMapping(value="/api/workstation-agent/{id}/frame",consumes=MediaType.IMAGE_JPEG_VALUE)
    public void publish(@PathVariable Long id,@RequestHeader(value="X-Station-Key",required=false) String key,jakarta.servlet.http.HttpServletRequest request) throws java.io.IOException {
        if(request.getContentLengthLong()>WorkstationFrames.MAX_BYTES)throw WorkstationService.fail(HttpStatus.PAYLOAD_TOO_LARGE,"图像超过 1MB");
        byte[] bytes=request.getInputStream().readNBytes(WorkstationFrames.MAX_BYTES+1);frames.publish(service.station(id),key,bytes);
    }
}
