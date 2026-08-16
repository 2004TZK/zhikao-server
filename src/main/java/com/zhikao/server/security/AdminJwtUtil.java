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
 * 管理员 JWT 工具：与用户 Access Token 独立（任务书 6.11：独立 token 体系）。
 * Token 中带 role=ADMIN claim，由 JwtAuthenticationFilter 识别授予 ROLE_ADMIN。
 */
@Component
public class AdminJwtUtil {

    @Value("${zhikao.jwt.admin-secret}")
    private String adminSecret;

    @Value("${zhikao.jwt.admin-token-ttl-ms:7200000}")
    private long adminTokenTtlMs;

    private SecretKey getKey() {
        return Keys.hmacShaKeyFor(adminSecret.getBytes(StandardCharsets.UTF_8));
    }

    /** 签发管理员 Token */
    public String generateAdminToken(String username) {
        Date now = new Date();
        return Jwts.builder()
                .subject(username)
                .claim("role", "ADMIN")
                .issuedAt(now)
                .expiration(new Date(now.getTime() + adminTokenTtlMs))
                .signWith(getKey())
                .compact();
    }

    /**
     * 解析管理员 Token；合法且 role=ADMIN 返回 Claims，否则返回 null。
     */
    public Claims parseAdminToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(getKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            if ("ADMIN".equals(claims.get("role", String.class))) {
                return claims;
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }
}
