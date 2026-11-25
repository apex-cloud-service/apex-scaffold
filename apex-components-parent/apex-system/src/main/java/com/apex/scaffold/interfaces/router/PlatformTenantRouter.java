package com.apex.scaffold.interfaces.router;

import static org.springframework.web.reactive.function.server.RequestPredicates.contentType;

import com.apex.scaffold.interfaces.handler.PlatformTenantHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springdoc.core.annotations.RouterOperation;
import org.springdoc.core.annotations.RouterOperations;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

/**
 * 平台租户管理路由配置（/api/v2/platform/tenants）。
 *
 * <p>仅面向平台管理员，端点分别要求 {@code tenant:read/create/lifecycle/quota}
 * 权限。租户管理员不具备这些权限，无法访问平台级租户目录。</p>
 *
 * @author Apex Team
 * @since 1.3.2
 */
@Configuration
public class PlatformTenantRouter {

    /**
     * 平台租户管理路由
     *
     * <p>注册 {@code /api/v2/platform/tenants} 下的列表、详情与开通等端点，委托
     * {@code PlatformTenantHandler} 处理。
     *
     * @param handler 平台租户处理器
     * @return 平台租户管理路由函数
     */
    @RouterOperations({
        @RouterOperation(
                path = "/api/v2/platform/tenants",
                method = RequestMethod.GET,
                beanClass = PlatformTenantHandler.class,
                beanMethod = "page",
                operation =
                        @Operation(
                                summary = "分页查询租户目录",
                                description = "平台管理员分页查询租户目录，支持按编码与名称模糊匹配、按状态精确筛选",
                                tags = {"后台-租户管理"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "查询成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/api/v2/platform/tenants",
                method = RequestMethod.POST,
                beanClass = PlatformTenantHandler.class,
                beanMethod = "create",
                operation =
                        @Operation(
                                summary = "开通租户",
                                description = "创建新租户并设置初始配额；租户编码全局唯一",
                                tags = {"后台-租户管理"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "开通成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/api/v2/platform/tenants/{id}",
                method = RequestMethod.GET,
                beanClass = PlatformTenantHandler.class,
                beanMethod = "detail",
                operation =
                        @Operation(
                                summary = "查询租户详情",
                                description = "根据租户 ID 查询租户基础信息、状态与配额",
                                tags = {"后台-租户管理"},
                                parameters = {
                                    @Parameter(
                                            name = "id",
                                            description = "租户 ID",
                                            required = true,
                                            in = ParameterIn.PATH,
                                            schema = @Schema(type = "string"))
                                },
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "查询成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/api/v2/platform/tenants/{id}/actions",
                method = RequestMethod.POST,
                beanClass = PlatformTenantHandler.class,
                beanMethod = "changeStatus",
                operation =
                        @Operation(
                                summary = "执行租户状态动作",
                                description = "统一执行停用/启用/冻结/解冻/注销/归档动作，支持乐观锁版本校验",
                                tags = {"后台-租户管理"},
                                parameters = {
                                    @Parameter(
                                            name = "id",
                                            description = "租户 ID",
                                            required = true,
                                            in = ParameterIn.PATH,
                                            schema = @Schema(type = "string"))
                                },
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "动作执行成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/api/v2/platform/tenants/{id}/quota",
                method = RequestMethod.PUT,
                beanClass = PlatformTenantHandler.class,
                beanMethod = "updateQuota",
                operation =
                        @Operation(
                                summary = "更新租户配额",
                                description = "更新租户最大用户数与最大存储容量，支持乐观锁版本校验",
                                tags = {"后台-租户管理"},
                                parameters = {
                                    @Parameter(
                                            name = "id",
                                            description = "租户 ID",
                                            required = true,
                                            in = ParameterIn.PATH,
                                            schema = @Schema(type = "string"))
                                },
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "更新成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                }))
    })
    @Bean
    public RouterFunction<ServerResponse> platformTenantRoutes(PlatformTenantHandler handler) {
        return RouterFunctions.route()
                .path("/api/v2/platform/tenants", routes -> routes.GET("", handler::page)
                        .POST("", contentType(MediaType.APPLICATION_JSON), handler::create)
                        .GET("/{id}", handler::detail)
                        .POST("/{id}/actions", contentType(MediaType.APPLICATION_JSON), handler::changeStatus)
                        .PUT("/{id}/quota", contentType(MediaType.APPLICATION_JSON), handler::updateQuota))
                .build();
    }
}
