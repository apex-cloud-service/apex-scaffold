package com.apex.scaffold.application.config;

import static io.r2dbc.spi.ConnectionFactoryOptions.*;

import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import io.r2dbc.spi.Option;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

/**
 * R2DBC 连接测试配置工具。
 *
 * <p>提供临时 R2DBC 连接工厂创建方法，用于数据库连接测试。
 * 不使用 Spring 管理的连接池，每次测试创建临时连接。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.2.0
 */
@Slf4j
@Configuration
public class R2dbcTestConfig {

    /**
     * 创建临时 R2DBC 连接工厂用于测试。
     *
     * @param dbType   数据库类型 (mysql, postgresql)
     * @param host     主机地址
     * @param port     端口号
     * @param database 数据库名
     * @param username 用户名
     * @param password 密码
     * @return R2DBC 连接工厂
     */
    public static ConnectionFactory createTestConnectionFactory(
            String dbType, String host, Integer port, String database, String username, String password) {

        ConnectionFactoryOptions.Builder optionsBuilder = ConnectionFactoryOptions.builder()
                .option(DRIVER, getDriverName(dbType))
                .option(HOST, host)
                .option(PORT, port)
                .option(DATABASE, database)
                .option(USER, username)
                .option(PASSWORD, password)
                .option(CONNECT_TIMEOUT, Duration.ofSeconds(5));

        if ("mysql".equalsIgnoreCase(dbType)) {
            optionsBuilder.option(Option.valueOf("sslMode"), "DISABLED");
        }

        if ("postgresql".equalsIgnoreCase(dbType)) {
            optionsBuilder.option(Option.valueOf("sslMode"), "DISABLE");
        }

        return ConnectionFactories.get(optionsBuilder.build());
    }

    /**
     * 获取 R2DBC 驱动名称。
     *
     * @param dbType 数据库类型
     * @return R2DBC 驱动名称
     */
    private static String getDriverName(String dbType) {
        return switch (dbType.toLowerCase()) {
            case "mysql" -> "mysql";
            case "postgresql", "postgres" -> "postgresql";
            default -> throw new IllegalArgumentException("不支持的数据库类型: " + dbType);
        };
    }
}
