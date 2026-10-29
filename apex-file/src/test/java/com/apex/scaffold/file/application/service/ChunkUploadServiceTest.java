package com.apex.scaffold.file.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.file.application.dto.chunk.*;
import com.apex.scaffold.file.domain.enums.StorageType;
import com.apex.scaffold.file.domain.model.entity.FileChunkEntity;
import com.apex.scaffold.file.domain.model.entity.FileEntity;
import com.apex.scaffold.file.domain.repository.FileChunkRepository;
import com.apex.scaffold.file.domain.repository.FileRepository;
import com.apex.scaffold.file.domain.service.StorageService;
import com.apex.scaffold.file.domain.service.StorageServiceProvider;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/** 验证分片会话归属、对象缺失、失败补偿及元数据保留。 */
@ExtendWith(MockitoExtension.class)
class ChunkUploadServiceTest {
    @Mock
    private StorageService storage;

    @Mock
    private FileRepository files;

    @Mock
    private FileChunkRepository chunks;

    private ChunkUploadService service;

    /** 创建使用明确提供商的被测服务。 */
    @BeforeEach
    void setUp() {
        service = new ChunkUploadService(
                new StorageServiceProvider(Map.of(StorageType.LOCAL, storage), StorageType.LOCAL),
                files,
                chunks,
                "apex",
                300,
                "local");
    }

    /** 注入与租户编号不同的可信用户身份。 */
    private <T> Mono<T> authenticated(Mono<T> operation) {
        return operation.contextWrite(c -> c.put(FileAccessContext.KEY, new FileAccessContext("42", 7L)));
    }

    /** 创建尚未完成的归属完整主记录。 */
    private FileEntity file() {
        FileEntity f = new FileEntity();
        f.setId(1L);
        f.setTenantId("42");
        f.setUserId(7L);
        f.setStorageType("local");
        f.setBucketName("apex");
        f.setStorageStatus("UPLOADING");
        f.setOriginalName("movie.mp4");
        f.setFileSize(6L);
        f.setTotalChunks(2);
        f.setChunkSize(3L);
        return f;
    }

    /** 创建归属完整的已存储分片。 */
    private FileChunkEntity part(int index, int total) {
        FileChunkEntity p = FileChunkEntity.stored(1L, index, 3L, total, null, "chunks/1/" + index, "apex", 7L);
        p.setTenantId("42");
        p.setStorageType("local");
        return p;
    }

    /** 验证初始化使用用户编号而非租户编号。 */
    @Test
    void initPersistsTrustedUser() {
        when(files.save(any())).thenAnswer(inv -> {
            FileEntity f = inv.getArgument(0);
            assertThat(f.getUserId()).isEqualTo(7L);
            assertThat(f.getTenantId()).isEqualTo("42");
            assertThat(f.getStorageType()).isEqualTo("local");
            f.setId(1L);
            return Mono.just(f);
        });
        StepVerifier.create(authenticated(service.init(ChunkInitRequest.builder()
                        .fileName("movie.mp4")
                        .fileSize(6L)
                        .totalChunks(2)
                        .build())))
                .assertNext(r -> {
                    assertThat(r.chunkSize()).isEqualTo(3L);
                    assertThat(r.totalChunks()).isEqualTo(2);
                })
                .verifyComplete();
    }

    /** 无分片时也必须从持久化会话恢复初始化约定。 */
    @Test
    void statusRestoresInitializationBeforeAnyChunk() {
        when(files.save(any())).thenAnswer(inv -> {
            FileEntity saved = inv.getArgument(0);
            saved.setId(1L);
            when(files.findById(1L)).thenReturn(Mono.just(saved));
            return Mono.just(saved);
        });
        when(chunks.findByFileIdOrderByIndex(1L)).thenReturn(Flux.empty());
        StepVerifier.create(authenticated(service.init(ChunkInitRequest.builder()
                                .fileName("movie.mp4")
                                .fileSize(6L)
                                .totalChunks(2)
                                .chunkSize(3L)
                                .contentType("video/mp4")
                                .md5("expected-md5")
                                .build())
                        .flatMap(result -> service.status(result.fileId()))))
                .assertNext(result -> {
                    assertThat(result.totalChunks()).isEqualTo(2);
                    assertThat(result.chunkSize()).isEqualTo(3L);
                })
                .verifyComplete();
    }

