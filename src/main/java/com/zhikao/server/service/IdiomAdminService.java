package com.zhikao.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhikao.server.common.BizException;
import com.zhikao.server.common.ErrorCode;
import com.zhikao.server.dto.IdiomRequest;
import com.zhikao.server.entity.Idiom;
import com.zhikao.server.entity.IdiomCategory;
import com.zhikao.server.entity.IdiomCategoryRel;
import com.zhikao.server.entity.ImportDocument;
import com.zhikao.server.mapper.IdiomCategoryMapper;
import com.zhikao.server.mapper.IdiomCategoryRelMapper;
import com.zhikao.server.mapper.IdiomMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 成语管理服务（T3.3，管理后台）。
 * 一个成语可挂多个分类（关联表先清后写）。
 */
@Service
@RequiredArgsConstructor
public class IdiomAdminService {

    private final IdiomMapper idiomMapper;
    private final IdiomCategoryMapper categoryMapper;
    private final IdiomCategoryRelMapper relMapper;

    /** 分页搜索：keyword 匹配 word+pinyin+explanation；categoryId 过滤 */
    public Page<Idiom> page(int page, int size, String keyword, Long categoryId, Integer status) {
        LambdaQueryWrapper<Idiom> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Idiom::getWord, keyword)
                    .or().like(Idiom::getPinyin, keyword)
                    .or().like(Idiom::getExplanation, keyword));
        }
        if (categoryId != null) {
            List<Long> idiomIds = relMapper.selectList(new LambdaQueryWrapper<IdiomCategoryRel>()
                            .eq(IdiomCategoryRel::getCategoryId, categoryId))
                    .stream().map(IdiomCategoryRel::getIdiomId).collect(Collectors.toList());
            if (idiomIds.isEmpty()) {
                wrapper.eq(Idiom::getId, -1L); // 无匹配
            } else {
                wrapper.in(Idiom::getId, idiomIds);
            }
        }
        if (status != null) {
            wrapper.eq(Idiom::getStatus, status);
        }
        wrapper.orderByDesc(Idiom::getUpdatedAt);
        return idiomMapper.selectPage(new Page<>(page, size), wrapper);
    }

    /** 详情（含分类 id 列表） */
    public IdiomDetail detail(Long id) {
        Idiom idiom = idiomMapper.selectById(id);
        if (idiom == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "成语不存在");
        }
        List<Long> categoryIds = relMapper.selectList(new LambdaQueryWrapper<IdiomCategoryRel>()
                        .eq(IdiomCategoryRel::getIdiomId, id))
                .stream().map(IdiomCategoryRel::getCategoryId).collect(Collectors.toList());
        IdiomDetail detail = new IdiomDetail();
        detail.setIdiom(idiom);
        detail.setCategoryIds(categoryIds);
        return detail;
    }

    @Transactional
    public Idiom create(IdiomRequest request) {
        validateWordUnique(request.getWord(), null);
        validateSource(request.getSourceType(), request.getSourceDocumentId());
        Idiom idiom = new Idiom();
        applyRequest(idiom, request);
        idiom.setStatus(request.getStatus() != null ? request.getStatus() : 1);
        idiomMapper.insert(idiom);
        saveCategoryRels(idiom.getId(), request.getCategoryIds());
        return idiom;
    }

    @Transactional
    public Idiom update(IdiomRequest request) {
        if (request.getId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "id 不能为空");
        }
        Idiom idiom = idiomMapper.selectById(request.getId());
        if (idiom == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "成语不存在");
        }
        validateWordUnique(request.getWord(), request.getId());
        validateSource(request.getSourceType(), request.getSourceDocumentId());
        applyRequest(idiom, request);
        idiomMapper.updateById(idiom);
        // 先清后写分类关联
        relMapper.deleteByCategoryRelIdiomId(request.getId());
        saveCategoryRels(request.getId(), request.getCategoryIds());
        return idiom;
    }

    public void offShelf(Long id) {
        Idiom idiom = idiomMapper.selectById(id);
        if (idiom == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "成语不存在");
        }
        idiom.setStatus(0);
        idiomMapper.updateById(idiom);
    }

    public void onShelf(Long id) {
        Idiom idiom = idiomMapper.selectById(id);
        if (idiom == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "成语不存在");
        }
        idiom.setStatus(1);
        idiomMapper.updateById(idiom);
    }

    /** 分类列表 */
    public List<IdiomCategory> categories() {
        return categoryMapper.selectList(new LambdaQueryWrapper<IdiomCategory>()
                .orderByAsc(IdiomCategory::getSort));
    }

    @Transactional
    public IdiomCategory createCategory(String name, Integer sort) {
        if (!StringUtils.hasText(name)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "分类名不能为空");
        }
        Long count = categoryMapper.selectCount(new LambdaQueryWrapper<IdiomCategory>()
                .eq(IdiomCategory::getName, name));
        if (count != null && count > 0) {
            throw new BizException(ErrorCode.BIZ_CONFLICT, "分类名已存在");
        }
        IdiomCategory category = new IdiomCategory();
        category.setName(name);
        category.setSort(sort != null ? sort : 0);
        category.setStatus(1);
        categoryMapper.insert(category);
        return category;
    }

    /**
     * 从导入草稿写入正式表（T3.9）。
     * 来源回写（10.5）：source_type=4 / source_document_id / source_title。
     */
    @Transactional
    public Long createFromDraft(java.util.Map<String, Object> parsed, ImportDocument document) {
        Idiom idiom = new Idiom();
        idiom.setWord(str(parsed.get("word")));
        idiom.setPinyin(str(parsed.get("pinyin")));
        idiom.setExplanation(str(parsed.get("explanation")));
        idiom.setOrigin(str(parsed.get("origin")));
        idiom.setExample(str(parsed.get("example")));
        idiom.setSynonyms(str(parsed.get("synonyms")));
        idiom.setAntonyms(str(parsed.get("antonyms")));
        idiom.setDifficulty(3);
        idiom.setStatus(1);
        idiom.setSourceType(4);
        idiom.setSourceDocumentId(document.getId());
        idiom.setSourceTitle(document.getFileName());
        idiomMapper.insert(idiom);
        return idiom.getId();
    }

    private static String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private void saveCategoryRels(Long idiomId, List<Long> categoryIds) {
        if (CollectionUtils.isEmpty(categoryIds)) {
            return;
        }
        for (Long categoryId : categoryIds) {
            if (categoryMapper.selectById(categoryId) == null) {
                throw new BizException(ErrorCode.PARAM_INVALID, "分类不存在: " + categoryId);
            }
            IdiomCategoryRel rel = new IdiomCategoryRel();
            rel.setIdiomId(idiomId);
            rel.setCategoryId(categoryId);
            relMapper.insert(rel);
        }
    }

    private void validateWordUnique(String word, Long excludeId) {
        LambdaQueryWrapper<Idiom> wrapper = new LambdaQueryWrapper<Idiom>().eq(Idiom::getWord, word);
        if (excludeId != null) {
            wrapper.ne(Idiom::getId, excludeId);
        }
        Long count = idiomMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BizException(ErrorCode.BIZ_CONFLICT, "成语已存在");
        }
    }

    private void validateSource(Integer sourceType, Long sourceDocumentId) {
        if (sourceType != null && sourceType == 4 && sourceDocumentId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "文档导入来源（source_type=4）必须填写来源文档 id");
        }
    }

    private void applyRequest(Idiom idiom, IdiomRequest request) {
        idiom.setWord(request.getWord());
        idiom.setPinyin(request.getPinyin());
        idiom.setExplanation(request.getExplanation());
        idiom.setOrigin(request.getOrigin());
        idiom.setExample(request.getExample());
        idiom.setSynonyms(request.getSynonyms());
        idiom.setAntonyms(request.getAntonyms());
        idiom.setConfusing(request.getConfusing());
        idiom.setCommonError(request.getCommonError());
        idiom.setDifficulty(request.getDifficulty());
        idiom.setStatus(request.getStatus() != null ? request.getStatus() : idiom.getStatus());
        idiom.setSourceType(request.getSourceType());
        idiom.setSourceDocumentId(request.getSourceDocumentId());
        idiom.setSourceTitle(request.getSourceTitle());
    }

    /** 详情返回结构：成语 + 分类 id 列表 */
    public static class IdiomDetail {
        private Idiom idiom;
        private List<Long> categoryIds = new ArrayList<>();

        public Idiom getIdiom() {
            return idiom;
        }

        public void setIdiom(Idiom idiom) {
            this.idiom = idiom;
        }

        public List<Long> getCategoryIds() {
            return categoryIds;
        }

        public void setCategoryIds(List<Long> categoryIds) {
            this.categoryIds = categoryIds;
        }
    }
}
