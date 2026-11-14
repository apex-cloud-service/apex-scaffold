package com.apex.scaffold.file.infrastructure.storage.service;

import com.apex.scaffold.storage.MinioStorage;
import io.minio.MinioClient;

/**
 * MinIO 旧类名兼容门面，由独立 SPI 执行全部同步操作；不参与组件扫描。
 *
 * @author Apex Team
 * @since 1.3.1
 */
public class MinioServiceImpl extends ObjectStorageServiceAdapter {
    /** @param client MinIO 客户端 */
    public MinioServiceImpl(MinioClient client) {
        super(new MinioStorage(client));
    }
}
