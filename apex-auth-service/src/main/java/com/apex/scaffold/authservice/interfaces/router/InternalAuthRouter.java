package com.apex.scaffold.authservice.interfaces.router;

import com.apex.scaffold.authservice.interfaces.handler.InternalAuthHandler;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.contract.dto.internal.TokenValidationDTO;
import com.apex.scaffold.contract.dto.internal.TokenValidationRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.util.List;
import org.springdoc.core.annotations.RouterOperation;
import org.springdoc.core.annotations.RouterOperations;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

/**
 * 认证中心内部 API 路由配置（迁移方案 Phase 2.3）.
 *
 * <p>注册 {@code /internal/v1/**} 内部契约路由，对应 {@code apex-api-contract} 中
 * {@link com.apex.scaffold.contract.client.AuthFeignClient} 声明的两个接口：
 * 查询用户权限码、校验存量 Token。该组路由不经网关（网关层对 {@code /internal/v1/**}
 * 强制 403），仅允许集群内服务通过服务发现直连调用，统一返回 {@code Result} 包装体。
 *
 * @author Apex Team
 * @since 1.3.1
 */
@Configuration
public class InternalAuthRouter {

    /**
     * 注册内部认证路由.
     *
     * @param handler 内部认证处理器
     * @return 路由函数
     */
    @Bean
    @SuppressWarnings("PMD.MethodTooLongRule")
    @RouterOperations({
        @RouterOperation(
                path = "/internal/v1/users/{accountId}/permissions",
                method = RequestMethod.GET,
                beanClass = InternalAuthHandler.class,
                beanMethod = "getUserPermissions",
                operation =
                        @Operation(
                                operationId = "internalGetUserPermissions",
                                summary = "查询用户权限码（内部）",
                                description = "内部契约接口，不经网关。按 accountId（统一账户 ID）与 tenantId（租户）"
                                        + "展开用户的权限编码集合，返回 Result<List<String>>；非活跃/锁定/已删除"
                                        + "用户返回空列表。",
                                tags = {"内部契约"},
                                parameters = {
                                    @Parameter(
                                            name = "accountId",
                                            description = "统一账户 ID（正整数）",
                                            in = ParameterIn.PATH,
                                            required = true,
                                            schema =
                                                    @Schema(
                                                            implementation = Long.class,
                                                            type = "integer",
                                                            format = "int64",
                                                            minimum = "1")),
                                    @Parameter(
                                            name = "tenantId",
                                            description = "租户 ID（必填）",
                                            in = ParameterIn.QUERY,
                                            required = true,
                                            schema =
                                                    @Schema(
                                                            implementation = Long.class,
                                                            type = "integer",
                                                            format = "int64"))
                                },
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "Result 封装的权限编码列表",
                                            content =
                                                    @Content(
                                                            mediaType = "application/json",
                                                            schema =
                                                                    @Schema(
                                                                            implementation =
                                                                                    InternalPermissionsResult.class))),
                                    @ApiResponse(
                                            responseCode = "400",
                                            description = "参数非法",
                                            content =
                                                    @Content(
                                                            mediaType = "application/json",
                                                            schema =
                                                                    @Schema(
                                                                            implementation =
                                                                                    com.apex.scaffold.common.result
                                                                                            .Result.class))),
                                    @ApiResponse(
                                            responseCode = "500",
                                            description = "系统错误",
                                            content =
                                                    @Content(
                                                            mediaType = "application/json",
                                                            schema =
                                                                    @Schema(
                                                                            implementation =
                                                                                    com.apex.scaffold.common.result
                                                                                            .Result.class)))
                                })),
        @RouterOperation(
                path = "/internal/v1/tokens/validate",
                method = RequestMethod.POST,
                beanClass = InternalAuthHandler.class,
                beanMethod = "validateToken",
                operation =
                        @Operation(
                                operationId = "internalValidateToken",
                                summary = "校验存量 Token（内部）",
                                description = "内部契约接口，不经网关。对访问令牌执行语义校验（签名、有效期、撤销状态、"
                                        + "租户一致性），完成校验时以 200 返回，业务有效性体现在 data.valid 字段，"
                                        + "便于消费方按「拒绝优先」语义处理。",
                                tags = {"内部契约"},
                                requestBody =
                                        @RequestBody(
                                                required = true,
                                                description = "Token 校验请求",
                                                content =
                                                        @Content(
                                                                mediaType = "application/json",
                                                                schema =
                                                                        @Schema(
                                                                                implementation =
                                                                                        TokenValidationRequest.class))),
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "Result 封装的 Token 校验快照",
                                            content =
                                                    @Content(
                                                            mediaType = "application/json",
                                                            schema =
                                                                    @Schema(
                                                                            implementation =
                                                                                    TokenValidationResult.class))),
                                    @ApiResponse(
                                            responseCode = "400",
                                            description = "请求体缺失或非法",
                                            content =
                                                    @Content(
                                                            mediaType = "application/json",
                                                            schema =
                                                                    @Schema(
                                                                            implementation =
                                                                                    com.apex.scaffold.common.result
                                                                                            .Result.class))),
                                    @ApiResponse(
                                            responseCode = "500",
                                            description = "系统错误",
                                            content =
                                                    @Content(
                                                            mediaType = "application/json",
                                                            schema =
                                                                    @Schema(
                                                                            implementation =
                                                                                    com.apex.scaffold.common.result
                                                                                            .Result.class)))
                                })),
        @RouterOperation(
                path = "/internal/v1/tokens/revocations",
                method = RequestMethod.GET,
                beanClass = InternalAuthHandler.class,
                beanMethod = "tokenRevocations",
                operation =
                        @Operation(
                                operationId = "internalTokenRevocations",
                                summary = "令牌撤销增量流（内部）",
                                description = "内部契约接口，不经网关。按撤销时间游标返回已撤销令牌的 jti 增量"
                                        + "（统一认证收口 A3），供网关建立本地撤销缓存；查询参数 since 必填、"
                                        + "limit 可选（默认 500）。",
                                tags = {"内部契约"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "Result 封装的撤销增量与下一游标",
                                            content =
                                                    @Content(
                                                            mediaType = "application/json",
                                                            schema =
                                                                    @Schema(
                                                                            implementation =
                                                                                    com.apex.scaffold.common.result
                                                                                            .Result.class))),
                                    @ApiResponse(
                                            responseCode = "400",
                                            description = "游标缺失或非法",
                                            content =
                                                    @Content(
                                                            mediaType = "application/json",
                                                            schema =
                                                                    @Schema(
                                                                            implementation =
                                                                                    com.apex.scaffold.common.result
                                                                                            .Result.class)))
                                }))
    })
    public RouterFunction<ServerResponse> internalAuthRoutes(InternalAuthHandler handler) {
        return RouterFunctions.route()
                .GET(
                        "/internal/v1/users/{accountId}/permissions",
                        RequestPredicates.accept(MediaType.APPLICATION_JSON),
                        handler::getUserPermissions)
                .POST(
                        "/internal/v1/tokens/validate",
                        RequestPredicates.accept(MediaType.APPLICATION_JSON),
                        handler::validateToken)
                .GET("/internal/v1/tokens/revocations", handler::tokenRevocations)
                .build();
    }

    /** 内部令牌校验的实际 Result 包装，仅用于 OpenAPI 建模。 */
    @Schema(name = "TokenValidationResult", description = "Result 包装的令牌校验快照")
    public static class TokenValidationResult extends Result<TokenValidationDTO> {}

    /** 内部权限查询的实际 Result 包装，仅用于 OpenAPI 建模。 */
    @Schema(name = "InternalPermissionsResult", description = "Result 包装的权限编码数组")
    public static class InternalPermissionsResult extends Result<List<String>> {}
}
