package com.zhikao.server.config;

import com.zhikao.server.security.JwtAuthenticationFilter;
import com.zhikao.server.security.RestAccessDeniedHandler;
import com.zhikao.server.security.RestAuthenticationEntryPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * 安全配置（T2.4 用户认证 + T3.1 管理员独立鉴权）：
 *  - 认证接口/管理员登录/文档接口放行（permitAll）
 *  - /admin/** 要求 ROLE_ADMIN（普通用户 Token 访问返回 3001）
 *  - 其余接口要求认证，未认证返回统一契约 2001
 *  - 无状态会话
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RestAuthenticationEntryPoint restAuthenticationEntryPoint;
    private final RestAccessDeniedHandler restAccessDeniedHandler;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // 管理员登录（无需 Token）
                        .requestMatchers("/admin/login").permitAll()
                        // 管理后台接口：独立鉴权，需 ROLE_ADMIN
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        // 无需登录的认证接口
                        .requestMatchers("/api/v1/auth/register",
                                "/api/v1/auth/login",
                                "/api/v1/auth/guest",
                                "/api/v1/auth/refresh").permitAll()
                        // 开发期放行：演示接口 + knife4j / springdoc 文档
                        .requestMatchers("/api/v1/demo/**", "/doc.html", "/webjars/**",
                                "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html",
                                "/favicon.ico").permitAll()
                        // 其余接口需要 Access Token
                        .anyRequest().authenticated())
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .exceptionHandling(handler -> handler
                        .authenticationEntryPoint(restAuthenticationEntryPoint)
                        .accessDeniedHandler(restAccessDeniedHandler))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
