package com.apex.scaffold.authservice.interfaces.handler;

import com.apex.scaffold.auth.application.service.AuthOpenAppRegistryService;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.internal.InternalServiceIdentity;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.contract.dto.internal.OpenAppImportRequest;
import com.apex.scaffold.contract.dto.internal.OpenAppRegistrationRequest;
import com.apex.scaffold.contract.dto.internal.OpenAppStatusRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * 开放应用注册内部契约：只接受已验签的 system 管理服务调用。
 *
 * <p>签名身份与租户来自 {@link InternalServiceIdentity}（过滤器写入的 exchange attribute），
 * 请求体中的租户不被信任；主体账号预验证标记也仅在调用方为 system 时才可使用。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@Component
@RequiredArgsConstructor
@Tag(name = "内部契约-开放应用注册", description = "仅供已验签的 system 管理服务调用；租户取自签名身份，请求体租户不被信任")
public class InternalOpenAppRegistryHandler {

    /** 开放应用注册信息的统一响应结构，仅用于接口文档。 */
    @Schema(name = "InternalOpenAppRegistrationResult")
    public static class OpenAppResult extends Result<Object> {}

    /** 开放应用列表的统一响应结构，仅用于接口文档。 */
    @Schema(name = "InternalOpenAppListResult")
    public static class OpenAppListResult extends Result<java.util.List<Object>> {}

    private static final String SYSTEM = "apex-system-service";

    /** 内部契约统一的 403 说明。 */
    private static final String FORBIDDEN_DESC = "调用方非已验签的 system 服务，或缺少可信租户";

    private final AuthOpenAppRegistryService registry;

