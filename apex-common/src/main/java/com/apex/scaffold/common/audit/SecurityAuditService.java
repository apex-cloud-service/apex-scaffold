package com.apex.scaffold.common.audit;

import java.util.Map;
import reactor.core.publisher.Mono;

/**
 * 安全审计服务（领域端口）
 *
 * <p>统一记录所有安全相关事件，包括认证、授权、Token操作、安全威胁等。
 * 下沉到 apex-common，供 apex-auth-center 与 apex-application 共用同一份接口定义，
 * 避免主应用模块反向依赖本应独立部署的 apex-auth-center 内部实现类。
 *
 * <h2>审计事件类型</h2>
 * <ul>
 *   <li>认证事件：登录成功/失败、登出、密码修改</li>
 *   <li>Token事件：签发、刷新、撤销、验证失败</li>
 *   <li>授权事件：权限检查通过/失败、角色变更</li>
 *   <li>安全威胁：暴力破解、Token重放、异常访问</li>
 * </ul>
 *
 * <h2>审计日志字段</h2>
 * <ul>
 *   <li>时间戳：精确到毫秒</li>
 *   <li>事件类型：标准化的事件分类</li>
 *   <li>用户信息：userId、username、tenantId</li>
 *   <li>客户端信息：IP、User-Agent、设备ID</li>
 *   <li>操作详情：资源、动作、结果、失败原因</li>
 *   <li>安全上下文：会话ID、请求ID、Token JTI</li>
 * </ul>
 *
 * <h2>模块下沉说明</h2>
 * <p>本接口原位于 {@code apex-auth-center} 模块内部，2026-09-30 下沉至 {@code apex-common}，
 * 作为跨模块共享的安全审计端口，供 {@code apex-auth-center}（认证中心自身安全事件）与
 * {@code apex-application}（如 OAuth2 客户端密钥轮换等场景）共用同一份接口定义，
 * 从而消除 {@code apex-application} 反向依赖 {@code apex-auth-center} 内部实现类的架构耦合。
 *
 * <p>本接口所有方法均为“发出后不等待结果”的审计记录语义：返回 {@code Mono<Void>} 的方法在订阅后
 * 完成审计事件落地（当前实现为结构化日志输出）即视为完成；返回 {@code void} 的方法（密钥相关事件）
 * 为同步调用，调用方无需关心审计记录是否真正落盘，审计失败不应影响主业务流程。
 *
 * @author Apex Security Team
 * @version 1.0.0
 * @since 2026-09-25
 */
public interface SecurityAuditService {

    /**
     * 记录登录成功事件
     *
     * @param userId 用户ID
     * @param username 用户名
     * @param tenantId 租户ID
     * @param clientIp 客户端IP
     * @param userAgent 浏览器User-Agent
     * @param deviceId 设备ID
     * @return 完成信号
     */
    Mono<Void> logLoginSuccess(
            Long userId, String username, String tenantId, String clientIp, String userAgent, String deviceId);

    /**
     * 记录登录失败事件
     *
     * @param username 用户名
     * @param tenantId 租户ID
     * @param clientIp 客户端IP
     * @param userAgent 浏览器User-Agent
     * @param reason 失败原因
     * @return 完成信号
     */
    Mono<Void> logLoginFailure(String username, String tenantId, String clientIp, String userAgent, String reason);

    /**
     * 记录登出事件
     *
     * @param userId 用户ID
     * @param username 用户名
     * @param tenantId 租户ID
     * @param clientIp 客户端IP
     * @return 完成信号
     */
    Mono<Void> logLogout(Long userId, String username, String tenantId, String clientIp);

    /**
     * 记录Token签发事件
     *
     * @param userId 用户ID
     * @param tenantId 租户ID
     * @param clientIp 客户端IP
     * @param deviceId 设备ID
     * @param tokenType Token类型（ACCESS/REFRESH）
     * @return 完成信号
     */
    Mono<Void> logTokenIssued(Long userId, String tenantId, String clientIp, String deviceId, String tokenType);

    /**
     * 记录Token刷新事件
     *
     * @param userId 用户ID
     * @param tenantId 租户ID
     * @param clientIp 客户端IP
     * @param jti 旧Token的JTI
     * @return 完成信号
     */
    Mono<Void> logTokenRefreshed(Long userId, String tenantId, String clientIp, String jti);

    /**
     * 记录Token撤销事件
     *
     * @param userId 用户ID
     * @param username 用户名
     * @param tenantId 租户ID
     * @param jti 被撤销Token的JTI
     * @param reason 撤销原因
     * @param clientIp 客户端IP
     * @return 完成信号
     */
    Mono<Void> logTokenRevoked(
            Long userId, String username, String tenantId, String jti, String reason, String clientIp);

    /**
     * 记录Token验证失败事件
     *
     * @param clientIp 客户端IP
     * @param reason 失败原因
     * @param tokenHint Token提示信息（脱敏后，用于排查）
     * @return 完成信号
     */
    Mono<Void> logTokenValidationFailure(String clientIp, String reason, String tokenHint);

