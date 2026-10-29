package com.apex.scaffold.file.infrastructure.storage.service;

import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.storage.LocalStorage;
import java.nio.file.Files;
import java.nio.file.Path;
import reactor.core.publisher.Mono;

/**
 * 本地存储旧类名兼容门面，文件访问委托独立 SPI。
 * publicBaseUrl 保留构造签名，不将普通访问路径伪装成预签名。
 *
 * @author Apex Team
 * @since 1.3.1
 */
public class LocalStorageServiceImpl extends ObjectStorageServiceAdapter {
    private final Path root;
    /** @param basePath 本地根目录 @param publicBaseUrl 历史访问前缀 */
    public LocalStorageServiceImpl(String basePath, String publicBaseUrl) {
        super(new LocalStorage(basePath));
        root = Path.of(basePath).toAbsolutePath().normalize();
    }

    /** @param bucket 本地桶名 @return 创建目录完成信号 */
    public Mono<Void> createBucket(String bucket) {
        return Mono.fromCallable(() -> {
                    if (bucket == null
                            || bucket.isBlank()
                            || bucket.contains("/")
                            || bucket.contains("\\")
                            || bucket.equals(".")
                            || bucket.equals("..")) {
                        throw new IllegalArgumentException("非法本地桶名称");
                    }
                    Path directory = root.resolve(bucket);
                    if (Files.isSymbolicLink(directory) || Files.isSymbolicLink(root)) {
                        throw new IllegalArgumentException("存储路径不允许符号链接");
                    }
                    Files.createDirectories(directory);
                    return true;
                })
                .subscribeOn(BlockingIoSchedulers.storage())
                .then();
    }
}
