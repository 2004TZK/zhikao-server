package com.zhikao.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhikao.server.common.BizException;
import com.zhikao.server.common.ErrorCode;
import com.zhikao.server.entity.Favorite;
import com.zhikao.server.entity.Knowledge;
import com.zhikao.server.entity.KnowledgeCategory;
import com.zhikao.server.entity.Question;
import com.zhikao.server.entity.UserKnowledge;
import com.zhikao.server.mapper.FavoriteMapper;
import com.zhikao.server.mapper.KnowledgeCategoryMapper;
import com.zhikao.server.mapper.KnowledgeMapper;
import com.zhikao.server.mapper.QuestionMapper;
import com.zhikao.server.mapper.UserKnowledgeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 常识模块服务（契约 6.4，T5.1）。
 *  - 分类列表（含各分类知识点数、已掌握数）
 *  - 列表（categoryId/keyword/mastery 过滤 + 分页）
 *  - 详情（含用户掌握状态、收藏状态、相关真题反查——question 按 knowledge_id 反查）
 *  - 标记已掌握（mastery → 2 已学习，进入复习调度）
 */
@Service
@RequiredArgsConstructor
public class KnowledgeService {

    private final KnowledgeMapper knowledgeMapper;
    private final KnowledgeCategoryMapper categoryMapper;
    private final UserKnowledgeMapper userKnowledgeMapper;
    private final QuestionMapper questionMapper;
    private final FavoriteMapper favoriteMapper;

    /** 分类列表（含各分类知识点数、已掌握数，契约 6.4） */
    public List<Map<String, Object>> categories(Long userId) {
        List<KnowledgeCategory> categories = categoryMapper.selectList(
                new LambdaQueryWrapper<KnowledgeCategory>()
                        .eq(KnowledgeCategory::getStatus, 1)
                        .orderByAsc(KnowledgeCategory::getSort));
        List<Map<String, Object>> result = new ArrayList<>();
        for (KnowledgeCategory category : categories) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", category.getId());
            item.put("name", category.getName());
            item.put("icon", category.getIcon());
            item.put("sort", category.getSort());
            long total = knowledgeMapper.selectCount(new LambdaQueryWrapper<Knowledge>()
                    .eq(Knowledge::getCategoryId, category.getId())
                    .eq(Knowledge::getStatus, 1));
            item.put("knowledgeCount", total);
            if (userId != null) {
                long mastered = userKnowledgeMapper.selectCount(new LambdaQueryWrapper<UserKnowledge>()
                        .eq(UserKnowledge::getUserId, userId)
                        .in(UserKnowledge::getMastery, 3, 4)
                        .inSql(UserKnowledge::getKnowledgeId,
                                "SELECT id FROM knowledge WHERE category_id = " + category.getId() + " AND status = 1"));
                item.put("masteredCount", mastered);
            } else {
                item.put("masteredCount", 0);
            }
            result.add(item);
        }
        return result;
    }

    /** 列表（分页 + 过滤，契约 6.4） */
    public Page<Knowledge> page(Long userId, Long categoryId, String keyword, Integer mastery, int page, int size) {
        LambdaQueryWrapper<Knowledge> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Knowledge::getStatus, 1);
        if (categoryId != null) {
            wrapper.eq(Knowledge::getCategoryId, categoryId);
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Knowledge::getTitle, keyword)
                    .or().like(Knowledge::getSummary, keyword)
                    .or().like(Knowledge::getContent, keyword));
        }
        if (mastery != null && userId != null) {
            // 按掌握度过滤（如只看已掌握 mastery>=3）
            List<Long> ids = userKnowledgeMapper.selectList(new LambdaQueryWrapper<UserKnowledge>()
                            .eq(UserKnowledge::getUserId, userId)
                            .eq(UserKnowledge::getMastery, mastery))
                    .stream().map(UserKnowledge::getKnowledgeId).toList();
            if (ids.isEmpty()) {
                wrapper.eq(Knowledge::getId, -1L);
            } else {
                wrapper.in(Knowledge::getId, ids);
            }
        }
        wrapper.orderByDesc(Knowledge::getUpdatedAt);
        return knowledgeMapper.selectPage(new Page<>(page, size), wrapper);
    }

    /** 详情（含掌握状态、收藏状态、相关真题列表） */
    public Map<String, Object> detail(Long userId, Long id) {
        Knowledge knowledge = knowledgeMapper.selectById(id);
        if (knowledge == null || knowledge.getStatus() != 1) {
            throw new BizException(ErrorCode.NOT_FOUND, "知识点不存在");
        }
        Map<String, Object> result = new HashMap<>();
        result.put("id", knowledge.getId());
        result.put("title", knowledge.getTitle());
        result.put("categoryId", knowledge.getCategoryId());
        result.put("summary", knowledge.getSummary());
        result.put("content", knowledge.getContent());
        result.put("keyPoints", knowledge.getKeyPoints());
        result.put("commonMistakes", knowledge.getCommonMistakes());
        result.put("difficulty", knowledge.getDifficulty());
        result.put("sourceType", knowledge.getSourceType());

        // 用户状态
        if (userId != null) {
            UserKnowledge uk = userKnowledgeMapper.selectOne(new LambdaQueryWrapper<UserKnowledge>()
                    .eq(UserKnowledge::getUserId, userId)
                    .eq(UserKnowledge::getKnowledgeId, id));
            result.put("mastery", uk == null ? 0 : uk.getMastery());
            result.put("favorited", favoriteMapper.selectCount(new LambdaQueryWrapper<Favorite>()
                    .eq(Favorite::getUserId, userId)
                    .eq(Favorite::getTargetType, 1)
                    .eq(Favorite::getTargetId, id)) > 0);
        } else {
            result.put("mastery", 0);
            result.put("favorited", false);
        }

        // 相关真题反查（6.4：question 按 knowledge_id 反查，返回题干/年份/来源）
        List<Question> questions = questionMapper.selectList(new LambdaQueryWrapper<Question>()
                .eq(Question::getKnowledgeId, id)
                .eq(Question::getStatus, 1)
                .orderByDesc(Question::getUpdatedAt)
                .last("LIMIT 10"));
        List<Map<String, Object>> relatedQuestions = new ArrayList<>();
        for (Question q : questions) {
            Map<String, Object> qm = new HashMap<>();
            qm.put("id", q.getId());
            qm.put("content", q.getContent());
            qm.put("sourceName", q.getSourceName());
            qm.put("examYear", q.getExamYear());
            relatedQuestions.add(qm);
        }
        result.put("relatedQuestions", relatedQuestions);
        return result;
    }

    /** 标记已掌握（7.1：学习中→已学习，进入复习调度 review_stage=0） */
    public void markMastered(Long userId, Long knowledgeId) {
        UserKnowledge uk = userKnowledgeMapper.selectOne(new LambdaQueryWrapper<UserKnowledge>()
                .eq(UserKnowledge::getUserId, userId)
                .eq(UserKnowledge::getKnowledgeId, knowledgeId));
        if (uk == null) {
            uk = new UserKnowledge();
            uk.setUserId(userId);
            uk.setKnowledgeId(knowledgeId);
            uk.setMastery(2);
            uk.setReviewStage(0);
            uk.setStudyCount(1);
            uk.setCorrectCount(0);
            uk.setWrongCount(0);
            userKnowledgeMapper.insert(uk);
        } else {
            if (uk.getMastery() >= 2) {
                return; // 已学习及以上不降级
            }
            uk.setMastery(2);
            uk.setReviewStage(0);
            userKnowledgeMapper.updateById(uk);
        }
    }
}