    /**
     * 创建开放应用。
     *
     * @param request 服务器请求，请求体为 {@link OpenAppRegistrationRequest}
     * @return 统一响应，{@code data} 为注册结果
     */
    @Operation(
            operationId = "internalCreateOpenApp",
            summary = "创建开放应用（内部）",
            description = "内部契约接口，不经网关；租户取自签名身份，请求体中的租户不被信任")
    @RequestBody(
            required = true,
            description = "开放应用注册请求",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = OpenAppRegistrationRequest.class)))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "创建成功，data 为注册结果",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = OpenAppResult.class))),
        @ApiResponse(responseCode = "400", description = "请求体缺失或参数校验失败"),
        @ApiResponse(responseCode = "403", description = FORBIDDEN_DESC)
    })
    public Mono<ServerResponse> create(ServerRequest request) {
        return trusted(request, identity -> request.bodyToMono(OpenAppRegistrationRequest.class)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "请求体缺失")))
                .flatMap(body -> registry.create(body))
                .flatMap(this::ok));
    }

    /**
     * 更新开放应用。
     *
     * @param request 服务器请求，路径参数 {@code clientId}
     * @return 统一响应，{@code data} 为更新后的注册信息
     */
    @Operation(
            operationId = "internalUpdateOpenApp",
            summary = "更新开放应用（内部）",
            description = "内部契约接口，不经网关；按 clientId 整体更新注册信息")
    @Parameter(
            name = "clientId",
            in = ParameterIn.PATH,
            required = true,
            description = "开放应用客户端标识",
            schema = @Schema(type = "string"))
    @RequestBody(
            required = true,
            description = "开放应用注册请求",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = OpenAppRegistrationRequest.class)))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "更新成功，data 为更新后的注册信息",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = OpenAppResult.class))),
        @ApiResponse(responseCode = "400", description = "请求体缺失或参数校验失败"),
        @ApiResponse(responseCode = "404", description = "开放应用不存在"),
        @ApiResponse(responseCode = "403", description = FORBIDDEN_DESC)
    })
    public Mono<ServerResponse> update(ServerRequest request) {
        return trusted(request, identity -> request.bodyToMono(OpenAppRegistrationRequest.class)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "请求体缺失")))
                .flatMap(body -> registry.update(request.pathVariable("clientId"), body))
                .flatMap(this::ok));
    }

    /**
     * 查询单个开放应用。
     *
     * @param request 服务器请求，路径参数 {@code clientId}
     * @return 统一响应，{@code data} 为注册信息
     */
    @Operation(
            operationId = "internalGetOpenApp",
            summary = "查询单个开放应用（内部）",
            description = "内部契约接口，不经网关；按 clientId 返回注册信息")
    @Parameter(
            name = "clientId",
            in = ParameterIn.PATH,
            required = true,
            description = "开放应用客户端标识",
            schema = @Schema(type = "string"))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "查询成功，data 为注册信息",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = OpenAppResult.class))),
        @ApiResponse(responseCode = "404", description = "开放应用不存在"),
        @ApiResponse(responseCode = "403", description = FORBIDDEN_DESC)
    })
    public Mono<ServerResponse> get(ServerRequest request) {
        return trusted(request, identity -> registry.get(request.pathVariable("clientId"))
                .flatMap(this::ok));
    }

    /**
     * 分页查询开放应用。
     *
     * @param request 服务器请求（query 参数 page/size）
     * @return 统一响应，{@code data} 为应用列表
     */
    @Operation(
            operationId = "internalListOpenApps",
            summary = "分页查询开放应用（内部）",
            description = "内部契约接口，不经网关；page 缺省 0，size 缺省 20")
    @Parameter(
            name = "page",
            in = ParameterIn.QUERY,
            description = "页码，缺省 0",
            schema = @Schema(type = "integer", format = "int32", defaultValue = "0", minimum = "0"))
    @Parameter(
            name = "size",
            in = ParameterIn.QUERY,
            description = "每页数量，缺省 20",
            schema = @Schema(type = "integer", format = "int32", defaultValue = "20", minimum = "1"))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "查询成功，data 为应用列表",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = OpenAppListResult.class))),
        @ApiResponse(responseCode = "400", description = "分页参数非法"),
        @ApiResponse(responseCode = "403", description = FORBIDDEN_DESC)
    })
    public Mono<ServerResponse> list(ServerRequest request) {
        return trusted(request, identity -> {
            int page = parse(request.queryParam("page").orElse("0"));
            int size = parse(request.queryParam("size").orElse("20"));
            return registry.list(page, size).collectList().flatMap(this::ok);
        });
    }

    /**
     * 启用或停用开放应用。
     *
     * @param request 服务器请求，请求体为 {@link OpenAppStatusRequest}
     * @return 统一响应，{@code data} 为更新后的注册信息
     */
    @Operation(
            operationId = "internalUpdateOpenAppStatus",
            summary = "启用或停用开放应用（内部）",
            description = "内部契约接口，不经网关；按 clientId 切换启用状态")
    @Parameter(
            name = "clientId",
            in = ParameterIn.PATH,
            required = true,
            description = "开放应用客户端标识",
            schema = @Schema(type = "string"))
    @RequestBody(
            required = true,
            description = "开放应用状态请求",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = OpenAppStatusRequest.class)))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "状态更新成功，data 为更新后的注册信息",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = OpenAppResult.class))),
        @ApiResponse(responseCode = "400", description = "请求体缺失"),
        @ApiResponse(responseCode = "404", description = "开放应用不存在"),
        @ApiResponse(responseCode = "403", description = FORBIDDEN_DESC)
    })
    public Mono<ServerResponse> status(ServerRequest request) {
        return trusted(request, identity -> request.bodyToMono(OpenAppStatusRequest.class)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "请求体缺失")))
                .flatMap(body -> registry.status(request.pathVariable("clientId"), body.getEnabled()))
                .flatMap(this::ok));
    }

    /**
     * 轮换开放应用密钥，新密钥只在本次响应中出现。
     *
     * @param request 服务器请求，路径参数 {@code clientId}
     * @return 统一响应，{@code data.clientSecret} 为新明文密钥
     */
    @Operation(
            operationId = "internalRotateOpenAppSecret",
            summary = "轮换开放应用密钥（内部）",
            description = "内部契约接口，不经网关；新密钥只在本次响应中出现，旧密钥立即失效")
    @Parameter(
            name = "clientId",
            in = ParameterIn.PATH,
            required = true,
            description = "开放应用客户端标识",
            schema = @Schema(type = "string"))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "轮换成功，data.clientSecret 为新明文密钥（仅本次返回）",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = OpenAppResult.class))),
        @ApiResponse(responseCode = "404", description = "开放应用不存在"),
        @ApiResponse(responseCode = "403", description = FORBIDDEN_DESC)
    })
    public Mono<ServerResponse> rotateSecret(ServerRequest request) {
        return trusted(request, identity -> registry.rotateSecret(request.pathVariable("clientId"))
                .flatMap(this::ok));
    }

    /**
     * 删除开放应用。
     *
     * @param request 服务器请求，路径参数 {@code clientId}
     * @return 统一响应，{@code data} 为空
     */
    @Operation(operationId = "internalDeleteOpenApp", summary = "删除开放应用（内部）", description = "内部契约接口，不经网关；删除后 data 为空")
    @Parameter(
            name = "clientId",
            in = ParameterIn.PATH,
            required = true,
            description = "开放应用客户端标识",
            schema = @Schema(type = "string"))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "删除成功，data 为空",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = OpenAppResult.class))),
        @ApiResponse(responseCode = "404", description = "开放应用不存在"),
        @ApiResponse(responseCode = "403", description = FORBIDDEN_DESC)
    })
    public Mono<ServerResponse> delete(ServerRequest request) {
        return trusted(request, identity -> registry.delete(request.pathVariable("clientId"))
                .then(ok(Result.success())));
    }

    /**
     * 导入一条旧注册源开放应用。
     *
     * @param request 服务器请求，请求体为 {@link OpenAppImportRequest}
     * @return 统一响应，{@code data} 为注册信息
     */
    @Operation(
            operationId = "internalImportOpenApp",
            summary = "导入旧注册源开放应用（内部）",
            description = "内部契约接口，不经网关；用于迁移旧注册源中的开放应用记录")
    @RequestBody(
            required = true,
            description = "开放应用导入请求",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = OpenAppImportRequest.class)))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "导入成功，data 为注册信息",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = OpenAppResult.class))),
        @ApiResponse(responseCode = "400", description = "请求体缺失或参数校验失败"),
        @ApiResponse(responseCode = "403", description = FORBIDDEN_DESC)
    })
    public Mono<ServerResponse> importApp(ServerRequest request) {
        return trusted(request, identity -> request.bodyToMono(OpenAppImportRequest.class)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "请求体缺失")))
                .flatMap(body -> registry.importApp(body))
                .flatMap(this::ok));
    }

    private <T> Mono<ServerResponse> trusted(
            ServerRequest request, java.util.function.Function<InternalServiceIdentity, Mono<ServerResponse>> action) {
        return Mono.defer(() -> {
            Object raw = request.attributes().get(InternalServiceIdentity.ATTR_IDENTITY);
            if (!(raw instanceof InternalServiceIdentity identity)
                    || !SYSTEM.equals(identity.serviceName())
                    || identity.tenantId() == null
                    || identity.tenantId().isBlank()) {
                return Mono.error(new BusinessException(ResultCode.FORBIDDEN, "内部注册仅允许已签名 system 调用"));
            }
            return action.apply(identity);
        });
    }

    private Mono<ServerResponse> ok(Object data) {
        return ServerResponse.ok().bodyValue(Result.success(data));
    }

    private int parse(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException error) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "分页参数非法");
        }
    }
}
