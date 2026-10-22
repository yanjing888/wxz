package com.wuxiaozhi.service;

import com.wuxiaozhi.dto.*;
import com.wuxiaozhi.entity.User;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.zip.*;

@Service
public class TeacherEvaluationExportService {
    private final TeacherService teachers;
    private final TeacherTeachingContextService contexts;
    private final ReportAttachmentService attachments;
    public TeacherEvaluationExportService(TeacherService teachers, TeacherTeachingContextService contexts, ReportAttachmentService attachments) {
        this.teachers = teachers; this.contexts = contexts; this.attachments = attachments;
    }
    private List<TeacherReportItemDto> reports(User teacher, String code) {
        var classes = contexts.assignedClasses(teacher, code);
        return teachers.listReports(teacher, code).stream().filter(row -> classes.contains(row.getStudentClass())).toList();
    }
    public byte[] grades(User teacher, String code) throws IOException {
        var classes = contexts.assignedClasses(teacher, code);
        var rows = teachers.classroom(teacher, code, null).getStudents().stream()
                .filter(row -> classes.contains(row.getStudentClass())).toList();
        var reports = reports(teacher, code);
        try (var book = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var sheet = book.createSheet("实验评价");
            String[] headers = {"姓名", "学号", "班级", "实验", "开始时间", "报告提交时间", "实验用时（分钟）", "实验报告得分", "AI得分", "过程考核总分", "状态"};
            var head = sheet.createRow(0);
            var style = book.createCellStyle(); style.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex()); style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            var font = book.createFont(); font.setBold(true); style.setFont(font);
            for (int column = 0; column < headers.length; column++) { var cell = head.createCell(column); cell.setCellValue(headers[column]); cell.setCellStyle(style); sheet.setColumnWidth(column, (column == 3 ? 38 : column == 4 || column == 5 ? 23 : 18) * 256); }
            for (var student : rows) {
                var report = reports.stream().filter(r -> r.getUserId().equals(student.getUserId()) && Objects.equals(r.getStudentClass(), student.getStudentClass())).findFirst().orElse(null);
                boolean submitted = report != null && report.isReportCompleted();
                Object[] values = {student.getStudentName(), student.getUsername(), student.getStudentClass(), student.getExperimentName(), time(student.getStartTime()),
                        report == null ? "" : time(report.getReportSubmittedAt()), student.getSessionId() == null ? "" : student.getMinutesOnSession(),
                        report == null ? null : report.getTeacherScore(), report == null ? null : report.getAiReviewScore(),
                        report == null ? student.getProcessScore() : report.getProcessScore(), report != null && report.isGradingCompleted() ? "已批改" : submitted ? "待批改" : "未提交"};
                var row = sheet.createRow(sheet.getLastRowNum() + 1);
                for (int col = 0; col < values.length; col++) { var cell = row.createCell(col); if (values[col] instanceof Number number) cell.setCellValue(number.doubleValue()); else cell.setCellValue(values[col] == null ? "" : values[col].toString()); }
            }
            sheet.createFreezePane(0, 1); sheet.setAutoFilter(new CellRangeAddress(0, sheet.getLastRowNum(), 0, headers.length - 1));
            book.write(output); return output.toByteArray();
        }
    }
    public Path reportArchive(User teacher, String code) throws Exception {
        var reports = reports(teacher, code);
        if (reports.isEmpty()) throw new ResponseStatusException(HttpStatus.CONFLICT, "当前实验暂无已提交的报告");
        Path target = Files.createTempFile("wxz-reports-", ".zip");
        try {
            try (var zip = new ZipOutputStream(Files.newOutputStream(target), java.nio.charset.StandardCharsets.UTF_8)) {
                for (var report : reports) {
                    byte[] bytes; String name;
                    if (report.getReportFileName() != null) {
                        bytes = attachments.read(teachers.getSessionForTeacher(teacher, report.getSessionId())).getData(); name = report.getReportFileName();
                    } else { bytes = teachers.getReportDocx(teacher, report.getSessionId()); name = "实验报告.docx"; }
                    String path = safe(report.getStudentClass()) + "/" + safe(report.getStudentName()) + "_" + report.getUserId() + "_" + report.getSessionId() + "_" + safe(name);
                    zip.putNextEntry(new ZipEntry(path)); zip.write(bytes); zip.closeEntry();
                }
            }
            return target;
        } catch (Exception e) { Files.deleteIfExists(target); throw e; }
    }
    private String safe(String value) { return (value == null ? "未命名" : value).replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").replace("..", "_"); }
    private String time(LocalDateTime value) { return value == null ? "" : value.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")); }
}
