package com.apex.scaffold.authservice.config;

import com.apex.scaffold.auth.infrastructure.event.LocalDomainEventPublisher;
import com.apex.scaffold.domain.service.DomainEventPublisher;
import com.apex.scaffold.infrastructure.event.DomainEventOutboxDispatcher;
import com.apex.scaffold.infrastructure.event.DomainEventOutboxStore;
import com.apex.scaffold.infrastructure.event.DomainEventPublisherImpl;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 认证中心领域事件装配配置（迁移方案 Phase 2.6 登出传播）。
 *
 * <p>认证中心的 {@code scanBasePackages} 仅覆盖 {@code com.apex.scaffold.authservice}
 * 与 {@code com.apex.scaffold.common}，不包含基础设施层 {@code com.apex.scaffold.infrastructure}，
 * 因此 {@link DomainEventOutboxStore}、{@link DomainEventPublisherImpl}、
 * {@link DomainEventOutboxDispatcher} 不会随组件扫描自动注册，必须在此显式装配。</p>
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li><strong>生产/开发（{@code apex.events.outbox.enabled=true}，默认）</strong>：注册基于事务
 *       Outbox 的可靠投递三件套——存储、发布器（{@code @Primary}）、后台投递器。</li>
 *   <li><strong>basic/H2（{@code apex.events.outbox.enabled=false}）</strong>：注册轻量级
 *       {@link LocalDomainEventPublisher}。原因：Outbox 存储 SQL 使用 MySQL 方言
 *       （{@code ON DUPLICATE KEY UPDATE ... IF()}），H2 MySQL 模式不支持 {@code IF()} 函数，
 *       启用会导致登出流程写入失败。</li>
 * </ul>
 *
 * @author Apex Team
 * @since 1.3.1
 * @see DomainEventPublisherImpl
 * @see LocalDomainEventPublisher
 */
@Configuration
public class AuthCenterEventConfig {

    /** Outbox 开关配置前缀。 */
    private static final String OUTBOX_ENABLED_PREFIX = "apex.events.outbox";

    /**
     * 领域事件 Outbox 存储 Bean（生产环境）。
     *
     * @param dataSource 业务数据源（MySQL/PostgreSQL）
     * @return DomainEventOutboxStore 实例
     */
    @Bean
    @ConditionalOnProperty(
            prefix = OUTBOX_ENABLED_PREFIX,
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    public DomainEventOutboxStore domainEventOutboxStore(DataSource dataSource) {
        return new DomainEventOutboxStore(dataSource);
    }

    /**
     * 基于事务 Outbox 的领域事件发布器 Bean（生产环境）。
     *
     * <p>标注为 {@code @Primary}，登出等业务注入的 {@link DomainEventPublisher}
     * 默认使用本实现，先写 Outbox 再由投递器可靠投递。</p>
     *
     * @param domainEventOutboxStore Outbox 存储
     * @return DomainEventPublisher 实例
     */
    @Bean
    @Primary
    @ConditionalOnProperty(
            prefix = OUTBOX_ENABLED_PREFIX,
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    public DomainEventPublisher domainEventOutboxPublisher(DomainEventOutboxStore domainEventOutboxStore) {
        return new DomainEventPublisherImpl(domainEventOutboxStore);
    }

    /**
     * 领域事件 Outbox 后台投递器 Bean（生产环境）。
     *
     * <p>依赖 {@code @EnableScheduling}（已在 {@code AuthServiceApplication} 启用），
     * 按 {@code apex.events.outbox.poll-delay-ms} 轮询领取并投递事件，同时执行保留策略清理。</p>
     *
     * @param domainEventOutboxStore Outbox 存储
     * @param applicationEventPublisher Spring 本地事件发布器
     * @param batchSize 单批最大事件数，默认 50
     * @param maxAttempts 最大投递次数，默认 8
     * @param lockTimeoutSeconds 处理锁超时秒数，默认 120
     * @param parallelism 单实例投递并行度，默认 4
     * @param retentionEnabled 是否启用保留策略清理，默认 true
     * @param retentionDeliveredHours DELIVERED 记录保留小时数，默认 168
     * @param retentionDeadDays DEAD 记录保留天数，默认 7
     * @param retentionBatchSize 保留策略清理单批删除行数上限，默认 5000
     * @return DomainEventOutboxDispatcher 实例
     */
    @Bean
    @ConditionalOnProperty(
            prefix = OUTBOX_ENABLED_PREFIX,
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    public DomainEventOutboxDispatcher domainEventOutboxDispatcher(
            DomainEventOutboxStore domainEventOutboxStore,
            ApplicationEventPublisher applicationEventPublisher,
            @Value("${apex.events.outbox.batch-size:50}") int batchSize,
            @Value("${apex.events.outbox.max-attempts:8}") int maxAttempts,
            @Value("${apex.events.outbox.lock-timeout-seconds:120}") int lockTimeoutSeconds,
            @Value("${apex.events.outbox.parallelism:4}") int parallelism,
            @Value("${apex.events.outbox.retention-enabled:true}") boolean retentionEnabled,
            @Value("${apex.events.outbox.retention-delivered-hours:168}") int retentionDeliveredHours,
            @Value("${apex.events.outbox.retention-dead-days:7}") int retentionDeadDays,
            @Value("${apex.events.outbox.retention-batch-size:5000}") int retentionBatchSize) {
        return new DomainEventOutboxDispatcher(
                domainEventOutboxStore,
                applicationEventPublisher,
                batchSize,
                maxAttempts,
                lockTimeoutSeconds,
                parallelism,
                retentionEnabled,
                retentionDeliveredHours,
                retentionDeadDays,
                retentionBatchSize);
    }

    /**
     * 轻量级领域事件发布器 Bean（basic/H2 环境降级）。
     *
     * <p>当显式关闭 Outbox 时启用，仅记录日志不落库，保证服务可正常启动与登出流程可用。</p>
     *
     * @return DomainEventPublisher 实例
     */
    @Bean
    @ConditionalOnProperty(prefix = OUTBOX_ENABLED_PREFIX, name = "enabled", havingValue = "false")
    public DomainEventPublisher localDomainEventPublisher() {
        return new LocalDomainEventPublisher();
    }
}
