package com.wuxiaozhi.service;

import com.wuxiaozhi.entity.StudentExperimentAssignment;
import com.wuxiaozhi.entity.User;
import com.wuxiaozhi.entity.UserRole;
import com.wuxiaozhi.repository.StudentExperimentAssignmentRepository;
import com.wuxiaozhi.repository.UserRepository;
import com.wuxiaozhi.repository.LabSessionRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class StudentExperimentAccessService {

    private static final String DEFAULT_STUDENT_USERNAME = "test01";
    private static final List<String> DEFAULT_EXPERIMENTS = List.of(
            "newton_rings",
            "air_wedge_thickness",
            "microscope_length_measurement"
    );

    private final StudentExperimentAssignmentRepository assignmentRepository;
    private final UserRepository userRepository;
    private final LabSessionRepository sessions;

    public StudentExperimentAccessService(StudentExperimentAssignmentRepository assignmentRepository,
                                          UserRepository userRepository, LabSessionRepository sessions) {
        this.assignmentRepository = assignmentRepository;
        this.userRepository = userRepository;
        this.sessions=sessions;
    }

    @PostConstruct
    public void ensureDefaultStudentAssignment() {
        userRepository.findByUsername(DEFAULT_STUDENT_USERNAME).ifPresent(user -> {
            for (String code : DEFAULT_EXPERIMENTS) {
                if (!assignmentRepository.existsByUserIdAndExperimentCode(user.getId(), code)) {
                    StudentExperimentAssignment assignment = new StudentExperimentAssignment();
                    assignment.setUserId(user.getId());
                    assignment.setExperimentCode(code);
                    assignment.setAssignedByUserId(user.getId());
                    assignmentRepository.save(assignment);
                }
            }
        });
    }

    public Set<String> assignedCodes(Long userId) {
        return assignmentRepository.findByUserIdOrderByAssignedAtAscIdAsc(userId).stream()
                .map(StudentExperimentAssignment::getExperimentCode)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** 多个实验按分配顺序排队；已开始的未完成实验优先，报告不占实操队列。 */
    public java.util.Optional<StudentExperimentAssignment> currentAssignment(Long userId) {
        var assigned=assignmentRepository.findByUserIdOrderByAssignedAtAscIdAsc(userId);
        var history=sessions.findByUserIdOrderByStartTimeDesc(userId);
        var finished=history.stream().filter(s->"FINISHED".equals(s.getStatus())).map(com.wuxiaozhi.entity.LabSession::getExperimentCode).collect(Collectors.toSet());
        var pending=assigned.stream().filter(a->!finished.contains(a.getExperimentCode())).toList();
        for(var session:history)if("ACTIVE".equals(session.getStatus())){
            var active=pending.stream().filter(a->a.getExperimentCode().equals(session.getExperimentCode())).findFirst();
            if(active.isPresent())return active;
        }
        return pending.stream().findFirst();
    }

    @org.springframework.transaction.annotation.Transactional
    public void requireCurrentIfStudent(Long userId,String code) {
        User user=userRepository.lockById(userId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"用户不存在"));
        if(!UserRole.STUDENT.equalsIgnoreCase(user.getRole()))return;
        requireAssignedIfStudent(userId,code);
        var current=currentAssignment(userId);
        if(current.isEmpty()||!current.get().getExperimentCode().equals(code))throw new ResponseStatusException(HttpStatus.CONFLICT,"请先完成当前实验，其他已分配实验按顺序等待；待交报告可在报告中心继续提交");
    }

    public String assignmentClass(Long userId,String code) {
        var assignment=assignmentRepository.findByUserIdAndExperimentCode(userId,code).orElse(null);
        if(assignment!=null&&assignment.getClassName()!=null&&!assignment.getClassName().isBlank())return assignment.getClassName();
        return userRepository.findById(userId).map(u->ClassRosterService.classKey(u.getStudentClass())).orElse("未分班");
    }

    public boolean isAssigned(Long userId, String experimentCode) {
        return assignmentRepository.existsByUserIdAndExperimentCode(userId, experimentCode);
    }

    public void requireAssignedIfStudent(Long userId, String experimentCode) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在"));
        if (UserRole.STUDENT.equalsIgnoreCase(user.getRole())
                && !isAssigned(userId, experimentCode)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "该实验未分配给您，请联系教师");
        }
    }

    public List<StudentExperimentAssignment> findByUserIds(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        return assignmentRepository.findByUserIdIn(userIds);
    }
}
