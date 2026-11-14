package com.apex.scaffold.authservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactoryOptions;
import io.r2dbc.spi.Option;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.autoconfigure.r2dbc.R2dbcProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.core.io.FileSystemResource;
import org.yaml.snakeyaml.Yaml;

/**
 * 主 Compose 与 auth prod 数据源的离线属性绑定契约；不创建连接或启动外部客户端。
 *
 * @author Apex Team
 */
class AuthDataSourceConfigContractTest {

    private static final Path ROOT = repositoryRoot();
    private static final String SAMPLE_PASSWORD = "offline-contract-password";

    @Test
    void composeMergedEnvironmentBindsBothClientsToSameDatabaseAndUser() throws IOException {
        Map<String, Object> variables = composeEnvironment();
        StandardEnvironment environment = environment(variables);
        DataSourceProperties jdbc = jdbc(environment);
        R2dbcProperties reactive = reactive(environment);

        assertSameTarget(jdbc, reactive, "mysql", 3306, "apex_auth", "offline_user");
        assertThat(SAMPLE_PASSWORD.equals(jdbc.getPassword())).isTrue();
        assertThat(SAMPLE_PASSWORD.equals(reactive.getPassword())).isTrue();
        assertThat(variables).doesNotContainKeys("SPRING_R2DBC_URL", "SPRING_R2DBC_USERNAME");
        assertThat(environment.getProperty("spring.profiles.active")).isEqualTo("prod,enterprise");
    }

    @Test
    void composeJdbcOverrideDisablesTlsWhileReactiveProdStillRequiresTls() throws IOException {
        StandardEnvironment environment = environment(composeEnvironment());

        assertThat(jdbc(environment).getUrl()).contains("useSSL=false").doesNotContain("requireSSL=true");
        assertReactiveTls(reactive(environment));
    }

    @Test
    void commonDbTargetOverrideLeavesExplicitComposeJdbcTargetUnchanged() throws IOException {
        Map<String, Object> variables = new HashMap<>(composeEnvironment());
        String originalJdbcUrl = variables.get("SPRING_DATASOURCE_URL").toString();
        variables.putAll(Map.of("DB_HOST", "override.offline.invalid", "DB_NAME", "override_auth"));
        StandardEnvironment environment = environment(variables);
        DataSourceProperties jdbc = jdbc(environment);
        R2dbcProperties reactive = reactive(environment);

        assertThat(jdbc.getUrl()).isEqualTo(originalJdbcUrl);
        URI jdbcUri = URI.create(jdbc.getUrl().substring(5));
        assertThat(jdbcUri.getHost()).isEqualTo("mysql");
        assertThat(jdbcUri.getPort()).isEqualTo(3306);
        assertThat(jdbcUri.getPath()).isEqualTo("/apex_auth");
        assertThat(jdbc.getUrl()).contains("useSSL=false");
        ConnectionFactoryOptions options = ConnectionFactoryOptions.parse(reactive.getUrl());
        assertThat(options.getRequiredValue(ConnectionFactoryOptions.HOST)).isEqualTo("override.offline.invalid");
        assertThat(options.getRequiredValue(ConnectionFactoryOptions.PORT)).isEqualTo(3306);
        assertThat(options.getRequiredValue(ConnectionFactoryOptions.DATABASE)).isEqualTo("override_auth");
        assertThat(jdbc.getUsername()).isEqualTo("offline_user");
        assertThat(reactive.getUsername()).isEqualTo("offline_user");
        assertThat(SAMPLE_PASSWORD.equals(jdbc.getPassword())).isTrue();
        assertThat(SAMPLE_PASSWORD.equals(reactive.getPassword())).isTrue();
        assertThat(variables).doesNotContainKey("SPRING_R2DBC_URL");
        assertReactiveTls(reactive);
    }

