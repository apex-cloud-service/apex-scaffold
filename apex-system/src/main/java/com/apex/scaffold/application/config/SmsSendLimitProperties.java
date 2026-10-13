package com.apex.scaffold.application.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 短信发送手机号维度限流配置。
 *
 * <p>IP 与设备指纹限流由接口层统一过滤器负责，本配置控制同一手机号在一个时间窗口内
 * 可以触发的短信次数，防止跨 IP 的短信轰炸。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 3.2.0
 */
@Data
@Component
@ConfigurationProperties(prefix = "apex.security.sms-send-limit")
public class SmsSendLimitProperties {

    /** 单个手机号在窗口内最多允许发送的次数。 */
    private int maxAttempts = 3;

    /** 限流窗口秒数。 */
    private long windowSeconds = 300L;
}
