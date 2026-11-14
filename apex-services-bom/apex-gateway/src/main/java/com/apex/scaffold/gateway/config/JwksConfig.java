package com.apex.scaffold.gateway.config;

import com.apex.scaffold.gateway.security.JwksProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * JWKS 验签配置装载.
 *
 * <p>启用 {@link JwksProperties} 的配置绑定（前缀 {@code apex.gateway.jwks}）。
 *
 * <p><b>热刷新</b>：{@code @ConfigurationProperties} Bean 由 Spring Cloud
 * {@code ConfigurationPropertiesRebinder} 在 Nacos 触发 {@code RefreshEvent} 时自动重新绑定，
 * 密钥轮换无需重启网关；因此此处<b>不再</b>额外声明 {@code @RefreshScope} 代理 Bean，
 * 避免与 {@link EnableConfigurationProperties} 自动注册的属性 Bean 产生同类型重复定义。
 *
 * @author Apex Team
 * @since 1.3.1
 */
@Configuration
@EnableConfigurationProperties(JwksProperties.class)
public class JwksConfig {}
