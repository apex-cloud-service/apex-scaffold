package com.apex.scaffold.integrationservice.config;

import com.apex.scaffold.domain.service.DomainEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 集成服务领域事件装配（批次 E4）。
 *
 * <p>集成组件库（{@code apex-integration}）的遥测受理、规则引擎与 webhook 入站均经
 * 构造器注入 {@link DomainEventPublisher}，宿主必须提供实现。当前装配轻量级
 * {@link IntegrationLocalEventPublisher}（进程内派发、不落库）。</p>
 *
 * <h2>为什么不装配事务 Outbox 三件套</h2>
 * <p>{@code apex_integration} 库的 V1/V2 迁移不含 {@code sys_domain_event_outbox} 表，
 * 且历史迁移不可改写；Outbox 存储在无表环境写事件会直接失败。
 * 待集成域后续版本补齐 Outbox 表后，可参照 auth-service {@code AuthCenterEventConfig}
 * 的模式切换为 {@code DomainEventOutboxStore + DomainEventPublisherImpl + Dispatcher} 可靠投递，
 * 并保留本实现作为 basic/H2 降级路径。</p>
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Configuration(proxyBeanMethods = false)
public class IntegrationEventConfig {

    /**
     * 领域事件发布器（进程内派发）。
     *
     * @return DomainEventPublisher 实例
     */
    @Bean
    public DomainEventPublisher domainEventPublisher() {
        return new IntegrationLocalEventPublisher();
    }
}
