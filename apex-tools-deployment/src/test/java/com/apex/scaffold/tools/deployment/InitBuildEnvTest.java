package com.apex.scaffold.tools.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * 构建环境初始化脚本的成功与失败分支测试：JDK/mvnd 版本契约、`current` 符号链接拒绝与密钥不外泄。
 *
 * @author Apex Team
 * @since 1.3.1
 */
class InitBuildEnvTest {

    /** 测试进程等待上限秒数。 */
    private static final int TIMEOUT_SECONDS = 10;

    /** 初始化脚本路径。 */
    static final Path SCRIPT = Path.of(System.getProperty(LocalDeployment.ROOT_PROPERTY))
            .resolve("scripts/build/init-build-env.sh")
            .normalize();

    /** 桩版 SDKMAN 初始化脚本，覆盖 sdk/java/mvnd 三个命令。 */
    static final String SDKMAN =
            """
        sdk() {
            [[ "$*" == env ]] || return 90
            export JAVA_HOME="$HOME/.sdkman/candidates/java/${TEST_JAVA_HOME:-21.0.13.0+9-tem}"
            export APEX_TEST_ACTIVATED=yes
            return "${TEST_SDK_STATUS:-0}"
        }
        java() {
            [[ "$*" == -version ]] || return 91
            printf 'java version "%s"\\n' "${TEST_JAVA_VERSION:-21.0.13}" >&2
            return "${TEST_JAVA_STATUS:-0}"
        }
        mvnd() {
            [[ "$*" == --version ]] || return 92
            if [[ -n "${TEST_MVND_OUTPUT:-}" ]]; then
                printf '%s\\n' "$TEST_MVND_OUTPUT"
            else
                printf 'Apache Maven Daemon (mvnd) %s darwin-amd64 native client (f912abcd)\\n' "${TEST_MVND_VERSION:-2.0.0-rc-3}"
                printf 'Terminal: org.jline.terminal.impl.DumbTerminal\\n'
                printf 'Apache Maven 4.0.0-rc-3\\n'
                printf 'Java version: 21.0.13, vendor: Eclipse Adoptium\\n'
            fi
            return "${TEST_MVND_STATUS:-0}"
        }
        return "${TEST_INIT_STATUS:-0}"
        """;

    @TempDir
    Path temp;

    /**
     * 参数化场景：环境变量覆盖与期望结果。
     *
     * @param overrides 环境变量覆盖
     * @param success   是否应初始化成功
     * @author Apex Team
     * @since 1.3.1
     */
    record Case(Map<String, String> overrides, boolean success) {}

    /**
     * 构造初始化脚本的参数化场景集合。
     *
     * @return 场景流
     */
    static Stream<Case> cases() {
        return Stream.of(
                new Case(Map.of(), true),
                new Case(
                        Map.of(
                                "TEST_MVND_OUTPUT",
                                "Oct 16, 2026 10:00:00 AM org.jline.utils.Log logr\n"
                                        + "WARNING: Unable to create a system terminal, creating a dumb terminal\n"
                                        + "Apache Maven Daemon (mvnd) 2.0.0-rc-3 darwin-amd64 native client (f912abcd)\n"
                                        + "Terminal: org.jline.terminal.impl.DumbTerminal\n"
                                        + "Apache Maven 4.0.0-rc-3 (abcdef)\n"
                                        + "Java version: 21.0.13, vendor: Eclipse Adoptium\n"),
                        true),
                new Case(Map.of("TEST_INIT_STATUS", "11"), false),
                new Case(Map.of("TEST_SDK_STATUS", "12"), false),
                new Case(Map.of("TEST_JAVA_STATUS", "13"), false),
                new Case(Map.of("TEST_JAVA_VERSION", "25.0.1"), false),
                new Case(Map.of("TEST_JAVA_HOME", "current"), false),
                new Case(Map.of("TEST_MVND_STATUS", "14"), false),
                new Case(Map.of("TEST_MVND_VERSION", "2.0.0-rc-2"), false),
                new Case(Map.of("TEST_MVND_VERSION", "2.0.0-rc-30"), false),
                new Case(Map.of("TEST_MVND_OUTPUT", "Apache Maven 2.0.0-rc-3"), false),
                new Case(
                        Map.of(
                                "TEST_MVND_OUTPUT",
                                "Apache Maven Daemon (mvnd) 2.0.0-rc-30 darwin-amd64 native client\nApache Maven 2.0.0-rc-3\n"),
                        false),
                new Case(
                        Map.of(
                                "TEST_MVND_OUTPUT",
                                "WARNING: Apache Maven Daemon (mvnd) 2.0.0-rc-3\nApache Maven 2.0.0-rc-3\n"),
                        false));
    }

    @ParameterizedTest
    @MethodSource("cases")
    void initializationInAllModes(Case scenario) throws Exception {
        Path init = temp.resolve(".sdkman/bin/sdkman-init.sh");
        Files.createDirectories(init.getParent());
        Files.writeString(init, SDKMAN);
        Files.writeString(
                temp.resolve(".sdkmanrc"), "java=21.0.13.0+9-tem\nmvnd=2.0.0-rc-3\nSECRET=must-not-be-printed\n");
        String check = "[[ \"$APEX_TEST_ACTIVATED\" == yes ]] || exit 93\n"
                + "[[ \"$JAVA_HOME\" == \"$HOME/.sdkman/candidates/java/21.0.13.0+9-tem\" ]] || exit 94\n";
        List<List<String>> commands = List.of(
                List.of("/bin/zsh", "-f", SCRIPT.toString()),
                List.of("/bin/zsh", "-f", "-c", "source \"$1\"\n" + check, "test-init", SCRIPT.toString()),
                List.of(
                        "/bin/zsh",
                        "-f",
                        "-c",
                        "if source \"$1\"; then\n" + check + "\nelse exit $?; fi",
                        "test-init",
                        SCRIPT.toString()));
        for (List<String> command : commands) {
            ProcessBuilder builder =
                    new ProcessBuilder(command).directory(temp.toFile()).redirectErrorStream(true);
            builder.environment().clear();
            builder.environment().putAll(Map.of("HOME", temp.toString(), "PATH", "/usr/bin:/bin", "LC_ALL", "C"));
            builder.environment().putAll(scenario.overrides());
            Process process = builder.start();
            assertTrue(process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS), "build initialization timed out");
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertFalse(output.contains("must-not-be-printed"));
            if (scenario.success()) {
                assertEquals(0, process.exitValue(), output);
                assertEquals(1, output.split("构建环境检查通过", -1).length - 1, output);
                assertTrue(output.contains("Java:"), output);
                assertTrue(output.contains("mvnd: Apache Maven Daemon (mvnd) 2.0.0-rc-3"), output);
            } else {
                assertNotEquals(0, process.exitValue(), output);
                assertFalse(output.contains("构建环境检查通过"), output);
            }
        }
    }
}
