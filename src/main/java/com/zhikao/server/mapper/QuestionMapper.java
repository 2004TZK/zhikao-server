package com.zhikao.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhikao.server.entity.Question;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface QuestionMapper extends BaseMapper<Question> {
}
