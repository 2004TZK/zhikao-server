package com.zhikao.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhikao.server.entity.Knowledge;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface KnowledgeMapper extends BaseMapper<Knowledge> {
}
