package com.apex.scaffold.knowledge.infrastructure.persistence.repository;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.knowledge.config.KnowledgeProperties;
import com.apex.scaffold.knowledge.domain.gateway.KnowledgeDocumentLifecyclePort;
import com.apex.scaffold.knowledge.domain.model.KnowledgeChunk;
import com.apex.scaffold.knowledge.domain.model.KnowledgeDocument;
import com.apex.scaffold.knowledge.domain.model.KnowledgeDocumentCleanup;
import com.apex.scaffold.knowledge.domain.model.KnowledgeDocumentLease;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.publisher.Mono;

/**
 * 文档生命周期专属 JDBC 仓储。以 work 行作为每文档锁，锁顺序 work→document→cleanup。
 * 模板绑定同一业务 DataSource，不向容器发布事务管理器，不依赖响应式/no-op 事务。
 * 手写 SQL 全部包含 tenant_id；后台仅从专属任务表投影取得可信租户。
 *
 * @author Apex Team
 * @since 1.3.2
 */
@Repository
public class KnowledgeDocumentLifecycleRepository implements KnowledgeDocumentLifecyclePort {

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final KnowledgeProperties.Cleanup config;

    /**
     * 使用业务数据源构建仓储；同步事务模板与 JdbcTemplate 绑定同一 DataSource。
     *
     * @param dataSource 知识库业务数据源
     * @param properties 知识库配置，读取清理租约与重试参数
     */
    public KnowledgeDocumentLifecycleRepository(DataSource dataSource, KnowledgeProperties properties) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        this.config = properties.getCleanup();
    }

    private <T> Mono<T> scoped(java.util.function.Function<String, T> action) {
        return TenantContext.currentTenantIdReactive()
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.UNAUTHORIZED, "可信租户上下文缺失")))
                .flatMap(tenant -> io(tenant, () -> action.apply(tenant)));
    }

    private <T> Mono<T> io(String tenant, Supplier<T> action) {
        return Mono.fromCallable(() -> TenantContext.callWithTenant(requireTenant(tenant), action))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .retryWhen(reactor.util.retry.Retry.max(2)
                        .filter(org.springframework.dao.DuplicateKeyException.class::isInstance));
    }

    private <T> T tx(Supplier<T> action) {
        return transaction.execute(status -> action.get());
    }

    @Override
    public Mono<KnowledgeDocumentLease> reserveUpload(
            String title, String provider, String bucket, String objectKey, String endpoint, String indexName) {
        return scoped(tenant -> tx(() -> {
            long id = IdWorker.getId();
            String owner = UUID.randomUUID().toString();
            LocalDateTime now = now();
            LocalDateTime until = now.plus(config.getLeaseDuration());
            exactlyOne(jdbc.update(
                    "INSERT INTO kb_document_work (id,tenant_id,document_id,owner,generation,kind,lease_until)"
                            + " VALUES (?,?,?, ?,1,'UPLOAD',?)",
                    id,
                    tenant,
                    id,
                    owner,
                    until));
            exactlyOne(jdbc.update(
                    "INSERT INTO kb_document_cleanup (id,tenant_id,document_id,title,mode,status,"
                            + "storage_provider,storage_bucket,object_key,object_required,index_endpoint,index_name,index_required,owner,generation,lease_until,next_retry_at)"
                            + " VALUES (?,?,?,?,'UPLOAD_STAGING','PENDING',?,?,?,1,?,?,0,?,1,?,?)",
                    id,
                    tenant,
                    id,
                    title,
                    provider,
                    bucket,
                    objectKey,
                    endpoint,
                    indexName,
                    owner,
                    until,
                    until));
            return new KnowledgeDocumentLease(tenant, id, owner, 1, "UPLOAD", until);
        }));
    }

    @Override
    public Mono<Void> bindUploadedKey(KnowledgeDocumentLease lease, String actualKey) {
        return scoped(tenant -> tx(() -> {
                    assertLease(tenant, lease, false);
                    var cleanup = cleanupRow(tenant, lease.documentId(), true);
                    if (cleanup == null || !actualKey.equals(cleanup.get("object_key"))) {
                        throw new IllegalStateException("供应商未保持独占对象键，禁止推断自动删除目标");
                    }
                    exactlyOne(jdbc.update(
                            "UPDATE kb_document_cleanup SET object_key=?,update_time=? WHERE tenant_id=? AND"
                                    + " document_id=? AND owner=? AND generation=? AND mode='UPLOAD_STAGING'",
                            actualKey,
                            now(),
                            tenant,
                            lease.documentId(),
                            lease.owner(),
                            lease.generation()));
                    return true;
                }))
                .then();
    }

    @Override
    public Mono<KnowledgeDocument> registerUpload(KnowledgeDocumentLease lease, KnowledgeDocument document) {
        return scoped(tenant -> tx(() -> {
            assertLease(tenant, lease, false);
            Map<String, Object> cleanup = cleanupRow(tenant, lease.documentId(), true);
            if (cleanup == null || !"UPLOAD_STAGING".equals(cleanup.get("mode"))) {
                throw new IllegalStateException("上传凭证已失效");
            }
            exactlyOne(jdbc.update(
                    "INSERT INTO kb_document (id,space_id,title,source_type,content_type,object_key,"
                            + "storage_provider,storage_bucket,object_owned,file_size,parse_status,index_status,chunk_count,tenant_id)"
                            + " VALUES (?,?,?,'UPLOAD',?,?,?,?,1,?,'PENDING','NONE',0,?)",
                    lease.documentId(),
                    document.getSpaceId(),
                    document.getTitle(),
                    document.getContentType(),
                    cleanup.get("object_key"),
                    cleanup.get("storage_provider"),
                    cleanup.get("storage_bucket"),
                    document.getFileSize(),
                    tenant));
            exactlyOne(jdbc.update(
                    "UPDATE kb_document_cleanup SET"
                            + " mode='REGISTERED',status='COMPLETED',owner=NULL,lease_until=NULL,next_retry_at=NULL,update_time=?"
                            + " WHERE tenant_id=? AND document_id=? AND owner=? AND generation=? AND"
                            + " mode='UPLOAD_STAGING'",
                    now(),
                    tenant,
                    lease.documentId(),
                    lease.owner(),
                    lease.generation()));
            releaseLocked(lease);
            return loadDocument(tenant, lease.documentId(), false);
        }));
    }

    @Override
    public Mono<Boolean> abortUpload(KnowledgeDocumentLease lease) {
        return scoped(tenant -> tx(() -> {
            Map<String, Object> work = workRow(tenant, lease.documentId());
            Map<String, Object> cleanup = cleanupRow(tenant, lease.documentId(), true);
            if (!tenant.equals(lease.tenantId())
                    || !matches(work, lease)
                    || cleanup == null
                    || !"UPLOAD_STAGING".equals(cleanup.get("mode"))) return false;
            exactlyOne(jdbc.update(
                    "UPDATE kb_document_work SET kind='UPLOAD_ABORT',lease_until=?,update_time=?"
                            + " WHERE tenant_id=? AND document_id=? AND owner=? AND generation=?",
                    now().plus(config.getLeaseDuration()),
                    now(),
                    tenant,
                    lease.documentId(),
                    lease.owner(),
                    lease.generation()));
            exactlyOne(jdbc.update(
                    "UPDATE kb_document_cleanup SET status='FAILED',error_message=?,next_review_at=?,"
                            + "update_time=? WHERE tenant_id=? AND document_id=? AND mode='UPLOAD_STAGING'",
                    "上传未完成或登记失败，等待独占对象补偿",
                    now().plus(config.getTombstoneReviewInterval()),
                    now(),
                    tenant,
                    lease.documentId()));
            return true;
        }));
    }

    @Override
    public Mono<Void> failUpload(KnowledgeDocumentLease lease, String errorMessage, boolean objectRemoved) {
        return scoped(tenant -> tx(() -> {
                    Map<String, Object> work = workRow(tenant, lease.documentId());
                    if (!matches(work, lease)) {
                        return false;
                    }
                    exactlyOne(jdbc.update(
                            "UPDATE kb_document_cleanup SET"
                                    + " status='FAILED',error_message=?,object_removed=?,next_retry_at=?,next_review_at=?,owner=NULL,lease_until=NULL,update_time=?"
                                    + " WHERE tenant_id=? AND document_id=? AND mode='UPLOAD_STAGING'",
                            safeError(errorMessage),
                            objectRemoved ? 1 : 0,
                            now().plus(config.getRetryDelay()),
                            now().plus(config.getTombstoneReviewInterval()),
                            now(),
                            tenant,
                            lease.documentId()));
                    releaseLocked(lease);
                    return true;
                }))
                .then();
    }

    @Override
    public Mono<Boolean> acceptDelete(Long documentId, String endpoint, String indexName) {
        return scoped(tenant -> tx(() -> {
            Map<String, Object> existingWork = workRow(tenant, documentId);
            if (existingWork == null) ensureWork(tenant, documentId);
            workRow(tenant, documentId);
            KnowledgeDocument document = loadDocument(tenant, documentId, true);
            Map<String, Object> cleanup = cleanupRow(tenant, documentId, true);
            if (document == null) {
                if (cleanup != null && !"REGISTERED".equals(cleanup.get("mode"))) {
                    return true;
                }
                throw missing();
            }
            if (document.isDeleted()) {
                if (cleanup != null && "DELETE".equals(cleanup.get("mode"))) {
                    return true;
                }
            }
            boolean indexRequired = document.isIndexRequired()
                    || (document.getIndexStatus() != KnowledgeDocument.IndexStatus.NONE
                            && document.getIndexEndpoint() == null);
            String indexEndpoint = document.getIndexEndpoint() == null ? endpoint : document.getIndexEndpoint();
            String targetIndex = document.getIndexName() == null ? indexName : document.getIndexName();
            boolean objectRequired =
                    document.getSourceType() == KnowledgeDocument.SourceType.UPLOAD && document.getObjectKey() != null;
            if (cleanup == null) {
                exactlyOne(jdbc.update(
                        "INSERT INTO kb_document_cleanup (id,tenant_id,document_id,title,mode,status,"
                                + "storage_provider,storage_bucket,object_key,object_required,index_endpoint,index_name,index_required,next_retry_at)"
                                + " VALUES (?,?,?,?,'DELETE','PENDING',?,?,?,?,?,?,?,?)",
                        IdWorker.getId(),
                        tenant,
                        documentId,
                        document.getTitle(),
                        document.getStorageProvider(),
                        document.getStorageBucket(),
                        document.getObjectKey(),
                        objectRequired ? 1 : 0,
                        indexEndpoint,
                        targetIndex,
                        indexRequired ? 1 : 0,
                        now()));
            } else {
                exactlyOne(jdbc.update(
                        "UPDATE kb_document_cleanup SET mode='DELETE',status='PENDING',title=?,"
                                + "storage_provider=?,storage_bucket=?,object_key=?,object_required=?,object_removed=0,"
                                + "index_endpoint=?,index_name=?,index_required=?,index_confirmed=0,owner=NULL,lease_until=NULL,next_retry_at=?,next_review_at=NULL,error_message=NULL,update_time=?"
                                + " WHERE tenant_id=? AND document_id=?",
                        document.getTitle(),
                        document.getStorageProvider(),
                        document.getStorageBucket(),
                        document.getObjectKey(),
                        objectRequired ? 1 : 0,
                        indexEndpoint,
                        targetIndex,
                        indexRequired ? 1 : 0,
                        now(),
                        now(),
                        tenant,
                        documentId));
            }
            if (!document.isDeleted()) {
                exactlyOne(jdbc.update(
                        "UPDATE kb_document SET deleted=1,update_time=?,version=version+1"
                                + " WHERE tenant_id=? AND id=? AND deleted=0",
                        now(),
                        tenant,
                        documentId));
            }
            return true;
        }));
    }

    @Override
    public Mono<KnowledgeDocument> requestReindex(Long documentId) {
        return scoped(tenant -> tx(() -> {
            ensureWork(tenant, documentId);
            Map<String, Object> work = workRow(tenant, documentId);
            if (active(work)) {
                throw new BusinessException(ResultCode.BUSINESS_ERROR, "文档正在处理，请稍后重试");
            }
            KnowledgeDocument doc = loadDocument(tenant, documentId, true);
            if (doc == null || doc.isDeleted()) {
                throw missing();
            }
            String parse = doc.getContentText() == null || doc.getContentText().isEmpty() ? "PENDING" : "PARSED";
            exactlyOne(jdbc.update(
                    "UPDATE kb_document SET"
                            + " parse_status=?,parse_error=NULL,chunk_count=0,index_status='PENDING',update_time=?,version=version+1"
                            + " WHERE tenant_id=? AND id=? AND deleted=0",
                    parse,
                    now(),
                    tenant,
                    documentId));
            return loadDocument(tenant, documentId, false);
        }));
    }

    @Override
    public Mono<KnowledgeDocumentLease> claimIngestion(Long documentId) {
        return scoped(tenant -> tx(() -> {
            ensureWork(tenant, documentId);
            Map<String, Object> work = workRow(tenant, documentId);
            KnowledgeDocument doc = loadDocument(tenant, documentId, true);
            if (doc == null || doc.isDeleted() || active(work)) {
                return null;
            }
            Map<String, Object> cleanup = cleanupRow(tenant, documentId, true);
            if (cleanup != null && !"REGISTERED".equals(cleanup.get("mode"))) {
                return null;
            }
            return claim(tenant, documentId, work, "INGEST");
        }));
    }

    @Override
    public Mono<KnowledgeDocument> persistDocument(KnowledgeDocumentLease lease, KnowledgeDocument document) {
        return scoped(tenant -> tx(() -> {
            assertLease(tenant, lease, true);
            exactlyOne(jdbc.update(
                    "UPDATE kb_document SET"
                            + " title=?,content_text=?,parse_status=?,parse_error=?,chunk_count=?,index_status=?,update_time=?,version=version+1"
                            + " WHERE tenant_id=? AND id=? AND deleted=0",
                    document.getTitle(),
                    document.getContentText(),
                    document.getParseStatus().name(),
                    document.getParseError(),
                    document.getChunkCount(),
                    document.getIndexStatus().name(),
                    now(),
                    tenant,
                    lease.documentId()));
            return loadDocument(tenant, lease.documentId(), false);
        }));
    }

    @Override
    public Mono<Integer> replaceChunks(KnowledgeDocumentLease lease, List<KnowledgeChunk> chunks) {
        return scoped(tenant -> tx(() -> {
            assertLease(tenant, lease, true);
            int removed =
                    jdbc.update("DELETE FROM kb_chunk WHERE tenant_id=? AND document_id=?", tenant, lease.documentId());
            nonNegative(removed);
            for (KnowledgeChunk chunk : chunks) {
                long id = IdWorker.getId();
                exactlyOne(jdbc.update(
                        "INSERT INTO kb_chunk (id,document_id,seq,content,content_hash,token_estimate,tenant_id)"
                                + " VALUES (?,?,?,?,?,?,?)",
                        id,
                        lease.documentId(),
                        chunk.getSeq(),
                        chunk.getContent(),
                        chunk.getContentHash(),
                        chunk.getTokenEstimate(),
                        tenant));
                chunk.setId(id);
                chunk.setDocumentId(lease.documentId());
            }
            return chunks.size();
        }));
    }

    @Override
    public Mono<Void> recordIndexTarget(KnowledgeDocumentLease lease, String endpoint, String indexName) {
        return scoped(tenant -> tx(() -> {
                    assertLease(tenant, lease, true);
                    exactlyOne(jdbc.update(
                            "UPDATE kb_document SET index_endpoint=?,index_name=?,index_required=1,"
                                    + "update_time=?,version=version+1 WHERE tenant_id=? AND id=? AND deleted=0",
                            endpoint,
                            indexName,
                            now(),
                            tenant,
                            lease.documentId()));
                    return true;
                }))
                .then();
    }

    @Override
    public Mono<Boolean> renew(KnowledgeDocumentLease lease) {
        return scoped(tenant -> tx(() -> {
            if (!tenant.equals(lease.tenantId())) {
                throw missing();
            }
            int affected = jdbc.update(
                    "UPDATE kb_document_work SET lease_until=?,update_time=?"
                            + " WHERE tenant_id=? AND document_id=? AND owner=? AND generation=? AND lease_until>?",
                    now().plus(config.getLeaseDuration()),
                    now(),
                    tenant,
                    lease.documentId(),
                    lease.owner(),
                    lease.generation(),
                    now());
            zeroOrOne(affected);
            if (affected == 1 && "CLEANUP".equals(lease.kind())) {
                exactlyOne(jdbc.update(
                        "UPDATE kb_document_cleanup SET lease_until=? WHERE tenant_id=? AND document_id=?"
                                + " AND owner=? AND generation=?",
                        now().plus(config.getLeaseDuration()),
                        tenant,
                        lease.documentId(),
                        lease.owner(),
                        lease.generation()));
            }
            return affected == 1;
        }));
    }

    @Override
    public Mono<Void> release(KnowledgeDocumentLease lease) {
        return scoped(tenant -> tx(() -> {
                    if (!tenant.equals(lease.tenantId())) {
                        throw missing();
                    }
                    releaseLocked(lease);
                    return true;
                }))
                .then();
    }

    @Override
    public Mono<List<String>> scanTaskTenants(int limit) {
        return Mono.fromCallable(() -> jdbc.queryForList(
                        "SELECT tenant_id FROM kb_document_work WHERE deleted=0 AND tenant_id IS NOT NULL AND"
                                + " tenant_id<>'' GROUP BY tenant_id ORDER BY tenant_id LIMIT ?",
                        String.class,
                        bounded(limit)))
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Mono<List<Long>> scanIngestion(String tenantId, LocalDateTime threshold, int limit) {
        return io(
                tenantId,
                () -> jdbc.queryForList(
                        "SELECT d.id FROM kb_document_work w JOIN kb_document d ON d.id=w.document_id AND"
                                + " d.tenant_id=w.tenant_id WHERE w.tenant_id=? AND d.tenant_id=? AND w.deleted=0 AND"
                                + " d.deleted=0 AND (w.lease_until IS NULL OR w.lease_until<=?) AND d.update_time<=? AND"
                                + " (d.parse_status IN ('PENDING','PARSING','FAILED') OR d.index_status IN"
                                + " ('PENDING','FAILED')) ORDER BY d.id LIMIT ?",
                        Long.class,
                        tenantId,
                        tenantId,
                        now(),
                        threshold,
                        bounded(limit)));
    }

    @Override
    public Mono<List<Long>> scanCleanups(String tenantId, LocalDateTime time, int limit) {
        return io(
                tenantId,
                () -> jdbc.queryForList(
                        "SELECT c.document_id FROM kb_document_cleanup c JOIN kb_document_work w ON"
                                + " c.document_id=w.document_id AND c.tenant_id=w.tenant_id WHERE c.tenant_id=? AND"
                                + " w.tenant_id=? AND c.deleted=0 AND w.deleted=0 AND c.mode<>'REGISTERED' AND"
                                + " (w.lease_until IS NULL OR w.lease_until<=?) AND ((c.status IN"
                                + " ('PENDING','FAILED','PROCESSING') AND c.attempts<? AND (c.next_retry_at IS NULL OR"
                                + " c.next_retry_at<=?)) OR (c.mode IN ('DELETE','LEGACY_INDEX','UPLOAD_STAGING') AND"
                                + " c.next_review_at<=?)) ORDER BY c.document_id LIMIT ?",
                        Long.class,
                        tenantId,
                        tenantId,
                        time,
                        config.getMaxAttempts(),
                        time,
                        time,
                        bounded(limit)));
    }

    @Override
    public Mono<KnowledgeDocumentLease> claimCleanup(String tenantId, Long documentId) {
        return io(
                tenantId,
                () -> tx(() -> {
                    Map<String, Object> work = workRow(tenantId, documentId);
                    if (work == null || active(work)) {
                        return null;
                    }
                    KnowledgeDocument doc = loadDocument(tenantId, documentId, true);
                    Map<String, Object> cleanup = cleanupRow(tenantId, documentId, true);
                    if (cleanup == null
                            || "REGISTERED".equals(cleanup.get("mode"))
                            || (doc != null && !doc.isDeleted())) {
                        return null;
                    }
                    LocalDateTime retry = timestamp(cleanup.get("next_retry_at"));
                    LocalDateTime review = timestamp(cleanup.get("next_review_at"));
                    boolean reviewDue = review != null
                            && !review.isAfter(now())
                            && List.of("DELETE", "LEGACY_INDEX", "UPLOAD_STAGING")
                                    .contains(cleanup.get("mode"));
                    if (!reviewDue
                            && ("COMPLETED".equals(cleanup.get("status"))
                                    || number(cleanup.get("attempts")) >= config.getMaxAttempts()
                                    || (retry != null && retry.isAfter(now())))) {
                        return null;
                    }
                    KnowledgeDocumentLease lease = claim(tenantId, documentId, work, "CLEANUP");
                    exactlyOne(jdbc.update(
                            "UPDATE kb_document_cleanup SET"
                                    + " status='PROCESSING',owner=?,generation=?,lease_until=?,attempts=attempts+1,update_time=?"
                                    + " WHERE tenant_id=? AND document_id=?",
                            lease.owner(),
                            lease.generation(),
                            lease.leaseUntil(),
                            now(),
                            tenantId,
                            documentId));
                    return lease;
                }));
    }

    @Override
    public Mono<KnowledgeDocumentCleanup> findCleanup(Long documentId) {
        return scoped(tenant -> {
            List<KnowledgeDocumentCleanup> rows = jdbc.query(
                    "SELECT * FROM kb_document_cleanup WHERE tenant_id=?"
                            + " AND document_id=? AND deleted=0 AND mode<>'REGISTERED'",
                    this::cleanupModel,
                    tenant,
                    documentId);
            return rows.isEmpty() ? null : rows.getFirst();
        });
    }

    @Override
    public Mono<PageResponse<KnowledgeDocumentCleanup>> cleanupPage(int page, int size) {
        return scoped(tenant -> {
            if (page < 1 || size < 1 || size > 100) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "分页参数无效");
            }
            Long count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM kb_document_cleanup WHERE tenant_id=?"
                            + " AND deleted=0 AND mode<>'REGISTERED'",
                    Long.class,
                    tenant);
            List<KnowledgeDocumentCleanup> list = jdbc.query(
                    "SELECT * FROM kb_document_cleanup WHERE tenant_id=?"
                            + " AND deleted=0 AND mode<>'REGISTERED' ORDER BY document_id DESC LIMIT ? OFFSET ?",
                    this::cleanupModel,
                    tenant,
                    size,
                    (long) (page - 1) * size);
            return PageResponse.of(list, count == null ? 0 : count, page, size);
        });
    }

    @Override
    public Mono<Boolean> retryCleanup(Long documentId) {
        return scoped(tenant -> tx(() -> {
            Map<String, Object> work = workRow(tenant, documentId);
            Map<String, Object> cleanup = cleanupRow(tenant, documentId, true);
            if (cleanup == null || "REGISTERED".equals(cleanup.get("mode"))) {
                throw missing();
            }
            if (active(work)) {
                throw new BusinessException(ResultCode.BUSINESS_ERROR, "清理正在执行，请稍后重试");
            }
            exactlyOne(jdbc.update(
                    "UPDATE kb_document_cleanup SET status='PENDING',attempts=0,error_message=NULL,"
                            + "next_retry_at=?,update_time=? WHERE tenant_id=? AND document_id=?",
                    now(),
                    now(),
                    tenant,
                    documentId));
            return true;
        }));
    }

    @Override
    public Mono<Void> deleteChunks(KnowledgeDocumentLease lease) {
        return scoped(tenant -> tx(() -> {
                    assertLease(tenant, lease, false);
                    KnowledgeDocument doc = loadDocument(tenant, lease.documentId(), true);
                    if (doc != null && !doc.isDeleted()) {
                        throw new IllegalStateException("未删除文档不得清理分块");
                    }
                    nonNegative(jdbc.update(
                            "DELETE FROM kb_chunk WHERE tenant_id=? AND document_id=?", tenant, lease.documentId()));
                    return true;
                }))
                .then();
    }

    @Override
    public Mono<Void> confirmIndex(KnowledgeDocumentLease lease) {
        return scoped(tenant -> tx(() -> {
                    assertLease(tenant, lease, false);
                    exactlyOne(jdbc.update(
                            "UPDATE kb_document_cleanup SET index_confirmed=1,update_time=?"
                                    + " WHERE tenant_id=? AND document_id=? AND owner=? AND generation=?",
                            now(),
                            tenant,
                            lease.documentId(),
                            lease.owner(),
                            lease.generation()));
                    return true;
                }))
                .then();
    }

    @Override
    public Mono<Void> confirmObject(KnowledgeDocumentLease lease) {
        return scoped(tenant -> tx(() -> {
                    assertLease(tenant, lease, false);
                    exactlyOne(jdbc.update(
                            "UPDATE kb_document_cleanup SET object_removed=1,update_time=?"
                                    + " WHERE tenant_id=? AND document_id=? AND owner=? AND generation=?",
                            now(),
                            tenant,
                            lease.documentId(),
                            lease.owner(),
                            lease.generation()));
                    return true;
                }))
                .then();
    }

    @Override
    public Mono<Void> finishCleanup(KnowledgeDocumentLease lease, String errorMessage) {
        return scoped(tenant -> tx(() -> {
                    assertLease(tenant, lease, false);
                    Map<String, Object> cleanup = cleanupRow(tenant, lease.documentId(), true);
                    boolean success = errorMessage == null;
                    if (success
                            && number(cleanup.get("index_required")) == 1
                            && number(cleanup.get("index_confirmed")) != 1) {
                        throw new IllegalStateException("必需索引未确认，禁止完成清理");
                    }
                    if (success
                            && number(cleanup.get("object_required")) == 1
                            && number(cleanup.get("object_removed")) != 1
                            && !"UPLOAD_STAGING".equals(cleanup.get("mode"))) {
                        throw new IllegalStateException("必需对象未确认，禁止完成清理");
                    }
                    long attempts = number(cleanup.get("attempts"));
                    LocalDateTime nextRetry = success || attempts >= config.getMaxAttempts()
                            ? null
                            : now().plus(config.getRetryDelay().multipliedBy(Math.min(60, Math.max(1, attempts))));
                    boolean tombstone =
                            List.of("DELETE", "LEGACY_INDEX", "UPLOAD_STAGING").contains(cleanup.get("mode"));
                    exactlyOne(jdbc.update(
                            "UPDATE kb_document_cleanup SET"
                                    + " status=?,error_message=?,next_retry_at=?,next_review_at=?,owner=NULL,lease_until=NULL,update_time=?"
                                    + " WHERE tenant_id=? AND document_id=? AND owner=? AND generation=?",
                            success ? "COMPLETED" : "FAILED",
                            safeError(errorMessage),
                            nextRetry,
                            tombstone ? now().plus(config.getTombstoneReviewInterval()) : null,
                            now(),
                            tenant,
                            lease.documentId(),
                            lease.owner(),
                            lease.generation()));
                    releaseLocked(lease);
                    return true;
                }))
                .then();
    }

    @Override
    public Mono<Integer> importLegacyDeleted(String tenantId, String endpoint, String indexName, int limit) {
        return io(
                tenantId,
                () -> tx(() -> {
                    List<Long> ids = jdbc.queryForList(
                            "SELECT d.id FROM kb_document d LEFT JOIN kb_document_cleanup c ON c.document_id=d.id AND"
                                    + " c.tenant_id=d.tenant_id WHERE d.tenant_id=? AND d.deleted=1 AND c.document_id IS"
                                    + " NULL ORDER BY d.id LIMIT ?",
                            Long.class,
                            tenantId,
                            bounded(limit));
                    int imported = 0;
                    for (Long id : ids) {
                        ensureWork(tenantId, id);
                        workRow(tenantId, id);
                        KnowledgeDocument doc = loadDocument(tenantId, id, true);
                        if (doc == null || !doc.isDeleted() || cleanupRow(tenantId, id, true) != null) {
                            continue;
                        }
                        exactlyOne(jdbc.update(
                                "INSERT INTO kb_document_cleanup (id,tenant_id,document_id,title,mode,status,"
                                        + "object_required,index_endpoint,index_name,index_required,next_retry_at)"
                                        + " VALUES (?,?,?,?,'LEGACY_INDEX','PENDING',0,?,?,1,?)",
                                IdWorker.getId(),
                                tenantId,
                                id,
                                doc.getTitle(),
                                doc.getIndexEndpoint() == null ? endpoint : doc.getIndexEndpoint(),
                                doc.getIndexName() == null ? indexName : doc.getIndexName(),
                                now()));
                        imported++;
                    }
                    return imported;
                }));
    }

    private KnowledgeDocumentLease claim(String tenant, Long id, Map<String, Object> work, String kind) {
        String owner = UUID.randomUUID().toString();
        long generation = number(work.get("generation")) + 1;
        LocalDateTime until = now().plus(config.getLeaseDuration());
        exactlyOne(jdbc.update(
                "UPDATE kb_document_work SET owner=?,generation=?,kind=?,lease_until=?,update_time=?"
                        + " WHERE tenant_id=? AND document_id=? AND generation=?",
                owner,
                generation,
                kind,
                until,
                now(),
                tenant,
                id,
                generation - 1));
        return new KnowledgeDocumentLease(tenant, id, owner, generation, kind, until);
    }

    private void assertLease(String tenant, KnowledgeDocumentLease lease, boolean requireActiveDocument) {
        if (!tenant.equals(lease.tenantId())) {
            throw missing();
        }
        Map<String, Object> work = workRow(tenant, lease.documentId());
        if (!matches(work, lease) || !active(work) || !lease.kind().equals(work.get("kind"))) {
            throw new IllegalStateException("文档认领租约失效，拒绝旧持有者写入");
        }
        if (requireActiveDocument) {
            KnowledgeDocument doc = loadDocument(tenant, lease.documentId(), true);
            if (doc == null || doc.isDeleted()) {
                throw new IllegalStateException("文档已删除，拒绝后续写入");
            }
        }
    }

    private boolean matches(Map<String, Object> work, KnowledgeDocumentLease lease) {
        return work != null
                && lease.owner().equals(work.get("owner"))
                && lease.generation() == number(work.get("generation"));
    }

    private boolean active(Map<String, Object> work) {
        LocalDateTime until = work == null ? null : timestamp(work.get("lease_until"));
        return work != null && work.get("owner") != null && until != null && until.isAfter(now());
    }

    private void releaseLocked(KnowledgeDocumentLease lease) {
        int affected = jdbc.update(
                "UPDATE kb_document_work SET owner=NULL,kind=NULL,lease_until=NULL,update_time=?"
                        + " WHERE tenant_id=? AND document_id=? AND owner=? AND generation=?",
                now(),
                lease.tenantId(),
                lease.documentId(),
                lease.owner(),
                lease.generation());
        zeroOrOne(affected);
    }

    private void ensureWork(String tenant, Long id) {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM kb_document WHERE tenant_id=? AND id=?", Long.class, tenant, id)
                == 0) {
            throw missing();
        }
        if (jdbc.queryForObject(
                        "SELECT COUNT(*) FROM kb_document_work WHERE tenant_id=? AND document_id=?",
                        Long.class,
                        tenant,
                        id)
                == 0) {
            exactlyOne(jdbc.update(
                    "INSERT INTO kb_document_work (id,tenant_id,document_id) VALUES (?,?,?)",
                    IdWorker.getId(),
                    tenant,
                    id));
        }
    }

    private Map<String, Object> workRow(String tenant, Long id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM kb_document_work WHERE tenant_id=?" + " AND document_id=? AND deleted=0 FOR UPDATE",
                tenant,
                id);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private Map<String, Object> cleanupRow(String tenant, Long id, boolean lock) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM kb_document_cleanup WHERE tenant_id=?" + " AND document_id=? AND deleted=0"
                        + (lock ? " FOR UPDATE" : ""),
                tenant,
                id);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private KnowledgeDocument loadDocument(String tenant, Long id, boolean lock) {
        List<KnowledgeDocument> rows = jdbc.query(
                "SELECT * FROM kb_document WHERE tenant_id=? AND id=?" + (lock ? " FOR UPDATE" : ""),
                this::documentModel,
                tenant,
                id);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private KnowledgeDocument documentModel(ResultSet rs, int row) throws SQLException {
        return KnowledgeDocument.builder()
                .id(rs.getLong("id"))
                .spaceId(rs.getLong("space_id"))
                .title(rs.getString("title"))
                .sourceType(KnowledgeDocument.SourceType.valueOf(rs.getString("source_type")))
                .contentType(rs.getString("content_type"))
                .objectKey(rs.getString("object_key"))
                .fileSize(rs.getObject("file_size", Long.class))
                .contentText(rs.getString("content_text"))
                .parseStatus(KnowledgeDocument.ParseStatus.valueOf(rs.getString("parse_status")))
                .parseError(rs.getString("parse_error"))
                .chunkCount(rs.getInt("chunk_count"))
                .indexStatus(KnowledgeDocument.IndexStatus.valueOf(rs.getString("index_status")))
                .storageProvider(rs.getString("storage_provider"))
                .storageBucket(rs.getString("storage_bucket"))
                .locationSnapshot(rs.getInt("object_owned") == 1)
                .indexEndpoint(rs.getString("index_endpoint"))
                .indexName(rs.getString("index_name"))
                .indexRequired(rs.getInt("index_required") == 1)
                .deleted(rs.getInt("deleted") == 1)
                .createTime(rs.getTimestamp("create_time").toLocalDateTime())
                .updateTime(rs.getTimestamp("update_time").toLocalDateTime())
                .build();
    }

    private KnowledgeDocumentCleanup cleanupModel(ResultSet rs, int row) throws SQLException {
        return new KnowledgeDocumentCleanup(
                rs.getLong("document_id"),
                rs.getString("tenant_id"),
                rs.getString("title"),
                KnowledgeDocumentCleanup.Mode.valueOf(rs.getString("mode")),
                KnowledgeDocumentCleanup.Status.valueOf(rs.getString("status")),
                rs.getString("storage_provider"),
                rs.getString("storage_bucket"),
                rs.getString("object_key"),
                rs.getInt("object_required") == 1,
                rs.getString("index_endpoint"),
                rs.getString("index_name"),
                rs.getInt("index_required") == 1,
                rs.getInt("index_confirmed") == 1,
                rs.getInt("object_removed") == 1,
                rs.getString("error_message"),
                rs.getInt("attempts"),
                rs.getTimestamp("update_time").toLocalDateTime(),
                timestamp(rs.getTimestamp("next_retry_at")),
                timestamp(rs.getTimestamp("next_review_at")));
    }

    private LocalDateTime now() {
        return LocalDateTime.now();
    }

    private static LocalDateTime timestamp(Object value) {
        if (value == null) return null;
        if (value instanceof LocalDateTime time) return time;
        return ((java.sql.Timestamp) value).toLocalDateTime();
    }

    private static long number(Object value) {
        return value == null ? 0 : ((Number) value).longValue();
    }

    private static void exactlyOne(int affected) {
        if (affected != 1) throw new IllegalStateException("文档写入受影响行数不符: " + affected);
    }

    private static void zeroOrOne(int affected) {
        if (affected < 0 || affected > 1) throw new IllegalStateException("文档认领受影响行数不符: " + affected);
    }

    private static void nonNegative(int affected) {
        if (affected < 0) throw new IllegalStateException("分块清理受影响行数无效");
    }

    private static int bounded(int limit) {
        return Math.min(1000, Math.max(1, limit));
    }

    private static String requireTenant(String tenant) {
        if (tenant == null || tenant.isBlank() || tenant.length() > 64) throw new IllegalArgumentException("可信租户ID无效");
        return tenant;
    }

    private static String safeError(String error) {
        return error == null ? null : error.substring(0, Math.min(500, error.length()));
    }

    private static BusinessException missing() {
        return new BusinessException(ResultCode.NOT_FOUND, "知识文档或清理凭证不存在");
    }
}