    @Test
    void explicitBothUrlOverridesAlignTargetsAndPreserveCredentialsAndProdTls() throws IOException {
        Map<String, Object> variables = new HashMap<>(composeEnvironment());
        variables.putAll(
                Map.of(
                        "DB_HOST",
                        "override.offline.invalid",
                        "DB_NAME",
                        "override_auth",
                        "SPRING_DATASOURCE_URL",
                        "jdbc:mysql://override.offline.invalid:3306/override_auth?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=true&requireSSL=true",
                        "SPRING_R2DBC_URL",
                        "r2dbc:mysql://override.offline.invalid:3306/override_auth?serverTimezone=Asia/Shanghai&sslMode=REQUIRED"));
        StandardEnvironment environment = environment(variables);
        DataSourceProperties jdbc = jdbc(environment);
        R2dbcProperties reactive = reactive(environment);

        assertSameTarget(jdbc, reactive, "override.offline.invalid", 3306, "override_auth", "offline_user");
        assertThat(SAMPLE_PASSWORD.equals(jdbc.getPassword())).isTrue();
        assertThat(SAMPLE_PASSWORD.equals(reactive.getPassword())).isTrue();
        assertThat(jdbc.getUrl()).isEqualTo(variables.get("SPRING_DATASOURCE_URL"));
        assertThat(reactive.getUrl()).isEqualTo(variables.get("SPRING_R2DBC_URL"));
        assertThat(jdbc.getUrl()).contains("useSSL=true", "requireSSL=true").doesNotContain("useSSL=false");
        assertReactiveTls(reactive);
    }

    @Test
    void directProdDbVariablesBindBothClientsWithSecureDefaults() throws IOException {
        StandardEnvironment environment = environment(Map.of(
                "DB_HOST", "db.offline.invalid",
                "DB_PORT", "13306",
                "DB_NAME", "offline_auth",
                "DB_USERNAME", "offline_user",
                "DB_PASSWORD", SAMPLE_PASSWORD));

        DataSourceProperties jdbc = jdbc(environment);
        R2dbcProperties reactive = reactive(environment);
        assertSameTarget(jdbc, reactive, "db.offline.invalid", 13306, "offline_auth", "offline_user");
        assertThat(jdbc.getUrl()).contains("useSSL=true", "requireSSL=true");
        assertReactiveTls(reactive);
    }

    @Test
    void absentDbTargetUsesLocalhostAndDefaultDatabaseRatherThanFailingBinding() throws IOException {
        StandardEnvironment environment =
                environment(Map.of("DB_USERNAME", "offline_user", "DB_PASSWORD", SAMPLE_PASSWORD));

        assertSameTarget(jdbc(environment), reactive(environment), "localhost", 3306, "apex_auth", "offline_user");
        assertReactiveTls(reactive(environment));
    }

