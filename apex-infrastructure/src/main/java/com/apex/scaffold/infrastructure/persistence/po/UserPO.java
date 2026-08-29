package com.apex.scaffold.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户持久化对象。
 *
 * <p>对应数据库中的sys_user表，存储用户的基本信息、微信认证信息和登录信息。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
public class UserPO extends BasePO {

    /**
     * 构造用户持久化对象。
     */
    public UserPO() {}

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
     * 登录密码（BCrypt 哈希）。
     *
     * <p>仅账号密码登录使用，禁止存储明文。</p>
     *
     * @since 2.2.0
     */
    @TableField("password")
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
    private String accessToken;

    /**
     * 微信RefreshToken。
     *
     * <p>微信授权刷新令牌，用于刷新AccessToken。
     */
    @TableField("refresh_token")
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

    /** 出生日期。 */
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
