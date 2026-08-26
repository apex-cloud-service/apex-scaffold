package com.apex.scaffold.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apex.scaffold.application.dto.FileUploadResult;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.domain.service.StorageService;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 文件服务单元测试类
 *
 * <p>测试 FileService 的核心业务逻辑
 *
 * @author Apex Team
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FileServiceTest {

    private static final String DEFAULT_BUCKET = "apex";

    @Mock
    private StorageService storageService;

    private FileService fileService;

    /** 创建使用固定桶、1024 字节上限与 60 秒 URL 有效期的被测服务。 */
    @BeforeEach
    void setUp() {
        fileService = new FileService(storageService, DEFAULT_BUCKET, 1024L, 60);
    }

    /** JPEG 文件头魔数：FF D8 FF */
    private static byte[] jpegHeader() {
        return new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
    }

    /**
     * 上传文件到指定桶成功场景。
     */
    @Test
    @DisplayName("upload - 成功场景")
    void upload_Success() {
        when(storageService.upload(eq("bucket"), eq("photo.jpg"), any(InputStream.class), eq("image/jpeg")))
                .thenReturn(Mono.just("photo.jpg"));

        fileService
                .upload("bucket", "photo.jpg", new ByteArrayInputStream(jpegHeader()), "image/jpeg")
                .as(StepVerifier::create)
                .expectNext("photo.jpg")
                .verifyComplete();
    }

    /** 验证 HTTP 上传重载固定使用服务端桶并返回结构化对象定位信息。 */
    @Test
    @DisplayName("upload - 固定桶并返回结构化结果")
    void uploadBytes_ReturnsStructuredResult() {
        when(storageService.upload(eq("apex"), eq("photo.jpg"), any(InputStream.class), eq("image/jpeg")))
                .thenReturn(Mono.just("uuid-photo.jpg"));
        when(storageService.getPresignedUrl("apex", "uuid-photo.jpg", 60))
                .thenReturn(Mono.just("https://storage.example/uuid-photo.jpg"));

        fileService
                .upload("photo.jpg", jpegHeader(), "image/jpeg", 1, "")
                .as(StepVerifier::create)
                .assertNext(result -> assertThat(result)
                        .isEqualTo(new FileUploadResult(
                                "apex", "uuid-photo.jpg", "https://storage.example/uuid-photo.jpg", 60, null)))
                .verifyComplete();
    }

    /** 验证应用服务自身也会拒绝超限字节数组。 */
    @Test
    @DisplayName("upload - 超过统一大小限制时拒绝")
    void uploadBytes_RejectsOversizedFile() {
        byte[] oversized = new byte[1025];

        fileService
                .upload("photo.jpg", oversized, "image/jpeg", 1, "")
                .as(StepVerifier::create)
                .expectError(BusinessException.class)
                .verify();

        verify(storageService, never()).upload(any(), any(), any(), any());
    }

    /** 不支持的扩展名在写入存储前被拒绝。 */
    @Test
    @DisplayName("upload - 不支持的扩展名被拒绝")
    void uploadBytes_RejectsUnsupportedFileType() {
        assertThatThrownBy(() -> fileService.upload("photo.txt", new byte[] {1, 2, 3}, "text/plain", 1, ""))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持的文件类型");

        verify(storageService, never()).upload(any(), any(), any(), any());
    }

    /** 客户端文件名带路径前缀时仅保留 basename。 */
    @Test
    @DisplayName("upload - 客户端文件名带路径前缀时仅保留 basename")
    void uploadBytes_StripsClientSuppliedPathPrefix() {
        when(storageService.upload(eq("apex"), eq("photo.jpg"), any(InputStream.class), eq("image/jpeg")))
                .thenReturn(Mono.just("photo.jpg"));
        when(storageService.getPresignedUrl("apex", "photo.jpg", 60))
                .thenReturn(Mono.just("https://storage.example/photo.jpg"));

        fileService
                .upload("nested/path/photo.jpg", jpegHeader(), "image/jpeg", 1, "")
                .as(StepVerifier::create)
                .assertNext(result -> assertThat(result.fileName()).isEqualTo("photo.jpg"))
                .verifyComplete();
    }

    /**
     * 获取预签名访问地址。
     */
    @Test
    @DisplayName("getUrl - 成功场景")
    void getUrl_Success() {
        when(storageService.getPresignedUrl("bucket", "obj", 60)).thenReturn(Mono.just("http://example.com/file"));

        fileService
                .getUrl("bucket", "obj")
                .as(StepVerifier::create)
                .expectNext("http://example.com/file")
                .verifyComplete();
    }

    /**
     * 删除文件。
     */
    @Test
    @DisplayName("delete - 成功场景")
    void delete_Success() {
        when(storageService.delete("bucket", "obj")).thenReturn(Mono.just(true));

        fileService
                .delete("bucket", "obj")
                .as(StepVerifier::create)
                .expectNext(true)
                .verifyComplete();
    }

    /**
     * 文件存在。
     */
    @Test
    @DisplayName("exists - 文件存在")
    void exists_True() {
        when(storageService.exists("bucket", "obj")).thenReturn(Mono.just(true));

        fileService
                .exists("bucket", "obj")
                .as(StepVerifier::create)
                .expectNext(true)
                .verifyComplete();
    }

    /**
     * 文件不存在。
     */
    @Test
    @DisplayName("exists - 文件不存在")
    void exists_False() {
        when(storageService.exists("bucket", "obj")).thenReturn(Mono.just(false));

        fileService
                .exists("bucket", "obj")
                .as(StepVerifier::create)
                .expectNext(false)
                .verifyComplete();
    }
}
