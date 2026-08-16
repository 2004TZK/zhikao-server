package com.zhikao.server.common;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Redis 缓存工具（任务书 2.3/2.5：热点内容缓存）。
 *
 * <p>封装 get/set/delete/expire 等常用操作，统一 key 前缀与序列化。
 * 内容缓存使用：KnowledgeService / IdiomService 等读取热点内容时使用。
 */
@Component
@RequiredArgsConstructor
public class CacheService {

    private final RedisTemplate<String, Object> redisTemplate;

    /** 读取缓存，不存在返回 null */
    public Object get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    /** 写入缓存（永久，仅用于无时效数据） */
    public void set(String key, Object value) {
        redisTemplate.opsForValue().set(key, value);
    }

    /** 写入缓存并设置过期时间 */
    public void set(String key, Object value, long timeout, TimeUnit unit) {
        redisTemplate.opsForValue().set(key, value, timeout, unit);
    }

    /** 删除缓存 */
    public void delete(String key) {
        redisTemplate.delete(key);
    }

    /** 判断 key 是否存在 */
    public boolean exists(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    /** 设置过期时间 */
    public boolean expire(String key, long timeout, TimeUnit unit) {
        return Boolean.TRUE.equals(redisTemplate.expire(key, timeout, unit));
    }

    /** 自增（用于计数场景） */
    public Long increment(String key) {
        return redisTemplate.opsForValue().increment(key);
    }

    /** 自增并设置过期时间（原子） */
    public Long increment(String key, long timeout, TimeUnit unit) {
        Long value = redisTemplate.opsForValue().increment(key);
        if (value != null && value == 1) {
            redisTemplate.expire(key, timeout, unit);
        }
        return value;
    }
}
