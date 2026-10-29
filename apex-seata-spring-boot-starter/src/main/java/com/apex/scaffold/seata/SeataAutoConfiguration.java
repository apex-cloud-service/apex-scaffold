package com.apex.scaffold.seata;

import javax.sql.DataSource;
import org.apache.seata.core.model.BranchType;
import org.apache.seata.core.rpc.netty.RmNettyRemotingClient;
import org.apache.seata.core.rpc.netty.TmNettyRemotingClient;
import org.apache.seata.rm.DefaultResourceManager;
import org.apache.seata.rm.RMClient;
import org.apache.seata.rm.datasource.DataSourceProxy;
import org.apache.seata.rm.datasource.SeataDataSourceProxy;
import org.apache.seata.rm.datasource.xa.DataSourceProxyXA;
import org.apache.seata.saga.engine.StateMachineEngine;
import org.apache.seata.saga.engine.config.DbStateMachineConfig;
import org.apache.seata.saga.engine.impl.ProcessCtrlStateMachineEngine;
import org.apache.seata.saga.engine.store.db.DbAndReportTcStateLogStore;
import org.apache.seata.saga.engine.tm.SagaTransactionalTemplate;
import org.apache.seata.saga.rm.StateMachineEngineHolder;
import org.apache.seata.spring.annotation.GlobalTransactionScanner;
import org.apache.seata.tm.TMClient;
import org.apache.seata.tm.api.GlobalTransactionContext;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.web.server.WebFilter;

/**
 * 不加载上游注解事务扫描器，只有显式启用时创建真实 TM/RM 客户端。
 *
 * <p>通过 {@code apex.seata.mode} 选择 AT/TCC/SAGA/XA：AT 与 XA 包装单 JDBC 数据源；
 * TCC 由 Seata 扫描器代理 {@code @LocalTCC} 动作并承担 TM/RM 生命周期；SAGA 装配状态机引擎。
 * 四种模式共享 XID 传播过滤与 Outbox 防护。
 *
 * @author Apex Team
 * @since 1.3.1
 */
@AutoConfiguration(
        beforeName = {
            "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
            "com.baomidou.dynamic.datasource.spring.boot.autoconfigure.DynamicDataSourceAutoConfiguration"
        })
@EnableConfigurationProperties(SeataProperties.class)
@ConditionalOnProperty(prefix = "apex.seata", name = "enabled", havingValue = "true")
public class SeataAutoConfiguration {
    private static final String DYNAMIC_PRIMARY = "spring.datasource.dynamic.primary";
    private static final String DYNAMIC_ROUTING_CLASS = "DynamicRoutingDataSource";

