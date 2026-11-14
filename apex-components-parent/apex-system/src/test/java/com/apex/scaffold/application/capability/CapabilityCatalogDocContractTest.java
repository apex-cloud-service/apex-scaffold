package com.apex.scaffold.application.capability;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 能力目录文档契约测试（方案 D4：文档侧能力目录页与代码侧目录对齐）。
 *
 * <p>D4 要求能力目录"文档侧：docs 能力目录页"与"代码侧 {@link CapabilityCatalog}"
 * 以契约测试对齐：文档能力表引用的能力编码、名称、归属中心、提供组件与开放 scope
 * 必须与代码登记完全一致，任一侧变更未同步即失败，防止文档与实现漂移。
 *
 * <p>文档路径：{@code docs/02-architecture/capability-catalog.md}（相对仓库根）；
 * 测试按模块目录 {@code ../docs/...} 解析（Surefire 工作目录为模块 basedir）。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@DisplayName("能力目录文档契约测试")
class CapabilityCatalogDocContractTest {

    /** 文档相对模块目录的路径（{@code apex-system/../docs/...}）。 */
    private static final Path DOC_PATH = Path.of("..", "docs", "02-architecture", "capability-catalog.md");

    /** 能力清单表头首列（用于跳过表头行）。 */
    private static final String HEADER_FIRST_CELL = "能力编码";

    @Test
    @DisplayName("文档能力表与代码目录完全一致")
    void docTableMatchesCodeCatalog() throws IOException {
        List<String[]> rows = parseCapabilityRows();
        assertThat(rows).as("能力目录文档 %s 必须包含 §1 能力清单表格", DOC_PATH).isNotEmpty();

        List<CapabilityCatalog.Capability> catalog = CapabilityCatalog.all();

        // 编码集合与顺序一致（目录插入顺序即文档展示顺序）
        List<String> docCodes = rows.stream().map(row -> row[0]).toList();
        List<String> codeCodes =
                catalog.stream().map(CapabilityCatalog.Capability::code).toList();
        assertThat(docCodes).as("文档能力编码（含顺序）必须与 CapabilityCatalog 登记一致").containsExactlyElementsOf(codeCodes);

        for (int i = 0; i < catalog.size(); i++) {
            CapabilityCatalog.Capability capability = catalog.get(i);
            String[] row = rows.get(i);
            assertThat(row[1]).as("能力 %s 名称", capability.code()).isEqualTo(capability.name());
            assertThat(row[2]).as("能力 %s 归属中心", capability.code()).isEqualTo(capability.center());
            assertThat(row[3]).as("能力 %s 提供组件", capability.code()).isEqualTo(capability.component());
            assertThat(row[4]).as("能力 %s 负责人", capability.code()).isEqualTo(capability.owner());
            assertThat(row[5]).as("能力 %s 版本", capability.code()).isEqualTo(capability.version());
            assertThat(parseValues(row[6]))
                    .as("能力 %s 消费方", capability.code())
                    .containsExactlyInAnyOrderElementsOf(capability.consumers());
            assertThat(row[7]).as("能力 %s SLO", capability.code()).isEqualTo(capability.slo());
            assertThat(row[8]).as("能力 %s 生命周期", capability.code()).isEqualTo(capability.lifecycle());
            assertThat(parseScopes(row[9]))
                    .as("能力 %s 开放 scope", capability.code())
                    .containsExactlyInAnyOrderElementsOf(capability.openScopes());
            assertThat(row[10]).as("能力 %s 计量维度说明不可为空", capability.code()).isNotBlank();
            assertThat(row[11]).as("能力 %s 说明", capability.code()).isEqualTo(capability.description());
        }
    }

    /**
     * 解析文档 §1 能力清单表格行。
     *
     * @return 每行为能力目录表格列的行列表
     * @throws IOException 文档读取失败
     */
    private List<String[]> parseCapabilityRows() throws IOException {
        assertThat(Files.exists(DOC_PATH))
                .as("能力目录文档必须存在（相对 %s 解析）: %s", Path.of("").toAbsolutePath(), DOC_PATH.toAbsolutePath())
                .isTrue();
        List<String[]> rows = new ArrayList<>();
        for (String line : Files.readAllLines(DOC_PATH)) {
            if (!line.startsWith("|")) {
                continue;
            }
            String[] cells = Arrays.stream(line.split("\\|"))
                    .map(String::trim)
                    .filter(cell -> !cell.isEmpty())
                    .toArray(String[]::new);
            if (cells.length < 12 || HEADER_FIRST_CELL.equals(cells[0]) || cells[0].startsWith("---")) {
                continue;
            }
            rows.add(cells);
        }
        return rows;
    }

    /**
     * 解析逗号分隔列：{@code 无} 表示空集。
     *
     * @param raw 列原文
     * @return 列值集合
     */
    private Set<String> parseValues(String raw) {
        if (raw == null || raw.isBlank() || "无".equals(raw.trim())) {
            return Set.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * 解析 scope 列：{@code 无} 表示空集，否则按逗号拆分。
     *
     * @param raw scope 列原文
     * @return scope 集合
     */
    private Set<String> parseScopes(String raw) {
        if (raw == null || raw.isBlank() || "无".equals(raw.trim())) {
            return Set.of();
        }
        return Arrays.stream(raw.split(","))
                .map(item -> item.trim().toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }
}
