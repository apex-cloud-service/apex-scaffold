package com.apex.scaffold.interfaces.config;

import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 事务配置类
 *
 * <p>为项目提供事务管理支持。由于项目使用 MyBatis-Plus + MySQL（阻塞数据源），
 * 本配置基于 {@link DataSourceTransactionManager} 创建事务管理器。
 *
 * <p>在 WebFlux 环境中，Application 层的事务操作应该通过 {@code subscribeOn(blockingScheduler)}
 * 在阻塞线程池上执行，避免阻塞 Netty 事件循环线程。
 *
 * <p>应用上下文显式排除了 {@code R2dbcAutoConfiguration}（未引入 R2DBC），因此不装配
 * 任何响应式事务 Bean；响应式事务由各微服务（如 apex-office-service）按需自行配置。
 *
 * @author Apex Team
 * @since 1.2.0
 */
@Configuration
public class TransactionConfig {

    /**
     * 配置平台事务管理器
     *
     * <p>基于 JDBC DataSource 创建事务管理器，用于 MyBatis-Plus 的事务管理。
     *
     * @param dataSource 数据源
     * @return 平台事务管理器
     */
    @Bean
    public PlatformTransactionManager transactionManager(DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }
}
