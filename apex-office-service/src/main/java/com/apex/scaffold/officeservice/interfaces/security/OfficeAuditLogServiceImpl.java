package com.apex.scaffold.officeservice.interfaces.security;

import com.apex.scaffold.common.audit.AuditLogService;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 可按用户、租户、操作及结果检索的本地审计日志；不提供可靠持久化保证。
 *
 * <p>调用者只传入白名单元数据，不传凭据、正文或未经处理的异常消息。
 * 日志在订阅时写入当前日志后端，完成信号不代表已持久化、已送达远端或业务事务已提交。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Slf4j
@Service
public class OfficeAuditLogServiceImpl implements AuditLogService {
    /**
     * 在订阅时写入单条带固定标记的本地审计日志。
     *
     * @param userId 可信用户 ID；认证失败事件可缺失
     * @param tenantId 可信租户 ID；认证失败事件可缺失
     * @param action 受控的操作或事件类型
     * @param resource 不含请求路径参数或正文的资源标识
     * @param result 受控的结果标识，不应将响应生成解释为业务成功
     * @param details 调用方筛选后的白名单元数据，不得包含敏感数据
     * @return 日志调用完成信号；日志调用抛出的异常通过错误信号传播，不重试或吞错
     */
    @Override
    public Mono<Void> log(
            String userId,
            String tenantId,
            String action,
            String resource,
            String result,
            Map<String, Object> details) {
        return Mono.fromRunnable(() -> log.info(
                "OFFICE_AUDIT userId={} tenantId={} action={} resource={} result={} details={}",
                userId,
                tenantId,
                action,
                resource,
                result,
                details));
    }

    /**
     * 记录认证结果，主动省略客户端 IP 和自由文本原因，避免采集个人数据及敏感错误。
     *
     * @param userId 可信用户 ID，未建立身份时可缺失
     * @param tenantId 可信租户 ID，未建立身份时可缺失
     * @param event 受控的认证事件类型
     * @param ip 契约传入的客户端 IP，本实现不记录
     * @param success 认证是否成功，映射为 GRANTED 或 DENIED
     * @param reason 契约传入的认证原因，本实现不记录
     * @return 本地审计完成信号；日志调用失败时传播错误，无持久化确认语义
     */
    @Override
    public Mono<Void> logAuthEvent(
            String userId, String tenantId, String event, String ip, boolean success, String reason) {
        return log(userId, tenantId, event, "authentication", success ? "GRANTED" : "DENIED", Map.of());
    }

    /**
     * 记录精确权限码对应的授权判断，不写入令牌、角色快照或请求正文。
     *
     * @param userId 已验证的用户 ID
     * @param tenantId 已验证的租户 ID
     * @param resource 受控的资源标识
     * @param permission 非 null 的声明权限码
     * @param granted 是否授权通过，映射为 GRANTED 或 DENIED
     * @return 本地审计完成信号；日志调用失败时传播错误，无持久化确认语义
     * @throws NullPointerException permission 为 null，无法构建不可变元数据映射
     */
    @Override
    public Mono<Void> logPermissionCheck(
            String userId, String tenantId, String resource, String permission, boolean granted) {
        return log(
                userId,
                tenantId,
                "PERMISSION_CHECK",
                resource,
                granted ? "GRANTED" : "DENIED",
                Map.of("permission", permission));
    }
}
