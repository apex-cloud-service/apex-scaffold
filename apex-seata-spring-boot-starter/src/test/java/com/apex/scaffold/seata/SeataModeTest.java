package com.apex.scaffold.seata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import javax.sql.DataSource;
import org.apache.seata.core.model.GlobalStatus;
import org.apache.seata.rm.datasource.DataSourceProxy;
import org.apache.seata.rm.datasource.SeataDataSourceProxy;
import org.apache.seata.rm.datasource.xa.DataSourceProxyXA;
import org.apache.seata.saga.engine.StateMachineEngine;
import org.apache.seata.saga.engine.config.DbStateMachineConfig;
import org.apache.seata.saga.engine.store.db.DbAndReportTcStateLogStore;
import org.apache.seata.saga.engine.tm.SagaTransactionalTemplate;
import org.apache.seata.spring.annotation.GlobalTransactionScanner;
import org.apache.seata.tm.api.GlobalTransaction;
import org.apache.seata.tm.api.GlobalTransactionRole;
import org.apache.seata.tm.api.transaction.TransactionInfo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.mock.env.MockEnvironment;

/**
 * 事务模式选择（apex.seata.mode）的绑定与按模式装配规则测试。
 *
 * <p>全部为无网络测试：不触发 TM/RM 客户端初始化（netty），SAGA 仅在 H2 内存库上装配引擎。
 * Seata 客户端配置类型在类加载前固定为 file，避免依赖外部注册中心。
 * @author Apex Team
 */
class SeataModeTest {

    @BeforeAll
    static void configureSeataClientOffline() {
        // ConfigurationFactory 静态初始化要求显式配置类型；file 类型仅读本地配置，不发起网络。
        System.setProperty("config.type", "file");
        System.setProperty("registry.type", "file");
        // file 类型必须指定配置文件名，否则 FileConfiguration 报 "name can't be null"；
        // 对应 src/test/resources/seata-test.conf，仅满足装配期解析，测试不发起真实连接。
        System.setProperty("seata.config.name", "seata-test");
    }

    private DataSource plainDataSource() {
        // H2 内存库即可满足 JDBC 元数据读取；AT 代理对 H2 的 dbtype 校验在负向用例中单独断言。
        SimpleDriverDataSource dataSource = new SimpleDriverDataSource();
        dataSource.setDriver(new org.h2.Driver());
        dataSource.setUrl("jdbc:h2:mem:seata_mode_wrap");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        return dataSource;
    }

    private SeataProperties bindMode(String raw) {
        MapConfigurationPropertySource source = new MapConfigurationPropertySource(Map.of("apex.seata.mode", raw));
        return new Binder(source)
                .bind("apex.seata", Bindable.of(SeataProperties.class))
                .get();
    }

    @Test
    void modeDefaultsToAtWhenUnset() {
        SeataProperties properties = new Binder()
                .bind("apex.seata", Bindable.of(SeataProperties.class))
                .orElse(new SeataProperties());
        assertThat(properties.getMode()).isEqualTo(SeataProperties.Mode.AT);
    }

    @Test
    void modeBindingIsCaseInsensitive() {
        assertThat(bindMode("tcc").getMode()).isEqualTo(SeataProperties.Mode.TCC);
        assertThat(bindMode("Saga").getMode()).isEqualTo(SeataProperties.Mode.SAGA);
        assertThat(bindMode("xa").getMode()).isEqualTo(SeataProperties.Mode.XA);
    }

    @Test
    void modeBindingRejectsUnknownValue() {
        assertThatThrownBy(() -> bindMode("2PC")).isInstanceOf(BindException.class);
    }

