package com.zhikao.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 错题本实体（表：wrong_question）。
 * 不存题目快照（v1.3）：只保存 question_id，展示实时 question 内容。
 */
@Data
@TableName("wrong_question")
public class WrongQuestion {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long questionId;

    private Integer wrongCount;

    private LocalDateTime lastWrongTime;

    /** 是否移出：0 在错题本 1 已移出 */
    private Integer isRemoved;

    private LocalDateTime createdAt;
}
