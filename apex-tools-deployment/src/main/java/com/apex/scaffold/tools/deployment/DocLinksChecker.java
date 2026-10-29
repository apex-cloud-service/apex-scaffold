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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 全仓 Markdown 本地链接与锚点校验工具，逐行为对齐原 {@code check-doc-links.sh} 内嵌 Python 实现。
 *
 * <p>契约保持不变：文件清单（NUL 分隔、字节排序）由入口脚本经 {@code safe_find_custom} 生成后
 * 作为第一个参数传入，第二个参数为项目根；输出「坏链接（原因）: 文件:行号: 目标 -> 绝对路径」、
 * 「扫描完成」与三项统计，存在断链时退出码为 1。校验项包括路径存在、大小写精确匹配、
 * 目标被 .gitignore 排除、Markdown 标题与 HTML 显式锚点；目录链接以其下 README.md 为锚点文档。
 *
 * @author Apex Team
 * @since 1.3.1
 */
public final class DocLinksChecker {

    private static final Pattern FENCE = Pattern.compile(" {0,3}(`{3,}|~{3,})");

    private static final Pattern INDENTED_CODE = Pattern.compile("(?: {4}|\\t)");

    private static final Pattern LIST_ITEM = Pattern.compile("\\s*(?:[-+*]|\\d+[.)])\\s+");

    private static final Pattern HTML_COMMENT = Pattern.compile("<!--.*?-->", Pattern.DOTALL);

    private static final Pattern INLINE_CODE = Pattern.compile("(`+)(?!`)(.*?)(?<!`)\\1(?!`)", Pattern.DOTALL);

    private static final Pattern ATX_HEADING = Pattern.compile(" {0,3}#{1,6}(?:[ \\t]+(.*)|$)");

    private static final Pattern TRAILING_HASHES = Pattern.compile("[ \\t]+#+[ \\t]*$");

    private static final Pattern SETEXT_UNDERLINE = Pattern.compile(" {0,3}(?:=+|-+)[ \\t]*");

    private static final Pattern INLINE_LINK_TEXT = Pattern.compile("!?\\[([^\\]]*)\\]\\([^)]*\\)");

    private static final Pattern REFERENCE_LINK_TEXT = Pattern.compile("\\[([^\\]]*)\\]\\[[^\\]]*\\]");

    private static final Pattern HTML_TAG = Pattern.compile("<[^>]*>");

    private static final Pattern BACKSLASH_PUNCT =
            Pattern.compile("\\\\([!\"#$%&'()*+,\\-./:;<=>?@\\[\\\\\\]^_`{|}~])");

    private static final Pattern BACKSLASH_DEST =
            Pattern.compile("\\\\([!\"#$%&'()*+,\\-./:;<=>?@\\[\\\\\\]^_`{|}~ ])");

    private static final Pattern REFERENCE_DEFINITION =
            Pattern.compile("^ {0,3}\\[([^\\]\\n]+)\\]:[ \\t]*", Pattern.MULTILINE);

    private static final Pattern REFERENCE_DEFINITION_LINE =
            Pattern.compile("^ {0,3}\\[[^\\]\\n]+\\]:[^\\n]*", Pattern.MULTILINE);

    private static final Pattern COLLAPSED_REFERENCE = Pattern.compile("\\[([^\\]\\n]*)\\]");

    private static final Pattern LINK_SUFFIX =
            Pattern.compile("\\s*(?:\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'|\\((?:\\\\.|[^)\\\\])*\\))?\\s*\\)");

    private DocLinksChecker() {}

