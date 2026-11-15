package com.apex.scaffold.knowledge.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.knowledge.config.KnowledgeProperties;
import com.apex.scaffold.knowledge.domain.model.KnowledgeDocument;
import java.time.LocalDateTime;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import reactor.test.StepVerifier;

class KnowledgeDocumentLifecycleRepositoryTest {

    private JdbcTemplate jdbc;
    private KnowledgeProperties properties;
    private KnowledgeDocumentLifecycleRepository repository;

    @BeforeEach
    void setUp() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:cleanup-" + java.util.UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        new ResourceDatabasePopulator(new FileSystemResource("src/test/resources/db/basic-schema.sql")).execute(ds);
        jdbc = new JdbcTemplate(ds);
        properties = new KnowledgeProperties();
        repository = new KnowledgeDocumentLifecycleRepository(ds, properties);
    }

    private void document(long id, String tenant, boolean snapshot) {
        jdbc.update(
                "INSERT INTO kb_document (id,space_id,title,source_type,object_key,parse_status,index_status,tenant_id)"
                        + " VALUES (?,2,'文档','UPLOAD','kb/key','PARSED','INDEXED',?)",
                id,
                tenant);
        if (snapshot) {
            jdbc.update(
                    "UPDATE kb_document SET"
                            + " storage_provider='local',storage_bucket='original',object_owned=1,index_required=1,index_endpoint='http://meili',index_name='kb_chunks'"
                            + " WHERE id=?",
                    id);
        }
    }

    private <T> reactor.core.publisher.Mono<T> tenant(String id, reactor.core.publisher.Mono<T> action) {
        return action.contextWrite(TenantContext.withTenant(id));
    }

    @Test
    void intentAndSoftDeleteAreAtomicTenantScopedAndIdempotent() {
        document(1, "t1", true);
        StepVerifier.create(tenant("t2", repository.acceptDelete(1L, "http://meili", "kb_chunks")))
                .expectError()
                .verify();
        assertThat(jdbc.queryForObject("SELECT deleted FROM kb_document WHERE id=1", Integer.class))
                .isZero();
        StepVerifier.create(tenant("t1", repository.acceptDelete(1L, "http://meili", "kb_chunks")))
                .expectNext(true)
                .verifyComplete();
        StepVerifier.create(tenant("t1", repository.acceptDelete(1L, "http://meili", "kb_chunks")))
                .expectNext(true)
                .verifyComplete();
        assertThat(jdbc.queryForObject("SELECT deleted FROM kb_document WHERE id=1", Integer.class))
                .isEqualTo(1);
        StepVerifier.create(tenant("t1", repository.findCleanup(1L)))
                .assertNext(cleanup -> {
                    assertThat(cleanup.bucket()).isEqualTo("original");
                    assertThat(cleanup.indexRequired()).isTrue();
                })
                .verifyComplete();
        StepVerifier.create(tenant("t2", repository.findCleanup(1L))).verifyComplete();
    }

    @Test
    void deletedAndExpiredOwnersCannotWriteAndCleanupWaitsForIngestLease() {
        document(2, "t1", true);
        var lease = tenant("t1", repository.claimIngestion(2L)).block();
        assertThat(lease).isNotNull();
        StepVerifier.create(tenant("t1", repository.claimIngestion(2L))).verifyComplete();
        StepVerifier.create(tenant("t1", repository.acceptDelete(2L, "http://meili", "kb_chunks")))
                .expectNext(true)
                .verifyComplete();
        StepVerifier.create(repository.claimCleanup("t1", 2L)).verifyComplete();
        StepVerifier.create(tenant(
                        "t1",
                        repository.persistDocument(
                                lease, KnowledgeDocument.builder().id(2L).build())))
                .expectError()
                .verify();
        jdbc.update(
                "UPDATE kb_document_work SET lease_until=? WHERE document_id=2",
                LocalDateTime.now().minusMinutes(1));
        var cleanupLease = repository.claimCleanup("t1", 2L).block();
        assertThat(cleanupLease).isNotNull();
        StepVerifier.create(tenant("t1", repository.renew(lease)))
                .expectNext(false)
                .verifyComplete();
        StepVerifier.create(tenant(
                        "t1",
                        repository.persistDocument(
                                lease, KnowledgeDocument.builder().id(2L).build())))
                .expectError()
                .verify();
    }

    @Test
    void completedTombstoneIsRetainedAndPeriodicallyClaimedAgain() {
        document(3, "t1", true);
        tenant("t1", repository.acceptDelete(3L, "http://meili", "kb_chunks")).block();
        var lease = repository.claimCleanup("t1", 3L).block();
        tenant("t1", repository.confirmIndex(lease)).block();
        tenant("t1", repository.confirmObject(lease)).block();
        tenant("t1", repository.finishCleanup(lease, null)).block();
        assertThat(jdbc.queryForObject("SELECT status FROM kb_document_cleanup WHERE document_id=3", String.class))
                .isEqualTo("COMPLETED");
        StepVerifier.create(repository.claimCleanup("t1", 3L)).verifyComplete();
        jdbc.update(
                "UPDATE kb_document_cleanup SET next_review_at=? WHERE document_id=3",
                LocalDateTime.now().minusMinutes(1));
        StepVerifier.create(repository.claimCleanup("t1", 3L))
                .assertNext(next -> {
                    assertThat(next.generation()).isGreaterThan(lease.generation());
                })
                .verifyComplete();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM kb_document_cleanup WHERE document_id=3", Long.class))
                .isEqualTo(1);
    }

    @Test
    void legacyWithoutStorageSnapshotKeepsFailureCredential() {
        document(4, "t1", false);
        tenant("t1", repository.acceptDelete(4L, "http://meili", "kb_chunks")).block();
        var lease = repository.claimCleanup("t1", 4L).block();
        tenant("t1", repository.confirmIndex(lease)).block();
        tenant("t1", repository.finishCleanup(lease, "历史对象缺少定位快照，需人工定位")).block();
        StepVerifier.create(tenant("t1", repository.findCleanup(4L)))
                .assertNext(cleanup -> {
                    assertThat(cleanup.status().name()).isEqualTo("FAILED");
                    assertThat(cleanup.provider()).isNull();
                    assertThat(cleanup.attempts()).isEqualTo(1);
                    assertThat(cleanup.nextRetryAt()).isNotNull();
                })
                .verifyComplete();
    }

    @Test
    void uploadStagingCannotBeCleanedWhileUploadingAndRegisteredNeverReviewed() {
        var lease = tenant("t1", repository.reserveUpload("文档", "local", "bucket", "kb/exclusive", null, null))
                .block();
        StepVerifier.create(repository.claimCleanup("t1", lease.documentId())).verifyComplete();
        tenant("t1", repository.bindUploadedKey(lease, "kb/exclusive")).block();
        var doc = KnowledgeDocument.builder()
                .spaceId(2L)
                .title("文档")
                .sourceType(KnowledgeDocument.SourceType.UPLOAD)
                .objectKey("kb/exclusive")
                .parseStatus(KnowledgeDocument.ParseStatus.PENDING)
                .indexStatus(KnowledgeDocument.IndexStatus.NONE)
                .chunkCount(0)
                .build();
        tenant("t1", repository.registerUpload(lease, doc)).block();
        StepVerifier.create(repository.claimCleanup("t1", lease.documentId())).verifyComplete();
        assertThat(jdbc.queryForObject(
                        "SELECT mode FROM kb_document_cleanup WHERE document_id=?", String.class, lease.documentId()))
                .isEqualTo("REGISTERED");
        assertThat(jdbc.queryForObject(
                        "SELECT storage_provider FROM kb_document WHERE id=?", String.class, lease.documentId()))
                .isEqualTo("local");
    }

    @Test
    void abortFencesUnfinishedRegistrationButCannotAbortCommittedUpload() {
        var uploading = tenant("t1", repository.reserveUpload("文档", "local", "bucket", "kb/abort", null, null))
                .block();
        StepVerifier.create(tenant("t1", repository.abortUpload(uploading)))
                .expectNext(true)
                .verifyComplete();
        StepVerifier.create(tenant(
                        "t1",
                        repository.registerUpload(
                                uploading,
                                KnowledgeDocument.builder()
                                        .spaceId(2L)
                                        .title("文档")
                                        .build())))
                .expectError()
                .verify();
        tenant("t1", repository.failUpload(uploading, "上传已取消", false)).block();
        var registered = tenant("t1", repository.reserveUpload("文档", "local", "bucket", "kb/registered", null, null))
                .block();
        tenant(
                        "t1",
                        repository.registerUpload(
                                registered,
                                KnowledgeDocument.builder()
                                        .spaceId(2L)
                                        .title("文档")
                                        .build()))
                .block();
        StepVerifier.create(tenant("t1", repository.abortUpload(registered)))
                .expectNext(false)
                .verifyComplete();
        assertThat(jdbc.queryForObject(
                        "SELECT deleted FROM kb_document WHERE id=?", Integer.class, registered.documentId()))
                .isZero();
    }

    @Test
    void missingTenantAndAtomicIntentRollbackAreFailClosed() {
        document(7, "t1", true);
        TenantContext.clear();
        StepVerifier.create(repository.acceptDelete(7L, "http://meili", "kb_chunks"))
                .expectError()
                .verify();
        jdbc.execute("ALTER TABLE kb_document ADD CONSTRAINT reject_doc7_delete CHECK (id<>7 OR deleted=0)");
        StepVerifier.create(tenant("t1", repository.acceptDelete(7L, "http://meili", "kb_chunks")))
                .expectError()
                .verify();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM kb_document_cleanup WHERE document_id=7", Long.class))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT deleted FROM kb_document WHERE id=7", Integer.class))
                .isZero();
    }

    @Test
    void concurrentIngestionClaimHasOneWinnerIncludingMissingWorkRow() {
        document(8, "t1", true);
        var claims = reactor.core.publisher.Flux.merge(
                        tenant("t1", repository.claimIngestion(8L)), tenant("t1", repository.claimIngestion(8L)))
                .collectList()
                .block();
        assertThat(claims).hasSize(1);
    }

    @Test
    void registeredUploadDoesNotAppearInCleanupScanAndFailureKeepsConfirmedObjectStage() {
        var lease = tenant("t1", repository.reserveUpload("文档", "local", "bucket", "kb/once", null, null))
                .block();
        tenant("t1", repository.bindUploadedKey(lease, "kb/once")).block();
        tenant(
                        "t1",
                        repository.registerUpload(
                                lease,
                                KnowledgeDocument.builder()
                                        .spaceId(2L)
                                        .title("文档")
                                        .sourceType(KnowledgeDocument.SourceType.UPLOAD)
                                        .parseStatus(KnowledgeDocument.ParseStatus.PENDING)
                                        .indexStatus(KnowledgeDocument.IndexStatus.NONE)
                                        .chunkCount(0)
                                        .build()))
                .block();
        StepVerifier.create(repository.scanCleanups("t1", LocalDateTime.now().plusDays(10), 50))
                .assertNext(ids -> assertThat(ids).doesNotContain(lease.documentId()))
                .verifyComplete();
    }

    @Test
    void uploadFailureLeavesPersistentCredentialAndExpiredUploadBecomesCompensatable() {
        var lease = tenant("t1", repository.reserveUpload("文档", "local", "bucket", "kb/exclusive", null, null))
                .block();
        tenant("t1", repository.failUpload(lease, "登记失败；补偿失败", false)).block();
        StepVerifier.create(tenant("t1", repository.findCleanup(lease.documentId())))
                .assertNext(c -> {
                    assertThat(c.status().name()).isEqualTo("FAILED");
                    assertThat(c.objectKey()).isEqualTo("kb/exclusive");
                })
                .verifyComplete();
        jdbc.update(
                "UPDATE kb_document_cleanup SET next_retry_at=? WHERE document_id=?",
                LocalDateTime.now().minusMinutes(1),
                lease.documentId());
        StepVerifier.create(repository.claimCleanup("t1", lease.documentId()))
                .expectNextCount(1)
                .verifyComplete();
    }
}
