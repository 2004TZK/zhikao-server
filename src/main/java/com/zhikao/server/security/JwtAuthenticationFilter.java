package com.zhikao.server.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * JWT 认证过滤器（T3.1 扩展）：
 *  - 用户 Access Token（Authorization: Bearer）：写入 ROLE_USER，业务上下文 AuthContext
 *  - 管理员 Token（独立体系）：写入 ROLE_ADMIN，不写入业务 AuthContext
 *  - 校验失败：不写入认证信息，由 RestAuthenticationEntryPoint 返回 2001
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;
    private final AdminJwtUtil adminJwtUtil;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String header = request.getHeader("Authorization");
            if (header != null && header.startsWith(BEARER_PREFIX)) {
                String token = header.substring(BEARER_PREFIX.length());

                // 先尝试解析用户 Access Token
                Claims claims = jwtUtil.parseToken(token);
                if (claims != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                    Long userId = Long.valueOf(claims.getSubject());
                    Integer userType = claims.get("userType", Integer.class);
                    String username = claims.get("username", String.class);

                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    username, null,
                                    List.of(new SimpleGrantedAuthority("ROLE_USER")));
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);

                    AuthContext.set(userId, userType);
                } else {
                    // 再尝试解析管理员 Token（独立体系）
                    Claims adminClaims = adminJwtUtil.parseAdminToken(token);
                    if (adminClaims != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                        String adminName = adminClaims.getSubject();
                        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
                        authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
                        UsernamePasswordAuthenticationToken adminAuth =
                                new UsernamePasswordAuthenticationToken(adminName, null, authorities);
                        adminAuth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(adminAuth);
                    }
                }
            }
            filterChain.doFilter(request, response);
        } finally {
            AuthContext.clear();
            SecurityContextHolder.clearContext();
        }
    }
}
