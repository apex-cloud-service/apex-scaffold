package com.apex.scaffold.interfaces.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * 照片举报审核迁移的去重、聚合与委托权限契约测试。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
class PhotoReportMigrationContractTest {

    /**
     * 验证 V77 定义举报去重唯一键、聚合计数列，并声明委托给审核相关角色与
     * 权限标识。
     */
    @Test
    void migrationDefinesDeduplicationAggregationAndDelegatedPermissions() throws Exception {
        String sql;
        try (var stream = getClass().getResourceAsStream("/db/migration/V77__create_photo_report_review.sql")) {
            assertThat(stream).isNotNull();
            sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertThat(sql)
                .contains(
                        "uk_photo_report_reporter_photo",
                        "report_count INT NOT NULL DEFAULT 1",
                        "PHOTO_REVIEWER",
                        "REVIEW_MANAGER",
                        "SUPER_ADMIN",
                        "photo:report:read",
                        "photo:report:claim",
                        "photo:report:decide");
    }
}
