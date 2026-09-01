package com.apex.scaffold.interfaces.test;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.baomidou.dynamic.datasource.DynamicRoutingDataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;

/**
 * P6Spy 集成路径验证测试（dynamic-datasource p6spy=true）。
 *
 * <p>dev 环境通过 {@code spring.datasource.dynamic.p6spy=true} 启用 SQL 日志代理
 * （spy.properties + p6spy 3.9.1）。本测试在稳定可启动的 it 上下文上覆盖
 * {@code p6spy=true}，验证 dynamic-datasource 4.3.1 的 P6Spy 包装机制真实可执行：
 * <ul>
 *   <li>数据源为 {@link DynamicRoutingDataSource}（与 dev/prod 同构）</li>
 *   <li>经代理执行 SQL 正常返回（P6Spy 初始化不失败）</li>
 *   <li>p6spy logger 捕获到 SQL 代理日志（证明 SQL 确实走 P6Spy）</li>
 * </ul>
 *
 * <p>默认（不覆盖属性）时 it 仍为 {@code p6spy: false}，不受本测试影响。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 2.2.0
 */
@SpringBootTest(properties = {"spring.datasource.dynamic.p6spy=true"})
@ActiveProfiles("it")
@Import(ItProfileMockRedisConfiguration.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class P6SpyEnabledSmokeTest {

    private final DataSource dataSource;

    /**
     * 构造器注入 DataSource。
     *
     * @param dataSource 数据源
     */
    public P6SpyEnabledSmokeTest(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * 执行 {@code shouldUseDynamicRoutingDataSource} 操作。
     */
    @Test
    void shouldUseDynamicRoutingDataSource() {
        assertThat(dataSource)
                .as("数据源应为 dynamic-datasource（与 dev/prod 同构）")
                .isInstanceOf(DynamicRoutingDataSource.class);
    }

    /**
     * 执行 {@code shouldExecuteSqlThroughP6SpyProxy} 操作。
     *
     * @throws Exception 当操作无法完成时
     */
    @Test
    void shouldExecuteSqlThroughP6SpyProxy() throws Exception {
        try (Connection conn = dataSource.getConnection();
                Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery("SELECT 1 AS one")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1)).isEqualTo(1);
        }
    }

    /**
     * 执行 {@code shouldLogSqlViaP6SpyLogger} 操作。
     *
     * <p>在 p6spy logger 上挂 {@link ListAppender}，执行 SQL 后断言捕获到代理日志，
     * 证明 dev 的 p6spy 配置（spy.properties + dynamic p6spy=true）真实生效。</p>
     *
     * @throws Exception 当操作无法完成时
     */
    @Test
    void shouldLogSqlViaP6SpyLogger() throws Exception {
        Logger p6spyLogger = (Logger) LoggerFactory.getLogger("p6spy");
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        p6spyLogger.addAppender(appender);

        try (Connection conn = dataSource.getConnection();
                Statement st = conn.createStatement()) {
            st.execute("SELECT 1");
        } finally {
            p6spyLogger.detachAppender(appender);
        }

        assertThat(appender.list).as("p6spy logger 应捕获 SQL 代理日志（证明 P6Spy 真实生效）").isNotEmpty();
    }
}
