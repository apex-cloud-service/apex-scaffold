package com.apex.scaffold.interfaces.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 密码编码器配置类
 *
 * <p>提供 {@link PasswordEncoder} Bean 用于密码加密和验证。
 * 独立配置类避免与 {@link SecurityConfig} 产生循环依赖。
 *
 * <p>使用 BCrypt 算法进行密码加密，这是一种自适应哈希函数，
 * 通过加盐和多轮计算提供强大的密码安全性。
 *
 * @author Apex Team
 * @since 1.2.0
 */
@Configuration
public class PasswordEncoderConfig {

    /**
     * 配置密码编码器 Bean
     *
     * <p>使用 BCrypt 算法对密码进行加密，用于用户注册和登录时的密码验证。
     * BCrypt 是一种自适应哈希函数，通过加盐和多轮计算提高安全性。
     *
     * @return BCrypt 密码编码器实例
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
