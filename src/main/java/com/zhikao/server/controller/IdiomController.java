package com.zhikao.server.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhikao.server.common.Result;
import com.zhikao.server.entity.Idiom;
import com.zhikao.server.entity.IdiomCategory;
import com.zhikao.server.security.AuthContext;
import com.zhikao.server.service.FavoriteService;
import com.zhikao.server.service.IdiomService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 成语模块（契约：6.5 /idiom）。
 */
@Tag(name = "成语", description = "成语分类/列表/详情/学习/收藏")
@RestController
@RequestMapping("/api/v1/idiom")
@RequiredArgsConstructor
public class IdiomController {

    private final IdiomService idiomService;
    private final FavoriteService favoriteService;

    @Operation(summary = "分类列表")
    @GetMapping("/categories")
    public Result<List<IdiomCategory>> categories() {
        return Result.ok(idiomService.categories());
    }

    @Operation(summary = "列表（categoryId/keyword + 分页）")
    @GetMapping("/list")
    public Result<Page<Idiom>> list(@RequestParam(required = false) Long categoryId,
                                    @RequestParam(required = false) String keyword,
                                    @RequestParam(defaultValue = "1") int page,
                                    @RequestParam(defaultValue = "20") int size) {
        size = Math.min(Math.max(size, 1), 50);
        return Result.ok(idiomService.page(categoryId, keyword, page, size));
    }

    @Operation(summary = "详情（含相关题目反查）")
    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        return Result.ok(idiomService.detail(AuthContext.getUserId(), id));
    }

    @Operation(summary = "收藏/取消收藏（toggle）")
    @PostMapping("/{id}/favorite")
    public Result<Boolean> favorite(@PathVariable Long id) {
        boolean favorited = favoriteService.toggle(AuthContext.getUserId(), 2, id);
        return Result.ok(favorited);
    }
}
