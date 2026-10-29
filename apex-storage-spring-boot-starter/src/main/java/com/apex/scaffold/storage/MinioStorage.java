package com.apex.scaffold.storage;

import io.minio.*;
import io.minio.errors.ErrorResponseException;
import io.minio.http.Method;
import java.io.InputStream;

/**
 * MinIO SDK 策略，原样保存键与内容类型，仅 NoSuchKey/NoSuchObject 表示对象缺失。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@lombok.RequiredArgsConstructor
public final class MinioStorage extends CloudStorage {
    private final MinioClient client;

    @Override
    public String type() {
        return "minio";
    }

    @Override
    protected void put(String bucket, String key, InputStream source, String contentType) throws Exception {
        client.putObject(PutObjectArgs.builder().bucket(bucket).object(key).stream(source, -1, 10485760)
                .contentType(contentType)
                .build());
    }

    @Override
    protected InputStream get(String bucket, String key) throws Exception {
        return client.getObject(
                GetObjectArgs.builder().bucket(bucket).object(key).build());
    }

    @Override
    protected void remove(String bucket, String key) throws Exception {
        client.removeObject(
                RemoveObjectArgs.builder().bucket(bucket).object(key).build());
    }

    @Override
    protected void head(String bucket, String key) throws Exception {
        client.statObject(StatObjectArgs.builder().bucket(bucket).object(key).build());
    }

    @Override
    protected String sign(String bucket, String key, int ttlSeconds) throws Exception {
        return client.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                .bucket(bucket)
                .object(key)
                .method(Method.GET)
                .expiry(ttlSeconds)
                .build());
    }

    @Override
    protected boolean missing(Throwable error) {
        return error instanceof ErrorResponseException response
                && ("NoSuchKey".equals(response.errorResponse().code())
                        || "NoSuchObject".equals(response.errorResponse().code()));
    }
}
