package com.wuxiaozhi.security;

import com.wuxiaozhi.entity.UserRole;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.server.ResponseStatusException;

public final class AuthSupport {

    private AuthSupport() {
    }

    public static Long currentUserId(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "未登录");
        }
        return (Long) authentication.getPrincipal();
    }

    public static void requireTeacher(Authentication authentication) {
        if (!hasRole(authentication, UserRole.TEACHER)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "需要教师权限");
        }
    }

    public static boolean hasRole(Authentication authentication, String role) {
        if (authentication == null || role == null) {
            return false;
        }
        String authority = "ROLE_" + role;
        for (GrantedAuthority ga : authentication.getAuthorities()) {
            if (authority.equals(ga.getAuthority())) {
                return true;
            }
        }
        return false;
    }
}
