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
 * @author Apex Team
 * @since 1.3.0
 */
@Configuration
@Tag(name = "办公平台审批", description = "办公平台审批流程管理接口")
public class OfficeApprovalRouter {
    private static final String BASE_PATH = "/api/office/approvals";

    /**
     * 注册审批路由。
     *
     * @param handler 审批处理器
     * @return 路由函数
     */
    @RouterOperations({
        @RouterOperation(
                path = BASE_PATH,
                method = RequestMethod.POST,
                beanClass = OfficeApprovalHandler.class,
                beanMethod = "initiateApproval"),
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
                beanMethod = "cancelApproval"),
        @RouterOperation(
                path = BASE_PATH + "/pending",
                method = RequestMethod.GET,
                beanClass = OfficeApprovalHandler.class,
                beanMethod = "getPendingApprovals"),
        @RouterOperation(
                path = BASE_PATH + "/initiated",
                method = RequestMethod.GET,
                beanClass = OfficeApprovalHandler.class,
                beanMethod = "getMyInitiatedApprovals")
    })
    @Bean
    public RouterFunction<ServerResponse> approvalRoutes(OfficeApprovalHandler handler) {
        return RouterFunctions.nest(
                path(BASE_PATH),
                RouterFunctions.route()
                        .POST("", accept(MediaType.APPLICATION_JSON), handler::initiateApproval)
                        .GET("/{processInstanceId}", handler::getApprovalDetail)
                        .POST(
                                "/{processInstanceId}/approve",
                                accept(MediaType.APPLICATION_JSON),
                                handler::approveProcess)
                        .DELETE("/{processInstanceId}", handler::cancelApproval)
                        .GET("/pending", handler::getPendingApprovals)
                        .GET("/initiated", handler::getMyInitiatedApprovals)
                        .build());
    }
}
