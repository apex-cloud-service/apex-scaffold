package com.apex.scaffold.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ServiceConfigurationError;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
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

    /** 测试用第三方后端桩（非 final，便于子类覆写异常行为）。 */
    private static class StubStorage implements ObjectStorage {
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

    @Test
    @DisplayName("type() 抛异常的实现被跳过，其余条目保留")
    void collectSkipsThrowingType() {
        ObjectStorage broken = new StubStorage("broken") {
            @Override
            public String type() {
                throw new IllegalStateException("type 调用失败");
            }
        };
        List<ObjectStorage> target = new ArrayList<>();
        StorageProviderLoader.collect(
                List.of(broken, new StubStorage("azure-blob")).iterator(), target);
        assertThat(target).extracting(ObjectStorage::type).containsExactly("azure-blob");
    }

    @Test
    @DisplayName("type() 返回空白的实现被跳过")
    void collectSkipsBlankType() {
        List<ObjectStorage> target = new ArrayList<>();
        StorageProviderLoader.collect(
                List.<ObjectStorage>of(new StubStorage("   "), new StubStorage("local"))
                        .iterator(),
                target);
        assertThat(target).extracting(ObjectStorage::type).containsExactly("local");
    }

    @Test
    @DisplayName("next() 抛 ServiceConfigurationError 时跳过该条目并继续")
    void collectContinuesAfterNextError() {
        Iterator<ObjectStorage> iterator = new Iterator<>() {
            private int index = -1;

            @Override
            public boolean hasNext() {
                return index < 1;
            }

            @Override
            public ObjectStorage next() {
                index++;
                if (index == 0) {
                    throw new ServiceConfigurationError("坏实现实例化失败");
                }
                return new StubStorage("azure-blob");
            }
        };
        List<ObjectStorage> target = new ArrayList<>();
        StorageProviderLoader.collect(iterator, target);
        assertThat(target).extracting(ObjectStorage::type).containsExactly("azure-blob");
    }

    @Test
    @DisplayName("描述符遍历中途失败时停止发现但保留已发现条目")
    void collectRetainsDiscoveredOnDescriptorFailure() {
        AtomicInteger hasNextCalls = new AtomicInteger();
        Iterator<ObjectStorage> iterator = new Iterator<>() {
            @Override
            public boolean hasNext() {
                if (hasNextCalls.incrementAndGet() == 1) {
                    return true;
                }
                throw new ServiceConfigurationError("描述符不可遍历");
            }

            @Override
            public ObjectStorage next() {
                return new StubStorage("azure-blob");
            }
        };
        List<ObjectStorage> target = new ArrayList<>();
        StorageProviderLoader.collect(iterator, target);
        assertThat(target).extracting(ObjectStorage::type).containsExactly("azure-blob");
        assertThat(hasNextCalls).hasValue(2);
    }
}
