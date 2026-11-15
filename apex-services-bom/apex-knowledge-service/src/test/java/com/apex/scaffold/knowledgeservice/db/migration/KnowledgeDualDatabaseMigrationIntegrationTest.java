package com.apex.scaffold.knowledgeservice.db.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.mysql.cj.jdbc.MysqlDataSource;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 知识库域双库迁移回归（Testcontainers 真实 MySQL 9 + PostgreSQL 15）。
 *
 * <p>校验 V1与新增V2迁移在两方言下均能从空库完成，七张kb_*表、定位快照及清理认领字段与
 * flyway_schema_history 全部就位。默认 Failsafe 门禁包含本测试；
 * 并发容器资源竞争导致的启动失败按 AGENTS.md 口径单模块重跑或降并行复核。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@Tag("integration")
@Testcontainers(disabledWithoutDocker = true)
class KnowledgeDualDatabaseMigrationIntegrationTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:9.0.1");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15");

    /**
     * 对 MySQL 空库执行V1/V2迁移并验证七表与清理定位/认领字段。
     */
    @Test
    void mysqlMigrationCreatesKnowledgeTables() throws Exception {
        MysqlDataSource dataSource = new MysqlDataSource();
        dataSource.setUrl(MYSQL.getJdbcUrl());
        dataSource.setUser(MYSQL.getUsername());
        dataSource.setPassword(MYSQL.getPassword());

        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration/mysql")
                .baselineOnMigrate(true)
                .load();
        flyway.migrate();

        assertThat(jdbcTables(dataSource))
                .containsExactlyInAnyOrder(
                        "kb_space",
                        "kb_document",
                        "kb_chunk",
                        "kb_conversation",
                        "kb_message",
                        "kb_document_work",
                        "kb_document_cleanup",
                        "flyway_schema_history");
        assertCleanupColumns(dataSource);
    }

    /**
     * 对 PostgreSQL 空库执行V1/V2迁移并验证七表与清理定位/认领字段。
     */
    @Test
    void postgresMigrationCreatesKnowledgeTables() {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setUrl(POSTGRES.getJdbcUrl());
        dataSource.setUser(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());

        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration/postgresql")
                .baselineOnMigrate(true)
                .load();
        flyway.migrate();

        assertThat(jdbcTables(dataSource))
                .containsExactlyInAnyOrder(
                        "kb_space",
                        "kb_document",
                        "kb_chunk",
                        "kb_conversation",
                        "kb_message",
                        "kb_document_work",
                        "kb_document_cleanup",
                        "flyway_schema_history");
        assertCleanupColumns(dataSource);
    }

    private void assertCleanupColumns(DataSource dataSource) {
        try (var connection = dataSource.getConnection();
                var statement = connection.createStatement();
                var rs = statement.executeQuery(
                        "SELECT storage_provider,storage_bucket,object_owned,index_endpoint,index_name,index_required"
                                + " FROM kb_document WHERE 1=0")) {
            assertThat(rs.getMetaData().getColumnCount()).isEqualTo(6);
            try (var cleanup = connection.createStatement();
                    var columns = cleanup.executeQuery("SELECT"
                            + " tenant_id,document_id,mode,status,owner,generation,lease_until,attempts,next_retry_at,next_review_at"
                            + " FROM kb_document_cleanup WHERE 1=0")) {
                assertThat(columns.getMetaData().getColumnCount()).isEqualTo(10);
            }
        } catch (Exception error) {
            throw new IllegalStateException("清理凭证迁移字段校验失败", error);
        }
    }

    /**
     * 判断数据源方言（表清单查询按方言选择库函数）。
     *
     * @param dataSource 数据源
     * @return true 表示 PostgreSQL
     */
    private boolean isPostgres(DataSource dataSource) {
        return dataSource instanceof PGSimpleDataSource;
    }

    /**
     * 查询库内全部表名。
     *
     * @param dataSource 数据源
     * @return 表名集合（小写）
     */
    private java.util.Set<String> jdbcTables(DataSource dataSource) {
        java.util.Set<String> tables = new java.util.HashSet<>();
        try (var connection = dataSource.getConnection();
                var statement = connection.createStatement();
                // MySQL 用 DATABASE()，PostgreSQL 用 CURRENT_SCHEMA；两方言各自命中本库
                var rs = statement.executeQuery("SELECT table_name FROM information_schema.tables WHERE table_schema = "
                        + (isPostgres(dataSource) ? "CURRENT_SCHEMA" : "DATABASE()"))) {
            while (rs.next()) {
                tables.add(rs.getString(1).toLowerCase());
            }
        } catch (Exception e) {
            throw new IllegalStateException("表清单查询失败", e);
        }
        return tables;
    }
}
