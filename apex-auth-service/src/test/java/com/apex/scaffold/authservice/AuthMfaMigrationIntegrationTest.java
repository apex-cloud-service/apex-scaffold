package com.apex.scaffold.authservice;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.auth.domain.model.MfaConfig;
import com.apex.scaffold.auth.infrastructure.repository.MfaConfigRepository;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactoryOptions;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.r2dbc.repository.support.R2dbcRepositoryFactory;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.PostgreSQLContainer;

/** 认证服务 MFA 迁移（V51–V59）及独立 apex_auth 库的真实 MySQL/PostgreSQL 回归，使用 Testcontainers 托管容器生命周期。 */
class AuthMfaMigrationIntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    @ParameterizedTest
    @ValueSource(strings = {"mysql", "postgresql"})
    void shouldMigrateIndependentAuthDatabaseAndUseMfaColumns(String vendor) throws Exception {
        verifyDatabase(vendor, false);
    }

    @ParameterizedTest
    @ValueSource(strings = {"mysql", "postgresql"})
    void shouldPreserveCompatibleManuallyCreatedTable(String vendor) throws Exception {
        verifyDatabase(vendor, true);
    }

    private static void verifyDatabase(String vendor, boolean existingTable) throws Exception {
        boolean mysql = vendor.equals("mysql");
        String database = "apex_mfa_" + UUID.randomUUID().toString().replace("-", "");
        JdbcDatabaseContainer<?> rootContainer = mysql
                ? new MySQLContainer<>("mysql:9.0").withUsername("root")
                : new PostgreSQLContainer<>("postgres:15");
        rootContainer
                .withDatabaseName(mysql ? "mysql" : "postgres")
                .withUrlParam("connectTimeout", mysql ? "5000" : "5")
                .withUrlParam("socketTimeout", mysql ? "10000" : "10");
        if (!mysql) {
            rootContainer.withUrlParam("loginTimeout", "10");
        }
        rootContainer.start();
        try {
            String host = rootContainer.getHost();
            Integer port = rootContainer.getMappedPort(mysql ? 3306 : 5432);
            String user = mysql ? "root" : database;
            String password =
                    mysql ? rootContainer.getPassword() : UUID.randomUUID().toString();
            if (mysql) {
                try (Connection connection = DriverManager.getConnection(
                                jdbcUrl(vendor, host, port, "mysql"),
                                rootContainer.getUsername(),
                                rootContainer.getPassword());
                        var statement = connection.createStatement()) {
                    statement.execute("CREATE DATABASE " + database);
                }
            } else {
                try (Connection connection = DriverManager.getConnection(
                                jdbcUrl(vendor, host, port, "postgres"),
                                rootContainer.getUsername(),
                                rootContainer.getPassword());
                        var statement = connection.createStatement()) {
                    statement.execute("CREATE ROLE " + user + " LOGIN PASSWORD '" + password + "'");
                    statement.execute("CREATE DATABASE " + database + " OWNER " + user + " TEMPLATE template0");
                }
            }
            Path migrations = Path.of("src/main/resources/db/migration", vendor).toAbsolutePath();
            Flyway baseline = Flyway.configure()
                    .dataSource(jdbcUrl(vendor, host, port, database), user, password)
                    .locations("filesystem:" + migrations)
                    .target("57")
                    .cleanDisabled(true)
                    .load();
            assertThat(baseline.migrate().migrationsExecuted).isEqualTo(14);
            try (var connection = baseline.getConfiguration().getDataSource().getConnection();
                    var statement = connection.createStatement()) {
                if (existingTable) {
                    statement.execute("CREATE TABLE mfa_config (id "
                            + (mysql ? "BIGINT AUTO_INCREMENT" : "BIGSERIAL")
                            + " PRIMARY KEY, user_id VARCHAR(64) NOT NULL, tenant_id VARCHAR(64) NOT NULL,"
                            + " type VARCHAR(20) NOT NULL, status VARCHAR(20) NOT NULL DEFAULT 'PENDING',"
                            + " secret TEXT, backup_codes TEXT, device_info VARCHAR(255), last_verified_at TIMESTAMP,"
                            + " failed_attempts INT NOT NULL DEFAULT 0, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                            + " updated_at TIMESTAMP, UNIQUE (tenant_id,user_id,type))");
                    statement.executeUpdate("INSERT INTO mfa_config (user_id,tenant_id,type,secret)"
                            + " VALUES ('existing-user','existing-tenant','TOTP','opaque-test-ciphertext')");
                }
            }
            Flyway flyway = Flyway.configure()
                    .dataSource(jdbcUrl(vendor, host, port, database), user, password)
                    .locations("filesystem:" + migrations)
                    .cleanDisabled(true)
                    .validateMigrationNaming(true)
                    .load();
            flyway.migrate();
            try (var connection = flyway.getConfiguration().getDataSource().getConnection();
                    var statement = connection.createStatement()) {
                try (var rows = statement.executeQuery("SELECT id,user_id,tenant_id,type,status,secret,backup_codes,"
                        + "device_info,last_verified_at,failed_attempts,created_at,updated_at FROM mfa_config")) {
                    assertThat(rows.getMetaData().getColumnCount()).isEqualTo(12);
                    assertThat(rows.next()).isEqualTo(existingTable);
                    if (existingTable) {
                        assertThat(rows.getString("secret")).isEqualTo("opaque-test-ciphertext");
                    }
                }
                statement.executeUpdate("INSERT INTO mfa_config (user_id,tenant_id,type) VALUES"
                        + " ('shared-user','tenant-a','SMS'),('shared-user','tenant-b','SMS')");
                try (var rows = statement.executeQuery("SELECT status,failed_attempts,created_at FROM mfa_config"
                        + " WHERE user_id='shared-user' AND tenant_id='tenant-a' AND type='SMS'")) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getString(1)).isEqualTo("PENDING");
                    assertThat(rows.getInt(2)).isZero();
                    assertThat(rows.getTimestamp(3)).isNotNull();
                }
                ScriptUtils.executeSqlScript(
                        connection, new FileSystemResource(migrations.resolve("V58__create_reactive_mfa_config.sql")));
            }
            verifyReactiveRepository(vendor, host, port, database, user, password);
            flyway.validate();
            assertThat(flyway.info().applied()).hasSize(16);
            assertThat(flyway.info().pending()).isEmpty();
            assertThat(flyway.migrate().migrationsExecuted).isZero();
        } finally {
            rootContainer.close();
        }
    }

    private static void verifyReactiveRepository(
            String vendor, String host, int port, String database, String user, String password) {
        var options = ConnectionFactoryOptions.builder()
                .option(ConnectionFactoryOptions.DRIVER, vendor)
                .option(ConnectionFactoryOptions.HOST, host)
                .option(ConnectionFactoryOptions.PORT, port)
                .option(ConnectionFactoryOptions.DATABASE, database)
                .option(ConnectionFactoryOptions.USER, user)
                .option(ConnectionFactoryOptions.PASSWORD, password)
                .build();
        var repository = new R2dbcRepositoryFactory(new R2dbcEntityTemplate(ConnectionFactories.get(options)))
                .getRepository(MfaConfigRepository.class);
        var saved = repository
                .save(MfaConfig.builder()
                        .userId("reactive-user")
                        .tenantId("tenant-a")
                        .type(MfaConfig.MfaType.TOTP)
                        .status(MfaConfig.MfaStatus.PENDING)
                        .secret("opaque-test-ciphertext")
                        .backupCodes("opaque-backup-ciphertext")
                        .deviceInfo("test-device")
                        .failedAttempts(0)
                        .createdAt(LocalDateTime.now())
                        .build())
                .block(TIMEOUT);
        assertThat(saved.getId()).isNotNull();
        var loaded = repository
                .findByUserIdAndTypeAndTenantId("reactive-user", MfaConfig.MfaType.TOTP, "tenant-a")
                .block(TIMEOUT);
        assertThat(loaded.getSecret()).isEqualTo(saved.getSecret());
        assertThat(loaded.getBackupCodes()).isEqualTo(saved.getBackupCodes());
        loaded.setStatus(MfaConfig.MfaStatus.ACTIVE);
        loaded.setLastVerifiedAt(LocalDateTime.now());
        loaded.setUpdatedAt(LocalDateTime.now());
        repository.save(loaded).block(TIMEOUT);
        assertThat(repository
                        .findByUserIdAndTenantId("reactive-user", "tenant-b")
                        .collectList()
                        .block(TIMEOUT))
                .isEmpty();
        assertThat(repository
                        .findByUserIdAndTenantId("reactive-user", "tenant-a")
                        .single()
                        .block(TIMEOUT)
                        .getStatus())
                .isEqualTo(MfaConfig.MfaStatus.ACTIVE);
        repository.deleteById(saved.getId()).block(TIMEOUT);
        assertThat(repository.findById(saved.getId()).block(TIMEOUT)).isNull();
    }

    private static String jdbcUrl(String vendor, String host, int port, String database) {
        return "jdbc:" + vendor + "://" + host + ":" + port + "/" + database
                + (vendor.equals("mysql")
                        ? "?allowPublicKeyRetrieval=true&useSSL=false&connectTimeout=5000&socketTimeout=10000"
                        : "?connectTimeout=5&loginTimeout=10&socketTimeout=10");
    }
}
