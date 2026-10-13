package com.apex.scaffold.auth.domain.model.entity;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 会话实体
 *
 * <p>会话管理用户的登录状态和设备信息，支持单设备登录、多设备管理和会话踢出。
 *
 * @author Apex Scaffold Team
 * @since 1.2.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionEntity {

    /**
     * 会话ID
     */
    private Long id;

    /**
     * 租户ID
     */
    private String tenantId;

    /**
     * 会话唯一标识
     */
    private String sessionId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 访问令牌ID（关联auth_token表）
     */
    private Long tokenId;

    /**
     * 设备类型（WEB、MOBILE_IOS、MOBILE_ANDROID、DESKTOP等）
     */
    private String deviceType;

    /**
     * 设备唯一标识
     */
    private String deviceId;

    /**
     * 设备名称
     */
    private String deviceName;

    /**
     * 客户端IP地址
     */
    private String ipAddress;

    /**
     * User-Agent
     */
    private String userAgent;

    /**
     * 地理位置信息（城市、国家等）
     */
    private String location;

    /**
     * 会话状态（ACTIVE、EXPIRED、TERMINATED、KICKED_OUT）
     */
    private String status;

    /**
     * 会话创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 最后活跃时间
     */
    private LocalDateTime lastActiveAt;

    /**
     * 会话过期时间
     */
    private LocalDateTime expiresAt;

    /**
     * 会话元数据（JSON格式）
     */
    private String metadata;

    /**
     * 是否删除
     */
    private Boolean deleted;

    /**
     * 乐观锁版本号
     */
    private Integer version;

    /**
     * 检查会话是否活跃
     *
     * @return 如果会话状态为ACTIVE且未过期返回true
     */
    public boolean isActive() {
        return "ACTIVE".equals(status)
                && !Boolean.TRUE.equals(deleted)
                && expiresAt != null
                && LocalDateTime.now().isBefore(expiresAt);
    }

    /**
     * 检查会话是否已过期
     *
     * @return 如果会话已过期返回true
     */
    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }

    /**
     * 更新最后活跃时间
     */
    public void updateLastActive() {
        this.lastActiveAt = LocalDateTime.now();
    }

    /**
     * 终止会话
     */
    public void terminate() {
        this.status = "TERMINATED";
        this.lastActiveAt = LocalDateTime.now();
    }

    /**
     * 踢出会话
     */
    public void kickOut() {
        this.status = "KICKED_OUT";
        this.lastActiveAt = LocalDateTime.now();
    }

    /**
     * 标记会话为已过期
     */
    public void markExpired() {
        this.status = "EXPIRED";
        this.lastActiveAt = LocalDateTime.now();
    }

    /**
     * 检查是否为移动端会话
     *
     * @return 如果是移动端设备返回true
     */
    public boolean isMobileDevice() {
        return "MOBILE_IOS".equals(deviceType) || "MOBILE_ANDROID".equals(deviceType);
    }

    /**
     * 检查是否为Web端会话
     *
     * @return 如果是Web设备返回true
     */
    public boolean isWebDevice() {
        return "WEB".equals(deviceType);
    }
}
