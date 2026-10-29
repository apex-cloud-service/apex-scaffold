package com.apex.scaffold.file.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.file.domain.enums.StorageType;
import com.apex.scaffold.file.domain.model.entity.FileEntity;
import com.apex.scaffold.file.domain.model.entity.FileShareEntity;
import com.apex.scaffold.file.domain.repository.FileRepository;
import com.apex.scaffold.file.domain.repository.FileShareRepository;
import com.apex.scaffold.file.domain.service.StorageService;
import com.apex.scaffold.file.domain.service.StorageServiceProvider;
import com.apex.scaffold.file.domain.service.TenantStatusPort;
import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 文件限时只读分享应用服务测试。
 *
 * <p>覆盖计划 42.3/42.6（O05/T18）的关键验收项：创建与撤销的归属授权、令牌仅存摘要、
 * 过期/撤销/删文件/租户停用/非 STORED 等场景拒绝兑换，以及代理下载不签发直连地址。</p>
 *
 * @author Apex Team
 */
class FileShareServiceTest {

    private static final String TENANT = "tenant-a";

    private final FileRepository fileRepository = mock(FileRepository.class);
    private final FileShareRepository shareRepository = mock(FileShareRepository.class);
    private final StorageService storage = mock(StorageService.class);
    private final TenantStatusPort tenantStatusPort = mock(TenantStatusPort.class);

    private FileShareService service;

    @BeforeEach
    void setup() {
        service = new FileShareService(
                fileRepository,
                shareRepository,
                new StorageServiceProvider(Map.of(StorageType.LOCAL, storage), StorageType.LOCAL),
                tenantStatusPort,
                86400,
                604800);
        lenient().when(tenantStatusPort.isActive(anyString())).thenReturn(true);
    }

    private <T> Mono<T> authenticated(Mono<T> operation) {
        return operation.contextWrite(context -> context.put(FileAccessContext.KEY, new FileAccessContext(TENANT, 7L)));
    }

    private FileEntity ownedStoredFile() {
        FileEntity file = new FileEntity();
        file.setId(1L);
        file.setTenantId(TENANT);
        file.setUserId(7L);
        file.setStorageType("LOCAL");
        file.setBucketName("apex");
        file.setFileName("actual.pdf");
        file.setOriginalName("report.pdf");
        file.setContentType("application/pdf");
        file.setFileSize(100L);
        file.setStorageStatus("STORED");
        return file;
    }

    private FileShareEntity activeShare() {
        FileShareEntity share = new FileShareEntity();
        share.setId(11L);
        share.setFileId(1L);
        share.setTenantId(TENANT);
        share.setCreatorId(7L);
        share.setTokenHash("hash");
        share.setExpireAt(LocalDateTime.now().plusHours(1));
        share.setStatus(FileShareEntity.STATUS_ACTIVE);
        return share;
    }

    // ---------------- 创建 ----------------

    @Test
    @DisplayName("创建分享 - 库内仅保存令牌摘要，明文仅在响应返回")
    void createPersistsOnlyTokenHash() {
        when(fileRepository.findById(1L)).thenReturn(Mono.just(ownedStoredFile()));
        when(shareRepository.save(any())).thenAnswer(invocation -> {
            FileShareEntity share = invocation.getArgument(0);
            share.setId(11L);
            return Mono.just(share);
        });

        StepVerifier.create(authenticated(service.create(1L, 3600)))
                .assertNext(created -> {
                    assertThat(created.shareId()).isEqualTo(11L);
                    assertThat(created.token()).hasSize(43);
                    assertThat(created.expireAt()).isAfter(LocalDateTime.now().plusSeconds(3500));
                })
                .verifyComplete();

        verify(shareRepository).save(org.mockito.ArgumentMatchers.argThat(share -> {
            assertThat(share.getTokenHash()).matches("[0-9a-f]{64}");
            assertThat(share.getStatus()).isEqualTo(FileShareEntity.STATUS_ACTIVE);
            assertThat(share.getTenantId()).isEqualTo(TENANT);
            assertThat(share.getCreatorId()).isEqualTo(7L);
            return true;
        }));
    }

    @Test
    @DisplayName("创建分享 - 有效期收敛到上限")
    void createCapsExpireSeconds() {
        when(fileRepository.findById(1L)).thenReturn(Mono.just(ownedStoredFile()));
        when(shareRepository.save(any())).thenAnswer(invocation -> {
            FileShareEntity share = invocation.getArgument(0);
            share.setId(11L);
            return Mono.just(share);
        });

        StepVerifier.create(authenticated(service.create(1L, 99999999)))
                .expectNextCount(1)
                .verifyComplete();

        verify(shareRepository).save(org.mockito.ArgumentMatchers.argThat(share -> share.getExpireAt()
                .isBefore(LocalDateTime.now().plusSeconds(604801))));
    }

