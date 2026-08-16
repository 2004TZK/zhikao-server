package com.zhikao.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhikao.server.common.BizException;
import com.zhikao.server.common.ErrorCode;
import com.zhikao.server.dto.QuestionRequest;
import com.zhikao.server.entity.Question;
import com.zhikao.server.entity.QuestionOption;
import com.zhikao.server.mapper.QuestionMapper;
import com.zhikao.server.mapper.QuestionOptionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 题目管理服务（T3.4，管理后台）。
 * 保存时强制 5.2 硬约束：
 *  - 选项数量 2~6
 *  - 必须且只能存在 1 个 is_correct=1
 *  - knowledge_id 与 idiom_id 至少一个非空（否则 1003）
 */
@Service
@RequiredArgsConstructor
public class QuestionAdminService {

    private final QuestionMapper questionMapper;
    private final QuestionOptionMapper optionMapper;

    /** 分页搜索：keyword 匹配题干；关联/来源过滤 */
    public Page<Question> page(int page, int size, String keyword, Integer sourceType, Integer status) {
        LambdaQueryWrapper<Question> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.like(Question::getContent, keyword);
        }
        if (sourceType != null) {
            wrapper.eq(Question::getSourceType, sourceType);
        }
        if (status != null) {
            wrapper.eq(Question::getStatus, status);
        }
        wrapper.orderByDesc(Question::getUpdatedAt);
        return questionMapper.selectPage(new Page<>(page, size), wrapper);
    }

    /** 详情（含选项列表） */
    public QuestionDetail detail(Long id) {
        Question question = questionMapper.selectById(id);
        if (question == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "题目不存在");
        }
        List<QuestionOption> options = optionMapper.selectList(
                new LambdaQueryWrapper<QuestionOption>()
                        .eq(QuestionOption::getQuestionId, id)
                        .orderByAsc(QuestionOption::getLabel));
        QuestionDetail detail = new QuestionDetail();
        detail.setQuestion(question);
        detail.setOptions(options);
        return detail;
    }

    @Transactional
    public Question create(QuestionRequest request) {
        validateConstraints(request);
        Question question = new Question();
        applyRequest(question, request);
        question.setStatus(request.getStatus() != null ? request.getStatus() : 1);
        questionMapper.insert(question);
        saveOptions(question.getId(), request.getOptions());
        return question;
    }

    @Transactional
    public Question update(QuestionRequest request) {
        if (request.getId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "id 不能为空");
        }
        Question question = questionMapper.selectById(request.getId());
        if (question == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "题目不存在");
        }
        validateConstraints(request);
        applyRequest(question, request);
        questionMapper.updateById(question);
        // 先清后写选项
        optionMapper.deleteByQuestionId(request.getId());
        saveOptions(request.getId(), request.getOptions());
        return question;
    }

    public void offShelf(Long id) {
        Question question = questionMapper.selectById(id);
        if (question == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "题目不存在");
        }
        question.setStatus(0);
        questionMapper.updateById(question);
    }

    public void onShelf(Long id) {
        Question question = questionMapper.selectById(id);
        if (question == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "题目不存在");
        }
        question.setStatus(1);
        questionMapper.updateById(question);
    }

    /**
     * 5.2 硬约束校验：
     *  - 选项 2~6 个
     *  - 唯一正确答案（必须 1 个 is_correct=1）
     *  - knowledge_id 与 idiom_id 至少一个非空
     */
    private void validateConstraints(QuestionRequest request) {
        List<QuestionRequest.OptionRequest> options = request.getOptions();
        if (options == null || options.size() < 2 || options.size() > 6) {
            throw new BizException(ErrorCode.BIZ_CONFLICT, "选项数量需在 2~6 之间");
        }
        long correctCount = options.stream()
                .filter(o -> o.getIsCorrect() != null && o.getIsCorrect() == 1)
                .count();
        if (correctCount != 1) {
            throw new BizException(ErrorCode.BIZ_CONFLICT, "必须且只能存在 1 个正确答案");
        }
        if (request.getKnowledgeId() == null && request.getIdiomId() == null) {
            throw new BizException(ErrorCode.BIZ_CONFLICT, "题目必须至少关联一个知识点或成语");
        }
        if (request.getSourceType() != null && request.getSourceType() == 4 && request.getSourceDocumentId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "文档导入来源必须填写来源文档 id");
        }
    }

    private void saveOptions(Long questionId, List<QuestionRequest.OptionRequest> options) {
        int index = 0;
        char[] labels = {'A', 'B', 'C', 'D', 'E', 'F'};
        for (QuestionRequest.OptionRequest req : options) {
            QuestionOption option = new QuestionOption();
            option.setQuestionId(questionId);
            option.setLabel(req.getLabel() != null && !req.getLabel().isBlank()
                    ? req.getLabel() : String.valueOf(labels[index]));
            option.setContent(req.getContent());
            option.setIsCorrect(req.getIsCorrect() != null ? req.getIsCorrect() : 0);
            optionMapper.insert(option);
            index++;
        }
    }

    private void applyRequest(Question question, QuestionRequest request) {
        question.setType(request.getType() != null ? request.getType() : 1);
        question.setContent(request.getContent());
        question.setAnalysis(request.getAnalysis());
        question.setDifficulty(request.getDifficulty());
        question.setKnowledgeId(request.getKnowledgeId());
        question.setIdiomId(request.getIdiomId());
        question.setSourceType(request.getSourceType());
        question.setSourceName(request.getSourceName());
        question.setExamYear(request.getExamYear());
        question.setProvince(request.getProvince());
        question.setQuestionNo(request.getQuestionNo());
        question.setSourceDocumentId(request.getSourceDocumentId());
        question.setStatus(request.getStatus() != null ? request.getStatus() : question.getStatus());
    }

    /** 详情返回结构：题目 + 选项列表 */
    public static class QuestionDetail {
        private Question question;
        private List<QuestionOption> options = new ArrayList<>();

        public Question getQuestion() {
            return question;
        }

        public void setQuestion(Question question) {
            this.question = question;
        }

        public List<QuestionOption> getOptions() {
            return options;
        }

        public void setOptions(List<QuestionOption> options) {
            this.options = options;
        }
    }
}
