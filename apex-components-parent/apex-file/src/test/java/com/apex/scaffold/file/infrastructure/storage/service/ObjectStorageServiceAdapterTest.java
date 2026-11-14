package com.apex.scaffold.file.infrastructure.storage.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.apex.scaffold.file.domain.service.StorageService;
import com.apex.scaffold.file.infrastructure.storage.config.DefaultStorageConfiguration;
import com.apex.scaffold.storage.ObjectStorage;
import com.apex.scaffold.storage.autoconfigure.StorageAutoConfiguration;
import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import reactor.core.publisher.Mono;

/**
 * 验证文件兼容键生成及独立自动配置组合装配。
 *
 * @author Apex Team
 * @since 1.3.1
 */
class ObjectStorageServiceAdapterTest {
    @Test
    void preservesHistoricalKeyRules() {
        ObjectStorage storage = mock(ObjectStorage.class);
        when(storage.type()).thenReturn("local");
        when(storage.upload(anyString(), anyString(), any(), anyString()))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(1, String.class)));
        ObjectStorageServiceAdapter adapter = new ObjectStorageServiceAdapter(storage);
        String key = adapter.upload("bucket", "dir/name.jpg", new ByteArrayInputStream(new byte[0]), "image/jpeg")
                .block();
        assertThat(key).matches("dir/[a-f0-9]{32}\\.jpg");
        when(storage.type()).thenReturn("volcengine");
        assertThat(adapter.upload("bucket", "dir/name.jpg", new ByteArrayInputStream(new byte[0]), "image/jpeg")
                        .block())
                .isEqualTo("dir/name.jpg");
    }

    @Test
    void exposesOneBusinessFacade(@TempDir Path root) {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(StorageAutoConfiguration.class))
                .withUserConfiguration(DefaultStorageConfiguration.class)
                .withPropertyValues("storage.local.base-path=" + root)
                .run(context -> assertThat(context).hasSingleBean(StorageService.class));
    }
}
