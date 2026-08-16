package com.zhikao.server.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 成语保存/更新请求（管理后台，契约 6.11）。
 * 一个成语可挂多个分类（categoryIds）。
 */
@Data
public class IdiomRequest {

    private Long id;

    @NotBlank(message = "成语不能为空")
    @Size(max = 20, message = "成语最长 20 字")
    private String word;

    @Size(max = 100, message = "拼音最长 100 字")
    private String pinyin;

    private String explanation;

    private String origin;

    private String example;

    /** 近义词（JSON 数组） */
    private String synonyms;

    /** 反义词（JSON 数组） */
    private String antonyms;

    /** 易混成语及辨析（JSON） */
    private String confusing;

    /** 常见误用 */
    private String commonError;

    @NotNull(message = "难度不能为空")
    @Min(value = 1, message = "难度 1~5")
    @Max(value = 5, message = "难度 1~5")
    private Integer difficulty;

    private Integer status;

    /** 多分类 id 列表（一个成语可挂多个分类） */
    private List<Long> categoryIds;

    @NotNull(message = "来源类型必填")
    @Min(value = 1, message = "来源类型 1~4")
    @Max(value = 4, message = "来源类型 1~4")
    private Integer sourceType;

    private Long sourceDocumentId;

    @Size(max = 200, message = "来源标题最长 200 字")
    private String sourceTitle;
}
