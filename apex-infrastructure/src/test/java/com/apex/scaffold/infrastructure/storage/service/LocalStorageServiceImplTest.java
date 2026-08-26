package com.apex.scaffold.infrastructure.storage.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.apex.scaffold.common.exception.BusinessException;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@link LocalStorageServiceImpl} 可配置目录与路径安全测试。
 *
 * @author Apex Team
 * @since 1.0.0
 */
@DisplayName("Local 本地存储服务")
class LocalStorageServiceImplTest {

    @TempDir
    private Path externalStoragePath;

    /** 验证文件能够保存到项目外指定的绝对路径。 */
    @Test
    void shouldUploadToConfiguredExternalPath() throws Exception {
        LocalStorageServiceImpl service = new LocalStorageServiceImpl(externalStoragePath.toString(), "");
        byte[] content = "external-storage".getBytes(StandardCharsets.UTF_8);

        String savedName = service.upload("apex", "avatar.txt", new ByteArrayInputStream(content), "text/plain")
                .block(Duration.ofSeconds(5));

        Path savedFile = externalStoragePath.resolve("apex").resolve(savedName);
        assertThat(savedFile).exists();
        assertThat(Files.readAllBytes(savedFile)).isEqualTo(content);
    }

    /** 验证带目录前缀的对象名会自动创建子目录并落盘。 */
    @Test
    void shouldUploadToNestedDirectoryWhenObjectNameContainsPrefix() throws Exception {
        LocalStorageServiceImpl service = new LocalStorageServiceImpl(externalStoragePath.toString(), "");
        byte[] content = "sex-directory-storage".getBytes(StandardCharsets.UTF_8);

        String savedName = service.upload("apex", "AM/avatar.txt", new ByteArrayInputStream(content), "text/plain")
                .block(Duration.ofSeconds(5));

        Path savedFile = externalStoragePath.resolve("apex").resolve(savedName);
        assertThat(savedName).startsWith("AM/");
        assertThat(savedFile).exists();
        assertThat(Files.readAllBytes(savedFile)).isEqualTo(content);
    }

    /** 验证存储桶名称不能逃逸配置的根目录。 */
    @Test
    void shouldRejectBucketPathTraversal() {
        LocalStorageServiceImpl service = new LocalStorageServiceImpl(externalStoragePath.toString(), "");

        assertThatThrownBy(() -> service.download("../outside", "secret.txt").block(Duration.ofSeconds(5)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("非法的存储桶路径");
    }

    /** 验证空白根目录配置会在启动阶段失败。 */
    @Test
    void shouldRejectBlankBasePath() {
        assertThatThrownBy(() -> new LocalStorageServiceImpl(" ", ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("根目录不能为空");
    }
}
