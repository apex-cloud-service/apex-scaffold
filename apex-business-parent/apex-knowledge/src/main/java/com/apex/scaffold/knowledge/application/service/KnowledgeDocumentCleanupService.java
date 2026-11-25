package com.apex.scaffold.knowledge.application.service;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.knowledge.application.dto.KnowledgeDocumentCleanupDTO;
import com.apex.scaffold.knowledge.config.KnowledgeProperties;
import com.apex.scaffold.knowledge.domain.gateway.KnowledgeDocumentLifecyclePort;
import com.apex.scaffold.knowledge.domain.gateway.KnowledgeVectorIndexPort;
import com.apex.scaffold.knowledge.domain.model.KnowledgeDocumentCleanup;
import com.apex.scaffold.knowledge.domain.model.KnowledgeDocumentLease;
import com.apex.scaffold.storage.StorageRegistry;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 文档清理编排。外部操作不占 JDBC 事务；认领、阶段确认与结果各自短事务持久化。
 * 每次 tombstone 复核都再次按 tenant/document 删除，不能因此前 indexConfirmed 跳过迟到任务。
 *
 * @author Apex Team
 * @since 1.3.2
 */
@Service
@RequiredArgsConstructor
public class KnowledgeDocumentCleanupService {

    private final KnowledgeDocumentLifecyclePort lifecycle;
    private final KnowledgeVectorIndexPort vectorIndex;
    private final StorageRegistry storageRegistry;
    private final KnowledgeProperties properties;
    private final KnowledgeDocumentLeaseRunner leaseRunner;

    /**
     * 分页查询当前可信租户的清理凭证，排除正常REGISTERED上传。
     *
     * @param page 从1开始的页码
     * @param size 每页1至100条
     * @return list/total分页，错误摘要不含对象定位或供应商密钥
     */
    public Mono<PageResponse<KnowledgeDocumentCleanupDTO>> page(int page, int size) {
        return lifecycle
                .cleanupPage(page, size)
                .map(result -> PageResponse.of(
                        result.getList().stream()
                                .map(KnowledgeDocumentCleanupDTO::from)
                                .toList(),
                        result.getTotal(),
                        page,
                        size));
    }

    /**
     * 查询当前租户文档清理进度，不依赖已隐藏主档的查询结果。
     *
     * @param documentId 删除文档或失败上传的预分配ID
     * @return 对外状态DTO；凭证缺失或跨租户发出NOT_FOUND
     */
    public Mono<KnowledgeDocumentCleanupDTO> detail(Long documentId) {
        return lifecycle
                .findCleanup(documentId)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "文档清理凭证不存在")))
                .map(KnowledgeDocumentCleanupDTO::from);
    }

    /**
     * 接受手动重试，数据库拒绝活跃认领冲突并将重试时间置为当前时间。
     *
     * @param documentId 当前租户的清理凭证文档ID
     * @return true仅表示重试已接受，不在请求中等待实际资源删除
     */
    public Mono<Boolean> retry(Long documentId) {
        return lifecycle.retryCleanup(documentId);
    }

    /**
     * worker 专属投影租户桥接；未认领直接结束，不以缺租户或索引关闭伪造完成。
     *
     * <p>认领并执行清理的完成信号；单项失败已持久化为待重试。
     *
     * @param tenantId 从专属任务投影取得的可信租户
     * @param documentId 待清理文档 ID
     */
    public Mono<Void> process(String tenantId, Long documentId) {
        return lifecycle
                .claimCleanup(tenantId, documentId)
                .flatMap(lease -> leaseRunner
                        .run(
                                lease,
                                lifecycle
                                        .findCleanup(documentId)
                                        .switchIfEmpty(Mono.error(new IllegalStateException("清理凭证丢失")))
                                        .flatMap(cleanup -> clean(lease, cleanup)))
                        .onErrorResume(error -> lifecycle.finishCleanup(lease, "清理执行失败，需重试")))
                .contextWrite(TenantContext.withTenant(tenantId));
    }

    private Mono<Void> clean(KnowledgeDocumentLease lease, KnowledgeDocumentCleanup cleanup) {
        List<String> errors = new ArrayList<>();
        return lifecycle
                .deleteChunks(lease)
                .then(cleanIndex(lease, cleanup).onErrorResume(error -> {
                    errors.add(properties.getMeilisearch().isEnabled() ? "索引清理未确认，需重试" : "索引能力未启用，必需索引未清理");
                    return Mono.empty();
                }))
                .then(cleanObject(lease, cleanup).onErrorResume(error -> {
                    errors.add(
                            cleanup.provider() == null || cleanup.bucket() == null
                                    ? "历史对象缺少定位快照，需人工定位；未自动删除对象"
                                    : "对象清理失败，需检查原提供商配置后重试");
                    return Mono.empty();
                }))
                .then(Mono.defer(
                        () -> lifecycle.finishCleanup(lease, errors.isEmpty() ? null : String.join("；", errors))));
    }

    private Mono<Void> cleanIndex(KnowledgeDocumentLease lease, KnowledgeDocumentCleanup cleanup) {
        if (!cleanup.indexRequired()) {
            return Mono.empty();
        }
        if (!properties.getMeilisearch().isEnabled()) {
            return Mono.error(new IllegalStateException("索引能力未启用"));
        }
        return vectorIndex
                .deleteDocumentChunks(
                        cleanup.indexEndpoint(), cleanup.indexName(), cleanup.tenantId(), cleanup.documentId())
                .then(lifecycle.confirmIndex(lease));
    }

    private Mono<Void> cleanObject(KnowledgeDocumentLease lease, KnowledgeDocumentCleanup cleanup) {
        if (!cleanup.objectRequired() || cleanup.objectRemoved()) {
            return Mono.empty();
        }
        return Mono.defer(() -> {
            if (cleanup.provider() == null || cleanup.bucket() == null || cleanup.objectKey() == null) {
                return Mono.error(new IllegalStateException("历史对象缺少定位快照"));
            }
            return storageRegistry
                    .require(cleanup.provider())
                    .delete(cleanup.bucket(), cleanup.objectKey())
                    .switchIfEmpty(Mono.error(new IllegalStateException("对象删除未返回确认")))
                    .then(
                            cleanup.mode() == KnowledgeDocumentCleanup.Mode.UPLOAD_STAGING
                                    ? Mono.empty()
                                    : lifecycle.confirmObject(lease));
        });
    }
}
