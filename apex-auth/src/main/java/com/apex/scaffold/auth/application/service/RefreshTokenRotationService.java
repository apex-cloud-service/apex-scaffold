package com.apex.scaffold.auth.application.service;

import com.apex.scaffold.auth.domain.model.entity.TokenEntity;
import com.apex.scaffold.auth.domain.model.vo.TokenPair;
import com.apex.scaffold.auth.domain.repository.TokenRepository;
import com.apex.scaffold.auth.domain.repository.UserRepository;
import com.apex.scaffold.auth.domain.service.JwtKeyMaterialService;
import com.apex.scaffold.auth.domain.service.TokenEncryptionService;
import com.apex.scaffold.common.audit.SecurityAuditService;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import io.jsonwebtoken.JwtException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Refresh Token轮换服务
 *
 * <p>实现Token Family机制，防止Refresh Token泄露和重放攻击
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li><b>Token Family</b>: 每次登录创建新Family，轮换时继承Family ID</li>
 *   <li><b>一次性使用</b>: 每个Refresh Token只能使用一次，使用后立即标记</li>
 *   <li><b>重放检测</b>: 若检测到已使用的Token被重用，撤销整个Family</li>
 *   <li><b>异常检测</b>: 监控IP变化、快速刷新等异常模式</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <pre>
 * 1. 验证Refresh Token有效性
 * 2. 检查Token Family是否已被撤销（重放攻击检测）
 * 3. 使用Redis SETNX原子标记Token为已使用
 * 4. 若Token已被使用过，立即撤销整个Family并告警
 * 5. 生成新Token对，继承Family ID
 * 6. 撤销旧Refresh Token
 * 7. 记录轮换历史用于审计
 * </pre>
 *
 * @author Apex Security Team
 * @since 1.3.1
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenRotationService {

    private static final String TOKEN_USED_KEY_PREFIX = "token:used:";
    private static final String FAMILY_REVOKED_KEY_PREFIX = "token:family:revoked:";
    private static final Duration TOKEN_USED_TTL = Duration.ofMinutes(10);
    private static final Duration FAMILY_REVOKED_TTL = Duration.ofDays(7);

    private static final long ACCESS_TOKEN_EXPIRE_MS = 2 * 60 * 60 * 1000L;
    private static final long REFRESH_TOKEN_EXPIRE_MS = 7 * 24 * 60 * 60 * 1000L;

    private static final String TOKEN_TYPE_REFRESH = "REFRESH";

    private final TokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final TokenEncryptionService encryptionService;
    private final SecurityAuditService auditService;
    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final JwtKeyMaterialService jwtKeyMaterialService;

    /**
     * 轮换Refresh Token
     *
     * @param oldRefreshToken 旧的Refresh Token
     * @return 新的TokenPair（包含新的Access Token和Refresh Token）
     */
    @SuppressWarnings("PMD.MethodTooLongRule")
    public Mono<TokenPair> rotateRefreshToken(String oldRefreshToken) {
        log.debug("开始Refresh Token轮换");

        // 使用 RSA/RS256 公钥验签并解析刷新令牌（Phase 2.1 迁移）
        return jwtKeyMaterialService
                .parse(oldRefreshToken)
                .onErrorMap(
                        exception -> exception instanceof JwtException || exception instanceof IllegalArgumentException,
                        exception -> {
                            log.warn("Token解析失败: type={}", exception.getClass().getSimpleName());
                            return new BusinessException(ResultCode.UNAUTHORIZED, "Token无效");
                        })
                .flatMap(claims -> {
                    // 1. 解析声明并校验令牌类型
                    String jti = claims.get("jti", String.class);
                    Long userId = claims.get("userId", Long.class);
                    String tenantId = claims.get("tenantId", String.class);
                    String tokenType = claims.get("tokenType", String.class);

                    if (!TOKEN_TYPE_REFRESH.equals(tokenType)) {
                        return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "必须使用Refresh Token"));
                    }

                    // 2. 从数据库获取Token实体
                    return tokenRepository
                            .findByJti(jti)
                            .switchIfEmpty(Mono.error(new BusinessException(ResultCode.UNAUTHORIZED, "Token不存在")))
                            .flatMap(oldTokenEntity -> {
                                // 3. 验证Token状态
                                if (oldTokenEntity.isRevoked()) {
                                    log.debug("尝试使用已撤销的Token: userId={}", userId);
                                    return Mono.error(new BusinessException(ResultCode.UNAUTHORIZED, "Token已撤销"));
                                }

                                if (oldTokenEntity.isExpired()) {
                                    return Mono.error(new BusinessException(ResultCode.UNAUTHORIZED, "Token已过期"));
                                }

                                String tokenFamily = oldTokenEntity.getTokenFamily();
                                if (tokenFamily == null || tokenFamily.isEmpty()) {
                                    // 兼容旧Token，创建新Family
                                    tokenFamily = UUID.randomUUID().toString();
                                    log.debug("为旧Token创建Family: userId={}", userId);
                                }

                                String finalTokenFamily = tokenFamily;

                                // 4. 检查Family是否已被撤销（重放攻击检测）
                                return checkFamilyRevoked(finalTokenFamily).flatMap(isRevoked -> {
                                    if (isRevoked) {
                                        log.debug("检测到Token Family重放攻击: userId={}", userId);

                                        return auditService
                                                .logSecurityEvent(
                                                        "TOKEN_FAMILY_REUSE",
                                                        String.format("检测到Token Family %s 重放攻击", finalTokenFamily),
                                                        userId,
                                                        null,
                                                        tenantId,
                                                        oldTokenEntity.getClientIp())
                                                .then(Mono.error(new BusinessException(
                                                        ResultCode.UNAUTHORIZED, "检测到安全威胁，Token Family已被撤销，请重新登录")));
                                    }

                                    // 5. 原子标记Token为已使用（使用Redis SETNX）
                                    return markTokenAsUsed(jti).flatMap(isFirstUse -> {
                                        if (!isFirstUse) {
                                            // Token被并发使用或重放
                                            log.debug("检测到Token重用: userId={}", userId);

                                            // 撤销整个Token Family
                                            return revokeTokenFamily(finalTokenFamily, userId, tenantId)
                                                    .then(auditService.logSecurityEvent(
                                                            "TOKEN_REUSE",
                                                            String.format("检测到Token重用，已撤销Family %s", finalTokenFamily),
                                                            userId,
                                                            null,
                                                            tenantId,
                                                            oldTokenEntity.getClientIp()))
                                                    .then(Mono.error(new BusinessException(
                                                            ResultCode.UNAUTHORIZED, "检测到Token重用，所有会话已撤销，请重新登录")));
                                        }

                                        // 6. 生成新的Token对（继承Token Family）
                                        return issueRotatedToken(oldTokenEntity, finalTokenFamily)
                                                .flatMap(newTokenPair -> {
                                                    // 7. 撤销旧Token
                                                    return revokeOldToken(oldTokenEntity)
                                                            .then(auditService.logTokenRotation(
                                                                    userId,
                                                                    tenantId,
                                                                    finalTokenFamily,
                                                                    jti,
                                                                    newTokenPair.getRefreshTokenJti(),
                                                                    oldTokenEntity.getClientIp()))
                                                            .thenReturn(newTokenPair);
                                                });
                                    });
                                });
                            })
                            .doOnSuccess(pair -> log.debug("Token轮换成功"))
                            .doOnError(e ->
                                    log.debug("Token轮换失败: type={}", e.getClass().getSimpleName()));
                });
    }

    /**
     * 检查Token Family是否已被撤销
     */
    private Mono<Boolean> checkFamilyRevoked(String tokenFamily) {
        String key = FAMILY_REVOKED_KEY_PREFIX + tokenFamily;
        return redisTemplate.hasKey(key);
    }

    /**
     * 原子标记Token为已使用
     * 使用Redis SETNX确保只能标记一次
     *
     * @return true表示首次使用，false表示Token已被使用过
     */
    private Mono<Boolean> markTokenAsUsed(String jti) {
        String key = TOKEN_USED_KEY_PREFIX + jti;
        return redisTemplate
                .opsForValue()
                .setIfAbsent(key, "used", TOKEN_USED_TTL)
                .defaultIfEmpty(false);
    }

    /**
     * 撤销整个Token Family
     * 当检测到Token重用时调用
     */
    private Mono<Void> revokeTokenFamily(String tokenFamily, Long userId, String tenantId) {
        log.debug("撤销Token Family: userId={}", userId);

        // 1. 在Redis中标记Family为已撤销
        String key = FAMILY_REVOKED_KEY_PREFIX + tokenFamily;
        return redisTemplate
                .opsForValue()
                .set(key, "revoked", FAMILY_REVOKED_TTL)
                .then(
                        // 2. 撤销数据库中该Family的所有Token
                        tokenRepository.revokeByTokenFamily(
                                tokenFamily, String.valueOf(userId), "token_reuse_detected"))
                .then();
    }

    /**
     * 签发轮换后的新Token
     * 继承Token Family ID
     */
    @SuppressWarnings("PMD.MethodTooLongRule")
    private Mono<TokenPair> issueRotatedToken(TokenEntity oldToken, String tokenFamily) {
        String newAccessJti = UUID.randomUUID().toString();
        String newRefreshJti = UUID.randomUUID().toString();

        Long userId = oldToken.getUserId();
        String tenantId = oldToken.getTenantId();
        String deviceId = oldToken.getDeviceId();
        String clientIp = oldToken.getClientIp();

        return userRepository
                .findById(userId)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "用户不存在")))
                .flatMap(user -> {
                    // 组织JWT声明
                    Map<String, Object> accessClaims = new HashMap<>(16);
                    accessClaims.put("jti", newAccessJti);
                    accessClaims.put("userId", userId);
                    accessClaims.put("tenantId", tenantId);
                    accessClaims.put("tokenType", "ACCESS");
                    accessClaims.put("deviceId", deviceId);

                    Map<String, Object> refreshClaims = new HashMap<>(16);
                    refreshClaims.put("jti", newRefreshJti);
                    refreshClaims.put("userId", userId);
                    refreshClaims.put("tenantId", tenantId);
                    refreshClaims.put("tokenType", "REFRESH");
                    refreshClaims.put("deviceId", deviceId);

                    // 使用 RSA/RS256 非对称密钥签发新令牌（Phase 2.1 迁移）
                    return jwtKeyMaterialService
                            .sign(String.valueOf(userId), accessClaims, ACCESS_TOKEN_EXPIRE_MS)
                            .zipWith(jwtKeyMaterialService.sign(
                                    String.valueOf(userId), refreshClaims, REFRESH_TOKEN_EXPIRE_MS))
                            .flatMap(signedTokens -> {
                                String accessToken = signedTokens.getT1();
                                String refreshToken = signedTokens.getT2();

                                // 加密Token
                                TokenEncryptionService.EncryptedToken encryptedAccess =
                                        encryptionService.encrypt(accessToken);
                                TokenEncryptionService.EncryptedToken encryptedRefresh =
                                        encryptionService.encrypt(refreshToken);

                                LocalDateTime now = LocalDateTime.now();

                                // 创建新Token实体（继承Token Family）
                                TokenEntity newAccessEntity = TokenEntity.builder()
                                        .tenantId(tenantId)
                                        .userId(userId)
                                        .tokenType("ACCESS")
                                        .tokenValue(encryptedAccess.encryptedData())
                                        .tokenHash(encryptionService.hashToken(accessToken))
                                        .tokenIv(encryptedAccess.iv())
                                        .jti(newAccessJti)
                                        .expiresAt(now.plusSeconds(ACCESS_TOKEN_EXPIRE_MS / 1000))
                                        .deviceId(deviceId)
                                        .clientIp(clientIp)
                                        .tokenFamily(tokenFamily) // 继承Family ID
                                        .previousJti(oldToken.getJti()) // 记录前一个Token
                                        .build();

                                TokenEntity newRefreshEntity = TokenEntity.builder()
                                        .tenantId(tenantId)
                                        .userId(userId)
                                        .tokenType("REFRESH")
                                        .tokenValue(encryptedRefresh.encryptedData())
                                        .tokenHash(encryptionService.hashToken(refreshToken))
                                        .tokenIv(encryptedRefresh.iv())
                                        .jti(newRefreshJti)
                                        .expiresAt(now.plusSeconds(REFRESH_TOKEN_EXPIRE_MS / 1000))
                                        .deviceId(deviceId)
                                        .clientIp(clientIp)
                                        .tokenFamily(tokenFamily) // 继承Family ID
                                        .previousJti(oldToken.getJti()) // 记录前一个Token
                                        .rotationCount(oldToken.getRotationCount() + 1)
                                        .build();

                                // 保存新Token
                                return tokenRepository
                                        .save(newAccessEntity)
                                        .then(tokenRepository.save(newRefreshEntity))
                                        .thenReturn(TokenPair.builder()
                                                .accessToken(accessToken)
                                                .refreshToken(refreshToken)
                                                .refreshTokenJti(newRefreshJti) // 用于审计
                                                .expiresIn(ACCESS_TOKEN_EXPIRE_MS / 1000)
                                                .tokenType("Bearer")
                                                .build());
                            });
                });
    }

    /**
     * 撤销旧Token
     */
    private Mono<Void> revokeOldToken(TokenEntity oldToken) {
        oldToken.setRevoked(1);
        oldToken.setRevokedAt(LocalDateTime.now());
        oldToken.setRevokedBy("system");
        oldToken.setRevokeReason("token_rotated");
        return tokenRepository.update(oldToken).then();
    }
}
