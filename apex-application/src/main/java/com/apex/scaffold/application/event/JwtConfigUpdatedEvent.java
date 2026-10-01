package com.apex.scaffold.application.event;

import com.apex.scaffold.application.dto.admin.JwtConfigDTO;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * JWT 配置更新事件
 *
 * <p>当租户的 JWT 配置发生更新时发布此事件，用于通知相关组件刷新 JWT 签名和验证配置。
 *
 * <h2>事件触发时机</h2>
 * <ul>
 *   <li>JWT 密钥更新</li>
 *   <li>令牌过期时间调整</li>
 *   <li>签名算法变更</li>
 *   <li>签发者/接收者配置变更</li>
 * </ul>
 *
 * <h2>事件处理建议</h2>
 * <ul>
 *   <li>清除相关租户的 JWT 验证缓存</li>
 *   <li>通知分布式节点刷新 JWT 配置</li>
 *   <li>记录配置变更审计日志</li>
 *   <li>触发安全告警（如密钥变更）</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Getter
public class JwtConfigUpdatedEvent extends ApplicationEvent {

    private final String tenantId;

    private final JwtConfigDTO newConfig;

    /**
     * 构造 JWT 配置更新事件
     *
     * @param source 事件源（通常是 Service 实例）
     * @param tenantId 租户标识
     * @param newConfig 更新后的 JWT 配置
     */
    public JwtConfigUpdatedEvent(Object source, String tenantId, JwtConfigDTO newConfig) {
        super(source);
        this.tenantId = tenantId;
        this.newConfig = newConfig;
    }

    @Override
    public String toString() {
        return String.format(
                "JwtConfigUpdatedEvent{tenantId=%s, accessTokenExpiration=%d, refreshTokenExpiration=%d}",
                tenantId, newConfig.getAccessTokenExpiration(), newConfig.getRefreshTokenExpiration());
    }
}
