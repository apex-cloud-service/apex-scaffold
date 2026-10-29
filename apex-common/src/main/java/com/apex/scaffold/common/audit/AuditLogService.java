package com.apex.scaffold.common.audit;

import java.util.Map;
import reactor.core.publisher.Mono;

/**
 * 审计日志服务接口
 *
 * 用于记录系统中的安全相关操作
 * @author Apex Team
 * @since 1.2.0
 */
public interface AuditLogService {

    /**
     * 记录审计日志
     *
     * @param userId 用户ID
     * @param tenantId 租户ID
     * @param action 操作类型
     * @param resource 资源
     * @param result 操作结果
     * @param details 详细信息
     */
    Mono<Void> log(
            String userId, String tenantId, String action, String resource, String result, Map<String, Object> details);

    /**
     * 记录认证事件
     *
     * @param userId 用户ID
     * @param tenantId 租户ID
     * @param event 事件类型（如登录成功、登录失败、登出）
     * @param ip 客户端IP地址
     * @param success 认证是否成功
     * @param reason 事件原因说明（如失败原因）
     */
    Mono<Void> logAuthEvent(String userId, String tenantId, String event, String ip, boolean success, String reason);

    /**
     * 记录权限检查事件
     *
     * @param userId 用户ID
     * @param tenantId 租户ID
     * @param resource 被访问的资源
     * @param permission 所需的权限编码
     * @param granted 是否授权通过
     */
    Mono<Void> logPermissionCheck(String userId, String tenantId, String resource, String permission, boolean granted);
}
