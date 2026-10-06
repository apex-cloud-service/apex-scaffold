package com.apex.scaffold.infrastructure.persistence.po;

import com.apex.scaffold.common.persistence.po.BasePO;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 用户持久化对象
 *
 * <p>对应数据库表 {@code sys_user}，存储用户的完整信息，包括：
 * <ul>
 *   <li>基本信息：用户名、昵称、头像、性别、生日、地理位置</li>
 *   <li>微信信息：OpenID、UnionID、AccessToken、RefreshToken</li>
 *   <li>认证信息：密码（BCrypt 哈希）、手机号</li>
 *   <li>组织信息：部门ID、租户ID（多租户隔离）</li>
 *   <li>状态信息：启用状态、注册引导状态、最后登录时间/IP、心跳时间</li>
 * </ul>
 *
 * <h2>表结构</h2>
 * <p>表名：{@code sys_user}<br>
 * 主键：{@code id}（继承自 {@link BasePO}）<br>
 * 唯一索引：{@code uk_open_id}（微信OpenID）、{@code uk_username}（登录账号）<br>
 * 普通索引：{@code idx_phone}（手机号）、{@code idx_dept_id}（部门ID）、{@code idx_tenant_id}（租户ID）
 *
 * <h2>使用场景</h2>
 * <ul>
 *   <li>微信 APP 登录：通过 OpenID/UnionID 查询或创建用户</li>
 *   <li>账号密码登录：通过 username 查询用户并验证密码</li>
 *   <li>手机号登录：通过 phone 查询用户</li>
 *   <li>数据权限控制：通过 deptId 和 tenantId 实现部门级、租户级数据隔离</li>
 * </ul>
 *
 * <h2>注意事项</h2>
 * <ul>
 *   <li>密码字段必须存储 BCrypt 哈希值，禁止存储明文</li>
 *   <li>OpenID 和 UnionID 来自微信授权，不可手动修改</li>
 *   <li>AccessToken 有效期 2 小时，过期后需使用 RefreshToken 刷新</li>
 *   <li>多租户场景下 tenantId 必填，单租户场景可为 NULL</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.0.0
 * @see BasePO
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
public class UserPO extends BasePO {

    /**
     * 微信OpenID。
     *
     * <p>用户在微信开放平台的唯一标识。微信注册用户必填，
     * 手机号注册用户在绑定微信前可为 NULL。</p>
     */
    @TableField("open_id")
    private String openId;

    /**
     * 登录账号（账号密码登录）。
     *
     * <p>传统账号密码登录场景下使用，微信/手机号注册用户可为 {@code null}。
     * 全局唯一（{@code uk_username} 唯一索引约束）。</p>
     *
     * @since 2.2.0
     */
    @TableField("username")
    private String username;

    /**
     * 所属部门 ID（数据权限）。
     *
     * <p>对应 {@code sys_user.dept_id} 列（V6 迁移新增），关联 {@code sys_dept.id}，
     * 配合数据权限拦截器实现部门级数据隔离。</p>
     *
     * @since 2.3.0
     */
    @TableField("dept_id")
    private Long deptId;

    /**
     * 租户ID（多租户隔离）。
     *
     * <p>对应 {@code sys_user.tenant_id} 列（V14+ 多租户迁移新增），
     * 实现租户级数据隔离。配合 {@code @TenantAware} 注解和 {@code TenantContext}
     * 自动填充和过滤。多租户场景必填，单租户场景可为 {@code null}。</p>
     *
     * @since 2.5.0
     */
    @TableField("tenant_id")
    private String tenantId;

    /**
     * 登录密码（BCrypt 哈希）。
     *
     * <p>仅账号密码登录使用，禁止存储明文。</p>
     *
     * @since 2.2.0
     */
    @TableField("password")
    @lombok.ToString.Exclude
    private String password;

    /**
     * 微信UnionID。
     *
     * <p>用户在微信开放平台的全局唯一标识，同一用户在不同应用下的UnionID相同。
     */
    @TableField("union_id")
    private String unionId;

    /**
     * 微信AccessToken。
     *
     * <p>微信授权访问令牌，用于调用微信API。
     */
    @TableField("access_token")
    @lombok.ToString.Exclude
    private String accessToken;

    /**
     * 微信RefreshToken。
     *
     * <p>微信授权刷新令牌，用于刷新AccessToken。
     */
    @TableField("refresh_token")
    @lombok.ToString.Exclude
    private String refreshToken;

