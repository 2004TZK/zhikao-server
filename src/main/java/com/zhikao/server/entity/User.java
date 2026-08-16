package com.zhikao.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 用户实体（表：user）。
 * 正式用户与游客共用同一 user_id 体系（v1.4 定稿）。
 */
@Data
@TableName("user")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户类型：1 正式用户 2 游客 */
    private Integer userType;

    /** 登录账号名（游客为系统生成占位名，不用于登录） */
    private String username;

    /** 游客身份 UUID（user_type=2 必填） */
    private String guestUuid;

    /** BCrypt 密码哈希（游客为 NULL，严禁存明文） */
    private String passwordHash;

    private String nickname;

    private String avatar;

    /** 状态：0 正常 1 禁用 2 已注销 */
    private Integer status;

    private Integer streakDays;

    private LocalDate lastStudyDate;

    /** 累计学习时长（分钟），冗余缓存，事实来源=study_record 聚合 */
    private Integer totalStudyMinutes;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
