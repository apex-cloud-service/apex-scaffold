package com.apex.scaffold.knowledge.domain.model;

/**
 * 文档解析结果。
 *
 * @param text 抽取出的纯文本正文（已做基本清洗，非空）
 * @param detectedContentType 解析器探测到的内容类型
 * @author Apex Team
 * @since 1.3.2
 */
public record ParsedDocument(String text, String detectedContentType) {}
