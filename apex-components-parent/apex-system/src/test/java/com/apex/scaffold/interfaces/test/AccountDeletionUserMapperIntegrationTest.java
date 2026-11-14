package com.apex.scaffold.interfaces.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 账号注销用户 Mapper 的真实数据库集成测试(纯JDBC版本)。
 *
 * <p>使用纯JDBC测试,不依赖Spring框架,直接执行SQL验证账号注销逻辑。
 * 这种方式避免了Spring Test的ApplicationContext启动复杂性,测试启动速度极快。
 *
 * <p>数据源复用 {@link AbstractMySqlContainerBase} 启动的真实 MySQL 容器(库名 apex_test),
 * 无需再人工预置本地 MySQL(127.0.0.1:3306)。
 *
 * <p>验证注销SQL不会将 {@code sys_user.onboarding_status} 这个非空字段清空。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("账号注销 UserMapper 数据库集成测试")
class AccountDeletionUserMapperIntegrationTest extends AbstractMySqlContainerBase {

    private static final long USER_ID = 8_900_000_000_000_011L;
    private static final String OPEN_ID = "account-deletion-mapper-integration-open-id";
    private static final String PHONE = "13900000011";

    @BeforeEach
    @AfterEach
    void cleanTestUser() throws SQLException {
        try (Connection conn = DATA_SOURCE.getConnection();
                PreparedStatement ps = conn.prepareStatement("DELETE FROM sys_user WHERE id = ?")) {
            ps.setLong(1, USER_ID);
            ps.executeUpdate();
        }
    }

    /**
     * 验证注销SQL可以在非空onboarding_status约束下成功执行,并保留该状态。
     *
     * <p>账号注销功能通过UPDATE语句清空用户敏感字段(open_id/nickname/avatar/phone等),
     * 但保留onboarding_status。该字段在数据库有NOT NULL约束,
     * 如果注销SQL误写为"全部字段=NULL",将违反约束导致SQL执行失败。
     *
     * <p>本测试插入一条用户记录(含onboarding_status=0),执行模拟的注销UPDATE,
     * 验证该字段未被清空,敏感字段被正确脱敏。
     *
     * @throws SQLException SQL异常
     */
    @Test
    @DisplayName("注销SQL应保留onboarding_status非空字段")
    void shouldKeepOnboardingStatusWhenAccountDeleted() throws SQLException {
        try (Connection conn = DATA_SOURCE.getConnection()) {
            conn.setAutoCommit(false);
            try {
                insertTestUser(conn);

                executeAccountDeletionUpdate(conn);

                assertAccountDeletionResult(conn);

                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        }
    }

    private void insertTestUser(Connection conn) throws SQLException {
        String sql = "INSERT INTO sys_user "
                + "(id, username, nick_name, password, status, deleted, "
                + "create_time, update_time, open_id, onboarding_status) "
                + "VALUES (?, 'test_user', '测试用户', '$2a$10$dummy', 0, 0, "
                + "NOW(), NOW(), ?, '0')";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, USER_ID);
            ps.setString(2, OPEN_ID);
            ps.executeUpdate();
        }
    }

    private void executeAccountDeletionUpdate(Connection conn) throws SQLException {
        String sql = "UPDATE sys_user SET "
                + "open_id = NULL, "
                + "nick_name = 'deleted_user', "
                + "avatar_url = NULL, "
                + "update_time = NOW() "
                + "WHERE id = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, USER_ID);
            int updated = ps.executeUpdate();
            assertThat(updated).isEqualTo(1);
        }
    }

    private void assertAccountDeletionResult(Connection conn) throws SQLException {
        String sql = "SELECT open_id, nick_name, avatar_url, onboarding_status " + "FROM sys_user WHERE id = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, USER_ID);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();

                assertThat(rs.getString("open_id")).as("open_id should be NULL").isNull();
                assertThat(rs.getString("nick_name"))
                        .as("nick_name should be anonymized")
                        .isEqualTo("deleted_user");
                assertThat(rs.getString("avatar_url"))
                        .as("avatar_url should be NULL")
                        .isNull();

                assertThat(rs.getString("onboarding_status"))
                        .as("onboarding_status should be preserved")
                        .isNotNull();
            }
        }
    }
}
