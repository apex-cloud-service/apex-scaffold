package com.apex.scaffold.infrastructure.persistence.config;

import com.baomidou.mybatisplus.annotation.DbType;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 数据库配置属性。
 *
 * <p>通过 {@code apex.database.type} 配置当前运行时使用的数据库类型，
 * 用于统一驱动 MyBatis-Plus 分页方言、Flyway 迁移目录与 Druid 连接池
 * 数据库类型，避免在多个配置点硬编码数据库方言。</p>
 *
 * <p>首期支持 {@code mysql} 与 {@code postgresql}，其余类型在启动解析时
 * 抛出 {@link IllegalArgumentException}，防止生成不可用的方言配置。</p>
 *
 * <pre>{@code
 * apex:
 *   database:
 *     type: postgresql
 * }</pre>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "apex.database")
public class DatabaseProperties {

    /**
     * 数据库类型。
     *
     * <p>支持 {@code mysql}、{@code postgresql}，默认 {@code mysql}。
     * 不区分大小写。</p>
     */
    private String type = "mysql";

    /**
     * 构造数据库配置属性。
     */
    public DatabaseProperties() {}

    /**
     * 获取数据库类型。
     *
     * @return 数据库类型字符串（小写）
     */
    public String getType() {
        return type;
    }

    /**
     * 设置数据库类型。
     *
     * @param type 数据库类型字符串
     */
    public void setType(String type) {
        this.type = type;
    }

    /**
     * 解析为 MyBatis-Plus {@link DbType} 枚举。
     *
     * @return 对应的 MyBatis-Plus 数据库类型
     * @throws IllegalArgumentException 当数据库类型不受支持时抛出
     */
    public DbType resolveDbType() {
        String normalized = normalize();
        return switch (normalized) {
            case "mysql" -> DbType.MYSQL;
            case "postgresql" -> DbType.POSTGRE_SQL;
            default -> throw new IllegalArgumentException("不支持的数据库类型: " + type);
        };
    }

    /**
     * 获取 Flyway 迁移脚本目录。
     *
     * <p>按数据库类型分目录组织，例如 {@code classpath:db/migration/mysql}。</p>
     *
     * @return Flyway {@code locations} 配置值
     */
    public String flywayLocations() {
        return "classpath:db/migration/" + normalize();
    }

    private String normalize() {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("数据库类型不能为空");
        }
        return type.trim().toLowerCase();
    }
}
