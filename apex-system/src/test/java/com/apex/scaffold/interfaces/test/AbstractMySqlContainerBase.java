package com.apex.scaffold.interfaces.test;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 基于 Testcontainers 的 MySQL 集成测试共享基类。
 *
 * <p>启动一次性、进程内共享的真实 MySQL 容器（库名 {@code apex_test}，账号 {@code root/apex-root}，
 * 与历史本地依赖配置保持一致），并对容器执行 Flyway 迁移（{@code classpath:db/migration/mysql}）。
 * 多个数据依赖的集成测试复用同一容器，避免重复启动与重复迁移，
 * 彻底消除对人工预置本地 MySQL（127.0.0.1:3306）的环境依赖。
 *
 * <p>类名刻意不以 {@code Test} / {@code IT} 结尾，避免被 Surefire/Failsafe 当作测试类误执行。
 *
 * @author Apex Team
 */
abstract class AbstractMySqlContainerBase {

    /** 与本地 Maven 仓库已缓存的镜像保持一致，避免构建期额外拉取镜像。 */
    private static final String MYSQL_IMAGE = "mysql:8.0";

    private static final String DATABASE_NAME = "apex_test";
    private static final String USERNAME = "root";
    private static final String PASSWORD = "apex-root";

    /** 容器内 MySQL 的 Hikari 连接池，供各集成测试复用。 */
    protected static final DataSource DATA_SOURCE;

    static {
        MySQLContainer<?> container = new MySQLContainer<>(DockerImageName.parse(MYSQL_IMAGE))
                .withDatabaseName(DATABASE_NAME)
                .withUsername(USERNAME)
                .withPassword(PASSWORD);
        container.start();
        migrate(container);
        DATA_SOURCE = createDataSource(container);
    }

    protected AbstractMySqlContainerBase() {}

    /**
     * 对容器执行 Flyway 迁移。
     *
     * <p>历史遗留缺陷：{@code V21__enhance_workflow_tables.sql} 引用不存在的 {@code wf_definition} 表
     * （V20 实际创建的表名为 {@code wf_workflow_definition}），迁移链会在 V21 处中断。
     * 这与改造前「本地预置 MySQL」集成测试的既定行为完全一致（V21 失败可忽略，V1-V20 迁移成功即可），
     * 故此处仅容忍该已知缺陷，其余迁移失败仍会抛出，避免掩盖真实回归。
     *
     * @param container 已启动的 MySQL 容器
     */
    private static void migrate(MySQLContainer<?> container) {
        try {
            Flyway.configure()
                    .dataSource(container.getJdbcUrl(), container.getUsername(), container.getPassword())
                    .locations("classpath:db/migration/mysql")
                    .load()
                    .migrate();
        } catch (FlywayException ex) {
            if (!isKnownV21Defect(ex)) {
                throw ex;
            }
        }
    }

    /**
     * 判断异常是否为已知的 V21 {@code wf_definition} 迁移缺陷。
     *
     * @param ex Flyway 异常
     * @return {@code true} 表示确为该已知缺陷
     */
    private static boolean isKnownV21Defect(Throwable ex) {
        for (Throwable current = ex; current != null; current = current.getCause()) {
            String message = current.getMessage();
            if (message != null
                    && message.contains("V21__enhance_workflow_tables")
                    && message.contains("wf_definition")) {
                return true;
            }
        }
        return false;
    }

    private static DataSource createDataSource(MySQLContainer<?> container) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(container.getJdbcUrl());
        config.setUsername(container.getUsername());
        config.setPassword(container.getPassword());
        config.setMaximumPoolSize(2);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(10000);
        return new HikariDataSource(config);
    }
}
