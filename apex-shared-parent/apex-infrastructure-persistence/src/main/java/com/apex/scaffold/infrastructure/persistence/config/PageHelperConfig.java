package com.apex.scaffold.infrastructure.persistence.config;

import com.github.pagehelper.PageInterceptor;
import java.util.Properties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * PageHelper分页插件配置类
 *
 * <p>配置PageHelper分页插件参数
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Configuration
@lombok.NoArgsConstructor
public class PageHelperConfig {

    /**
     * PageHelper 拦截器配置
     *
     * <p>配置分页插件参数
     *
     * @return PageInterceptor
     */
    @Bean
    public PageInterceptor pageInterceptor() {
        PageInterceptor interceptor = new PageInterceptor();
        Properties properties = new Properties();
        properties.setProperty("helperDialect", "mysql");
        properties.setProperty("reasonable", "true");
        properties.setProperty("supportMethodsArguments", "true");
        properties.setProperty("params", "count=countSql");
        properties.setProperty("autoRuntimeDialect", "true");
        interceptor.setProperties(properties);
        return interceptor;
    }
}
