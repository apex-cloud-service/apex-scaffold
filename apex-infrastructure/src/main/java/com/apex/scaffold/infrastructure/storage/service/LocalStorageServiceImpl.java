package com.apex.scaffold.infrastructure.storage.service;

import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriUtils;
import reactor.core.publisher.Mono;

/**
 * 本地存储服务实现类
 *
 * <p>基于本地文件系统实现的文件存储服务，适用于单机部署场景
 *
 * <h2>存储路径</h2>
 * <p>默认存储路径：/root/data/apex/uploads/{bucketName}/{fileName}。可通过
 * {@code storage.local.base-path} 或环境变量 {@code STORAGE_LOCAL_BASE_PATH}
 * 指定项目外的绝对路径，也可指向已经挂载到本机的 NFS/SMB 目录。</p>
 *
 * <h2>核心功能</h2>
 * <ul>
 *   <li>文件上传：上传文件到本地指定目录</li>
 *   <li>文件下载：从本地指定目录下载文件</li>
 *   <li>文件删除：删除本地指定文件</li>
 *   <li>文件存在性检查：检查本地文件是否存在</li>
 *   <li>存储桶管理：自动创建和管理本地目录</li>
 *   <li>预签名URL生成：生成本地访问路径URL</li>
 * </ul>
 *
 * <h2>安全防护</h2>
 * <p>在 {@link #getFilePath(String, String)} 方法中通过路径规范化和前缀校验
 * 防止路径遍历攻击（如 ../../etc/passwd）。</p>
 *
 * <h2>使用示例</h2>
 * <pre>
 * // 配置文件添加
 * storage.default-type=local
 * storage.local.base-path=/data/apex/uploads
 *
 * // 使用示例
 * Mono&lt;String&gt; uploadResult = storageService.upload("avatars", "user.jpg", inputStream, "image/jpeg");
 * Mono&lt;InputStream&gt; downloadResult = storageService.download("avatars", "user.jpg");
 * </pre>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see AbstractStorageService
 */
@Service("localStorageService")
@ConditionalOnProperty(name = "storage.local.enabled", havingValue = "true", matchIfMissing = true)
public class LocalStorageServiceImpl extends AbstractStorageService {
    private static final Logger log = LoggerFactory.getLogger(LocalStorageServiceImpl.class);

    /** 本地存储根路径，所有上传文件均限制在该目录内。 */
    private final Path storageBasePath;

    /** 本地存储的对外访问基础URL */
    private final String publicBaseUrl;

    /**
     * 创建本地存储服务。
     *
     * <p>相对路径以应用进程工作目录为基准，绝对路径可指向项目目录外或服务器挂载目录。
     * 构造时统一转换为规范化绝对路径，确保后续路径边界校验稳定。</p>
     *
     * @param storageBasePath 本地存储根目录
     */
    public LocalStorageServiceImpl(
            @Value("${storage.local.base-path:/root/data/apex/uploads}") String storageBasePath,
            @Value("${storage.local.public-base-url:}") String publicBaseUrl) {
        if (storageBasePath == null || storageBasePath.isBlank()) {
            throw new IllegalArgumentException("本地存储根目录不能为空");
        }
        this.storageBasePath = Paths.get(storageBasePath).toAbsolutePath().normalize();
        this.publicBaseUrl = publicBaseUrl != null ? publicBaseUrl.replaceAll("/$", "") : "";
        log.info(
                "本地存储根目录: {}, 对外基础URL: {}",
                this.storageBasePath,
                this.publicBaseUrl.isEmpty() ? "未配置(使用相对路径)" : this.publicBaseUrl);
    }

    /**
     * 获取存储类型标识。
     *
     * @return 存储类型 "LOCAL"
     */
    @Override
    /**
     * 获取StorageType
     *
     * @return 结果
     */
    protected String getStorageType() {
        return "LOCAL";
    }

    /**
     * 确保存储桶存在
     *
     * <p>检查本地存储桶目录是否存在，如果不存在则自动创建。
     * 目录创建失败时将抛出 {@link BusinessException}。
     *
     * @param bucketName 存储桶名称
     * @return 完成信号
     */
    @Override
    /**
     * 是否存在BucketExistsInternal
     *
     * @param bucketName 参数
     *
     * @return 结果
     */
    protected Mono<Void> ensureBucketExistsInternal(String bucketName) {
        return Mono.fromRunnable(() -> {
                    try {
                        Path bucketPath = getBucketPath(bucketName);
                        if (!Files.exists(bucketPath)) {
                            Files.createDirectories(bucketPath);
                            log.info("自动创建存储桶: {}", bucketName);
                        }
                    } catch (IOException e) {
                        log.error("检查/创建存储桶失败，bucket: {}", bucketName, e);
                        throw new BusinessException(ResultCode.STORAGE_ERROR, "检查/创建存储桶失败");
                    }
                })
                .then();
    }

