package com.zhikao.server.service;

import com.zhikao.server.entity.UserIdiom;
import com.zhikao.server.entity.UserKnowledge;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * T7.1 验收测试：掌握度状态机 + 固定间隔调度（7.1/7.2 规则）。
 * 覆盖：答对 stage+1、stage>=4 置掌握、stage>=5 置熟练且不再调度、答错回退两档、超期判定。
 */
class ReviewScheduleServiceTest {

    private final ReviewScheduleService service = new ReviewScheduleService();

    @Test
    void correctIncrementsStage() {
        assertEquals(1, service.onCorrect(0));
        assertEquals(2, service.onCorrect(1));
        assertEquals(3, service.onCorrect(2));
        assertEquals(4, service.onCorrect(3));
        assertEquals(5, service.onCorrect(4));
    }

    @Test
    void stageCappedAtFive() {
        assertEquals(5, service.onCorrect(5)); // 熟练后不再前进
    }

    @Test
    void wrongRollsBackTwoStages() {
        assertEquals(0, service.onWrong(2));  // 2-2=0
        assertEquals(1, service.onWrong(3));  // 3-2=1
        assertEquals(0, service.onWrong(1));  // max(1-2,0)=0
        assertEquals(0, service.onWrong(0));  // 不为负
    }

    @Test
    void intervalsFollowSpec() {
        long day = 24 * 60;
        assertEquals(10, ReviewScheduleService.REVIEW_INTERVALS_MINUTES[0]);
        assertEquals(day, ReviewScheduleService.REVIEW_INTERVALS_MINUTES[1]);
        assertEquals(3 * day, ReviewScheduleService.REVIEW_INTERVALS_MINUTES[2]);
        assertEquals(7 * day, ReviewScheduleService.REVIEW_INTERVALS_MINUTES[3]);
        assertEquals(15 * day, ReviewScheduleService.REVIEW_INTERVALS_MINUTES[4]);
        assertEquals(30 * day, ReviewScheduleService.REVIEW_INTERVALS_MINUTES[5]);
    }

    @Test
    void masteryDerivation() {
        // stage>=4 → 掌握(3)
        assertEquals(3, service.deriveMastery(2, 4));
        // stage>=5 → 熟练(4)
        assertEquals(4, service.deriveMastery(3, 5));
        // 低 stage 保持已学习(2)
        assertEquals(2, service.deriveMastery(2, 2));
        // mastery 只升不降：已熟练(4) 答错后 stage 回退，mastery 仍 4
        assertEquals(4, service.deriveMastery(4, 2));
    }

    @Test
    void knowledgeCorrectFlow() {
        UserKnowledge uk = new UserKnowledge();
        uk.setReviewStage(3);
        uk.setMastery(2);
        uk.setCorrectCount(0);
        uk.setWrongCount(0);

        int newStage = service.applyKnowledgeCorrect(uk);
        assertEquals(4, newStage);
        assertEquals(3, uk.getMastery()); // 15 天档 → 掌握
        assertEquals(1, uk.getCorrectCount());
        assertNotNull(uk.getNextReviewTime());
    }

    @Test
    void knowledgeWrongFlow() {
        UserKnowledge uk = new UserKnowledge();
        uk.setReviewStage(3);
        uk.setMastery(3);
        uk.setCorrectCount(0);
        uk.setWrongCount(0);

        int newStage = service.applyKnowledgeWrong(uk);
        assertEquals(1, newStage); // 3-2=1
        assertEquals(3, uk.getMastery()); // 答错不降级
        assertEquals(1, uk.getWrongCount());
        // 新间隔 = 1 天
        assertNotNull(uk.getNextReviewTime());
    }

    @Test
    void idiomCorrectToMastered() {
        UserIdiom ui = new UserIdiom();
        ui.setReviewStage(4);
        ui.setMastery(3);
        ui.setCorrectCount(0);
        ui.setWrongCount(0);

        int newStage = service.applyIdiomCorrect(ui);
        assertEquals(5, newStage);
        assertEquals(4, ui.getMastery()); // 30 天档通过 → 熟练
    }

    @Test
    void stageZeroWrongStaysZero() {
        UserKnowledge uk = new UserKnowledge();
        uk.setReviewStage(0);
        uk.setMastery(2);
        uk.setCorrectCount(0);
        uk.setWrongCount(0);

        int newStage = service.applyKnowledgeWrong(uk);
        assertEquals(0, newStage);
        // 10 分钟后再次复习
        assertNotNull(uk.getNextReviewTime());
    }
}
