package com.zhikao.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhikao.server.entity.QuestionOption;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface QuestionOptionMapper extends BaseMapper<QuestionOption> {

    /** 删除某题全部选项（更新时先清后写） */
    @Delete("DELETE FROM question_option WHERE question_id = #{questionId}")
    int deleteByQuestionId(Long questionId);
}
