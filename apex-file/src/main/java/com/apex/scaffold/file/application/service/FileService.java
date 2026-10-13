package com.apex.scaffold.file.application.service;

import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.file.application.dto.FileUploadResult;
import com.apex.scaffold.file.application.util.FileTypeValidator;
import com.apex.scaffold.file.domain.service.StorageService;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 文件应用服务类
 *
 * <p>提供文件上传、删除、URL 生成等文件操作功能
 *
 * <h2>核心功能</h2>
 * <ul>
 *   <li>文件上传：将文件流上传到对象存储（支持魔数校验）</li>
 *   <li>预签名 URL 生成：生成带时效性的文件访问地址</li>
 *   <li>文件删除：从存储中删除指定文件</li>
 *   <li>文件存在性检查：检查文件是否存在于存储中</li>
 * </ul>
 *
 * <h2>安全校验</h2>
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
public class FileService {

    private static final Set<String> ALLOWED_EXTENSIONS =
            Set.of("jpg", "jpeg", "png", "gif", "bmp", "webp", "heic", "heif");
    private static final Set<String> ALLOWED_CONTENT_TYPES =
            Set.of("image/jpeg", "image/png", "image/gif", "image/bmp", "image/webp", "image/heic", "image/heif");

    private final StorageService storageService;
    private final String defaultBucketName;
    private final long maxFileSize;
    private final int presignedUrlExpireSeconds;

    /**
     * 创建文件应用服务。
     *
     * @param storageService 由 {@code storage.default-type} 选出的默认存储门面
     * @param defaultBucketName 服务端固定存储桶名称
     * @param maxFileSize 最大允许上传字节数
     * @param presignedUrlExpireSeconds 预签名 URL 有效期，单位秒
     */
    public FileService(
            StorageService storageService,
            @Value("${storage.bucket-name:apex}") String defaultBucketName,
            @Value("${file.upload.max-size-bytes:10485760}") long maxFileSize,
            @Value("${storage.presigned-url-expire-seconds:300}") int presignedUrlExpireSeconds) {
        this.storageService = storageService;
        this.defaultBucketName = defaultBucketName;
        this.maxFileSize = maxFileSize;
        this.presignedUrlExpireSeconds = presignedUrlExpireSeconds;
    }

    /**
     * 上传文件并返回完整对象定位信息与临时访问地址。
     *
     * <p>物理存储桶完全由服务端配置决定，调用方不能覆盖。文件校验通过后
     * 写入对象存储，并生成带有效期的预签名访问 URL。</p>
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
        String sanitizedObjectName = sanitizeClientFileName(objectName);
        return upload(defaultBucketName, sanitizedObjectName, fileBytes, contentType)
                .flatMap(storedObjectName -> getUrl(defaultBucketName, storedObjectName)
                        .map(url -> new FileUploadResult(
                                defaultBucketName, storedObjectName, url, presignedUrlExpireSeconds, null)));
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
        byte[] fileBytes;
        try {
            fileBytes = inputStream.readAllBytes();
        } catch (IOException e) {
            return Mono.error(new BusinessException(ResultCode.ERROR, "读取文件数据失败"));
        }
        return upload(bucketName, objectName, fileBytes, contentType);
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
        return storageService.upload(bucketName, objectName, new java.io.ByteArrayInputStream(fileBytes), contentType);
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
     * 获取预签名访问地址
     *
     * @param bucketName 存储桶名称
     * @param objectName 对象名称
     * @return 预签名 URL
     */
    public Mono<String> getUrl(String bucketName, String objectName) {
        return storageService.getPresignedUrl(bucketName, objectName, presignedUrlExpireSeconds);
    }

    /**
     * 下载指定对象。
     *
     * @param bucketName 存储桶名称
     * @param objectName 对象名称
     * @return 文件输入流，调用方负责关闭
     */
    public Mono<InputStream> download(String bucketName, String objectName) {
        return storageService.download(bucketName, objectName);
    }

    /**
     * 删除文件
     *
     * @param bucketName 存储桶名称
     * @param objectName 对象名称
     * @return 是否删除成功
     */
    public Mono<Boolean> delete(String bucketName, String objectName) {
        return storageService.delete(bucketName, objectName);
    }

    /**
     * 检查文件是否存在
     *
     * @param bucketName 存储桶名称
     * @param objectName 对象名称
     * @return 是否存在
     */
    public Mono<Boolean> exists(String bucketName, String objectName) {
        return storageService.exists(bucketName, objectName);
    }
}
