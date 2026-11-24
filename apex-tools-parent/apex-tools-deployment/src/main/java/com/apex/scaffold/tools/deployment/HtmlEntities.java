package com.apex.scaffold.tools.deployment;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 与 CPython {@code html.unescape} 逐字符对齐的 HTML 实体解码。
 *
 * <p>移植自本机 CPython 3.15 的 {@code html} 模块：命名实体表以资源文件
 * {@code html5-entities.txt}（由 {@code html.entities.html5} 一次性导出，
 * 同时含带分号与 106 个 legacy 无分号条目）加载；数值引用的非法码点替换表与
 * 移除表保持原实现。供文档链接检查器解码链接目标与标题文本，保证移植前后
 * 解码结果一致。
 *
 * @author Apex Team
 * @since 1.3.1
 */
final class HtmlEntities {

    private static final char AMPERSAND = '&';
    private static final char HASH = '#';
    private static final char LOWER_X = 'x';
    private static final char UPPER_X = 'X';
    private static final int MAX_SMALL_CODEPOINT_BITS = 31;
    private static final int MAX_UNICODE_CODEPOINT = 0x10FFFF;
    private static final int HIGH_SURROGATE_START = 0xD800;
    private static final int LOW_SURROGATE_END = 0xDFFF;
    private static final int HTML_ENTITY_CAPACITY = 4096;
    private static final int MIN_ENTITY_PREFIX_LENGTH = 2;
    private static final char BACKSLASH = '\\';

    private static final Pattern CHARREF =
            Pattern.compile("&(#[0-9]+;?|#[xX][0-9a-fA-F]+;?|[^\\t\\n\\f <&#;]{1,32};?)");

    private static final char REPLACEMENT = '\uFFFD';

    private static final Map<Integer, String> INVALID_CHARREFS = buildInvalidCharrefs();

    private static final Map<String, String> HTML5 = loadHtml5();

    private HtmlEntities() {}

    /**
     * 解码全部命名与数值字符引用，语义与 CPython {@code html.unescape} 一致。
     *
     * @param s 含或不含字符引用的文本
     * @return 解码后的文本
     */
    static String unescape(String s) {
        if (s.indexOf(AMPERSAND) < 0) {
            return s;
        }
        Matcher m = CHARREF.matcher(s);
        StringBuilder sb = new StringBuilder(s.length());
        int last = 0;
        while (m.find()) {
            sb.append(s, last, m.start());
            sb.append(replaceCharref(m.group(1)));
            last = m.end();
        }
        sb.append(s, last, s.length());
        return sb.toString();
    }

    private static String replaceCharref(String s) {
        if (s.charAt(0) == HASH) {
            BigInteger num;
            if (s.charAt(1) == LOWER_X || s.charAt(1) == UPPER_X) {
                num = new BigInteger(s.substring(2).replaceAll(";$", ""), 16);
            } else {
                num = new BigInteger(s.substring(1).replaceAll(";$", ""));
            }
            // 非法替换表键最大 0x9F，超界数值不可能命中，先走范围分支
            if (num.bitLength() <= MAX_SMALL_CODEPOINT_BITS) {
                int v = num.intValue();
                String mapped = INVALID_CHARREFS.get(v);
                if (mapped != null) {
                    return mapped;
                }
            }
            if (num.compareTo(BigInteger.valueOf(MAX_UNICODE_CODEPOINT)) > 0) {
                return String.valueOf(REPLACEMENT);
            }
            int v = num.intValue();
            if (v >= HIGH_SURROGATE_START && v <= LOW_SURROGATE_END) {
                return String.valueOf(REPLACEMENT);
            }
            if (isInvalidCodepoint(v)) {
                return "";
            }
            return new String(Character.toChars(v));
        }
        String direct = HTML5.get(s);
        if (direct != null) {
            return direct;
        }
        for (int x = s.length() - 1; x >= MIN_ENTITY_PREFIX_LENGTH; x--) {
            String prefix = HTML5.get(s.substring(0, x));
            if (prefix != null) {
                return prefix + s.substring(x);
            }
        }
        return "&" + s;
    }

    private static boolean isInvalidCodepoint(int v) {
        return (v >= 0x1 && v <= 0x8)
                || v == 0xB
                || (v >= 0xE && v <= 0x1F)
                || (v >= 0x7F && v <= 0x9F)
                || (v >= 0xFDD0 && v <= 0xFDEF)
                || (v & 0xFFFF) == 0xFFFE
                || (v & 0xFFFF) == 0xFFFF;
    }

    private static Map<Integer, String> buildInvalidCharrefs() {
        Map<Integer, String> map = new HashMap<>(HTML_ENTITY_CAPACITY);
        map.put(0x00, "\uFFFD");
        map.put(0x0D, "\r");
        map.put(0x80, "\u20AC");
        map.put(0x81, "\u0081");
        map.put(0x82, "\u201A");
        map.put(0x83, "\u0192");
        map.put(0x84, "\u201E");
        map.put(0x85, "\u2026");
        map.put(0x86, "\u2020");
        map.put(0x87, "\u2021");
        map.put(0x88, "\u02C6");
        map.put(0x89, "\u2030");
        map.put(0x8A, "\u0160");
        map.put(0x8B, "\u2039");
        map.put(0x8C, "\u0152");
        map.put(0x8D, "\u008D");
        map.put(0x8E, "\u017D");
        map.put(0x8F, "\u008F");
        map.put(0x90, "\u0090");
        map.put(0x91, "\u2018");
        map.put(0x92, "\u2019");
        map.put(0x93, "\u201C");
        map.put(0x94, "\u201D");
        map.put(0x95, "\u2022");
        map.put(0x96, "\u2013");
        map.put(0x97, "\u2014");
        map.put(0x98, "\u02DC");
        map.put(0x99, "\u2122");
        map.put(0x9A, "\u0161");
        map.put(0x9B, "\u203A");
        map.put(0x9C, "\u0153");
        map.put(0x9D, "\u009D");
        map.put(0x9E, "\u017E");
        map.put(0x9F, "\u0178");
        return Map.copyOf(map);
    }

    private static Map<String, String> loadHtml5() {
        Map<String, String> map = new HashMap<>(HTML_ENTITY_CAPACITY);
        try (InputStream in = HtmlEntities.class.getResourceAsStream("/html5-entities.txt")) {
            if (in == null) {
                throw new IllegalStateException("缺少资源 html5-entities.txt");
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isEmpty()) {
                        continue;
                    }
                    int tab = line.indexOf('\t');
                    if (tab <= 0) {
                        throw new IllegalStateException("html5-entities.txt 行格式非法: " + line);
                    }
                    map.put(line.substring(0, tab), decodeValue(line.substring(tab + 1)));
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("读取 html5-entities.txt 失败", e);
        }
        return Map.copyOf(map);
    }

    /** 还原导出时的反斜杠u四位十六进制转义（换行、制表、反斜杠等不可直接入表字符）。 */
    private static String decodeValue(String escaped) {
        if (escaped.indexOf(BACKSLASH) < 0) {
            return escaped;
        }
        StringBuilder sb = new StringBuilder(escaped.length());
        for (int i = 0; i < escaped.length(); i++) {
            char c = escaped.charAt(i);
            if (c == '\\' && i + 6 <= escaped.length() && escaped.charAt(i + 1) == 'u') {
                sb.append((char) Integer.parseInt(escaped.substring(i + 2, i + 6), 16));
                i += 5;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
