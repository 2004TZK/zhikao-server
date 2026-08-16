package com.zhikao.server.controller.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhikao.server.common.Result;
import com.zhikao.server.dto.IdiomRequest;
import com.zhikao.server.entity.Idiom;
import com.zhikao.server.entity.IdiomCategory;
import com.zhikao.server.service.IdiomAdminService;
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

import java.util.List;

/**
 * 管理后台-成语管理（契约：6.11 /admin/idiom）。
 * 一个成语可挂多个分类。
 */
@Tag(name = "管理后台-成语")
@RestController
@RequestMapping("/admin/idiom")
@RequiredArgsConstructor
public class AdminIdiomController {

    private final IdiomAdminService idiomAdminService;

    @Operation(summary = "成语分页列表")
    @GetMapping
    public Result<Page<Idiom>> page(@RequestParam(defaultValue = "1") int page,
                                    @RequestParam(defaultValue = "20") int size,
                                    @RequestParam(required = false) String keyword,
                                    @RequestParam(required = false) Long categoryId,
                                    @RequestParam(required = false) Integer status) {
        return Result.ok(idiomAdminService.page(page, size, keyword, categoryId, status));
    }

    @Operation(summary = "成语详情（含分类 id 列表）")
    @GetMapping("/{id}")
    public Result<IdiomAdminService.IdiomDetail> detail(@PathVariable Long id) {
        return Result.ok(idiomAdminService.detail(id));
    }

    @Operation(summary = "新增成语")
    @PostMapping
    public Result<Idiom> create(@Valid @RequestBody IdiomRequest request) {
        return Result.ok(idiomAdminService.create(request));
    }

    @Operation(summary = "更新成语")
    @PutMapping("/{id}")
    public Result<Idiom> update(@PathVariable Long id, @Valid @RequestBody IdiomRequest request) {
        request.setId(id);
        return Result.ok(idiomAdminService.update(request));
    }

    @Operation(summary = "下架成语（逻辑删除）")
    @DeleteMapping("/{id}/off-shelf")
    public Result<Void> offShelf(@PathVariable Long id) {
        idiomAdminService.offShelf(id);
        return Result.ok();
    }

    @Operation(summary = "上架成语")
    @PostMapping("/{id}/on-shelf")
    public Result<Void> onShelf(@PathVariable Long id) {
        idiomAdminService.onShelf(id);
        return Result.ok();
    }

    @Operation(summary = "成语分类列表")
    @GetMapping("/categories")
    public Result<List<IdiomCategory>> categories() {
        return Result.ok(idiomAdminService.categories());
    }

    @Operation(summary = "新增成语分类")
    @PostMapping("/categories")
    public Result<IdiomCategory> createCategory(@Valid @RequestBody CategoryRequest request) {
        return Result.ok(idiomAdminService.createCategory(request.getName(), request.getSort()));
    }

    public static class CategoryRequest {
        private String name;
        private Integer sort;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Integer getSort() {
            return sort;
        }

        public void setSort(Integer sort) {
            this.sort = sort;
        }
    }
}
