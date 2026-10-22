package com.wuxiaozhi.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuxiaozhi.dto.StepReviewRequest;
import com.wuxiaozhi.dto.experiment.StepConfig;
import com.wuxiaozhi.entity.*;
import com.wuxiaozhi.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class ProcessAssessmentService {
    private final StepAssessmentRepository assessments;
    private final ExperimentConfigService experiments;
    private final SessionDataLogRepository dataLogs;
    private final ObjectMapper mapper;
    private final UserRepository users;
    private final LabSessionRepository sessions;
    private final FileStorageService files;

    public ProcessAssessmentService(StepAssessmentRepository assessments, ExperimentConfigService experiments,
                                    SessionDataLogRepository dataLogs, ObjectMapper mapper,
                                    UserRepository users, LabSessionRepository sessions, FileStorageService files) {
        this.assessments = assessments; this.experiments = experiments;
        this.dataLogs = dataLogs; this.mapper = mapper; this.users = users;
        this.sessions = sessions;
        this.files = files;
    }

    private Map<String, StepConfig> steps(LabSession session) {
        return experiments.getByCode(session.getExperimentCode()).getSteps();
    }
    private StepConfig step(LabSession session, int stepId) {
        StepConfig config = steps(session).get(String.valueOf(stepId));
        if (config == null) throw error("实验步骤不存在");
        return config;
    }
    public boolean requiresReview(StepConfig config) { return !Boolean.FALSE.equals(config.getAssessmentRequired()); }
    public double maxScore(StepConfig config) {
        Double value = config.getAssessmentMaxScore();
        return value != null && Double.isFinite(value) && value > 0 ? value : 10.0;
    }
    public List<String> criteria(StepConfig config) {
        return config.getAssessmentCriteria() == null || config.getAssessmentCriteria().isEmpty()
                ? List.of("完成本步骤操作要求", "操作方法与仪器使用规范", "数据记录或实验现象符合要求") : config.getAssessmentCriteria();
    }
    private Map<Integer, StepAssessment> records(LabSession session) {
        Map<Integer, StepAssessment> result = new HashMap<>();
        assessments.findByUserIdAndExperimentCodeOrderByStepIdAsc(session.getUserId(), session.getExperimentCode())
                .forEach(row -> result.put(row.getStepId(), row));
        return result;
    }
    private boolean done(StepAssessment row) {
        return row != null && row.getSubmittedAt() != null && Set.of("PASSED", "COMPLETED").contains(row.getStatus());
    }
    private boolean submitted(StepAssessment row) {
        if (row == null || row.getSubmittedAt() == null) return false;
        try { return !evidenceImages(row.getEvidenceJson()).isEmpty(); }
        catch (RuntimeException e) { return false; }
    }
    private boolean advances(StepAssessment row, StepConfig config) {
        return submitted(row) && (!Boolean.TRUE.equals(config.getAssessmentBlocking()) || !requiresReview(config) || done(row));
    }
    private boolean canSubmit(LabSession session, StepAssessment row, boolean finished) {
        if (!Set.of("ACTIVE", "FINISHED").contains(session.getStatus()) || (done(row) && submitted(row))) return false;
        return !finished || (row != null && row.getSubmittedAt() != null && Set.of("PENDING", "REVISION").contains(row.getStatus()));
    }
    public int resumeStep(LabSession session) {
        var rows = records(session);
        var ids = steps(session).keySet().stream().mapToInt(Integer::parseInt).sorted().toArray();
        for (int id : ids) if (!advances(rows.get(id), step(session, id))) return id;
        return ids.length == 0 ? 1 : ids[ids.length - 1];
    }
    public void requireAccessible(LabSession session, int stepId) {
        step(session, stepId);
        Map<Integer, StepAssessment> rows = records(session);
        for (int id : steps(session).keySet().stream().mapToInt(Integer::parseInt).filter(id -> id < stepId).toArray()) {
            if (!advances(rows.get(id), step(session, id))) throw error("请先提交步骤 " + id + " 的检查图片；关键检查点需教师确认后继续");
        }
    }
    public void requireFinished(LabSession session) {
        users.lockById(session.getUserId());
        Map<Integer, StepAssessment> rows = records(session);
        if (steps(session).keySet().stream().map(Integer::parseInt).anyMatch(id -> !advances(rows.get(id), step(session, id))))
            throw error("请先提交全部步骤的检查图片，并通过关键检查点");
    }
    public Map<String, Object> summary(LabSession session) {
        Map<Integer, StepAssessment> rows = records(session);
        List<Map<String, Object>> items = new ArrayList<>();
        boolean unlocked = true;
        double earned = 0, total = 0;
        int completed = 0, reviewed = 0, required = 0;
        boolean finished = "FINISHED".equals(session.getStatus()) || sessions.existsByUserIdAndExperimentCodeAndStatus(session.getUserId(), session.getExperimentCode(), "FINISHED");
        for (int id : steps(session).keySet().stream().mapToInt(Integer::parseInt).sorted().toArray()) {
            StepConfig config = step(session, id);
            StepAssessment row = rows.get(id);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("stepId", id); item.put("title", config.getTitle());
            item.put("required", requiresReview(config)); item.put("maxScore", maxScore(config));
            item.put("dataFields", config.getDataFields());
            item.put("criteria", criteria(config)); item.put("accessible", unlocked);
            item.put("blocking", requiresReview(config) && Boolean.TRUE.equals(config.getAssessmentBlocking()));
            item.put("submitted", submitted(row));
            item.put("canSubmit", unlocked && canSubmit(session, row, finished));
            item.put("canReview", unlocked && requiresReview(config) && submitted(row) && "PENDING".equals(row.getStatus()));
            item.put("status", row == null ? "NOT_STARTED" : row.getStatus());
            item.put("record", row);
            if (advances(row, config)) completed++;
            if (requiresReview(config)) {
                required++; total += maxScore(config);
                if (done(row) && "PASSED".equals(row.getStatus()) && row.getScore() != null) {
                    reviewed++; earned += row.getScore();
                }
            }
            unlocked = unlocked && advances(row, config);
            items.add(item);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("steps", items); result.put("completedCount", completed);
        result.put("totalCount", items.size()); result.put("gradedCount", reviewed);
        result.put("requiredCount", required); result.put("earnedScore", earned); result.put("maxScore", total);
        result.put("score", required > 0 && reviewed == required ? Math.round(earned / total * 1000.0) / 10.0 : null);
        return result;
    }
    @Transactional
    public Map<String, Object> complete(LabSession session, int stepId, String note) {
        return complete(session, stepId, note, List.of(), null);
    }
    @Transactional
    public Map<String, Object> complete(LabSession session, int stepId, String note, List<MultipartFile> images, List<List<String>> table) {
        users.lockById(session.getUserId());
        requireAccessible(session, stepId);
        StepConfig config = step(session, stepId);
        StepAssessment row = assessments.findByUserIdAndExperimentCodeAndStepId(session.getUserId(), session.getExperimentCode(), stepId)
                .orElseGet(StepAssessment::new);
        if (done(row) && submitted(row)) return summary(session);
        boolean finished = "FINISHED".equals(session.getStatus()) || sessions.existsByUserIdAndExperimentCodeAndStatus(session.getUserId(), session.getExperimentCode(), "FINISHED");
        if (!canSubmit(session, row, finished)) throw error("当前步骤不可提交");
        boolean supplement = row.getId() != null && "PENDING".equals(row.getStatus());
        List<String> imageUrls = supplement ? evidenceImages(row.getEvidenceJson()) : new ArrayList<>();
        List<List<String>> submittedTable = table == null && supplement ? evidenceTable(row.getEvidenceJson()) : table;
        if (images.isEmpty() && imageUrls.isEmpty()) throw error("请完成必填项：检查图片（至少上传或拍摄一张）");
        if (submittedTable != null && !submittedTable.isEmpty() && !validTable(submittedTable)) throw error("实验数据表格填写不完整，请补全或删除表格后提交");
        if (supplement && images.isEmpty() && Objects.equals(submittedTable, evidenceTable(row.getEvidenceJson()))
                && (note == null || note.isBlank() || Objects.equals(note, row.getStudentNote()))) return summary(session);
        if (images.stream().mapToLong(MultipartFile::getSize).sum() > 18L * 1024 * 1024) throw error("本次图片总大小不能超过 18MB");
        for (MultipartFile image : images) {
            if (image.isEmpty() || image.getSize() > 10L * 1024 * 1024) throw error("图片不能为空，单张图片不能超过 10MB");
            String type = image.getContentType();
            if (type == null || !Set.of("image/jpeg", "image/png", "image/webp", "image/gif", "image/bmp").contains(type.toLowerCase(Locale.ROOT)))
                throw error("请选择 JPG、PNG、WebP、GIF 或 BMP 图片");
            try { imageUrls.add(files.store(image)); }
            catch (java.io.IOException | IllegalArgumentException e) { throw error("检查图片保存失败，请重新提交"); }
        }
        row.setUserId(session.getUserId()); row.setExperimentCode(session.getExperimentCode());
        row.setSessionId(session.getId()); row.setStepId(stepId);
        if (!supplement || (note != null && !note.isBlank())) row.setStudentNote(note);
        row.setStatus(requiresReview(config) ? "PENDING" : "COMPLETED");
        row.setSubmittedAt(LocalDateTime.now());
        row.setEvidenceJson(snapshot(session, stepId, imageUrls, submittedTable));
        append(row, supplement ? "SUPPLEMENTED" : "SUBMITTED", note);
        assessments.saveAndFlush(row);
        return summary(session);
    }
    public double processWeight(String code) {
        Double weight = experiments.getByCode(code).getProcessScoreWeight();
        return weight != null && Double.isFinite(weight) && weight >= 0 && weight <= 100 ? weight : 40.0;
    }
    public Double overallScore(Map<String, Object> summary, Double reportScore, String code) {
        Object score = summary.get("score");
        if (!(score instanceof Number) || reportScore == null) return null;
        double weight = processWeight(code) / 100.0;
        return Math.round((((Number) score).doubleValue() * weight + reportScore * (1 - weight)) * 10.0) / 10.0;
    }
    @Transactional
    public Map<String, Object> review(LabSession session, int stepId, User teacher, StepReviewRequest request) {
        users.lockById(session.getUserId());
        requireAccessible(session, stepId);
        StepConfig config = step(session, stepId);
        if (!requiresReview(config)) throw error("本步骤无需教师评分");
        if (request.getScore() == null || !Double.isFinite(request.getScore()) || request.getScore() < 0 || request.getScore() > maxScore(config))
            throw error("分数必须在 0～" + maxScore(config) + " 之间");
        if (request.getPassed() == null) throw error("请选择通过或退回整改");
        if (request.getPassed() && (request.getCheckedCriteria() == null || !request.getCheckedCriteria().containsAll(criteria(config))))
            throw error("通过前请逐项确认检查要点");
        if (!request.getPassed() && (request.getComment() == null || request.getComment().isBlank())) throw error("退回整改时请填写具体改进意见");
        StepAssessment row = assessments.findByUserIdAndExperimentCodeAndStepId(session.getUserId(), session.getExperimentCode(), stepId)
                .orElseGet(StepAssessment::new);
        if (!Objects.equals(request.getVersion(), row.getVersion())) throw error("检查记录已更新，请刷新后重试");
        if (Set.of("PASSED", "COMPLETED").contains(row.getStatus())) throw error("本步骤已通过，不能重复评分");
        if (!submitted(row) || !"PENDING".equals(row.getStatus())) throw error("请等待学生提交完整的检查材料");
        row.setUserId(session.getUserId()); row.setExperimentCode(session.getExperimentCode());
        row.setStepId(stepId); row.setSessionId(session.getId());
        row.setScore(request.getScore()); row.setComment(request.getComment());
        row.setCheckedCriteriaJson(json(request.getCheckedCriteria() == null ? List.of() : request.getCheckedCriteria()));
        row.setStatus(request.getPassed() ? "PASSED" : "REVISION");
        row.setTeacherId(teacher.getId()); row.setTeacherName(teacher.getDisplayName()); row.setReviewedAt(LocalDateTime.now());
        append(row, "REVIEWED", request.getComment());
        assessments.saveAndFlush(row);
        return summary(session);
    }
    private void append(StepAssessment row, String action, String note) {
        try {
            List<Map<String, Object>> history = row.getHistoryJson() == null ? new ArrayList<>()
                    : mapper.readValue(row.getHistoryJson(), new TypeReference<List<Map<String, Object>>>() {});
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("action", action); event.put("at", LocalDateTime.now().toString()); event.put("status", row.getStatus());
            event.put("note", note); event.put("score", "REVIEWED".equals(action) ? row.getScore() : null);
            event.put("teacherName", "REVIEWED".equals(action) ? row.getTeacherName() : null);
            event.put("teacherId", "REVIEWED".equals(action) ? row.getTeacherId() : null);
            event.put("checkedCriteria", "REVIEWED".equals(action) ? row.getCheckedCriteriaJson() : null);
            event.put("sessionId", row.getSessionId()); event.put("evidence", row.getEvidenceJson());
            history.add(event); row.setHistoryJson(json(history));
        } catch (Exception e) { throw new IllegalStateException("无法保存过程评价记录", e); }
    }
    private String snapshot(LabSession session, int stepId, List<String> imageUrls, List<List<String>> table) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("data", dataLogs.findBySessionIdOrderByCreatedAtAsc(session.getId()).stream().filter(d -> d.getStepId() == stepId).toList());
        evidence.put("images", imageUrls.stream().filter(url -> url != null && !url.isBlank()).distinct().toList());
        evidence.put("table", table);
        return json(evidence);
    }
    private List<List<String>> evidenceTable(String evidence) {
        if (evidence == null) return null;
        try { return mapper.convertValue(mapper.readTree(evidence).get("table"), new TypeReference<List<List<String>>>() {}); }
        catch (Exception e) { return null; }
    }
    private boolean validTable(List<List<String>> table) {
        if (table == null || table.size() < 2 || table.size() > 50 || table.get(0) == null) return false;
        int columns = table.get(0).size();
        if (columns < 1 || columns > 20) return false;
        if (table.stream().anyMatch(row -> row == null || row.size() != columns || row.stream().anyMatch(cell -> cell == null || cell.length() > 2000))) return false;
        return table.get(0).stream().allMatch(cell -> !cell.isBlank()) && table.stream().skip(1).flatMap(List::stream).anyMatch(cell -> !cell.isBlank());
    }
    private List<String> evidenceImages(String evidence) {
        if (evidence == null) return new ArrayList<>();
        try {
            Map<String, Object> parsed = mapper.readValue(evidence, new TypeReference<Map<String, Object>>() {});
            Object images = parsed.get("images");
            if (images instanceof List<?> list) return new ArrayList<>(list.stream().filter(String.class::isInstance)
                    .map(String.class::cast).filter(url -> !url.isBlank()).distinct().toList());
        } catch (Exception e) { throw new IllegalStateException("检查图片记录读取失败", e); }
        return new ArrayList<>();
    }
    private String json(Object value) {
        try { return mapper.writeValueAsString(value); } catch (Exception e) { throw new IllegalStateException(e); }
    }
    private ResponseStatusException error(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
}
