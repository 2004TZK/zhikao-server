package com.zhikao.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhikao.server.common.BizException;
import com.zhikao.server.common.ErrorCode;
import com.zhikao.server.dto.KnowledgeRequest;
import com.zhikao.server.entity.Knowledge;
import com.zhikao.server.entity.KnowledgeCategory;
import com.zhikao.server.mapper.KnowledgeCategoryMapper;
import com.zhikao.server.mapper.KnowledgeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 知识点管理服务（T3.2，管理后台）。
 * 规则：删除仅逻辑下架（status=0）；录入必填来源字段（9.3）。
 */
@Service
@RequiredArgsConstructor
public class KnowledgeAdminService {

    private final KnowledgeMapper knowledgeMapper;
    private final KnowledgeCategoryMapper categoryMapper;

    /** 分页搜索：keyword 匹配 title+summary+content；支持 categoryId/status 过滤 */
    public Page<Knowledge> page(int page, int size, String keyword, Long categoryId, Integer status) {
        LambdaQueryWrapper<Knowledge> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Knowledge::getTitle, keyword)
                    .or().like(Knowledge::getSummary, keyword)
                    .or().like(Knowledge::getContent, keyword));
        }
        if (categoryId != null) {
            wrapper.eq(Knowledge::getCategoryId, categoryId);
        }
        if (status != null) {
            wrapper.eq(Knowledge::getStatus, status);
        }
        wrapper.orderByDesc(Knowledge::getUpdatedAt);
        return knowledgeMapper.selectPage(new Page<>(page, size), wrapper);
    }

    /** 详情 */
    public Knowledge detail(Long id) {
        Knowledge knowledge = knowledgeMapper.selectById(id);
        if (knowledge == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "知识点不存在");
        }
        return knowledge;
    }

    /** 新增（校验分类存在 + 来源必填） */
    @Transactional
    public Knowledge create(KnowledgeRequest request) {
        validateCategory(request.getCategoryId());
        validateSource(request.getSourceType(), request.getSourceDocumentId());
        Knowledge knowledge = new Knowledge();
        applyRequest(knowledge, request);
        knowledge.setStatus(request.getStatus() != null ? request.getStatus() : 1);
        knowledgeMapper.insert(knowledge);
        return knowledge;
    }

    /** 更新 */
    @Transactional
    public Knowledge update(KnowledgeRequest request) {
        if (request.getId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "id 不能为空");
        }
        Knowledge knowledge = knowledgeMapper.selectById(request.getId());
        if (knowledge == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "知识点不存在");
        }
        validateCategory(request.getCategoryId());
        validateSource(request.getSourceType(), request.getSourceDocumentId());
        applyRequest(knowledge, request);
        knowledgeMapper.updateById(knowledge);
        return knowledge;
    }

    /** 逻辑下架（不做物理删除，任务书 5.3） */
    public void offShelf(Long id) {
        Knowledge knowledge = knowledgeMapper.selectById(id);
        if (knowledge == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "知识点不存在");
        }
        knowledge.setStatus(0);
        knowledgeMapper.updateById(knowledge);
    }

    /** 上架 */
    public void onShelf(Long id) {
        Knowledge knowledge = knowledgeMapper.selectById(id);
        if (knowledge == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "知识点不存在");
        }
        knowledge.setStatus(1);
        knowledgeMapper.updateById(knowledge);
    }

    /** 分类列表（含状态过滤，默认全部） */
    public List<KnowledgeCategory> categories() {
        return categoryMapper.selectList(new LambdaQueryWrapper<KnowledgeCategory>()
                .orderByAsc(KnowledgeCategory::getSort));
    }

    /** 新增分类 */
    @Transactional
    public KnowledgeCategory createCategory(String name, String icon, Integer sort) {
        if (!StringUtils.hasText(name)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "分类名不能为空");
        }
        Long count = categoryMapper.selectCount(new LambdaQueryWrapper<KnowledgeCategory>()
                .eq(KnowledgeCategory::getName, name));
        if (count != null && count > 0) {
            throw new BizException(ErrorCode.BIZ_CONFLICT, "分类名已存在");
        }
        KnowledgeCategory category = new KnowledgeCategory();
        category.setName(name);
        category.setIcon(icon);
        category.setSort(sort != null ? sort : 0);
        category.setStatus(1);
        categoryMapper.insert(category);
        return category;
    }

    private void validateCategory(Long categoryId) {
        if (categoryMapper.selectById(categoryId) == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "分类不存在");
        }
    }

    /** 来源校验：source_type 1~3 无需文档 id；source_type=4 必填 source_document_id（5.2） */
    private void validateSource(Integer sourceType, Long sourceDocumentId) {
        if (sourceType != null && sourceType == 4 && sourceDocumentId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "文档导入来源（source_type=4）必须填写来源文档 id");
        }
    }

    private void applyRequest(Knowledge knowledge, KnowledgeRequest request) {
        knowledge.setTitle(request.getTitle());
        knowledge.setCategoryId(request.getCategoryId());
        knowledge.setSummary(request.getSummary());
        knowledge.setContent(request.getContent());
        knowledge.setKeyPoints(request.getKeyPoints());
        knowledge.setCommonMistakes(request.getCommonMistakes());
        knowledge.setDifficulty(request.getDifficulty());
        knowledge.setStatus(request.getStatus() != null ? request.getStatus() : knowledge.getStatus());
        knowledge.setSourceType(request.getSourceType());
        knowledge.setSourceDocumentId(request.getSourceDocumentId());
        knowledge.setSourceTitle(request.getSourceTitle());
    }
}
