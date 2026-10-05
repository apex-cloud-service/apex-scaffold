package com.apex.scaffold.infrastructure.persistence.po;

import com.apex.scaffold.common.persistence.po.BasePO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * SSO 审计日志持久化对象
 *
 * <p>对应数据库表 {@code sys_sso_audit_log}，记录所有 SSO 相关的操作日志和安全事件。
 * 继承 {@link BasePO} 获得通用字段（ID、创建时间、更新时间、逻辑删除等）。
 *
 * <h2>核心字段</h2>
 * <ul>
 *   <li>{@link #providerId} - SSO 提供商 ID（关联 {@code sys_sso_provider}）</li>
 *   <li>{@link #userId} - 系统用户 ID（关联 {@code sys_user}，可选）</li>
 *   <li>{@link #externalId} - 外部平台用户 ID（第三方身份标识）</li>
 *   <li>{@link #operation} - 操作类型（LOGIN=登录、LOGOUT=登出、TOKEN_REFRESH=令牌刷新、AUTH_FAILED=认证失败）</li>
 *   <li>{@link #status} - 操作状态（SUCCESS=成功、FAILED=失败）</li>
 *   <li>{@link #ipAddress} - 客户端 IP 地址</li>
 *   <li>{@link #userAgent} - 客户端 User-Agent</li>
 *   <li>{@link #errorMessage} - 错误信息（操作失败时记录）</li>
 *   <li>{@link #requestData} - 请求数据（JSON 格式，记录关键请求参数）</li>
 *   <li>{@link #tenantId} - 租户标识（多租户隔离）</li>
 * </ul>
 *
 * <h2>操作类型</h2>
 * <ul>
 *   <li>LOGIN：SSO 登录操作</li>
 *   <li>LOGOUT：SSO 登出操作</li>
 *   <li>TOKEN_REFRESH：刷新访问令牌</li>
 *   <li>AUTH_FAILED：认证失败（密码错误、账号锁定等）</li>
 *   <li>SAML_REQUEST：SAML 认证请求</li>
 *   <li>SAML_RESPONSE：SAML 认证响应</li>
 *   <li>OAUTH_AUTHORIZE：OAuth2 授权请求</li>
 *   <li>OAUTH_TOKEN：OAuth2 令牌请求</li>
 * </ul>
 *
 * <h2>业务场景</h2>
 * <ul>
 *   <li>安全审计：记录所有 SSO 相关操作，支持安全合规</li>
 *   <li>异常检测：识别异常登录行为（如异地登录、频繁失败）</li>
 *   <li>故障排查：通过日志追踪 SSO 认证问题</li>
 *   <li>统计分析：统计 SSO 使用情况和成功率</li>
 *   <li>合规报告：生成安全审计报告</li>
 * </ul>
 *
 * <h2>安全特性</h2>
 * <ul>
 *   <li>记录客户端 IP 地址，用于异地登录检测</li>
 *   <li>记录 User-Agent，识别可疑设备</li>
 *   <li>记录失败原因，辅助安全分析</li>
 *   <li>敏感数据脱敏：{@code requestData} 中的密码等敏感字段需脱敏</li>
 * </ul>
 *
 * <h2>注意事项</h2>
 * <ul>
 *   <li>审计日志通常保留较长时间（如 1 年），支持合规审计</li>
 *   <li>请求数据 {@code requestData} 需对敏感字段（密码、令牌）进行脱敏</li>
 *   <li>大流量场景下可使用异步写入，避免影响 SSO 性能</li>
 *   <li>定期归档历史日志，避免数据库膨胀</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.2.0
 * @see BasePO
 * @see SysSsoProviderPO
 * @see SysSsoSessionPO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@TableName("sys_sso_audit_log")
public class SysSsoAuditLogPO extends BasePO {

    private Long providerId;
    private Long userId;
    private String externalId;
    private String operation;
    private String status;
    private String ipAddress;
    private String userAgent;
    private String errorMessage;
    private String requestData;
    private String tenantId;
}
