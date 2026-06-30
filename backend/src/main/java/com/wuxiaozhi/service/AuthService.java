package com.wuxiaozhi.service;

import com.wuxiaozhi.config.AppProperties;
import com.wuxiaozhi.dto.AuthResponse;
import com.wuxiaozhi.dto.LoginRequest;
import com.wuxiaozhi.dto.RegisterRequest;
import com.wuxiaozhi.dto.ResetPasswordRequest;
import com.wuxiaozhi.entity.User;
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

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    @PostConstruct
    public void ensureDefaultUser() {
        if (userRepository.existsByUsername(DEFAULT_USERNAME)) {
            return;
        }
        User user = new User();
        user.setUsername(DEFAULT_USERNAME);
        user.setPasswordHash(passwordEncoder.encode(DEFAULT_PASSWORD));
        user.setDisplayName("测试用户");
        user.setStudentClass("测试班级");
        userRepository.save(user);
    }

    public AuthResponse register(RegisterRequest req) {
        if (userRepository.existsByUsername(req.getUsername())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "用户名已存在");
        }
        User user = new User();
        user.setUsername(req.getUsername());
        user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        user.setDisplayName(req.getDisplayName());
        user.setStudentClass(req.getStudentClass());
        userRepository.save(user);
        return buildAuthResponse(user);
    }

    public AuthResponse login(LoginRequest req) {
        User user = userRepository.findByUsername(req.getUsername())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户名或密码错误"));
        if (!passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
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

    private AuthResponse buildAuthResponse(User user) {
        String token = jwtUtil.generateToken(user.getId(), user.getUsername());
        return new AuthResponse(token, user.getId(), user.getUsername(), user.getDisplayName(), user.getStudentClass());
    }
}
