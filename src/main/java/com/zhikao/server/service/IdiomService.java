package com.zhikao.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhikao.server.common.BizException;
import com.zhikao.server.common.ErrorCode;
import com.zhikao.server.entity.Favorite;
import com.zhikao.server.entity.Idiom;
import com.zhikao.server.entity.IdiomCategory;
import com.zhikao.server.entity.IdiomCategoryRel;
import com.zhikao.server.entity.Question;
import com.zhikao.server.entity.UserIdiom;
import com.zhikao.server.mapper.FavoriteMapper;
import com.zhikao.server.mapper.IdiomCategoryMapper;
import com.zhikao.server.mapper.IdiomCategoryRelMapper;
import com.zhikao.server.mapper.IdiomMapper;
import com.zhikao.server.mapper.QuestionMapper;
import com.zhikao.server.mapper.UserIdiomMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 成语模块服务（契约 6.5，T5.2）。
 *  - 分类列表
 *  - 列表（categoryId/keyword + 分页）
 *  - 详情（含相关题目反查——question.idiom_id）
 */
@Service
@RequiredArgsConstructor
public class IdiomService {

    private final IdiomMapper idiomMapper;
    private final IdiomCategoryMapper categoryMapper;
    private final IdiomCategoryRelMapper relMapper;
    private final QuestionMapper questionMapper;
    private final FavoriteMapper favoriteMapper;
    private final UserIdiomMapper userIdiomMapper;

    /** 分类列表 */
    public List<IdiomCategory> categories() {
        return categoryMapper.selectList(new LambdaQueryWrapper<IdiomCategory>()
                .eq(IdiomCategory::getStatus, 1)
                .orderByAsc(IdiomCategory::getSort));
    }

    /** 列表（categoryId/keyword + 分页） */
    public Page<Idiom> page(Long categoryId, String keyword, int page, int size) {
        LambdaQueryWrapper<Idiom> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Idiom::getStatus, 1);
        if (categoryId != null) {
            List<Long> idiomIds = relMapper.selectList(new LambdaQueryWrapper<IdiomCategoryRel>()
                            .eq(IdiomCategoryRel::getCategoryId, categoryId))
                    .stream().map(IdiomCategoryRel::getIdiomId).collect(Collectors.toList());
            if (idiomIds.isEmpty()) {
                wrapper.eq(Idiom::getId, -1L);
            } else {
                wrapper.in(Idiom::getId, idiomIds);
            }
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Idiom::getWord, keyword)
                    .or().like(Idiom::getPinyin, keyword)
                    .or().like(Idiom::getExplanation, keyword));
        }
        wrapper.orderByDesc(Idiom::getUpdatedAt);
        return idiomMapper.selectPage(new Page<>(page, size), wrapper);
    }

    /** 详情（含相关题目反查、收藏状态） */
    public Map<String, Object> detail(Long userId, Long id) {
        Idiom idiom = idiomMapper.selectById(id);
        if (idiom == null || idiom.getStatus() != 1) {
            throw new BizException(ErrorCode.NOT_FOUND, "成语不存在");
        }
        Map<String, Object> result = new HashMap<>();
        result.put("id", idiom.getId());
        result.put("word", idiom.getWord());
        result.put("pinyin", idiom.getPinyin());
        result.put("explanation", idiom.getExplanation());
        result.put("origin", idiom.getOrigin());
        result.put("example", idiom.getExample());
        result.put("synonyms", idiom.getSynonyms());
        result.put("antonyms", idiom.getAntonyms());
        result.put("confusing", idiom.getConfusing());
        result.put("commonError", idiom.getCommonError());
        result.put("difficulty", idiom.getDifficulty());

        // 收藏状态
        if (userId != null) {
            result.put("favorited", favoriteMapper.selectCount(new LambdaQueryWrapper<Favorite>()
                    .eq(Favorite::getUserId, userId)
                    .eq(Favorite::getTargetType, 2)
                    .eq(Favorite::getTargetId, id)) > 0);
        } else {
            result.put("favorited", false);
        }

        // 相关题目反查（6.5：question.idiom_id）
        List<Question> questions = questionMapper.selectList(new LambdaQueryWrapper<Question>()
                .eq(Question::getIdiomId, id)
                .eq(Question::getStatus, 1)
                .orderByDesc(Question::getUpdatedAt)
                .last("LIMIT 10"));
        List<Map<String, Object>> relatedQuestions = new ArrayList<>();
        for (Question q : questions) {
            Map<String, Object> qm = new HashMap<>();
            qm.put("id", q.getId());
            qm.put("content", q.getContent());
            qm.put("sourceName", q.getSourceName());
            relatedQuestions.add(qm);
        }
        result.put("relatedQuestions", relatedQuestions);
        return result;
    }
}
