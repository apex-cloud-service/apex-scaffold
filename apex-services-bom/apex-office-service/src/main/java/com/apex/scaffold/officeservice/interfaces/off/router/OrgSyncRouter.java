package com.apex.scaffold.officeservice.interfaces.off.router;

import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.office.off.application.dto.DepartmentDTO;
import com.apex.scaffold.office.off.application.dto.EmployeeDTO;
import com.apex.scaffold.office.off.application.dto.SyncHistoryDTO;
import com.apex.scaffold.officeservice.interfaces.off.handler.OrgSyncHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
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
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

/**
 * 组织同步数据管理路由。
 *
 * <p>提供部门维护、员工查询及同步历史查询；本路由不发起第三方同步。
 * 业务响应统一使用 {@link Result}，处理器捕获的异常及记录不存在均返回 HTTP 400。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.0
 * @see OrgSyncHandler
 */
@Configuration
public class OrgSyncRouter {

    /**
     * 注册组织同步数据的八个管理端点。
     *
     * @param handler 组织同步数据处理器
     * @return 部门、员工和同步历史路由
     */
    @RouterOperations({
        @RouterOperation(
                path = "/admin-api/v1/org/departments",
                method = RequestMethod.GET,
                beanClass = OrgSyncHandler.class,
                beanMethod = "getDepartmentList",
                operation =
                        @Operation(
                                operationId = "getDepartmentList",
                                summary = "获取部门列表",
                                description = "分页查询部门，按 status 原值过滤；分页参数缺失或解析失败时采用默认值",
                                tags = {"组织同步管理"},
                                parameters = {
                                    @Parameter(
                                            name = "pageNum",
                                            in = ParameterIn.QUERY,
                                            description = "页码",
                                            schema = @Schema(type = "integer", format = "int32", defaultValue = "1")),
                                    @Parameter(
                                            name = "pageSize",
                                            in = ParameterIn.QUERY,
                                            description = "每页数量",
                                            schema = @Schema(type = "integer", format = "int32", defaultValue = "10")),
                                    @Parameter(
                                            name = "status",
                                            in = ParameterIn.QUERY,
                                            description = "部门状态，按存储值匹配",
                                            schema = @Schema(type = "string"))
                                },
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "部门分页结果",
                                            content =
                                                    @Content(
                                                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                            schema =
                                                                    @Schema(
                                                                            implementation =
                                                                                    DepartmentPageResult.class))),
                                    @ApiResponse(
                                            responseCode = "400",
                                            description = "处理失败，业务码见 code",
                                            content =
                                                    @Content(
                                                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                            schema = @Schema(implementation = OrgErrorResult.class)))
                                })),
        @RouterOperation(
                path = "/admin-api/v1/org/departments/{id}",
                method = RequestMethod.GET,
                beanClass = OrgSyncHandler.class,
                beanMethod = "getDepartmentDetail",
                operation =
                        @Operation(
                                operationId = "getDepartmentDetail",
                                summary = "获取部门详情",
                                description = "按部门 ID 查询详情；不存在时以 HTTP 400 返回 NOT_FOUND 业务码",
                                tags = {"组织同步管理"},
                                parameters =
                                        @Parameter(
                                                name = "id",
                                                in = ParameterIn.PATH,
                                                required = true,
                                                description = "部门 ID",
                                                schema = @Schema(type = "integer", format = "int64")),
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "部门详情",
                                            content =
                                                    @Content(
                                                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                            schema = @Schema(implementation = DepartmentResult.class))),
                                    @ApiResponse(
                                            responseCode = "400",
                                            description = "ID 无效、部门不存在或处理失败",
                                            content =
                                                    @Content(
                                                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                            schema = @Schema(implementation = OrgErrorResult.class)))
                                })),
        @RouterOperation(
                path = "/admin-api/v1/org/departments",
                method = RequestMethod.POST,
                beanClass = OrgSyncHandler.class,
                beanMethod = "createDepartment",
                operation =
                        @Operation(
                                operationId = "createDepartment",
                                summary = "创建部门",
                                description = "读取部门 JSON 并委托应用服务创建，返回部门数据",
                                tags = {"组织同步管理"},
                                requestBody =
                                        @RequestBody(
                                                required = true,
                                                description = "部门数据",
                                                content =
                                                        @Content(
                                                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                                schema =
                                                                        @Schema(implementation = DepartmentDTO.class))),
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "部门数据",
                                            content =
                                                    @Content(
                                                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                            schema = @Schema(implementation = DepartmentResult.class))),
                                    @ApiResponse(
                                            responseCode = "400",
                                            description = "校验或处理失败",
                                            content =
                                                    @Content(
                                                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                            schema = @Schema(implementation = OrgErrorResult.class)))
                                })),
        @RouterOperation(
                path = "/admin-api/v1/org/departments/{id}",
                method = RequestMethod.PUT,
                beanClass = OrgSyncHandler.class,
                beanMethod = "updateDepartment",
                operation =
                        @Operation(
                                operationId = "updateDepartment",
                                summary = "更新部门",
                                description = "按路径 ID 更新部门；不存在时以 HTTP 400 返回 NOT_FOUND 业务码",
                                tags = {"组织同步管理"},
                                parameters =
                                        @Parameter(
                                                name = "id",
                                                in = ParameterIn.PATH,
                                                required = true,
                                                description = "部门 ID",
                                                schema = @Schema(type = "integer", format = "int64")),
                                requestBody =
                                        @RequestBody(
                                                required = true,
                                                description = "部门数据",
                                                content =
                                                        @Content(
                                                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                                schema =
                                                                        @Schema(implementation = DepartmentDTO.class))),
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "更新后的部门",
                                            content =
                                                    @Content(
                                                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                            schema = @Schema(implementation = DepartmentResult.class))),
                                    @ApiResponse(
                                            responseCode = "400",
                                            description = "ID 无效、部门不存在、校验或处理失败",
                                            content =
                                                    @Content(
                                                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                            schema = @Schema(implementation = OrgErrorResult.class)))
                                })),
        @RouterOperation(
                path = "/admin-api/v1/org/departments/{id}",
                method = RequestMethod.DELETE,
                beanClass = OrgSyncHandler.class,
                beanMethod = "deleteDepartment",
                operation =
                        @Operation(
                                operationId = "deleteDepartment",
                                summary = "删除部门",
                                description = "按部门 ID 删除，完成后返回空 data 的统一响应",
                                tags = {"组织同步管理"},
                                parameters =
                                        @Parameter(
                                                name = "id",
                                                in = ParameterIn.PATH,
                                                required = true,
                                                description = "部门 ID",
                                                schema = @Schema(type = "integer", format = "int64")),
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "删除完成，data 为空",
                                            content =
                                                    @Content(
                                                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                            schema = @Schema(implementation = OrgErrorResult.class))),
                                    @ApiResponse(
                                            responseCode = "400",
                                            description = "ID 无效或处理失败",
                                            content =
                                                    @Content(
                                                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                            schema = @Schema(implementation = OrgErrorResult.class)))
                                })),
        @RouterOperation(
                path = "/admin-api/v1/org/employees",
                method = RequestMethod.GET,
                beanClass = OrgSyncHandler.class,
                beanMethod = "getEmployeeList",
                operation =
                        @Operation(
                                operationId = "getEmployeeList",
                                summary = "获取员工列表",
                                description = "分页查询员工，支持部门和状态过滤；deptId 解析失败时不按部门过滤",
                                tags = {"组织同步管理"},
                                parameters = {
                                    @Parameter(
                                            name = "pageNum",
                                            in = ParameterIn.QUERY,
                                            description = "页码",
                                            schema = @Schema(type = "integer", format = "int32", defaultValue = "1")),
                                    @Parameter(
                                            name = "pageSize",
                                            in = ParameterIn.QUERY,
                                            description = "每页数量",
                                            schema = @Schema(type = "integer", format = "int32", defaultValue = "10")),
                                    @Parameter(
                                            name = "deptId",
                                            in = ParameterIn.QUERY,
                                            description = "部门 ID",
                                            schema = @Schema(type = "integer", format = "int64")),
                                    @Parameter(
                                            name = "status",
                                            in = ParameterIn.QUERY,
                                            description = "员工状态，按存储值匹配",
                                            schema = @Schema(type = "string"))
                                },
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "员工分页结果",
                                            content =
                                                    @Content(
                                                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                            schema =
                                                                    @Schema(
                                                                            implementation =
                                                                                    EmployeePageResult.class))),
                                    @ApiResponse(
                                            responseCode = "400",
                                            description = "处理失败",
                                            content =
                                                    @Content(
                                                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                            schema = @Schema(implementation = OrgErrorResult.class)))
                                })),
        @RouterOperation(
                path = "/admin-api/v1/org/employees/{id}",
                method = RequestMethod.GET,
                beanClass = OrgSyncHandler.class,
                beanMethod = "getEmployeeDetail",
                operation =
                        @Operation(
                                operationId = "getEmployeeDetail",
                                summary = "获取员工详情",
                                description = "按员工 ID 查询详情；不存在时以 HTTP 400 返回 NOT_FOUND 业务码",
                                tags = {"组织同步管理"},
                                parameters =
                                        @Parameter(
                                                name = "id",
                                                in = ParameterIn.PATH,
                                                required = true,
                                                description = "员工 ID",
                                                schema = @Schema(type = "integer", format = "int64")),
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "员工详情",
                                            content =
                                                    @Content(
                                                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                            schema = @Schema(implementation = EmployeeResult.class))),
                                    @ApiResponse(
                                            responseCode = "400",
                                            description = "ID 无效、员工不存在或处理失败",
                                            content =
                                                    @Content(
                                                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                            schema = @Schema(implementation = OrgErrorResult.class)))
                                })),
        @RouterOperation(
                path = "/admin-api/v1/org/sync-history",
                method = RequestMethod.GET,
                beanClass = OrgSyncHandler.class,
                beanMethod = "getSyncHistory",
                operation =
                        @Operation(
                                operationId = "getSyncHistory",
                                summary = "获取同步历史",
                                description = "分页查询同步历史，按来源和状态的存储值过滤",
                                tags = {"组织同步管理"},
                                parameters = {
                                    @Parameter(
                                            name = "pageNum",
                                            in = ParameterIn.QUERY,
                                            description = "页码",
                                            schema = @Schema(type = "integer", format = "int32", defaultValue = "1")),
                                    @Parameter(
                                            name = "pageSize",
                                            in = ParameterIn.QUERY,
                                            description = "每页数量",
                                            schema = @Schema(type = "integer", format = "int32", defaultValue = "10")),
                                    @Parameter(
                                            name = "syncSource",
                                            in = ParameterIn.QUERY,
                                            description = "同步来源",
                                            schema = @Schema(type = "string")),
                                    @Parameter(
                                            name = "syncStatus",
                                            in = ParameterIn.QUERY,
                                            description = "同步状态",
                                            schema = @Schema(type = "string"))
                                },
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "同步历史分页结果",
                                            content =
                                                    @Content(
                                                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                            schema =
                                                                    @Schema(
                                                                            implementation =
                                                                                    SyncHistoryPageResult.class))),
                                    @ApiResponse(
                                            responseCode = "400",
                                            description = "处理失败",
                                            content =
                                                    @Content(
                                                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                            schema = @Schema(implementation = OrgErrorResult.class)))
                                }))
    })
    @Bean
    public RouterFunction<ServerResponse> orgSyncRoutes(OrgSyncHandler handler) {
        return RouterFunctions.route()
                .GET("/admin-api/v1/org/departments", handler::getDepartmentList)
                .GET("/admin-api/v1/org/departments/{id}", handler::getDepartmentDetail)
                .POST("/admin-api/v1/org/departments", handler::createDepartment)
                .PUT("/admin-api/v1/org/departments/{id}", handler::updateDepartment)
                .DELETE("/admin-api/v1/org/departments/{id}", handler::deleteDepartment)
                .GET("/admin-api/v1/org/employees", handler::getEmployeeList)
                .GET("/admin-api/v1/org/employees/{id}", handler::getEmployeeDetail)
                .GET("/admin-api/v1/org/sync-history", handler::getSyncHistory)
                .build();
    }

    /** 部门详情统一响应的文档类型。 */
    @Schema(name = "DepartmentResult")
    public static class DepartmentResult extends Result<DepartmentDTO> {}

    /** 部门分页统一响应的文档类型。 */
    @Schema(name = "DepartmentPageResult")
    public static class DepartmentPageResult extends Result<PageResponse<DepartmentDTO>> {}

    /** 员工详情统一响应的文档类型。 */
    @Schema(name = "EmployeeResult")
    public static class EmployeeResult extends Result<EmployeeDTO> {}

    /** 员工分页统一响应的文档类型。 */
    @Schema(name = "EmployeePageResult")
    public static class EmployeePageResult extends Result<PageResponse<EmployeeDTO>> {}

    /** 同步历史分页统一响应的文档类型。 */
    @Schema(name = "SyncHistoryPageResult")
    public static class SyncHistoryPageResult extends Result<PageResponse<SyncHistoryDTO>> {}

    /** 无数据统一响应的文档类型，同时用于错误响应。 */
    @Schema(name = "OrgErrorResult")
    public static class OrgErrorResult extends Result<Void> {}
}