    /** 验证无身份及非法初始化参数均不产生记录。 */
    @Test
    void rejectsInvalidInitialization() {
        ChunkInitRequest request = ChunkInitRequest.builder()
                .fileName("x.bin")
                .fileSize(0L)
                .totalChunks(0)
                .build();
        StepVerifier.create(service.init(request))
                .expectError(BusinessException.class)
                .verify();
        StepVerifier.create(authenticated(service.init(request)))
                .expectError(BusinessException.class)
                .verify();
        verifyNoInteractions(files, storage);
    }

    /** 验证历史无归属和其他用户会话拒绝访问。 */
    @Test
    void rejectsForeignAndUnknownOwnership() {
        FileEntity f = file();
        f.setUserId(8L);
        when(files.findById(1L)).thenReturn(Mono.just(f));
        StepVerifier.create(authenticated(service.status(1L)))
                .expectError(BusinessException.class)
                .verify();
        f.setUserId(7L);
        f.setTenantId(null);
        StepVerifier.create(authenticated(service.status(1L)))
                .expectError(BusinessException.class)
                .verify();
        verifyNoInteractions(chunks, storage);
    }

    /** 验证缺失主记录返回错误。 */
    @Test
    void rejectsMissingFile() {
        when(files.findById(1L)).thenReturn(Mono.empty());
        StepVerifier.create(authenticated(service.status(1L)))
                .expectError(BusinessException.class)
                .verify();
    }

    /** 验证已上传分片幂等返回，不再次写对象。 */
    @Test
    void idempotentChunk() {
        when(files.findById(1L)).thenReturn(Mono.just(file()));
        when(chunks.findByFileIdAndIndex(1L, 0)).thenReturn(Mono.just(part(0, 2)));
        StepVerifier.create(authenticated(service.uploadChunk(
                        ChunkUploadRequest.builder()
                                .fileId(1L)
                                .chunkIndex(0)
                                .totalChunks(2)
                                .build(),
                        new byte[] {1})))
                .assertNext(r -> assertThat(r.uploaded()).isFalse())
                .verifyComplete();
        verifyNoInteractions(storage);
    }

    /** 验证新分片持久化实际返回键和完整归属。 */
    @Test
    void storesActualChunkKey() {
        when(files.findById(1L)).thenReturn(Mono.just(file()));
        when(chunks.findByFileIdAndIndex(1L, 0)).thenReturn(Mono.empty());
        when(storage.upload(eq("apex"), anyString(), any(InputStream.class), anyString()))
                .thenReturn(Mono.just("actual-part"));
        when(chunks.save(any())).thenAnswer(inv -> {
            FileChunkEntity p = inv.getArgument(0);
            assertThat(p.getUserId()).isEqualTo(7L);
            assertThat(p.getTenantId()).isEqualTo("42");
            assertThat(p.getObjectName()).isEqualTo("actual-part");
            return Mono.just(p);
        });
        StepVerifier.create(authenticated(service.uploadChunk(
                        ChunkUploadRequest.builder()
                                .fileId(1L)
                                .chunkIndex(0)
                                .totalChunks(2)
                                .build(),
                        new byte[] {1, 2, 3})))
                .assertNext(r -> assertThat(r.objectName()).isEqualTo("actual-part"))
                .verifyComplete();
    }

