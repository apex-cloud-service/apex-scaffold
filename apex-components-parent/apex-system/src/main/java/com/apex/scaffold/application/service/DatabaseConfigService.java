package com.apex.scaffold.application.service;

import com.apex.scaffold.application.config.R2dbcTestConfig;
import com.apex.scaffold.application.dto.admin.CurrentDatabaseInfoDTO;
import com.apex.scaffold.application.dto.admin.DatabaseConfigRequest;
import com.apex.scaffold.application.dto.admin.DatabaseTestResultDTO;
import com.apex.scaffold.application.dto.admin.DeploymentConfigResultDTO;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 数据库配置应用服务。
 *
 * <p>提供数据库连接测试（基于 R2DBC）、部署配置生成和当前数据库信息查询功能。
 * 所有方法返回 {@link Mono}，支持响应式编程。</p>
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>连接测试：R2DBC（响应式，非阻塞）</li>
 *   <li>当前数据库信息：JDBC（复用应用主数据源）</li>
 *   <li>配置生成：纯逻辑计算（无 I/O）</li>
 * </ul>
 *
 * @author Apex Team
 * @version 2.0.0
 * @since 1.2.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DatabaseConfigService {

    private static final String CONNECTION_PARAMETER_SEPARATOR = "&";

    private static final String ERROR_TIMEOUT = "timeout";

    private static final String ERROR_CONNECT_TIMEOUT = "connect timed out";

    private static final String ERROR_CONNECTION_REFUSED = "Connection refused";

    private static final String ERROR_UNKNOWN_DATABASE = "Unknown database";

    private static final String ERROR_ACCESS_DENIED = "Access denied";

    private static final String DB_TYPE_MYSQL = "mysql";
    private static final String DB_TYPE_POSTGRESQL = "postgresql";

    private final DataSource dataSource;

    @Value("${spring.datasource.url:}")
    private String currentDatasourceUrl;

    /**
     * 测试数据库连接（R2DBC 响应式实现）。
     *
     * <p>使用 R2DBC 创建临时连接进行测试，完全非阻塞。
     * 测试完成后自动关闭连接，不影响应用主数据源。</p>
     *
     * @param request 数据库配置请求
     * @return 连接测试结果
     */
    public Mono<DatabaseTestResultDTO> testConnection(DatabaseConfigRequest request) {
        log.info(
                "开始R2DBC连接测试: dbType={}, host={}, port={}, database={}",
                request.getDbType(),
                request.getHost(),
                request.getPort(),
                request.getDatabase());

        return Mono.defer(() -> {
            try {
                ConnectionFactory connectionFactory = R2dbcTestConfig.createTestConnectionFactory(
                        request.getDbType(),
                        request.getHost(),
                        request.getPort(),
                        request.getDatabase(),
                        request.getUsername(),
                        request.getPassword());

                return Mono.from(connectionFactory.create())
                        .flatMap(connection -> {
                            String testQuery = getTestQuery(request.getDbType());

                            return Mono.from(connection
                                            .createStatement(testQuery)
                                            .execute())
                                    .flatMap(result ->
                                            Mono.from(result.getRowsUpdated()).defaultIfEmpty(0L))
                                    .then(getConnectionMetadata(connection, request.getDbType()))
                                    .doFinally(signalType -> {
                                        Mono.from(connection.close())
                                                .onErrorResume(e -> {
                                                    log.warn(
                                                            "关闭测试连接时出错: {}",
                                                            e.getClass().getSimpleName());
                                                    return Mono.empty();
                                                })
                                                .subscribe();
                                    });
                        })
                        .map(version -> {
                            log.info("R2DBC连接测试成功: version={}", version);
                            return DatabaseTestResultDTO.builder()
                                    .success(true)
                                    .message("连接成功")
                                    .version(version)
                                    .build();
                        })
                        .onErrorResume(e -> {
                            log.warn("R2DBC连接测试失败: {}", e.getClass().getSimpleName());
                            String errorMessage = extractErrorMessage(e);
                            return Mono.just(DatabaseTestResultDTO.builder()
                                    .success(false)
                                    .message("连接失败: " + errorMessage)
                                    .build());
                        });

            } catch (Exception e) {
                log.warn("创建R2DBC连接工厂失败: {}", e.getClass().getSimpleName());
                return Mono.just(DatabaseTestResultDTO.builder()
                        .success(false)
                        .message("配置错误，请检查数据库连接参数")
                        .build());
            }
        });
    }

    /**
     * 获取数据库版本信息（R2DBC）。
     *
     * @param connection R2DBC 连接
     * @param dbType     数据库类型
     * @return 数据库版本信息
     */
    private Mono<String> getConnectionMetadata(Connection connection, String dbType) {
        String versionQuery = getVersionQuery(dbType);

        return Mono.from(connection.createStatement(versionQuery).execute())
                .flatMap(result -> Mono.from(result.map((row, metadata) -> {
                    Object versionObj = row.get(0);
                    return versionObj != null ? versionObj.toString() : "Unknown";
                })))
                .defaultIfEmpty("Unknown")
                .map(version -> dbType.toUpperCase() + " " + version);
    }

    /**
     * 获取测试查询语句。
     *
     * @param dbType 数据库类型
     * @return 测试 SQL
     */
    private String getTestQuery(String dbType) {
        return switch (dbType.toLowerCase()) {
            case DB_TYPE_MYSQL -> "SELECT 1";
            case DB_TYPE_POSTGRESQL, "postgres" -> "SELECT 1";
            default -> "SELECT 1";
        };
    }

    /**
     * 获取版本查询语句。
     *
     * @param dbType 数据库类型
     * @return 版本查询 SQL
     */
    private String getVersionQuery(String dbType) {
        return switch (dbType.toLowerCase()) {
            case DB_TYPE_MYSQL -> "SELECT VERSION()";
            case DB_TYPE_POSTGRESQL, "postgres" -> "SELECT version()";
            default -> "SELECT VERSION()";
        };
    }

    /**
     * 提取可读的错误信息。
     *
     * @param e 异常
     * @return 错误信息
     */
    private String extractErrorMessage(Throwable e) {
        String message = e.getMessage();
        if (message == null) {
            message = e.getClass().getSimpleName();
        }

        if (message.contains(ERROR_ACCESS_DENIED)) {
            return "认证失败，请检查用户名和密码";
        } else if (message.contains(ERROR_UNKNOWN_DATABASE)) {
            return "数据库不存在";
        } else if (message.contains(ERROR_CONNECTION_REFUSED) || message.contains(ERROR_CONNECT_TIMEOUT)) {
            return "无法连接到数据库服务器，请检查主机和端口";
        } else if (message.contains(ERROR_TIMEOUT)) {
            return "连接超时";
        } else {
            return "数据库连接失败，请检查连接配置";
        }
    }

    /**
     * 生成部署配置。
     *
     * <p>根据提供的数据库配置生成 YAML、环境变量和 Docker Compose 格式的部署配置。</p>
     *
     * @param request 数据库配置请求
     * @return 部署配置生成结果
     */
    public Mono<DeploymentConfigResultDTO> generateDeploymentConfig(DatabaseConfigRequest request) {
        return Mono.fromCallable(() -> {
                    String yaml = generateYamlConfig(request);
                    String env = generateEnvConfig(request);
                    String dockerCompose = generateDockerComposeConfig(request);

                    return DeploymentConfigResultDTO.builder()
                            .yaml(yaml)
                            .env(env)
                            .dockerCompose(dockerCompose)
                            .build();
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /**
     * 获取当前数据库信息。
     *
     * <p>查询当前运行中数据库的类型、版本和驱动版本。</p>
     *
     * @return 当前数据库信息
     */
    public Mono<CurrentDatabaseInfoDTO> getCurrentDatabaseInfo() {
        return Mono.fromCallable(() -> {
                    try (java.sql.Connection connection = dataSource.getConnection()) {
                        DatabaseMetaData metaData = connection.getMetaData();

                        String type = metaData.getDatabaseProductName();
                        String version = metaData.getDatabaseProductVersion();
                        String driverVersion = metaData.getDriverVersion();

                        return CurrentDatabaseInfoDTO.builder()
                                .type(type)
                                .version(version)
                                .driverVersion(driverVersion)
                                .build();

                    } catch (SQLException e) {
                        log.error("获取当前数据库信息失败: type={}", e.getClass().getSimpleName());
                        return CurrentDatabaseInfoDTO.builder()
                                .type("Unknown")
                                .version("Unknown")
                                .driverVersion("Unknown")
                                .build();
                    }
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /**
     * 构建 JDBC URL。
     *
     * @param request 数据库配置请求
     * @return JDBC URL
     */
    private String buildJdbcUrl(DatabaseConfigRequest request) {
        String baseUrl;
        if (DB_TYPE_MYSQL.equalsIgnoreCase(request.getDbType())) {
            baseUrl =
                    String.format("jdbc:mysql://%s:%d/%s", request.getHost(), request.getPort(), request.getDatabase());
        } else if (DB_TYPE_POSTGRESQL.equalsIgnoreCase(request.getDbType())) {
            baseUrl = String.format(
                    "jdbc:postgresql://%s:%d/%s", request.getHost(), request.getPort(), request.getDatabase());
        } else {
            throw new IllegalArgumentException("不支持的数据库类型: " + request.getDbType());
        }

        if (request.getConnectionParams() != null
                && !request.getConnectionParams().isEmpty()) {
            java.util.Set<String> allowed =
                    java.util.Set.of("useSSL", "serverTimezone", "useUnicode", "characterEncoding", "ssl", "sslmode");
            for (String parameter : request.getConnectionParams().split(CONNECTION_PARAMETER_SEPARATOR)) {
                String[] pair = parameter.split("=", 2);
                if (pair.length != 2 || !allowed.contains(pair[0]) || !pair[1].matches("[A-Za-z0-9_./+-]+")) {
                    throw new IllegalArgumentException("不支持的数据库连接参数");
                }
            }
            baseUrl += "?" + request.getConnectionParams();
        }

        return baseUrl;
    }

    /**
     * 生成 YAML 配置。
     *
     * @param request 数据库配置请求
     * @return YAML 格式配置
     */
    private String generateYamlConfig(DatabaseConfigRequest request) {
        String driver = DB_TYPE_MYSQL.equalsIgnoreCase(request.getDbType())
                ? "com.mysql.cj.jdbc.Driver"
                : "org.postgresql.Driver";

        String jdbcUrl = buildJdbcUrl(request);

        return String.format(
                """
                spring:
                  datasource:
                    url: %s
                    username: %s
                    password: ${DB_PASSWORD}  # 从环境变量注入
                    driver-class-name: %s
                    hikari:
                      maximum-pool-size: 20
                      minimum-idle: 5
                      connection-timeout: 30000
                      idle-timeout: 600000
                      max-lifetime: 1800000
                """,
                jdbcUrl, request.getUsername(), driver);
    }

    /**
     * 生成环境变量配置。
     *
     * @param request 数据库配置请求
     * @return 环境变量格式配置
     */
    private String generateEnvConfig(DatabaseConfigRequest request) {
        String driver = DB_TYPE_MYSQL.equalsIgnoreCase(request.getDbType())
                ? "com.mysql.cj.jdbc.Driver"
                : "org.postgresql.Driver";

        String jdbcUrl = buildJdbcUrl(request);

        return String.format(
                """
                DB_URL=%s
                DB_USERNAME=%s
                DB_PASSWORD=%s
                DB_DRIVER=%s
                """,
                jdbcUrl, request.getUsername(), "", driver);
    }

    /**
     * 生成 Docker Compose 配置。
     *
     * @param request 数据库配置请求
     * @return Docker Compose 格式配置
     */
    private String generateDockerComposeConfig(DatabaseConfigRequest request) {
        String image = DB_TYPE_MYSQL.equalsIgnoreCase(request.getDbType()) ? "mysql:9.0" : "postgres:15";

        String envPrefix = DB_TYPE_MYSQL.equalsIgnoreCase(request.getDbType()) ? "MYSQL" : "POSTGRES";

        return String.format(
                """
                version: '3.8'
                services:
                  database:
                    image: %s
                    container_name: apex-database
                    ports:
                      - "%d:%d"
                    environment:
                      %s_ROOT_PASSWORD: %s
                      %s_DATABASE: %s
                      %s_USER: %s
                      %s_PASSWORD: %s
                    volumes:
                      - db_data:/var/lib/%s
                    networks:
                      - apex-network

                volumes:
                  db_data:

                networks:
                  apex-network:
                    driver: bridge
                """,
                image,
                request.getPort(),
                request.getPort(),
                envPrefix,
                "${DB_PASSWORD:?DB_PASSWORD is required}",
                envPrefix,
                request.getDatabase(),
                envPrefix,
                request.getUsername(),
                envPrefix,
                "${DB_PASSWORD:?DB_PASSWORD is required}",
                DB_TYPE_MYSQL.equalsIgnoreCase(request.getDbType()) ? "mysql" : "postgresql");
    }
}
