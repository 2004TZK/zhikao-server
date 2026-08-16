package com.zhikao.server.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 题目保存/更新请求（管理后台，契约 6.11）。
 * 硬约束（5.2，后端与管理后台同时校验）：
 *  - 选项数量 2~6
 *  - 必须且只能存在 1 个 is_correct=1 的选项
 *  - knowledge_id 与 idiom_id 至少一个非空（否则 1003）
 */
@Data
public class QuestionRequest {

    private Long id;

    /** 题型：1 单选（V1.0 只用） */
    private Integer type;

    @NotBlank(message = "题干不能为空")
    private String content;

    private String analysis;

    @NotNull(message = "难度不能为空")
    @Min(value = 1, message = "难度 1~5")
    @Max(value = 5, message = "难度 1~5")
    private Integer difficulty;

    /** 关联常识知识点（与 idiomId 至少一个非空） */
    private Long knowledgeId;

    /** 关联成语 */
    private Long idiomId;

    @NotNull(message = "来源类型必填")
    @Min(value = 1, message = "来源类型 1~4")
    @Max(value = 4, message = "来源类型 1~4")
    private Integer sourceType;

    /** 来源名称（如"2024 年国考行测"；真题/模拟题必填，见 9.3） */
    private String sourceName;

    private Integer examYear;

    private String province;

    private String questionNo;

    private Long sourceDocumentId;

    private Integer status;

    /** 选项列表（2~6 个，必须且只能一个正确） */
    @NotEmpty(message = "选项不能为空")
    @Size(min = 2, max = 6, message = "选项数量需在 2~6 之间")
    @Valid
    private List<OptionRequest> options;

    @Data
    public static class OptionRequest {
        private Long id;
        /** 选项标号 A/B/C/D... */
        private String label;
        @NotBlank(message = "选项内容不能为空")
        private String content;
        /** 是否正确答案：0 否 1 是 */
        private Integer isCorrect;
    }
}