    @Test
    void missingCredentialsRemainUnresolvedDuringBindingButStrictResolutionFails() throws IOException {
        StandardEnvironment environment = environment(Map.of());
        DataSourceProperties jdbc = jdbc(environment);
        R2dbcProperties reactive = reactive(environment);

        // Binder 不提供凭据必填校验，不能把属性绑定成功等同于数据库可认证。
        assertThat(jdbc.getUsername()).isEqualTo("${DB_USERNAME}");
        assertThat(reactive.getUsername()).isEqualTo("${DB_USERNAME}");
        assertThat("${DB_PASSWORD}".equals(jdbc.getPassword())).isTrue();
        assertThat("${DB_PASSWORD}".equals(reactive.getPassword())).isTrue();
        assertThatThrownBy(() -> environment.getRequiredProperty("spring.r2dbc.username"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> environment.getRequiredProperty("spring.datasource.password"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void jdbcOnlyEnvironmentCannotSupplyReactiveCredentialsOrTarget() throws IOException {
        Map<String, Object> variables = new HashMap<>(composeEnvironment());
        variables.keySet().removeIf(name -> name.startsWith("DB_"));
        StandardEnvironment environment = environment(variables);

        assertThat(URI.create(jdbc(environment).getUrl().substring(5)).getHost())
                .isEqualTo("mysql");
        ConnectionFactoryOptions options =
                ConnectionFactoryOptions.parse(reactive(environment).getUrl());
        assertThat(options.getRequiredValue(ConnectionFactoryOptions.HOST)).isEqualTo("localhost");
        assertThat(reactive(environment).getUsername()).isEqualTo("${DB_USERNAME}");
    }

    @Test
    void explicitSpringReactiveEnvironmentOverridesProdDbVariables() throws IOException {
        Map<String, Object> variables = new HashMap<>(composeEnvironment());
        variables.put(
                "SPRING_R2DBC_URL", "r2dbc:mysql://explicit.offline.invalid:13306/explicit_auth?sslMode=REQUIRED");
        variables.put("SPRING_R2DBC_USERNAME", "explicit_user");
        variables.put("SPRING_R2DBC_PASSWORD", SAMPLE_PASSWORD);
        R2dbcProperties reactive = reactive(environment(variables));
        ConnectionFactoryOptions options = ConnectionFactoryOptions.parse(reactive.getUrl());

        assertThat(options.getRequiredValue(ConnectionFactoryOptions.HOST)).isEqualTo("explicit.offline.invalid");
        assertThat(options.getRequiredValue(ConnectionFactoryOptions.PORT)).isEqualTo(13306);
        assertThat(options.getRequiredValue(ConnectionFactoryOptions.DATABASE)).isEqualTo("explicit_auth");
        assertThat(reactive.getUsername()).isEqualTo("explicit_user");
        assertThat(SAMPLE_PASSWORD.equals(reactive.getPassword())).isTrue();
        assertReactiveTls(reactive);
    }

    private static void assertSameTarget(
            DataSourceProperties jdbc, R2dbcProperties reactive, String host, int port, String database, String user) {
        URI jdbcUri = URI.create(jdbc.getUrl().substring(5));
        ConnectionFactoryOptions options = ConnectionFactoryOptions.parse(reactive.getUrl());
        assertThat(jdbcUri.getHost()).isEqualTo(host);
        assertThat(jdbcUri.getPort()).isEqualTo(port);
        assertThat(jdbcUri.getPath()).isEqualTo("/" + database);
        assertThat(options.getRequiredValue(ConnectionFactoryOptions.HOST)).isEqualTo(host);
        assertThat(options.getRequiredValue(ConnectionFactoryOptions.PORT)).isEqualTo(port);
        assertThat(options.getRequiredValue(ConnectionFactoryOptions.DATABASE)).isEqualTo(database);
        assertThat(jdbc.getUsername()).isEqualTo(user);
        assertThat(reactive.getUsername()).isEqualTo(user);
    }

    private static void assertReactiveTls(R2dbcProperties reactive) {
        ConnectionFactoryOptions options = ConnectionFactoryOptions.parse(reactive.getUrl());
        assertThat(options.getRequiredValue(Option.valueOf("sslMode"))).isEqualTo("REQUIRED");
        assertThat(ConnectionFactories.supports(options)).isTrue();
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

    private static StandardEnvironment environment(Map<String, Object> variables) throws IOException {
        StandardEnvironment environment = isolatedEnvironment(variables);
        environment.setActiveProfiles("prod", "enterprise");
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        for (String name : new String[] {"application-prod.yml", "application.yml", "bootstrap.yml"}) {
            for (PropertySource<?> source : loader.load(
                    name,
                    new FileSystemResource(
                            ROOT.resolve("apex-services-bom/apex-auth-service/src/main/resources/" + name)))) {
                environment.getPropertySources().addLast(source);
            }
        }
        return environment;
    }

    private static StandardEnvironment isolatedEnvironment(Map<String, Object> variables) {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().addFirst(new SystemEnvironmentPropertySource("offline-compose", variables));
        return environment;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> composeEnvironment() throws IOException {
        Map<String, Object> compose;
        try (InputStream input = Files.newInputStream(ROOT.resolve("podman-compose.yml"))) {
            compose = new Yaml().load(input);
        }
        Map<String, Object> services = (Map<String, Object>) compose.get("services");
        Map<String, Object> auth = (Map<String, Object>) services.get("apex-auth-service");
        Map<String, Object> merged = (Map<String, Object>) auth.get("environment");
        StandardEnvironment interpolation =
                isolatedEnvironment(Map.of("DB_USERNAME", "offline_user", "DB_PASSWORD", SAMPLE_PASSWORD));
        Map<String, Object> result = new HashMap<>();
        // 只展开数据库和 profile 键；不读取 .env、不解析或输出共享安全凭据。
        merged.forEach((name, value) -> {
            if (name.startsWith("DB_")
                    || name.startsWith("SPRING_DATASOURCE_")
                    || name.startsWith("SPRING_R2DBC_")
                    || name.equals("SPRING_PROFILES_ACTIVE")) {
                result.put(
                        name,
                        interpolation.resolveRequiredPlaceholders(
                                value.toString().replace(":-", ":")));
            }
        });
        return result;
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
