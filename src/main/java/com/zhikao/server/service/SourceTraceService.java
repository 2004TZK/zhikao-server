package com.zhikao.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhikao.server.common.BizException;
import com.zhikao.server.common.ErrorCode;
import com.zhikao.server.entity.ImportDocument;
import com.zhikao.server.entity.ImportRecord;
import com.zhikao.server.entity.Knowledge;
import com.zhikao.server.entity.Idiom;
import com.zhikao.server.entity.Question;
import com.zhikao.server.mapper.IdiomMapper;
import com.zhikao.server.mapper.ImportDocumentMapper;
import com.zhikao.server.mapper.ImportRecordMapper;
import com.zhikao.server.mapper.KnowledgeMapper;
import com.zhikao.server.mapper.QuestionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 来源追溯服务（T3.11）。
 * 场景（任务书 10.5）：发现内容有误 → 查看来源 → 定位 import_record 与原文摘录 → 修正后重新发布。
 * 支持：正式内容（knowledge/idiom/question）→ 来源 import_document → import_record 草稿 → rawExcerpt。
 */
@Service
@RequiredArgsConstructor
public class SourceTraceService {

    private final KnowledgeMapper knowledgeMapper;
    private final IdiomMapper idiomMapper;
    private final QuestionMapper questionMapper;
    private final ImportRecordMapper importRecordMapper;
    private final ImportDocumentMapper importDocumentMapper;

    /**
     * 追溯正式内容的来源信息。
     *
     * @param contentType 内容类型：1 知识点 2 成语 3 题目
     * @param targetId    正式内容 id
     */
    public TraceResult trace(Long contentType, Long targetId) {
        Long sourceDocumentId = null;
        String sourceTitle = null;

        // 1. 查正式内容，取来源字段
        int type = contentType.intValue();
        switch (type) {
            case 1 -> {
                Knowledge knowledge = knowledgeMapper.selectById(targetId);
                if (knowledge == null) {
                    throw new BizException(ErrorCode.NOT_FOUND, "知识点不存在");
                }
                sourceDocumentId = knowledge.getSourceDocumentId();
                sourceTitle = knowledge.getSourceTitle();
            }
            case 2 -> {
                Idiom idiom = idiomMapper.selectById(targetId);
                if (idiom == null) {
                    throw new BizException(ErrorCode.NOT_FOUND, "成语不存在");
                }
                sourceDocumentId = idiom.getSourceDocumentId();
                sourceTitle = idiom.getSourceTitle();
            }
            case 3 -> {
                Question question = questionMapper.selectById(targetId);
                if (question == null) {
                    throw new BizException(ErrorCode.NOT_FOUND, "题目不存在");
                }
                sourceDocumentId = question.getSourceDocumentId();
                sourceTitle = question.getSourceName();
            }
            default -> throw new BizException(ErrorCode.PARAM_INVALID, "未知内容类型");
        }

        TraceResult result = new TraceResult();
        result.setContentType(contentType.intValue());
        result.setTargetId(targetId);
        result.setSourceTitle(sourceTitle);

        // 2. 有文档来源 → 反查 import_document 与对应草稿
        if (sourceDocumentId != null) {
            ImportDocument document = importDocumentMapper.selectById(sourceDocumentId);
            if (document != null) {
                result.setSourceDocumentId(document.getId());
                result.setSourceFileName(document.getFileName());
            }
            ImportRecord record = importRecordMapper.selectOne(
                    new LambdaQueryWrapper<ImportRecord>()
                            .eq(ImportRecord::getDocumentId, sourceDocumentId)
                            .eq(ImportRecord::getTargetId, targetId)
                            .last("LIMIT 1"));
            if (record != null) {
                result.setImportRecordId(record.getId());
                result.setRawExcerpt(record.getRawExcerpt());
                result.setRecordStatus(record.getStatus());
            }
        }
        return result;
    }

    /** 追溯结果 */
    public static class TraceResult {
        private int contentType;
        private Long targetId;
        private String sourceTitle;
        private Long sourceDocumentId;
        private String sourceFileName;
        private Long importRecordId;
        private String rawExcerpt;
        private Integer recordStatus;

        public int getContentType() {
            return contentType;
        }

        public void setContentType(int contentType) {
            this.contentType = contentType;
        }

        public Long getTargetId() {
            return targetId;
        }

        public void setTargetId(Long targetId) {
            this.targetId = targetId;
        }

        public String getSourceTitle() {
            return sourceTitle;
        }

        public void setSourceTitle(String sourceTitle) {
            this.sourceTitle = sourceTitle;
        }

        public Long getSourceDocumentId() {
            return sourceDocumentId;
        }

        public void setSourceDocumentId(Long sourceDocumentId) {
            this.sourceDocumentId = sourceDocumentId;
        }

        public String getSourceFileName() {
            return sourceFileName;
        }

        public void setSourceFileName(String sourceFileName) {
            this.sourceFileName = sourceFileName;
        }

        public Long getImportRecordId() {
            return importRecordId;
        }

        public void setImportRecordId(Long importRecordId) {
            this.importRecordId = importRecordId;
        }

        public String getRawExcerpt() {
            return rawExcerpt;
        }

        public void setRawExcerpt(String rawExcerpt) {
            this.rawExcerpt = rawExcerpt;
        }

        public Integer getRecordStatus() {
            return recordStatus;
        }

        public void setRecordStatus(Integer recordStatus) {
            this.recordStatus = recordStatus;
        }
    }
}
