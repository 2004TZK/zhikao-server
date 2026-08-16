package com.zhikao.server.controller.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhikao.server.common.Result;
import com.zhikao.server.dto.KnowledgeRequest;
import com.zhikao.server.entity.Knowledge;
import com.zhikao.server.entity.KnowledgeCategory;
import com.zhikao.server.service.KnowledgeAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理后台-知识点管理（契约：6.11 /admin/knowledge）。
 * 删除仅下架；录入必填来源字段。
 */
@Tag(name = "管理后台-知识点")
@RestController
@RequestMapping("/admin/knowledge")
@RequiredArgsConstructor
public class AdminKnowledgeController {

    private final KnowledgeAdminService knowledgeAdminService;

    @Operation(summary = "知识点分页列表")
    @GetMapping
    public Result<Page<Knowledge>> page(@RequestParam(defaultValue = "1") int page,
                                        @RequestParam(defaultValue = "20") int size,
                                        @RequestParam(required = false) String keyword,
                                        @RequestParam(required = false) Long categoryId,
                                        @RequestParam(required = false) Integer status) {
        return Result.ok(knowledgeAdminService.page(page, size, keyword, categoryId, status));
    }

    @Operation(summary = "知识点详情")
    @GetMapping("/{id}")
    public Result<Knowledge> detail(@PathVariable Long id) {
        return Result.ok(knowledgeAdminService.detail(id));
    }

    @Operation(summary = "新增知识点")
    @PostMapping
    public Result<Knowledge> create(@Valid @RequestBody KnowledgeRequest request) {
        return Result.ok(knowledgeAdminService.create(request));
    }

    @Operation(summary = "更新知识点")
    @PutMapping("/{id}")
    public Result<Knowledge> update(@PathVariable Long id, @Valid @RequestBody KnowledgeRequest request) {
        request.setId(id);
        return Result.ok(knowledgeAdminService.update(request));
    }

    @Operation(summary = "下架知识点（逻辑删除，非物理）")
    @DeleteMapping("/{id}/off-shelf")
    public Result<Void> offShelf(@PathVariable Long id) {
        knowledgeAdminService.offShelf(id);
        return Result.ok();
    }

    @Operation(summary = "上架知识点")
    @PostMapping("/{id}/on-shelf")
    public Result<Void> onShelf(@PathVariable Long id) {
        knowledgeAdminService.onShelf(id);
        return Result.ok();
    }

    @Operation(summary = "分类列表")
    @GetMapping("/categories")
    public Result<List<KnowledgeCategory>> categories() {
        return Result.ok(knowledgeAdminService.categories());
    }

    @Operation(summary = "新增分类")
    @PostMapping("/categories")
    public Result<KnowledgeCategory> createCategory(@Valid @RequestBody CategoryRequest request) {
        return Result.ok(knowledgeAdminService.createCategory(
                request.getName(), request.getIcon(), request.getSort()));
    }

    @Operation(summary = "状态统计（上下架数量，供管理页展示）")
    @GetMapping("/stats")
    public Result<Map<String, Long>> stats() {
        Map<String, Long> map = new HashMap<>();
        map.put("total", knowledgeAdminService.page(1, 1, null, null, null).getTotal());
        map.put("onShelf", knowledgeAdminService.page(1, 1, null, null, 1).getTotal());
        map.put("offShelf", knowledgeAdminService.page(1, 1, null, null, 0).getTotal());
        return Result.ok(map);
    }

    public static class CategoryRequest {
        private String name;
        private String icon;
        private Integer sort;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getIcon() {
            return icon;
        }

        public void setIcon(String icon) {
            this.icon = icon;
        }

        public Integer getSort() {
            return sort;
        }

        public void setSort(Integer sort) {
            this.sort = sort;
        }
    }
}
