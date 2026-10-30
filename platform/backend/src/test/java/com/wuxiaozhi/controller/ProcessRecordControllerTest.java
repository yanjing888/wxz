package com.wuxiaozhi.controller;

import com.wuxiaozhi.entity.User;
import com.wuxiaozhi.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import java.util.Map;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class ProcessRecordControllerTest {
    @Test void usesOwnerAndTeacherScopeChecksAndReturnsUncachedDocx() throws Exception {
        var sessions = mock(LabSessionService.class); var teachers = mock(TeacherService.class);
        var reports = mock(ReportService.class); var files = mock(FileStorageService.class);
        var data = Map.<String,Object>of("sessionStatus", "FINISHED", "experimentName", "实验", "studentName", "学生");
        var auth = new UsernamePasswordAuthenticationToken(7L, null);
        var teacher = new User();
        when(sessions.buildReportData(12L, 7L)).thenReturn(data);
        when(teachers.requireTeacher(7L)).thenReturn(teacher);
        when(teachers.getReport(teacher, 12L)).thenReturn(data);
        when(reports.generateProcessRecordDocx(data, files)).thenReturn(new byte[]{1,2});
        var controller = new ProcessRecordController(sessions, teachers, reports, files);
        var response = controller.student(12L, auth);
        assertEquals("no-store", response.getHeaders().getCacheControl());
        assertTrue(response.getHeaders().getContentDisposition().getFilename().endsWith(".docx"));
        controller.teacher(12L, auth);
        verify(sessions).buildReportData(12L, 7L);
        verify(teachers).requireTeacher(7L); verify(teachers).getReport(teacher, 12L);
        verifyNoMoreInteractions(sessions, teachers);
    }
}
