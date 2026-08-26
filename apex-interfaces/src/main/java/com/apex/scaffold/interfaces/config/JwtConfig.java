package com.apex.scaffold.interfaces.config;

import com.apex.scaffold.common.config.JwtProperties;
import com.apex.scaffold.common.util.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * JWT 运行时配置初始化。
 *
 * <p>通过 {@link ApplicationRunner} 在 Spring 容器完全启动后将 YAML 中 {@code jwt.*} 配置
 * 注入到静态工具类 {@link JwtUtil} 中，使其默认过期时间和最小密钥长度可通过配置文件调整。
 *
 * @author Apex Team
 * @version 1.1.0
 * @since 2.1.0
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {

    /**
     * 容器启动后立即将 JwtProperties 配置注入 JwtUtil。
     *
     * <p>使用 {@link ApplicationRunner} 确保所有 Bean（包括 {@link JwtProperties}）
     * 已完成绑定，且 JwtUtil 在第一次业务调用前已就绪。</p>
     *
     * @param properties JWT 配置属性
     * @return ApplicationRunner 实例
     */
    @Bean
    public ApplicationRunner jwtUtilInitializer(JwtProperties properties) {
        return args -> JwtUtil.applyConfig(properties);
    }
}
