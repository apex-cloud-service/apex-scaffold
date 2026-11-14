package com.apex.scaffold.knowledgeservice.interfaces.security;

import com.apex.scaffold.common.audit.AuditLogService;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 知识库服务本地审计日志（不落库，不提供可靠持久化保证）。
 *
 * <p>调用者只传入白名单元数据，不传凭据、正文或未经处理的异常消息。
 * 日志在订阅时写入当前日志后端，完成信号不代表已持久化或已送达远端。
 * 知识库管理写操作（空间/文档）经 {@code KnowledgeSecurityAspect} 使用本实现；
 * 问答为读操作不产生审计。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@Slf4j
@Service
public class KnowledgeAuditLogServiceImpl implements AuditLogService {

    /**
     * 在订阅时写入单条带固定标记的本地审计日志。
     *
     * @param userId 可信用户 ID；认证失败事件可缺失
     * @param tenantId 可信租户 ID；认证失败事件可缺失
     * @param action 受控的操作或事件类型
     * @param resource 不含请求路径参数或正文的资源标识
     * @param result 受控的结果标识
     * @param details 调用方筛选后的白名单元数据，不得包含敏感数据
     * @return 完成信号
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
                "KNOWLEDGE_AUDIT userId={} tenantId={} action={} resource={} result={} details={}",
                userId,
                tenantId,
                action,
                resource,
                result,
                details));
    }

    /**
     * 认证事件：主动省略客户端 IP 和自由文本原因，避免采集个人数据及敏感错误。
     *
     * @param userId 可信用户 ID，未建立身份时可缺失
     * @param tenantId 可信租户 ID，未建立身份时可缺失
     * @param event 受控的认证事件类型
     * @param ip 契约传入的客户端 IP，本实现不记录
     * @param success 认证是否成功
     * @param reason 契约传入的认证原因，本实现不记录
     * @return 完成信号
     */
    @Override
    public Mono<Void> logAuthEvent(
            String userId, String tenantId, String event, String ip, boolean success, String reason) {
        return Mono.fromRunnable(() -> log.info(
                "KNOWLEDGE_AUDIT userId={} tenantId={} event={} result={}",
                userId,
                tenantId,
                event,
                success ? "GRANTED" : "DENIED"));
    }

    /**
     * 权限检查事件。
     *
     * @param userId 可信用户 ID
     * @param tenantId 可信租户 ID
     * @param resource 被访问的资源
     * @param permission 所需的权限编码
     * @param granted 是否授权通过
     * @return 完成信号
     */
    @Override
    public Mono<Void> logPermissionCheck(
            String userId, String tenantId, String resource, String permission, boolean granted) {
        return Mono.fromRunnable(() -> log.info(
                "KNOWLEDGE_AUDIT userId={} tenantId={} resource={} permission={} result={}",
                userId,
                tenantId,
                resource,
                permission,
                granted ? "GRANTED" : "DENIED"));
    }
}
