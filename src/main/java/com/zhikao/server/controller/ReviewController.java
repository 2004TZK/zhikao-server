package com.zhikao.server.controller;

import com.zhikao.server.common.Result;
import com.zhikao.server.security.AuthContext;
import com.zhikao.server.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 复习模块（契约：6.8 /review）。
 */
@Tag(name = "复习", description = "今日复习概览/会话/提交结果")
@RestController
@RequestMapping("/api/v1/review")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @Operation(summary = "今日复习概览（pendingTotal/suggestedCount/分组）")
    @GetMapping("/today")
    public Result<Map<String, Object>> today() {
        return Result.ok(reviewService.todayOverview(AuthContext.getUserId()));
    }

    @Operation(summary = "开始复习会话（返回按调度排序的复习项，默认建议量 ≤20）")
    @PostMapping("/sessions")
    public Result<List<Map<String, Object>>> createSession() {
        return Result.ok(reviewService.createReviewSession(AuthContext.getUserId()));
    }

    @Operation(summary = "提交单项复习结果（幂等：当日同目标只推进一次）")
    @PostMapping("/sessions/{id}/items")
    public Result<Void> submitItem(@PathVariable Long id, @RequestBody SubmitItemRequest request) {
        reviewService.submitReviewResult(AuthContext.getUserId(),
                request.getTargetType(), request.getTargetId(), request.getIsCorrect());
        return Result.ok();
    }

    @Data
    public static class SubmitItemRequest {
        private Integer targetType;
        private Long targetId;
        private Boolean isCorrect;
    }
}
