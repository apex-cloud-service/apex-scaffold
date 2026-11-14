package com.apex.scaffold.office.application;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.office.domain.OfficePlatform;
import com.apex.scaffold.office.domain.approval.*;
import com.apex.scaffold.office.domain.port.ApprovalPort;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 办公平台审批应用服务。
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
 * @author Apex Team
 * @version 1.3.1
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OfficeApprovalService {

    private final ApprovalProcessRepository approvalProcessRepository;
    private final ApprovalCancelOperationRepository approvalCancelOperationRepository;
    private final Map<String, ApprovalPort> approvalClients;
    private final ObjectMapper objectMapper;

    /**
     * 发起审批流程。
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
        // 流程定义 ID 同时作为审批模板标识：office 侧无独立模板实体，模板信息随定义下发。
        String templateId = processDefinitionId;
        String processInstanceId = java.util.UUID.randomUUID().toString();
        String title = "审批流程-" + processInstanceId;
        // 发起人展示名暂以发起人 ID 兜底：本地不回查组织用户，展示名由办公平台侧解析。
        String initiatorName = initiatorId;
        // 审批人由办公平台按流程定义动态指定，本地发起阶段不预置。
        String[] approverIds = new String[0];

        // 表单原文整体透传（data 键承载原文），不在本地做 JSON 结构解析，字段语义由平台侧维护。
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
     * 查询审批详情。
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
                                // 解析平台返回的审批明细并回填审批记录。
                                List<ApprovalRecord> records = parseApprovalRecords(process.getPlatform(), detail);
                                process.setRecords(records);
                                return process;
                            });
                })
                .doOnSuccess(process -> log.info("审批详情查询成功 - processInstanceId: {}", processInstanceId))
                .doOnError(error -> log.error("审批详情查询失败 - processInstanceId: {}", processInstanceId, error));
    }

    /**
     * 执行审批操作。
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
                                    // 添加审批记录。
                                    ApprovalRecord record = ApprovalRecord.builder()
                                            .approverId(approverId)
                                            .action(action)
                                            .comment(comment)
                                            .approvalTime(LocalDateTime.now())
                                            .build();
                                    process.addRecord(record);

                                    // 按动作映射流程状态：仅 APPROVE/REJECT 变更状态。
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
     * 撤销审批流程（兼容旧调用）
     *
     * <p>未显式提供幂等键时按流程实例 ID 派生，保证同一流程的重复撤销不会重复触发远端操作。
     *
     * @param processInstanceId 业务流程实例ID
     * @return 撤销操作是否成功
     */
    public Mono<Boolean> cancelApproval(String processInstanceId) {
        return cancelApproval(processInstanceId, null);
    }

    /**
     * 撤销审批流程（幂等 + 补偿 + 编排状态落库）
     *
     * <p>撤销跨越"远端平台撤销"与"本地流程状态落库"两个步骤，二者不在同一事务内，因此用
     * {@link ApprovalCancelOperation} 记录把两个步骤串成可重放的状态机：
     * <ul>
     *   <li>首次请求写入 PENDING 记录后再调用远端撤销</li>
     *   <li>远端成功后记录推进到 REMOTE_CANCELLED，随后在同一同步事务内完成本地流程状态与记录状态落库</li>
     *   <li>同一幂等键重放时，COMPLETED 直接返回成功，REMOTE_CANCELLED 只重试本地补偿，PENDING 提示稍后重试</li>
     *   <li>FAILED 记录保留失败原因，重放时先重置为 PENDING 再重新执行</li>
     * </ul>
     *
     * @param processInstanceId 业务流程实例ID
     * @param idempotencyKey 客户端幂等键，为空或空白时按流程实例 ID 派生
     * @return 撤销操作是否成功
     */
    public Mono<Boolean> cancelApproval(String processInstanceId, String idempotencyKey) {
        String effectiveKey =
                (idempotencyKey == null || idempotencyKey.isBlank()) ? "auto:" + processInstanceId : idempotencyKey;
        Long tenantId = currentTenantId();

        log.info("撤销审批流程 - processInstanceId: {}, idempotencyKey: {}", processInstanceId, effectiveKey);

        return approvalCancelOperationRepository
                .findByTenantAndKey(tenantId, effectiveKey)
                .flatMap(this::resumeCancel)
                .switchIfEmpty(Mono.defer(() -> newCancel(tenantId, processInstanceId, effectiveKey)))
                .doOnSuccess(success -> log.info(
                        "审批流程撤销结束 - processInstanceId: {}, idempotencyKey: {}, result: {}",
                        processInstanceId,
                        effectiveKey,
                        success))
                .doOnError(error -> log.error(
                        "审批流程撤销失败 - processInstanceId: {}, idempotencyKey: {}",
                        processInstanceId,
                        effectiveKey,
                        error));
    }

    /**
     * 重放既有撤销记录。
     *
     * @param operation 已存在的编排记录
     * @return 撤销结果
     */
    private Mono<Boolean> resumeCancel(ApprovalCancelOperation operation) {
        ApprovalCancelStatus status = operation.getStatus();
        if (status == ApprovalCancelStatus.COMPLETED) {
            log.info("撤销请求命中已完成记录，直接返回成功 - idempotencyKey: {}", operation.getIdempotencyKey());
            return Mono.just(true);
        }
        if (status == ApprovalCancelStatus.REMOTE_CANCELLED) {
            log.info("撤销请求命中待补偿记录，重试本地落库 - idempotencyKey: {}", operation.getIdempotencyKey());
            return compensateLocal(operation);
        }
        if (status == ApprovalCancelStatus.PENDING) {
            return Mono.error(new RuntimeException("撤销操作正在处理中，请稍后重试"));
        }

        // FAILED：重置为待撤销阶段后重新执行。
        operation.markPending();
        return approvalCancelOperationRepository.update(operation).flatMap(retried -> approvalProcessRepository
                .findByProcessInstanceId(retried.getProcessInstanceId())
                .switchIfEmpty(Mono.error(new RuntimeException("审批流程不存在: " + retried.getProcessInstanceId())))
                .flatMap(process -> {
                    if (!process.isCancellable()) {
                        return Mono.error(new RuntimeException("当前审批状态不允许撤销"));
                    }
                    return performCancel(retried, process);
                }));
    }

    /**
     * 首次撤销：校验流程状态，写入编排记录并执行远端撤销。
     *
     * @param tenantId 租户ID
     * @param processInstanceId 业务流程实例ID
     * @param idempotencyKey 幂等键
     * @return 撤销结果
     */
    private Mono<Boolean> newCancel(Long tenantId, String processInstanceId, String idempotencyKey) {
        return approvalProcessRepository
                .findByProcessInstanceId(processInstanceId)
                .switchIfEmpty(Mono.error(new RuntimeException("审批流程不存在: " + processInstanceId)))
                .flatMap(process -> {
                    if (!process.isCancellable()) {
                        return Mono.error(new RuntimeException("当前审批状态不允许撤销"));
                    }
                    return approvalCancelOperationRepository
                            .save(ApprovalCancelOperation.pending(tenantId, processInstanceId, idempotencyKey))
                            .flatMap(operation -> performCancel(operation, process));
                });
    }

    /**
     * 调用远端撤销并推进编排状态。
     *
     * <p>远端成功后先落 REMOTE_CANCELLED，再通过 {@link #compensateLocal(ApprovalCancelOperation)}
     * 在同步事务内补齐本地流程状态；远端已撤销后本地落库失败不得回退为失败状态，保留待补偿阶段即可。
     *
     * @param operation 编排记录
     * @param process 审批流程
     * @return 撤销结果
     */
    private Mono<Boolean> performCancel(ApprovalCancelOperation operation, ApprovalProcess process) {
        ApprovalPort client = getApprovalClient(process.getPlatform());
        return client.cancelApproval(process.getPlatformInstanceId(), process.getInitiatorId())
                .flatMap(success -> {
                    if (!Boolean.TRUE.equals(success)) {
                        operation.markFailed("远端平台拒绝撤销");
                        return approvalCancelOperationRepository
                                .update(operation)
                                .thenReturn(false);
                    }
                    operation.markRemoteCancelled();
                    return approvalCancelOperationRepository.update(operation).flatMap(this::compensateLocal);
                })
                .onErrorResume(error -> {
                    if (operation.getStatus() == ApprovalCancelStatus.REMOTE_CANCELLED) {
                        log.warn("远端已撤销但本地落库失败，保留待补偿状态 - idempotencyKey: {}", operation.getIdempotencyKey(), error);
                        return Mono.just(true);
                    }
                    operation.markFailed(safeErrorMessage(error));
                    return approvalCancelOperationRepository.update(operation).thenReturn(false);
                });
    }

    /**
     * 本地补偿：在同一同步事务内更新流程状态并标记编排记录完成。
     *
     * <p>本地落库未生效时不视为撤销失败，保留 REMOTE_CANCELLED 以便同键重放补齐。
     *
     * @param operation 编排记录
     * @return 撤销结果
     */
    private Mono<Boolean> compensateLocal(ApprovalCancelOperation operation) {
        return approvalCancelOperationRepository
                .completeCancel(operation.getTenantId(), operation.getProcessInstanceId(), operation.getId())
                .map(completed -> {
                    if (!Boolean.TRUE.equals(completed)) {
                        log.warn(
                                "本地撤销状态落库未生效，保留待补偿状态 - processInstanceId: {}, idempotencyKey: {}",
                                operation.getProcessInstanceId(),
                                operation.getIdempotencyKey());
                    }
                    return true;
                });
    }

    /**
     * 读取当前租户ID
     *
     * @return 租户ID
     */
    private Long currentTenantId() {
        return TenantContext.currentTenantId()
                .map(Long::parseLong)
                .orElseThrow(() -> new IllegalStateException("租户ID未设置"));
    }

    /**
     * 截断异常信息以匹配编排记录 errorMessage 字段长度。
     *
     * @param error 异常
     * @return 可持久化的失败原因
     */
    private static String safeErrorMessage(Throwable error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) {
            message = error.getClass().getSimpleName();
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }

    /**
     * 查询待审批列表。
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
     * 查询我已执行审批动作的流程列表。
     *
     * @param approverId 当前审批人ID
     * @return 包含本人同意或拒绝记录的流程列表
     */
    public Flux<ApprovalProcess> getCompletedApprovals(String approverId) {
        log.info("查询已办审批列表 - approverId: {}", approverId);

        return approvalProcessRepository
                .findCompletedByApprover(approverId)
                .flatMapMany(Flux::fromIterable)
                .doOnComplete(() -> log.info("已办审批列表查询成功 - approverId: {}", approverId))
                .doOnError(error -> log.error("已办审批列表查询失败 - approverId: {}", approverId, error));
    }

    /**
     * 查询我发起的审批列表。
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
     * 根据平台类型获取审批端口实现。
     *
     * <p>本地审批过程保存的平台标识为枚举名（大写，如 {@code FEISHU}），
     * 而客户端 Bean 名为小写前缀（如 {@code feishuApprovalClient}），
     * 因此查找前统一转小写，避免详情、审批与撤销链路查不到端口。
     *
     * @param platform 平台类型
     * @return 审批端口实例
     */
    private ApprovalPort getApprovalClient(String platform) {
        ApprovalPort client =
                approvalClients.get(String.valueOf(platform).toLowerCase(java.util.Locale.ROOT) + "ApprovalClient");
        if (client == null) {
            throw new RuntimeException("不支持的办公平台: " + platform);
        }
        return client;
    }

    /**
     * 将对象转换为JSON字符串。
     *
     * <p>对于已经是JSON字符串的入参（如表单数据），直接原样返回，避免二次转义；
     * 其他对象（如审批人ID数组）通过 {@link ObjectMapper} 序列化为合法JSON。
     *
     * @param obj 对象
     * @return JSON字符串；入参为null时返回null
     */
    private String convertToJson(Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof String json) {
            return json;
        }
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("对象序列化为JSON失败，类型：{}", obj.getClass().getName(), e);
            throw new IllegalStateException("对象序列化为JSON失败", e);
        }
    }

    /**
     * 解析平台审批明细，回填审批记录列表。
     *
     * <p>各平台明细中记录列表的字段不同，按平台提取：
     * <ul>
     *   <li>dingtalk：顶层 {@code tasks}（回退 {@code operationRecords}）</li>
     *   <li>feishu：顶层 {@code task_list}（回退 {@code timeline}）</li>
     *   <li>wecom：{@code info.record}，每个 record 的 {@code approverattr}
     *       内每个审批人展开为一条记录，动作由 record 的 {@code sp_status} 映射</li>
     * </ul>
     *
     * <p>字段按常见别名容忍缺失；动作无法识别时留空，时间按毫秒/秒时间戳自适应。
     * 结构不符或解析异常时返回空列表并记录告警，不让单平台格式差异阻断详情查询。
     *
     * @param platform 平台标识（本地保存为枚举名，如 {@code FEISHU}）
     * @param detail 平台返回的审批详情
     * @return 审批记录列表（永不为 null）
     */
    private List<ApprovalRecord> parseApprovalRecords(String platform, Map<String, Object> detail) {
        if (detail == null || detail.isEmpty()) {
            return List.of();
        }
        try {
            return switch (String.valueOf(platform).toLowerCase(java.util.Locale.ROOT)) {
                case "dingtalk" -> parseRecordList(firstList(detail, List.of("tasks", "operationRecords")));
                case "feishu" -> parseRecordList(firstList(detail, List.of("task_list", "timeline")));
                case "wecom" -> parseRecordList(firstList(castEntry(detail.get("info")), List.of("record")));
                default -> parseRecordList(firstList(detail, List.of("tasks", "task_list", "record")));
            };
        } catch (Exception ex) {
            log.warn("审批记录解析失败，回填为空记录 - platform: {}", platform, ex);
            return List.of();
        }
    }

    /**
     * 将记录条目列表映射为审批记录；企微 {@code approverattr} 嵌套结构展开为逐审批人记录。
     *
     * @param entries 平台记录条目
     * @return 审批记录列表
     */
    private List<ApprovalRecord> parseRecordList(List<?> entries) {
        if (entries.isEmpty()) {
            return List.of();
        }
        List<ApprovalRecord> records = new ArrayList<>(entries.size());
        int order = 1;
        for (Object item : entries) {
            if (!(item instanceof Map<?, ?> raw)) {
                continue;
            }
            Map<String, Object> entry = castEntry(raw);
            Object approvers = entry.get("approverattr");
            if (approvers instanceof List<?> approverList && !approverList.isEmpty()) {
                ApprovalAction action = wecomAction(entry.get("sp_status"));
                for (Object approverObj : approverList) {
                    if (approverObj instanceof Map<?, ?> approverRaw) {
                        records.add(buildRecord(castEntry(approverRaw), action, order++));
                    }
                }
                continue;
            }
            ApprovalAction action = normalizeAction(textOf(entry, List.of("action", "result")));
            records.add(buildRecord(entry, action, order++));
        }
        return records;
    }

    /**
     * 按别名顺序提取单条记录字段并构建审批记录。
     *
     * @param entry 记录条目
     * @param action 已确定的动作（可为 null）
     * @param nodeOrder 节点序号（从 1 开始）
     * @return 审批记录
     */
    private ApprovalRecord buildRecord(Map<String, Object> entry, ApprovalAction action, int nodeOrder) {
        return ApprovalRecord.builder()
                .approverId(textOf(entry, List.of("userid", "user_id", "approverid")))
                .approverName(textOf(entry, List.of("approve_name", "user_name", "name")))
                .action(action)
                .comment(textOf(entry, List.of("speech", "comment", "remark")))
                .approvalTime(timeOf(entry, List.of("sp_time", "create_time", "createTime", "operationTime")))
                .nodeOrder(nodeOrder)
                .build();
    }

    /** 将平台动作文本映射为 {@link ApprovalAction}；无法识别时返回 null。 */
    private ApprovalAction normalizeAction(String raw) {
        if (raw == null) {
            return null;
        }
        return switch (raw.toUpperCase(java.util.Locale.ROOT)) {
            case "AGREE", "APPROVE", "APPROVED", "PASS", "AUTO_APPROVED" -> ApprovalAction.APPROVE;
            case "REFUSE", "REJECT", "REJECTED" -> ApprovalAction.REJECT;
            case "TRANSFER" -> ApprovalAction.TRANSFER;
            default -> null;
        };
    }

    /** 企微 record 状态映射：2 审批通过、3 审批拒绝，其余（含待审 1）留空。 */
    private ApprovalAction wecomAction(Object spStatus) {
        Integer status = intOf(spStatus);
        if (status == null) {
            return null;
        }
        return switch (status) {
            case 2 -> ApprovalAction.APPROVE;
            case 3 -> ApprovalAction.REJECT;
            default -> null;
        };
    }

    /** 按别名顺序取第一个 List 类型字段；未命中时返回空列表。 */
    private List<?> firstList(Map<String, Object> source, List<String> keys) {
        if (source == null) {
            return List.of();
        }
        for (String key : keys) {
            if (source.get(key) instanceof List<?> list) {
                return list;
            }
        }
        return List.of();
    }

    /** 按别名顺序取第一个非空文本字段。 */
    private String textOf(Map<String, Object> entry, List<String> keys) {
        for (String key : keys) {
            Object value = entry.get(key);
            if (value != null && !String.valueOf(value).isBlank()) {
                return String.valueOf(value).trim();
            }
        }
        return null;
    }

    /**
     * 按别名顺序提取时间字段并转为本地时间；秒级时间戳自适应放大为毫秒。
     *
     * @return 审批时间；字段缺失或非法时返回 null
     */
    private LocalDateTime timeOf(Map<String, Object> entry, List<String> keys) {
        for (String key : keys) {
            Long epochMillis = epochMillisOf(entry.get(key));
            if (epochMillis != null) {
                return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault());
            }
        }
        return null;
    }

    private Long epochMillisOf(Object value) {
        if (value == null) {
            return null;
        }
        try {
            long epoch = Long.parseLong(String.valueOf(value).trim());
            // 小于 1e12 视为秒级时间戳，放大为毫秒。
            return epoch < 1_000_000_000_000L ? epoch * 1000L : epoch;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Integer intOf(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castEntry(Object raw) {
        return raw instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }
}
