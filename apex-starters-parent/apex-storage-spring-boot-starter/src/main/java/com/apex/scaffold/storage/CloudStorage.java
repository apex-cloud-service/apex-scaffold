package com.apex.scaffold.storage;

import java.io.InputStream;
import reactor.core.publisher.Mono;

/**
 * 云存储同步 SDK 的订阅边界，仅将明确的对象缺失转换为空结果。
 * 云端桶由部署预创建，上传不重试调用方流。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@SuppressWarnings("PMD.AbstractClassShouldStartWithAbstractNamingRule")
public abstract class CloudStorage extends BlockingObjectStorage {
    /**
     * 将借用的输入流同步上传至预创建的桶，禁止关闭原始流。
     *
     * @param bucket 目标桶
     * @param key 对象键
     * @param source 不转移所有权的输入流
     * @param contentType MIME 类型
     * @throws Exception SDK 上传失败
     */
    protected abstract void put(String bucket, String key, InputStream source, String contentType) throws Exception;

    /**
     * 打开下载流，资源所有权交付给订阅者。
     *
     * @param bucket 目标桶
     * @param key 对象键
     * @return 由调用方关闭的对象流
     * @throws Exception SDK 下载失败
     */
    protected abstract InputStream get(String bucket, String key) throws Exception;

    /**
     * 同步提交对象删除请求，失败保持 SDK 异常。
     *
     * @param bucket 目标桶
     * @param key 对象键
     * @throws Exception SDK 删除失败
     */
    protected abstract void remove(String bucket, String key) throws Exception;

    /**
     * 查询对象元数据，区分对象缺失与权限、网络错误。
     *
     * @param bucket 目标桶
     * @param key 对象键
     * @throws Exception SDK 元数据查询失败
     */
    protected abstract void head(String bucket, String key) throws Exception;

    /**
     * 按给定有效秒数生成 GET 签名地址。
     *
     * @param bucket 目标桶
     * @param key 对象键
     * @param ttlSeconds 已校验的签名有效秒数
     * @return GET 预签名地址
     * @throws Exception SDK 签名失败
     */
    protected abstract String sign(String bucket, String key, int ttlSeconds) throws Exception;

    /**
     * 判断异常是否明确代表对象缺失，不能吞掉其他错误。
     *
     * @param error SDK 抛出的异常
     * @return 仅对象缺失时返回 true
     */
    protected abstract boolean missing(Throwable error);

    @Override
    public final Mono<String> upload(String bucket, String key, InputStream source, String contentType) {
        return io(() -> {
            // SDK 只能消费流，不能夺取调用方的关闭责任。
            InputStream borrowed = new java.io.FilterInputStream(source) {
                @Override
                public void close() {}
            };
            put(bucket, key, borrowed, contentType);
            return key;
        });
    }

    @Override
    public final Mono<InputStream> download(String bucket, String key) {
        return io(() -> get(bucket, key))
                .onErrorResume(this::missing, error -> Mono.empty())
                .doOnDiscard(InputStream.class, stream -> closeStream(stream).subscribe());
    }

    @Override
    public final Mono<Boolean> delete(String bucket, String key) {
        return io(() -> {
                    head(bucket, key);
                    remove(bucket, key);
                    return true;
                })
                .onErrorResume(this::missing, error -> Mono.just(false));
    }

    @Override
    public final Mono<Boolean> exists(String bucket, String key) {
        return io(() -> {
                    head(bucket, key);
                    return true;
                })
                .onErrorResume(this::missing, error -> Mono.just(false));
    }

    @Override
    public final Mono<String> presignGet(String bucket, String key, int ttlSeconds) {
        return io(() -> {
            validateTtl(ttlSeconds);
            return sign(bucket, key, ttlSeconds);
        });
    }
}
