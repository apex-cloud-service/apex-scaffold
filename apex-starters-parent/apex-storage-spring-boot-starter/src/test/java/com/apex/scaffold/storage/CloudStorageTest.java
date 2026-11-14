package com.apex.scaffold.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.ObjectMetadata;
import com.volcengine.tos.TOSV2;
import com.volcengine.tos.TosServerException;
import com.volcengine.tos.model.object.PutObjectInput;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.test.StepVerifier;

/**
 * 验证 SDK 请求参数、延迟订阅与错误分类，不依赖真实云凭据。
 *
 * @author Apex Team
 * @since 1.3.1
 */
class CloudStorageTest {
    @Test
    void minioUploadIsDeferredAndRetainsKeyAndMime() throws Exception {
        MinioClient client = mock(MinioClient.class);
        MinioStorage storage = new MinioStorage(client);
        var operation = storage.upload("bucket", "dir/key", new ByteArrayInputStream(new byte[0]), "image/jpeg");
        verifyNoInteractions(client);
        StepVerifier.create(operation).expectNext("dir/key").verifyComplete();
        ArgumentCaptor<PutObjectArgs> request = ArgumentCaptor.forClass(PutObjectArgs.class);
        verify(client).putObject(request.capture());
        assertThat(request.getValue().object()).isEqualTo("dir/key");
        assertThat(request.getValue().contentType()).isEqualTo("image/jpeg");
        StepVerifier.create(storage.presignGet("bucket", "key", 0))
                .expectError(IllegalArgumentException.class)
                .verify();
    }

    @Test
    void tosMimeAndErrorsRemainDistinct() {
        TOSV2 client = mock(TOSV2.class);
        TosStorage storage = new TosStorage(client);
        StepVerifier.create(storage.upload("bucket", "key", new ByteArrayInputStream(new byte[0]), "image/png"))
                .expectNext("key")
                .verifyComplete();
        ArgumentCaptor<PutObjectInput> request = ArgumentCaptor.forClass(PutObjectInput.class);
        verify(client).putObject(request.capture());
        assertThat(request.getValue().getOptions().getContentType()).isEqualTo("image/png");
        when(client.headObject(any())).thenThrow(new TosServerException(404).setCode("NoSuchKey"));
        StepVerifier.create(storage.exists("bucket", "key")).expectNext(false).verifyComplete();
        doThrow(new TosServerException(403).setCode("AccessDenied"))
                .when(client)
                .headObject(any());
        StepVerifier.create(storage.exists("bucket", "key"))
                .expectError(TosServerException.class)
                .verify();
    }

    @Test
    void ossPreservesMimeAndPropagatesAuthorizationFailure() {
        OSS client = mock(OSS.class);
        OssStorage storage = new OssStorage(client);
        StepVerifier.create(storage.upload("bucket", "key", new ByteArrayInputStream(new byte[0]), "text/plain"))
                .expectNext("key")
                .verifyComplete();
        ArgumentCaptor<ObjectMetadata> metadata = ArgumentCaptor.forClass(ObjectMetadata.class);
        verify(client).putObject(eq("bucket"), eq("key"), any(java.io.InputStream.class), metadata.capture());
        assertThat(metadata.getValue().getContentType()).isEqualTo("text/plain");
        OSSException missing = mock(OSSException.class);
        when(missing.getErrorCode()).thenReturn("NoSuchKey");
        when(client.getObjectMetadata("bucket", "key")).thenThrow(missing);
        StepVerifier.create(storage.delete("bucket", "key")).expectNext(false).verifyComplete();
        verify(client, never()).deleteObject(anyString(), anyString());
        doThrow(new IllegalStateException("denied")).when(client).getObjectMetadata("bucket", "key");
        StepVerifier.create(storage.exists("bucket", "key"))
                .expectError(IllegalStateException.class)
                .verify();
    }

    @Test
    void qiniuSignsEncodedKeyAndRejectsForeignBucket() {
        QiniuStorage storage = new QiniuStorage(
                com.qiniu.util.Auth.create("access", "secret"),
                new com.qiniu.storage.Configuration(com.qiniu.storage.Region.region0()),
                "bucket",
                "https://cdn.example.com");
        String url = storage.presignGet("bucket", "dir/a b.jpg", 300).block();
        assertThat(url).startsWith("https://cdn.example.com/dir/a%20b.jpg").contains("token=");
        StepVerifier.create(storage.presignGet("other", "key", 300))
                .expectError(IllegalArgumentException.class)
                .verify();
        StepVerifier.create(storage.presignGet("bucket", "key", 604801))
                .expectError(IllegalArgumentException.class)
                .verify();
    }
}
