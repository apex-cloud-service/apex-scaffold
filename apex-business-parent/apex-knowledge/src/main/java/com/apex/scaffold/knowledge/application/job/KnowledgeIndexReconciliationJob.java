package com.apex.scaffold.knowledge.application.job;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.knowledge.application.service.IngestionService;
import com.apex.scaffold.knowledge.config.KnowledgeProperties;
import com.apex.scaffold.knowledge.domain.gateway.KnowledgeDocumentLifecyclePort;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 摄取校准仅从专属 work 投影获取租户，再以显式租户条件查询滞留主档。
 * ingest 的数据库租约协调并发，不以15分钟阈值作为互斥锁；解析恢复独立于索引开关。
 *
 * @author Apex Team
 * @since 1.3.2
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeIndexReconciliationJob {

    private final KnowledgeDocumentLifecyclePort lifecycle;
    private final IngestionService ingestionService;
    private final KnowledgeProperties properties;

    /** 默认每日02:30，调度线程桥接阻塞订阅；数据库I/O已由仓储隔离。 */
    @Scheduled(cron = "${apex.knowledge.reconcile-cron:0 30 2 * * ?}")
    public void reconcile() {
        runOnce().block();
    }

    /**
     * 执行一轮摄取校准：按专属工作投影的租户扫描滞留文档并重新触发摄取。
     *
     * <p>仅处理超过 15 分钟且处于指定解析/索引状态的记录；单文档失败记录日志后继续，
     * 不以无租户上下文扫描业务表。
     *
     * <p>本轮校准结束的完成信号；单项错误已被隔离。
     */
    public Mono<Void> runOnce() {
        Mono<List<String>> tenants = properties.getCleanup().getTenantIds().isEmpty()
                ? lifecycle.scanTaskTenants(1000)
                : Mono.just(properties.getCleanup().getTenantIds());
        return tenants.flatMapMany(Flux::fromIterable)
                .distinct()
                .concatMap(tenant -> lifecycle
                        .scanIngestion(
                                tenant,
                                LocalDateTime.now().minusMinutes(15),
                                properties.getCleanup().getBatchSize())
                        .flatMapMany(Flux::fromIterable)
                        .concatMap(id -> Mono.defer(() -> ingestionService.ingest(id))
                                .onErrorResume(error -> {
                                    log.warn("知识摄取校准失败: tenantId={}, documentId={}", tenant, id);
                                    return Mono.empty();
                                })
                                .then(Mono.delay(properties.getCleanup().getItemDelay())))
                        .contextWrite(TenantContext.withTenant(tenant)))
                .then();
    }
}
