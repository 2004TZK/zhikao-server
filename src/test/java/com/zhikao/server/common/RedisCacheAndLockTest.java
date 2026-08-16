package com.zhikao.server.common;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * T2.5 验收测试：Redis 缓存读写 + 分布式锁并发不重复执行。
 * 需要本地 Redis（6379）运行。
 */
@SpringBootTest
class RedisCacheAndLockTest {

    @Autowired
    private CacheService cacheService;

    @Autowired
    private RedisLock redisLock;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private static final String TEST_KEY = "test:cache:unit";

    @BeforeEach
    void setUp() {
        stringRedisTemplate.delete(TEST_KEY);
    }

    @AfterEach
    void tearDown() {
        stringRedisTemplate.delete(TEST_KEY);
    }

    @Test
    void cacheReadWrite() {
        // 写读
        cacheService.set(TEST_KEY, "hello-zhikao");
        assertEquals("hello-zhikao", cacheService.get(TEST_KEY));

        // 过期
        cacheService.set(TEST_KEY, "temp", 1, TimeUnit.SECONDS);
        assertEquals("temp", cacheService.get(TEST_KEY));

        // 删除
        cacheService.delete(TEST_KEY);
        assertFalse(cacheService.exists(TEST_KEY));
    }

    @Test
    void lockMutualExclusion() throws InterruptedException {
        // 模拟任务书 7.4：并发请求下"每日任务只生成一次"（Redis 锁防重）
        // 20 个线程同时尝试生成任务：第一个拿到锁并生成（写完成标记），
        // 其余线程拿到锁后发现标记已存在，跳过生成。最终生成次数必须为 1。
        int threadCount = 20;
        String lockKey = "daily_task:2026-08-16:user:test";
        String doneKey = "daily_task:done:2026-08-16:user:test";
        stringRedisTemplate.delete(doneKey);

        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);
        AtomicInteger generateCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    // 业务语义：重试获取锁（模拟请求并发到达）
                    String token = null;
                    for (int attempt = 0; attempt < 100 && token == null; attempt++) {
                        token = redisLock.tryLock(lockKey, 10, TimeUnit.SECONDS);
                        if (token == null) {
                            Thread.sleep(10);
                        }
                    }
                    assertNotNull(token, "重试 100 次仍拿不到锁");
                    try {
                        // 拿到锁后检查是否已生成（防重）
                        Boolean generated = stringRedisTemplate.hasKey(doneKey);
                        if (Boolean.FALSE.equals(generated)) {
                            generateCount.incrementAndGet();
                            stringRedisTemplate.opsForValue().set(doneKey, "1");
                        }
                    } finally {
                        redisLock.unlock(lockKey, token);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();
        start.countDown();
        done.await(30, TimeUnit.SECONDS);
        pool.shutdown();

        // 验收点：并发下生成动作只执行 1 次（不重复生成）
        assertEquals(1, generateCount.get(),
                "Redis 分布式锁应保证并发下任务只生成一次");
        stringRedisTemplate.delete(doneKey);
    }

    @Test
    void lockCannotBeReleasedByOthers() {
        String tokenA = redisLock.tryLock("daily_task:test2", 10, TimeUnit.SECONDS);
        assertNotNull(tokenA);
        // 他人（错误 token）尝试释放
        redisLock.unlock("daily_task:test2", "wrong-token");
        assertTrue(stringRedisTemplate.hasKey("lock:daily_task:test2"),
                "错误 token 不应释放他人持有的锁");
        // 持有者释放
        redisLock.unlock("daily_task:test2", tokenA);
        assertFalse(stringRedisTemplate.hasKey("lock:daily_task:test2"));
    }
}
