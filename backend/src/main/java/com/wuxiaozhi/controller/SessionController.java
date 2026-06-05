package com.wuxiaozhi.controller;

import com.wuxiaozhi.dto.*;
import com.wuxiaozhi.entity.LabSession;
import com.wuxiaozhi.dto.device.DeviceStatusDto;
import com.wuxiaozhi.service.DeviceAcquisitionService;
import com.wuxiaozhi.service.LabSessionService;
import com.wuxiaozhi.service.ReportService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final LabSessionService labSessionService;
    private final DeviceAcquisitionService deviceAcquisitionService;
    private final ReportService reportService;

    public SessionController(LabSessionService labSessionService,
                             DeviceAcquisitionService deviceAcquisitionService,
                             ReportService reportService) {
        this.labSessionService = labSessionService;
        this.deviceAcquisitionService = deviceAcquisitionService;
        this.reportService = reportService;
    }

    @PostMapping
    public LabSession start(@Valid @RequestBody StartSessionRequest req) {
        return labSessionService.startSession(req);
    }

    @GetMapping("/{sessionId}")
    public LabSession get(@PathVariable Long sessionId) {
        return labSessionService.getSession(sessionId);
    }

    @PatchMapping("/{sessionId}/step")
    public LabSession updateStep(@PathVariable Long sessionId, @RequestParam int stepId) {
        return labSessionService.updateStep(sessionId, stepId);
    }

    @GetMapping("/{sessionId}/data")
    public Map<String, Object> getData(@PathVariable Long sessionId) {
        return labSessionService.getSessionData(sessionId);
    }

    @PostMapping("/{sessionId}/data")
    public SessionDataSubmitResponse submitData(@PathVariable Long sessionId,
                                                @Valid @RequestBody SubmitSessionDataRequest req) {
        return labSessionService.submitSessionData(sessionId, req);
    }

    @PostMapping("/{sessionId}/device/connect")
    public DeviceStatusDto deviceConnect(@PathVariable Long sessionId, @RequestParam int stepId) {
        return deviceAcquisitionService.connect(sessionId, stepId);
    }

    @GetMapping("/{sessionId}/device/status")
    public DeviceStatusDto deviceStatus(@PathVariable Long sessionId, @RequestParam int stepId) {
        return deviceAcquisitionService.status(sessionId, stepId);
    }

    @PostMapping("/{sessionId}/device/read")
    public DeviceStatusDto deviceRead(@PathVariable Long sessionId, @RequestParam int stepId) {
        return deviceAcquisitionService.readOnce(sessionId, stepId);
    }

    @PostMapping("/{sessionId}/device/acquire")
    public Map<String, Object> deviceAcquire(@PathVariable Long sessionId, @RequestParam int stepId) {
        return deviceAcquisitionService.acquireSync(sessionId, stepId);
    }

    @GetMapping(value = "/{sessionId}/device/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter deviceStream(@PathVariable Long sessionId, @RequestParam int stepId) {
        return deviceAcquisitionService.stream(sessionId, stepId);
    }

    @PostMapping("/{sessionId}/device/stop")
    public void deviceStop(@PathVariable Long sessionId, @RequestParam int stepId) {
        deviceAcquisitionService.stop(sessionId, stepId);
    }

    @GetMapping("/{sessionId}/device/snapshot")
    public Map<String, Object> deviceSnapshot(@PathVariable Long sessionId, @RequestParam int stepId) {
        return deviceAcquisitionService.snapshot(sessionId, stepId);
    }

    @PostMapping("/{sessionId}/assist")
    public AssistResponse assist(@PathVariable Long sessionId, @RequestBody AssistRequest req) {
        return labSessionService.assist(sessionId, req);
    }

    @PostMapping(value = "/{sessionId}/assist/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter assistStream(@PathVariable Long sessionId, @RequestBody AssistRequest req) {
        return labSessionService.assistStream(sessionId, req);
    }

    @PostMapping("/{sessionId}/env-check")
    public EnvCheckResponse envCheck(@PathVariable Long sessionId) {
        return labSessionService.envCheck(sessionId);
    }

    @PostMapping("/{sessionId}/tutorial-view")
    public LabSession tutorialView(@PathVariable Long sessionId) {
        return labSessionService.incrementTutView(sessionId);
    }

    @PostMapping("/{sessionId}/finish")
    public LabSession finish(@PathVariable Long sessionId) {
        return labSessionService.finishSession(sessionId);
    }

    @GetMapping("/{sessionId}/report")
    public Map<String, Object> report(@PathVariable Long sessionId) {
        return labSessionService.buildReportData(sessionId);
    }

    @GetMapping("/{sessionId}/report/docx")
    public ResponseEntity<byte[]> reportDocx(@PathVariable Long sessionId) throws Exception {
        Map<String, Object> data = labSessionService.buildReportData(sessionId);
        byte[] bytes = reportService.generateDocx(data);
        String filename = URLEncoder.encode("实验总结报告-" + data.get("experimentName") + ".docx", StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .body(bytes);
    }
}
