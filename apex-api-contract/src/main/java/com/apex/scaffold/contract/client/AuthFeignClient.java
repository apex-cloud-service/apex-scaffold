package com.apex.scaffold.contract.client;

import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.contract.config.FeignClientConstants;
import com.apex.scaffold.contract.config.FeignTenantTraceConfiguration;
import com.apex.scaffold.contract.dto.internal.TokenValidationDTO;
import com.apex.scaffold.contract.dto.internal.TokenValidationRequest;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 认证授权服务内部 Feign 客户端.
 *
 * <p>提供方：apex-auth-service<br/>
 * 消费方：system-service / workflow-service / office-service（权限判定、Token 语义查询）
 *
 * <p><b>内部 API 契约</b>：路径前缀 {@code /internal/v1/}，不经过网关；
 * 通过 {@link FeignTenantTraceConfiguration} 自动注入 {@code X-Tenant-Id} / {@code X-Trace-Id} 头。
 *
 * @author Apex Team
 * @since 1.3.1
 */
@FeignClient(
        name = FeignClientConstants.AUTH_SERVICE_NAME,
        contextId = "authFeignClient",
        configuration = FeignTenantTraceConfiguration.class,
        fallbackFactory = AuthFeignClientFallbackFactory.class)
public interface AuthFeignClient {

    /**
     * 查询用户在指定租户内的实时权限编码列表.
     *
     * <p>用途：system/service 在接口标注 {@code @RequirePermission} 且权限缓存缺失时，
     * 实时向 auth-service 发起权限判定。消费侧应对结果做 Caffeine 短缓存（TTL≤5min），
     * 并配合 {@code RolePermissionChangedEvent} 广播做失效。
     *
     * <p>降级语义（拒绝优先）：auth-service 不可用时由 {@link AuthFeignClientFallbackFactory}
     * 返回 {@code SERVICE_UNAVAILABLE}，调用方应按"权限暂不可验证 → 403"处理，
     * 而非放行，保证安全默认。
     *
     * @param accountId  统一账户 ID
     * @param tenantId   租户 ID（可选，用于多租户隔离的权限过滤）
     * @return 权限编码列表（如 {@code ["user:read", "approval:create"]}）
     */
    @GetMapping("/internal/v1/users/{accountId}/permissions")
    Result<List<String>> getUserPermissions(
            @PathVariable("accountId") Long accountId, @RequestParam("tenantId") Long tenantId);

    /**
     * 校验存量 Token 并返回语义信息（过渡期能力）.
     *
     * <p>用途：过渡期 system-service 需要理解存量主应用 Token 是否有效、归属哪个主体时，
     * 调用本接口完成语义校验。目标态下签校验统一收敛到网关 JWKS + 服务本地
     * oauth2-resource-server，本接口随存量流量迁移逐步下线。
     *
     * @param request 校验请求（原始 Token + 可选租户/来源）
     * @return 校验结果（有效/失效 + 主体身份 + 过期时间）
     */
    @PostMapping("/internal/v1/tokens/validate")
    Result<TokenValidationDTO> validateToken(@RequestBody TokenValidationRequest request);
}
