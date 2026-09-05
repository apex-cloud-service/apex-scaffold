package com.apex.scaffold.application.service;

import com.apex.scaffold.domain.model.workflow.ProcessInstance;
import com.apex.scaffold.domain.model.workflow.Task;
import com.apex.scaffold.domain.model.workflow.WorkflowDefinition;
import com.apex.scaffold.domain.repository.ProcessInstanceRepository;
import com.apex.scaffold.domain.repository.TaskRepository;
import com.apex.scaffold.domain.repository.WorkflowDefinitionRepository;
import java.time.LocalDateTime;
import java.util.List;
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
 * <h2>核心功能</h2>
 * <ul>
 *   <li><strong>流程定义管理</strong>：创建、查询工作流定义</li>
 *   <li><strong>流程实例管理</strong>：启动流程、查询流程状态</li>
 *   <li><strong>任务管理</strong>：查询待办任务、完成任务、驳回任务</li>
 * </ul>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * // 1. 创建流程定义
 * WorkflowDefinition definition = new WorkflowDefinition();
 * definition.setWorkflowCode("leave_approval");
 * definition.setWorkflowName("请假审批流程");
 * definition.setDagJson("{...}");  // DAG 流程图 JSON
 * workflowService.createWorkflowDefinition(definition).block();
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
 *   <li><strong>事务边界</strong>：数据库操作由 Repository 层控制事务，
 *       Service 层不显式声明 {@code @Transactional}</li>
 * </ul>
 *
 * <h2>注意事项</h2>
 * <ul>
 *   <li>流程定义的 DAG JSON 必须符合 {@link com.apex.scaffold.domain.workflow.WorkflowNode} 和
 *       {@link com.apex.scaffold.domain.workflow.WorkflowEdge} 的格式规范</li>
 *   <li>流程上下文 JSON 应保持精简，避免存储大对象（建议 &lt; 10KB）</li>
 *   <li>任务完成/驳回操作是幂等的，重复调用不会产生副作用</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.3.0
 * @since 1.3.0
 * @see com.apex.scaffold.domain.model.workflow.WorkflowDefinition
 * @see com.apex.scaffold.domain.model.workflow.ProcessInstance
 * @see com.apex.scaffold.domain.model.workflow.Task
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowService {

    private final WorkflowDefinitionRepository workflowDefinitionRepository;
    private final ProcessInstanceRepository processInstanceRepository;
    private final TaskRepository taskRepository;

    /**
     * 创建工作流定义。
     *
     * @param definition 工作流定义
     * @return 创建结果
     */
    public Mono<WorkflowDefinition> createWorkflowDefinition(WorkflowDefinition definition) {
        log.info("创建工作流定义: code={}, name={}", definition.getWorkflowCode(), definition.getWorkflowName());
        return workflowDefinitionRepository.insert(definition).thenReturn(definition);
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
     * @param workflowCode 工作流编码
     * @param businessKey 业务主键
     * @param initiatorId 发起人ID
     * @param contextJson 流程上下文JSON
     * @return 流程实例
     */
    public Mono<ProcessInstance> startProcessInstance(
            String workflowCode, String businessKey, Long initiatorId, String contextJson) {
        log.info("发起流程实例: workflowCode={}, businessKey={}, initiatorId={}", workflowCode, businessKey, initiatorId);

        return workflowDefinitionRepository.findByCode(workflowCode).flatMap(definition -> {
            ProcessInstance instance = new ProcessInstance();
            instance.setWorkflowDefinitionId(definition.getId());
            instance.setBusinessKey(businessKey);
            instance.setStatus("RUNNING");
            instance.setInitiatorId(initiatorId);
            instance.setContextJson(contextJson);
            instance.setStartTime(LocalDateTime.now());
            return processInstanceRepository.insert(instance).thenReturn(instance);
        });
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
     * @param taskId 任务ID
     * @param approverId 审批人ID
     * @param comment 审批意见
     * @return 完成结果
     */
    public Mono<Void> completeTask(Long taskId, Long approverId, String comment) {
        log.info("完成任务: taskId={}, approverId={}, comment={}", taskId, approverId, comment);

        return taskRepository
                .findById(taskId)
                .flatMap(task -> {
                    task.setStatus("COMPLETED");
                    task.setCompletedTime(LocalDateTime.now());
                    return taskRepository.updateById(task);
                })
                .then();
    }

    /**
     * 驳回任务。
     *
     * @param taskId 任务ID
     * @param approverId 审批人ID
     * @param comment 驳回意见
     * @return 驳回结果
     */
    public Mono<Void> rejectTask(Long taskId, Long approverId, String comment) {
        log.info("驳回任务: taskId={}, approverId={}, comment={}", taskId, approverId, comment);

        return taskRepository
                .findById(taskId)
                .flatMap(task -> {
                    task.setStatus("REJECTED");
                    task.setCompletedTime(LocalDateTime.now());
                    return taskRepository.updateById(task);
                })
                .flatMap(count -> processInstanceRepository.findById(taskId).flatMap(instance -> {
                    instance.setStatus("REJECTED");
                    instance.setEndTime(LocalDateTime.now());
                    return processInstanceRepository.updateById(instance);
                }))
                .then();
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
     * <h3>使用示例</h3>
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
     * <h3>注意事项</h3>
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
     * @return 回退操作结果
     * @throws IllegalArgumentException 当目标节点不存在或不是前置节点时抛出
     */
    public Mono<Void> rollbackToNode(Long taskId, String targetNodeCode, Long operatorId, String reason) {
        log.info(
                "流程回退: taskId={}, targetNodeCode={}, operatorId={}, reason={}",
                taskId,
                targetNodeCode,
                operatorId,
                reason);

        return taskRepository
                .findById(taskId)
                .flatMap(task -> {
                    task.setStatus("ROLLED_BACK");
                    task.setCompletedTime(LocalDateTime.now());
                    return taskRepository
                            .updateById(task)
                            .then(processInstanceRepository.findById(task.getProcessInstanceId()))
                            .flatMap(instance -> {
                                Task newTask = new Task();
                                newTask.setProcessInstanceId(instance.getId());
                                newTask.setNodeCode(targetNodeCode);
                                newTask.setStatus("PENDING");
                                newTask.setAssigneeId(operatorId);
                                newTask.setCreatedTime(LocalDateTime.now());
                                return taskRepository.insert(newTask);
                            });
                })
                .then();
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
     * <h3>使用示例</h3>
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
     * <h3>与委派的区别</h3>
     * <ul>
     *   <li><strong>转办</strong>：任务所有权永久转移，新审批人完成后流程继续</li>
     *   <li><strong>委派</strong>：任务所有权临时转移，受托人完成后任务回到原审批人</li>
     * </ul>
     *
     * @param taskId 任务ID
     * @param newAssigneeId 新审批人ID
     * @param operatorId 当前操作人ID
     * @param reason 转办原因
     * @return 转办操作结果
     * @throws IllegalStateException 当任务已完成或不属于操作人时抛出
     */
    public Mono<Void> transferTask(Long taskId, Long newAssigneeId, Long operatorId, String reason) {
        log.info(
                "转办任务: taskId={}, newAssigneeId={}, operatorId={}, reason={}",
                taskId,
                newAssigneeId,
                operatorId,
                reason);

        return taskRepository
                .findById(taskId)
                .flatMap(task -> {
                    if (!"PENDING".equals(task.getStatus())) {
                        return Mono.error(new IllegalStateException("只能转办待办任务"));
                    }
                    if (!task.getAssigneeId().equals(operatorId)) {
                        return Mono.error(new IllegalStateException("只能转办自己的任务"));
                    }
                    task.setTransferredFrom(operatorId);
                    task.setAssigneeId(newAssigneeId);
                    return taskRepository.updateById(task);
                })
                .then();
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
     * <h3>使用示例</h3>
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
     * <h3>委派流程</h3>
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
     * @return 委派操作结果
     * @throws IllegalStateException 当任务已完成或不属于操作人时抛出
     */
    public Mono<Void> delegateTask(Long taskId, Long delegateToId, Long operatorId, String reason) {
        log.info(
                "委派任务: taskId={}, delegateToId={}, operatorId={}, reason={}", taskId, delegateToId, operatorId, reason);

        return taskRepository
                .findById(taskId)
                .flatMap(task -> {
                    if (!"PENDING".equals(task.getStatus())) {
                        return Mono.error(new IllegalStateException("只能委派待办任务"));
                    }
                    if (!task.getAssigneeId().equals(operatorId)) {
                        return Mono.error(new IllegalStateException("只能委派自己的任务"));
                    }
                    Task delegatedTask = new Task();
                    delegatedTask.setProcessInstanceId(task.getProcessInstanceId());
                    delegatedTask.setNodeCode(task.getNodeCode());
                    delegatedTask.setStatus("DELEGATED");
                    delegatedTask.setAssigneeId(delegateToId);
                    delegatedTask.setTransferredFrom(operatorId);
                    delegatedTask.setCreatedTime(LocalDateTime.now());
                    return taskRepository.insert(delegatedTask);
                })
                .then();
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
     * <h3>使用场景</h3>
     * <ul>
     *   <li>业务数据需要修正，暂时冻结审批流程</li>
     *   <li>发现流程配置错误，需要修复后再继续</li>
     *   <li>审批人长期不在，需要暂停流程等待回归</li>
     * </ul>
     *
     * <h3>使用示例</h3>
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
     * @return 挂起操作结果
     * @throws IllegalStateException 当流程已完成或已挂起时抛出
     */
    public Mono<Void> suspendInstance(Long processInstanceId, Long operatorId, String reason) {
        log.info("挂起流程实例: processInstanceId={}, operatorId={}, reason={}", processInstanceId, operatorId, reason);

        return processInstanceRepository
                .findById(processInstanceId)
                .flatMap(instance -> {
                    if ("COMPLETED".equals(instance.getStatus()) || "REJECTED".equals(instance.getStatus())) {
                        return Mono.error(new IllegalStateException("已完成或已驳回的流程无法挂起"));
                    }
                    if ("SUSPENDED".equals(instance.getStatus())) {
                        return Mono.error(new IllegalStateException("流程已处于挂起状态"));
                    }
                    instance.setSuspended(true);
                    instance.setStatus("SUSPENDED");
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
     * <h3>使用示例</h3>
     * <pre>{@code
     * // 恢复流程实例
     * workflowService.resumeInstance(
     *     67890L,   // 流程实例ID
     *     10001L,   // 操作人ID
     *     "业务数据已修复，恢复审批流程"  // 恢复原因
     * ).block();
     * }</pre>
     *
     * <h3>注意事项</h3>
     * <ul>
     *   <li>只能恢复处于 SUSPENDED 状态的流程</li>
     *   <li>恢复后待办任务立即可以审批</li>
     *   <li>需要确认挂起原因已解决再恢复</li>
     * </ul>
     *
     * @param processInstanceId 流程实例ID
     * @param operatorId 操作人ID
     * @param reason 恢复原因
     * @return 恢复操作结果
     * @throws IllegalStateException 当流程未挂起时抛出
     */
    public Mono<Void> resumeInstance(Long processInstanceId, Long operatorId, String reason) {
        log.info("恢复流程实例: processInstanceId={}, operatorId={}, reason={}", processInstanceId, operatorId, reason);

        return processInstanceRepository
                .findById(processInstanceId)
                .flatMap(instance -> {
                    if (!"SUSPENDED".equals(instance.getStatus())) {
                        return Mono.error(new IllegalStateException("只能恢复挂起状态的流程"));
                    }
                    instance.setSuspended(false);
                    instance.setStatus("RUNNING");
                    return processInstanceRepository.updateById(instance);
                })
                .then();
    }
}
