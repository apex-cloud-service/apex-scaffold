package com.apex.scaffold.application.office;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.domain.office.OfficePlatform;
import com.apex.scaffold.domain.office.approval.*;
import com.apex.scaffold.domain.office.port.ApprovalPort;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 办公平台审批应用服务
 *
 * <p>负责审批流程的用例编排和业务逻辑协调，是Application层的核心服务。
 * 主要职责包括：
 * <ul>
 *   <li>协调领域对象和基础设施组件完成审批流程操作</li>
 *   <li>管理事务边界，确保数据一致性</li>
 *   <li>处理跨平台审批的统一逻辑</li>
 *   <li>维护租户隔离和安全控制</li>
 * </ul>
 *
 * <p>本服务依赖 {@link ApprovalPort} 端口接口而非具体实现，
 * 符合依赖倒置原则和端口-适配器架构模式。
 *
 * @author Apex Scaffold
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OfficeApprovalService {

    private final ApprovalProcessRepository approvalProcessRepository;
    private final Map<String, ApprovalPort> approvalClients;

    /**
     * 发起审批流程
     *
     * <p>在指定的办公平台创建审批流程，并在本地数据库保存审批流程记录。
     *
     * @param platform 办公平台类型
     * @param appId 应用ID
     * @param processDefinitionId 流程定义ID
     * @param initiatorId 发起人ID
     * @param formData 审批表单数据（JSON字符串）
     * @return 创建成功的审批流程
     */
    public Mono<ApprovalProcess> initiateApproval(
            OfficePlatform platform, Long appId, String processDefinitionId, String initiatorId, String formData) {
        // 使用processDefinitionId作为templateId
        String templateId = processDefinitionId;
        String processInstanceId = java.util.UUID.randomUUID().toString();
        String title = "审批流程-" + processInstanceId;
        String initiatorName = initiatorId; // 简化处理，实际应查询用户名
        String[] approverIds = new String[0]; // 从表单数据中提取或使用默认值

        // 将JSON字符串转换为Map（简化处理，实际应使用JSON解析器）
        Map<String, Object> formDataMap = new java.util.HashMap<>();
        formDataMap.put("data", formData);

        log.info(
                "发起审批流程 - platform: {}, appId: {}, templateId: {}, processInstanceId: {}, initiatorId: {}",
                platform,
                appId,
                templateId,
                processInstanceId,
                initiatorId);

        ApprovalPort client = getApprovalClient(platform.name().toLowerCase());

        return client.createApproval(templateId, initiatorId, approverIds, formDataMap, title)
                .flatMap(platformInstanceId -> {
                    Long tenantId = TenantContext.currentTenantId()
                            .map(Long::parseLong)
                            .orElseThrow(() -> new IllegalStateException("租户ID未设置"));

                    ApprovalProcess process = ApprovalProcess.builder()
                            .tenantId(tenantId)
                            .platform(platform.name())
                            .platformInstanceId(platformInstanceId)
                            .processInstanceId(processInstanceId)
                            .templateId(templateId)
                            .title(title)
                            .initiatorId(initiatorId)
                            .initiatorName(initiatorName)
                            .status(ApprovalStatus.PENDING)
                            .formData(convertToJson(formData))
                            .currentApproverIds(convertToJson(approverIds))
                            .initiateTime(LocalDateTime.now())
                            .build();

                    return approvalProcessRepository.save(process);
                })
                .doOnSuccess(process -> log.info(
                        "审批流程发起成功 - processInstanceId: {}, platformInstanceId: {}",
                        process.getProcessInstanceId(),
                        process.getPlatformInstanceId()))
                .doOnError(error -> log.error("审批流程发起失败 - processInstanceId: {}", processInstanceId, error));
    }

    /**
     * 查询审批详情
     *
     * <p>根据业务流程实例ID查询审批流程的详细信息，包括审批记录、当前状态等。
     *
     * @param processInstanceId 业务流程实例ID
     * @return 审批流程详情
     */
    public Mono<ApprovalProcess> getApprovalDetail(String processInstanceId) {
        log.info("查询审批详情 - processInstanceId: {}", processInstanceId);

        return approvalProcessRepository
                .findByProcessInstanceId(processInstanceId)
                .switchIfEmpty(Mono.error(new RuntimeException("审批流程不存在: " + processInstanceId)))
                .flatMap(process -> {
                    ApprovalPort client = getApprovalClient(process.getPlatform());
                    return client.getApprovalDetail(process.getPlatformInstanceId())
                            .map(detail -> {
                                // 更新审批记录
                                List<ApprovalRecord> records = parseApprovalRecords(detail);
                                process.setRecords(records);
                                return process;
                            });
                })
                .doOnSuccess(process -> log.info("审批详情查询成功 - processInstanceId: {}", processInstanceId))
                .doOnError(error -> log.error("审批详情查询失败 - processInstanceId: {}", processInstanceId, error));
    }

    /**
     * 执行审批操作
     *
     * <p>审批人对审批流程执行同意或拒绝操作。
     *
     * @param processInstanceId 业务流程实例ID
     * @param approverId 审批人ID
     * @param action 审批动作（APPROVE/REJECT）
     * @param comment 审批意见
     * @return 更新后的审批流程
     */
    public Mono<ApprovalProcess> approveProcess(
            String processInstanceId, String approverId, ApprovalAction action, String comment) {
        log.info("执行审批操作 - processInstanceId: {}, approverId: {}, action: {}", processInstanceId, approverId, action);

        return approvalProcessRepository
                .findByProcessInstanceId(processInstanceId)
                .switchIfEmpty(Mono.error(new RuntimeException("审批流程不存在: " + processInstanceId)))
                .flatMap(process -> {
                    if (process.isCompleted()) {
                        return Mono.error(new RuntimeException("审批流程已完成，无法继续操作"));
                    }

                    ApprovalPort client = getApprovalClient(process.getPlatform());
                    return client.approveProcess(process.getPlatformInstanceId(), approverId, action.name(), comment)
                            .flatMap(success -> {
                                if (success) {
                                    // 添加审批记录
                                    ApprovalRecord record = ApprovalRecord.builder()
                                            .approverId(approverId)
                                            .action(action)
                                            .comment(comment)
                                            .approvalTime(LocalDateTime.now())
                                            .build();
                                    process.addRecord(record);

                                    // 更新审批状态
                                    if (action == ApprovalAction.APPROVE) {
                                        process.updateStatus(ApprovalStatus.APPROVED);
                                    } else if (action == ApprovalAction.REJECT) {
                                        process.updateStatus(ApprovalStatus.REJECTED);
                                    }

                                    return approvalProcessRepository
                                            .update(process)
                                            .thenReturn(process);
                                }
                                return Mono.error(new RuntimeException("审批操作失败"));
                            });
                })
                .doOnSuccess(
                        success -> log.info("审批操作执行成功 - processInstanceId: {}, result: {}", processInstanceId, success))
                .doOnError(error -> log.error("审批操作执行失败 - processInstanceId: {}", processInstanceId, error));
    }

    /**
     * 撤销审批流程
     *
     * <p>发起人撤回已发起的审批流程。
     *
     * @param processInstanceId 业务流程实例ID
     * @return 撤销操作是否成功
     */
    public Mono<Boolean> cancelApproval(String processInstanceId) {
        log.info("撤销审批流程 - processInstanceId: {}", processInstanceId);

        return approvalProcessRepository
                .findByProcessInstanceId(processInstanceId)
                .switchIfEmpty(Mono.error(new RuntimeException("审批流程不存在: " + processInstanceId)))
                .flatMap(process -> {
                    String initiatorId = process.getInitiatorId();

                    if (!process.isCancellable()) {
                        return Mono.error(new RuntimeException("当前审批状态不允许撤销"));
                    }

                    ApprovalPort client = getApprovalClient(process.getPlatform());
                    return client.cancelApproval(process.getPlatformInstanceId(), initiatorId)
                            .flatMap(success -> {
                                if (success) {
                                    process.updateStatus(ApprovalStatus.CANCELLED);
                                    return approvalProcessRepository
                                            .update(process)
                                            .thenReturn(true);
                                }
                                return Mono.just(false);
                            });
                })
                .doOnSuccess(
                        success -> log.info("审批流程撤销成功 - processInstanceId: {}, result: {}", processInstanceId, success))
                .doOnError(error -> log.error("审批流程撤销失败 - processInstanceId: {}", processInstanceId, error));
    }

    /**
     * 查询待审批列表
     *
     * <p>查询指定审批人的所有待审批流程。
     *
     * @param approverId 审批人ID
     * @return 待审批流程列表（Flux）
     */
    public Flux<ApprovalProcess> getPendingApprovals(String approverId) {
        log.info("查询待审批列表 - approverId: {}", approverId);

        return approvalProcessRepository
                .findPendingByApprover(approverId)
                .flatMapMany(Flux::fromIterable)
                .doOnComplete(() -> log.info("待审批列表查询成功 - approverId: {}", approverId))
                .doOnError(error -> log.error("待审批列表查询失败 - approverId: {}", approverId, error));
    }

    /**
     * 查询我发起的审批列表
     *
     * <p>查询指定发起人发起的所有审批流程。
     *
     * @param initiatorId 发起人ID
     * @return 审批流程列表（Flux）
     */
    public Flux<ApprovalProcess> getMyInitiatedApprovals(String initiatorId) {
        log.info("查询我发起的审批列表 - initiatorId: {}", initiatorId);

        return approvalProcessRepository
                .findByInitiator(initiatorId)
                .flatMapMany(Flux::fromIterable)
                .doOnComplete(() -> log.info("我发起的审批列表查询成功 - initiatorId: {}", initiatorId))
                .doOnError(error -> log.error("我发起的审批列表查询失败 - initiatorId: {}", initiatorId, error));
    }

    /**
     * 根据平台类型获取审批端口实现
     *
     * @param platform 平台类型
     * @return 审批端口实例
     */
    private ApprovalPort getApprovalClient(String platform) {
        ApprovalPort client = approvalClients.get(platform + "ApprovalClient");
        if (client == null) {
            throw new RuntimeException("不支持的办公平台: " + platform);
        }
        return client;
    }

    /**
     * 将对象转换为JSON字符串
     *
     * @param obj 对象
     * @return JSON字符串
     */
    private String convertToJson(Object obj) {
        // 实际实现中应使用ObjectMapper
        // 这里简化处理
        return obj != null ? obj.toString() : null;
    }

    /**
     * 解析审批记录
     *
     * @param detail 审批详情
     * @return 审批记录列表
     */
    private List<ApprovalRecord> parseApprovalRecords(Map<String, Object> detail) {
        // 实际实现中应根据不同平台的响应结构解析审批记录
        // 这里简化处理
        return List.of();
    }
}
