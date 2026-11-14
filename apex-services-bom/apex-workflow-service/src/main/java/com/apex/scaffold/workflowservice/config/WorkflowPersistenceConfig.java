package com.apex.scaffold.workflowservice.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import java.util.Properties;
import org.apache.ibatis.mapping.DatabaseIdProvider;
import org.apache.ibatis.mapping.VendorDatabaseIdProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 工作流服务持久化插件配置。
 *
 * <p>为直接使用 Mapper 分页的查询注册分页插件，确保记录与总数使用相同查询条件。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Configuration(proxyBeanMethods = false)
public class WorkflowPersistenceConfig {

    /**
     * 为统计 SQL 提供数据库方言标识。
     *
     * @return MySQL/PostgreSQL 方言标识提供者
     */
    @Bean
    @ConditionalOnMissingBean(DatabaseIdProvider.class)
    public DatabaseIdProvider workflowDatabaseIdProvider() {
        var provider = new VendorDatabaseIdProvider();
        var properties = new Properties();
        properties.setProperty("MySQL", "mysql");
        properties.setProperty("PostgreSQL", "postgresql");
        provider.setProperties(properties);
        return provider;
    }

    /**
     * 创建自动识别数据库方言的分页拦截器链。
     *
     * @return 工作流 MyBatis-Plus 拦截器链
     */
    @Bean
    public MybatisPlusInterceptor workflowMybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor());
        return interceptor;
    }
}