    /**
     * 命令行入口：参数为文件清单路径与项目根，退出码 0 表示无断链、1 表示存在断链。
     *
     * @param args 文件清单与项目根
     * @throws IOException 文件清单或 Markdown 读取失败
     * @throws InterruptedException git check-ignore 子进程等待被中断
     */
    public static void main(String[] args) throws IOException, InterruptedException {
        if (args.length != 2) {
            System.err.println("usage: DocLinksChecker <files-list> <project-root>");
            System.exit(2);
        }
        PrintStream out = new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8);
        System.exit(check(Path.of(args[0]), Path.of(args[1]), out));
    }

    /**
     * 执行全量校验并返回退出码，供入口脚本与测试复用。
     *
     * @param filesList NUL 分隔的 Markdown 文件清单
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
        int files = 0;
        Map<Path, Set<String>> anchorCache = new HashMap<>();
        List<CheckedLink> checked = new ArrayList<>();
        for (byte[] name : readNulSeparated(filesList)) {
            if (name.length == 0) {
                continue;
            }
            Path source = Path.of(new String(name, StandardCharsets.UTF_8));
            files++;
            String text = Files.readString(source, StandardCharsets.UTF_8);
            int[] newlines = newlineOffsets(text);
            for (LinkRef ref : links(text)) {
                Path target = targetPath(source, ref.value());
                if (target == null) {
                    continue;
                }
                total++;
                checked.add(new CheckedLink(source, lineOf(newlines, ref.offset()), ref.value(), target));
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
        out.print("   Markdown 文件数: " + files + "\n");
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
        String fragment = unquote(urlSplit(HtmlEntities.unescape(link.value())).fragment());
        Path document = Files.isDirectory(target) ? target.resolve("README.md") : target;
        if (!fragment.isEmpty()
                && document.getFileName() != null
                && isMarkdownName(document)
                && Files.isRegularFile(document)) {
            Set<String> anchors = anchorCache.computeIfAbsent(document, DocLinksChecker::readAnchorsUnchecked);
            if (!anchors.contains(fragment)) {
                return "锚点不存在";
            }
        }
        return null;
    }

    private static boolean isMarkdownName(Path document) {
        String name = document.getFileName().toString();
        int dot = name.lastIndexOf('.');
        // pathlib.suffix 忽略 ".md" 这类隐藏文件的首个点
        return dot > 0 && name.substring(dot + 1).toLowerCase(Locale.ROOT).equals("md");
    }

    private static Set<String> readAnchorsUnchecked(Path document) {
        try {
            return anchors(Files.readString(document, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("读取锚点文档失败: " + document, e);
        }
    }

    private static Set<Path> targetsOf(List<CheckedLink> checked) {
        return checked.stream().map(CheckedLink::target).collect(Collectors.toSet());
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
        if (candidates.isEmpty() || !Files.isDirectory(projectRoot.resolve(".git"))) {
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
        Thread writer = new Thread(() -> {
            try (var out = process.getOutputStream()) {
                out.write(payload.toString().getBytes(StandardCharsets.UTF_8));
            } catch (IOException ignored) {
                // git 提前退出（如无匹配返回码 1）时写入端自然失败
            }
        });
        writer.start();
        Set<Path> ignored = new HashSet<>();
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
        ignored = Arrays.stream(stdout.split("\n"))
                .filter(line -> !line.isBlank())
                .map(projectRoot::resolve)
                .collect(Collectors.toSet());
        return ignored;
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

    // ===== 文本遮蔽（fence、缩进代码、注释、行内代码替换为空格，保留换行与长度） =====

    private static String maskCode(String text, boolean inline) {
        StringBuilder joined = new StringBuilder(text.length());
        String fence = null;
        for (String line : splitLinesKeepEnds(text)) {
            Matcher m = FENCE.matcher(line);
            boolean fenceStart = m.lookingAt();
            if (fence != null) {
                if (fenceStart
                        && m.group(1).charAt(0) == fence.charAt(0)
                        && m.group(1).length() >= fence.length()
                        && line.substring(m.end()).strip().isEmpty()) {
                    fence = null;
                }
                joined.append(maskNewlines(line));
            } else if (fenceStart) {
                fence = m.group(1);
                joined.append(maskNewlines(line));
            } else if (INDENTED_CODE.matcher(line).lookingAt()
                    && !LIST_ITEM.matcher(line).lookingAt()) {
                joined.append(maskNewlines(line));
            } else {
                joined.append(line);
            }
        }
        String result = maskMatches(joined.toString(), HTML_COMMENT);
        if (!inline) {
            return result;
        }
        return maskMatches(result, INLINE_CODE);
    }

    private static String maskNewlines(String line) {
        StringBuilder sb = new StringBuilder(line.length());
        for (int i = 0; i < line.length(); i++) {
            sb.append(line.charAt(i) == '\n' ? '\n' : ' ');
        }
        return sb.toString();
    }

    private static String maskMatches(String text, Pattern pattern) {
        Matcher m = pattern.matcher(text);
        StringBuilder sb = new StringBuilder(text.length());
        int last = 0;
        while (m.find()) {
            sb.append(text, last, m.start());
            sb.append(maskNewlines(m.group()));
            last = m.end();
        }
        sb.append(text, last, text.length());
        return sb.toString();
    }

    /** Python str.splitlines(keepends=True)：按 Unicode 行边界切分并保留行尾。 */
    private static List<String> splitLinesKeepEnds(String text) {
        List<String> lines = new ArrayList<>();
        int start = 0;
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            int boundary = lineBoundary(c);
            if (boundary < 0) {
                i++;
                continue;
            }
            int end = i + 1;
            if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                end = i + 2;
            }
            lines.add(text.substring(start, end));
            start = end;
            i = start;
        }
        if (start < text.length()) {
            lines.add(text.substring(start));
        }
        return lines;
    }

    /** Python str.splitlines()：按 Unicode 行边界切分并去掉行尾。 */
    private static List<String> splitLines(String text) {
        List<String> lines = new ArrayList<>();
        int start = 0;
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (lineBoundary(c) < 0) {
                i++;
                continue;
            }
            int skip = c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n' ? 2 : 1;
            lines.add(text.substring(start, i));
            i += skip;
            start = i;
        }
        if (start < text.length()) {
            lines.add(text.substring(start));
        }
        return lines;
    }

    private static int lineBoundary(char c) {
        if (c == '\r'
                || c == '\n'
                || c == '\u000B'
                || c == '\u000C'
                || c == '\u001C'
                || c == '\u001D'
                || c == '\u001E'
                || c == '\u0085'
                || c == '\u2028'
                || c == '\u2029') {
            return 1;
        }
        return -1;
    }

    // ===== 锚点收集（显式 HTML id/name + GitHub 风格标题 slug，重复标题追加序号） =====

    private static Set<String> anchors(String text) {
        String visible = maskCode(text, false);
        Set<String> result = new HashSet<>(explicitAnchors(maskCode(text, true)));
        Set<String> used = new HashSet<>();
        List<String> lines = splitLines(visible);
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            String title;
            Matcher m = ATX_HEADING.matcher(line);
            if (m.lookingAt()) {
                String group = m.group(1) == null ? "" : m.group(1);
                title = TRAILING_HASHES.matcher(group).replaceAll("").strip();
            } else if (index + 1 < lines.size()
                    && !line.strip().isEmpty()
                    && !line.startsWith("    ")
                    && SETEXT_UNDERLINE.matcher(lines.get(index + 1)).matches()) {
                title = line.strip();
            } else {
                continue;
            }
            String base = headingSlug(title);
            String slug = base;
            int suffix = 0;
            while (used.contains(slug)) {
                suffix++;
                slug = base + "-" + suffix;
            }
            used.add(slug);
            result.add(slug);
        }
        return result;
    }

    private static String headingSlug(String title) {
        String value = INLINE_LINK_TEXT.matcher(title).replaceAll("$1");
        value = REFERENCE_LINK_TEXT.matcher(value).replaceAll("$1");
        value = HTML_TAG.matcher(value).replaceAll("");
        value = HtmlEntities.unescape(value).toLowerCase(Locale.ROOT);
        value = BACKSLASH_PUNCT.matcher(value).replaceAll("$1");
        value = value.replace("`", "").replace("*", "").replace("~", "");
        StringBuilder sb = new StringBuilder(value.length());
        value.codePoints().forEach(cp -> {
            if (cp == ' ' || cp == '-' || cp == '_' || isLetterNumberMark(cp)) {
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

    /** 宽容解析文本中的起始标签，收集 id 属性与 a 标签的 name 属性值。 */
    private static Set<String> explicitAnchors(String text) {
        Set<String> values = new HashSet<>();
        int i = 0;
        int length = text.length();
        while (i < length) {
            if (text.charAt(i) != '<') {
                i++;
                continue;
            }
            if (i + 1 < length && isAsciiLetter(text.charAt(i + 1))) {
                i = parseStartTag(text, i, values);
            } else if (i + 1 < length
                    && (text.charAt(i + 1) == '/' || text.charAt(i + 1) == '!' || text.charAt(i + 1) == '?')) {
                int gt = text.indexOf('>', i);
                i = gt < 0 ? length : gt + 1;
            } else {
                // 其余 '<' 按字面文本处理，仅前进一格继续扫描
                i++;
            }
        }
        return values;
    }

    /** 返回解析后继续扫描的位置；未找到闭合 '>' 的残缺标签按原文处理、不采集属性。 */
    private static int parseStartTag(String text, int lt, Set<String> values) {
        int length = text.length();
        int i = lt + 1;
        while (i < length && "\t\n\r\f />\0".indexOf(text.charAt(i)) < 0) {
            i++;
        }
        String tag = text.substring(lt + 1, i).toLowerCase(Locale.ROOT);
        // 先定位标签闭合，未闭合的残缺标签不采集属性（与 HTMLParser 在 EOF 的行为一致）
        int scan = i;
        while (scan < length && text.charAt(scan) != '>') {
            if ((text.charAt(scan) == '"' || text.charAt(scan) == '\'')) {
                char quote = text.charAt(scan);
                int close = text.indexOf(quote, scan + 1);
                if (close < 0) {
                    return length;
                }
                scan = close + 1;
            } else {
                scan++;
            }
        }
        if (scan >= length) {
            return length;
        }
        while (i < scan) {
            char c = text.charAt(i);
            if (htmlSpace(c) || c == '/') {
                i++;
                continue;
            }
            int nameStart = i;
            while (i < scan && "\t\n\r\f /=>".indexOf(text.charAt(i)) < 0) {
                i++;
            }
            String name = text.substring(nameStart, i).toLowerCase(Locale.ROOT);
            int j = i;
            while (j < scan && htmlSpace(text.charAt(j))) {
                j++;
            }
            String value = null;
            if (j < scan && text.charAt(j) == '=') {
                j++;
                while (j < scan && htmlSpace(text.charAt(j))) {
                    j++;
                }
                if (j < scan && (text.charAt(j) == '"' || text.charAt(j) == '\'')) {
                    char quote = text.charAt(j);
                    int close = text.indexOf(quote, j + 1);
                    if (close < 0 || close >= scan) {
                        value = HtmlEntities.unescape(text.substring(j + 1, scan));
                        i = scan;
                    } else {
                        value = HtmlEntities.unescape(text.substring(j + 1, close));
                        i = close + 1;
                    }
                } else {
                    int valueStart = j;
                    while (j < scan && !htmlSpace(text.charAt(j)) && text.charAt(j) != '>') {
                        j++;
                    }
                    value = HtmlEntities.unescape(text.substring(valueStart, j));
                    i = j;
                }
            }
            if (value != null && (name.equals("id") || (tag.equals("a") && name.equals("name")))) {
                values.add(value);
            }
        }
        return scan + 1;
    }

    private static boolean htmlSpace(char c) {
        return c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\u000C' || c == '\u000B';
    }

    private static boolean isAsciiLetter(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    // ===== 链接抽取（行内 + 引用定义 + 目标解析，偏移基于遮蔽后仍保长度的文本） =====

    private record LinkRef(int offset, String value) {}

    /** 行内链接目标解析结果：目标文本与消费结束的绝对偏移。 */
    private record Dest(String value, int end) {}

    private static List<LinkRef> links(String text) {
        String masked = maskCode(text, true);
        Map<String, String> references = new HashMap<>();
        Matcher definition = REFERENCE_DEFINITION.matcher(masked);
        while (definition.find()) {
            Dest dest = destination(masked, definition.end());
            if (dest != null) {
                references.putIfAbsent(labelKey(definition.group(1)), dest.value());
            }
        }
        masked = blankMatches(masked, REFERENCE_DEFINITION_LINE);
        List<LinkRef> result = new ArrayList<>();
        int length = masked.length();
        int pos = 0;
        while (true) {
            int start = nextUnescapedBracket(masked, pos);
            if (start < 0) {
                break;
            }
            int close = scanBracketContent(masked, start + 1);
            if (close < 0) {
                // 该 '[' 无法闭合：继续尝试其后继 '['，等价正则回溯后继续搜索
                pos = start + 1;
                continue;
            }
            int end = close + 1;
            if (end < length && masked.charAt(end) == '(') {
                Dest dest = destination(masked, end + 1);
                if (dest != null) {
                    Matcher suffix = LINK_SUFFIX.matcher(masked);
                    suffix.region(dest.end(), length);
                    if (suffix.lookingAt()) {
                        // region 之下 end() 已是绝对索引，等价 Python 的 tail + suffix.end()
                        pos = suffix.end();
                        result.add(new LinkRef(start, dest.value()));
                        continue;
                    }
                }
                pos = end;
            } else {
                String key;
                Matcher collapsed = COLLAPSED_REFERENCE.matcher(masked);
                if (end < length && collapsed.region(end, length).lookingAt()) {
                    String label = collapsed.group(1);
                    key = labelKey(label.isEmpty() ? masked.substring(start + 1, close) : label);
                    pos = collapsed.end();
                } else {
                    key = labelKey(masked.substring(start + 1, close));
                    pos = end;
                }
                String value = references.get(key);
                if (value != null) {
                    result.add(new LinkRef(start, value));
                }
            }
        }
        return result;
    }

    /** 下一个前导字符不是反斜杠的 '['，等价 LINK 模式的前向断言限定。 */
    private static int nextUnescapedBracket(String text, int from) {
        int candidate = text.indexOf('[', from);
        while (candidate > 0 && text.charAt(candidate - 1) == '\\') {
            candidate = text.indexOf('[', candidate + 1);
        }
        return candidate;
    }

    /**
     * 扫描 '[' 之后的内容直到闭合 ']'，等价 LINK 模式的内容分组：反斜杠转义对跳过
     * （转义的 {@code .} 不含换行），嵌套括号对须同行闭合；返回闭合 ']' 下标或 -1。
     */
    private static int scanBracketContent(String text, int start) {
        int i = start;
        int length = text.length();
        while (i < length) {
            char c = text.charAt(i);
            if (c == '\\') {
                if (i + 1 < length && text.charAt(i + 1) != '\n') {
                    i += 2;
                    continue;
                }
                return -1;
            }
            if (c == ']') {
                return i;
            }
            if (c == '[') {
                int j = i + 1;
                while (j < length && text.charAt(j) != ']' && text.charAt(j) != '\n') {
                    j++;
                }
                if (j < length && text.charAt(j) == ']') {
                    i = j + 1;
                    continue;
                }
                return -1;
            }
            i++;
        }
        return -1;
    }

    private static String blankMatches(String text, Pattern pattern) {
        Matcher m = pattern.matcher(text);
        StringBuilder sb = new StringBuilder(text.length());
        int last = 0;
        while (m.find()) {
            sb.append(text, last, m.start());
            sb.append(" ".repeat(m.group().length()));
            last = m.end();
        }
        sb.append(text, last, text.length());
        return sb.toString();
    }

    private static String labelKey(String label) {
        List<String> tokens = new ArrayList<>();
        int i = 0;
        int length = label.length();
        while (i < length) {
            while (i < length && pySpace(label.charAt(i))) {
                i++;
            }
            int start = i;
            while (i < length && !pySpace(label.charAt(i))) {
                i++;
            }
            if (i > start) {
                tokens.add(label.substring(start, i));
            }
        }
        return String.join(" ", tokens).toLowerCase(Locale.ROOT);
    }

    /** 解析行内链接目标，返回目标文本与消费结束偏移；无目标时返回 null。 */
    private static Dest destination(String text, int start) {
        int pos = start;
        int length = text.length();
        while (pos < length && pySpace(text.charAt(pos))) {
            pos++;
        }
        if (pos >= length) {
            return null;
        }
        if (text.charAt(pos) == '<') {
            // 手工扫描等价 <((?:\\.|[^<>\n])*)>：内容不含裸 <、>、换行，反斜杠对可转义 > 与 <
            int i = pos + 1;
            while (i < length) {
                char c = text.charAt(i);
                if (c == '\\') {
                    if (i + 1 < length && text.charAt(i + 1) != '\n') {
                        i += 2;
                        continue;
                    }
                    return null;
                }
                if (c == '>') {
                    return new Dest(text.substring(pos + 1, i), i + 1);
                }
                if (c == '<' || c == '\n') {
                    return null;
                }
                i++;
            }
            return null;
        }
        int valueStart = pos;
        int depth = 0;
        while (pos < length) {
            char c = text.charAt(pos);
            if (c == '\\' && pos + 1 < length) {
                pos += 2;
                continue;
            }
            if (pySpace(c) || (c == ')' && depth == 0)) {
                break;
            }
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            }
            pos++;
        }
        if (depth != 0) {
            return null;
        }
        return new Dest(text.substring(valueStart, pos), pos);
    }

    /** Python str.isspace：空白分隔符与 U+00A0 等不间断空格均视为空白。 */
    private static boolean pySpace(char c) {
        if (c == ' '
                || c == '\t'
                || c == '\n'
                || c == '\r'
                || c == '\u000B'
                || c == '\u000C'
                || c == '\u001C'
                || c == '\u001D'
                || c == '\u001E'
                || c == '\u001F'
                || c == '\u0085') {
            return true;
        }
        return Character.getType(c) == Character.SPACE_SEPARATOR;
    }

    // ===== 目标路径解析（反斜杠/HTML 实体解码、scheme 过滤、百分号解码、绝对化） =====

    private static Path targetPath(Path source, String destination) {
        String value = HtmlEntities.unescape(BACKSLASH_DEST.matcher(destination).replaceAll("$1"));
        if (value.isEmpty() || value.startsWith("//")) {
            return null;
        }
        UrlSplit uri = urlSplit(value);
        if (!uri.scheme().isEmpty() && !uri.scheme().equals("file")) {
            return null;
        }
        if (uri.scheme().equals("file")
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
                if (!isAsciiLetter(c) && !(c >= '0' && c <= '9') && c != '+' && c != '-' && c != '.') {
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
        if (rest.startsWith("//")) {
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
        if (s.indexOf('%') < 0) {
            return s;
        }
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream(s.length());
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '%' && i + 2 < s.length()) {
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
        if (c >= '0' && c <= '9') {
            return c - '0';
        }
        if (c >= 'a' && c <= 'f') {
            return c - 'a' + 10;
        }
        if (c >= 'A' && c <= 'F') {
            return c - 'A' + 10;
        }
        return -1;
    }

    private record CheckedLink(Path source, int line, String value, Path target) {}
}
