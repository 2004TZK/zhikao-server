package com.zhikao.server.document.parser;

import com.zhikao.server.document.model.ParsedDocument;
import com.zhikao.server.storage.FileStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * TXT 解析器（T3.7）：按 UTF-8 读取全文。
 */
@Component
@RequiredArgsConstructor
public class TxtParser implements DocumentParser {

    private final FileStorage fileStorage;

    @Override
    public ParsedDocument parse(String filePath) {
        try (InputStream in = fileStorage.load(filePath)) {
            if (in == null) {
                throw new IllegalStateException("文件不存在: " + filePath);
            }
            ParsedDocument result = new ParsedDocument();
            result.setRawText(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            return result;
        } catch (IOException e) {
            throw new IllegalStateException("TXT 解析失败: " + e.getMessage(), e);
        }
    }
}
