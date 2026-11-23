package com.apex.scaffold.authservice.interfaces.handler;

import com.apex.scaffold.auth.application.service.InternalAuthService;
import com.apex.scaffold.authservice.interfaces.util.RequestHandlerUtil;
import com.apex.scaffold.common.annotation.OperationLog;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.contract.dto.internal.TokenValidationRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "内部契约-认证", description = "仅供集群内部已验签调用：用户权限快照、令牌校验与撤销增量流；不经网关")
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
    @OperationLog(
            module = "InternalAuth",
            type = OperationLog.OperationType.SELECT,
            description = "getUserPermissions",
            saveRequest = false,
            saveResponse = false)
    @Operation(
            operationId = "getUserPermissions",
            summary = "查询用户权限码（内部）",
            description = "集群内服务直连接口，不经网关。按统一账户 ID 返回其权限编码列表，" + "路径参数 accountId 为统一账户 ID，查询参数 tenantId 为契约侧租户 ID（必填）")
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
     * 处理「令牌撤销增量流」请求（统一认证收口 A3）.
     *
     * <p>查询参数 {@code since} 为撤销时间游标（epoch 毫秒，必填，允许 0 起始），
     * {@code limit} 为单批上限（可选，默认 500，服务端收敛到 [1, 1000]）。
     * 响应 {@code data} 为 {@code {revocations:[{jti,revokedAt}], cursor}}，
     * 游标推进到本批最大撤销时间，供网关周期拉取。
     *
     * @param request 当前请求
     * @return 撤销增量响应（{@code Result<InternalAuthService.TokenRevocationFeed>}）
     */
    @OperationLog(
            module = "InternalAuth",
            type = OperationLog.OperationType.SELECT,
            description = "tokenRevocations",
            saveRequest = false,
            saveResponse = false)
    @Operation(
            operationId = "internalTokenRevocations",
            summary = "令牌撤销增量流（内部）",
            description = "集群内服务直连接口，不经网关。按撤销时间游标返回已撤销令牌的 jti 增量，" + "供网关建立本地撤销缓存（登出/踢人/撤权在撤销间隔内于网关生效）")
    public Mono<ServerResponse> tokenRevocations(ServerRequest request) {
        long since;
        try {
            String raw = request.queryParam("since").orElse(null);
            if (raw == null || raw.isBlank()) {
                return error(400, ResultCode.PARAM_ERROR);
            }
            since = Long.parseLong(raw);
            if (since < 0) {
                return error(400, ResultCode.PARAM_ERROR);
            }
        } catch (NumberFormatException invalidSince) {
            return error(400, ResultCode.PARAM_ERROR);
        }
        int limit = 500;
        String rawLimit = request.queryParam("limit").orElse(null);
        if (rawLimit != null && !rawLimit.isBlank()) {
            try {
                limit = Integer.parseInt(rawLimit);
            } catch (NumberFormatException invalidLimit) {
                return error(400, ResultCode.PARAM_ERROR);
            }
        }
        long cursor = since;
        return service.revocationsSince(cursor, limit)
                .flatMap(responses::buildSuccessResponse)
                .onErrorResume(failure -> error(500, ResultCode.ERROR));
    }

    /**
     * 处理「校验存量 Token」请求.
     *
     * <p>请求体为 {@link TokenValidationRequest}（原始 Token + 可选租户 / 来源）。
     * 正常完成校验时以 HTTP 200 返回，业务有效性体现在 {@code data.valid} 字段中，
     * 便于消费方按「拒绝优先」语义处理。
     *
     * @param request 当前请求
     * @return Token 语义校验快照响应（{@code Result<TokenValidationDTO>}）
     */
    @OperationLog(
            module = "InternalAuth",
            type = OperationLog.OperationType.OTHER,
            description = "validateToken",
            saveRequest = false,
            saveResponse = false)
    @Operation(
            operationId = "validateToken",
            summary = "校验存量 Token（内部）",
            description = "集群内服务直连接口，不经网关。对存量 Token 做语义校验，请求体为 TokenValidationRequest；"
                    + "正常完成校验时以 HTTP 200 返回，业务有效性体现在 data.valid 字段中，便于消费方按「拒绝优先」语义处理")
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