    /**
     * 创建存储桶
     *
     * <p>创建指定名称的本地存储桶目录。如果目录已存在则忽略。
     *
     * @param bucketName 存储桶名称
     * @return 完成信号
     */
    @Override
    /**
     * 创建BucketInternal
     *
     * @param bucketName 参数
     *
     * @return 结果
     */
    protected Mono<Void> createBucketInternal(String bucketName) {
        return Mono.fromRunnable(() -> {
                    try {
                        Path bucketPath = getBucketPath(bucketName);
                        if (!Files.exists(bucketPath)) {
                            Files.createDirectories(bucketPath);
                            log.info("创建存储桶成功: {}", bucketName);
                        }
                    } catch (IOException e) {
                        log.error("创建存储桶失败，bucket: {}", bucketName, e);
                        throw new BusinessException(ResultCode.STORAGE_ERROR, "创建存储桶失败");
                    }
                })
                .then();
    }

    /**
     * 下载文件
     *
     * <p>从本地存储中下载指定文件，返回文件输入流。
     * 如果文件不存在则抛出异常。
     *
     * @param bucketName 存储桶名称
     * @param objectName 文件名
     * @return 文件输入流
     */
    @Override
    /**
     * 加载load
     *
     * @param bucketName 参数
     *
     * @param objectName 参数
     *
     * @return 结果
     */
    public Mono<InputStream> download(String bucketName, String objectName) {
        return Mono.fromCallable(() -> {
                    Path filePath = getFilePath(bucketName, objectName);
                    if (!Files.exists(filePath)) {
                        throw new IOException("文件不存在: " + filePath);
                    }
                    return (InputStream) new FileInputStream(filePath.toFile());
                })
                .doOnError(e -> log.error("文件下载失败", e));
    }

    /**
     * 获取预签名 URL
     *
     * <p>生成本地存储文件的访问路径 URL，格式为 /local-storage/{bucketName}/{objectName}。
     *
     * @param bucketName 存储桶名称
     * @param objectName 文件名
     * @param expiry     过期时间（秒），本地存储忽略该参数
     * @return 文件访问 URL
     */
    @Override
    /**
     * 获取PresignedUrl
     *
     * @param bucketName 参数
     *
     * @param objectName 参数
     *
     * @param expiry 参数
     *
     * @return 结果
     */
    public Mono<String> getPresignedUrl(String bucketName, String objectName, Integer expiry) {
        String path = "/api/v1/file/content?fileName=" + UriUtils.encodeQueryParam(objectName, StandardCharsets.UTF_8);
        return Mono.just(publicBaseUrl.isEmpty() ? path : publicBaseUrl + path);
    }

