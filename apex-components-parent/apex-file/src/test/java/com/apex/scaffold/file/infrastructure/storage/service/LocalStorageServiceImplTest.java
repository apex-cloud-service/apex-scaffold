package com.apex.scaffold.file.infrastructure.storage.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.test.StepVerifier;

/**
 * 本地旧门面验证，覆盖 UUID 键兼容、缺失语义与签名拒绝。
 *
 * @author Apex Team
 * @since 1.3.1
 */
class LocalStorageServiceImplTest {
    @Test
    void roundTrip(@TempDir Path root) throws Exception {
        LocalStorageServiceImpl service = new LocalStorageServiceImpl(root.toString(), "");
        String key = service.upload("bucket", "dir/a.jpg", new ByteArrayInputStream(new byte[] {1, 2}), "image/jpeg")
                .block();
        assertThat(key).matches("dir/[a-f0-9]{32}\\.jpg");
        try (var stream = service.download("bucket", key).block()) {
            assertThat(stream.readAllBytes()).containsExactly(1, 2);
        }
        assertThat(service.exists("bucket", key).block()).isTrue();
        assertThat(service.delete("bucket", key).block()).isTrue();
        StepVerifier.create(service.download("bucket", key)).verifyComplete();
        assertThat(service.delete("bucket", key).block()).isFalse();
        assertThat(service.getPhotoUrlPrefix()).isEqualTo("/uploads/apex");
    }

    @Test
    void invalidPathAndUnsupportedSigning(@TempDir Path root) {
        LocalStorageServiceImpl service = new LocalStorageServiceImpl(root.toString(), "https://cdn.example.com");
        StepVerifier.create(service.download("bucket", "../secret"))
                .expectError(IllegalArgumentException.class)
                .verify();
        StepVerifier.create(service.getPresignedUrl("bucket", "key", 300))
                .expectError(UnsupportedOperationException.class)
                .verify();
        StepVerifier.create(service.getPresignedUrl("bucket", "key", 0))
                .expectError(IllegalArgumentException.class)
                .verify();
        StepVerifier.create(service.createBucket("bucket")).verifyComplete();
    }
}
