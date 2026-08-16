package com.zhikao.server.controller;

import com.zhikao.server.common.Result;
import com.zhikao.server.security.AuthContext;
import com.zhikao.server.service.StatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 统计模块（契约：6.10 /stats）。
 */
@Tag(name = "统计", description = "学习统计 overview/trend/daily-report")
@RestController
@RequestMapping("/api/v1/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;

    @Operation(summary = "学习统计总览")
    @GetMapping("/overview")
    public Result<Map<String, Object>> overview() {
        return Result.ok(statsService.overview(AuthContext.getUserId()));
    }

    @Operation(summary = "近 N 天学习趋势（每日时长/答题数/正确率）")
    @GetMapping("/trend")
    public Result<List<Map<String, Object>>> trend(@RequestParam(defaultValue = "7") int days) {
        return Result.ok(statsService.trend(AuthContext.getUserId(), days));
    }

    @Operation(summary = "当日学习报告")
    @GetMapping("/daily-report")
    public Result<Map<String, Object>> dailyReport() {
        return Result.ok(statsService.dailyReport(AuthContext.getUserId()));
    }
}
