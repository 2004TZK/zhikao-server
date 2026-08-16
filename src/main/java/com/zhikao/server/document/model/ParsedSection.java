package com.zhikao.server.document.model;

import lombok.Data;

/**
 * 解析内容块（T3.7）：标题 + 正文片段。
 * T3.8 结构化识别基于此生成 import_record 草稿。
 */
@Data
public class ParsedSection {

    private String title;

    private String content;

    /** 块类型（规则猜测，T3.8 细化）：1 知识点 2 成语 3 题目 0 未识别 */
    private int guessType = 0;

    public ParsedSection() {
    }

    public ParsedSection(String title, String content) {
        this.title = title;
        this.content = content;
    }
}
