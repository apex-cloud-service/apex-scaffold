package com.apex.scaffold.interfaces.handler;

import com.apex.scaffold.application.dto.admin.HealthSnapshotCommand;
import com.apex.scaffold.application.service.HealthExportService;
import com.apex.scaffold.application.service.OperationsHealthService;
import com.apex.scaffold.common.annotation.OperationLog;
import com.apex.scaffold.common.annotation.RequirePermission;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.interfaces.dto.monitor.HealthReportResponse;
import com.apex.scaffold.interfaces.monitor.health.HealthReportAssembler;
import com.apex.scaffold.interfaces.util.MessageUtils;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Validator;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * 运维体检处理器（函数式端点）。
 *
 * <p>输出可解释的管理员体检报告：每项检查给出状态、采集范围、采样时间、
 * 数据来源、证据与整改建议，并按应用存活、依赖就绪、业务风险、容量分组。</p>
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>GET /api/v2/operations/health-report - 管理员体检报告（平台级）</li>
 *   <li>POST /api/v2/operations/health-snapshots - 采集并固化一次体检快照</li>
 *   <li>GET /api/v2/operations/health-snapshots - 体检快照历史列表</li>
 *   <li>GET /api/v2/operations/health-snapshots/export - 体检快照 CSV 导出</li>
 *   <li>GET /api/v2/operations/health-trend - 体检趋势分析</li>
 *   <li>GET /api/v2/operations/tenant-health-summary - 租户受限体检摘要</li>
 * </ul>
 *
 * <p><b>范围约束</b>：报告来自当前实例进程内采集，单实例结果不冒充全栈健康；
 * 跨实例汇总需依赖受控采集契约或监控平台，不由本端点提供。</p>
 *
 * @author Apex Team
 * @version 1.1.0
 * @since 1.3.0
 * @see HealthReportAssembler
 * @see OperationsHealthService
 */
@Component
@ConditionalOnClass({CircuitBreakerRegistry.class, RocketMQTemplate.class})
@Tag(name = "后台-运维体检", description = "可解释的管理员体检报告与历史趋势（单实例范围）")
public class OperationsHealthHandler extends BaseHandler {

    /** 体检报告组装器。 */
    private final HealthReportAssembler assembler;

    /** 体检历史应用服务。 */
    private final OperationsHealthService operationsHealthService;

    /** 体检快照异步导出应用服务。 */
    private final HealthExportService healthExportService;

    /**
     * 创建运维体检处理器。
     *
     * @param messageUtils 国际化消息工具
     * @param validator Jakarta Bean Validation 校验器
     * @param assembler 体检报告组装器
     * @param operationsHealthService 体检历史应用服务
     * @param healthExportService 体检快照异步导出应用服务
     */
    public OperationsHealthHandler(
            MessageUtils messageUtils,
            Validator validator,
            HealthReportAssembler assembler,
            OperationsHealthService operationsHealthService,
            HealthExportService healthExportService) {
        super(messageUtils, validator);
        this.assembler = assembler;
        this.operationsHealthService = operationsHealthService;
        this.healthExportService = healthExportService;
    }

