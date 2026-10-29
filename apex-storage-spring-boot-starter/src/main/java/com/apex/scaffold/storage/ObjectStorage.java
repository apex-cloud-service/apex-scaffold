package com.apex.scaffold.storage;

import java.io.InputStream;
import java.util.function.Function;
import reactor.core.publisher.Mono;

/**
 * 与业务域无关的对象存储端口。上传流归调用方所有，下载交付的流由调用方关闭。
 * 仅明确的对象缺失转换为空或 false，其余错误通过响应式信号传播。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
public interface ObjectStorage {
    /**
     * 获取稳定的提供商标识。
     * @return local、minio、volcengine、aliyun-oss 或 qiniu
     */
    String type();

    /**
     * 将调用方输入流上传至指定桶，原样保存对象键。
     * @param bucket 目标桶
     * @param key 对象键
     * @param source 调用方负责关闭的输入流
     * @param contentType MIME 类型
     * @return 实际对象键
     */
    Mono<String> upload(String bucket, String key, InputStream source, String contentType);

    /**
     * 打开对象下载流，调用方在消费结束后关闭资源。
     * @param bucket 目标桶
     * @param key 对象键
     * @return 输入流，对象缺失时为空
     */
    Mono<InputStream> download(String bucket, String key);

    /**
     * 删除对象，仅明确缺失时返回 false。
     * @param bucket 目标桶
     * @param key 对象键
     * @return 删除请求结果
     */
    Mono<Boolean> delete(String bucket, String key);

    /**
     * 查询对象存在性，访问失败保留错误信号。
     * @param bucket 目标桶
     * @param key 对象键
     * @return 对象是否存在
     */
    Mono<Boolean> exists(String bucket, String key);

    /**
     * 生成 GET 预签名 URL，本地无 HTTP 签名服务时明确失败。
     * @param bucket 目标桶
     * @param key 对象键
     * @param ttlSeconds 有效秒数，范围 1 至 604800
     * @return GET 签名 URL
     */
    Mono<String> presignGet(String bucket, String key, int ttlSeconds);

    /**
     * 在成功、错误和取消时关闭下载流。消费函数负责隔离阻塞读取。
     * @param bucket 目标桶
     * @param key 对象键
     * @param consumer 流消费函数
     * @param <T> 消费结果类型
     * @return 消费结果
     */
    default <T> Mono<T> withDownload(String bucket, String key, Function<InputStream, Mono<T>> consumer) {
        return Mono.usingWhen(
                download(bucket, key),
                consumer,
                BlockingObjectStorage::closeStream,
                (stream, error) -> BlockingObjectStorage.closeStream(stream),
                BlockingObjectStorage::closeStream);
    }
}
