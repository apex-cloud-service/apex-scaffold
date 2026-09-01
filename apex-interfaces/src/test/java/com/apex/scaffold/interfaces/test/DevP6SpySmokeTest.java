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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;

/**
 * dev 环境完整启动 + P6Spy 冒烟测试。
 *
 * <p>验证 dev profile 可完整启动（MySQL + Redis 真实连接），且
 * dynamic-datasource p6spy=true 的 SQL 日志代理真实生效：
 * <ul>
 *   <li>数据源为 {@link DynamicRoutingDataSource}（与 prod 同构）</li>
 *   <li>经代理执行 SQL 正常返回（P6Spy 初始化不失败）</li>
 *   <li>p6spy logger 捕获到 SQL 代理日志（证明 dev 的 P6Spy 真实输出）</li>
 * </ul>
 *
 * <p>dev 环境外部依赖：MySQL（127.0.0.1:3306/apex_data）+ Redis（127.0.0.1:6379）
 * 需可用；RocketMQ/Zipkin/Sentinel 无本地实例，测试中排除其自动配置。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 2.2.0
 */
@SpringBootTest(
        properties = {
            "spring.autoconfigure.exclude="
                    + "org.apache.rocketmq.spring.autoconfigure.RocketMQAutoConfiguration,"
                    + "org.springframework.boot.actuate.autoconfigure.tracing.zipkin.ZipkinAutoConfiguration,"
                    + "com.alibaba.csp.sentinel.adapter.spring.webflux.SentinelWebFluxAutoConfiguration"
        })
@ActiveProfiles("dev")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class DevP6SpySmokeTest {

    private final DataSource dataSource;

    /**
     * 构造器注入 DataSource。
     *
     * @param dataSource dev 数据源（dynamic-datasource）
     */
    public DevP6SpySmokeTest(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Test
    void shouldUseDynamicRoutingDataSource() {
        assertThat(dataSource)
                .as("dev 数据源应为 dynamic-datasource（与 prod 同构）")
                .isInstanceOf(DynamicRoutingDataSource.class);
    }

    /**
     *
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
     *
     * <p>在 p6spy logger 上挂 {@link ListAppender}，执行 SQL 后断言捕获到代理日志，
     * 证明 dev 的 p6spy 配置（spy.properties + dynamic p6spy=true）真实生效。</p>
     *
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

        assertThat(appender.list)
                .as("p6spy logger 应捕获 SQL 代理日志（证明 dev 的 P6Spy 真实生效）")
                .isNotEmpty();
    }
}
