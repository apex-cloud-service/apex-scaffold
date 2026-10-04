package com.apex.scaffold.interfaces.router.office;

import static org.springframework.web.reactive.function.server.RequestPredicates.*;

import com.apex.scaffold.interfaces.handler.office.OfficeOrganizationHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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
 * 办公平台组织同步路由配置
 *
 * <p>定义组织架构同步相关的路由规则，采用函数式路由风格。
 * 所有路由前缀：/api/v1/office/organization
 *
 * <p>路由列表：
 * <ul>
 *   <li>POST /sync - 同步组织（部门或成员）</li>
 *   <li>GET /mappings/department/{platform}/{externalOrgId} - 查询部门映射</li>
 *   <li>GET /mappings/member/{platform}/{externalUserId} - 查询成员映射</li>
 *   <li>GET /mappings - 查询组织映射列表</li>
 *   <li>GET /sync/tasks - 查询同步任务列表</li>
 * </ul>
 *
 * @author Apex Team
 * @since 1.3.0
 */
@Configuration
@RequiredArgsConstructor
public class OfficeOrganizationRouter {

    private static final String BASE_PATH = "/api/v1/office/organization";

    private final OfficeOrganizationHandler handler;

    /**
     * 组织同步路由函数
     *
     * @return 路由函数
     */
    @RouterOperations({
        @RouterOperation(
                path = BASE_PATH + "/sync",
                method = RequestMethod.POST,
                beanClass = OfficeOrganizationHandler.class,
                beanMethod = "syncOrganization",
                operation =
                        @Operation(
                                operationId = "syncOrganization",
                                summary = "同步组织",
                                description = "从外部办公平台同步部门或成员信息到Apex系统，支持全量和增量同步",
                                tags = {"办公平台组织同步"},
                                requestBody =
                                        @RequestBody(
                                                description = "同步请求参数",
                                                required = true,
                                                content =
                                                        @Content(
                                                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                                schema =
                                                                        @Schema(
                                                                                implementation =
                                                                                        com.apex.scaffold.interfaces.dto
                                                                                                .office.organization
                                                                                                .SyncOrganizationRequest
                                                                                                .class))),
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "同步成功",
                                            content =
                                                    @Content(
                                                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                            schema =
                                                                    @Schema(
                                                                            implementation =
                                                                                    com.apex.scaffold.interfaces.dto
                                                                                            .office.organization
                                                                                            .SyncOrganizationResponse
                                                                                            .class))),
                                    @ApiResponse(responseCode = "400", description = "请求参数错误"),
                                    @ApiResponse(responseCode = "500", description = "服务器内部错误")
                                })),
        @RouterOperation(
                path = BASE_PATH + "/mappings/department/{platform}/{externalOrgId}",
                method = RequestMethod.GET,
                beanClass = OfficeOrganizationHandler.class,
                beanMethod = "getDepartmentMapping",
                operation =
                        @Operation(
                                operationId = "getDepartmentMapping",
                                summary = "查询部门映射",
                                description = "根据外部部门ID查询对应的Apex部门映射关系",
                                tags = {"办公平台组织同步"},
                                parameters = {
                                    @Parameter(
                                            name = "platform",
                                            description = "平台类型（FEISHU/DINGTALK/WECOM）",
                                            required = true,
                                            in = ParameterIn.PATH),
                                    @Parameter(
                                            name = "externalOrgId",
                                            description = "外部部门ID",
                                            required = true,
                                            in = ParameterIn.PATH)
                                },
                                responses = {
                                    @ApiResponse(responseCode = "200", description = "查询成功"),
                                    @ApiResponse(responseCode = "404", description = "映射不存在")
                                })),
        @RouterOperation(
                path = BASE_PATH + "/mappings/member/{platform}/{externalUserId}",
                method = RequestMethod.GET,
                beanClass = OfficeOrganizationHandler.class,
                beanMethod = "getMemberMapping",
                operation =
                        @Operation(
                                operationId = "getMemberMapping",
                                summary = "查询成员映射",
                                description = "根据外部用户ID查询对应的Apex用户映射关系",
                                tags = {"办公平台组织同步"},
                                parameters = {
                                    @Parameter(
                                            name = "platform",
                                            description = "平台类型（FEISHU/DINGTALK/WECOM）",
                                            required = true,
                                            in = ParameterIn.PATH),
                                    @Parameter(
                                            name = "externalUserId",
                                            description = "外部用户ID",
                                            required = true,
                                            in = ParameterIn.PATH)
                                },
                                responses = {
                                    @ApiResponse(responseCode = "200", description = "查询成功"),
                                    @ApiResponse(responseCode = "404", description = "映射不存在")
                                })),
        @RouterOperation(
                path = BASE_PATH + "/mappings",
                method = RequestMethod.GET,
                beanClass = OfficeOrganizationHandler.class,
                beanMethod = "listOrganizationMappings",
                operation =
                        @Operation(
                                operationId = "listOrganizationMappings",
                                summary = "查询组织映射列表",
                                description = "查询指定平台的所有组织映射关系",
                                tags = {"办公平台组织同步"},
                                parameters = {
                                    @Parameter(
                                            name = "platform",
                                            description = "平台类型（可选，不传则查询所有平台）",
                                            required = false,
                                            in = ParameterIn.QUERY)
                                },
                                responses = {@ApiResponse(responseCode = "200", description = "查询成功")})),
        @RouterOperation(
                path = BASE_PATH + "/sync/tasks",
                method = RequestMethod.GET,
                beanClass = OfficeOrganizationHandler.class,
                beanMethod = "listSyncTasks",
                operation =
                        @Operation(
                                operationId = "listSyncTasks",
                                summary = "查询同步任务列表",
                                description = "查询历史同步任务记录，支持分页",
                                tags = {"办公平台组织同步"},
                                parameters = {
                                    @Parameter(
                                            name = "platform",
                                            description = "平台类型（可选）",
                                            required = false,
                                            in = ParameterIn.QUERY),
                                    @Parameter(
                                            name = "page",
                                            description = "页码",
                                            required = false,
                                            in = ParameterIn.QUERY,
                                            schema = @Schema(defaultValue = "1")),
                                    @Parameter(
                                            name = "size",
                                            description = "每页大小",
                                            required = false,
                                            in = ParameterIn.QUERY,
                                            schema = @Schema(defaultValue = "20"))
                                },
                                responses = {@ApiResponse(responseCode = "200", description = "查询成功")}))
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
