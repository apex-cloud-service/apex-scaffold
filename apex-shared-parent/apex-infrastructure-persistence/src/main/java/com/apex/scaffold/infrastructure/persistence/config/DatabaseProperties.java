package com.apex.scaffold.infrastructure.persistence.config;

import com.baomidou.mybatisplus.annotation.DbType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
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
@Getter
@Setter
@NoArgsConstructor
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

    /**
     * 校验数据库类型非空，并返回去除首尾空白后按默认区域设置转换的小写值。
     *
     * <p>不修改原配置，也不校验数据库类型是否受支持。
     *
     * @return 规范化后的数据库类型
     * @throws IllegalArgumentException 当配置为 {@code null} 或空白字符串时抛出
     */
    private String normalize() {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("数据库类型不能为空");
        }
        return type.trim().toLowerCase();
    }
}
