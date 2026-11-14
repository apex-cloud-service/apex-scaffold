package com.apex.scaffold.officeservice.config;

import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 站内信同步 JDBC 事务装配，避免已有响应式空事务管理器阻止自动配置。
 * @author Apex Team
 * @since 1.3.1
 */
@Configuration
public class MessageTransactionConfig {
    /**
     * 为消息与接收人的同步写入提供真实本地事务。
     * @param dataSource 办公数据库
     * @return JDBC 事务管理器
     */
    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean(PlatformTransactionManager.class)
    public PlatformTransactionManager messageJdbcTransactionManager(DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }
}
