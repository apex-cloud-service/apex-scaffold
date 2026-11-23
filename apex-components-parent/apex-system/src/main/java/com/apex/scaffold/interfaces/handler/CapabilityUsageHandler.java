package com.apex.scaffold.interfaces.handler;

import com.apex.scaffold.application.service.CapabilityUsageService;
import com.apex.scaffold.common.annotation.RequirePermission;
import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.common.result.ResultCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * 能力调用用量查询 Handler（方案 D4 计量，只读）。
 *
 * <p>承载 {@code GET /api/v2/capability-usage}：按 租户 × 能力 输出接口调用
 * 次数统计，数据源为既有操作审计日志聚合（{@code sys_operation_log}）。
 * 访问由 {@code capability:usage:read} 权限门控（V84 登记）。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Tag(name = "能力-调用用量", description = "按日期区间分页查询租户能力调用用量统计")
public class CapabilityUsageHandler {

    private final CapabilityUsageService capabilityUsageService;

    /**
     * 分页查询能力调用用量。
     *
     * @param request 当前请求（查询参数：capability 必填；tenantId/from/to/pageNum/pageSize 可选）
     * @return 用量分页结果（{@code Result<PageResponse<CapabilityUsageDTO>>}）
     */
    @Operation(
            operationId = "queryCapabilityUsage",
            summary = "查询能力调用用量",
            description = "按 租户 × 能力 输出接口调用次数统计（只读，基于操作审计日志聚合）；"
                    + "单租户查询时按源补充存量维度（存储用量/设备数/消息量），数据源降级时该字段为 null；"
                    + "时间区间缺省近 7 天，最大跨度 92 天；需 capability:usage:read 权限")
    @Parameters({
        @Parameter(
                name = "capability",
                in = ParameterIn.QUERY,
                required = true,
                description = "能力编码（如 file-storage/workflow/user-org/office-message）",
                schema = @Schema(type = "string")),
        @Parameter(
                name = "tenantId",
                in = ParameterIn.QUERY,
                required = false,
                description = "租户标识（缺省为平台全量视角，存量维度不填充）",
                schema = @Schema(type = "string")),
        @Parameter(
                name = "from",
                in = ParameterIn.QUERY,
                required = false,
                description = "起始日期（ISO yyyy-MM-dd，含当天）",
                schema = @Schema(type = "string")),
        @Parameter(
                name = "to",
                in = ParameterIn.QUERY,
                required = false,
                description = "截止日期（ISO yyyy-MM-dd，含当天）",
                schema = @Schema(type = "string")),
        @Parameter(
                name = "pageNum",
                in = ParameterIn.QUERY,
                required = false,
                description = "页码，从 1 开始",
                schema = @Schema(type = "integer")),
        @Parameter(
                name = "pageSize",
                in = ParameterIn.QUERY,
                required = false,
                description = "每页条数（服务端上限 100）",
                schema = @Schema(type = "integer"))
    })
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Result 封装的用量分页结果",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = PageResponse.class))),
        @ApiResponse(responseCode = "400", description = "capability 缺失或日期/分页参数非法"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要 capability:usage:read 权限")
    })
    @RequirePermission("capability:usage:read")
    public Mono<ServerResponse> queryUsage(ServerRequest request) {
        String capability = request.queryParam("capability").map(String::trim).orElse(null);
        if (capability == null || capability.isBlank()) {
            return ServerResponse.badRequest().bodyValue(Result.error(ResultCode.PARAM_ERROR.getMsg()));
        }
        String tenantId = request.queryParam("tenantId").map(String::trim).orElse(null);
        LocalDate from;
        LocalDate to;
        Integer pageNum;
        Integer pageSize;
        try {
            from = parseDate(request.queryParam("from").orElse(null));
            to = parseDate(request.queryParam("to").orElse(null));
            pageNum = parseInt(request.queryParam("pageNum").orElse(null));
            pageSize = parseInt(request.queryParam("pageSize").orElse(null));
        } catch (DateTimeParseException | NumberFormatException error) {
            return ServerResponse.badRequest().bodyValue(Result.error(ResultCode.PARAM_ERROR.getMsg()));
        }
        return capabilityUsageService
                .queryUsage(tenantId, capability, from, to, pageNum, pageSize)
                .flatMap(page -> ServerResponse.ok().bodyValue(Result.success(page)));
    }

    /**
     * 解析 ISO 日期查询参数；空白返回 {@code null}（沿用服务端缺省口径）。
     *
     * @param value 原始参数值
     * @return 日期或 {@code null}
     */
    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return LocalDate.parse(value.trim());
    }

    /**
     * 解析整数查询参数；空白返回 {@code null}（沿用服务端缺省口径）。
     *
     * @param value 原始参数值
     * @return 整数或 {@code null}
     */
    private Integer parseInt(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Integer.valueOf(value.trim());
    }
}
