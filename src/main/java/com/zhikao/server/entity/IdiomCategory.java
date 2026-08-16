package com.zhikao.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 成语分类实体（表：idiom_category）。
 */
@Data
@TableName("idiom_category")
public class IdiomCategory {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private Integer sort;

    /** 状态：0 下架 1 上架 */
    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
