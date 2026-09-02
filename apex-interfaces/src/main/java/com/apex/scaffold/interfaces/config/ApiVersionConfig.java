package com.apex.scaffold.interfaces.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.config.PathMatchConfigurer;
import org.springframework.web.reactive.config.WebFluxConfigurer;

/**
 * API版本管理配置
 *
 *
 *<p>本工程基于Spring WebFlux函数式路由,URL路径版本控制
 *(如 /api/v1/users、/api/v2/users)由各路由直接在RouterFunction中注册,
 *此处统一配置WebFlux路径匹配规则。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Configuration
public class ApiVersionConfig implements WebFluxConfigurer {

    /**
     * 配置API路径匹配规则
     */
    @Override
    public void configurePathMatching(PathMatchConfigurer configurer) {
        // 兼容结尾斜杠,如 /api/v1/users 与 /api/v1/users/ 均视为同一路由
        configurer.setUseTrailingSlashMatch(true);
    }
}
