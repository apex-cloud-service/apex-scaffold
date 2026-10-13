package com.apex.scaffold.officeservice.interfaces.router.office;

import static org.springframework.web.reactive.function.server.RequestPredicates.*;

import com.apex.scaffold.officeservice.interfaces.handler.office.OfficeCalendarHandler;
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
 * 办公平台日历事件路由配置
 *
 * <p>配置日历事件相关的HTTP路由，使用函数式路由定义所有端点。
 * 每个路由都配置了完整的SpringDoc注解，用于生成在线API文档。
 *
 * <p>路由列表：
 * <ul>
 *   <li>POST /api/office/calendar/events - 创建日历事件</li>
 *   <li>GET /api/office/calendar/events/{eventId} - 查询事件详情</li>
 *   <li>PUT /api/office/calendar/events/{eventId} - 更新日历事件</li>
 *   <li>DELETE /api/office/calendar/events/{eventId} - 删除日历事件</li>
 *   <li>GET /api/office/calendar/events/range - 查询时间范围内的事件</li>
 *   <li>GET /api/office/calendar/events/my - 查询我的日历事件</li>
 *   <li>GET /api/office/calendar/events/upcoming - 查询即将到来的事件</li>
 * </ul>
 *
 * @author Apex Scaffold
 * @since 1.0.0
 */
@Configuration
@RequiredArgsConstructor
public class OfficeCalendarRouter {

    private static final String BASE_PATH = "/api/office/calendar/events";

    private final OfficeCalendarHandler handler;

