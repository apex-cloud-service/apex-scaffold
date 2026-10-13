package com.apex.scaffold.file.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.file.domain.service.StorageService;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@DisplayName("FileService 应用服务单元测试")
class FileServiceTest {

    private static final byte[] JPG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1, 2, 3, 4};

    @Mock
    private StorageService storageService;

    private FileService fileService;

    @BeforeEach
    void setUp() {
        fileService = new FileService(storageService, "apex", 10L * 1024 * 1024, 300);
    }

    @Test
    @DisplayName("upload：合法图片上传应返回定位信息与预签名 URL")
    void upload_happyPath() {
        when(storageService.upload(eq("apex"), anyString(), any(InputStream.class), eq("image/jpeg")))
                .thenReturn(Mono.just("stored.jpg"));
        when(storageService.getPresignedUrl("apex", "stored.jpg", 300)).thenReturn(Mono.just("http://x/stored.jpg"));

        StepVerifier.create(fileService.upload("photo.jpg", JPG, "image/jpeg", null, null))
                .assertNext(result -> {
                    assertThat(result.bucketName()).isEqualTo("apex");
                    assertThat(result.fileName()).isEqualTo("stored.jpg");
                    assertThat(result.url()).isEqualTo("http://x/stored.jpg");
                    assertThat(result.expiresIn()).isEqualTo(300);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("upload：空文件应抛出段参数错误")
    void upload_emptyFile() {
        StepVerifier.create(fileService.upload("photo.jpg", new byte[0], "image/jpeg", null, null))
                .expectErrorSatisfies(ex -> {
                    assertThat(ex).isInstanceOf(BusinessException.class);
                    assertThat(((BusinessException) ex).getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode());
                })
                .verify();
        verify(storageService, never()).upload(anyString(), anyString(), any(InputStream.class), anyString());
    }

    @Test
    @DisplayName("upload：超过大小限制应抛出参数错误")
    void upload_tooLarge() {
        byte[] big = new byte[10 * 1024 * 1024 + 1];
        StepVerifier.create(fileService.upload("photo.jpg", big, "image/jpeg", null, null))
                .expectErrorSatisfies(ex -> {
                    assertThat(ex).isInstanceOf(BusinessException.class);
                    assertThat(((BusinessException) ex).getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode());
                })
                .verify();
    }

    @Test
    @DisplayName("upload：带路径前缀的文件名应清洗为 basename")
    void upload_sanitizeClientFileName() {
        when(storageService.upload(eq("apex"), eq("photo.jpg"), any(InputStream.class), eq("image/jpeg")))
                .thenReturn(Mono.just("photo.jpg"));
        when(storageService.getPresignedUrl("apex", "photo.jpg", 300)).thenReturn(Mono.just("http://x/p.jpg"));

        StepVerifier.create(fileService.upload("C:\\tmp\\photo.jpg", JPG, "image/jpeg", null, null))
                .expectNextCount(1)
                .verifyComplete();
        verify(storageService).upload(eq("apex"), eq("photo.jpg"), any(InputStream.class), eq("image/jpeg"));
    }

    @Test
    @DisplayName("upload：不支持的扩展名应校验失败")
    void upload_unupportedExtension() {
        StepVerifier.create(Mono.defer(() -> fileService.upload("a.txt", JPG, "image/jpeg", null, null)))
                .expectErrorSatisfies(ex -> {
                    assertThat(ex).isInstanceOf(BusinessException.class);
                    assertThat(((BusinessException) ex).getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode());
                })
                .verify();
    }

    @Test
    @DisplayName("upload：不支持的内容类型应校验失败")
    void upload_unupportedContentType() {
        StepVerifier.create(Mono.defer(() -> fileService.upload("photo.jpg", JPG, "text/plain", null, null)))
                .expectErrorSatisfies(ex -> {
                    assertThat(ex).isInstanceOf(BusinessException.class);
                    assertThat(((BusinessException) ex).getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode());
                })
                .verify();
    }

    @Test
    @DisplayName("upload：魔数与声明类型不一致应校验失败")
    void upload_magicMismatch() {
        StepVerifier.create(Mono.defer(() -> fileService.upload("photo.png", JPG, "image/png", null, null)))
                .expectErrorSatisfies(ex -> {
                    assertThat(ex).isInstanceOf(BusinessException.class);
                    assertThat(((BusinessException) ex).getCode()).isEqualTo(ResultCode.FILE_TYPE_MISMATCH.getCode());
                })
                .verify();
    }

    @Test
    @DisplayName("upload(流)：读取输入流失败应返回系统错误")
    void upload_streamReadFailure() {
        InputStream broken = new InputStream() {
            @Override
            public int read() {
                return -1;
            }

            @Override
            public int read(byte[] b, int off, int len) throws java.io.IOException {
                throw new java.io.IOException("boom");
            }
        };
        StepVerifier.create(fileService.upload("apex", "photo.jpg", broken, "image/jpeg"))
                .expectErrorSatisfies(ex -> {
                    assertThat(ex).isInstanceOf(BusinessException.class);
                    assertThat(((BusinessException) ex).getCode()).isEqualTo(ResultCode.ERROR.getCode());
                })
                .verify();
    }

    @Test
    @DisplayName("verify：合法流上传应委托存储服务")
    void upload_streamHappyPath() {
        when(storageService.upload(eq("apex"), eq("photo.jpg"), any(InputStream.class), eq("image/jpeg")))
                .thenReturn(Mono.just("photo.jpg"));

        StepVerifier.create(fileService.upload("apex", "photo.jpg", new ByteArrayInputStream(JPG), "image/jpeg"))
                .expectNext("photo.jpg")
                .verifyComplete();
    }

    @Test
    @DisplayName("getUrl/download/delete/exists：应直接委托存储服务")
    void delegates() {
        when(storageService.getPresignedUrl("apex", "f.jpg", 300)).thenReturn(Mono.just("http://url"));
        when(storageService.download("apex", "f.jpg")).thenReturn(Mono.just(new ByteArrayInputStream(JPG)));
        when(storageService.delete("apex", "f.jpg")).thenReturn(Mono.just(true));
        when(storageService.exists("apex", "f.jpg")).thenReturn(Mono.just(true));

        StepVerifier.create(fileService.getUrl("apex", "f.jpg"))
                .expectNext("http://url")
                .verifyComplete();
        StepVerifier.create(fileService.download("apex", "f.jpg"))
                .expectNextCount(1)
                .verifyComplete();
        StepVerifier.create(fileService.delete("apex", "f.jpg"))
                .expectNext(true)
                .verifyComplete();
        StepVerifier.create(fileService.exists("apex", "f.jpg"))
                .expectNext(true)
                .verifyComplete();
    }
}
