package com.apex.scaffold.auth.domain.model.entity;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * JWT密钥实体
 *
 * <p>用于存储JWT签名密钥的版本信息和生命周期状态。
 * 支持密钥轮换策略，保证在密钥切换期间的平滑过渡。
 *
 * <h2>密钥状态机</h2>
 * <pre>
 * PENDING(待启用) → ACTIVE(当前生效) → ROTATING(轮换中) → DEPRECATED(已废弃) → REVOKED(已吊销)
 * </pre>
 *
 * <h2>轮换策略</h2>
 * <ul>
 *   <li>每个时刻只有一个ACTIVE密钥用于签发新Token</li>
 *   <li>ROTATING状态的密钥仅用于验证旧Token（过渡期24小时）</li>
 *   <li>DEPRECATED状态的密钥保留用于审计和历史Token验证</li>
 *   <li>定期任务每90天自动触发密钥轮换</li>
 * </ul>
 *
 * @author Apex Team
 * @since 2026-09-25
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("jwt_keys")
public class JwtKeyEntity {

    /**
     * 主键ID
     */
    @Id
    private Long id;

    /**
     * 密钥版本号
     * <p>格式: v1, v2, v3... 或时间戳格式: 20260925_001
     */
    private String keyVersion;

    /**
     * 加密后的密钥内容
     * <p>存储AES-256加密后的JWT签名密钥，加密密钥从环境变量KEY_ENCRYPTION_KEY获取
     */
    private String encryptedSecret;

    /**
     * 密钥算法
     * <p>HS256 (HMAC-SHA256) 或 RS256 (RSA-SHA256)
     */
    private String algorithm;

    /**
     * 密钥状态
     * <p>PENDING: 已生成但未启用</p>
     * <p>ACTIVE: 当前生效，用于签发新Token</p>
     * <p>ROTATING: 轮换中，仅用于验证旧Token</p>
     * <p>DEPRECATED: 已废弃，保留用于历史Token验证</p>
     * <p>REVOKED: 已吊销，不再用于任何验证</p>
     */
    private String status;

    /**
     * 密钥启用时间
     */
    private LocalDateTime activatedAt;

    /**
     * 密钥计划过期时间
     * <p>通常设置为启用后90天
     */
    private LocalDateTime expiresAt;

    /**
     * 密钥实际废弃时间
     */
    private LocalDateTime deprecatedAt;

    /**
     * 密钥吊销时间
     */
    private LocalDateTime revokedAt;

    /**
     * 轮换原因
     * <p>SCHEDULED: 定期自动轮换</p>
     * <p>SECURITY_INCIDENT: 安全事件触发</p>
     * <p>MANUAL: 管理员手动触发</p>
     * <p>LEAKED: 密钥泄露</p>
     */
    private String rotationReason;

    /**
     * 操作人用户ID
     */
    private Long operatorUserId;

    /**
     * 操作人用户名
     */
    private String operatorUsername;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    /**
     * 备注说明
     */
    private String remark;

    /**
     * 检查密钥是否为活跃状态
     *
     * @return 密钥状态为ACTIVE时返回true
     */
    public boolean isActive() {
        return "ACTIVE".equals(status);
    }

    /**
     * 检查密钥是否可用于验证Token
     * <p>ACTIVE和ROTATING状态的密钥都可以用于验证
     *
     * @return 密钥状态为ACTIVE或ROTATING时返回true
     */
    public boolean isUsableForVerification() {
        return "ACTIVE".equals(status) || "ROTATING".equals(status);
    }

    /**
     * 检查密钥是否已过期
     *
     * @return 当前时间超过计划过期时间时返回true
     */
    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }

    /**
     * 检查密钥是否即将过期（30天内）
     *
     * @return 计划过期时间在30天内时返回true
     */
    public boolean isExpiringSoon() {
        return expiresAt != null && LocalDateTime.now().plusDays(30).isAfter(expiresAt);
    }
}
