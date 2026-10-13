package com.apex.scaffold.file.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.file.application.dto.chunk.ChunkInitRequest;
import com.apex.scaffold.file.application.dto.chunk.ChunkMergeRequest;
import com.apex.scaffold.file.application.dto.chunk.ChunkUploadRequest;
import com.apex.scaffold.file.domain.model.entity.FileChunkEntity;
import com.apex.scaffold.file.domain.model.entity.FileEntity;
import com.apex.scaffold.file.domain.repository.FileChunkRepository;
import com.apex.scaffold.file.domain.repository.FileRepository;
import com.apex.scaffold.file.domain.service.StorageService;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChunkUploadService 分片上传单元测试")
class ChunkUploadServiceTest {

    @Mock
    private StorageService storageService;

    @Mock
    private FileRepository fileRepository;

    @Mock
    private FileChunkRepository fileChunkRepository;

    private ChunkUploadService chunkUploadService;

    @BeforeEach
    void setUp() {
        chunkUploadService = new ChunkUploadService(storageService, fileRepository, fileChunkRepository, "apex", 300);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private FileEntity fileWithId(Long id) {
        FileEntity file = new FileEntity();
        file.setId(id);
        return file;
    }

    private FileChunkEntity chunk(Long fileId, int index, int totalChunks, long size) {
        FileChunkEntity c = new FileChunkEntity();
        c.setFileId(fileId);
        c.setChunkIndex(index);
        c.setTotalChunks(totalChunks);
        c.setChunkSize(size);
        c.setBucketName("apex");
        c.setObjectName("chunks/" + fileId + "/" + index);
        return c;
    }

    @Test
    @DisplayName("init：分片大小缺省时应自动计算并落库用户 ID")
    void init_autoChunkSize() {
        TenantContext.setTenantId("42");
        AtomicReference<FileEntity> saved = new AtomicReference<>();
        when(fileRepository.save(any(FileEntity.class))).thenAnswer(inv -> {
            FileEntity f = inv.getArgument(0);
            f.setId(100L);
            saved.set(f);
            return Mono.just(f);
        });

        ChunkInitRequest request = ChunkInitRequest.builder()
                .fileName("movie.mp4")
                .fileSize(10_000_000L)
                .totalChunks(4)
                .build();

        StepVerifier.create(chunkUploadService.init(request))
                .assertNext(result -> {
                    assertThat(result.fileId()).isEqualTo(100L);
                    assertThat(result.bucketName()).isEqualTo("apex");
                    assertThat(result.totalChunks()).isEqualTo(4);
                    assertThat(result.chunkSize()).isEqualTo(2_500_000L);
                    assertThat(result.uploadedChunks()).isEmpty();
                })
                .verifyComplete();

        assertThat(saved.get().getBusinessType()).isEqualTo("CHUNK");
        assertThat(saved.get().getStorageStatus()).isEqualTo("UPLOADING");
        assertThat(saved.get().getUserId()).isEqualTo(42L);
    }

    @Test
    @DisplayName("init：显式分片大小应原样使用")
    void init_explicitChunkSize() {
        TenantContext.clear();
        when(fileRepository.save(any(FileEntity.class))).thenAnswer(inv -> {
            FileEntity f = inv.getArgument(0);
            f.setId(1L);
            return Mono.just(f);
        });

        ChunkInitRequest request = ChunkInitRequest.builder()
                .fileName("a.bin")
                .fileSize(100L)
                .totalChunks(2)
                .chunkSize(64L)
                .build();

        StepVerifier.create(chunkUploadService.init(request))
                .assertNext(result -> assertThat(result.chunkSize()).isEqualTo(64L))
                .verifyComplete();
    }

    @Test
    @DisplayName("uploadChunk：空内容应抛出参数错误")
    void uploadChunk_emptyContent() {
        ChunkUploadRequest request = ChunkUploadRequest.builder()
                .fileId(1L)
                .chunkIndex(0)
                .totalChunks(2)
                .build();
        StepVerifier.create(chunkUploadService.uploadChunk(request, new byte[0]))
                .expectErrorSatisfies(ex -> {
                    assertThat(ex).isInstanceOf(BusinessException.class);
                    assertThat(((BusinessException) ex).getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode());
                })
                .verify();
    }

    @Test
    @DisplayName("uploadChunk：文件记录不存在应返回未找到")
    void uploadChunk_fileNotFound() {
        when(fileRepository.findById(1L)).thenReturn(Mono.empty());
        ChunkUploadRequest request = ChunkUploadRequest.builder()
                .fileId(1L)
                .chunkIndex(0)
                .totalChunks(2)
                .build();
        StepVerifier.create(chunkUploadService.uploadChunk(request, new byte[] {1, 2, 3}))
                .expectErrorSatisfies(ex -> {
                    assertThat(ex).isInstanceOf(BusinessException.class);
                    assertThat(((BusinessException) ex).getCode()).isEqualTo(ResultCode.NOT_FOUND.getCode());
                })
                .verify();
    }

    @Test
    @DisplayName("uploadChunk：分片已存在应幂等返回既有对象键")
    void uploadChunk_idempotent() {
        when(fileRepository.findById(1L)).thenReturn(Mono.just(fileWithId(1L)));
        FileChunkEntity existing = chunk(1L, 0, 2, 3L);
        when(fileChunkRepository.findByFileIdAndIndex(1L, 0)).thenReturn(Mono.just(existing));
        when(storageService.upload(
                        eq("apex"), eq("chunks/1/0"), any(InputStream.class), eq("application/octet-stream")))
                .thenReturn(Mono.just("chunks/1/0"));

        ChunkUploadRequest request = ChunkUploadRequest.builder()
                .fileId(1L)
                .chunkIndex(0)
                .totalChunks(2)
                .build();
        StepVerifier.create(chunkUploadService.uploadChunk(request, new byte[] {9, 9, 9}))
                .assertNext(result -> {
                    assertThat(result.uploaded()).isFalse();
                    assertThat(result.objectName()).isEqualTo("chunks/1/0");
                })
                .verifyComplete();
        verify(fileChunkRepository, never()).save(any(FileChunkEntity.class));
    }

    @Test
    @DisplayName("uploadChunk：新分片应上传存储并入库")
    void uploadChunk_newChunk() {
        when(fileRepository.findById(1L)).thenReturn(Mono.just(fileWithId(1L)));
        when(fileChunkRepository.findByFileIdAndIndex(1L, 1)).thenReturn(Mono.empty());
        when(storageService.upload(
                        eq("apex"), eq("chunks/1/1"), any(InputStream.class), eq("application/octet-stream")))
                .thenReturn(Mono.just("chunks/1/1"));
        when(fileChunkRepository.save(any(FileChunkEntity.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        ChunkUploadRequest request = ChunkUploadRequest.builder()
                .fileId(1L)
                .chunkIndex(1)
                .totalChunks(2)
                .build();
        StepVerifier.create(chunkUploadService.uploadChunk(request, new byte[] {1, 2, 3, 4}))
                .assertNext(result -> {
                    assertThat(result.uploaded()).isTrue();
                    assertThat(result.objectName()).isEqualTo("chunks/1/1");
                    assertThat(result.chunkSize()).isEqualTo(4L);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("merge：文件记录不存在应返回未找到")
    void merge_fileNotFound() {
        when(fileRepository.findById(1L)).thenReturn(Mono.empty());
        ChunkMergeRequest request = ChunkMergeRequest.builder().fileId(1L).build();
        StepVerifier.create(chunkUploadService.merge(request))
                .expectErrorSatisfies(ex -> {
                    assertThat(ex).isInstanceOf(BusinessException.class);
                    assertThat(((BusinessException) ex).getCode()).isEqualTo(ResultCode.NOT_FOUND.getCode());
                })
                .verify();
    }

    @Test
    @DisplayName("merge：无任何分片应返回业务错误")
    void merge_noChunks() {
        when(fileRepository.findById(1L)).thenReturn(Mono.just(fileWithId(1L)));
        when(fileChunkRepository.findByFileIdOrderByIndex(1L)).thenReturn(Flux.empty());
        ChunkMergeRequest request = ChunkMergeRequest.builder().fileId(1L).build();
        StepVerifier.create(chunkUploadService.merge(request))
                .expectErrorSatisfies(ex -> {
                    assertThat(ex).isInstanceOf(BusinessException.class);
                    assertThat(((BusinessException) ex).getCode()).isEqualTo(ResultCode.BUSINESS_ERROR.getCode());
                })
                .verify();
    }

    @Test
    @DisplayName("merge：分片不完整应返回业务错误")
    void merge_incomplete() {
        when(fileRepository.findById(1L)).thenReturn(Mono.just(fileWithId(1L)));
        when(fileChunkRepository.findByFileIdOrderByIndex(1L))
                .thenReturn(Flux.just(chunk(1L, 0, 3, 5L), chunk(1L, 1, 3, 5L)));
        ChunkMergeRequest request = ChunkMergeRequest.builder().fileId(1L).build();
        StepVerifier.create(chunkUploadService.merge(request))
                .expectErrorSatisfies(ex -> {
                    assertThat(ex).isInstanceOf(BusinessException.class);
                    assertThat(((BusinessException) ex).getCode()).isEqualTo(ResultCode.BUSINESS_ERROR.getCode());
                })
                .verify();
    }

    @Test
    @DisplayName("merge：全部分片就绪应拼接落库并清理分片")
    void merge_happyPath() {
        FileEntity file = fileWithId(1L);
        file.setOriginalName("final.mp4");
        when(fileRepository.findById(1L)).thenReturn(Mono.just(file));
        when(fileChunkRepository.findByFileIdOrderByIndex(1L))
                .thenReturn(Flux.just(chunk(1L, 0, 2, 3L), chunk(1L, 1, 2, 3L)));
        when(storageService.download("apex", "chunks/1/0"))
                .thenReturn(Mono.just(new ByteArrayInputStream(new byte[] {1, 2, 3})));
        when(storageService.download("apex", "chunks/1/1"))
                .thenReturn(Mono.just(new ByteArrayInputStream(new byte[] {4, 5, 6})));
        when(storageService.upload(eq("apex"), eq("final.mp4"), any(InputStream.class), eq("application/octet-stream")))
                .thenReturn(Mono.just("final.mp4"));
        when(fileRepository.save(any(FileEntity.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(storageService.getPresignedUrl("apex", "final.mp4", 300)).thenReturn(Mono.just("http://x/final.mp4"));
        when(storageService.delete(anyString(), anyString())).thenReturn(Mono.just(true));
        when(fileChunkRepository.deleteByFileId(1L)).thenReturn(Mono.empty());

        ChunkMergeRequest request = ChunkMergeRequest.builder().fileId(1L).build();
        StepVerifier.create(chunkUploadService.merge(request))
                .assertNext(result -> {
                    assertThat(result.fileId()).isEqualTo(1L);
                    assertThat(result.fileName()).isEqualTo("final.mp4");
                    assertThat(result.url()).isEqualTo("http://x/final.mp4");
                    assertThat(result.fileSize()).isEqualTo(6L);
                })
                .verifyComplete();
        verify(fileChunkRepository).deleteByFileId(1L);
    }

    @Test
    @DisplayName("status：无分片时返回空的分片进度")
    void status_empty() {
        when(fileRepository.findById(1L)).thenReturn(Mono.just(fileWithId(1L)));
        when(fileChunkRepository.findByFileIdOrderByIndex(1L)).thenReturn(Flux.empty());
        StepVerifier.create(chunkUploadService.status(1L))
                .assertNext(result -> {
                    assertThat(result.fileId()).isEqualTo(1L);
                    assertThat(result.chunkSize()).isNull();
                    assertThat(result.uploadedChunks()).isEmpty();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("status：应返回已上传分片序号列表")
    void status_withChunks() {
        when(fileRepository.findById(1L)).thenReturn(Mono.just(fileWithId(1L)));
        when(fileChunkRepository.findByFileIdOrderByIndex(1L))
                .thenReturn(Flux.just(chunk(1L, 0, 2, 5L), chunk(1L, 1, 2, 5L)));
        StepVerifier.create(chunkUploadService.status(1L))
                .assertNext(result -> {
                    assertThat(result.chunkSize()).isEqualTo(5L);
                    assertThat(result.totalChunks()).isEqualTo(2);
                    assertThat(result.uploadedChunks()).containsExactly(0, 1);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("status：文件不存在应返回未找到")
    void status_notFound() {
        when(fileRepository.findById(1L)).thenReturn(Mono.empty());
        StepVerifier.create(chunkUploadService.status(1L))
                .expectErrorSatisfies(ex -> {
                    assertThat(ex).isInstanceOf(BusinessException.class);
                    assertThat(((BusinessException) ex).getCode()).isEqualTo(ResultCode.NOT_FOUND.getCode());
                })
                .verify();
    }

    @Test
    @DisplayName("merge：请求指定文件名与内容类型应优先于登记值")
    void merge_requestOverridesName() {
        FileEntity file = fileWithId(1L);
        file.setOriginalName("original.bin");
        file.setContentType("application/octet-stream");
        when(fileRepository.findById(1L)).thenReturn(Mono.just(file));
        when(fileChunkRepository.findByFileIdOrderByIndex(1L)).thenReturn(Flux.just(chunk(1L, 0, 1, 2L)));
        when(storageService.download("apex", "chunks/1/0"))
                .thenReturn(Mono.just(new ByteArrayInputStream(new byte[] {7, 7})));
        when(storageService.upload(eq("apex"), eq("renamed.zip"), any(InputStream.class), eq("application/zip")))
                .thenReturn(Mono.just("renamed.zip"));
        when(fileRepository.save(any(FileEntity.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(storageService.getPresignedUrl("apex", "renamed.zip", 300)).thenReturn(Mono.just("http://x/renamed.zip"));
        when(storageService.delete(anyString(), anyString())).thenReturn(Mono.just(true));
        when(fileChunkRepository.deleteByFileId(1L)).thenReturn(Mono.empty());

        ChunkMergeRequest request = ChunkMergeRequest.builder()
                .fileId(1L)
                .fileName("renamed.zip")
                .contentType("application/zip")
                .md5("abc")
                .build();
        StepVerifier.create(chunkUploadService.merge(request))
                .assertNext(result -> {
                    assertThat(result.fileName()).isEqualTo("renamed.zip");
                    assertThat(result.md5()).isEqualTo("abc");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("merge：缺少最终文件名应返回参数错误")
    void merge_missingFinalName() {
        FileEntity file = fileWithId(1L);
        when(fileRepository.findById(1L)).thenReturn(Mono.just(file));
        when(fileChunkRepository.findByFileIdOrderByIndex(1L)).thenReturn(Flux.just(chunk(1L, 0, 1, 2L)));
        ChunkMergeRequest request = ChunkMergeRequest.builder().fileId(1L).build();
        StepVerifier.create(chunkUploadService.merge(request))
                .expectErrorSatisfies(ex -> {
                    assertThat(ex).isInstanceOf(BusinessException.class);
                    assertThat(((BusinessException) ex).getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode());
                })
                .verify();
    }

    @Test
    @DisplayName("init：文件大小与分片数异常时应回退默认分片大小")
    void init_invalidSizesFallback() {
        when(fileRepository.save(any(FileEntity.class))).thenAnswer(inv -> {
            FileEntity f = inv.getArgument(0);
            f.setId(9L);
            return Mono.just(f);
        });
        ChunkInitRequest request = ChunkInitRequest.builder()
                .fileName("x.bin")
                .fileSize(0L)
                .totalChunks(0)
                .build();
        StepVerifier.create(chunkUploadService.init(request))
                .assertNext(result -> assertThat(result.chunkSize()).isEqualTo(5 * 1024 * 1024L))
                .verifyComplete();
    }
}
