package com.wuxiaozhi.service;

import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblWidth;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTblWidth;

import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 将学生报告编辑器中的 HTML 片段写入 Word（段落、表格、图片、基础行内样式）。
 */
public class ReportHtmlDocxWriter {

    private static final Pattern TABLE_PATTERN = Pattern.compile(
            "<table[^>]*>(.*?)</table>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern IMG_PATTERN = Pattern.compile(
            "<img[^>]+src=[\"'](data:image/[^\"']+)[\"'][^>]*>", Pattern.CASE_INSENSITIVE);

    public void writeSectionContent(XWPFDocument doc, String htmlOrText) {
        if (htmlOrText == null || htmlOrText.isBlank()) {
            addIndentedParagraph(doc, "（待补充）", false);
            return;
        }
        String html = htmlOrText.trim();
        if (!html.contains("<")) {
            writePlainText(doc, html);
            return;
        }

        String remaining = html;
        Matcher tableMatcher = TABLE_PATTERN.matcher(remaining);
        int lastEnd = 0;
        boolean foundTable = false;
        while (tableMatcher.find()) {
            foundTable = true;
            if (tableMatcher.start() > lastEnd) {
                writeInlineHtml(doc, remaining.substring(lastEnd, tableMatcher.start()));
            }
            writeHtmlTable(doc, tableMatcher.group(0));
            lastEnd = tableMatcher.end();
        }
        if (foundTable) {
            if (lastEnd < remaining.length()) {
                writeInlineHtml(doc, remaining.substring(lastEnd));
            }
            return;
        }
        writeInlineHtml(doc, remaining);
    }

    private void writePlainText(XWPFDocument doc, String text) {
        for (String line : text.split("\\r?\\n")) {
            addIndentedParagraph(doc, stripTags(line), false);
        }
    }

    private void writeInlineHtml(XWPFDocument doc, String fragment) {
        if (fragment == null || fragment.isBlank()) {
            return;
        }
        String cleaned = fragment
                .replaceAll("(?i)</p>\\s*", "\n")
                .replaceAll("(?i)<p[^>]*>", "")
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</li>\\s*", "\n")
                .replaceAll("(?i)<li[^>]*>", "• ")
                .replaceAll("(?i)</?ul[^>]*>", "")
                .replaceAll("(?i)</?strong>", "")
                .replaceAll("(?i)</?b>", "")
                .replaceAll("(?i)</?em>", "")
                .replaceAll("(?i)</?i>", "");

        Matcher imgMatcher = IMG_PATTERN.matcher(cleaned);
        int imgLast = 0;
        while (imgMatcher.find()) {
            if (imgMatcher.start() > imgLast) {
                writeTextRuns(doc, cleaned.substring(imgLast, imgMatcher.start()));
            }
            writeBase64Image(doc, imgMatcher.group(1));
            imgLast = imgMatcher.end();
        }
        if (imgLast < cleaned.length()) {
            writeTextRuns(doc, cleaned.substring(imgLast));
        }
    }

    private void writeTextRuns(XWPFDocument doc, String text) {
        String plain = decodeEntities(stripTags(text)).trim();
        if (plain.isEmpty()) {
            return;
        }
        for (String line : plain.split("\\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            XWPFParagraph p = doc.createParagraph();
            p.setIndentationFirstLine(420);
            p.setSpacingAfter(80);
            XWPFRun run = p.createRun();
            run.setFontFamily("宋体");
            run.setFontSize(12);
            run.setText(trimmed);
        }
    }

    private void writeHtmlTable(XWPFDocument doc, String tableHtml) {
        List<List<String>> rows = parseTableRows(tableHtml);
        if (rows.isEmpty()) {
            return;
        }
        int cols = rows.stream().mapToInt(List::size).max().orElse(0);
        if (cols == 0) {
            return;
        }
        XWPFTable table = doc.createTable(rows.size(), cols);
        setTableWidth(table, 9000);
        for (int r = 0; r < rows.size(); r++) {
            List<String> row = rows.get(r);
            for (int c = 0; c < cols; c++) {
                String text = c < row.size() ? row.get(c) : "";
                setCellText(table.getRow(r).getCell(c), text, r == 0);
            }
        }
        doc.createParagraph().setSpacingAfter(120);
    }

    private List<List<String>> parseTableRows(String tableHtml) {
        List<List<String>> rows = new ArrayList<>();
        Matcher rowMatcher = Pattern.compile("<tr[^>]*>(.*?)</tr>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL)
                .matcher(tableHtml);
        while (rowMatcher.find()) {
            String rowHtml = rowMatcher.group(1);
            List<String> cells = new ArrayList<>();
            Matcher cellMatcher = Pattern.compile("<t[hd][^>]*>(.*?)</t[hd]>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL)
                    .matcher(rowHtml);
            while (cellMatcher.find()) {
                cells.add(decodeEntities(stripTags(cellMatcher.group(1))).trim());
            }
            if (!cells.isEmpty()) {
                rows.add(cells);
            }
        }
        return rows;
    }

    private void writeBase64Image(XWPFDocument doc, String dataUrl) {
        try {
            int comma = dataUrl.indexOf(',');
            if (comma < 0) {
                return;
            }
            String meta = dataUrl.substring(0, comma);
            byte[] bytes = Base64.getDecoder().decode(dataUrl.substring(comma + 1));
            int pictureType = meta.contains("png") ? XWPFDocument.PICTURE_TYPE_PNG : XWPFDocument.PICTURE_TYPE_JPEG;
            XWPFParagraph p = doc.createParagraph();
            p.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun run = p.createRun();
            run.addPicture(new ByteArrayInputStream(bytes), pictureType, "image", Units.toEMU(360), Units.toEMU(240));
            p.setSpacingAfter(120);
        } catch (Exception ignored) {
            addIndentedParagraph(doc, "（图片无法嵌入 Word）", false);
        }
    }

    private void addIndentedParagraph(XWPFDocument doc, String text, boolean bold) {
        XWPFParagraph p = doc.createParagraph();
        p.setIndentationFirstLine(420);
        p.setSpacingAfter(80);
        XWPFRun run = p.createRun();
        run.setFontFamily("宋体");
        run.setFontSize(12);
        run.setBold(bold);
        run.setText(text != null ? text : "");
    }

    private void setCellText(XWPFTableCell cell, String text, boolean bold) {
        if (cell.getParagraphs().isEmpty()) {
            cell.addParagraph();
        }
        XWPFParagraph p = cell.getParagraphs().get(0);
        while (!p.getRuns().isEmpty()) {
            p.removeRun(0);
        }
        XWPFRun run = p.createRun();
        run.setFontFamily("宋体");
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

    private String stripTags(String html) {
        if (html == null) {
            return "";
        }
        return html.replaceAll("<[^>]+>", "").trim();
    }

    private String decodeEntities(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replace("&nbsp;", " ")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
                .replace("&quot;", "\"");
    }
}
