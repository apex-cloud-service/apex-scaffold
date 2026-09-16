package com.apex.scaffold.infrastructure.office.service;

import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.domain.office.approval.ApprovalProcess;
import com.apex.scaffold.domain.office.approval.ApprovalProcessDomainService;
import com.apex.scaffold.domain.office.approval.ApprovalProcessRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 审批流程领域服务实现
 *
 * <p>实现审批流程的核心业务逻辑，协调仓储和外部平台客户端。
 * 当前为最小化实现，待完善平台客户端集成。
 *
 * @author Apex Team
 * @since 1.3.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalProcessDomainServiceImpl implements ApprovalProcessDomainService {

    private final ApprovalProcessRepository approvalProcessRepository;

    @Override
    public Mono<ApprovalProcess> initiateApproval(ApprovalProcess process) {
        log.info("发起审批流程，租户：{}，平台：{}", process.getTenantId(), process.getPlatform());

        // 当前最小化实现：直接保存审批流程
        // TODO: 集成平台审批客户端，调用外部平台API创建审批实例
        return approvalProcessRepository
                .save(process)
                .doOnSuccess(p -> log.info("审批流程发起成功，流程ID：{}", p.getId()))
                .doOnError(e -> log.error("审批流程发起失败", e));
    }

    @Override
    public Mono<ApprovalProcess> syncApprovalStatus(Long processId) {
        log.info("同步审批状态，流程ID：{}", processId);

        return approvalProcessRepository
                .findById(processId)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "审批流程不存在")))
                .flatMap(process -> {
                    // TODO: 调用平台API同步最新状态
                    log.warn("审批状态同步功能待实现，流程ID：{}", processId);
                    return Mono.just(process);
                })
                .doOnSuccess(p -> log.info("审批状态同步完成，流程ID：{}", p.getId()))
                .doOnError(e -> log.error("审批状态同步失败，流程ID：{}", processId, e));
    }

    @Override
    public Mono<ApprovalProcess> cancelApproval(Long processId, String reason) {
        log.info("撤销审批流程，流程ID：{}", processId);

        return approvalProcessRepository
                .findById(processId)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "审批流程不存在")))
                .flatMap(process -> {
                    if (!process.isCancellable()) {
                        return Mono.error(new BusinessException(ResultCode.BUSINESS_ERROR, "当前状态不允许撤销"));
                    }

                    // TODO: 调用平台API撤销审批
                    log.warn("审批撤销功能待实现，流程ID：{}", processId);
                    return approvalProcessRepository.save(process);
                })
                .doOnSuccess(p -> log.info("审批流程撤销成功，流程ID：{}", p.getId()))
                .doOnError(e -> log.error("审批流程撤销失败，流程ID：{}", processId, e));
    }

    @Override
    public Mono<Boolean> urgeApproval(Long processId) {
        log.info("催办审批流程，流程ID：{}", processId);

        return approvalProcessRepository
                .findById(processId)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "审批流程不存在")))
                .flatMap(process -> {
                    // TODO: 调用平台API催办审批
                    log.warn("审批催办功能待实现，流程ID：{}", processId);
                    return Mono.just(true);
                })
                .doOnSuccess(v -> log.info("审批催办完成，流程ID：{}", processId))
                .doOnError(e -> log.error("审批催办失败，流程ID：{}", processId, e));
    }
}
