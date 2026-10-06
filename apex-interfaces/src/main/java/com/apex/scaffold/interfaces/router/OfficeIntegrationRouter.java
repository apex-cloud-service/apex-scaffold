package com.apex.scaffold.interfaces.router;

import static org.springframework.web.reactive.function.server.RequestPredicates.*;

import com.apex.scaffold.interfaces.handler.OfficeIntegrationHandler;
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
 * 办公集成路由配置。
 *
 * <p>基于 WebFlux 函数式编程模型注册第三方办公平台集成端点，路由前缀为
 * {@code /api/v1/office}，覆盖审批流程与日历事件的查询、创建、更新、删除
 * 及会议室可用性查询等业务端点，统一委托 {@link OfficeIntegrationHandler} 处理。
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.2.0
 * @see OfficeIntegrationHandler
 */
@Configuration
@Tag(name = "办公集成管理", description = "第三方办公平台集成接口，支持审批流程和日历事件管理")
public class OfficeIntegrationRouter {

    private static final String BASE_PATH = "/api/v1/office";

    /** 注册〈办公平台集成〉路由，涵盖审批流程与日历事件等业务端点。 */
    @RouterOperations({
        @RouterOperation(
                path = BASE_PATH + "/approvals",
                method = RequestMethod.GET,
                operation =
                        @Operation(
                                operationId = "getApprovalList",
                                summary = "获取审批列表",
                                description = "分页获取审批流程列表，支持按状态筛选",
                                tags = {"办公集成管理"},
                                parameters = {
                                    @Parameter(
                                            name = "pageNum",
                                            description = "页码",
                                            in = ParameterIn.QUERY,
                                            schema = @Schema(type = "integer", defaultValue = "1", example = "1")),
                                    @Parameter(
                                            name = "pageSize",
                                            description = "每页大小",
                                            in = ParameterIn.QUERY,
                                            schema = @Schema(type = "integer", defaultValue = "10", example = "10")),
                                    @Parameter(
                                            name = "status",
                                            description = "审批状态（PENDING-待审批/APPROVED-已通过/REJECTED-已拒绝/CANCELLED-已撤销）",
                                            in = ParameterIn.QUERY,
                                            schema = @Schema(type = "string", example = "PENDING"))
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
                                                                                    com.apex.scaffold.application.dto
                                                                                            .ApprovalProcessDTO
                                                                                            .class))),
                                    @ApiResponse(responseCode = "400", description = "请求参数错误"),
                                    @ApiResponse(responseCode = "500", description = "服务器内部错误")
                                })),
        @RouterOperation(
                path = BASE_PATH + "/approvals",
                method = RequestMethod.POST,
                operation =
                        @Operation(
                                operationId = "createApproval",
                                summary = "创建审批流程",
                                description = "在指定办公平台创建审批流程，支持飞书、钉钉、企业微信",
                                tags = {"办公集成管理"},
                                requestBody =
                                        @RequestBody(
                                                description = "审批流程信息",
                                                required = true,
                                                content =
                                                        @Content(
                                                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                                schema =
                                                                        @Schema(
                                                                                implementation =
                                                                                        com.apex.scaffold.application
                                                                                                .dto.ApprovalProcessDTO
                                                                                                .class))),
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "创建成功",
                                            content =
                                                    @Content(
                                                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                            schema =
                                                                    @Schema(
                                                                            implementation =
                                                                                    com.apex.scaffold.application.dto
                                                                                            .ApprovalProcessDTO
                                                                                            .class))),
                                    @ApiResponse(responseCode = "400", description = "请求参数错误"),
                                    @ApiResponse(responseCode = "500", description = "服务器内部错误")
                                })),
        @RouterOperation(
                path = BASE_PATH + "/approvals/pending",
                method = RequestMethod.GET,
                operation =
                        @Operation(
                                operationId = "getPendingApprovals",
                                summary = "获取待审批列表",
                                description = "查询当前用户的待审批事项列表",
                                tags = {"办公集成管理"},
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
                                                                                    com.apex.scaffold.application.dto
                                                                                            .ApprovalProcessDTO
                                                                                            .class))),
                                    @ApiResponse(responseCode = "500", description = "服务器内部错误")
                                })),
        @RouterOperation(
                path = BASE_PATH + "/approvals/{id}",
                method = RequestMethod.GET,
                operation =
                        @Operation(
                                operationId = "getApprovalDetail",
                                summary = "获取审批详情",
                                description = "根据审批ID查询审批流程的完整信息",
                                tags = {"办公集成管理"},
                                parameters = {
                                    @Parameter(
                                            name = "id",
                                            description = "审批ID",
                                            required = true,
                                            in = ParameterIn.PATH,
                                            schema = @Schema(type = "integer", format = "int64", example = "1001"))
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
                                                                                    com.apex.scaffold.application.dto
                                                                                            .ApprovalProcessDTO
                                                                                            .class))),
                                    @ApiResponse(responseCode = "404", description = "审批流程不存在"),
                                    @ApiResponse(responseCode = "400", description = "请求参数错误")
                                })),
        @RouterOperation(
                path = BASE_PATH + "/approvals/{id}",
                method = RequestMethod.PUT,
                operation =
                        @Operation(
                                operationId = "updateApproval",
                                summary = "更新审批流程",
                                description = "更新指定审批流程的信息",
                                tags = {"办公集成管理"},
                                parameters = {
                                    @Parameter(
                                            name = "id",
                                            description = "审批ID",
                                            required = true,
                                            in = ParameterIn.PATH,
                                            schema = @Schema(type = "integer", format = "int64", example = "1001"))
                                },
                                requestBody =
                                        @RequestBody(
                                                description = "审批流程信息",
                                                required = true,
                                                content =
                                                        @Content(
                                                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                                schema =
                                                                        @Schema(
                                                                                implementation =
                                                                                        com.apex.scaffold.application
                                                                                                .dto.ApprovalProcessDTO
                                                                                                .class))),
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "更新成功",
                                            content =
                                                    @Content(
                                                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                            schema =
                                                                    @Schema(
                                                                            implementation =
                                                                                    com.apex.scaffold.application.dto
                                                                                            .ApprovalProcessDTO
                                                                                            .class))),
                                    @ApiResponse(responseCode = "404", description = "审批流程不存在"),
                                    @ApiResponse(responseCode = "400", description = "请求参数错误")
                                })),
        @RouterOperation(
                path = BASE_PATH + "/approvals/{id}",
                method = RequestMethod.DELETE,
                operation =
                        @Operation(
                                operationId = "deleteApproval",
                                summary = "删除审批流程",
                                description = "删除指定的审批流程（仅发起人可删除）",
                                tags = {"办公集成管理"},
                                parameters = {
                                    @Parameter(
                                            name = "id",
                                            description = "审批ID",
                                            required = true,
                                            in = ParameterIn.PATH,
                                            schema = @Schema(type = "integer", format = "int64", example = "1001"))
                                },
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "删除成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)),
                                    @ApiResponse(responseCode = "404", description = "审批流程不存在"),
                                    @ApiResponse(responseCode = "400", description = "请求参数错误或无权限删除")
                                })),
        @RouterOperation(
                path = BASE_PATH + "/calendar-events",
                method = RequestMethod.GET,
                operation =
                        @Operation(
                                operationId = "getCalendarEventList",
                                summary = "获取日历事件列表",
                                description = "分页获取日历事件列表，支持按事件类型筛选",
                                tags = {"办公集成管理"},
                                parameters = {
                                    @Parameter(
                                            name = "pageNum",
                                            description = "页码",
                                            in = ParameterIn.QUERY,
                                            schema = @Schema(type = "integer", defaultValue = "1", example = "1")),
                                    @Parameter(
                                            name = "pageSize",
                                            description = "每页大小",
                                            in = ParameterIn.QUERY,
                                            schema = @Schema(type = "integer", defaultValue = "10", example = "10")),
                                    @Parameter(
                                            name = "eventType",
                                            description = "事件类型（MEETING-会议/TASK-任务/REMINDER-提醒）",
                                            in = ParameterIn.QUERY,
                                            schema = @Schema(type = "string", example = "MEETING"))
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
                                                                                    com.apex.scaffold.application.dto
                                                                                            .CalendarEventDTO.class))),
                                    @ApiResponse(responseCode = "400", description = "请求参数错误"),
                                    @ApiResponse(responseCode = "500", description = "服务器内部错误")
                                })),
        @RouterOperation(
                path = BASE_PATH + "/calendar-events",
                method = RequestMethod.POST,
                operation =
                        @Operation(
                                operationId = "createCalendarEvent",
                                summary = "创建日历事件",
                                description = "在指定办公平台创建日历事件，支持会议、任务、提醒等类型",
                                tags = {"办公集成管理"},
                                requestBody =
                                        @RequestBody(
                                                description = "日历事件信息",
                                                required = true,
                                                content =
                                                        @Content(
                                                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                                schema =
                                                                        @Schema(
                                                                                implementation =
                                                                                        com.apex.scaffold.application
                                                                                                .dto.CalendarEventDTO
                                                                                                .class))),
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "创建成功",
                                            content =
                                                    @Content(
                                                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                            schema =
                                                                    @Schema(
                                                                            implementation =
                                                                                    com.apex.scaffold.application.dto
                                                                                            .CalendarEventDTO.class))),
                                    @ApiResponse(responseCode = "400", description = "请求参数错误"),
                                    @ApiResponse(responseCode = "500", description = "服务器内部错误")
                                })),
        @RouterOperation(
                path = BASE_PATH + "/calendar-events/upcoming",
                method = RequestMethod.GET,
                operation =
                        @Operation(
                                operationId = "getUpcomingEvents",
                                summary = "获取即将到来的事件",
                                description = "查询当前用户即将到来的日历事件",
                                tags = {"办公集成管理"},
                                parameters = {
                                    @Parameter(
                                            name = "limit",
                                            description = "返回结果数量限制",
                                            in = ParameterIn.QUERY,
                                            schema = @Schema(type = "integer", defaultValue = "10", example = "10"))
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
                                                                                    com.apex.scaffold.application.dto
                                                                                            .CalendarEventDTO.class))),
                                    @ApiResponse(responseCode = "500", description = "服务器内部错误")
                                })),
        @RouterOperation(
                path = BASE_PATH + "/calendar-events/meeting-room-availability",
                method = RequestMethod.GET,
                operation =
                        @Operation(
                                operationId = "getMeetingRoomAvailability",
                                summary = "查询会议室可用性",
                                description = "查询指定时间段内会议室的可用性",
                                tags = {"办公集成管理"},
                                parameters = {
                                    @Parameter(
                                            name = "meetingRoomId",
                                            description = "会议室ID",
                                            required = true,
                                            in = ParameterIn.QUERY,
                                            schema = @Schema(type = "string", example = "room_001")),
                                    @Parameter(
                                            name = "startTime",
                                            description = "开始时间（ISO 8601格式）",
                                            required = true,
                                            in = ParameterIn.QUERY,
                                            schema =
                                                    @Schema(
                                                            type = "string",
                                                            format = "date-time",
                                                            example = "2026-09-23T09:00:00")),
                                    @Parameter(
                                            name = "endTime",
                                            description = "结束时间（ISO 8601格式）",
                                            required = true,
                                            in = ParameterIn.QUERY,
                                            schema =
                                                    @Schema(
                                                            type = "string",
                                                            format = "date-time",
                                                            example = "2026-09-23T18:00:00"))
                                },
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "查询成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)),
                                    @ApiResponse(responseCode = "400", description = "请求参数错误"),
                                    @ApiResponse(responseCode = "500", description = "服务器内部错误")
                                })),
        @RouterOperation(
                path = BASE_PATH + "/calendar-events/{id}",
                method = RequestMethod.GET,
                operation =
                        @Operation(
                                operationId = "getCalendarEventDetail",
                                summary = "获取日历事件详情",
                                description = "根据事件ID查询日历事件的完整信息",
                                tags = {"办公集成管理"},
                                parameters = {
                                    @Parameter(
                                            name = "id",
                                            description = "事件ID",
                                            required = true,
                                            in = ParameterIn.PATH,
                                            schema = @Schema(type = "integer", format = "int64", example = "2001"))
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
                                                                                    com.apex.scaffold.application.dto
                                                                                            .CalendarEventDTO.class))),
                                    @ApiResponse(responseCode = "404", description = "日历事件不存在"),
                                    @ApiResponse(responseCode = "400", description = "请求参数错误")
                                })),
        @RouterOperation(
                path = BASE_PATH + "/calendar-events/{id}",
                method = RequestMethod.PUT,
                operation =
                        @Operation(
                                operationId = "updateCalendarEvent",
                                summary = "更新日历事件",
                                description = "更新指定日历事件的信息",
                                tags = {"办公集成管理"},
                                parameters = {
                                    @Parameter(
                                            name = "id",
                                            description = "事件ID",
                                            required = true,
                                            in = ParameterIn.PATH,
                                            schema = @Schema(type = "integer", format = "int64", example = "2001"))
                                },
                                requestBody =
                                        @RequestBody(
                                                description = "日历事件信息",
                                                required = true,
                                                content =
                                                        @Content(
                                                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                                schema =
                                                                        @Schema(
                                                                                implementation =
                                                                                        com.apex.scaffold.application
                                                                                                .dto.CalendarEventDTO
                                                                                                .class))),
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "更新成功",
                                            content =
                                                    @Content(
                                                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                            schema =
                                                                    @Schema(
                                                                            implementation =
                                                                                    com.apex.scaffold.application.dto
                                                                                            .CalendarEventDTO.class))),
                                    @ApiResponse(responseCode = "404", description = "日历事件不存在"),
                                    @ApiResponse(responseCode = "400", description = "请求参数错误")
                                })),
        @RouterOperation(
                path = BASE_PATH + "/calendar-events/{id}",
                method = RequestMethod.DELETE,
                operation =
                        @Operation(
                                operationId = "deleteCalendarEvent",
                                summary = "删除日历事件",
                                description = "删除指定的日历事件（仅组织者可删除）",
                                tags = {"办公集成管理"},
                                parameters = {
                                    @Parameter(
                                            name = "id",
                                            description = "事件ID",
                                            required = true,
                                            in = ParameterIn.PATH,
                                            schema = @Schema(type = "integer", format = "int64", example = "2001"))
                                },
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "删除成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)),
                                    @ApiResponse(responseCode = "404", description = "日历事件不存在"),
                                    @ApiResponse(responseCode = "400", description = "请求参数错误或无权限删除")
                                }))
    })
    @Bean
    public RouterFunction<ServerResponse> officeIntegrationRoutes(OfficeIntegrationHandler handler) {
        return RouterFunctions.route()
                .path(BASE_PATH, routes -> routes.GET("/approvals", handler::getApprovalList)
                        .POST("/approvals", handler::createApproval)
                        .GET("/approvals/pending", handler::getPendingApprovals)
                        .GET("/approvals/{id}", handler::getApprovalDetail)
                        .PUT("/approvals/{id}", handler::updateApproval)
                        .DELETE("/approvals/{id}", handler::deleteApproval)
                        .GET("/calendar-events", handler::getCalendarEventList)
                        .POST("/calendar-events", handler::createCalendarEvent)
                        .GET("/calendar-events/upcoming", handler::getUpcomingEvents)
                        .GET("/calendar-events/meeting-room-availability", handler::getMeetingRoomAvailability)
                        .GET("/calendar-events/{id}", handler::getCalendarEventDetail)
                        .PUT("/calendar-events/{id}", handler::updateCalendarEvent)
                        .DELETE("/calendar-events/{id}", handler::deleteCalendarEvent))
                .build();
    }
}