    /**
     * 记录Token重放攻击事件
     *
     * @param userId 用户ID
     * @param tenantId 租户ID
     * @param clientIp 客户端IP
     * @param jti 被重放Token的JTI
     * @return 完成信号
     */
    Mono<Void> logTokenReplayAttack(Long userId, String tenantId, String clientIp, String jti);

    /**
     * 记录暴力破解攻击事件
     *
     * @param username 用户名
     * @param clientIp 客户端IP
     * @param attemptCount 尝试次数
     * @return 完成信号
     */
    Mono<Void> logBruteForceAttack(String username, String clientIp, int attemptCount);

    /**
     * 记录权限检查失败事件
     *
     * @param userId 用户ID
     * @param username 用户名
     * @param tenantId 租户ID
     * @param resource 被访问资源
     * @param action 执行动作
     * @param clientIp 客户端IP
     * @return 完成信号
     */
    Mono<Void> logPermissionDenied(
            Long userId, String username, String tenantId, String resource, String action, String clientIp);

    /**
     * 记录敏感操作事件
     *
     * @param userId 用户ID
     * @param username 用户名
     * @param tenantId 租户ID
     * @param operation 操作名称
     * @param resource 操作资源
     * @param clientIp 客户端IP
     * @return 完成信号
     */
    Mono<Void> logSensitiveOperation(
            Long userId, String username, String tenantId, String operation, String resource, String clientIp);

    /**
     * 记录密码修改事件
     *
     * @param userId 用户ID
     * @param username 用户名
     * @param tenantId 租户ID
     * @param clientIp 客户端IP
     * @return 完成信号
     */
    Mono<Void> logPasswordChanged(Long userId, String username, String tenantId, String clientIp);

    /**
     * 记录账户锁定事件
     *
     * @param userId 用户ID
     * @param username 用户名
     * @param tenantId 租户ID
     * @param reason 锁定原因
     * @return 完成信号
     */
    Mono<Void> logAccountLocked(Long userId, String username, String tenantId, String reason);

    /**
     * 记录账户解锁事件
     *
     * @param userId 用户ID
     * @param username 用户名
     * @param tenantId 租户ID
     * @param operator 操作人
     * @return 完成信号
     */
    Mono<Void> logAccountUnlocked(Long userId, String username, String tenantId, String operator);

    /**
     * 记录密钥吊销事件
     *
     * @param keyVersion 密钥版本
     * @param reason 吊销原因
     * @param operatorUsername 操作人用户名
     */
    void logKeyRevocation(String keyVersion, String reason, String operatorUsername);

    /**
     * 记录密钥轮换事件
     *
     * @param oldVersion 旧密钥版本
     * @param newVersion 新密钥版本
     * @param reason 轮换原因
     * @param operatorUsername 操作人用户名
     */
    void logKeyRotation(String oldVersion, String newVersion, String reason, String operatorUsername);

    /**
     * 记录密钥轮换失败事件
     *
     * @param keyVersion 密钥版本
     * @param reason 失败原因
     * @param operatorUsername 操作人用户名
     */
    void logKeyRotationFailure(String keyVersion, String reason, String operatorUsername);

    /**
     * 记录密钥生成事件
     *
     * @param keyVersion 密钥版本
     * @param reason 生成原因
     * @param operatorUsername 操作人用户名
     */
    void logKeyGeneration(String keyVersion, String reason, String operatorUsername);

    /**
     * 记录通用安全事件
     *
     * @param eventType 事件类型
     * @param message 消息
     * @param userId 用户ID
     * @param username 用户名
     * @param tenantId 租户ID
     * @param reason 原因
     * @return 完成信号
     */
    Mono<Void> logSecurityEvent(
            String eventType, String message, Long userId, String username, String tenantId, String reason);

    /**
     * 记录通用安全事件（带严重级别、成功标记和详情）
     *
     * @param eventType 事件类型
     * @param message 消息
     * @param userId 用户ID（字符串形式）
     * @param username 用户名
     * @param severity 严重级别（LOW/MEDIUM/HIGH/CRITICAL）
     * @param success 是否成功
     * @param details 详情Map
     * @param reason 原因
     * @return 完成信号
     */
    Mono<Void> logSecurityEvent(
            String eventType,
            String message,
            String userId,
            String username,
            String severity,
            boolean success,
            Map<String, Object> details,
            String reason);

    /**
     * 记录客户端密钥轮换事件
     *
     * @param clientId 客户端ID
     * @param operatorUserId 操作人ID
     * @param clientIp 客户端IP
     * @param details 详情Map
     * @return 完成信号
     */
    Mono<Void> logClientSecretRotation(
            String clientId, String operatorUserId, String clientIp, Map<String, Object> details);

    /**
     * 记录Token轮换事件
     *
     * @param userId 用户ID
     * @param tenantId 租户ID
     * @param tokenFamily Token家族ID
     * @param oldJti 旧Token的JTI
     * @param newJti 新Token的JTI
     * @param clientIp 客户端IP
     * @return 完成信号
     */
    Mono<Void> logTokenRotation(
            Long userId, String tenantId, String tokenFamily, String oldJti, String newJti, String clientIp);
}
