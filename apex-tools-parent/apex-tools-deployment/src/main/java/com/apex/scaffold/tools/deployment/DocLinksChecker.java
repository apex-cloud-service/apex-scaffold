package com.apex.scaffold.tools.deployment;

import java.io.BufferedReader;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.HtmlInline;
import org.commonmark.node.Image;
import org.commonmark.node.Link;
import org.commonmark.node.Node;
import org.commonmark.node.SourceSpan;

/**
 * 全仓 Markdown/HTML 本地链接与锚点校验工具。
 *
 * <p>文件清单（NUL 分隔、字节排序）由入口脚本经 {@code safe_find_custom} 生成后作为第一个参数传入，
 * 第二个参数为项目根；输出「坏链接（原因）: 文件:行号: 目标 -> 绝对路径」与统计，存在断链时退出码为 1。
 * Markdown 采用与本地渲染器相同的 CommonMark/GFM 语法；HTML 仅检查字面 href/src 与 id/name，
 * 不解析 JavaScript。目录链接以其下 README.md 为锚点文档。
 *
 * @author Apex Team
 * @since 1.3.1
 */
public final class DocLinksChecker {

    private static final int ARGUMENT_COUNT = 2;
    private static final int EXIT_USAGE = 2;
    private static final int INITIAL_CAPACITY = 16;
    private static final String GIT_DIRECTORY = ".git";
    private static final String HTML_TAG_START = "<";
    private static final String HTML_TAG_END = ">";
    private static final String HTML_TAG_NAME_CHARS = "\t\n\r\f />\0";
    private static final String HTML_ATTRIBUTE_NAME_CHARS = "\t\n\r\f /=>";
    private static final String FILE_SCHEME = "file";
    private static final String DOUBLE_SLASH = "//";
    private static final String PERCENT_SIGN = "%";
    private static final char DIGIT_ZERO = '0';
    private static final char DIGIT_NINE = '9';
    private static final char HEX_LOWER_A = 'a';
    private static final char HEX_LOWER_F = 'f';
    private static final char HEX_UPPER_A = 'A';
    private static final char HEX_UPPER_F = 'F';

    private static final Pattern BACKSLASH_DEST =
            Pattern.compile("\\\\([!\"#$%&'()*+,\\-./:;<=>?@\\[\\\\\\]^_`{|}~ ])");

    private DocLinksChecker() {}

