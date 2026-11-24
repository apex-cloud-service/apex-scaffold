package com.apex.scaffold.tools.deployment;

import java.util.Locale;

/**
 * GitHub 风格标题 slug 生成，供 {@link DocLinksChecker} 与 {@link MarkdownPageRenderer} 共用，
 * 保证「链接校验判定的锚点」与「本地文档服务实际渲染的标题 id」始终同一口径。
 *
 * <p>规则：转小写后仅保留 Unicode L/N/M 类（字母、数字、组合标记）码点与连字符、下划线，
 * 空白折叠为 {@code -}；重复标题的序号后缀由调用方按「第二个起追加 {@code -1}」约定追加。
 *
 * @author Apex Team
 * @since 1.3.1
 */
final class GitHubHeadingSlug {

    private static final char SPACE = ' ';
    private static final char HYPHEN = '-';
    private static final char UNDERSCORE = '_';

    private GitHubHeadingSlug() {}

    /**
     * 由标题文本生成 slug 基名。
     *
     * <p>传入前应已去除 HTML 标签与 Markdown 行内标记、完成实体解码（校验器从源码文本
     * 解析，渲染器从语法树提取，各自的预处理在调用方完成）；本方法额外清理可能残留的
     * Markdown 标记字符作为防御。
     *
     * @param title 标题文本
     * @return slug 基名（不含重复序号后缀）
     */
    static String of(String title) {
        String value = title.strip().toLowerCase(Locale.ROOT);
        value = value.replace("`", "").replace("*", "").replace("~", "").replace("\\", "");
        StringBuilder sb = new StringBuilder(value.length());
        value.codePoints().forEach(cp -> {
            if (cp == SPACE || cp == HYPHEN || cp == UNDERSCORE || isLetterNumberMark(cp)) {
                sb.appendCodePoint(cp);
            }
        });
        return sb.toString().replace(" ", "-");
    }

    /** Unicode 类别属于 L/N/M（字母、数字、组合标记）的码点在 GitHub slug 中保留。 */
    private static boolean isLetterNumberMark(int codePoint) {
        int type = Character.getType(codePoint);
        return type == Character.UPPERCASE_LETTER
                || type == Character.LOWERCASE_LETTER
                || type == Character.TITLECASE_LETTER
                || type == Character.MODIFIER_LETTER
                || type == Character.OTHER_LETTER
                || type == Character.DECIMAL_DIGIT_NUMBER
                || type == Character.LETTER_NUMBER
                || type == Character.OTHER_NUMBER
                || type == Character.NON_SPACING_MARK
                || type == Character.COMBINING_SPACING_MARK
                || type == Character.ENCLOSING_MARK;
    }
}
