package com.zhikao.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 学习记录实体（表：study_record，统计数据事实来源，7.7）。
 */
@Data
@TableName("study_record")
public class StudyRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 记录类型：1 学知识点 2 学成语 3 练习 4 复习 */
    private Integer recordType;

    private Long targetId;

    /** 本次时长（秒），服务端校验 0~1800 */
    private Integer durationSeconds;

    private LocalDateTime createdAt;
}
