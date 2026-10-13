package com.apex.scaffold.file.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.file.domain.enums.StorageType;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link StorageServiceProvider} 存储 SPI 切流回归测试.
 *
 * <p>验证按存储类型选择服务、缺失时按 LOCAL→MINIO→VOLCENGINE 顺序降级、
 * 无效入参降级以及默认服务获取等切流逻辑，确保存储后端切换质量门禁不被破坏。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("StorageServiceProvider 存储切流回归测试")
class StorageServiceProviderTest {

    private static final String LOCAL_PREFIX = "/uploads/apex";
    private static final String MINIO_PREFIX = "/minio/apex";
    private static final String VOLC_PREFIX = "/tos/apex-photo";

    private StorageService createService(String prefix) {
        StorageService service = new StorageService() {
            @Override
            public reactor.core.publisher.Mono<String> upload(
                    String bucketName, String objectName, java.io.InputStream inputStream, String contentType) {
                return reactor.core.publisher.Mono.just(objectName);
            }

            @Override
            public reactor.core.publisher.Mono<java.io.InputStream> download(String bucketName, String objectName) {
                return reactor.core.publisher.Mono.empty();
            }

            @Override
            public reactor.core.publisher.Mono<Boolean> delete(String bucketName, String objectName) {
                return reactor.core.publisher.Mono.just(true);
            }

            @Override
            public reactor.core.publisher.Mono<String> getPresignedUrl(
                    String bucketName, String objectName, Integer expiry) {
                return reactor.core.publisher.Mono.empty();
            }

            @Override
            public reactor.core.publisher.Mono<Boolean> exists(String bucketName, String objectName) {
                return reactor.core.publisher.Mono.just(false);
            }

            @Override
            public String getPhotoUrlPrefix() {
                return prefix;
            }
        };
        return service;
    }

    @Test
    @DisplayName("按存储类型精确选择：三种存储类型均应返回各自服务")
    void getStorageService_byType_returnsMatchingService() {
        StorageService local = createService(LOCAL_PREFIX);
        StorageService minio = createService(MINIO_PREFIX);
        StorageService volc = createService(VOLC_PREFIX);

        StorageServiceProvider provider =
                new StorageServiceProvider(Optional.of(minio), Optional.of(volc), Optional.of(local));

        assertThat(provider.getStorageService(StorageType.LOCAL).getPhotoUrlPrefix())
                .isEqualTo(LOCAL_PREFIX);
        assertThat(provider.getStorageService(StorageType.MINIO).getPhotoUrlPrefix())
                .isEqualTo(MINIO_PREFIX);
        assertThat(provider.getStorageService(StorageType.VOLCENGINE).getPhotoUrlPrefix())
                .isEqualTo(VOLC_PREFIX);
    }

    @Test
    @DisplayName("按字符串名称选择：支持大小写不敏感")
    void getStorageService_byName_caseInsensitive() {
        StorageService minio = createService(MINIO_PREFIX);

        StorageServiceProvider provider =
                new StorageServiceProvider(Optional.of(minio), Optional.empty(), Optional.empty());

        assertThat(provider.getStorageService("minio").getPhotoUrlPrefix()).isEqualTo(MINIO_PREFIX);
        assertThat(provider.getStorageService("MINIO").getPhotoUrlPrefix()).isEqualTo(MINIO_PREFIX);
    }

    @Test
    @DisplayName("切流降级：请求类型未配置时应降级到本地存储(LOCAL 最高优先级)")
    void getStorageService_fallbackToLocal_whenRequestedAbsent() {
        StorageService local = createService(LOCAL_PREFIX);
        StorageService minio = createService(MINIO_PREFIX);

        StorageServiceProvider provider =
                new StorageServiceProvider(Optional.of(minio), Optional.empty(), Optional.of(local));

        assertThat(provider.getStorageService(StorageType.MINIO).getPhotoUrlPrefix())
                .isEqualTo(MINIO_PREFIX);
        assertThat(provider.getStorageService(StorageType.VOLCENGINE).getPhotoUrlPrefix())
                .isEqualTo(LOCAL_PREFIX);
    }

