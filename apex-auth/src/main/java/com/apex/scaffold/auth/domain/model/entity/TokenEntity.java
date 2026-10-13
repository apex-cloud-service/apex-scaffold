package com.apex.scaffold.auth.domain.model.entity;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Token实体
 *
 * <p>领域层Token实体，管理访问令牌和刷新令牌的生命周期。
 * 支持JWT和Opaque Token两种格式。
 *
 * <h2>核心职责</h2>
 * <ul>
 *   <li>Token存储和管理</li>
 *   <li>Token过期检测</li>
 *   <li>Token撤销管理</li>
 *   <li>设备和客户端信息追踪</li>
 * </ul>
 *
 * <h2>业务规则</h2>
 * <ul>
 *   <li>访问令牌默认有效期2小时</li>
 *   <li>刷新令牌默认有效期7天</li>
 *   <li>已撤销的Token不能再次使用</li>
 *   <li>过期的Token自动失效</li>
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
public class TokenEntity {

    /**
     * Token ID
     */
    private Long id;

    /**
     * 租户ID
     */
    private String tenantId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * Token类型
     *
     * <p>枚举值：
     * <ul>
     *   <li>ACCESS - 访问令牌</li>
     *   <li>REFRESH - 刷新令牌</li>
     * </ul>
     */
    private String tokenType;

    /**
     * Token值
     *
     * <p>实际的JWT字符串或Opaque Token
     */
    private String tokenValue;

    /**
     * JWT ID
     *
     * <p>JWT的唯一标识符（jti声明），用于Token撤销和黑名单
     */
    private String jti;

    /**
     * 过期时间
     */
    private LocalDateTime expiresAt;

    /**
     * 撤销标记
     *
     * <p>0-正常，1-已撤销
     */
    private Integer revoked;

    /**
     * 撤销时间
     */
    private LocalDateTime revokedAt;

    /**
     * 撤销操作人
     *
     * <p>可能的值：
     * <ul>
     *   <li>userId - 用户主动撤销（登出）</li>
     *   <li>system - 系统自动撤销（安全策略触发）</li>
     *   <li>admin:{userId} - 管理员撤销</li>
     * </ul>
     */
    private String revokedBy;

    /**
     * 撤销原因
     *
     * <p>枚举值：
     * <ul>
     *   <li>expired - Token过期</li>
     *   <li>logout - 用户登出</li>
     *   <li>security - 安全策略触发（如检测到异常）</li>
     *   <li>manual - 手动撤销</li>
     *   <li>password_changed - 密码修改</li>
     *   <li>account_locked - 账户锁定</li>
     *   <li>suspicious_activity - 可疑活动</li>
     * </ul>
     */
    private String revokeReason;

    /**
     * 最后使用时间
     *
     * <p>用于检测Token使用模式和异常行为
     */
    private LocalDateTime lastUsedAt;

    /**
     * Token使用次数
     *
     * <p>用于检测Token重放攻击和异常使用模式
     */
    private Integer useCount;

    /**
     * 设备ID
     */
    private String deviceId;

    /**
     * 客户端IP地址
     */
    private String clientIp;

    /**
     * User-Agent
     */
    private String userAgent;

    /**
     * Token家族ID
     *
     * <p>用于Refresh Token轮换追踪。
     * 同一个登录会话产生的所有Refresh Token共享同一个Family ID。
     * 当检测到Family内的Token被重复使用时，撤销整个Family以防止Token窃取。
     */
    private String tokenFamily;

    /**
     * 前一个Token的JTI
     *
     * <p>用于追踪Token轮换链。
     * 当Refresh Token被轮换时，新Token记录旧Token的JTI，形成链式追踪。
     */
    private String previousJti;

    /**
     * Token轮换计数
     *
     * <p>记录该Token在Family中经历了多少次轮换。
     * 用于检测异常轮换行为和限制轮换次数。
     */
    private Integer rotationCount;

    /**
     * Token哈希值
     *
     * <p>存储Token值的SHA-256哈希，用于：
     * <ul>
     *   <li>快速查找Token而无需存储明文</li>
     *   <li>提高数据库查询性能（索引）</li>
     *   <li>增强安全性，避免泄露原始Token</li>
     * </ul>
     */
    private String tokenHash;

    /**
     * Token加密IV（初始化向量）
     *
     * <p>存储AES-GCM加密Token时使用的IV（Base64编码）。
     * 与tokenValue配合使用以解密Token。
     */
    private String tokenIv;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 检查Token是否已过期
     *
     * @return true-已过期，false-未过期
     */
    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }

    /**
     * 检查Token是否已撤销
     *
     * @return true-已撤销，false-未撤销
     */
    public boolean isRevoked() {
        return revoked != null && revoked == 1;
    }

    /**
     * 检查Token是否有效
     *
     * <p>Token有效需要同时满足：未过期、未撤销
     *
     * @return true-有效，false-无效
     */
    public boolean isValid() {
        return !isExpired() && !isRevoked();
    }

    /**
     * 撤销Token
     *
     * <p>将Token标记为已撤销，撤销后不可恢复
     */
    public void revoke() {
        this.revoked = 1;
        this.revokedAt = LocalDateTime.now();
        this.revokedBy = "system";
        this.revokeReason = "manual";
    }

    /**
     * 撤销Token（带原因和操作人）
     *
     * @param revokedBy 撤销操作人
     * @param reason 撤销原因
     */
    public void revoke(String revokedBy, String reason) {
        this.revoked = 1;
        this.revokedAt = LocalDateTime.now();
        this.revokedBy = revokedBy;
        this.revokeReason = reason;
    }

    /**
     * 记录Token使用
     *
     * <p>更新最后使用时间和使用次数
     */
    public void recordUsage() {
        this.lastUsedAt = LocalDateTime.now();
        this.useCount = (this.useCount == null ? 0 : this.useCount) + 1;
    }
}
