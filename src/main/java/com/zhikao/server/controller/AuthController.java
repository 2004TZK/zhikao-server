package com.zhikao.server.controller;

import com.zhikao.server.common.Result;
import com.zhikao.server.dto.DeleteAccountRequest;
import com.zhikao.server.dto.GuestRequest;
import com.zhikao.server.dto.LoginRequest;
import com.zhikao.server.dto.RefreshRequest;
import com.zhikao.server.dto.RegisterRequest;
import com.zhikao.server.security.AuthContext;
import com.zhikao.server.service.AuthService;
import com.zhikao.server.vo.AuthResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证模块（契约：6.2 /auth）。
 */
@Tag(name = "认证", description = "注册/登录/游客/刷新/登出/注销")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "注册")
    @PostMapping("/register")
    public Result<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return Result.ok(authService.register(request));
    }

    @Operation(summary = "登录")
    @PostMapping("/login")
    public Result<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.ok(authService.login(request));
    }

    @Operation(summary = "游客换取 Token")
    @PostMapping("/guest")
    public Result<AuthResponse> guest(@Valid @RequestBody GuestRequest request) {
        return Result.ok(authService.guest(request));
    }

    @Operation(summary = "刷新 Access Token")
    @PostMapping("/refresh")
    public Result<AuthResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return Result.ok(authService.refresh(request.getRefreshToken()));
    }

    @Operation(summary = "获取当前用户信息")
    @GetMapping("/profile")
    public Result<AuthResponse> profile() {
        return Result.ok(authService.profile(AuthContext.getUserId()));
    }

    @Operation(summary = "登出")
    @PostMapping("/logout")
    public Result<Void> logout(@RequestBody(required = false) RefreshRequest request,
                               @RequestHeader(value = "X-Refresh-Token", required = false) String refreshHeader) {
        String refreshToken = request != null && request.getRefreshToken() != null
                ? request.getRefreshToken() : refreshHeader;
        authService.logout(refreshToken);
        return Result.ok();
    }

    @Operation(summary = "账号注销（需密码二次确认）")
    @PostMapping("/delete-account")
    public Result<Void> deleteAccount(@Valid @RequestBody DeleteAccountRequest request) {
        authService.deleteAccount(AuthContext.getUserId(), request);
        return Result.ok();
    }
}
