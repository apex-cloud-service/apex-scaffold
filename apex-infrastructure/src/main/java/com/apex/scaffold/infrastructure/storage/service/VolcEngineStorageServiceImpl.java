package com.apex.scaffold.infrastructure.storage.service;

import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.volcengine.tos.TOSV2;
import com.volcengine.tos.model.bucket.HeadBucketV2Input;
import com.volcengine.tos.model.object.DeleteObjectInput;
import com.volcengine.tos.model.object.GetObjectV2Input;
import com.volcengine.tos.model.object.HeadObjectV2Input;
import com.volcengine.tos.model.object.PreSignedURLInput;
import com.volcengine.tos.model.object.PreSignedURLOutput;
import com.volcengine.tos.model.object.PutObjectInput;
import java.io.InputStream;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 火山引擎对象存储 TOS 服务实现类
 *
 * <p>基于火山引擎 TOS（Tinder Object Storage）实现的文件存储服务，
 * 通过 {@code storage.volcengine.enabled=true} 配置条件激活。
 * 仅在启用火山云存储时创建该 Bean。
 *
 * <h2>核心功能</h2>
 * <ul>
 *   <li>文件上传：上传文件到 TOS 存储桶</li>
 *   <li>文件下载：从 TOS 存储桶下载文件</li>
 *   <li>文件删除：删除 TOS 存储桶中的文件</li>
 *   <li>文件存在性检查：检查 TOS 存储桶中的文件是否存在</li>
 *   <li>预签名 URL 生成：生成 TOS 预签名访问 URL</li>
 * </ul>
 *
 * <h2>使用示例</h2>
 * <pre>{@code * // 配置文件
 * storage.volcengine.enabled=true
 * storage.volcengine.access-key=your-access-key
 * storage.volcengine.secret-key=your-secret-key
 * storage.volcengine.region=cn-beijing
 * storage.volcengine.endpoint=https://tos-cn-beijing.volces.com
 * }</pre>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see AbstractStorageService
 * @see TOSV2
 */
@Service("volcEngineStorageService")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "storage.volcengine.enabled", havingValue = "true", matchIfMissing = false)
public class VolcEngineStorageServiceImpl extends AbstractStorageService {
    private static final Logger log = LoggerFactory.getLogger(VolcEngineStorageServiceImpl.class);

    /** TOS 客户端，用于操作火山引擎对象存储服务 */
    private final TOSV2 tosClient;

    /**
     * 获取存储类型标识。
     *
     * @return 存储类型 "VOLCENGINE"
     */
    @Override
    /**
     * 获取StorageType
     *
     * @return 结果
     */
    protected String getStorageType() {
        return "VOLCENGINE";
    }