    /** 首片也不得自行改变初始化约定的总片数。 */
    @Test
    void rejectsChangedTotalOnFirstChunk() {
        when(files.save(any())).thenAnswer(inv -> {
            FileEntity saved = inv.getArgument(0);
            saved.setId(1L);
            when(files.findById(1L)).thenReturn(Mono.just(saved));
            return Mono.just(saved);
        });
        lenient().when(chunks.findByFileIdAndIndex(1L, 0)).thenReturn(Mono.empty());
        lenient()
                .when(storage.upload(anyString(), anyString(), any(), anyString()))
                .thenReturn(Mono.just("bad"));
        lenient().when(chunks.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        StepVerifier.create(authenticated(service.init(ChunkInitRequest.builder()
                                .fileName("movie.mp4")
                                .fileSize(6L)
                                .totalChunks(2)
                                .build())
                        .flatMap(result -> service.uploadChunk(
                                ChunkUploadRequest.builder()
                                        .fileId(result.fileId())
                                        .chunkIndex(0)
                                        .totalChunks(1)
                                        .build(),
                                new byte[] {1}))))
                .expectError(BusinessException.class)
                .verify();
        verifyNoInteractions(storage);
    }

    /** 验证空分片在访问对象存储前被拒绝。 */
    @Test
    void rejectsEmptyChunk() {
        StepVerifier.create(authenticated(service.uploadChunk(
                        ChunkUploadRequest.builder()
                                .fileId(1L)
                                .chunkIndex(0)
                                .totalChunks(2)
                                .build(),
                        new byte[0])))
                .expectError(BusinessException.class)
                .verify();
        verifyNoInteractions(storage, files);
    }

    /** 验证无分片及分片不完整时不能合并。 */
    @Test
    void rejectsIncompleteParts() {
        when(files.findById(1L)).thenReturn(Mono.just(file()));
        when(chunks.findByFileIdOrderByIndex(1L)).thenReturn(Flux.empty(), Flux.just(part(0, 2)));
        for (int i = 0; i < 2; i++) {
            StepVerifier.create(authenticated(
                            service.merge(ChunkMergeRequest.builder().fileId(1L).build())))
                    .expectError(BusinessException.class)
                    .verify();
        }
        verifyNoInteractions(storage);
    }

    /** 验证数据库记录存在但真实分片缺失时不上传合并对象。 */
    @Test
    void missingObjectFailsMerge() {
        when(files.claimMerge(any())).thenReturn(Mono.just(true));
        when(files.findById(1L)).thenReturn(Mono.just(file()));
        when(chunks.findByFileIdOrderByIndex(1L)).thenReturn(Flux.just(part(0, 2), part(1, 2)));
        when(storage.download("apex", "chunks/1/0"))
                .thenReturn(Mono.just(new ByteArrayInputStream(new byte[] {1, 2, 3})));
        when(storage.download("apex", "chunks/1/1")).thenReturn(Mono.empty());
        when(files.releaseMerge(any())).thenReturn(Mono.just(true));
        StepVerifier.create(authenticated(
                        service.merge(ChunkMergeRequest.builder().fileId(1L).build())))
                .expectError(BusinessException.class)
                .verify();
        verify(storage, never()).upload(anyString(), anyString(), any(), anyString());
        verify(files).releaseMerge(any());
        verify(chunks, never()).deleteByFileId(anyLong());
    }

    /** 数据库抢占失败的竞争调用不得下载或创建最终对象。 */
    @Test
    void losingMergeDoesNotCreateObject() {
        when(files.findById(1L)).thenReturn(Mono.just(file()));
        when(chunks.findByFileIdOrderByIndex(1L)).thenReturn(Flux.just(part(0, 2), part(1, 2)));
        when(files.claimMerge(any())).thenReturn(Mono.just(false));
        StepVerifier.create(authenticated(
                        service.merge(ChunkMergeRequest.builder().fileId(1L).build())))
                .expectError(BusinessException.class)
                .verify();
        verifyNoInteractions(storage);
    }

    /** 部分清理失败后，可在可信身份下重试，包括已删除的对象。 */
    @Test
    void retriesPartialCleanup() {
        FileEntity saved = file();
        saved.setStorageStatus("STORED");
        when(files.findById(1L)).thenReturn(Mono.just(saved));
        when(chunks.findByFileIdOrderByIndex(1L)).thenReturn(Flux.just(part(0, 2), part(1, 2)));
        when(storage.delete("apex", "chunks/1/0")).thenReturn(Mono.just(true), Mono.just(false));
        when(storage.exists("apex", "chunks/1/0")).thenReturn(Mono.just(false));
        when(storage.delete("apex", "chunks/1/1"))
                .thenReturn(Mono.error(new IllegalStateException("unavailable")), Mono.just(true));
        when(chunks.deleteByFileId(1L)).thenReturn(Mono.empty());
        StepVerifier.create(authenticated(service.retryCleanup(1L))).verifyComplete();
        verify(chunks, never()).deleteByFileId(anyLong());
        StepVerifier.create(authenticated(service.retryCleanup(1L))).verifyComplete();
        verify(chunks).deleteByFileId(1L);
    }

    /** 本地合并完成清理后返回编码后的授权下载地址，云后端仍使用配置的签名有效期。 */
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(
            value = StorageType.class,
            names = {"LOCAL", "MINIO"})
    void mergeUsesRecordedBackendAccessUrl(StorageType backend) {
        service = new ChunkUploadService(
                new StorageServiceProvider(Map.of(backend, storage), backend), files, chunks, "apex", 123, "local");
        FileEntity file = file();
        file.setStorageType(backend.name());
        FileChunkEntity first = part(0, 2);
        FileChunkEntity second = part(1, 2);
        first.setStorageType(backend.name());
        second.setStorageType(backend.name());
        when(files.findById(1L)).thenReturn(Mono.just(file));
        when(files.claimMerge(any())).thenReturn(Mono.just(true));
        when(chunks.findByFileIdOrderByIndex(1L)).thenReturn(Flux.just(first, second));
        when(storage.download(anyString(), anyString()))
                .thenAnswer(inv -> Mono.just(new ByteArrayInputStream(new byte[] {1, 2, 3})));
        when(storage.upload(eq("apex"), anyString(), any(InputStream.class), anyString()))
                .thenAnswer(inv -> {
                    InputStream merged = inv.getArgument(2);
                    assertThat(merged.readAllBytes()).containsExactly(1, 2, 3, 1, 2, 3);
                    return Mono.just("dir/final movie.mp4");
                });
        when(files.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(storage.delete(anyString(), anyString())).thenReturn(Mono.just(true));
        when(chunks.deleteByFileId(1L)).thenReturn(Mono.empty());
        if (backend == StorageType.MINIO) {
            when(storage.getPresignedUrl("apex", "dir/final movie.mp4", 123)).thenReturn(Mono.just("signed-url"));
        }
        StepVerifier.create(authenticated(
                        service.merge(ChunkMergeRequest.builder().fileId(1L).build())))
                .assertNext(result -> {
                    assertThat(result.fileName()).isEqualTo("dir/final movie.mp4");
                    assertThat(result.url())
                            .isEqualTo(
                                    backend == StorageType.LOCAL
                                            ? "/api/v1/file/content?bucketName=apex&fileName=dir/final%20movie.mp4"
                                            : "signed-url");
                    assertThat(file.getStorageStatus()).isEqualTo("STORED");
                })
                .verifyComplete();
        verify(chunks).deleteByFileId(1L);
        if (backend == StorageType.LOCAL) {
            verify(storage, never()).getPresignedUrl(anyString(), anyString(), anyInt());
        } else {
            verify(storage).getPresignedUrl("apex", "dir/final movie.mp4", 123);
        }
    }

    /** 验证合并成功而对象清理失败时保留分片元数据。 */
    @Test
    void failedCleanupRetainsMetadata() {
        when(files.claimMerge(any())).thenReturn(Mono.just(true));
        when(storage.exists(anyString(), anyString())).thenReturn(Mono.just(true));
        when(files.findById(1L)).thenReturn(Mono.just(file()));
        when(chunks.findByFileIdOrderByIndex(1L)).thenReturn(Flux.just(part(0, 2), part(1, 2)));
        when(storage.download(anyString(), anyString()))
                .thenAnswer(inv -> Mono.just(new ByteArrayInputStream(new byte[] {1, 2, 3})));
        when(storage.upload(eq("apex"), anyString(), any(InputStream.class), anyString()))
                .thenReturn(Mono.just("actual-final"));
        when(files.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        lenient()
                .when(storage.getPresignedUrl("apex", "actual-final", 300))
                .thenReturn(Mono.error(new UnsupportedOperationException("local has no signing")));
        when(storage.delete(anyString(), anyString())).thenReturn(Mono.just(false));
        StepVerifier.create(authenticated(
                        service.merge(ChunkMergeRequest.builder().fileId(1L).build())))
                .assertNext(r -> {
                    assertThat(r.fileName()).isEqualTo("actual-final");
                    assertThat(r.url()).isEqualTo("/api/v1/file/content?bucketName=apex&fileName=actual-final");
                })
                .verifyComplete();
        verify(chunks, never()).deleteByFileId(anyLong());
    }
}
