package com.zhikao.server.controller;

import com.zhikao.server.common.Result;
import com.zhikao.server.security.AuthContext;
import com.zhikao.server.service.FavoriteService;
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
 * 收藏模块（契约：6.9 GET /favorite/list）。
 */
@Tag(name = "收藏", description = "收藏列表")
@RestController
@RequestMapping("/api/v1/favorite")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favoriteService;

    @Operation(summary = "收藏列表（targetType 1 知识点 2 成语，含标题）")
    @GetMapping("/list")
    public Result<List<Map<String, Object>>> list(@RequestParam Integer targetType) {
        return Result.ok(favoriteService.listWithTitle(AuthContext.getUserId(), targetType));
    }
}
