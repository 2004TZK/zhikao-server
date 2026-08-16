package com.zhikao.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 题目选项实体（表：question_option）。
 */
@Data
@TableName("question_option")
public class QuestionOption {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long questionId;

    /** 选项标号 A/B/C/D... */
    private String label;

    private String content;

    /** 是否正确答案：0 否 1 是 */
    private Integer isCorrect;

    private LocalDateTime createdAt;
}
