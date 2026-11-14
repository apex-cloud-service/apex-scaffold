package com.apex.scaffold.domain.model.entity;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * 系统会话配置领域实体。
 *
 * <p>管理用户会话的生命周期和并发控制策略，包括最大会话数、超时时间、
 * 并发登录控制以及"记住我"功能的配置。
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>控制单用户的最大并发会话数</li>
 *   <li>定义会话的超时时间和失效策略</li>
 *   <li>管理并发登录时的会话踢出规则</li>
 *   <li>配置"记住我"功能及其有效期</li>
 *   <li>实现租户级别的会话配置隔离</li>
 * </ul>
 *
 * <h2>线程安全性</h2>
 * <p>当用户在多个设备或浏览器同时登录时，系统根据配置采取不同策略：
 * <ul>
 *   <li><strong>允许多会话</strong>（{@link #concurrentControl} = 0）：
 *       允许用户同时保持多个会话，最多 {@link #maxSessions} 个</li>
 *   <li><strong>踢出旧会话</strong>（{@link #concurrentControl} = 1）：
 *       新登录时自动踢出最早的会话，保持会话数不超过 {@link #maxSessions}</li>
 * </ul>
 *
 * <h2>职责范围</h2>
 * <p>启用后允许用户选择长期保持登录状态，即使关闭浏览器也不会失效。
 * 通常用于非敏感应用或用户信任的个人设备。安全要求高的场景应禁用此功能。
 *
 * <h2>设计说明</h2>
 * <p>每个租户可以配置独立的会话策略，满足不同组织的安全合规要求：
 * <ul>
 *   <li>金融机构：短超时（10-15分钟）、禁用"记住我"、单会话</li>
 *   <li>企业办公：中超时（30分钟）、允许"记住我"、多会话</li>
 *   <li>内容平台：长超时（1-2小时）、启用"记住我"、无限会话</li>
 * </ul>
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>会话超时时间通常为 15-60 分钟</li>
 *   <li>"记住我"有效期通常为 7-30 天</li>
 *   <li>"记住我"有效期必须大于会话超时时间</li>
 *   <li>最大会话数建议不超过 10 个，防止资源滥用</li>
 *   <li>禁用配置后，新会话创建将失败，但已有会话仍然有效直到超时</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see com.apex.scaffold.infrastructure.persistence.po.SysSessionConfigPO
 */
@Getter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class SysSessionConfigEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 单用户最大会话数。
     *
     * <p>限制单个用户可以同时保持的会话数量，防止资源滥用和账号共享。
     * 超过限制时根据 {@link #concurrentControl} 策略处理。
     *
     * <p>常见配置：
     * <ul>
     *   <li>1 - 严格单会话（高安全场景）</li>
     *   <li>3-5 - 多设备支持（企业场景）</li>
     *   <li>-1 或 999 - 无限制（特殊需求）</li>
     * </ul>
     *
     * <p>默认值 1，必填。
     */
    private Integer maxSessions;

    /**
     * 会话超时时间（秒）。
     *
     * <p>定义用户处于非活跃状态后会话自动失效的时间。超时后用户需要重新登录。
     * 对于高安全要求的应用，应设置较短的超时时间。
     *
     * <p>常见配置：
     * <ul>
     *   <li>900 秒（15 分钟）- 高安全场景</li>
     *   <li>1800 秒（30 分钟）- 标准企业场景</li>
     *   <li>3600 秒（1 小时）- 低频交互场景</li>
     * </ul>
     *
     * <p>默认值 1800 秒（30 分钟），必填。
     */
    private Integer sessionTimeout;

    /**
     * 并发控制策略。
     *
     * <p>定义当用户会话数达到 {@link #maxSessions} 限制时的处理策略：
     * <ul>
     *   <li>0 - 允许多会话：达到限制后拒绝新登录，保护现有会话</li>
     *   <li>1 - 踢出旧会话：自动踢出最早的会话，允许新登录</li>
     * </ul>
     *
     * <p>默认值 1（踢出旧会话），必填。
     *
     * @see #maxSessions
     */
    private Integer concurrentControl;

    /**
     * 是否启用"记住我"功能。
     *
     * <p>启用后用户可以选择长期保持登录状态，即使关闭浏览器也不会失效。
     * 实现方式通常是签发长期有效的 Refresh Token 或持久化 Cookie。
     *
     * <p>安全考虑：
     * <ul>
     *   <li>0 - 禁用：强制短期会话，适用于高安全场景（如金融、医疗）</li>
     *   <li>1 - 启用：提供便利性，适用于一般应用</li>
     * </ul>
     *
     * <p>默认值 1（启用），必填。
     *
     * @see #rememberMeTtl
     */
    private Integer rememberMeEnabled;

    /**
     * "记住我"有效期（秒）。
     *
     * <p>定义用户选择"记住我"后令牌或 Cookie 的有效期。
     * 有效期内用户无需重新登录即可恢复会话。
     *
     * <p>常见配置：
     * <ul>
     *   <li>604800 秒（7 天）- 平衡安全和便利</li>
     *   <li>1209600 秒（14 天）- 标准配置</li>
     *   <li>2592000 秒（30 天）- 长期免登录</li>
     * </ul>
     *
     * <p>默认值 604800 秒（7 天），必填。业务规则要求必须大于 {@link #sessionTimeout}。
     *
     * @see #rememberMeEnabled
     */
    private Integer rememberMeTtl;

    /**
     * 是否启用。
     *
     * <p>控制会话配置是否生效：
     * <ul>
     *   <li>0 - 禁用：新会话创建将失败，但已有会话仍然有效直到超时</li>
     *   <li>1 - 启用：正常创建和管理会话</li>
     * </ul>
     *
     * <p>默认值 1（启用），必填。
     */
    private Integer enabled;

    /**
     * 租户标识。
     *
     * <p>实现多租户数据隔离，确保不同租户的会话配置相互独立。
     * 所有查询和操作都必须携带租户标识进行过滤。
     *
     * <p>长度限制 64 字符，必填。
     */
    private String tenantId;

    public SysSessionConfigEntity() {
        super();
    }

    /**
     * 设置单用户最大会话数，赋值后刷新更新时间。
     *
     * @param maxSessions 单个用户可同时保持的最大会话数
     */
    public void setMaxSessions(Integer maxSessions) {
        this.maxSessions = maxSessions;
        touch();
    }

    /**
     * 设置会话超时时间，赋值后刷新更新时间。
     *
     * @param sessionTimeout 用户非活跃后会话自动失效的时间（秒）
     */
    public void setSessionTimeout(Integer sessionTimeout) {
        this.sessionTimeout = sessionTimeout;
        touch();
    }

    /**
     * 设置并发控制策略，赋值后刷新更新时间。
     *
     * @param concurrentControl 会话数达到上限时的处理策略（0-允许多会话并拒绝新登录，1-踢出最早的旧会话并允许新登录）
     */
    public void setConcurrentControl(Integer concurrentControl) {
        this.concurrentControl = concurrentControl;
        touch();
    }

    /**
     * 设置是否启用"记住我"功能，赋值后刷新更新时间。
     *
     * @param rememberMeEnabled 是否启用（0-禁用，1-启用）
     */
    public void setRememberMeEnabled(Integer rememberMeEnabled) {
        this.rememberMeEnabled = rememberMeEnabled;
        touch();
    }

    /**
     * 设置"记住我"有效期，赋值后刷新更新时间。
     *
     * @param rememberMeTtl 选择"记住我"后令牌或 Cookie 的有效期（秒），必须大于会话超时时间
     */
    public void setRememberMeTtl(Integer rememberMeTtl) {
        this.rememberMeTtl = rememberMeTtl;
        touch();
    }

    /**
     * 设置是否启用会话配置，赋值后刷新更新时间。
     *
     * @param enabled 是否启用（0-禁用，1-启用）
     */
    public void setEnabled(Integer enabled) {
        this.enabled = enabled;
        touch();
    }

    /**
     * 设置租户标识，赋值后刷新更新时间。
     *
     * @param tenantId 租户标识，用于多租户数据隔离
     */
    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
        touch();
    }
}
