package com.zhikao.server.controller.admin;

import com.zhikao.server.common.Result;
import com.zhikao.server.service.ImportReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理后台-导入审核（契约：6.12 /admin/import/records）。
 * 状态机与审核红线见任务书 10.3。
 */
@Tag(name = "管理后台-导入审核")
@RestController
@RequestMapping("/admin/import/records")
@RequiredArgsConstructor
public class AdminImportReviewController {

    private final ImportReviewService importReviewService;

    @Operation(summary = "编辑草稿内容（仅待审核/已驳回可编辑，否则 4004；不改状态）")
    @PutMapping("/{id}")
    public Result<Void> edit(@PathVariable Long id, @RequestBody EditRequest request) {
        importReviewService.edit(id, request.getParsedContent(), request.getSourceTitle());
        return Result.ok();
    }

    @Operation(summary = "重新提交审核（仅已驳回草稿，REJECTED → PENDING_REVIEW）")
    @PostMapping("/{id}/submit")
    public Result<Void> submit(@PathVariable Long id) {
        importReviewService.submit(id);
        return Result.ok();
    }

    @Operation(summary = "审核通过 → 写入正式表并回写来源字段")
    @PostMapping("/{id}/approve")
    public Result<Void> approve(@PathVariable Long id) {
        // T3.1 开发期：管理员 id 固定 1（admin_user 表落地后从上下文取）
        importReviewService.approve(id, 1L);
        return Result.ok();
    }

    @Operation(summary = "驳回（必填 errorMessage）")
    @PostMapping("/{id}/reject")
    public Result<Void> reject(@PathVariable Long id, @RequestBody RejectRequest request) {
        importReviewService.reject(id, request.getErrorMessage(), 1L);
        return Result.ok();
    }

    @Operation(summary = "批量审核通过（逐条处理，部分失败不影响成功条目）")
    @PostMapping("/batch-approve")
    public Result<java.util.List<ImportReviewService.BatchApproveResult>> batchApprove(
            @RequestBody BatchApproveRequest request) {
        return Result.ok(importReviewService.batchApprove(request.getRecordIds(), 1L));
    }

    @Data
    public static class BatchApproveRequest {
        private java.util.List<Long> recordIds;
    }

    @Data
    public static class EditRequest {
        private String parsedContent;
        private String sourceTitle;
    }

    @Data
    public static class RejectRequest {
        @NotBlank(message = "驳回原因必填")
        private String errorMessage;
    }
}
