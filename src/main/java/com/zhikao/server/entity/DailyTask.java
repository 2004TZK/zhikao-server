package com.zhikao.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 每日任务实体（表：daily_task）。
 */
@Data
@TableName("daily_task")
public class DailyTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private LocalDate taskDate;

    /** 任务类型：1 常识积累 2 成语训练 3 今日复习 */
    private Integer taskType;

    /** 目标数量（复习类=min(到期数,20)） */
    private Integer targetCount;

    private Integer doneCount;

    /** 状态：0 未完成 1 已完成 */
    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
