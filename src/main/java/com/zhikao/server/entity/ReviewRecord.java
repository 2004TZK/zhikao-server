package com.zhikao.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 复习记录实体（表：review_record）。
 */
@Data
@TableName("review_record")
public class ReviewRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 目标类型：1 知识点 2 成语 */
    private Integer targetType;

    private Long targetId;

    /** 复习类型：1 即将遗忘 2 普通 3 错题强化 */
    private Integer reviewType;

    /** 复习自测结果：0 错 1 对 */
    private Integer isCorrect;

    private LocalDateTime createdAt;
}
