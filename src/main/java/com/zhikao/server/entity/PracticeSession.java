package com.zhikao.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 练习场次实体（表：practice_session）。
 */
@Data
@TableName("practice_session")
public class PracticeSession {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 类型：1 每日挑战 2 专项训练 3 随机训练 4 复习 5 错题重练 */
    private Integer type;

    /** ref_id 指向对象类型：1 常识分类 2 成语分类 3 知识点 4 成语 5 错题 6 无 */
    private Integer refType;

    /** ref_type 对应目标 id（ref_type=6 时为 NULL） */
    private Long refId;

    private Integer totalCount;

    private Integer correctCount;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private LocalDateTime createdAt;
}
