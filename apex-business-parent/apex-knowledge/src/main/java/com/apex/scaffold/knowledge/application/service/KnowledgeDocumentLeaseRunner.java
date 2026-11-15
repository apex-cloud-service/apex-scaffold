package com.apex.scaffold.knowledge.application.service;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.knowledge.config.KnowledgeProperties;
import com.apex.scaffold.knowledge.domain.gateway.KnowledgeDocumentLifecyclePort;
import com.apex.scaffold.knowledge.domain.model.KnowledgeDocumentLease;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 认领期间续租；续租失败取消本地管线，未知远程任务由持久 tombstone 最终复核。
 *
 * @author Apex Team
 * @since 1.3.2
 */
@Component
@RequiredArgsConstructor
public class KnowledgeDocumentLeaseRunner {

    private final KnowledgeDocumentLifecyclePort lifecycle;
    private final KnowledgeProperties properties;

    /**
     * 在租约保护下执行文档操作，并按心跳间隔续租。
     *
     * <p>业务操作完成或失败即结束；续租返回 false 时以租约失效错误取消本地管线。
     * 远程索引任务可能已提交但结果未知，后续由持久 tombstone 定向复核收敛。
     * 执行上下文显式绑定租约记录的租户，避免依赖回调线程的 ThreadLocal。
     *
     * @param lease 已认领的工作租约
     * @param action 受保护的响应式操作
     * @param <T> 操作结果类型
     * @return 与业务操作同信号的 Mono；租约失效时发出错误
     */
    public <T> Mono<T> run(KnowledgeDocumentLease lease, Mono<T> action) {
        Mono<T> heartbeat = Flux.interval(properties.getCleanup().getHeartbeatInterval())
                .concatMap(tick -> lifecycle
                        .renew(lease)
                        .flatMap(renewed ->
                                renewed ? Mono.just(true) : Mono.error(new IllegalStateException("文档工作租约失效"))))
                .then(Mono.never());
        return Mono.firstWithSignal(action, heartbeat).contextWrite(TenantContext.withTenant(lease.tenantId()));
    }
}
