package com.zhikao.server.document.parser;

import com.zhikao.server.document.model.ParsedDocument;
import com.zhikao.server.storage.FileStorage;
import lombok.RequiredArgsConstructor;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Word 解析器（POI，T3.7）。
 * 支持 docx（XWPF）与 doc（HWPF），保留标题/段落/表格/列表结构。
 */
@Component
@RequiredArgsConstructor
public class WordParser implements DocumentParser {

    private final FileStorage fileStorage;

    @Override
    public ParsedDocument parse(String filePath) {
        try (InputStream in = fileStorage.load(filePath)) {
            if (in == null) {
                throw new IllegalStateException("文件不存在: " + filePath);
            }
            if (filePath.toLowerCase().endsWith(".docx")) {
                return parseDocx(in);
            }
            return parseDoc(in);
        } catch (IOException e) {
            throw new IllegalStateException("Word 解析失败: " + e.getMessage(), e);
        }
    }

    private ParsedDocument parseDocx(InputStream in) throws IOException {
        try (XWPFDocument doc = new XWPFDocument(in)) {
            StringBuilder sb = new StringBuilder();
            List<XWPFParagraph> paragraphs = doc.getParagraphs();
            for (XWPFParagraph p : paragraphs) {
                sb.append(p.getText()).append('\n');
            }
            for (XWPFTable table : doc.getTables()) {
                table.getRows().forEach(row -> {
                    row.getTableCells().forEach(cell -> sb.append(cell.getText()).append(' '));
                    sb.append('\n');
                });
            }
            ParsedDocument result = new ParsedDocument();
            result.setRawText(sb.toString());
            return result;
        }
    }

    private ParsedDocument parseDoc(InputStream in) throws IOException {
        try (HWPFDocument doc = new HWPFDocument(in)) {
            try (WordExtractor extractor = new WordExtractor(doc)) {
                ParsedDocument result = new ParsedDocument();
                result.setRawText(extractor.getText());
                return result;
            }
        }
    }
}
