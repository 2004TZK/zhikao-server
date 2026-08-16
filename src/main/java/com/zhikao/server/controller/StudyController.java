package com.zhikao.server.controller;

import com.zhikao.server.common.Result;
import com.zhikao.server.security.AuthContext;
import com.zhikao.server.service.StudyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 学习行为上报（契约 6.4/6.5，T4.6）。
 * POST /knowledge/{id}/study、/idiom/{id}/study
 */
@Tag(name = "学习", description = "学习行为上报与连续天数")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class StudyController {

    private final StudyService studyService;

    @Operation(summary = "上报学习知识点（可携带 durationSeconds，0~1800 防刷）")
    @PostMapping("/knowledge/{id}/study")
    public Result<Void> studyKnowledge(@PathVariable Long id, @RequestBody(required = false) StudyRequest request) {
        studyService.reportKnowledgeStudy(AuthContext.getUserId(), id,
                request == null ? null : request.getDurationSeconds());
        return Result.ok();
    }

    @Operation(summary = "上报学习成语")
    @PostMapping("/idiom/{id}/study")
    public Result<Void> studyIdiom(@PathVariable Long id, @RequestBody(required = false) StudyRequest request) {
        studyService.reportIdiomStudy(AuthContext.getUserId(), id,
                request == null ? null : request.getDurationSeconds());
        return Result.ok();
    }

    @Data
    public static class StudyRequest {
        /** 学习时长（秒），服务端裁剪 0~1800 */
        private Integer durationSeconds;
    }
}
