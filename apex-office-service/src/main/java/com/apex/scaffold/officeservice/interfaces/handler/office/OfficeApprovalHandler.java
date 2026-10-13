package com.apex.scaffold.officeservice.interfaces.handler.office;

import com.apex.scaffold.common.annotation.OperationLog;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.office.application.OfficeApprovalService;
import com.apex.scaffold.officeservice.interfaces.assembler.office.ApprovalProcessAssembler;
import com.apex.scaffold.officeservice.interfaces.dto.office.approval.ApprovalProcessResponse;
import com.apex.scaffold.officeservice.interfaces.dto.office.approval.ApproveRequest;
import com.apex.scaffold.officeservice.interfaces.dto.office.approval.InitiateApprovalRequest;
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
 * 办公平台审批Handler
 *
 * <p>处理审批相关的HTTP请求，包括：
 * <ul>
 *   <li>发起审批流程</li>
 *   <li>查询审批状态和详情</li>
 *   <li>执行审批操作（同意/拒绝等）</li>
 *   <li>撤销审批</li>
 *   <li>查询待审批列表</li>
 *   <li>查询我发起的审批</li>
 * </ul>
 *
 * <p>实现要点：
 * <ul>
 *   <li>使用{@link RequestHandlerUtil}进行统一的参数校验和响应转换</li>
 *   <li>完整的SpringDoc注解，生成清晰的在线API文档</li>
 *   <li>中文JavaDoc说明业务逻辑和参数含义</li>
 *   <li>响应式编程，返回{@link Mono}类型</li>
 * </ul>
 *
 * @author Apex Team
 * @since 1.3.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Tag(name = "办公平台审批管理", description = "办公平台审批流程发起、查询、审批操作、撤销等相关接口")
public class OfficeApprovalHandler {

    private final OfficeApprovalService approvalService;
    private final ApprovalProcessAssembler assembler;
    private final RequestHandlerUtil requestHandlerUtil;

