package com.apex.scaffold.file.infrastructure.storage.service;

import com.apex.scaffold.file.domain.service.StorageService;
import com.apex.scaffold.storage.ObjectStorage;
import java.io.InputStream;
import java.util.UUID;
import reactor.core.publisher.Mono;

/**
 * 文件领域兼容门面。本地和 MinIO 沿用目录与扩展名保留的 UUID 键，其余策略保留原键。
 * 所有 SDK 调用委托独立 SPI，上传流仍由调用方关闭。
 *
 * @author Apex Team
 * @since 1.3.1
 */
@SuppressWarnings("PMD.ServiceOrDaoClassShouldEndWithImplRule")
public class ObjectStorageServiceAdapter implements StorageService {
    private static final String LOCAL_TYPE = "local";
    private static final String MINIO_TYPE = "minio";
    private final ObjectStorage storage;

    /** @param storage 独立对象存储策略 */
    public ObjectStorageServiceAdapter(ObjectStorage storage) {
        this.storage = storage;
    }

    /** 上传已校验类型的文件，本地与 MinIO 保留历史 UUID 键规则，输入流由调用方关闭。 */
    @Override
    public Mono<String> upload(String bucket, String key, InputStream source, String contentType) {
        return Mono.defer(() -> {
            if (!AbstractStorageService.ALLOWED_FILE_TYPES.contains(contentType)) {
                throw new IllegalArgumentException("不支持的文件类型: " + contentType);
            }
            String actualKey = key;
            if (LOCAL_TYPE.equals(storage.type()) || MINIO_TYPE.equals(storage.type())) {
                int slash = key.lastIndexOf('/');
                int dot = key.lastIndexOf('.');
                actualKey = key.substring(0, slash + 1)
                        + UUID.randomUUID().toString().replace("-", "")
                        + (dot > slash ? key.substring(dot) : "");
            }
            return storage.upload(bucket, actualKey, source, contentType);
        });
    }

    /** 下载对象，返回的输入流由业务消费方关闭。 */
    @Override
    public Mono<InputStream> download(String bucket, String key) {
        return storage.download(bucket, key);
    }

    /** 删除对象并保留底层策略的删除确认结果。 */
    @Override
    public Mono<Boolean> delete(String bucket, String key) {
        return storage.delete(bucket, key);
    }

    /** 查询对象是否存在，不修改存储状态。 */
    @Override
    public Mono<Boolean> exists(String bucket, String key) {
        return storage.exists(bucket, key);
    }

    /** 请求底层真实预签名，本地策略明确拒绝，业务层需使用授权下载地址。 */
    @Override
    public Mono<String> getPresignedUrl(String bucket, String key, Integer expiry) {
        return storage.presignGet(bucket, key, expiry == null ? 300 : expiry);
    }

    /** 返回历史图片路径前缀，仅用于兼容展示，不构成访问授权。 */
    @Override
    public String getPhotoUrlPrefix() {
        return switch (storage.type()) {
            case LOCAL_TYPE -> "/uploads/apex";
            case MINIO_TYPE -> "/minio/apex";
            case "volcengine" -> "/tos/apex-photo";
            default -> "/" + storage.type() + "/apex-photo";
        };
    }
}
