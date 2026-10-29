package com.apex.scaffold.storage;

import static org.assertj.core.api.Assertions.*;

import com.apex.scaffold.storage.autoconfigure.StorageAutoConfiguration;
import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import reactor.test.StepVerifier;

/** 通用存储契约测试，验证严格路由与本地资源边界。 */
class StorageContractTest {
    @TempDir
    Path root;

    @Test
    void registryRejectsUnknownAndDuplicateProviders() {
        var local = new LocalStorage(root.toString());
        var registry = new StorageRegistry(List.of(local), "local");
        assertThat(registry.defaultStorage()).isSameAs(local);
        assertThatThrownBy(() -> registry.require("qiniu")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StorageRegistry(List.of(local, local), "local"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void localRoundTripAndMissingObjects() throws Exception {
        var local = new LocalStorage(root.toString());
        assertThat(local.upload("bucket", "dir/key.txt", new ByteArrayInputStream(new byte[] {1, 2}), "text/plain")
                        .block())
                .isEqualTo("dir/key.txt");
        try (var stream = local.download("bucket", "dir/key.txt").block()) {
            assertThat(stream.readAllBytes()).containsExactly(1, 2);
        }
        assertThat(local.exists("bucket", "dir/key.txt").block()).isTrue();
        assertThat(local.delete("bucket", "dir/key.txt").block()).isTrue();
        StepVerifier.create(local.download("bucket", "dir/key.txt")).verifyComplete();
        assertThat(local.delete("bucket", "dir/key.txt").block()).isFalse();
        StepVerifier.create(local.download("bucket", "../secret"))
                .expectError(IllegalArgumentException.class)
                .verify();
        StepVerifier.create(local.presignGet("bucket", "key", 0))
                .expectError(IllegalArgumentException.class)
                .verify();
    }

    @Test
    void closesDownloadOnCompletionErrorAndCancellation() throws Exception {
        ObjectStorage storage = org.mockito.Mockito.mock(ObjectStorage.class, org.mockito.Mockito.CALLS_REAL_METHODS);
        for (int mode = 0; mode < 3; mode++) {
            java.io.InputStream stream = org.mockito.Mockito.mock(java.io.InputStream.class);
            org.mockito.Mockito.when(storage.download("bucket", "key"))
                    .thenReturn(reactor.core.publisher.Mono.just(stream));
            if (mode == 0) {
                StepVerifier.create(
                                storage.withDownload("bucket", "key", input -> reactor.core.publisher.Mono.just("ok")))
                        .expectNext("ok")
                        .verifyComplete();
            } else if (mode == 1) {
                StepVerifier.create(storage.withDownload(
                                "bucket",
                                "key",
                                input -> reactor.core.publisher.Mono.error(new IllegalStateException())))
                        .expectError(IllegalStateException.class)
                        .verify();
            } else {
                StepVerifier.create(storage.withDownload("bucket", "key", input -> reactor.core.publisher.Mono.never()))
                        .thenCancel()
                        .verify();
            }
            org.mockito.Mockito.verify(stream, org.mockito.Mockito.timeout(2000))
                    .close();
        }
    }

    @Test
    void disabledAndMissingSdkCannotBecomeDefault() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(StorageAutoConfiguration.class))
                .withPropertyValues("storage.local.enabled=false")
                .run(context -> assertThat(context).hasFailed());
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(StorageAutoConfiguration.class))
                .withClassLoader(new org.springframework.boot.test.context.FilteredClassLoader("io.minio"))
                .withPropertyValues("storage.minio.enabled=true", "storage.default-type=minio")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void allCloudStrategiesCanBeExplicitDefaults() {
        for (String type : List.of("minio", "volcengine", "aliyun-oss", "qiniu")) {
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(StorageAutoConfiguration.class))
                    .withPropertyValues(
                            "storage.default-type=" + type,
                            "storage." + type + ".enabled=true",
                            "storage." + type + ".endpoint=https://storage.example.com",
                            "storage." + type + ".access-key=access",
                            "storage." + type + ".secret-key=secret",
                            "storage." + type + ".region=cn-beijing",
                            "storage." + type + ".bucket=bucket",
                            "storage." + type + ".download-domain=https://cdn.example.com")
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        assertThat(context.getBean(StorageRegistry.class)
                                        .defaultStorage()
                                        .type())
                                .isEqualTo(type);
                    });
        }
    }

    @Test
    void eachEnabledCloudFailsWhenItsSdkIsMissing() {
        var packages = java.util.Map.of(
                "minio", "io.minio",
                "volcengine", "com.volcengine.tos",
                "aliyun-oss", "com.aliyun.oss",
                "qiniu", "com.qiniu");
        packages.forEach((type, sdkPackage) -> new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(StorageAutoConfiguration.class))
                .withClassLoader(new org.springframework.boot.test.context.FilteredClassLoader(sdkPackage))
                .withPropertyValues("storage." + type + ".enabled=true")
                .run(context -> assertThat(context).hasFailed()));
    }

    @Test
    void autoConfigurationIsIndependentAndRejectsUnknownDefault() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(StorageAutoConfiguration.class))
                .withPropertyValues("storage.local.base-path=" + root)
                .run(context -> assertThat(context).hasSingleBean(StorageRegistry.class));
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(StorageAutoConfiguration.class))
                .withPropertyValues("storage.default-type=unknown")
                .run(context -> assertThat(context).hasFailed());
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(StorageAutoConfiguration.class))
                .withPropertyValues("storage.qiniu.enabled=true")
                .run(context -> assertThat(context).hasFailed());
    }
}
