package com.zhikao.server.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhikao.server.common.Result;
import com.zhikao.server.entity.Knowledge;
import com.zhikao.server.security.AuthContext;
import com.zhikao.server.service.FavoriteService;
import com.zhikao.server.service.KnowledgeService;
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
 * 常识模块（契约：6.4 /knowledge）。
 */
@Tag(name = "常识", description = "常识知识点分类/列表/详情/学习/收藏/已掌握")
@RestController
@RequestMapping("/api/v1/knowledge")
@RequiredArgsConstructor
public class KnowledgeController {

    private final KnowledgeService knowledgeService;
    private final FavoriteService favoriteService;

    @Operation(summary = "分类列表（含各分类知识点数、已掌握数）")
    @GetMapping("/categories")
    public Result<List<Map<String, Object>>> categories() {
        return Result.ok(knowledgeService.categories(AuthContext.getUserId()));
    }

    @Operation(summary = "列表（categoryId/keyword/mastery + 分页）")
    @GetMapping("/list")
    public Result<Page<Knowledge>> list(@RequestParam(required = false) Long categoryId,
                                        @RequestParam(required = false) String keyword,
                                        @RequestParam(required = false) Integer mastery,
                                        @RequestParam(defaultValue = "1") int page,
                                        @RequestParam(defaultValue = "20") int size) {
        // 分页边界（6.1：1<=size<=50）
        size = Math.min(Math.max(size, 1), 50);
        return Result.ok(knowledgeService.page(AuthContext.getUserId(), categoryId, keyword, mastery, page, size));
    }

    @Operation(summary = "详情（含掌握状态/收藏状态/相关真题反查）")
    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        return Result.ok(knowledgeService.detail(AuthContext.getUserId(), id));
    }

    @Operation(summary = "标记已掌握")
    @PostMapping("/{id}/mastered")
    public Result<Void> mastered(@PathVariable Long id) {
        knowledgeService.markMastered(AuthContext.getUserId(), id);
        return Result.ok();
    }

    @Operation(summary = "收藏/取消收藏（toggle）")
    @PostMapping("/{id}/favorite")
    public Result<Boolean> favorite(@PathVariable Long id) {
        boolean favorited = favoriteService.toggle(AuthContext.getUserId(), 1, id);
        return Result.ok(favorited);
    }
}
