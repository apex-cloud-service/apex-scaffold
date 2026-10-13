package com.apex.scaffold.authservice.interfaces.handler;

import com.apex.scaffold.auth.application.service.InternalAuthService;
import com.apex.scaffold.authservice.interfaces.util.RequestHandlerUtil;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.contract.dto.internal.TokenValidationRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * 认证中心内部 API 处理器（迁移方案 Phase 2.3）.
 *
 * <p>承载 {@code /internal/v1/**} 内部契约的请求适配：解析路径/查询/请求体参数，
 * 委托 {@link InternalAuthService} 完成业务处理，并统一以 {@link Result} 封装返回。
 * 该类接口不经网关（网关对 {@code /internal/v1/**} 强制 403），仅集群内服务直连调用。
 *
 * @author Apex Team
 * @since 1.3.1
 */
@Component
@RequiredArgsConstructor
public class InternalAuthHandler {

    /** 内部认证应用服务 */
    private final InternalAuthService service;

    /** 请求处理与响应构建工具 */
    private final RequestHandlerUtil responses;

    /**
     * 处理「查询用户权限码」请求.
     *
     * <p>路径参数 {@code accountId} 为统一账户 ID（主应用 sys_user.id），
     * 查询参数 {@code tenantId} 为契约侧租户 ID（必填）。
     *
     * @param request 当前请求
     * @return 权限编码列表响应（{@code Result<List<String>>}）
     */
    public Mono<ServerResponse> getUserPermissions(ServerRequest request) {
        Long accountId;
        try {
            accountId = Long.valueOf(request.pathVariable("accountId"));
            if (accountId <= 0) {
                return error(400, ResultCode.PARAM_ERROR);
            }
        } catch (NumberFormatException invalidAccount) {
            return error(400, ResultCode.PARAM_ERROR);
        }

        Long tenantId;
        try {
            String raw = request.queryParam("tenantId").orElse(null);
            if (raw == null || raw.isBlank()) {
                return error(400, ResultCode.PARAM_ERROR);
            }
            tenantId = Long.valueOf(raw);
        } catch (NumberFormatException invalidTenant) {
            return error(400, ResultCode.PARAM_ERROR);
        }

        return service.getUserPermissions(accountId, tenantId)
                .map(permissions -> (List<String>) permissions)
                .flatMap(responses::buildSuccessResponse)
                .onErrorResume(failure -> error(500, ResultCode.ERROR));
    }

    /**
     * 处理「校验存量 Token」请求.
     *
     * <p>请求体为 {@link TokenValidationRequest}（原始 Token + 可选租户 / 来源）。
     * 校验结论始终以 200 返回，业务有效性体现在 {@code data.valid} 字段中，
     * 便于消费方按「拒绝优先」语义处理。
     *
     * @param request 当前请求
     * @return Token 语义校验快照响应（{@code Result<TokenValidationDTO>}）
     */
    public Mono<ServerResponse> validateToken(ServerRequest request) {
        Mono<ServerResponse> handled = responses
                .validateAndMap(request, TokenValidationRequest.class)
                .flatMap(service::validateToken)
                .flatMap(responses::buildSuccessResponse);
        return handled.switchIfEmpty(Mono.defer(() -> error(400, ResultCode.PARAM_ERROR)))
                .onErrorResume(failure -> error(500, ResultCode.ERROR));
    }

    /**
     * 构建错误响应（HTTP 状态 + 统一 Result 体）.
     *
     * @param status HTTP 状态码
     * @param code   业务结果码
     * @return 错误响应
     */
    private Mono<ServerResponse> error(int status, ResultCode code) {
        return ServerResponse.status(status).bodyValue(Result.error(code));
    }
}
