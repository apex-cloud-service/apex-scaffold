package com.apex.scaffold.infrastructure.persistence.po;

import com.apex.scaffold.common.persistence.po.BasePO;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 登录日志持久化对象。
 *
 * <p>对应数据库中的 {@code sys_login_log} 表，存储登录尝试记录。
 * 每次登录（无论成败）写入一条日志，供安全审计与异常排查。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@TableName("sys_login_log")
public class LoginLogPO extends BasePO {

    /**
     * 登录账号。
     */
    private String username;

    /**
     * 客户端 IP 地址。
     */
    private String ip;

    /**
     * 浏览器信息。
     */
    private String browser;

    /**
     * 操作系统信息。
     */
    private String os;

    /**
     * 登录状态（0-成功，1-失败）。
     */
    private Integer status;

    /**
     * 登录消息。
     */
    private String message;

    /**
     * 登录时间。
     */
    private LocalDateTime loginTime;

    /**
     * 客户端类型（PC_ADMIN/PC_WEB/WECHAT_MP/MINI_PROGRAM/APP_ANDROID/APP_IOS/H5/UNKNOWN）。
     *
     * <p>对应 V59 迁移新增列，当前登录请求未携带 X-Client-Type 时为空。</p>
     */
    private String clientType;

    /**
     * 客户端平台（android/ios/h5/harmony/wechat）。
     */
    private String platform;
}
