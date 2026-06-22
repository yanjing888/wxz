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

            doc.createParagraph();
            addCenterNote(doc, "本报告由物小智智能实验平台根据本次实验过程数据自动生成。");

            doc.write(out);
            return out.toByteArray();
        }
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
        addHeading(doc, "3. 操作纠错记录");
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
        addHeading(doc, "4. 实验数据记录");
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
            setCellText(row.getCell(2), nullToDash(entry.get("valuesSummary")));
            setCellText(row.getCell(3), nullToDash(entry.get("validationSummary")));
        }
        styleDataTable(table);
    }

    @SuppressWarnings("unchecked")
    private void addEnvLogsSection(XWPFDocument doc, Map<String, Object> report) {
        addHeading(doc, "5. 环境安全巡检记录");
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
