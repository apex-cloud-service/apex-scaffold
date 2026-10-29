package com.apex.scaffold.auth.application.service;

import com.apex.scaffold.auth.domain.model.entity.UserEntity;
import com.apex.scaffold.auth.domain.repository.UserRepository;
import com.apex.scaffold.auth.domain.service.TokenService;
import com.apex.scaffold.contract.client.AuthFeignClient;
import com.apex.scaffold.contract.dto.internal.TokenValidationDTO;
import com.apex.scaffold.contract.dto.internal.TokenValidationRequest;
import io.jsonwebtoken.Claims;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 认证中心内部 API 应用服务（迁移方案 Phase 2.3）.
 *
 * <p>对应 {@link AuthFeignClient} 契约的两个内部接口实现，仅供集群内服务直连调用
 * （经 {@code /internal/v1/**}，不经过网关，网关层强制 403 拒绝）。
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>权限查询：{@code GET /internal/v1/users/{accountId}/permissions}
 *       —— 消费方（system/workflow/office service）持有主应用 {@code sys_user.id}
 *       （即 JWT 中的 {@code accountId}），据此反查认证中心用户并展开权限码。</li>
 *   <li>Token 校验：{@code POST /internal/v1/tokens/validate}
 *       —— 过渡期校验存量 Token 并返回语义快照（{@link TokenValidationDTO}），
 *       不含任何凭证或密钥，auth 不可用时机「拒绝优先」由消费方 Fallback 承担。</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <p>契约侧使用 {@code Long tenantId}，而认证中心内部以 {@code String} 租户编码
 * （如 {@code default}）作为租户主键。本服务在边界处做显式桥接：
 * <ul>
 *   <li>入参方向：将 {@code Long tenantId} 转为 {@code String.valueOf(tenantId)}，
 *       用于 {@code JdbcTenantScope} 的多租户隔离；</li>
 *   <li>出参方向：仅当内部编码可解析为数字时才回填 DTO 的 {@code Long tenantId}，
 *       非数字编码（如 {@code default}）返回 {@code null}，消费方应优先依赖
 *       {@code X-Tenant-Id} 头透传的字符串租户。</li>
 * </ul>
 *
 * @author Apex Team
 * @since 1.3.1
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InternalAuthService {

    /** 业务异常消息关键字：Token 已过期 */
    private static final String MESSAGE_KEY_EXPIRED = "过期";

    /** 业务异常消息关键字：Token 已被撤销 */
    private static final String MESSAGE_KEY_REVOKED = "撤销";

    /** 业务异常消息关键字：Token 不存在 */
    private static final String MESSAGE_KEY_NOT_FOUND = "不存在";

    /** 业务异常消息关键字：Token 不完整 */
    private static final String MESSAGE_KEY_INCOMPLETE = "不完整";

    /** 业务异常消息关键字：租户不匹配 */
    private static final String MESSAGE_KEY_TENANT = "租户";

    /** 用户仓储（用于按统一账户 ID 反查认证中心用户） */
    private final UserRepository userRepository;

    /** 统一授权查询服务（复用权限展开逻辑） */
    private final UnifiedAuthorizationQueryService authorizationQueryService;

    /** Token 服务（用于存量 Token 语义校验） */
    private final TokenService tokenService;

    /** JWT 密钥材料服务（读取 claims 中的 accountId / clientType，用于校验快照富化） */
    private final com.apex.scaffold.auth.domain.service.JwtKeyMaterialService jwtKeyMaterialService;

    /**
     * 查询指定账户（统一账户 ID）在指定租户下的实时权限编码列表.
     *
     * <p>消费方持有的 {@code accountId} 是主应用 {@code sys_user.id}，需先反查
     * 认证中心用户，再复用 {@link UnifiedAuthorizationQueryService#query} 展开其权限码。
     *
     * @param accountId 统一账户 ID（主应用 sys_user.id）
     * @param tenantId  租户 ID（契约侧 Long，内部桥接为 String 编码）
     * @return 权限编码列表；账户不存在或账户非活跃时返回空列表（拒绝优先由调用方裁定）
     */
    public Mono<List<String>> getUserPermissions(Long accountId, Long tenantId) {
        if (accountId == null || accountId <= 0) {
            return Mono.just(List.of());
        }
        String tenant = bridge(tenantId);
        if (tenant == null || tenant.isBlank()) {
            return Mono.just(List.of());
        }
        return userRepository
                .findByAccountId(accountId, tenant)
                .filter(UserEntity::isActive)
                .filter(user -> !user.isLocked())
                .filter(user -> Integer.valueOf(0).equals(user.getDeleted()))
                .flatMap(user ->
                        authorizationQueryService.query(tenant, user.getId()).map(snapshot -> snapshot.permissions()))
                .defaultIfEmpty(List.of())
                .onErrorResume(error -> {
                    log.debug(
                            "内部权限查询失败，按空权限处理: accountId={}, tenant={}, cause={}",
                            accountId,
                            tenant,
                            error.getClass().getSimpleName());
                    return Mono.just(List.of());
                });
    }

    /**
     * 校验存量 Token 并返回语义快照（过渡期能力）.
     *
     * <p>校验通过返回{@code valid=true}并携带主体身份（accountId / tenantId）与过期时间；
     * 校验失败返回{@code valid=false}并给出失效原因（{@code EXPIRED} / {@code REVOKED} /
     * {@code INVALID_SIGNATURE} 等）。本方法不抛出异常，始终返回快照，便于消费方按
     * 「拒绝优先」语义处理。透传租户与 Token 声明租户不一致时按失效（{@code TENANT_MISMATCH}）处理。
     *
     * @param request 校验请求（原始 Token + 可选租户 / 来源）
     * @return Token 语义校验快照
     */
    public Mono<TokenValidationDTO> validateToken(TokenValidationRequest request) {
        if (request == null || request.getToken() == null || request.getToken().isBlank()) {
            return Mono.just(TokenValidationDTO.builder()
                    .valid(false)
                    .reason("MISSING_TOKEN")
                    .build());
        }
        String hintTenant = bridge(request.getTenantId());
        return tokenService
                .validateToken(request.getToken())
                .flatMap(entity -> jwtKeyMaterialService
                        .parse(request.getToken())
                        .map(claims -> toValidationDTO(entity, claims, hintTenant)))
                .onErrorResume(error -> Mono.just(TokenValidationDTO.builder()
                        .valid(false)
                        .reason(resolveFailureReason(error))
                        .build()));
    }

    /**
     * 将校验通过的 Token 与解析出的 claims 组装为语义快照.
     *
     * @param entity   认证中心 Token 实体（含租户 / 过期时间 / 撤销状态）
     * @param claims   JWT 声明（含 accountId / clientType）
     * @param hintTenant 消费方透传的租户（可空），非空且与 Token 声明租户不一致时判为失效
     * @return Token 语义校验快照
     */
    private TokenValidationDTO toValidationDTO(
            com.apex.scaffold.auth.domain.model.entity.TokenEntity entity, Claims claims, String hintTenant) {
        String tokenTenant = entity.getTenantId();
        if (hintTenant != null && !hintTenant.isBlank() && !hintTenant.equals(tokenTenant)) {
            return TokenValidationDTO.builder()
                    .valid(false)
                    .reason("TENANT_MISMATCH")
                    .build();
        }
        Long accountId = claims.get("accountId", Long.class);
        String clientType = claims.get("clientType", String.class);
        return TokenValidationDTO.builder()
                .valid(true)
                .accountId(accountId)
                .tenantId(toLongTenantId(tokenTenant))
                .clientType(clientType)
                .expiresAt(entity.getExpiresAt() != null ? entity.getExpiresAt() : null)
                .build();
    }

    /**
     * 将契约侧 {@code Long tenantId} 桥接为认证中心内部 {@code String} 租户编码.
     *
     * @param tenantId 契约侧租户 ID，可空
     * @return 内部租户编码；入参为空时返回 {@code null}
     */
    private String bridge(Long tenantId) {
        return tenantId == null ? null : String.valueOf(tenantId);
    }

    /**
     * 将认证中心内部 {@code String} 租户编码桥接为契约侧 {@code Long tenantId}.
     *
     * <p>仅当编码为纯数字时可解析，否则返回 {@code null}（如 {@code default}）。
     *
     * @param code 内部租户编码
     * @return 数字租户 ID；非数字编码返回 {@code null}
     */
    private Long toLongTenantId(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(code);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 将校验阶段抛出的异常映射为快照中的失效原因.
     *
     * @param error 校验异常
     * @return 语义化失效原因码
     */
    private String resolveFailureReason(Throwable error) {
        return com.apex.scaffold.common.exception.BusinessException.class.isInstance(error)
                ? extractBusinessReason((com.apex.scaffold.common.exception.BusinessException) error)
                : "INVALID_SIGNATURE";
    }

    /**
     * 从业务异常消息中提取语义化失效原因.
     *
     * @param failure 业务异常
     * @return 命中关键字时的原因码，否则默认 {@code INVALID_SIGNATURE}
     */
    private String extractBusinessReason(com.apex.scaffold.common.exception.BusinessException failure) {
        String message = failure.getMessage();
        if (message == null) {
            return "INVALID_SIGNATURE";
        }
        String normalized = message.toLowerCase();
        if (normalized.contains(MESSAGE_KEY_EXPIRED)) {
            return "EXPIRED";
        }
        if (normalized.contains(MESSAGE_KEY_REVOKED)) {
            return "REVOKED";
        }
        if (normalized.contains(MESSAGE_KEY_NOT_FOUND) || normalized.contains(MESSAGE_KEY_INCOMPLETE)) {
            return "TOKEN_NOT_FOUND";
        }
        if (normalized.contains(MESSAGE_KEY_TENANT)) {
            return "TENANT_MISMATCH";
        }
        return "INVALID_SIGNATURE";
    }
}
