package com.wuxiaozhi.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final com.wuxiaozhi.repository.UserRepository users;

    public JwtAuthFilter(JwtUtil jwtUtil,com.wuxiaozhi.repository.UserRepository users) {
        this.jwtUtil = jwtUtil;
        this.users=users;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                var claims=jwtUtil.parseClaims(token);
                Long userId = Long.parseLong(claims.getSubject());
                var user=users.findById(userId).orElseThrow();
                Number version=claims.get("authVersion",Number.class);
                if(!user.isEnabled()||user.getAuthVersion()!=(version==null?0:version.intValue()))throw new IllegalArgumentException("账号登录已失效");
                String role = user.getRole();
                var auth = new UsernamePasswordAuthenticationToken(
                        userId, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (Exception ignored) {
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }
}