    @Test
    @DisplayName("创建分享 - 非所有者或跨租户拒绝")
    void createRejectsNonOwner() {
        FileEntity file = ownedStoredFile();
        file.setUserId(99L);
        when(fileRepository.findById(1L)).thenReturn(Mono.just(file));

        StepVerifier.create(authenticated(service.create(1L, null)))
                .expectErrorMatches(error -> error instanceof BusinessException businessException
                        && businessException.getCode() == ResultCode.FORBIDDEN.getCode())
                .verify();
        verify(shareRepository, never()).save(any());
    }

    @Test
    @DisplayName("创建分享 - 非 STORED 文件拒绝")
    void createRejectsNotStored() {
        FileEntity file = ownedStoredFile();
        file.setStorageStatus("UPLOADING");
        when(fileRepository.findById(1L)).thenReturn(Mono.just(file));

        StepVerifier.create(authenticated(service.create(1L, null)))
                .expectErrorMatches(error -> error instanceof BusinessException businessException
                        && businessException.getCode() == ResultCode.FORBIDDEN.getCode())
                .verify();
    }

    @Test
    @DisplayName("创建分享 - 文件不存在返回 NOT_FOUND")
    void createRejectsMissingFile() {
        when(fileRepository.findById(9L)).thenReturn(Mono.empty());

        StepVerifier.create(authenticated(service.create(9L, null)))
                .expectErrorMatches(error -> error instanceof BusinessException businessException
                        && businessException.getCode() == ResultCode.NOT_FOUND.getCode())
                .verify();
    }

    @Test
    @DisplayName("创建分享 - 缺少可信身份拒绝")
    void createRejectsMissingIdentity() {
        StepVerifier.create(service.create(1L, null))
                .expectErrorMatches(error -> error instanceof BusinessException businessException
                        && businessException.getCode() == ResultCode.UNAUTHORIZED.getCode())
                .verify();
        verify(fileRepository, never()).findById(anyLong());
    }

    // ---------------- 撤销 ----------------

    @Test
    @DisplayName("撤销分享 - 条件更新命中即成功")
    void revokeByOwner() {
        when(shareRepository.markRevoked(eq(11L), eq(TENANT), eq(7L), any())).thenReturn(Mono.just(true));

        StepVerifier.create(authenticated(service.revoke(11L))).verifyComplete();
        verify(shareRepository, never()).findById(anyLong());
    }

    @Test
    @DisplayName("撤销分享 - 跨租户或非创建者拒绝")
    void revokeRejectsForeignShare() {
        when(shareRepository.markRevoked(eq(11L), eq(TENANT), eq(7L), any())).thenReturn(Mono.just(false));
        FileShareEntity foreign = activeShare();
        foreign.setTenantId("tenant-b");
        foreign.setCreatorId(99L);
        when(shareRepository.findById(11L)).thenReturn(Mono.just(foreign));

        StepVerifier.create(authenticated(service.revoke(11L)))
                .expectErrorMatches(error -> error instanceof BusinessException businessException
                        && businessException.getCode() == ResultCode.FORBIDDEN.getCode())
                .verify();
    }

    @Test
    @DisplayName("撤销分享 - 重复撤销视为成功")
    void revokeIsIdempotent() {
        when(shareRepository.markRevoked(eq(11L), eq(TENANT), eq(7L), any())).thenReturn(Mono.just(false));
        FileShareEntity revoked = activeShare();
        revoked.setStatus(FileShareEntity.STATUS_REVOKED);
        when(shareRepository.findById(11L)).thenReturn(Mono.just(revoked));

        StepVerifier.create(authenticated(service.revoke(11L))).verifyComplete();
    }

    @Test
    @DisplayName("撤销分享 - 记录不存在返回 NOT_FOUND")
    void revokeRejectsMissingShare() {
        when(shareRepository.markRevoked(eq(11L), eq(TENANT), eq(7L), any())).thenReturn(Mono.just(false));
        when(shareRepository.findById(11L)).thenReturn(Mono.empty());

        StepVerifier.create(authenticated(service.revoke(11L)))
                .expectErrorMatches(error -> error instanceof BusinessException businessException
                        && businessException.getCode() == ResultCode.NOT_FOUND.getCode())
                .verify();
    }

    // ---------------- 兑换与下载 ----------------

