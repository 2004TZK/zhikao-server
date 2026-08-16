package com.zhikao.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 题目实体（表：question）。
 * 硬约束（5.2）：knowledge_id 与 idiom_id 至少一个非空。
 */
@Data
@TableName("question")
public class Question {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 题型：1 单选（V1.0 只用） */
    private Integer type;

    private String content;

    private String analysis;

    private Integer difficulty;

    private Long knowledgeId;

    private Long idiomId;

    /** 来源：1 真题 2 模拟题 3 自编题 4 AI 生成题（V2.0） */
    private Integer sourceType;

    private String sourceName;

    private Integer examYear;

    private String province;

    private String questionNo;

    private Long sourceDocumentId;

    /** 状态：0 下架 1 上架 */
    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
