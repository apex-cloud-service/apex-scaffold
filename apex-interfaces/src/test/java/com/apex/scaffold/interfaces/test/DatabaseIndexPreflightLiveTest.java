package com.apex.scaffold.interfaces.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * V33~V41 索引迁移的目标 MySQL 只读预检与迁移后核验。
 *
 * <p>默认跳过，只有显式设置 {@code -Dapex.index.preflight.enabled=true} 时才连接目标数据库。
 * 设置 {@code -Dapex.index.preflight.phase=post} 时执行迁移后断言。
 *
 * @author Apex Team
 */
@EnabledIfSystemProperty(named = "apex.index.preflight.enabled", matches = "true")
class DatabaseIndexPreflightLiveTest {

    /**
     * 检查表、唯一键数据和拟新增索引名称，不执行任何 DDL/DML。
     *
     * @throws Exception 连接或查询失败时抛出
     */
    @Test
    void targetDatabaseShouldBeReadyForIndexMigrations() throws Exception {
        boolean postflight = "post".equalsIgnoreCase(System.getProperty("apex.index.preflight.phase", "pre"));
        String yaml = Files.readString(projectRoot().resolve("apex-interfaces/src/main/resources/application-dev.yml"));

        try (Connection connection = DriverManager.getConnection(
                property(yaml, "DB_MASTER_URL"),
                property(yaml, "DB_MASTER_USERNAME"),
                property(yaml, "DB_MASTER_PASSWORD"))) {
            connection.setReadOnly(true);

            String databaseIdentity = row(connection, "SELECT DATABASE(),@@hostname,@@version,@@read_only");
            String latestFlyway = row(
                    connection,
                    "SELECT version,description,success FROM flyway_schema_history "
                            + "WHERE success = 1 AND type <> 'DELETE' "
                            + "ORDER BY installed_rank DESC LIMIT 1");
            long duplicateGroups = number(
                    connection,
                    "SELECT COUNT(*) FROM (SELECT user_id,photo_id FROM biz_photo_show_stats "
                            + "GROUP BY user_id,photo_id HAVING COUNT(*)>1) duplicates_found");
            long indexNameConflicts = number(
                    connection,
                    "SELECT COUNT(DISTINCT CONCAT(table_name,':',index_name)) "
                            + "FROM information_schema.statistics "
                            + "WHERE table_schema=DATABASE() AND index_name IN ("
                            + "'idx_outbox_claimed_worker','idx_show_history_user_time_photo',"
                            + "'uk_show_stats_user_photo','idx_show_stats_user_last_time',"
                            + "'idx_elo_log_battle_photo_deleted','idx_elo_log_photo_deleted_time',"
                            + "'idx_vote_user_deleted_time_id','idx_battle_user_status_type_time',"
                            + "'idx_credit_tx_user_time_id','idx_system_message_user_time_id')");
            long callTimeColumns = number(
                    connection,
                    "SELECT COUNT(*) FROM information_schema.columns "
                            + "WHERE table_schema=DATABASE() AND table_name='sys_api_call_log' "
                            + "AND column_name='call_time'");
            long successfulMigrations = number(
                    connection,
                    "SELECT COUNT(*) FROM flyway_schema_history "
                            + "WHERE version IN ('33','34','35','36','37','38','39','40','41') "
                            + "AND success=1");
            long targetTables = number(
                    connection,
                    "SELECT COUNT(*) FROM information_schema.tables "
                            + "WHERE table_schema=DATABASE() AND table_name IN ("
                            + "'sys_api_call_log','sys_domain_event_outbox','biz_photo_show_history',"
                            + "'biz_photo_show_stats','elo_change_log','biz_vote','biz_battle',"
                            + "'biz_credit_tx','biz_system_message')");

            System.out.println("database_identity=" + databaseIdentity);
            System.out.println("latest_flyway=" + latestFlyway);
            System.out.println("show_stats_duplicate_groups=" + duplicateGroups);
            System.out.println("planned_index_name_conflicts=" + indexNameConflicts);
            System.out.println("api_call_log_call_time_columns=" + callTimeColumns);
            System.out.println("successful_v33_v41_migrations=" + successfulMigrations);
            System.out.println("target_table_count=" + targetTables);

            assertThat(duplicateGroups).as("biz_photo_show_stats 重复业务键组数").isZero();
            assertThat(targetTables).as("目标表数量").isEqualTo(9);
            if (postflight) {
                assertThat(flywayVersionAtLeast(latestFlyway, 41))
                        .as("最新 Flyway 版本应不低于 V41（实际: %s）", latestFlyway)
                        .isTrue();
                assertThat(indexNameConflicts).as("已创建计划索引数").isEqualTo(10);
                assertThat(callTimeColumns).as("V33 执行后 call_time 列数").isOne();
                assertThat(successfulMigrations).as("V33~V41 成功迁移数").isEqualTo(9);
            } else {
                assertThat(indexNameConflicts).as("拟新增索引同名冲突数").isZero();
                assertThat(callTimeColumns).as("V33 执行前 call_time 列数").isZero();
                assertThat(successfulMigrations).as("V33~V41 执行前成功迁移数").isZero();
            }
        }
    }

