package com.apex.scaffold.knowledge.application.job;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.knowledge.application.service.KnowledgeDocumentCleanupService;
import com.apex.scaffold.knowledge.config.KnowledgeProperties;
import com.apex.scaffold.knowledge.domain.gateway.KnowledgeDocumentLifecyclePort;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 独立于索引开关的持久清理扫描。认领由数据库租约协调多实例，顺序执行并按配置限流。
 * 正常任务从专属 work 租户投影扫描；历史 softdeleted 索引清理默认关闭且要求明确租户范围。
 * 不扫描全索引/主档差集，不删除无物理主档孤儿，completed tombstone 永久定向复核。
 *
 * @author Apex Team
 * @since 1.3.2
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "apex.knowledge.cleanup", name = "enabled", havingValue = "true", matchIfMissing = true)
public class KnowledgeDocumentCleanupJob {

    private final KnowledgeDocumentLifecyclePort lifecycle;
    private final KnowledgeDocumentCleanupService cleanupService;
    private final KnowledgeProperties properties;

    /** 调度线程是同步桥接入口，默认每30秒执行；阻塞JDBC仍在专属jdbc池。 */
    @Scheduled(fixedDelayString = "${apex.knowledge.cleanup.scan-interval:30s}")
    public void cleanup() {
        runOnce().block();
    }

    /**
     * 执行一轮清理扫描：先按显式配置登记历史软删除索引，再处理专属投影中的待清理文档。
     *
     * <p>租户来源为配置列表或持久任务投影；单文档失败仅记录日志并保留凭证，
     * 不中断其他租户或文档。索引能力关闭时必需索引项保留失败状态，不伪造成完成。
     *
     * <p>本轮扫描结束的完成信号；单项错误已被隔离，不向调用方传播。
     */
    public Mono<Void> runOnce() {
        var config = properties.getCleanup();
        Mono<List<String>> tenants =
                config.getTenantIds().isEmpty() ? lifecycle.scanTaskTenants(1000) : Mono.just(config.getTenantIds());
        Mono<Void> legacy =
                !config.isLegacyIndexEnabled() || config.getLegacyTenantIds().isEmpty()
                        ? Mono.empty()
                        : Flux.fromIterable(config.getLegacyTenantIds())
                                .concatMap(tenant -> lifecycle
                                        .importLegacyDeleted(
                                                tenant,
                                                properties.getMeilisearch().getUrl(),
                                                properties.getMeilisearch().getIndexPrefix() + "kb_chunks",
                                                config.getBatchSize())
                                        .onErrorResume(error -> {
                                            log.warn("历史知识索引清理登记失败: tenantId={}", tenant);
                                            return Mono.empty();
                                        }))
                                .then();
        return legacy.then(tenants)
                .flatMapMany(Flux::fromIterable)
                .distinct()
                .concatMap(tenant -> lifecycle
                        .scanCleanups(tenant, LocalDateTime.now(), config.getBatchSize())
                        .flatMapMany(Flux::fromIterable)
                        .concatMap(id -> cleanupService
                                .process(tenant, id)
                                .onErrorResume(error -> {
                                    log.warn("知识文档清理失败（持久凭证保留）: tenantId={}, documentId={}", tenant, id);
                                    return Mono.empty();
                                })
                                .then(Mono.delay(config.getItemDelay())))
                        .contextWrite(TenantContext.withTenant(tenant)))
                .then();
    }
}
