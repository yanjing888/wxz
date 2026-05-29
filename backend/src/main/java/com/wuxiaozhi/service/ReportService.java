package com.wuxiaozhi.service;

import com.wuxiaozhi.entity.CorrectionLog;
import com.wuxiaozhi.entity.EnvCheckLog;
import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblWidth;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTblWidth;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
public class ReportService {

    public byte[] generateDocx(Map<String, Object> report) throws Exception {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XWPFParagraph title = doc.createParagraph();
            title.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun titleRun = title.createRun();
            titleRun.setBold(true);
            titleRun.setFontSize(18);
            titleRun.setText("实验总结报告");

            addParagraph(doc, "实验名称：" + report.get("experimentName"));
            addParagraph(doc, "学生：" + report.get("studentName") + "    班级：" + report.get("studentClass"));
            addParagraph(doc, "生成时间：" + report.get("generatedAt"));

            addHeading(doc, "1. 实操回顾与数据统计");
            addParagraph(doc, "有效纠错次数：" + report.get("helpCount"));
            addParagraph(doc, "纠错标注点数：" + report.get("errorPointCount"));
            addParagraph(doc, "教程查阅次数：" + report.get("tutViewCount"));
            addParagraph(doc, "严重告警(L3)：" + report.get("labL3Count"));

            @SuppressWarnings("unchecked")
            List<CorrectionLog> corrections = (List<CorrectionLog>) report.get("corrections");
            addHeading(doc, "2. 操作纠错记录");
            if (corrections == null || corrections.isEmpty()) {
                addParagraph(doc, "操作表现优异，未发现明显操作逻辑错误。");
            } else {
                for (CorrectionLog log : corrections) {
                    addParagraph(doc, "步骤：" + log.getStepTitle());
                    addParagraph(doc, "类型：" + log.getErrorType());
                    addParagraph(doc, "时间：" + log.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                    addParagraph(doc, "详情：" + log.getDetail());
                    addParagraph(doc, "反馈：" + stripMarkdown(log.getFeedback()));
                    addParagraph(doc, "");
                }
            }

            @SuppressWarnings("unchecked")
            List<EnvCheckLog> envLogs = (List<EnvCheckLog>) report.get("envLogs");
            addHeading(doc, "3. 环境巡检记录");
            if (envLogs == null || envLogs.isEmpty()) {
                addParagraph(doc, "暂无巡检记录。");
            } else {
                XWPFTable table = doc.createTable(envLogs.size() + 1, 3);
                setTableWidth(table, 9000);
                setCellText(table.getRow(0).getCell(0), "时间");
                setCellText(table.getRow(0).getCell(1), "等级");
                setCellText(table.getRow(0).getCell(2), "摘要");
                for (int i = 0; i < envLogs.size(); i++) {
                    EnvCheckLog log = envLogs.get(i);
                    XWPFTableRow row = table.getRow(i + 1);
                    setCellText(row.getCell(0), log.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
                    setCellText(row.getCell(1), log.getLevel());
                    setCellText(row.getCell(2), log.getSummary() + (log.getSuggestion() != null ? "；" + log.getSuggestion() : ""));
                }
            }

            @SuppressWarnings("unchecked")
            List<String> knowledge = (List<String>) report.get("reportKnowledge");
            addHeading(doc, "4. 知识巩固建议");
            if (knowledge != null) {
                for (String item : knowledge) {
                    addParagraph(doc, "• " + item);
                }
            }

            @SuppressWarnings("unchecked")
            List<String> path = (List<String>) report.get("reportPath");
            addHeading(doc, "5. 后续学习路径");
            if (path != null) {
                for (int i = 0; i < path.size(); i++) {
                    addParagraph(doc, (i + 1) + ". " + path.get(i));
                }
            }

            doc.write(out);
            return out.toByteArray();
        }
    }

    private void addHeading(XWPFDocument doc, String text) {
        XWPFParagraph p = doc.createParagraph();
        p.setSpacingBefore(200);
        XWPFRun run = p.createRun();
        run.setBold(true);
        run.setFontSize(14);
        run.setText(text);
    }

    private void addParagraph(XWPFDocument doc, String text) {
        XWPFParagraph p = doc.createParagraph();
        XWPFRun run = p.createRun();
        run.setFontSize(11);
        run.setText(text != null ? text : "");
    }

    private void setCellText(XWPFTableCell cell, String text) {
        cell.removeParagraph(0);
        XWPFParagraph p = cell.addParagraph();
        XWPFRun run = p.createRun();
        run.setFontSize(10);
        run.setText(text != null ? text : "");
    }

    private void setTableWidth(XWPFTable table, int width) {
        CTTblWidth tblWidth = table.getCTTbl().addNewTblPr().addNewTblW();
        tblWidth.setType(STTblWidth.DXA);
        tblWidth.setW(BigInteger.valueOf(width));
    }

    private String stripMarkdown(String text) {
        if (text == null) return "";
        return text.replace("**", "").replace("*", "");
    }
}
