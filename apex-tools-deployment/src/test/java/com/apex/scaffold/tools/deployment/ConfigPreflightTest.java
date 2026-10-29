package com.apex.scaffold.tools.deployment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 部署配置前置校验的端到端行为测试：CLI 退出码、敏感值隐藏、Compose 展开与优先级。
 *
 * @author Apex Team
 * @since 1.3.1
 */
class ConfigPreflightTest {

    /** 子进程默认超时秒数。 */
    private static final int DEFAULT_TIMEOUT_SECONDS = 30;

    /** Compose 子进程超时秒数。 */
    private static final int COMPOSE_TIMEOUT_SECONDS = 120;

    /** 测试断言不得出现的 redis 模板回退值。 */
    private static final String PLACEHOLDER_REDIS_PASSWORD = "apex-redis-password";

    /** 仓库根目录。 */
    static final Path ROOT = Path.of(System.getProperty(LocalDeployment.ROOT_PROPERTY))
            .toAbsolutePath()
            .normalize();

    @TempDir
    Path temp;

    /**
     * 子进程执行结果。
     *
     * @param status 退出码
     * @param output 合并后的标准输出与标准错误
     * @author Apex Team
     * @since 1.3.1
     */
    record Result(int status, String output) {}

    /**
     * 进程内调用 CLI 入口。
     *
     * @param env  环境变量
     * @param args 命令行参数
     * @return 执行结果
     */
    Result cli(Map<String, String> env, String... args) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (PrintStream stream = new PrintStream(output, true, StandardCharsets.UTF_8)) {
            int status = LocalDeployment.run(args, env, stream, stream);
            return new Result(status, output.toString(StandardCharsets.UTF_8));
        }
    }

    /**
     * 以隔离环境变量运行外部命令。
     *
     * @param command 命令
     * @param env     环境变量
     * @param cwd     工作目录
     * @return 执行结果
     * @throws Exception 启动或等待失败时抛出
     */
    Result process(List<String> command, Map<String, String> env, Path cwd) throws Exception {
        Path stdout = Files.createTempFile(temp, "stdout-", ".txt");
        Path stderr = Files.createTempFile(temp, "stderr-", ".txt");
        ProcessBuilder builder = new ProcessBuilder(command)
                .directory(cwd.toFile())
                .redirectOutput(stdout.toFile())
                .redirectError(stderr.toFile());
        builder.environment().clear();
        builder.environment().put("PATH", System.getenv("PATH"));
        builder.environment().putAll(env);
        Process process = builder.start();
        long timeout = command.contains("compose") || "podman-compose".equals(command.get(0))
                ? COMPOSE_TIMEOUT_SECONDS
                : DEFAULT_TIMEOUT_SECONDS;
        if (!process.waitFor(timeout, TimeUnit.SECONDS)) {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
            fail("isolated command timed out");
        }
        return new Result(process.exitValue(), Files.readString(stdout) + Files.readString(stderr));
    }

    /**
     * 以独立 JVM 运行 CLI 入口，验证真实进程的退出码与输出。
     *
     * @param root 工作目录与仓库根
     * @param env  环境变量
     * @param args 命令行参数
     * @return 执行结果
     * @throws Exception 启动或等待失败时抛出
     */
    Result child(Path root, Map<String, String> env, String... args) throws Exception {
        String classPath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        List<String> command = new ArrayList<>(List.of(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-D" + LocalDeployment.ROOT_PROPERTY + "=" + root,
                "-cp",
                classPath,
                LocalDeployment.class.getName()));
        command.addAll(List.of(args));
        return process(command, env, root);
    }

    @Test
    void requiredPreflightValuesAndPrivacy() {
        for (String target : List.of("compose", "nacos-datasource-prod")) {
            String name = "compose".equals(target) ? "REDIS_PASSWORD" : "DB_HOST_PROD";
            List<String> invalid = new ArrayList<>(List.of("", " \t\n", "$" + "{" + name + "}"));
            if ("compose".equals(target)) {
                invalid.add(PLACEHOLDER_REDIS_PASSWORD);
            }
            for (String value : invalid) {
                Result result =
                        cli(Map.of(name, value, "DB_HOST", "SENTINEL_NOT_AN_ALIAS"), "preflight", "--target", target);
                assertEquals(1, result.status());
                assertTrue(result.output().contains(name));
                assertFalse(result.output().contains("SENTINEL_NOT_AN_ALIAS"));
                assertFalse(result.output().contains(PLACEHOLDER_REDIS_PASSWORD));
            }
            Result missing = cli(Map.of(), "preflight", "--target", target);
            assertEquals(1, missing.status());
            assertTrue(missing.output().contains(name));
            Result valid = cli(Map.of(name, "SENTINEL_EXPLICIT_VALUE"), "preflight", "--target", target);
            assertEquals(0, valid.status());
            assertFalse(valid.output().contains("SENTINEL_EXPLICIT_VALUE"));
        }
        assertEquals(2, cli(Map.of(), "preflight", "--target", "unknown").status());
        assertEquals(2, cli(Map.of(), "preflight").status());
    }

    @Test
    void cliDoesNotEchoBadSecretsOrInputOrLoadDotenv() throws Exception {
        String sentinel = "PRIVATE-SENTINEL-DO-NOT-LOG";
        Result result = child(temp, Map.of("APEX_DATA_ENCRYPTION_KEY", sentinel), "validate");
        assertEquals(1, result.status());
        assertFalse(result.output().contains(sentinel));
        assertFalse(result.output().contains("Exception"));

        Files.writeString(temp.resolve(".env"), "REDIS_PASSWORD=SENTINEL_NOT_LOADED\n");
        result = child(temp, Map.of(), "preflight", "--target", "compose");
        assertEquals(1, result.status());
        assertTrue(result.output().contains("REDIS_PASSWORD"));
        assertFalse(result.output().contains("SENTINEL_NOT_LOADED"));

        Path input = temp.resolve("input.json");
        Files.writeString(input, sentinel);
        result = child(
                temp,
                LocalDeploymentTest.keys(),
                "prepare",
                "--input",
                input.toString(),
                "--output",
                temp.resolve("bundle").toString());
        assertEquals(1, result.status());
        assertFalse(result.output().contains(sentinel));
        assertFalse(Files.exists(temp.resolve("bundle")));
    }

    @Test
    void composeUsesYamlAndValidatesEveryService() throws Exception {
        List<String> command = ComposeConfigValidator.composeCommand(ROOT, Path.of("config space.env"));
        assertEquals("podman-compose", command.get(0));
        assertEquals("config", command.get(command.size() - 1));
        assertEquals(ROOT.resolve("podman-compose.yml").toString(), command.get(2));
        assertFalse(command.contains("--project-directory"));
        assertFalse(command.contains("--format"));
        assertEquals(Path.of("config space.env").toAbsolutePath().toString(), command.get(4));

        Map<String, String> env = LocalDeploymentTest.keys();
        env.put("REDIS_PASSWORD", "SENTINEL_REDIS");
        Map<String, Object> services = new LinkedHashMap<>();
        services.put("first", Map.of("environment", env));
        services.put("mysql", Map.of("environment", Map.of("MYSQL_DATABASE", "apex_system")));
        String secured = LocalDeploymentTest.JSON.writeValueAsString(Map.of("services", services));
        assertDoesNotThrow(() -> ComposeConfigValidator.validateCompose(secured));

        Map<String, String> bad = new HashMap<>(env);
        bad.put("JWT_SECRET", "short");
        services.put("second", Map.of("environment", bad));
        String brokenKeys = LocalDeploymentTest.JSON.writeValueAsString(Map.of("services", services));
        assertThrows(Exception.class, () -> ComposeConfigValidator.validateCompose(brokenKeys));

        services.remove("second");
        services.put("redis", Map.of("environment", Map.of("REDIS_PASSWORD", PLACEHOLDER_REDIS_PASSWORD)));
        String brokenRedis = LocalDeploymentTest.JSON.writeValueAsString(Map.of("services", services));
        assertThrows(Exception.class, () -> ComposeConfigValidator.validateCompose(brokenRedis));
        assertThrows(Exception.class, () -> ComposeConfigValidator.validateCompose("services: {}"));

        String anchor = "defaults: &security "
                + LocalDeploymentTest.JSON.writeValueAsString(env)
                + "\nservices:\n  one:\n    environment:\n      <<: *security\n";
        assertDoesNotThrow(() -> ComposeConfigValidator.validateCompose(anchor));
    }

    @Test
    void operationsEntrypointUsesJavaArtifact() throws Exception {
        Path launcher = ROOT.resolve("scripts/utils/local-deployment.sh");
        assertTrue(Files.isRegularFile(launcher), "Shell operations entrypoint is required");
        assertEquals(
                0,
                process(List.of("/bin/bash", "-n", launcher.toString()), Map.of(), temp)
                        .status());

        Map<?, ?> services =
                (Map<?, ?>) ComposeConfigValidator.yaml(Files.readString(ROOT.resolve("podman-compose.yml")))
                        .get("services");
        Map<?, ?> check = (Map<?, ?>) services.get("security-key-check");
        assertEquals(List.of("java", "-jar", "/tools/local-deployment.jar", "validate"), check.get("command"));
        assertTrue(check.get("image").toString().contains("21"));
        assertFalse(check.toString().contains("python"));
    }

    /**
     * 准备真实 Compose 校验所需的临时工程目录。
     *
     * @throws Exception 复制文件失败时抛出
     */
    void requireCompose() throws Exception {
        assumeTrue(
                process(List.of("/bin/sh", "-c", "command -v podman-compose >/dev/null"), Map.of(), temp)
                                .status()
                        == 0,
                "podman-compose required");
        Files.copy(ROOT.resolve("podman-compose.yml"), temp.resolve("podman-compose.yml"));
    }

    @Test
    void actualComposeFallbackEnvFileAndHostPrecedence() throws Exception {
        requireCompose();
        Path envFile = temp.resolve("sentinel.env");
        List<Map<String, String>> hosts = List.of(
                Map.of(), Map.of(), Map.of("REDIS_PASSWORD", "SENTINEL_HOST_REDIS"), Map.of("REDIS_PASSWORD", " \t"));
        List<String> files = List.of(
                "",
                "REDIS_PASSWORD=SENTINEL_FILE_REDIS\n",
                "REDIS_PASSWORD=\n",
                "REDIS_PASSWORD=SENTINEL_FILE_REDIS\n");
        int[] expected = {1, 0, 0, 1};
        for (int i = 0; i < hosts.size(); i++) {
            Files.writeString(envFile, files.get(i));
            Map<String, String> env = LocalDeploymentTest.keys();
            env.putAll(hosts.get(i));
            Result result = child(temp, env, "compose", "--env-file", envFile.toString());
            assertEquals(expected[i], result.status(), "isolated Compose validation status");
            for (String sentinel :
                    List.of("SENTINEL_FILE_REDIS", "SENTINEL_HOST_REDIS", PLACEHOLDER_REDIS_PASSWORD, "j".repeat(48))) {
                assertFalse(result.output().contains(sentinel));
            }
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    void actualComposeOverridesPreserveAnchorsAndExplicitDatabaseUrls() throws Exception {
        requireCompose();
        Path override = temp.resolve("override.yml");
        Path envFile = temp.resolve("sentinel.env");
        Map<String, String> file = Map.of(
                "DB_HOST", "sentinel-file.invalid",
                "DB_NAME", "sentinel_file",
                "DB_USERNAME", "SENTINEL_FILE_USER",
                "DB_PASSWORD", "SENTINEL_FILE_PASSWORD",
                "REDIS_PASSWORD", "SENTINEL_FILE_REDIS");
        StringBuilder contents = new StringBuilder();
        file.forEach(
                (key, value) -> contents.append(key).append('=').append(value).append('\n'));
        Files.writeString(envFile, contents);

        Map<String, String> security = LocalDeploymentTest.keys();
        for (boolean host : List.of(false, true)) {
            for (boolean explicit : List.of(false, true)) {
                Map<String, String> env = new HashMap<>(security);
                Map<String, String> expected = new HashMap<>(file);
                if (host) {
                    Map<String, String> values = Map.of(
                            "DB_HOST", "sentinel-host.invalid",
                            "DB_NAME", "sentinel_host",
                            "DB_USERNAME", "SENTINEL_HOST_USER",
                            "DB_PASSWORD", "SENTINEL_HOST_PASSWORD");
                    env.putAll(values);
                    expected.putAll(values);
                }
                Map<String, String> values =
                        new HashMap<>(Map.of("DB_HOST", "$" + "{DB_HOST}", "DB_NAME", "$" + "{DB_NAME}"));
                if (explicit) {
                    values.put("SPRING_DATASOURCE_URL", "jdbc:mysql://$" + "{DB_HOST}:3306/$" + "{DB_NAME}");
                    values.put("SPRING_R2DBC_URL", "r2dbc:mysql://$" + "{DB_HOST}:3306/$" + "{DB_NAME}");
                }
                Files.writeString(
                        override,
                        LocalDeploymentTest.JSON.writeValueAsString(
                                Map.of("services", Map.of("apex-auth-service", Map.of("environment", values)))));
                Result result = process(
                        List.of(
                                "podman-compose",
                                "-f",
                                temp.resolve("podman-compose.yml").toString(),
                                "-f",
                                override.toString(),
                                "--env-file",
                                envFile.toString(),
                                "config"),
                        env,
                        temp);
                assertEquals(0, result.status(), "isolated Compose rendering failed");
                Map<String, Object> services = (Map<String, Object>)
                        ComposeConfigValidator.yaml(result.output()).get("services");
                Map<String, String> auth =
                        (Map<String, String>) ((Map<?, ?>) services.get("apex-auth-service")).get("environment");
                expected.forEach(
                        (key, value) -> assertTrue(value.equals(auth.get(key)), "auth " + key + " precedence"));
                security.forEach((key, value) -> assertTrue(value.equals(auth.get(key)), "auth " + key + " anchor"));
                assertTrue(expected.get("DB_USERNAME").equals(auth.get("SPRING_DATASOURCE_USERNAME")));
                assertTrue(expected.get("DB_PASSWORD").equals(auth.get("SPRING_DATASOURCE_PASSWORD")));
                assertEquals("3306", auth.get("DB_PORT"));
                assertEquals("redis", auth.get("REDIS_HOST"));
                assertEquals("nacos:8848", auth.get("NACOS_SERVER_ADDR"));
                URI jdbc = URI.create(auth.get("SPRING_DATASOURCE_URL").substring(5));
                if (explicit) {
                    URI r2dbc = URI.create(auth.get("SPRING_R2DBC_URL").substring(6));
                    for (URI uri : List.of(jdbc, r2dbc)) {
                        assertEquals(expected.get("DB_HOST"), uri.getHost());
                        assertEquals(3306, uri.getPort());
                        assertEquals("/" + expected.get("DB_NAME"), uri.getPath());
                    }
                } else {
                    assertEquals("mysql", jdbc.getHost());
                    assertEquals(3306, jdbc.getPort());
                    assertEquals("/apex_auth", jdbc.getPath());
                    assertFalse(auth.containsKey("SPRING_R2DBC_URL"));
                }
                Map<String, String> system =
                        (Map<String, String>) ((Map<?, ?>) services.get("apex-system-service")).get("environment");
                assertEquals("mysql", system.get("DB_HOST"));
                assertEquals("apex_system", system.get("DB_NAME"));
                assertTrue(expected.get("DB_USERNAME").equals(system.get("DB_USERNAME")));
                assertTrue(expected.get("DB_PASSWORD").equals(system.get("DB_PASSWORD")));
            }
        }
    }
}
