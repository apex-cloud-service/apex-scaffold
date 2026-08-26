package com.apex.scaffold.interfaces.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;

/**
 * 集成测试启动校验。
 *
 * @author weiqiang.ling
 */
@SpringBootTest
@ActiveProfiles("it")
@Import(ItProfileMockRedisConfiguration.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class FlywayMigrationStartupTest {

    private final DataSource dataSource;

    /**
     * 构造器注入 DataSource。
     *
     * @param dataSource 数据源
     */
    public FlywayMigrationStartupTest(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * 执行 {@code shouldStartWithEmbeddedDependencies} 操作。
     * @throws SQLException 当操作无法完成时
     */
    @Test
    void shouldStartWithEmbeddedDependencies() throws SQLException {
        try (var connection = dataSource.getConnection();
                var rs = connection.getMetaData().getTables(null, null, "sys_user", null)) {
            assertThat(rs.next()).isTrue();
        }
    }
}
