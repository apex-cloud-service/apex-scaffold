package com.apex.scaffold.interfaces.handler;

import com.apex.scaffold.domain.repository.FileRepository;
import com.apex.scaffold.domain.service.StorageService;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * 存储代理处理器的抽象基类。
 *
 * <p>将 {@link TosPhotoProxyHandler} 和 {@link MinioPhotoProxyHandler}
 * 中完全相同的代理逻辑提取到本抽象类中，子类只需提供路径前缀、存储服务实现和日志前缀即可。
 * 本类不继承 {@link BaseHandler}，因为代理处理器不要求用户认证，
 * 仅负责将浏览器请求以 HTTP 302 重定向方式转发到对象存储预签名 URL。</p>
 *
 * <h2>设计意图</h2>
 * <p>投票照片获取服务（{@link com.apex.scaffold.application.service.VotePhotoService}）
 * 通过策略模式调用 {@link StorageService#getPhotoUrlPrefix()} 获取 URL 前缀。
 * 不同存储后端生成不同前缀的内部代理路径（如 {@code /tos/apex-photo/AF/a.jpg}
 * 或 {@code /minio/apex/AF/a.jpg}），统一由本类的 {@link #proxy(ServerRequest)}
 * 方法解析路径、生成预签名 URL 后执行 302 重定向，使客户端可以直接从存储后端拉取照片。</p>
 *
 * <h2>子类实现要求</h2>
 * <p>子类必须是 Spring Bean（标注 {@code @Component} 或通过
 * {@code @Configuration} 注册），需要实现三个抽象方法：</p>
 * <ul>
 *   <li>{@link #getPathPrefix()} — 返回代理路径前缀，如 {@code "/tos"} 或 {@code "/minio"}</li>
 *   <li>{@link #getStorageService()} — 返回具体的 {@link StorageService} 实现，
 *       通常通过 {@code @Qualifier} 注入</li>
 *   <li>{@link #getLogPrefix()} — 返回日志消息前缀，如 {@code "TOS"} 或 {@code "MinIO"}</li>
 * </ul>
 *
 * <h2>工作流程</h2>
 * <ol>
 *   <li>使用正则表达式 {@code ^<prefix>/([^/]+)/(.+)$} 解析请求路径，
 *       提取存储桶名称和对象键</li>
 *   <li>对对象键进行 URL 解码，处理中文文件名等特殊字符</li>
 *   <li>调用 {@link StorageService#getPresignedUrl(String, String, Integer)}
 *       生成预签名 URL（默认有效期 300 秒）</li>
 *   <li>以 HTTP 302 Found 重定向客户端到预签名 URL</li>
 *   <li>路径不匹配时返回 HTTP 404，预签名 URL 生成失败时返回 HTTP 500</li>
 * </ol>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 2.2.0
 * @see TosPhotoProxyHandler
 * @see MinioPhotoProxyHandler
 * @see StorageService
 */
@Slf4j
public abstract class AbstractStorageProxyHandler {

    /** 文件元数据仓储；用于在签发存储 URL 前执行逻辑删除可见性校验。 */
    private final FileRepository fileRepository;

    /**
     * 创建存储代理处理器。
     *
     * @param fileRepository 文件元数据仓储，用于在签发预签名 URL 前校验对象未被逻辑删除；
     *                       可为 {@code null}，此时跳过可见性校验（仅供轻量测试场景使用）
     */
    protected AbstractStorageProxyHandler(FileRepository fileRepository) {
        this.fileRepository = fileRepository;
    }

    /** 仅供不受 Spring 管理的轻量测试使用。 */
    protected AbstractStorageProxyHandler() {
        this(null);
    }

    /** 预签名 URL 默认有效期（秒） */
    private static final int DEFAULT_PRESIGNED_EXPIRE_SECONDS = 300;

    /** 预签名 URL 本地缓存最大条目数，超过后执行保守清空以限制内存。 */
    private static final int MAX_PRESIGNED_CACHE_ENTRIES = 10_000;

    /** 按存储类型、桶和对象键缓存仍在安全期内的预签名 URL。 */
    private final Map<String, CachedPresignedUrl> presignedUrlCache = new ConcurrentHashMap<>();

    /**
     * 预签名 URL 有效期（秒），可通过 {@code storage.presigned-url-expire-seconds} 配置覆盖。
     *
     * <p>此字段通过 Spring {@link Value} 注解注入，子类作为 Spring Bean 注册后自动获得注入值。
     * 默认值为 300 秒（5 分钟）。</p>
     */
    @Value("${storage.presigned-url-expire-seconds:300}")
    private int presignedUrlExpireSeconds;

    /**
     * 预签名 URL 进程内复用时间（秒）。
     *
     * <p>配置必须小于签名有效期，运行时还会自动预留 30 秒安全窗口。</p>
     */
    @Value("${storage.presigned-url-cache-seconds:240}")
    private int presignedUrlCacheSeconds;

    /** HTTP 302 私有缓存时间（秒），不得超过预签名 URL 的安全剩余时间。 */
    @Value("${storage.proxy-redirect-cache-seconds:60}")
    private int proxyRedirectCacheSeconds;

    /**
     * 返回代理路径前缀。
     *
     * <p>子类实现示例：</p>
     * <ul>
     *   <li>{@code TosPhotoProxyHandler} 返回 {@code "/tos"}</li>
     *   <li>{@code MinioPhotoProxyHandler} 返回 {@code "/minio"}</li>
     * </ul>
     *
     * @return 代理路径前缀，用于构建路径匹配正则表达式
     */
    protected abstract String getPathPrefix();

    /**
     * 返回对应的 {@link StorageService} 实现。
     *
     * <p>子类通常通过构造器注入并通过 {@link org.springframework.beans.factory.annotation.Qualifier}
     * 精确指定具体的存储服务 Bean：</p>
     * <ul>
     *   <li>{@code TosPhotoProxyHandler} 注入 {@code @Qualifier("volcEngineStorageService")}</li>
     *   <li>{@code MinioPhotoProxyHandler} 注入 {@code @Qualifier("minioStorageService")}</li>
     * </ul>
     *
     * @return 具体的存储服务实现，用于生成预签名 URL
     */
    protected abstract StorageService getStorageService();

    /**
     * 返回日志消息前缀。
     *
     * <p>子类实现示例：</p>
     * <ul>
     *   <li>{@code TosPhotoProxyHandler} 返回 {@code "TOS"}</li>
     *   <li>{@code MinioPhotoProxyHandler} 返回 {@code "MinIO"}</li>
     * </ul>
     *
     * @return 日志消息前缀，用于区分不同存储后端的日志输出
     */
    protected abstract String getLogPrefix();

    /**
     * 获取有效的预签名 URL 过期时间。
     *
     * <p>若配置值小于等于 0，则回退到默认值 {@value #DEFAULT_PRESIGNED_EXPIRE_SECONDS} 秒。</p>
     *
     * @return 有效的过期时间（秒），始终为正整数
     */
    protected int getEffectiveExpireSeconds() {
        return presignedUrlExpireSeconds > 0 ? presignedUrlExpireSeconds : DEFAULT_PRESIGNED_EXPIRE_SECONDS;
    }

    /**
     * 处理存储代理请求。
     *
     * <p>从请求路径中提取存储桶名称和对象键，生成预签名 URL，
     * 并以 HTTP 302 重定向方式返回给客户端。</p>
     *
     * <h2>路径格式</h2>
     * <pre>{@code * <pathPrefix>/{bucketName}/{directory}/{filename}
     * 示例（TOS）：/tos/apex-photo/AF/a.jpg
     *   → bucketName = "apex-photo"
     *   → objectKey  = "AF/a.jpg"
     * 示例（MinIO）：/minio/apex/AF/a.jpg
     *   → bucketName = "apex"
     *   → objectKey  = "AF/a.jpg"
     * }</pre>
     *
     * @param request HTTP 请求，路径格式为 {@code <pathPrefix>/{bucketName}/{...objectKey}}
     * @return HTTP 302 重定向响应（成功），
     *         HTTP 404 响应（路径不匹配或参数无效），
     *         HTTP 500 响应（预签名 URL 生成失败）
     */
    public Mono<ServerResponse> proxy(ServerRequest request) {
        String path = request.requestPath().value();
        Pattern pathPattern = Pattern.compile("^" + getPathPrefix() + "/([^/]+)/(.+)$");
        Matcher matcher = pathPattern.matcher(path);
        if (!matcher.matches()) {
            log.warn("{} 代理路径不匹配: {}", getLogPrefix(), path);
            return ServerResponse.notFound().build();
        }

        String bucketName = matcher.group(1);
        String objectKey = matcher.group(2);

        if (bucketName == null || bucketName.isBlank() || objectKey == null || objectKey.isBlank()) {
            log.warn("{} 代理路径参数无效: bucket={}, key={}", getLogPrefix(), bucketName, objectKey);
            return ServerResponse.notFound().build();
        }

        // URL 解码对象键，处理中文文件名等特殊字符
        String decodedObjectKey = java.net.URLDecoder.decode(objectKey, StandardCharsets.UTF_8);

        log.debug("{} 代理请求: bucket={}, key={}", getLogPrefix(), bucketName, decodedObjectKey);

        return isObjectReadable(bucketName, decodedObjectKey)
                .flatMap(readable ->
                        readable ? getCachedOrCreatePresignedUrl(bucketName, decodedObjectKey) : Mono.empty())
                .flatMap(presignedUrl -> {
                    log.debug("{} 预签名 URL 生成成功: bucket={}, key={}", getLogPrefix(), bucketName, decodedObjectKey);
                    return ServerResponse.status(HttpStatus.FOUND)
                            .location(URI.create(presignedUrl))
                            .header("Cache-Control", "private, max-age=" + Math.max(0, effectiveRedirectCacheSeconds()))
                            .build();
                })
                .switchIfEmpty(ServerResponse.notFound().build())
                .onErrorResume(e -> {
                    log.error(
                            "{} 预签名 URL 生成失败: bucket={}, key={}, error={}",
                            getLogPrefix(),
                            bucketName,
                            decodedObjectKey,
                            e.getMessage(),
                            e);
                    return ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .build();
                });
    }

    /**
     * 逻辑删除后的对象在五天保留期内仍物理存在，但不得继续签发新的访问 URL。
     * 没有历史元数据的对象继续按原行为放行，避免影响存量非数据库对象。
     */
    private Mono<Boolean> isObjectReadable(String bucketName, String objectKey) {
        if (fileRepository == null) {
            return Mono.just(true);
        }
        return fileRepository
                .findByStorageLocation(bucketName, objectKey)
                .map(file -> !file.isDeleted()
                        && !"PENDING_DELETE".equals(file.getStorageStatus())
                        && !"DELETED".equals(file.getStorageStatus()))
                .defaultIfEmpty(true);
    }

    /**
     * 获取仍在安全期内的缓存签名，未命中时调用存储服务生成并写入本地缓存。
     *
     * @param bucketName 存储桶名称
     * @param objectKey 对象键
     * @return 可安全用于 HTTP 302 的预签名 URL
     */
    private Mono<String> getCachedOrCreatePresignedUrl(String bucketName, String objectKey) {
        String cacheKey = getLogPrefix() + ":" + bucketName + ":" + objectKey;
        long now = System.nanoTime();
        CachedPresignedUrl cached = presignedUrlCache.get(cacheKey);
        if (cached != null && now < cached.expiresAtNanos()) {
            return Mono.just(cached.url());
        }
        return getStorageService()
                .getPresignedUrl(bucketName, objectKey, getEffectiveExpireSeconds())
                .doOnNext(url -> {
                    if (presignedUrlCache.size() >= MAX_PRESIGNED_CACHE_ENTRIES) {
                        presignedUrlCache.clear();
                    }
                    long expiresAt = System.nanoTime()
                            + Duration.ofSeconds(effectivePresignedCacheSeconds())
                                    .toNanos();
                    presignedUrlCache.put(cacheKey, new CachedPresignedUrl(url, expiresAt));
                });
    }

    /**
     * 计算预签名 URL 的安全缓存秒数。
     *
     * @return 至少 1 秒且比签名有效期少 30 秒的缓存时间
     */
    private int effectivePresignedCacheSeconds() {
        int configured = presignedUrlCacheSeconds > 0 ? presignedUrlCacheSeconds : 1;
        int safeUpperBound = Math.max(1, getEffectiveExpireSeconds() - 30);
        return Math.min(configured, safeUpperBound);
    }

    /**
     * 计算 302 响应允许客户端缓存的秒数。
     *
     * @return 不超过本地签名安全缓存期的非负秒数
     */
    private int effectiveRedirectCacheSeconds() {
        int configured = Math.max(0, proxyRedirectCacheSeconds);
        return Math.min(configured, effectivePresignedCacheSeconds());
    }

    /**
     * 进程内预签名 URL 缓存值。
     *
     * @param url 预签名 URL
     * @param expiresAtNanos 基于单调时钟的安全失效时间
     * @author Apex Team
     */
    private record CachedPresignedUrl(String url, long expiresAtNanos) {}
}
