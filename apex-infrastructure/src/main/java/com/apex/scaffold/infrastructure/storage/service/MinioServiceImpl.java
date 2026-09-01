package com.apex.scaffold.infrastructure.storage.service;

import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.http.Method;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * MinIO存储服务实现类
 *
 * <p>基于MinIO对象存储服务实现的文件存储服务，适用于分布式部署场景
 *
 * <h2>核心功能</h2>
 * <ul>
 *   <li>文件上传：上传文件到MinIO存储桶</li>
 *   <li>文件下载：从MinIO存储桶下载文件</li>
 *   <li>文件删除：删除MinIO存储桶中的文件</li>
 *   <li>文件存在性检查：检查MinIO存储桶中的文件是否存在</li>
 *   <li>存储桶管理：校验部署流程预创建的MinIO存储桶</li>
 *   <li>预签名URL生成：生成MinIO预签名访问URL</li>
 * </ul>
 *
 * <h2>使用示例</h2>
 * <pre>
 * // 配置文件添加
 * storage.default-type=minio
 * minio.endpoint=http://localhost:9000
 * minio.access-key=your-access-key
 * minio.secret-key=your-secret-key
 *
 * // 使用示例
 * Mono&lt;String&gt; uploadResult = storageService.upload("avatars", "user.jpg", inputStream, "image/jpeg");
 * Mono&lt;String&gt; presignedUrl = storageService.getPresignedUrl("avatars", "user.jpg", 60);
 * </pre>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see AbstractStorageService
 * @see MinioClient
 */
@Service("minioStorageService")
@RequiredArgsConstructor
@ConditionalOnBean(MinioClient.class)
public class MinioServiceImpl extends AbstractStorageService {
    private static final Logger log = LoggerFactory.getLogger(MinioServiceImpl.class);

    /**
     * MinIO 客户端
     *
     * <p>用于操作 MinIO 对象存储服务，提供文件上传、下载、删除等基础能力
     */
    private final MinioClient minioClient;

    /**
     * 返回当前存储类型标识。
     *
     * @return 存储类型字符串 "MINIO"
     */
    @Override
    protected String getStorageType() {
        return "MINIO";
    }

    /**
     * 确保MinIO存储桶已经由部署流程创建。
     *
     * <p>检查目标存储桶是否存在；不存在时拒绝上传，避免运行期账号拥有建桶权限。</p>
     *
     * @param bucketName 存储桶名称
     * @return 空Mono，表示操作完成
     */
    @Override
    protected Mono<Void> ensureBucketExistsInternal(String bucketName) {
        return Mono.fromRunnable(() -> {
                    try {
                        boolean exists = minioClient.bucketExists(
                                BucketExistsArgs.builder().bucket(bucketName).build());
                        if (!exists) {
                            throw new BusinessException(ResultCode.STORAGE_ERROR, "存储桶不存在，请由部署流程预先创建");
                        }
                    } catch (BusinessException exception) {
                        throw exception;
                    } catch (Exception e) {
                        log.error("检查存储桶失败，bucket: {}", bucketName, e);
                        throw new BusinessException(ResultCode.STORAGE_ERROR, "检查存储桶失败");
                    }
                })
                .then();
    }

    /**
     * 创建MinIO存储桶。
     *
     * <p>如果存储桶已存在则跳过创建，否则调用MinIO客户端创建新存储桶。</p>
     *
     * @param bucketName 存储桶名称
     * @return 异步操作结果
     */
    @Override
    protected Mono<Void> createBucketInternal(String bucketName) {
        return Mono.fromRunnable(() -> {
                    try {
                        boolean exists = minioClient.bucketExists(
                                BucketExistsArgs.builder().bucket(bucketName).build());
                        if (!exists) {
                            minioClient.makeBucket(
                                    MakeBucketArgs.builder().bucket(bucketName).build());
                            log.info("创建存储桶成功: {}", bucketName);
                        }
                    } catch (Exception e) {
                        log.error("创建存储桶失败，bucket: {}", bucketName, e);
                        throw new BusinessException(ResultCode.STORAGE_ERROR, "创建存储桶失败");
                    }
                })
                .then();
    }

