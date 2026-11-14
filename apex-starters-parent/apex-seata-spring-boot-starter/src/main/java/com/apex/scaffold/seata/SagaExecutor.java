package com.apex.scaffold.seata;

import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import org.apache.seata.core.context.RootContext;
import org.apache.seata.saga.engine.StateMachineEngine;
import org.apache.seata.saga.statelang.domain.StateMachineInstance;
import reactor.core.publisher.Mono;

/**
 * SAGA 状态机的响应式门面：阻塞引擎调用统一隔离到 jdbc 池，返回脱敏后的实例快照。
 *
 * <p>引擎方法为同步阻塞实现（内部经 TM 与 TC 编排），不得在请求线程直接调用；
 * 状态机定义与存储三表由所属服务维护，本类不承载编排定义。
 * @author Apex Team
 * @since 1.3.1
 */
@lombok.RequiredArgsConstructor
public final class SagaExecutor {
    private final StateMachineEngine engine;

    /**
     * 按状态机名称启动新实例；businessKey 与租户组合须唯一（存储表唯一键约束）。
     *
     * @param stateMachineName 已注册的状态机名称
     * @param tenantId 状态机所属租户标识
     * @param businessKey 业务唯一键
     * @param startParams 状态机启动参数
     * @return 不含启动参数原文的实例状态快照
     * @implSpec 每次订阅均在 JDBC 专用调度器上调用同步引擎，不自动重试。
     */
    public Mono<Map<String, Object>> start(
            String stateMachineName, String tenantId, String businessKey, Map<String, Object> startParams) {
        return bindXid(() -> engine.startWithBusinessKey(stateMachineName, tenantId, businessKey, startParams))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .map(SagaExecutor::snapshot);
    }

    /**
     * 基于上次失败实例向前恢复推进。
     *
     * @param businessKey 待恢复实例的业务键
     * @param startParams 恢复执行所需的参数
     * @return 不含启动参数原文的实例状态快照
     * @implSpec 每次订阅均在 JDBC 专用调度器上调用同步引擎，不自动重试。
     */
    public Mono<Map<String, Object>> forward(String businessKey, Map<String, Object> startParams) {
        return bindXid(() -> engine.forward(businessKey, startParams))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .map(SagaExecutor::snapshot);
    }

    /**
     * 手动触发补偿回滚。
     *
     * @param businessKey 待补偿实例的业务键
     * @param startParams 补偿执行所需的参数
     * @return 不含启动参数原文的实例状态快照
     * @implSpec 每次订阅均在 JDBC 专用调度器上调用同步引擎，不自动重试。
     */
    public Mono<Map<String, Object>> compensate(String businessKey, Map<String, Object> startParams) {
        return bindXid(() -> engine.compensate(businessKey, startParams))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .map(SagaExecutor::snapshot);
    }

    /**
     * 按 SAGA 实例 ID 从存储重新加载编排状态。
     *
     * @param instanceId SAGA 实例标识
     * @return 不含启动参数原文的实例状态快照；引擎未返回实例时为空 Mono
     * @implSpec 每次订阅均在 JDBC 专用调度器上重新加载实例，不自动重试。
     */
    public Mono<Map<String, Object>> getInstance(String instanceId) {
        return Mono.fromCallable(() -> engine.reloadStateMachineInstance(instanceId))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .map(instance -> instance == null ? Map.of() : snapshot(instance));
    }

    /**
     * 将 Reactor Context 中受信的外层 XID 绑定到当前 JDBC 线程的 RootContext，
     * 使状态机引擎以参与者身份加入外层全局事务；无外层 XID 时由引擎自行开启事务。
     */
    private <T> Mono<T> bindXid(Callable<T> action) {
        return Mono.deferContextual(context -> Mono.fromCallable(() -> {
            String xid = context.getOrDefault(SeataTransactionExecutor.XID_CONTEXT_KEY, null);
            String previous = RootContext.getXID();
            if (previous != null && xid != null && !previous.equals(xid)) {
                throw new IllegalStateException("SAGA 引擎线程 XID 与请求事务不一致");
            }
            boolean bound = false;
            try {
                if (previous == null && xid != null) {
                    RootContext.bind(xid);
                    bound = true;
                }
                return action.call();
            } finally {
                if (bound) {
                    RootContext.unbind();
                }
                if (previous != null && !bound) {
                    RootContext.bind(previous);
                }
            }
        }));
    }

    /** 仅导出非敏感编排字段，参数原文不入快照，避免状态机入参泄漏到响应或日志。 */
    private static Map<String, Object> snapshot(StateMachineInstance instance) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", instance.getId());
        snapshot.put("machineId", instance.getMachineId());
        snapshot.put("businessKey", instance.getBusinessKey());
        snapshot.put("tenantId", instance.getTenantId());
        snapshot.put(
                "status",
                instance.getStatus() == null ? null : instance.getStatus().name());
        snapshot.put(
                "compensationStatus",
                instance.getCompensationStatus() == null
                        ? null
                        : instance.getCompensationStatus().name());
        snapshot.put("startedAt", instance.getGmtStarted());
        snapshot.put("endAt", instance.getGmtEnd());
        return snapshot;
    }
}
