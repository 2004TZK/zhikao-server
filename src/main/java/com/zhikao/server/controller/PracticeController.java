package com.zhikao.server.controller;

import com.zhikao.server.common.Result;
import com.zhikao.server.security.AuthContext;
import com.zhikao.server.service.PracticeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 练习模块（契约：6.6 /practice）。
 */
@Tag(name = "练习", description = "组卷/判分/结果")
@RestController
@RequestMapping("/api/v1/practice")
@RequiredArgsConstructor
public class PracticeController {

    private final PracticeService practiceService;

    @Operation(summary = "创建练习场次（每日挑战/专项/随机），返回题目列表（不含答案）")
    @PostMapping("/sessions")
    public Result<Map<String, Object>> createSession(@RequestBody CreateSessionRequest request) {
        return Result.ok(practiceService.createSession(
                AuthContext.getUserId(), request.getType(), request.getRefType(), request.getRefId(), request.getCount()));
    }

    @Operation(summary = "提交单题答案（服务端判定）")
    @PostMapping("/sessions/{id}/answers")
    public Result<Map<String, Object>> submitAnswer(@PathVariable Long id, @RequestBody SubmitAnswerRequest request) {
        return Result.ok(practiceService.submitAnswer(
                AuthContext.getUserId(), id, request.getQuestionId(), request.getOptionId()));
    }

    @Operation(summary = "场次结果（正确率/错题列表）")
    @GetMapping("/sessions/{id}/result")
    public Result<Map<String, Object>> sessionResult(@PathVariable Long id) {
        return Result.ok(practiceService.sessionResult(AuthContext.getUserId(), id));
    }

    @Operation(summary = "错题重练组卷")
    @PostMapping("/wrong-practice")
    public Result<Map<String, Object>> wrongPractice(@RequestParam(defaultValue = "10") int count) {
        return Result.ok(practiceService.createWrongPractice(AuthContext.getUserId(), count));
    }

    @Data
    public static class CreateSessionRequest {
        /** 1 每日挑战 2 专项训练 3 随机训练 */
        private Integer type;
        /** ref_type：1 常识分类 2 成语分类 3 知识点 4 成语 */
        private Integer refType;
        private Long refId;
        private Integer count;
    }

    @Data
    public static class SubmitAnswerRequest {
        private Long questionId;
        private Long optionId;
    }
}
