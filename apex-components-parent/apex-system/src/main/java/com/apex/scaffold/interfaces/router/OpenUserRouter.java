package com.apex.scaffold.interfaces.router;

import com.apex.scaffold.interfaces.handler.OpenUserHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
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
 * 开放用户组织只读路由（/open-api/users）。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@Configuration
public class OpenUserRouter {

    /**
     * 注册开放用户只读路由。
     *
     * @param handler 开放用户处理器
     * @return 路由函数
     */
    @RouterOperations({
        @RouterOperation(
                path = "/open-api/users",
                method = RequestMethod.GET,
                beanClass = OpenUserHandler.class,
                beanMethod = "list",
                operation =
                        @Operation(
                                operationId = "openUserList",
                                summary = "开放接口-分页查询租户用户",
                                description = "仅返回 id/username/nickname/deptId/status 的最小字段集，不返回手机号与邮箱",
                                tags = {"开放用户"},
                                parameters = {
                                    @Parameter(
                                            name = "pageNum",
                                            in = ParameterIn.QUERY,
                                            description = "页码，默认 1，越界自动收敛"),
                                    @Parameter(
                                            name = "pageSize",
                                            in = ParameterIn.QUERY,
                                            description = "每页条数，默认 20，上限 100")
                                },
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "查询成功，data.rows 为用户精简列表",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)),
                                    @ApiResponse(responseCode = "401", description = "开放令牌无效或缺失"),
                                    @ApiResponse(responseCode = "500", description = "服务端异常")
                                }))
    })
    @Bean
    public RouterFunction<ServerResponse> openUserRoutes(OpenUserHandler handler) {
        return RouterFunctions.route().GET("/open-api/users", handler::list).build();
    }
}
