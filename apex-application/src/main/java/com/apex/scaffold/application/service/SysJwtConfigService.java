package com.apex.scaffold.application.service;

import com.apex.scaffold.application.assembler.SysJwtConfigAssembler;
import com.apex.scaffold.application.dto.admin.JwtConfigDTO;
import com.apex.scaffold.application.dto.admin.JwtConfigRequest;
import com.apex.scaffold.application.dto.admin.JwtTestRequest;
import com.apex.scaffold.application.dto.admin.JwtTestResponse;
import com.apex.scaffold.application.event.JwtConfigUpdatedEvent;
import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.domain.model.entity.SysJwtConfigEntity;
import com.apex.scaffold.domain.repository.SysJwtConfigRepository;
import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * JWT 配置应用服务。
 *
 * <p>提供租户级别的 JWT 配置管理和测试功能，支持 JWT Token 的生成、验证和配置更新。
 * 所有操作基于当前租户上下文，确保多租户环境下的配置隔离。
 *
 * <h2>核心功能</h2>
 * <ul>
 *   <li>查询租户 JWT 配置（签名算法、密钥、有效期等）</li>
 *   <li>更新租户 JWT 配置并发布配置变更事件</li>
 *   <li>测试 JWT 配置有效性（生成测试 Token 并验证）</li>
 * </ul>
 *
 * <h2>支持的签名算法</h2>
 * <ul>
 *   <li>HS256: HMAC-SHA256（对称加密，推荐用于单体应用）</li>
 *   <li>HS512: HMAC-SHA512（对称加密，安全性更高）</li>
 *   <li>RS256: RSA-SHA256（非对称加密，适用于微服务架构）- 待实现</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysJwtConfigService {

    private final SysJwtConfigRepository jwtConfigRepository;
    private final SysJwtConfigAssembler jwtConfigAssembler;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 查询当前租户的 JWT 配置。
     *
     * @return JWT 配置 DTO
     */
    public Mono<JwtConfigDTO> getByTenantId() {
        String tenantId = TenantContext.currentTenantId()
                .orElseThrow(() -> new BusinessException(ResultCode.FORBIDDEN, "租户上下文未初始化"));
        log.debug("查询租户 JWT 配置: tenantId=", tenantId);

        return jwtConfigRepository
                .findByTenantId(tenantId)
                .map(jwtConfigAssembler::toDTO)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "JWT 配置不存在，请联系管理员初始化配置")));
    }

    /**
     * 更新当前租户的 JWT 配置。
     *
     * @param request JWT 配置更新请求
     * @return 更新后的 JWT 配置 DTO
     */
    public Mono<JwtConfigDTO> update(JwtConfigRequest request) {
        String tenantId = TenantContext.currentTenantId()
                .orElseThrow(() -> new BusinessException(ResultCode.FORBIDDEN, "租户上下文未初始化"));
        log.info("更新租户 JWT 配置: tenantId={}", tenantId);

        return jwtConfigRepository
                .findByTenantId(tenantId)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "JWT 配置不存在，请联系管理员初始化配置")))
                .flatMap(existingEntity -> {
                    SysJwtConfigEntity updatedEntity = jwtConfigAssembler.toEntity(request);
                    updatedEntity.setId(existingEntity.getId());
                    updatedEntity.setTenantId(tenantId);
                    updatedEntity.setEnabled(1);

                    return jwtConfigRepository.updateById(updatedEntity).then(Mono.just(updatedEntity));
                })
                .map(entity -> {
                    JwtConfigDTO dto = jwtConfigAssembler.toDTO(entity);
                    eventPublisher.publishEvent(new JwtConfigUpdatedEvent(this, tenantId, dto));
                    log.info("JWT 配置更新成功并发布事件: tenantId={}", tenantId);
                    return dto;
                });
    }

    /**
     * 测试 JWT 配置。
     *
     * <p>使用当前租户的 JWT 配置生成测试 Token 并验证，用于测试配置是否正确。
     * 该方法会执行完整的 Token 生命周期：生成 → 签名 → 解码 → 验证。
     *
     * <h2>测试流程</h2>
     * <ol>
     *   <li>获取当前租户的 JWT 配置（算法、密钥、签发者等）</li>
     *   <li>根据请求中的 Payload 和配置生成 JWT Token</li>
     *   <li>使用相同的配置解码并验证 Token</li>
     *   <li>返回 Token 字符串、解码结果和验证状态</li>
     * </ol>
     *
     * <h2>安全说明</h2>
     * <ul>
     *   <li>生成的测试 Token 仅用于配置验证，不应用于生产环境</li>
     *   <li>Token 密钥在传输过程中已加密，不会泄露</li>
     *   <li>测试操作会记录审计日志，便于安全追踪</li>
     * </ul>
     *
     * @param request 测试请求，包含自定义 Payload 和可选的过期时间
     * @return 测试响应，包含生成的 Token、解码结果和验证状态
     */
    public Mono<JwtTestResponse> testJwt(JwtTestRequest request) {
        log.info("开始测试 JWT 配置，payload keys: {}", request.getPayload().keySet());

        return getByTenantId().flatMap(config -> Mono.fromCallable(() -> {
                    try {
                        // 1. 获取签名算法
                        Algorithm algorithm = getAlgorithm(config);

                        // 2. 构建 JWT Token
                        JWTCreator.Builder builder = JWT.create().withIssuer(config.getIssuer());

                        // 3. 添加自定义 Payload
                        request.getPayload().forEach((key, value) -> {
                            if (value instanceof String) {
                                builder.withClaim(key, (String) value);
                            } else if (value instanceof Integer) {
                                builder.withClaim(key, (Integer) value);
                            } else if (value instanceof Long) {
                                builder.withClaim(key, (Long) value);
                            } else if (value instanceof Boolean) {
                                builder.withClaim(key, (Boolean) value);
                            } else if (value != null) {
                                // 其他类型转为字符串
                                builder.withClaim(key, value.toString());
                            }
                        });

                        // 4. 设置时间信息
                        LocalDateTime issuedAt = LocalDateTime.now();
                        long expiresIn = request.getExpiresIn() != null
                                ? request.getExpiresIn()
                                : (config.getAccessTokenExpiration() != null
                                        ? config.getAccessTokenExpiration()
                                        : 3600);
                        LocalDateTime expiresAt = issuedAt.plusSeconds(expiresIn);

                        builder.withIssuedAt(Date.from(
                                        issuedAt.atZone(ZoneId.systemDefault()).toInstant()))
                                .withExpiresAt(Date.from(
                                        expiresAt.atZone(ZoneId.systemDefault()).toInstant()));

                        // 5. 生成 Token
                        String token = builder.sign(algorithm);
                        log.info("JWT Token 生成成功，长度: {}", token.length());

                        // 6. 验证 Token
                        JWTVerifier verifier = JWT.require(algorithm)
                                .withIssuer(config.getIssuer())
                                .build();
                        DecodedJWT decoded = verifier.verify(token);

                        // 7. 提取解码后的 Payload
                        Map<String, Object> decodedPayload = new HashMap<>(16);
                        decoded.getClaims().forEach((key, claim) -> {
                            if (!claim.isNull()) {
                                // 尝试获取不同类型的值
                                try {
                                    decodedPayload.put(key, claim.asString());
                                } catch (Exception e) {
                                    // 如果不是字符串，尝试其他类型
                                    decodedPayload.put(key, claim.toString());
                                }
                            }
                        });

                        // 8. 构建响应
                        return JwtTestResponse.builder()
                                .token(token)
                                .decoded(decodedPayload)
                                .isValid(true)
                                .expiresAt(expiresAt)
                                .issuedAt(issuedAt)
                                .issuer(config.getIssuer())
                                .build();

                    } catch (Exception e) {
                        log.error("JWT 测试失败", e);
                        // 返回失败响应
                        return JwtTestResponse.builder().isValid(false).build();
                    }
                })
                // JWT 签名与验证为 CPU 密集型计算，统一调度到通用后台线程池，
                // 避免占用事件循环线程（严禁直接使用 Schedulers.boundedElastic()）
                .subscribeOn(BlockingIoSchedulers.general()));
    }

    /**
     * 获取签名算法。
     *
     * <p>根据 JWT 配置中的算法类型和密钥，创建对应的签名算法实例。
     * 该方法为私有方法，仅供内部使用。
     *
     * <h2>支持的算法</h2>
     * <ul>
     *   <li>HS256: HMAC-SHA256（对称加密）</li>
     *   <li>HS512: HMAC-SHA512（对称加密）</li>
     *   <li>RS256: RSA-SHA256（非对称加密）- 待实现</li>
     * </ul>
     *
     * <h2>密钥要求</h2>
     * <ul>
     *   <li>HS256/HS512: 密钥长度建议 ≥ 256 位（32 字节）</li>
     *   <li>RS256: 需要提供 RSA 公钥和私钥（PEM 格式）</li>
     * </ul>
     *
     * @param config JWT 配置 DTO
     * @return 签名算法实例
     * @throws BusinessException 如果算法不支持或密钥格式错误
     */
    private Algorithm getAlgorithm(JwtConfigDTO config) {
        String algorithmType = config.getAlgorithm().toUpperCase();

        return switch (algorithmType) {
            case "HS256" -> Algorithm.HMAC256(config.getSecret());
            case "HS512" -> Algorithm.HMAC512(config.getSecret());
            case "RS256" -> throw new BusinessException(ResultCode.ERROR, "暂不支持 RSA 算法，请使用 HS256 或 HS512");
            default -> throw new BusinessException(ResultCode.ERROR, "不支持的签名算法: " + config.getAlgorithm());
        };
    }
}
