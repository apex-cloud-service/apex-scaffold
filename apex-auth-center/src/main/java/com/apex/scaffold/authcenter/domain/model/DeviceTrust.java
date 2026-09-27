package com.apex.scaffold.authcenter.domain.model;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 设备信任管理领域模型
 * @author Apex Team
 * @since 1.2.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceTrust {

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 租户ID
     */
    private String tenantId;

    /**
     * 设备ID（唯一标识）
     */
    private String deviceId;

    /**
     * 设备名称
     */
    private String deviceName;

    /**
     * 设备类型
     */
    private DeviceType deviceType;

    /**
     * 设备指纹
     */
    private String deviceFingerprint;

    /**
     * 信任级别
     */
    private TrustLevel trustLevel;

    /**
     * 信任状态
     */
    private TrustStatus status;

    /**
     * 首次认证时间
     */
    private LocalDateTime firstAuthAt;

    /**
     * 最后认证时间
     */
    private LocalDateTime lastAuthAt;

    /**
     * 最后使用IP
     */
    private String lastIp;

    /**
     * 最后使用位置（国家/城市）
     */
    private String lastLocation;

    /**
     * 认证次数
     */
    private Integer authCount;

    /**
     * 风险评分（0-100）
     */
    private Integer riskScore;

    /**
     * 设备元数据（JSON格式：操作系统、浏览器等）
     */
    private String metadata;

    /**
     * 信任过期时间
     */
    private LocalDateTime expiresAt;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    /**
     * 设备类型枚举
     */
    public enum DeviceType {
        /**
         * 桌面电脑
         */
        DESKTOP,

        /**
         * 笔记本电脑
         */
        LAPTOP,

        /**
         * 手机
         */
        MOBILE,

        /**
         * 平板
         */
        TABLET,

        /**
         * 未知
         */
        UNKNOWN
    }

    /**
     * 信任级别枚举
     */
    public enum TrustLevel {
        /**
         * 未信任 - 需要完整MFA
         */
        UNTRUSTED(0),

        /**
         * 低信任 - 需要MFA
         */
        LOW(1),

        /**
         * 中信任 - 定期需要MFA
         */
        MEDIUM(2),

        /**
         * 高信任 - 很少需要MFA
         */
        HIGH(3),

        /**
         * 完全信任 - 免除MFA（限30天）
         */
        FULL(4);

        private final int level;

        TrustLevel(int level) {
            this.level = level;
        }

        /**
         * 获取信任级别数值
         *
         * @return 信任级别数值（0-未信任，1-低，2-中，3-高，4-完全信任）
         */
        public int getLevel() {
            return level;
        }
    }

    /**
     * 信任状态枚举
     */
    public enum TrustStatus {
        /**
         * 待验证
         */
        PENDING,

        /**
         * 已激活
         */
        ACTIVE,

        /**
         * 已过期
         */
        EXPIRED,

        /**
         * 已撤销
         */
        REVOKED,

        /**
         * 已锁定（检测到异常）
         */
        LOCKED
    }

    /**
     * 判断是否已激活
     *
     * @return 信任状态为激活时返回true，否则返回false
     */
    public boolean isActive() {
        return TrustStatus.ACTIVE.equals(status);
    }

    /**
     * 判断是否已过期
     *
     * @return 当前时间已超过信任过期时间时返回true，未设置过期时间或未过期返回false
     */
    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }

    /**
     * 判断是否高风险设备
     *
     * @return 风险评分大于70时返回true，否则返回false
     */
    public boolean isHighRisk() {
        return riskScore != null && riskScore > 70;
    }

    /**
     * 更新最后使用信息
     *
     * @param ip 本次使用的客户端 IP 地址
     * @param location 本次使用的位置信息（国家/城市）
     */
    public void updateLastUsed(String ip, String location) {
        this.lastAuthAt = LocalDateTime.now();
        this.lastIp = ip;
        this.lastLocation = location;
        this.authCount = (this.authCount == null ? 0 : this.authCount) + 1;
    }

    /**
     * 提升信任级别
     */
    public void upgradeTrustLevel() {
        if (this.trustLevel.getLevel() < TrustLevel.FULL.getLevel()) {
            TrustLevel[] levels = TrustLevel.values();
            this.trustLevel = levels[this.trustLevel.getLevel() + 1];
        }
    }

    /**
     * 降低信任级别
     */
    public void downgradeTrustLevel() {
        if (this.trustLevel.getLevel() > TrustLevel.UNTRUSTED.getLevel()) {
            TrustLevel[] levels = TrustLevel.values();
            this.trustLevel = levels[this.trustLevel.getLevel() - 1];
        }
    }

    /**
     * 计算风险评分
     */
    public void calculateRiskScore() {
        int score = 0;

        // 基于信任级别
        score += (5 - trustLevel.getLevel()) * 20; // 0-80分

        // 基于使用频率（低频使用增加风险）
        if (authCount != null && authCount < 5) {
            score += 20;
        }

        // 基于时间（长时间未使用增加风险）
        if (lastAuthAt != null && lastAuthAt.plusDays(30).isBefore(LocalDateTime.now())) {
            score += 20;
        }

        this.riskScore = Math.min(100, score);
    }
}
