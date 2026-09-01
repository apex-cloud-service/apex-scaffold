package com.apex.scaffold.interfaces.test;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.Application;
import com.apex.scaffold.infrastructure.persistence.mapper.UserMapper;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * 账号注销用户 Mapper 的真实数据库集成测试。
 *
 * <p>通过 it Profile 的 MySQL 执行实际 {@link UserMapper} 注解 SQL，
 * 验证注销不会将 {@code sys_user.onboarding_status} 这个非空字段清空。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@SpringBootTest(
        classes = Application.class,
        webEnvironment = WebEnvironment.NONE,
        properties = {
            "apex.scheduling.enabled=false",
            "apex.correct-rate.preload.enabled=false",
            "apex.redis.cleanup-on-startup=false"
        })
@ActiveProfiles("it")
@Import(ItProfileMockRedisConfiguration.class)
@DisplayName("账号注销 UserMapper 数据库集成测试")
class AccountDeletionUserMapperIntegrationTest {

    private static final long USER_ID = 8_900_000_000_000_011L;
    private static final String OPEN_ID = "account-deletion-mapper-integration-open-id";
    private static final String PHONE = "13900000011";

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 清理本测试使用的隔离用户，避免重复执行污染测试库数据。 */
    @BeforeEach
    @AfterEach
    void cleanTestUser() {
        jdbcTemplate.update("DELETE FROM sys_user WHERE id = ?", USER_ID);
    }

    /**
     * 验证注销 SQL 可以在非空 onboarding_status 约束下成功执行，并保留该状态。
     */
    @Test
    @DisplayName("注销不应将非空 onboarding_status 置为 NULL")
    void shouldDeactivateUserWithoutNullingOnboardingStatus() {
        jdbcTemplate.update(
                "INSERT INTO sys_user "
                        + "(id, open_id, phone, nick_name, status, onboarding_status, "
                        + "create_time, update_time, version, deleted) "
                        + "VALUES (?, ?, ?, ?, 1, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0)",
                USER_ID,
                OPEN_ID,
                PHONE,
                "注销集成测试用户",
                "FACE_PROFILE_UPLOADED");

        int affectedRows = userMapper.deactivateForAccountDeletion(USER_ID);

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT status, deleted, open_id, phone, onboarding_status " + "FROM sys_user WHERE id = ?", USER_ID);

        assertThat(affectedRows).isOne();
        assertThat(((Number) row.get("status")).intValue()).isZero();
        assertThat(((Number) row.get("deleted")).intValue()).isOne();
        assertThat(row.get("open_id")).isNull();
        assertThat(row.get("phone")).isNull();
        assertThat(row.get("onboarding_status")).isEqualTo("FACE_PROFILE_UPLOADED");
    }
}