    /**
     * 确保存储桶已经由部署流程创建。
     *
     * <p>检查 TOS 中指定存储桶是否存在且当前账号具备访问权限；
     * 校验失败时拒绝上传，避免运行期账号拥有建桶权限。</p>
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
                        tosClient.headBucket(new HeadBucketV2Input().setBucket(bucketName));
                    } catch (BusinessException exception) {
                        throw exception;
                    } catch (Exception e) {
                        log.error("TOS存储桶不存在或无访问权限, bucket: {}", bucketName, e);
                        throw new BusinessException(ResultCode.STORAGE_ERROR, "TOS存储桶不存在或无访问权限，请由部署流程预先创建");
                    }
                })
                .then();
    }

    /**
     * 创建存储桶。
     *
     * <p>在 TOS 中创建指定名称的存储桶。创建失败时将抛出 {@link BusinessException}。</p>
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
                        tosClient.createBucket(
                                new com.volcengine.tos.model.bucket.CreateBucketV2Input().setBucket(bucketName));
                        log.info("创建TOS存储桶成功: {}", bucketName);
                    } catch (Exception e) {
                        log.error("创建 TOS 存储桶失败，bucket: {}", bucketName, e);
                        throw new BusinessException(ResultCode.STORAGE_ERROR, "创建 TOS 存储桶失败");
                    }
                })
                .then();
    }

    /**
     * 下载文件
     *
     * <p>从TOS存储桶下载指定文件，使用try-with-resources确保InputStream在异常时正确关闭。
     * 文件内容读取到内存后以ByteArrayInputStream形式返回，避免流泄漏。
     *
     * @param bucketName 存储桶名称
     * @param objectName 对象名称（文件路径）
     * @return 包含文件内容的输入流
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
                    GetObjectV2Input input =
                            new GetObjectV2Input().setBucket(bucketName).setKey(objectName);
                    try (InputStream stream = tosClient.getObject(input).getContent()) {
                        return (InputStream) new java.io.ByteArrayInputStream(stream.readAllBytes());
                    }
                })
                .doOnError(e -> log.error("TOS文件下载失败, bucket: {}, object: {}", bucketName, objectName, e));
    }

    /**
     * 生成 TOS 预签名访问 URL。
     *
     * <p>生成带有过期时间的预签名 URL，用于临时访问私有存储桶中的文件。
     * 默认过期时间为 300 秒。</p>
     *
     * @param bucketName 存储桶名称
     * @param objectName 对象（文件）名称
     * @param expiry     过期时间（秒），为 null 时默认 300 秒
     * @return 预签名 URL
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
        int effectiveExpiry = expiry == null ? 300 : expiry;
        return Mono.fromCallable(() -> {
                    PreSignedURLInput input = new PreSignedURLInput()
                            .setBucket(bucketName)
                            .setKey(objectName)
                            .setHttpMethod("GET")
                            .setExpires(effectiveExpiry);
                    PreSignedURLOutput output = tosClient.preSignedURL(input);
                    return output.getSignedUrl();
                })
                .doOnError(e -> log.error("TOS生成预签名URL失败", e));
    }

    /**
     * 执行文件上传至 TOS。
     *
     * <p>使用原始文件名作为对象 key，将文件上传到指定 TOS 存储桶。
     * 上传失败时抛出 {@link BusinessException}。</p>
     *
     * @param bucketName  存储桶名称
     * @param fileName    原始文件名
     * @param inputStream 文件输入流
     * @param contentType 文件MIME类型
     * @return 与传入值完全一致的原始文件名
     * @throws BusinessException 上传失败时抛出
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
        try {
            PutObjectInput input =
                    new PutObjectInput().setBucket(bucketName).setKey(fileName).setContent(inputStream);
            tosClient.putObject(input);
            log.info("TOS文件上传成功, bucket: {}, fileName: {}", bucketName, fileName);
            return fileName;
        } catch (Exception e) {
            log.error("TOS 文件上传失败，bucket: {}, fileName: {}", bucketName, fileName, e);
            throw new BusinessException(ResultCode.STORAGE_ERROR, "TOS 文件上传失败");
        }
    }

    /**
     * 执行文件删除。
     *
     * <p>从 TOS 存储桶中删除指定文件。</p>
     *
     * @param bucketName 存储桶名称
     * @param fileName   文件名
     * @return 删除成功返回 true，失败返回 false
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
            DeleteObjectInput input =
                    new DeleteObjectInput().setBucket(bucketName).setKey(fileName);
            tosClient.deleteObject(input);
            log.info("TOS文件删除成功, bucket: {}, fileName: {}", bucketName, fileName);
            return true;
        } catch (Exception e) {
            log.error("TOS文件删除失败", e);
            return false;
        }
    }

    /**
     * 检查 TOS 中文件是否存在。
     *
     * <p>通过 TOS {@code headObject} API 检查指定文件是否存在。</p>
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
        try {
            HeadObjectV2Input input =
                    new HeadObjectV2Input().setBucket(bucketName).setKey(fileName);
            tosClient.headObject(input);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 构建预签名 URL 路径。
     *
     * <p>构建 TOS 文件的访问路径，格式为 /tos/{bucketName}/{fileName}。</p>
     *
     * @param bucketName 存储桶名称
     * @param fileName   文件名
     * @param expiry     过期时间（秒）
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
        return "/tos/" + bucketName + "/" + fileName;
    }

    /**
     * 获取火山云 TOS 照片公开访问 URL 前缀（策略模式）。
     *
     * <p>TOS 照片通过预签名 URL 代理端点访问，前缀格式为 {@code /tos/{bucketName}}。
     * 当前 bucketName 为 {@code apex-photo}，完整前缀为 {@code /tos/apex-photo}。</p>
     *
     * @return 照片访问 URL 前缀，如 {@code /tos/apex-photo}
     */
    @Override
    /**
     * 获取PhotoUrlPrefix
     *
     * @return 结果
     */
    public String getPhotoUrlPrefix() {
        return "/tos/" + getBucketName();
    }

    /**
     * 获取火山云 TOS 存储桶名称。
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
        return TOS_PHOTO_BUCKET;
    }

    /** 火山云 TOS 照片存储桶名称 */
    private static final String TOS_PHOTO_BUCKET = "apex-photo";
}
