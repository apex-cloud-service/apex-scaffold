package com.apex.scaffold.infrastructure.audit;

import com.apex.scaffold.common.audit.AuditLogService;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 审计日志服务默认实现
 *
 * 当前实现为简单的日志记录，生产环境应该持久化到数据库或审计系统
 * @author Apex Team
 * @since 1.2.0
 */
@Slf4j
@Service
@SuppressWarnings("PMD.ServiceOrDaoClassShouldEndWithImplRule")
public class DefaultAuditLogService implements AuditLogService {

    /**
     * 在订阅时将通用审计事件写入应用日志，不执行数据库持久化或事务提交等待。
     *
     * <p>调用方负责提供可信的用户、租户身份并预先脱敏详情；本实现直接输出传入值，
     * 不从上下文补全身份，也不切换订阅线程。
     *
     * @param userId 操作用户标识
     * @param tenantId 操作所属租户标识
     * @param action 操作名称
     * @param resource 被操作资源标识
     * @param result 操作结果描述
     * @param details 已脱敏的附加详情，可为 {@code null}
     * @return 日志调用完成时结束的信号，每次订阅均重新记录
     */
    @Override
    public Mono<Void> log(
            String userId,
            String tenantId,
            String action,
            String resource,
            String result,
            Map<String, Object> details) {
        return Mono.fromRunnable(() -> {
            log.info(
                    "AUDIT: userId={}, tenantId={}, action={}, resource={}, result={}, details={}, time={}",
                    userId,
                    tenantId,
                    action,
                    resource,
                    result,
                    details,
                    LocalDateTime.now());
        });
    }

    /**
     * 订阅时在当前订阅线程记录认证事件；用户、租户和原因均由调用方提供并脱敏。
     *
     * @param userId 认证涉及的用户标识
     * @param tenantId 可信租户标识
     * @param event 认证事件名称
     * @param ip 事件来源地址
     * @param success 认证是否成功
     * @param reason 已脱敏的结果原因
     * @return 日志调用完成信号，不代表审计数据已持久化
     */
    @Override
    public Mono<Void> logAuthEvent(
            String userId, String tenantId, String event, String ip, boolean success, String reason) {
        return Mono.fromRunnable(() -> {
            log.info(
                    "AUTH_EVENT: userId={}, tenantId={}, event={}, ip={}, success={}, reason={}, time={}",
                    userId,
                    tenantId,
                    event,
                    ip,
                    success,
                    reason,
                    LocalDateTime.now());
        });
    }

    /**
     * 订阅时记录权限检查结果，不执行权限判断或修改授权状态。
     *
     * @param userId 被检查的用户标识
     * @param tenantId 可信租户标识
     * @param resource 被检查的资源描述，传入日志调用但当前模板未为其配置占位符
     * @param permission 权限编码
     * @param granted 权限检查是否通过
     * @return 当前订阅线程上的日志调用完成信号，不代表持久化完成
     */
    @Override
    public Mono<Void> logPermissionCheck(
            String userId, String tenantId, String resource, String permission, boolean granted) {
        return Mono.fromRunnable(() -> {
            log.info(
                    "PERMISSION_CHECK: userId={}, tenantId={}, resource=, permission={}, granted={}, time={}",
                    userId,
                    tenantId,
                    resource,
                    permission,
                    granted,
                    LocalDateTime.now());
        });
    }
}
