package com.apex.scaffold.application.office.approval;

import com.apex.scaffold.application.office.approval.command.InitiateApprovalCommand;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.domain.office.approval.ApprovalProcess;
import com.apex.scaffold.domain.office.approval.ApprovalProcessDomainService;
import com.apex.scaffold.domain.office.approval.ApprovalProcessRepository;
import com.apex.scaffold.domain.office.approval.ApprovalStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * 审批流程应用服务
 *
 * <p>负责审批流程的用例编排和事务管理，提供以下功能：
 * <ul>
 *   <li>发起审批流程</li>
 *   <li>同步审批状态</li>
 *   <li>撤销审批</li>
 *   <li>催办审批</li>
 *   <li>查询审批流程</li>
 * </ul>
 *
 * @author Apex Team
 * @since 1.3.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalProcessApplicationService {
    
    private final ApprovalProcessRepository approvalProcessRepository;
    private final ApprovalProcessDomainService approvalProcessDomainService;
    
    /**
     * 发起审批
     *
     * <p>创建审批流程并提交到办公平台。
     *
     * @param command 发起审批命令
     * @return 审批流程
     */
    public Mono<ApprovalProcess> initiateApproval(InitiateApprovalCommand command) {
        log.info("发起审批，租户：{}，平台：，审批码：{}", 
                command.getTenantId(), command.getPlatform(), command.getPlatformApprovalCode());
        
        return Mono.defer(() -> {
            ApprovalProcess process = ApprovalProcess.builder()
                    .tenantId(command.getTenantId())
                    .platform(command.getPlatform())
                    .appId(command.getAppId())
                    .platformApprovalCode(command.getPlatformApprovalCode())
                    .title(command.getTitle())
                    .initiatorUserId(command.getInitiatorUserId())
                    .formData(command.getFormData())
                    .status(ApprovalStatus.PENDING)
                    .initiatedAt(LocalDateTime.now())
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            
            return approvalProcessDomainService.initiateApproval(process)
                    .doOnSuccess(p -> log.info("审批发起成功，流程ID：{}，平台实例ID：{}", 
                            p.getId(), p.getPlatformInstanceId()))
                    .doOnError(e -> log.error("审批发起失败", e));
        });
    }
    
    /**
     * 同步审批状态
     *
     * <p>从办公平台同步最新的审批状态和审批记录。
     *
     * @param processId 审批流程ID
     * @return 审批流程
     */
    public Mono<ApprovalProcess> syncApprovalStatus(Long processId) {
        log.info("同步审批状态，流程ID：{}", processId);
        
        return approvalProcessDomainService.syncApprovalStatus(processId)
                .doOnSuccess(p -> log.info("审批状态同步成功，流程ID：{}，状态：{}", 
                        p.getId(), p.getStatus()))
                .doOnError(e -> log.error("审批状态同步失败，流程ID：{}", processId, e));
    }
    
    /**
     * 撤销审批
     *
     * <p>撤销已发起的审批流程。只有未完成的审批才能撤销。
     *
     * @param processId 审批流程ID
     * @param reason 撤销原因
     * @return 审批流程
     */
    public Mono<ApprovalProcess> cancelApproval(Long processId, String reason) {
        log.info("撤销审批，流程ID：{}，原因：{}", processId, reason);
        
        return approvalProcessRepository.findById(processId)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "审批流程不存在")))
                .flatMap(process -> {
                    if (!process.canCancel()) {
                        return Mono.error(new BusinessException(
                                ResultCode.BUSINESS_ERROR, "当前状态不允许撤销"));
                    }
                    return approvalProcessDomainService.cancelApproval(processId, reason);
                })
                .doOnSuccess(p -> log.info("审批撤销成功，流程ID：{}", p.getId()))
                .doOnError(e -> log.error("审批撤销失败，流程ID：{}", processId, e));
    }
    
    /**
     * 催办审批
     *
     * <p>向当前审批人发送催办通知。
     *
     * @param processId 审批流程ID
     * @return 催办结果
     */
    public Mono<Boolean> urgeApproval(Long processId) {
        log.info("催办审批，流程ID：{}", processId);
        
        return approvalProcessRepository.findById(processId)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "审批流程不存在")))
                .flatMap(process -> {
                    if (process.isCompleted()) {
                        return Mono.error(new BusinessException(
                                ResultCode.BUSINESS_ERROR, "审批已完成，无需催办"));
                    }
                    return approvalProcessDomainService.urgeApproval(processId);
                })
                .doOnSuccess(result -> log.info("审批催办成功，流程ID：{}", processId))
                .doOnError(e -> log.error("审批催办失败，流程ID：{}", processId, e));
    }
    
    /**
     * 根据ID查询审批流程
     *
     * @param processId 审批流程ID
     * @return 审批流程
     */
    public Mono<ApprovalProcess> getProcessById(Long processId) {
        log.debug("查询审批流程，流程ID：{}", processId);
        
        return approvalProcessRepository.findById(processId)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "审批流程不存在")));
    }
    
    /**
     * 查询审批流程列表
     *
     * @param tenantId 租户ID
     * @param platform 平台类型（可选）
     * @param status 审批状态（可选）
     * @param page 页码
     * @param size 每页数量
     * @return 审批流程列表
     */
    public Flux<ApprovalProcess> queryProcesses(Long tenantId, String platform, 
                                                 ApprovalStatus status, int page, int size) {
        log.debug("查询审批流程列表，租户：{}，平台：{}，状态：{}", tenantId, platform, status);
        
        return approvalProcessRepository.findByTenantId(tenantId, platform, status, page, size);
    }
    
    /**
     * 统计审批流程数量
     *
     * @param tenantId 租户ID
     * @param platform 平台类型（可选）
     * @param status 审批状态（可选）
     * @return 审批流程数量
     */
    public Mono<Long> countProcesses(Long tenantId, String platform, ApprovalStatus status) {
        return approvalProcessRepository.countByTenantId(tenantId, platform, status);
    }
    
    /**
     * 查询用户发起的审批流程
     *
     * @param tenantId 租户ID
     * @param initiatorUserId 发起人用户ID
     * @param page 页码
     * @param size 每页数量
     * @return 审批流程列表
     */
    public Flux<ApprovalProcess> getProcessesByInitiator(Long tenantId, String initiatorUserId, 
                                                          int page, int size) {
        log.debug("查询用户发起的审批流程，租户：{}，发起人：{}", tenantId, initiatorUserId);
        
        return approvalProcessRepository.findByInitiator(tenantId, initiatorUserId, page, size);
    }
}
