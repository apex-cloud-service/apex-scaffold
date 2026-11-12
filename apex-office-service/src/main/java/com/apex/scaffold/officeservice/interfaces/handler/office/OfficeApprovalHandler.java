package com.apex.scaffold.officeservice.interfaces.handler.office;

import com.apex.scaffold.common.annotation.OperationLog;
import com.apex.scaffold.common.annotation.RequirePermission;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.office.application.OfficeApprovalService;
import com.apex.scaffold.officeservice.interfaces.assembler.office.ApprovalProcessAssembler;
import com.apex.scaffold.officeservice.interfaces.dto.office.approval.ApprovalProcessResponse;
import com.apex.scaffold.officeservice.interfaces.dto.office.approval.ApproveRequest;
import com.apex.scaffold.officeservice.interfaces.dto.office.approval.InitiateApprovalRequest;
import com.apex.scaffold.officeservice.interfaces.security.AuthenticatedUser;
import com.apex.scaffold.officeservice.interfaces.util.RequestHandlerUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * 办公平台审批处理器。异步业务错误由 RequestHandlerUtil 转换为 HTTP 400 响应。
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
     * 校验请求并发起审批流程。
     * @param request 包含审批平台、流程定义、发起人与表单数据的请求
     * @return Result 包装的审批详情，失败时 HTTP 400
     */
    @RequirePermission("office:approval:initiate")
    @OperationLog(
            module = "办公平台审批管理",
            type = OperationLog.OperationType.INSERT,
            description = "发起审批流程",
            saveRequest = false,
            saveResponse = false)
    @Operation(
            operationId = "initiateApproval",
            summary = "发起审批流程",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "发起成功",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ApprovalResult.class))),
                @ApiResponse(
                        responseCode = "400",
                        description = "请求校验或业务处理失败",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ApprovalErrorResult.class)))
            })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = InitiateApprovalRequest.class)))
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
     * 按平台流程实例 ID 查询审批详情。
     * @param request 包含 processInstanceId 路径参数的请求
     * @return Result 包装的审批详情，业务失败时 HTTP 400
     */
    @RequirePermission("office:approval:query")
    @Operation(
            operationId = "getApprovalDetail",
            summary = "查询审批流程详情",
            parameters = {
                @Parameter(
                        name = "processInstanceId",
                        in = ParameterIn.PATH,
                        required = true,
                        description = "流程实例ID",
                        schema = @Schema(type = "string"))
            },
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "查询成功",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ApprovalResult.class))),
                @ApiResponse(
                        responseCode = "400",
                        description = "流程不存在或业务处理失败",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ApprovalErrorResult.class)))
            })
    @OperationLog(
            module = "OfficeApproval",
            type = OperationLog.OperationType.SELECT,
            description = "getApprovalDetail",
            saveRequest = false,
            saveResponse = false)
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
     * 使用请求体中的审批人与操作类型执行审批。
     * @param request 包含流程实例 ID 和审批操作请求体的请求
     * @return Result 包装的更新后审批详情，失败时 HTTP 400
     */
    @RequirePermission("office:approval:approve")
    @OperationLog(
            module = "办公平台审批管理",
            type = OperationLog.OperationType.UPDATE,
            description = "执行审批操作",
            saveRequest = false,
            saveResponse = false)
    @Operation(
            operationId = "approveProcess",
            summary = "执行审批操作",
            parameters = {
                @Parameter(
                        name = "processInstanceId",
                        in = ParameterIn.PATH,
                        required = true,
                        description = "流程实例ID",
                        schema = @Schema(type = "string"))
            },
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "审批操作成功",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ApprovalResult.class))),
                @ApiResponse(
                        responseCode = "400",
                        description = "请求校验或业务处理失败",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ApprovalErrorResult.class)))
            })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApproveRequest.class)))
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
     * 按流程实例 ID 请求撤销审批。
     * @param request 包含 processInstanceId 路径参数与可选 Idempotency-Key 请求头的请求
     * @return Result 包装的布尔操作结果，业务失败时 HTTP 400
     */
    @RequirePermission("office:approval:cancel")
    @OperationLog(
            module = "办公平台审批管理",
            type = OperationLog.OperationType.DELETE,
            description = "撤销审批流程",
            saveRequest = false,
            saveResponse = false)
    @Operation(
            operationId = "cancelApproval",
            summary = "撤销审批流程",
            parameters = {
                @Parameter(
                        name = "processInstanceId",
                        in = ParameterIn.PATH,
                        required = true,
                        description = "流程实例ID",
                        schema = @Schema(type = "string")),
                @Parameter(
                        name = "Idempotency-Key",
                        in = ParameterIn.HEADER,
                        required = false,
                        description = "幂等键，同一流程的重复撤销请求会重放首次结果",
                        schema = @Schema(type = "string"))
            },
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "撤销结果",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ApprovalBooleanResult.class))),
                @ApiResponse(
                        responseCode = "400",
                        description = "流程不存在、状态不允许或业务处理失败",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ApprovalErrorResult.class)))
            })
    public Mono<ServerResponse> cancelApproval(ServerRequest request) {
        String processInstanceId = request.pathVariable("processInstanceId");
        String idempotencyKey = request.headers().firstHeader("Idempotency-Key");
        log.info("撤销审批，流程实例ID：{}，幂等键：{}", processInstanceId, idempotencyKey);

        return approvalService
                .cancelApproval(processInstanceId, idempotencyKey)
                .flatMap(success -> requestHandlerUtil.ok(Result.success(success)))
                .doOnSuccess(res -> log.info("撤销审批完成"))
                .onErrorResume(requestHandlerUtil::handleError);
    }

    /**
     * 查询指定审批人的待审批流程。
     * @param request 必须包含 approverId 查询参数
     * @return Result 包装的审批列表
     */
    @RequirePermission("office:approval:pending")
    @Operation(
            operationId = "getPendingApprovals",
            summary = "查询待审批列表",
            description = "使用查询参数指定审批人",
            parameters =
                    @Parameter(
                            name = "approverId",
                            in = ParameterIn.QUERY,
                            required = true,
                            description = "审批人ID",
                            schema = @Schema(type = "string")),
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "查询成功",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ApprovalListResult.class))),
                @ApiResponse(
                        responseCode = "400",
                        description = "业务处理失败",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ApprovalErrorResult.class)))
            })
    @OperationLog(
            module = "OfficeApproval",
            type = OperationLog.OperationType.SELECT,
            description = "getPendingApprovals",
            saveRequest = false,
            saveResponse = false)
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
     * 查询当前登录用户已执行审批操作的流程。
     * @param request 当前认证请求
     * @return Result 包装的已办审批列表
     */
    @RequirePermission("office:approval:list")
    @Operation(
            operationId = "getCompletedApprovals",
            summary = "查询已办审批",
            description = "按当前认证用户的同意或拒绝记录查询，不接受客户端指定审批人",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "查询成功",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ApprovalListResult.class))),
                @ApiResponse(
                        responseCode = "400",
                        description = "认证主体缺失或业务处理失败",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ApprovalErrorResult.class)))
            })
    @OperationLog(
            module = "OfficeApproval",
            type = OperationLog.OperationType.SELECT,
            description = "getCompletedApprovals",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> getCompletedApprovals(ServerRequest request) {
        return request.principal()
                .filter(AuthenticatedUser.class::isInstance)
                .cast(AuthenticatedUser.class)
                .map(AuthenticatedUser::userId)
                .filter(userId -> userId != null)
                .map(String::valueOf)
                .switchIfEmpty(Mono.error(new IllegalStateException("当前请求未包含有效认证用户")))
                .flatMapMany(approvalService::getCompletedApprovals)
                .map(assembler::toResponse)
                .collectList()
                .flatMap(list -> requestHandlerUtil.ok(Result.success(list)))
                .doOnSuccess(res -> log.debug("查询已办审批完成"))
                .onErrorResume(requestHandlerUtil::handleError);
    }

    /**
     * 查询指定发起人的审批流程。
     * @param request 必须包含 initiatorId 查询参数
     * @return Result 包装的审批列表
     */
    @RequirePermission("office:approval:initiated")
    @Operation(
            operationId = "getMyInitiatedApprovals",
            summary = "查询发起的审批",
            description = "使用查询参数指定发起人",
            parameters =
                    @Parameter(
                            name = "initiatorId",
                            in = ParameterIn.QUERY,
                            required = true,
                            description = "发起人ID",
                            schema = @Schema(type = "string")),
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "查询成功",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ApprovalListResult.class))),
                @ApiResponse(
                        responseCode = "400",
                        description = "业务处理失败",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ApprovalErrorResult.class)))
            })
    @OperationLog(
            module = "OfficeApproval",
            type = OperationLog.OperationType.SELECT,
            description = "getMyInitiatedApprovals",
            saveRequest = false,
            saveResponse = false)
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

    /** ApprovalResult ApprovalProcess 统一响应包装。 */
    @Schema(name = "OfficeApprovalResult")
    public static class ApprovalResult extends Result<ApprovalProcessResponse> {}

    /** ApprovalListResult ApprovalProcess 列表统一响应包装。 */
    @Schema(name = "OfficeApprovalListResult")
    public static class ApprovalListResult extends Result<List<ApprovalProcessResponse>> {}

    /** ApprovalBooleanResult 布尔结果统一响应包装。 */
    @Schema(name = "OfficeApprovalBooleanResult")
    public static class ApprovalBooleanResult extends Result<Boolean> {}

    /** ApprovalErrorResult 无数据统一响应包装。 */
    @Schema(name = "OfficeApprovalErrorResult")
    public static class ApprovalErrorResult extends Result<Void> {}
}
