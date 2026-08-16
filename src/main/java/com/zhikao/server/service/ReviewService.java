package com.zhikao.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhikao.server.common.BizException;
import com.zhikao.server.common.ErrorCode;
import com.zhikao.server.entity.Idiom;
import com.zhikao.server.entity.Knowledge;
import com.zhikao.server.entity.ReviewRecord;
import com.zhikao.server.entity.UserIdiom;
import com.zhikao.server.entity.UserKnowledge;
import com.zhikao.server.mapper.IdiomMapper;
import com.zhikao.server.mapper.KnowledgeMapper;
import com.zhikao.server.mapper.ReviewRecordMapper;
import com.zhikao.server.mapper.UserIdiomMapper;
import com.zhikao.server.mapper.UserKnowledgeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 复习服务（契约 6.8，T7.2）。
 *  - /review/today：pendingTotal / suggestedCount / 三组分组数量
 *  - /review/sessions：按调度排序取复习项（默认建议量）
 *  - 提交单项复习结果：触发掌握度与 next_review_time 更新（7.1/7.2）
 *  - review_record 落库
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewService {

    private static final int SESSION_CAP = 20;
    private static final int FORGETTING_OVERDUE_DAYS = 2;

    private final UserKnowledgeMapper userKnowledgeMapper;
    private final UserIdiomMapper userIdiomMapper;
    private final ReviewRecordMapper reviewRecordMapper;
    private final KnowledgeMapper knowledgeMapper;
    private final IdiomMapper idiomMapper;
    private final ReviewScheduleService scheduleService;
    private final JdbcTemplate jdbcTemplate;

    /** 今日复习概览（契约 6.13 示例 4） */
    public Map<String, Object> todayOverview(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        List<DueItem> due = queryDueItems(userId, now);
        int pendingTotal = due.size();
        int suggestedCount = Math.min(pendingTotal, SESSION_CAP);

        // 分组：即将遗忘（超期>2天）/普通/错题强化
        int forgetting = 0;
        int normal = 0;
        for (DueItem item : due) {
            if (item.isOverdueMoreThan(FORGETTING_OVERDUE_DAYS)) {
                forgetting++;
            } else {
                normal++;
            }
        }
        int wrongReinforce = countWrongReinforce(userId);

        Map<String, Object> groups = new HashMap<>();
        groups.put("forgetting", forgetting);
        groups.put("normal", normal);
        groups.put("wrongReinforce", wrongReinforce);

        Map<String, Object> result = new HashMap<>();
        result.put("pendingTotal", pendingTotal);
        result.put("suggestedCount", suggestedCount);
        result.put("groups", groups);
        return result;
    }

    /** 开始复习：返回按调度排序的复习项（默认取建议量，上限 20） */
    public List<Map<String, Object>> createReviewSession(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        List<DueItem> due = queryDueItems(userId, now);
        // 排序：超期时长降序（即将遗忘优先）
        due.sort((a, b) -> Long.compare(b.overdueSeconds(now), a.overdueSeconds(now)));

        List<Map<String, Object>> items = new ArrayList<>();
        for (int i = 0; i < Math.min(due.size(), SESSION_CAP); i++) {
            DueItem item = due.get(i);
            Map<String, Object> reviewItem = new HashMap<>();
            reviewItem.put("targetType", item.targetType);
            reviewItem.put("targetId", item.targetId);
            reviewItem.put("reviewType", item.isOverdueMoreThan(FORGETTING_OVERDUE_DAYS) ? 1 : 2);
            reviewItem.put("reviewStage", item.reviewStage);
            reviewItem.put("title", item.title);
            reviewItem.put("content", item.content);
            items.add(reviewItem);
        }
        return items;
    }

    /**
     * 提交单项复习结果（契约 6.8 POST /review/sessions/{id}/items）。
     * 幂等：同一天同一目标同类型只计一次（review_record 去重）。
     */
    @Transactional
    public void submitReviewResult(Long userId, Integer targetType, Long targetId, Boolean isCorrect) {
        // 幂等：当日已复习过该目标则跳过（防重复推进）
        Long todayCount = reviewRecordMapper.selectCount(new LambdaQueryWrapper<ReviewRecord>()
                .eq(ReviewRecord::getUserId, userId)
                .eq(ReviewRecord::getTargetType, targetType)
                .eq(ReviewRecord::getTargetId, targetId)
                .ge(ReviewRecord::getCreatedAt, java.time.LocalDate.now().atStartOfDay()));
        if (todayCount != null && todayCount > 0) {
            return;
        }

        boolean correct = Boolean.TRUE.equals(isCorrect);
        if (targetType == 1) {
            UserKnowledge uk = userKnowledgeMapper.selectOne(new LambdaQueryWrapper<UserKnowledge>()
                    .eq(UserKnowledge::getUserId, userId)
                    .eq(UserKnowledge::getKnowledgeId, targetId));
            if (uk == null) {
                throw new BizException(ErrorCode.NOT_FOUND, "该知识点未在学习状态");
            }
            if (correct) {
                scheduleService.applyKnowledgeCorrect(uk);
            } else {
                scheduleService.applyKnowledgeWrong(uk);
            }
            userKnowledgeMapper.updateById(uk);
        } else {
            UserIdiom ui = userIdiomMapper.selectOne(new LambdaQueryWrapper<UserIdiom>()
                    .eq(UserIdiom::getUserId, userId)
                    .eq(UserIdiom::getIdiomId, targetId));
            if (ui == null) {
                throw new BizException(ErrorCode.NOT_FOUND, "该成语未在学习状态");
            }
            if (correct) {
                scheduleService.applyIdiomCorrect(ui);
            } else {
                scheduleService.applyIdiomWrong(ui);
            }
            userIdiomMapper.updateById(ui);
        }

        // review_record 落库
        ReviewRecord record = new ReviewRecord();
        record.setUserId(userId);
        record.setTargetType(targetType);
        record.setTargetId(targetId);
        record.setReviewType(correct ? 2 : 1);
        record.setIsCorrect(correct ? 1 : 0);
        reviewRecordMapper.insert(record);
    }

    /** 查询到期项（user_knowledge + user_idiom，next_review_time <= now） */
    private List<DueItem> queryDueItems(Long userId, LocalDateTime now) {
        List<DueItem> result = new ArrayList<>();

        List<UserKnowledge> knowledgeList = userKnowledgeMapper.selectList(
                new LambdaQueryWrapper<UserKnowledge>()
                        .eq(UserKnowledge::getUserId, userId)
                        .le(UserKnowledge::getNextReviewTime, now)
                        .lt(UserKnowledge::getMastery, 4)); // 熟练不再调度
        for (UserKnowledge uk : knowledgeList) {
            Knowledge knowledge = knowledgeMapper.selectById(uk.getKnowledgeId());
            if (knowledge == null) {
                continue;
            }
            DueItem item = new DueItem();
            item.targetType = 1;
            item.targetId = uk.getKnowledgeId();
            item.reviewStage = uk.getReviewStage();
            item.nextReviewTime = uk.getNextReviewTime();
            item.title = knowledge.getTitle();
            item.content = knowledge.getSummary();
            result.add(item);
        }

        List<UserIdiom> idiomList = userIdiomMapper.selectList(
                new LambdaQueryWrapper<UserIdiom>()
                        .eq(UserIdiom::getUserId, userId)
                        .le(UserIdiom::getNextReviewTime, now)
                        .lt(UserIdiom::getMastery, 4));
        for (UserIdiom ui : idiomList) {
            Idiom idiom = idiomMapper.selectById(ui.getIdiomId());
            if (idiom == null) {
                continue;
            }
            DueItem item = new DueItem();
            item.targetType = 2;
            item.targetId = ui.getIdiomId();
            item.reviewStage = ui.getReviewStage();
            item.nextReviewTime = ui.getNextReviewTime();
            item.title = idiom.getWord();
            item.content = idiom.getExplanation();
            result.add(item);
        }
        return result;
    }

    /** 错题强化计数（wrong_question 中 is_removed=0 且近 3 天未重练的题，7.3） */
    private int countWrongReinforce(Long userId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM wrong_question w WHERE w.user_id = ? AND w.is_removed = 0 " +
                        "AND NOT EXISTS (SELECT 1 FROM review_record r WHERE r.user_id = w.user_id " +
                        "AND r.target_type = 1 AND r.target_id = w.question_id AND r.created_at >= DATE_SUB(NOW(), INTERVAL 3 DAY))",
                Long.class, userId);
        return count == null ? 0 : count.intValue();
    }

    /** 到期项内部结构 */
    private static class DueItem {
        int targetType;
        Long targetId;
        int reviewStage;
        LocalDateTime nextReviewTime;
        String title = "";
        String content = "";

        boolean isOverdueMoreThan(int days) {
            if (nextReviewTime == null) {
                return false;
            }
            return java.time.Duration.between(nextReviewTime, LocalDateTime.now()).toDays() > days;
        }

        long overdueSeconds(LocalDateTime now) {
            if (nextReviewTime == null) {
                return 0;
            }
            return java.time.Duration.between(nextReviewTime, now).getSeconds();
        }
    }
}
