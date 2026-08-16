package com.zhikao.server.document.parser;

import com.zhikao.server.document.model.ParsedDocument;

/**
 * 文档解析器接口（T3.7）。
 * 受控解析唯一入口（任务书 14.4 规则 8）：所有上传文件只能经此接口解析，
 * 禁止动态加载/执行文件内容。
 */
public interface DocumentParser {

    /**
     * 解析文档为文本与结构块。
     *
     * @param filePath 文件存储路径（FileStorage key）
     * @return 解析结果（含 scannedPdf 标记）
     * @throws IllegalStateException 解析失败（状态机流转为 5 FAILED）
     */
    ParsedDocument parse(String filePath);
}
