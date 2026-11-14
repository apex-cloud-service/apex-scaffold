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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 架构守护测试：禁止生产源码中直接调用 {@code Schedulers.boundedElastic()}。
 *
 * <p>本测试作为 Step 1-4 阻塞 I/O 专用池迁移的回归门禁，一旦出现新的
 * 直接调用 {@code Schedulers.boundedElastic()} 或其全限定名
 * {@code reactor.core.scheduler.Schedulers.boundedElastic()} 就快速失败，
 * 防止开发者绕开六大专用池（jdbc/cache/storage/event/remote/general）
 * 回归到 Reactor 全局共享线程池，造成慢依赖互相挤占的性能风险。
 *
 * <h2>放行规则</h2>
 * <ul>
 *   <li>允许在 Javadoc 注释、行注释（//...）与块注释（/* ... *&#47;）
 *       中出现 {@code boundedElastic} 字样——本测试在扫描前先剥离全部注释。</li>
 *   <li>允许在 Java 字符串常量（双引号内容）中出现，例如用于日志、错误消息、
 *       动态反射等场景——扫描前先剥离字符串字面量内容。</li>
 *   <li>{@code src/test/java} 目录下的测试代码不受约束（本测试不扫描该目录）。</li>
 * </ul>
 *
 * <h2>实现说明</h2>
 * <p>不引入 ArchUnit 依赖，使用 JDK 原生 {@code java.nio.file.Files.walk}
 * 遍历源码文件，手工实现注释剥离与正则匹配，避免与项目现有 ArchUnit 版本冲突。
 * 违规结果以「文件相对路径:行号」的形式收集，断言失败时打印全部违例行，
 * 便于代码评审时快速定位。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
class NoBoundedElasticArchGuardTest {

    /** 需要扫描的业务模块两级路径（第二阶段嵌套布局：<分组父>/<artifactId>）。 */
    private static final String[] MODULES = {
        "apex-shared-parent/apex-common",
        "apex-shared-parent/apex-domain",
        "apex-shared-parent/apex-infrastructure-persistence",
        "apex-shared-parent/apex-infrastructure-cache",
        "apex-shared-parent/apex-infrastructure-messaging",
        "apex-shared-parent/apex-infrastructure-remote",
        "apex-shared-parent/apex-infrastructure-integration",
        "apex-components-parent/apex-system"
    };

    /** 每个模块下源码所在的相对路径。 */
    private static final String SRC_MAIN_JAVA = "src/main/java";

    /**
     * 禁止直接调用 boundedElastic 的正则。
     *
     * <p>匹配形如：
     * <ul>
     *   <li>{@code Schedulers.boundedElastic(}</li>
     *   <li>{@code Schedulers  .  boundedElastic (}</li>
     *   <li>{@code reactor.core.scheduler.Schedulers.boundedElastic(}</li>
     * </ul>
     * <p>在注释与字符串被剥离后，任何剩余匹配均视为真实调用。
     */
    private static final Pattern BOUNDED_ELASTIC_CALL = Pattern.compile("Schedulers\\s*\\.\\s*boundedElastic\\s*\\(");

    /**
     * 检查共享能力及 system 模块 src/main/java 下不再存在真实的 boundedElastic() 调用。
     *
     * <p>遍历流程：
     * <ol>
     *   <li>从 {@code System.getProperty("user.dir")} 向上定位仓库根目录。</li>
     *   <li>对所有受约束模块拼接 {@code <module>/src/main/java} 并验证路径存在。</li>
     *   <li>对每个存在的源码目录使用 {@code Files.walkFileTree} 递归扫描所有
     *       {@code .java} 源文件。</li>
     *   <li>对每个源文件先按「块注释 → 字符串字面量 → 行注释」顺序剥离安全文本，
     *       再逐行用正则检测真实调用；命中则收集「相对路径:行号」到违规列表。</li>
     *   <li>最后断言违规列表为空；如存在违规，在断言消息中逐行打印全部条目，
     *       配合 CI 日志一眼定位。</li>
     * </ol>
     *
     * @throws IOException 若读取源文件或遍历目录发生 I/O 异常，测试直接失败以暴露问题
     */
    @Test
    @DisplayName("生产代码不得直接调用 Schedulers.boundedElastic()")
    void noDirectBoundedElasticCallsInProductionSources() throws IOException {
        Path repoRoot =
                Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        while (repoRoot != null && !Files.exists(repoRoot.resolve("AGENTS.md"))) {
            repoRoot = repoRoot.getParent();
        }
        assertTrue(repoRoot != null, "必须定位到仓库根目录（AGENTS.md 标记），防止源码守护静默跳过");
        List<Violation> violations = new ArrayList<>();

        for (String module : MODULES) {
            Path moduleSrc = repoRoot.resolve(module).resolve(SRC_MAIN_JAVA);
            // 受约束模块目录缺失即失败，防止布局漂移时守护面静默缩水
            assertTrue(Files.isDirectory(moduleSrc), "受约束模块源码目录必须存在: " + module);
            scanDirectory(moduleSrc, repoRoot, violations);
        }

        assertTrue(violations.isEmpty(), () -> buildViolationMessage(violations));
        assertEquals(0, violations.size(), "违规计数应为 0");
    }

