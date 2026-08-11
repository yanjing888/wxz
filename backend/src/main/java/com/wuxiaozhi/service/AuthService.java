package com.wuxiaozhi.service;

import com.wuxiaozhi.dto.AuthResponse;
import com.wuxiaozhi.dto.LoginRequest;
import com.wuxiaozhi.dto.RegisterRequest;
import com.wuxiaozhi.dto.ResetPasswordRequest;
import com.wuxiaozhi.entity.User;
import com.wuxiaozhi.entity.UserRole;
import com.wuxiaozhi.repository.UserRepository;
import com.wuxiaozhi.security.JwtUtil;
import jakarta.annotation.PostConstruct;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    private static final String DEFAULT_USERNAME = "test01";
    private static final String DEFAULT_PASSWORD = "test01";
    private static final String DEFAULT_TEACHER_USERNAME = "teacher01";
    private static final String DEFAULT_TEACHER_PASSWORD = "teacher01";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    @PostConstruct
    public void ensureDefaultUsers() {
        userRepository.findAll().forEach(user -> {
            if (user.getRole() == null || user.getRole().isBlank()) {
                user.setRole(UserRole.STUDENT);
                userRepository.save(user);
            }
        });
        if (!userRepository.existsByUsername(DEFAULT_USERNAME)) {
            User user = new User();
            user.setUsername(DEFAULT_USERNAME);
            user.setPasswordHash(passwordEncoder.encode(DEFAULT_PASSWORD));
            user.setDisplayName("测试用户");
            user.setStudentClass("物理2401");
            user.setRole(UserRole.STUDENT);
            userRepository.save(user);
        }
        if (!userRepository.existsByUsername(DEFAULT_TEACHER_USERNAME)) {
            User teacher = new User();
            teacher.setUsername(DEFAULT_TEACHER_USERNAME);
            teacher.setPasswordHash(passwordEncoder.encode(DEFAULT_TEACHER_PASSWORD));
            teacher.setDisplayName("测试教师");
            teacher.setStudentClass("");
            teacher.setRole(UserRole.TEACHER);
            userRepository.save(teacher);
        }
    }

    public AuthResponse register(RegisterRequest req) {
        if (userRepository.existsByUsername(req.getUsername())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "用户名已存在");
        }
        User user = new User();
        user.setUsername(req.getUsername());
        user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        user.setDisplayName(req.getDisplayName().trim());
        user.setStudentClass(normalizeClass(req.getStudentClass()));
        user.setRole(UserRole.STUDENT);
        userRepository.save(user);
        return buildAuthResponse(user);
    }

    public AuthResponse login(LoginRequest req) {
        String loginId = req.getUsername().trim();
        User user = userRepository.findByUsername(loginId)
                .or(() -> userRepository.findFirstByDisplayName(loginId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "账号/姓名或密码错误"));
        if (!passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "账号/姓名或密码错误");
        }
        return buildAuthResponse(user);
    }

    public AuthResponse resetPassword(ResetPasswordRequest req) {
        User user = userRepository.findByUsername(req.getUsername())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "账号不存在"));
        user.setPasswordHash(passwordEncoder.encode(req.getNewPassword()));
        userRepository.save(user);
        return buildAuthResponse(user);
    }

    public User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在"));
    }

    public AuthResponse currentUser(Long userId) {
        User user = getUser(userId);
        return new AuthResponse("", user.getId(), user.getUsername(), user.getDisplayName(),
                normalizeRole(user.getRole()), safeClass(user.getStudentClass()));
    }

    private AuthResponse buildAuthResponse(User user) {
        String role = normalizeRole(user.getRole());
        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), role);
        return new AuthResponse(token, user.getId(), user.getUsername(), user.getDisplayName(),
                role, safeClass(user.getStudentClass()));
    }

    private String normalizeRole(String role) {
        return UserRole.TEACHER.equalsIgnoreCase(role) ? UserRole.TEACHER : UserRole.STUDENT;
    }

    private String normalizeClass(String studentClass) {
        return studentClass != null ? studentClass.trim() : "";
    }

    private String safeClass(String studentClass) {
        return studentClass != null ? studentClass : "";
    }
}
