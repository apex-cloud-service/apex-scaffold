package com.apex.scaffold.storage;

import com.volcengine.tos.TOSV2;
import com.volcengine.tos.TosServerException;
import com.volcengine.tos.model.object.*;
import java.io.InputStream;

/**
 * 火山 TOS 策略，下载直接交付 SDK 流，不在内存缓存完整对象。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@lombok.RequiredArgsConstructor
public final class TosStorage extends CloudStorage {
    private final TOSV2 client;

    @Override
    public String type() {
        return "volcengine";
    }

    @Override
    protected void put(String bucket, String key, InputStream source, String contentType) {
        client.putObject(new PutObjectInput()
                .setBucket(bucket)
                .setKey(key)
                .setContent(source)
                .setOptions(new ObjectMetaRequestOptions().setContentType(contentType)));
    }

    @Override
    protected InputStream get(String bucket, String key) {
        return client.getObject(new GetObjectV2Input().setBucket(bucket).setKey(key))
                .getContent();
    }

    @Override
    protected void remove(String bucket, String key) {
        client.deleteObject(new DeleteObjectInput().setBucket(bucket).setKey(key));
    }

    @Override
    protected void head(String bucket, String key) {
        client.headObject(new HeadObjectV2Input().setBucket(bucket).setKey(key));
    }

    @Override
    protected String sign(String bucket, String key, int ttlSeconds) {
        return client.preSignedURL(new PreSignedURLInput()
                        .setBucket(bucket)
                        .setKey(key)
                        .setHttpMethod("GET")
                        .setExpires(ttlSeconds))
                .getSignedUrl();
    }

    @Override
    protected boolean missing(Throwable error) {
        return error instanceof TosServerException response && "NoSuchKey".equals(response.getCode());
    }
}
