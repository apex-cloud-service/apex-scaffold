package com.apex.scaffold.infrastructure.storage.service;

import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.domain.service.StorageService;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * 存储服务抽象基类
 *
 * <p>
 * 提供文件存储的统一抽象，实现存储服务的通用逻辑，包括文件校验、存储桶管理等
 *
 * <h2>支持的存储类型</h2>
 * <ul>
 * <li>本地存储：基于本地文件系统的存储</li>
 * <li>MinIO存储：基于MinIO对象存储服务的存储</li>
 * <li>火山引擎TOS存储：基于火山引擎对象存储TOS的存储</li>
 * </ul>
 *
 * <h2>支持的文件类型</h2>
 * <ul>
 * <li>图片：JPEG、PNG、GIF、WebP</li>
 * <li>文档：PDF、Word、Excel、PowerPoint</li>
 * <li>文本：纯文本、HTML、CSS、JavaScript、JSON、XML</li>
 * </ul>
 *
 * <h2>核心功能</h2>
 * <ul>
 * <li>文件上传：支持单文件上传并统一校验文件类型</li>
 * <li>文件下载：支持文件下载</li>
 * <li>文件删除：支持文件删除</li>
 * <li>文件存在性检查：检查文件是否存在</li>
 * <li>存储桶管理：校验存储桶存在性并支持显式创建</li>
 * <li>预签名URL生成：生成临时访问URL</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <p>
 * 采用响应式编程模式，所有操作返回Mono类型，支持异步非阻塞操作。 子类只需实现抽象方法即可完成特定存储服务的集成
 * </p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see StorageService
 */
@Slf4j
public abstract class AbstractStorageService implements StorageService {

