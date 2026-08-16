package com.zhikao.server.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 统一错误码（契约来源：任务书 6.1 统一错误码表）。
 */
@Getter
@AllArgsConstructor
public enum ErrorCode {

    /** 成功 */
    SUCCESS(0, "success"),
    /** 参数校验失败 */
    PARAM_INVALID(1001, "参数校验失败"),
    /** 资源不存在 */
    NOT_FOUND(1002, "资源不存在"),
    /** 业务约束冲突（如单选题正确选项不唯一、题目未关联知识点或成语） */
    BIZ_CONFLICT(1003, "业务约束冲突"),
    /** 未登录或 Access Token 过期 */
    UNAUTHORIZED(2001, "未登录或登录已过期"),
    /** Refresh Token 过期，需重新登录 */
    REFRESH_EXPIRED(2002, "登录已过期，请重新登录"),
    /** 账号已被禁用或已注销 */
    ACCOUNT_DISABLED(2003, "账号已被禁用或已注销"),
    /** 无权限（非管理员访问 /admin） */
    FORBIDDEN(3001, "无权限访问"),
    /** 文件类型不支持 */
    FILE_TYPE_UNSUPPORTED(4001, "文件类型不支持"),
    /** 文件大小超限 */
    FILE_TOO_LARGE(4002, "文件大小超限"),
    /** 扫描版 PDF 无文本层，需 OCR */
    SCANNED_PDF(4003, "扫描版 PDF 无文本层，暂不支持解析"),
    /** 导入记录/文档当前状态不允许该操作 */
    IMPORT_STATE_INVALID(4004, "当前状态不允许该操作"),
    /** 游客当日额度用尽 */
    GUEST_QUOTA_EXCEEDED(9001, "游客当日体验额度已用完，请登录后继续"),
    /** 服务器内部错误 */
    INTERNAL_ERROR(5000, "服务器内部错误");

    private final int code;
    private final String message;
}
