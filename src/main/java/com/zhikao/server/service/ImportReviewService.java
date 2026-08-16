package com.zhikao.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhikao.server.common.BizException;
import com.zhikao.server.common.ErrorCode;
import com.zhikao.server.document.model.ParsedDocument;
import com.zhikao.server.entity.ImportDocument;
import com.zhikao.server.entity.ImportRecord;
import com.zhikao.server.mapper.ImportDocumentMapper;
import com.zhikao.server.mapper.ImportRecordMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * 审核入库服务（T3.9，契约 6.12 /admin/import/records）。
 *
 * <p>状态机（任务书 10.3）：
 *  - 0 PENDING_REVIEW → 1 APPROVED（approve：写正式表，回写 target_id 与来源字段，终态）
 *  - 0 PENDING_REVIEW → 2 REJECTED（reject：必填 errorMessage）
 *  - 2 REJECTED → 0 PENDING_REVIEW（编辑后 submit 重新提交）
 *  - 0/2 → 3 INVALIDATED（文档重新解析时旧草稿批量失效）
 *
 * <p>审核红线（10.3）：机器解析产物只能成为草稿，approve 经人工审核后写正式表。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImportReviewService {

    private final ImportRecordMapper importRecordMapper;
    private final ImportDocumentMapper importDocumentMapper;
    private final KnowledgeAdminService knowledgeAdminService;
    private final IdiomAdminService idiomAdminService;
    private final QuestionAdminService questionAdminService;
    private final ObjectMapper objectMapper;

    /** 编辑草稿内容（仅待审核/已驳回可编辑，否则 4004；编辑只改内容不改状态） */
    public void edit(Long recordId, String parsedContent, String sourceTitle) {
        ImportRecord record = getRecord(recordId);
        if (record.getStatus() != 0 && record.getStatus() != 2) {
            throw new BizException(ErrorCode.IMPORT_STATE_INVALID, "仅待审核/已驳回草稿可编辑");
        }
        if (StringUtils.hasText(parsedContent)) {
            record.setParsedContent(parsedContent);
        }
        if (StringUtils.hasText(sourceTitle)) {
            record.setSourceTitle(sourceTitle);
        }
        importRecordMapper.updateById(record);
    }

    /** 驳回草稿重新提交审核（2 REJECTED → 0 PENDING_REVIEW；仅已驳回可调用，否则 4004） */
    public void submit(Long recordId) {
        ImportRecord record = getRecord(recordId);
        if (record.getStatus() != 2) {
            throw new BizException(ErrorCode.IMPORT_STATE_INVALID, "仅已驳回草稿可重新提交");
        }
        record.setStatus(0);
        record.setErrorMessage(null);
        importRecordMapper.updateById(record);
    }

    /**
     * 审核通过 → 写入正式表（knowledge/idiom/question），回写 target_id 与来源字段。
     * 来源回写（任务书 10.5）：source_type=4 / source_document_id / source_title。
     */
    @Transactional
    public void approve(Long recordId, Long reviewerId) {
        ImportRecord record = getRecord(recordId);
        if (record.getStatus() != 0) {
            throw new BizException(ErrorCode.IMPORT_STATE_INVALID, "仅待审核草稿可通过");
        }
        ImportDocument document = importDocumentMapper.selectById(record.getDocumentId());
        if (document == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "所属导入任务不存在");
        }

        Long targetId = writeToFormalTable(record, document);
        record.setTargetId(targetId);
        record.setStatus(1);
        record.setReviewedBy(reviewerId);
        importRecordMapper.updateById(record);

        // 更新 import_document 统计
        updateDocumentStats(document.getId());
    }

    /** 驳回：必填 error_message */
    public void reject(Long recordId, String errorMessage, Long reviewerId) {
        if (!StringUtils.hasText(errorMessage)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "驳回必须填写原因");
        }
        ImportRecord record = getRecord(recordId);
        if (record.getStatus() != 0) {
            throw new BizException(ErrorCode.IMPORT_STATE_INVALID, "仅待审核草稿可驳回");
        }
        record.setStatus(2);
        record.setErrorMessage(errorMessage);
        record.setReviewedBy(reviewerId);
        importRecordMapper.updateById(record);

        updateDocumentStats(record.getDocumentId());
    }

    /** 按内容类型写入对应正式表并返回新记录 id */
    private Long writeToFormalTable(ImportRecord record, ImportDocument document) {
        Map<String, Object> parsed = parseContent(record.getParsedContent());
        return switch (record.getContentType()) {
            case 1 -> knowledgeAdminService.createFromDraft(parsed, document);
            case 2 -> idiomAdminService.createFromDraft(parsed, document);
            case 3 -> questionAdminService.createFromDraft(parsed, document);
            default -> throw new BizException(ErrorCode.PARAM_INVALID, "未知内容类型: " + record.getContentType());
        };
    }

    /** 文档全部草稿处理完后置为已完成（4），见 10.3 完成定义 */
    private void updateDocumentStats(Long documentId) {
        ImportDocument document = importDocumentMapper.selectById(documentId);
        if (document == null) {
            return;
        }
        Long pending = importRecordMapper.selectCount(new LambdaQueryWrapper<ImportRecord>()
                .eq(ImportRecord::getDocumentId, documentId)
                .eq(ImportRecord::getStatus, 0));
        Long approved = importRecordMapper.selectCount(new LambdaQueryWrapper<ImportRecord>()
                .eq(ImportRecord::getDocumentId, documentId)
                .eq(ImportRecord::getStatus, 1));
        Long rejected = importRecordMapper.selectCount(new LambdaQueryWrapper<ImportRecord>()
                .eq(ImportRecord::getDocumentId, documentId)
                .eq(ImportRecord::getStatus, 2));

        document.setSuccessCount(approved.intValue());
        document.setFailedCount(rejected.intValue());
        // 完成定义（10.3）：无待审核草稿，且（有通过条目 或 全部驳回）
        if (pending == 0 && (approved > 0 || rejected > 0)) {
            document.setStatus(4);
        } else if (pending > 0) {
            document.setStatus(3);
        }
        importDocumentMapper.updateById(document);
    }

    private ImportRecord getRecord(Long recordId) {
        ImportRecord record = importRecordMapper.selectById(recordId);
        if (record == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "草稿不存在");
        }
        return record;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseContent(String parsedContent) {
        try {
            if (!StringUtils.hasText(parsedContent)) {
                return new java.util.LinkedHashMap<>();
            }
            return objectMapper.readValue(parsedContent, Map.class);
        } catch (Exception e) {
            throw new BizException(ErrorCode.PARAM_INVALID, "草稿内容 JSON 解析失败");
        }
    }
}
