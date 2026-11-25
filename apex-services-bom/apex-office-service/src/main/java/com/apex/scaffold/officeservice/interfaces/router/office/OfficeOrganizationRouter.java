package com.apex.scaffold.officeservice.interfaces.router.office;

import static org.springframework.web.reactive.function.server.RequestPredicates.*;

import com.apex.scaffold.officeservice.interfaces.handler.office.OfficeOrganizationHandler;
import lombok.RequiredArgsConstructor;
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
 * 办公平台组织同步路由，接口契约由对应处理方法声明。
 *
 * <p>同步接口接受 JSON 请求体；映射查询按外部平台、组织标识或用户标识定位，
 * 同步任务查询用于查看组织同步历史。路由层只负责路径和请求方法匹配，租户及权限校验由处理器完成。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Configuration
@RequiredArgsConstructor
public class OfficeOrganizationRouter {

    private static final String BASE_PATH = "/api/v1/office/organization";
    private final OfficeOrganizationHandler handler;

    /**
     * 注册组织同步、映射查询和同步历史接口。
     *
     * <p>同步写接口限定为 JSON 请求，查询接口直接交由处理器完成参数解析和响应映射。
     *
     * <p>处理器通过构造器注入而非 Bean 方法参数获取；租户取自请求头，路由层不做过滤。
     *
     * @return 组织同步路由，挂载于 {@code /api/v1/office/organization}
     */
    @RouterOperations({
        @RouterOperation(
                path = BASE_PATH + "/sync",
                method = RequestMethod.POST,
                beanClass = OfficeOrganizationHandler.class,
                beanMethod = "syncOrganization"),
        @RouterOperation(
                path = BASE_PATH + "/mappings/department/{platform}/{externalOrgId}",
                method = RequestMethod.GET,
                beanClass = OfficeOrganizationHandler.class,
                beanMethod = "getDepartmentMapping"),
        @RouterOperation(
                path = BASE_PATH + "/mappings/member/{platform}/{externalUserId}",
                method = RequestMethod.GET,
                beanClass = OfficeOrganizationHandler.class,
                beanMethod = "getMemberMapping"),
        @RouterOperation(
                path = BASE_PATH + "/mappings",
                method = RequestMethod.GET,
                beanClass = OfficeOrganizationHandler.class,
                beanMethod = "listOrganizationMappings"),
        @RouterOperation(
                path = BASE_PATH + "/sync/tasks",
                method = RequestMethod.GET,
                beanClass = OfficeOrganizationHandler.class,
                beanMethod = "listSyncTasks")
    })
    @Bean
    public RouterFunction<ServerResponse> officeOrganizationRoutes() {
        return RouterFunctions.route()
                .path(BASE_PATH, builder -> builder.POST(
                                "/sync", accept(MediaType.APPLICATION_JSON), handler::syncOrganization)
                        .GET("/mappings/department/{platform}/{externalOrgId}", handler::getDepartmentMapping)
                        .GET("/mappings/member/{platform}/{externalUserId}", handler::getMemberMapping)
                        .GET("/mappings", handler::listOrganizationMappings)
                        .GET("/sync/tasks", handler::listSyncTasks))
                .build();
    }
}
