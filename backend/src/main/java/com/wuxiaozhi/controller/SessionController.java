package com.wuxiaozhi.controller;

import com.wuxiaozhi.dto.*;
import com.wuxiaozhi.entity.ChatMessage;
import com.wuxiaozhi.entity.EnvCheckLog;
import com.wuxiaozhi.entity.LabSession;
import com.wuxiaozhi.dto.device.DeviceStatusDto;
import com.wuxiaozhi.service.DeviceAcquisitionService;
import com.wuxiaozhi.service.LabSessionService;
import com.wuxiaozhi.service.ReportService;
import com.wuxiaozhi.service.StudentExperimentService;
import com.wuxiaozhi.service.UvcCameraCaptureService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final LabSessionService labSessionService;
    private final DeviceAcquisitionService deviceAcquisitionService;
    private final ReportService reportService;
    private final UvcCameraCaptureService uvcCameraCaptureService;
    private final StudentExperimentService studentExperimentService;

    public SessionController(LabSessionService labSessionService,
                             DeviceAcquisitionService deviceAcquisitionService,
                             ReportService reportService,
                             UvcCameraCaptureService uvcCameraCaptureService,
                             StudentExperimentService studentExperimentService) {
        this.labSessionService = labSessionService;
        this.deviceAcquisitionService = deviceAcquisitionService;
        this.reportService = reportService;
        this.uvcCameraCaptureService = uvcCameraCaptureService;
        this.studentExperimentService = studentExperimentService;
    }

    @PostMapping
    public LabSession start(@Valid @RequestBody StartSessionRequest req, Authentication authentication) {
        return labSessionService.startSession(req, currentUserId(authentication));
    }

    @GetMapping
    public List<LabSession> list(@RequestParam(required = false) String experimentCode,
                                 @RequestParam(defaultValue = "false") boolean includeEmpty,
                                 Authentication authentication) {
        return labSessionService.listSessions(currentUserId(authentication), experimentCode, includeEmpty);
    }

    @GetMapping("/latest")
    public LabSession latest(@RequestParam String experimentCode, Authentication authentication) {
        return labSessionService.getLatestActiveSession(currentUserId(authentication), experimentCode);
    }

    @GetMapping("/resume")
    public LabSession resume(@RequestParam String experimentCode, Authentication authentication) {
        return labSessionService.getResumeSession(currentUserId(authentication), experimentCode);
    }

    @GetMapping("/{sessionId:\\d+}")
    public LabSession get(@PathVariable Long sessionId, Authentication authentication) {
        return labSessionService.getSession(sessionId, currentUserId(authentication));
    }

    @GetMapping("/{sessionId:\\d+}/messages")
    public List<ChatMessage> messages(@PathVariable Long sessionId, Authentication authentication) {
        return labSessionService.getMessages(sessionId, currentUserId(authentication));
    }

    @PatchMapping("/{sessionId:\\d+}/messages/latest-ai-image")
    public ChatMessage attachLatestAiImage(@PathVariable Long sessionId,
                                           @RequestBody AttachMessageImageRequest req,
                                           Authentication authentication) {
        return labSessionService.attachLatestAiMessageImage(
                sessionId, currentUserId(authentication), req != null ? req.getImageUrl() : null);
    }

    @PatchMapping("/{sessionId:\\d+}/step")
    public LabSession updateStep(@PathVariable Long sessionId, @RequestParam int stepId, Authentication authentication) {
        return labSessionService.updateStep(sessionId, currentUserId(authentication), stepId);
    }

    @PatchMapping("/{sessionId:\\d+}/camera-status")
    public void updateCameraStatus(@PathVariable Long sessionId,
                                   @RequestBody UpdateCameraStatusRequest req,
                                   Authentication authentication) {
        boolean active = req != null && Boolean.TRUE.equals(req.getActive());
        labSessionService.updateCameraStatus(sessionId, currentUserId(authentication), active);
    }

    @PatchMapping("/{sessionId:\\d+}/env-check-enabled")
    public LabSession updateEnvCheckEnabled(@PathVariable Long sessionId,
                                            @RequestBody UpdateEnvCheckEnabledRequest req,
                                            Authentication authentication) {
        boolean enabled = req != null && req.isEnabled();
        return labSessionService.updateEnvCheckEnabled(sessionId, currentUserId(authentication), enabled);
    }

    @GetMapping("/{sessionId:\\d+}/data")
    public Map<String, Object> getData(@PathVariable Long sessionId, Authentication authentication) {
        return labSessionService.getSessionData(sessionId, currentUserId(authentication));
    }

    @PostMapping("/{sessionId:\\d+}/data")
    public SessionDataSubmitResponse submitData(@PathVariable Long sessionId,
                                                @Valid @RequestBody SubmitSessionDataRequest req,
                                                Authentication authentication) {
        return labSessionService.submitSessionData(sessionId, currentUserId(authentication), req);
    }

    @DeleteMapping("/{sessionId:\\d+}/data/{dataLogId:\\d+}")
    public Map<String, Object> deleteData(@PathVariable Long sessionId,
                                          @PathVariable Long dataLogId,
                                          Authentication authentication) {
        labSessionService.deleteSessionData(sessionId, currentUserId(authentication), dataLogId);
        return Map.of("ok", true);
    }

    @PostMapping("/{sessionId:\\d+}/device/connect")
    public DeviceStatusDto deviceConnect(@PathVariable Long sessionId, @RequestParam int stepId, Authentication authentication) {
        labSessionService.getSession(sessionId, currentUserId(authentication));
        return deviceAcquisitionService.connect(sessionId, stepId);
    }

    @GetMapping("/{sessionId:\\d+}/device/status")
    public DeviceStatusDto deviceStatus(@PathVariable Long sessionId, @RequestParam int stepId, Authentication authentication) {
        labSessionService.getSession(sessionId, currentUserId(authentication));
        return deviceAcquisitionService.status(sessionId, stepId);
    }

    @PostMapping("/{sessionId:\\d+}/device/read")
    public DeviceStatusDto deviceRead(@PathVariable Long sessionId, @RequestParam int stepId, Authentication authentication) {
        labSessionService.getSession(sessionId, currentUserId(authentication));
        return deviceAcquisitionService.readOnce(sessionId, stepId);
    }

    @PostMapping("/{sessionId:\\d+}/device/acquire")
    public Map<String, Object> deviceAcquire(@PathVariable Long sessionId, @RequestParam int stepId, Authentication authentication) {
        labSessionService.getSession(sessionId, currentUserId(authentication));
        return deviceAcquisitionService.acquireSync(sessionId, stepId);
    }

    @GetMapping(value = "/{sessionId:\\d+}/device/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter deviceStream(@PathVariable Long sessionId, @RequestParam int stepId, Authentication authentication) {
        labSessionService.getSession(sessionId, currentUserId(authentication));
        return deviceAcquisitionService.stream(sessionId, stepId);
    }

    @PostMapping("/{sessionId:\\d+}/device/stop")
    public void deviceStop(@PathVariable Long sessionId, @RequestParam int stepId, Authentication authentication) {
        labSessionService.getSession(sessionId, currentUserId(authentication));
        deviceAcquisitionService.stop(sessionId, stepId);
    }

    @GetMapping("/{sessionId:\\d+}/device/snapshot")
    public Map<String, Object> deviceSnapshot(@PathVariable Long sessionId, @RequestParam int stepId, Authentication authentication) {
        labSessionService.getSession(sessionId, currentUserId(authentication));
        return deviceAcquisitionService.snapshot(sessionId, stepId);
    }

    @PostMapping("/{sessionId:\\d+}/ccd-capture")
    public Map<String, Object> ccdCapture(@PathVariable Long sessionId, Authentication authentication) {
        labSessionService.getSession(sessionId, currentUserId(authentication));
        return uvcCameraCaptureService.captureToUpload();
    }

    @PostMapping("/{sessionId:\\d+}/assist")
    public AssistResponse assist(@PathVariable Long sessionId, @RequestBody AssistRequest req, Authentication authentication) {
        return labSessionService.assist(sessionId, currentUserId(authentication), req);
    }

    @PostMapping(value = "/{sessionId:\\d+}/assist/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter assistStream(@PathVariable Long sessionId, @RequestBody AssistRequest req, Authentication authentication) {
        return labSessionService.assistStream(sessionId, currentUserId(authentication), req);
    }

    @PostMapping("/{sessionId:\\d+}/env-check")
    public EnvCheckResponse envCheck(@PathVariable Long sessionId,
                                     @RequestBody(required = false) EnvCheckRequest req,
                                     Authentication authentication) {
        return labSessionService.envCheck(sessionId, currentUserId(authentication), req);
    }

    @GetMapping("/{sessionId:\\d+}/env-logs")
    public List<EnvCheckLog> envLogs(@PathVariable Long sessionId, Authentication authentication) {
        currentUserId(authentication);
        return labSessionService.getEnvCheckLogs(sessionId);
    }

    @PostMapping("/{sessionId:\\d+}/tutorial-view")
    public LabSession tutorialView(@PathVariable Long sessionId, Authentication authentication) {
        return labSessionService.incrementTutView(sessionId, currentUserId(authentication));
    }

    @PostMapping("/{sessionId:\\d+}/finish")
    public LabSession finish(@PathVariable Long sessionId, Authentication authentication) {
        return labSessionService.finishSession(sessionId, currentUserId(authentication));
    }

    @PostMapping("/{sessionId:\\d+}/archive")
    public LabSession archive(@PathVariable Long sessionId, Authentication authentication) {
        return labSessionService.archiveFromHistory(sessionId, currentUserId(authentication));
    }

    @GetMapping("/{sessionId:\\d+}/report")
    public Map<String, Object> report(@PathVariable Long sessionId, Authentication authentication) {
        return labSessionService.buildReportData(sessionId, currentUserId(authentication));
    }

    @GetMapping("/{sessionId:\\d+}/report/docx")
    public ResponseEntity<byte[]> reportDocx(@PathVariable Long sessionId, Authentication authentication) throws Exception {
        Map<String, Object> data = labSessionService.buildReportData(sessionId, currentUserId(authentication));
        byte[] bytes = reportService.generateDocx(data);
        String filename = URLEncoder.encode("实验总结报告-" + data.get("experimentName") + ".docx", StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .body(bytes);
    }

    @PostMapping("/{sessionId:\\d+}/report/student-docx")
    public ResponseEntity<byte[]> studentReportDocx(@PathVariable Long sessionId,
                                                    @RequestBody StudentReportDocxRequest request,
                                                    Authentication authentication) throws Exception {
        Map<String, Object> data = labSessionService.buildReportData(sessionId, currentUserId(authentication));
        try {
            String experimentCode = String.valueOf(data.getOrDefault("experimentCode", ""));
            studentExperimentService.saveReportBody(
                    currentUserId(authentication),
                    experimentCode,
                    sessionId,
                    StudentExperimentService.sectionsFromDocxItems(request.getSections()));
        } catch (Exception ignored) {
            // 导出仍继续，正文补存失败不阻断下载
        }
        Map<String, Object> meta = Map.of(
                "experimentName", String.valueOf(data.getOrDefault("experimentName", "实验")),
                "studentName", String.valueOf(data.getOrDefault("studentName", "")),
                "studentClass", String.valueOf(data.getOrDefault("studentClass", ""))
        );
        byte[] bytes = reportService.generateStudentReportDocx(meta, request.getSections());
        String filename = URLEncoder.encode(meta.get("experimentName") + "-实验报告.docx", StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .body(bytes);
    }

    private Long currentUserId(Authentication authentication) {
        return (Long) authentication.getPrincipal();
    }
}
