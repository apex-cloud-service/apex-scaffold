package com.apex.scaffold.interfaces.router;

import static org.springframework.web.reactive.function.server.RequestPredicates.contentType;

import com.apex.scaffold.interfaces.handler.InternalOrganizationHandler;
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
 * 组织目录内部接口路由（/internal/v1/org）。
 *
 * <p>路径由 {@code SystemConstants.INTERNAL_ORG_PATHS} 登记，安全链对该前缀放行，
 * 租户由 {@code X-Tenant-Id} 头声明并在 {@code SecurityConfig} 的桥接过滤器中
 * 写入 Reactor Context。该路由只服务集群内直连调用，不经网关暴露。</p>
 *
 * @author Apex Team
 * @since 2.6.0
 */
@Configuration
public class InternalOrganizationRouter {

    /**
     * 组织目录内部接口路由
     *
     * <p>注册 {@code POST /internal/v1/org/users/resolve}，通过请求头 {@code X-Tenant-Id} 指定目标租户，
     * 委托 {@code InternalOrganizationHandler#resolveUsers} 批量解析真实系统用户。
     *
     * @param handler 组织目录内部接口处理器
     * @return 组织目录内部接口路由函数
     */
    @RouterOperations({
        @RouterOperation(
                path = "/internal/v1/org/users/resolve",
                method = RequestMethod.POST,
                beanClass = InternalOrganizationHandler.class,
                beanMethod = "resolveUsers",
                operation =
                        @Operation(
                                summary = "批量解析真实系统用户",
                                description = "按手机号、邮箱或登录名把外部通讯录身份解析为本租户真实系统用户；仅集群内调用",
                                tags = {"内部-组织目录"},
                                parameters = {
                                    @Parameter(
                                            name = "X-Tenant-Id",
                                            description = "目标租户 ID",
                                            required = true,
                                            in = ParameterIn.HEADER)
                                },
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "解析完成",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                }))
    })
    @Bean
    public RouterFunction<ServerResponse> internalOrganizationRoutes(InternalOrganizationHandler handler) {
        return RouterFunctions.route()
                .path(
                        "/internal/v1/org",
                        routes -> routes.POST(
                                "/users/resolve", contentType(MediaType.APPLICATION_JSON), handler::resolveUsers))
                .build();
    }
}