    /**
     * 递归扫描单个源码目录，处理每个 .java 文件并填充违规列表。
     *
     * @param srcDir     源码根目录（某个模块的 src/main/java）
     * @param repoRoot   仓库根目录，用于计算文件相对路径
     * @param violations 违规收集器，本方法直接向其追加命中项
     * @throws IOException 若 walkFileTree 过程抛出 I/O 异常
     */
    private static void scanDirectory(Path srcDir, Path repoRoot, List<Violation> violations) throws IOException {
        Files.walkFileTree(srcDir, new SimpleFileVisitor<Path>() {
            /**
             * 测试 visitFile。
             * @param file 参数 file（类型 Path）
             * @param attrs 参数 attrs（类型 BasicFileAttributes）
             * @return 返回 FileVisitResult 类型结果
             */
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (file.toString().endsWith(".java")) {
                    scanJavaFile(file, repoRoot, violations);
                }
                return FileVisitResult.CONTINUE;
            }

            /**
             * 测试 visitFileFailed。
             * @param file 参数 file（类型 Path）
             * @param exc 参数 exc（类型 IOException）
             * @return 返回 FileVisitResult 类型结果
             */
            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * 扫描单个 .java 文件：剥离注释与字符串后检查 boundedElastic 调用。
     *
     * <p>处理顺序：先读取全部字符，一次性剥离块注释与字符串字面量得到「净代码」，
     * 再按行分割剥离行注释并逐行进行正则匹配。这样可以正确处理跨多行的块注释，
     * 同时避免字符串中的伪命中。
     *
     * @param file       源文件路径
     * @param repoRoot   仓库根目录（用于生成相对路径）
     * @param violations 违规收集器
     */
    private static void scanJavaFile(Path file, Path repoRoot, List<Violation> violations) {
        try {
            String content = Files.readString(file);
            String stripped = stripBlockCommentsAndStrings(content);
            String[] lines = stripped.split("\\R", -1);
            String relativePath = repoRoot.relativize(file).toString();

            for (int i = 0; i < lines.length; i++) {
                String line = stripLineComment(lines[i]);
                Matcher matcher = BOUNDED_ELASTIC_CALL.matcher(line);
                if (matcher.find()) {
                    violations.add(new Violation(relativePath, i + 1, line.trim()));
                }
            }
        } catch (IOException ignore) {
            // 单个文件读取失败不中断整体扫描，避免权限问题导致测试无意义失败
        }
    }

    /**
     * 从源码全文中一次性剥离块注释（/* ... *&#47;）与双引号字符串字面量。
     *
     * <p>使用状态机逐字符扫描：
     * <ul>
     *   <li>正常状态遇到 {@code /*} 进入块注释；遇到 {@code "} 进入字符串；
     *       遇到其他字符原样保留。</li>
     *   <li>块注释内仅寻找结束标记 {@code *&#47;}，其余字符丢弃。</li>
     *   <li>字符串内处理转义 {@code \"}，寻找结束双引号，中间内容全部替换为占位空格，
     *       保持行号结构不变。</li>
     * </ul>
     *
     * @param source 原始源码全文
     * @return 去除块注释与字符串字面量内容后的文本（行号不变）
     */
    static String stripBlockCommentsAndStrings(String source) {
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
                    if (cc == '\n' || cc == '\r') {
                        out.append(cc);
                    } else {
                        out.append(' ');
                    }
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
                        if (cc == '\n' || cc == '\r') {
                            out.append(cc);
                        } else {
                            out.append(' ');
                        }
                        i++;
                    }
                }
            } else if (c == '\'') {
                out.append(c);
                i++;
                while (i < len) {
                    char cc = source.charAt(i);
                    if (cc == '\\' && i + 1 < len) {
                        out.append(source.charAt(i)).append(source.charAt(i + 1));
                        i += 2;
                    } else {
                        out.append(cc);
                        i++;
                        if (cc == '\'') {
                            break;
                        }
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
     * 从单行代码中剥离行注释（{@code //} 之后的内容全部丢弃）。
     *
     * <p>由于进入本方法前块注释与字符串字面量已被剥离，此处简单匹配
     * 首次出现的 {@code //} 即可，无需处理字符串内部的伪注释符号。
     *
     * @param line 单行源码（不含块注释与字符串）
     * @return 去除行注释后的净代码行
     */
    static String stripLineComment(String line) {
        int idx = line.indexOf("//");
        return idx < 0 ? line : line.substring(0, idx);
    }

    /**
     * 构造断言失败时展示的完整违规消息（每行一条，附带相对路径与行号）。
     *
     * @param violations 已收集的违规列表
     * @return 格式化后的多行错误文本，供 CI 日志直接阅读
     */
    private static String buildViolationMessage(List<Violation> violations) {
        StringBuilder sb = new StringBuilder();
        sb.append("发现 ")
                .append(violations.size())
                .append(" 处直接调用 Schedulers.boundedElastic() 的生产代码，")
                .append("请改用 BlockingIoSchedulers 六大专用池（jdbc/cache/storage/event/remote/general）：")
                .append(System.lineSeparator());
        for (Violation v : violations) {
            sb.append("  - ")
                    .append(v.relativePath)
                    .append(':')
                    .append(v.lineNumber)
                    .append("  |  ")
                    .append(v.snippet)
                    .append(System.lineSeparator());
        }
        return sb.toString();
    }

    /**
     * 单次违规记录：文件相对路径、行号以及命中行的前后文片段。
     *
     * <p>使用 record 便于直接在违规列表中存储结构化信息，
     * {@link #buildViolationMessage(List)} 最终将其格式化为可读文本。
     *
     * @param relativePath 源文件相对于仓库根的路径
     * @param lineNumber   命中的行号（1-based）
     * @param snippet      命中行去除前后空白的代码片段
     * @author Apex Team
     * @version 1.0.0
     * @since 1.0.0
     */
    private record Violation(String relativePath, int lineNumber, String snippet) {}
}
