package com.apex.scaffold.application.security;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 图形验证码自动配置。
 *
 * <p>注册 {@link CaptchaProperties} 到 Spring 容器，供
 * {@link CaptchaService} 构造注入使用。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(CaptchaProperties.class)
public class CaptchaConfiguration {}