    protected static final Set<String> ALLOWED_FILE_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/gif",
            "image/webp",
            "image/bmp",
            "image/heic",
            "image/heif",
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "text/plain",
            "text/html",
            "text/css",
            "text/javascript",
            "application/json",
            "application/xml");

    /**
     * 获取存储类型
     *
     * <p>返回当前存储服务的类型标识
     *
     * @return 存储类型名称
     */
    protected abstract String getStorageType();

    /**
     * 确保存储桶存在
     *
     * <p>如果存储桶不存在则创建，存在则直接返回
     *
     * @return 异步操作结果
     */
    /**
     * TODO: Add method description
     *
     * @param bucketName TODO
     * @return TODO
     */
    protected abstract Mono<Void> ensureBucketExistsInternal(String bucketName);

    /**
     * 创建存储桶
     *
     * @return 异步操作结果
     */
    /**
     * TODO: Add method description
     *
     * @param bucketName TODO
     * @return TODO
     */
    protected abstract Mono<Void> createBucketInternal(String bucketName);

    /**
     * 上传文件到对象存储。
     *
     * <p>校验文件类型后，确保存储桶存在，然后委托子类执行实际上传。文件大小由接口层和应用层按统一配置校验。</p>
     *
     * @param bucketName  存储桶名称
     * @param objectName  对象（文件）名称
     * @param inputStream 文件输入流
     * @param contentType 文件MIME类型
     * @return 上传成功返回唯一文件名
     */
    @Override
    public Mono<String> upload(String bucketName, String objectName, InputStream inputStream, String contentType) {
        return uploadFile(bucketName, objectName, inputStream, contentType);
    }

    /**
     * 上传文件（带校验）。
     *
     * <p>校验文件类型后，确保存储桶存在，然后委托子类执行实际上传。
     * 所有操作在弹性调度器上执行。</p>
     *
     * @param bucketName  存储桶名称
     * @param fileName    文件名
     * @param inputStream 文件输入流
     * @param contentType 文件MIME类型
     * @return 上传后的唯一文件名
     * @throws IllegalArgumentException 文件类型不支持时抛出
     */
    protected Mono<String> uploadFile(String bucketName, String fileName, InputStream inputStream, String contentType) {
        return Mono.fromCallable(() -> {
                    validateFileType(contentType);
                    return Boolean.TRUE;
                })
                .flatMap(ignored -> ensureBucketExistsInternal(bucketName)
                        .then(Mono.fromCallable(() -> doUpload(bucketName, fileName, inputStream, contentType))))
                .subscribeOn(BlockingIoSchedulers.storage());
    }

    /**
     * 上传字节数组文件。
     *
     * <p>将字节数组包装为输入流后委托 {@link #uploadFile(String, String, InputStream, String)} 执行上传。
     *
     * @param bucketName  存储桶名称
     * @param fileName    文件名
     * @param bytes       文件字节数组
     * @param contentType 文件MIME类型
     * @return 上传后的唯一文件名
     */
    public Mono<String> uploadFile(String bucketName, String fileName, byte[] bytes, String contentType) {
        return uploadFile(bucketName, fileName, new ByteArrayInputStream(bytes), contentType);
    }

    /**
     * 删除指定文件。
     *
     * <p>调用子类的 {@link #doDelete} 执行实际删除操作，在弹性调度器上执行。
     *
     * @param objectName 对象（文件）名称
     * @return 删除成功返回 true，失败返回 false
     */
    @Override
    public Mono<Boolean> delete(String bucketName, String objectName) {
        return deleteFile(bucketName, objectName);
    }

    /**
     * 删除指定文件。
     *
     * <p>调用子类的 {@link #doDelete} 执行实际删除，操作在弹性调度器上执行。
     * 删除失败时返回 false 而非抛出异常。</p>
     *
     * @param fileName   文件名
     * @return 删除成功返回 true，失败返回 false
     */
    protected Mono<Boolean> deleteFile(String bucketName, String fileName) {
        return Mono.fromCallable(() -> doDelete(bucketName, fileName))
                .subscribeOn(BlockingIoSchedulers.storage())
                .onErrorReturn(false);
    }

    /**
     * 判断文件是否存在。
     *
     * <p>检查指定存储桶中文件是否存在。</p>
     *
     * @param objectName 对象名称
     * @return 文件存在返回 true，否则返回 false
     */
    @Override
    public Mono<Boolean> exists(String bucketName, String objectName) {
        return checkFileExists(bucketName, objectName);
    }

    /**
     * 检查文件是否存在。
     *
     * <p>调用子类的 {@link #doExists} 检查指定存储桶中的文件是否存在。</p>
     *
     * @param fileName   文件名
     * @return 文件存在返回 true，否则返回 false
     */
    protected Mono<Boolean> checkFileExists(String bucketName, String fileName) {
        return Mono.fromCallable(() -> doExists(bucketName, fileName)).subscribeOn(BlockingIoSchedulers.storage());
    }

    /**
     * 从存储中下载文件并以字节数组返回。
     *
     * <p>调用子类的 {@link #download(String, String)} 获取文件输入流，在弹性调度器上读取全部字节，
     * 读取完成后关闭输入流；关闭异常仅记录日志，不影响调用结果。</p>
     *
     * @param bucketName 存储桶名称
     * @param fileName   文件名
     * @return 文件内容的字节数组
     */
    protected Mono<byte[]> downloadFileAsBytes(String bucketName, String fileName) {
        return download(bucketName, fileName).flatMap(stream -> Mono.fromCallable(() -> stream.readAllBytes())
                .subscribeOn(BlockingIoSchedulers.storage())
                .doFinally(s -> {
                    try {
                        stream.close();
                    } catch (Exception e) {
                        log.warn("关闭输入流异常", e);
                    }
                }));
    }

    /**
     * 创建存储桶。
     *
     * <p>调用子类的 {@link #createBucketInternal(String)} 在弹性调度器上执行存储桶创建。
     *
     * @return 空 Mono，表示创建完成
     */
    public Mono<Void> createBucket(String bucketName) {
        return createBucketInternal(bucketName).subscribeOn(BlockingIoSchedulers.storage());
    }

    /**
     * 校验文件MIME类型是否在允许列表中。
     *
     * <p>不允许的文件类型会抛出 {@link IllegalArgumentException}。</p>
     *
     * @param contentType 文件MIME类型
     * @throws IllegalArgumentException 文件类型不支持时抛出
     */
    protected void validateFileType(String contentType) {
        if (!ALLOWED_FILE_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("不支持的文件类型: " + contentType);
        }
    }

    /**
     * 生成唯一文件名。
     *
     * <p>使用 UUID 生成全局唯一标识，并保留原始文件的扩展名。
     * 例如 "photo.jpg" → "a1b2c3d4e5f6789012345678901234ab.jpg"。</p>
     *
     * @param originalFileName 原始文件名
     * @return 去连字符的UUID + 原始扩展名组成的唯一文件名
     */
    protected String generateUniqueFileName(String originalFileName) {
        String normalizedFileName = originalFileName.replace('\\', '/');
        int lastSlashIndex = normalizedFileName.lastIndexOf('/');
        String directoryPrefix = lastSlashIndex >= 0 ? normalizedFileName.substring(0, lastSlashIndex + 1) : "";
        String fileName = lastSlashIndex >= 0 ? normalizedFileName.substring(lastSlashIndex + 1) : normalizedFileName;
        String extension = "";
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex > 0) {
            extension = fileName.substring(lastDotIndex);
        }
        return directoryPrefix + UUID.randomUUID().toString().replace("-", "") + extension;
    }

    /**
     * 执行文件上传
     *
     * @param bucketName   存储桶名称
     * @param fileName     文件名
     * @param inputStream  文件输入流
     * @param contentType  文件类型
     * @return 上传后的文件访问路径
     */
    protected abstract String doUpload(String bucketName, String fileName, InputStream inputStream, String contentType);

    /**
     * 执行文件删除
     *
     * @param fileName   文件名
     * @return 删除结果，true 表示删除成功，false 表示删除失败
     */
    /**
     * TODO: Add method description
     *
     * @param bucketName TODO
     * @param fileName TODO
     * @return TODO
     */
    protected abstract boolean doDelete(String bucketName, String fileName);

    /**
     * 检查文件是否存在
     *
     * @param fileName   文件名
     * @return true 表示文件存在，false 表示文件不存在
     */
    /**
     * TODO: Add method description
     *
     * @param bucketName TODO
     * @param fileName TODO
     * @return TODO
     */
    protected abstract boolean doExists(String bucketName, String fileName);

    /**
     * 构建预签名 URL 路径。
     *
     * <p>构建文件的访问路径，格式为 /storage/{bucketName}/{fileName}。</p>
     *
     * @param fileName   文件名
     * @param expiry     过期时间（秒）
     * @return 文件访问路径
     */
    protected String buildPresignedUrl(String bucketName, String fileName, int expiry) {
        return "/storage/" + bucketName + "/" + fileName;
    }

    /**
     * 获取照片公开访问 URL 前缀（策略模式核心方法）。
     *
     * <p>默认实现返回 {@code /storage/{bucketName}}，子类应覆盖以提供
     * 存储类型特定的 URL 格式。应用层通过此方法获得正确的 URL 前缀，
     * 无需感知具体存储类型。</p>
     *
     * @return 照片访问 URL 前缀
     */
    @Override
    public String getPhotoUrlPrefix() {
        return "/storage/" + getBucketName();
    }

    /**
     * 获取当前有效的存储桶名称。
     *
     * <p>子类可覆盖此方法提供自定义桶名，默认返回空字符串。
     * 主要用于 {@link #getPhotoUrlPrefix()} 构造 URL 前缀。</p>
     *
     * @return 存储桶名称
     */
    protected String getBucketName() {
        return "";
    }
}
