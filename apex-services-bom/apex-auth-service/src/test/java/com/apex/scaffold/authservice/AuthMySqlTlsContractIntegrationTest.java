package com.apex.scaffold.authservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactoryOptions;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.autoconfigure.r2dbc.R2dbcProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.core.io.FileSystemResource;
import org.yaml.snakeyaml.Yaml;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 显式启用的真实 MySQL TLS 契约；每个场景独占 Podman 网络与 tmpfs 数据目录。
 *
 * @author Apex Team
 */
@EnabledIfEnvironmentVariable(named = "APEX_AUTH_MYSQL_TLS_IT", matches = "true")
class AuthMySqlTlsContractIntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(15);
    private static final String DATABASE = "isolated_auth_contract";
    private static final String USER = "contract_user";
    private static final Path ROOT = repositoryRoot();

    @ParameterizedTest
    @ValueSource(strings = {"tls", "no-tls", "tls-required"})
    void realDriversMustRespectSourceTlsPolicies(String serverMode) throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String container = "apex-auth-tls-" + suffix;
        String network = container + "-net";
        String password = UUID.randomUUID().toString();
        boolean networkCreated = false;
        try {
            command(Map.of(), "podman", "network", "create", network);
            networkCreated = true;
            command(
                    Map.of("MYSQL_ROOT_PASSWORD", password, "MYSQL_PASSWORD", password),
                    "podman",
                    "run",
                    "--detach",
                    "--pull=never",
                    "--name",
                    container,
                    "--network",
                    network,
                    "--publish",
                    "127.0.0.1::3306",
                    "--tmpfs",
                    "/var/lib/mysql:rw,size=1g",
                    "--env",
                    "MYSQL_ROOT_PASSWORD",
                    "--env",
                    "MYSQL_PASSWORD",
                    "--env",
                    "MYSQL_USER=" + USER,
                    "--env",
                    "MYSQL_DATABASE=" + DATABASE,
                    "docker.io/library/mysql:9.0",
                    serverMode.equals("no-tls") ? "--ssl=OFF" : "--ssl=ON",
                    "--require-secure-transport=" + (serverMode.equals("tls-required") ? "ON" : "OFF"));
            String mapping =
                    command(Map.of(), "podman", "port", container, "3306/tcp").trim();
            assertThat(mapping).startsWith("127.0.0.1:").doesNotContain("\n");
            int port = Integer.parseInt(mapping.substring(mapping.lastIndexOf(':') + 1));
            Map<String, Object> variables = new HashMap<>(Map.of(
                    "DB_HOST",
                    "127.0.0.1",
                    "DB_PORT",
                    Integer.toString(port),
                    "DB_NAME",
                    DATABASE,
                    "DB_USERNAME",
                    USER,
                    "DB_PASSWORD",
                    password));
            StandardEnvironment prod = environment(variables);
            DataSourceProperties prodJdbc = jdbc(prod);
            R2dbcProperties prodReactive = reactive(prod);
            awaitReady(
                    serverMode.equals("no-tls")
                            ? prodJdbc.getUrl()
                                    .replace("useSSL=true&requireSSL=true", "useSSL=false&allowPublicKeyRetrieval=true")
                            : prodJdbc.getUrl(),
                    password);

            // 从主 Compose 读取真实覆盖参数，只将测试目标替换为独占回环端口。
            variables.put("SPRING_DATASOURCE_URL", composeJdbcUrl(port));
            DataSourceProperties composeJdbc = jdbc(environment(variables));
            if (serverMode.equals("tls-required")) {
                assertThatThrownBy(() -> jdbcSnapshot(composeJdbc.getUrl(), password))
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("insecure transport");
            } else {
                assertSnapshot(jdbcSnapshot(composeJdbc.getUrl(), password), false);
            }
            if (serverMode.equals("no-tls")) {
                assertThatThrownBy(() -> jdbcSnapshot(prodJdbc.getUrl(), password))
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("SSL");
                assertThatThrownBy(() -> reactiveSnapshot(prodReactive.getUrl(), password))
                        .hasStackTraceContaining("SSL");
            } else {
                assertSnapshot(jdbcSnapshot(prodJdbc.getUrl(), password), true);
                assertSnapshot(reactiveSnapshot(prodReactive.getUrl(), password), true);
            }
            String disabled =
                    prodReactive.getUrl().replace("sslMode=REQUIRED", "sslMode=DISABLED&allowPublicKeyRetrieval=true");
            if (serverMode.equals("tls-required")) {
                assertThatThrownBy(() -> reactiveSnapshot(disabled, password))
                        .hasStackTraceContaining("insecure transport");
            } else {
                assertSnapshot(reactiveSnapshot(disabled, password), false);
            }
        } finally {
            // run 部分失败也可能留下已创建的容器；只清理本次唯一名称。
            try {
                command(Map.of(), "podman", "rm", "--force", "--ignore", container);
            } finally {
                if (networkCreated) {
                    command(Map.of(), "podman", "network", "rm", network);
                }
            }
        }
    }

    private static void assertSnapshot(Map<String, String> snapshot, boolean encrypted) {
        assertThat(snapshot.get("database")).isEqualTo(DATABASE);
        assertThat(snapshot.get("Ssl_cipher").isEmpty()).isEqualTo(!encrypted);
        assertThat(snapshot.get("Ssl_version").isEmpty()).isEqualTo(!encrypted);
    }

    private static Map<String, String> jdbcSnapshot(String url, String password) throws SQLException {
        Map<String, String> snapshot = new HashMap<>();
        try (var connection =
                        DriverManager.getConnection(url + "&connectTimeout=5000&socketTimeout=10000", USER, password);
                var statement = connection.createStatement()) {
            try (var rows = statement.executeQuery("SELECT DATABASE()")) {
                assertThat(rows.next()).isTrue();
                snapshot.put("database", rows.getString(1));
            }
            try (var rows =
                    statement.executeQuery("SHOW SESSION STATUS WHERE Variable_name IN ('Ssl_cipher','Ssl_version')")) {
                while (rows.next()) {
                    snapshot.put(rows.getString(1), rows.getString(2));
                }
            }
        }
        return snapshot;
    }

    private static Map<String, String> reactiveSnapshot(String url, String password) {
        var options = ConnectionFactoryOptions.parse(url)
                .mutate()
                .option(ConnectionFactoryOptions.USER, USER)
                .option(ConnectionFactoryOptions.PASSWORD, password)
                .option(ConnectionFactoryOptions.CONNECT_TIMEOUT, Duration.ofSeconds(5))
                .build();
        return Mono.usingWhen(
                        Mono.from(ConnectionFactories.get(options).create()),
                        connection -> {
                            Map<String, String> snapshot = new HashMap<>();
                            return Flux.from(connection
                                            .createStatement("SELECT DATABASE() AS db")
                                            .execute())
                                    .concatMap(result -> result.map((row, metadata) -> row.get("db", String.class)))
                                    .doOnNext(database -> snapshot.put("database", database))
                                    .thenMany(Flux.from(connection
                                            .createStatement(
                                                    "SHOW SESSION STATUS WHERE Variable_name IN ('Ssl_cipher','Ssl_version')")
                                            .execute()))
                                    .concatMap(result -> result.map((row, metadata) ->
                                            Map.entry(row.get(0, String.class), row.get(1, String.class))))
                                    .doOnNext(entry -> snapshot.put(entry.getKey(), entry.getValue()))
                                    .then(Mono.just(snapshot));
                        },
                        connection -> Mono.from(connection.close()))
                .block(TIMEOUT);
    }

    private static void awaitReady(String url, String password) throws Exception {
        long deadline = System.nanoTime() + Duration.ofMinutes(2).toNanos();
        while (System.nanoTime() < deadline) {
            try {
                jdbcSnapshot(url, password);
                return;
            } catch (SQLException ignored) {
                TimeUnit.SECONDS.sleep(1);
            }
        }
        throw new IllegalStateException("隔离 MySQL 在两分钟内未就绪；未输出凭据或连接异常详情");
    }

    private static DataSourceProperties jdbc(StandardEnvironment environment) {
        return Binder.get(environment)
                .bind("spring.datasource", DataSourceProperties.class)
                .get();
    }

    private static R2dbcProperties reactive(StandardEnvironment environment) {
        return Binder.get(environment)
                .bind("spring.r2dbc", R2dbcProperties.class)
                .get();
    }

    private static StandardEnvironment environment(Map<String, Object> variables) throws Exception {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().addFirst(new SystemEnvironmentPropertySource("isolated-contract", variables));
        var loader = new YamlPropertySourceLoader();
        for (var source : loader.load(
                "prod",
                new FileSystemResource(
                        ROOT.resolve("apex-services-bom/apex-auth-service/src/main/resources/application-prod.yml")))) {
            environment.getPropertySources().addLast(source);
        }
        return environment;
    }

    @SuppressWarnings("unchecked")
    private static String composeJdbcUrl(int port) throws Exception {
        try (InputStream input = Files.newInputStream(ROOT.resolve("podman-compose.yml"))) {
            Map<String, Object> compose = new Yaml().load(input);
            var services = (Map<String, Object>) compose.get("services");
            var auth = (Map<String, Object>) services.get("apex-auth-service");
            var variables = (Map<String, Object>) auth.get("environment");
            String source = variables.get("SPRING_DATASOURCE_URL").toString();
            assertThat(source).startsWith("jdbc:mysql://mysql:3306/apex_auth?");
            return source.replace(
                    "jdbc:mysql://mysql:3306/apex_auth?", "jdbc:mysql://127.0.0.1:" + port + "/" + DATABASE + "?");
        }
    }

    private static String command(Map<String, String> environment, String... args) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(args).redirectError(ProcessBuilder.Redirect.DISCARD);
        builder.environment().putAll(environment);
        Process process = builder.start();
        try {
            if (!process.waitFor(45, TimeUnit.SECONDS)) {
                throw new IllegalStateException("隔离 Podman 命令超时: " + args[1]);
            }
            assertThat(process.exitValue()).as("Podman %s", args[1]).isZero();
            return new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } finally {
            process.destroyForcibly();
        }
    }

    private static Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null && !Files.isRegularFile(current.resolve("podman-compose.yml"))) {
            current = current.getParent();
        }
        if (current == null) {
            throw new IllegalStateException("找不到主 Compose");
        }
        return current;
    }
}
