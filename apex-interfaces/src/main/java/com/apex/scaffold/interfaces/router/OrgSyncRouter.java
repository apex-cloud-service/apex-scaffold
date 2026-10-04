package com.apex.scaffold.interfaces.router;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;

import com.apex.scaffold.application.dto.DepartmentDTO;
import com.apex.scaffold.interfaces.handler.OrgSyncHandler;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import org.springdoc.core.annotations.RouterOperation;
import org.springdoc.core.annotations.RouterOperations;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

/**
 * 组织架构同步路由配置。
 *
 * <p>基于 WebFlux 函数式编程模型注册外部组织架构数据的管理端点，路由前缀为
 * {@code /admin-api/v1/org}，覆盖部门与员工的列表查询、详情、创建、更新、删除
 * 及组织架构同步触发端点，业务处理统一委托 {@link OrgSyncHandler}。
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.2.0
 * @see OrgSyncHandler
 */
@Configuration
public class OrgSyncRouter {

    @RouterOperations({
        @RouterOperation(
                path = "/admin-api/v1/org/departments",
                beanClass = OrgSyncHandler.class,
                beanMethod = "getDepartmentList"),
        @RouterOperation(
                path = "/admin-api/v1/org/departments/{id}",
                beanClass = OrgSyncHandler.class,
                beanMethod = "getDepartmentDetail"),
        @RouterOperation(
                path = "/admin-api/v1/org/departments",
                beanClass = OrgSyncHandler.class,
                beanMethod = "createDepartment"),
        @RouterOperation(
                path = "/admin-api/v1/org/departments/{id}",
                beanClass = OrgSyncHandler.class,
                beanMethod = "updateDepartment"),
        @RouterOperation(
                path = "/admin-api/v1/org/departments/{id}",
                beanClass = OrgSyncHandler.class,
                beanMethod = "deleteDepartment"),
        @RouterOperation(
                path = "/admin-api/v1/org/employees",
                beanClass = OrgSyncHandler.class,
                beanMethod = "getEmployeeList"),
        @RouterOperation(
                path = "/admin-api/v1/org/employees/{id}",
                beanClass = OrgSyncHandler.class,
                beanMethod = "getEmployeeDetail"),
        @RouterOperation(
                path = "/admin-api/v1/org/sync-history",
                beanClass = OrgSyncHandler.class,
                beanMethod = "getSyncHistory")
    })
    @Bean
    @SuppressWarnings("PMD.MethodTooLongRule")
    public RouterFunction<ServerResponse> orgSyncRoutes(OrgSyncHandler handler) {
        return SpringdocRouteBuilder.route()
                .GET("/admin-api/v1/org/departments", handler::getDepartmentList, ops -> ops.operationId(
                                "getDepartmentList")
                        .summary("获取部门列表")
                        .description("分页获取部门列表，支持按状态筛选")
                        .tag("组织同步管理")
                        .parameter(parameterBuilder()
                                .name("pageNum")
                                .description("页码")
                                .in(ParameterIn.QUERY)
                                .required(false)
                                .example("1"))
                        .parameter(parameterBuilder()
                                .name("pageSize")
                                .description("每页数量")
                                .in(ParameterIn.QUERY)
                                .required(false)
                                .example("10"))
                        .parameter(parameterBuilder()
                                .name("status")
                                .description("部门状态，可选值：ACTIVE（启用）、INACTIVE（停用）")
                                .in(ParameterIn.QUERY)
                                .required(false)
                                .example("ACTIVE"))
                        .response(responseBuilder().responseCode("200").description("获取成功，返回部门分页列表"))
                        .response(responseBuilder().responseCode("400").description("参数错误"))
                        .response(responseBuilder().responseCode("401").description("未授权，需要先登录"))
                        .response(responseBuilder().responseCode("500").description("服务器内部错误")))
                .GET("/admin-api/v1/org/departments/{id}", handler::getDepartmentDetail, ops -> ops.operationId(
                                "getDepartmentDetail")
                        .summary("获取部门详情")
                        .description("根据部门ID获取部门详细信息，包括部门名称、上级部门、负责人等")
                        .tag("组织同步管理")
                        .parameter(parameterBuilder()
                                .name("id")
                                .description("部门ID")
                                .in(ParameterIn.PATH)
                                .required(true)
                                .example("1"))
                        .response(responseBuilder().responseCode("200").description("获取成功，返回部门详细信息"))
                        .response(responseBuilder().responseCode("400").description("参数错误，部门ID格式不正确"))
                        .response(responseBuilder().responseCode("404").description("部门不存在"))
                        .response(responseBuilder().responseCode("401").description("未授权，需要先登录")))
                .POST("/admin-api/v1/org/departments", handler::createDepartment, ops -> ops.operationId(
                                "createDepartment")
                        .summary("创建部门")
                        .description("创建新部门，需要提供部门名称、上级部门等信息")
                        .tag("组织同步管理")
                        .requestBody(requestBodyBuilder()
                                .required(true)
                                .description("部门信息")
                                .content(org.springdoc.core.fn.builders.content.Builder.contentBuilder()
                                        .mediaType(MediaType.APPLICATION_JSON_VALUE)
                                        .schema(org.springdoc.core.fn.builders.schema.Builder.schemaBuilder()
                                                .implementation(DepartmentDTO.class))))
                        .response(responseBuilder().responseCode("200").description("创建成功，返回新建部门信息"))
                        .response(responseBuilder().responseCode("400").description("参数错误或参数校验失败"))
                        .response(responseBuilder().responseCode("401").description("未授权，需要先登录"))
                        .response(responseBuilder().responseCode("500").description("服务器内部错误")))
                .PUT("/admin-api/v1/org/departments/{id}", handler::updateDepartment, ops -> ops.operationId(
                                "updateDepartment")
                        .summary("更新部门")
                        .description("更新指定部门的信息，可以修改部门名称、上级部门、负责人等")
                        .tag("组织同步管理")
                        .parameter(parameterBuilder()
                                .name("id")
                                .description("部门ID")
                                .in(ParameterIn.PATH)
                                .required(true)
                                .example("1"))
                        .requestBody(requestBodyBuilder()
                                .required(true)
                                .description("部门信息")
                                .content(org.springdoc.core.fn.builders.content.Builder.contentBuilder()
                                        .mediaType(MediaType.APPLICATION_JSON_VALUE)
                                        .schema(org.springdoc.core.fn.builders.schema.Builder.schemaBuilder()
                                                .implementation(DepartmentDTO.class))))
                        .response(responseBuilder().responseCode("200").description("更新成功，返回更新后的部门信息"))
                        .response(responseBuilder().responseCode("400").description("参数错误或参数校验失败"))
                        .response(responseBuilder().responseCode("404").description("部门不存在"))
                        .response(responseBuilder().responseCode("401").description("未授权，需要先登录"))
                        .response(responseBuilder().responseCode("500").description("服务器内部错误")))
                .DELETE("/admin-api/v1/org/departments/{id}", handler::deleteDepartment, ops -> ops.operationId(
                                "deleteDepartment")
                        .summary("删除部门")
                        .description("删除指定部门，注意：如果部门下有员工或子部门，可能无法删除")
                        .tag("组织同步管理")
                        .parameter(parameterBuilder()
                                .name("id")
                                .description("部门ID")
                                .in(ParameterIn.PATH)
                                .required(true)
                                .example("1"))
                        .response(responseBuilder().responseCode("200").description("删除成功"))
                        .response(responseBuilder().responseCode("400").description("参数错误，部门ID格式不正确"))
                        .response(responseBuilder().responseCode("404").description("部门不存在"))
                        .response(responseBuilder().responseCode("401").description("未授权，需要先登录"))
                        .response(responseBuilder().responseCode("500").description("服务器内部错误")))
                .GET("/admin-api/v1/org/employees", handler::getEmployeeList, ops -> ops.operationId("getEmployeeList")
                        .summary("获取员工列表")
                        .description("分页获取员工列表，支持按部门ID和状态筛选")
                        .tag("组织同步管理")
                        .parameter(parameterBuilder()
                                .name("pageNum")
                                .description("页码")
                                .in(ParameterIn.QUERY)
                                .required(false)
                                .example("1"))
                        .parameter(parameterBuilder()
                                .name("pageSize")
                                .description("每页数量")
                                .in(ParameterIn.QUERY)
                                .required(false)
                                .example("10"))
                        .parameter(parameterBuilder()
                                .name("deptId")
                                .description("部门ID，用于筛选指定部门的员工")
                                .in(ParameterIn.QUERY)
                                .required(false)
                                .example("1"))
                        .parameter(parameterBuilder()
                                .name("status")
                                .description("员工状态，可选值：ACTIVE（在职）、INACTIVE（离职）")
                                .in(ParameterIn.QUERY)
                                .required(false)
                                .example("ACTIVE"))
                        .response(responseBuilder().responseCode("200").description("获取成功，返回员工分页列表"))
                        .response(responseBuilder().responseCode("400").description("参数错误"))
                        .response(responseBuilder().responseCode("401").description("未授权，需要先登录"))
                        .response(responseBuilder().responseCode("500").description("服务器内部错误")))
                .GET("/admin-api/v1/org/employees/{id}", handler::getEmployeeDetail, ops -> ops.operationId(
                                "getEmployeeDetail")
                        .summary("获取员工详情")
                        .description("根据员工ID获取员工详细信息，包括员工姓名、部门、职位、联系方式等")
                        .tag("组织同步管理")
                        .parameter(parameterBuilder()
                                .name("id")
                                .description("员工ID")
                                .in(ParameterIn.PATH)
                                .required(true)
                                .example("1"))
                        .response(responseBuilder().responseCode("200").description("获取成功，返回员工详细信息"))
                        .response(responseBuilder().responseCode("400").description("参数错误，员工ID格式不正确"))
                        .response(responseBuilder().responseCode("404").description("员工不存在"))
                        .response(responseBuilder().responseCode("401").description("未授权，需要先登录")))
                .GET("/admin-api/v1/org/sync-history", handler::getSyncHistory, ops -> ops.operationId("getSyncHistory")
                        .summary("获取同步历史")
                        .description("分页获取组织架构同步历史记录，包括同步时间、同步来源、同步状态等信息")
                        .tag("组织同步管理")
                        .parameter(parameterBuilder()
                                .name("pageNum")
                                .description("页码")
                                .in(ParameterIn.QUERY)
                                .required(false)
                                .example("1"))
                        .parameter(parameterBuilder()
                                .name("pageSize")
                                .description("每页数量")
                                .in(ParameterIn.QUERY)
                                .required(false)
                                .example("10"))
                        .parameter(parameterBuilder()
                                .name("syncSource")
                                .description("同步来源，可选值：FEISHU（飞书）、DINGTALK（钉钉）、WEWORK（企业微信）")
                                .in(ParameterIn.QUERY)
                                .required(false)
                                .example("FEISHU"))
                        .parameter(parameterBuilder()
                                .name("syncStatus")
                                .description("同步状态，可选值：SUCCESS（成功）、FAILED（失败）、PROCESSING（处理中）")
                                .in(ParameterIn.QUERY)
                                .required(false)
                                .example("SUCCESS"))
                        .response(responseBuilder().responseCode("200").description("获取成功，返回同步历史分页列表"))
                        .response(responseBuilder().responseCode("400").description("参数错误"))
                        .response(responseBuilder().responseCode("401").description("未授权，需要先登录"))
                        .response(responseBuilder().responseCode("500").description("服务器内部错误")))
                .build();
    }
}