    /**
     * 判断最新 Flyway 行代表的版本是否不低于给定版本。
     *
     * <p>版本号取 {@code version|description|success} 的首段并转为数值比较，
     * 兼容 {@code 6.1}、{@code 7.1} 等非整型历史版本；数据库后续追加新迁移时
     * 该断言仍保持"已达到 V41"的门禁语义，不会因最新版本推进而误报。</p>
     *
     * @param flywayRow      flyway_schema_history 最新行拼接文本
     * @param minimumVersion 门禁要求的最低版本
     * @return 是否达到或超过门禁版本
     */
    private boolean flywayVersionAtLeast(String flywayRow, int minimumVersion) {
        int separator = flywayRow.indexOf('|');
        String version = separator < 0 ? flywayRow : flywayRow.substring(0, separator);
        return Double.parseDouble(version) >= minimumVersion;
    }

    /**
     * 从环境变量或开发配置默认值读取连接属性。
     *
     * @param yaml 配置内容
     * @param name 环境变量名称
     * @return 属性值
     */
    private String property(String yaml, String name) {
        String environmentValue = System.getenv(name);
        if (environmentValue != null && !environmentValue.isBlank()) {
            return environmentValue;
        }
        Matcher matcher = Pattern.compile("\\$\\{" + name + ":([^}]*)}").matcher(yaml);
        if (!matcher.find()) {
            throw new IllegalStateException("缺少数据库配置: " + name);
        }
        return matcher.group(1);
    }

    /**
     * 读取单行单列数值。
     *
     * @param connection 数据库连接
     * @param sql 查询
     * @return 数值
     * @throws Exception 查询失败时抛出
     */
    private long number(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(sql)) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getLong(1);
        }
    }

    /**
     * 将单行查询结果拼接为不含凭据的审计文本。
     *
     * @param connection 数据库连接
     * @param sql 查询
     * @return 审计文本
     * @throws Exception 查询失败时抛出
     */
    private String row(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(sql)) {
            assertThat(resultSet.next()).isTrue();
            StringBuilder value = new StringBuilder();
            for (int column = 1; column <= resultSet.getMetaData().getColumnCount(); column++) {
                if (column > 1) {
                    value.append('|');
                }
                value.append(resultSet.getString(column));
            }
            return value.toString();
        }
    }

    /**
     * 解析聚合工程根目录。
     *
     * @return 项目根目录
     */
    private Path projectRoot() {
        Path current = Path.of(System.getProperty("user.dir"));
        if (Files.isDirectory(current.resolve("apex-interfaces"))) {
            return current;
        }
        return current.getParent();
    }
}
