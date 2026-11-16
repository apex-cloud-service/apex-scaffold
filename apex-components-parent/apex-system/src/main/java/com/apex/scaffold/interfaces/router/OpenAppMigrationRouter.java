package com.apex.scaffold.interfaces.router;

import com.apex.scaffold.contract.dto.internal.OpenAppImportRequest;
import com.apex.scaffold.interfaces.handler.OpenAppMigrationHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
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
 * 存量开放应用迁移路由：平台管理员专用，导入为 JSON 数组。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@Configuration
public class OpenAppMigrationRouter {

    @Bean
    /**
     * 注册存量开放应用迁移路由。
     *
     * @param handler 迁移处理器
     * @return 路由函数
     */
    @RouterOperations({
        @RouterOperation(
                path = "/admin-api/v1/open-apps/migration/export",
                method = RequestMethod.GET,
                beanClass = OpenAppMigrationHandler.class,
                beanMethod = "export",
                operation =
                        @Operation(
                                operationId = "exportLegacyOpenApps",
                                summary = "导出待迁移的存量开放应用清单",
                                description = "返回待导入清单（含密码哈希，不含明文密钥），需平台管理员权限",
                                tags = {"开放应用迁移"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "导出成功，data 为待导入清单",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)),
                                    @ApiResponse(responseCode = "403", description = "权限不足，需要 open:app:manage"),
                                    @ApiResponse(responseCode = "500", description = "服务端异常")
                                })),
        @RouterOperation(
                path = "/admin-api/v1/open-apps/migration/import",
                method = RequestMethod.POST,
                beanClass = OpenAppMigrationHandler.class,
                beanMethod = "importApps",
                operation =
                        @Operation(
                                operationId = "importLegacyOpenApps",
                                summary = "批量导入存量开放应用",
                                description = "逐条写入并汇总成功与失败；单条失败不影响其余条目，失败项只含异常类型",
                                tags = {"开放应用迁移"},
                                requestBody =
                                        @RequestBody(
                                                required = true,
                                                description = "待导入的开放应用数组",
                                                content =
                                                        @Content(
                                                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                                array =
                                                                        @ArraySchema(
                                                                                schema =
                                                                                        @Schema(
                                                                                                implementation =
                                                                                                        OpenAppImportRequest
                                                                                                                .class)))),
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "导入完成，data 为成功/失败汇总",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)),
                                    @ApiResponse(responseCode = "400", description = "请求体为空或非 JSON 数组"),
                                    @ApiResponse(responseCode = "403", description = "权限不足，需要 open:app:manage"),
                                    @ApiResponse(responseCode = "500", description = "服务端异常")
                                }))
    })
    public RouterFunction<ServerResponse> openAppMigrationRoutes(OpenAppMigrationHandler handler) {
        return RouterFunctions.route()
                .GET("/admin-api/v1/open-apps/migration/export", handler::export)
                .POST(
                        "/admin-api/v1/open-apps/migration/import",
                        RequestPredicates.contentType(MediaType.APPLICATION_JSON),
                        handler::importApps)
                .build();
    }
}
