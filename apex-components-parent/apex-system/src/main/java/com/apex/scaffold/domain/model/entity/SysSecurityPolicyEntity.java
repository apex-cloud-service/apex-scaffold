package com.apex.scaffold.domain.model.entity;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * 系统安全策略领域实体。
 *
 * <p>定义系统的密码强度要求、登录失败处理、账户锁定规则以及 IP 访问控制策略。
 * 用于增强系统的安全防护能力，防止暴力破解、未授权访问等安全威胁。
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>定义密码复杂度要求（长度、字符类型）</li>
 *   <li>配置密码过期策略</li>
 *   <li>管理登录失败次数限制和账户锁定</li>
 *   <li>控制 IP 白名单和黑名单访问策略</li>
 *   <li>实现租户级别的安全策略隔离</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <p>通过组合以下规则提升密码安全性：
 * <ul>
 *   <li>{@link #passwordMinLength} - 最小长度要求</li>
 *   <li>{@link #passwordRequireUpper} - 是否必须包含大写字母</li>
 *   <li>{@link #passwordRequireLower} - 是否必须包含小写字母</li>
 *   <li>{@link #passwordRequireDigit} - 是否必须包含数字</li>
 *   <li>{@link #passwordRequireSpecial} - 是否必须包含特殊字符</li>
 * </ul>
 *
 * <p>推荐配置示例：
 * <ul>
 *   <li>低安全：长度≥6，无字符类型要求</li>
 *   <li>中安全：长度≥8，大小写+数字</li>
 *   <li>高安全：长度≥12，大小写+数字+特殊字符</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <p>防止暴力破解攻击的核心机制：
 * <ul>
 *   <li>累计登录失败次数，达到 {@link #maxLoginAttempts} 后触发锁定</li>
 *   <li>锁定期间用户无法登录，持续 {@link #lockoutDuration} 秒</li>
 *   <li>锁定解除后失败计数器重置</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <p>通过白名单和黑名单实现精细的网络访问控制：
 * <ul>
 *   <li><strong>白名单</strong>（{@link #ipWhitelist}）：只允许列表中的 IP 访问，未列出则拒绝</li>
 *   <li><strong>黑名单</strong>（{@link #ipBlacklist}）：禁止列表中的 IP 访问，未列出则允许</li>
 *   <li>白名单优先级高于黑名单</li>
 *   <li>支持 IP 段表示（如 192.168.1.0/24）</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <p>每个租户可以配置独立的安全策略，满足不同组织的合规要求：
 * <ul>
 *   <li>金融机构：高强度密码、密码定期更换（90天）、严格 IP 控制</li>
 *   <li>企业办公：中强度密码、长期有效、部分 IP 控制</li>
 *   <li>公开服务：低强度密码、永久有效、无 IP 限制</li>
 * </ul>
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>密码最小长度建议不低于 8 位</li>
 *   <li>最大登录尝试次数通常为 3-5 次</li>
 *   <li>锁定时长通常为 15-60 分钟</li>
 *   <li>密码过期天数通常为 30-90 天，null 表示永久有效</li>
 *   <li>禁用配置后，密码和登录验证仍然执行，但不应用额外的安全限制</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see com.apex.scaffold.infrastructure.persistence.po.SysSecurityPolicyPO
 */
@Getter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class SysSecurityPolicyEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 密码最小长度。
     *
     * <p>定义用户密码必须满足的最小字符数。
     * 较长的密码通常更难被暴力破解。
     *
     * <p>常见配置：
     * <ul>
     *   <li>6 - 最低安全要求（不推荐）</li>
     *   <li>8 - 标准安全要求</li>
     *   <li>12 - 高安全要求</li>
     *   <li>16 - 极高安全要求</li>
     * </ul>
     *
     * <p>默认值 8，必填。
     */
    private Integer passwordMinLength;

    /**
     * 密码是否必须包含大写字母。
     *
     * <p>启用后密码必须至少包含一个大写字母（A-Z）。
     *
     * <p>取值：
     * <ul>
     *   <li>0 - 否：不强制要求</li>
     *   <li>1 - 是：必须包含</li>
     * </ul>
     *
     * <p>默认值 1（是），必填。
     */
    private Integer passwordRequireUpper;

    /**
     * 密码是否必须包含小写字母。
     *
     * <p>启用后密码必须至少包含一个小写字母（a-z）。
     *
     * <p>取值：
     * <ul>
     *   <li>0 - 否：不强制要求</li>
     *   <li>1 - 是：必须包含</li>
     * </ul>
     *
     * <p>默认值 1（是），必填。
     */
    private Integer passwordRequireLower;

    /**
     * 密码是否必须包含数字。
     *
     * <p>启用后密码必须至少包含一个数字（0-9）。
     *
     * <p>取值：
     * <ul>
     *   <li>0 - 否：不强制要求</li>
     *   <li>1 - 是：必须包含</li>
     * </ul>
     *
     * <p>默认值 1（是），必填。
     */
    private Integer passwordRequireDigit;

    /**
     * 密码是否必须包含特殊字符。
     *
     * <p>启用后密码必须至少包含一个特殊字符（如 !@#$%^&*）。
     *
     * <p>取值：
     * <ul>
     *   <li>0 - 否：不强制要求</li>
     *   <li>1 - 是：必须包含</li>
     * </ul>
     *
     * <p>默认值 1（是），必填。
     */
    private Integer passwordRequireSpecial;

    /**
     * 密码过期天数。
     *
     * <p>定义密码的有效期，超过后用户必须修改密码才能继续使用系统。
     * 用于强制用户定期更换密码，降低密码泄露风险。
     *
     * <p>常见配置：
     * <ul>
     *   <li>30 天 - 高频更换（高安全场景）</li>
     *   <li>90 天 - 标准配置（企业场景）</li>
     *   <li>180 天 - 低频更换</li>
     *   <li>null - 永久有效（一般应用）</li>
     * </ul>
     *
     * <p>默认值 null（不过期），可选。
     */
    private Integer passwordExpiryDays;

    /**
     * 最大登录尝试次数。
     *
     * <p>定义用户在被锁定前可以连续失败登录的最大次数。
     * 达到限制后触发账户锁定，防止暴力破解。
     *
     * <p>常见配置：
     * <ul>
     *   <li>3 次 - 严格控制</li>
     *   <li>5 次 - 标准配置</li>
     *   <li>10 次 - 宽松配置</li>
     * </ul>
     *
     * <p>默认值 5，必填。
     *
     * @see #lockoutDuration
     */
    private Integer maxLoginAttempts;

    /**
     * 锁定时长（秒）。
     *
     * <p>定义账户因登录失败过多而被锁定的持续时间。
     * 锁定期间用户无法登录，即使输入正确密码。
     *
     * <p>常见配置：
     * <ul>
     *   <li>900 秒（15 分钟）- 短期锁定</li>
     *   <li>1800 秒（30 分钟）- 标准锁定</li>
     *   <li>3600 秒（1 小时）- 长期锁定</li>
     * </ul>
     *
     * <p>默认值 1800 秒（30 分钟），必填。
     *
     * @see #maxLoginAttempts
     */
    private Integer lockoutDuration;

    /**
     * IP 白名单（JSON 数组格式）。
     *
     * <p>定义允许访问系统的 IP 地址或 IP 段列表。
     * 只有列表中的 IP 才能访问，未列出的 IP 全部拒绝。
     *
     * <p>格式示例：
     * <pre>
     * ["192.168.1.100", "10.0.0.0/8", "2001:db8::1"]
     * </pre>
     *
     * <p>应用场景：
     * <ul>
     *   <li>内部管理系统：限制为公司内网 IP</li>
     *   <li>VPN 接入：限制为 VPN 出口 IP</li>
     *   <li>特定地区：限制为特定国家或地区的 IP 段</li>
     * </ul>
     *
     * <p>白名单优先级高于黑名单。null 或空数组表示不启用白名单。
     *
     * @see #ipBlacklist
     */
    private String ipWhitelist;

    /**
     * IP 黑名单（JSON 数组格式）。
     *
     * <p>定义禁止访问系统的 IP 地址或 IP 段列表。
     * 列表中的 IP 将被拒绝访问，未列出的 IP 允许访问。
     *
     * <p>格式示例：
     * <pre>
     * ["192.168.1.200", "172.16.0.0/12", "203.0.113.0/24"]
     * </pre>
     *
     * <p>应用场景：
     * <ul>
     *   <li>恶意攻击：封禁发起攻击的 IP</li>
     *   <li>异常行为：临时封禁可疑 IP</li>
     *   <li>地区限制：封禁特定国家或地区的 IP 段</li>
     * </ul>
     *
     * <p>黑名单优先级低于白名单。null 或空数组表示不启用黑名单。
     *
     * @see #ipWhitelist
     */
    private String ipBlacklist;

    /**
     * 是否启用。
     *
     * <p>控制安全策略是否生效：
     * <ul>
     *   <li>0 - 禁用：不应用额外的安全限制，但基础验证仍然执行</li>
     *   <li>1 - 启用：应用所有配置的安全策略</li>
     * </ul>
     *
     * <p>默认值 1（启用），必填。
     */
    private Integer enabled;

    /**
     * 租户标识。
     *
     * <p>实现多租户数据隔离，确保不同租户的安全策略相互独立。
     * 所有查询和操作都必须携带租户标识进行过滤。
     *
     * <p>长度限制 64 字符，必填。
     */
    private String tenantId;

    public SysSecurityPolicyEntity() {
        super();
    }

    /**
     * 设置密码最小长度，赋值后刷新更新时间。
     *
     * @param passwordMinLength 密码最小长度（字符数）
     */
    public void setPasswordMinLength(Integer passwordMinLength) {
        this.passwordMinLength = passwordMinLength;
        touch();
    }

    /**
     * 设置密码是否必须包含大写字母，赋值后刷新更新时间。
     *
     * @param passwordRequireUpper 是否必须包含大写字母（0-否，1-是）
     */
    public void setPasswordRequireUpper(Integer passwordRequireUpper) {
        this.passwordRequireUpper = passwordRequireUpper;
        touch();
    }

    /**
     * 设置密码是否必须包含小写字母，赋值后刷新更新时间。
     *
     * @param passwordRequireLower 是否必须包含小写字母（0-否，1-是）
     */
    public void setPasswordRequireLower(Integer passwordRequireLower) {
        this.passwordRequireLower = passwordRequireLower;
        touch();
    }

    /**
     * 设置密码是否必须包含数字，赋值后刷新更新时间。
     *
     * @param passwordRequireDigit 是否必须包含数字（0-否，1-是）
     */
    public void setPasswordRequireDigit(Integer passwordRequireDigit) {
        this.passwordRequireDigit = passwordRequireDigit;
        touch();
    }

    /**
     * 设置密码是否必须包含特殊字符，赋值后刷新更新时间。
     *
     * @param passwordRequireSpecial 是否必须包含特殊字符（0-否，1-是）
     */
    public void setPasswordRequireSpecial(Integer passwordRequireSpecial) {
        this.passwordRequireSpecial = passwordRequireSpecial;
        touch();
    }

    /**
     * 设置密码过期天数，赋值后刷新更新时间。
     *
     * @param passwordExpiryDays 密码过期天数（天），null 表示永久有效
     */
    public void setPasswordExpiryDays(Integer passwordExpiryDays) {
        this.passwordExpiryDays = passwordExpiryDays;
        touch();
    }

    /**
     * 设置最大登录尝试次数，赋值后刷新更新时间。
     *
     * @param maxLoginAttempts 达到该次数后触发账户锁定的连续登录失败次数上限
     */
    public void setMaxLoginAttempts(Integer maxLoginAttempts) {
        this.maxLoginAttempts = maxLoginAttempts;
        touch();
    }

    /**
     * 设置账户锁定时长，赋值后刷新更新时间。
     *
     * @param lockoutDuration 登录失败过多后的账户锁定时长（秒）
     */
    public void setLockoutDuration(Integer lockoutDuration) {
        this.lockoutDuration = lockoutDuration;
        touch();
    }

    /**
     * 设置 IP 白名单，赋值后刷新更新时间。
     *
     * @param ipWhitelist 允许访问系统的 IP 地址或 IP 段列表（JSON 数组格式），null 或空数组表示不启用
     */
    public void setIpWhitelist(String ipWhitelist) {
        this.ipWhitelist = ipWhitelist;
        touch();
    }

    /**
     * 设置 IP 黑名单，赋值后刷新更新时间。
     *
     * @param ipBlacklist 禁止访问系统的 IP 地址或 IP 段列表（JSON 数组格式），null 或空数组表示不启用
     */
    public void setIpBlacklist(String ipBlacklist) {
        this.ipBlacklist = ipBlacklist;
        touch();
    }

    /**
     * 设置是否启用安全策略，赋值后刷新更新时间。
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
