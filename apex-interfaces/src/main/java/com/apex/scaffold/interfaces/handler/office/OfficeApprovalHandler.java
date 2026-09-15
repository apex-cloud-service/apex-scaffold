package com.apex.scaffold.interfaces.handler.office;

import com.apex.scaffold.application.office.OfficeApprovalService;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.interfaces.assembler.office.ApprovalProcessAssembler;
import com.apex.scaffold.interfaces.dto.office.approval.ApprovalProcessResponse;
import com.apex.scaffold.interfaces.dto.office.approval.ApproveRequest;
import com.apex.scaffold.interfaces.dto.office.approval.InitiateApprovalRequest;
import com.apex.scaffold.interfaces.util.RequestHandlerUtil;
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
public class OfficeApprovalHandler {
    
    private final OfficeApprovalService approvalService;
    private final ApprovalProcessAssembler assembler;
    
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
    public Mono<ServerResponse> initiateApproval(ServerRequest request) {
        log.info("收到发起审批请求");
        
        return RequestHandlerUtil.validateAndMap(request, InitiateApprovalRequest.class)
                .flatMap(req -> approvalService.initiateApproval(
                        req.getPlatform(),
                        req.getAppId(),
                        req.getProcessDefinitionId(),
                        req.getInitiatorId(),
                        req.getFormData()
                ))
                .map(assembler::toResponse)
                .flatMap(response -> RequestHandlerUtil.ok(Result.success(response)))
                .doOnSuccess(res -> log.info("发起审批成功"))
                .onErrorResume(RequestHandlerUtil::handleError);
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
    public Mono<ServerResponse> getApprovalDetail(ServerRequest request) {
        String processInstanceId = request.pathVariable("processInstanceId");
        log.info("查询审批详情，流程实例ID：{}", processInstanceId);
        
        return approvalService.getApprovalDetail(processInstanceId)
                .map(assembler::toResponse)
                .flatMap(response -> RequestHandlerUtil.ok(Result.success(response)))
                .doOnSuccess(res -> log.debug("查询审批详情成功"))
                .onErrorResume(RequestHandlerUtil::handleError);
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
    public Mono<ServerResponse> approveProcess(ServerRequest request) {
        String processInstanceId = request.pathVariable("processInstanceId");
        log.info("收到审批操作请求，流程实例ID：{}", processInstanceId);
        
        return RequestHandlerUtil.validateAndMap(request, ApproveRequest.class)
                .flatMap(req -> approvalService.approveProcess(
                        processInstanceId,
                        req.getApproverId(),
                        req.getAction(),
                        req.getComment()
                ))
                .map(assembler::toResponse)
                .flatMap(response -> RequestHandlerUtil.ok(Result.success(response)))
                .doOnSuccess(res -> log.info("审批操作完成"))
                .onErrorResume(RequestHandlerUtil::handleError);
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
    public Mono<ServerResponse> cancelApproval(ServerRequest request) {
        String processInstanceId = request.pathVariable("processInstanceId");
        log.info("撤销审批，流程实例ID：{}", processInstanceId);
        
        return approvalService.cancelApproval(processInstanceId)
                .flatMap(success -> RequestHandlerUtil.ok(Result.success(success)))
                .doOnSuccess(res -> log.info("撤销审批完成"))
                .onErrorResume(RequestHandlerUtil::handleError);
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
    public Mono<ServerResponse> getPendingApprovals(ServerRequest request) {
        String approverId = request.queryParam("approverId")
                .orElseThrow(() -> new IllegalArgumentException("审批人ID不能为空"));
        log.info("查询待审批列表，审批人：{}", approverId);
        
        return approvalService.getPendingApprovals(approverId)
                .map(assembler::toResponse)
                .collectList()
                .flatMap(list -> RequestHandlerUtil.ok(Result.success(list)))
                .doOnSuccess(res -> log.debug("查询待审批列表完成"))
                .onErrorResume(RequestHandlerUtil::handleError);
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
    public Mono<ServerResponse> getMyInitiatedApprovals(ServerRequest request) {
        String initiatorId = request.queryParam("initiatorId")
                .orElseThrow(() -> new IllegalArgumentException("发起人ID不能为空"));
        log.info("查询我发起的审批，发起人：{}", initiatorId);
        
        return approvalService.getMyInitiatedApprovals(initiatorId)
                .map(assembler::toResponse)
                .collectList()
                .flatMap(list -> RequestHandlerUtil.ok(Result.success(list)))
                .doOnSuccess(res -> log.debug("查询我发起的审批完成"))
                .onErrorResume(RequestHandlerUtil::handleError);
    }
}
