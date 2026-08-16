package com.zhikao.server.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhikao.server.common.Result;
import com.zhikao.server.entity.ImportDocument;
import com.zhikao.server.mapper.ImportDocumentMapper;
import com.zhikao.server.service.DocumentImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 管理后台-知识库导入（契约：6.12 /admin/import）。
 * T3.6：文件上传/任务列表/详情（解析与审核在 T3.7~T3.11）。
 */
@Tag(name = "管理后台-知识库导入")
@RestController
@RequestMapping("/admin/import")
@RequiredArgsConstructor
public class AdminImportController {

    private final DocumentImportService documentImportService;
    private final ImportDocumentMapper importDocumentMapper;

    @Operation(summary = "上传文档（≤10 个/批，白名单类型，≤20MB）")
    @PostMapping("/upload")
    public Result<List<DocumentImportService.UploadResult>> upload(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam("importType") Integer importType) {
        // T3.1 开发期：管理员 id 从配置账号取 1（后续 admin_user 表落地后从上下文取）
        return Result.ok(documentImportService.upload(files, importType, 1L));
    }

    @Operation(summary = "导入任务列表（分页）")
    @GetMapping("/documents")
    public Result<Page<ImportDocument>> documents(@RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(defaultValue = "20") int size,
                                                  @RequestParam(required = false) Integer status) {
        LambdaQueryWrapper<ImportDocument> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(ImportDocument::getStatus, status);
        }
        wrapper.orderByDesc(ImportDocument::getCreatedAt);
        return Result.ok(importDocumentMapper.selectPage(new Page<>(page, size), wrapper));
    }

    @Operation(summary = "开始/重新解析（异步，状态 0→1→2/5）")
    @PostMapping("/documents/{id}/parse")
    public Result<Void> parse(@PathVariable Long id) {
        documentImportService.parseDocument(id);
        return Result.ok();
    }

    @Operation(summary = "导入任务详情")
    @GetMapping("/documents/{id}")
    public Result<ImportDocument> documentDetail(@PathVariable Long id) {
        ImportDocument doc = importDocumentMapper.selectById(id);
        if (doc == null) {
            return Result.fail(com.zhikao.server.common.ErrorCode.NOT_FOUND, "导入任务不存在");
        }
        return Result.ok(doc);
    }
}
