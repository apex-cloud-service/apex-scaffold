package com.apex.scaffold.knowledge.application.job;

import com.apex.scaffold.knowledge.application.service.IngestionService;
import com.apex.scaffold.knowledge.domain.gateway.KnowledgeDocumentRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 知识摄取校准 Job：每日扫描滞留/失败的文档重新入管线。
 *
 * <p>覆盖三类滞留：解析中中断（PARSING）、解析失败（FAILED/PENDING）与索引失败
 * （PENDING/FAILED）。仅扫描更新时间早于 15 分钟前的记录，避免与进行中的摄取
 * 并发踩踏；单文档失败不影响批次推进。与 system 的 SearchIndexReconciliationJob
 * 保持同一模式：调度线程上阻塞订阅响应式管线。
 *
 * @author Apex Team
 * @since 1.3.2
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "apex.knowledge.meilisearch", name = "enabled", havingValue = "true")
public class KnowledgeIndexReconciliationJob {

    /** 单轮扫描上限，防止失败风暴。 */
    private static final int BATCH_LIMIT = 100;

    /** 滞留判定阈值（分钟）：晚于该时间的记录视为仍在处理中。 */
    private static final int STALE_MINUTES = 15;

    private final KnowledgeDocumentRepository documentRepository;

    private final IngestionService ingestionService;

    /**
     * 构建校准 Job。
     *
     * @param documentRepository 文档仓储
     * @param ingestionService 摄取编排服务
     */
    public KnowledgeIndexReconciliationJob(
            KnowledgeDocumentRepository documentRepository, IngestionService ingestionService) {
        this.documentRepository = documentRepository;
        this.ingestionService = ingestionService;
    }

    /**
     * 每日 02:30 执行校准扫描。
     */
    @Scheduled(cron = "${apex.knowledge.reconcile-cron:0 30 2 * * ?}")
    public void reconcile() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(STALE_MINUTES);
        log.info("知识摄取校准开始: threshold={}", threshold);
        Long parsedCount = documentRepository
                .findIdsByStatusAndUpdatedBefore(List.of("PENDING", "PARSING", "FAILED"), threshold, BATCH_LIMIT)
                .flatMapMany(Flux::fromIterable)
                .concatMap(id -> ingestionService.ingest(id).onErrorResume(error -> {
                    log.warn("校准摄取失败（继续批次）: documentId={}", id, error);
                    return Mono.empty();
                }))
                .count()
                .block();
        Long indexedCount = documentRepository
                .findIdsByIndexStatusAndUpdatedBefore(List.of("PENDING", "FAILED"), threshold, BATCH_LIMIT)
                .flatMapMany(Flux::fromIterable)
                .concatMap(id -> ingestionService.ingest(id).onErrorResume(error -> {
                    log.warn("校准索引重建失败（继续批次）: documentId={}", id, error);
                    return Mono.empty();
                }))
                .count()
                .block();
        log.info("知识摄取校准完成: parseScanned={}, indexScanned={}", parsedCount, indexedCount);
    }
}
