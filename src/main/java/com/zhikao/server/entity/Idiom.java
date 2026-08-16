package com.zhikao.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 成语实体（表：idiom）。
 */
@Data
@TableName("idiom")
public class Idiom {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String word;

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

    private Integer difficulty;

    /** 状态：0 下架 1 上架 */
    private Integer status;

    /** 来源：1 自建 2 公共领域资料 3 合法授权 4 文档导入 */
    private Integer sourceType;

    private Long sourceDocumentId;

    private String sourceTitle;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
