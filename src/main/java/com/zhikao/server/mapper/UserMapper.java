package com.zhikao.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhikao.server.entity.User;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户 Mapper。
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

    /**
     * 账号注销：按 5.3 定死策略物理删除用户全部行为数据（单事务由调用方保证）。
     * 表名单固定，无用户输入拼接，不存在 SQL 注入风险。
     */
    @Delete("DELETE FROM user_knowledge WHERE user_id = #{userId}")
    int deleteUserKnowledge(Long userId);

    @Delete("DELETE FROM user_idiom WHERE user_id = #{userId}")
    int deleteUserIdiom(Long userId);

    @Delete("DELETE FROM favorite WHERE user_id = #{userId}")
    int deleteFavorite(Long userId);

    @Delete("DELETE FROM daily_task WHERE user_id = #{userId}")
    int deleteDailyTask(Long userId);

    @Delete("DELETE FROM practice_session WHERE user_id = #{userId}")
    int deletePracticeSession(Long userId);

    @Delete("DELETE FROM user_answer WHERE user_id = #{userId}")
    int deleteUserAnswer(Long userId);

    @Delete("DELETE FROM wrong_question WHERE user_id = #{userId}")
    int deleteWrongQuestion(Long userId);

    @Delete("DELETE FROM study_record WHERE user_id = #{userId}")
    int deleteStudyRecord(Long userId);

    @Delete("DELETE FROM review_record WHERE user_id = #{userId}")
    int deleteReviewRecord(Long userId);
}
