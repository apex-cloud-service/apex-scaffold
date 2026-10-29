package com.apex.scaffold.file.application.service;

import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.file.application.dto.share.FileShareContent;
import com.apex.scaffold.file.application.dto.share.FileShareCreated;
import com.apex.scaffold.file.application.dto.share.FileShareExchangeResult;
import com.apex.scaffold.file.domain.model.entity.FileEntity;
import com.apex.scaffold.file.domain.model.entity.FileShareEntity;
import com.apex.scaffold.file.domain.repository.FileRepository;
import com.apex.scaffold.file.domain.repository.FileShareRepository;
import com.apex.scaffold.file.domain.service.StorageServiceProvider;
import com.apex.scaffold.file.domain.service.TenantStatusPort;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 文件限时只读分享应用服务。
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>创建与撤销使用 {@link FileAccessContext} 的可信身份，要求文件所有者本人操作，
 *       跨租户或非所有者一律拒绝</li>
 *   <li>只有存储状态为 {@code STORED} 的文件可创建分享与兑换</li>
 *   <li>兑换不读取匿名请求携带的身份信息，租户与文件完全由服务端分享记录确定</li>
 *   <li>分享令牌为 32 字节随机值，库内仅保存 SHA-256 摘要，明文只在创建响应返回一次</li>
 *   <li>日志不记录令牌与任何可复用的访问凭据</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.2
 * @see FileShareRepository
 * @see FileAccessContext
 */
@Service
@Slf4j
public class FileShareService {

    private static final String STORED = "STORED";

    /** 分享令牌随机字节数。 */
    private static final int TOKEN_BYTES = 32;

    /** 分享最短有效期（秒），避免创建即失效。 */
    private static final int MIN_EXPIRE_SECONDS = 60;

    /** 分享有效期上限（秒），与预签名上限一致。 */
    private static final int DEFAULT_MAX_EXPIRE_SECONDS = 604800;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final FileRepository fileRepository;
    private final FileShareRepository fileShareRepository;
    private final StorageServiceProvider provider;
    private final TenantStatusPort tenantStatusPort;
    private final int defaultExpireSeconds;
    private final int maxExpireSeconds;

    /**
     * 构造文件分享应用服务。
     *
     * @param fileRepository 文件仓储
     * @param fileShareRepository 分享记录仓储
     * @param provider 按真实存储类型选择的存储注册表
     * @param tenantStatusPort 租户可用状态端口
     * @param defaultExpireSeconds 默认有效期（秒）
     * @param maxExpireSeconds 有效期上限（秒）
     */
    public FileShareService(
            FileRepository fileRepository,
            FileShareRepository fileShareRepository,
            StorageServiceProvider provider,
            TenantStatusPort tenantStatusPort,
            @Value("${file.share.default-expire-seconds:86400}") int defaultExpireSeconds,
            @Value("${file.share.max-expire-seconds:604800}") int maxExpireSeconds) {
        this.fileRepository = fileRepository;
        this.fileShareRepository = fileShareRepository;
        this.provider = provider;
        this.tenantStatusPort = tenantStatusPort;
        this.defaultExpireSeconds = defaultExpireSeconds;
        this.maxExpireSeconds = maxExpireSeconds > 0 ? maxExpireSeconds : DEFAULT_MAX_EXPIRE_SECONDS;
    }

