package com.apex.scaffold.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.InputStream;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

/**
 * 存储 SPI 扩展点测试（方案 M3 / 批次 C3）。
 *
 * <p>覆盖：SPI 后端经 ServiceLoader 发现并注册、重复 type 被严格拒绝、
 * 未启用类型访问失败（不隐式换后端）。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@DisplayName("存储 SPI 扩展点测试")
class StorageProviderLoaderTest {

    /** 测试用第三方后端桩。 */
    private static final class StubStorage implements ObjectStorage {
        private final String type;

        StubStorage(String type) {
            this.type = type;
        }

        @Override
        public String type() {
            return type;
        }

        @Override
        public Mono<String> upload(String bucket, String key, InputStream source, String contentType) {
            return Mono.just(key);
        }

        @Override
        public Mono<InputStream> download(String bucket, String key) {
            return Mono.empty();
        }

        @Override
        public Mono<Boolean> delete(String bucket, String key) {
            return Mono.just(true);
        }

        @Override
        public Mono<Boolean> exists(String bucket, String key) {
            return Mono.just(true);
        }

        @Override
        public Mono<String> presignGet(String bucket, String key, int ttlSeconds) {
            return Mono.just("https://example/" + key);
        }
    }

    @Test
    @DisplayName("SPI 后端可与内置后端合并注册")
    void registersSpiProvider() {
        StorageRegistry registry =
                new StorageRegistry(List.of(new StubStorage("local"), new StubStorage("azure-blob")), "local");
        assertThat(registry.types()).isEqualTo(Set.of("local", "azure-blob"));
        assertThat(registry.require("azure-blob").type()).isEqualTo("azure-blob");
        assertThat(registry.defaultStorage().type()).isEqualTo("local");
    }

    @Test
    @DisplayName("重复 type 严格拒绝（不隐式覆盖内置后端）")
    void rejectsDuplicateType() {
        assertThatThrownBy(
                        () -> new StorageRegistry(List.of(new StubStorage("local"), new StubStorage("local")), "local"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("重复存储策略");
    }

    @Test
    @DisplayName("未知或未启用类型访问失败")
    void rejectsUnknownType() {
        StorageRegistry registry = new StorageRegistry(List.of(new StubStorage("local")), "local");
        assertThatThrownBy(() -> registry.require("azure-blob"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("未启用或未知");
    }

    @Test
    @DisplayName("ServiceLoader 无第三方声明时返回空列表（内置后端不受影响）")
    void loaderReturnsEmptyWhenNoSpi() {
        assertThat(StorageProviderLoader.load(getClass().getClassLoader())).isEmpty();
    }
}
