package com.zhikao.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 成语-分类关联实体（表：idiom_category_rel，多对多）。
 */
@Data
@TableName("idiom_category_rel")
public class IdiomCategoryRel {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long idiomId;

    private Long categoryId;

    private LocalDateTime createdAt;
}
