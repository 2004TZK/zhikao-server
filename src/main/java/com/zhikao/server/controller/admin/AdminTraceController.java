package com.zhikao.server.controller.admin;

import com.zhikao.server.common.Result;
import com.zhikao.server.service.SourceTraceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 来源追溯（契约：任务书 10.5 / T3.11）。
 * 正式内容 → 来源文件 → import_record → 原文摘录 端到端追溯。
 */
@Tag(name = "管理后台-来源追溯")
@RestController
@RequestMapping("/admin/trace")
@RequiredArgsConstructor
public class AdminTraceController {

    private final SourceTraceService sourceTraceService;

    @Operation(summary = "追溯正式内容来源（contentType 1 知识点 2 成语 3 题目）")
    @GetMapping
    public Result<SourceTraceService.TraceResult> trace(
            @RequestParam Integer contentType,
            @RequestParam Long targetId) {
        return Result.ok(sourceTraceService.trace(contentType.longValue(), targetId));
    }
}
