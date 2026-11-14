package com.apex.scaffold.interfaces.router;

import com.apex.scaffold.interfaces.handler.TenantPackageHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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
 * 租户套餐管理路由（方案批次 B6）。
 *
 * <p>{@code /api/v2/tenant-packages}：套餐 CRUD（平台 ADMIN 专属权限码
 * {@code tenant:package:read/create/update}，种子随 V79）。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Configuration
public class TenantPackageRouter {

    /**
     * 注册套餐管理路由.
     *
     * @param handler 套餐管理 Handler
     * @return 路由函数
     */
    @Bean
    @RouterOperations({
        @RouterOperation(
                path = "/api/v2/tenant-packages",
                method = RequestMethod.GET,
                beanClass = TenantPackageHandler.class,
                beanMethod = "listPackages",
                operation =
                        @Operation(
                                operationId = "listTenantPackages",
                                summary = "查询套餐列表",
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "Result 封装的套餐列表",
                                            content =
                                                    @io.swagger.v3.oas.annotations.media.Content(
                                                            mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/api/v2/tenant-packages/{packageCode}",
                method = RequestMethod.GET,
                beanClass = TenantPackageHandler.class,
                beanMethod = "getPackage",
                operation =
                        @Operation(
                                operationId = "getTenantPackage",
                                summary = "查询套餐详情",
                                responses = {
                                    @ApiResponse(responseCode = "200", description = "套餐详情"),
                                    @ApiResponse(responseCode = "404", description = "套餐不存在")
                                })),
        @RouterOperation(
                path = "/api/v2/tenant-packages",
                method = RequestMethod.POST,
                beanClass = TenantPackageHandler.class,
                beanMethod = "createPackage",
                operation =
                        @Operation(
                                operationId = "createTenantPackage",
                                summary = "创建套餐",
                                responses = {
                                    @ApiResponse(responseCode = "200", description = "已创建套餐"),
                                    @ApiResponse(responseCode = "400", description = "编码冲突或参数非法")
                                })),
        @RouterOperation(
                path = "/api/v2/tenant-packages",
                method = RequestMethod.PUT,
                beanClass = TenantPackageHandler.class,
                beanMethod = "updatePackage",
                operation =
                        @Operation(
                                operationId = "updateTenantPackage",
                                summary = "更新套餐",
                                responses = {
                                    @ApiResponse(responseCode = "200", description = "已更新套餐"),
                                    @ApiResponse(responseCode = "404", description = "套餐不存在")
                                }))
    })
    public RouterFunction<ServerResponse> tenantPackageRoutes(TenantPackageHandler handler) {
        return RouterFunctions.route()
                .GET(
                        "/api/v2/tenant-packages",
                        RequestPredicates.accept(MediaType.APPLICATION_JSON),
                        handler::listPackages)
                .GET(
                        "/api/v2/tenant-packages/{packageCode}",
                        RequestPredicates.accept(MediaType.APPLICATION_JSON),
                        handler::getPackage)
                .POST(
                        "/api/v2/tenant-packages",
                        RequestPredicates.accept(MediaType.APPLICATION_JSON),
                        handler::createPackage)
                .PUT(
                        "/api/v2/tenant-packages",
                        RequestPredicates.accept(MediaType.APPLICATION_JSON),
                        handler::updatePackage)
                .build();
    }
}