    /** 非 TCC 模式（AT/XA/SAGA）由本模块 ClientLifecycle 负责 TM/RM 生命周期。 */
    static final class NonTccMode implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return !TccMode.matchesRaw(context.getEnvironment());
        }
    }

    /** TCC 模式：GlobalTransactionScanner 同时负责客户端初始化与 TCC 动作代理，避免双重初始化。 */
    static final class TccMode implements Condition {
        static boolean matchesRaw(Environment environment) {
            String mode = environment == null ? null : environment.getProperty("apex.seata.mode");
            return mode != null && mode.trim().toUpperCase().equals(SeataProperties.Mode.TCC.name());
        }

        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return matchesRaw(context.getEnvironment());
        }
    }

    /** SAGA 模式专属状态机引擎装配。 */
    static final class SagaMode implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            String mode = context.getEnvironment().getProperty("apex.seata.mode");
            return mode != null && mode.trim().toUpperCase().equals(SeataProperties.Mode.SAGA.name());
        }
    }

    @Bean(initMethod = "start", destroyMethod = "close")
    @Conditional(NonTccMode.class)
    ClientLifecycle seataClientLifecycle(SeataProperties properties, Environment environment) {
        validate(properties, environment);
        return new ClientLifecycle(properties);
    }

    /**
     * TCC 模式专用：由 Seata 扫描器完成 TM/RM 客户端初始化与 {@code @LocalTCC}/{@code @TwoPhaseBusinessAction}
     * 动作代理，替代本模块 ClientLifecycle，避免双重初始化。
     *
     * <p>{@link GlobalTransactionScanner} 是 {@code BeanPostProcessor}，必须由容器在
     * {@code registerBeanPostProcessors} 阶段创建：此时全部 Bean 定义已注册、业务 Bean 尚未实例化，
     * {@code afterPropertiesSet} 的待增强快照与后续 {@code wrapIfNecessary} 才能同时成立。
     * 工厂方法声明为 {@code static}，避免为了调用它而提前实例化本配置类；否则扫描器在业务 Bean
     * 都已创建后才初始化，动作对象拿不到代理，Try 无法注册 TC 分支。
     */
    @Bean
    @Conditional(TccMode.class)
    static GlobalTransactionScanner seataTccScanner(SeataProperties properties, Environment environment) {
        validate(properties, environment);
        return new GlobalTransactionScanner(properties.getApplicationId(), properties.getTransactionServiceGroup());
    }

    /** 按模式包装数据源：AT/XA 需要代理分支注册，TCC/SAGA 保持原始数据源。 */
    @Bean
    static BeanPostProcessor seataDataSourceProxy(ObjectProvider<SeataProperties> properties) {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String name) {
                if (!(bean instanceof DataSource source)) {
                    return bean;
                }
                return wrapDataSource(source, properties.getObject());
            }
        };
    }

    /**
     * 模式 → 数据源代理类型决策（包可见，便于无网络环境单元测试）。
     * 返回 null 表示该模式不包装数据源（TCC 分支由动作代理注册，SAGA 引擎直接持有原始数据源）。
     */
    static Class<? extends SeataDataSourceProxy> proxyClassFor(SeataProperties.Mode mode) {
        return switch (mode) {
            case AT -> DataSourceProxy.class;
            case XA -> DataSourceProxyXA.class;
            default -> null;
        };
    }

    /**
     * 单 JDBC 数据源包装规则（包可见，便于无网络环境单元测试）。
     *
     * <p>AT：默认自动补偿代理；XA：强隔离代理；TCC：分支注册由动作代理完成，不包装；
     * SAGA：状态机引擎直接持有原始数据源持久化编排状态。AT/XA 代理构造要求真实
     * MySQL/PostgreSQL 数据库（H2 等测试库不在 AT dbtype 支持列表）。
     */
    static DataSource wrapDataSource(DataSource source, SeataProperties properties) {
        SeataProperties.Mode mode = properties.getMode();
        if (proxyClassFor(mode) == null) {
            return source;
        }
        if (source.getClass().getName().contains(DYNAMIC_ROUTING_CLASS)) {
            throw new IllegalStateException("Seata AT/XA 不支持当前动态路由数据源装配");
        }
        if (source instanceof SeataDataSourceProxy) {
            throw new IllegalStateException("检测到已有 Seata 数据源代理，请禁用其他自动代理");
        }
        return mode == SeataProperties.Mode.XA ? new DataSourceProxyXA(source) : new DataSourceProxy(source);
    }

    /** 事务执行器四种模式通用：负责全局事务 begin/commit/rollback 与线程上下文清理。 */
    @Bean
    SeataTransactionExecutor seataTransactionExecutor(SeataProperties properties) {
        return new SeataTransactionExecutor(GlobalTransactionContext::createNew, properties.getTimeoutMillis());
    }

    /** SAGA：状态机存储配置；原始数据源用于编排状态持久化，afterPropertiesSet 自动装配事务模板。 */
    @Bean
    @Conditional(SagaMode.class)
    DbStateMachineConfig seataSagaDbStateMachineConfig(SeataProperties properties, DataSource dataSource) {
        DbStateMachineConfig config = new DbStateMachineConfig();
        config.setDataSource(dataSource);
        config.setApplicationId(properties.getApplicationId());
        config.setTxServiceGroup(properties.getTransactionServiceGroup());
        config.setTablePrefix(properties.getSaga().getTablePrefix());
        config.setResources(properties.getSaga().getStateMachineResources());
        return config;
    }

    /**
     * SAGA：装配状态机引擎；引擎初始化与状态机定义注册由配置的 afterPropertiesSet 完成。
     *
     * <p>本模式 RM 由 Seata 服务加载器装配 {@code SagaResourceManager}，其 TC 二阶段
     * commit/rollback 只从 {@code StateMachineEngineHolder} 取引擎，因此必须在此登记本引擎。
     * 登记对象按 {@link StateMachineEngine} 接口持有，不依赖 RM 的加载实现类。
     */
    @Bean
    @Conditional(SagaMode.class)
    StateMachineEngine seataStateMachineEngine(DbStateMachineConfig config) {
        if (config.getStateLogStore() instanceof DbAndReportTcStateLogStore store) {
            // 配置初始化后 store 已持有完成上报模板；此处包一层归属修正，避免参与者提前结束外层全局事务。
            SagaTransactionalTemplate current = store.getSagaTransactionalTemplate();
            if (current != null && !(current instanceof SagaReportOwnership)) {
                store.setSagaTransactionalTemplate(new SagaReportOwnership(current));
            }
        }
        ProcessCtrlStateMachineEngine engine = new ProcessCtrlStateMachineEngine();
        engine.setStateMachineConfig(config);
        StateMachineEngineHolder.setStateMachineEngine(engine);
        return engine;
    }

    /** SAGA：以 Mono 暴露状态机启动/推进/查询，阻塞引擎调用隔离到 jdbc 池。 */
    @Bean
    @Conditional(SagaMode.class)
    SagaExecutor seataSagaExecutor(StateMachineEngine engine) {
        return new SagaExecutor(engine);
    }

    @Bean
    SeataXidPropagation seataXidPropagation(SeataProperties properties) {
        return new SeataXidPropagation(properties);
    }

    @Bean
    WebFilter seataInboundFilter(SeataXidPropagation propagation) {
        return propagation.inbound();
    }

    @Bean
    SeataOutboxGuard seataOutboxGuard() {
        return new SeataOutboxGuard();
    }

    /** 四种模式共用的启用前置校验：凭据、传播安全与单数据源约束。 */
    static void validate(SeataProperties properties, Environment environment) {
        if (properties.getApplicationId() == null
                || properties.getApplicationId().isBlank()
                || properties.getTransactionServiceGroup() == null
                || properties.getTransactionServiceGroup().isBlank()
                || properties.getTimeoutMillis() <= 0) {
            throw new IllegalStateException("启用 Seata 必须设置应用标识、事务组和正数超时");
        }
        if (properties.getPropagationSecret() == null
                || properties.getPropagationSecret().length() < 32
                || properties.getInboundPathPrefix() == null
                || !properties.getInboundPathPrefix().startsWith("/internal/")
                || properties.getAllowedOrigins().stream().anyMatch(origin -> !origin.startsWith("https://"))) {
            throw new IllegalStateException("Seata 传播必须使用至少 32 字符独立凭据、内部路径及 HTTPS 目标");
        }
        if (environment.containsProperty(DYNAMIC_PRIMARY)
                || environment.getProperty("spring.datasource.dynamic.seata", Boolean.class, false)) {
            throw new IllegalStateException("本 Seata starter 仅支持单数据源；请关闭动态数据源配置，禁止双代理");
        }
    }

    @lombok.RequiredArgsConstructor(access = lombok.AccessLevel.PACKAGE)
    static final class ClientLifecycle implements AutoCloseable {
        private final SeataProperties properties;

        /**
         * 初始化非 TCC 模式的 TM/RM 客户端；XA 模式额外注册 XA 分支资源管理器。
         *
         * @implSpec 由 Spring Bean 初始化回调同步执行，客户端初始化失败时阻止 Bean 完成初始化。
         */
        public void start() {
            TMClient.init(properties.getApplicationId(), properties.getTransactionServiceGroup());
            RMClient.init(properties.getApplicationId(), properties.getTransactionServiceGroup());
            // seata-all 手动模式下 XA/SAGA 分支资源管理器不随 RMClient.init 注册：
            // XA 必须显式注册 ResourceManagerXA；SAGA 必须注册 org.apache 的 SagaResourceManager，
            // 否则服务加载器会装入 io.seata 兼容副本，其 TC 二阶段从 io.seata 静态持有者取引擎，
            // 拿不到本模块登记的 StateMachineEngine。
            if (properties.getMode() == SeataProperties.Mode.XA) {
                DefaultResourceManager.mockResourceManager(
                        BranchType.XA, new org.apache.seata.rm.datasource.xa.ResourceManagerXA());
            } else if (properties.getMode() == SeataProperties.Mode.SAGA) {
                DefaultResourceManager.mockResourceManager(
                        BranchType.SAGA, new org.apache.seata.saga.rm.SagaResourceManager());
            }
        }

        @Override
        public void close() {
            TmNettyRemotingClient.getInstance().destroy();
            RmNettyRemotingClient.getInstance().destroy();
        }
    }
}
