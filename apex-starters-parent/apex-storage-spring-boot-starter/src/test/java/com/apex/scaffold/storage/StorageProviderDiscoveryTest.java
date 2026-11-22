package com.apex.scaffold.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.core.publisher.Mono;

/**
 * 存储 SPI 真实描述符发现测试：通过临时类路径下的
 * {@code META-INF/services/com.apex.scaffold.storage.ObjectStorage} 验证
 * {@link StorageProviderLoader} 的端到端发现，而非只注入模拟迭代器。
 *
 * <p>覆盖：正常发现、空白 type 跳过、实现构造失败跳过、无描述符返回空。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@DisplayName("存储 SPI 真实描述符发现")
class StorageProviderDiscoveryTest {

    @TempDir
    Path temp;

    /**
     * 可被 SPI 实例化的第三方后端桩（必须 public 且有无参构造）。
     */
    public static class DiscoverableStorage implements ObjectStorage {

        /** SPI 要求无参构造。 */
        public DiscoverableStorage() {}

        @Override
        public String type() {
            return "discoverable";
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

    /**
     * 构造时抛异常的 SPI 实现，用于验证单条实例化失败被隔离。
     */
    public static class ExplodingStorage implements ObjectStorage {

        /** 构造即失败。 */
        public ExplodingStorage() {
            throw new IllegalStateException("构造失败");
        }

        @Override
        public String type() {
            return "exploding";
        }

        @Override
        public Mono<String> upload(String bucket, String key, InputStream source, String contentType) {
            return Mono.empty();
        }

        @Override
        public Mono<InputStream> download(String bucket, String key) {
            return Mono.empty();
        }

        @Override
        public Mono<Boolean> delete(String bucket, String key) {
            return Mono.just(false);
        }

        @Override
        public Mono<Boolean> exists(String bucket, String key) {
            return Mono.just(false);
        }

        @Override
        public Mono<String> presignGet(String bucket, String key, int ttlSeconds) {
            return Mono.empty();
        }
    }

    private ClassLoader descriptorClassLoader(String... implementationClasses) throws IOException {
        Path services = temp.resolve("META-INF/services");
        Files.createDirectories(services);
        Files.writeString(
                services.resolve(ObjectStorage.class.getName()),
                String.join("\n", implementationClasses) + "\n",
                StandardCharsets.UTF_8);
        URL url = temp.toUri().toURL();
        return new URLClassLoader(new URL[] {url}, getClass().getClassLoader());
    }

    @Test
    @DisplayName("真实描述符被 ServiceLoader 发现并保留 type")
    void discoversProviderFromRealDescriptor() throws Exception {
        ClassLoader loader = descriptorClassLoader(DiscoverableStorage.class.getName());

        var discovered = StorageProviderLoader.load(loader);

        assertThat(discovered).hasSize(1);
        assertThat(discovered.get(0).type()).isEqualTo("discoverable");
    }

    @Test
    @DisplayName("单条实现构造失败被跳过，同描述符内其余条目保留")
    void skipsExplodingEntryAndKeepsHealthyOne() throws Exception {
        ClassLoader loader =
                descriptorClassLoader(ExplodingStorage.class.getName(), DiscoverableStorage.class.getName());

        var discovered = StorageProviderLoader.load(loader);

        assertThat(discovered).extracting(ObjectStorage::type).containsExactly("discoverable");
    }

    @Test
    @DisplayName("描述符声明不存在的类时跳过，不中断其余条目")
    void skipsMissingClass() throws Exception {
        ClassLoader loader = descriptorClassLoader(
                "com.apex.scaffold.storage.NotExistingStorage", DiscoverableStorage.class.getName());

        var discovered = StorageProviderLoader.load(loader);

        assertThat(discovered).extracting(ObjectStorage::type).containsExactly("discoverable");
    }

    @Test
    @DisplayName("无描述符时返回空列表，内置后端不受影响")
    void returnsEmptyWithoutDescriptor() {
        assertThat(StorageProviderLoader.load(getClass().getClassLoader())).isEmpty();
    }
}
