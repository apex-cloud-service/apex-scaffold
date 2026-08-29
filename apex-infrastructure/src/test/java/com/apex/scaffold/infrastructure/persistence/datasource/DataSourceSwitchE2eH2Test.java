package com.apex.scaffold.infrastructure.persistence.datasource;

import com.apex.scaffold.common.context.DataSourceContext;
import com.baomidou.dynamic.datasource.DynamicRoutingDataSource;
import com.baomidou.dynamic.datasource.provider.DynamicDataSourceProvider;
import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 数据源动态切换端到端测试（双 H2 内存库 + Baomidou DynamicRoutingDataSource）。
 *
 * <p>验证自定义 {@link DataSourceContext} + Baomidou 切换引擎的路由正确性：
 * 主库/从库（探针表标记值不同）在不同切换状态下的实际选库结果，与
 * {@code DataSourceSwitchAspect} 的切换调用完全一致（push 两处 / pop 两处）。</p>
 *
 * @author Apex Team
 */
@DisplayName("数据源动态切换端到端测试(H2)")
class DataSourceSwitchE2eH2Test {

    private static DynamicRoutingDataSource routing;
    private static HikariDataSource masterDs;
    private static HikariDataSource slaveDs;

    @BeforeAll
    static void setUpRoutingDataSource() throws Exception {
        masterDs = h2DataSource("datasource_master", "MASTER");
        slaveDs = h2DataSource("datasource_slave", "SLAVE");

        DynamicDataSourceProvider provider = () -> {
            Map<String, DataSource> map = new HashMap<>();
            map.put("master", masterDs);
            map.put("slave", slaveDs);
            return map;
        };
        routing = new DynamicRoutingDataSource(List.of(provider));
        routing.setPrimary("master");
        routing.afterPropertiesSet();
    }

    @AfterEach
    void tearDown() {
        DataSourceContext.clear();
        DynamicDataSourceContextHolder.clear();
    }

    @Test
    void shouldUsePrimaryWhenNoSwitch() throws Exception {
        assertThat(probeSource()).isEqualTo("MASTER");
    }

    @Test
    void shouldRouteToSlaveWhenSwitched() throws Exception {
        switchTo("slave");

        assertThat(probeSource()).isEqualTo("SLAVE");

        switchBack();
        assertThat(probeSource()).isEqualTo("MASTER");
    }

    @Test
    void shouldSupportNestedSwitch() throws Exception {
        switchTo("slave");
        switchTo("master");

        // 内层 master 生效
        assertThat(probeSource()).isEqualTo("MASTER");

        switchBack();
        // 回到外层 slave
        assertThat(probeSource()).isEqualTo("SLAVE");

        switchBack();
        assertThat(probeSource()).isEqualTo("MASTER");
    }

    @Test
    void shouldReadCurrentDataSourceName() {
        switchTo("slave");
        assertThat(DataSourceContext.current()).hasValue("slave");
        assertThat(DynamicDataSourceContextHolder.peek()).isEqualTo("slave");
    }

    /**
     * 模拟切面的切换调用（push 两处，与 DataSourceSwitchAspect.switchTo 一致）。
     */
    private static void switchTo(String name) {
        DataSourceContext.push(name);
        DynamicDataSourceContextHolder.push(name);
    }

    /**
     * 模拟切面的释放调用（pop 两处，与 DataSourceSwitchAspect.restore 一致）。
     */
    private static void switchBack() {
        DataSourceContext.pop();
        DynamicDataSourceContextHolder.poll();
    }

    /**
     * 经路由数据源查询探针表，返回当前数据源的标记值。
     */
    private static String probeSource() throws Exception {
        try (Connection connection = routing.getConnection();
                Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery("SELECT source FROM routing_probe")) {
            rs.next();
            return rs.getString(1);
        }
    }

    /**
     * 创建带探针表（标记值）的 H2 内存数据源。
     */
    private static HikariDataSource h2DataSource(String dbName, String marker) throws Exception {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl("jdbc:h2:mem:" + dbName + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        ds.setUsername("sa");
        ds.setPassword("");
        ds.setDriverClassName("org.h2.Driver");
        try (Connection connection = ds.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE routing_probe (id INT PRIMARY KEY, source VARCHAR(20))");
            statement.execute("INSERT INTO routing_probe (id, source) VALUES (1, '" + marker + "')");
        }
        return ds;
    }
}
