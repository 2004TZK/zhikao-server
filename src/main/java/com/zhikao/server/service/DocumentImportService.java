package com.zhikao.server.service;

import com.zhikao.server.common.BizException;
import com.zhikao.server.common.ErrorCode;
import com.zhikao.server.entity.ImportDocument;
import com.zhikao.server.mapper.ImportDocumentMapper;
import com.zhikao.server.storage.FileStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 知识库导入服务（T3.6：上传与存储；T3.7 起补解析编排）。
 *
 * <p>上传约定（任务书 6.1）：
 *  - 类型白名单 .pdf/.docx/.doc/.txt（非法返回 4001）
 *  - 单文件 ≤ 20MB（超限返回 4002）
 *  - 单批次 ≤ 10 个文件
 *  - 上传只落 import_document（status=0）与文件存储，不写任何正式内容表
 */
@Service
@RequiredArgsConstructor
public class DocumentImportService {

    private static final long MAX_FILE_SIZE = 20L * 1024 * 1024; // 20MB
    private static final int MAX_BATCH = 10;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "docx", "doc", "txt");

    private final ImportDocumentMapper importDocumentMapper;
    private final FileStorage fileStorage;

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
}
