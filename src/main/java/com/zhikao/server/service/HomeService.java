package com.zhikao.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhikao.server.entity.DailyTask;
import com.zhikao.server.entity.User;
import com.zhikao.server.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 首页服务（契约 6.3，T4.4）。
 *  - /home/overview：连续天数、今日完成度、今日学习时长、今日任务、复习积压数
 *  - /home/recommend：7.6 推荐算法（常识 3 + 成语 3，全局去重，v1.4）
 */
@Service
@RequiredArgsConstructor
public class HomeService {

    private final UserMapper userMapper;
    private final DailyTaskService dailyTaskService;
    private final JdbcTemplate jdbcTemplate;

    /** 首页聚合（响应契约见 6.13 示例 1） */
    public Map<String, Object> overview(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new com.zhikao.server.common.BizException(
                    com.zhikao.server.common.ErrorCode.NOT_FOUND, "用户不存在");
        }

        // 今日任务懒加载（7.4）
        List<DailyTask> tasks = dailyTaskService.ensureTodayTasks(userId);
        int[] progress = dailyTaskService.todayProgress(userId);

        // 复习积压数（7.3 pendingTotal）
        long reviewPending = countDueReview(userId);
        long suggestedCount = Math.min(reviewPending, 20);

        // 今日学习时长（study_record 聚合，7.7）
        int todayMinutes = todayStudyMinutes(userId);

        Map<String, Object> result = new HashMap<>();
        result.put("streakDays", user.getStreakDays());
        result.put("todayCompleted", progress[0]);
        result.put("todayTarget", progress[1]);
        result.put("todayStudyMinutes", todayMinutes);
        result.put("reviewPendingTotal", reviewPending);
        result.put("reviewSuggestedCount", suggestedCount);

        List<Map<String, Object>> taskList = new ArrayList<>();
        for (DailyTask task : tasks) {
            Map<String, Object> item = new HashMap<>();
            item.put("taskType", task.getTaskType());
            item.put("taskName", taskName(task.getTaskType()));
            item.put("targetCount", task.getTargetCount());
            item.put("doneCount", task.getDoneCount());
            item.put("status", task.getStatus());
            taskList.add(item);
        }
        result.put("tasks", taskList);
        return result;
    }

    /**
     * 今日推荐（7.6 算法：常识 3 + 成语 3，去重）。
     * 优先级：到期复习 → 高频错误 → 未学习 → 薄弱分类 → 随机兜底。
     * V1.0 简化实现：优先级 3（未学习）为主 + 随机兜底；完整聚合在 P5/P6 数据就绪后增强。
     */
    public Map<String, Object> recommend(Long userId) {
        List<Map<String, Object>> knowledgeList = recommendContent(userId, "knowledge", 3);
        List<Map<String, Object>> idiomList = recommendContent(userId, "idiom", 3);

        Map<String, Object> result = new HashMap<>();
        result.put("knowledgeList", knowledgeList);
        result.put("idiomList", idiomList);
        return result;
    }

    private List<Map<String, Object>> recommendContent(Long userId, String table, int limit) {
        // 未学习内容（无 user_xxx 记录）优先；不足随机兜底（7.6 优先级 3/5）
        String nameCol = "knowledge".equals(table) ? "title" : "word";
        String sql = "SELECT id, " + nameCol + " AS name FROM " + table + " WHERE status = 1 " +
                "AND id NOT IN (SELECT " + table + "_id FROM user_" + table + " WHERE user_id = ?) " +
                "ORDER BY RAND() LIMIT " + limit;
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, userId);
        if (rows.size() < limit) {
            // 兜底：随机内容
            String fallbackSql = "SELECT id, " + nameCol + " AS name FROM " + table + " WHERE status = 1 ORDER BY RAND() LIMIT " + limit;
            for (Map<String, Object> row : jdbcTemplate.queryForList(fallbackSql)) {
                rows.add(row);
            }
        }
        // 去重（v1.4 规则）
        List<Map<String, Object>> result = new ArrayList<>();
        java.util.Set<Long> seen = new java.util.HashSet<>();
        for (Map<String, Object> row : rows) {
            Long id = ((Number) row.get("id")).longValue();
            if (seen.add(id)) {
                result.add(row);
            }
            if (result.size() >= limit) {
                break;
            }
        }
        return result;
    }

    private long countDueReview(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        return countDue(userId, now, "user_knowledge") + countDue(userId, now, "user_idiom");
    }

    private long countDue(Long userId, LocalDateTime now, String table) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE user_id = ? AND next_review_time <= ?",
                Long.class, userId, now);
        return count == null ? 0 : count;
    }

    private int todayStudyMinutes(Long userId) {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        Long seconds = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(duration_seconds), 0) FROM study_record WHERE user_id = ? AND created_at >= ?",
                Long.class, userId, startOfDay);
        return seconds == null ? 0 : (int) (seconds / 60);
    }

    private String taskName(Integer taskType) {
        return switch (taskType) {
            case 1 -> "常识积累";
            case 2 -> "成语训练";
            case 3 -> "今日复习";
            default -> "未知任务";
        };
    }
}
