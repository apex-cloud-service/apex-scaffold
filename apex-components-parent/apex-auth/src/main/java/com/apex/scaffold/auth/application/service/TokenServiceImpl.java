package com.apex.scaffold.auth.application.service;

import com.apex.scaffold.auth.domain.model.entity.TokenEntity;
import com.apex.scaffold.auth.domain.model.entity.UserEntity;
import com.apex.scaffold.auth.domain.model.vo.TokenEntityPair;
import com.apex.scaffold.auth.domain.model.vo.TokenPair;
import com.apex.scaffold.auth.domain.model.vo.TokenRotationResult;
import com.apex.scaffold.auth.domain.repository.TokenRepository;
import com.apex.scaffold.auth.domain.repository.UserRepository;
import com.apex.scaffold.auth.domain.service.JwtKeyMaterialService;
import com.apex.scaffold.auth.domain.service.TokenEncryptionService;
import com.apex.scaffold.auth.domain.service.TokenService;
import com.apex.scaffold.common.audit.SecurityAuditService;
import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * JWT 生命周期用例：事务前签名加密，仓储原子签发或消费轮换，提交后执行有界非阻断审计。
 *
 * <p>普通验证拒绝所有已撤销记录；刷新共享验签及可信声明核对，但保留撤销状态供仓储分类。
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
    private static final long ACCESS_TOKEN_EXPIRE_MS = 2 * 60 * 60 * 1000L;
    private static final long REFRESH_TOKEN_EXPIRE_MS = 7 * 24 * 60 * 60 * 1000L;
    private static final Duration AUDIT_TIMEOUT = Duration.ofSeconds(2);

    private final TokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final TokenEncryptionService encryptionService;
    private final SecurityAuditService auditService;
    private final JwtKeyMaterialService jwtKeyMaterialService;

    @Override
    public Mono<TokenPair> issueToken(Long userId, String tenantId, String deviceId, String clientIp) {
        return issueToken(userId, tenantId, deviceId, clientIp, null);
    }

    @Override
    public Mono<TokenPair> issueToken(
            Long userId, String tenantId, String deviceId, String clientIp, String clientType) {
        return activeUser(userId, tenantId)
                .flatMap(user -> prepareTokens(user, deviceId, clientIp, clientType, null))
                .flatMap(prepared -> tokenRepository
                        .savePair(prepared.entities())
                        .flatMap(saved -> auditAfterCommit(() -> auditService.logTokenIssued(
                                        userId, tenantId, clientIp, deviceId, "ACCESS+REFRESH"))
                                .thenReturn(prepared.response(saved))))
                .doOnSuccess(pair -> log.debug("Token签发成功: userId={}", userId));
    }

    private Mono<UserEntity> activeUser(Long userId, String tenantId) {
        return userRepository
                .findById(userId)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "用户不存在")))
                .flatMap(user -> {
                    if (!Objects.equals(tenantId, user.getTenantId())) {
                        return Mono.error(new BusinessException(ResultCode.FORBIDDEN, "租户不匹配"));
                    }
                    if (!user.isActive()) {
                        return Mono.error(new BusinessException(ResultCode.FORBIDDEN, "账户已禁用"));
                    }
                    if (user.isLocked()) {
                        return Mono.error(new BusinessException(ResultCode.FORBIDDEN, "账户已锁定"));
                    }
                    return Mono.just(user);
                });
    }

    /**
     * 异步加载签名密钥并加密新 JWT；此阶段不写数据库，也不消费旧刷新令牌。
     * @param user 当前有效用户档案，统一账户声明取此档案
     * @param deviceId 原设备标识
     * @param clientIp 原客户端 IP
     * @param clientType 已验签的客户端类型，初次签发由调用方提供
     * @param previous 轮换前记录；初次签发为 null
     * @return 明文响应及密文持久化记录，仅在事务提交后返回调用方
     */
    private Mono<PreparedTokens> prepareTokens(
            UserEntity user, String deviceId, String clientIp, String clientType, TokenEntity previous) {
        return Mono.defer(() -> {
            String accessJti = UUID.randomUUID().toString();
            String refreshJti = UUID.randomUUID().toString();
            String family = previous == null
                            || previous.getTokenFamily() == null
                            || previous.getTokenFamily().isBlank()
                    ? UUID.randomUUID().toString()
                    : previous.getTokenFamily();
            int rotationCount = previous == null
                    ? 0
                    : Math.addExact(previous.getRotationCount() == null ? 0 : previous.getRotationCount(), 1);
            Map<String, Object> accessClaims = buildClaims(accessJti, user, "ACCESS", deviceId, clientType);
            Map<String, Object> refreshClaims = buildClaims(refreshJti, user, REFRESH_TOKEN_TYPE, deviceId, clientType);
            return jwtKeyMaterialService
                    .sign(String.valueOf(user.getId()), accessClaims, ACCESS_TOKEN_EXPIRE_MS)
                    .zipWith(jwtKeyMaterialService.sign(
                            String.valueOf(user.getId()), refreshClaims, REFRESH_TOKEN_EXPIRE_MS))
                    .map(signed -> {
                        LocalDateTime now = LocalDateTime.now();
                        TokenEntity access = preparedEntity(
                                user,
                                deviceId,
                                clientIp,
                                accessJti,
                                "ACCESS",
                                signed.getT1(),
                                now.plusSeconds(ACCESS_TOKEN_EXPIRE_MS / 1000),
                                family,
                                rotationCount,
                                previous);
                        TokenEntity refresh = preparedEntity(
                                user,
                                deviceId,
                                clientIp,
                                refreshJti,
                                REFRESH_TOKEN_TYPE,
                                signed.getT2(),
                                now.plusSeconds(REFRESH_TOKEN_EXPIRE_MS / 1000),
                                family,
                                rotationCount,
                                previous);
                        return new PreparedTokens(signed.getT1(), signed.getT2(), new TokenEntityPair(access, refresh));
                    });
        });
    }

    private TokenEntity preparedEntity(
            UserEntity user,
            String deviceId,
            String clientIp,
            String jti,
            String tokenType,
            String token,
            LocalDateTime expiry,
            String family,
            int rotationCount,
            TokenEntity previous) {
        TokenEncryptionService.EncryptedToken encrypted = encryptionService.encrypt(token);
        return TokenEntity.builder()
                .tenantId(user.getTenantId())
                .userId(user.getId())
                .tokenType(tokenType)
                .tokenValue(encrypted.encryptedData())
                .tokenHash(encryptionService.hashToken(token))
                .tokenIv(encrypted.iv())
                .jti(jti)
                .expiresAt(expiry)
                .deviceId(deviceId)
                .clientIp(clientIp)
                .userAgent(previous == null ? null : previous.getUserAgent())
                .tokenFamily(family)
                .previousJti(previous == null ? null : previous.getJti())
                .rotationCount(rotationCount)
                .build();
    }

    private Map<String, Object> buildClaims(
            String jti, UserEntity user, String tokenType, String deviceId, String clientType) {
        Map<String, Object> claims = new HashMap<>(CLAIMS_INITIAL_CAPACITY);
        claims.put("jti", jti);
        claims.put("userId", user.getId());
        claims.put("tenantId", user.getTenantId());
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
        return verifiedToken(token, false).map(VerifiedToken::entity);
    }

    /**
     * 共享签名、声明、租户、记录身份与有效期验证，不因刷新允许检查撤销状态而跳过可信核对。
     * @param token 原始 JWT
     * @param allowRevoked 刷新时允许返回已撤销记录，普通验证保持拒绝
     * @return 已核对的持久化记录及验签声明
     */
    private Mono<VerifiedToken> verifiedToken(String token, boolean allowRevoked) {
        return jwtKeyMaterialService
                .parse(token)
                .onErrorMap(
                        ExpiredJwtException.class,
                        exception -> new BusinessException(ResultCode.UNAUTHORIZED, "Token已过期"))
                .onErrorMap(
                        exception -> exception instanceof JwtException || exception instanceof IllegalArgumentException,
                        exception -> new BusinessException(ResultCode.UNAUTHORIZED, "Token无效"))
                .flatMap(claims -> {
                    String jti = claims.get("jti", String.class);
                    Long userId = claims.get("userId", Long.class);
                    String tenantId = claims.get("tenantId", String.class);
                    String tokenType = claims.get("tokenType", String.class);
                    if (jti == null
                            || jti.isBlank()
                            || userId == null
                            || tenantId == null
                            || tenantId.isBlank()
                            || tokenType == null
                            || tokenType.isBlank()) {
                        return Mono.error(new BusinessException(ResultCode.UNAUTHORIZED, "Token声明不完整"));
                    }
                    return Mono.deferContextual(context -> {
                        String existingTenant = context.getOrDefault(
                                TenantContext.REACTOR_CONTEXT_KEY,
                                TenantContext.currentTenantId().orElse(null));
                        if (existingTenant != null && !existingTenant.equals(tenantId)) {
                            return Mono.error(new BusinessException(ResultCode.FORBIDDEN));
                        }
                        return tokenRepository
                                .findByJti(jti)
                                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.UNAUTHORIZED, "Token不存在")))
                                .flatMap(entity -> {
                                    if (!Objects.equals(entity.getTenantId(), tenantId)
                                            || !Objects.equals(entity.getUserId(), userId)
                                            || !Objects.equals(entity.getJti(), jti)
                                            || !Objects.equals(entity.getTokenType(), tokenType)
                                            || (entity.getTokenHash() != null
                                                    && !Objects.equals(
                                                            entity.getTokenHash(),
                                                            encryptionService.hashToken(token)))) {
                                        return Mono.error(new BusinessException(ResultCode.UNAUTHORIZED));
                                    }
                                    if (entity.isRevoked() && !allowRevoked) {
                                        return Mono.error(new BusinessException(ResultCode.UNAUTHORIZED, "Token已撤销"));
                                    }
                                    if (entity.getExpiresAt() == null
                                            || !entity.getExpiresAt().isAfter(LocalDateTime.now())) {
                                        return Mono.error(new BusinessException(ResultCode.UNAUTHORIZED, "Token已过期"));
                                    }
                                    return Mono.just(new VerifiedToken(entity, claims));
                                })
                                .transformDeferred(TenantContext::withTenant)
                                .contextWrite(TenantContext.withTenant(tenantId));
                    });
                });
    }

    @Override
    public Mono<TokenPair> refreshToken(String refreshToken) {
        return verifiedToken(refreshToken, true).flatMap(verified -> {
            TokenEntity previous = verified.entity();
            if (!REFRESH_TOKEN_TYPE.equals(previous.getTokenType())) {
                return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "必须使用刷新令牌"));
            }
            Mono<TokenPair> refresh;
            if (previous.isRevoked()) {
                refresh = tokenRepository
                        .checkRefreshReplay(previous)
                        .flatMap(result -> finishRotation(result, previous, null));
            } else {
                String clientType = verified.claims().get("clientType", String.class);
                refresh = activeUser(previous.getUserId(), previous.getTenantId())
                        .flatMap(user -> prepareTokens(
                                user, previous.getDeviceId(), previous.getClientIp(), clientType, previous))
                        .flatMap(prepared -> tokenRepository
                                .rotateRefreshToken(previous, prepared.entities())
                                .flatMap(result -> finishRotation(result, previous, prepared)));
            }
            return refresh.transformDeferred(TenantContext::withTenant)
                    .contextWrite(TenantContext.withTenant(previous.getTenantId()));
        });
    }

    /**
     * 仓储结果已提交后才映射重放为 401，审计不可使安全撤销回滚或隐藏已提交令牌。
     * @param result 同步事务提交结果
     * @param previous 旧刷新令牌记录
     * @param prepared 成功轮换对应的响应；单独检查重放时为 null
     * @return 新令牌对或认证失败信号
     */
    private Mono<TokenPair> finishRotation(TokenRotationResult result, TokenEntity previous, PreparedTokens prepared) {
        if (result.status() == TokenRotationResult.Status.REPLAY) {
            return auditAfterCommit(() -> auditService.logTokenReplayAttack(
                            previous.getUserId(), previous.getTenantId(), previous.getClientIp(), previous.getJti()))
                    .then(Mono.error(new BusinessException(ResultCode.UNAUTHORIZED, "检测到安全威胁，已撤销所有会话，请重新登录")));
        }
        if (result.status() != TokenRotationResult.Status.ROTATED || prepared == null) {
            return Mono.error(new BusinessException(ResultCode.UNAUTHORIZED, "Token已撤销或失效"));
        }
        return auditAfterCommit(() -> auditService.logTokenRefreshed(
                        previous.getUserId(), previous.getTenantId(), previous.getClientIp(), previous.getJti()))
                .thenReturn(prepared.response(result.tokens()));
    }

    /**
     * 有界等待提交后的审计，记录失败类型但不输出敏感载荷，不改变已提交的认证结果。
     * @param audit 延迟执行的审计操作
     * @return 审计结束、超时或失败后的完成信号
     */
    private Mono<Void> auditAfterCommit(Supplier<Mono<Void>> audit) {
        return Mono.defer(audit).timeout(AUDIT_TIMEOUT).onErrorResume(error -> {
            log.warn("Token提交后审计失败: type={}", error.getClass().getSimpleName());
            return Mono.empty();
        });
    }

    @Override
    public Mono<Void> revokeToken(String token) {
        return jwtKeyMaterialService
                .parse(token)
                .onErrorMap(
                        exception -> exception instanceof JwtException || exception instanceof IllegalArgumentException,
                        exception -> new BusinessException(ResultCode.UNAUTHORIZED, "Token无效"))
                .flatMap(claims -> {
                    String jti = claims.get("jti", String.class);
                    if (jti == null || jti.isBlank()) {
                        return Mono.error(new BusinessException(ResultCode.UNAUTHORIZED, "Token格式错误"));
                    }
                    return tokenRepository
                            .findByJti(jti)
                            .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "Token不存在")))
                            .flatMap(entity -> tokenRepository
                                    .revokeByJti(jti, "user:" + entity.getUserId(), "manual")
                                    .then(auditAfterCommit(() -> auditService.logTokenRevoked(
                                            entity.getUserId(),
                                            null,
                                            entity.getTenantId(),
                                            jti,
                                            "manual",
                                            entity.getClientIp()))));
                });
    }

    @Override
    public Mono<Void> revokeAllUserTokens(Long userId, String tenantId) {
        return tokenRepository
                .revokeByUserId(userId, tenantId)
                .doOnSuccess(count -> log.debug("已撤销用户所有Token: userId={}, changed={}", userId, count))
                .then();
    }

    @Override
    public Mono<Long> extractUserId(String token) {
        return validateToken(token).map(TokenEntity::getUserId);
    }

    private record VerifiedToken(TokenEntity entity, Claims claims) {}

    private record PreparedTokens(String accessToken, String refreshToken, TokenEntityPair entities) {
        TokenPair response(TokenEntityPair saved) {
            return TokenPair.builder()
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .accessTokenId(saved.accessToken().getId())
                    .refreshTokenJti(saved.refreshToken().getJti())
                    .expiresIn(ACCESS_TOKEN_EXPIRE_MS / 1000)
                    .tokenType("Bearer")
                    .build();
        }
    }
}
