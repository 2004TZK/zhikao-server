package com.zhikao.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 常识分类实体（表：knowledge_category）。
 */
@Data
@TableName("knowledge_category")
public class KnowledgeCategory {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String icon;

    private Integer sort;

    /** 状态：0 下架 1 上架 */
    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
