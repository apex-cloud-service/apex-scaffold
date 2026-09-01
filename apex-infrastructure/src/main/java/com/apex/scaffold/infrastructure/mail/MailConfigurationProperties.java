package com.apex.scaffold.infrastructure.mail;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 邮件配置属性（spring.mail 前缀）。
 *
 * <p>声明 SMTP 服务器关键配置，供契约测试校验配置完整性；
 * 实际绑定由 Spring Boot 自动配置 {@code JavaMailSender} 完成。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 2.2.0
 */
@ConfigurationProperties(prefix = "spring.mail")
@Data
public class MailConfigurationProperties {

    /** SMTP 主机 */
    private String host;

    /** SMTP 端口 */
    private Integer port;

    /** 发件人账号 */
    private String username;

    /** 发件人密码（环境变量注入） */
    private String password;
}
