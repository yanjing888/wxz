package com.wuxiaozhi.service;

import com.wuxiaozhi.entity.*;
import com.wuxiaozhi.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.io.*;
import java.time.LocalDateTime;
import java.util.Locale;
import org.apache.poi.xwpf.usermodel.XWPFDocument;

@Service
public class ReportAttachmentService {
    private final StudentExperimentAccessService access;
    private final LabSessionRepository sessions;
    private final StudentExperimentProgressRepository progress;
    private final UserRepository users;
    private final FileStorageService files;
    public ReportAttachmentService(StudentExperimentAccessService access, LabSessionRepository sessions,
                                   StudentExperimentProgressRepository progress, UserRepository users, FileStorageService files) {
        this.access = access; this.sessions = sessions; this.progress = progress; this.users = users; this.files = files;
    }
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long userId, String code, Long sessionId, MultipartFile file) throws IOException {
        if (!access.assignedCodes(userId).contains(code)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "未分配该实验");
        var session = sessions.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "实验记录不存在"));
        if (!code.equals(session.getExperimentCode()) || !"FINISHED".equals(session.getStatus()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "请先结束对应实验，再上传报告");
        if (file == null || file.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择实验报告文件");
        if (file.getSize() > 20L * 1024 * 1024) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "报告文件不能超过 20MB");
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "");
        if (name.length() > 200) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文件名不能超过 200 字");
        String lower = name.toLowerCase(Locale.ROOT);
        String type;
        if (lower.endsWith(".pdf")) {
            byte[] prefix;
            try (var stream = file.getInputStream()) { prefix = stream.readNBytes(5); }
            if (!new String(prefix, java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF-"))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PDF 文件格式无效");
            type = "application/pdf";
        } else if (lower.endsWith(".docx")) {
            try (var doc = new XWPFDocument(file.getInputStream())) { doc.getDocument(); }
            catch (Exception e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Word 文件无效，请上传 .docx 文件"); }
            type = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        } else throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请上传 PDF 或 Word（.docx）报告");
        users.lockById(userId);
        var record = progress.findByUserIdAndExperimentCode(userId, code).orElseGet(() -> {
            var item = new StudentExperimentProgress(); item.setUserId(userId); item.setExperimentCode(code); return item;
        });
        if (record.getReportFileUrl() != null || Boolean.TRUE.equals(record.getGradingCompleted()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "报告已提交或已批改，不能覆盖");
        record.setReportFileUrl(files.storeAttachment(file)); record.setReportFileName(name);
        record.setReportFileType(type); record.setReportFileSize(file.getSize());
        record.setReportSessionId(sessionId); record.setReportCompleted(true);
        record.setReportSubmittedAt(LocalDateTime.now()); record.setUpdatedAt(LocalDateTime.now());
        record.setAiReviewScore(null); record.setAiReviewComment(null); record.setAiReviewJson(null);
        record.setTeacherScore(null); record.setTeacherComment(null); record.setGradingCompleted(false); record.setGradingAt(null);
        progress.saveAndFlush(record);
    }
    public StudentExperimentProgress requireReport(LabSession session) {
        return progress.findByUserIdAndExperimentCode(session.getUserId(), session.getExperimentCode())
                .filter(p -> p.isReportCompleted() && session.getId().equals(p.getReportSessionId()) && p.getReportFileUrl() != null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "未上传电子版报告"));
    }
    public StoredUpload read(LabSession session) throws IOException { return files.read(requireReport(session).getReportFileUrl()); }
    public org.springframework.http.ResponseEntity<byte[]> response(LabSession session) throws IOException {
        var record = requireReport(session); var file = files.read(record.getReportFileUrl());
        return org.springframework.http.ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.parseMediaType(record.getReportFileType()))
                .header("Content-Disposition", org.springframework.http.ContentDisposition.attachment()
                        .filename(record.getReportFileName(), java.nio.charset.StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff").body(file.getData());
    }
}
