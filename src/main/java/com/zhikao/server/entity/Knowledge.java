package com.zhikao.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 常识知识点实体（表：knowledge）。
 */
@Data
@TableName("knowledge")
public class Knowledge {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private Long categoryId;

    /** 一句话记忆 */
    private String summary;

    /** 核心知识 */
    private String content;

    /** 重点内容（JSON 数组） */
    private String keyPoints;

    /** 易错点 */
    private String commonMistakes;

    /** 难度 1-5 */
    private Integer difficulty;

    /** 状态：0 下架 1 上架 */
    private Integer status;

    /** 来源：1 自建 2 公共领域资料 3 合法授权 4 文档导入 */
    private Integer sourceType;

    /** 来源 import_document.id（source_type=4 必填） */
    private Long sourceDocumentId;

    /** 来源文件名/原始资料标题 */
    private String sourceTitle;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