    @Test
    void atModeDecidesAtProxyAndRejectsUnsupportedDbtype() {
        assertThat(SeataAutoConfiguration.proxyClassFor(SeataProperties.Mode.AT))
                .isEqualTo(DataSourceProxy.class);
        // AT 代理构造校验 dbtype：H2 basic 档位不在支持列表，启用 AT 时必须使用 MySQL/PostgreSQL。
        assertThatThrownBy(() -> SeataAutoConfiguration.wrapDataSource(plainDataSource(), new SeataProperties()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AT mode don't support the dbtype: h2");
    }

    @Test
    void xaModeDecidesXaProxy() {
        assertThat(SeataAutoConfiguration.proxyClassFor(SeataProperties.Mode.XA))
                .isEqualTo(DataSourceProxyXA.class);
        // XA 代理构造依赖已注册的 XA ResourceManager（ClientLifecycle.start 负责），此处仅断言决策。
    }

    @Test
    void tccAndSagaModesKeepPlainDataSource() {
        DataSource plain = plainDataSource();
        assertThat(SeataAutoConfiguration.wrapDataSource(plain, bindMode("TCC")))
                .isSameAs(plain);
        assertThat(SeataAutoConfiguration.wrapDataSource(plain, bindMode("SAGA")))
                .isSameAs(plain);
        assertThat(SeataAutoConfiguration.proxyClassFor(SeataProperties.Mode.TCC))
                .isNull();
        assertThat(SeataAutoConfiguration.proxyClassFor(SeataProperties.Mode.SAGA))
                .isNull();
    }

    @Test
    void atAndXaModesRejectDoubleProxy() {
        SeataDataSourceProxy alreadyProxied = Mockito.mock(SeataDataSourceProxy.class);
        assertThatThrownBy(() -> SeataAutoConfiguration.wrapDataSource(alreadyProxied, bindMode("AT")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("已有 Seata 数据源代理");
        assertThatThrownBy(() -> SeataAutoConfiguration.wrapDataSource(alreadyProxied, bindMode("XA")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void tccModeBuildsScannerOwningClientLifecycle() {
        GlobalTransactionScanner scanner =
                SeataAutoConfiguration.seataTccScanner(validProperties("TCC"), mockEnv("TCC"));
        assertThat(scanner).isNotNull();
        // 只调用工厂方法：afterPropertiesSet（TM/RM 初始化 + 待增强快照）由容器刷新触发，测试保持无网络。
    }

    @Test
    void sagaModeWiresEngineOnSingleDataSource() {
        SeataProperties properties = validProperties("SAGA");
        properties.getSaga().setTablePrefix("seata_");
        // 空定义数组：仅验证装配链路；状态机 JSON 定义加载与真实执行需 TC 与 SAGA 存储三表。
        properties.getSaga().setStateMachineResources(new String[0]);

        DataSource dataSource = plainDataSource();
        DbStateMachineConfig config =
                new SeataAutoConfiguration().seataSagaDbStateMachineConfig(properties, dataSource);
        // 校验自动配置把数据源、应用标识、事务组与表前缀绑定到状态机存储配置。
        assertThat(config.getDataSource()).isSameAs(dataSource);
        assertThat(config.getApplicationId()).isEqualTo("apex-test");
        assertThat(config.getTxServiceGroup()).isEqualTo("apex-tx-group");
        assertThat(config.getTablePrefix()).isEqualTo("seata_");

        StateMachineEngine engine = new SeataAutoConfiguration().seataStateMachineEngine(config);
        assertThat(engine).isNotNull();
        assertThat(engine.getStateMachineConfig()).isSameAs(config);
        // 不调用 afterPropertiesSet：其内部构造 DefaultSagaTransactionalTemplate 会初始化 TM 并连接 TC，
        // 状态机定义加载与真实执行属需 TC 与 SAGA 存储三表的集成验证范围。
    }

    @Test
    void sagaParticipantCannotReportGlobalCompletion() throws Exception {
        SagaTransactionalTemplate delegate = Mockito.mock(SagaTransactionalTemplate.class);
        GlobalTransaction participant = Mockito.mock(GlobalTransaction.class);
        Mockito.when(participant.getGlobalTransactionRole()).thenReturn(GlobalTransactionRole.Participant);
        TransactionInfo info = new TransactionInfo();
        Mockito.when(delegate.beginTransaction(info)).thenReturn(participant);
        SagaReportOwnership ownership = new SagaReportOwnership(delegate);

        // 外层事务已存在时 begin 返回参与者视图：本状态机没有发起该事务，不得上报其终态。
        assertThat(ownership.beginTransaction(info)).isSameAs(participant);
        ownership.reportTransaction(participant, GlobalStatus.Committed);
        ownership.reportTransaction(participant, GlobalStatus.Rollbacked);

        Mockito.verify(delegate).beginTransaction(info);
        Mockito.verifyNoMoreInteractions(delegate);
    }

    @Test
    void sagaLauncherReportsActualGlobalCompletion() throws Exception {
        SagaTransactionalTemplate delegate = Mockito.mock(SagaTransactionalTemplate.class);
        GlobalTransaction launcher = Mockito.mock(GlobalTransaction.class);
        Mockito.when(launcher.getGlobalTransactionRole()).thenReturn(GlobalTransactionRole.Launcher);
        Mockito.when(launcher.getXid()).thenReturn("127.0.0.1:8091:1");
        TransactionInfo info = new TransactionInfo();
        Mockito.when(delegate.beginTransaction(info)).thenReturn(launcher);
        SagaReportOwnership ownership = new SagaReportOwnership(delegate);

        ownership.beginTransaction(info);
        ownership.reportTransaction(launcher, GlobalStatus.Committed);
        // 一个全局事务只有一个终态；重复上报不得再次发往 TC。
        ownership.reportTransaction(launcher, GlobalStatus.Committed);

        Mockito.verify(delegate).beginTransaction(info);
        Mockito.verify(delegate).reportTransaction(launcher, GlobalStatus.Committed);
        Mockito.verifyNoMoreInteractions(delegate);
    }

    @Test
    void sagaTransactionReloadedForCompensationCannotReportGlobalCompletion() throws Exception {
        SagaTransactionalTemplate delegate = Mockito.mock(SagaTransactionalTemplate.class);
        GlobalTransaction reloaded = Mockito.mock(GlobalTransaction.class);
        // GlobalTransactionContext.reload 在 Seata 2.2.0 固定返回 Launcher 角色，
        // 补偿线程上的二阶段回滚因此不能靠角色判断归属。
        Mockito.when(reloaded.getGlobalTransactionRole()).thenReturn(GlobalTransactionRole.Launcher);
        Mockito.when(reloaded.getXid()).thenReturn("127.0.0.1:8091:2");
        Mockito.when(delegate.reloadTransaction("127.0.0.1:8091:2")).thenReturn(reloaded);
        SagaReportOwnership ownership = new SagaReportOwnership(delegate);

        assertThat(ownership.reloadTransaction("127.0.0.1:8091:2")).isSameAs(reloaded);
        ownership.reportTransaction(reloaded, GlobalStatus.Rollbacked);

        Mockito.verify(delegate).reloadTransaction("127.0.0.1:8091:2");
        Mockito.verifyNoMoreInteractions(delegate);
    }

    @Test
    void sagaEngineWrapsInitializedStoreTemplateOnlyOnce() {
        SagaTransactionalTemplate delegate = Mockito.mock(SagaTransactionalTemplate.class);
        DbAndReportTcStateLogStore store = new DbAndReportTcStateLogStore();
        store.setSagaTransactionalTemplate(delegate);
        DbStateMachineConfig config = new DbStateMachineConfig();
        config.setStateLogStore(store);
        SeataAutoConfiguration autoConfiguration = new SeataAutoConfiguration();

        autoConfiguration.seataStateMachineEngine(config);
        assertThat(store.getSagaTransactionalTemplate()).isInstanceOf(SagaReportOwnership.class);
        SagaReportOwnership ownership = (SagaReportOwnership) store.getSagaTransactionalTemplate();
        assertThat(ownership.delegate()).isSameAs(delegate);
        autoConfiguration.seataStateMachineEngine(config);
        assertThat(store.getSagaTransactionalTemplate()).isSameAs(ownership);
        Mockito.verifyNoInteractions(delegate);
    }

    private SeataProperties validProperties(String mode) {
        SeataProperties properties = new SeataProperties();
        properties.setEnabled(true);
        properties.setMode(SeataProperties.Mode.valueOf(mode));
        properties.setApplicationId("apex-test");
        properties.setTransactionServiceGroup("apex-tx-group");
        properties.setPropagationSecret("0123456789abcdef0123456789abcdef");
        return properties;
    }

    private MockEnvironment mockEnv(String mode) {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("apex.seata.mode", mode);
        return environment;
    }
}
