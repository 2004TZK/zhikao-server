package com.zhikao.server.service;

import com.zhikao.server.common.BizException;
import com.zhikao.server.common.ErrorCode;
import com.zhikao.server.security.AdminJwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 管理员认证服务（T3.1）。
 * 开发期方案：账号密码来自配置（application.yml zhikao.admin.*）。
 * 待任务书补充 admin_user 表契约后迁移至数据库。
 */
@Service
@RequiredArgsConstructor
public class AdminAuthService {

    private final AdminJwtUtil adminJwtUtil;
    private final PasswordEncoder passwordEncoder;

    @Value("${zhikao.admin.username}")
    private String adminUsername;

    @Value("${zhikao.admin.password}")
    private String adminPassword;

    /**
     * 管理员登录：校验配置账号密码，签发独立管理员 Token。
     * 返回 token 与用户名。
     */
    public AdminLoginResponse login(String username, String password) {
        if (!adminUsername.equals(username)
                || !passwordEncoder.matches(password, passwordEncoder.encode(adminPassword))) {
            throw new BizException(ErrorCode.PARAM_INVALID, "管理员用户名或密码错误");
        }
        String token = adminJwtUtil.generateAdminToken(username);
        AdminLoginResponse response = new AdminLoginResponse();
        response.setToken(token);
        response.setUsername(username);
        return response;
    }

    public static class AdminLoginResponse {
        private String token;
        private String username;

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }
    }
}
