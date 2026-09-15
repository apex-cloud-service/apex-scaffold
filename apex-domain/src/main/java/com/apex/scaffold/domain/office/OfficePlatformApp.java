package com.apex.scaffold.domain.office;

import lombok.Builder;
import lombok.Value;

/**
 * 办公平台应用配置聚合根
 *
 * <p>表示租户级别的办公平台应用配置，包含认证凭据引用、回调地址和能力标识。
 * 每个租户可以为每个平台配置独立的应用，实现租户隔离。
 *
 * <p>设计要点：
 * <ul>
 *   <li>secretReference 和 signingSecretReference 只保存环境变量引用名，不存储实际密钥</li>
 *   <li>每个租户-平台组合最多只有一个启用的应用配置</li>
 *   <li>capabilities 以逗号分隔的字符串存储平台支持的能力集</li>
 *   <li>enabled 控制应用是否可用于 OAuth 和 API 调用</li>
 * </ul>
 *
 * @author Apex Team
 * @since 1.2.0
 */
@Value
@Builder
public class OfficePlatformApp {
    /** 主键 ID */
    Long id;

    /** 租户标识 */
    String tenantId;

    /** 平台类型 */
    OfficePlatform platform;

    /** 平台应用 ID（ClientId/CorpId 等） */
    String appId;

    /** 应用密钥环境变量引用名（不保存实际密钥） */
    String secretReference;

    /** OAuth 回调地址 */
    String redirectUri;

    /** 签名验证密钥环境变量引用名（用于 Webhook 等） */
    String signingSecretReference;

    /** 平台能力标识（逗号分隔） */
    String capabilities;

    /** 是否启用 */
    boolean enabled;

    /** 乐观锁版本号 */
    Integer version;

    /** 逻辑删除标记（0-未删除，1-已删除） */
    Integer deleted;
}
