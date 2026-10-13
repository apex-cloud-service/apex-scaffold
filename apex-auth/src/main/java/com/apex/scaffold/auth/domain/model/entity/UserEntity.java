package com.apex.scaffold.auth.domain.model.entity;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户认证实体
 *
 * <p>领域层用户实体，包含用户基本信息和认证相关属性。
 * 遵循DDD领域驱动设计原则，封装用户相关的业务逻辑和规则。
 *
 * <h2>核心职责</h2>
 * <ul>
 *   <li>用户基本信息管理</li>
 *   <li>认证凭证管理（密码哈希、OpenID等）</li>
 *   <li>账户状态管理（正常、禁用、锁定）</li>
 *   <li>登录失败次数追踪和账户锁定策略</li>
 * </ul>
 *
 * <h2>业务规则</h2>
 * <ul>
 *   <li>连续登录失败5次后自动锁定账户30分钟</li>
 *   <li>用户名在租户内必须唯一</li>
 *   <li>密码必须使用BCrypt或Argon2加密存储</li>
 *   <li>支持多租户隔离，每个用户属于唯一租户</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 2026-09-20
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserEntity {
    private static final int LOCKED_STATUS = 2;
    private static final int MAX_FAILED_ATTEMPTS = 5;

    /**
     * 用户ID
     *
     * <p>全局唯一标识符，使用雪花算法生成
     */
    private Long id;

    /**
     * 租户ID
     *
     * <p>用于多租户数据隔离，每个用户属于唯一租户
     */
    private String tenantId;

    /**
     * 用户名
     *
     * <p>租户内唯一，用于登录认证
     */
    private String username;

    /**
     * 邮箱地址
     *
     * <p>可选字段，用于邮箱登录和通知
     */
    private String email;

    /**
     * 手机号码
     *
     * <p>可选字段，用于短信登录和通知
     */
    private String phone;

    /**
     * 密码哈希值
     *
     * <p>使用BCrypt或Argon2加密存储，永远不存储明文密码
     */
    @lombok.ToString.Exclude
    private String passwordHash;

    /**
     * 第三方平台OpenID
     *
     * <p>用于微信、支付宝等第三方登录，关联外部用户身份
     */
    private String openId;

    /**
     * 统一账户ID（主应用 sys_user.id）
     *
     * <p>PC 管理端统一账户体系关联：登录签发的 JWT 携带本字段（claims.accountId），
     * 主应用据此装配 {@code sys_user} 身份与 RBAC 权限；认证中心不复制权限数据。
     * 可为空（尚未与主应用账户建立映射）。
     *
     * @since 3.3.0
     */
    private Long accountId;

    /**
     * 账户状态
     *
     * <p>枚举值：
     * <ul>
     *   <li>1 - 正常：账户可正常使用</li>
     *   <li>0 - 禁用：账户被管理员禁用</li>
     *   <li>2 - 锁定：因登录失败次数过多被自动锁定</li>
     *   <li>3 - 待激活：新注册用户待邮箱/手机激活</li>
     * </ul>
     */
    private Integer status;

    /**
     * 最后登录时间
     *
     * <p>记录用户最近一次成功登录的时间，用于统计和安全审计
     */
    private LocalDateTime lastLoginAt;

    /**
     * 最后登录IP地址
     *
     * <p>记录用户最近一次登录的IP，用于异常登录检测
     */
    private String lastLoginIp;

    /**
     * 连续登录失败次数
     *
     * <p>每次登录失败后递增，成功登录后重置为0
     * 达到阈值（默认5次）后触发账户锁定
     */
    private Integer failedLoginAttempts;

    /**
     * 账户锁定截止时间
     *
     * <p>账户被锁定后的解锁时间，超过此时间自动解锁
     * 默认锁定30分钟
     */
    private LocalDateTime lockedUntil;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    /**
     * 逻辑删除标记
     *
     * <p>0-未删除，1-已删除
     */
    private Integer deleted;

    /**
     * 检查账户是否正常
     *
     * <p>验证账户状态是否为正常（status=1）
     *
     * @return true-账户正常，false-账户异常
     */
    public boolean isActive() {
        return status != null && (status == 1 || (status == LOCKED_STATUS && lockedUntil != null && !isLocked()));
    }

    /**
     * 检查账户是否被锁定
     *
     * <p>验证账户是否处于锁定状态或锁定时间未到期
     *
     * @return true-账户已锁定，false-账户未锁定
     */
    public boolean isLocked() {
        if (lockedUntil != null && LocalDateTime.now().isBefore(lockedUntil)) {
            return true;
        }
        return status != null && status == LOCKED_STATUS && lockedUntil == null;
    }

    /**
     * 记录登录失败
     *
     * <p>每次登录失败后调用此方法，递增失败次数。
     * 如果失败次数达到阈值（5次），自动锁定账户30分钟。
     */
    public void recordLoginFailure() {
        if (failedLoginAttempts == null) {
            failedLoginAttempts = 0;
        }
        failedLoginAttempts++;

        // 达到阈值（5次）后锁定账户
        if (failedLoginAttempts >= MAX_FAILED_ATTEMPTS) {
            status = LOCKED_STATUS;
            lockedUntil = LocalDateTime.now().plusMinutes(30);
        }
    }

    /**
     * 记录登录成功
     *
     * <p>登录成功后调用此方法，重置失败次数并更新最后登录信息
     *
     * @param clientIp 客户端IP地址
     */
    public void recordLoginSuccess(String clientIp) {
        failedLoginAttempts = 0;
        lockedUntil = null;
        if (status != null && status == LOCKED_STATUS) {
            status = 1;
        }
        lastLoginAt = LocalDateTime.now();
        lastLoginIp = clientIp;
    }

    /**
     * 解锁账户
     *
     * <p>手动解锁账户，清除锁定状态和失败次数
     */
    public void unlock() {
        status = 1;
        failedLoginAttempts = 0;
        lockedUntil = null;
    }

    /**
     * 禁用账户
     *
     * <p>由管理员主动禁用账户
     */
    public void disable() {
        status = 0;
    }

    /**
     * 启用账户
     *
     * <p>由管理员启用已禁用的账户
     */
    public void enable() {
        status = 1;
    }
}
