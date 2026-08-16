package com.zhikao.server.document.parser;

import com.zhikao.server.document.model.ParsedDocument;
import com.zhikao.server.storage.FileStorage;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;

/**
 * PDF 解析器（PDFBox，T3.7）。
 *  - 有文本层：直接提取（TEXT_PDF）
 *  - 无文本层（扫描版）：标记 scannedPdf=true，由调用方返回 4003（V1.0 不支持 OCR，V1.5 提供）
 */
@Component
@RequiredArgsConstructor
public class PdfParser implements DocumentParser {

    private final FileStorage fileStorage;

    @Override
    public ParsedDocument parse(String filePath) {
        try (InputStream in = fileStorage.load(filePath)) {
            if (in == null) {
                throw new IllegalStateException("文件不存在: " + filePath);
            }
            try (PDDocument document = Loader.loadPDF(in.readAllBytes())) {
                ParsedDocument result = new ParsedDocument();
                if (document.isEncrypted()) {
                    throw new IllegalStateException("PDF 已加密，无法解析");
                }
                PDFTextStripper stripper = new PDFTextStripper();
                String text = stripper.getText(document);
                if (text == null || text.trim().isEmpty()) {
                    // 无文本层 → 扫描版 PDF（V1.0 拒绝解析，V1.5 OCR）
                    result.setScannedPdf(true);
                    return result;
                }
                result.setRawText(text);
                return result;
            }
        } catch (IOException e) {
            throw new IllegalStateException("PDF 解析失败: " + e.getMessage(), e);
        }
    }
}
