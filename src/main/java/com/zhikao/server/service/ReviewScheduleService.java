package com.zhikao.server.service;

import com.zhikao.server.entity.UserIdiom;
import com.zhikao.server.entity.UserKnowledge;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 掌握度与复习调度核心（任务书 7.1/7.2，T7.1）。
 *
 * <p>固定间隔状态机（非 SM-2）：
 *  - 间隔梯度：stage 0~5 → 10分钟/1天/3天/7天/15天/30天
 *  - 复习答对：stage+1（上限 5）；stage>=4 置掌握(3)；stage>=5 置熟练(4) 不再调度
 *  - 复习答错：stage=max(stage-2, 0)，重入短周期
 *  - mastery 只升不降（v1.4：答错仅回退 stage，不降级 mastery）
 */
@Slf4j
@Service
public class ReviewScheduleService {

    /** 固定间隔梯度（分钟），任务书 7.2 唯一事实来源 */
    public static final long[] REVIEW_INTERVALS_MINUTES = {
            10,            // stage 0
            24 * 60,       // stage 1: 1 天
            3 * 24 * 60,   // stage 2: 3 天
            7 * 24 * 60,   // stage 3: 7 天
            15 * 24 * 60,  // stage 4: 15 天
            30 * 24 * 60   // stage 5: 30 天
    };

    /** 熟练档位（不再主动调度） */
    public static final int MASTERED_STAGE = 5;

    /**
     * 复习答对：更新 review_stage 与 next_review_time。
     * 返回更新后的 stage。
     */
    public int onCorrect(int currentStage) {
        int newStage = Math.min(currentStage + 1, MASTERED_STAGE);
        return newStage;
    }

    /**
     * 复习答错：stage = max(stage-2, 0)（容错回退，不直接归零）。
     */
    public int onWrong(int currentStage) {
        return Math.max(currentStage - 2, 0);
    }

    /**
     * 计算下次复习时间（按新 stage 的间隔）。
     */
    public LocalDateTime nextReviewTime(int newStage) {
        long minutes = REVIEW_INTERVALS_MINUTES[Math.min(Math.max(newStage, 0), MASTERED_STAGE)];
        return LocalDateTime.now().plusMinutes(minutes);
    }

    /**
     * 根据 stage 推导掌握度（5.5 枚举：2 已学习 → 3 掌握 → 4 熟练）。
     */
    public int deriveMastery(int currentMastery, int newStage) {
        // mastery 只升不降（v1.4）
        if (newStage >= MASTERED_STAGE) {
            return Math.max(currentMastery, 4); // 熟练
        }
        if (newStage >= 4) {
            return Math.max(currentMastery, 3); // 掌握
        }
        return Math.max(currentMastery, 2); // 已学习（至少进入调度）
    }

    /** 知识点复习答对：更新记录并返回新 stage */
    public int applyKnowledgeCorrect(UserKnowledge uk) {
        int newStage = onCorrect(uk.getReviewStage());
        uk.setReviewStage(newStage);
        uk.setCorrectCount(uk.getCorrectCount() + 1);
        uk.setNextReviewTime(nextReviewTime(newStage));
        uk.setMastery(deriveMastery(uk.getMastery(), newStage));
        return newStage;
    }

    /** 知识点复习答错：更新记录并返回新 stage */
    public int applyKnowledgeWrong(UserKnowledge uk) {
        int newStage = onWrong(uk.getReviewStage());
        uk.setReviewStage(newStage);
        uk.setWrongCount(uk.getWrongCount() + 1);
        uk.setNextReviewTime(nextReviewTime(newStage));
        // mastery 不降级，但若从未达标维持已学习
        uk.setMastery(Math.max(uk.getMastery(), 2));
        return newStage;
    }

    /** 成语复习答对 */
    public int applyIdiomCorrect(UserIdiom ui) {
        int newStage = onCorrect(ui.getReviewStage());
        ui.setReviewStage(newStage);
        ui.setCorrectCount(ui.getCorrectCount() + 1);
        ui.setNextReviewTime(nextReviewTime(newStage));
        ui.setMastery(deriveMastery(ui.getMastery(), newStage));
        return newStage;
    }

    /** 成语复习答错 */
    public int applyIdiomWrong(UserIdiom ui) {
        int newStage = onWrong(ui.getReviewStage());
        ui.setReviewStage(newStage);
        ui.setWrongCount(ui.getWrongCount() + 1);
        ui.setNextReviewTime(nextReviewTime(newStage));
        ui.setMastery(Math.max(ui.getMastery(), 2));
        return newStage;
    }
}
