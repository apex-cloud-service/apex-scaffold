package com.apex.scaffold.storage;

import com.aliyun.oss.HttpMethod;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.GeneratePresignedUrlRequest;
import com.aliyun.oss.model.ObjectMetadata;
import java.io.InputStream;
import java.time.Instant;
import java.util.Date;

/**
 * 阿里云 OSS 策略，保留键和 MIME，仅对象缺失转换为空值。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@lombok.RequiredArgsConstructor
public final class OssStorage extends CloudStorage {
    private final OSS client;

    @Override
    public String type() {
        return "aliyun-oss";
    }

    @Override
    protected void put(String bucket, String key, InputStream source, String contentType) {
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentType(contentType);
        client.putObject(bucket, key, source, metadata);
    }

    @Override
    protected InputStream get(String bucket, String key) {
        return client.getObject(bucket, key).getObjectContent();
    }

    @Override
    protected void remove(String bucket, String key) {
        client.deleteObject(bucket, key);
    }

    @Override
    protected void head(String bucket, String key) {
        client.getObjectMetadata(bucket, key);
    }

    @Override
    protected String sign(String bucket, String key, int ttlSeconds) {
        GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(bucket, key, HttpMethod.GET);
        request.setExpiration(Date.from(Instant.now().plusSeconds(ttlSeconds)));
        return client.generatePresignedUrl(request).toExternalForm();
    }

    @Override
    protected boolean missing(Throwable error) {
        return error instanceof OSSException response && "NoSuchKey".equals(response.getErrorCode());
    }
}
