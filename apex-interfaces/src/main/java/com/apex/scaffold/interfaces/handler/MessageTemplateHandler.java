package com.apex.scaffold.interfaces.handler;

import com.apex.scaffold.application.dto.MessageTemplateDTO;
import com.apex.scaffold.application.service.OffMessageTemplateService;
import com.apex.scaffold.common.annotation.OperationLog;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.common.util.TraceIdUtil;
import com.apex.scaffold.interfaces.util.MessageUtils;
import com.apex.scaffold.interfaces.util.RequestHandlerUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * 消息模板处理器（函数式端点）。
 *
 * <p>提供消息模板的查询、创建、更新与删除 API，用于管理系统中的各类消息模板配置。
 * 消息模板支持模板编码查询与启用状态过滤，是消息推送与通知功能的基础能力。
 *
 * <h2>核心功能</h2>
 * <ul>
 *   <li>模板查询：根据模板编码获取模板详情、查询所有启用的模板</li>
 *   <li>模板管理：创建、更新、删除消息模板</li>
 * </ul>
 *
 * <h2>接口列表</h2>
 * <ul>
 *   <li>GET /api/v1/message-templates/{templateCode} - 根据编码获取模板</li>
 *   <li>GET /api/v1/message-templates - 获取所有启用的模板</li>
 *   <li>POST /api/v1/message-templates - 创建消息模板</li>
 *   <li>PUT /api/v1/message-templates/{templateId} - 更新消息模板</li>
 *   <li>DELETE /api/v1/message-templates/{templateId} - 删除消息模板</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see OffMessageTemplateService
 */
@Slf4j
@Component
@Tag(name = "消息模板管理", description = "消息模板配置相关接口")
public class MessageTemplateHandler extends BaseHandler {

    /** 消息模板应用服务 */
    private final OffMessageTemplateService templateService;

    /** 请求处理工具类 */
    private final RequestHandlerUtil requestHandlerUtil;

    /**
     * 构造消息模板处理器。
     *
     * @param templateService 消息模板应用服务
     * @param requestHandlerUtil 请求处理工具类
     * @param messageUtils 国际化消息工具
     */
    public MessageTemplateHandler(
            OffMessageTemplateService templateService,
            RequestHandlerUtil requestHandlerUtil,
            MessageUtils messageUtils) {
        super(messageUtils);
        this.templateService = templateService;
        this.requestHandlerUtil = requestHandlerUtil;
    }

