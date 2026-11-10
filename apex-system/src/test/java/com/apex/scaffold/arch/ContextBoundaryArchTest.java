package com.apex.scaffold.arch;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 限界上下文边界守卫（方案 M3 / 批次 C0）。
 *
 * <p>固化 {@code docs/02-architecture/context-map.md} 的跨上下文约束：业务上下文之间
 * 不得直接 import 对方的实现包，跨上下文只能经共享 gateway 契约
 * （{@code com.apex.scaffold.domain.gateway}）、api-contract（{@code com.apex.scaffold.contract}）
 * 或 HTTP 防腐层。
 *
 * <p>实现方式：扫描各业务库 main 源码目录的 {@code import} 行。采用源码文本扫描而非
 * ArchUnit 字节码导入，原因是没有 Maven 模块同时依赖多个业务库（跨上下文编译期不可见），
 * 而源码扫描可在单模块测试中覆盖全部上下文。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@DisplayName("限界上下文边界守卫")
class ContextBoundaryArchTest {

    /** 各业务上下文 → 其 main 源码目录（相对仓库根；测试工作目录为模块目录）。 */
    private static final String[][] CONTEXTS = {
        {"system", "apex-system/src/main/java/com/apex/scaffold"},
        {"auth", "apex-auth/src/main/java/com/apex/scaffold/auth"},
        {"workflow", "apex-workflow/src/main/java/com/apex/scaffold/workflow"},
        {"file", "apex-file/src/main/java/com/apex/scaffold/file"},
        {"office", "apex-office/src/main/java/com/apex/scaffold/office"},
        {"integration", "apex-integration/src/main/java/com/apex/scaffold/integration"}
    };

    /**
     * 业务上下文之间不得直接 import 对方实现包。
     *
     * @throws IOException 源码目录读取失败
     */
    @Test
    @DisplayName("业务上下文之间无直接实现依赖")
    void contextsDoNotImportEachOther() throws IOException {
        Path repoRoot = repoRoot();
        List<String> violations = new ArrayList<>();
        for (String[] context : CONTEXTS) {
            String ownName = context[0];
            Path sourceRoot = repoRoot.resolve(context[1]);
            assertThat(sourceRoot).as("业务上下文 %s 的源码目录必须存在", ownName).isDirectory();
            for (String[] other : CONTEXTS) {
                if (other[0].equals(ownName)) {
                    continue;
                }
                String forbidden = "import com.apex.scaffold." + other[0] + ".";
                try (Stream<Path> files = Files.walk(sourceRoot)) {
                    for (Path file :
                            files.filter(p -> p.toString().endsWith(".java")).toList()) {
                        String content = Files.readString(file, StandardCharsets.UTF_8);
                        if (content.contains(forbidden)) {
                            violations.add(ownName + " → " + other[0] + " : " + repoRoot.relativize(file));
                        }
                    }
                }
            }
        }
        assertThat(violations)
                .as("跨上下文只能经 domain/gateway 契约、api-contract 或 HTTP 防腐层（见 context-map.md）")
                .isEmpty();
    }

    /**
     * 定位仓库根目录（测试工作目录为模块目录）。
     *
     * @return 仓库根
     */
    private static Path repoRoot() {
        Path current = Paths.get("").toAbsolutePath();
        while (current != null && !Files.exists(current.resolve("AGENTS.md"))) {
            current = current.getParent();
        }
        assertThat(current).as("未找到仓库根（AGENTS.md）").isNotNull();
        return current;
    }
}
