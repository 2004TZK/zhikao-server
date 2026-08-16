package com.zhikao.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库导入：解析草稿实体（表：import_record）。
 */
@Data
@TableName("import_record")
public class ImportRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long documentId;

    /** 内容类型：1 知识点 2 成语 3 题目 */
    private Integer contentType;

    /** 解析出的块标题 */
    private String sourceTitle;

    /** 结构化解结果（JSON：title/summary/keyPoints/commonMistakes/category 等） */
    private String parsedContent;

    /** 对应原文摘录（审核对照用） */
    private String rawExcerpt;

    /** 状态：0 待审核 1 已通过入库 2 已驳回 3 已失效 */
    private Integer status;

    private String errorMessage;

    /** 入库后对应的 knowledge/idiom/question id */
    private Long targetId;

    private Long reviewedBy;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
