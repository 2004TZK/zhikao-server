package com.zhikao.server.service;

import com.zhikao.server.common.BizException;
import com.zhikao.server.common.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

/**
 * 游客额度控制（任务书 6.1/1.3，T4.3）。
 * 游客每日限额：知识点/成语详情浏览各 5 次、答题 5 道。
 * 计数存 Redis（key: guest:quota:{date}:{userId}:{type}），自然日过期。
 * 限额校验必须在服务端强制（防客户端绕过）。
 */
@Service
@RequiredArgsConstructor
public class GuestService {

    private static final String QUOTA_PREFIX = "guest:quota:";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final int DAILY_QUOTA = 5;

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 校验并消耗一次游客额度。
     *
     * @param userId 游客 user_id（user_type=2）
     * @param type   额度类型：detail_knowledge / detail_idiom / answer
     */
    public void consumeQuota(Long userId, String type) {
        if (userId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "缺少用户身份");
        }
        String key = quotaKey(userId, type);
        Long current = stringRedisTemplate.opsForValue().increment(key);
        if (current != null && current == 1) {
            // 首次计数：设置自然日过期
            stringRedisTemplate.expire(key, remainingMillisToMidnight(), TimeUnit.MILLISECONDS);
        }
        if (current != null && current > DAILY_QUOTA) {
            throw new BizException(ErrorCode.GUEST_QUOTA_EXCEEDED);
        }
    }

    /** 查询游客某类型剩余额度 */
    public int remainingQuota(Long userId, String type) {
        String value = stringRedisTemplate.opsForValue().get(quotaKey(userId, type));
        int used = value == null ? 0 : Integer.parseInt(value);
        return Math.max(0, DAILY_QUOTA - used);
    }

    private String quotaKey(Long userId, String type) {
        return QUOTA_PREFIX + LocalDate.now().format(DATE_FMT) + ":" + userId + ":" + type;
    }

    private long remainingMillisToMidnight() {
        LocalDate today = LocalDate.now();
        LocalDate tomorrow = today.plusDays(1);
        java.time.LocalDateTime midnight = tomorrow.atStartOfDay();
        return java.time.Duration.between(java.time.LocalDateTime.now(), midnight).toMillis();
    }
}
