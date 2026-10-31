package com.apex.scaffold.tools.deployment;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.commonmark.Extension;
import org.commonmark.node.Code;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Heading;
import org.commonmark.node.HtmlInline;
import org.commonmark.node.Node;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.Text;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

/**
 * 本地文档服务的 Markdown 渲染器：将 {@code .md} 文件按 CommonMark/GFM（表格、删除线、任务列表）
 * 渲染为完整 HTML 页面，使文档内目录与跨文件链接在浏览器中可直接点击。
 *
 * <p>标题 id 采用 {@link GitHubHeadingSlug} 生成的 GitHub 风格 slug（与 {@link DocLinksChecker}
 * 的锚点校验同一实现），并在渲染结果中按标题出现顺序注入；文档中的显式 {@code <a id>} 原样透传，
 * 两类锚点口径一致，目录链接因此始终有可跳转目标。仅处理受信任的仓库内文档，不做 HTML 消毒。
 *
 * @author Apex Team
 * @since 1.3.1
 */
final class MarkdownPageRenderer {

    private static final List<Extension> EXTENSIONS = List.of(
            org.commonmark.ext.gfm.tables.TablesExtension.create(),
            org.commonmark.ext.gfm.strikethrough.StrikethroughExtension.create(),
            org.commonmark.ext.task.list.items.TaskListItemsExtension.create());

    private static final Parser PARSER = Parser.builder().extensions(EXTENSIONS).build();

    private static final HtmlRenderer RENDERER =
            HtmlRenderer.builder().extensions(EXTENSIONS).build();

    private static final String CSS = "body{margin:0;padding:2rem 1rem;background:#f6f8fa;"
            + "color:#1f2328;font-family:-apple-system,\"Segoe UI\",\"PingFang SC\",\"Microsoft YaHei\",sans-serif;"
            + "line-height:1.7}"
            + "article{max-width:920px;margin:0 auto;padding:2rem 2.5rem;background:#fff;"
            + "border-radius:8px;box-shadow:0 1px 3px rgba(0,0,0,.08)}"
            + "h1,h2{border-bottom:1px solid #d1d9e0;padding-bottom:.3em}"
            + "pre{background:#f6f8fa;border:1px solid #d1d9e0;border-radius:6px;padding:.8rem;overflow:auto}"
            + "code{background:#eff1f3;border-radius:4px;padding:.1em .35em;font-size:.92em}"
            + "pre code{background:none;padding:0}"
            + "table{border-collapse:collapse;margin:1rem 0;display:block;overflow:auto;max-width:100%}"
            + "th,td{border:1px solid #d1d9e0;padding:.4rem .7rem}"
            + "th{background:#f6f8fa}"
            + "blockquote{border-left:4px solid #d1d9e0;margin:1rem 0;padding:.2rem 1rem;color:#59636e}"
            + "img{max-width:100%}a{color:#0969da}";

    private MarkdownPageRenderer() {}

    /**
     * 将 Markdown 渲染为完整 HTML 页面，并为全部 ATX 标题注入 GitHub 风格 id。
     *
     * @param markdown Markdown 源码
     * @param title 页面标题（应为已去除扩展名的文档名，调用方负责转义）
     * @return 完整 HTML 文档
     */
    static String render(String markdown, String title) {
        Node document = PARSER.parse(markdown);
        List<String> headingIds = headingIds(document);
        String body = injectHeadingIds(RENDERER.render(document), headingIds);
        return "<!DOCTYPE html>\n<html lang=\"zh-CN\">\n<head>\n<meta charset=\"utf-8\">\n"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n"
                + "<title>" + escapeHtml(title) + "</title>\n<style>" + CSS + "</style>\n"
                + "</head>\n<body>\n<article>\n" + body + "</article>\n</body>\n</html>\n";
    }

    /** 按文档顺序收集全部标题的 slug，重复标题按「第二个起追加 {@code -1}」去重。 */
    private static List<String> headingIds(Node root) {
        List<String> ids = new ArrayList<>();
        Set<String> used = new HashSet<>();
        collectHeadings(root, ids, used);
        return ids;
    }

    private static void collectHeadings(Node node, List<String> ids, Set<String> used) {
        for (Node current = node.getFirstChild(); current != null; current = current.getNext()) {
            if (current instanceof Heading) {
                StringBuilder sb = new StringBuilder();
                appendHeadingText(current.getFirstChild(), sb);
                String base = GitHubHeadingSlug.of(sb.toString());
                String slug = base;
                int suffix = 0;
                while (!used.add(slug)) {
                    suffix++;
                    slug = base + "-" + suffix;
                }
                ids.add(slug);
            }
            collectHeadings(current, ids, used);
        }
    }

    /**
     * 提取标题行内节点的可见文本：代码取字面量、换行折叠为空格、图片取 alt 文本；
     * 行内 HTML 标签不参与 slug。强调、链接等容器节点递归取其文本。
     */
    private static void appendHeadingText(Node node, StringBuilder sb) {
        for (Node current = node; current != null; current = current.getNext()) {
            if (current instanceof Text text) {
                sb.append(text.getLiteral());
            } else if (current instanceof Code code) {
                sb.append(code.getLiteral());
            } else if (current instanceof SoftLineBreak || current instanceof HardLineBreak) {
                sb.append(' ');
            } else if (current instanceof HtmlInline) {
                // 行内 HTML 标签不计入 slug
            } else if (current.getFirstChild() != null) {
                appendHeadingText(current.getFirstChild(), sb);
            }
        }
    }

    /**
     * 将渲染结果中第 i 个 {@code <h1>}～{@code <h6>} 起始标签注入第 i 个 slug。
     * commonmark 输出的标题起始标签无属性；若输出中的标题标签数量与语法树不一致
     * （文档含原始 HTML 标题等边缘情况），放弃注入以避免错位。
     */
    private static String injectHeadingIds(String html, List<String> headingIds) {
        if (headingIds.isEmpty()) {
            return html;
        }
        List<Integer> starts = new ArrayList<>();
        int i = 0;
        while ((i = html.indexOf("<h", i)) >= 0) {
            if (i + 2 < html.length()
                    && html.charAt(i + 2) >= '1'
                    && html.charAt(i + 2) <= '6'
                    && html.charAt(i + 3) == '>') {
                starts.add(i);
                i += 4;
            } else {
                i += 2;
            }
        }
        if (starts.size() != headingIds.size()) {
            return html;
        }
        StringBuilder sb = new StringBuilder(html.length());
        int previous = 0;
        for (int index = 0; index < starts.size(); index++) {
            int start = starts.get(index);
            sb.append(html, previous, start + 3)
                    .append(" id=\"")
                    .append(headingIds.get(index))
                    .append('"');
            previous = start + 3;
        }
        sb.append(html, previous, html.length());
        return sb.toString();
    }

    private static String escapeHtml(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
