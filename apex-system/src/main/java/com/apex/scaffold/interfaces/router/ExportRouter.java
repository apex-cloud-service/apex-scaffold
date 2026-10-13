package com.apex.scaffold.interfaces.router;

import com.apex.scaffold.interfaces.handler.ExcelExportHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
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
 * 导出路由配置（/admin-api/v1/export）。
 *
 * <p>由原 {@code ApiRouter} 按子域拆分而来。</p>
 *
 * @author Apex Team
 * @since 1.3.0
 */
@Configuration
public class ExportRouter {

    /**
     * 数据导出路由（/admin-api/v1/export/users）。
     *
     * <p>提供 Excel 导出端点（C5），经 {@code RequirePermissionAspect}
     * 校验 {@code system:user:list} 权限。</p>
     *
     * @param handler Excel 导出处理器
     * @return 导出路由函数
     * @since 2.2.0
     */
    @RouterOperations({
        @RouterOperation(
                path = "/admin-api/v1/export/users",
                method = RequestMethod.GET,
                beanClass = ExcelExportHandler.class,
                beanMethod = "exportUsers",
                operation =
                        @Operation(
                                summary = "导出用户列表",
                                description = "按条件导出用户列表为 Excel 文件",
                                tags = {"数据导出"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                }))
    })
    @Bean
    public RouterFunction<ServerResponse> adminExportRoutes(ExcelExportHandler handler) {
        return RouterFunctions.route()
                .GET("/admin-api/v1/export/users", handler::exportUsers)
                .build();
    }
}