    @Test
    @DisplayName("切流降级顺序：无本地时降级到 MINIO，再降级到 VOLCENGINE")
    void getStorageService_fallbackOrder_withoutLocal() {
        StorageService minio = createService(MINIO_PREFIX);
        StorageService volc = createService(VOLC_PREFIX);

        StorageServiceProvider providerWithMinio =
                new StorageServiceProvider(Optional.of(minio), Optional.of(volc), Optional.empty());
        assertThat(providerWithMinio.getStorageService(StorageType.LOCAL).getPhotoUrlPrefix())
                .isEqualTo(MINIO_PREFIX);

        StorageServiceProvider providerVolcOnly =
                new StorageServiceProvider(Optional.empty(), Optional.of(volc), Optional.empty());
        assertThat(providerVolcOnly.getStorageService(StorageType.LOCAL).getPhotoUrlPrefix())
                .isEqualTo(VOLC_PREFIX);
    }

    @Test
    @DisplayName("null 存储类型应降级到第一个可用服务")
    void getStorageService_nullType_fallsBack() {
        StorageService local = createService(LOCAL_PREFIX);

        StorageServiceProvider provider =
                new StorageServiceProvider(Optional.empty(), Optional.empty(), Optional.of(local));

        assertThat(provider.getStorageService((StorageType) null).getPhotoUrlPrefix())
                .isEqualTo(LOCAL_PREFIX);
    }

    @Test
    @DisplayName("空白或不支持的字符串类型应降级到第一个可用服务")
    void getStorageService_blankOrInvalidString_fallsBack() {
        StorageService local = createService(LOCAL_PREFIX);

        StorageServiceProvider provider =
                new StorageServiceProvider(Optional.empty(), Optional.empty(), Optional.of(local));

        assertThat(provider.getStorageService((String) null).getPhotoUrlPrefix())
                .isEqualTo(LOCAL_PREFIX);
        assertThat(provider.getStorageService("").getPhotoUrlPrefix()).isEqualTo(LOCAL_PREFIX);
        assertThat(provider.getStorageService("   ").getPhotoUrlPrefix()).isEqualTo(LOCAL_PREFIX);
        assertThat(provider.getStorageService("UNKNOWN").getPhotoUrlPrefix()).isEqualTo(LOCAL_PREFIX);
    }

    @Test
    @DisplayName("全部存储服务缺失时返回 null")
    void getStorageService_allAbsent_returnsNull() {
        StorageServiceProvider provider =
                new StorageServiceProvider(Optional.empty(), Optional.empty(), Optional.empty());

        assertThat(provider.getStorageService(StorageType.MINIO)).isNull();
        assertThat(provider.getStorageService(StorageType.LOCAL)).isNull();
        assertThat(provider.getDefaultStorageService()).isNull();
    }

    @Test
    @DisplayName("默认服务应按 LOCAL→MINIO→VOLCENGINE 顺序返回")
    void getDefaultStorageService_priorityOrder() {
        StorageService local = createService(LOCAL_PREFIX);
        StorageService minio = createService(MINIO_PREFIX);
        StorageService volc = createService(VOLC_PREFIX);

        StorageServiceProvider provider =
                new StorageServiceProvider(Optional.of(minio), Optional.of(volc), Optional.of(local));
        assertThat(provider.getDefaultStorageService().getPhotoUrlPrefix()).isEqualTo(LOCAL_PREFIX);

        StorageServiceProvider providerNoLocal =
                new StorageServiceProvider(Optional.of(minio), Optional.of(volc), Optional.empty());
        assertThat(providerNoLocal.getDefaultStorageService().getPhotoUrlPrefix())
                .isEqualTo(MINIO_PREFIX);

        StorageServiceProvider providerVolcOnly =
                new StorageServiceProvider(Optional.empty(), Optional.of(volc), Optional.empty());
        assertThat(providerVolcOnly.getDefaultStorageService().getPhotoUrlPrefix())
                .isEqualTo(VOLC_PREFIX);
    }

    @Test
    @DisplayName("切流后目标服务仍未配置时应降级到本地，确保默认读写不失败")
    void switchOver_missingTarget_fallsBackToLocal() {
        StorageService local = createService(LOCAL_PREFIX);

        StorageServiceProvider provider =
                new StorageServiceProvider(Optional.empty(), Optional.empty(), Optional.of(local));

        StorageService selected = provider.getStorageService(StorageType.MINIO);
        assertThat(selected).isSameAs(local);
    }
}