    /**
     * Token过期时间。
     *
     * <p>微信AccessToken的过期时间。
     */
    @TableField("token_expire_time")
    private LocalDateTime tokenExpireTime;

    /**
     * 昵称。
     *
     * <p>用户的微信昵称。
     */
    @TableField("nick_name")
    private String nickName;

    /**
     * 头像URL。
     *
     * <p>用户的微信头像地址。
     */
    @TableField("avatar_url")
    private String avatarUrl;

    /**
     * 性别。
     *
     * <p>0-未知，1-男，2-女。
     */
    @TableField("gender")
    private Integer gender;

    /**
     * 出生日期。
     *
     * <p>用户的出生日期，用于年龄计算、生日提醒等功能。
     * 格式：{@code yyyy-MM-dd}，可选字段。</p>
     */
    @TableField("birthday")
    private LocalDate birthday;

    /**
     * 国家。
     *
     * <p>用户所在国家。
     */
    @TableField("country")
    private String country;

    /**
     * 省份。
     *
     * <p>用户所在省份。
     */
    @TableField("province")
    private String province;

    /**
     * 城市。
     *
     * <p>用户所在城市。
     */
    @TableField("city")
    private String city;

    /**
     * 语言。
     *
     * <p>用户的语言偏好。
     */
    @TableField("language")
    private String language;

    /**
     * 手机号。
     *
     * <p>用户的手机号码，需用户授权后获取。
     */
    @TableField("phone")
    private String phone;

    /**
     * 用户邮箱。
     *
     * <p>用户的电子邮箱地址，用于 Keycloak OIDC 登录和邮件通知。
     * Keycloak OIDC 登录时从 ID Token 中获取 email claim。</p>
     *
     * @since 2.4.0
     */
    @TableField("email")
    private String email;

    /**
     * Keycloak 用户唯一标识（sub claim）。
     *
     * <p>Keycloak OIDC 登录时从 ID Token 中获取的 subject claim，
     * 用于关联 Keycloak 用户与本地用户账号。全局唯一（{@code uk_keycloak_sub} 唯一索引约束）。
     * 仅 Keycloak SSO 登录用户有值，本地注册用户为 {@code null}。</p>
     *
     * @since 2.4.0
     */
    @TableField("keycloak_sub")
    private String keycloakSub;

    /**
     * 状态。
     *
     * <p>0-禁用，1-正常。
     */
    @TableField("status")
    private Integer status;

    /**
     * 注册引导状态。
     *
     * <p>新注册用户默认为 ONBOARDING_PENDING，基准正面照上传完成后推进为
     * FACE_PROFILE_UPLOADED；历史用户由数据库迁移保持 ACTIVE。</p>
     */
    @TableField("onboarding_status")
    private String onboardingStatus;

    /**
     * 最后登录时间。
     *
     * <p>用户最后登录的时间。
     */
    @TableField("last_login_time")
    private LocalDateTime lastLoginTime;

    /**
     * 最后登录IP。
     *
     * <p>用户最后登录的IP地址。
     */
    @TableField("last_login_ip")
    private String lastLoginIp;

    /**
     * 最后心跳时间。
     *
     * <p>用于判定用户是否在线（5分钟内有心跳即为在线）。
     */
    @TableField("last_heartbeat_time")
    private LocalDateTime lastHeartbeatTime;

    /**
     * 创建用户实体（最小字段版本）。
     *
     * @param openId  微信openId
     * @param unionId 微信unionId（可空）
     * @return 用户实体
     */
    public static UserPO create(String openId, String unionId) {
        UserPO u = new UserPO();
        u.setOpenId(openId);
        u.setUnionId(unionId);
        u.setStatus(1);
        u.setDeleted(0);
        LocalDateTime now = LocalDateTime.now();
        u.setCreateTime(now);
        u.setUpdateTime(now);
        return u;
    }

    /**
     * 更新微信Token信息。
     *
     * @param accessToken  微信accessToken
     * @param refreshToken 微信refreshToken
     * @param expireTime   过期时间
     */
    public void updateToken(String accessToken, String refreshToken, LocalDateTime expireTime) {
        setAccessToken(accessToken);
        setRefreshToken(refreshToken);
        setTokenExpireTime(expireTime);
        setUpdateTime(LocalDateTime.now());
    }

    /**
     * 记录登录信息。
     *
     * @param ip 登录IP
     */
    public void recordLogin(String ip) {
        setLastLoginIp(ip);
        setLastLoginTime(LocalDateTime.now());
        setUpdateTime(LocalDateTime.now());
    }
}
