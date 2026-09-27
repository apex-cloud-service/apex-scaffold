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
public class DefaultAuditLogService implements AuditLogService {

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
