package com.apex.scaffold.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.domain.enums.StorageType;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link StorageServiceProvider} 的单元测试类
 *
 * <p>测试存储服务提供者的核心逻辑：根据存储类型/名称获取服务实例、默认回退等。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("StorageServiceProvider 单元测试")
@ExtendWith(MockitoExtension.class)
class StorageServiceProviderTest {

    @Mock
    private StorageService minioStorageService;

    @Mock
    private StorageService volcEngineStorageService;

    @Mock
    private StorageService localStorageService;

    private StorageServiceProvider provider;

    /**
     * 设置{@code up}。
     */
    @BeforeEach
    void setUp() {
        provider = new StorageServiceProvider(
                Optional.of(minioStorageService),
                Optional.of(volcEngineStorageService),
                Optional.of(localStorageService));
    }

    @Nested
    @DisplayName("getStorageService(StorageType) 方法")
    class GetServiceByTypeTests {

        /**
         * 执行 {@code shouldReturnMinioForMinioType} 操作。
         */
        @Test
        @DisplayName("传入 MINIO 应返回 MinIO 服务")
        void shouldReturnMinioForMinioType() {
            assertThat(provider.getStorageService(StorageType.MINIO)).isSameAs(minioStorageService);
        }

        /**
         * 执行 {@code shouldReturnVolcEngineForVolcEngineType} 操作。
         */
        @Test
        @DisplayName("传入 VOLCENGINE 应返回火山引擎服务")
        void shouldReturnVolcEngineForVolcEngineType() {
            assertThat(provider.getStorageService(StorageType.VOLCENGINE)).isSameAs(volcEngineStorageService);
        }

        /**
         * 执行 {@code shouldReturnLocalForLocalType} 操作。
         */
        @Test
        @DisplayName("传入 LOCAL 应返回本地服务")
        void shouldReturnLocalForLocalType() {
            assertThat(provider.getStorageService(StorageType.LOCAL)).isSameAs(localStorageService);
        }

        /**
         * 执行 {@code shouldFallbackToLocalWhenNull} 操作。
         */
        @Test
        @DisplayName("传入 null 应回退到 Local")
        void shouldFallbackToLocalWhenNull() {
            assertThat(provider.getStorageService((StorageType) null)).isSameAs(localStorageService);
        }
    }

    @Nested
    @DisplayName("getStorageService(String) 方法")
    class GetServiceByNameTests {

        /**
         * 执行 {@code shouldReturnMinioForMinioName} 操作。
         */
        @Test
        @DisplayName("传入 \"MINIO\" 应返回 MinIO 服务")
        void shouldReturnMinioForMinioName() {
            assertThat(provider.getStorageService("MINIO")).isSameAs(minioStorageService);
        }

        /**
         * 执行 {@code shouldReturnVolcEngineForVolcEngineName} 操作。
         */
        @Test
        @DisplayName("传入 \"VOLCENGINE\" 应返回火山引擎服务")
        void shouldReturnVolcEngineForVolcEngineName() {
            assertThat(provider.getStorageService("VOLCENGINE")).isSameAs(volcEngineStorageService);
        }

        /**
         * 执行 {@code shouldReturnLocalForLocalName} 操作。
         */
        @Test
        @DisplayName("传入 \"LOCAL\" 应返回本地服务")
        void shouldReturnLocalForLocalName() {
            assertThat(provider.getStorageService("LOCAL")).isSameAs(localStorageService);
        }

        /**
         * 执行 {@code shouldReturnMinioForLowercaseName} 操作。
         */
        @Test
        @DisplayName("传入小写 \"minio\" 应返回 MinIO 服务")
        void shouldReturnMinioForLowercaseName() {
            assertThat(provider.getStorageService("minio")).isSameAs(minioStorageService);
        }

        /**
         * 执行 {@code shouldFallbackToLocalWhenNameNull} 操作。
         */
        @Test
        @DisplayName("传入 null 应回退到 Local")
        void shouldFallbackToLocalWhenNameNull() {
            assertThat(provider.getStorageService((String) null)).isSameAs(localStorageService);
        }

        /**
         * 执行 {@code shouldFallbackToLocalWhenNameBlank} 操作。
         */
        @Test
        @DisplayName("传入空字符串应回退到 Local")
        void shouldFallbackToLocalWhenNameBlank() {
            assertThat(provider.getStorageService("")).isSameAs(localStorageService);
        }

        /**
         * 执行 {@code shouldFallbackToLocalWhenUnsupported} 操作。
         */
        @Test
        @DisplayName("传入不支持的存储类型名称应回退到 Local")
        void shouldFallbackToLocalWhenUnsupported() {
            assertThat(provider.getStorageService("S3")).isSameAs(localStorageService);
        }
    }

    @Nested
    @DisplayName("getDefaultStorageService 方法")
    class GetDefaultServiceTests {

        /**
         * 执行 {@code defaultServiceShouldBeLocal} 操作。
         */
        @Test
        @DisplayName("默认服务应为 Local")
        void defaultServiceShouldBeLocal() {
            assertThat(provider.getDefaultStorageService()).isSameAs(localStorageService);
        }
    }
}
