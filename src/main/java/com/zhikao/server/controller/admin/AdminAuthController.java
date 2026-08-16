package com.zhikao.server.controller.admin;

import com.zhikao.server.common.Result;
import com.zhikao.server.service.AdminAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理后台认证（契约：6.11 POST /admin/login，独立 token 体系）。
 */
@Tag(name = "管理后台-认证")
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AdminAuthService adminAuthService;

    @Operation(summary = "管理员登录")
    @PostMapping("/login")
    public Result<AdminAuthService.AdminLoginResponse> login(@Valid @RequestBody AdminLoginRequest request) {
        return Result.ok(adminAuthService.login(request.getUsername(), request.getPassword()));
    }

    @Data
    public static class AdminLoginRequest {
        @NotBlank(message = "用户名不能为空")
        private String username;

        @NotBlank(message = "密码不能为空")
        private String password;
    }
}
