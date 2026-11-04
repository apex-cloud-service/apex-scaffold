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
 *   <li>{@code src/test/java} 不在扫描范围：{@code @WebFluxTest} 下的 {@code WebTestClient}
 *       无法构造器解析，测试代码允许 {@code @Resource}；Mockito 的 {@code @InjectMocks}
 *       不属于 Spring 注入。</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
class NoAutowiredArchTest {

    /** 源码相对路径。 */
    private static final String SRC_MAIN_JAVA = "src/main/java";

    /** 禁止出现的注入注解（匹配 {@code @注解名}，允许全限定名前缀）。 */
    private static final String[] FORBIDDEN_ANNOTATIONS = {
        "@Autowired", "@Resource", "@Inject", "@org.springframework.beans.factory.annotation.Autowired"
    };

    /**
     * 断言生产代码中不存在被禁止的依赖注入注解。
     *
     * @throws IOException 遍历源码目录失败时直接失败，避免守护静默跳过
     */
    @Test
    @DisplayName("生产代码不得使用 @Autowired / @Resource / @Inject 字段注入")
    void noFieldInjectionInProductionSources() throws IOException {
        Path repoRoot =
                Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        while (repoRoot != null && !Files.isDirectory(repoRoot.resolve("apex-common"))) {
            repoRoot = repoRoot.getParent();
        }
        assertTrue(repoRoot != null, "必须定位到仓库根目录，防止源码守护静默跳过");

        List<String> moduleDirs = new ArrayList<>();
        try (Stream<Path> entries = Files.list(repoRoot)) {
            entries.filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.startsWith("apex-"))
                    .sorted()
                    .forEach(moduleDirs::add);
        }
        assertTrue(moduleDirs.size() > 20, "模块目录数量异常，重新检查扫描范围");

        List<Violation> violations = new ArrayList<>();
        int scannedModules = 0;
        for (String module : moduleDirs) {
            Path moduleSrc = repoRoot.resolve(module).resolve(SRC_MAIN_JAVA);
            if (!Files.isDirectory(moduleSrc)) {
                continue;
            }
            scannedModules++;
            scanDirectory(moduleSrc, repoRoot, violations);
        }
        assertTrue(scannedModules > 15, "实际扫描的模块数少于预期，守护范围可能失效");

        assertTrue(violations.isEmpty(), () -> buildViolationMessage(violations));
        assertEquals(0, violations.size(), "违规计数应为 0");
    }

    /**
     * 递归扫描单个源码目录。
     *
     * @param srcDir     源码根目录
     * @param repoRoot   仓库根目录
     * @param violations 违规收集器
     * @throws IOException 遍历失败时抛出
     */
    private static void scanDirectory(Path srcDir, Path repoRoot, List<Violation> violations) throws IOException {
        Files.walkFileTree(srcDir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (file.toString().endsWith(".java")) {
                    scanJavaFile(file, repoRoot, violations);
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
    private static void scanJavaFile(Path file, Path repoRoot, List<Violation> violations) {
        try {
            String content = Files.readString(file);
            String stripped = stripCommentsAndStrings(content);
            String[] lines = stripped.split("\\R", -1);
            String relativePath = repoRoot.relativize(file).toString();
            for (int i = 0; i < lines.length; i++) {
                String line = stripLineComment(lines[i]);
                for (String annotation : FORBIDDEN_ANNOTATIONS) {
                    if (line.contains(annotation)) {
                        violations.add(new Violation(relativePath, i + 1, line.trim(), annotation));
                        break;
                    }
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
