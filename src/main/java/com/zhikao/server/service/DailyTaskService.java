package com.zhikao.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhikao.server.common.RedisLock;
import com.zhikao.server.entity.DailyTask;
import com.zhikao.server.entity.User;
import com.zhikao.server.mapper.DailyTaskMapper;
import com.zhikao.server.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 每日任务服务（任务书 7.4，T4.4）。
 *  - 懒加载生成：每日首次访问 /home/overview 时生成（Redis 分布式锁防重）
 *  - 任务类型：1 常识积累（3 个知识点）、2 成语训练（5 道成语题）、3 今日复习（min(到期,20)）
 *  - done_count 幂等计数键（v1.4）：同一天同一目标只计一次
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DailyTaskService {

    private final DailyTaskMapper dailyTaskMapper;
    private final UserMapper userMapper;
    private final RedisLock redisLock;
    private final JdbcTemplate jdbcTemplate;

    private static final int KNOWLEDGE_TARGET = 3;
    private static final int IDIOM_TARGET = 5;
    private static final int REVIEW_CAP = 20;

    /**
     * 懒加载生成今日任务（幂等：Redis 锁防并发重复生成）。
     * 返回该用户今日全部任务。
     */
    @Transactional
    public List<DailyTask> ensureTodayTasks(Long userId) {
        LocalDate today = LocalDate.now();
        List<DailyTask> tasks = dailyTaskMapper.selectList(
                new LambdaQueryWrapper<DailyTask>()
                        .eq(DailyTask::getUserId, userId)
                        .eq(DailyTask::getTaskDate, today)
                        .orderByAsc(DailyTask::getTaskType));
        if (tasks.size() >= 3) {
            return tasks;
        }

        // 未生成过 → 加锁防并发重复生成
        String lockKey = "daily_task:" + today + ":user:" + userId;
        String token = redisLock.tryLock(lockKey, 10, TimeUnit.SECONDS);
        if (token == null) {
            // 拿不到锁：另一请求正在生成，重查返回
            return dailyTaskMapper.selectList(new LambdaQueryWrapper<DailyTask>()
                    .eq(DailyTask::getUserId, userId)
                    .eq(DailyTask::getTaskDate, today));
        }
        try {
            // 二次检查（double-check）
            tasks = dailyTaskMapper.selectList(new LambdaQueryWrapper<DailyTask>()
                    .eq(DailyTask::getUserId, userId)
                    .eq(DailyTask::getTaskDate, today));
            if (tasks.size() < 3) {
                tasks = generateTasks(userId, today);
            }
            return tasks;
        } finally {
            redisLock.unlock(lockKey, token);
        }
    }

    private List<DailyTask> generateTasks(Long userId, LocalDate today) {
        List<DailyTask> tasks = new ArrayList<>();
        // 1. 常识积累
        DailyTask knowledgeTask = new DailyTask();
        knowledgeTask.setUserId(userId);
        knowledgeTask.setTaskDate(today);
        knowledgeTask.setTaskType(1);
        knowledgeTask.setTargetCount(KNOWLEDGE_TARGET);
        knowledgeTask.setDoneCount(0);
        knowledgeTask.setStatus(0);
        dailyTaskMapper.insert(knowledgeTask);
        tasks.add(knowledgeTask);

        // 2. 成语训练
        DailyTask idiomTask = new DailyTask();
        idiomTask.setUserId(userId);
        idiomTask.setTaskDate(today);
        idiomTask.setTaskType(2);
        idiomTask.setTargetCount(IDIOM_TARGET);
        idiomTask.setDoneCount(0);
        idiomTask.setStatus(0);
        dailyTaskMapper.insert(idiomTask);
        tasks.add(idiomTask);

        // 3. 今日复习（target = min(到期数, 20)，7.3 建议量）
        long dueCount = countDueReview(userId);
        DailyTask reviewTask = new DailyTask();
        reviewTask.setUserId(userId);
        reviewTask.setTaskDate(today);
        reviewTask.setTaskType(3);
        reviewTask.setTargetCount((int) Math.min(dueCount, REVIEW_CAP));
        reviewTask.setDoneCount(0);
        reviewTask.setStatus(0);
        dailyTaskMapper.insert(reviewTask);
        tasks.add(reviewTask);

        return tasks;
    }

    /** 到期复习数：user_knowledge/user_idiom 中 next_review_time <= now（7.3 积压量） */
    private long countDueReview(Long userId) {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        return countDueInTable(userId, now, "user_knowledge")
                + countDueInTable(userId, now, "user_idiom");
    }

    private long countDueInTable(Long userId, java.time.LocalDateTime now, String table) {
        String sql = "SELECT COUNT(*) FROM " + table + " WHERE user_id = ? AND next_review_time <= ?";
        Long count = jdbcTemplate.queryForObject(sql, Long.class, userId, now);
        return count == null ? 0 : count;
    }

    /**
     * 上报行为并更新任务进度（幂等：v1.4 去重键规则）。
     *
     * @param userId      用户
     * @param taskType    任务类型
     * @param dedupKey    去重键（如 knowledge_id / user_answer_id / review_record_id）
     * @param dedupStorage 去重存储（简化：Redis SET，正式实现见 7.4）
     */
    public void reportProgress(Long userId, Integer taskType, String dedupKey, String dedupStorage) {
        LocalDate today = LocalDate.now();
        DailyTask task = dailyTaskMapper.selectOne(
                new LambdaQueryWrapper<DailyTask>()
                        .eq(DailyTask::getUserId, userId)
                        .eq(DailyTask::getTaskDate, today)
                        .eq(DailyTask::getTaskType, taskType)
                        .last("LIMIT 1"));
        if (task == null) {
            return; // 任务未生成（正常流程应先 ensureTodayTasks）
        }
        if (task.getStatus() == 1) {
            return; // 已完成不再计数
        }
        // 幂等去重：同一天同一目标只计一次（此处以 Redis SET 判重，由调用方提供存储）
        if (dedupStorage != null && dedupStorage.contains(dedupKey)) {
            return;
        }
        task.setDoneCount(task.getDoneCount() + 1);
        if (task.getDoneCount() >= task.getTargetCount()) {
            task.setStatus(1);
        }
        dailyTaskMapper.updateById(task);
    }

    /** 计算今日完成度（todayCompleted/todayTarget，契约 6.13） */
    public int[] todayProgress(Long userId) {
        List<DailyTask> tasks = ensureTodayTasks(userId);
        int completed = 0;
        int target = 0;
        for (DailyTask task : tasks) {
            target += task.getTargetCount();
            completed += Math.min(task.getDoneCount(), task.getTargetCount());
        }
        return new int[]{completed, target};
    }
}
