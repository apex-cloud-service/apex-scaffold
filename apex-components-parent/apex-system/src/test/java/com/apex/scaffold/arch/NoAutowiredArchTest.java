package com.apex.scaffold.arch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 架构守护测试：禁止生产源码使用 {@code @Autowired} 与字段级依赖注入。
 *
 * <p>仓库统一采用构造器注入（可配合 {@code @RequiredArgsConstructor}）。字段注入和
 * Setter 注入会隐藏依赖、妨碍不可变性与脱离容器的单元测试，因此本测试作为回归门禁：
 * 一旦新增 {@code @Autowired}、{@code @Resource} 或 {@code @Inject} 就快速失败。
 *
 * <h2>判据</h2>
 * <ul>
 *   <li>扫描全仓 {@code apex-&#42;/src/main/java} 下的所有 Java 源文件。</li>
 *   <li>命中 {@code @Autowired}（含全限定名写法 {@code @org.springframework.beans.factory.annotation.Autowired}）。</li>
 *   <li>命中作为字段/方法注入使用的 {@code @Resource} 与 {@code @Inject}。</li>
 *   <li>注释与字符串字面量在匹配前剥离，避免 Javadoc 中说明性文字造成误报。</li>
 *   <li>生产源码（{@code src/main/java}）与测试源码（{@code src/test/java}）分别扫描：</li>
 *   <li>两段范围均禁止 {@code @Autowired}（含全限定名）。</li>
 *   <li>测试源码额外禁止 {@code @Inject}；{@code @Resource} 仅允许出现在
 *       {@code @WebFluxTest} 切片测试的 {@code WebTestClient} 字段——该上下文无法
 *       构造器解析该 Bean，属框架约束而非代码选择。其余 {@code @Resource} 按违规处理。</li>
 *   <li>Mockito 的 {@code @InjectMocks} 不属于 Spring 注入，不参与匹配。</li>
 *   <li>字段级 {@code @Value} 同样视为字段注入并禁止；构造器参数或 {@code @Bean} 方法参数上的
 *       {@code @Value} 属于受支持的配置注入方式，不参与匹配。</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
class NoAutowiredArchTest {

    /** 生产源码相对路径。 */
    private static final String SRC_MAIN_JAVA = "src/main/java";

    /** 测试源码相对路径。 */
    private static final String SRC_TEST_JAVA = "src/test/java";

    /** 生产源码禁止出现的注入注解（匹配 {@code @注解名}，允许全限定名前缀）。 */
    private static final String[] FORBIDDEN_IN_MAIN = {
        "@Autowired", "@Resource", "@Inject", "@org.springframework.beans.factory.annotation.Autowired"
    };

    /** 测试源码禁止出现的注入注解：{@code @Resource} 另行按白名单场景判定。 */
    private static final String[] FORBIDDEN_IN_TEST = {
        "@Autowired", "@Inject", "@org.springframework.beans.factory.annotation.Autowired"
    };

    /** 字段声明正则：用于识别 {@code @Value} 是否直接标注字段（字段注入）。 */
    private static final java.util.regex.Pattern FIELD_DECLARATION = java.util.regex.Pattern.compile(
            "^\\s*(?:private|protected|public)\\s+(?!static)[\\w<>\\[\\], .?]+\\s+\\w+\\s*(?:;|=)");

    /**
     * 断言生产代码中不存在被禁止的依赖注入注解。
     *
     * @throws IOException 遍历源码目录失败时直接失败，避免守护静默跳过
     */
    @Test
    @DisplayName("生产与测试代码均不得使用 @Autowired（测试额外禁 @Inject）")
    void noFieldInjectionAcrossSources() throws IOException {
        Path repoRoot = locateRepoRoot();
        List<String> moduleDirs = listModuleDirs(repoRoot);
        assertTrue(moduleDirs.size() > 20, "模块目录数量异常，重新检查扫描范围");

        List<Violation> violations = new ArrayList<>();
        int scannedMainModules = 0;
        int scannedTestModules = 0;
        for (String module : moduleDirs) {
            Path mainSrc = repoRoot.resolve(module).resolve(SRC_MAIN_JAVA);
            if (Files.isDirectory(mainSrc)) {
                scannedMainModules++;
                scanDirectory(mainSrc, repoRoot, FORBIDDEN_IN_MAIN, violations);
            }
            Path testSrc = repoRoot.resolve(module).resolve(SRC_TEST_JAVA);
            if (Files.isDirectory(testSrc)) {
                scannedTestModules++;
                scanTestDirectory(testSrc, repoRoot, violations);
            }
        }
        assertTrue(scannedMainModules > 15, "生产源码实际扫描模块数少于预期，守护范围可能失效");
        assertTrue(scannedTestModules > 15, "测试源码实际扫描模块数少于预期，守护范围可能失效");

        assertTrue(violations.isEmpty(), () -> buildViolationMessage(violations));
        assertEquals(0, violations.size(), "违规计数应为 0");
    }

    /** 定位仓库根目录（以仓库根独有的 AGENTS.md 为标记）。 */
    private static Path locateRepoRoot() {
        Path repoRoot =
                Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        while (repoRoot != null && !Files.exists(repoRoot.resolve("AGENTS.md"))) {
            repoRoot = repoRoot.getParent();
        }
        assertTrue(repoRoot != null, "必须定位到仓库根目录（AGENTS.md 标记），防止源码守护静默跳过");
        return repoRoot;
    }

    /** 列出两级嵌套布局下的模块目录（<分组父>/apex-*，第二阶段起叶子不再平铺仓库根）。 */
    private static List<String> listModuleDirs(Path repoRoot) throws IOException {
        List<String> moduleDirs = new ArrayList<>();
        try (Stream<Path> groups = Files.list(repoRoot)) {
            List<Path> groupDirs = groups.filter(Files::isDirectory)
                    .filter(path -> path.getFileName().toString().startsWith("apex-"))
                    .sorted()
                    .toList();
            for (Path group : groupDirs) {
                try (Stream<Path> modules = Files.list(group)) {
                    modules.filter(Files::isDirectory)
                            .filter(path -> path.getFileName().toString().startsWith("apex-"))
                            .sorted()
                            .forEach(module -> moduleDirs.add(group.getFileName()
                                    .resolve(module.getFileName())
                                    .toString()));
                }
            }
        }
        return moduleDirs;
    }

    /**
     * 扫描测试源码目录：禁用 {@code @Autowired}/{@code @Inject}；{@code @Resource} 仅当
     * 所在文件属于 {@code @WebFluxTest} 切片且用于 {@code WebTestClient} 字段时豁免。
     */
    private static void scanTestDirectory(Path testSrc, Path repoRoot, List<Violation> violations) throws IOException {
        Files.walkFileTree(testSrc, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (!file.toString().endsWith(".java")) {
                    return FileVisitResult.CONTINUE;
                }
                String content = Files.readString(file);
                String stripped = stripCommentsAndStrings(content);
                boolean webFluxSlice = stripped.contains("@WebFluxTest");
                String[] lines = stripped.split("\\R", -1);
                String relativePath = repoRoot.relativize(file).toString();
                for (int i = 0; i < lines.length; i++) {
                    String line = stripLineComment(lines[i]);
                    for (String annotation : FORBIDDEN_IN_TEST) {
                        if (matchesAnnotation(line, annotation)) {
                            violations.add(new Violation(relativePath, i + 1, line.trim(), annotation));
                            break;
                        }
                    }
                    if (line.contains("@Resource") && !isAllowedResourceField(webFluxSlice, lines, i)) {
                        violations.add(new Violation(relativePath, i + 1, line.trim(), "@Resource"));
                    }
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * 按注解边界精确匹配：命中文本之后不得紧跟标识符字符。
     *
     * <p>避免 {@code @Inject} 误匹配 Mockito 的 {@code @InjectMocks}（二者语义无关），
     * 也避免 {@code @Autowired} 误匹配自定义的 {@code @AutowiredCandidate} 之类命名。
     *
     * @param line       待匹配代码行
     * @param annotation 注解文本（含 {@code @}）
     * @return 命中时为 true
     */
    private static boolean matchesAnnotation(String line, String annotation) {
        int from = 0;
        while (true) {
            int idx = line.indexOf(annotation, from);
            if (idx < 0) {
                return false;
            }
            int after = idx + annotation.length();
            if (after >= line.length() || !Character.isJavaIdentifierPart(line.charAt(after))) {
                return true;
            }
            from = after;
        }
    }

    /**
     * 判定 {@code @Value} 是否落在字段上（字段注入）。
     *
     * <p>构造器参数与 {@code @Bean} 方法参数中的 {@code @Value} 是受支持的配置注入方式；
     * 仅当注解脱开后紧跟字段声明时判为违规。
     *
     * @param lines 净代码行
     * @param annotationIndex {@code @Value} 所在行索引
     * @return 字段注入时为 true
     */
    private static boolean isFieldLevelValue(String[] lines, int annotationIndex) {
        for (int j = annotationIndex + 1; j < Math.min(annotationIndex + 6, lines.length); j++) {
            String next = stripLineComment(lines[j]).trim();
            if (next.isEmpty() || next.startsWith("@")) {
                continue;
            }
            return FIELD_DECLARATION.matcher(lines[j]).find();
        }
        return false;
    }

    /**
     * 判定 {@code @Resource} 是否属于允许的切片场景：文件为 {@code @WebFluxTest} 且
     * 紧随其后的字段声明为 {@code WebTestClient}。
     */
    private static boolean isAllowedResourceField(boolean webFluxSlice, String[] lines, int annotationIndex) {
        if (!webFluxSlice) {
            return false;
        }
        for (int j = annotationIndex + 1; j < Math.min(annotationIndex + 4, lines.length); j++) {
            String next = stripLineComment(lines[j]).trim();
            if (next.isEmpty() || next.startsWith("@")) {
                continue;
            }
            return next.contains("WebTestClient");
        }
        return false;
    }

    /**
     * 递归扫描单个源码目录。
     *
     * @param srcDir     源码根目录
     * @param repoRoot   仓库根目录
     * @param violations 违规收集器
     * @throws IOException 遍历失败时抛出
     */
    private static void scanDirectory(Path srcDir, Path repoRoot, String[] forbidden, List<Violation> violations)
            throws IOException {
        Files.walkFileTree(srcDir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (file.toString().endsWith(".java")) {
                    scanJavaFile(file, repoRoot, forbidden, violations);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * 扫描单个 Java 源文件，剥离注释与字符串后匹配禁用注解。
     *
     * @param file       源文件
     * @param repoRoot   仓库根目录
     * @param violations 违规收集器
     */
    private static void scanJavaFile(Path file, Path repoRoot, String[] forbidden, List<Violation> violations) {
        try {
            String content = Files.readString(file);
            String stripped = stripCommentsAndStrings(content);
            String[] lines = stripped.split("\\R", -1);
            String relativePath = repoRoot.relativize(file).toString();
            for (int i = 0; i < lines.length; i++) {
                String line = stripLineComment(lines[i]);
                for (String annotation : forbidden) {
                    if (matchesAnnotation(line, annotation)) {
                        violations.add(new Violation(relativePath, i + 1, line.trim(), annotation));
                        break;
                    }
                }
                if (matchesAnnotation(line, "@Value") && isFieldLevelValue(lines, i)) {
                    violations.add(new Violation(relativePath, i + 1, line.trim(), "@Value(字段注入)"));
                }
            }
        } catch (IOException ignored) {
            // 单个文件读取失败不中断整体扫描
        }
    }

    /**
     * 剥离块注释、行注释与双引号字符串内容，保留换行以维持行号。
     *
     * @param source 原始源码
     * @return 净代码文本
     */
    static String stripCommentsAndStrings(String source) {
        StringBuilder out = new StringBuilder(source.length());
        int i = 0;
        int len = source.length();
        while (i < len) {
            char c = source.charAt(i);
            if (c == '/' && i + 1 < len && source.charAt(i + 1) == '*') {
                out.append("  ");
                i += 2;
                while (i < len) {
                    char cc = source.charAt(i);
                    if (cc == '*' && i + 1 < len && source.charAt(i + 1) == '/') {
                        out.append("  ");
                        i += 2;
                        break;
                    }
                    out.append(cc == '\n' || cc == '\r' ? cc : ' ');
                    i++;
                }
            } else if (c == '/' && i + 1 < len && source.charAt(i + 1) == '/') {
                while (i < len && source.charAt(i) != '\n') {
                    out.append(' ');
                    i++;
                }
            } else if (c == '"') {
                out.append('"');
                i++;
                while (i < len) {
                    char cc = source.charAt(i);
                    if (cc == '\\' && i + 1 < len) {
                        out.append("  ");
                        i += 2;
                    } else if (cc == '"') {
                        out.append('"');
                        i++;
                        break;
                    } else {
                        out.append(cc == '\n' || cc == '\r' ? cc : ' ');
                        i++;
                    }
                }
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    /**
     * 从单行代码中剥离行注释。
     *
     * @param line 单行源码
     * @return 净代码行
     */
    static String stripLineComment(String line) {
        int idx = line.indexOf("//");
        return idx < 0 ? line : line.substring(0, idx);
    }

    /**
     * 构造断言失败消息。
     *
     * @param violations 违规列表
     * @return 多行错误文本
     */
    private static String buildViolationMessage(List<Violation> violations) {
        StringBuilder sb = new StringBuilder();
        sb.append("发现 ")
                .append(violations.size())
                .append(" 处被禁止的依赖注入注解，请改为构造器注入")
                .append("（可配合 @RequiredArgsConstructor），并为测试提供静态工厂：")
                .append(System.lineSeparator());
        for (Violation v : violations) {
            sb.append("  - ")
                    .append(v.relativePath())
                    .append(':')
                    .append(v.lineNumber())
                    .append("  |  ")
                    .append(v.annotation())
                    .append("  |  ")
                    .append(v.snippet())
                    .append(System.lineSeparator());
        }
        return sb.toString();
    }

    /**
     * 单次违规记录。
     *
     * @param relativePath 源文件相对仓库根的路径
     * @param lineNumber   行号（1-based）
     * @param snippet      命中行片段
     * @param annotation   命中的注解文本
     */
    private record Violation(String relativePath, int lineNumber, String snippet, String annotation) {}
}
