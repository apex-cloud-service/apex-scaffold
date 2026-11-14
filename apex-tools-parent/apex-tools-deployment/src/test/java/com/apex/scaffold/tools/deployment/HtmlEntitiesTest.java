package com.apex.scaffold.tools.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * {@link HtmlEntities#unescape} 与 CPython html.unescape 的逐字符对齐断言。
 *
 * @author Apex Team
 * @since 1.3.1
 */
class HtmlEntitiesTest {

    @Test
    void namedEntitiesWithSemicolon() {
        assertEquals("&", HtmlEntities.unescape("&amp;"));
        assertEquals("<a>", HtmlEntities.unescape("&lt;a&gt;"));
        assertEquals("\u4e2d\u6587", HtmlEntities.unescape("&#x4e2d;&#x6587;"));
    }

    @Test
    void legacyNamedEntitiesWithoutSemicolon() {
        assertEquals("&", HtmlEntities.unescape("&amp"));
        assertEquals("<", HtmlEntities.unescape("&lt"));
        assertEquals("\u00A0", HtmlEntities.unescape("&nbsp"));
    }

    @Test
    void numericReferences() {
        assertEquals("&", HtmlEntities.unescape("&#38;"));
        assertEquals("&", HtmlEntities.unescape("&#x26;"));
        assertEquals("&", HtmlEntities.unescape("&#X26;"));
        assertEquals("\n", HtmlEntities.unescape("&NewLine;"));
        assertEquals("\t", HtmlEntities.unescape("&Tab;"));
    }

    @Test
    void invalidNumericReferencesMatchPython() {
        assertEquals("\uFFFD", HtmlEntities.unescape("&#0;"));
        assertEquals("\r", HtmlEntities.unescape("&#13;"));
        assertEquals("\u20AC", HtmlEntities.unescape("&#128;"));
        assertEquals("\uFFFD", HtmlEntities.unescape("&#55296;"));
        assertEquals("\uFFFD", HtmlEntities.unescape("&#1114112;"));
        assertEquals("", HtmlEntities.unescape("&#1;"));
        assertEquals("", HtmlEntities.unescape("&#xFDD0;"));
        assertEquals("\uD83D\uDD06", HtmlEntities.unescape("&#128262;"));
    }

    @Test
    void longestPrefixFallback() {
        // "amp" 是 legacy 无分号条目，前缀命中后余文原样保留
        assertEquals("&x", HtmlEntities.unescape("&ampx"));
        // 无任何前缀命中时原样返回
        assertEquals("&foo;", HtmlEntities.unescape("&foo;"));
        assertEquals("abc&def", HtmlEntities.unescape("abc&def"));
    }

    @Test
    void plainTextUnchanged() {
        assertEquals("plain text 123", HtmlEntities.unescape("plain text 123"));
        assertEquals("", HtmlEntities.unescape(""));
    }
}