    /**
     * 发起审批流程
     *
     * <p>业务逻辑：
     * <ol>
     *   <li>校验请求参数（平台类型、应用ID、流程定义ID等）</li>
     *   <li>调用应用服务创建审批流程</li>
     *   <li>返回流程实例ID和详细信息</li>
     * </ol>
     *
     * @param request HTTP请求
     * @return 审批流程响应
     */
    @OperationLog(module = "办公平台审批管理", type = OperationLog.OperationType.INSERT, description = "发起审批流程")
    @Operation(summary = "发起审批流程", description = "在指定办公平台创建审批流程实例，支持飞书、钉钉、企业微信等平台")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            description = "审批发起请求信息",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = InitiateApprovalRequest.class)))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "发起成功",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApprovalProcessResponse.class))),
        @ApiResponse(
                responseCode = "400",
                description = "请求参数错误",
                content = @Content(schema = @Schema(implementation = Result.class))),
        @ApiResponse(
                responseCode = "500",
                description = "发起失败",
                content = @Content(schema = @Schema(implementation = Result.class)))
    })
    public Mono<ServerResponse> initiateApproval(ServerRequest request) {
        log.info("收到发起审批请求");

        return requestHandlerUtil
                .validateAndMap(request, InitiateApprovalRequest.class)
                .flatMap(req -> approvalService.initiateApproval(
                        req.getPlatform(),
                        req.getAppId(),
                        req.getProcessDefinitionId(),
                        req.getInitiatorId(),
                        req.getFormData()))
                .map(assembler::toResponse)
                .flatMap(response -> requestHandlerUtil.ok(Result.success(response)))
                .doOnSuccess(res -> log.info("发起审批成功"))
                .onErrorResume(requestHandlerUtil::handleError);
    }

    /**
     * 查询审批详情
     *
     * <p>根据流程实例ID查询审批流程的完整信息，包括：
     * <ul>
     *   <li>基本信息（流程名称、发起人等）</li>
     *   <li>当前状态和审批节点</li>
     *   <li>审批记录列表</li>
     *   <li>表单数据</li>
     * </ul>
     *
     * @param request HTTP请求
     * @return 审批流程响应
     */
    @Operation(summary = "查询审批流程详情", description = "根据流程实例ID查询审批流程的完整信息，包括基本信息、当前状态、审批记录和表单数据")
    @Parameters({
        @Parameter(
                name = "processInstanceId",
                in = ParameterIn.PATH,
                required = true,
                description = "流程实例ID",
                schema = @Schema(type = "string"))
    })
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "查询成功",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApprovalProcessResponse.class))),
        @ApiResponse(
                responseCode = "404",
                description = "流程不存在",
                content = @Content(schema = @Schema(implementation = Result.class)))
    })
    public Mono<ServerResponse> getApprovalDetail(ServerRequest request) {
        String processInstanceId = request.pathVariable("processInstanceId");
        log.info("查询审批详情，流程实例ID：{}", processInstanceId);

        return approvalService
                .getApprovalDetail(processInstanceId)
                .map(assembler::toResponse)
                .flatMap(response -> requestHandlerUtil.ok(Result.success(response)))
                .doOnSuccess(res -> log.debug("查询审批详情成功"))
                .onErrorResume(requestHandlerUtil::handleError);
    }

    /**
     * 执行审批操作
     *
     * <p>审批人对待审批流程执行操作，支持的操作包括：
     * <ul>
     *   <li>APPROVE：同意</li>
     *   <li>REJECT：拒绝</li>
     *   <li>TRANSFER：转审</li>
     *   <li>ADD_SIGN：加签</li>
     * </ul>
     *
     * @param request HTTP请求
     * @return 更新后的审批流程响应
     */
    @OperationLog(module = "办公平台审批管理", type = OperationLog.OperationType.UPDATE, description = "执行审批操作")
    @Operation(summary = "执行审批操作", description = "审批人对待审批流程执行操作，支持同意、拒绝、转审、加签等操作")
    @Parameters({
        @Parameter(
                name = "processInstanceId",
                in = ParameterIn.PATH,
                required = true,
                description = "流程实例ID",
                schema = @Schema(type = "string"))
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            description = "审批操作请求信息",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApproveRequest.class)))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "审批操作成功",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApprovalProcessResponse.class))),
        @ApiResponse(
                responseCode = "400",
                description = "请求参数错误",
                content = @Content(schema = @Schema(implementation = Result.class))),
        @ApiResponse(
                responseCode = "403",
                description = "无权审批此流程",
                content = @Content(schema = @Schema(implementation = Result.class))),
        @ApiResponse(
                responseCode = "404",
                description = "流程不存在",
                content = @Content(schema = @Schema(implementation = Result.class)))
    })
    public Mono<ServerResponse> approveProcess(ServerRequest request) {
        String processInstanceId = request.pathVariable("processInstanceId");
        log.info("收到审批操作请求，流程实例ID：{}", processInstanceId);

        return requestHandlerUtil
                .validateAndMap(request, ApproveRequest.class)
                .flatMap(req -> approvalService.approveProcess(
                        processInstanceId, req.getApproverId(), req.getAction(), req.getComment()))
                .map(assembler::toResponse)
                .flatMap(response -> requestHandlerUtil.ok(Result.success(response)))
                .doOnSuccess(res -> log.info("审批操作完成"))
                .onErrorResume(requestHandlerUtil::handleError);
    }

    /**
     * 撤销审批
     *
     * <p>发起人撤销自己发起的审批流程。
     * 注意：只有发起人可以撤销，且流程必须处于待审批或审批中状态。
     *
     * @param request HTTP请求
     * @return 操作结果
     */
    @OperationLog(module = "办公平台审批管理", type = OperationLog.OperationType.DELETE, description = "撤销审批流程")
    @Operation(summary = "撤销审批流程", description = "发起人撤销自己发起的审批流程，只有发起人可以撤销，且流程必须处于待审批或审批中状态")
    @Parameters({
        @Parameter(
                name = "processInstanceId",
                in = ParameterIn.PATH,
                required = true,
                description = "流程实例ID",
                schema = @Schema(type = "string"))
    })
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "撤销成功",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = Result.class))),
        @ApiResponse(
                responseCode = "403",
                description = "无权撤销此流程",
                content = @Content(schema = @Schema(implementation = Result.class))),
        @ApiResponse(
                responseCode = "404",
                description = "流程不存在",
                content = @Content(schema = @Schema(implementation = Result.class))),
        @ApiResponse(
                responseCode = "409",
                description = "流程状态不允许撤销",
                content = @Content(schema = @Schema(implementation = Result.class)))
    })
    public Mono<ServerResponse> cancelApproval(ServerRequest request) {
        String processInstanceId = request.pathVariable("processInstanceId");
        log.info("撤销审批，流程实例ID：{}", processInstanceId);

        return approvalService
                .cancelApproval(processInstanceId)
                .flatMap(success -> requestHandlerUtil.ok(Result.success(success)))
                .doOnSuccess(res -> log.info("撤销审批完成"))
                .onErrorResume(requestHandlerUtil::handleError);
    }

    /**
     * 查询待审批列表
     *
     * <p>查询当前用户的待审批事项列表，包括：
     * <ul>
     *   <li>待我审批的流程</li>
     *   <li>流程基本信息</li>
     *   <li>发起人信息</li>
     *   <li>发起时间</li>
     * </ul>
     *
     * @param request HTTP请求
     * @return 待审批列表
     */
    @Operation(summary = "查询待审批列表", description = "查询当前用户的待审批事项列表，包括待我审批的流程、流程基本信息、发起人信息和发起时间")
    @Parameters({
        @Parameter(
                name = "approverId",
                in = ParameterIn.QUERY,
                required = true,
                description = "审批人ID",
                schema = @Schema(type = "string"))
    })
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "查询成功",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApprovalProcessResponse.class))),
        @ApiResponse(
                responseCode = "400",
                description = "请求参数错误",
                content = @Content(schema = @Schema(implementation = Result.class)))
    })
    public Mono<ServerResponse> getPendingApprovals(ServerRequest request) {
        String approverId =
                request.queryParam("approverId").orElseThrow(() -> new IllegalArgumentException("审批人ID不能为空"));
        log.info("查询待审批列表，审批人：{}", approverId);

        return approvalService
                .getPendingApprovals(approverId)
                .map(assembler::toResponse)
                .collectList()
                .flatMap(list -> requestHandlerUtil.ok(Result.success(list)))
                .doOnSuccess(res -> log.debug("查询待审批列表完成"))
                .onErrorResume(requestHandlerUtil::handleError);
    }

    /**
     * 查询我发起的审批
     *
     * <p>查询当前用户发起的所有审批流程，包括：
     * <ul>
     *   <li>进行中的流程</li>
     *   <li>已完成的流程</li>
     *   <li>已撤销的流程</li>
     * </ul>
     *
     * @param request HTTP请求
     * @return 我发起的审批列表
     */
    @Operation(summary = "查询我发起的审批", description = "查询当前用户发起的所有审批流程，包括进行中、已完成和已撤销的流程")
    @Parameters({
        @Parameter(
                name = "initiatorId",
                in = ParameterIn.QUERY,
                required = true,
                description = "发起人ID",
                schema = @Schema(type = "string"))
    })
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "查询成功",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApprovalProcessResponse.class))),
        @ApiResponse(
                responseCode = "400",
                description = "请求参数错误",
                content = @Content(schema = @Schema(implementation = Result.class)))
    })
    public Mono<ServerResponse> getMyInitiatedApprovals(ServerRequest request) {
        String initiatorId =
                request.queryParam("initiatorId").orElseThrow(() -> new IllegalArgumentException("发起人ID不能为空"));
        log.info("查询我发起的审批，发起人：{}", initiatorId);

        return approvalService
                .getMyInitiatedApprovals(initiatorId)
                .map(assembler::toResponse)
                .collectList()
                .flatMap(list -> requestHandlerUtil.ok(Result.success(list)))
                .doOnSuccess(res -> log.debug("查询我发起的审批完成"))
                .onErrorResume(requestHandlerUtil::handleError);
    }
}
