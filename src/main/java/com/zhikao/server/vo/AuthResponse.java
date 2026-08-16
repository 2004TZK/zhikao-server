package com.zhikao.server.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录/游客/刷新成功后的响应（契约：6.2）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {

    private String accessToken;

    private String refreshToken;

    /** Access Token 有效期（秒） */
    private Long expiresIn;

    private Long userId;

    private String username;

    private String nickname;

    private Integer userType;
}
