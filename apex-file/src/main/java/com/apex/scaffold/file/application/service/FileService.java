package com.apex.scaffold.file.application.service;

import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.file.application.dto.FileUploadResult;
import com.apex.scaffold.file.application.util.FileTypeValidator;
import com.apex.scaffold.file.domain.enums.StorageType;
import com.apex.scaffold.file.domain.service.StorageService;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 文件应用服务类
 *
 * <p>提供文件上传、删除、URL 生成等文件操作功能
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>文件上传：将文件流上传到对象存储（支持魔数校验）</li>
 *   <li>预签名 URL 生成：生成带时效性的文件访问地址</li>
 *   <li>文件删除：从存储中删除指定文件</li>
 *   <li>文件存在性检查：检查文件是否存在于存储中</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <p>
 * 文件上传时执行三重校验：
 * <ol>
 *   <li>扩展名校验：检查文件扩展名是否在白名单中</li>
 *   <li>Content-Type 校验：检查声明的内容类型是否匹配</li>
 *   <li>魔数校验：读取文件头字节验证真实文件类型（防止扩展名伪造）</li>
 * </ol>
 * </p>
 *
 * <h2>设计说明</h2>
 * <p>
 * 本服务是应用层与领域存储服务之间的适配器，
 * 封装 StorageService 的具体实现，提供更友好的接口。
 * </p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see StorageService
 * @see FileTypeValidator
 */
@Service
@Slf4j
public class FileService {

    private static final String STORED = "STORED";
    private static final String ORPHANED = "ORPHANED";

    private static final Set<String> ALLOWED_EXTENSIONS =
            Set.of("jpg", "jpeg", "png", "gif", "bmp", "webp", "heic", "heif");
    private static final Set<String> ALLOWED_CONTENT_TYPES =
            Set.of("image/jpeg", "image/png", "image/gif", "image/bmp", "image/webp", "image/heic", "image/heif");

    private final StorageService storageService;
    private final com.apex.scaffold.file.domain.repository.FileRepository fileRepository;
    private final com.apex.scaffold.file.domain.service.StorageServiceProvider provider;
    private final String storageType;

    /**
     * 创建具备归属持久化与历史提供商路由的文件应用服务。
     * @param storageService 默认存储门面
     * @param repository 文件仓储
     * @param provider 按真实类型选择的存储注册表
     * @param storageType 默认提供商类型
     * @param bucket 服务端存储桶
     * @param maxSize 上传大小限制
     * @param expiry 签名有效秒数
     */
    public FileService(
            StorageService storageService,
            com.apex.scaffold.file.domain.repository.FileRepository repository,
            com.apex.scaffold.file.domain.service.StorageServiceProvider provider,
            @Value("${storage.default-type:local}") String storageType,
            @Value("${storage.bucket-name:apex}") String bucket,
            @Value("${file.upload.max-size-bytes:10485760}") long maxSize,
            @Value("${storage.presigned-url-expire-seconds:300}") int expiry,
            com.apex.scaffold.file.domain.service.StorageQuotaPort storageQuotaPort) {
        this.storageService = storageService;
        this.fileRepository = repository;
        this.provider = provider;
        this.storageType = storageType;
        this.defaultBucketName = bucket;
        this.maxFileSize = maxSize;
        this.presignedUrlExpireSeconds = expiry;
        this.storageQuotaPort = storageQuotaPort;
    }

    private final String defaultBucketName;
    private final long maxFileSize;
    private final int presignedUrlExpireSeconds;

    /** 存储配额断言端口（方案 B5；未配置基址时为空实现语义）。 */
    private final com.apex.scaffold.file.domain.service.StorageQuotaPort storageQuotaPort;

