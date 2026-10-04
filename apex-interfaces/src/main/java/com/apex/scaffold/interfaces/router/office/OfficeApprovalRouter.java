package com.apex.scaffold.interfaces.router.office;

import static org.springframework.web.reactive.function.server.RequestPredicates.*;

import com.apex.scaffold.interfaces.handler.office.OfficeApprovalHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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
 * 办公平台审批路由配置
 *
 * <p>定义审批相关的HTTP路由规则，采用函数式路由方式。
 * 所有路由都包含完整的SpringDoc注解，用于生成在线API文档。
 *
 * <p>路由列表：
 * <ul>
 *   <li>POST /api/office/approvals - 发起审批</li>
 *   <li>GET /api/office/approvals/{processInstanceId} - 查询审批详情</li>
 *   <li>POST /api/office/approvals/{processInstanceId}/approve - 执行审批操作</li>
 *   <li>DELETE /api/office/approvals/{processInstanceId} - 撤销审批</li>
 *   <li>GET /api/office/approvals/pending - 查询待审批列表</li>
 *   <li>GET /api/office/approvals/initiated - 查询我发起的审批</li>
 * </ul>
 *
 * @author Apex Team
 * @since 1.3.0
 */
@Configuration
@Tag(name = "办公平台审批", description = "办公平台审批流程管理接口")
public class OfficeApprovalRouter {

    private static final String BASE_PATH = "/api/office/approvals";

