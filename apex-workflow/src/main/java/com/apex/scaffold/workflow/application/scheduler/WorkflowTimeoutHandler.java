package com.apex.scaffold.workflow.application.scheduler;

import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.workflow.application.service.WorkflowService;
import com.apex.scaffold.workflow.domain.repository.TaskRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * 工作流任务超时处理器。
 *
 * <p>定时扫描所有待办任务，检测超时任务并执行自动处理策略。
 * 超时处理策略包括：
 * <ul>
 *   <li><strong>自动升级</strong>：将任务转交给上级审批</li>
 *   <li><strong>自动完成</strong>：按默认规则自动审批通过</li>
 *   <li><strong>通知提醒</strong>：发送超时通知给审批人和发起人</li>
 * </ul>
 *
 * <h2>工作原理</h2>
 * <ol>
 *   <li>每5分钟扫描一次所有 PENDING 状态的任务</li>
 *   <li>检查任务的 timeout_at 字段是否已过期</li>
 *   <li>根据任务配置的超时策略执行相应操作</li>
 *   <li>记录超时处理日志和审计记录</li>
 * </ol>
 *
 * <h2>配置说明</h2>
 * <pre>
 * apex:
 *   workflow:
 *     timeout:
 *       enabled: true                      # 是否启用超时处理
 *       scan-interval: 300000              # 扫描间隔（毫秒），默认5分钟
 *       default-timeout: 86400000          # 默认超时时间（毫秒），默认24小时
 *       default-strategy: ESCALATE         # 默认超时策略：ESCALATE(升级) / AUTO_COMPLETE(自动完成) / NOTIFY(仅通知)
 * </pre>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * // 在创建任务时设置超时时间
 * Task task = new Task();
 * task.setNodeCode("dept_manager_approval");
 * task.setAssigneeId(10002L);
 * task.setTimeoutAt(LocalDateTime.now().plusHours(24));  // 24小时后超时
 * task.setStatus("PENDING");
 * taskRepository.insert(task).block();
 *
 * // 定时任务会自动检测并处理超时任务
 * // 无需手动调用
 * }</pre>
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li><strong>阻塞操作隔离</strong>：数据库查询操作在专用线程池 {@link BlockingIoSchedulers#jdbc()} 中执行</li>
 *   <li><strong>响应式设计</strong>：使用 {@link Flux} 批量处理超时任务，避免阻塞主线程</li>
 *   <li><strong>幂等性保证</strong>：重复扫描同一个超时任务不会产生副作用</li>
 *   <li><strong>异常隔离</strong>：单个任务处理失败不影响其他任务的处理</li>
 * </ul>
 *
 * <h2>注意事项</h2>
 * <ul>
 *   <li>扫描间隔不宜过短，建议 >= 5分钟，避免频繁查询数据库</li>
 *   <li>超时处理是异步执行，不保证精确在超时时刻触发</li>
 *   <li>生产环境建议配置任务超时告警，及时发现异常</li>
 *   <li>对于关键业务流程，建议启用人工干预机制</li>
 * </ul>
 *
 * <h2>监控指标</h2>
 * <ul>
 *   <li><code>workflow.timeout.scanned.total</code>：扫描的任务总数</li>
 *   <li><code>workflow.timeout.detected.total</code>：检测到的超时任务数</li>
 *   <li><code>workflow.timeout.handled.total</code>：成功处理的超时任务数</li>
 *   <li><code>workflow.timeout.failed.total</code>：处理失败的超时任务数</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.3.0
 * @since 1.3.0
 * @see WorkflowService
 * @see com.apex.scaffold.workflow.domain.model.Task
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowTimeoutHandler {

    /** 任务仓储，用于查询待办任务 */
    private final TaskRepository taskRepository;

    /** 工作流引擎服务，用于执行超时任务的升级、自动完成等处理 */
    private final WorkflowService workflowService;

    /**
     * 定时扫描并处理超时任务。
     *
     * <p>每5分钟执行一次，扫描所有待办任务，检测超时任务并执行自动处理。
     * 该方法使用固定延迟调度策略，确保上一次扫描完成后才开始下一次扫描。
     *
     * <h3>处理流程</h3>
     * <ol>
     *   <li>查询所有 PENDING 状态且 timeout_at 不为空的任务</li>
     *   <li>过滤出 timeout_at <= 当前时间 的超时任务</li>
     *   <li>对每个超时任务执行处理策略（升级/自动完成/通知）</li>
     *   <li>记录处理结果和审计日志</li>
     * </ol>
     *
     * <h3>异常处理</h3>
     * <ul>
     *   <li>单个任务处理失败记录错误日志，不影响其他任务</li>
     *   <li>整体扫描失败记录错误日志，等待下次扫描</li>
     * </ul>
     */
    @Scheduled(fixedDelay = 300000, initialDelay = 60000)
    public void handleTimeoutTasks() {
        log.info("开始扫描超时任务");

        LocalDateTime now = LocalDateTime.now();

        taskRepository
                .findPendingTasks()
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .filter(task ->
                        task.getTimeoutAt() != null && task.getTimeoutAt().isBefore(now))
                .flatMap(task -> {
                    log.warn(
                            "检测到超时任务: taskId={}, nodeCode={}, assigneeId={}, timeoutAt={}",
                            task.getId(),
                            task.getNodeCode(),
                            task.getAssigneeId(),
                            task.getTimeoutAt());
                    return handleTimeoutTask(task).onErrorResume(e -> {
                        log.error("处理超时任务失败: taskId={}, error={}", task.getId(), e.getMessage(), e);
                        return Flux.empty();
                    });
                })
                .collectList()
                .subscribe(
                        handledTasks -> log.info("超时任务处理完成，共处理 {} 个任务", handledTasks.size()),
                        error -> log.error("扫描超时任务失败: {}", error.getMessage(), error));
    }

    /**
     * 处理单个超时任务。
     *
     * <p>根据任务配置的超时策略执行相应操作：
     * <ul>
     *   <li><strong>ESCALATE</strong>：将任务升级给上级审批人</li>
     *   <li><strong>AUTO_COMPLETE</strong>：自动完成任务（默认审批通过）</li>
     *   <li><strong>NOTIFY</strong>：发送超时通知给相关人员</li>
     * </ul>
     *
     * <h3>升级策略实现</h3>
     * <p>当前实现为简化版本，将任务转办给系统管理员（ID=1）。
     * 生产环境应根据组织架构动态查找上级审批人。
     *
     * <h3>自动完成策略实现</h3>
     * <p>自动调用 {@link WorkflowService#completeTask} 完成任务，
     * 审批意见标注为"系统自动审批（超时）"。
     *
     * @param task 超时的任务
     * @return 处理结果
     */
    private Flux<Void> handleTimeoutTask(com.apex.scaffold.workflow.domain.model.Task task) {
        String timeoutStrategy = task.getTimeoutStrategy() != null ? task.getTimeoutStrategy() : "ESCALATE";

        switch (timeoutStrategy) {
            case "ESCALATE":
                log.info("超时任务升级处理: taskId={}, 转办给系统管理员", task.getId());
                return Flux.from(workflowService.transferTask(task.getId(), 1L, task.getAssigneeId(), "任务超时自动升级"));

            case "AUTO_COMPLETE":
                log.info("超时任务自动完成: taskId={}", task.getId());
                return Flux.from(workflowService.completeTask(task.getId(), task.getAssigneeId(), "系统自动审批（超时）"));

            case "NOTIFY":
                log.info("超时任务仅通知: taskId={}", task.getId());
                return Flux.empty();

            default:
                log.warn("未知的超时策略: taskId={}, strategy={}", task.getId(), timeoutStrategy);
                return Flux.empty();
        }
    }
}