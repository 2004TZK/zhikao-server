package com.zhikao.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhikao.server.common.BizException;
import com.zhikao.server.common.ErrorCode;
import com.zhikao.server.dto.DeleteAccountRequest;
import com.zhikao.server.dto.GuestRequest;
import com.zhikao.server.dto.LoginRequest;
import com.zhikao.server.dto.RegisterRequest;
import com.zhikao.server.entity.User;
import com.zhikao.server.mapper.UserMapper;
import com.zhikao.server.security.JwtUtil;
import com.zhikao.server.vo.AuthResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 认证服务（契约：6.2）。
 *  - 注册/登录：普通账号体系（username + password_hash）
 *  - 游客：guest_uuid 换取 user_type=2 记录（v1.4 方案 A）
 *  - Access Token 30 分钟（JWT 无状态）；Refresh Token 7 天（存 Redis，可吊销）
 *  - 登出：删除 Redis 中 Refresh Token
 *  - 注销：按 5.3 定死策略（行为数据全部 DELETE，user 脱敏保留）
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String REFRESH_TOKEN_PREFIX = "auth:refresh:";

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final StringRedisTemplate stringRedisTemplate;

    @Value("${zhikao.jwt.refresh-token-ttl-ms:604800000}")
    private long refreshTokenTtlMs;

    /** 注册：校验用户名唯一 → 建正式用户（user_type=1）→ 签发双 Token */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        Long exists = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, request.getUsername()));
        if (exists != null && exists > 0) {
            throw new BizException(ErrorCode.BIZ_CONFLICT, "用户名已存在");
        }
        User user = new User();
        user.setUserType(1);
        user.setUsername(request.getUsername());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setNickname(request.getNickname() != null && !request.getNickname().isBlank()
                ? request.getNickname() : request.getUsername());
        user.setStatus(0);
        user.setStreakDays(0);
        user.setTotalStudyMinutes(0);
        userMapper.insert(user);
        return issueTokens(user);
    }

    /** 登录：校验账号密码 → 签发双 Token */
    public AuthResponse login(LoginRequest request) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, request.getUsername()));
        if (user == null || user.getPasswordHash() == null
                || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "用户名或密码错误");
        }
        checkUserStatus(user);
        return issueTokens(user);
    }

    /**
     * 游客换取 Token（v1.4 方案 A）：
     * 按 guest_uuid 查 user 表，不存在则创建 user_type=2 记录，否则复用同一 user_id。
     */
    @Transactional
    public AuthResponse guest(GuestRequest request) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getGuestUuid, request.getGuestUuid()));
        if (user == null) {
            user = new User();
            user.setUserType(2);
            user.setGuestUuid(request.getGuestUuid());
            user.setUsername("guest_" + request.getGuestUuid().substring(0, Math.min(8, request.getGuestUuid().length())));
            user.setNickname("游客");
            user.setStatus(0);
            user.setStreakDays(0);
            user.setTotalStudyMinutes(0);
            userMapper.insert(user);
        } else {
            checkUserStatus(user);
        }
        return issueTokens(user);
    }

    /** 刷新：校验 Redis 中的 Refresh Token → 签发新 Access Token + 新 Refresh Token */
    public AuthResponse refresh(String refreshToken) {
        String userId = stringRedisTemplate.opsForValue().get(REFRESH_TOKEN_PREFIX + refreshToken);
        if (userId == null) {
            throw new BizException(ErrorCode.REFRESH_EXPIRED);
        }
        User user = userMapper.selectById(Long.valueOf(userId));
        if (user == null) {
            throw new BizException(ErrorCode.REFRESH_EXPIRED);
        }
        checkUserStatus(user);
        // 刷新后旧 Refresh Token 作废（轮换），防重放
        stringRedisTemplate.delete(REFRESH_TOKEN_PREFIX + refreshToken);
        return issueTokens(user);
    }

    /** 获取当前用户信息 */
    public AuthResponse profile(Long userId) {
        if (userId == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        checkUserStatus(user);
        return AuthResponse.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .userType(user.getUserType())
                .build();
    }

    /** 登出：删除 Redis 中的 Refresh Token */
    public void logout(String refreshToken) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            stringRedisTemplate.delete(REFRESH_TOKEN_PREFIX + refreshToken);
        }
    }

    /**
     * 账号注销（5.3 定死策略，T9.5 唯一执行依据）：
     * 行为数据全部物理删除；user 表脱敏保留（status=2）。单事务完成。
     */
    @Transactional
    public void deleteAccount(Long userId, DeleteAccountRequest request) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        if (user.getUserType() != null && user.getUserType() == 2) {
            // 游客注销：按同一策略（游客无密码，跳过密码校验）
        } else if (user.getPasswordHash() == null
                || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "密码错误");
        }

        // 行为数据全部物理删除（v1.4 定稿：不匿名化保留）
        deleteUserBehaviorData(userId);

        // user 表脱敏保留（LambdaUpdateWrapper 显式 set 空字段，MyBatis-Plus 默认策略忽略 null）
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .set(User::getUsername, "anonymous_" + userId)
                .set(User::getNickname, "已注销用户")
                .set(User::getAvatar, null)
                .set(User::getPasswordHash, null)
                .set(User::getGuestUuid, null)
                .set(User::getStatus, 2)
                .set(User::getStreakDays, 0)
                .set(User::getTotalStudyMinutes, 0));
    }

    /** 签发双 Token：Access（JWT 无状态）+ Refresh（Redis 存 7 天，value=userId） */
    private AuthResponse issueTokens(User user) {
        String accessToken = jwtUtil.generateAccessToken(user.getId(), user.getUserType(), user.getUsername());
        String refreshToken = UUID.randomUUID().toString().replace("-", "");
        stringRedisTemplate.opsForValue().set(REFRESH_TOKEN_PREFIX + refreshToken,
                String.valueOf(user.getId()), refreshTokenTtlMs, TimeUnit.MILLISECONDS);
        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(jwtUtil.getAccessTokenTtlMs() / 1000)
                .userId(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .userType(user.getUserType())
                .build();
    }

    private void checkUserStatus(User user) {
        if (user.getStatus() != null && user.getStatus() == 1) {
            throw new BizException(ErrorCode.ACCOUNT_DISABLED);
        }
        if (user.getStatus() != null && user.getStatus() == 2) {
            throw new BizException(ErrorCode.ACCOUNT_DISABLED, "账号已注销");
        }
    }

    /** 按 5.3 账号注销逐表策略删除用户行为数据（9 张表，全部物理删除） */
    private void deleteUserBehaviorData(Long userId) {
        userMapper.deleteUserKnowledge(userId);
        userMapper.deleteUserIdiom(userId);
        userMapper.deleteFavorite(userId);
        userMapper.deleteDailyTask(userId);
        userMapper.deletePracticeSession(userId);
        userMapper.deleteUserAnswer(userId);
        userMapper.deleteWrongQuestion(userId);
        userMapper.deleteStudyRecord(userId);
        userMapper.deleteReviewRecord(userId);
    }
}
