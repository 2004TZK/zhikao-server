package com.zhikao.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhikao.server.common.BizException;
import com.zhikao.server.common.ErrorCode;
import com.zhikao.server.entity.StudyRecord;
import com.zhikao.server.entity.User;
import com.zhikao.server.entity.UserKnowledge;
import com.zhikao.server.entity.UserIdiom;
import com.zhikao.server.mapper.StudyRecordMapper;
import com.zhikao.server.mapper.UserKnowledgeMapper;
import com.zhikao.server.mapper.UserIdiomMapper;
import com.zhikao.server.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 学习行为上报服务（T4.6，任务书 7.5/6.4/6.5）。
 *  - 上报"学习了"：初始化/更新 user_knowledge / user_idiom（7.1 状态机：未学习→学习中）
 *  - 学习时长防刷（6.1）：durationSeconds 0~1800 裁剪
 *  - 连续学习天数（7.5）：今天首次有效行为时更新 streak_days / last_study_date
 *  - 写入 study_record（事实来源，7.7）
 */
@Service
@RequiredArgsConstructor
public class StudyService {

    private static final int MAX_DURATION_SECONDS = 1800;

    private final UserMapper userMapper;
    private final UserKnowledgeMapper userKnowledgeMapper;
    private final UserIdiomMapper userIdiomMapper;
    private final StudyRecordMapper studyRecordMapper;
    private final DailyTaskService dailyTaskService;

    /** 上报学习知识点（契约 6.4 POST /knowledge/{id}/study） */
    @Transactional
    public void reportKnowledgeStudy(Long userId, Long knowledgeId, Integer durationSeconds) {
        // 知识点存在性由 Controller/上游校验，此处聚焦学习状态
        UserKnowledge record = userKnowledgeMapper.selectOne(
                new LambdaQueryWrapper<UserKnowledge>()
                        .eq(UserKnowledge::getUserId, userId)
                        .eq(UserKnowledge::getKnowledgeId, knowledgeId));
        if (record == null) {
            record = new UserKnowledge();
            record.setUserId(userId);
            record.setKnowledgeId(knowledgeId);
            record.setMastery(1); // 未学习 → 学习中（7.1）
            record.setReviewStage(0);
            record.setStudyCount(1);
            record.setCorrectCount(0);
            record.setWrongCount(0);
            record.setLastStudyTime(LocalDateTime.now());
            userKnowledgeMapper.insert(record);
        } else {
            record.setMastery(Math.max(record.getMastery(), 1));
            record.setStudyCount(record.getStudyCount() + 1);
            record.setLastStudyTime(LocalDateTime.now());
            userKnowledgeMapper.updateById(record);
        }
        writeStudyRecord(userId, 1, knowledgeId, durationSeconds);
    }

    /** 上报学习成语（契约 6.5 POST /idiom/{id}/study） */
    @Transactional
    public void reportIdiomStudy(Long userId, Long idiomId, Integer durationSeconds) {
        UserIdiom record = userIdiomMapper.selectOne(
                new LambdaQueryWrapper<UserIdiom>()
                        .eq(UserIdiom::getUserId, userId)
                        .eq(UserIdiom::getIdiomId, idiomId));
        if (record == null) {
            record = new UserIdiom();
            record.setUserId(userId);
            record.setIdiomId(idiomId);
            record.setMastery(1);
            record.setReviewStage(0);
            record.setStudyCount(1);
            record.setCorrectCount(0);
            record.setWrongCount(0);
            record.setLastStudyTime(LocalDateTime.now());
            userIdiomMapper.insert(record);
        } else {
            record.setMastery(Math.max(record.getMastery(), 1));
            record.setStudyCount(record.getStudyCount() + 1);
            record.setLastStudyTime(LocalDateTime.now());
            userIdiomMapper.updateById(record);
        }
        writeStudyRecord(userId, 2, idiomId, durationSeconds);
    }

    /** 写学习记录 + 连续天数更新 + 每日任务进度 */
    private void writeStudyRecord(Long userId, Integer recordType, Long targetId, Integer durationSeconds) {
        int duration = sanitizeDuration(durationSeconds);
        StudyRecord record = new StudyRecord();
        record.setUserId(userId);
        record.setRecordType(recordType);
        record.setTargetId(targetId);
        record.setDurationSeconds(duration);
        studyRecordMapper.insert(record);

        updateStreak(userId);
        // 常识积累任务（taskType=1）进度（幂等由 7.4 规则处理）
        dailyTaskService.reportProgress(userId, 1, "k" + targetId, null);
    }

    /** 时长防刷：0~1800 裁剪（6.1） */
    private int sanitizeDuration(Integer durationSeconds) {
        if (durationSeconds == null || durationSeconds < 0) {
            return 0;
        }
        return Math.min(durationSeconds, MAX_DURATION_SECONDS);
    }

    /**
     * 连续学习天数（7.5）：
     *  - last_study_date == today：不变
     *  - == today-1：streak_days + 1
     *  - 其他：streak_days = 1
     */
    public void updateStreak(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        LocalDate today = LocalDate.now();
        if (today.equals(user.getLastStudyDate())) {
            return;
        }
        if (user.getLastStudyDate() != null && today.minusDays(1).equals(user.getLastStudyDate())) {
            user.setStreakDays(user.getStreakDays() + 1);
        } else {
            user.setStreakDays(1);
        }
        user.setLastStudyDate(today);
        userMapper.updateById(user);
    }
}
