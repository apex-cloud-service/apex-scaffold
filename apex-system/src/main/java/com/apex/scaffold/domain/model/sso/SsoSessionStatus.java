package com.apex.scaffold.domain.model.sso;

import lombok.Getter;

/**
 * SSO 会话状态枚举
 *
 * <p>定义 SSO 单点登录会话的生命周期状态。会话状态管理对于维护用户登录态、
 * 实现单点登出和会话过期控制至关重要。
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>{@link #ACTIVE} - 会话活跃，用户已登录且会话未过期</li>
 *   <li>{@link #EXPIRED} - 会话过期，超过有效期需要重新认证</li>
 *   <li>{@link #LOGOUT} - 会话已登出，用户主动登出或被强制登出</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <pre>
 * ┌─────────┐
 * │  登录   │
 * └────┬────┘
 *      │
 *      ▼
 * ┌─────────┐     超时      ┌─────────┐
 * │ ACTIVE  │──────────────▶│ EXPIRED │
 * └────┬────┘                └─────────┘
 *      │
 *      │ 登出
 *      │
 *      ▼
 * ┌─────────┐
 * │ LOGOUT  │
 * └─────────┘
 * </pre>
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>会话验证：检查会话是否处于活跃状态</li>
 *   <li>单点登出：将会话标记为 LOGOUT 状态</li>
 *   <li>会话清理：定期清理 EXPIRED 和 LOGOUT 状态的会话</li>
 *   <li>审计统计：按状态统计会话数量和分布</li>
 *   <li>并发控制：限制单用户的 ACTIVE 会话数量</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Getter
public enum SsoSessionStatus {
    /**
     * 会话活跃状态
     *
     * <p>特征：
     * <ul>
     *   <li>用户已通过 SSO 认证成功</li>
     *   <li>会话尚未过期（当前时间 < expire_time）</li>
     *   <li>用户未主动登出</li>
     *   <li>会话未被管理员强制终止</li>
     * </ul>
     *
     * <p>行为：
     * <ul>
     *   <li>允许访问受保护资源</li>
     *   <li>可以刷新会话延长有效期</li>
     *   <li>支持单点登出操作</li>
     * </ul>
     */
    ACTIVE("活跃", "会话有效，用户已登录"),

    /**
     * 会话过期状态
     *
     * <p>特征：
     * <ul>
     *   <li>会话超过配置的有效期（当前时间 >= expire_time）</li>
     *   <li>用户长时间未活动导致会话失效</li>
     *   <li>IdP 侧会话已过期但应用侧未及时更新</li>
     * </ul>
     *
     * <p>行为：
     * <ul>
     *   <li>拒绝访问受保护资源</li>
     *   <li>提示用户重新登录</li>
     *   <li>可被定期清理任务删除</li>
     * </ul>
     *
     * <p>过期原因：
     * <ul>
     *   <li>超过 expire_time 设置的绝对时间</li>
     *   <li>IdP 侧会话失效（如 SAML Session Index 失效）</li>
     *   <li>定时任务扫描并标记过期会话</li>
     * </ul>
     */
    EXPIRED("过期", "会话已过期，需要重新认证"),

    /**
     * 会话已登出状态
     *
     * <p>特征：
     * <ul>
     *   <li>用户主动发起登出操作</li>
     *   <li>管理员强制用户下线</li>
     *   <li>单点登出（SLO）触发</li>
     *   <li>安全策略强制登出（如密码修改、权限变更）</li>
     * </ul>
     *
     * <p>行为：
     * <ul>
     *   <li>拒绝访问受保护资源</li>
     *   <li>清理本地会话和令牌</li>
     *   <li>记录登出审计日志</li>
     *   <li>可被定期清理任务删除</li>
     * </ul>
     *
     * <p>登出类型：
     * <ul>
     *   <li>用户主动登出：点击登出按钮</li>
     *   <li>单点登出（SLO）：IdP 发起的全局登出</li>
     *   <li>强制登出：管理员操作或安全策略触发</li>
     *   <li>并发登录控制：新会话挤掉旧会话</li>
     * </ul>
     */
    LOGOUT("已登出", "会话已登出或被终止"),

    /**
     * 会话已撤销状态
     *
     * <p>特征：
     * <ul>
     *   <li>管理员主动撤销用户会话</li>
     *   <li>安全策略强制撤销（如检测到异常行为）</li>
     *   <li>会话管理功能中的强制下线操作</li>
     * </ul>
     *
     * <p>与 LOGOUT 的区别：
     * <ul>
     *   <li>LOGOUT 通常由用户主动触发或单点登出触发</li>
     *   <li>REVOKED 通常由管理员或系统安全策略触发</li>
     *   <li>REVOKED 通常需要记录更详细的审计信息</li>
     * </ul>
     */
    REVOKED("已撤销", "会话已被管理员或系统撤销");

    private final String displayName;

    private final String description;

    SsoSessionStatus(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    /**
     * 根据名称查找 SSO 会话状态
     *
     * @param name 状态名称（不区分大小写）
     * @return 匹配的会话状态
     * @throws IllegalArgumentException 如果找不到匹配的状态
     */
    public static SsoSessionStatus fromName(String name) {
        for (SsoSessionStatus status : values()) {
            if (status.name().equalsIgnoreCase(name)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unsupported SSO session status: " + name);
    }

    /**
     * 判断会话是否有效（可用于访问资源）
     *
     * @return true 表示会话有效
     */
    public boolean isValid() {
        return this == ACTIVE;
    }

    /**
     * 判断会话是否可被清理
     *
     * <p>EXPIRED、LOGOUT 和 REVOKED 状态的会话通常不再需要保留，
     * 可由定期清理任务删除以释放存储空间。
     *
     * @return true 表示会话可被清理
     */
    public boolean isCleanable() {
        return this == EXPIRED || this == LOGOUT || this == REVOKED;
    }
}
