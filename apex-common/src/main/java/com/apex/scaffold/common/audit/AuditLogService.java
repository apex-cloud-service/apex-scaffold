package com.apex.scaffold.common.audit;

import reactor.core.publisher.Mono;

import java.util.Map;

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
     * @return 完成信号
     */
    Mono<Void> log(String userId, String tenantId, String action, 
                   String resource, String result, Map<String, Object> details);

    /**
     * 记录认证事件
     */
    Mono<Void> logAuthEvent(String userId, String tenantId, String event, 
                           String ip, boolean success, String reason);

    /**
     * 记录权限检查事件
     */
    Mono<Void> logPermissionCheck(String userId, String tenantId, String resource, 
                                  String permission, boolean granted);
}
