package com.apex.scaffold.infrastructure.storage.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.test.StepVerifier;

/**
 * MinIO存储服务实现类单元测试
 *
 * <p>测试 MinioServiceImpl 的 ensureBucketExistsInternal、createBucketInternal、download、
 * getPresignedUrl、doUpload、doDelete、doExists、buildPresignedUrl 方法，
 * 验证 MinIO 客户端操作的正确性和异常处理。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class MinioServiceImplTest {

    @Mock
    private MinioClient minioClient;

    private MinioServiceImpl minioService;

    /**
     * 设置{@code up}。
     */
    @BeforeEach
    void setUp() {
        minioService = new MinioServiceImpl(minioClient);
    }

    @Test
    @DisplayName("getStorageType - 应返回MINIO")
    void getStorageType_ReturnsMinio() {
        assertThat(minioService.getStorageType()).isEqualTo("MINIO");
    }

    @Test
    @DisplayName("ensureBucketExistsInternal - 存储桶已存在应直接完成")
    void ensureBucketExistsInternal_BucketExists_Completes() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);

        minioService
                .ensureBucketExistsInternal("test-bucket")
                .as(StepVerifier::create)
                .verifyComplete();

        verify(minioClient).bucketExists(any(BucketExistsArgs.class));
        verify(minioClient, never()).makeBucket(any(MakeBucketArgs.class));
    }

    @Test
    @DisplayName("ensureBucketExistsInternal - 存储桶不存在应拒绝上传")
    void ensureBucketExistsInternal_BucketNotExists_ReturnsError() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(false);

        minioService
                .ensureBucketExistsInternal("new-bucket")
                .as(StepVerifier::create)
                .expectErrorSatisfies(error -> assertThat(error.getMessage()).contains("存储桶不存在"))
                .verify();

        verify(minioClient).bucketExists(any(BucketExistsArgs.class));
        verify(minioClient, never()).makeBucket(any(MakeBucketArgs.class));
    }

    @Test
    @DisplayName("ensureBucketExistsInternal - 检查时抛出异常应返回错误")
    void ensureBucketExistsInternal_CheckException_ReturnsError() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenThrow(new RuntimeException("MinIO连接失败"));

        minioService
                .ensureBucketExistsInternal("test-bucket")
                .as(StepVerifier::create)
                .expectErrorSatisfies(e -> {
                    assertThat(e).isInstanceOf(RuntimeException.class);
                    assertThat(e.getMessage()).contains("检查存储桶失败");
                })
                .verify();
    }

    @Test
    @DisplayName("createBucketInternal - 存储桶不存在应创建")
    void createBucketInternal_BucketNotExists_CreatesBucket() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(false);

        minioService.createBucketInternal("new-bucket").as(StepVerifier::create).verifyComplete();

        verify(minioClient).makeBucket(any(MakeBucketArgs.class));
    }

    @Test
    @DisplayName("createBucketInternal - 存储桶已存在应直接完成")
    void createBucketInternal_BucketExists_Completes() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);

        minioService
                .createBucketInternal("existing-bucket")
                .as(StepVerifier::create)
                .verifyComplete();

        verify(minioClient, never()).makeBucket(any(MakeBucketArgs.class));
    }

    @Test
    @DisplayName("createBucketInternal - 创建存储桶时抛出异常应返回错误")
    void createBucketInternal_CreateException_ReturnsError() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenThrow(new RuntimeException("创建存储桶失败"));

        minioService
                .createBucketInternal("test-bucket")
                .as(StepVerifier::create)
                .expectErrorSatisfies(e -> {
                    assertThat(e).isInstanceOf(RuntimeException.class);
                    assertThat(e.getMessage()).contains("创建存储桶失败");
                })
                .verify();
    }

    @Test
    @DisplayName("download - 成功下载文件")
    void download_Success_ReturnsInputStream() throws Exception {
        byte[] content = "test file content".getBytes();
        GetObjectResponse mockResponse = mock(GetObjectResponse.class);
        when(minioClient.getObject(any(GetObjectArgs.class))).thenReturn(mockResponse);
        when(mockResponse.readAllBytes()).thenReturn(content);

        minioService
                .download("test-bucket", "test-file.txt")
                .as(StepVerifier::create)
                .assertNext(stream -> {
                    try {
                        assertThat(stream.readAllBytes()).isEqualTo(content);
                    } catch (java.io.IOException e) {
                        throw new RuntimeException(e);
                    }
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("download - 下载时抛出异常应传递错误")
    void download_Exception_PropagatesError() throws Exception {
        when(minioClient.getObject(any(GetObjectArgs.class))).thenThrow(new RuntimeException("文件不存在"));

        minioService
                .download("test-bucket", "not-exists.txt")
                .as(StepVerifier::create)
                .expectError(RuntimeException.class)
                .verify();
    }

    @Test
    @DisplayName("getPresignedUrl - 使用自定义有效期")
    void getPresignedUrl_CustomExpiry_ReturnsUrl() throws Exception {
        when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                .thenReturn("http://minio:9000/test-bucket/test-file.txt?token=abc");

        minioService
                .getPresignedUrl("test-bucket", "test-file.txt", 30)
                .as(StepVerifier::create)
                .expectNext("http://minio:9000/test-bucket/test-file.txt?token=abc")
                .verifyComplete();

        ArgumentCaptor<GetPresignedObjectUrlArgs> captor = ArgumentCaptor.forClass(GetPresignedObjectUrlArgs.class);
        verify(minioClient).getPresignedObjectUrl(captor.capture());
        assertThat(captor.getValue().expiry()).isEqualTo(30);
    }

    @Test
    @DisplayName("getPresignedUrl - expiry为null应使用默认值300秒")
    void getPresignedUrl_NullExpiry_ReturnsUrl() throws Exception {
        when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                .thenReturn("http://minio:9000/test-bucket/file.pdf?token=xyz");

        minioService
                .getPresignedUrl("test-bucket", "file.pdf", null)
                .as(StepVerifier::create)
                .expectNext("http://minio:9000/test-bucket/file.pdf?token=xyz")
                .verifyComplete();

        ArgumentCaptor<GetPresignedObjectUrlArgs> captor = ArgumentCaptor.forClass(GetPresignedObjectUrlArgs.class);
        verify(minioClient).getPresignedObjectUrl(captor.capture());
        assertThat(captor.getValue().expiry()).isEqualTo(300);
    }

    @Test
    @DisplayName("getPresignedUrl - 生成时抛出异常应传递错误")
    void getPresignedUrl_Exception_PropagatesError() throws Exception {
        when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                .thenThrow(new RuntimeException("生成预签名URL失败"));

        minioService
                .getPresignedUrl("test-bucket", "file.txt", 10)
                .as(StepVerifier::create)
                .expectError(RuntimeException.class)
                .verify();
    }

    @Test
    @DisplayName("doUpload - 成功上传文件")
    void doUpload_Success_ReturnsFileName() throws Exception {
        InputStream inputStream = new ByteArrayInputStream("test content".getBytes());
        when(minioClient.putObject(any(PutObjectArgs.class))).thenReturn(null);

        String result = minioService.doUpload("test-bucket", "my-file.txt", inputStream, "text/plain");

        assertThat(result).matches("[a-f0-9]{32}\\.txt");
        verify(minioClient).putObject(any(PutObjectArgs.class));
    }

    @Test
    @DisplayName("doUpload - 上传时抛出异常应抛出RuntimeException")
    void doUpload_Exception_ThrowsRuntimeException() throws Exception {
        InputStream inputStream = new ByteArrayInputStream("test content".getBytes());
        when(minioClient.putObject(any(PutObjectArgs.class))).thenThrow(new RuntimeException("MinIO写入失败"));

        assertThatThrownBy(() -> minioService.doUpload("test-bucket", "file.txt", inputStream, "text/plain"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("文件上传失败");
    }

    @Test
    @DisplayName("doDelete - 成功删除文件")
    void doDelete_Success_ReturnsTrue() throws Exception {
        minioService.doDelete("test-bucket", "file-to-delete.txt");

        verify(minioClient).removeObject(any(RemoveObjectArgs.class));
    }

    @Test
    @DisplayName("doDelete - 删除时抛出异常应返回false")
    void doDelete_Exception_ReturnsFalse() throws Exception {
        doThrow(new RuntimeException("删除失败")).when(minioClient).removeObject(any(RemoveObjectArgs.class));

        boolean result = minioService.doDelete("test-bucket", "file.txt");

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("doExists - 文件存在应返回true")
    void doExists_FileExists_ReturnsTrue() throws Exception {
        boolean result = minioService.doExists("test-bucket", "existing-file.txt");

        assertThat(result).isTrue();
        verify(minioClient).statObject(any(StatObjectArgs.class));
    }

    @Test
    @DisplayName("doExists - statObject抛出异常应返回false")
    void doExists_Exception_ReturnsFalse() throws Exception {
        doThrow(new RuntimeException("文件不存在")).when(minioClient).statObject(any(StatObjectArgs.class));

        boolean result = minioService.doExists("test-bucket", "not-exists.txt");

        assertThat(result).isFalse();
    }

    /**
     * 构建{@code presignedUrl_CorrectlyBuildsUrl}。
     */
    @Test
    @DisplayName("buildPresignedUrl - 应正确构建URL路径")
    void buildPresignedUrl_CorrectlyBuildsUrl() {
        String url = minioService.buildPresignedUrl("test-bucket", "test-file.txt", 60);

        assertThat(url).isEqualTo("/minio/test-bucket/test-file.txt");
    }

    /**
     * 构建{@code presignedUrl_SubdirectoryPath}。
     */
    @Test
    @DisplayName("buildPresignedUrl - 应使用自定义过期时间和子目录路径")
    void buildPresignedUrl_SubdirectoryPath() {
        String url = minioService.buildPresignedUrl("images", "avatars/user1.jpg", 30);

        assertThat(url).isEqualTo("/minio/images/avatars/user1.jpg");
    }
}
