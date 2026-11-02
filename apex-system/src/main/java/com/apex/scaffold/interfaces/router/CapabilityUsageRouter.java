package com.apex.scaffold.interfaces.router;

import com.apex.scaffold.interfaces.handler.CapabilityUsageHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springdoc.core.annotations.RouterOperation;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

/**
 * 能力调用用量查询路由（方案 D4 计量，只读）。
 *
 * <p>{@code GET /api/v2/capability-usage}：按 租户 × 能力 输出接口调用次数，
 * 数据源为既有操作审计日志聚合；访问由 {@code capability:usage:read}（V84）门控。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.2
 */
@Configuration
public class CapabilityUsageRouter {

    /** 能力用量查询路径。 */
    public static final String USAGE_PATH = "/api/v2/capability-usage";

    /**
     * 注册能力用量查询路由。
     *
     * @param handler 能力用量 Handler
     * @return 路由函数
     */
    @Bean
    @RouterOperation(
            path = USAGE_PATH,
            method = RequestMethod.GET,
            beanClass = CapabilityUsageHandler.class,
            beanMethod = "queryUsage",
            operation =
                    @Operation(
                            operationId = "queryCapabilityUsage",
                            summary = "查询能力调用用量",
                            description = "按租户与能力维度输出接口调用次数统计（只读，基于操作审计日志聚合）。"
                                    + "能力编码须与网关 open-api.capability 口径一致且已配置 URI 前缀映射；"
                                    + "时间区间缺省为近 7 天，最大跨度 92 天。",
                            parameters = {
                                @Parameter(
                                        name = "capability",
                                        in = ParameterIn.QUERY,
                                        required = true,
                                        description = "能力编码（如 file-storage/workflow/user-org/office-message）",
                                        schema = @Schema(type = "string")),
                                @Parameter(
                                        name = "tenantId",
                                        in = ParameterIn.QUERY,
                                        description = "租户标识；缺省回退当前租户上下文",
                                        schema = @Schema(type = "string")),
                                @Parameter(
                                        name = "from",
                                        in = ParameterIn.QUERY,
                                        description = "起始日期（含，ISO yyyy-MM-dd）",
                                        schema = @Schema(type = "string", format = "date")),
                                @Parameter(
                                        name = "to",
                                        in = ParameterIn.QUERY,
                                        description = "结束日期（含，ISO yyyy-MM-dd）",
                                        schema = @Schema(type = "string", format = "date")),
                                @Parameter(
                                        name = "pageNum",
                                        in = ParameterIn.QUERY,
                                        description = "页码",
                                        schema = @Schema(type = "integer", defaultValue = "1")),
                                @Parameter(
                                        name = "pageSize",
                                        in = ParameterIn.QUERY,
                                        description = "每页数量（1-100）",
                                        schema = @Schema(type = "integer", defaultValue = "20"))
                            },
                            tags = {"能力计量"},
                            responses = {
                                @ApiResponse(
                                        responseCode = "200",
                                        description = "Result 封装的分页用量结果",
                                        content =
                                                @io.swagger.v3.oas.annotations.media.Content(
                                                        mediaType = MediaType.APPLICATION_JSON_VALUE))
                            }))
    public RouterFunction<ServerResponse> capabilityUsageRoutes(CapabilityUsageHandler handler) {
        return RouterFunctions.route().GET(USAGE_PATH, handler::queryUsage).build();
    }
}