    /**
     * 审批路由配置
     *
     * @param handler 审批处理器
     * @return 路由函数
     */
    @RouterOperations({
        @RouterOperation(
                path = BASE_PATH,
                method = RequestMethod.POST,
                operation =
                        @Operation(
                                operationId = "initiateApproval",
                                summary = "发起审批流程",
                                description = "在指定办公平台发起审批流程，支持飞书、钉钉、企业微信",
                                tags = {"办公平台审批"},
                                requestBody =
                                        @RequestBody(
                                                description = "发起审批请求参数",
                                                required = true,
                                                content =
                                                        @Content(
                                                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                                schema =
                                                                        @Schema(
                                                                                implementation =
                                                                                        com.apex.scaffold.interfaces.dto
                                                                                                .office.approval
                                                                                                .InitiateApprovalRequest
                                                                                                .class))),
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "发起成功",
                                            content =
                                                    @Content(
                                                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                            schema =
                                                                    @Schema(
                                                                            implementation =
                                                                                    com.apex.scaffold.interfaces.dto
                                                                                            .office.approval
                                                                                            .ApprovalProcessResponse
                                                                                            .class))),
                                    @ApiResponse(responseCode = "400", description = "请求参数错误"),
                                    @ApiResponse(responseCode = "500", description = "服务器内部错误")
                                })),
        @RouterOperation(
                path = BASE_PATH + "/{processInstanceId}",
                method = RequestMethod.GET,
                operation =
                        @Operation(
                                operationId = "getApprovalDetail",
                                summary = "查询审批详情",
                                description = "根据流程实例ID查询审批流程的完整信息",
                                tags = {"办公平台审批"},
                                parameters = {
                                    @Parameter(
                                            name = "processInstanceId",
                                            description = "流程实例ID",
                                            required = true,
                                            in = ParameterIn.PATH,
                                            schema = @Schema(type = "string", example = "inst_001"))
                                },
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "查询成功",
                                            content =
                                                    @Content(
                                                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                            schema =
                                                                    @Schema(
                                                                            implementation =
                                                                                    com.apex.scaffold.interfaces.dto
                                                                                            .office.approval
                                                                                            .ApprovalProcessResponse
                                                                                            .class))),
                                    @ApiResponse(responseCode = "404", description = "审批流程不存在")
                                })),
        @RouterOperation(
                path = BASE_PATH + "/{processInstanceId}/approve",
                method = RequestMethod.POST,
                operation =
                        @Operation(
                                operationId = "approveProcess",
                                summary = "执行审批操作",
                                description = "审批人对待审批流程执行操作（同意/拒绝/转审/加签）",
                                tags = {"办公平台审批"},
                                parameters = {
                                    @Parameter(
                                            name = "processInstanceId",
                                            description = "流程实例ID",
                                            required = true,
                                            in = ParameterIn.PATH,
                                            schema = @Schema(type = "string", example = "inst_001"))
                                },
                                requestBody =
                                        @RequestBody(
                                                description = "审批操作请求参数",
                                                required = true,
                                                content =
                                                        @Content(
                                                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                                schema =
                                                                        @Schema(
                                                                                implementation =
                                                                                        com.apex.scaffold.interfaces.dto
                                                                                                .office.approval
                                                                                                .ApproveRequest
                                                                                                .class))),
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "审批操作成功",
                                            content =
                                                    @Content(
                                                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                            schema =
                                                                    @Schema(
                                                                            implementation =
                                                                                    com.apex.scaffold.interfaces.dto
                                                                                            .office.approval
                                                                                            .ApprovalProcessResponse
                                                                                            .class))),
                                    @ApiResponse(responseCode = "400", description = "请求参数错误或无权限操作"),
                                    @ApiResponse(responseCode = "404", description = "审批流程不存在")
                                })),
        @RouterOperation(
                path = BASE_PATH + "/{processInstanceId}",
                method = RequestMethod.DELETE,
                operation =
                        @Operation(
                                operationId = "cancelApproval",
                                summary = "撤销审批",
                                description = "发起人撤销自己发起的审批流程",
                                tags = {"办公平台审批"},
                                parameters = {
                                    @Parameter(
                                            name = "processInstanceId",
                                            description = "流程实例ID",
                                            required = true,
                                            in = ParameterIn.PATH,
                                            schema = @Schema(type = "string", example = "inst_001"))
                                },
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "撤销成功",
                                            content =
                                                    @Content(
                                                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                            schema = @Schema(implementation = Boolean.class))),
                                    @ApiResponse(responseCode = "400", description = "流程状态不允许撤销"),
                                    @ApiResponse(responseCode = "404", description = "审批流程不存在")
                                })),
        @RouterOperation(
                path = BASE_PATH + "/pending",
                method = RequestMethod.GET,
                operation =
                        @Operation(
                                operationId = "getPendingApprovals",
                                summary = "查询待审批列表",
                                description = "查询当前用户的待审批事项列表",
                                tags = {"办公平台审批"},
                                parameters = {
                                    @Parameter(
                                            name = "approverId",
                                            description = "审批人ID",
                                            required = true,
                                            in = ParameterIn.QUERY,
                                            schema = @Schema(type = "string", example = "user_002"))
                                },
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "查询成功",
                                            content =
                                                    @Content(
                                                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                            schema =
                                                                    @Schema(
                                                                            implementation =
                                                                                    com.apex.scaffold.interfaces.dto
                                                                                            .office.approval
                                                                                            .ApprovalProcessResponse
                                                                                            .class)))
                                })),
        @RouterOperation(
                path = BASE_PATH + "/initiated",
                method = RequestMethod.GET,
                operation =
                        @Operation(
                                operationId = "getMyInitiatedApprovals",
                                summary = "查询我发起的审批",
                                description = "查询当前用户发起的所有审批流程",
                                tags = {"办公平台审批"},
                                parameters = {
                                    @Parameter(
                                            name = "initiatorId",
                                            description = "发起人ID",
                                            required = true,
                                            in = ParameterIn.QUERY,
                                            schema = @Schema(type = "string", example = "user_001"))
                                },
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "查询成功",
                                            content =
                                                    @Content(
                                                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                            schema =
                                                                    @Schema(
                                                                            implementation =
                                                                                    com.apex.scaffold.interfaces.dto
                                                                                            .office.approval
                                                                                            .ApprovalProcessResponse
                                                                                            .class)))
                                }))
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
