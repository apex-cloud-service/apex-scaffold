package com.apex.scaffold.interfaces.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * 无需 Docker 的 Flyway 版本链静态检查。
 *
 * <p>通用化重构后迁移目录仅保留干净基线 V1__init_schema.sql 与
 * V2__seed_system_permissions.sql（P0 阶段新增的权限码/菜单种子）。</p>
 *
 * @author Apex Team
 */
class FlywayMigrationVersionTest {

    private static final Pattern VERSION_PATTERN = Pattern.compile("V([0-9_]+)__.+\\.sql");

    /**
     * 执行 {@code shouldHaveUniqueContiguousVersions} 操作。
     * @throws Exception 当操作无法完成时
     */
    @Test
    void shouldHaveUniqueContiguousVersions() throws Exception {
        Path projectRoot = Path.of(System.getProperty("user.dir"));
        Path migrationDirectory = projectRoot.resolve("apex-interfaces/src/main/resources/db/migration");
        if (!Files.isDirectory(migrationDirectory)) {
            migrationDirectory = projectRoot.resolve("src/main/resources/db/migration");
        }

        List<BigDecimal> versions;
        try (var files = Files.list(migrationDirectory)) {
            versions = files.map(path -> path.getFileName().toString())
                    .filter(filename -> VERSION_PATTERN.matcher(filename).matches())
                    .map(this::version)
                    .sorted()
                    .toList();
        }

        // V1 基线 34 表 + V2 权限种子 + V3 账号密码 + V4 审计权限 + V5 在线用户 + V6 部门
        assertThat(versions)
                .containsExactlyElementsOf(
                        List.of(
                                versionNumber("1"),
                                versionNumber("2"),
                                versionNumber("3"),
                                versionNumber("4"),
                                versionNumber("5"),
                                versionNumber("6")));
    }

    /**
     * 从迁移文件名提取可排序的 Flyway 版本。
     *
     * @param filename 迁移文件名
     * @return 规范化版本号
     */
    private BigDecimal version(String filename) {
        Matcher matcher = VERSION_PATTERN.matcher(filename == null ? "" : filename);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("非法 Flyway 文件名: " + filename);
        }
        return versionNumber(matcher.group(1).replace('_', '.'));
    }

    /**
     * 构造用于精确比较的版本号。
     *
     * @param value 点分版本文本
     * @return 十进制版本值
     */
    private BigDecimal versionNumber(String value) {
        return new BigDecimal(value);
    }
}
