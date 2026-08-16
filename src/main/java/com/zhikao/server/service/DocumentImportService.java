package com.zhikao.server.service;

import com.zhikao.server.common.BizException;
import com.zhikao.server.common.ErrorCode;
import com.zhikao.server.document.model.ParsedDocument;
import com.zhikao.server.document.parser.DocumentParserRouter;
import com.zhikao.server.document.service.ContentExtractService;
import com.zhikao.server.entity.ImportDocument;
import com.zhikao.server.mapper.ImportDocumentMapper;
import com.zhikao.server.storage.FileStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 知识库导入服务（T3.6 上传存储；T3.7 解析编排）。
 *
 * <p>上传约定（任务书 6.1）：白名单类型（4001）、≤20MB（4002）、≤10 个/批、
 * 只落 import_document（status=0）与文件存储，不写正式内容表。
 *
 * <p>解析状态机（任务书 10.3）：
 *  - 0 上传成功 → 1 解析中 → 2 解析完成
 *  - 解析失败 → 5 FAILED（记录 error_message，可重新解析）
 *  - 扫描版 PDF 无文本层 → 识别为 SCANNED_PDF，返回 4003（V1.0 不支持 OCR）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentImportService {

    private static final long MAX_FILE_SIZE = 20L * 1024 * 1024; // 20MB
    private static final int MAX_BATCH = 10;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "docx", "doc", "txt");

    private final ImportDocumentMapper importDocumentMapper;
    private final FileStorage fileStorage;
    private final DocumentParserRouter parserRouter;
    private final ContentExtractService contentExtractService;

    @Value("${zhikao.storage.upload-dir:uploads}")
    private String uploadDir;

    /**
     * 批量上传文件（≤10 个/批）。
     * 逐条处理：单条失败不中断整体（任务书 10.7 部分成功机制）。
     */
    public List<UploadResult> upload(List<MultipartFile> files, Integer importType, Long adminId) {
        if (files == null || files.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "未选择文件");
        }
        if (files.size() > MAX_BATCH) {
            throw new BizException(ErrorCode.PARAM_INVALID, "单批次最多 " + MAX_BATCH + " 个文件");
        }
        if (importType == null || importType < 1 || importType > 4) {
            throw new BizException(ErrorCode.PARAM_INVALID, "importType 需为 1~4");
        }

        List<UploadResult> results = new ArrayList<>();
        for (MultipartFile file : files) {
            UploadResult result = new UploadResult();
            result.setFileName(file.getOriginalFilename());
            try {
                validateFile(file);
                // 先生成存储 key 并保存文件，再落库（file_path 为 NOT NULL，见 5.2）
                String ext = extensionOf(file.getOriginalFilename());
                String subDir = switch (importType) {
                    case 1 -> "knowledge";
                    case 2 -> "idiom";
                    case 3 -> "question";
                    default -> "auto";
                };
                String key = subDir + "/" + System.currentTimeMillis() + "_" + file.getOriginalFilename();
                fileStorage.save(key, file.getInputStream(), file.getSize());

                ImportDocument doc = new ImportDocument();
                doc.setFileName(file.getOriginalFilename());
                doc.setFilePath(key);
                doc.setFileSize(file.getSize());
                doc.setFileType(resolveFileType(ext));
                doc.setImportType(importType);
                doc.setStatus(0);
                doc.setTotalSections(0);
                doc.setSuccessCount(0);
                doc.setFailedCount(0);
                doc.setCreatedBy(adminId);
                importDocumentMapper.insert(doc);

                result.setSuccess(true);
                result.setDocumentId(doc.getId());
                result.setFilePath(key);
            } catch (BizException e) {
                result.setSuccess(false);
                result.setErrorCode(e.getCode());
                result.setErrorMessage(e.getMessage());
            } catch (Exception e) {
                result.setSuccess(false);
                result.setErrorCode(ErrorCode.INTERNAL_ERROR.getCode());
                result.setErrorMessage("文件保存失败: " + e.getMessage());
            }
            results.add(result);
        }
        return results;
    }

    /** 校验：类型白名单（4001）+ 大小 ≤20MB（4002）+ 文件名安全 */
    private void validateFile(MultipartFile file) {
        String name = file.getOriginalFilename();
        if (!StringUtils.hasText(name) || name.contains("..") || name.contains("/") || name.contains("\\")) {
            throw new BizException(ErrorCode.FILE_TYPE_UNSUPPORTED, "非法文件名");
        }
        String ext = extensionOf(name);
        if (ext == null || !ALLOWED_EXTENSIONS.contains(ext)) {
            throw new BizException(ErrorCode.FILE_TYPE_UNSUPPORTED,
                    "文件类型不支持（仅限 .pdf/.docx/.doc/.txt）");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BizException(ErrorCode.FILE_TOO_LARGE, "文件大小超限（单文件 ≤ 20MB）");
        }
        if (file.getSize() <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "文件为空");
        }
    }

    private String extensionOf(String fileName) {
        if (fileName == null) {
            return null;
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return null;
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** 扩展名 → file_type 枚举（任务书 10.2；SCANNED_PDF 由 T3.7 解析时识别修正） */
    private String resolveFileType(String ext) {
        return switch (ext) {
            case "pdf" -> "TEXT_PDF";
            case "docx" -> "DOCX";
            case "doc" -> "DOC";
            case "txt" -> "TXT";
            default -> "TXT";
        };
    }

    /** 上传结果（部分成功机制：逐条返回） */
    public static class UploadResult {
        private String fileName;
        private boolean success;
        private Long documentId;
        private String filePath;
        private Integer errorCode;
        private String errorMessage;

        public String getFileName() {
            return fileName;
        }

        public void setFileName(String fileName) {
            this.fileName = fileName;
        }

        public boolean isSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public Long getDocumentId() {
            return documentId;
        }

        public void setDocumentId(Long documentId) {
            this.documentId = documentId;
        }

        public String getFilePath() {
            return filePath;
        }

        public void setFilePath(String filePath) {
            this.filePath = filePath;
        }

        public Integer getErrorCode() {
            return errorCode;
        }

        public void setErrorCode(Integer errorCode) {
            this.errorCode = errorCode;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public void setErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
        }
    }

    /**
     * 开始/重新解析（T3.7）。
     * 允许源状态：0/2/3/5（10.3 状态机；状态 4 已完成不允许重新解析）。
     * 异步执行：0→1→2/5。
     *
     * @throws BizException 4004：状态不允许；4003：扫描版 PDF
     */
    public void parseDocument(Long documentId) {
        ImportDocument doc = importDocumentMapper.selectById(documentId);
        if (doc == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "导入任务不存在");
        }
        int status = doc.getStatus();
        if (status == 1) {
            throw new BizException(ErrorCode.IMPORT_STATE_INVALID, "文档正在解析中");
        }
        if (status == 4) {
            throw new BizException(ErrorCode.IMPORT_STATE_INVALID, "文档已完成，不允许重新解析");
        }
        // 置为解析中（0/2/3/5 → 1）
        doc.setStatus(1);
        importDocumentMapper.updateById(doc);
        // 异步解析（幂等：重复触发同一文档时，状态=1 已在上面拦截）
        doParseAsync(documentId);
    }

    /** 异步执行解析（T3.7 骨架：文本提取；T3.8 接入结构化识别） */
    @Async
    public void doParseAsync(Long documentId) {
        ImportDocument doc = importDocumentMapper.selectById(documentId);
        try {
            ParsedDocument parsed = parserRouter.parse(doc.getFileType(), doc.getFilePath());
            if (parsed.isScannedPdf()) {
                // 扫描版 PDF 无文本层：标记并拒绝（V1.0 不支持 OCR）
                doc.setFileType("SCANNED_PDF");
                doc.setStatus(0);
                importDocumentMapper.updateById(doc);
                log.info("文档 {} 为扫描版 PDF（SCANNED_PDF），V1.0 不支持 OCR", documentId);
                return;
            }
            // 结构化识别：规则切分 → import_record 草稿（10.4；识别失败不中断，原文保留）
            int sectionCount = contentExtractService.extractAndSave(doc, parsed);
            doc.setStatus(2);
            doc.setTotalSections(sectionCount);
            importDocumentMapper.updateById(doc);
            log.info("文档 {} 解析完成（status=2），生成草稿 {} 条", documentId, sectionCount);
        } catch (Exception e) {
            // 解析失败：5 FAILED，记录 error_message（10.3 状态机）
            doc.setStatus(5);
            doc.setErrorMessage(e.getMessage() == null ? "解析失败" : e.getMessage());
            importDocumentMapper.updateById(doc);
            log.error("文档 {} 解析失败（status=5）: {}", documentId, e.getMessage());
        }
    }
}