    /**
     * 根据编码获取消息模板。
     *
     * <p>通过模板编码获取消息模板的详细信息，包含模板内容、类型、状态等配置。</p>
     *
     * @param request HTTP 请求，路径参数 {@code templateCode} 为模板编码
     * @return 带 traceId 的统一成功响应，data 为 {@link MessageTemplateDTO}
     */
    @Parameters({@Parameter(name = "templateCode", in = ParameterIn.PATH, required = true, description = "模板编码")})
    @Operation(summary = "根据编码获取模板", description = "通过模板编码获取消息模板详情")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "获取成功",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = MessageTemplateDTO.class))),
        @ApiResponse(
                responseCode = "404",
                description = "模板不存在",
                content = @Content(schema = @Schema(implementation = Result.class)))
    })
    @OperationLog(module = "消息模板", type = OperationLog.OperationType.SELECT, description = "根据编码获取模板")
    public Mono<ServerResponse> getTemplateByCode(ServerRequest request) {
        String traceId = TraceIdUtil.getTraceId();
        String templateCode = request.pathVariable("templateCode");

        return templateService
                .getTemplateByCode(templateCode)
                .flatMap(this::buildSuccess)
                .switchIfEmpty(buildError(ResultCode.NOT_FOUND, traceId));
    }

    /**
     * 获取所有启用的消息模板。
     *
     * <p>查询系统中所有处于启用状态的消息模板列表，用于前端下拉选择或批量展示。</p>
     *
     * @param request HTTP 请求，可选查询参数 {@code templateType} 用于按类型过滤
     * @return 带 traceId 的统一成功响应，data 为 {@link MessageTemplateDTO} 列表
     */
    @Parameters({@Parameter(name = "templateType", in = ParameterIn.QUERY, description = "模板类型")})
    @Operation(summary = "获取所有启用的模板", description = "获取所有已启用的消息模板列表")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "获取成功", content = @Content(mediaType = "application/json"))
    })
    @OperationLog(module = "消息模板", type = OperationLog.OperationType.SELECT, description = "获取启用模板列表")
    public Mono<ServerResponse> getEnabledTemplates(ServerRequest request) {
        String traceId = TraceIdUtil.getTraceId();
        return templateService
                .getAllEnabledTemplates()
                .collectList()
                .flatMap(this::buildSuccess)
                .switchIfEmpty(buildSuccess(java.util.Collections.emptyList()))
                .onErrorResume(e -> {
                    log.error("获取模板列表失败: {}", e.getMessage(), e);
                    return buildError(ResultCode.ERROR, traceId);
                });
    }

    /**
     * 创建消息模板。
     *
     * <p>创建一个新的消息模板配置，包括模板编码、名称、内容、类型等信息。</p>
     *
     * @param request HTTP 请求，请求体为 {@link MessageTemplateDTO}
     * @return 带 traceId 的统一成功响应，data 为已创建的 {@link MessageTemplateDTO}
     */
    @Operation(summary = "创建消息模板", description = "创建新的消息模板")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "创建成功",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = MessageTemplateDTO.class))),
        @ApiResponse(
                responseCode = "400",
                description = "参数错误",
                content = @Content(schema = @Schema(implementation = Result.class)))
    })
    @OperationLog(module = "消息模板", type = OperationLog.OperationType.INSERT, description = "创建消息模板")
    public Mono<ServerResponse> createTemplate(ServerRequest request) {
        String traceId = TraceIdUtil.getTraceId();
        return requestHandlerUtil
                .validateAndMap(request, MessageTemplateDTO.class)
                .flatMap(templateService::createTemplate)
                .flatMap(this::buildSuccess)
                .onErrorResume(e -> {
                    log.error("创建模板失败: {}", e.getMessage(), e);
                    return buildError(ResultCode.ERROR, traceId);
                });
    }

    /**
     * 更新指定消息模板。
     *
     * <p>解析并校验请求体，按路径参数 {@code templateId} 更新模板内容；模板不存在时返回 404。</p>
     *
     * @param request HTTP 请求，路径参数 {@code templateId} 为模板 ID，请求体为模板更新参数
     * @return 带 traceId 的统一成功响应，data 为更新后的 {@link MessageTemplateDTO}
     */
    @Parameters({@Parameter(name = "templateId", in = ParameterIn.PATH, required = true, description = "模板ID")})
    @Operation(summary = "更新消息模板", description = "更新指定消息模板")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "更新成功",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = MessageTemplateDTO.class))),
        @ApiResponse(
                responseCode = "404",
                description = "模板不存在",
                content = @Content(schema = @Schema(implementation = Result.class)))
    })
    @OperationLog(module = "消息模板", type = OperationLog.OperationType.UPDATE, description = "更新消息模板")
    public Mono<ServerResponse> updateTemplate(ServerRequest request) {
        String traceId = TraceIdUtil.getTraceId();
        Long templateId = safeParseLong(request.pathVariable("templateId"));
        if (templateId == null) {
            return buildError(ResultCode.PARAM_ERROR, traceId);
        }

        return requestHandlerUtil
                .validateAndMap(request, MessageTemplateDTO.class)
                .flatMap(dto -> templateService.updateTemplate(templateId, dto))
                .flatMap(this::buildSuccess)
                .switchIfEmpty(buildError(ResultCode.NOT_FOUND, traceId))
                .onErrorResume(e -> {
                    log.error("更新模板失败: {}", e.getMessage(), e);
                    return buildError(ResultCode.ERROR, traceId);
                });
    }

    /**
     * 删除指定消息模板。
     *
     * <p>按路径参数 {@code templateId} 删除消息模板，成功后返回空数据。</p>
     *
     * @param request HTTP 请求，路径参数 {@code templateId} 为模板 ID
     * @return 带 traceId 的统一成功响应，无业务数据
     */
    @Parameters({@Parameter(name = "templateId", in = ParameterIn.PATH, required = true, description = "模板ID")})
    @Operation(summary = "删除消息模板", description = "删除指定消息模板")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "删除成功", content = @Content(mediaType = "application/json"))
    })
    @OperationLog(module = "消息模板", type = OperationLog.OperationType.DELETE, description = "删除消息模板")
    public Mono<ServerResponse> deleteTemplate(ServerRequest request) {
        String traceId = TraceIdUtil.getTraceId();
        Long templateId = safeParseLong(request.pathVariable("templateId"));
        if (templateId == null) {
            return buildError(ResultCode.PARAM_ERROR, traceId);
        }

        return templateService
                .deleteTemplate(templateId)
                .then(buildSuccess(null))
                .onErrorResume(e -> {
                    log.error("删除模板失败: {}", e.getMessage(), e);
                    return buildError(ResultCode.ERROR, traceId);
                });
    }
}
