package com.apex.scaffold.infrastructure.persistence.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.baomidou.mybatisplus.annotation.DbType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 数据库配置属性单元测试。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
class DatabasePropertiesTest {

    @Test
    @DisplayName("默认数据库类型为 mysql")
    void defaultTypeIsMysql() {
        DatabaseProperties properties = new DatabaseProperties();
        assertThat(properties.getType()).isEqualTo("mysql");
    }

    @Test
    @DisplayName("mysql 解析为 DbType.MYSQL 且迁移目录为 db/migration/mysql")
    void mysqlResolvesToMysqlDialect() {
        DatabaseProperties properties = new DatabaseProperties();
        properties.setType("mysql");

        assertThat(properties.resolveDbType()).isEqualTo(DbType.MYSQL);
        assertThat(properties.flywayLocations()).isEqualTo("classpath:db/migration/mysql");
    }

    @Test
    @DisplayName("postgresql 解析为 DbType.POSTGRE_SQL 且迁移目录为 db/migration/postgresql")
    void postgresqlResolvesToPostgresDialect() {
        DatabaseProperties properties = new DatabaseProperties();
        properties.setType("postgresql");

        assertThat(properties.resolveDbType()).isEqualTo(DbType.POSTGRE_SQL);
        assertThat(properties.flywayLocations()).isEqualTo("classpath:db/migration/postgresql");
    }

    @Test
    @DisplayName("数据库类型不区分大小写")
    void typeIsCaseInsensitive() {
        DatabaseProperties properties = new DatabaseProperties();
        properties.setType("POSTGRESQL");

        assertThat(properties.resolveDbType()).isEqualTo(DbType.POSTGRE_SQL);
    }

    @Test
    @DisplayName("不支持的数据库类型抛出参数异常")
    void unsupportedTypeThrows() {
        DatabaseProperties properties = new DatabaseProperties();
        properties.setType("oracle");

        assertThatThrownBy(properties::resolveDbType)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不支持的数据库类型");
    }
}
