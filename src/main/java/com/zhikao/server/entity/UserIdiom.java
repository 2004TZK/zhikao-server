package com.zhikao.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户-成语学习状态实体（表：user_idiom）。
 */
@Data
@TableName("user_idiom")
public class UserIdiom {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long idiomId;

    private Integer mastery;

    private Integer reviewStage;

    private Integer studyCount;

    private Integer correctCount;

    private Integer wrongCount;

    private LocalDateTime lastStudyTime;

    private LocalDateTime nextReviewTime;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
