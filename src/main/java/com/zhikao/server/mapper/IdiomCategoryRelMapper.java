package com.zhikao.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhikao.server.entity.IdiomCategoryRel;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface IdiomCategoryRelMapper extends BaseMapper<IdiomCategoryRel> {

    /** 删除某成语的全部分类关联（更新成语分类时先清后写） */
    @Delete("DELETE FROM idiom_category_rel WHERE idiom_id = #{idiomId}")
    int deleteByCategoryRelIdiomId(Long idiomId);
}
