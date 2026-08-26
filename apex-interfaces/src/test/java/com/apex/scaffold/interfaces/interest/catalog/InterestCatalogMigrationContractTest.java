package com.apex.scaffold.interfaces.interest.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/**
 * V66 兴趣目录 staging 迁移文件的结构契约测试。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("v1.2 目录 staging 迁移契约")
class InterestCatalogMigrationContractTest {

    /**
     * 验证 V66 迁移只创建按 run 隔离的 staging 表，不内嵌直接写业务表的大型 INSERT。
     */
    @Test
    @DisplayName("V66只创建按run隔离的staging表，不写入巨型业务INSERT")
    void shouldDefineStagingTablesWithoutSeedInserts() throws Exception {
        String sql = new String(
                new ClassPathResource("db/migration/V66__create_interest_catalog_staging.sql")
                        .getInputStream()
                        .readAllBytes(),
                StandardCharsets.UTF_8);
        assertThat(sql)
                .contains("biz_interest_catalog_import_run")
                .contains("biz_interest_catalog_node_stage")
                .contains("biz_interest_catalog_alias_stage")
                .contains("biz_interest_catalog_edge_stage")
                .contains("biz_interest_catalog_facet_stage")
                .contains("biz_interest_catalog_release_manifest");
        assertThat(sql.toLowerCase())
                .doesNotContain("insert into `biz_interest_node`")
                .doesNotContain("insert into biz_interest_node");
    }
}