    /**
     * 上传文件并返回完整对象定位信息与临时访问地址。
     *
     * <p>物理存储桶完全由服务端配置决定，调用方不能覆盖。文件校验通过后
     * 写入对象存储；云存储返回带有效期的预签名 URL，Local 返回每次访问仍需认证授权的内容地址，
     * {@code expiresIn=0} 表示不适用签名 TTL。</p>
     *
     * <p>{@code clientSex} 与 {@code openId} 参数为兼容既有调用方而保留，
     * 通用上传流程不再使用它们。</p>
     *
     * @param objectName 客户端原始文件名
     * @param fileBytes 文件内容
     * @param contentType 文件 MIME 类型
     * @param clientSex 兼容保留参数，不再使用
     * @param openId 兼容保留参数，不再使用
     * @return 包含 bucket、object、URL 与有效期的上传结果
     */
    public Mono<FileUploadResult> upload(
            String objectName, byte[] fileBytes, String contentType, Integer clientSex, String openId) {
        if (fileBytes == null || fileBytes.length == 0) {
            return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "文件不能为空"));
        }
        if (fileBytes.length > maxFileSize) {
            return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "文件大小超过限制"));
        }
        // 方案 B5：存储配额前置校验（软上限，配额设施不可用放行；租户取可信上下文）
        String quotaTenant =
                com.apex.scaffold.common.context.TenantContext.currentTenantId().orElse(null);
        Mono<Void> quotaCheck =
                quotaTenant == null ? Mono.empty() : storageQuotaPort.assertStorageQuota(quotaTenant, fileBytes.length);
        String sanitizedObjectName = sanitizeClientFileName(objectName);
        return quotaCheck
                .then(Mono.defer(() -> upload(defaultBucketName, sanitizedObjectName, fileBytes, contentType)))
                .flatMap(storedObjectName -> getUrl(defaultBucketName, storedObjectName)
                        .map(url -> new FileUploadResult(
                                defaultBucketName,
                                storedObjectName,
                                url,
                                StorageType.LOCAL.name().equalsIgnoreCase(storageType) ? 0 : presignedUrlExpireSeconds,
                                null)));
    }

    /**
     * 上传文件到对象存储
     *
     * <p>将文件流上传到指定的存储桶中，上传前会执行以下校验：
     * <ol>
     *   <li>扩展名校验：检查文件扩展名是否在白名单中</li>
     *   <li>Content-Type 校验：检查声明的内容类型是否匹配</li>
     *   <li>魔数校验：读取文件头字节验证真实文件类型（防止扩展名伪造）</li>
     * </ol>
     * 仅支持常见图片格式（jpg、jpeg、png、gif、bmp、webp、heic、heif）。
     *
     * @param bucketName 存储桶名称
     * @param objectName 对象名称（包含扩展名，用于类型校验）
     * @param inputStream 文件输入流
     * @param contentType 文件内容类型（如 image/jpeg）
     * @return 上传成功后的文件访问路径
     * @throws BusinessException 当文件类型不合法时抛出
     */
    public Mono<String> upload(String bucketName, String objectName, InputStream inputStream, String contentType) {
        return Mono.fromCallable(() -> {
                    try (inputStream) {
                        return inputStream.readAllBytes();
                    } catch (IOException e) {
                        throw new BusinessException(ResultCode.ERROR, "读取文件数据失败");
                    }
                })
                .subscribeOn(com.apex.scaffold.common.scheduler.BlockingIoSchedulers.storage())
                .flatMap(bytes -> upload(bucketName, objectName, bytes, contentType));
    }

    /**
     * 校验文件并写入指定存储桶。
     *
     * @param bucketName 存储桶名称
     * @param objectName 原始对象名称
     * @param fileBytes 文件内容
     * @param contentType 文件 MIME 类型
     * @return 存储后生成的唯一对象名称
     */
    private Mono<String> upload(String bucketName, String objectName, byte[] fileBytes, String contentType) {
        validateFileType(objectName, contentType);
        if (fileBytes.length > maxFileSize) {
            return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "文件大小超过限制"));
        }
        FileTypeValidator.validateImage(new java.io.ByteArrayInputStream(fileBytes), objectName);
        return FileAccessContext.current().flatMap(identity -> {
            String uniqueName = java.util.UUID.randomUUID() + "-" + sanitizeClientFileName(objectName);
            return Mono.defer(() -> storageService.upload(
                            bucketName, uniqueName, new java.io.ByteArrayInputStream(fileBytes), contentType))
                    .subscribeOn(com.apex.scaffold.common.scheduler.BlockingIoSchedulers.storage())
                    .flatMap(key -> {
                        com.apex.scaffold.file.domain.model.entity.FileEntity file =
                                new com.apex.scaffold.file.domain.model.entity.FileEntity();
                        file.setTenantId(identity.tenantId());
                        file.setUserId(identity.userId());
                        file.setStorageType(storageType);
                        file.setBucketName(bucketName);
                        file.setFileName(key);
                        file.setFilePath(key);
                        file.setOriginalName(sanitizeClientFileName(objectName));
                        file.setFileSize((long) fileBytes.length);
                        file.setContentType(contentType);
                        file.setStorageStatus("STORED");
                        file.setBusinessType("GENERIC");
                        return Mono.defer(() -> fileRepository.save(file))
                                .switchIfEmpty(Mono.error(new IllegalStateException("文件记录未保存")))
                                .onErrorResume(error -> compensate(storageService, fileRepository, file, key, error))
                                .thenReturn(key);
                    });
        });
    }

    /**
     * 校验文件类型合法性。
     *
     * <p>检查文件名是否为空、扩展名是否在白名单中、Content-Type 是否匹配。
     *
     * @param objectName 对象名称（包含扩展名，用于类型校验）
     * @param contentType 文件内容类型（如 image/jpeg）
     * @throws BusinessException 当文件类型不在白名单范围内时抛出
     */
    private void validateFileType(String objectName, String contentType) {
        if (objectName == null || objectName.isBlank()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "文件名不能为空");
        }
        String extension = extractExtension(objectName);
        if (extension == null || !ALLOWED_EXTENSIONS.contains(extension.toLowerCase())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "不支持的文件类型：" + extension);
        }
        if (contentType != null
                && !contentType.isBlank()
                && !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "不支持的内容类型：" + contentType);
        }
    }

    /**
     * 从文件名中提取扩展名。
     *
     * <p>查找文件名中最后一个 '.' 之后的部分作为扩展名，
     * 若无扩展名或 '.' 在末尾则返回 null。</p>
     *
     * @param fileName 文件名
     * @return 文件扩展名（不含点号），若无法解析则返回 null
     */
    private String extractExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            return null;
        }
        return fileName.substring(dotIndex + 1);
    }

    /**
     * 清洗客户端上传文件名，只保留最终文件名部分。
     *
     * <p>客户端可能传入带路径前缀的文件名，例如 {@code C:\tmp\photo.jpg} 或
     * {@code nested/photo.jpg}。上传接口只接受文件名本身，不能允许客户端借由文件名
     * 额外控制对象目录层级，因此这里统一收敛为 basename。</p>
     *
     * @param objectName 客户端原始文件名
     * @return 清洗后的纯文件名
     */
    private String sanitizeClientFileName(String objectName) {
        if (objectName == null || objectName.isBlank()) {
            return objectName;
        }
        String normalized = objectName.replace('\\', '/');
        int slashIndex = normalized.lastIndexOf('/');
        return slashIndex >= 0 ? normalized.substring(slashIndex + 1) : normalized;
    }

    /**
     * 获取访问地址；本地文件使用仍需携带认证凭证的业务下载接口，云文件使用预签名。
     *
     * @param bucketName 存储桶名称
     * @param objectName 对象名称
     * @return 业务下载地址或预签名 URL
     */
    public Mono<String> getUrl(String bucketName, String objectName) {
        return authorized(bucketName, objectName).flatMap(file -> {
            return accessUrl(file, provider.getStorageService(file.getStorageType()), presignedUrlExpireSeconds);
        });
    }

    /**
     * 下载指定对象。
     *
     * @param bucketName 存储桶名称
     * @param objectName 对象名称
     * @return 文件输入流，调用方负责关闭
     */
    public Mono<InputStream> download(String bucketName, String objectName) {
        return authorized(bucketName, objectName).flatMap(file -> provider.getStorageService(file.getStorageType())
                .download(file.getBucketName(), file.getFileName()));
    }

    /**
     * 删除文件
     *
     * @param bucketName 存储桶名称
     * @param objectName 对象名称
     * @return 是否删除成功
     */
    public Mono<Boolean> delete(String bucketName, String objectName) {
        return authorized(bucketName, objectName).flatMap(file -> provider.getStorageService(file.getStorageType())
                .delete(file.getBucketName(), file.getFileName())
                .flatMap(deleted ->
                        deleted ? fileRepository.deleteById(file.getId()).thenReturn(true) : Mono.just(false)));
    }

    /**
     * 检查文件是否存在
     *
     * @param bucketName 存储桶名称
     * @param objectName 对象名称
     * @return 是否存在
     */
    public Mono<Boolean> exists(String bucketName, String objectName) {
        return authorized(bucketName, objectName).flatMap(file -> provider.getStorageService(file.getStorageType())
                .exists(file.getBucketName(), file.getFileName()));
    }

    /**
     * 在访问对象存储前完成身份、记录归属及已存储状态检查。
     * @param bucket 存储桶
     * @param key 实际对象键
     * @return 授权后的文件记录
     */
    private Mono<com.apex.scaffold.file.domain.model.entity.FileEntity> authorized(String bucket, String key) {
        return FileAccessContext.current().flatMap(identity -> fileRepository
                .findByStorageLocation(bucket, key)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "文件记录不存在")))
                .map(file -> {
                    identity.authorize(file);
                    if (!STORED.equals(file.getStorageStatus())) {
                        throw new BusinessException(ResultCode.FORBIDDEN, "文件尚不可访问");
                    }
                    return file;
                }));
    }

    /** @param fileId 清理记录编号 @return 所有者授权后确认删除对象并移除清理记录 */
    public Mono<Boolean> retryOrphanCleanup(Long fileId) {
        return FileAccessContext.current().flatMap(identity -> fileRepository
                .findById(fileId)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "清理记录不存在")))
                .flatMap(file -> {
                    identity.authorize(file);
                    if (!ORPHANED.equals(file.getStorageStatus())) {
                        return Mono.error(new BusinessException(ResultCode.FORBIDDEN, "记录不是孤儿清理依据"));
                    }
                    return deleteConfirmed(
                                    provider.getStorageService(file.getStorageType()),
                                    file.getBucketName(),
                                    file.getFileName())
                            .flatMap(deleted ->
                                    deleted ? fileRepository.deleteById(fileId).thenReturn(true) : Mono.just(false));
                }));
    }

    /** @param file 已授权的存储记录 @param storage 实际提供商 @param expiry 签名有效秒数 @return 受控下载地址或云签名 */
    static Mono<String> accessUrl(
            com.apex.scaffold.file.domain.model.entity.FileEntity file, StorageService storage, int expiry) {
        if (StorageType.LOCAL.name().equalsIgnoreCase(file.getStorageType())) {
            return Mono.just(org.springframework.web.util.UriComponentsBuilder.fromPath("/api/v1/file/content")
                    .queryParam("bucketName", file.getBucketName())
                    .queryParam("fileName", file.getFileName())
                    .build()
                    .encode()
                    .toUriString());
        }
        return storage.getPresignedUrl(file.getBucketName(), file.getFileName(), expiry);
    }

    /** @param storage 实际提供商 @param bucket 存储桶 @param key 实际对象键 @return 删除或确认不存在才返回成功 */
    static Mono<Boolean> deleteConfirmed(StorageService storage, String bucket, String key) {
        return Mono.defer(() -> storage.delete(bucket, key))
                .flatMap(deleted ->
                        deleted ? Mono.just(true) : storage.exists(bucket, key).map(exists -> !exists))
                .defaultIfEmpty(false);
    }

    /**
     * 数据库保存失败时删除已写入对象；补偿失败独立插入清理依据，不提供跨系统原子性。
     * @param storage 实际存储门面
     * @param repository 文件仓储
     * @param source 可信归属及提供商定位来源
     * @param key 已写入对象键
     * @param original 原始持久化异常
     * @param <T> 失败链的结果类型
     * @return 始终传播原始失败的响应式链
     */
    static <T> Mono<T> compensate(
            StorageService storage,
            com.apex.scaffold.file.domain.repository.FileRepository repository,
            com.apex.scaffold.file.domain.model.entity.FileEntity source,
            String key,
            Throwable original) {
        String bucket = source.getBucketName();
        return Mono.defer(() -> storage.delete(bucket, key))
                .defaultIfEmpty(false)
                .flatMap(deleted -> deleted
                        ? Mono.<Boolean>just(true)
                        : Mono.error(new IllegalStateException("对象补偿未删除: " + bucket + "/" + key)))
                .onErrorResume(error -> {
                    original.addSuppressed(error);
                    com.apex.scaffold.file.domain.model.entity.FileEntity orphan =
                            new com.apex.scaffold.file.domain.model.entity.FileEntity();
                    orphan.setTenantId(source.getTenantId());
                    orphan.setUserId(source.getUserId());
                    orphan.setStorageType(source.getStorageType());
                    orphan.setBucketName(bucket);
                    orphan.setFileName(key);
                    orphan.setFilePath(key);
                    orphan.setBusinessType("CLEANUP");
                    orphan.setStorageStatus("ORPHANED");
                    // 新主键插入独立记录，避免再次更新已失败的业务记录或覆盖合并会话。
                    return Mono.defer(() -> repository.save(orphan))
                            .switchIfEmpty(Mono.error(new IllegalStateException("清理依据未保存")))
                            .thenReturn(false)
                            .onErrorResume(persistError -> {
                                if (persistError != original) {
                                    original.addSuppressed(persistError);
                                }
                                log.error(
                                        "数据库不可用，清理依据无法持久化，必须人工恢复: type={}, bucket={}, object={}",
                                        source.getStorageType(),
                                        bucket,
                                        key,
                                        persistError);
                                return Mono.just(false);
                            });
                })
                .then(Mono.error(original));
    }
}
