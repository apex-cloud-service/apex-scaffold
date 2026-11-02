package com.apex.scaffold.officeservice.interfaces.handler.office;

import com.apex.scaffold.common.annotation.OperationLog;
import com.apex.scaffold.common.annotation.RequirePermission;
import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.office.notify.application.NotifyApplicationService;
import com.apex.scaffold.officeservice.interfaces.assembler.office.NotifyAssembler;
import com.apex.scaffold.officeservice.interfaces.dto.office.notify.CreateNotifyTemplateRequest;
import com.apex.scaffold.officeservice.interfaces.dto.office.notify.NotifyRequestRecordResponse;
import com.apex.scaffold.officeservice.interfaces.dto.office.notify.NotifyTemplateResponse;
import com.apex.scaffold.officeservice.interfaces.dto.office.notify.UpdateNotifyTemplateRequest;
import com.apex.scaffold.officeservice.interfaces.security.AuthenticatedUser;
import com.apex.scaffold.officeservice.interfaces.util.RequestHandlerUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * 统一通知管理处理器（统一消息门面 / 批次 D1）
 *
 * <p>提供通知模板维护与投递留痕查询接口：
 * <ul>
 *   <li>模板增删改查 - 需要 {@code office:notify:template:manage} / {@code office:notify:template:read}</li>
 *   <li>投递留痕分页 - 需要 {@code office:notify:record:read}，成功与失败留痕均可查</li>
 * </ul>
 *
 * <p>按模板投递的能力由内部契约接口 {@code POST /internal/v1/notify} 承载，
 * 不在本处理器范围。租户取自受信任的认证上下文，不接受客户端自行传入。
 *
 * @author Apex Scaffold Team
 * @since 1.4.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Tag(name = "统一通知管理", description = "通知模板维护与投递留痕查询相关接口")
public class OfficeNotifyHandler {

    private final NotifyApplicationService notifyApplicationService;
    private final NotifyAssembler assembler;
    private final RequestHandlerUtil requestHandlerUtil;

    /** 模板详情的统一响应。 */
    @Schema(name = "OfficeNotifyTemplateResult")
    public static class NotifyTemplateResult extends Result<NotifyTemplateResponse> {}

    /** 模板分页结果的统一响应。 */
    @Schema(name = "OfficeNotifyTemplatePageResult")
    public static class NotifyTemplatePageResult extends Result<PageResponse<NotifyTemplateResponse>> {}

    /** 模板写操作的布尔结果。 */
    @Schema(name = "OfficeNotifyTemplateBooleanResult")
    public static class NotifyTemplateBooleanResult extends Result<Boolean> {}

    /** 投递留痕分页结果的统一响应。 */
    @Schema(name = "OfficeNotifyRecordPageResult")
    public static class NotifyRecordPageResult extends Result<PageResponse<NotifyRequestRecordResponse>> {}

