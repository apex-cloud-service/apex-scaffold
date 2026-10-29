package com.apex.scaffold.file.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.file.domain.enums.StorageType;
import com.apex.scaffold.file.domain.model.entity.FileEntity;
import com.apex.scaffold.file.domain.repository.FileRepository;
import com.apex.scaffold.file.domain.service.StorageService;
import com.apex.scaffold.file.domain.service.StorageServiceProvider;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/** 文件归属授权、类型路由和落库失败补偿测试。 */
class FileServiceTest {
    private final StorageService storage = mock(StorageService.class);
    private final StorageService oldStorage = mock(StorageService.class);
    private final FileRepository repository = mock(FileRepository.class);
    private FileService service;
    private static final byte[] JPG = {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0};

    @Test
    void usesSingleImplicitConstructorAndContextAssembly() {
        assertThat(FileService.class.getDeclaredConstructors()).hasSize(1);
        assertThat(FileService.class.getDeclaredConstructors()[0].getDeclaredAnnotations())
                .isEmpty();
        try (var context = new org.springframework.context.annotation.AnnotationConfigApplicationContext()) {
            context.registerBean(StorageService.class, () -> storage);
            context.registerBean(FileRepository.class, () -> repository);
            context.registerBean(
                    StorageServiceProvider.class,
                    () -> new StorageServiceProvider(Map.of(StorageType.LOCAL, storage), StorageType.LOCAL));
            context.register(FileService.class);
            context.refresh();
            assertThat(context.getBean(FileService.class)).isNotNull();
        }
    }

    /** 初始化两种提供商以模拟默认类型切换。 */
    @BeforeEach
    void setup() {
        service = new FileService(
                storage,
                repository,
                new StorageServiceProvider(
                        Map.of(StorageType.LOCAL, storage, StorageType.MINIO, oldStorage), StorageType.LOCAL),
                "local",
                "apex",
                1024,
                300);
    }

    /** @param operation 文件操作 @param <T> 结果类型 @return 带可信身份的订阅链 */
    private <T> Mono<T> authenticated(Mono<T> operation) {
        return operation.contextWrite(
                context -> context.put(FileAccessContext.KEY, new FileAccessContext("tenant-a", 7L)));
    }

    /** @return 历史 MINIO 文件的完整归属记录 */
    private FileEntity owned() {
        FileEntity file = new FileEntity();
        file.setId(1L);
        file.setTenantId("tenant-a");
        file.setUserId(7L);
        file.setStorageType("MINIO");
        file.setBucketName("apex");
        file.setFileName("actual.jpg");
        file.setStorageStatus("STORED");
        return file;
    }

    /** 上传记录必须包含独立租户、真实用户、提供商和存储返回键。 */
    @Test
    void persistsOwnership() {
        when(storage.upload(eq("apex"), anyString(), any(InputStream.class), eq("image/jpeg")))
                .thenReturn(Mono.just("actual.jpg"));
        when(repository.save(any())).thenAnswer(invocation -> {
            FileEntity file = invocation.getArgument(0);
            assertThat(file.getTenantId()).isEqualTo("tenant-a");
            assertThat(file.getUserId()).isEqualTo(7L);
            assertThat(file.getStorageType()).isEqualTo("local");
            assertThat(file.getFileName()).isEqualTo("actual.jpg");
            return Mono.just(file);
        });
        FileEntity file = owned();
        file.setStorageType("local");
        when(repository.findByStorageLocation("apex", "actual.jpg")).thenReturn(Mono.just(file));
        StepVerifier.create(authenticated(service.upload("photo.jpg", JPG, "image/jpeg", null, null)))
                .assertNext(result -> {
                    assertThat(result.fileName()).isEqualTo("actual.jpg");
                    assertThat(result.url()).isEqualTo("/api/v1/file/content?bucketName=apex&fileName=actual.jpg");
                    assertThat(result.expiresIn()).isZero();
                })
                .verifyComplete();
    }

