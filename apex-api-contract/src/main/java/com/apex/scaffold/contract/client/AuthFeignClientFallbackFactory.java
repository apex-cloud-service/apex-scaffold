package com.apex.scaffold.contract.client;

import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.contract.dto.internal.TokenValidationDTO;
import com.apex.scaffold.contract.dto.internal.TokenValidationRequest;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * {@link AuthFeignClient} 降级工厂.
 *
 * <p>当 auth-service 不可用（超时/熔断/宕机）时依据「拒绝优先」的安全默认降级：</p>
 * <ul>
 *   <li>权限接口：返回 {@code SERVICE_UNAVAILABLE}，调用方应将其视为「权限暂不可验证」并返回
 *       403，而不是放行（避免降级期间越权）。可优先尝试读取本地 Caffeine 权限快照。</li>
 *   <li>Token 校验：返回 {@code SERVICE_UNAVAILABLE} 并标记为无效语义，过渡期业务应快速失败。</li>
 * </ul>
 *
 * @author Apex Team
 * @since 1.3.1
 */
@Component
@Slf4j
public class AuthFeignClientFallbackFactory implements FallbackFactory<AuthFeignClient> {

    @Override
    public AuthFeignClient create(Throwable cause) {
        return new AuthFeignClient() {
            @Override
            public Result<List<String>> getUserPermissions(Long accountId, Long tenantId) {
                log.warn(
                        "auth-service 不可用, 权限判定按拒绝优先降级: accountId={}, tenantId={}, cause={}",
                        accountId,
                        tenantId,
                        cause.getMessage());
                return Result.error(ResultCode.SERVICE_UNAVAILABLE);
            }

            @Override
            public Result<TokenValidationDTO> validateToken(TokenValidationRequest request) {
                log.warn("auth-service 不可用, Token 校验降级为无效: cause={}", cause.getMessage());
                return Result.error(ResultCode.SERVICE_UNAVAILABLE);
            }
        };
    }
}
