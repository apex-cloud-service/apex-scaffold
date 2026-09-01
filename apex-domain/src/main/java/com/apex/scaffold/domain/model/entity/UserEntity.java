package com.apex.scaffold.domain.model.entity;

import com.apex.scaffold.common.annotation.AggregateRoot;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * 用户聚合根实体
 *
 * <p>微信APP用户聚合根，包含用户基本信息、认证信息、登录信息。
 * 采用链式Setter模式进行复杂对象的构建。
 * 提供领域行为方法用于更新Token和记录登录信息。</p>
 *
 * <h2>聚合边界</h2>
 * <p>User聚合包含以下实体：UserEntity、UserProfileEntity、UserRoleEntity、UserConsentEntity</p>
 *
 * <h2>核心业务规则</h2>
 * <ul>
 *   <li>用户状态：0-禁用，1-正常</li>
 *   <li>通过openId进行微信身份识别</li>
 *   <li>unionId用于跨应用用户识别（同一用户在微信生态内唯一）</li>
 * </ul>
 *
 * @author Apex Team
 * @version 2.0.0
 * @since 1.0.0
 * @see BaseEntity
 */
@AggregateRoot(name = "User")
@Getter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class UserEntity extends BaseEntity {

    /**
     * 序列化版本号
     *
     * <p>用于对象序列化版本控制，确保反序列化兼容性</p>
     */
    private static final long serialVersionUID = 1L;

    /**
     * 微信OpenID
     *
     * <p>用户在微信开放平台的唯一标识，用于识别用户身份。
     * 同一用户在不同应用下的OpenID不同，但UnionID相同。</p>
     *
     * @see #unionId
     */
    private String openId;

    /**
     * 登录账号（账号密码登录）。
     *
     * <p>传统账号密码登录场景下使用，微信/手机号注册用户可为 {@code null}。
     * 全局唯一（{@code uk_username} 唯一索引约束），由
     * {@code PasswordAuthenticationStrategy} 认证时查询。</p>
     *
     * @since 2.2.0
     */
    private String username;

    /**
     * 所属部门 ID（数据权限）。
     *
     * <p>关联 {@code sys_dept.id}，配合 {@code @DataScope} 注解与数据权限拦截器
     * （{@code DataScopeInnerInterceptor}）实现部门级数据隔离；未分配部门时可为
     * {@code null}（此时默认不做数据权限过滤，生产环境应保证关键用户均归属部门）。</p>
     *
     * @since 2.3.0
     */
    private Long deptId;

    /**
     * 租户 ID（多租户隔离）。
     *
     * <p>SaaS 多租户场景下用于标识用户所属租户；由 MyBatis-Plus
     * {@code TenantLineInnerInterceptor} 自动为 SELECT/INSERT/UPDATE/DELETE 语句
     * 注入 {@code tenant_id} 过滤条件，实现行级租户数据隔离。未启用多租户
     * （{@code apex.tenant.enabled=false}）时该字段为空，不参与 SQL 改写。</p>
     *
     * <p>值来源：认证用户的持久化租户归属；请求头 {@code X-Tenant-Id} 仅可选择并校验
     * 已认证租户，不能覆盖认证身份，或创建用户时显式写入。
     *
     * @since 1.2.0
     */
    private String tenantId;

    /**
     * 登录密码（BCrypt 哈希）。
     *
     * <p>仅账号密码登录使用，存储 {@code BCryptPasswordEncoder} 生成的哈希值，
     * 禁止存储明文。校验由 {@code PasswordAuthenticationStrategy} 完成。</p>
     *
     * @since 2.2.0
     */
    private String password;

    /**
     * 微信UnionID
     *
     * <p>用户在微信开放平台的全局唯一标识。
     * 同一用户在不同应用下的UnionID保持一致，可用于跨应用用户识别。
     * 当用户未绑定微信开放平台账号时，此字段可能为空。</p>
     *
     * @see #openId
     */
    private String unionId;

    /**
     * 微信AccessToken
     *
     * <p>微信授权访问令牌，用于调用微信API接口。
     * 具有时效性，过期后需要使用RefreshToken进行刷新。</p>
     *
     * @see #refreshToken
     * @see #tokenExpireTime
     */
    private String accessToken;

    /**
     * 微信RefreshToken
     *
     * <p>微信授权刷新令牌，用于在AccessToken过期时获取新的访问令牌。
     * RefreshToken的有效期较长，通常为30天。</p>
     *
     * @see #accessToken
     * @see #tokenExpireTime
     */
    private String refreshToken;

    /**
     * Token过期时间
     *
     * <p>微信AccessToken的过期时间点。
     * 在此时间之后，需要使用RefreshToken获取新的AccessToken。</p>
     *
     * @see #accessToken
     * @see #refreshToken
     */
    private LocalDateTime tokenExpireTime;

    /**
     * 用户昵称
     *
     * <p>用户的微信昵称，用于展示和社交互动。
     * 用户可在微信中随时修改。</p>
     */
    private String nickName;

    /**
     * 头像URL
     *
     * <p>用户的微信头像地址URL。
     * 建议使用CDN加速的URL以提升加载速度。</p>
     */
    private String avatarUrl;

    /**
     * 性别
     *
     * <p>用户的性别信息。
     * <ul>
     *   <li>0 - 未知</li>
     *   <li>1 - 男性</li>
     *   <li>2 - 女性</li>
     * </ul>
     */
    private Integer gender;

    /** 出生日期；新注册用户必填，历史用户可能为空。 */
    private LocalDate birthday;

    /**
     * 国家
     *
     * <p>用户所在的国家/地区名称。
     * 基于微信用户资料，可能为空。</p>
     */
    private String country;

    /**
     * 省份
     *
     * <p>用户所在的省份/州名称。
     * 基于微信用户资料，可能为空。</p>
     */
    private String province;

    /**
     * 城市
     *
     * <p>用户所在的城市名称。
     * 基于微信用户资料，可能为空。</p>
     */
    private String city;

    /**
     * 语言
     *
     * <p>用户的语言偏好设置。
     * 用于多语言场景下的内容适配。</p>
     */
    private String language;

    /**
     * 手机号
     *
     * <p>用户的手机号码，通过微信授权获取。
     * 用于关键业务操作的身份验证，如登录、安全校验等。
     * 此字段涉及用户隐私，应加密存储并严格控制访问权限。</p>
     */
    private String phone;

    /**
     * 用户状态
     *
     * <p>标识用户的可用状态。
     * <ul>
     *   <li>0 - 禁用（无法登录和使用服务）</li>
     *   <li>1 - 正常（可正常使用所有服务）</li>
     * </ul>
     *
     * @see #isEnabled()
     */
    private Integer status;

    /**
     * 注册引导状态。
     *
     * <p>用于把账号创建与耗时的图片审核解耦。新用户创建后可以获得受限登录态，
     * 但客户端应根据该字段引导其完成基准正面照上传。</p>
     */
    private String onboardingStatus;

    /**
     * 最后登录时间
     *
     * <p>记录用户最近一次成功登录的时间戳。
     * 用于用户活跃度分析和安全监控。</p>
     *
     * @see #lastLoginIp
     */
    private LocalDateTime lastLoginTime;

    /**
     * 最后登录IP
     *
     * <p>记录用户最近一次成功登录的IP地址。
     * 用于安全审计和异常登录检测。</p>
     *
     * @see #lastLoginTime
     */
    private String lastLoginIp;

    /**
     * 最后心跳时间
     *
     * <p>记录用户最近一次客户端心跳的时间戳。
     * 用于判定用户是否在线（5分钟内有心跳即为在线），参与曝光权重活跃度系数计算。</p>
     */
    private LocalDateTime lastHeartbeatTime;

    /** Sets openId. For persistence/construction use only. */
    public void setOpenId(String value) {
        this.openId = value;
    }

    /** Sets username. For persistence/construction use only. */
    public void setUsername(String value) {
        this.username = value;
    }

    /** Sets password. For persistence/construction use only. */
    public void setPassword(String value) {
        this.password = value;
    }

    /** Sets unionId. For persistence/construction use only. */
    public void setUnionId(String value) {
        this.unionId = value;
    }

    /** Sets accessToken. For persistence/construction use only. */
    public void setAccessToken(String value) {
        this.accessToken = value;
    }

    /** Sets refreshToken. For persistence/construction use only. */
    public void setRefreshToken(String value) {
        this.refreshToken = value;
    }

    /** Sets tokenExpireTime. For persistence/construction use only. */
    public void setTokenExpireTime(LocalDateTime value) {
        this.tokenExpireTime = value;
    }

    /** Sets nickName. For persistence/construction use only. */
    public void setNickName(String value) {
        this.nickName = value;
    }

    /** Sets avatarUrl. For persistence/construction use only. */
    public void setAvatarUrl(String value) {
        this.avatarUrl = value;
    }

    /** Sets gender. For persistence/construction use only. */
    public void setGender(Integer value) {
        this.gender = value;
    }

    /** Sets birthday. For persistence/construction use only. */
    public void setBirthday(LocalDate value) {
        this.birthday = value;
    }

    /** Sets country. For persistence/construction use only. */
    public void setCountry(String value) {
        this.country = value;
    }

    /** Sets province. For persistence/construction use only. */
    public void setProvince(String value) {
        this.province = value;
    }

    /** Sets city. For persistence/construction use only. */
    public void setCity(String value) {
        this.city = value;
    }

    /** Sets language. For persistence/construction use only. */
    public void setLanguage(String value) {
        this.language = value;
    }

    /** Sets phone. For persistence/construction use only. */
    public void setPhone(String value) {
        this.phone = value;
    }

    /** Sets status. For persistence/construction use only. */
    public void setStatus(Integer value) {
        this.status = value;
    }

    /** Sets onboardingStatus. For persistence/construction use only. */
    public void setOnboardingStatus(String value) {
        this.onboardingStatus = value;
    }

    /** Sets lastLoginTime. For persistence/construction use only. */
    public void setLastLoginTime(LocalDateTime value) {
        this.lastLoginTime = value;
    }

    /** Sets lastLoginIp. For persistence/construction use only. */
    public void setLastLoginIp(String value) {
        this.lastLoginIp = value;
    }

    /** Sets lastHeartbeatTime. For persistence/construction use only. */
    public void setLastHeartbeatTime(LocalDateTime value) {
        this.lastHeartbeatTime = value;
    }

    /** Sets deptId. For persistence/construction use only. */
    public void setDeptId(Long value) {
        this.deptId = value;
    }

    /** Sets tenantId. For persistence/construction use only. */
    public void setTenantId(String value) {
        this.tenantId = value;
    }

    /**
     * 公共构造函数
     *
     * <p>提供默认构造函数，允许通过直接字段赋值方式创建实例。
     * 建议使用{@link #create(String, String)}工厂方法创建实例。</p>
     *
     * @see #create(String, String)
     */
    public UserEntity() {
        super();
    }

    /**
     * 静态工厂方法：创建新用户（最小化版本）
     *
     * <p>用于首次注册时创建用户实体，自动设置默认状态和时间戳。
     * 此方法创建的用户为启用状态（status=1），未删除（deleted=0）。</p>
     *
     * <h3>使用示例</h3>
     * <pre>{@code * UserEntity user = UserEntity.create(openId, unionId);
     * }</pre>
     *
     * @param openId 微信OpenID，不能为空
     * @param unionId 微信UnionID，可为空（用户未绑定开放平台时）
     * @return 新创建的用户实体实例
     * @throws IllegalArgumentException 如果openId为空
     */
    public static UserEntity create(String openId, String unionId) {
        if (openId == null || openId.isBlank()) {
            throw new IllegalArgumentException("OpenID不能为空");
        }

        LocalDateTime now = LocalDateTime.now();
        UserEntity user = new UserEntity();
        user.openId = openId;
        user.unionId = unionId;
        user.status = UserStatus.ENABLED.getCode();
        user.deleted = BaseEntity.LogicalDeletion.NOT_DELETED.getCode();
        user.setCreateTime(now);
        user.setUpdateTime(now);
        return user;
    }

    /**
     * 领域行为：更新微信Token信息
     *
     * <p>当用户完成微信授权登录或刷新Token时调用此方法。
     * 自动更新AccessToken、RefreshToken及其过期时间，同时更新时间戳。</p>
     *
     * <h3>业务规则</h3>
     * <ul>
     *   <li>AccessToken和RefreshToken必须同时更新</li>
     *   <li>过期时间由微信接口返回，通常为7200秒（2小时）</li>
     *   <li>自动更新updateTime以记录最近修改</li>
     * </ul>
     *
     * @param accessToken 新的微信AccessToken
     * @param refreshToken 新的微信RefreshToken
     * @param expireTime AccessToken的过期时间点
     * @see #accessToken
     * @see #refreshToken
     * @see #tokenExpireTime
     */
    public void updateToken(String accessToken, String refreshToken, LocalDateTime expireTime) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.tokenExpireTime = expireTime;
        this.updateTime = LocalDateTime.now();
    }

    /**
     * 领域行为：记录登录信息
     *
     * <p>每次用户成功登录时调用此方法，记录登录时间和IP地址。
     * 用于安全审计、活跃度统计和异常登录检测。</p>
     *
     * <h3>业务规则</h3>
     * <ul>
     *   <li>覆盖上一次的登录记录</li>
     *   <li>自动更新updateTime</li>
     * </ul>
     *
     * @param loginIp 登录时的IP地址
     * @see #lastLoginTime
     * @see #lastLoginIp
     */
    public void recordLogin(String loginIp) {
        this.lastLoginIp = loginIp;
        this.lastLoginTime = LocalDateTime.now();
        this.updateTime = LocalDateTime.now();
    }

    /**
     * 领域行为：检查用户是否启用
     *
     * <p>判断用户账户是否处于可用状态。
     * 禁用的用户无法登录和使用大部分服务功能。</p>
     *
     * @return true-用户已启用，false-用户已禁用
     * @see #status
     */
    public boolean isEnabled() {
        return this.status != null && this.status == UserStatus.ENABLED.getCode();
    }

    /**
     * 领域行为：禁用用户账户
     *
     * <p>将用户状态设置为禁用。
     * 通常用于管理员操作、违规处理或用户主动注销。</p>
     *
     * @see #status
     * @see #isEnabled()
     */
    public void disable() {
        this.status = UserStatus.DISABLED.getCode();
        this.updateTime = LocalDateTime.now();
    }

    /**
     * 领域行为：启用用户账户
     *
     * <p>将用户状态设置为正常。
     * 通常用于封禁解除或重新激活账户。</p>
     *
     * @see #status
     * @see #isEnabled()
     */
    public void enable() {
        this.status = UserStatus.ENABLED.getCode();
        this.updateTime = LocalDateTime.now();
    }

    /**
     * 内部枚举：用户状态
     *
     * <p>定义用户账户的可用状态。
     * 注意：{@link com.apex.scaffold.common.enums.UserStatus} 中也有同名枚举，
     * 数值一致（ENABLED=1 对应 NORMAL=1，DISABLED=0 对应 DISABLED=0），
     * 本枚举为实体内部使用，对外统一引用 {@link com.apex.scaffold.common.enums.UserStatus}。
     *
     * @deprecated 请优先使用 {@link com.apex.scaffold.common.enums.UserStatus}，
     *     本枚举仅为保持实体内部兼容性而保留
     */
    @Deprecated
    private enum UserStatus {
        /** 禁用状态 */
        DISABLED(0),
        /** 正常状态 */
        ENABLED(1);

        private final Integer code;

        /**
         * 创建 {@code UserStatus} 实例。
         *
         * @param code 枚举编码
         */
        UserStatus(Integer code) {
            this.code = code;
        }

        /**
         * 获取{@code code}。
         * @return 操作结果
         */
        public Integer getCode() {
            return code;
        }
    }
}
