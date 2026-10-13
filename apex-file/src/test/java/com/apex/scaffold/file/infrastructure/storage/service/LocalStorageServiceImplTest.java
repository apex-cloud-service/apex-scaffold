package com.apex.scaffold.file.infrastructure.storage.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.test.StepVerifier;

@DisplayName("LocalStorageServiceImpl 本地存储单元测试")
class LocalStorageServiceImplTest {

    private static final byte[] CONTENT = "hello-apex".getBytes(StandardCharsets.UTF_8);

    private LocalStorageServiceImpl service(Path base) {
        return new LocalStorageServiceImpl(base.toString(), "");
    }

    @Test
    @DisplayName("构造：空根目录应抛出 IllegalArgumentException")
    void constructor_blankBasePath() {
        assertThatThrownBy(() -> new LocalStorageServiceImpl("  ", "")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("getStorageType/getBucketName/getPhotoUrlPrefix：应返回本地存储标识")
    void metadata(@TempDir Path base) {
        LocalStorageServiceImpl svc = service(base);
        assertThat(svc.getPhotoUrlPrefix()).isEqualTo("/uploads/apex");
    }

    @Test
    @DisplayName("upload：应写入本地文件并返回唯一文件名")
    void upload(@TempDir Path base) {
        LocalStorageServiceImpl svc = service(base);
        StepVerifier.create(svc.upload("bkt", "photo.jpg", new ByteArrayInputStream(CONTENT), "image/jpeg"))
                .assertNext(name -> {
                    assertThat(name).endsWith(".jpg");
                    assertThat(Files.exists(base.resolve("bkt").resolve(name))).isTrue();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("download：应读取已上传文件内容")
    void download(@TempDir Path base) {
        LocalStorageServiceImpl svc = service(base);
        String name = svc.upload("bkt", "photo.jpg", new ByteArrayInputStream(CONTENT), "image/jpeg")
                .block();
        StepVerifier.create(svc.download("bkt", name))
                .assertNext(stream -> assertThat(readAll(stream)).isEqualTo("hello-apex"))
                .verifyComplete();
    }

    @Test
    @DisplayName("download：文件不存在应返回错误")
    void download_missing(@TempDir Path base) {
        LocalStorageServiceImpl svc = service(base);
        StepVerifier.create(svc.download("bkt", "nope.jpg")).expectError().verify();
    }

    @Test
    @DisplayName("exists：已上传返回 true，不存在返回 false")
    void exists(@TempDir Path base) {
        LocalStorageServiceImpl svc = service(base);
        String name = svc.upload("bkt", "photo.jpg", new ByteArrayInputStream(CONTENT), "image/jpeg")
                .block();
        StepVerifier.create(svc.exists("bkt", name)).expectNext(true).verifyComplete();
        StepVerifier.create(svc.exists("bkt", "missing.jpg")).expectNext(false).verifyComplete();
    }

    @Test
    @DisplayName("delete：删除已存在返回 true，删除不存在返回 false")
    void delete(@TempDir Path base) {
        LocalStorageServiceImpl svc = service(base);
        String name = svc.upload("bkt", "photo.jpg", new ByteArrayInputStream(CONTENT), "image/jpeg")
                .block();
        StepVerifier.create(svc.delete("bkt", name)).expectNext(true).verifyComplete();
        StepVerifier.create(svc.delete("bkt", name)).expectNext(false).verifyComplete();
    }

    @Test
    @DisplayName("路径遍历：文件名逃逸存储桶应抛出 STORAGE_ERROR")
    void pathTraversal(@TempDir Path base) {
        LocalStorageServiceImpl svc = service(base);
        StepVerifier.create(svc.download("bkt", "../../etc/passwd"))
                .expectErrorSatisfies(ex -> {
                    assertThat(ex).isInstanceOf(BusinessException.class);
                    assertThat(((BusinessException) ex).getCode()).isEqualTo(ResultCode.STORAGE_ERROR.getCode());
                })
                .verify();
    }

    @Test
    @DisplayName("存储桶名为空应抛出 STORAGE_ERROR")
    void blankBucket(@TempDir Path base) {
        LocalStorageServiceImpl svc = service(base);
        StepVerifier.create(svc.download("  ", "a.jpg"))
                .expectErrorSatisfies(ex -> {
                    assertThat(ex).isInstanceOf(BusinessException.class);
                    assertThat(((BusinessException) ex).getCode()).isEqualTo(ResultCode.STORAGE_ERROR.getCode());
                })
                .verify();
    }

    @Test
    @DisplayName("getPresignedUrl：未配置 publicBaseUrl 应返回相对路径")
    void getPresignedUrl_relative(@TempDir Path base) {
        LocalStorageServiceImpl svc = service(base);
        StepVerifier.create(svc.getPresignedUrl("bkt", "a b.jpg", 300))
                .assertNext(url -> assertThat(url).startsWith("/api/v1/file/content?fileName="))
                .verifyComplete();
    }

    @Test
    @DisplayName("getPresignedUrl：配置 publicBaseUrl 应拼接前缀并去除末尾斜杠")
    void getPresignedUrl_publicBaseUrl(@TempDir Path base) {
        LocalStorageServiceImpl svc = new LocalStorageServiceImpl(base.toString(), "https://cdn.example.com/");
        StepVerifier.create(svc.getPresignedUrl("bkt", "a.jpg", 300))
                .assertNext(url -> assertThat(url).startsWith("https://cdn.example.com/api/v1/file/content?fileName="))
                .verifyComplete();
    }

    @Test
    @DisplayName("createBucket：应创建存储桶目录")
    void createBucket(@TempDir Path base) {
        LocalStorageServiceImpl svc = service(base);
        StepVerifier.create(svc.createBucket("newbkt")).verifyComplete();
        assertThat(Files.isDirectory(base.resolve("newbkt"))).isTrue();
    }

    private static String readAll(InputStream stream) {
        try {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
