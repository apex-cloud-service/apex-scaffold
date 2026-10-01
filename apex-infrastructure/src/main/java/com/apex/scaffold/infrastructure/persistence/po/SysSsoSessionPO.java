package com.apex.scaffold.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * SSO 会话持久化对象
 *
 * <p>对应数据库表 {@code sys_sso_session}，存储单点登录会话信息，管理 SSO 会话的生命周期。
 * 继承 {@link BasePO} 获得通用字段（ID、创建时间、更新时间、逻辑删除等）。
 *
 * <h2>核心字段</h2>
 * <ul>
 *   <li>{@link #providerId} - SSO 提供商 ID（关联 {@code sys_sso_provider}）</li>
 *   <li>{@link #userId} - 系统用户 ID（关联 {@code sys_user}）</li>
 *   <li>{@link #externalId} - 外部平台用户 ID（第三方身份标识）</li>
 *   <li>{@link #sessionIndex} - 会话索引（SAML 2.0 SessionIndex 或 OAuth2 Session ID）</li>
 *   <li>{@link #assertion} - 断言信息（SAML Assertion 或 OAuth2 ID Token，加密存储）</li>
 *   <li>{@link #loginTime} - 登录时间</li>
 *   <li>{@link #expireTime} - 会话过期时间</li>
 *   <li>{@link #status} - 会话状态（ACTIVE=活跃、EXPIRED=过期、REVOKED=已撤销）</li>
 *   <li>{@link #tenantId} - 租户标识（多租户隔离）</li>
 * </ul>
 *
 * <h2>会话状态</h2>
 * <ul>
 *   <li>ACTIVE：会话活跃，用户已登录</li>
 *   <li>EXPIRED：会话过期，需要重新登录</li>
 *   <li>REVOKED：会话已撤销（用户主动登出或管理员强制下线）</li>
 * </ul>
 *
 * <h2>业务场景</h2>
 * <ul>
 *   <li>会话管理：记录和管理 SSO 登录会话</li>
 *   <li>单点登出：支持 SAML SLO 和 OAuth2 Token Revocation</li>
 *   <li>会话验证：验证 SSO 会话的有效性</li>
 *   <li>强制下线：管理员强制撤销用户的 SSO 会话</li>
 *   <li>会话审计：跟踪和审计 SSO 登录会话</li>
 * </ul>
 *
 * <h2>安全特性</h2>
 * <ul>
 *   <li>断言加密：{@code assertion} 字段应加密存储，防止敏感信息泄露</li>
 *   <li>会话过期：通过 {@code expireTime} 自动过期失效会话</li>
 *   <li>会话撤销：支持主动撤销会话，增强安全性</li>
 * </ul>
 *
 * <h2>注意事项</h2>
 * <ul>
 *   <li>会话索引 {@code sessionIndex} 用于单点登出时定位会话</li>
 *   <li>断言信息 {@code assertion} 包含敏感信息，需加密存储</li>
 *   <li>定期清理过期会话，避免数据库膨胀</li>
 *   <li>租户隔离通过 {@code tenantId} 字段实现</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.2.0
 * @see BasePO
 * @see SysSsoProviderPO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@TableName("sys_sso_session")
public class SysSsoSessionPO extends BasePO {

    private Long providerId;
    private Long userId;
    private String externalId;
    private String sessionIndex;
    private String assertion;
    private LocalDateTime loginTime;
    private LocalDateTime expireTime;
    private String status;
    private String tenantId;
}
