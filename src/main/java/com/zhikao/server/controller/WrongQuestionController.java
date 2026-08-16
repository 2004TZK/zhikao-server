package com.zhikao.server.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhikao.server.common.BizException;
import com.zhikao.server.common.ErrorCode;
import com.zhikao.server.common.Result;
import com.zhikao.server.entity.Question;
import com.zhikao.server.entity.WrongQuestion;
import com.zhikao.server.mapper.QuestionMapper;
import com.zhikao.server.mapper.WrongQuestionMapper;
import com.zhikao.server.security.AuthContext;
import com.zhikao.server.service.PracticeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 错题模块（契约：6.7 /wrong）。
 */
@Tag(name = "错题", description = "错题本列表/重练/移出")
@RestController
@RequestMapping("/api/v1/wrong")
@RequiredArgsConstructor
public class WrongQuestionController {

    private final WrongQuestionMapper wrongQuestionMapper;
    private final QuestionMapper questionMapper;
    private final PracticeService practiceService;

    @Operation(summary = "错题列表（含题目实时内容，非快照）")
    @GetMapping("/list")
    public Result<Page<Map<String, Object>>> list(@RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        Page<WrongQuestion> wrongPage = wrongQuestionMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<WrongQuestion>()
                        .eq(WrongQuestion::getUserId, AuthContext.getUserId())
                        .eq(WrongQuestion::getIsRemoved, 0)
                        .orderByDesc(WrongQuestion::getLastWrongTime));
        // 组装题目实时内容（6.7：错题本展示最新题目内容）
        Page<Map<String, Object>> resultPage = new Page<>(wrongPage.getCurrent(), wrongPage.getSize(), wrongPage.getTotal());
        java.util.List<Map<String, Object>> records = new java.util.ArrayList<>();
        for (WrongQuestion wrong : wrongPage.getRecords()) {
            Question question = questionMapper.selectById(wrong.getQuestionId());
            if (question == null) {
                continue;
            }
            Map<String, Object> item = new HashMap<>();
            item.put("wrongId", wrong.getId());
            item.put("questionId", question.getId());
            item.put("content", question.getContent());
            item.put("analysis", question.getAnalysis());
            item.put("knowledgeId", question.getKnowledgeId());
            item.put("idiomId", question.getIdiomId());
            item.put("wrongCount", wrong.getWrongCount());
            item.put("lastWrongTime", wrong.getLastWrongTime());
            records.add(item);
        }
        resultPage.setRecords(records);
        return Result.ok(resultPage);
    }

    @Operation(summary = "错题重练组卷（返回 sessionId）")
    @PostMapping("/practice")
    public Result<Map<String, Object>> practice(@RequestParam(defaultValue = "10") int count) {
        return Result.ok(practiceService.createWrongPractice(AuthContext.getUserId(), count));
    }

    @Operation(summary = "移出错题本（逻辑标记 is_removed=1）")
    @PostMapping("/{id}/remove")
    public Result<Void> remove(@PathVariable Long id) {
        WrongQuestion wrong = wrongQuestionMapper.selectById(id);
        if (wrong == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "错题不存在");
        }
        wrong.setIsRemoved(1);
        wrongQuestionMapper.updateById(wrong);
        return Result.ok();
    }
}
