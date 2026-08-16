package com.zhikao.server.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具：签发与解析 Access Token。
 * 契约（任务书 2.3/6.1）：Access Token 有效期 30 分钟，JWT 无状态。
 */
@Component
public class JwtUtil {

    /** 密钥（开发环境配置，生产环境必须通过环境变量注入） */
    @Value("${zhikao.jwt.secret}")
    private String secret;

    /** Access Token 有效期（毫秒），默认 30 分钟 */
    @Value("${zhikao.jwt.access-token-ttl-ms:1800000}")
    private long accessTokenTtlMs;

    private SecretKey getKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 签发 Access Token。
     *
     * @param userId   用户 id
     * @param userType 用户类型（1 正式用户 2 游客）
     * @param username 登录账号
     */
    public String generateAccessToken(Long userId, Integer userType, String username) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("userType", userType)
                .claim("username", username)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + accessTokenTtlMs))
                .signWith(getKey())
                .compact();
    }

    /**
     * 解析 Access Token，失败返回 null（过期、签名错误等）。
     */
    public Claims parseToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(getKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            return null;
        }
    }

    public long getAccessTokenTtlMs() {
        return accessTokenTtlMs;
    }
}
