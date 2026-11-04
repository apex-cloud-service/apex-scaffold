package com.apex.scaffold.integrationservice.interfaces.security;

import com.apex.scaffold.common.audit.AuditLogService;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 集成服务本地审计实现：仅输出白名单元数据，不记录凭据、正文或异常消息。
 *
 * <p>日志写入不代表已持久化、已送达远端或业务事务已提交。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@Slf4j
@Service
public class IntegrationAuditLogServiceImpl implements AuditLogService {

    @Override
    public Mono<Void> log(
            String userId,
            String tenantId,
            String action,
            String resource,
            String result,
            Map<String, Object> details) {
        return Mono.fromRunnable(() -> log.info(
                "INTEGRATION_AUDIT userId={} tenantId={} action={} resource={} result={} details={}",
                userId,
                tenantId,
                action,
                resource,
                result,
                details));
    }

    @Override
    public Mono<Void> logAuthEvent(
            String userId, String tenantId, String event, String ip, boolean success, String reason) {
        return log(userId, tenantId, event, "authentication", success ? "GRANTED" : "DENIED", Map.of());
    }

    @Override
    public Mono<Void> logPermissionCheck(
            String userId, String tenantId, String resource, String permission, boolean granted) {
        return log(
                userId,
                tenantId,
                "PERMISSION_CHECK",
                resource,
                granted ? "GRANTED" : "DENIED",
                Map.of("permission", permission == null ? "" : permission));
    }
}
