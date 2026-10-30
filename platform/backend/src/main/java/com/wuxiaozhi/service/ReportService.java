package com.wuxiaozhi.service;

import com.wuxiaozhi.entity.CorrectionLog;
import com.wuxiaozhi.entity.EnvCheckLog;
import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblWidth;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTblWidth;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
public class ReportService {

    private static final DateTimeFormatter LOG_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter REPORT_TIME = DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH:mm:ss");
    private static final DateTimeFormatter REPORT_DATE = DateTimeFormatter.ofPattern("yyyy年MM月dd日");

    private final ReportHtmlDocxWriter htmlDocxWriter = new ReportHtmlDocxWriter();

    /** Independent source material; never submits or grades a student's report. */
    public byte[] generateProcessRecordDocx(Map<String, Object> report, FileStorageService files) throws Exception {
        if (!"FINISHED".equals(report.get("sessionStatus"))) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, "请先结束实验，再下载实验过程记录");
        }
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var page = doc.getDocument().getBody().addNewSectPr().addNewPgSz();
            page.setW(BigInteger.valueOf(11906)); page.setH(BigInteger.valueOf(16838));
            addCenterTitle(doc, "实验过程记录");
            XWPFTable info = doc.createTable(4, 4); setTableWidth(info, 9000);
            fillInfoRow(info, 0, "实验名称", str(report.get("experimentName")), "学生", str(report.get("studentName")));
            fillInfoRow(info, 1, "班级", str(report.get("studentClass")), "生成时间", recordTime(report.get("generatedAt")));
            fillInfoRow(info, 2, "开始时间", recordTime(report.get("startTime")), "结束时间", recordTime(report.get("endTime")));
            fillInfoRow(info, 3, "资料用途", "撰写实验报告参考", "记录说明", "再次下载可获取最新检查意见");
            addDataLogsSection(doc, report, "一、实验数据");
            addHeading(doc, "二、过程检查记录");
            if (report.get("processAssessment") instanceof Map<?, ?> assessment && assessment.get("steps") instanceof List<?> steps) {
                for (Object item : steps) {
                    if (!(item instanceof Map<?, ?> step)) continue;
                    addSectionHeading(doc, "步骤 " + str(step.get("stepId")) + "  " + str(step.get("title")));
                    String status = str(step.get("status"));
                    addBody(doc, "当前状态：" + switch(status) { case "PASSED" -> "检查通过"; case "RETURNED", "REVISION" -> "退回整改"; case "NOT_STARTED" -> "未提交"; default -> "待检查"; });
                    if (report.get("processCheckRecords") instanceof List<?> history) {
                        XWPFTable checks = null;
                        for (Object value : history) {
                            if (!(value instanceof Map<?, ?> check) || !java.util.Objects.equals(check.get("stepId"), step.get("stepId"))) continue;
                            if (checks == null) {
                                checks = doc.createTable(1, 4); setTableWidth(checks, 9000);
                                setHeaderRow(checks, new String[]{"时间", "结果", "得分 / 满分", "检查意见或提交说明"});
                            }
                            var row = checks.createRow();
                            setCellText(row.getCell(0), recordTime(check.get("time")));
                            setCellText(row.getCell(1), str(check.get("result")));
                            setCellText(row.getCell(2), check.get("score") == null ? "—" : str(check.get("score")) + " / " + str(check.get("maxScore")));
                            setCellText(row.getCell(3), nullToDash(check.get("note")));
                            int[] widths = {2100, 1300, 1300, 4300};
                            for (int c = 0; c < 4; c++) { row.getCell(c).setWidth(str(widths[c])); checks.getRow(0).getCell(c).setWidth(str(widths[c])); }
                        }
                    }
                    if (step.get("record") instanceof com.wuxiaozhi.entity.StepAssessment record) {
                        if (record.getEvidenceJson() != null) {
                            var evidence = new com.fasterxml.jackson.databind.ObjectMapper().readTree(record.getEvidenceJson());
                            var rows = evidence.path("table");
                            if (rows.isArray() && !rows.isEmpty()) {
                                int columns = 0; for (var row : rows) columns = Math.max(columns, row.size());
                                if (columns > 0) {
                                    addBody(doc, "提交的数据表");
                                    XWPFTable table = doc.createTable(rows.size(), columns); setTableWidth(table, 9000);
                                    for (int r = 0; r < rows.size(); r++) for (int c = 0; c < columns; c++) setCellText(table.getRow(r).getCell(c), rows.get(r).path(c).asText(""));
                                }
                            }
                            int index = 0;
                            for (var image : evidence.path("images")) {
                                addBody(doc, "检查图片 " + (++index));
                                try {
                                    var stored = files.read(image.asText());
                                    var bitmap = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(stored.getData()));
                                    if (bitmap == null) throw new java.io.IOException("Unsupported image");
                                    var png = new ByteArrayOutputStream(); javax.imageio.ImageIO.write(bitmap, "png", png);
                                    double scale = Math.min(360.0 / bitmap.getWidth(), 240.0 / bitmap.getHeight());
                                    var imageParagraph = doc.createParagraph(); imageParagraph.setAlignment(ParagraphAlignment.CENTER);
                                    imageParagraph.createRun().addPicture(new java.io.ByteArrayInputStream(png.toByteArray()), XWPFDocument.PICTURE_TYPE_PNG, "检查图片", org.apache.poi.util.Units.toEMU(bitmap.getWidth() * scale), org.apache.poi.util.Units.toEMU(bitmap.getHeight() * scale));
                                } catch (Exception e) { addBody(doc, "图片暂无法读取，请在系统的过程检查详情中查看。"); }
                            }
                        }
                    }
                }
            }
            if (report.get("corrections") instanceof List<?> list && !list.isEmpty()) addCorrectionsSection(doc, report, "三、操作纠错记录");
            else { addHeading(doc, "三、操作纠错记录"); addBody(doc, "暂无已保存的操作纠错记录。"); }
            addEnvLogsSection(doc, report, "四、环境安全巡检记录");
            for (XWPFTable table : doc.getTables()) {
                if (table == info) continue;
                table.getRow(0).setRepeatHeader(true); styleHeaderRow(table.getRow(0));
                for (var cell : table.getRow(0).getTableCells()) cell.setColor("F1F5F9");
            }
            addCenterNote(doc, "物小智智能实验平台 · 实验过程记录（不代替学生实验报告）");
            doc.write(out); return out.toByteArray();
        }
    }

    private String recordTime(Object value) {
        if (value == null) return "—";
        String time = str(value).replace('T', ' ');
        int dot = time.indexOf('.');
        return dot > 0 ? time.substring(0, dot) : time;
    }

    public byte[] generateDocx(Map<String, Object> report) throws Exception {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            addCenterTitle(doc, "实验总结报告");

            addCoverTable(doc, report);
            doc.createParagraph();

            addStepSummariesSection(doc, report);
            addStatsSection(doc, report);
            addCorrectionsSection(doc, report);
            addDataLogsSection(doc, report);
            addEnvLogsSection(doc, report);
            addBulletSection(doc, "6. 知识巩固建议", (List<String>) report.get("reportKnowledge"));
            addNumberedSection(doc, "7. 后续学习路径", (List<String>) report.get("reportPath"));
            addProcessChecks(doc, report);

            doc.createParagraph();
            addCenterNote(doc, "本报告由物小智智能实验平台根据本次实验过程数据自动生成。");

            doc.write(out);
            return out.toByteArray();
        }
    }

    /** 学生在报告助手里编辑的正文，按七段结构导出为 Word */
    public byte[] generateStudentReportDocx(Map<String, Object> meta, List<Map<String, String>> sections)
            throws Exception {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            addCenterTitle(doc, str(meta.get("experimentName")));
            addCenterSubtitle(doc, "大学物理实验报告");

            XWPFTable table = doc.createTable(2, 4);
            setTableWidth(table, 9000);
            fillInfoRow(table, 0, "姓名", str(meta.get("studentName")), "班级", str(meta.get("studentClass")));
            fillInfoRow(table, 1, "实验名称", str(meta.get("experimentName")), "日期",
                    LocalDateTime.now().format(REPORT_DATE));
            styleInfoTable(table);
            doc.createParagraph();

            for (Map<String, String> section : sections) {
                String label = section.getOrDefault("label", "");
                String contentHtml = section.getOrDefault("contentHtml", "");
                String content = section.getOrDefault("content", "");
                addSectionHeading(doc, label);
                if (!contentHtml.isBlank()) {
                    htmlDocxWriter.writeSectionContent(doc, contentHtml);
                } else if (!content.isBlank()) {
                    htmlDocxWriter.writeSectionContent(doc, content);
                } else {
                    addBody(doc, "（待补充）");
                }
            }

            doc.createParagraph();
            addProcessChecks(doc, meta);
            addCenterNote(doc, "本报告正文由学生撰写，过程检查记录由平台汇总，物小智智能实验平台负责排版导出。");

            doc.write(out);
            return out.toByteArray();
        }
    }

    private void addProcessChecks(XWPFDocument doc, Map<String, Object> report) {
        addSectionHeading(doc, "附录 过程检查记录汇总");
        if (!(report.get("processCheckRecords") instanceof List<?> records) || records.isEmpty()) {
            addBody(doc, "暂无过程检查记录。");
            return;
        }
        Object lastStep = null;
        XWPFTable table = null;
        for (Object value : records) {
            if (!(value instanceof Map<?, ?> record)) continue;
            if (!java.util.Objects.equals(lastStep, record.get("stepId"))) {
                lastStep = record.get("stepId");
                addSectionHeading(doc, "步骤 " + str(lastStep) + "  " + str(record.get("stepTitle")));
                table = doc.createTable(1, 4); setTableWidth(table, 9000);
                setHeaderRow(table, new String[]{"时间", "检查结果", "得分 / 满分", "检查意见或提交说明"});
                table.getRow(0).setRepeatHeader(true);
            }
            var row = table.createRow();
            setCellText(row.getCell(0), str(record.get("time")).replace('T', ' '));
            setCellText(row.getCell(1), str(record.get("result")));
            setCellText(row.getCell(2), record.get("score") == null ? "—" : str(record.get("score")) + " / " + str(record.get("maxScore")));
            setCellText(row.getCell(3), str(record.get("note")).isBlank() ? "—" : str(record.get("note")));
            int[] widths = {2200, 1400, 1400, 4000};
            for (int i = 0; i < 4; i++) { row.getCell(i).setWidth(String.valueOf(widths[i])); table.getRow(0).getCell(i).setWidth(String.valueOf(widths[i])); }
        }
    }

    private void addCenterSubtitle(XWPFDocument doc, String text) {
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(ParagraphAlignment.CENTER);
        p.setSpacingAfter(200);
        XWPFRun run = p.createRun();
        run.setFontFamily("黑体");
        run.setFontSize(14);
        run.setText(text);
    }

    private void fillInfoRow(XWPFTable table, int rowIdx, String k1, String v1, String k2, String v2) {
        XWPFTableRow row = table.getRow(rowIdx);
        setCellText(row.getCell(0), k1, true);
        setCellText(row.getCell(1), v1);
        setCellText(row.getCell(2), k2, true);
        setCellText(row.getCell(3), v2);
    }

    private void styleInfoTable(XWPFTable table) {
        for (XWPFTableRow row : table.getRows()) {
            for (int i = 0; i < row.getTableCells().size(); i += 2) {
                setCellText(row.getCell(i), row.getCell(i).getText(), true);
            }
        }
    }

    private void addSectionHeading(XWPFDocument doc, String text) {
        XWPFParagraph p = doc.createParagraph();
        p.setSpacingBefore(240);
        p.setSpacingAfter(120);
        XWPFRun run = p.createRun();
        run.setFontFamily("黑体");
        run.setBold(true);
        run.setFontSize(14);
        run.setText(text != null ? text : "");
    }

    private void addCoverTable(XWPFDocument doc, Map<String, Object> report) {
        XWPFTable table = doc.createTable(5, 2);
        setTableWidth(table, 9000);
        fillRow(table, 0, "实验名称", str(report.get("experimentName")));
        fillRow(table, 1, "学生姓名", str(report.get("studentName")));
        fillRow(table, 2, "班级", str(report.get("studentClass")));
        fillRow(table, 3, "生成时间", formatGeneratedAt(report.get("generatedAt")));
        fillRow(table, 4, "会话编号", str(report.get("sessionId")));
        styleLabelValueTable(table);
    }

    @SuppressWarnings("unchecked")
    private void addStepSummariesSection(XWPFDocument doc, Map<String, Object> report) {
        addHeading(doc, "1. 实验步骤回顾");
        List<Map<String, Object>> steps = (List<Map<String, Object>>) report.get("stepSummaries");
        if (steps == null || steps.isEmpty()) {
            addBody(doc, "暂无步骤说明。");
            return;
        }
        for (Map<String, Object> step : steps) {
            String no = str(step.get("stepNo"));
            String title = str(step.get("title"));
            String desc = str(step.get("desc"));
            addBody(doc, no + ". " + title + (desc.isBlank() ? "" : "：" + desc));
        }
    }

    private void addStatsSection(XWPFDocument doc, Map<String, Object> report) {
        addHeading(doc, "2. 实操回顾与数据统计");
        XWPFTable table = doc.createTable(3, 4);
        setTableWidth(table, 9000);
        setHeaderRow(table, new String[]{"指标", "数值", "指标", "数值"});
        fillRow(table, 1, "有效纠错次数", str(report.get("helpCount")), "纠错标注点数", str(report.get("errorPointCount")));
        fillRow(table, 2, "教程查阅次数", str(report.get("tutViewCount")), "严重告警 (L2)", str(report.get("labL3Count")));
        styleDataTable(table);
    }

    @SuppressWarnings("unchecked")
    private void addCorrectionsSection(XWPFDocument doc, Map<String, Object> report) {
        addCorrectionsSection(doc, report, "3. 操作纠错记录");
    }

    @SuppressWarnings("unchecked")
    private void addCorrectionsSection(XWPFDocument doc, Map<String, Object> report, String title) {
        addHeading(doc, title);
        List<CorrectionLog> corrections = (List<CorrectionLog>) report.get("corrections");
        if (corrections == null || corrections.isEmpty()) {
            addBody(doc, "操作表现优异！整个实验过程中未发现明显操作逻辑错误。");
            return;
        }
        XWPFTable table = doc.createTable(corrections.size() + 1, 5);
        setTableWidth(table, 9000);
        setHeaderRow(table, new String[]{"序号", "步骤", "错误类型", "时间", "问题描述"});
        for (int i = 0; i < corrections.size(); i++) {
            CorrectionLog log = corrections.get(i);
            XWPFTableRow row = table.getRow(i + 1);
            setCellText(row.getCell(0), String.valueOf(i + 1));
            setCellText(row.getCell(1), nullToDash(log.getStepTitle()));
            setCellText(row.getCell(2), nullToDash(log.getErrorType()));
            setCellText(row.getCell(3), log.getCreatedAt() != null ? log.getCreatedAt().format(LOG_TIME) : "—");
            setCellText(row.getCell(4), nullToDash(log.getDetail()));
        }
        styleDataTable(table);
    }

    @SuppressWarnings("unchecked")
    private void addDataLogsSection(XWPFDocument doc, Map<String, Object> report) {
        addDataLogsSection(doc, report, "4. 实验数据记录");
    }

    @SuppressWarnings("unchecked")
    private void addDataLogsSection(XWPFDocument doc, Map<String, Object> report, String title) {
        addHeading(doc, title);
        List<Map<String, Object>> entries = (List<Map<String, Object>>) report.get("dataLogEntries");
        if (entries == null || entries.isEmpty()) {
            addBody(doc, "本次实验未提交结构化实验数据。");
            return;
        }
        XWPFTable table = doc.createTable(entries.size() + 1, 4);
        setTableWidth(table, 9000);
        setHeaderRow(table, new String[]{"步骤", "提交时间", "采集数据", "校验结果"});
        for (int i = 0; i < entries.size(); i++) {
            Map<String, Object> entry = entries.get(i);
            XWPFTableRow row = table.getRow(i + 1);
            setCellText(row.getCell(0), nullToDash(entry.get("stepTitle")));
            setCellText(row.getCell(1), nullToDash(entry.get("submittedAt")));
            String values = nullToDash(entry.get("valuesSummary"));
            if (entry.get("values") instanceof Map<?, ?> data && report.get("stepSchemas") instanceof Map<?, ?> schemas
                    && schemas.get(str(entry.get("stepId"))) instanceof Map<?, ?> schema && schema.get("fields") instanceof List<?> fields) {
                java.util.Map<String, String> labels = new java.util.HashMap<>();
                for (Object field : fields) if (field instanceof Map<?, ?> f) labels.put(str(f.get("key")), str(f.get("label")));
                values = data.entrySet().stream().map(e -> labels.getOrDefault(str(e.getKey()), str(e.getKey())) + "：" + str(e.getValue())).collect(java.util.stream.Collectors.joining("；"));
            }
            setCellText(row.getCell(2), values);
            setCellText(row.getCell(3), nullToDash(entry.get("validationSummary")));
        }
        styleDataTable(table);
    }

    @SuppressWarnings("unchecked")
    private void addEnvLogsSection(XWPFDocument doc, Map<String, Object> report) {
        addEnvLogsSection(doc, report, "5. 环境安全巡检记录");
    }

    @SuppressWarnings("unchecked")
    private void addEnvLogsSection(XWPFDocument doc, Map<String, Object> report, String title) {
        addHeading(doc, title);
        List<EnvCheckLog> envLogs = (List<EnvCheckLog>) report.get("envLogs");
        if (envLogs == null || envLogs.isEmpty()) {
            addBody(doc, "本次实验未产生环境巡检记录（未开启安全监测或未触发巡检）。");
            return;
        }
        XWPFTable table = doc.createTable(envLogs.size() + 1, 5);
        setTableWidth(table, 9000);
        setHeaderRow(table, new String[]{"巡检时间", "安全等级", "判断依据", "处置建议", "抽帧画面"});
        for (int i = 0; i < envLogs.size(); i++) {
            EnvCheckLog log = envLogs.get(i);
            XWPFTableRow row = table.getRow(i + 1);
            setCellText(row.getCell(0), log.getCreatedAt() != null ? log.getCreatedAt().format(LOG_TIME) : "—");
            setCellText(row.getCell(1), formatEnvLevel(log.getLevel()));
            setCellText(row.getCell(2), nullToDash(log.getSummary()));
            setCellText(row.getCell(3), nullToDash(log.getSuggestion()));
            boolean hasSnapshot = log.getSnapshotUrl() != null && !log.getSnapshotUrl().isBlank();
            setCellText(row.getCell(4), hasSnapshot ? "已采集" : "未采集");
        }
        styleDataTable(table);
    }

    private void addBulletSection(XWPFDocument doc, String heading, List<String> items) {
        addHeading(doc, heading);
        if (items == null || items.isEmpty()) {
            addBody(doc, "暂无内容。");
            return;
        }
        for (String item : items) {
            addBody(doc, "• " + item);
        }
    }

    private void addNumberedSection(XWPFDocument doc, String heading, List<String> items) {
        addHeading(doc, heading);
        if (items == null || items.isEmpty()) {
            addBody(doc, "暂无内容。");
            return;
        }
        for (int i = 0; i < items.size(); i++) {
            addBody(doc, (i + 1) + ". " + items.get(i));
        }
    }

    private void addCenterTitle(XWPFDocument doc, String text) {
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun run = p.createRun();
        run.setBold(true);
        run.setFontSize(18);
        run.setText(text);
        p.setSpacingAfter(200);
    }

    private void addCenterNote(XWPFDocument doc, String text) {
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun run = p.createRun();
        run.setFontSize(10);
        run.setColor("64748B");
        run.setText(text);
    }

    private void addHeading(XWPFDocument doc, String text) {
        XWPFParagraph p = doc.createParagraph();
        p.setSpacingBefore(240);
        p.setSpacingAfter(120);
        XWPFRun run = p.createRun();
        run.setBold(true);
        run.setFontSize(14);
        run.setText(text);
    }

    private void addBody(XWPFDocument doc, String text) {
        XWPFParagraph p = doc.createParagraph();
        p.setSpacingAfter(80);
        XWPFRun run = p.createRun();
        run.setFontSize(11);
        run.setText(text != null ? text : "");
    }

    private void setHeaderRow(XWPFTable table, String[] headers) {
        XWPFTableRow row = table.getRow(0);
        for (int i = 0; i < headers.length; i++) {
            setCellText(row.getCell(i), headers[i], true);
        }
    }

    private void fillRow(XWPFTable table, int rowIdx, String k1, String v1) {
        setCellText(table.getRow(rowIdx).getCell(0), k1, true);
        setCellText(table.getRow(rowIdx).getCell(1), v1);
    }

    private void fillRow(XWPFTable table, int rowIdx, String k1, String v1, String k2, String v2) {
        setCellText(table.getRow(rowIdx).getCell(0), k1, true);
        setCellText(table.getRow(rowIdx).getCell(1), v1);
        setCellText(table.getRow(rowIdx).getCell(2), k2, true);
        setCellText(table.getRow(rowIdx).getCell(3), v2);
    }

    private void styleLabelValueTable(XWPFTable table) {
        for (XWPFTableRow row : table.getRows()) {
            setCellText(row.getCell(0), row.getCell(0).getText(), true);
        }
    }

    private void styleDataTable(XWPFTable table) {
        if (!table.getRows().isEmpty()) {
            styleHeaderRow(table.getRow(0));
        }
        for (int r = 1; r < table.getRows().size(); r++) {
            for (XWPFTableCell cell : table.getRow(r).getTableCells()) {
                for (XWPFParagraph p : cell.getParagraphs()) {
                    p.setSpacingAfter(40);
                }
            }
        }
    }

    private void styleHeaderRow(XWPFTableRow row) {
        for (XWPFTableCell cell : row.getTableCells()) {
            setCellText(cell, cell.getText(), true);
        }
    }

    private void setCellText(XWPFTableCell cell, String text) {
        setCellText(cell, text, false);
    }

    private void setCellText(XWPFTableCell cell, String text, boolean bold) {
        if (cell.getParagraphs().isEmpty()) {
            cell.addParagraph();
        }
        XWPFParagraph p = cell.getParagraphs().get(0);
        while (p.getRuns().size() > 0) {
            p.removeRun(0);
        }
        XWPFRun run = p.createRun();
        run.setFontSize(10);
        run.setBold(bold);
        run.setText(text != null ? text : "");
    }

    private void setTableWidth(XWPFTable table, int width) {
        if (table.getCTTbl().getTblPr() == null) {
            table.getCTTbl().addNewTblPr();
        }
        CTTblWidth tblWidth = table.getCTTbl().getTblPr().isSetTblW()
                ? table.getCTTbl().getTblPr().getTblW()
                : table.getCTTbl().getTblPr().addNewTblW();
        tblWidth.setType(STTblWidth.DXA);
        tblWidth.setW(BigInteger.valueOf(width));
    }

    private String formatGeneratedAt(Object value) {
        if (value == null) {
            return LocalDateTime.now().format(REPORT_TIME);
        }
        String text = String.valueOf(value);
        try {
            return LocalDateTime.parse(text).format(REPORT_TIME);
        } catch (Exception ignored) {
            return text;
        }
    }

    private String formatEnvLevel(String level) {
        if (level == null || level.isBlank()) return "—";
        return switch (level) {
            case "L0" -> "L0 正常";
            case "L1" -> "L1 注意";
            case "L2" -> "L2 严重";
            case "L3" -> "L2 严重";
            case "NA" -> "不可用";
            default -> level;
        };
    }

    private String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String nullToDash(Object value) {
        if (value == null) return "—";
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? "—" : text;
    }
}
