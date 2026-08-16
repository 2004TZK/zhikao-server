package com.zhikao.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhikao.server.common.BizException;
import com.zhikao.server.common.ErrorCode;
import com.zhikao.server.entity.Idiom;
import com.zhikao.server.entity.Knowledge;
import com.zhikao.server.entity.StudyRecord;
import com.zhikao.server.entity.User;
import com.zhikao.server.entity.UserAnswer;
import com.zhikao.server.entity.UserIdiom;
import com.zhikao.server.entity.UserKnowledge;
import com.zhikao.server.mapper.IdiomMapper;
import com.zhikao.server.mapper.KnowledgeMapper;
import com.zhikao.server.mapper.StudyRecordMapper;
import com.zhikao.server.mapper.UserAnswerMapper;
import com.zhikao.server.mapper.UserIdiomMapper;
import com.zhikao.server.mapper.UserKnowledgeMapper;
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
 * 统计服务（契约 6.10，T8.1）。
 * 事实来源（7.7）：一律从明细表聚合（study_record / user_answer / user_knowledge / user_idiom），
 * 不读 user.total_study_minutes 缓存字段。
 */
@Service
@RequiredArgsConstructor
public class StatsService {

    private final UserMapper userMapper;
    private final StudyRecordMapper studyRecordMapper;
    private final UserAnswerMapper userAnswerMapper;
    private final UserKnowledgeMapper userKnowledgeMapper;
    private final UserIdiomMapper userIdiomMapper;
    private final KnowledgeMapper knowledgeMapper;
    private final IdiomMapper idiomMapper;
    private final JdbcTemplate jdbcTemplate;

    /** 学习天数（有 study_record 的不同自然日数） */
    private long studyDays(Long userId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT DATE(created_at)) FROM study_record WHERE user_id = ?",
                Long.class, userId);
        return count == null ? 0 : count;
    }

    /** 累计时长（分钟，study_record 聚合） */
    private long totalMinutes(Long userId) {
        Long seconds = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(duration_seconds), 0) FROM study_record WHERE user_id = ?",
                Long.class, userId);
        return seconds == null ? 0 : seconds / 60;
    }

    /** 答题量与正确率（user_answer 聚合） */
    private long[] answerStats(Long userId) {
        Long total = userAnswerMapper.selectCount(new LambdaQueryWrapper<UserAnswer>()
                .eq(UserAnswer::getUserId, userId));
        Long correct = userAnswerMapper.selectCount(new LambdaQueryWrapper<UserAnswer>()
                .eq(UserAnswer::getUserId, userId)
                .eq(UserAnswer::getIsCorrect, 1));
        long t = total == null ? 0 : total;
        long c = correct == null ? 0 : correct;
        return new long[]{t, c};
    }

    /** 掌握数（user_knowledge / user_idiom mastery>=3） */
    private long[] masteredCounts(Long userId) {
        long k = userKnowledgeMapper.selectCount(new LambdaQueryWrapper<UserKnowledge>()
                .eq(UserKnowledge::getUserId, userId)
                .ge(UserKnowledge::getMastery, 3));
        long i = userIdiomMapper.selectCount(new LambdaQueryWrapper<UserIdiom>()
                .eq(UserIdiom::getUserId, userId)
                .ge(UserIdiom::getMastery, 3));
        return new long[]{k, i};
    }

    /** /stats/overview（契约 6.10） */
    public Map<String, Object> overview(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        long days = studyDays(userId);
        long minutes = totalMinutes(userId);
        long[] answers = answerStats(userId);
        long[] mastered = masteredCounts(userId);

        Map<String, Object> result = new HashMap<>();
        result.put("studyDays", days);
        result.put("totalStudyMinutes", minutes);
        result.put("knowledgeMastered", mastered[0]);
        result.put("idiomMastered", mastered[1]);
        result.put("totalAnswers", answers[0]);
        result.put("correctAnswers", answers[1]);
        result.put("accuracy", answers[0] > 0 ? Math.round(answers[1] * 100.0 / answers[0]) : 0);
        result.put("streakDays", user.getStreakDays());
        return result;
    }

    /** /stats/trend：近 N 天趋势（每日时长/答题数/正确率） */
    public List<Map<String, Object>> trend(Long userId, int days) {
        int n = Math.min(Math.max(days, 1), 30);
        LocalDate today = LocalDate.now();
        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = n - 1; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            LocalDateTime start = date.atStartOfDay();
            LocalDateTime end = date.plusDays(1).atStartOfDay();

            Long minutes = jdbcTemplate.queryForObject(
                    "SELECT COALESCE(SUM(duration_seconds), 0) FROM study_record WHERE user_id = ? AND created_at >= ? AND created_at < ?",
                    Long.class, userId, start, end);
            Long answers = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM user_answer WHERE user_id = ? AND answer_time >= ? AND answer_time < ?",
                    Long.class, userId, start, end);
            Long correct = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM user_answer WHERE user_id = ? AND answer_time >= ? AND answer_time < ? AND is_correct = 1",
                    Long.class, userId, start, end);

            long m = minutes == null ? 0 : minutes / 60;
            long a = answers == null ? 0 : answers;
            long c = correct == null ? 0 : correct;

            Map<String, Object> item = new HashMap<>();
            item.put("date", date.toString());
            item.put("studyMinutes", m);
            item.put("answerCount", a);
            item.put("accuracy", a > 0 ? Math.round(c * 100.0 / a) : 0);
            result.add(item);
        }
        return result;
    }

    /** /stats/daily-report：当日学习报告 */
    public Map<String, Object> dailyReport(Long userId) {
        LocalDateTime start = LocalDate.now().atStartOfDay();
        LocalDateTime end = LocalDate.now().plusDays(1).atStartOfDay();

        Long minutes = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(duration_seconds), 0) FROM study_record WHERE user_id = ? AND created_at >= ? AND created_at < ?",
                Long.class, userId, start, end);
        Long answers = userAnswerMapper.selectCount(new LambdaQueryWrapper<UserAnswer>()
                .eq(UserAnswer::getUserId, userId)
                .ge(UserAnswer::getAnswerTime, start)
                .lt(UserAnswer::getAnswerTime, end));
        Long correct = userAnswerMapper.selectCount(new LambdaQueryWrapper<UserAnswer>()
                .eq(UserAnswer::getUserId, userId)
                .eq(UserAnswer::getIsCorrect, 1)
                .ge(UserAnswer::getAnswerTime, start)
                .lt(UserAnswer::getAnswerTime, end));
        Long reviewed = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM review_record WHERE user_id = ? AND created_at >= ? AND created_at < ?",
                Long.class, userId, start, end);
        Long studied = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT target_id) FROM study_record WHERE user_id = ? AND record_type IN (1,2) AND created_at >= ? AND created_at < ?",
                Long.class, userId, start, end);

        long a = answers == null ? 0 : answers;
        long c = correct == null ? 0 : correct;
        long m = minutes == null ? 0 : minutes / 60;

        Map<String, Object> result = new HashMap<>();
        result.put("date", LocalDate.now().toString());
        result.put("studyMinutes", m);
        result.put("studiedCount", studied == null ? 0 : studied);
        result.put("answerCount", a);
        result.put("accuracy", a > 0 ? Math.round(c * 100.0 / a) : 0);
        result.put("reviewCount", reviewed == null ? 0 : reviewed);
        result.put("streakDays", userMapper.selectById(userId).getStreakDays());
        return result;
    }
}
