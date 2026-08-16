package com.zhikao.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 答题明细实体（表：user_answer）。
 */
@Data
@TableName("user_answer")
public class UserAnswer {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long sessionId;

    private Long questionId;

    private Long selectedOptionId;

    /** 是否正确：0 否 1 是 */
    private Integer isCorrect;

    private LocalDateTime answerTime;

    private LocalDateTime createdAt;
}
