package com.zhikao.server.document.parser;

import com.zhikao.server.document.model.ParsedDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 解析器类型路由（T3.7）：按 file_type / 文件扩展名选择解析器。
 * 受控解析唯一入口（任务书 14.4 规则 8）。
 */
@Component
@RequiredArgsConstructor
public class DocumentParserRouter {

    private final PdfParser pdfParser;
    private final WordParser wordParser;
    private final TxtParser txtParser;

    /**
     * 按 file_type 路由解析。
     *
     * @param fileType import_document.file_type（TEXT_PDF/SCANNED_PDF/DOCX/DOC/TXT）
     */
    public ParsedDocument parse(String fileType, String filePath) {
        return switch (fileType) {
            case "TEXT_PDF", "SCANNED_PDF" -> pdfParser.parse(filePath);
            case "DOCX", "DOC" -> wordParser.parse(filePath);
            case "TXT" -> txtParser.parse(filePath);
            default -> throw new IllegalStateException("不支持的文档类型: " + fileType);
        };
    }
}
