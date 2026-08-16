package com.zhikao.server.controller;

import com.zhikao.server.common.Result;
import com.zhikao.server.security.AuthContext;
import com.zhikao.server.service.HomeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 首页模块（契约：6.3 /home）。
 */
@Tag(name = "首页", description = "首页聚合与推荐")
@RestController
@RequestMapping("/api/v1/home")
@RequiredArgsConstructor
public class HomeController {

    private final HomeService homeService;

    @Operation(summary = "首页聚合（连续天数/今日完成度/任务/积压/时长）")
    @GetMapping("/overview")
    public Result<Map<String, Object>> overview() {
        return Result.ok(homeService.overview(AuthContext.getUserId()));
    }

    @Operation(summary = "今日推荐（常识 3 + 成语 3，7.6 算法）")
    @GetMapping("/recommend")
    public Result<Map<String, Object>> recommend() {
        return Result.ok(homeService.recommend(AuthContext.getUserId()));
    }
}
