package com.zhikao.server.controller.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhikao.server.common.Result;
import com.zhikao.server.dto.QuestionRequest;
import com.zhikao.server.entity.Question;
import com.zhikao.server.service.QuestionAdminService;
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

/**
 * 管理后台-题目管理（契约：6.11 /admin/question）。
 * 保存时强制 5.2 选项约束与关联校验（必须至少关联一个知识点或成语，否则 1003）。
 */
@Tag(name = "管理后台-题目")
@RestController
@RequestMapping("/admin/question")
@RequiredArgsConstructor
public class AdminQuestionController {

    private final QuestionAdminService questionAdminService;

    @Operation(summary = "题目分页列表")
    @GetMapping
    public Result<Page<Question>> page(@RequestParam(defaultValue = "1") int page,
                                       @RequestParam(defaultValue = "20") int size,
                                       @RequestParam(required = false) String keyword,
                                       @RequestParam(required = false) Integer sourceType,
                                       @RequestParam(required = false) Integer status) {
        return Result.ok(questionAdminService.page(page, size, keyword, sourceType, status));
    }

    @Operation(summary = "题目详情（含选项列表）")
    @GetMapping("/{id}")
    public Result<QuestionAdminService.QuestionDetail> detail(@PathVariable Long id) {
        return Result.ok(questionAdminService.detail(id));
    }

    @Operation(summary = "新增题目（2~6 选项 + 唯一答案 + 至少关联一个知识点/成语）")
    @PostMapping
    public Result<Question> create(@Valid @RequestBody QuestionRequest request) {
        return Result.ok(questionAdminService.create(request));
    }

    @Operation(summary = "更新题目")
    @PutMapping("/{id}")
    public Result<Question> update(@PathVariable Long id, @Valid @RequestBody QuestionRequest request) {
        request.setId(id);
        return Result.ok(questionAdminService.update(request));
    }

    @Operation(summary = "下架题目（逻辑删除）")
    @DeleteMapping("/{id}/off-shelf")
    public Result<Void> offShelf(@PathVariable Long id) {
        questionAdminService.offShelf(id);
        return Result.ok();
    }

    @Operation(summary = "上架题目")
    @PostMapping("/{id}/on-shelf")
    public Result<Void> onShelf(@PathVariable Long id) {
        questionAdminService.onShelf(id);
        return Result.ok();
    }
}
