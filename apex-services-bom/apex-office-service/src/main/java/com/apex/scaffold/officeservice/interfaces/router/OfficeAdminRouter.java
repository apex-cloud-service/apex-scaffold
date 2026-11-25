package com.apex.scaffold.officeservice.interfaces.router;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.content.Builder.contentBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;
import static org.springdoc.core.fn.builders.schema.Builder.schemaBuilder;

import com.apex.scaffold.officeservice.interfaces.dto.office.OfficeAppRequest;
import com.apex.scaffold.officeservice.interfaces.dto.office.OfficeBindingReviewRequest;
import com.apex.scaffold.officeservice.interfaces.handler.OfficeAdminHandler;
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
 * 办公平台管理端路由，声明 {@code /admin-api/v1/office/**} 下的应用配置与身份绑定审批接口。
 *
 * <p>路由由两类职责组成：
 *
 * <ul>
 *   <li>办公应用配置 CRUD：维护第三方平台接入参数，删除需携带版本号实现乐观锁校验；
 *   <li>身份绑定审批：查询待审批绑定、审核通过/拒绝，以及对既有绑定执行解绑。
 * </ul>
 *
 * <p>所有端点均为管理端入口，权限与数据范围由安全配置及 Handler 内校验决定，
 * 不在路由层做租户过滤。路径使用 {@link SpringdocRouteBuilder} 链式声明 OpenAPI，
 * 与 {@code @RouterOperations} 注解存在重复，二者需保持一致以保证文档与实际路由匹配。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.2.0
 */
@Configuration
public class OfficeAdminRouter {
    /**
     * 配置办公平台管理端路由，包括应用配置和绑定审批。
     *
     * <p>端点数量较多且 OpenAPI 元数据内联声明，导致方法长度超过 PMD 阈值；
     * 抑制 {@code MethodTooLongRule} 而不拆分，以保持路由注册顺序与实际路径一致、便于审阅。
     * 仅承载路由表注册，鉴权、租户与数据范围校验全部下沉至处理器与安全配置。
     *
     * @param handler 办公平台管理端处理器
     * @return 管理端路由函数，挂载于 {@code /admin-api/v1/office}，含应用配置与绑定审批端点
     */
    @RouterOperations({
        @RouterOperation(
                path = "/admin-api/v1/office/apps",
                beanClass = OfficeAdminHandler.class,
                beanMethod = "listApps"),
        @RouterOperation(
                path = "/admin-api/v1/office/apps/{platform}",
                beanClass = OfficeAdminHandler.class,
                beanMethod = "getApp"),
        @RouterOperation(
                path = "/admin-api/v1/office/apps/{platform}",
                beanClass = OfficeAdminHandler.class,
                beanMethod = "createApp"),
        @RouterOperation(
                path = "/admin-api/v1/office/apps/{platform}",
                beanClass = OfficeAdminHandler.class,
                beanMethod = "updateApp"),
        @RouterOperation(
                path = "/admin-api/v1/office/apps/{platform}",
                beanClass = OfficeAdminHandler.class,
                beanMethod = "deleteApp"),
        @RouterOperation(
                path = "/admin-api/v1/office/bindings",
                beanClass = OfficeAdminHandler.class,
                beanMethod = "listBindings"),
        @RouterOperation(
                path = "/admin-api/v1/office/bindings/page",
                beanClass = OfficeAdminHandler.class,
                beanMethod = "listBindingsPage"),
        @RouterOperation(
                path = "/admin-api/v1/office/bindings/{id}/review",
                beanClass = OfficeAdminHandler.class,
                beanMethod = "reviewBinding"),
        @RouterOperation(
                path = "/admin-api/v1/office/bindings/{id}/unbind",
                beanClass = OfficeAdminHandler.class,
                beanMethod = "unbind")
    })
    @Bean
    @SuppressWarnings("PMD.MethodTooLongRule")
    public RouterFunction<ServerResponse> officeAdminRoutes(OfficeAdminHandler handler) {
        return SpringdocRouteBuilder.route()
                .GET("/admin-api/v1/office/apps", handler::listApps, ops -> ops.operationId("listOfficeApps")
                        .summary("查询办公平台应用列表")
                        .tag("办公平台管理")
                        .response(responseBuilder()
                                .responseCode("200")
                                .description("查询成功")
                                .content(contentBuilder()
                                        .mediaType(MediaType.APPLICATION_JSON_VALUE)
                                        .schema(schemaBuilder()
                                                .implementation(OfficeAdminHandler.AppListResult.class)))))
                .GET("/admin-api/v1/office/apps/{platform}", handler::getApp, ops -> ops.operationId("getOfficeApp")
                        .summary("查询指定平台应用配置")
                        .tag("办公平台管理")
                        .parameter(parameterBuilder()
                                .name("platform")
                                .description("平台类型")
                                .in(ParameterIn.PATH)
                                .required(true)
                                .example("FEISHU"))
                        .response(responseBuilder()
                                .responseCode("200")
                                .description("查询成功")
                                .content(contentBuilder()
                                        .mediaType(MediaType.APPLICATION_JSON_VALUE)
                                        .schema(schemaBuilder().implementation(OfficeAdminHandler.AppResult.class)))))
                .POST("/admin-api/v1/office/apps/{platform}", handler::createApp, ops -> ops.operationId(
                                "createOfficeApp")
                        .summary("创建办公平台应用配置")
                        .tag("办公平台管理")
                        .parameter(parameterBuilder()
                                .name("platform")
                                .description("平台类型")
                                .in(ParameterIn.PATH)
                                .required(true)
                                .example("FEISHU"))
                        .requestBody(requestBodyBuilder()
                                .description("应用配置信息")
                                .required(true)
                                .content(contentBuilder()
                                        .mediaType(MediaType.APPLICATION_JSON_VALUE)
                                        .schema(schemaBuilder().implementation(OfficeAppRequest.class))))
                        .response(responseBuilder()
                                .responseCode("200")
                                .description("创建成功")
                                .content(contentBuilder()
                                        .mediaType(MediaType.APPLICATION_JSON_VALUE)
                                        .schema(schemaBuilder().implementation(OfficeAdminHandler.AppResult.class)))))
                .PUT("/admin-api/v1/office/apps/{platform}", handler::updateApp, ops -> ops.operationId(
                                "updateOfficeApp")
                        .summary("更新办公平台应用配置")
                        .tag("办公平台管理")
                        .parameter(parameterBuilder()
                                .name("platform")
                                .description("平台类型")
                                .in(ParameterIn.PATH)
                                .required(true)
                                .example("FEISHU"))
                        .requestBody(requestBodyBuilder()
                                .description("应用配置信息")
                                .required(true)
                                .content(contentBuilder()
                                        .mediaType(MediaType.APPLICATION_JSON_VALUE)
                                        .schema(schemaBuilder().implementation(OfficeAppRequest.class))))
                        .response(responseBuilder()
                                .responseCode("200")
                                .description("更新成功")
                                .content(contentBuilder()
                                        .mediaType(MediaType.APPLICATION_JSON_VALUE)
                                        .schema(schemaBuilder().implementation(OfficeAdminHandler.AppResult.class)))))
                .DELETE("/admin-api/v1/office/apps/{platform}", handler::deleteApp, ops -> ops.operationId(
                                "deleteOfficeApp")
                        .summary("删除办公平台应用配置")
                        .tag("办公平台管理")
                        .parameter(parameterBuilder()
                                .name("platform")
                                .description("平台类型")
                                .in(ParameterIn.PATH)
                                .required(true)
                                .example("FEISHU"))
                        .parameter(parameterBuilder()
                                .name("version")
                                .description("乐观锁版本号")
                                .in(ParameterIn.QUERY)
                                .required(true)
                                .example("0"))
                        .response(responseBuilder()
                                .responseCode("200")
                                .description("删除成功")
                                .content(contentBuilder()
                                        .mediaType(MediaType.APPLICATION_JSON_VALUE)
                                        .schema(schemaBuilder()
                                                .implementation(OfficeAdminHandler.AdminBooleanResult.class)))))
                .GET("/admin-api/v1/office/bindings", handler::listBindings, ops -> ops.operationId(
                                "listOfficeBindings")
                        .summary("查询办公平台绑定列表")
                        .tag("办公平台管理")
                        .parameter(parameterBuilder()
                                .name("platform")
                                .description("平台类型")
                                .in(ParameterIn.QUERY)
                                .required(true)
                                .example("FEISHU"))
                        .parameter(parameterBuilder()
                                .name("page")
                                .description("页码")
                                .in(ParameterIn.QUERY)
                                .required(false)
                                .schema(schemaBuilder()
                                        .implementation(Integer.class)
                                        .defaultValue("1"))
                                .example("1"))
                        .parameter(parameterBuilder()
                                .name("size")
                                .description("每页数量")
                                .in(ParameterIn.QUERY)
                                .required(false)
                                .schema(schemaBuilder()
                                        .implementation(Integer.class)
                                        .defaultValue("20"))
                                .example("20"))
                        .response(responseBuilder()
                                .responseCode("200")
                                .description("查询成功")
                                .content(contentBuilder()
                                        .mediaType(MediaType.APPLICATION_JSON_VALUE)
                                        .schema(schemaBuilder()
                                                .implementation(OfficeAdminHandler.BindingListResult.class)))))
                .GET("/admin-api/v1/office/bindings/page", handler::listBindingsPage, ops -> ops.operationId(
                                "listOfficeBindingsPage")
                        .summary("分页查询办公平台绑定列表")
                        .tag("办公平台管理")
                        .parameter(parameterBuilder()
                                .name("platform")
                                .description("平台类型")
                                .in(ParameterIn.QUERY)
                                .required(true)
                                .example("FEISHU"))
                        .parameter(parameterBuilder()
                                .name("status")
                                .description("绑定状态（PENDING/APPROVED/REJECTED/REVOKED）")
                                .in(ParameterIn.QUERY)
                                .required(false)
                                .example("PENDING"))
                        .parameter(parameterBuilder()
                                .name("page")
                                .description("页码")
                                .in(ParameterIn.QUERY)
                                .required(false)
                                .schema(schemaBuilder()
                                        .implementation(Integer.class)
                                        .defaultValue("1"))
                                .example("1"))
                        .parameter(parameterBuilder()
                                .name("size")
                                .description("每页数量")
                                .in(ParameterIn.QUERY)
                                .required(false)
                                .schema(schemaBuilder()
                                        .implementation(Integer.class)
                                        .defaultValue("20"))
                                .example("20"))
                        .response(responseBuilder()
                                .responseCode("200")
                                .description("查询成功")
                                .content(contentBuilder()
                                        .mediaType(MediaType.APPLICATION_JSON_VALUE)
                                        .schema(schemaBuilder()
                                                .implementation(OfficeAdminHandler.BindingPageResult.class)))))
                .POST("/admin-api/v1/office/bindings/{id}/review", handler::reviewBinding, ops -> ops.operationId(
                                "reviewOfficeBinding")
                        .summary("审批身份绑定申请")
                        .tag("办公平台管理")
                        .parameter(parameterBuilder()
                                .name("id")
                                .description("绑定主键")
                                .in(ParameterIn.PATH)
                                .required(true)
                                .example("1001"))
                        .requestBody(requestBodyBuilder()
                                .description("审批决定")
                                .required(true)
                                .content(contentBuilder()
                                        .mediaType(MediaType.APPLICATION_JSON_VALUE)
                                        .schema(schemaBuilder().implementation(OfficeBindingReviewRequest.class))))
                        .response(responseBuilder()
                                .responseCode("200")
                                .description("审批成功")
                                .content(contentBuilder()
                                        .mediaType(MediaType.APPLICATION_JSON_VALUE)
                                        .schema(schemaBuilder()
                                                .implementation(OfficeAdminHandler.AdminBooleanResult.class)))))
                .DELETE("/admin-api/v1/office/bindings/{id}/unbind", handler::unbind, ops -> ops.operationId(
                                "unbindOfficeIdentity")
                        .summary("解绑办公平台身份")
                        .tag("办公平台管理")
                        .parameter(parameterBuilder()
                                .name("id")
                                .description("绑定主键")
                                .in(ParameterIn.PATH)
                                .required(true)
                                .example("1001"))
                        .parameter(parameterBuilder()
                                .name("version")
                                .description("乐观锁版本号")
                                .in(ParameterIn.QUERY)
                                .required(true)
                                .example("0"))
                        .response(responseBuilder()
                                .responseCode("200")
                                .description("解绑成功")
                                .content(contentBuilder()
                                        .mediaType(MediaType.APPLICATION_JSON_VALUE)
                                        .schema(schemaBuilder()
                                                .implementation(OfficeAdminHandler.AdminBooleanResult.class)))))
                .build();
    }
}
