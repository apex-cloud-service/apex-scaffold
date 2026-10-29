package com.apex.scaffold.workflowservice.interfaces.security;

import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 工作流操作的本地审计输出，使用固定标记区分普通应用日志。
 *
 * <p>仅接收切面筛选后的白名单元数据，不提供可靠持久化、远端送达或事务提交保证。
 * 不接收令牌、请求及响应正文、自由文本异常消息。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Slf4j
@Service
public class WorkflowAuditLogServiceImpl {
    /**
     * 在订阅时输出单条审计日志，不附加异常堆栈。
     *
     * @param userId 既有安全上下文中的用户 ID，缺失时不使用客户端值补全
     * @param tenantId 既有安全上下文中的租户 ID，缺失时不使用客户端值补全
     * @param action 操作注解声明的枚举名称
     * @param resource 不包含路径及查询参数的类名与方法名
     * @param result 响应就绪或调用错误标记，不表示业务成功
     * @param details 仅含操作声明、HTTP 方法和状态码、异常类型的元数据
     * @return 日志调用完成信号；输出失败时传播异常，无持久化确认语义
     */
    public Mono<Void> log(
            Long userId, String tenantId, String action, String resource, String result, Map<String, Object> details) {
        return Mono.fromRunnable(() -> log.info(
                "WORKFLOW_AUDIT userId={} tenantId={} action={} resource={} result={} details={}",
                userId,
                tenantId,
                action,
                resource,
                result,
                details));
    }
}
