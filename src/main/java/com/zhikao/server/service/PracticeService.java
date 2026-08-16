package com.zhikao.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhikao.server.common.BizException;
import com.zhikao.server.common.ErrorCode;
import com.zhikao.server.entity.PracticeSession;
import com.zhikao.server.entity.Question;
import com.zhikao.server.entity.QuestionOption;
import com.zhikao.server.entity.UserAnswer;
import com.zhikao.server.entity.UserIdiom;
import com.zhikao.server.entity.UserKnowledge;
import com.zhikao.server.entity.WrongQuestion;
import com.zhikao.server.mapper.PracticeSessionMapper;
import com.zhikao.server.mapper.QuestionMapper;
import com.zhikao.server.mapper.QuestionOptionMapper;
import com.zhikao.server.mapper.UserAnswerMapper;
import com.zhikao.server.mapper.UserIdiomMapper;
import com.zhikao.server.mapper.UserKnowledgeMapper;
import com.zhikao.server.mapper.WrongQuestionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 练习服务（契约 6.6，T6.1）。
 *  - 组卷：每日挑战（混合）/专项（ref_type+ref_id）/随机
 *  - 答案服务端判定（答案不下发客户端）
 *  - 答错自动写入 wrong_question（幂等：唯一索引累加）
 *  - 联动 user_knowledge / user_idiom 掌握度计数（correct_count/wrong_count）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PracticeService {

    private final PracticeSessionMapper sessionMapper;
    private final QuestionMapper questionMapper;
    private final QuestionOptionMapper optionMapper;
    private final UserAnswerMapper userAnswerMapper;
    private final WrongQuestionMapper wrongQuestionMapper;
    private final UserKnowledgeMapper userKnowledgeMapper;
    private final UserIdiomMapper userIdiomMapper;

    /** 创建场次并组卷（契约 6.6 POST /practice/sessions） */
    @Transactional
    public Map<String, Object> createSession(Long userId, Integer type, Integer refType, Long refId, Integer count) {
        int size = count == null ? 10 : Math.min(Math.max(count, 1), 50);

        List<Question> questions = buildPaper(type, refType, refId, size);
        if (questions.isEmpty()) {
            throw new BizException(ErrorCode.NOT_FOUND, "暂无符合条件的题目");
        }

        PracticeSession session = new PracticeSession();
        session.setUserId(userId);
        session.setType(type);
        session.setRefType(refType == null ? 6 : refType);
        session.setRefId(refId);
        session.setTotalCount(questions.size());
        session.setCorrectCount(0);
        session.setStartTime(LocalDateTime.now());
        sessionMapper.insert(session);

        // 组装响应（不含答案，契约 6.13 示例 2）
        List<Map<String, Object>> questionList = new ArrayList<>();
        for (Question q : questions) {
            Map<String, Object> qm = new HashMap<>();
            qm.put("questionId", q.getId());
            qm.put("content", q.getContent());
            qm.put("knowledgeId", q.getKnowledgeId());
            qm.put("idiomId", q.getIdiomId());
            List<QuestionOption> options = optionMapper.selectList(
                    new LambdaQueryWrapper<QuestionOption>()
                            .eq(QuestionOption::getQuestionId, q.getId())
                            .orderByAsc(QuestionOption::getLabel));
            List<Map<String, Object>> optionList = new ArrayList<>();
            for (QuestionOption o : options) {
                Map<String, Object> om = new HashMap<>();
                om.put("optionId", o.getId());
                om.put("label", o.getLabel());
                om.put("content", o.getContent());
                optionList.add(om);
            }
            qm.put("options", optionList);
            questionList.add(qm);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("sessionId", session.getId());
        result.put("totalCount", questions.size());
        result.put("questions", questionList);
        return result;
    }

    /** 组卷逻辑（三种模式） */
    private List<Question> buildPaper(Integer type, Integer refType, Long refId, int size) {
        if (type != null && type == 2 && refType != null) {
            // 专项训练：按 ref_type/ref_id
            return questionMapper.selectList(buildSpecialWrapper(refType, refId)
                    .eq(Question::getStatus, 1)
                    .last("LIMIT " + size));
        }
        // 每日挑战 / 随机：随机抽取
        return questionMapper.selectList(new LambdaQueryWrapper<Question>()
                .eq(Question::getStatus, 1)
                .orderByAsc(Question::getId)
                .last("LIMIT " + size));
    }

    private LambdaQueryWrapper<Question> buildSpecialWrapper(Integer refType, Long refId) {
        return switch (refType) {
            case 3 -> new LambdaQueryWrapper<Question>().eq(Question::getKnowledgeId, refId);
            case 4 -> new LambdaQueryWrapper<Question>().eq(Question::getIdiomId, refId);
            default -> new LambdaQueryWrapper<Question>();
        };
    }

    /**
     * 提交单题答案（契约 6.6 POST /practice/sessions/{id}/answers）。
     * 幂等：同一场次同一题重复提交不重复计数（6.1）。
     */
    @Transactional
    public Map<String, Object> submitAnswer(Long userId, Long sessionId, Long questionId, Long optionId) {
        PracticeSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "场次不存在");
        }

        // 幂等检查：已答过该题直接返回上次结果
        UserAnswer existing = userAnswerMapper.selectOne(new LambdaQueryWrapper<UserAnswer>()
                .eq(UserAnswer::getSessionId, sessionId)
                .eq(UserAnswer::getQuestionId, questionId)
                .last("LIMIT 1"));

        Question question = questionMapper.selectById(questionId);
        if (question == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "题目不存在");
        }
        // 正确答案判定（服务端）
        QuestionOption correctOption = optionMapper.selectOne(new LambdaQueryWrapper<QuestionOption>()
                .eq(QuestionOption::getQuestionId, questionId)
                .eq(QuestionOption::getIsCorrect, 1)
                .last("LIMIT 1"));
        boolean correct = correctOption != null && correctOption.getId().equals(optionId);

        if (existing != null) {
            // 幂等：返回已记录结果
            return buildAnswerResult(existing, question, correctOption);
        }

        UserAnswer answer = new UserAnswer();
        answer.setUserId(userId);
        answer.setSessionId(sessionId);
        answer.setQuestionId(questionId);
        answer.setSelectedOptionId(optionId);
        answer.setIsCorrect(correct ? 1 : 0);
        answer.setAnswerTime(LocalDateTime.now());
        userAnswerMapper.insert(answer);

        // 场次正确数更新
        if (correct) {
            session.setCorrectCount(session.getCorrectCount() + 1);
            sessionMapper.updateById(session);
        } else {
            // 答错自动入错题本（幂等累加）
            recordWrongQuestion(userId, questionId);
            // 掌握度联动：答错计数
            updateMasteryCounts(userId, question, false);
        }
        if (correct) {
            updateMasteryCounts(userId, question, true);
        }

        return buildAnswerResult(answer, question, correctOption);
    }

    /** 答错入错题本（唯一索引幂等，累加 wrong_count） */
    private void recordWrongQuestion(Long userId, Long questionId) {
        WrongQuestion wrong = wrongQuestionMapper.selectOne(new LambdaQueryWrapper<WrongQuestion>()
                .eq(WrongQuestion::getUserId, userId)
                .eq(WrongQuestion::getQuestionId, questionId)
                .last("LIMIT 1"));
        if (wrong == null) {
            wrong = new WrongQuestion();
            wrong.setUserId(userId);
            wrong.setQuestionId(questionId);
            wrong.setWrongCount(1);
            wrong.setLastWrongTime(LocalDateTime.now());
            wrong.setIsRemoved(0);
            wrongQuestionMapper.insert(wrong);
        } else {
            wrong.setWrongCount(wrong.getWrongCount() + 1);
            wrong.setLastWrongTime(LocalDateTime.now());
            wrong.setIsRemoved(0); // 移出后再次答错重新进入
            wrongQuestionMapper.updateById(wrong);
        }
    }

    /** 掌握度联动：更新 user_knowledge / user_idiom 的答对/答错计数（7.1 状态机计数部分） */
    private void updateMasteryCounts(Long userId, Question question, boolean correct) {
        if (question.getKnowledgeId() != null) {
            UserKnowledge uk = userKnowledgeMapper.selectOne(new LambdaQueryWrapper<UserKnowledge>()
                    .eq(UserKnowledge::getUserId, userId)
                    .eq(UserKnowledge::getKnowledgeId, question.getKnowledgeId()));
            if (uk != null) {
                if (correct) {
                    uk.setCorrectCount(uk.getCorrectCount() + 1);
                } else {
                    uk.setWrongCount(uk.getWrongCount() + 1);
                }
                userKnowledgeMapper.updateById(uk);
            }
        }
        if (question.getIdiomId() != null) {
            UserIdiom ui = userIdiomMapper.selectOne(new LambdaQueryWrapper<UserIdiom>()
                    .eq(UserIdiom::getUserId, userId)
                    .eq(UserIdiom::getIdiomId, question.getIdiomId()));
            if (ui != null) {
                if (correct) {
                    ui.setCorrectCount(ui.getCorrectCount() + 1);
                } else {
                    ui.setWrongCount(ui.getWrongCount() + 1);
                }
                userIdiomMapper.updateById(ui);
            }
        }
    }

    /** 组装判定响应（契约 6.13 示例 3：不含答案选项 id，返回正确答案 label + 解析） */
    private Map<String, Object> buildAnswerResult(UserAnswer answer, Question question, QuestionOption correctOption) {
        Map<String, Object> result = new HashMap<>();
        result.put("correct", answer.getIsCorrect() == 1);
        result.put("correctOptionLabel", correctOption == null ? null : correctOption.getLabel());
        result.put("analysis", question.getAnalysis());
        result.put("knowledgeId", question.getKnowledgeId());
        result.put("idiomId", question.getIdiomId());
        return result;
    }

    /** 场次结果（契约 6.6 GET /practice/sessions/{id}/result） */
    public Map<String, Object> sessionResult(Long userId, Long sessionId) {
        PracticeSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "场次不存在");
        }
        List<UserAnswer> answers = userAnswerMapper.selectList(new LambdaQueryWrapper<UserAnswer>()
                .eq(UserAnswer::getSessionId, sessionId));
        long wrongCount = answers.stream().filter(a -> a.getIsCorrect() == 0).count();

        Map<String, Object> result = new HashMap<>();
        result.put("sessionId", sessionId);
        result.put("totalCount", session.getTotalCount());
        result.put("correctCount", session.getCorrectCount());
        result.put("wrongCount", wrongCount);
        result.put("accuracy", session.getTotalCount() > 0
                ? Math.round(session.getCorrectCount() * 100.0 / session.getTotalCount()) : 0);
        // 错题列表（题目 id + 题干）
        List<Map<String, Object>> wrongList = new ArrayList<>();
        for (UserAnswer a : answers) {
            if (a.getIsCorrect() == 0) {
                Question q = questionMapper.selectById(a.getQuestionId());
                if (q != null) {
                    Map<String, Object> wm = new HashMap<>();
                    wm.put("questionId", q.getId());
                    wm.put("content", q.getContent());
                    wm.put("knowledgeId", q.getKnowledgeId());
                    wm.put("idiomId", q.getIdiomId());
                    wrongList.add(wm);
                }
            }
        }
        result.put("wrongQuestions", wrongList);
        return result;
    }

    /** 错题重练组卷（契约 6.7 POST /wrong/practice） */
    public Map<String, Object> createWrongPractice(Long userId, int count) {
        List<WrongQuestion> wrongs = wrongQuestionMapper.selectList(new LambdaQueryWrapper<WrongQuestion>()
                .eq(WrongQuestion::getUserId, userId)
                .eq(WrongQuestion::getIsRemoved, 0)
                .last("LIMIT " + count));
        if (wrongs.isEmpty()) {
            throw new BizException(ErrorCode.NOT_FOUND, "错题本为空");
        }
        List<Long> questionIds = wrongs.stream().map(WrongQuestion::getQuestionId).collect(Collectors.toList());
        List<Question> questions = questionMapper.selectList(new LambdaQueryWrapper<Question>()
                .in(Question::getId, questionIds)
                .eq(Question::getStatus, 1));

        PracticeSession session = new PracticeSession();
        session.setUserId(userId);
        session.setType(5); // 错题重练
        session.setRefType(5);
        session.setRefId(null);
        session.setTotalCount(questions.size());
        session.setCorrectCount(0);
        session.setStartTime(LocalDateTime.now());
        sessionMapper.insert(session);

        List<Map<String, Object>> questionList = new ArrayList<>();
        for (Question q : questions) {
            Map<String, Object> qm = new HashMap<>();
            qm.put("questionId", q.getId());
            qm.put("content", q.getContent());
            qm.put("knowledgeId", q.getKnowledgeId());
            qm.put("idiomId", q.getIdiomId());
            List<QuestionOption> options = optionMapper.selectList(
                    new LambdaQueryWrapper<QuestionOption>()
                            .eq(QuestionOption::getQuestionId, q.getId())
                            .orderByAsc(QuestionOption::getLabel));
            List<Map<String, Object>> optionList = new ArrayList<>();
            for (QuestionOption o : options) {
                Map<String, Object> om = new HashMap<>();
                om.put("optionId", o.getId());
                om.put("label", o.getLabel());
                om.put("content", o.getContent());
                optionList.add(om);
            }
            qm.put("options", optionList);
            questionList.add(qm);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("sessionId", session.getId());
        result.put("totalCount", questions.size());
        result.put("questions", questionList);
        return result;
    }
}
