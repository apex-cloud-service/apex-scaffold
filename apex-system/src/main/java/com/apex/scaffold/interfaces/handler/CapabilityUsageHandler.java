package com.apex.scaffold.interfaces.handler;

import com.apex.scaffold.application.service.CapabilityUsageService;
import com.apex.scaffold.common.annotation.RequirePermission;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.common.result.ResultCode;
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
 * @version 1.0.0
 * @since 1.3.2
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CapabilityUsageHandler {

    private final CapabilityUsageService capabilityUsageService;

    /**
     * 分页查询能力调用用量。
     *
     * @param request 当前请求（查询参数：capability 必填；tenantId/from/to/pageNum/pageSize 可选）
     * @return 用量分页结果（{@code Result<PageResponse<CapabilityUsageDTO>>}）
     */
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
