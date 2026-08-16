package com.zhikao.server.document.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 文档解析结果（T3.7）：文本提取 + 结构化块。
 */
@Data
public class ParsedDocument {

    /** 原始文本（全文，供 T3.8 规则识别与原文摘录） */
    private String rawText;

    /** 按标题/结构切分的块 */
    private List<ParsedSection> sections = new ArrayList<>();

    /** 是否扫描版 PDF（无文本层，需 OCR，V1.5） */
    private boolean scannedPdf;
}