    /**
     * 下载文件
     *
     * <p>从MinIO存储桶下载指定文件，返回原始InputStream实现流式传输，避免将整个文件加载到内存中。
     *
     * <h3>重要注意事项</h3>
     * <ul>
     *   <li>返回的InputStream必须由调用方负责关闭，推荐使用try-with-resources语句</li>
     *   <li>采用流式传输方式，支持大文件下载，不会导致内存溢出</li>
     *   <li>异常时自动关闭底层连接，并记录错误日志</li>
     * </ul>
     *
     * <h3>使用示例</h3>
     * <pre>
     * storageService.download("bucket", "path/to/file")
     *     .doOnNext(stream -> {
     *         try (stream) {
     *             // 处理文件流
     *         }
     *     })
     *     .subscribe();
     * </pre>
     *
     * @param bucketName 存储桶名称
     * @param objectName 对象名称（文件路径）
     * @return 包含文件内容的原始输入流，调用方负责关闭
     */
    @Override
    public Mono<InputStream> download(String bucketName, String objectName) {
        return Mono.fromCallable(() -> (InputStream) minioClient.getObject(GetObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectName)
                        .build()))
                .doOnError(e -> log.error("文件下载失败, bucket: {}, object: {}", bucketName, objectName, e));
    }

    /**
     * 生成MinIO文件的预签名访问URL。
     *
     * <p>生成带有过期时间的预签名GET URL，用于临时访问私有存储桶中的文件。</p>
     *
     * @param bucketName 存储桶名称
     * @param objectName 对象（文件）名称
     * @param expiry     过期时间（秒），为 {@code null} 时默认 300 秒
     * @return 预签名URL
     */
    @Override
    public Mono<String> getPresignedUrl(String bucketName, String objectName, Integer expiry) {
        int effectiveExpiry = expiry == null ? 300 : expiry;
        return Mono.fromCallable(() -> minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                        .method(Method.GET)
                        .bucket(bucketName)
                        .object(objectName)
                        .expiry(effectiveExpiry, TimeUnit.SECONDS)
                        .build()))
                .doOnError(e -> log.error("生成预签名URL失败", e));
    }

    /**
     * 执行文件上传至MinIO。
     *
     * <p>生成唯一文件名后将文件上传到指定MinIO存储桶。
     * 上传失败时抛出 {@link BusinessException}。</p>
     *
     * @param bucketName  存储桶名称
     * @param fileName    原始文件名
     * @param inputStream 文件输入流
     * @param contentType 文件MIME类型
     * @return 上传后的唯一文件名
     */
    @Override
    protected String doUpload(String bucketName, String fileName, InputStream inputStream, String contentType) {
        String uniqueFileName = generateUniqueFileName(fileName);
        try {
            minioClient.putObject(
                    PutObjectArgs.builder().bucket(bucketName).object(uniqueFileName).stream(inputStream, -1, 10485760)
                            .contentType(contentType)
                            .build());
            log.info("文件上传成功, bucket: {}, fileName: {}", bucketName, uniqueFileName);
            return uniqueFileName;
        } catch (Exception e) {
            log.error("文件上传失败，bucket: {}", bucketName, e);
            throw new BusinessException(ResultCode.STORAGE_ERROR, "文件上传失败");
        }
    }

    /**
     * 执行文件删除。
     *
     * <p>从 MinIO 存储桶中删除指定文件。</p>
     *
     * @param bucketName 存储桶名称
     * @param fileName   文件名
     * @return 删除成功返回 true，失败返回 false
     */
    @Override
    protected boolean doDelete(String bucketName, String fileName) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucketName)
                    .object(fileName)
                    .build());
            log.info("文件删除成功, bucket: {}, fileName: {}", bucketName, fileName);
            return true;
        } catch (Exception e) {
            log.error("文件删除失败", e);
            return false;
        }
    }

    /**
     * 检查文件是否存在。
     *
     * <p>检查 MinIO 存储桶中指定文件是否存在。</p>
     *
     * @param bucketName 存储桶名称
     * @param fileName   文件名
     * @return 文件存在返回 true，否则返回 false
     */
    @Override
    protected boolean doExists(String bucketName, String fileName) {
        try {
            minioClient.statObject(
                    StatObjectArgs.builder().bucket(bucketName).object(fileName).build());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 构建MinIO文件的访问路径。
     *
     * <p>返回格式为 /minio/{bucketName}/{fileName} 的相对路径。</p>
     *
     * @param bucketName 存储桶名称
     * @param fileName   文件名
     * @param expiry     过期时间（秒），MinIO实现中暂未使用
     * @return 文件访问路径
     */
    @Override
    protected String buildPresignedUrl(String bucketName, String fileName, int expiry) {
        return "/minio/" + bucketName + "/" + fileName;
    }

    /**
     * 获取 MinIO 照片公开访问 URL 前缀（策略模式）。
     *
     * <p>MinIO 照片通过 Nginx 代理访问，前缀格式为 {@code /minio/apex}。</p>
     *
     * @return 照片访问 URL 前缀，如 {@code /minio/apex}
     */
    @Override
    public String getPhotoUrlPrefix() {
        return "/minio/" + getBucketName();
    }

    /**
     * 获取 MinIO 存储桶名称。
     *
     * @return 存储桶名称
     */
    @Override
    protected String getBucketName() {
        return MINIO_BUCKET;
    }

    /** MinIO 存储桶名称 */
    private static final String MINIO_BUCKET = "apex";
}
