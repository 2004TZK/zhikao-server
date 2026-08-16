package com.zhikao.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户-知识点学习状态实体（表：user_knowledge，掌握度核心表）。
 */
@Data
@TableName("user_knowledge")
public class UserKnowledge {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long knowledgeId;

    /** 掌握度：0 未学习 1 学习中 2 已学习 3 掌握 4 熟练 */
    private Integer mastery;

    /** 当前复习阶段（间隔梯度索引 0~5） */
    private Integer reviewStage;

    private Integer studyCount;

    private Integer correctCount;

    private Integer wrongCount;

    private LocalDateTime lastStudyTime;

    private LocalDateTime nextReviewTime;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
