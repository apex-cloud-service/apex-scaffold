package com.apex.scaffold.infrastructure.office.service;

import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.domain.office.approval.ApprovalProcess;
import com.apex.scaffold.domain.office.approval.ApprovalProcessDomainService;
import com.apex.scaffold.domain.office.approval.ApprovalProcessRepository;
import com.apex.scaffold.domain.office.approval.ApprovalStatus;
import com.apex.scaffold.domain.office.app.OfficePlatformApp;
import com.apex.scaffold.domain.office.app.OfficePlatformAppRepository;
import com.apex.scaffold.infrastructure.office.client.approval.DingTalkApprovalClient;
import com.apex.scaffold.infrastructure.office.client.approval.FeishuApprovalClient;
import com.apex.scaffold.infrastructure.office.client.approval.WeComApprovalClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * 审批流程领域服务实现
 *
 * <p>实现审批流程的核心业务逻辑，协调仓储和外部平台客户端。
 *
 * @author Apex Team
 * @since 1.3.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalProcessDomainServiceImpl implements ApprovalProcessDomainService {
    
    private final ApprovalProcessRepository approvalProcessRepository;
    private final OfficePlatformAppRepository appRepository;
    private final FeishuApprovalClient feishuApprovalClient;
    private final DingTalkApprovalClient dingTalkApprovalClient;
    private final WeComApprovalClient weComApprovalClient;
    
    @Override
    public Mono<ApprovalProcess> initiateApproval(ApprovalProcess process) {
        log.info("发起审批流程，租户：{}，平台：{}", process.getTenantId(), process.getPlatform());
        
        return appRepository.findById(process.getAppId())
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "应用不存在")))
                .flatMap(app -> {
                    if (!app.isActive()) {
                        return Mono.error(new BusinessException(ResultCode.BUSINESS_ERROR, "应用未启用"));
                    }
                    
                    return createPlatformInstance(process, app)
                            .flatMap(approvalProcessRepository::save);
                })
                .doOnSuccess(p -> log.info("审批流程发起成功，流程ID：{}", p.getId()))
                .doOnError(e -> log.error("审批流程发起失败", e));
    }
    
    @Override
    public Mono<ApprovalProcess> syncApprovalStatus(Long processId) {
        log.info("同步审批状态，流程ID：{}", processId);
        
        return approvalProcessRepository.findById(processId)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "审批流程不存在")))
                .flatMap(process -> appRepository.findById(process.getAppId())
                        .flatMap(app -> getPlatformInstance(process, app))
                        .flatMap(updated -> {
                            // 更新流程状态
                            process.setStatus(updated.getStatus());
                            process.setCurrentNode(updated.getCurrentNode());
                            process.setRecords(updated.getRecords());
                            process.setResultMessage(updated.getResultMessage());
                            process.setCompletedAt(updated.getCompletedAt());
                            process.setUpdatedAt(LocalDateTime.now());
                            
                            return approvalProcessRepository.save(process);
                        }))
                .doOnSuccess(p -> log.info("审批状态同步成功，流程ID：{}，状态：{}", p.getId(), p.getStatus()))
                .doOnError(e -> log.error("审批状态同步失败，流程ID：", processId, e));
    }
    
    @Override
    public Mono<ApprovalProcess> cancelApproval(Long processId, String reason) {
        log.info("撤销审批流程，流程ID：{}", processId);
        
        return approvalProcessRepository.findById(processId)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "审批流程不存在")))
                .flatMap(process -> {
                    if (!process.canCancel()) {
                        return Mono.error(new BusinessException(
                                ResultCode.BUSINESS_ERROR, "当前状态不允许撤销"));
                    }
                    
                    return appRepository.findById(process.getAppId())
                            .flatMap(app -> cancelPlatformInstance(process, app, reason))
                            .flatMap(success -> {
                                if (success) {
                                    process.setStatus(ApprovalStatus.CANCELLED);
                                    process.setResultMessage(reason);
                                    process.setCompletedAt(LocalDateTime.now());
                                    process.setUpdatedAt(LocalDateTime.now());
                                    return approvalProcessRepository.save(process);
                                } else {
                                    return Mono.error(new BusinessException(
                                            ResultCode.BUSINESS_ERROR, "平台审批撤销失败"));
                                }
                            });
                })
                .doOnSuccess(p -> log.info("审批流程撤销成功，流程ID：{}", p.getId()))
                .doOnError(e -> log.error("审批流程撤销失败，流程ID：{}", processId, e));
    }
    
    @Override
    public Mono<Boolean> urgeApproval(Long processId) {
        log.info("催办审批流程，流程ID：{}", processId);
        
        return approvalProcessRepository.findById(processId)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "审批流程不存在")))
                .flatMap(process -> {
                    if (process.isCompleted()) {
                        return Mono.error(new BusinessException(
                                ResultCode.BUSINESS_ERROR, "审批已完成，无需催办"));
                    }
                    
                    // TODO: 实现催办逻辑，可能需要调用平台API或发送通知
                    log.info("执行催办操作，流程ID：{}", processId);
                    return Mono.just(true);
                })
                .doOnSuccess(result -> log.info("审批催办成功，流程ID：{}", processId))
                .doOnError(e -> log.error("审批催办失败，流程ID：{}", processId, e));
    }
    
    /**
     * 在平台上创建审批实例
     *
     * @param process 审批流程
     * @param app 平台应用
     * @return 更新后的审批流程
     */
    private Mono<ApprovalProcess> createPlatformInstance(ApprovalProcess process, OfficePlatformApp app) {
        String accessToken = app.getAccessToken();
        
        switch (process.getPlatform().toUpperCase()) {
            case "FEISHU":
                return feishuApprovalClient.createInstance(process, accessToken);
            case "DINGTALK":
                return dingTalkApprovalClient.createInstance(process, accessToken);
            case "WECOM":
                return weComApprovalClient.applyEvent(process, accessToken);
            default:
                return Mono.error(new BusinessException(
                        ResultCode.BUSINESS_ERROR, "不支持的平台类型"));
        }
    }
    
    /**
     * 从平台获取审批实例状态
     *
     * @param process 审批流程
     * @param app 平台应用
     * @return 更新后的审批流程
     */
    private Mono<ApprovalProcess> getPlatformInstance(ApprovalProcess process, OfficePlatformApp app) {
        String accessToken = app.getAccessToken();
        String instanceId = process.getPlatformInstanceId();
        
        switch (process.getPlatform().toUpperCase()) {
            case "FEISHU":
                return feishuApprovalClient.getInstance(instanceId, accessToken);
            case "DINGTALK":
                return dingTalkApprovalClient.getInstance(instanceId, accessToken);
            case "WECOM":
                return weComApprovalClient.getApprovalInfo(instanceId, accessToken);
            default:
                return Mono.error(new BusinessException(
                        ResultCode.BUSINESS_ERROR, "不支持的平台类型"));
        }
    }
    
    /**
     * 在平台上撤销审批实例
     *
     * @param process 审批流程
     * @param app 平台应用
     * @param reason 撤销原因
     * @return 撤销结果
     */
    private Mono<Boolean> cancelPlatformInstance(ApprovalProcess process, 
                                                  OfficePlatformApp app, String reason) {
        String accessToken = app.getAccessToken();
        String instanceId = process.getPlatformInstanceId();
        String userId = process.getInitiatorUserId();
        
        switch (process.getPlatform().toUpperCase()) {
            case "FEISHU":
                return feishuApprovalClient.cancelInstance(instanceId, userId, accessToken);
            case "DINGTALK":
                return dingTalkApprovalClient.terminateInstance(instanceId, userId, accessToken);
            case "WECOM":
                // 企业微信API可能不支持撤销，需要查阅文档
                log.warn("企业微信可能不支持撤销审批");
                return Mono.just(false);
            default:
                return Mono.error(new BusinessException(
                        ResultCode.BUSINESS_ERROR, "不支持的平台类型"));
        }
    }
}
