package com.zhikao.server.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 知识点保存/更新请求（管理后台，契约 6.11）。
 * 录入必填来源字段（任务书 9.3 通道一）。
 */
@Data
public class KnowledgeRequest {

    private Long id;

    @NotBlank(message = "标题不能为空")
    @Size(max = 100, message = "标题最长 100 字")
    private String title;

    @NotNull(message = "分类不能为空")
    private Long categoryId;

    @Size(max = 255, message = "一句话记忆最长 255 字")
    private String summary;

    private String content;

    /** 重点内容（JSON 数组） */
    private String keyPoints;

    private String commonMistakes;

    @NotNull(message = "难度不能为空")
    @Min(value = 1, message = "难度 1~5")
    @Max(value = 5, message = "难度 1~5")
    private Integer difficulty;

    /** 状态：0 下架 1 上架 */
    private Integer status;

    @NotNull(message = "来源类型必填（1 自建 2 公共领域资料 3 合法授权 4 文档导入）")
    @Min(value = 1, message = "来源类型 1~4")
    @Max(value = 4, message = "来源类型 1~4")
    private Integer sourceType;

    /** 来源 import_document.id（source_type=4 时必填，见 5.2） */
    private Long sourceDocumentId;

    /** 来源文件名/原始资料标题 */
    @Size(max = 200, message = "来源标题最长 200 字")
    private String sourceTitle;
}
