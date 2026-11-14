package com.apex.scaffold.storage;

import com.qiniu.common.QiniuException;
import com.qiniu.http.Client;
import com.qiniu.http.Response;
import com.qiniu.storage.BucketManager;
import com.qiniu.storage.Configuration;
import com.qiniu.storage.DownloadUrl;
import com.qiniu.storage.UploadManager;
import com.qiniu.util.Auth;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Instant;

/**
 * 七牛策略，下载域名固定关联一个桶，避免通过调用方桶名访问其他域名。
 * 响应流关闭时释放 HTTP 响应，上传响应及时关闭。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
public final class QiniuStorage extends CloudStorage {
    private static final String HTTPS_SCHEME = "https";
    private final Auth auth;
    private final UploadManager upload;
    private final BucketManager buckets;
    private final Client http;
    private final String bucket;
    private final URI domain;

    /**
     * 创建固定桶与可信 HTTPS 下载域名绑定的七牛策略。
     *
     * @param auth 七牛凭据
     * @param configuration 显式区域配置
     * @param bucket 域名对应桶
     * @param domain 可信下载域名
     * @throws IllegalArgumentException 下载域名不是无路径、无认证信息的 HTTPS 域名
     */
    public QiniuStorage(Auth auth, Configuration configuration, String bucket, String domain) {
        this.auth = auth;
        this.upload = new UploadManager(configuration);
        this.buckets = new BucketManager(auth, configuration);
        this.http = new Client(configuration);
        this.bucket = bucket;
        this.domain = URI.create(domain);
        if (!HTTPS_SCHEME.equals(this.domain.getScheme())
                || this.domain.getHost() == null
                || this.domain.getUserInfo() != null
                || this.domain.getQuery() != null
                || this.domain.getFragment() != null
                || !this.domain.getPath().isEmpty()) {
            throw new IllegalArgumentException("七牛下载域名必须是无路径的 HTTPS 域名");
        }
    }

    @Override
    public String type() {
        return "qiniu";
    }

    /**
     * 验证请求桶与可信下载域名绑定的桶一致。
     *
     * @param requested 请求桶名
     * @throws IllegalArgumentException 请求桶与配置桶不一致
     */
    private void validateBucket(String requested) {
        if (!bucket.equals(requested)) {
            throw new IllegalArgumentException("七牛桶与下载域名不匹配");
        }
    }

    @Override
    protected void put(String bucket, String key, InputStream source, String contentType) throws Exception {
        validateBucket(bucket);
        Response response = upload.put(source, key, auth.uploadToken(bucket, key), null, contentType);
        try {
            if (!response.isOK()) {
                throw new QiniuException(response);
            }
        } finally {
            response.close();
        }
    }

    @Override
    protected InputStream get(String bucket, String key) throws Exception {
        Response response = http.get(sign(bucket, key, 300));
        if (!response.isOK()) {
            QiniuException error = new QiniuException(response);
            response.close();
            throw error;
        }
        try {
            return new FilterInputStream(response.bodyStream()) {
                @Override
                public void close() throws IOException {
                    try {
                        super.close();
                    } finally {
                        response.close();
                    }
                }
            };
        } catch (Exception error) {
            response.close();
            throw error;
        }
    }

    @Override
    protected void remove(String bucket, String key) throws Exception {
        validateBucket(bucket);
        Response response = buckets.delete(bucket, key);
        try {
            if (!response.isOK()) {
                throw new QiniuException(response);
            }
        } finally {
            response.close();
        }
    }

    @Override
    protected void head(String bucket, String key) throws Exception {
        validateBucket(bucket);
        buckets.stat(bucket, key);
    }

    @Override
    protected String sign(String bucket, String key, int ttlSeconds) throws Exception {
        validateBucket(bucket);
        return new DownloadUrl(domain.getAuthority(), true, key)
                .buildURL(auth, Instant.now().getEpochSecond() + ttlSeconds);
    }

    @Override
    protected boolean missing(Throwable error) {
        return error instanceof QiniuException response
                && response.response != null
                && (response.response.statusCode == 612 || response.response.statusCode == 404);
    }
}