    /**
     * 创建限时只读分享，仅文件所有者可操作。
     *
     * @param fileId 文件编号
     * @param expireSeconds 期望有效期（秒），为空时使用默认值
     * @return 含明文令牌与失效时间的创建结果
     */
    public Mono<FileShareCreated> create(Long fileId, Integer expireSeconds) {
        return FileAccessContext.current().flatMap(identity -> fileRepository
                .findById(fileId)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "文件记录不存在")))
                .flatMap(file -> {
                    identity.authorize(file);
                    if (!STORED.equals(file.getStorageStatus())) {
                        return Mono.error(new BusinessException(ResultCode.FORBIDDEN, "文件尚不可分享"));
                    }
                    String token = generateToken();
                    FileShareEntity share = new FileShareEntity();
                    share.setFileId(file.getId());
                    share.setTenantId(file.getTenantId());
                    share.setCreatorId(identity.userId());
                    share.setTokenHash(sha256Hex(token));
                    share.setExpireAt(LocalDateTime.now().plusSeconds(resolveExpireSeconds(expireSeconds)));
                    share.setStatus(FileShareEntity.STATUS_ACTIVE);
                    return fileShareRepository
                            .save(share)
                            .map(saved ->
                                    new FileShareCreated(saved.getId(), saved.getFileId(), token, saved.getExpireAt()));
                }));
    }

    /**
     * 撤销分享，仅同一租户的创建者本人可操作；重复撤销视为成功。
     *
     * @param shareId 分享记录编号
     */
    public Mono<Void> revoke(Long shareId) {
        return FileAccessContext.current().flatMap(identity -> fileShareRepository
                .markRevoked(shareId, identity.tenantId(), identity.userId(), LocalDateTime.now())
                .flatMap(revoked -> {
                    if (Boolean.TRUE.equals(revoked)) {
                        return Mono.empty();
                    }
                    return assertRevocableBy(shareId, identity);
                }));
    }

    /**
     * 兑换分享，返回文件元数据而不返回内容。
     *
     * @param token 明文分享令牌
     * @return 兑换结果
     */
    public Mono<FileShareExchangeResult> exchange(String token) {
        return resolveSharedFile(token)
                .map(shared -> new FileShareExchangeResult(
                        shared.share().getId(),
                        shared.file().getId(),
                        shared.file().getOriginalName(),
                        shared.file().getContentType(),
                        shared.file().getFileSize(),
                        shared.share().getExpireAt()));
    }

    /**
     * 兑换并读取分享文件内容，由服务端代理读取，不签发可直接访问的存储地址。
     *
     * @param token 明文分享令牌
     * @return 文件内容
     */
    public Mono<FileShareContent> download(String token) {
        return resolveSharedFile(token).flatMap(shared -> {
            FileEntity file = shared.file();
            return provider.getStorageService(file.getStorageType())
                    .download(file.getBucketName(), file.getFileName())
                    .map(stream -> new FileShareContent(
                            file.getOriginalName(), file.getContentType(), file.getFileSize(), stream));
        });
    }

    /** 校验分享在未命中条件撤销时的真实归属，用于区分无权撤销与重复撤销。 */
    private Mono<Void> assertRevocableBy(Long shareId, FileAccessContext identity) {
        return fileShareRepository
                .findById(shareId)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "分享记录不存在")))
                .flatMap(share -> {
                    if (!Objects.equals(identity.tenantId(), share.getTenantId())
                            || !Objects.equals(identity.userId(), share.getCreatorId())) {
                        return Mono.error(new BusinessException(ResultCode.FORBIDDEN, "无权撤销该分享"));
                    }
                    return Mono.empty();
                });
    }

    /** 依据令牌摘要解析分享与文件，并校验有效期、状态、租户与存储状态。 */
    private Mono<SharedFile> resolveSharedFile(String token) {
        if (token == null || token.isBlank()) {
            return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "分享令牌不能为空"));
        }
        return fileShareRepository
                .findByTokenHash(sha256Hex(token.trim()))
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "分享不存在或已失效")))
                .flatMap(share -> {
                    if (!share.isRedeemable(LocalDateTime.now())) {
                        return Mono.error(new BusinessException(ResultCode.FORBIDDEN, "分享已过期或已撤销"));
                    }
                    if (!tenantStatusPort.isActive(share.getTenantId())) {
                        return Mono.error(new BusinessException(ResultCode.FORBIDDEN, "所属租户已停用"));
                    }
                    return fileRepository
                            .findById(share.getFileId())
                            .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "分享文件不存在")))
                            .flatMap(file -> {
                                if (!Objects.equals(share.getTenantId(), file.getTenantId())) {
                                    return Mono.error(new BusinessException(ResultCode.FORBIDDEN, "分享文件归属不一致"));
                                }
                                if (!STORED.equals(file.getStorageStatus())) {
                                    return Mono.error(new BusinessException(ResultCode.FORBIDDEN, "分享文件已不可访问"));
                                }
                                return Mono.just(new SharedFile(share, file));
                            });
                });
    }

    /** 计算并收敛有效期到 [最小, 上限] 区间。 */
    private int resolveExpireSeconds(Integer expireSeconds) {
        int requested = expireSeconds == null || expireSeconds <= 0 ? defaultExpireSeconds : expireSeconds;
        if (requested < MIN_EXPIRE_SECONDS) {
            return MIN_EXPIRE_SECONDS;
        }
        return Math.min(requested, maxExpireSeconds);
    }

    /** 生成不可猜测的分享令牌（32 字节随机、Base64URL 无填充）。 */
    private static String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** 计算令牌的 SHA-256 十六进制摘要。 */
    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前运行环境不支持 SHA-256", exception);
        }
    }

    /**
     * 兑换过程中的分享与文件组合。
     *
     * @param share 分享记录
     * @param file 被分享的文件记录
     */
    private record SharedFile(FileShareEntity share, FileEntity file) {}
}
