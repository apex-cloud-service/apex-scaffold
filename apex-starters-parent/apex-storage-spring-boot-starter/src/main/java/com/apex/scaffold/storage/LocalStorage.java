package com.apex.scaffold.storage;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import reactor.core.publisher.Mono;

/**
 * 本地文件策略，保留对象键并拒绝目录穿越及符号链接路径。
 * 上传流由调用方持有，下载流由消费方关闭；不提供虚假的 HTTP 签名。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
public final class LocalStorage extends BlockingObjectStorage {
    private final Path root;

    /** @param basePath 本地存储根目录 */
    public LocalStorage(String basePath) {
        if (basePath == null || basePath.isBlank()) {
            throw new IllegalArgumentException("本地存储根目录不能为空");
        }
        root = Path.of(basePath).toAbsolutePath().normalize();
    }

    @Override
    public String type() {
        return "local";
    }

    /**
     * 解析本地对象路径，拒绝目录穿越及路径上的符号链接。
     *
     * @param bucket 单段桶名
     * @param key 桶内对象键
     * @return 根目录内的规范化路径
     * @throws IllegalArgumentException 桶名、对象键或路径不安全
     */
    private Path resolve(String bucket, String key) {
        if (bucket == null
                || bucket.isBlank()
                || bucket.contains("/")
                || bucket.contains("\\")
                || bucket.equals(".")
                || bucket.equals("..")
                || key == null
                || key.isBlank()) {
            throw new IllegalArgumentException("非法存储桶或对象键");
        }
        Path directory = root.resolve(bucket).normalize();
        Path target = directory.resolve(key).normalize();
        if (!target.startsWith(directory) || target.equals(directory)) {
            throw new IllegalArgumentException("对象路径越界");
        }
        for (Path current = target; current != null && current.startsWith(root); current = current.getParent()) {
            if (Files.isSymbolicLink(current)) {
                throw new IllegalArgumentException("存储路径不允许符号链接");
            }
        }
        return target;
    }

    @Override
    public Mono<String> upload(String bucket, String key, InputStream source, String contentType) {
        return io(() -> {
            Path target = resolve(bucket, key);
            Files.createDirectories(target.getParent());
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            return key;
        });
    }

    @Override
    public Mono<InputStream> download(String bucket, String key) {
        return io(() -> {
                    try {
                        return Files.newInputStream(resolve(bucket, key));
                    } catch (NoSuchFileException missing) {
                        return null;
                    }
                })
                .doOnDiscard(InputStream.class, stream -> closeStream(stream).subscribe());
    }

    @Override
    public Mono<Boolean> delete(String bucket, String key) {
        return io(() -> Files.deleteIfExists(resolve(bucket, key)));
    }

    @Override
    public Mono<Boolean> exists(String bucket, String key) {
        return io(() -> {
            try {
                Files.readAttributes(
                        resolve(bucket, key),
                        java.nio.file.attribute.BasicFileAttributes.class,
                        LinkOption.NOFOLLOW_LINKS);
                return true;
            } catch (NoSuchFileException missing) {
                return false;
            }
        });
    }

    @Override
    public Mono<String> presignGet(String bucket, String key, int ttlSeconds) {
        return io(() -> {
            validateTtl(ttlSeconds);
            resolve(bucket, key);
            throw new UnsupportedOperationException("本地存储没有 HTTP 签名服务，请使用业务授权下载接口");
        });
    }
}
