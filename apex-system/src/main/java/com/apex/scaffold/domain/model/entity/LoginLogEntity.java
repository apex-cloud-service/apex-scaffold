package com.apex.scaffold.domain.model.entity;

import java.time.LocalDateTime;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * 登录日志聚合根实体。
 *
 * <p>记录每次登录尝试的完整信息，包括账号、来源 IP、浏览器/操作系统
 * 环境与登录结果，供安全审计与异常登录排查使用，满足等保合规要求。</p>
 *
 * <h2>核心业务规则</h2>
 * <ul>
 *   <li>每次登录尝试（无论成败）生成一条日志</li>
 *   <li>登录结果分为成功（0）与失败（1），失败时记录失败原因</li>
 *   <li>{@code username} 记录登录账号，便于按账号回溯登录历史</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see BaseEntity
 */
@Getter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class LoginLogEntity extends BaseEntity {

    /**
     * 序列化版本号。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 登录账号。
     *
     * <p>用户输入或系统解析的登录标识，例如手机号或用户名。</p>
     */
    private String username;

    /**
     * 客户端 IP 地址。
     */
    private String ip;

    /**
     * 浏览器信息。
     *
     * <p>从 User-Agent 解析的浏览器名称与版本。</p>
     */
    private String browser;

    /**
     * 操作系统信息。
     *
     * <p>从 User-Agent 解析的操作系统名称与版本。</p>
     */
    private String os;

    /**
     * 登录状态。
     *
     * <p>取值说明：0-成功，1-失败。</p>
     */
    private Integer status;

    /**
     * 登录消息。
     *
     * <p>成功时的提示或失败时的原因描述。</p>
     */
    private String message;

    /**
     * 登录时间。
     */
    private LocalDateTime loginTime;

    /**
     * 客户端类型。
     *
     * <p>多端客户端标识（PC_ADMIN/PC_WEB/WECHAT_MP/MINI_PROGRAM/APP_ANDROID/APP_IOS/H5），
     * 请求未携带 X-Client-Type 时为空，用于按终端维度审计登录来源。</p>
     */
    private String clientType;

    /**
     * 客户端平台。
     *
     * <p>客户端上报的平台标识（android/ios/h5/harmony/wechat）。</p>
     */
    private String platform;

    /** Sets clientType. For persistence/construction use only. */
    public void setClientType(String value) {
        this.clientType = value;
    }

    /** Sets platform. For persistence/construction use only. */
    public void setPlatform(String value) {
        this.platform = value;
    }

    /** Sets username. For persistence/construction use only. */
    public void setUsername(String value) {
        this.username = value;
    }

    /** Sets ip. For persistence/construction use only. */
    public void setIp(String value) {
        this.ip = value;
    }

    /** Sets browser. For persistence/construction use only. */
    public void setBrowser(String value) {
        this.browser = value;
    }

    /** Sets os. For persistence/construction use only. */
    public void setOs(String value) {
        this.os = value;
    }

    /** Sets status. For persistence/construction use only. */
    public void setStatus(Integer value) {
        this.status = value;
    }

    /** Sets message. For persistence/construction use only. */
    public void setMessage(String value) {
        this.message = value;
    }

    /** Sets loginTime. For persistence/construction use only. */
    public void setLoginTime(LocalDateTime value) {
        this.loginTime = value;
    }

    /**
     * 默认构造函数。
     */
    public LoginLogEntity() {
        super();
    }

    /**
     * 静态工厂方法：创建登录日志。
     *
     * @param username 登录账号
     * @param ip 客户端 IP
     * @param browser 浏览器信息
     * @param os 操作系统信息
     * @param success 是否成功
     * @param message 登录消息
     * @return 新创建的登录日志实体
     */
    public static LoginLogEntity create(
            String username, String ip, String browser, String os, boolean success, String message) {
        LoginLogEntity entity = new LoginLogEntity();
        entity.username = username;
        entity.ip = ip;
        entity.browser = browser;
        entity.os = os;
        entity.status = success ? LoginStatus.SUCCESS.getCode() : LoginStatus.FAILED.getCode();
        entity.message = message;
        entity.loginTime = LocalDateTime.now();
        entity.initTimestamps();
        return entity;
    }

    /**
     * 领域行为：检查登录是否成功。
     *
     * @return true-成功，false-失败
     */
    public boolean isSuccess() {
        return status != null && status.equals(LoginStatus.SUCCESS.getCode());
    }

    /**
     * 登录状态枚举。
     */
    public enum LoginStatus {
        /** 成功 */
        SUCCESS(0),
        /** 失败 */
        FAILED(1);

        private final Integer code;

        /**
         * 创建 {@code LoginStatus} 实例。
         *
         * @param code 枚举编码
         */
        LoginStatus(Integer code) {
            this.code = code;
        }

        /**
         * 获取枚举编码。
         *
         * @return 枚举编码
         */
        public Integer getCode() {
            return code;
        }
    }
}
