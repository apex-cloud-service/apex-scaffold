package com.apex.scaffold.auth.application.service;

import com.apex.scaffold.auth.application.dto.PasswordRecoveryConfirmRequest;
import com.apex.scaffold.auth.application.dto.PasswordRecoveryConfirmResponse;
import com.apex.scaffold.auth.application.dto.PasswordRecoveryInitiateRequest;
import com.apex.scaffold.auth.application.dto.PasswordRecoveryInitiateResponse;
import com.apex.scaffold.auth.application.dto.PasswordRecoveryVerifyRequest;
import com.apex.scaffold.auth.application.dto.PasswordRecoveryVerifyResponse;
import com.apex.scaffold.auth.domain.model.entity.PasswordRecoveryEntity;
import com.apex.scaffold.auth.domain.repository.PasswordRecoveryAccountDirectory;
import com.apex.scaffold.auth.domain.repository.PasswordRecoveryRepository;
import com.apex.scaffold.auth.domain.service.PasswordPolicyService;
import com.apex.scaffold.auth.domain.service.RecoveryChallengeSender;
import com.apex.scaffold.auth.domain.service.RecoveryCredentialStore;
import com.apex.scaffold.auth.domain.service.TokenService;
import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.domain.service.DomainEventPublisher;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 自助密码找回应用服务。
 *
 * <p>三段式链路：申请（解析账号 + 限流 + 投递一次性验证码）→ 验证（原子消费验证码，
 * 颁发一次性确认令牌）→ 确认（原子消费确认令牌 + 密码策略 + 原子重设 + 全端撤销会话）。</p>
 *
 * <p>安全约束：
 * <ul>
 *   <li>申请对账号存在与否保持同一响应形态（不存在账号返回占位恢复单），防止账号枚举；</li>
 *   <li>联系方式命中多个账号时落 {@code MANUAL_REQUIRED}，走受审计的人工恢复，不发码；</li>
 *   <li>验证码与确认令牌只存 SHA-256 摘要、绑定恢复单用途、短期 TTL，验证码重复校验计入失败次数；</li>
 *   <li>并发确认通过确认令牌原子消费与恢复单条件跃迁双重保证最多一次成功；</li>
 *   <li>撤销会话失败不能伪称全端退出，响应中返回 {@code PENDING} 并记录可追踪日志。</li>
 * </ul>
 *
 * @author Apex Security Team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordRecoveryApplicationService {

    /** 验证码有效期。 */
    private static final Duration CODE_TTL = Duration.ofMinutes(10);

    /** 确认令牌有效期。 */
    private static final Duration CONFIRM_TTL = Duration.ofMinutes(10);

    /** 申请侧限流窗口。 */
    private static final Duration RATE_WINDOW = Duration.ofHours(1);

    /** 同一联系方式每小时申请上限。 */
    private static final int CONTACT_HOURLY_LIMIT = 5;

    /** 同一来源 IP 每小时申请上限。 */
    private static final int IP_HOURLY_LIMIT = 20;

    /** 验证码最大校验失败次数，达到后恢复单作废。 */
    private static final int MAX_VERIFY_ATTEMPTS = 5;

    /** 验证码位数。 */
    private static final int CODE_LENGTH = 6;

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private static final Pattern PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");

    private static final String RATE_LIMIT_KEY_PREFIX = "auth:pwd-recovery:rl:";

    private static final String CONFIRM_STATUS_PASSWORD_RESET = "PASSWORD_RESET";

    private static final String REASON_PASSWORD_RESET = "PASSWORD_RESET";

    private final PasswordRecoveryAccountDirectory accountDirectory;

    private final PasswordRecoveryRepository recoveryRepository;

    private final RecoveryCredentialStore credentialStore;

    private final RecoveryChallengeSender challengeSender;

    private final PasswordPolicyService passwordPolicyService;

    private final PasswordEncoder passwordEncoder;

    private final TokenService tokenService;

    private final DomainEventPublisher domainEventPublisher;

    private final MessageSource messageSource;

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * 发起密码找回申请。
     *
     * <p>按 IP 与联系方式双维度限流；账号不存在时返回同形态占位恢复单，
     * 命中多个账号时落人工恢复单，均不投递验证码。</p>
     *
     * @param request 找回申请请求（恢复通道与联系方式）
     * @param clientIp 客户端IP，用于来源限流，可为null
     * @return 找回申请响应；账号不存在时为同形态占位恢复单
     */
    public Mono<PasswordRecoveryInitiateResponse> initiate(PasswordRecoveryInitiateRequest request, String clientIp) {
        String channel =
                request.getChannel() == null ? "" : request.getChannel().trim();
        String contact =
                request.getContact() == null ? "" : request.getContact().trim();
        validateChannelAndContact(channel, contact);

        String normalized = PasswordRecoveryEntity.CHANNEL_EMAIL.equals(channel) ? contact.toLowerCase() : contact;
        String contactHash = sha256(normalized);
        String ipHash = (clientIp == null || clientIp.isBlank()) ? "unknown" : sha256(clientIp.trim());

        return credentialStore
                .tryAcquireRateLimit(RATE_LIMIT_KEY_PREFIX + "ip:" + ipHash, IP_HOURLY_LIMIT, RATE_WINDOW)
                .flatMap(ipAllowed -> {
                    if (!ipAllowed) {
                        return Mono.error(new BusinessException(ResultCode.RATE_LIMIT));
                    }
                    return credentialStore.tryAcquireRateLimit(
                            RATE_LIMIT_KEY_PREFIX + "contact:" + contactHash, CONTACT_HOURLY_LIMIT, RATE_WINDOW);
                })
                .flatMap(contactAllowed -> {
                    if (!contactAllowed) {
                        return Mono.error(new BusinessException(ResultCode.RATE_LIMIT));
                    }
                    return recoveryRepository
                            .countRecentByContactHash(
                                    contactHash, LocalDateTime.now().minus(RATE_WINDOW))
                            .flatMap(recent -> recent >= CONTACT_HOURLY_LIMIT
                                    ? Mono.error(new BusinessException(ResultCode.RATE_LIMIT))
                                    : resolveAndCreate(channel, normalized, contactHash, ipHash));
                });
    }

    /**
     * 校验验证码并颁发一次性确认令牌。
     *
     * <p>验证码原子消费，一次有效；失败计入失败次数，达到上限后恢复单作废。
     * 所有无效场景返回统一的通用错误，不区分具体原因。</p>
     *
     * @param request 验证码校验请求（恢复单ID与验证码）
     * @return 校验响应，包含一次性确认令牌及其有效期
     */
    public Mono<PasswordRecoveryVerifyResponse> verify(PasswordRecoveryVerifyRequest request) {
        String recoveryId = trim(request.getRecoveryId());
        String code = trim(request.getCode());
        return recoveryRepository
                .findById(recoveryId)
                .switchIfEmpty(Mono.error(genericInvalid()))
                .flatMap(entity -> {
                    if (!PasswordRecoveryEntity.STATUS_REQUESTED.equals(entity.getStatus())
                            || entity.getExpiresAt() == null
                            || entity.getExpiresAt().isBefore(LocalDateTime.now())) {
                        return Mono.error(genericInvalid());
                    }
                    return credentialStore
                            .verifyAndConsumeCode(recoveryId, sha256(code))
                            .flatMap(matched -> {
                                if (!matched) {
                                    return onVerifyFailure(entity);
                                }
                                String token = generateSecureToken();
                                return credentialStore
                                        .storeConfirmToken(recoveryId, sha256(token), CONFIRM_TTL)
                                        .then(recoveryRepository.transition(
                                                recoveryId,
                                                PasswordRecoveryEntity.STATUS_REQUESTED,
                                                PasswordRecoveryEntity.STATUS_VERIFIED))
                                        .flatMap(won -> {
                                            if (!won) {
                                                return Mono.error(genericInvalid());
                                            }
                                            return Mono.just(
                                                    new PasswordRecoveryVerifyResponse(token, CONFIRM_TTL.toSeconds()));
                                        });
                            });
                });
    }

    /**
     * 确认重设密码并撤销全部会话。
     *
     * <p>确认令牌原子消费保证并发下最多一次成功；密码重设与恢复单终结均使用条件更新；
     * 撤销失败时返回 {@code sessionRevocation=PENDING}，不伪称全端退出。</p>
     *
     * @param request 确认重设请求（恢复单ID、确认令牌与新密码）
     * @return 确认响应，包含重设状态与会话撤销状态
     */
    public Mono<PasswordRecoveryConfirmResponse> confirm(PasswordRecoveryConfirmRequest request) {
        String recoveryId = trim(request.getRecoveryId());
        String confirmToken = trim(request.getConfirmToken());
        String newPassword = request.getNewPassword() == null ? "" : request.getNewPassword();
        return recoveryRepository
                .findById(recoveryId)
                .switchIfEmpty(Mono.error(genericInvalid()))
                .flatMap(entity -> {
                    if (!PasswordRecoveryEntity.STATUS_VERIFIED.equals(entity.getStatus())
                            || entity.getUserId() == null
                            || entity.getTenantId() == null) {
                        return Mono.error(genericInvalid());
                    }
                    return credentialStore
                            .consumeConfirmToken(recoveryId, sha256(confirmToken))
                            .flatMap(matched -> {
                                if (!matched) {
                                    return Mono.error(genericInvalid());
                                }
                                return validatePasswordPolicy(newPassword, entity.getUsername())
                                        .flatMap(policy -> resetPasswordAndRevoke(entity, newPassword));
                            });
                });
    }

    private Mono<PasswordPolicyService.PasswordValidationResult> validatePasswordPolicy(
            String newPassword, String username) {
        return passwordPolicyService
                .validatePassword(newPassword, username, false)
                .flatMap(result -> {
                    if (!result.isPassed()) {
                        List<String> errors = result.getErrors() == null ? List.of() : result.getErrors();
                        return Mono.error(new BusinessException(
                                ResultCode.PARAM_INVALID,
                                message("auth.password_recovery.password_policy_failed", String.join("; ", errors))));
                    }
                    return Mono.just(result);
                });
    }

    private Mono<PasswordRecoveryConfirmResponse> resetPasswordAndRevoke(
            PasswordRecoveryEntity entity, String newPassword) {
        // BCrypt 为 CPU 密集的同步调用，必须延迟到订阅后并在阻塞池执行，
        // 避免在 Redis/事件循环线程上直接编码密码。
        return Mono.fromCallable(() -> passwordEncoder.encode(newPassword))
                .subscribeOn(com.apex.scaffold.common.scheduler.BlockingIoSchedulers.general())
                .flatMap(encoded -> accountDirectory
                        .updatePasswordHash(entity.getUserId(), entity.getTenantId(), encoded)
                        .flatMap(updated -> {
                            if (!updated) {
                                return Mono.error(genericInvalid());
                            }
                            return recoveryRepository.transition(
                                    entity.getId(),
                                    PasswordRecoveryEntity.STATUS_VERIFIED,
                                    PasswordRecoveryEntity.STATUS_CONSUMED);
                        })
                        .flatMap(consumed -> {
                            if (!consumed) {
                                // 并发的另一个确认请求已完成重设
                                return Mono.error(genericInvalid());
                            }
                            return tokenService
                                    .revokeAllUserTokens(entity.getUserId(), entity.getTenantId())
                                    .thenReturn(Boolean.TRUE)
                                    .onErrorResume(ex -> {
                                        log.error(
                                                "密码重设后撤销会话失败，撤销仍在处理: recoveryId={}, userId={}",
                                                entity.getId(),
                                                entity.getUserId(),
                                                ex);
                                        return Mono.just(Boolean.FALSE);
                                    })
                                    .flatMap(revoked -> {
                                        PasswordRecoveryConfirmResponse response = new PasswordRecoveryConfirmResponse(
                                                CONFIRM_STATUS_PASSWORD_RESET, revoked ? "COMPLETED" : "PENDING");
                                        // publish() 是惰性 Mono，必须组合进主链才会真正写入 Outbox；
                                        // 撤销已完成时事件失败不改变响应，但需记录。
                                        return publishRevokedEventSafely(entity, response)
                                                .thenReturn(response);
                                    })
                                    .contextWrite(TenantContext.withTenant(entity.getTenantId()));
                        }));
    }

    /**
     * 发布撤销事件；仅在会话撤销完成时发布，失败不改变已完成的密码重设结果。
     *
     * @param entity   恢复单
     * @param response 确认响应
     * @return 发布完成信号；未完成撤销或发布失败时为空完成
     */
    private Mono<Void> publishRevokedEventSafely(
            PasswordRecoveryEntity entity, PasswordRecoveryConfirmResponse response) {
        if (!"COMPLETED".equals(response.getSessionRevocation())) {
            return Mono.empty();
        }
        return Mono.defer(() -> domainEventPublisher.publish(new com.apex.scaffold.domain.event.TokenRevokedEvent(
                        entity.getUserId(), entity.getTenantId(), REASON_PASSWORD_RESET)))
                .onErrorResume(ex -> {
                    log.error("发布撤销事件失败: recoveryId={}, userId={}", entity.getId(), entity.getUserId(), ex);
                    return Mono.empty();
                });
    }

    private Mono<PasswordRecoveryVerifyResponse> onVerifyFailure(PasswordRecoveryEntity entity) {
        return recoveryRepository.incrementAttemptCount(entity.getId()).flatMap(attempts -> {
            if (attempts >= MAX_VERIFY_ATTEMPTS) {
                return recoveryRepository
                        .transition(
                                entity.getId(),
                                PasswordRecoveryEntity.STATUS_REQUESTED,
                                PasswordRecoveryEntity.STATUS_EXPIRED)
                        .then(Mono.error(genericInvalid()));
            }
            return Mono.error(genericInvalid());
        });
    }

    private Mono<PasswordRecoveryInitiateResponse> resolveAndCreate(
            String channel, String normalizedContact, String contactHash, String ipHash) {
        Mono<PasswordRecoveryAccountDirectory.Resolution> resolution =
                PasswordRecoveryEntity.CHANNEL_EMAIL.equals(channel)
                        ? accountDirectory.resolveByEmail(normalizedContact)
                        : accountDirectory.resolveByPhone(normalizedContact);
        return resolution.flatMap(res -> {
            if (PasswordRecoveryAccountDirectory.Resolution.KIND_NONE.equals(res.kind())) {
                // 账号不存在：返回同形态占位恢复单，不落库、不发码，防止账号枚举
                return Mono.just(
                        new PasswordRecoveryInitiateResponse(UUID.randomUUID().toString(), CODE_TTL.toSeconds()));
            }
            if (PasswordRecoveryAccountDirectory.Resolution.KIND_MULTIPLE.equals(res.kind())) {
                // 命中多个账号：落受审计的人工恢复单，不发码
                PasswordRecoveryEntity manual = PasswordRecoveryEntity.builder()
                        .id(UUID.randomUUID().toString())
                        .channel(channel)
                        .contactHash(contactHash)
                        .status(PasswordRecoveryEntity.STATUS_MANUAL_REQUIRED)
                        .attemptCount(0)
                        .requestIpHash(ipHash)
                        .expiresAt(LocalDateTime.now().plus(CODE_TTL))
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build();
                return recoveryRepository
                        .save(manual)
                        .map(saved -> new PasswordRecoveryInitiateResponse(saved.getId(), CODE_TTL.toSeconds()));
            }
            return createAndSendChallenge(channel, normalizedContact, contactHash, ipHash, res);
        });
    }

    private Mono<PasswordRecoveryInitiateResponse> createAndSendChallenge(
            String channel,
            String normalizedContact,
            String contactHash,
            String ipHash,
            PasswordRecoveryAccountDirectory.Resolution resolution) {
        PasswordRecoveryEntity entity = PasswordRecoveryEntity.builder()
                .id(UUID.randomUUID().toString())
                .userId(resolution.userId())
                .tenantId(resolution.tenantId())
                .username(resolution.username())
                .channel(channel)
                .contactHash(contactHash)
                .status(PasswordRecoveryEntity.STATUS_REQUESTED)
                .attemptCount(0)
                .requestIpHash(ipHash)
                .expiresAt(LocalDateTime.now().plus(CODE_TTL))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        return recoveryRepository.save(entity).flatMap(saved -> {
            String code = generateNumericCode();
            return credentialStore
                    .storeCode(saved.getId(), sha256(code), CODE_TTL)
                    .then(challengeSender.send(channel, normalizedContact, code))
                    .thenReturn(new PasswordRecoveryInitiateResponse(saved.getId(), CODE_TTL.toSeconds()));
        });
    }

    private void validateChannelAndContact(String channel, String contact) {
        if (PasswordRecoveryEntity.CHANNEL_SMS.equals(channel)) {
            if (!PHONE_PATTERN.matcher(contact).matches()) {
                throw new BusinessException(
                        ResultCode.PARAM_INVALID, message("auth.password_recovery.contact.phone_invalid"));
            }
            return;
        }
        if (!PasswordRecoveryEntity.CHANNEL_EMAIL.equals(channel)) {
            throw new BusinessException(
                    ResultCode.PARAM_INVALID, message("auth.password_recovery.channel.unsupported"));
        }
        if (!EMAIL_PATTERN.matcher(contact).matches()) {
            throw new BusinessException(
                    ResultCode.PARAM_INVALID, message("auth.password_recovery.contact.email_invalid"));
        }
    }

    private BusinessException genericInvalid() {
        return new BusinessException(ResultCode.UNAUTHORIZED, message("auth.password_recovery.request_invalid"));
    }

    /**
     * 解析用户可见文案。
     *
     * <p>恢复链路是匿名入口且跨线程执行，不携带请求 Locale，统一使用简体中文；
     * 资源缺失时返回 key 本身而不是空串。</p>
     *
     * @param code 消息键
     * @param args 消息模板参数
     * @return 已格式化的文案
     */
    private String message(String code, Object... args) {
        return messageSource.getMessage(code, args, code, Locale.SIMPLIFIED_CHINESE);
    }

    private String generateNumericCode() {
        return String.format("%0" + CODE_LENGTH + "d", secureRandom.nextInt(1_000_000));
    }

    private String generateSecureToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 摘要算法不可用", ex);
        }
    }
}