    @Test
    @DisplayName("兑换分享 - 成功返回文件元数据")
    void exchangeReturnsMetadata() {
        when(shareRepository.findByTokenHash(anyString())).thenReturn(Mono.just(activeShare()));
        when(fileRepository.findById(1L)).thenReturn(Mono.just(ownedStoredFile()));

        StepVerifier.create(service.exchange("plain-token"))
                .assertNext(result -> {
                    assertThat(result.shareId()).isEqualTo(11L);
                    assertThat(result.fileId()).isEqualTo(1L);
                    assertThat(result.originalName()).isEqualTo("report.pdf");
                    assertThat(result.contentType()).isEqualTo("application/pdf");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("兑换分享 - 空白令牌拒绝")
    void exchangeRejectsBlankToken() {
        StepVerifier.create(service.exchange("  "))
                .expectErrorMatches(error -> error instanceof BusinessException businessException
                        && businessException.getCode() == ResultCode.PARAM_ERROR.getCode())
                .verify();
        verify(shareRepository, never()).findByTokenHash(anyString());
    }

    @Test
    @DisplayName("兑换分享 - 摘要未命中视为不存在")
    void exchangeRejectsUnknownToken() {
        when(shareRepository.findByTokenHash(anyString())).thenReturn(Mono.empty());

        StepVerifier.create(service.exchange("plain-token"))
                .expectErrorMatches(error -> error instanceof BusinessException businessException
                        && businessException.getCode() == ResultCode.NOT_FOUND.getCode())
                .verify();
    }

    @Test
    @DisplayName("兑换分享 - 过期拒绝")
    void exchangeRejectsExpired() {
        FileShareEntity share = activeShare();
        share.setExpireAt(LocalDateTime.now().minusSeconds(1));
        when(shareRepository.findByTokenHash(anyString())).thenReturn(Mono.just(share));

        StepVerifier.create(service.exchange("plain-token"))
                .expectErrorMatches(error -> error instanceof BusinessException businessException
                        && businessException.getCode() == ResultCode.FORBIDDEN.getCode())
                .verify();
        verify(fileRepository, never()).findById(anyLong());
    }

    @Test
    @DisplayName("兑换分享 - 已撤销拒绝")
    void exchangeRejectsRevoked() {
        FileShareEntity share = activeShare();
        share.setStatus(FileShareEntity.STATUS_REVOKED);
        when(shareRepository.findByTokenHash(anyString())).thenReturn(Mono.just(share));

        StepVerifier.create(service.exchange("plain-token"))
                .expectErrorMatches(error -> error instanceof BusinessException businessException
                        && businessException.getCode() == ResultCode.FORBIDDEN.getCode())
                .verify();
    }

    @Test
    @DisplayName("兑换分享 - 租户停用拒绝")
    void exchangeRejectsSuspendedTenant() {
        when(shareRepository.findByTokenHash(anyString())).thenReturn(Mono.just(activeShare()));
        when(tenantStatusPort.isActive(TENANT)).thenReturn(false);

        StepVerifier.create(service.exchange("plain-token"))
                .expectErrorMatches(error -> error instanceof BusinessException businessException
                        && businessException.getCode() == ResultCode.FORBIDDEN.getCode())
                .verify();
    }

    @Test
    @DisplayName("兑换分享 - 文件已删除拒绝")
    void exchangeRejectsMissingFile() {
        when(shareRepository.findByTokenHash(anyString())).thenReturn(Mono.just(activeShare()));
        when(fileRepository.findById(1L)).thenReturn(Mono.empty());

        StepVerifier.create(service.exchange("plain-token"))
                .expectErrorMatches(error -> error instanceof BusinessException businessException
                        && businessException.getCode() == ResultCode.NOT_FOUND.getCode())
                .verify();
    }

    @Test
    @DisplayName("兑换分享 - 分享与文件租户不一致拒绝")
    void exchangeRejectsTenantMismatch() {
        when(shareRepository.findByTokenHash(anyString())).thenReturn(Mono.just(activeShare()));
        FileEntity file = ownedStoredFile();
        file.setTenantId("tenant-b");
        when(fileRepository.findById(1L)).thenReturn(Mono.just(file));

        StepVerifier.create(service.exchange("plain-token"))
                .expectErrorMatches(error -> error instanceof BusinessException businessException
                        && businessException.getCode() == ResultCode.FORBIDDEN.getCode())
                .verify();
    }

    @Test
    @DisplayName("兑换分享 - 文件非 STORED 拒绝")
    void exchangeRejectsNotStoredFile() {
        when(shareRepository.findByTokenHash(anyString())).thenReturn(Mono.just(activeShare()));
        FileEntity file = ownedStoredFile();
        file.setStorageStatus("DELETED");
        when(fileRepository.findById(1L)).thenReturn(Mono.just(file));

        StepVerifier.create(service.exchange("plain-token"))
                .expectErrorMatches(error -> error instanceof BusinessException businessException
                        && businessException.getCode() == ResultCode.FORBIDDEN.getCode())
                .verify();
    }

    @Test
    @DisplayName("下载分享 - 服务端代理读取，不签发直连地址")
    void downloadProxiesContent() {
        when(shareRepository.findByTokenHash(anyString())).thenReturn(Mono.just(activeShare()));
        when(fileRepository.findById(1L)).thenReturn(Mono.just(ownedStoredFile()));
        when(storage.download("apex", "actual.pdf"))
                .thenReturn(Mono.just(new ByteArrayInputStream("hello".getBytes())));

        StepVerifier.create(service.download("plain-token"))
                .assertNext(content -> {
                    assertThat(content.fileName()).isEqualTo("report.pdf");
                    assertThat(content.contentType()).isEqualTo("application/pdf");
                    assertThat(content.fileSize()).isEqualTo(100L);
                    assertThat(content.content()).isNotNull();
                })
                .verifyComplete();
        verify(storage).download("apex", "actual.pdf");
    }
}
