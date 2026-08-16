package com.zhikao.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库导入：文件任务实体（表：import_document）。
 */
@Data
@TableName("import_document")
public class ImportDocument {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String fileName;

    /** 存储路径（FileStorage 相对 key） */
    private String filePath;

    /** 文件类型：TEXT_PDF/SCANNED_PDF/DOCX/DOC/TXT */
    private String fileType;

    private Long fileSize;

    /** 导入类型：1 常识知识库 2 成语知识库 3 题库 4 自动识别 */
    private Integer importType;

    /** 状态：0 上传成功 1 解析中 2 解析完成 3 审核中 4 已完成 5 解析失败 */
    private Integer status;

    private Integer totalSections;

    private Integer successCount;

    private Integer failedCount;

    /** 解析失败原因（status=5 时记录；任务书 10.3 状态机要求，5.2 表结构补充） */
    private String errorMessage;

    private Long createdBy;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
