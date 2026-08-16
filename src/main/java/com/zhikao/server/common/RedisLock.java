package com.zhikao.server.common;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Redis 分布式锁（任务书 2.3/7.4：每日任务生成锁）。
 *
 * <p>基于 SET NX EX 原子指令实现，value 为随机 token：
 *  - tryLock：原子占用，带过期时间防死锁
 *  - unlock：Lua 脚本保证"token 匹配才删除"原子性，防止误删他人锁
 *
 * <p>用法：{@code tryLock(key, 10, TimeUnit.SECONDS)} 返回非 null token 时执行临界区，
 * finally 中 {@code unlock(key, token)}。
 */
@Component
@RequiredArgsConstructor
public class RedisLock {

    private final StringRedisTemplate stringRedisTemplate;

    private static final String LOCK_PREFIX = "lock:";

    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    /**
     * 尝试获取锁（非阻塞）。
     *
     * @param key     锁名（业务语义，如 daily_task:2026-08-16:user:1）
     * @param timeout 锁超时时间
     * @param unit    时间单位
     * @return 成功返回锁 token（用于释放），失败返回 null
     */
    public String tryLock(String key, long timeout, TimeUnit unit) {
        String token = UUID.randomUUID().toString();
        Boolean success = stringRedisTemplate.opsForValue()
                .setIfAbsent(LOCK_PREFIX + key, token, Duration.ofMillis(unit.toMillis(timeout)));
        return Boolean.TRUE.equals(success) ? token : null;
    }

    /**
     * 释放锁：仅当 token 匹配（自己是持有者）时才删除，防止误删。
     */
    public void unlock(String key, String token) {
        if (token == null) {
            return;
        }
        stringRedisTemplate.execute(UNLOCK_SCRIPT,
                Collections.singletonList(LOCK_PREFIX + key), token);
    }
}
