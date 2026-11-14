package com.apex.scaffold.interfaces.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 仓库 SQL 脚本注释规范的静态契约测试。
 *
 * <p>v2.0 通用化重构后迁移目录仅保留 V1__init_schema.sql 干净基线；
 * 本测试要求所有受版本管理的 SQL 文件在首条可执行语句前提供说明性注释。</p>
 *
 * @author Apex Team
 */
class SqlScriptCommentContractTest {

    /** 历史 SQL 中允许缺少前导注释的例外清单（v2.0 后已清空）。 */
    private static final Set<Path> LEGACY_SQL_WITHOUT_LEADING_COMMENT = Set.of();

    /**
     * 所有受版本管理的 SQL 文件都必须以 SQL 单行注释作为首个非空内容。
     *
     * @throws Exception 遍历或读取文件失败时抛出
     */
    @Test
    void everySqlScriptShouldStartWithDocumentationComment() throws Exception {
        Path root = projectRoot();
        List<Path> sqlFiles;
        try (var paths = Files.walk(root)) {
            sqlFiles = paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".sql"))
                    .filter(path -> !path.toString().contains("/target/"))
                    .filter(path -> !path.toString().contains("/.git/"))
                    .sorted()
                    .toList();
        }

        assertThat(sqlFiles).isNotEmpty();
        // 守护面自检：扫描必须覆盖多个顶层分组目录，防止根定位漂移导致扫描面静默缩水
        long coveredGroups = sqlFiles.stream()
                .map(sqlFile -> root.relativize(sqlFile).getName(0).toString())
                .filter(name -> name.startsWith("apex-"))
                .distinct()
                .count();
        assertThat(coveredGroups)
                .as("SQL 契约扫描应覆盖至少三个顶层分组目录")
                .isGreaterThanOrEqualTo(3);
        for (Path sqlFile : sqlFiles) {
            Path relativePath = root.relativize(sqlFile);
            if (LEGACY_SQL_WITHOUT_LEADING_COMMENT.contains(relativePath)) {
                continue;
            }
            String firstContentLine = Files.readAllLines(sqlFile).stream()
                    .map(String::strip)
                    .filter(line -> !line.isEmpty())
                    .findFirst()
                    .orElse("");
            assertThat(firstContentLine)
                    .as("SQL 文件首个非空行必须是说明性注释: %s", relativePath)
                    .startsWith("--");
        }
    }

    /**
     * 解析项目根目录：从测试工作目录向上查找仓库根独有的 AGENTS.md 标记
     * （第二阶段嵌套布局下父目录是分组父，不能按一级父目录推断仓库根）。
     *
     * @return 聚合工程根目录
     */
    private Path projectRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        while (current != null && !Files.exists(current.resolve("AGENTS.md"))) {
            current = current.getParent();
        }
        assertThat(current).as("未找到仓库根（AGENTS.md 标记）").isNotNull();
        return current;
    }
}