    /**
     * 命令行入口：参数为文件清单路径与项目根，退出码 0 表示无断链、1 表示存在断链。
     *
     * @param args 文件清单与项目根
     * @throws IOException 文件读取失败
     * @throws InterruptedException git check-ignore 子进程等待被中断
     */
    public static void main(String[] args) throws IOException, InterruptedException {
        if (args.length != ARGUMENT_COUNT) {
            System.err.println("usage: DocLinksChecker <files-list> <project-root>");
            System.exit(EXIT_USAGE);
        }
        PrintStream out = new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8);
        System.exit(check(Path.of(args[0]), Path.of(args[1]), out));
    }

    /**
     * 执行全量校验并返回退出码，供入口脚本与测试复用。
     *
     * @param filesList NUL 分隔的 Markdown/HTML 文件清单
     * @param root 项目根（大小写检查与 gitignore 判定的基准目录）
     * @param out 结果输出流
     * @return 0 无断链；1 存在断链
     * @throws IOException 读取文件失败
     * @throws InterruptedException git 子进程等待被中断
     */
    public static int check(Path filesList, Path root, PrintStream out) throws IOException, InterruptedException {
        Path projectRoot = root.toAbsolutePath().normalize();
        int total = 0;
        int broken = 0;
        int markdownFiles = 0;
        int htmlFiles = 0;
        Map<Path, Set<String>> anchorCache = new HashMap<>(INITIAL_CAPACITY);
        List<CheckedLink> checked = new ArrayList<>();
        for (byte[] name : readNulSeparated(filesList)) {
            if (name.length == 0) {
                continue;
            }
            Path source = Path.of(new String(name, StandardCharsets.UTF_8));
            boolean html = isHtmlName(source);
            if (html) {
                htmlFiles++;
            } else {
                markdownFiles++;
            }
            String text = Files.readString(source, StandardCharsets.UTF_8);
            DocumentRefs refs = html ? htmlRefs(text) : markdownRefs(text);
            anchorCache.put(source, refs.anchors());
            int[] newlines = newlineOffsets(text);
            for (LinkRef ref : refs.links()) {
                // CommonMark 已解码 Markdown 转义与实体；HTML 属性仅在此解码一次。
                String destination = ref.decoded()
                        ? ref.value()
                        : HtmlEntities.unescape(
                                BACKSLASH_DEST.matcher(ref.value()).replaceAll("$1"));
                Path target = targetPath(source, destination);
                if (target == null) {
                    continue;
                }
                total++;
                checked.add(new CheckedLink(
                        source,
                        lineOf(newlines, ref.offset()),
                        ref.value(),
                        target,
                        unquote(urlSplit(destination).fragment())));
            }
        }

        Set<Path> ignored = ignoredTargets(projectRoot, targetsOf(checked));
        for (CheckedLink link : checked) {
            String reason = classify(link, projectRoot, ignored, anchorCache);
            if (reason != null) {
                broken++;
                out.print("坏链接（" + reason + "）: " + link.source() + ":" + link.line() + ": " + link.value() + " -> "
                        + link.target() + "\n");
            }
        }
        out.print("扫描完成\n");
        out.print("   Markdown 文件数: " + markdownFiles + "\n");
        if (htmlFiles > 0) {
            out.print("   HTML 文件数: " + htmlFiles + "\n");
        }
        out.print("   总链接数: " + total + "\n");
        out.print("   断裂链接: " + broken + "\n");
        return broken > 0 ? 1 : 0;
    }

    private static String classify(
            CheckedLink link, Path projectRoot, Set<Path> ignored, Map<Path, Set<String>> anchorCache)
            throws IOException {
        Path target = link.target();
        if (!Files.exists(target)) {
            return "路径不存在";
        }
        if (!exactExists(projectRoot, target)) {
            return "大小写不匹配";
        }
        if (ignored.contains(target)) {
            return "目标被 .gitignore 排除";
        }
        String fragment = link.fragment();
        if (fragment.isEmpty()) {
            return null;
        }
        boolean directory = Files.isDirectory(target);
        Path document = directory ? target.resolve("README.md") : target;
        if (directory) {
            if (!Files.isRegularFile(document)) {
                return "锚点文档不存在";
            }
            if (!exactExists(projectRoot, document)) {
                return "大小写不匹配";
            }
            if (ignored.contains(document)) {
                return "目标被 .gitignore 排除";
            }
        }
        if (isDocumentName(document) && Files.isRegularFile(document)) {
            Set<String> anchors = anchorCache.get(document);
            if (anchors == null) {
                String text = Files.readString(document, StandardCharsets.UTF_8);
                anchors = (isHtmlName(document) ? htmlRefs(text) : markdownRefs(text)).anchors();
                anchorCache.put(document, anchors);
            }
            if (!anchors.contains(fragment)) {
                return "锚点不存在";
            }
        }
        return null;
    }

    private static boolean isMarkdownName(Path document) {
        return "md".equals(extension(document));
    }

    private static boolean isHtmlName(Path document) {
        String extension = extension(document);
        return "html".equals(extension) || "htm".equals(extension);
    }

    private static boolean isDocumentName(Path document) {
        return isMarkdownName(document) || isHtmlName(document);
    }

    private static String extension(Path document) {
        if (document.getFileName() == null) {
            return "";
        }
        String name = document.getFileName().toString();
        int dot = name.lastIndexOf('.');
        // pathlib.suffix 忽略 ".md" 这类隐藏文件的首个点
        return dot > 0 ? name.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
    }

    private static Set<Path> targetsOf(List<CheckedLink> checked) {
        Set<Path> targets = checked.stream().map(CheckedLink::target).collect(Collectors.toSet());
        for (CheckedLink link : checked) {
            if (Files.isDirectory(link.target())) {
                targets.add(link.target().resolve("README.md"));
            }
        }
        return targets;
    }

    /** 大小写精确存在检查：逐段与目录实际表项比较，发现 Linux/GitHub 上的断链。 */
    private static boolean exactExists(Path projectRoot, Path target) throws IOException {
        if (!target.startsWith(projectRoot)) {
            return Files.exists(target);
        }
        Path relative = projectRoot.relativize(target);
        Path current = projectRoot;
        for (Path part : relative) {
            if (!listDir(current).contains(part.toString())) {
                return false;
            }
            current = current.resolve(part);
        }
        return true;
    }

    private static Set<String> listDir(Path dir) {
        try (var stream = Files.list(dir)) {
            Set<String> names = new HashSet<>();
            stream.forEach(p -> names.add(p.getFileName().toString()));
            return names;
        } catch (IOException e) {
            return Set.of();
        }
    }

    /** 返回被 .gitignore 排除的仓库内目标；这类目标只存在于本机，克隆后链接必然失效。 */
    private static Set<Path> ignoredTargets(Path projectRoot, Set<Path> candidates)
            throws IOException, InterruptedException {
        if (candidates.isEmpty() || !Files.isDirectory(projectRoot.resolve(GIT_DIRECTORY))) {
            return Set.of();
        }
        List<Path> sorted = new ArrayList<>(candidates);
        sorted.sort((a, b) -> a.toString().compareTo(b.toString()));
        StringBuilder payload = new StringBuilder();
        for (Path candidate : sorted) {
            if (candidate.startsWith(projectRoot)) {
                payload.append(projectRoot.relativize(candidate)).append('\n');
            }
        }
        if (payload.length() == 0) {
            return Set.of();
        }
        Process process = new ProcessBuilder("git", "-C", projectRoot.toString(), "check-ignore", "--stdin").start();
        // 清单可能超出管道缓冲区，写 stdin 与读 stdout 必须并发，避免与 git 互相阻塞
        CompletableFuture<Void> writer = CompletableFuture.runAsync(() -> {
            try (var out = process.getOutputStream()) {
                out.write(payload.toString().getBytes(StandardCharsets.UTF_8));
            } catch (IOException ignored) {
                // git 提前退出（如无匹配返回码 1）时写入端自然失败
            }
        });
        String stdout;
        try (BufferedReader reader =
                new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
            stdout = sb.toString();
        }
        writer.join();
        process.waitFor();
        return Arrays.stream(stdout.split("\n"))
                .filter(line -> !line.isBlank())
                .map(projectRoot::resolve)
                .collect(Collectors.toSet());
    }

    private static int[] newlineOffsets(String text) {
        List<Integer> offsets = new ArrayList<>();
        int index = text.indexOf('\n');
        while (index >= 0) {
            offsets.add(index);
            index = text.indexOf('\n', index + 1);
        }
        int[] result = new int[offsets.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = offsets.get(i);
        }
        return result;
    }

    /** 行号：严格位于偏移之前的换行数 + 1，等价 Python bisect_left(newlines, offset) + 1。 */
    private static int lineOf(int[] newlines, int offset) {
        int lo = 0;
        int hi = newlines.length;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (newlines[mid] < offset) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo + 1;
    }

    private static List<byte[]> readNulSeparated(Path filesList) throws IOException {
        byte[] all = Files.readAllBytes(filesList);
        List<byte[]> parts = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < all.length; i++) {
            if (all[i] == 0) {
                parts.add(Arrays.copyOfRange(all, start, i));
                start = i + 1;
            }
        }
        if (start < all.length) {
            parts.add(Arrays.copyOfRange(all, start, all.length));
        }
        parts.sort(DocLinksChecker::compareUnsigned);
        return parts;
    }

    private static int compareUnsigned(byte[] a, byte[] b) {
        int length = Math.min(a.length, b.length);
        for (int i = 0; i < length; i++) {
            int cmp = Byte.compareUnsigned(a[i], b[i]);
            if (cmp != 0) {
                return cmp;
            }
        }
        return Integer.compare(a.length, b.length);
    }

    // ===== CommonMark/GFM 语法树抽取：引用链接已解析为 Link/Image；代码节点不参与 =====

    private static DocumentRefs markdownRefs(String text) {
        Node document = MarkdownPageRenderer.parse(text);
        List<LinkRef> links = new ArrayList<>();
        char[] html = new char[text.length()];
        Arrays.fill(html, ' ');
        collectMarkdown(document, text, links, html);
        HtmlRefs htmlRefs = scanHtml(new String(html));
        links.removeIf(link -> htmlRefs.excluded().stream().anyMatch(range -> range.contains(link.offset())));
        links.addAll(htmlRefs.links());
        links.sort((a, b) -> Integer.compare(a.offset(), b.offset()));
        Set<String> anchors = new HashSet<>(MarkdownPageRenderer.headingIds(document));
        anchors.addAll(htmlRefs.anchors());
        return new DocumentRefs(links, anchors);
    }

    private static void collectMarkdown(Node root, String text, List<LinkRef> links, char[] html) {
        for (Node current = root.getFirstChild(); current != null; current = current.getNext()) {
            List<SourceSpan> spans = current.getSourceSpans();
            if (!spans.isEmpty()) {
                int offset = spans.getFirst().getInputIndex();
                if (current instanceof Link link) {
                    links.add(new LinkRef(offset, link.getDestination(), true));
                } else if (current instanceof Image image) {
                    links.add(new LinkRef(offset, image.getDestination(), true));
                } else if (current instanceof HtmlInline || current instanceof HtmlBlock) {
                    // 只复制真实 HTML 节点的源码；空白保留原偏移，跨行属性诊断不会漂移。
                    for (SourceSpan span : spans) {
                        int start = span.getInputIndex();
                        text.getChars(start, start + span.getLength(), html, start);
                    }
                }
            }
            collectMarkdown(current, text, links, html);
        }
    }

    private static DocumentRefs htmlRefs(String text) {
        HtmlRefs refs = scanHtml(text);
        return new DocumentRefs(refs.links(), refs.anchors());
    }

    // ===== 字面 HTML 标签/属性扫描：排除注释、script/style 内容及 pre/code 示例 =====

    private static HtmlRefs scanHtml(String text) {
        List<LinkRef> links = new ArrayList<>();
        Set<String> anchors = new HashSet<>();
        List<Range> excluded = new ArrayList<>();
        int pos = 0;
        while ((pos = text.indexOf(HTML_TAG_START, pos)) >= 0) {
            if (text.startsWith("<!--", pos)) {
                int end = text.indexOf("-->", pos + 4);
                int next = end < 0 ? text.length() : end + 3;
                excluded.add(new Range(pos, next));
                pos = next;
            } else if (pos + 1 < text.length() && isAsciiLetter(text.charAt(pos + 1))) {
                HtmlTag tag = parseStartTag(text, pos, links, anchors);
                if (tag == null) {
                    break;
                }
                if (Set.of("script", "style", "pre", "code").contains(tag.name())) {
                    Matcher close = Pattern.compile("</" + tag.name() + "\\s*>", Pattern.CASE_INSENSITIVE)
                            .matcher(text);
                    int end = close.find(tag.end()) ? close.end() : text.length();
                    excluded.add(new Range(pos, end));
                    pos = end;
                } else {
                    pos = tag.end();
                }
            } else if (pos + 1 < text.length() && "/!?".indexOf(text.charAt(pos + 1)) >= 0) {
                int end = text.indexOf('>', pos + 2);
                pos = end < 0 ? text.length() : end + 1;
            } else {
                pos++;
            }
        }
        return new HtmlRefs(links, anchors, excluded);
    }

    /** 宽容解析带单双引号或无引号属性的起始标签；未闭合标签不采集属性。 */
    private static HtmlTag parseStartTag(String text, int lt, List<LinkRef> links, Set<String> anchors) {
        int length = text.length();
        int i = lt + 1;
        while (i < length && HTML_TAG_NAME_CHARS.indexOf(text.charAt(i)) < 0) {
            i++;
        }
        String tag = text.substring(lt + 1, i).toLowerCase(Locale.ROOT);
        int scan = i;
        while (scan < length && text.charAt(scan) != HTML_TAG_END.charAt(0)) {
            if (text.charAt(scan) == '"' || text.charAt(scan) == '\'') {
                int close = text.indexOf(text.charAt(scan), scan + 1);
                if (close < 0) {
                    return null;
                }
                scan = close + 1;
            } else {
                scan++;
            }
        }
        if (scan >= length) {
            return null;
        }
        Set<String> seen = new HashSet<>();
        while (i < scan) {
            char c = text.charAt(i);
            if (htmlSpace(c) || c == '/') {
                i++;
                continue;
            }
            int nameStart = i;
            while (i < scan && HTML_ATTRIBUTE_NAME_CHARS.indexOf(text.charAt(i)) < 0) {
                i++;
            }
            String name = text.substring(nameStart, i).toLowerCase(Locale.ROOT);
            while (i < scan && htmlSpace(text.charAt(i))) {
                i++;
            }
            if (i >= scan || text.charAt(i) != '=') {
                continue;
            }
            i++;
            while (i < scan && htmlSpace(text.charAt(i))) {
                i++;
            }
            int start = i;
            String value;
            if (i < scan && isQuote(text.charAt(i))) {
                char quote = text.charAt(i++);
                start = i;
                int close = text.indexOf(quote, i);
                value = text.substring(i, close);
                i = close + 1;
            } else {
                while (i < scan && !htmlSpace(text.charAt(i))) {
                    i++;
                }
                value = text.substring(start, i);
            }
            if (!seen.add(name)) {
                continue;
            }
            if (isLinkAttribute(name)) {
                links.add(new LinkRef(start, value, false));
            } else if (isAnchorAttribute(tag, name)) {
                anchors.add(HtmlEntities.unescape(value));
            }
        }
        return new HtmlTag(tag, scan + 1);
    }

    private static boolean htmlSpace(char c) {
        return c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\u000C' || c == '\u000B';
    }

    private static boolean isAsciiLetter(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    private static boolean isQuote(char value) {
        return value == '"' || value == '\'';
    }

    private static boolean isLinkAttribute(String name) {
        return "href".equals(name) || "src".equals(name);
    }

    private static boolean isAnchorAttribute(String tag, String name) {
        return "id".equals(name) || ("a".equals(tag) && "name".equals(name));
    }

    private record LinkRef(int offset, String value, boolean decoded) {}

    private record DocumentRefs(List<LinkRef> links, Set<String> anchors) {}

    private record HtmlRefs(List<LinkRef> links, Set<String> anchors, List<Range> excluded) {}

    private record HtmlTag(String name, int end) {}

    private record Range(int start, int end) {
        boolean contains(int offset) {
            return offset >= start && offset < end;
        }
    }

    // ===== 目标路径解析（反斜杠/HTML 实体解码、scheme 过滤、百分号解码、绝对化） =====

    private static Path targetPath(Path source, String value) {
        if (value.isEmpty() || value.startsWith(DOUBLE_SLASH)) {
            return null;
        }
        UrlSplit uri = urlSplit(value);
        if (!uri.scheme().isEmpty() && !FILE_SCHEME.equals(uri.scheme())) {
            return null;
        }
        if (FILE_SCHEME.equals(uri.scheme())
                && !uri.netloc().isEmpty()
                && !uri.netloc().toLowerCase(Locale.ROOT).equals("localhost")) {
            return Path.of("//" + uri.netloc() + unquote(uri.path()));
        }
        String path = unquote(uri.path());
        if (path.isEmpty()) {
            return source;
        }
        Path joined =
                Path.of(path).isAbsolute() ? Path.of(path) : source.getParent().resolve(path);
        return joined.toAbsolutePath().normalize();
    }

    /** URL 拆分结果（对齐 Python urlsplit 的四段语义）。 */
    private record UrlSplit(String scheme, String netloc, String path, String fragment) {}

    /** urlsplit 的最小移植：scheme 识别、netloc、path 与 fragment 切分。 */
    private static UrlSplit urlSplit(String url) {
        String scheme = "";
        String rest = url;
        int colon = url.indexOf(':');
        if (colon > 0 && isAsciiLetter(url.charAt(0))) {
            boolean allSchemeChars = true;
            for (int i = 0; i < colon; i++) {
                char c = url.charAt(i);
                if (!isSchemeCharacter(c)) {
                    allSchemeChars = false;
                    break;
                }
            }
            if (allSchemeChars) {
                scheme = url.substring(0, colon).toLowerCase(Locale.ROOT);
                rest = url.substring(colon + 1);
            }
        }
        String netloc = "";
        if (rest.startsWith(DOUBLE_SLASH)) {
            int end = rest.length();
            for (int i = 2; i < rest.length(); i++) {
                char c = rest.charAt(i);
                if (c == '/' || c == '?' || c == '#') {
                    end = i;
                    break;
                }
            }
            netloc = rest.substring(2, end);
            rest = rest.substring(end);
        }
        String fragment = "";
        int hash = rest.indexOf('#');
        if (hash >= 0) {
            fragment = rest.substring(hash + 1);
            rest = rest.substring(0, hash);
        }
        int question = rest.indexOf('?');
        if (question >= 0) {
            rest = rest.substring(0, question);
        }
        return new UrlSplit(scheme, netloc, rest, fragment);
    }

    /** Python urllib.parse.unquote：百分号解码为 UTF-8，非法序列替换为 U+FFFD。 */
    private static String unquote(String s) {
        if (s.indexOf(PERCENT_SIGN) < 0) {
            return s;
        }
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream(s.length());
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == PERCENT_SIGN.charAt(0) && i + 2 < s.length()) {
                int high = hexValue(s.charAt(i + 1));
                int low = hexValue(s.charAt(i + 2));
                if (high >= 0 && low >= 0) {
                    buffer.write((high << 4) | low);
                    i += 3;
                    continue;
                }
            }
            if (Character.isHighSurrogate(c) && i + 1 < s.length() && Character.isLowSurrogate(s.charAt(i + 1))) {
                byte[] bytes = s.substring(i, i + 2).getBytes(StandardCharsets.UTF_8);
                buffer.writeBytes(bytes);
                i += 2;
                continue;
            }
            byte[] bytes = String.valueOf(c).getBytes(StandardCharsets.UTF_8);
            buffer.writeBytes(bytes);
            i++;
        }
        return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
    }

    private static int hexValue(char c) {
        if (c >= DIGIT_ZERO && c <= DIGIT_NINE) {
            return c - '0';
        }
        if (c >= HEX_LOWER_A && c <= HEX_LOWER_F) {
            return c - 'a' + 10;
        }
        if (c >= HEX_UPPER_A && c <= HEX_UPPER_F) {
            return c - 'A' + 10;
        }
        return -1;
    }

    private static boolean isSchemeCharacter(char value) {
        return isAsciiLetter(value)
                || (value >= DIGIT_ZERO && value <= DIGIT_NINE)
                || value == '+'
                || value == '-'
                || value == '.';
    }

    private record CheckedLink(Path source, int line, String value, Path target, String fragment) {}
}
