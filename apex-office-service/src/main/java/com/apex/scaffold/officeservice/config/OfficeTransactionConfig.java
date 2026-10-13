package com.apex.scaffold.officeservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.ReactiveTransaction;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

/**
 * 办公服务响应式事务配置（迁移期降级实现）。
 *
 * <p>办公服务沿用阻塞式 MyBatis + JDBC 作为持久化栈，未引入 R2DBC，因此容器中不存在
 * 真正的 {@link ReactiveTransactionManager}，Spring Boot 的响应式事务自动配置不会生效。
 * 而领域层 {@code OfficeOrganizationSyncService} 等组件通过构造器强依赖
 * {@link TransactionalOperator}，导致应用上下文无法启动。
 *
 * <p>为在不改动共享领域代码的前提下让办公服务独立启动，这里提供一个空操作
 * （no-op）的响应式事务管理器，并据此创建 {@link TransactionalOperator}：
 * <ul>
 *   <li>{@code getReactiveTransaction} 返回一个空事务对象，不做任何资源绑定</li>
 *   <li>{@code commit}/{@code rollback} 均为空操作</li>
 * </ul>
 * 由此 {@code .as(transactionalOperator::transactional)} 退化为透传——响应式管道
 * 正常执行与传播错误，但不具备响应式事务的原子性保证。
 *
 * <p><b>已知限制：</b>本实现仅为迁移期降级手段。办公平台组织同步等流程的原子性目前
 * 依赖底层阻塞式事务边界，后续 Phase 6+ 应结合阻塞 I/O 调度与
 * {@code TransactionTemplate} 或引入真实响应式数据源后替换本实现。
 *
 * @author Apex Team
 * @since 1.3.0
 */
@Configuration
public class OfficeTransactionConfig {

    /**
     * 办公服务响应式事务操作器（基于空操作事务管理器的降级实现）。
     *
     * @return 透传式的 {@link TransactionalOperator}
     */
    @Bean
    public TransactionalOperator transactionalOperator() {
        return TransactionalOperator.create(new NoOpReactiveTransactionManager());
    }

    /**
     * 空操作响应式事务管理器。
     *
     * <p>不建立真实事务，仅用于满足 {@link TransactionalOperator} 的构造依赖。
     */
    static final class NoOpReactiveTransactionManager implements ReactiveTransactionManager {

        @Override
        public Mono<ReactiveTransaction> getReactiveTransaction(TransactionDefinition definition) {
            return Mono.just(new NoOpReactiveTransaction());
        }

        @Override
        public Mono<Void> commit(ReactiveTransaction transaction) {
            return Mono.empty();
        }

        @Override
        public Mono<Void> rollback(ReactiveTransaction transaction) {
            return Mono.empty();
        }
    }

    /**
     * 空事务对象。
     *
     * <p>{@code TransactionExecution} 的方法在 Spring 6.1 均为默认实现，此处无需覆写。
     */
    static final class NoOpReactiveTransaction implements ReactiveTransaction {}
}
