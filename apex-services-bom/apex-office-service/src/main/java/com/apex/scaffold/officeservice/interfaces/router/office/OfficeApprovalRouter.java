package com.apex.scaffold.officeservice.interfaces.router.office;

import static org.springframework.web.reactive.function.server.RequestPredicates.*;

import com.apex.scaffold.officeservice.interfaces.handler.office.OfficeApprovalHandler;
import io.swagger.v3.oas.annotations.tags.Tag;
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
 * 办公平台审批路由，接口契约由关联的处理器方法声明。
 *
 * <p>接口统一挂载在 {@code /api/office/approvals} 下，支持发起、详情查询、审批、取消以及
 * 待办、已办和本人发起列表查询。发起与审批操作要求 JSON 请求体，审批状态和权限规则由处理器及应用服务负责。
 *
 * @author Apex Team
 * @version 1.3.0
 * @since 1.3.0
 */
@Configuration
@Tag(name = "办公平台审批", description = "办公平台审批流程管理接口")
public class OfficeApprovalRouter {
    private static final String BASE_PATH = "/api/office/approvals";

    /**
     * 注册审批路由。
     *
     * <p>待办和发起列表等静态路径在流程实例标识路径之前注册，避免被通用详情路径误匹配；
     * 审批处理器负责请求校验、当前用户权限判断和业务状态流转。
     *
     * @param handler 审批处理器
     * @return 路由函数，挂载于 {@code /api/office/approvals}
     */
    @RouterOperations({
        @RouterOperation(
                path = BASE_PATH,
                method = RequestMethod.POST,
                beanClass = OfficeApprovalHandler.class,
                beanMethod = "initiateApproval"),
        @RouterOperation(
                path = BASE_PATH + "/pending",
                method = RequestMethod.GET,
                beanClass = OfficeApprovalHandler.class,
                beanMethod = "getPendingApprovals"),
        @RouterOperation(
                path = BASE_PATH + "/completed",
                method = RequestMethod.GET,
                beanClass = OfficeApprovalHandler.class,
                beanMethod = "getCompletedApprovals"),
        @RouterOperation(
                path = BASE_PATH + "/initiated",
                method = RequestMethod.GET,
                beanClass = OfficeApprovalHandler.class,
                beanMethod = "getMyInitiatedApprovals"),
        @RouterOperation(
                path = BASE_PATH + "/{processInstanceId}",
                method = RequestMethod.GET,
                beanClass = OfficeApprovalHandler.class,
                beanMethod = "getApprovalDetail"),
        @RouterOperation(
                path = BASE_PATH + "/{processInstanceId}/approve",
                method = RequestMethod.POST,
                beanClass = OfficeApprovalHandler.class,
                beanMethod = "approveProcess"),
        @RouterOperation(
                path = BASE_PATH + "/{processInstanceId}",
                method = RequestMethod.DELETE,
                beanClass = OfficeApprovalHandler.class,
                beanMethod = "cancelApproval")
    })
    @Bean
    public RouterFunction<ServerResponse> approvalRoutes(OfficeApprovalHandler handler) {
        return RouterFunctions.nest(
                path(BASE_PATH),
                RouterFunctions.route()
                        .POST("", accept(MediaType.APPLICATION_JSON), handler::initiateApproval)
                        .GET("/pending", handler::getPendingApprovals)
                        .GET("/completed", handler::getCompletedApprovals)
                        .GET("/initiated", handler::getMyInitiatedApprovals)
                        .GET("/{processInstanceId}", handler::getApprovalDetail)
                        .POST(
                                "/{processInstanceId}/approve",
                                accept(MediaType.APPLICATION_JSON),
                                handler::approveProcess)
                        .DELETE("/{processInstanceId}", handler::cancelApproval)
                        .build());
    }
}
