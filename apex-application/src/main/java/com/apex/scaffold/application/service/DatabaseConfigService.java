package com.apex.scaffold.application.service;

import com.apex.scaffold.application.config.R2dbcTestConfig;
import com.apex.scaffold.application.dto.admin.CurrentDatabaseInfoDTO;
import com.apex.scaffold.application.dto.admin.DatabaseConfigRequest;
import com.apex.scaffold.application.dto.admin.DatabaseTestResultDTO;
import com.apex.scaffold.application.dto.admin.DeploymentConfigResultDTO;
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
import reactor.core.scheduler.Schedulers;

/**
 * 数据库配置应用服务。
 *
 * <p>提供数据库连接测试（基于 R2DBC）、部署配置生成和当前数据库信息查询功能。
 * 所有方法返回 {@link Mono}，支持响应式编程。</p>
 *
 * <h2>技术选型</h2>
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
/**n * DatabaseConfigServicen *n * <p>TODO: 补充服务的详细说明n *n * @author Apex Teamn * @version 1.0.0n * @since 1.0.0n */
public class DatabaseConfigService {

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
                                                    log.warn("关闭测试连接时出错: {}", e.getMessage());
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
                            log.warn("R2DBC连接测试失败: {}", e.getMessage());
                            String errorMessage = extractErrorMessage(e);
                            return Mono.just(DatabaseTestResultDTO.builder()
                                    .success(false)
                                    .message("连接失败: " + errorMessage)
                                    .build());
                        });

            } catch (Exception e) {
                log.warn("创建R2DBC连接工厂失败: {}", e.getMessage());
                return Mono.just(DatabaseTestResultDTO.builder()
                        .success(false)
                        .message("配置错误: " + e.getMessage())
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
            case "mysql" -> "SELECT 1";
            case "postgresql", "postgres" -> "SELECT 1";
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
            case "mysql" -> "SELECT VERSION()";
            case "postgresql", "postgres" -> "SELECT version()";
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

        if (message.contains("Access denied")) {
            return "认证失败，请检查用户名和密码";
        } else if (message.contains("Unknown database")) {
            return "数据库不存在";
        } else if (message.contains("Connection refused") || message.contains("connect timed out")) {
            return "无法连接到数据库服务器，请检查主机和端口";
        } else if (message.contains("timeout")) {
            return "连接超时";
        } else {
            return message;
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
                .subscribeOn(Schedulers.boundedElastic());
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
                        log.error("获取当前数据库信息失败", e);
                        return CurrentDatabaseInfoDTO.builder()
                                .type("Unknown")
                                .version("Unknown")
                                .driverVersion("Unknown")
                                .build();
                    }
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 构建 JDBC URL。
     *
     * @param request 数据库配置请求
     * @return JDBC URL
     */
    private String buildJdbcUrl(DatabaseConfigRequest request) {
        String baseUrl;
        if ("mysql".equalsIgnoreCase(request.getDbType())) {
            baseUrl =
                    String.format("jdbc:mysql://%s:%d/%s", request.getHost(), request.getPort(), request.getDatabase());
        } else if ("postgresql".equalsIgnoreCase(request.getDbType())) {
            baseUrl = String.format(
                    "jdbc:postgresql://%s:%d/%s", request.getHost(), request.getPort(), request.getDatabase());
        } else {
            throw new IllegalArgumentException("不支持的数据库类型: " + request.getDbType());
        }

        if (request.getConnectionParams() != null
                && !request.getConnectionParams().isEmpty()) {
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
        String driver =
                "mysql".equalsIgnoreCase(request.getDbType()) ? "com.mysql.cj.jdbc.Driver" : "org.postgresql.Driver";

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
        String driver =
                "mysql".equalsIgnoreCase(request.getDbType()) ? "com.mysql.cj.jdbc.Driver" : "org.postgresql.Driver";

        String jdbcUrl = buildJdbcUrl(request);

        return String.format(
                """
                DB_URL=%s
                DB_USERNAME=%s
                DB_PASSWORD=%s
                DB_DRIVER=%s
                """,
                jdbcUrl, request.getUsername(), request.getPassword(), driver);
    }

    /**
     * 生成 Docker Compose 配置。
     *
     * @param request 数据库配置请求
     * @return Docker Compose 格式配置
     */
    private String generateDockerComposeConfig(DatabaseConfigRequest request) {
        String image = "mysql".equalsIgnoreCase(request.getDbType()) ? "mysql:8.0" : "postgres:15";

        String envPrefix = "mysql".equalsIgnoreCase(request.getDbType()) ? "MYSQL" : "POSTGRES";

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
                request.getPassword(),
                envPrefix,
                request.getDatabase(),
                envPrefix,
                request.getUsername(),
                envPrefix,
                request.getPassword(),
                "mysql".equalsIgnoreCase(request.getDbType()) ? "mysql" : "postgresql");
    }
}
