package com.apex.scaffold.infrastructure.mail;

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
public class MailConfigurationProperties {

    /** SMTP 主机 */
    private String host;

    /** SMTP 端口 */
    private Integer port;

    /** 发件人账号 */
    private String username;

    /** 发件人密码（环境变量注入） */
    private String password;

    /** @return SMTP 主机 */
    public String getHost() { return host; }

    /** @param host SMTP 主机 */
    public void setHost(String host) { this.host = host; }

    /** @return SMTP 端口 */
    public Integer getPort() { return port; }

    /** @param port SMTP 端口 */
    public void setPort(Integer port) { this.port = port; }

    /** @return 发件人账号 */
    public String getUsername() { return username; }

    /** @param username 发件人账号 */
    public void setUsername(String username) { this.username = username; }

    /** @return 发件人密码 */
    public String getPassword() { return password; }

    /** @param password 发件人密码 */
    public void setPassword(String password) { this.password = password; }
}
