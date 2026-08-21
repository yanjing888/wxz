package com.wuxiaozhi.service;

import com.wuxiaozhi.entity.StudentExperimentAssignment;
import com.wuxiaozhi.entity.User;
import com.wuxiaozhi.entity.UserRole;
import com.wuxiaozhi.repository.StudentExperimentAssignmentRepository;
import com.wuxiaozhi.repository.UserRepository;
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

    public StudentExperimentAccessService(StudentExperimentAssignmentRepository assignmentRepository,
                                          UserRepository userRepository) {
        this.assignmentRepository = assignmentRepository;
        this.userRepository = userRepository;
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
        return assignmentRepository.findByUserIdOrderByExperimentCodeAsc(userId).stream()
                .map(StudentExperimentAssignment::getExperimentCode)
                .collect(Collectors.toCollection(LinkedHashSet::new));
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