    /**
     * 创建通知模板
     *
     * @param request HTTP 请求，请求体为模板创建参数
     * @return 创建成功的模板
     */
    @Operation(operationId = "createNotifyTemplate", summary = "创建通知模板", description = "创建统一通知模板，编码在租户内唯一")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            description = "通知模板创建请求",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CreateNotifyTemplateRequest.class)))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "创建成功",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = NotifyTemplateResult.class))),
        @ApiResponse(
                responseCode = "400",
                description = "请求参数错误或模板编码重复",
                content = @Content(schema = @Schema(implementation = Result.class)))
    })
    @RequirePermission("office:notify:template:manage")
    @OperationLog(
            module = "OfficeNotify",
            type = OperationLog.OperationType.INSERT,
            description = "createNotifyTemplate",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> createTemplate(ServerRequest request) {
        return trustedTenantId(request)
                .flatMap(tenantId -> requestHandlerUtil
                        .validateAndMap(request, CreateNotifyTemplateRequest.class)
                        .flatMap(req -> notifyApplicationService.createTemplate(tenantId, assembler.toEntity(req))))
                .map(assembler::toResponse)
                .flatMap(response -> requestHandlerUtil.ok(Result.success(response)))
                .onErrorResume(e -> requestHandlerUtil.handleError(e));
    }

    /**
     * 更新通知模板
     *
     * @param request HTTP 请求，路径参数 templateId，请求体为变更项
     * @return 更新后的模板
     */
    @Operation(operationId = "updateNotifyTemplate", summary = "更新通知模板", description = "局部更新通知模板，仅提交的字段生效")
    @Parameters({
        @Parameter(
                name = "templateId",
                in = ParameterIn.PATH,
                required = true,
                description = "模板ID",
                schema = @Schema(type = "integer", format = "int64"))
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            description = "通知模板更新请求",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = UpdateNotifyTemplateRequest.class)))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "更新成功",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = NotifyTemplateResult.class))),
        @ApiResponse(
                responseCode = "400",
                description = "请求参数错误或模板编码重复",
                content = @Content(schema = @Schema(implementation = Result.class))),
        @ApiResponse(
                responseCode = "404",
                description = "模板不存在",
                content = @Content(schema = @Schema(implementation = Result.class)))
    })
    @RequirePermission("office:notify:template:manage")
    @OperationLog(
            module = "OfficeNotify",
            type = OperationLog.OperationType.UPDATE,
            description = "updateNotifyTemplate",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> updateTemplate(ServerRequest request) {
        Long templateId = RequestHandlerUtil.safeParseLong(request.pathVariable("templateId"));
        return trustedTenantId(request)
                .flatMap(tenantId -> requestHandlerUtil
                        .validateAndMap(request, UpdateNotifyTemplateRequest.class)
                        .flatMap(req -> notifyApplicationService.updateTemplate(
                                tenantId, templateId, assembler.toChanges(req))))
                .map(assembler::toResponse)
                .flatMap(response -> requestHandlerUtil.ok(Result.success(response)))
                .onErrorResume(e -> requestHandlerUtil.handleError(e));
    }

    /**
     * 删除通知模板
     *
     * @param request HTTP 请求，路径参数 templateId
     * @return 删除结果
     */
    @Operation(operationId = "deleteNotifyTemplate", summary = "删除通知模板", description = "逻辑删除通知模板，历史投递留痕保留，data 为布尔结果")
    @Parameters({
        @Parameter(
                name = "templateId",
                in = ParameterIn.PATH,
                required = true,
                description = "模板ID",
                schema = @Schema(type = "integer", format = "int64"))
    })
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "删除成功",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = NotifyTemplateBooleanResult.class))),
        @ApiResponse(
                responseCode = "404",
                description = "模板不存在",
                content = @Content(schema = @Schema(implementation = Result.class)))
    })
    @RequirePermission("office:notify:template:manage")
    @OperationLog(
            module = "OfficeNotify",
            type = OperationLog.OperationType.DELETE,
            description = "deleteNotifyTemplate",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> deleteTemplate(ServerRequest request) {
        Long templateId = RequestHandlerUtil.safeParseLong(request.pathVariable("templateId"));
        return trustedTenantId(request)
                .flatMap(tenantId -> notifyApplicationService.deleteTemplate(tenantId, templateId))
                .flatMap(deleted -> requestHandlerUtil.ok(Result.success(deleted)))
                .onErrorResume(e -> requestHandlerUtil.handleError(e));
    }

    /**
     * 查询通知模板详情
     *
     * @param request HTTP 请求，路径参数 templateId
     * @return 模板详情
     */
    @Operation(operationId = "getNotifyTemplate", summary = "查询通知模板详情", description = "按模板ID查询详情")
    @Parameters({
        @Parameter(
                name = "templateId",
                in = ParameterIn.PATH,
                required = true,
                description = "模板ID",
                schema = @Schema(type = "integer", format = "int64"))
    })
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "查询成功",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = NotifyTemplateResult.class))),
        @ApiResponse(
                responseCode = "404",
                description = "模板不存在",
                content = @Content(schema = @Schema(implementation = Result.class)))
    })
    @RequirePermission("office:notify:template:read")
    @OperationLog(
            module = "OfficeNotify",
            type = OperationLog.OperationType.SELECT,
            description = "getNotifyTemplate",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> getTemplate(ServerRequest request) {
        Long templateId = RequestHandlerUtil.safeParseLong(request.pathVariable("templateId"));
        return trustedTenantId(request)
                .flatMap(tenantId -> notifyApplicationService.getTemplate(tenantId, templateId))
                .map(assembler::toResponse)
                .flatMap(response -> requestHandlerUtil.ok(Result.success(response)))
                .onErrorResume(e -> requestHandlerUtil.handleError(e));
    }

    /**
     * 分页查询通知模板
     *
     * @param request HTTP 请求，查询参数 pageNum、pageSize、channel、keyword
     * @return 模板分页结果
     */
    @Operation(operationId = "listNotifyTemplates", summary = "分页查询通知模板", description = "按通道与关键字分页查询通知模板")
    @Parameters({
        @Parameter(
                name = "pageNum",
                in = ParameterIn.QUERY,
                description = "页码，从1开始",
                schema = @Schema(type = "integer")),
        @Parameter(
                name = "pageSize",
                in = ParameterIn.QUERY,
                description = "每页条数，上限200",
                schema = @Schema(type = "integer")),
        @Parameter(
                name = "channel",
                in = ParameterIn.QUERY,
                description = "通道过滤：INBOX/FEISHU/DINGTALK/WECOM/SMS/EMAIL",
                schema = @Schema(type = "string")),
        @Parameter(
                name = "keyword",
                in = ParameterIn.QUERY,
                description = "编码或主题模糊匹配",
                schema = @Schema(type = "string"))
    })
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "查询成功",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = NotifyTemplatePageResult.class)))
    })
    @RequirePermission("office:notify:template:read")
    @OperationLog(
            module = "OfficeNotify",
            type = OperationLog.OperationType.SELECT,
            description = "listNotifyTemplates",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> listTemplates(ServerRequest request) {
        Integer pageNum =
                RequestHandlerUtil.safeParseInt(request.queryParam("pageNum").orElse(null), 1);
        Integer pageSize =
                RequestHandlerUtil.safeParseInt(request.queryParam("pageSize").orElse(null), 20);
        String channel = request.queryParam("channel").orElse(null);
        String keyword = request.queryParam("keyword").orElse(null);
        return trustedTenantId(request)
                .flatMap(tenantId ->
                        notifyApplicationService.listTemplates(tenantId, pageNum, pageSize, channel, keyword))
                .map(page -> PageResponse.of(
                        assembler.toResponseList(page.getRecords()),
                        page.getTotal(),
                        page.getPageNum(),
                        page.getPageSize()))
                .flatMap(response -> requestHandlerUtil.ok(Result.success(response)))
                .onErrorResume(e -> requestHandlerUtil.handleError(e));
    }

    /**
     * 分页查询投递留痕
     *
     * @param request HTTP 请求，查询参数 pageNum、pageSize、channel、templateCode、status
     * @return 投递留痕分页结果
     */
    @Operation(operationId = "listNotifyRecords", summary = "分页查询投递留痕", description = "按通道、模板编码与投递状态分页查询留痕")
    @Parameters({
        @Parameter(
                name = "pageNum",
                in = ParameterIn.QUERY,
                description = "页码，从1开始",
                schema = @Schema(type = "integer")),
        @Parameter(
                name = "pageSize",
                in = ParameterIn.QUERY,
                description = "每页条数，上限200",
                schema = @Schema(type = "integer")),
        @Parameter(name = "channel", in = ParameterIn.QUERY, description = "通道过滤", schema = @Schema(type = "string")),
        @Parameter(
                name = "templateCode",
                in = ParameterIn.QUERY,
                description = "模板编码精确过滤",
                schema = @Schema(type = "string")),
        @Parameter(
                name = "status",
                in = ParameterIn.QUERY,
                description = "投递状态：PENDING/SUCCESS/FAILED",
                schema = @Schema(type = "string"))
    })
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "查询成功",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = NotifyRecordPageResult.class)))
    })
    @RequirePermission("office:notify:record:read")
    @OperationLog(
            module = "OfficeNotify",
            type = OperationLog.OperationType.SELECT,
            description = "listNotifyRecords",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> listRecords(ServerRequest request) {
        Integer pageNum =
                RequestHandlerUtil.safeParseInt(request.queryParam("pageNum").orElse(null), 1);
        Integer pageSize =
                RequestHandlerUtil.safeParseInt(request.queryParam("pageSize").orElse(null), 20);
        String channel = request.queryParam("channel").orElse(null);
        String templateCode = request.queryParam("templateCode").orElse(null);
        String status = request.queryParam("status").orElse(null);
        return trustedTenantId(request)
                .flatMap(tenantId -> notifyApplicationService.listRecords(
                        tenantId, pageNum, pageSize, channel, templateCode, status))
                .map(page -> PageResponse.of(
                        page.getRecords().stream().map(assembler::toResponse).toList(),
                        page.getTotal(),
                        page.getPageNum(),
                        page.getPageSize()))
                .flatMap(response -> requestHandlerUtil.ok(Result.success(response)))
                .onErrorResume(e -> requestHandlerUtil.handleError(e));
    }

    /** 租户只能来自已验证的认证上下文，不接受客户端头覆盖。 */
    private Mono<String> trustedTenantId(ServerRequest request) {
        return request.principal()
                .filter(AuthenticatedUser.class::isInstance)
                .cast(AuthenticatedUser.class)
                .map(AuthenticatedUser::tenantId)
                .filter(tenantId -> tenantId != null && !tenantId.isBlank())
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.UNAUTHORIZED, "未认证或缺少租户上下文")));
    }
}