    /**
     * 执行文件上传
     *
     * <p>将输入流写入本地文件系统，生成唯一文件名防止冲突。
     * 上传成功返回唯一文件名，失败抛出 {@link BusinessException}。
     *
     * @param bucketName  存储桶名称
     * @param fileName    原始文件名
     * @param inputStream 文件输入流
     * @param contentType 文件内容类型
     * @return 保存后的唯一文件名
     */
    @Override
    /**
     * 加载load
     *
     * @param bucketName 参数
     *
     * @param fileName 参数
     *
     * @param inputStream 参数
     *
     * @param contentType 参数
     *
     * @return 结果
     */
    protected String doUpload(String bucketName, String fileName, InputStream inputStream, String contentType) {
        String uniqueFileName = generateUniqueFileName(fileName);
        try {
            Path filePath = getFilePath(bucketName, uniqueFileName);
            Files.createDirectories(filePath.getParent());
            try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
                inputStream.transferTo(fos);
            }
            log.info("文件上传成功, bucket: {}, fileName: {}", bucketName, uniqueFileName);
            return uniqueFileName;
        } catch (IOException e) {
            log.error("文件上传失败，bucket: {}, fileName: {}", bucketName, uniqueFileName, e);
            throw new BusinessException(ResultCode.STORAGE_ERROR, "文件上传失败");
        }
    }

    /**
     * 执行文件删除
     *
     * <p>从本地文件系统中删除指定文件。文件不存在时返回 false 而非抛出异常。
     *
     * @param bucketName 存储桶名称
     * @param fileName   文件名
     * @return 删除成功返回 true，文件不存在或删除失败返回 false
     */
    @Override
    /**
     * 删除te
     *
     * @param bucketName 参数
     *
     * @param fileName 参数
     *
     * @return 结果
     */
    protected boolean doDelete(String bucketName, String fileName) {
        try {
            Path filePath = getFilePath(bucketName, fileName);
            boolean deleted = Files.deleteIfExists(filePath);
            if (deleted) {
                log.info("文件删除成功, bucket: {}, fileName: {}", bucketName, fileName);
            }
            return deleted;
        } catch (IOException e) {
            log.error("文件删除失败", e);
            return false;
        }
    }

    /**
     * 检查文件是否存在
     *
     * <p>检查本地文件系统中指定文件是否存在。
     *
     * @param bucketName 存储桶名称
     * @param fileName   文件名
     * @return 文件存在返回 true，否则返回 false
     */
    @Override
    /**
     * 是否存在ts
     *
     * @param bucketName 参数
     *
     * @param fileName 参数
     *
     * @return 结果
     */
    protected boolean doExists(String bucketName, String fileName) {
        Path filePath = getFilePath(bucketName, fileName);
        return Files.exists(filePath);
    }

    /**
     * 构建预签名 URL
     *
     * <p>构建本地存储文件的访问路径，格式为 /local-storage/{bucketName}/{fileName}。
     *
     * @param bucketName 存储桶名称
     * @param fileName   文件名
     * @param expiry     过期时间（秒），本地存储忽略该参数
     * @return 文件访问路径
     */
    @Override
    /**
     * 构建PresignedUrl
     *
     * @param bucketName 参数
     *
     * @param fileName 参数
     *
     * @param expiry 参数
     *
     * @return 结果
     */
    protected String buildPresignedUrl(String bucketName, String fileName, int expiry) {
        return "/api/v1/file/content?fileName=" + UriUtils.encodeQueryParam(fileName, StandardCharsets.UTF_8);
    }

    /**
     * 获取存储桶的本地路径
     *
     * <p>基于存储根路径和存储桶名称计算本地文件系统路径。
     *
     * @param bucketName 存储桶名称
     * @return 存储桶的本地路径
     */
    private Path getBucketPath(String bucketName) {
        if (bucketName == null || bucketName.isBlank()) {
            throw new BusinessException(ResultCode.STORAGE_ERROR, "存储桶名称不能为空");
        }
        Path bucketPath = storageBasePath.resolve(bucketName).normalize();
        if (!bucketPath.startsWith(storageBasePath)) {
            throw new BusinessException(ResultCode.STORAGE_ERROR, "非法的存储桶路径: " + bucketName);
        }
        return bucketPath;
    }

    /**
     * 获取文件的完整路径，并进行路径遍历防护
     *
     * <p>通过 {@link Path#normalize()} 规范化路径，并校验解析后的路径
     * 是否仍在预期的存储桶目录范围内，防止路径遍历攻击（如 ../../etc/passwd）。
     *
     * @param bucketName 存储桶名称
     * @param fileName   文件名
     * @return 规范化的文件路径
     * @throws BusinessException 如果解析后的路径超出存储桶目录范围
     */
    private Path getFilePath(String bucketName, String fileName) {
        Path bucketPath = getBucketPath(bucketName);
        Path resolved = bucketPath.resolve(fileName).normalize();
        if (!resolved.startsWith(bucketPath)) {
            throw new BusinessException(ResultCode.STORAGE_ERROR, "非法的文件路径: " + fileName);
        }
        return resolved;
    }

    /**
     * 获取本地存储照片公开访问 URL 前缀（策略模式）。
     *
     * <p>本地照片通过 WebFlux 静态资源映射访问，前缀格式为 {@code /uploads/apex}。
     * 物理路径到 URL 的映射由 {@code VotePhotoResourceConfig} 完成。</p>
     *
     * @return 照片访问 URL 前缀，如 {@code /uploads/apex}
     */
    @Override
    /**
     * 获取PhotoUrlPrefix
     *
     * @return 结果
     */
    public String getPhotoUrlPrefix() {
        return "/uploads/" + getBucketName();
    }

    /**
     * 获取本地存储桶名称。
     *
     * @return 存储桶名称
     */
    @Override
    /**
     * 获取BucketName
     *
     * @return 结果
     */
    protected String getBucketName() {
        return LOCAL_BUCKET;
    }

    /** 本地存储桶名称 */
    private static final String LOCAL_BUCKET = "apex";
}
