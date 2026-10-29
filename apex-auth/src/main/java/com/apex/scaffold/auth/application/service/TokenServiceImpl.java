package com.apex.scaffold.auth.application.service;

import com.apex.scaffold.auth.domain.model.entity.TokenEntity;
import com.apex.scaffold.auth.domain.model.vo.TokenPair;
import com.apex.scaffold.auth.domain.repository.TokenRepository;
import com.apex.scaffold.auth.domain.repository.UserRepository;
import com.apex.scaffold.auth.domain.service.JwtKeyMaterialService;
import com.apex.scaffold.auth.domain.service.TokenEncryptionService;
import com.apex.scaffold.auth.domain.service.TokenService;
import com.apex.scaffold.common.audit.SecurityAuditService;
import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Token服务实现类
 *
 * <p>基于JWT实现Token的签发、验证、刷新和撤销功能。
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>签发Token对：访问令牌（2小时）+ 刷新令牌（7天）</li>
 *   <li>验证Token：签名验证、过期检测、撤销检查</li>
 *   <li>刷新Token：使用刷新令牌换取新的访问令牌</li>
 *   <li>撤销Token：单个撤销或批量撤销用户所有Token</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <pre>{@code
 * {
 *   "jti": "唯一Token ID",
 *   "userId": "用户ID",
 *   "tenantId": "租户ID",
 *   "tokenType": "ACCESS/REFRESH",
 *   "deviceId": "设备ID",
 *   "iat": "签发时间",
 *   "exp": "过期时间"
 * }
 * }</pre>
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>所有Token持久化到数据库，支持撤销和追踪</li>
 *   <li>验证时检查数据库撤销状态，已撤销Token拒绝访问</li>
 *   <li>刷新令牌不轮换，使用时不生成新的刷新令牌</li>
 *   <li>租户隔离：Token只能在签发租户内使用</li>
 * </ul>
 *
 * @author Apex Team
 * @since 1.3.1
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenServiceImpl implements TokenService {

    private static final int CLAIMS_INITIAL_CAPACITY = 8;
    private static final String REFRESH_TOKEN_TYPE = "REFRESH";

    private final TokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final TokenEncryptionService encryptionService;
    private final SecurityAuditService auditService;
    private final JwtKeyMaterialService jwtKeyMaterialService;

    private static final long ACCESS_TOKEN_EXPIRE_MS = 2 * 60 * 60 * 1000L;
    private static final long REFRESH_TOKEN_EXPIRE_MS = 7 * 24 * 60 * 60 * 1000L;

    @Override
    @SuppressWarnings("PMD.MethodTooLongRule")
    public Mono<TokenPair> issueToken(Long userId, String tenantId, String deviceId, String clientIp) {
        return issueToken(userId, tenantId, deviceId, clientIp, null);
    }

    @Override
    @SuppressWarnings("PMD.MethodTooLongRule")
    public Mono<TokenPair> issueToken(
            Long userId, String tenantId, String deviceId, String clientIp, String clientType) {
        log.debug("开始签发Token: userId={}, clientType={}", userId, clientType);

        return userRepository
                .findById(userId)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "用户不存在")))
                .flatMap(user -> {
                    if (!tenantId.equals(user.getTenantId())) {
                        return Mono.error(new BusinessException(ResultCode.FORBIDDEN, "租户不匹配"));
                    }
                    if (!user.isActive()) {
                        return Mono.error(new BusinessException(ResultCode.FORBIDDEN, "账户已禁用"));
                    }
                    if (user.isLocked()) {
                        return Mono.error(new BusinessException(ResultCode.FORBIDDEN, "账户已锁定"));
                    }

                    String accessJti = UUID.randomUUID().toString();
                    String refreshJti = UUID.randomUUID().toString();

                    Map<String, Object> accessClaims =
                            buildClaims(accessJti, userId, tenantId, "ACCESS", deviceId, user, clientType);
                    Map<String, Object> refreshClaims =
                            buildClaims(refreshJti, userId, tenantId, REFRESH_TOKEN_TYPE, deviceId, user, clientType);

                    // 使用 RSA/RS256 非对称密钥签发访问令牌与刷新令牌（Phase 2.1 迁移）
                    return jwtKeyMaterialService
                            .sign(String.valueOf(userId), accessClaims, ACCESS_TOKEN_EXPIRE_MS)
                            .zipWith(jwtKeyMaterialService.sign(
                                    String.valueOf(userId), refreshClaims, REFRESH_TOKEN_EXPIRE_MS))
                            .flatMap(signedTokens -> {
                                String accessToken = signedTokens.getT1();
                                String refreshToken = signedTokens.getT2();

                                // 加密Token并计算哈希
                                TokenEncryptionService.EncryptedToken encryptedAccess =
                                        encryptionService.encrypt(accessToken);
                                TokenEncryptionService.EncryptedToken encryptedRefresh =
                                        encryptionService.encrypt(refreshToken);
                                String accessTokenHash = encryptionService.hashToken(accessToken);
                                String refreshTokenHash = encryptionService.hashToken(refreshToken);

                                // 生成Token Family ID用于轮换检测
                                String tokenFamily = UUID.randomUUID().toString();

                                LocalDateTime now = LocalDateTime.now();
                                TokenEntity accessTokenEntity = TokenEntity.builder()
                                        .tenantId(tenantId)
                                        .userId(userId)
                                        .tokenType("ACCESS")
                                        .tokenValue(encryptedAccess.encryptedData())
                                        .tokenHash(accessTokenHash)
                                        .tokenIv(encryptedAccess.iv())
                                        .jti(accessJti)
                                        .expiresAt(now.plusSeconds(ACCESS_TOKEN_EXPIRE_MS / 1000))
                                        .deviceId(deviceId)
                                        .clientIp(clientIp)
                                        .tokenFamily(tokenFamily)
                                        .build();

                                TokenEntity refreshTokenEntity = TokenEntity.builder()
                                        .tenantId(tenantId)
                                        .userId(userId)
                                        .tokenType(REFRESH_TOKEN_TYPE)
                                        .tokenValue(encryptedRefresh.encryptedData())
                                        .tokenHash(refreshTokenHash)
                                        .tokenIv(encryptedRefresh.iv())
                                        .jti(refreshJti)
                                        .expiresAt(now.plusSeconds(REFRESH_TOKEN_EXPIRE_MS / 1000))
                                        .deviceId(deviceId)
                                        .clientIp(clientIp)
                                        .tokenFamily(tokenFamily)
                                        .build();

                                return tokenRepository
                                        .save(accessTokenEntity)
                                        .then(tokenRepository.save(refreshTokenEntity))
                                        .flatMap(savedRefreshToken -> auditService
                                                .logTokenIssued(userId, tenantId, clientIp, deviceId, "ACCESS+REFRESH")
                                                .thenReturn(TokenPair.builder()
                                                        .accessToken(accessToken)
                                                        .refreshToken(refreshToken)
                                                        .expiresIn(ACCESS_TOKEN_EXPIRE_MS / 1000)
                                                        .tokenType("Bearer")
                                                        .build()));
                            });
                })
                .doOnSuccess(pair -> log.debug("Token签发成功: userId={}", userId));
    }

    /**
     * 构建 Token claims（统一账户模型）。
     *
     * <p>除既有 jti/userId/tenantId/tokenType/deviceId 外，追加统一账户字段：
     * {@code accountId}（主应用 sys_user.id，未映射时不写入）、{@code username}、
     * {@code clientType}（客户端类型编码，调用方未上报时不写入），供主应用
     * 装配统一身份与按终端审计。
     *
     * @param jti        Token 唯一ID
     * @param userId     认证中心用户ID
     * @param tenantId   租户ID
     * @param tokenType  令牌类型（ACCESS/REFRESH）
     * @param deviceId   设备ID
     * @param user       用户实体（携带统一账户映射）
     * @param clientType 客户端类型编码，可为空
     * @return claims Map
     */
    private Map<String, Object> buildClaims(
            String jti,
            Long userId,
            String tenantId,
            String tokenType,
            String deviceId,
            com.apex.scaffold.auth.domain.model.entity.UserEntity user,
            String clientType) {
        Map<String, Object> claims = new HashMap<>(CLAIMS_INITIAL_CAPACITY);
        claims.put("jti", jti);
        claims.put("userId", userId);
        claims.put("tenantId", tenantId);
        claims.put("tokenType", tokenType);
        claims.put("deviceId", deviceId);
        if (user.getAccountId() != null) {
            claims.put("accountId", user.getAccountId());
        }
        if (user.getUsername() != null && !user.getUsername().isBlank()) {
            claims.put("username", user.getUsername());
        }
        if (clientType != null && !clientType.isBlank()) {
            claims.put("clientType", clientType);
        }
        return claims;
    }

    @Override
    public Mono<TokenEntity> validateToken(String token) {
        return jwtKeyMaterialService
                .parse(token)
                .onErrorMap(ExpiredJwtException.class, exception -> {
                    log.debug("Token已过期");
                    return new BusinessException(ResultCode.UNAUTHORIZED, "Token已过期");
                })
                .onErrorMap(
                        exception -> exception instanceof JwtException || exception instanceof IllegalArgumentException,
                        exception -> {
                            log.debug("Token验证失败");
                            return new BusinessException(ResultCode.UNAUTHORIZED, "Token无效");
                        })
                .flatMap(claims -> {
                    String jti = claims.get("jti", String.class);
                    Long userId = claims.get("userId", Long.class);
                    String tenantId = claims.get("tenantId", String.class);

                    if (jti == null || jti.isBlank() || userId == null || tenantId == null || tenantId.isBlank()) {
                        return Mono.error(new BusinessException(ResultCode.UNAUTHORIZED, "Token声明不完整"));
                    }

                    return Mono.deferContextual(context -> {
                        String existingTenant = context.getOrDefault(TenantContext.REACTOR_CONTEXT_KEY, null);
                        if (existingTenant != null && !existingTenant.equals(tenantId)) {
                            return Mono.error(new BusinessException(ResultCode.FORBIDDEN));
                        }
                        return tokenRepository
                                .findByJti(jti)
                                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.UNAUTHORIZED, "Token不存在")))
                                .flatMap(tokenEntity -> {
                                    if (!Objects.equals(tokenEntity.getTenantId(), tenantId)
                                            || !Objects.equals(tokenEntity.getUserId(), userId)
                                            || !Objects.equals(tokenEntity.getJti(), jti)
                                            || !Objects.equals(
                                                    tokenEntity.getTokenType(),
                                                    claims.get("tokenType", String.class))) {
                                        return Mono.error(new BusinessException(ResultCode.UNAUTHORIZED));
                                    }
                                    if (tokenEntity.isRevoked()) {
                                        return Mono.error(new BusinessException(ResultCode.UNAUTHORIZED, "Token已撤销"));
                                    }
                                    if (tokenEntity.isExpired()) {
                                        return Mono.error(new BusinessException(ResultCode.UNAUTHORIZED, "Token已过期"));
                                    }
                                    if (!tokenEntity.isValid()) {
                                        return Mono.error(new BusinessException(ResultCode.UNAUTHORIZED, "Token无效"));
                                    }
                                    return Mono.just(tokenEntity);
                                })
                                .transformDeferred(mono -> TenantContext.withTenant(mono))
                                .contextWrite(TenantContext.withTenant(tenantId));
                    });
                });
    }

    @Override
    public Mono<TokenPair> refreshToken(String refreshToken) {
        return validateToken(refreshToken)
                .flatMap(tokenEntity -> {
                    if (!REFRESH_TOKEN_TYPE.equals(tokenEntity.getTokenType())) {
                        return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "必须使用刷新令牌"));
                    }

                    // 检测Token重放攻击：如果Token已被撤销，说明可能是重放攻击
                    if (tokenEntity.isRevoked()) {
                        log.warn("检测到可疑的Token刷新尝试: userId={}", tokenEntity.getUserId());

                        // 记录Token重放攻击审计日志
                        return auditService
                                .logTokenReplayAttack(
                                        tokenEntity.getUserId(),
                                        tokenEntity.getTenantId(),
                                        tokenEntity.getClientIp(),
                                        tokenEntity.getJti())
                                .then(tokenRepository.revokeByUserId(
                                        tokenEntity.getUserId(), tokenEntity.getTenantId()))
                                .then(Mono.error(
                                        new BusinessException(ResultCode.UNAUTHORIZED, "检测到安全威胁，已撤销所有会话，请重新登录")));
                    }

                    // 实施Token轮换：撤销旧的refresh token
                    return tokenRepository
                            .revokeByJti(tokenEntity.getJti(), "system", "token_refresh")
                            .flatMap(revoked -> {
                                if (!revoked) {
                                    log.warn("Token轮换失败: 无法撤销旧token");
                                    return Mono.error(new BusinessException(ResultCode.ERROR, "Token刷新失败"));
                                }

                                // 签发新的Token对（包含新的refresh token）
                                return issueToken(
                                                tokenEntity.getUserId(),
                                                tokenEntity.getTenantId(),
                                                tokenEntity.getDeviceId(),
                                                tokenEntity.getClientIp())
                                        .flatMap(newTokenPair -> auditService
                                                .logTokenRefreshed(
                                                        tokenEntity.getUserId(),
                                                        tokenEntity.getTenantId(),
                                                        tokenEntity.getClientIp(),
                                                        tokenEntity.getJti())
                                                .thenReturn(newTokenPair));
                            })
                            .transformDeferred(mono -> TenantContext.withTenant(mono))
                            .contextWrite(TenantContext.withTenant(tokenEntity.getTenantId()));
                })
                .doOnSuccess(pair -> log.debug("Token刷新成功，已轮换refresh token"));
    }

    @Override
    public Mono<Void> revokeToken(String token) {
        return jwtKeyMaterialService
                .parse(token)
                .onErrorMap(
                        exception -> exception instanceof JwtException || exception instanceof IllegalArgumentException,
                        exception -> {
                            log.debug("Token无效");
                            return new BusinessException(ResultCode.UNAUTHORIZED, "Token无效");
                        })
                .flatMap(claims -> {
                    String jti = claims.get("jti", String.class);

                    if (jti == null || jti.isBlank()) {
                        return Mono.error(new BusinessException(ResultCode.UNAUTHORIZED, "Token格式错误"));
                    }

                    return tokenRepository
                            .findByJti(jti)
                            .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "Token不存在")))
                            .flatMap(tokenEntity -> {
                                // 记录撤销原因和操作人
                                tokenEntity.revoke("user:" + tokenEntity.getUserId(), "manual");
                                return tokenRepository
                                        .update(tokenEntity)
                                        .then(auditService.logTokenRevoked(
                                                tokenEntity.getUserId(),
                                                null, // username需要从UserService获取
                                                tokenEntity.getTenantId(),
                                                jti,
                                                "manual",
                                                tokenEntity.getClientIp()));
                            })
                            .doOnSuccess(v -> log.debug("Token撤销成功"));
                });
    }

    @Override
    public Mono<Void> revokeAllUserTokens(Long userId, String tenantId) {
        return tokenRepository
                .revokeByUserId(userId, tenantId)
                .doOnSuccess(count -> log.debug("已撤销用户所有Token: userId={}, count={}", userId, count))
                .then();
    }

    @Override
    public Mono<Long> extractUserId(String token) {
        return validateToken(token).map(TokenEntity::getUserId);
    }
}
