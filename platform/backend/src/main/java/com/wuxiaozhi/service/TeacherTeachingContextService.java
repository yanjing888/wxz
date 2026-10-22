package com.wuxiaozhi.service;

import com.wuxiaozhi.entity.*;
import com.wuxiaozhi.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TeacherTeachingContextService {
    private final TeacherExperimentContextRepository contexts;
    private final ClassRosterService roster;
    private final ExperimentConfigService experiments;
    private final UserRepository users;
    private final StudentExperimentAssignmentRepository assignments;
    public TeacherTeachingContextService(TeacherExperimentContextRepository contexts, ClassRosterService roster,
                                         ExperimentConfigService experiments, UserRepository users,
                                         StudentExperimentAssignmentRepository assignments) {
        this.contexts = contexts; this.roster = roster; this.experiments = experiments; this.users = users;
        this.assignments = assignments;
    }
    public List<String> assignedClasses(User teacher, String code) {
        experiments.getByCode(code);
        var students = roster.students(teacher, "").stream().collect(Collectors.toMap(User::getId, user -> user));
        var visible = roster.visibleClasses(teacher);
        return assignments.findByExperimentCode(code).stream()
                .filter(assignment -> students.containsKey(assignment.getUserId()))
                .map(assignment -> assignment.getClassName() == null || assignment.getClassName().isBlank()
                        ? ClassRosterService.classKey(students.get(assignment.getUserId()).getStudentClass())
                        : assignment.getClassName().trim())
                .filter(visible::contains).distinct().sorted().toList();
    }
    public Map<String, Object> get(User teacher, String code) {
        experiments.getByCode(code);
        var classes = assignedClasses(teacher, code);
        String selected = contexts.findByTeacherIdAndExperimentCode(teacher.getId(), code)
                .map(TeacherExperimentContext::getClassName).orElse("");
        return Map.of("experimentCode", code, "className", classes.contains(selected) ? selected : "", "classes", classes);
    }
    @Transactional
    public Map<String, Object> select(User teacher, String code, String requested) {
        experiments.getByCode(code);
        String name = requested == null ? "" : requested.trim();
        if (!name.isBlank() && !assignedClasses(teacher, code).contains(name))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "请选择已分配当前实验且有权限管理的教学班");
        users.lockById(teacher.getId());
        var context = contexts.findByTeacherIdAndExperimentCode(teacher.getId(), code).orElseGet(TeacherExperimentContext::new);
        context.setTeacherId(teacher.getId()); context.setExperimentCode(code); context.setClassName(name);
        contexts.saveAndFlush(context);
        return get(teacher, code);
    }
}