    /** 本地文件返回需认证的业务下载地址，不调用不受支持的预签名。 */
    @Test
    void localUrlUsesAuthorizedDownload() {
        FileEntity file = owned();
        file.setStorageType("local");
        file.setFileName("dir/a b.jpg");
        when(repository.findByStorageLocation("apex", "dir/a b.jpg")).thenReturn(Mono.just(file));
        StepVerifier.create(authenticated(service.getUrl("apex", "dir/a b.jpg")))
                .expectNext("/api/v1/file/content?bucketName=apex&fileName=dir/a%20b.jpg")
                .verifyComplete();
        verifyNoInteractions(storage);
    }

    /** 无认证身份时不得操作任何对象。 */
    @Test
    void rejectsMissingIdentity() {
        StepVerifier.create(service.exists("apex", "actual.jpg"))
                .expectError(BusinessException.class)
                .verify();
        verifyNoInteractions(storage, oldStorage, repository);
    }

    /** 历史空归属、其他租户、其他用户均被拒绝。 */
    @Test
    void rejectsUnknownAndForeignOwnership() {
        for (int variant = 0; variant < 3; variant++) {
            FileEntity file = owned();
            if (variant == 0) {
                file.setTenantId(null);
            }
            if (variant == 1) {
                file.setTenantId("tenant-b");
            }
            if (variant == 2) {
                file.setUserId(8L);
            }
            when(repository.findByStorageLocation("apex", "actual.jpg")).thenReturn(Mono.just(file));
            StepVerifier.create(authenticated(service.download("apex", "actual.jpg")))
                    .expectError(BusinessException.class)
                    .verify();
        }
        verifyNoInteractions(storage, oldStorage);
    }

    /** 默认提供商切换后所有访问均按记录中的实际类型路由。 */
    @Test
    void routesToRecordedStorageAndRetainsMetadataOnFailedDelete() {
        when(repository.findByStorageLocation("apex", "actual.jpg")).thenReturn(Mono.just(owned()));
        when(oldStorage.exists("apex", "actual.jpg")).thenReturn(Mono.just(true));
        when(oldStorage.download("apex", "actual.jpg")).thenReturn(Mono.just(new ByteArrayInputStream(JPG)));
        when(oldStorage.getPresignedUrl("apex", "actual.jpg", 300)).thenReturn(Mono.just("old-url"));
        when(oldStorage.delete("apex", "actual.jpg")).thenReturn(Mono.just(false));
        StepVerifier.create(authenticated(service.exists("apex", "actual.jpg")))
                .expectNext(true)
                .verifyComplete();
        StepVerifier.create(authenticated(service.download("apex", "actual.jpg")))
                .expectNextCount(1)
                .verifyComplete();
        StepVerifier.create(authenticated(service.getUrl("apex", "actual.jpg")))
                .expectNext("old-url")
                .verifyComplete();
        StepVerifier.create(authenticated(service.delete("apex", "actual.jpg")))
                .expectNext(false)
                .verifyComplete();
        verify(repository, never()).deleteById(anyLong());
        verifyNoInteractions(storage);
    }

    /** 对象写入成功而数据库失败时执行补偿并传播原始错误。 */
    @Test
    void compensatesDatabaseFailure() {
        when(storage.upload(anyString(), anyString(), any(InputStream.class), anyString()))
                .thenReturn(Mono.just("actual.jpg"));
        when(repository.save(any())).thenReturn(Mono.error(new IllegalStateException("database failure")));
        when(storage.delete("apex", "actual.jpg")).thenReturn(Mono.just(true));
        StepVerifier.create(authenticated(service.upload("photo.jpg", JPG, "image/jpeg", null, null)))
                .expectErrorMessage("database failure")
                .verify();
        verify(storage).delete("apex", "actual.jpg");
    }

    /** 对象删除确认成功后才删除文件元数据。 */
    @Test
    void deletesMetadataAfterObjectDeletion() {
        when(repository.findByStorageLocation("apex", "actual.jpg")).thenReturn(Mono.just(owned()));
        when(oldStorage.delete("apex", "actual.jpg")).thenReturn(Mono.just(true));
        when(repository.deleteById(1L)).thenReturn(Mono.empty());
        StepVerifier.create(authenticated(service.delete("apex", "actual.jpg")))
                .expectNext(true)
                .verifyComplete();
        var order = inOrder(oldStorage, repository);
        order.verify(repository).findByStorageLocation("apex", "actual.jpg");
        order.verify(oldStorage).delete("apex", "actual.jpg");
        order.verify(repository).deleteById(1L);
    }

