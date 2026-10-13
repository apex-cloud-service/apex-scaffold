package com.apex.scaffold.file.infrastructure.storage.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@DisplayName("AbstractStorageService 抽象基类单元测试")
public class AbstractStorageServiceTest {

    private static class TestStorageService extends AbstractStorageService {
        boolean bucketExists = true;
        boolean created = false;
        String doUploadResult = "generated-name";
        boolean doExistsResult = true;
        boolean throwOnDelete = false;
        String downloadContent = "hello";
        String bucketNameOverride = "test-bucket";

        @Override
        protected String getStorageType() {
            return "TEST";
        }

        @Override
        protected Mono<Void> ensureBucketExistsInternal(String bucketName) {
            if (!bucketExists) {
                return createBucketInternal(bucketName);
            }
            return Mono.empty();
        }

        @Override
        protected Mono<Void> createBucketInternal(String bucketName) {
            created = true;
            return Mono.empty();
        }

        @Override
        protected String doUpload(String bucketName, String fileName, InputStream inputStream, String contentType) {
            return doUploadResult;
        }

        @Override
        protected boolean doDelete(String bucketName, String fileName) {
            if (throwOnDelete) {
                throw new IllegalStateException("delete failed");
            }
            return true;
        }

        @Override
        protected boolean doExists(String bucketName, String fileName) {
            return doExistsResult;
        }

        @Override
        public Mono<InputStream> download(String bucketName, String objectName) {
            return Mono.just(new ByteArrayInputStream(downloadContent.getBytes(StandardCharsets.UTF_8)));
        }

        @Override
        public Mono<String> getPresignedUrl(String bucketName, String objectName, Integer expiry) {
            return Mono.just(buildPresignedUrl(bucketName, objectName, expiry == null ? 300 : expiry));
        }

        @Override
        protected String getBucketName() {
            return bucketNameOverride;
        }

        String validateFileTypePkg(String contentType) {
            validateFileType(contentType);
            return "ok";
        }

        String generateUniqueFileNamePkg(String name) {
            return generateUniqueFileName(name);
        }

        String buildPresignedUrlPkg(String bucket, String file, int expiry) {
            return buildPresignedUrl(bucket, file, expiry);
        }

        String getPhotoUrlPrefixPkg() {
            return getPhotoUrlPrefix();
        }
    }

    @Test
    @DisplayName("upload：合法类型应委托实际上报并返回唯一文件名")
    void upload_happyPath() {
        TestStorageService svc = new TestStorageService();
        svc.doUploadResult = "uuid.jpg";
        StepVerifier.create(svc.upload("bkt", "photo.jpg", new ByteArrayInputStream(new byte[] {1}), "image/jpeg"))
                .expectNext("uuid.jpg")
                .verifyComplete();
    }

    @Test
    @DisplayName("upload：存储桶不存在时应自动创建后再上报")
    void upload_bucketAutoCreate() {
        TestStorageService svc = new TestStorageService();
        svc.bucketExists = false;
        StepVerifier.create(svc.upload("bkt", "a.png", new ByteArrayInputStream(new byte[] {1}), "image/png"))
                .expectNext("generated-name")
                .verifyComplete();
        assertThat(svc.created).isTrue();
    }

    @Test
    @DisplayName("upload：不支持的文件类型应返回错误")
    void upload_disallowedType() {
        TestStorageService svc = new TestStorageService();
        StepVerifier.create(svc.upload("bkt", "a.zip", new ByteArrayInputStream(new byte[] {1}), "text/csv"))
                .expectError(IllegalArgumentException.class)
                .verify();
    }

    @Test
    @DisplayName("uploadFile：字节数组重载应正常上报")
    void uploadFile_bytes() {
        TestStorageService svc = new TestStorageService();
        svc.doUploadResult = "uuid.jpg";
        StepVerifier.create(svc.uploadFile("bkt", "photo.jpg", new byte[] {1, 2, 3}, "image/jpeg"))
                .expectNext("uuid.jpg")
                .verifyComplete();
    }

    @Test
    @DisplayName("delete：删除失败应返回 false 而非抛出")
    void delete_errorReturnsFalse() {
        TestStorageService svc = new TestStorageService();
        svc.throwOnDelete = true;
        StepVerifier.create(svc.delete("bkt", "a.jpg")).expectNext(false).verifyComplete();
    }

    @Test
    @DisplayName("exists：应透传底层存在性结果")
    void exists() {
        TestStorageService svc = new TestStorageService();
        svc.doExistsResult = true;
        StepVerifier.create(svc.exists("bkt", "a.jpg")).expectNext(true).verifyComplete();
        svc.doExistsResult = false;
        StepVerifier.create(svc.exists("bkt", "a.jpg")).expectNext(false).verifyComplete();
    }

    @Test
    @DisplayName("validateFileType：允许的 MIME 类型通过，非法类型抛 IllegalArgumentException")
    void validateFileType() {
        TestStorageService svc = new TestStorageService();
        assertThat(svc.validateFileTypePkg("image/jpeg")).isEqualTo("ok");
        assertThat(svc.validateFileTypePkg("application/pdf")).isEqualTo("ok");
        assertThatThrownBy(() -> svc.validateFileTypePkg("text/csv")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("generateUniqueFileName：应保留扩展名并生成去连字符 UUID 文件名")
    void generateUniqueFileName() {
        TestStorageService svc = new TestStorageService();
        String name = svc.generateUniqueFileNamePkg("photo.jpg");
        assertThat(name).endsWith(".jpg");
        assertThat(name).doesNotContain("-");
        assertThat(name).isEqualTo(name.replaceFirst("\\.jpg$", "") + ".jpg");
        assertThat(name.length()).isEqualTo(32 + 4);
    }

    @Test
    @DisplayName("generateUniqueFileName：应保留目录前缀并规范化反斜杠")
    void generateUniqueFileName_preservesDir() {
        TestStorageService svc = new TestStorageService();
        String win = svc.generateUniqueFileNamePkg("dir\\sub\\x.png");
        assertThat(win).startsWith("dir/sub/").endsWith(".png");
        String posix = svc.generateUniqueFileNamePkg("a/b/c.txt");
        assertThat(posix).startsWith("a/b/").endsWith(".txt");
    }

    @Test
    @DisplayName("buildPresignedUrl：应构建 /storage/{bucket}/{file}")
    void buildPresignedUrl() {
        TestStorageService svc = new TestStorageService();
        assertThat(svc.buildPresignedUrlPkg("bkt", "a.jpg", 300)).isEqualTo("/storage/bkt/a.jpg");
    }

    @Test
    @DisplayName("getPhotoUrlPrefix：应返回 /storage/{bucketName}")
    void getPhotoUrlPrefix() {
        TestStorageService svc = new TestStorageService();
        svc.bucketNameOverride = "apex";
        assertThat(svc.getPhotoUrlPrefixPkg()).isEqualTo("/storage/apex");
    }

    @Test
    @DisplayName("getPresignedUrl：应委托 buildPresignedUrl（expiry 空回退 300）")
    void getPresignedUrl_defaultExpiry() {
        TestStorageService svc = new TestStorageService();
        StepVerifier.create(svc.getPresignedUrl("bkt", "a.jpg", null))
                .expectNext("/storage/bkt/a.jpg")
                .verifyComplete();
    }

    @Test
    @DisplayName("getStorageType：应返回子类声明的类型")
    void getStorageType() {
        TestStorageService svc = new TestStorageService();
        assertThat(svc.getStorageType()).isEqualTo("TEST");
    }
}