    /**
     * 配置日历事件路由
     *
     * @return RouterFunction 路由函数
     */
    @RouterOperations({
        @RouterOperation(
                path = BASE_PATH,
                method = RequestMethod.POST,
                operation =
                        @Operation(
                                operationId = "createCalendarEvent",
                                summary = "创建日历事件",
                                description = "在指定办公平台创建日历事件，支持飞书、钉钉、企业微信",
                                tags = {"办公平台日历"},
                                requestBody =
                                        @RequestBody(
                                                description = "日历事件创建请求",
                                                required = true,
                                                content =
                                                        @Content(
                                                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                                schema =
                                                                        @Schema(
                                                                                implementation =
                                                                                        com.apex.scaffold.officeservice
                                                                                                .interfaces.dto.office
                                                                                                .calendar
                                                                                                .CreateCalendarEventRequest
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
                                                                                    com.apex.scaffold.officeservice
                                                                                            .interfaces.dto.office
                                                                                            .calendar
                                                                                            .CalendarEventResponse
                                                                                            .class))),
                                    @ApiResponse(responseCode = "400", description = "请求参数错误"),
                                    @ApiResponse(responseCode = "500", description = "服务器内部错误")
                                })),
        @RouterOperation(
                path = BASE_PATH + "/{eventId}",
                method = RequestMethod.GET,
                operation =
                        @Operation(
                                operationId = "getCalendarEventDetail",
                                summary = "查询日历事件详情",
                                description = "根据事件ID查询日历事件的详细信息",
                                tags = {"办公平台日历"},
                                parameters = {
                                    @Parameter(
                                            name = "eventId",
                                            description = "事件ID",
                                            required = true,
                                            in = ParameterIn.PATH,
                                            schema = @Schema(type = "integer", format = "int64"))
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
                                                                                    com.apex.scaffold.officeservice
                                                                                            .interfaces.dto.office
                                                                                            .calendar
                                                                                            .CalendarEventResponse
                                                                                            .class))),
                                    @ApiResponse(responseCode = "404", description = "事件不存在"),
                                    @ApiResponse(responseCode = "500", description = "服务器内部错误")
                                })),
        @RouterOperation(
                path = BASE_PATH + "/{eventId}",
                method = RequestMethod.PUT,
                operation =
                        @Operation(
                                operationId = "updateCalendarEvent",
                                summary = "更新日历事件",
                                description = "更新指定日历事件的信息，包括标题、时间、参与者等",
                                tags = {"办公平台日历"},
                                parameters = {
                                    @Parameter(
                                            name = "eventId",
                                            description = "事件ID",
                                            required = true,
                                            in = ParameterIn.PATH,
                                            schema = @Schema(type = "integer", format = "int64"))
                                },
                                requestBody =
                                        @RequestBody(
                                                description = "日历事件更新请求",
                                                required = true,
                                                content =
                                                        @Content(
                                                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                                schema =
                                                                        @Schema(
                                                                                implementation =
                                                                                        com.apex.scaffold.officeservice
                                                                                                .interfaces.dto.office
                                                                                                .calendar
                                                                                                .UpdateCalendarEventRequest
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
                                                                                    com.apex.scaffold.officeservice
                                                                                            .interfaces.dto.office
                                                                                            .calendar
                                                                                            .CalendarEventResponse
                                                                                            .class))),
                                    @ApiResponse(responseCode = "400", description = "请求参数错误"),
                                    @ApiResponse(responseCode = "404", description = "事件不存在"),
                                    @ApiResponse(responseCode = "500", description = "服务器内部错误")
                                })),
        @RouterOperation(
                path = BASE_PATH + "/{eventId}",
                method = RequestMethod.DELETE,
                operation =
                        @Operation(
                                operationId = "deleteCalendarEvent",
                                summary = "删除日历事件",
                                description = "删除指定的日历事件",
                                tags = {"办公平台日历"},
                                parameters = {
                                    @Parameter(
                                            name = "eventId",
                                            description = "事件ID",
                                            required = true,
                                            in = ParameterIn.PATH,
                                            schema = @Schema(type = "integer", format = "int64"))
                                },
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "删除成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)),
                                    @ApiResponse(responseCode = "404", description = "事件不存在"),
                                    @ApiResponse(responseCode = "500", description = "服务器内部错误")
                                })),
        @RouterOperation(
                path = BASE_PATH + "/range",
                method = RequestMethod.GET,
                operation =
                        @Operation(
                                operationId = "getCalendarEventsByTimeRange",
                                summary = "查询时间范围内的日历事件",
                                description = "查询指定时间范围内的所有日历事件",
                                tags = {"办公平台日历"},
                                parameters = {
                                    @Parameter(
                                            name = "organizerId",
                                            description = "组织者ID",
                                            required = true,
                                            in = ParameterIn.QUERY,
                                            schema = @Schema(type = "string")),
                                    @Parameter(
                                            name = "startTime",
                                            description = "开始时间（ISO格式）",
                                            required = true,
                                            in = ParameterIn.QUERY,
                                            schema = @Schema(type = "string", format = "date-time")),
                                    @Parameter(
                                            name = "endTime",
                                            description = "结束时间（ISO格式）",
                                            required = true,
                                            in = ParameterIn.QUERY,
                                            schema = @Schema(type = "string", format = "date-time"))
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
                path = BASE_PATH + "/my",
                method = RequestMethod.GET,
                operation =
                        @Operation(
                                operationId = "getMyCalendarEvents",
                                summary = "查询我的日历事件",
                                description = "查询当前用户组织或参与的所有日历事件",
                                tags = {"办公平台日历"},
                                parameters = {
                                    @Parameter(
                                            name = "userId",
                                            description = "用户ID",
                                            required = true,
                                            in = ParameterIn.QUERY,
                                            schema = @Schema(type = "string"))
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
                path = BASE_PATH + "/upcoming",
                method = RequestMethod.GET,
                operation =
                        @Operation(
                                operationId = "getUpcomingEvents",
                                summary = "查询即将到来的日历事件",
                                description = "查询指定用户即将到来的日历事件（未来7天内）",
                                tags = {"办公平台日历"},
                                parameters = {
                                    @Parameter(
                                            name = "userId",
                                            description = "用户ID",
                                            required = true,
                                            in = ParameterIn.QUERY,
                                            schema = @Schema(type = "string"))
                                },
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "查询成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)),
                                    @ApiResponse(responseCode = "400", description = "请求参数错误"),
                                    @ApiResponse(responseCode = "500", description = "服务器内部错误")
                                }))
    })
    @Bean
    public RouterFunction<ServerResponse> officeCalendarRoutes() {
        return RouterFunctions.route(
                        POST(BASE_PATH).and(accept(MediaType.APPLICATION_JSON)), handler::createCalendarEvent)
                .andRoute(GET(BASE_PATH + "/{eventId}"), handler::getCalendarEventDetail)
                .andRoute(
                        PUT(BASE_PATH + "/{eventId}").and(accept(MediaType.APPLICATION_JSON)),
                        handler::updateCalendarEvent)
                .andRoute(DELETE(BASE_PATH + "/{eventId}"), handler::deleteCalendarEvent)
                .andRoute(GET(BASE_PATH + "/range"), handler::getCalendarEventsByTimeRange)
                .andRoute(GET(BASE_PATH + "/my"), handler::getMyCalendarEvents)
                .andRoute(GET(BASE_PATH + "/upcoming"), handler::getUpcomingEvents);
    }
}
