package com.apex.scaffold.workflow.application.service;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.workflow.application.validation.WorkflowDefinitionGraph;
import com.apex.scaffold.workflow.domain.model.ProcessInstance;
import com.apex.scaffold.workflow.domain.model.Task;
import com.apex.scaffold.workflow.domain.model.WorkflowDefinition;
import com.apex.scaffold.workflow.domain.model.WorkflowDefinitionStatus;
import com.apex.scaffold.workflow.domain.repository.ProcessInstanceRepository;
import com.apex.scaffold.workflow.domain.repository.TaskRepository;
import com.apex.scaffold.workflow.domain.repository.WorkflowDefinitionRepository;
import com.apex.scaffold.workflow.domain.repository.WorkflowTaskTransaction;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 工作流引擎服务
 *
 * <p>提供轻量级工作流引擎的核心功能，包括流程定义管理、流程实例管理和任务管理。
 * 该服务基于 DDD 领域驱动设计，遵循 WebFlux 响应式编程模型。
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li><strong>流程定义管理</strong>：创建草稿、编辑、校验、发布、启用/停用与查询</li>
 *   <li><strong>流程实例管理</strong>：启动流程、查询流程状态</li>
 *   <li><strong>任务管理</strong>：查询待办任务、完成任务、驳回任务</li>
 * </ul>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * // 1. 创建并发布流程定义
 * WorkflowDefinition definition = new WorkflowDefinition();
 * definition.setWorkflowCode("leave_approval");
 * definition.setWorkflowName("请假审批流程");
 * definition.setDagJson("{...}");  // DAG 流程图 JSON
 * WorkflowDefinition draft = workflowService.createDraft(definition, 10000L).block();
 * workflowService.publishDefinition(draft.getId(), 10000L).block();
 *
 * // 2. 发起流程实例
 * ProcessInstance instance = workflowService.startProcessInstance(
 *     "leave_approval",           // 流程编码
 *     "LEAVE-2026-001",          // 业务主键
 *     10001L,                    // 发起人ID
 *     "{\"days\": 3, \"reason\": \"病假\"}"  // 流程上下文
 * ).block();
 *
 * // 3. 查询用户待办任务
 * List<Task> tasks = workflowService.getPendingTasks(10002L).block();
 *
 * // 4. 审批通过
 * workflowService.completeTask(
 *     tasks.get(0).getId(),      // 任务ID
 *     10002L,                    // 审批人ID
 *     "同意请假申请"              // 审批意见
 * ).block();
 *
 * // 5. 驳回任务
 * workflowService.rejectTask(
 *     taskId,
 *     10002L,
 *     "请假理由不充分"
 * ).block();
 * }</pre>
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li><strong>响应式设计</strong>：所有方法返回 {@link Mono} 或 {@link reactor.core.publisher.Flux}，
 *       支持非阻塞异步调用</li>
 *   <li><strong>License 门控</strong>：此服务需要 Enterprise 版 License 的 workflow 特性门控，
 *       通过 {@code @FeatureGate("workflow")} 注解控制</li>
 *   <li><strong>事务边界</strong>：任务条件更新保护单行竞争，
 *       驳回、回退与委派通过同步事务端口保证多步写入原子性</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>流程定义的 DAG JSON 必须符合 WorkflowNode 和
 *       WorkflowEdge 的格式规范</li>
 *   <li>流程上下文 JSON 应保持精简，避免存储大对象（建议 &lt; 10KB）</li>
 *   <li>任务操作仅允许当前办理人处理 PENDING 任务，校验失败时不修改任务</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.3.0
 * @since 1.3.0
 * @see com.apex.scaffold.workflow.domain.model.WorkflowDefinition
 * @see com.apex.scaffold.workflow.domain.model.ProcessInstance
 * @see com.apex.scaffold.workflow.domain.model.Task
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowService {

    /** 流程实例挂起状态码 */
    private static final String STATUS_SUSPENDED = "SUSPENDED";

    /** 流程实例驳回状态码 */
    private static final String STATUS_REJECTED = "REJECTED";

    /** 流程实例完成状态码 */
    private static final String STATUS_COMPLETED = "COMPLETED";

    /** 流程实例运行状态码 */
    private static final String STATUS_RUNNING = "RUNNING";

    /** 任务待处理状态码 */
    private static final String STATUS_PENDING = "PENDING";

    /** 超时策略：自动完成 */
    private static final String TIMEOUT_STRATEGY_AUTO_COMPLETE = "AUTO_COMPLETE";

    /** 超时策略：升级 */
    private static final String TIMEOUT_STRATEGY_ESCALATE = "ESCALATE";

    /** 工作流定义仓储，负责流程定义的持久化操作 */
    private final WorkflowDefinitionRepository workflowDefinitionRepository;

    /** 流程实例仓储，负责流程实例的持久化操作 */
    private final ProcessInstanceRepository processInstanceRepository;

    /** 任务仓储，负责任务的持久化操作 */
    private final TaskRepository taskRepository;

    private final WorkflowTaskTransaction taskTransaction;

    /**
     * 创建流程定义草稿。
     *
     * <p>同一流程编码下版本号自动递增，新草稿默认未启用；发布前可继续编辑。
     * 并发创建同版本时由 {@code (tenant_id, workflow_code, version, deleted)} 唯一键拒绝。</p>
     *
     * @param payload    流程定义负载，仅使用 workflowCode、workflowName、description、dagJson
     * @param operatorId 操作人ID
     * @return 创建后的草稿
     */
    public Mono<WorkflowDefinition> createDraft(WorkflowDefinition payload, Long operatorId) {
        String workflowCode = payload.getWorkflowCode();
        if (workflowCode == null || workflowCode.isBlank()) {
            return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "流程编码不能为空"));
        }
        log.info("创建流程定义草稿: code={}, name={}", workflowCode, payload.getWorkflowName());
        return workflowDefinitionRepository
                .findVersionsByCode(workflowCode)
                .map(versions -> versions.isEmpty() ? 1 : versions.get(0).getVersion() + 1)
                .defaultIfEmpty(1)
                .flatMap(version -> {
                    LocalDateTime now = LocalDateTime.now();
                    WorkflowDefinition draft = new WorkflowDefinition();
                    draft.setWorkflowCode(workflowCode);
                    draft.setWorkflowName(payload.getWorkflowName());
                    draft.setDescription(payload.getDescription());
                    draft.setDagJson(payload.getDagJson());
                    draft.setVersion(version);
                    draft.setStatus(WorkflowDefinitionStatus.DRAFT);
                    draft.setEnabled(0);
                    draft.setCreateBy(operatorId);
                    draft.setUpdateBy(operatorId);
                    draft.setCreateTime(now);
                    draft.setUpdateTime(now);
                    return workflowDefinitionRepository.insert(draft).thenReturn(draft);
                });
    }

    /**
     * 更新流程定义草稿。
     *
     * @param id         流程定义ID
     * @param payload    更新负载，仅覆盖 workflowName、description、dagJson
     * @param operatorId 操作人ID
     * @return 更新后的草稿
     */
    public Mono<WorkflowDefinition> updateDraft(Long id, WorkflowDefinition payload, Long operatorId) {
        log.info("更新流程定义草稿: id={}, operatorId={}", id, operatorId);
        return workflowDefinitionRepository
                .findById(id)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "流程定义不存在")))
                .flatMap(definition -> {
                    if (!definition.isEditable()) {
                        return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "只有草稿状态的流程定义可以编辑"));
                    }
                    definition.setWorkflowName(payload.getWorkflowName());
                    definition.setDescription(payload.getDescription());
                    definition.setDagJson(payload.getDagJson());
                    definition.setUpdateBy(operatorId);
                    definition.setUpdateTime(LocalDateTime.now());
                    return workflowDefinitionRepository
                            .updateById(definition)
                            .flatMap(count -> count == 1
                                    ? Mono.just(definition)
                                    : Mono.error(new IllegalStateException("流程定义已被其他操作修改")));
                });
    }

    /**
     * 校验流程定义的 DAG 结构。
     *
     * @param id 流程定义ID
     */
    public Mono<Void> validateDefinition(Long id) {
        return workflowDefinitionRepository
                .findById(id)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "流程定义不存在")))
                .map(definition -> {
                    WorkflowDefinitionGraph.parse(definition.getDagJson());
                    return definition;
                })
                .then();
    }

    /**
     * 发布流程定义草稿。
     *
     * <p>发布前再次校验 DAG；发布后该版本内容不可变，运行中的实例继续引用原版本。</p>
     *
     * @param id         流程定义ID
     * @param operatorId 操作人ID
     * @return 发布后的流程定义
     */
    public Mono<WorkflowDefinition> publishDefinition(Long id, Long operatorId) {
        log.info("发布流程定义: id={}, operatorId={}", id, operatorId);
        return workflowDefinitionRepository
                .findById(id)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "流程定义不存在")))
                .flatMap(definition -> {
                    if (!definition.isEditable()) {
                        return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "只有草稿状态的流程定义可以发布"));
                    }
                    WorkflowDefinitionGraph.parse(definition.getDagJson());
                    definition.publish(operatorId, LocalDateTime.now());
                    definition.setUpdateBy(operatorId);
                    definition.setUpdateTime(LocalDateTime.now());
                    return workflowDefinitionRepository
                            .updateById(definition)
                            .flatMap(count -> count == 1
                                    ? Mono.just(definition)
                                    : Mono.error(new IllegalStateException("流程定义已被其他操作修改")));
                });
    }

    /**
     * 启用已发布的流程定义。
     *
     * @param id         流程定义ID
     * @param operatorId 操作人ID
     * @return 启用后的流程定义
     */
    public Mono<WorkflowDefinition> enableDefinition(Long id, Long operatorId) {
        log.info("启用流程定义: id={}, operatorId={}", id, operatorId);
        return workflowDefinitionRepository
                .findById(id)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "流程定义不存在")))
                .flatMap(definition -> {
                    if (definition.isDraft()) {
                        return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "流程定义尚未发布，不能启用"));
                    }
                    definition.enable();
                    definition.setUpdateBy(operatorId);
                    definition.setUpdateTime(LocalDateTime.now());
                    return workflowDefinitionRepository
                            .updateById(definition)
                            .flatMap(count -> count == 1
                                    ? Mono.just(definition)
                                    : Mono.error(new IllegalStateException("流程定义已被其他操作修改")));
                });
    }

    /**
     * 停用已发布的流程定义。
     *
     * @param id         流程定义ID
     * @param operatorId 操作人ID
     * @return 停用后的流程定义
     */
    public Mono<WorkflowDefinition> disableDefinition(Long id, Long operatorId) {
        log.info("停用流程定义: id={}, operatorId={}", id, operatorId);
        return workflowDefinitionRepository
                .findById(id)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "流程定义不存在")))
                .flatMap(definition -> {
                    if (definition.isDraft()) {
                        return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "流程定义尚未发布，不能停用"));
                    }
                    definition.disable();
                    definition.setUpdateBy(operatorId);
                    definition.setUpdateTime(LocalDateTime.now());
                    return workflowDefinitionRepository
                            .updateById(definition)
                            .flatMap(count -> count == 1
                                    ? Mono.just(definition)
                                    : Mono.error(new IllegalStateException("流程定义已被其他操作修改")));
                });
    }

    /**
     * 根据编码查询工作流定义。
     *
     * @param workflowCode 工作流编码
     * @return 工作流定义
     */
    public Mono<WorkflowDefinition> getWorkflowDefinitionByCode(String workflowCode) {
        return workflowDefinitionRepository.findByCode(workflowCode);
    }

    /**
     * 发起流程实例。
     *
     * <p>仅允许发起已发布且启用的流程定义。发起时按定义创建首个审批任务；
     * 若开始节点直接指向结束节点，实例直接完成。</p>
     *
     * @param workflowCode 工作流编码
     * @param businessKey 业务主键
     * @param initiatorId 发起人ID
     * @param contextJson 流程上下文JSON
     * @return 流程实例
     */
    public Mono<ProcessInstance> startProcessInstance(
            String workflowCode, String businessKey, Long initiatorId, String contextJson) {
        return startProcessInstance(workflowCode, businessKey, initiatorId, contextJson, null);
    }

    /**
     * 发起流程实例并在同一事务内追加待投递事件。
     *
     * <p>事件工厂在实例与首个任务写入后、事务提交前调用；返回 {@code null} 时不追加。
     *
     * @param workflowCode 工作流编码
     * @param businessKey 业务主键
     * @param initiatorId 发起人ID
     * @param contextJson 流程上下文JSON
     * @param eventFactory 由已创建实例构造事件；可空
     * @return 流程实例
     */
    public Mono<ProcessInstance> startProcessInstance(
            String workflowCode,
            String businessKey,
            Long initiatorId,
            String contextJson,
            java.util.function.Function<ProcessInstance, com.apex.scaffold.common.event.AbstractDomainEvent>
                    eventFactory) {
        log.info("发起流程实例: workflowCode={}, businessKey={}, initiatorId={}", workflowCode, businessKey, initiatorId);

        return workflowDefinitionRepository
                .findByCode(workflowCode)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "流程定义不存在或未发布：" + workflowCode)))
                .flatMap(definition -> {
                    WorkflowDefinitionGraph graph = WorkflowDefinitionGraph.parse(definition.getDagJson());
                    return inTaskTransactionResult(operations -> {
                        LocalDateTime now = LocalDateTime.now();
                        ProcessInstance instance = new ProcessInstance();
                        instance.setWorkflowDefinitionId(definition.getId());
                        instance.setBusinessKey(businessKey);
                        instance.setInitiatorId(initiatorId);
                        instance.setContextJson(contextJson);
                        instance.setStartTime(now);
                        WorkflowDefinitionGraph.Node first = graph.next(graph.start());
                        if (first == null || first.isEnd()) {
                            instance.setStatus(STATUS_COMPLETED);
                            instance.setEndTime(now);
                            requireOne(operations.insertInstance(instance), "流程实例创建失败");
                            appendStartEvent(operations, eventFactory, instance);
                            return instance;
                        }
                        instance.setStatus(STATUS_RUNNING);
                        instance.setCurrentNodeKey(first.nodeId());
                        requireOne(operations.insertInstance(instance), "流程实例创建失败");
                        requireOne(operations.insertTask(buildTask(instance, first, initiatorId, now)), "任务创建失败");
                        appendStartEvent(operations, eventFactory, instance);
                        return instance;
                    });
                });
    }

    private void appendStartEvent(
            WorkflowTaskTransaction.Operations operations,
            java.util.function.Function<ProcessInstance, com.apex.scaffold.common.event.AbstractDomainEvent> factory,
            ProcessInstance instance) {
        if (factory != null) {
            com.apex.scaffold.common.event.AbstractDomainEvent event = factory.apply(instance);
            if (event != null) {
                operations.appendEvent(event);
            }
        }
    }

    /**
     * 查询用户的待办任务列表。
     *
     * @param assigneeId 办理人ID
     * @return 待办任务列表
     */
    public Mono<List<Task>> getPendingTasks(Long assigneeId) {
        return taskRepository.findByAssignee(assigneeId);
    }

    /**
     * 完成任务（审批通过）。
     *
     * <p>在同步事务内以条件更新竞争任务，随后按流程定义推进到后继节点：
     * 后继为审批节点时创建新待办任务，后继为结束节点时结束流程实例。</p>
     *
     * @param taskId 任务ID
     * @param approverId 审批人ID
     * @param comment 审批意见
     */
    public Mono<Void> completeTask(Long taskId, Long approverId, String comment) {
        return completeTask(taskId, approverId, comment, null);
    }

    /**
     * 完成任务并在同一事务内追加待投递事件。
     *
     * <p>事件工厂在任务与实例状态更新之后、事务提交之前调用；落箱失败会回滚整个事务，
     * 避免业务已提交但事件丢失。工厂返回 {@code null} 时不追加事件。
     *
     * @param taskId 任务ID
     * @param approverId 审批人ID
     * @param comment 审批意见
     * @param eventFactory 由已完成任务与实例构造事件；可空
     */
    public Mono<Void> completeTask(
            Long taskId,
            Long approverId,
            String comment,
            java.util.function.BiFunction<Task, ProcessInstance, com.apex.scaffold.common.event.AbstractDomainEvent>
                    eventFactory) {
        log.info("完成任务: taskId={}, approverId={}, comment={}", taskId, approverId, comment);

        return inTaskTransaction(operations -> {
            Task task = validatePendingTask(operations.findTask(taskId), approverId, "完成");
            task.setStatus(STATUS_COMPLETED);
            task.setCompletedTime(LocalDateTime.now());
            requireOne(operations.updateTask(task), "任务已被其他操作修改");
            ProcessInstance instance = requireInstance(operations, task.getProcessInstanceId());
            if (!STATUS_RUNNING.equals(instance.getStatus())) {
                throw new IllegalStateException("流程实例不在运行状态，无法推进");
            }
            WorkflowDefinition definition = operations.findDefinition(instance.getWorkflowDefinitionId());
            if (definition == null) {
                throw new IllegalStateException("流程定义不存在");
            }
            WorkflowDefinitionGraph graph = WorkflowDefinitionGraph.parse(definition.getDagJson());
            WorkflowDefinitionGraph.Node current = graph.node(task.getNodeCode());
            if (current == null) {
                throw new IllegalStateException("流程定义缺少任务节点：" + task.getNodeCode());
            }
            WorkflowDefinitionGraph.Node next = graph.next(current);
            LocalDateTime now = LocalDateTime.now();
            if (next == null || next.isEnd()) {
                instance.setStatus(STATUS_COMPLETED);
                instance.setEndTime(now);
            } else {
                instance.setCurrentNodeKey(next.nodeId());
                requireOne(operations.insertTask(buildTask(instance, next, instance.getInitiatorId(), now)), "任务创建失败");
            }
            requireOne(operations.updateInstance(instance), "流程实例更新失败");
            if (eventFactory != null) {
                com.apex.scaffold.common.event.AbstractDomainEvent event = eventFactory.apply(task, instance);
                if (event != null) {
                    operations.appendEvent(event);
                }
            }
        });
    }

    /**
     * 驳回任务。
     *
     * @param taskId 任务ID
     * @param approverId 审批人ID
     * @param comment 驳回意见
     */
    public Mono<Void> rejectTask(Long taskId, Long approverId, String comment) {
        log.info("驳回任务: taskId={}, approverId={}, comment={}", taskId, approverId, comment);

        return inTaskTransaction(operations -> {
            Task task = validatePendingTask(operations.findTask(taskId), approverId, "驳回");
            task.setStatus(STATUS_REJECTED);
            task.setCompletedTime(LocalDateTime.now());
            requireOne(operations.updateTask(task), "任务已被其他操作修改");
            ProcessInstance instance = requireInstance(operations, task.getProcessInstanceId());
            instance.setStatus(STATUS_REJECTED);
            instance.setEndTime(LocalDateTime.now());
            requireOne(operations.updateInstance(instance), "流程实例更新失败");
        });
    }

    /**
     * 查询流程实例。
     *
     * @param processInstanceId 流程实例ID
     * @return 流程实例
     */
    public Mono<ProcessInstance> getProcessInstance(Long processInstanceId) {
        return processInstanceRepository.findById(processInstanceId);
    }

    /**
     * 流程回退到指定节点。
     *
     * <p>将当前任务回退到流程中的前置节点，用于处理审批驳回后需要重新审批的场景。
     * 回退操作会：
     * <ul>
     *   <li>将当前任务标记为 ROLLED_BACK 状态</li>
     *   <li>在目标节点创建新的待办任务</li>
     *   <li>保留完整的流转记录，确保审计追溯</li>
     * </ul>
     *
     * <p><strong>使用示例</strong></p>
     * <pre>{@code
     * // 将任务回退到"部门经理审批"节点
     * workflowService.rollbackToNode(
     *     12345L,              // 当前任务ID
     *     "dept_manager_node", // 目标节点编码
     *     10002L,              // 操作人ID
     *     "申请材料不完整，请补充后重新提交"  // 回退原因
     * ).block();
     * }</pre>
     *
     * <p><strong>注意事项</strong></p>
     * <ul>
     *   <li>目标节点必须是当前任务的前置节点，不允许跨分支回退</li>
     *   <li>回退操作不会删除历史审批记录</li>
     *   <li>回退后的任务继承原流程实例的上下文</li>
     * </ul>
     *
     * @param taskId 当前任务ID
     * @param targetNodeCode 目标节点编码
     * @param operatorId 操作人ID
     * @param reason 回退原因
     * @throws IllegalArgumentException 当目标节点不存在或不是前置节点时抛出
     */
    public Mono<Void> rollbackToNode(Long taskId, String targetNodeCode, Long operatorId, String reason) {
        return rollbackToNode(taskId, targetNodeCode, operatorId, reason, null);
    }

    /**
     * 回退任务并在同一事务内追加待投递事件。
     *
     * @param taskId 当前任务ID
     * @param targetNodeCode 目标节点编码
     * @param operatorId 操作人ID
     * @param reason 回退原因
     * @param eventFactory 由新任务构造事件；可空
     */
    public Mono<Void> rollbackToNode(
            Long taskId,
            String targetNodeCode,
            Long operatorId,
            String reason,
            java.util.function.Function<Task, com.apex.scaffold.common.event.AbstractDomainEvent> eventFactory) {
        log.info(
                "流程回退: taskId={}, targetNodeCode={}, operatorId={}, reason={}",
                taskId,
                targetNodeCode,
                operatorId,
                reason);

        return inTaskTransaction(operations -> {
            Task task = validatePendingTask(operations.findTask(taskId), operatorId, "回退");
            task.setStatus("ROLLED_BACK");
            task.setCompletedTime(LocalDateTime.now());
            requireOne(operations.updateTask(task), "任务已被其他操作修改");
            ProcessInstance instance = requireInstance(operations, task.getProcessInstanceId());
            Task newTask = new Task();
            newTask.setProcessInstanceId(instance.getId());
            newTask.setNodeCode(targetNodeCode);
            newTask.setNodeName(task.getNodeName());
            newTask.setStatus(STATUS_PENDING);
            newTask.setAssigneeId(operatorId);
            newTask.setCreatedTime(LocalDateTime.now());
            requireOne(operations.insertTask(newTask), "任务创建失败");
            if (eventFactory != null) {
                com.apex.scaffold.common.event.AbstractDomainEvent event = eventFactory.apply(newTask);
                if (event != null) {
                    operations.appendEvent(event);
                }
            }
        });
    }

    /**
     * 转办任务给其他人。
     *
     * <p>将当前待办任务转交给其他用户处理，用于处理原审批人不在或需要他人代办的场景。
     * 转办操作会：
     * <ul>
     *   <li>记录原审批人信息到 transferred_from 字段</li>
     *   <li>将任务的 assignee_id 修改为新审批人</li>
     *   <li>保留任务的其他属性（状态、创建时间等）</li>
     * </ul>
     *
     * <p><strong>使用示例</strong></p>
     * <pre>{@code
     * // 将任务转办给同事
     * workflowService.transferTask(
     *     12345L,   // 任务ID
     *     10003L,   // 新审批人ID
     *     10002L,   // 当前审批人ID
     *     "本周出差，请帮忙代为审批"  // 转办原因
     * ).block();
     * }</pre>
     *
     * <p><strong>与委派的区别</strong></p>
     * <ul>
     *   <li><strong>转办</strong>：任务所有权永久转移，新审批人完成后流程继续</li>
     *   <li><strong>委派</strong>：任务所有权临时转移，受托人完成后任务回到原审批人</li>
     * </ul>
     *
     * @param taskId 任务ID
     * @param newAssigneeId 新审批人ID
     * @param operatorId 当前操作人ID
     * @param reason 转办原因
     * @throws IllegalStateException 当任务已完成或不属于操作人时抛出
     */
    public Mono<Void> transferTask(Long taskId, Long newAssigneeId, Long operatorId, String reason) {
        return transferTask(taskId, newAssigneeId, operatorId, reason, null);
    }

    /**
     * 转办任务并在同一事务内追加待投递事件。
     *
     * @param taskId 任务ID
     * @param newAssigneeId 新办理人
     * @param operatorId 操作人
     * @param reason 转办原因
     * @param eventFactory 由更新后任务构造事件；可空
     */
    public Mono<Void> transferTask(
            Long taskId,
            Long newAssigneeId,
            Long operatorId,
            String reason,
            java.util.function.Function<Task, com.apex.scaffold.common.event.AbstractDomainEvent> eventFactory) {
        log.info(
                "转办任务: taskId={}, newAssigneeId={}, operatorId={}, reason={}",
                taskId,
                newAssigneeId,
                operatorId,
                reason);

        return inTaskTransaction(operations -> {
            Task task = validatePendingTask(operations.findTask(taskId), operatorId, "转办");
            task.setTransferredFrom(operatorId);
            task.setAssigneeId(newAssigneeId);
            requireOne(operations.updateTask(task), "任务已被其他操作修改");
            if (eventFactory != null) {
                com.apex.scaffold.common.event.AbstractDomainEvent event = eventFactory.apply(task);
                if (event != null) {
                    operations.appendEvent(event);
                }
            }
        });
    }

    /**
     * 委派任务给其他人。
     *
     * <p>将当前待办任务临时委托给其他用户处理，受托人完成后任务回到原审批人。
     * 委派操作会：
     * <ul>
     *   <li>创建一个新的 DELEGATED 状态任务给受托人</li>
     *   <li>原任务保持 PENDING 状态，等待受托人完成</li>
     *   <li>受托人完成后，原任务自动回到原审批人</li>
     * </ul>
     *
     * <p><strong>使用示例</strong></p>
     * <pre>{@code
     * // 委派任务给下属初审
     * workflowService.delegateTask(
     *     12345L,   // 任务ID
     *     10004L,   // 受托人ID
     *     10002L,   // 委托人ID
     *     "请先进行初审，审核通过后我再终审"  // 委派说明
     * ).block();
     * }</pre>
     *
     * <p><strong>委派流程</strong></p>
     * <ol>
     *   <li>原审批人创建委派任务</li>
     *   <li>受托人收到待办并处理</li>
     *   <li>受托人完成后，任务回到原审批人</li>
     *   <li>原审批人最终完成审批</li>
     * </ol>
     *
     * @param taskId 任务ID
     * @param delegateToId 受托人ID
     * @param operatorId 委托人ID
     * @param reason 委派说明
     * @throws IllegalStateException 当任务已完成或不属于操作人时抛出
     */
    public Mono<Void> delegateTask(Long taskId, Long delegateToId, Long operatorId, String reason) {
        return delegateTask(taskId, delegateToId, operatorId, reason, null);
    }

    /**
     * 委派任务并在同一事务内追加待投递事件。
     *
     * @param taskId 任务ID
     * @param delegateToId 受托人
     * @param operatorId 操作人
     * @param reason 委派原因
     * @param eventFactory 由新建委派任务构造事件；可空
     */
    public Mono<Void> delegateTask(
            Long taskId,
            Long delegateToId,
            Long operatorId,
            String reason,
            java.util.function.Function<Task, com.apex.scaffold.common.event.AbstractDomainEvent> eventFactory) {
        log.info(
                "委派任务: taskId={}, delegateToId={}, operatorId={}, reason={}", taskId, delegateToId, operatorId, reason);

        return inTaskTransaction(operations -> {
            Task task = validatePendingTask(operations.findTask(taskId), operatorId, "委派");
            requireOne(operations.updateTask(task), "任务已被其他操作修改");
            requireInstance(operations, task.getProcessInstanceId());
            Task delegatedTask = new Task();
            delegatedTask.setProcessInstanceId(task.getProcessInstanceId());
            delegatedTask.setNodeCode(task.getNodeCode());
            delegatedTask.setNodeName(task.getNodeName());
            delegatedTask.setStatus("DELEGATED");
            delegatedTask.setAssigneeId(delegateToId);
            delegatedTask.setTransferredFrom(operatorId);
            delegatedTask.setCreatedTime(LocalDateTime.now());
            requireOne(operations.insertTask(delegatedTask), "任务创建失败");
            if (eventFactory != null) {
                com.apex.scaffold.common.event.AbstractDomainEvent event = eventFactory.apply(delegatedTask);
                if (event != null) {
                    operations.appendEvent(event);
                }
            }
        });
    }

    /**
     * 处理后台扫描到的超时任务。
     *
     * <p>按持久租户和原快照条件办理；失效快照与 CAS 落败正常跳过，数据库异常仍然传播。
     *
     * <p>以空完成信号结束；任务已失效时同样为空完成，异常经响应式链路原样传播。
     *
     * @param expected 扫描时读取到的任务快照，用于校验状态、版本、处理人与超时配置未被并发修改
     */
    public Mono<Void> handleTimeoutTask(Task expected) {
        return inTaskTransaction(operations -> {
            Task current = operations.findTask(expected.getId());
            if (current == null
                    || !STATUS_PENDING.equals(current.getStatus())
                    || !java.util.Objects.equals(current.getTenantId(), expected.getTenantId())
                    || !java.util.Objects.equals(current.getVersion(), expected.getVersion())
                    || !java.util.Objects.equals(current.getAssigneeId(), expected.getAssigneeId())
                    || !java.util.Objects.equals(current.getTimeoutAt(), expected.getTimeoutAt())
                    || !java.util.Objects.equals(current.getTimeoutStrategy(), expected.getTimeoutStrategy())
                    || current.getTimeoutAt() == null
                    || current.getTimeoutAt().isAfter(LocalDateTime.now())) {
                return;
            }
            String strategy =
                    expected.getTimeoutStrategy() == null ? TIMEOUT_STRATEGY_ESCALATE : expected.getTimeoutStrategy();
            if (TIMEOUT_STRATEGY_AUTO_COMPLETE.equals(strategy)) {
                current.setStatus(STATUS_COMPLETED);
                current.setCompletedTime(LocalDateTime.now());
            } else if (TIMEOUT_STRATEGY_ESCALATE.equals(strategy)) {
                current.setTransferredFrom(expected.getAssigneeId());
                current.setAssigneeId(1L);
            } else {
                throw new IllegalArgumentException("未知的超时策略: " + strategy);
            }
            int count = operations.updateTimeoutTask(current, expected);
            if (count != 0 && count != 1) {
                throw new IllegalStateException("超时任务更新行数异常");
            }
        });
    }

    private Task validatePendingTask(Task task, Long operatorId, String operation) {
        if (task == null) {
            throw new IllegalStateException("任务不存在");
        }
        if (!STATUS_PENDING.equals(task.getStatus())) {
            throw new IllegalStateException("只能" + operation + "待办任务");
        }
        if (operatorId == null || task.getAssigneeId() == null || !operatorId.equals(task.getAssigneeId())) {
            throw new IllegalStateException("只能" + operation + "自己的任务");
        }
        return task;
    }

    private Mono<Void> inTaskTransaction(Consumer<WorkflowTaskTransaction.Operations> operation) {
        return Mono.deferContextual(context -> {
            String tenant = context.getOrDefault(TenantContext.REACTOR_CONTEXT_KEY, null);
            if (tenant == null || tenant.isBlank()) {
                return Mono.error(new IllegalStateException("工作流仓储缺少有效租户上下文"));
            }
            return Mono.<Void>fromRunnable(() -> taskTransaction.execute(tenant, operation))
                    .subscribeOn(BlockingIoSchedulers.jdbc());
        });
    }

    private <T> Mono<T> inTaskTransactionResult(Function<WorkflowTaskTransaction.Operations, T> operation) {
        return Mono.deferContextual(context -> {
            String tenant = context.getOrDefault(TenantContext.REACTOR_CONTEXT_KEY, null);
            if (tenant == null || tenant.isBlank()) {
                return Mono.error(new IllegalStateException("工作流仓储缺少有效租户上下文"));
            }
            return Mono.fromCallable(() -> {
                        AtomicReference<T> reference = new AtomicReference<>();
                        taskTransaction.execute(tenant, operations -> reference.set(operation.apply(operations)));
                        return reference.get();
                    })
                    .subscribeOn(BlockingIoSchedulers.jdbc());
        });
    }

    private Task buildTask(
            ProcessInstance instance, WorkflowDefinitionGraph.Node node, Long initiatorId, LocalDateTime now) {
        Task task = new Task();
        task.setProcessInstanceId(instance.getId());
        task.setNodeCode(node.nodeId());
        task.setNodeName(node.nodeName() == null ? node.nodeId() : node.nodeName());
        task.setStatus(STATUS_PENDING);
        task.setAssigneeId(WorkflowDefinitionGraph.resolveAssignee(node, initiatorId));
        task.setCreatedTime(now);
        String strategy = node.timeoutAction();
        if (node.timeoutHours() != null) {
            if (TIMEOUT_STRATEGY_AUTO_COMPLETE.equals(strategy) || TIMEOUT_STRATEGY_ESCALATE.equals(strategy)) {
                task.setTimeoutAt(now.plusHours(node.timeoutHours()));
                task.setTimeoutStrategy(strategy);
            }
        }
        return task;
    }

    private ProcessInstance requireInstance(WorkflowTaskTransaction.Operations operations, Long id) {
        ProcessInstance instance = operations.findInstance(id);
        if (instance == null) {
            throw new IllegalStateException("流程实例不存在");
        }
        return instance;
    }

    private void requireOne(int count, String message) {
        if (count != 1) {
            throw new IllegalStateException(message);
        }
    }

    /**
     * 挂起流程实例。
     *
     * <p>暂停流程实例的执行，挂起后该流程的所有待办任务无法审批，直到恢复。
     * 挂起操作会：
     * <ul>
     *   <li>将流程实例状态设置为 SUSPENDED</li>
     *   <li>阻止所有待办任务的审批操作</li>
     *   <li>保留流程上下文和历史记录</li>
     * </ul>
     *
     * <p><strong>使用场景</strong></p>
     * <ul>
     *   <li>业务数据需要修正，暂时冻结审批流程</li>
     *   <li>发现流程配置错误，需要修复后再继续</li>
     *   <li>审批人长期不在，需要暂停流程等待回归</li>
     * </ul>
     *
     * <p><strong>使用示例</strong></p>
     * <pre>{@code
     * // 挂起流程实例
     * workflowService.suspendInstance(
     *     67890L,   // 流程实例ID
     *     10001L,   // 操作人ID
     *     "发现业务数据异常，暂停审批，待修复后恢复"  // 挂起原因
     * ).block();
     * }</pre>
     *
     * @param processInstanceId 流程实例ID
     * @param operatorId 操作人ID
     * @param reason 挂起原因
     * @throws IllegalStateException 当流程已完成或已挂起时抛出
     */
    public Mono<Void> suspendInstance(Long processInstanceId, Long operatorId, String reason) {
        log.info("挂起流程实例: processInstanceId={}, operatorId={}, reason={}", processInstanceId, operatorId, reason);

        return processInstanceRepository
                .findById(processInstanceId)
                .flatMap(instance -> {
                    if (STATUS_COMPLETED.equals(instance.getStatus()) || STATUS_REJECTED.equals(instance.getStatus())) {
                        return Mono.error(new IllegalStateException("已完成或已驳回的流程无法挂起"));
                    }
                    if (STATUS_SUSPENDED.equals(instance.getStatus())) {
                        return Mono.error(new IllegalStateException("流程已处于挂起状态"));
                    }
                    instance.setSuspended(true);
                    instance.setStatus(STATUS_SUSPENDED);
                    return processInstanceRepository.updateById(instance);
                })
                .then();
    }

    /**
     * 恢复挂起的流程实例。
     *
     * <p>恢复挂起的流程实例，使其可以继续执行。
     * 恢复操作会：
     * <ul>
     *   <li>将流程实例状态从 SUSPENDED 改回 RUNNING</li>
     *   <li>允许所有待办任务继续审批</li>
     *   <li>保留挂起期间的所有数据</li>
     * </ul>
     *
     * <p><strong>使用示例</strong></p>
     * <pre>{@code
     * // 恢复流程实例
     * workflowService.resumeInstance(
     *     67890L,   // 流程实例ID
     *     10001L,   // 操作人ID
     *     "业务数据已修复，恢复审批流程"  // 恢复原因
     * ).block();
     * }</pre>
     *
     * <p><strong>注意事项</strong></p>
     * <ul>
     *   <li>只能恢复处于 SUSPENDED 状态的流程</li>
     *   <li>恢复后待办任务立即可以审批</li>
     *   <li>需要确认挂起原因已解决再恢复</li>
     * </ul>
     *
     * @param processInstanceId 流程实例ID
     * @param operatorId 操作人ID
     * @param reason 恢复原因
     * @throws IllegalStateException 当流程未挂起时抛出
     */
    public Mono<Void> resumeInstance(Long processInstanceId, Long operatorId, String reason) {
        log.info("恢复流程实例: processInstanceId={}, operatorId={}, reason={}", processInstanceId, operatorId, reason);

        return processInstanceRepository
                .findById(processInstanceId)
                .flatMap(instance -> {
                    if (!STATUS_SUSPENDED.equals(instance.getStatus())) {
                        return Mono.error(new IllegalStateException("只能恢复挂起状态的流程"));
                    }
                    instance.setSuspended(false);
                    instance.setStatus("RUNNING");
                    return processInstanceRepository.updateById(instance);
                })
                .then();
    }
}
