package com.zhikao.server.document.service;

import com.zhikao.server.document.model.ParsedDocument;
import com.zhikao.server.document.model.ParsedSection;
import com.zhikao.server.entity.ImportDocument;
import com.zhikao.server.entity.ImportRecord;
import com.zhikao.server.mapper.ImportRecordMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 结构化识别服务（T3.8）：基于规则的文档 → import_record 草稿。
 *
 * <p>规则（任务书 10.4）：
 *  - 按章节标题（"第X章"/"一、"/数字编号）切分为多个知识点块
 *  - 标记词识别："一句话记忆/核心知识/重点/易错点" → summary/content/keyPoints/commonMistakes
 *  - 成语条目识别："成语条目：xxx" 或 "成语+拼音+释义+出处+例句" 结构 → 成语草稿
 *  - 题目识别："题干 + A~D 选项 + 答案 + 解析" 结构 → 题目草稿
 *  - 兜底：无法识别段落完整保留（parsedContent + rawExcerpt），不中断任务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContentExtractService {

    /** 章节标题：第X章 / 数字编号 / "一、"（必须带顿号或点号，避免误匹配"一句话记忆"） */
    private static final Pattern CHAPTER_PATTERN = Pattern.compile(
            "^(第[一二三四五六七八九十百0-9]+[章节部分篇篇回]|[0-9]+[、.．]|[一二三四五六七八九十][、.．])\\s*(.*)$");
    /** 强类型标题：成语条目：xxx / 成语：xxx / 题目：xxx / 第X题 */
    private static final Pattern TYPED_TITLE_PATTERN = Pattern.compile(
            "^(成语条目|成语|题目|例题)[:：]\\s*(.*)$|^第[0-9一二三四五六七八九十]+题(.*)$");
    private static final Pattern MARKER_PATTERN = Pattern.compile(
            "^(一句话记忆|核心知识|重点|易错点|释义|出处|例句|近义词|反义词|拼音|答案|解析)\\s*[:：]?\\s*(.*)$");
    private static final Pattern OPTION_PATTERN = Pattern.compile("^([A-Fa-f])[.．、]\\s*(.*)$");

    private final ImportRecordMapper importRecordMapper;
    private final ObjectMapper objectMapper;

    /**
     * 从解析结果生成 import_record 草稿。
     * 识别失败/无法识别段落一律进入待审核草稿（10.4：不中断任务）。
     *
     * @return 生成的草稿数量
     */
    public int extractAndSave(ImportDocument document, ParsedDocument parsed) {
        String rawText = parsed.getRawText();
        if (rawText == null || rawText.isBlank()) {
            return 0;
        }

        List<ParsedSection> sections = splitByTitle(rawText);
        List<ImportRecord> records = new ArrayList<>();

        for (ParsedSection section : sections) {
            int guessType = section.getGuessType();
            if (guessType == 2 || (guessType == 0 && isIdiomSection(section))) {
                records.add(buildIdiomRecord(document.getId(), section));
            } else if (guessType == 3 || (guessType == 0 && isQuestionSection(section))) {
                records.add(buildQuestionRecord(document.getId(), section));
            } else {
                records.add(buildKnowledgeRecord(document.getId(), section));
            }
        }

        for (ImportRecord record : records) {
            importRecordMapper.insert(record);
        }
        return records.size();
    }

    /** 按标题切分：章节标题 / "成语条目："/"题目：" 强标题作为新块起点 */
    private List<ParsedSection> splitByTitle(String rawText) {
        List<ParsedSection> sections = new ArrayList<>();
        ParsedSection current = null;
        StringBuilder buffer = new StringBuilder();

        for (String line : rawText.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            // 尝试强类型标题（成语条目/题目/第X题）
            Matcher typed = TYPED_TITLE_PATTERN.matcher(trimmed);
            if (typed.matches()) {
                if (current != null) {
                    current.setContent(buffer.toString().trim());
                    sections.add(current);
                }
                current = new ParsedSection();
                current.setTitle(trimmed);
                // "题目：xxx" 时题干 xxx 也应进入正文供 buildQuestionRecord 解析
                if (trimmed.startsWith("题目") || trimmed.startsWith("例题")
                        || trimmed.matches("^第[0-9一二三四五六七八九十]+题.*")) {
                    current.setGuessType(3);
                    String stem = typed.group(2) != null ? typed.group(2) : typed.group(3);
                    if (stem != null && !stem.isBlank()) {
                        buffer = new StringBuilder();
                        buffer.append(stem.trim()).append('\n');
                    } else {
                        buffer = new StringBuilder();
                    }
                } else {
                    current.setGuessType(2);
                    buffer = new StringBuilder();
                }
                continue;
            }

            // 尝试章节标题（知识点块）
            Matcher chapter = CHAPTER_PATTERN.matcher(trimmed);
            if (chapter.matches()) {
                if (current != null) {
                    current.setContent(buffer.toString().trim());
                    sections.add(current);
                }
                current = new ParsedSection();
                current.setTitle(trimmed);
                current.setGuessType(1);
                buffer = new StringBuilder();
                continue;
            }

            // 普通行：加入当前块（无块则创建兜底块）
            if (current == null) {
                current = new ParsedSection();
                current.setTitle(trimmed.substring(0, Math.min(trimmed.length(), 30)));
                current.setGuessType(0);
            }
            buffer.append(trimmed).append('\n');
        }
        if (current != null) {
            current.setContent(buffer.toString().trim());
            sections.add(current);
        }
        return sections;
    }

    /** 成语识别（无强标题时）：含"拼音+释义"或"释义+出处"标记词组合 */
    private boolean isIdiomSection(ParsedSection section) {
        String content = section.getContent() + "\n" + section.getTitle();
        boolean hasPinyin = content.contains("拼音");
        boolean hasExplanation = content.contains("释义") || content.contains("解释");
        boolean hasOrigin = content.contains("出处");
        return (hasPinyin && hasExplanation) || (hasExplanation && hasOrigin);
    }

    /** 题目识别：含 ≥2 个选项且含"答案" */
    private boolean isQuestionSection(ParsedSection section) {
        String content = section.getContent();
        int optionCount = 0;
        for (String line : content.split("\\n")) {
            if (OPTION_PATTERN.matcher(line.trim()).matches()) {
                optionCount++;
            }
        }
        return optionCount >= 2 && content.contains("答案");
    }

    /** 知识点草稿：标记词映射到字段 */
    private ImportRecord buildKnowledgeRecord(Long documentId, ParsedSection section) {
        Map<String, Object> parsed = new LinkedHashMap<>();
        parsed.put("title", section.getTitle());
        parsed.put("category", null);
        parsed.put("summary", null);
        parsed.put("content", null);
        parsed.put("keyPoints", null);
        parsed.put("commonMistakes", null);

        for (String line : section.getContent().split("\\n")) {
            Matcher matcher = MARKER_PATTERN.matcher(line.trim());
            if (matcher.matches()) {
                String key = matcher.group(1);
                String value = matcher.group(2).trim();
                switch (key) {
                    case "一句话记忆" -> parsed.put("summary", value);
                    case "核心知识" -> parsed.put("content", value);
                    case "重点" -> parsed.put("keyPoints", value);
                    case "易错点" -> parsed.put("commonMistakes", value);
                    default -> {
                    }
                }
            }
        }

        ImportRecord record = new ImportRecord();
        record.setDocumentId(documentId);
        record.setContentType(1);
        record.setSourceTitle(truncate(section.getTitle(), 200));
        record.setParsedContent(toJson(parsed));
        record.setRawExcerpt(truncate(section.getContent(), 2000));
        record.setStatus(0);
        return record;
    }

    /** 成语草稿 */
    private ImportRecord buildIdiomRecord(Long documentId, ParsedSection section) {
        Map<String, Object> parsed = new LinkedHashMap<>();
        parsed.put("word", extractIdiomWord(section));
        parsed.put("pinyin", null);
        parsed.put("explanation", null);
        parsed.put("origin", null);
        parsed.put("example", null);
        parsed.put("synonyms", null);
        parsed.put("antonyms", null);

        for (String line : section.getContent().split("\\n")) {
            Matcher matcher = MARKER_PATTERN.matcher(line.trim());
            if (matcher.matches()) {
                String key = matcher.group(1);
                String value = matcher.group(2).trim();
                switch (key) {
                    case "拼音" -> parsed.put("pinyin", value);
                    case "释义" -> parsed.put("explanation", value);
                    case "出处" -> parsed.put("origin", value);
                    case "例句" -> parsed.put("example", value);
                    case "近义词" -> parsed.put("synonyms", value);
                    case "反义词" -> parsed.put("antonyms", value);
                    default -> {
                    }
                }
            }
        }

        ImportRecord record = new ImportRecord();
        record.setDocumentId(documentId);
        record.setContentType(2);
        record.setSourceTitle(truncate(section.getTitle(), 200));
        record.setParsedContent(toJson(parsed));
        record.setRawExcerpt(truncate(section.getContent(), 2000));
        record.setStatus(0);
        return record;
    }

    /** 从"成语条目：画蛇添足"标题提取成语词 */
    private String extractIdiomWord(ParsedSection section) {
        String title = section.getTitle();
        Matcher typed = TYPED_TITLE_PATTERN.matcher(title);
        if (typed.matches()) {
            String word = typed.group(2) != null ? typed.group(2) : typed.group(3);
            if (word != null && !word.isBlank()) {
                return word.trim();
            }
        }
        return title;
    }

    /** 题目草稿：题干 + 选项 + 答案 + 解析（关联在审核环节人工指定，10.4 规则 4） */
    private ImportRecord buildQuestionRecord(Long documentId, ParsedSection section) {
        Map<String, Object> parsed = new LinkedHashMap<>();
        parsed.put("content", null);
        parsed.put("options", new ArrayList<Map<String, Object>>());
        parsed.put("answer", null);
        parsed.put("analysis", null);
        parsed.put("knowledgeId", null);
        parsed.put("idiomId", null);

        String[] lines = section.getContent().split("\\n");
        StringBuilder stem = new StringBuilder();
        List<Map<String, Object>> options = new ArrayList<>();
        String answer = null;
        String analysis = null;

        for (String line : lines) {
            String trimmed = line.trim();
            Matcher optionMatcher = OPTION_PATTERN.matcher(trimmed);
            if (optionMatcher.matches()) {
                Map<String, Object> option = new LinkedHashMap<>();
                option.put("label", optionMatcher.group(1).toUpperCase());
                option.put("content", optionMatcher.group(2).trim());
                option.put("isCorrect", 0);
                options.add(option);
            } else if (trimmed.startsWith("答案")) {
                answer = trimmed.replaceFirst("答案\\s*[:：]?\\s*", "").trim();
            } else if (trimmed.startsWith("解析")) {
                analysis = trimmed.replaceFirst("解析\\s*[:：]?\\s*", "").trim();
            } else {
                stem.append(trimmed).append(' ');
            }
        }
        parsed.put("content", stem.toString().trim());
        parsed.put("options", options);
        parsed.put("answer", answer);
        parsed.put("analysis", analysis);

        ImportRecord record = new ImportRecord();
        record.setDocumentId(documentId);
        record.setContentType(3);
        record.setSourceTitle(truncate(stem.toString().trim(), 200));
        record.setParsedContent(toJson(parsed));
        record.setRawExcerpt(truncate(section.getContent(), 2000));
        record.setStatus(0);
        return record;
    }

    private String toJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            log.warn("parsed_content JSON 序列化失败", e);
            return "{}";
        }
    }

    private String truncate(String text, int maxLen) {
        if (text == null) {
            return "";
        }
        return text.length() <= maxLen ? text : text.substring(0, maxLen);
    }
}
