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
     * 解析从聚合工程或 apex-interfaces 模块运行测试时的项目根目录。
     *
     * @return 聚合工程根目录
     */
    private Path projectRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        if (Files.isDirectory(current.resolve("apex-interfaces"))) {
            return current;
        }
        return current.getParent();
    }
}
