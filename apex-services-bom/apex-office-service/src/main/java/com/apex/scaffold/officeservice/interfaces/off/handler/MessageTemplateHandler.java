package com.apex.scaffold.officeservice.interfaces.off.handler;

import com.apex.scaffold.common.annotation.OperationLog;
import com.apex.scaffold.common.annotation.RequirePermission;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.office.off.application.dto.MessageTemplateDTO;
import com.apex.scaffold.office.off.application.service.OffMessageTemplateService;
import com.apex.scaffold.officeservice.interfaces.util.RequestHandlerUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * 消息模板配置接口，异常由 {@link RequestHandlerUtil} 转换为 HTTP 400 业务响应。
 *
 * @author Apex Team
 * @since 1.3.1
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Tag(name = "消息模板管理", description = "消息模板配置相关接口")
public class MessageTemplateHandler {
    private final OffMessageTemplateService templateService;
    private final RequestHandlerUtil requestHandlerUtil;

    /**
     * 按编码查询模板；不存在时返回 HTTP 400，业务码为 NOT_FOUND。
     * @param request 包含 templateCode 路径参数的请求
     * @return data 为模板的统一响应
     */
    @RequirePermission("office:notify:template:read")
    @Operation(
            operationId = "getTemplateByCode",
            summary = "根据编码获取模板",
            parameters =
                    @Parameter(
                            name = "templateCode",
                            in = ParameterIn.PATH,
                            required = true,
                            schema = @Schema(type = "string")),
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "获取成功",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = TemplateResult.class))),
                @ApiResponse(
                        responseCode = "400",
                        description = "模板不存在或处理失败",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = TemplateVoidResult.class)))
            })
    @OperationLog(
            module = "消息模板",
            type = OperationLog.OperationType.SELECT,
            description = "根据编码获取模板",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> getTemplateByCode(ServerRequest request) {
        String templateCode = request.pathVariable("templateCode");

        return templateService
                .getTemplateByCode(templateCode)
                .flatMap(requestHandlerUtil::buildSuccessResponse)
                .switchIfEmpty(requestHandlerUtil.buildErrorResponse(ResultCode.NOT_FOUND, "模板不存在"))
                .onErrorResume(this::handleError);
    }

    /**
     * 查询全部启用模板，不读取类型过滤参数。
     * @param request HTTP 请求
     * @return data 为模板数组的统一响应
     */
    @RequirePermission("office:notify:template:read")
    @Operation(
            operationId = "getEnabledTemplates",
            summary = "获取所有启用的模板",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "获取成功",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = TemplateListResult.class))),
                @ApiResponse(
                        responseCode = "400",
                        description = "处理失败",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = TemplateVoidResult.class)))
            })
    @OperationLog(
            module = "消息模板",
            type = OperationLog.OperationType.SELECT,
            description = "获取启用模板列表",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> getEnabledTemplates(ServerRequest request) {
        return templateService
                .getAllEnabledTemplates()
                .collectList()
                .map(list -> list.isEmpty() ? Collections.emptyList() : list)
                .flatMap(requestHandlerUtil::buildSuccessResponse)
                .onErrorResume(this::handleError);
    }

    /**
     * 校验并创建消息模板。
     *
     * <p>请求体映射为 {@link MessageTemplateDTO} 后交模板服务创建，校验失败或业务失败
     * 统一经错误映射返回 400。
     *
     * @param request JSON 请求体为 MessageTemplateDTO
     * @return 200 响应，{@code data} 为已创建的模板；校验或处理失败返回 400
     */
    @RequirePermission("office:notify:template:manage")
    @Operation(
            operationId = "createTemplate",
            summary = "创建消息模板",
            requestBody =
                    @RequestBody(
                            required = true,
                            content =
                                    @Content(
                                            mediaType = "application/json",
                                            schema = @Schema(implementation = MessageTemplateDTO.class))),
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "创建成功",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = TemplateResult.class))),
                @ApiResponse(
                        responseCode = "400",
                        description = "校验或业务处理失败",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = TemplateVoidResult.class)))
            })
    @OperationLog(
            module = "消息模板",
            type = OperationLog.OperationType.INSERT,
            description = "创建消息模板",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> createTemplate(ServerRequest request) {
        return requestHandlerUtil
                .validateAndMap(request, MessageTemplateDTO.class)
                .flatMap(templateService::createTemplate)
                .flatMap(requestHandlerUtil::buildSuccessResponse)
                .onErrorResume(this::handleError);
    }

    /**
     * 更新模板；无效 ID 或模板不存在时返回 HTTP 400。
     *
     * <p>模板主键经安全解析，非数字时直接返回参数错误；请求体映射后交模板服务局部更新，
     * 模板不存在时返回业务码 {@code NOT_FOUND}。
     *
     * @param request 包含 templateId 路径参数与 MessageTemplateDTO 请求体
     * @return 200 响应，{@code data} 为更新后的模板；ID 无效、模板不存在或处理失败返回 400
     */
    @RequirePermission("office:notify:template:manage")
    @Operation(
            operationId = "updateTemplate",
            summary = "更新消息模板",
            parameters =
                    @Parameter(
                            name = "templateId",
                            in = ParameterIn.PATH,
                            required = true,
                            schema = @Schema(type = "integer", format = "int64")),
            requestBody =
                    @RequestBody(
                            required = true,
                            content =
                                    @Content(
                                            mediaType = "application/json",
                                            schema = @Schema(implementation = MessageTemplateDTO.class))),
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "更新成功",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = TemplateResult.class))),
                @ApiResponse(
                        responseCode = "400",
                        description = "ID无效、模板不存在或处理失败",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = TemplateVoidResult.class)))
            })
    @OperationLog(
            module = "消息模板",
            type = OperationLog.OperationType.UPDATE,
            description = "更新消息模板",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> updateTemplate(ServerRequest request) {
        Long templateId = RequestHandlerUtil.safeParseLong(request.pathVariable("templateId"));
        if (templateId == null) {
            return requestHandlerUtil.buildErrorResponse(ResultCode.PARAM_ERROR, "模板ID无效");
        }

        return requestHandlerUtil
                .validateAndMap(request, MessageTemplateDTO.class)
                .flatMap(dto -> templateService.updateTemplate(templateId, dto))
                .flatMap(requestHandlerUtil::buildSuccessResponse)
                .switchIfEmpty(requestHandlerUtil.buildErrorResponse(ResultCode.NOT_FOUND, "模板不存在"))
                .onErrorResume(this::handleError);
    }

    /**
     * 删除指定模板。
     *
     * <p>模板主键经安全解析，非数字时直接返回参数错误；删除成功返回空 {@code data}，
     * 处理失败经错误映射返回 400。
     *
     * @param request 包含 templateId 路径参数的请求
     * @return 200 响应，{@code data} 为空；ID 无效或处理失败返回 400
     */
    @RequirePermission("office:notify:template:manage")
    @Operation(
            operationId = "deleteTemplate",
            summary = "删除消息模板",
            parameters =
                    @Parameter(
                            name = "templateId",
                            in = ParameterIn.PATH,
                            required = true,
                            schema = @Schema(type = "integer", format = "int64")),
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "删除成功",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = TemplateVoidResult.class))),
                @ApiResponse(
                        responseCode = "400",
                        description = "ID无效或处理失败",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = TemplateVoidResult.class)))
            })
    @OperationLog(
            module = "消息模板",
            type = OperationLog.OperationType.DELETE,
            description = "删除消息模板",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> deleteTemplate(ServerRequest request) {
        Long templateId = RequestHandlerUtil.safeParseLong(request.pathVariable("templateId"));
        if (templateId == null) {
            return requestHandlerUtil.buildErrorResponse(ResultCode.PARAM_ERROR, "模板ID无效");
        }

        return templateService
                .deleteTemplate(templateId)
                .then(Mono.defer(() -> requestHandlerUtil.buildSuccessResponse(null)))
                .onErrorResume(this::handleError);
    }

    private Mono<ServerResponse> handleError(Throwable throwable) {
        log.error("消息模板处理失败: {}", throwable.getMessage(), throwable);
        return requestHandlerUtil.handleError(throwable);
    }

    /** TemplateResult MessageTemplate 统一响应包装。 */
    @Schema(name = "OfficeTemplateResult")
    public static class TemplateResult extends Result<MessageTemplateDTO> {}

    /** TemplateListResult MessageTemplate 列表统一响应包装。 */
    @Schema(name = "OfficeTemplateListResult")
    public static class TemplateListResult extends Result<List<MessageTemplateDTO>> {}

    /** TemplateVoidResult 无数据统一响应包装。 */
    @Schema(name = "OfficeTemplateVoidResult")
    public static class TemplateVoidResult extends Result<Void> {}
}
