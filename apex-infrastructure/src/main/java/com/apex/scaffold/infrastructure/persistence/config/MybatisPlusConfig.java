package com.apex.scaffold.infrastructure.persistence.config;

import com.apex.scaffold.common.context.DataScopeProvider;
import com.apex.scaffold.infrastructure.persistence.datapermission.DataScopeInnerInterceptor;
import com.apex.scaffold.infrastructure.persistence.datapermission.DefaultTenantLineHandler;
import com.apex.scaffold.infrastructure.persistence.datapermission.MdcDataScopeProvider;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * MyBatis-Plus 配置类
 *
 * <p>配置 MyBatis-Plus 扩展插件：
 * <ul>
 *   <li><strong>多租户插件</strong>：{@link TenantLineInnerInterceptor} + {@link DefaultTenantLineHandler}，
 *       当 {@code apex.tenant.enabled=true}（Enterprise 默认开启）时自动注入
 *       {@code tenant_id} 过滤条件，实现 SaaS 行级租户隔离；
 *       忽略平台级系统表（sys_menu、sys_permission 等）</li>
 *   <li><strong>数据权限插件</strong>：{@link DataScopeInnerInterceptor}，
 *       自动为标注 {@code @DataScope} 的查询注入部门/用户数据权限过滤条件（自定义数据权限）</li>
 *   <li><strong>乐观锁插件</strong>：{@link OptimisticLockerInnerInterceptor}，
 *       自动为标注了 {@code @Version} 注解的字段生成乐观锁 SQL</li>
 * </ul>
 *
 * <p>插件注册顺序（MyBatis-Plus 3.5 内部执行顺序为先进先执行）：
 * 多租户 → 数据权限 → 乐观锁。多租户先于数据权限执行，确保数据权限的部门过滤
 * 已在租户过滤之后，避免同 ID 跨租户部门污染。</p>
 *
 * <p><b>分页插件说明</b>：MyBatis-Plus 3.5.9 起分页插件 {@code PaginationInnerInterceptor}
 * 从 {@code mybatis-plus-extension} 拆分为独立模块 {@code mybatis-plus-jsqlparser}
 * （依赖 JSQLParser），Starter 不再自动注册。本脚手架的分页统一走
 * {@link com.apex.scaffold.infrastructure.persistence.repository.BaseRepositoryImpl} 的
 * PageHelper 分页（见 {@code findPage}）；如需在业务 Mapper 上直接使用
 * {@code selectPage}，请引入 {@code mybatis-plus-jsqlparser} 并在此显式注册分页插件。
 * 数据权限拦截器的 SQL 改写基于 PageHelper 传递依赖的 JSQLParser 4.7，无需新增依赖。</p>
 *
 * @author Apex Team
 * @version 2.3.0
 * @since 1.0.0
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties({SlowSqlProperties.class, DatabaseProperties.class})
public class MybatisPlusConfig {

    /** 是否启用多租户行级隔离（Enterprise 默认 true） */
    @org.springframework.beans.factory.annotation.Value("${apex.tenant.enabled:false}")
    private boolean tenantEnabled;

    /** 数据库配置属性（驱动分页方言与迁移目录） */
    private final DatabaseProperties databaseProperties;

    /**
     * 构造 MyBatis-Plus 配置类。
     *
     * @param databaseProperties 数据库配置属性
     */
    public MybatisPlusConfig(DatabaseProperties databaseProperties) {
        this.databaseProperties = databaseProperties;
    }

    /**
     * 注册 MyBatis-Plus 拦截器链（多租户 + 数据权限 + 乐观锁）。
     *
     * <p>在 MyBatis-Plus 3.5.x 中，所有内部拦截器需通过 {@link MybatisPlusInterceptor}
     * 统一注册。
     * <ul>
     *   <li>多租户插件（条件激活）：在每条 SQL 前/后自动注入 {@code tenant_id} 过滤条件，
     *       平台级系统表不参与；详见 {@link DefaultTenantLineHandler}。</li>
     *   <li>数据权限插件在每条 SELECT 执行前自动注入
     *       {@code @DataScope} 声明的部门/用户过滤条件，支持全部/本部门/本部门及以下/
     *       仅本人/自定义五种数据权限类型；</li>
     *   <li>乐观锁插件自动为 {@code UPDATE} 语句追加 {@code WHERE version = oldVersion}
     *       条件，更新时同时执行 {@code SET version = version + 1}。
     *       MyBatis-Plus 原生只在冲突时返回 0 行，因此另由
     *       {@link com.apex.scaffold.infrastructure.persistence.aspect.OptimisticLockUpdateAspect} 转换为
     *       {@link org.springframework.dao.OptimisticLockingFailureException}。</li>
     * </ul>
     *
     * <p>配合 {@link com.apex.scaffold.common.annotation.OptimisticLockRetry} 注解使用，
     * 可自动重试解决乐观锁冲突。</p>
     *
     * @param dataScopeProvider 数据权限装配器（解析当前用户数据可见范围）
     * @return MyBatis-Plus 拦截器链实例
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor(DataScopeProvider dataScopeProvider) {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        if (tenantEnabled) {
            interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new DefaultTenantLineHandler()));
            log.info("MyBatis-Plus 多租户拦截器 TenantLineInnerInterceptor 已注册");
        } else {
            log.info("MyBatis-Plus 多租户拦截器未启用（apex.tenant.enabled=false）");
        }
        interceptor.addInnerInterceptor(new DataScopeInnerInterceptor(dataScopeProvider));
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(databaseProperties.resolveDbType()));
        log.info("MyBatis-Plus 数据权限/乐观锁/分页插件已注册，数据库类型={}", databaseProperties.getType());
        return interceptor;
    }

    /**
     * 注册默认数据权限装配器（ThreadLocal + MDC）。
     *
     * <p>应用可通过定义同名 {@link DataScopeProvider} Bean 覆盖（如基于角色表
     * {@code data_scope} 字段的完整实现）。</p>
     *
     * @return 默认数据权限装配器
     */
    @Bean
    @ConditionalOnMissingBean(DataScopeProvider.class)
    public DataScopeProvider dataScopeProvider() {
        log.info("MyBatis-Plus 默认数据权限装配器已注册（ThreadLocal + MDC）");
        return new MdcDataScopeProvider();
    }

    /**
     * 注册慢 SQL 指标拦截器（MyBatis 插件）。
     *
     * <p>该插件由 MyBatis 自动装配收集并应用到 SqlSessionFactory（与 PageHelper 的
     * {@code PageInterceptor} 链式共存）。统计 SQL 执行耗时，上报
     * {@code apex.sql.execution} / {@code apex.sql.slow.count} / {@code apex.sql.slow.max.ms}
     * 指标，支撑 Grafana 大盘「数据库（慢SQL）」分组。通过
     * {@code apex.sql.slow.enabled}（默认 true）与 {@code apex.sql.slow.threshold-ms}
     * （默认 1000）配置。</p>
     *
     * @param properties    慢 SQL 配置属性
     * @param meterRegistry Micrometer 指标注册表
     * @return 慢 SQL 指标拦截器
     */
    @Bean
    @ConditionalOnProperty(prefix = "apex.sql.slow", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean(SlowSqlMetricsInterceptor.class)
    public SlowSqlMetricsInterceptor slowSqlMetricsInterceptor(
            SlowSqlProperties properties, MeterRegistry meterRegistry) {
        log.info("慢SQL指标拦截器已注册, thresholdMs={}", properties.getThresholdMs());
        return new SlowSqlMetricsInterceptor(properties, meterRegistry);
    }
}