    /** 超限、伪造图片签名与非法媒体类型必须在存储调用前拒绝。 */
    @Test
    void rejectsInvalidImageContent() {
        StepVerifier.create(authenticated(service.upload("photo.jpg", new byte[1025], "image/jpeg", null, null)))
                .expectError(BusinessException.class)
                .verify();
        StepVerifier.create(Mono.defer(() ->
                        authenticated(service.upload("photo.jpg", new byte[] {1, 2, 3, 4}, "image/jpeg", null, null))))
                .expectError(BusinessException.class)
                .verify();
        StepVerifier.create(Mono.defer(() -> authenticated(service.upload("photo.jpg", JPG, "text/plain", null, null))))
                .expectError(BusinessException.class)
                .verify();
        verifyNoInteractions(storage, oldStorage, repository);
    }

    /** 补偿失败仍传播落库错误，并保留补偿失败的诊断信息。 */
    @Test
    void preservesDatabaseErrorWhenCompensationFails() {
        IllegalStateException databaseError = new IllegalStateException("database failure");
        when(storage.upload(anyString(), anyString(), any(InputStream.class), anyString()))
                .thenReturn(Mono.just("actual.jpg"));
        when(repository.save(any())).thenReturn(Mono.error(databaseError));
        when(storage.delete("apex", "actual.jpg")).thenReturn(Mono.just(false));
        StepVerifier.create(authenticated(service.upload("photo.jpg", JPG, "image/jpeg", null, null)))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isSameAs(databaseError);
                    assertThat(error.getSuppressed()).hasSize(1);
                })
                .verify();
    }

    /** 业务保存失败但数据库仍可用时，补偿失败必须留下独立清理记录。 */
    @Test
    void persistsOrphanWhenCompensationFails() {
        when(storage.upload(anyString(), anyString(), any(InputStream.class), anyString()))
                .thenReturn(Mono.just("actual.jpg"));
        when(repository.save(any()))
                .thenReturn(Mono.error(new IllegalStateException("business insert failed")), Mono.just(owned()));
        when(storage.delete("apex", "actual.jpg")).thenReturn(Mono.just(false));
        StepVerifier.create(authenticated(service.upload("photo.jpg", JPG, "image/jpeg", null, null)))
                .expectErrorMessage("business insert failed")
                .verify();
        var records = org.mockito.ArgumentCaptor.forClass(FileEntity.class);
        verify(repository, times(2)).save(records.capture());
        assertThat(records.getAllValues().get(1).getStorageStatus()).isEqualTo("ORPHANED");
        assertThat(records.getAllValues().get(1).getFileName()).isEqualTo("actual.jpg");
        assertThat(records.getAllValues().get(1).getTenantId()).isEqualTo("tenant-a");
    }

    /** 孤儿清理先验证记录归属，对象已不存在也允许移除清理依据。 */
    @Test
    void retriesOrphanCleanup() {
        FileEntity orphan = owned();
        orphan.setStorageStatus("ORPHANED");
        when(repository.findById(1L)).thenReturn(Mono.just(orphan));
        when(oldStorage.delete("apex", "actual.jpg")).thenReturn(Mono.just(false));
        when(oldStorage.exists("apex", "actual.jpg")).thenReturn(Mono.just(false));
        when(repository.deleteById(1L)).thenReturn(Mono.empty());
        StepVerifier.create(authenticated(service.retryOrphanCleanup(1L)))
                .expectNext(true)
                .verifyComplete();
        verify(repository).deleteById(1L);
    }

    /** 输入校验在任何写入前完成。 */
    @Test
    void validatesImages() {
        StepVerifier.create(service.upload("photo.jpg", new byte[0], "image/jpeg", null, null))
                .expectError(BusinessException.class)
                .verify();
        StepVerifier.create(Mono.defer(() -> service.upload("a.txt", JPG, "text/plain", null, null)))
                .expectError(BusinessException.class)
                .verify();
        verifyNoInteractions(storage);
    }
}