    /**
     * 管理员体检报告。
     *
     * <p>系统资源采集包含同步磁盘与文件描述符读取，整体在通用阻塞池执行，
     * 不占用事件循环。</p>
     *
     * @param request HTTP 请求
     * @return 统一成功响应，data 为体检报告
     */
    @RequirePermission("system:operations:health:read")
    @Operation(
            operationId = "operationsHealthReport",
            summary = "管理员体检报告",
            description = "按应用存活、依赖就绪、业务风险、容量分组输出体检结论；" + "每项含状态、范围、采样时间、来源、证据与建议，未知/未配置不计为健康")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "生成成功",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = Object.class))),
        @ApiResponse(responseCode = "401", description = "未授权，需要登录"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要system:operations:health:read权限")
    })
    @OperationLog(
            module = "OperationsHealth",
            type = OperationLog.OperationType.SELECT,
            description = "health-report",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> healthReport(ServerRequest request) {
        return Mono.fromSupplier(assembler::report)
                .subscribeOn(BlockingIoSchedulers.general())
                .flatMap(this::buildSuccess);
    }

    /**
     * 采集并固化一次体检快照。
     *
     * <p>先执行与 {@code GET /health-report} 完全相同的实例内采集，再把结论级字段
     * （总体状态、状态计数、摘要、规则版本、生成时间）落库；逐项证据不持久化。</p>
     *
     * @param request HTTP 请求
     * @return 统一成功响应，data 为已落库的快照
     */
    @RequirePermission("system:operations:health:snapshot:create")
    @Operation(
            operationId = "operationsHealthCaptureSnapshot",
            summary = "采集体检快照",
            description = "执行一次实例内体检并把结论级结果固化到历史快照，用于后续历史对比与趋势分析")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "采集并落库成功"),
        @ApiResponse(responseCode = "401", description = "未授权，需要登录"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要system:operations:health:snapshot:create权限或缺少租户上下文")
    })
    @OperationLog(
            module = "OperationsHealth",
            type = OperationLog.OperationType.OTHER,
            description = "health-snapshot-capture",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> captureSnapshot(ServerRequest request) {
        return Mono.fromSupplier(assembler::report)
                .subscribeOn(BlockingIoSchedulers.general())
                .map(OperationsHealthHandler::toCommand)
                .flatMap(operationsHealthService::capture)
                .flatMap(this::buildSuccess);
    }

    /**
     * 分页查询体检快照历史
     *
     * <p>解析可选的 {@code from}/{@code to}（ISO 8601）、{@code status} 结论与分页参数
     * （页码缺省 1、大小受限收敛），时间格式或结论取值非法时返回 400，其余交由服务层检索分页结果。
     *
     * @param request HTTP 请求，查询参数 {@code from}、{@code to}（ISO 8601）、
     *                {@code status}、{@code page}、{@code size}
     * @return 统一成功响应，data 为分页快照
     */
    @RequirePermission("system:operations:health:snapshot:list")
    @Operation(
            operationId = "operationsHealthListSnapshots",
            summary = "查询体检快照历史",
            description = "按生成时间范围与总体结论分页查询已固化的体检快照，租户范围由认证上下文决定")
    @Parameter(
            name = "from",
            description = "生成时间下界(ISO 8601)",
            in = ParameterIn.QUERY,
            schema = @Schema(type = "string", format = "date-time"))
    @Parameter(
            name = "to",
            description = "生成时间上界(ISO 8601)",
            in = ParameterIn.QUERY,
            schema = @Schema(type = "string", format = "date-time"))
    @Parameter(
            name = "status",
            description = "总体结论(UP/DEGRADED/DOWN/UNKNOWN)",
            in = ParameterIn.QUERY,
            schema = @Schema(type = "string"))
    @Parameter(name = "page", description = "页码，从1开始", in = ParameterIn.QUERY, schema = @Schema(type = "integer"))
    @Parameter(name = "size", description = "每页大小", in = ParameterIn.QUERY, schema = @Schema(type = "integer"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "查询成功"),
        @ApiResponse(responseCode = "400", description = "时间格式或结论取值非法"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要system:operations:health:snapshot:list权限")
    })
    @OperationLog(
            module = "OperationsHealth",
            type = OperationLog.OperationType.SELECT,
            description = "health-snapshot-list",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> listSnapshots(ServerRequest request) {
        int page = safePageNum(request);
        int size = safePageSize(request);
        return operationsHealthService
                .search(
                        parseTime(request, "from"),
                        parseTime(request, "to"),
                        request.queryParam("status").orElse(null),
                        page,
                        size)
                .flatMap(this::buildSuccess);
    }

    /**
     * 导出体检快照 CSV
     *
     * <p>解析可选的 {@code from}/{@code to} 与 {@code status}，调用服务层导出为 CSV；记录数超过上限时
     * 返回 400 要求缩小范围，成功时以附件形式输出（文件名按 UTF-8 编码）。
     *
     * @param request HTTP 请求，查询参数 {@code from}、{@code to}、{@code status}
     * @return CSV 附件响应
     */
    @RequirePermission("system:operations:health:snapshot:export")
    @Operation(
            operationId = "operationsHealthExportSnapshots",
            summary = "导出体检快照",
            description = "按时间范围与总体结论导出体检快照 CSV，记录数超过上限时拒绝并要求缩小范围")
    @Parameter(
            name = "from",
            description = "生成时间下界(ISO 8601)",
            in = ParameterIn.QUERY,
            schema = @Schema(type = "string", format = "date-time"))
    @Parameter(
            name = "to",
            description = "生成时间上界(ISO 8601)",
            in = ParameterIn.QUERY,
            schema = @Schema(type = "string", format = "date-time"))
    @Parameter(
            name = "status",
            description = "总体结论(UP/DEGRADED/DOWN/UNKNOWN)",
            in = ParameterIn.QUERY,
            schema = @Schema(type = "string"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "导出成功，返回 CSV 文件流"),
        @ApiResponse(responseCode = "400", description = "时间格式非法或记录数超过导出上限"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要system:operations:health:snapshot:export权限")
    })
    @OperationLog(
            module = "OperationsHealth",
            type = OperationLog.OperationType.SELECT,
            description = "health-snapshot-export",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> exportSnapshots(ServerRequest request) {
        return operationsHealthService
                .export(
                        parseTime(request, "from"),
                        parseTime(request, "to"),
                        request.queryParam("status").orElse(null))
                .flatMap(file -> ServerResponse.ok()
                        .contentType(MediaType.parseMediaType(file.contentType()))
                        .header(
                                "Content-Disposition",
                                ContentDisposition.attachment()
                                        .filename(file.fileName(), StandardCharsets.UTF_8)
                                        .build()
                                        .toString())
                        .bodyValue(file.content()));
    }

    /**
     * 提交异步导出任务。
     *
     * <p>用于记录数超出同步导出上限的场景：提交后立即返回任务标识，
     * 由后台调度器分页生成 CSV。</p>
     *
     * @param request HTTP 请求，查询参数 {@code from}、{@code to}、{@code status}
     * @return 统一成功响应，data 为任务视图
     */
    @RequirePermission("system:operations:health:export:task:create")
    @Operation(
            operationId = "operationsHealthSubmitExportTask",
            summary = "提交体检快照异步导出任务",
            description = "记录数超过异步导出上限时拒绝；任务由后台生成 CSV，完成后经下载端点获取")
    @Parameter(
            name = "from",
            description = "生成时间下界(ISO 8601)",
            in = ParameterIn.QUERY,
            schema = @Schema(type = "string", format = "date-time"))
    @Parameter(
            name = "to",
            description = "生成时间上界(ISO 8601)",
            in = ParameterIn.QUERY,
            schema = @Schema(type = "string", format = "date-time"))
    @Parameter(
            name = "status",
            description = "总体结论(UP/DEGRADED/DOWN/UNKNOWN)",
            in = ParameterIn.QUERY,
            schema = @Schema(type = "string"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "提交成功，返回任务标识"),
        @ApiResponse(responseCode = "400", description = "时间格式非法、结论取值非法或记录数超过导出上限"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要system:operations:health:export:task:create权限")
    })
    @OperationLog(
            module = "OperationsHealth",
            type = OperationLog.OperationType.OTHER,
            description = "health-export-task-submit",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> submitExportTask(ServerRequest request) {
        return healthExportService
                .submit(
                        parseTime(request, "from"),
                        parseTime(request, "to"),
                        request.queryParam("status").orElse(null))
                .flatMap(this::buildSuccess);
    }

    /**
     * 查询异步导出任务状态。
     *
     * @param request HTTP 请求，路径参数 {@code id}
     * @return 统一成功响应，data 为任务视图
     */
    @RequirePermission("system:operations:health:export:task:read")
    @Operation(
            operationId = "operationsHealthGetExportTask",
            summary = "查询体检快照异步导出任务",
            description = "返回任务状态、尝试次数、失败原因与导出记录数；文件内容需经下载端点获取")
    @Parameter(name = "id", description = "任务ID", in = ParameterIn.PATH, schema = @Schema(type = "integer"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "查询成功"),
        @ApiResponse(responseCode = "404", description = "任务不存在"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要system:operations:health:export:task:read权限")
    })
    @OperationLog(
            module = "OperationsHealth",
            type = OperationLog.OperationType.SELECT,
            description = "health-export-task-get",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> getExportTask(ServerRequest request) {
        return healthExportService
                .findTask(parseId(request))
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "导出任务不存在")))
                .flatMap(this::buildSuccess);
    }

    /**
     * 下载异步导出任务的结果文件
     *
     * <p>解析路径任务 ID 后调用 {@code healthExportService.findDownload}，仅已完成任务可下载；
     * 任务不存在、未完成或跨租户时返回对应错误，成功时以附件形式输出 CSV。
     *
     * @param request HTTP 请求，路径参数 {@code id}
     * @return CSV 附件响应
     */
    @RequirePermission("system:operations:health:export:task:read")
    @Operation(
            operationId = "operationsHealthDownloadExportTask",
            summary = "下载体检快照异步导出结果",
            description = "仅已完成任务可下载；任务不存在、未完成或跨租户时按对应错误返回")
    @Parameter(name = "id", description = "任务ID", in = ParameterIn.PATH, schema = @Schema(type = "integer"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "下载成功，返回 CSV 文件流"),
        @ApiResponse(responseCode = "400", description = "任务尚未完成"),
        @ApiResponse(responseCode = "404", description = "任务不存在"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要system:operations:health:export:task:read权限")
    })
    @OperationLog(
            module = "OperationsHealth",
            type = OperationLog.OperationType.SELECT,
            description = "health-export-task-download",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> downloadExportTask(ServerRequest request) {
        return healthExportService
                .findDownload(parseId(request))
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "导出任务不存在")))
                .flatMap(file -> ServerResponse.ok()
                        .contentType(MediaType.parseMediaType(file.contentType()))
                        .header(
                                "Content-Disposition",
                                ContentDisposition.attachment()
                                        .filename(file.fileName(), StandardCharsets.UTF_8)
                                        .build()
                                        .toString())
                        .bodyValue(file.content()));
    }

    /**
     * 查询体检趋势
     *
     * <p>解析可选 {@code limit}（缺省 0 表示不限制）与 ISO 8601 时间范围后调用
     * {@code operationsHealthService.trend}，给出结论分布、首尾状态与是否收敛；样本不足两个时不判定改善。
     *
     * @param request HTTP 请求，查询参数 {@code from}、{@code to}（ISO 8601）、{@code limit}
     * @return 统一成功响应，data 为趋势分析
     */
    @RequirePermission("system:operations:health:snapshot:list")
    @Operation(
            operationId = "operationsHealthTrend",
            summary = "查询体检趋势",
            description = "按时间范围给出结论分布、首尾状态与是否收敛，样本不足两个时不判定改善")
    @Parameter(
            name = "from",
            description = "起始时间(ISO 8601)，缺省回看7天",
            in = ParameterIn.QUERY,
            schema = @Schema(type = "string", format = "date-time"))
    @Parameter(
            name = "to",
            description = "结束时间(ISO 8601)，缺省为当前时间",
            in = ParameterIn.QUERY,
            schema = @Schema(type = "string", format = "date-time"))
    @Parameter(name = "limit", description = "取样上限", in = ParameterIn.QUERY, schema = @Schema(type = "integer"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "查询成功"),
        @ApiResponse(responseCode = "400", description = "起始时间晚于结束时间或时间格式非法"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要system:operations:health:snapshot:list权限")
    })
    @OperationLog(
            module = "OperationsHealth",
            type = OperationLog.OperationType.SELECT,
            description = "health-trend",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> trend(ServerRequest request) {
        int limit = safeParseInt(request.queryParam("limit").orElse(null), 0);
        return operationsHealthService
                .trend(parseTime(request, "from"), parseTime(request, "to"), limit)
                .flatMap(this::buildSuccess);
    }

    /**
     * 查询租户体检摘要（受限口径）
     *
     * <p>调用 {@code operationsHealthService.tenantSummary()} 返回当前租户最近一次体检的结论级摘要，
     * 不含实例标识与逐项证据。
     *
     * @param request HTTP 请求
     * @return 统一成功响应，data 为租户摘要
     */
    @RequirePermission("system:operations:health:tenant:read")
    @Operation(
            operationId = "operationsTenantHealthSummary",
            summary = "查询租户体检摘要",
            description = "只返回当前租户最近一次体检的总体结论、风险计数与近期趋势，不含实例标识与逐项证据")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "查询成功"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要system:operations:health:tenant:read权限")
    })
    @OperationLog(
            module = "OperationsHealth",
            type = OperationLog.OperationType.SELECT,
            description = "tenant-health-summary",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> tenantSummary(ServerRequest request) {
        return operationsHealthService.tenantSummary().flatMap(this::buildSuccess);
    }

    /** 把体检报告收敛为结论级快照命令，避免应用层依赖接口层 DTO。 */
    private static HealthSnapshotCommand toCommand(HealthReportResponse report) {
        HealthReportResponse.StatusCounts counts = report.counts();
        return HealthSnapshotCommand.builder()
                .captureSource("MANUAL")
                .serviceName(report.service())
                .instanceId(report.instance())
                .scope(report.scope())
                .ruleVersion(report.ruleVersion())
                .overallStatus(report.overallStatus())
                .summary(report.summary())
                .upCount(counts == null ? null : counts.up())
                .degradedCount(counts == null ? null : counts.degraded())
                .downCount(counts == null ? null : counts.down())
                .unknownCount(counts == null ? null : counts.unknown())
                .notConfiguredCount(counts == null ? null : counts.notConfigured())
                .generatedAt(parseReportTime(report.generatedAt()))
                .build();
    }

    /** 体检报告的生成时间以 ISO-8601 文本给出，落库前转为本地时间；无法解析时以当前时间兜底。 */
    private static LocalDateTime parseReportTime(String generatedAt) {
        if (generatedAt == null || generatedAt.isBlank()) {
            return LocalDateTime.now();
        }
        try {
            return LocalDateTime.parse(generatedAt, DateTimeFormatter.ISO_DATE_TIME);
        } catch (DateTimeParseException e) {
            return LocalDateTime.now();
        }
    }

    private static LocalDateTime parseTime(ServerRequest request, String name) {
        return request.queryParam(name)
                .filter(value -> !value.isBlank())
                .map(value -> {
                    try {
                        return LocalDateTime.parse(value, DateTimeFormatter.ISO_DATE_TIME);
                    } catch (DateTimeParseException e) {
                        throw new BusinessException(ResultCode.PARAM_ERROR, "时间参数格式非法: " + name);
                    }
                })
                .orElse(null);
    }

    private static Long parseId(ServerRequest request) {
        String raw = request.pathVariable("id");
        try {
            return Long.valueOf(raw);
        } catch (NumberFormatException e) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "任务ID格式非法: " + raw);
        }
    }

    private static int safePageNum(ServerRequest request) {
        int page = request.queryParam("page").map(Integer::parseInt).orElse(1);
        return page < 1 ? 1 : page;
    }

    private static int safePageSize(ServerRequest request) {
        int size = request.queryParam("size").map(Integer::parseInt).orElse(20);
        return size < 1 ? 20 : size;
    }
}
