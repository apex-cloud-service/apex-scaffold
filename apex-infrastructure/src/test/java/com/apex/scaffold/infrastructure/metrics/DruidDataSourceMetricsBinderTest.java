package com.apex.scaffold.infrastructure.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.alibaba.druid.pool.DruidDataSource;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Druid 连接池指标绑定器单元测试
 *
 * @author Apex Team
 */
class DruidDataSourceMetricsBinderTest {

    /** 指标注册表 */
    private MeterRegistry registry;

    /** 被测绑定器 */
    private DruidDataSourceMetricsBinder binder;

    /** 初始化测试环境 */
    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        binder = new DruidDataSourceMetricsBinder(registry, mock(DataSource.class));
    }

    /** 绑定 Druid 数据源时应注册连接状态 Gauge 与累计统计 FunctionCounter */
    @Test
    void bindDataSourceRegistersDruidGauges() {
        DruidDataSource druid = new DruidDataSource();
        druid.setName("master");
        druid.setInitialSize(15);
        druid.setMinIdle(15);
        druid.setMaxActive(50);

        boolean bound = binder.bindDataSource("master", druid);

        assertTrue(bound);
        assertEquals(50.0, registry.get("druid.connections.max").gauge().value());
        assertEquals(15.0, registry.get("druid.connections.min").gauge().value());
        assertEquals(15.0, registry.get("druid.connections.initial").gauge().value());
        assertEquals(0.0, registry.get("druid.connections.active").gauge().value());
        assertEquals(0.0, registry.get("druid.connections.usage").gauge().value());
        assertEquals(
                0.0, registry.get("druid.statistics.connect").functionCounter().count());
        assertEquals(
                0.0, registry.get("druid.statistics.commit").functionCounter().count());
        assertEquals(
                0.0, registry.get("druid.statistics.rollback").functionCounter().count());
        assertEquals(
                "master", registry.get("druid.connections.max").gauge().getId().getTag("name"));
    }

    /** 非 Druid 数据源应跳过且不注册任何指标 */
    @Test
    void bindDataSourceSkipsNonDruidDataSource() throws SQLException {
        DataSource nonDruid = mock(DataSource.class);

        boolean bound = binder.bindDataSource("other", nonDruid);

        assertFalse(bound);
        assertEquals(0, registry.getMeters().size());
    }

    /** 重复绑定同一数据源应保持幂等，不重复注册 */
    @Test
    void bindDataSourceIsIdempotent() {
        DruidDataSource druid = new DruidDataSource();
        druid.setName("master");
        druid.setMaxActive(50);

        binder.bindDataSource("master", druid);
        int metersAfterFirstBind = registry.getMeters().size();
        binder.bindDataSource("master", druid);

        assertEquals(metersAfterFirstBind, registry.getMeters().size());
        assertEquals(50.0, registry.get("druid.connections.max").gauge().value());
    }
}
