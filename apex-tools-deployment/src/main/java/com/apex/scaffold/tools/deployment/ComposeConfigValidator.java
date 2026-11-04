package com.apex.scaffold.tools.deployment;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * Compose 配置校验：通过 podman-compose config 展开锚点与插值后，再逐服务检查安全环境变量。
 *
 * @author Apex Team
 * @since 1.3.1
 */
public final class ComposeConfigValidator {

    /** 待校验的 Compose 文件名。 */
    private static final String COMPOSE_FILE = "podman-compose.yml";

    /** 服务定义节点名。 */
    private static final String SERVICES = "services";

    /** 服务内环境变量节点名。 */
    private static final String ENVIRONMENT = "environment";

    /** 标记应用服务（含数据加密密钥）的变量名。 */
    private static final String DATA_ENCRYPTION_KEY = "APEX_DATA_ENCRYPTION_KEY";

    /** 触发前置检查的 Redis 密码变量名。 */
    private static final String REDIS_PASSWORD = "REDIS_PASSWORD";

    /** YAML 解析码点上限，避免异常大文件耗尽内存。 */
    private static final int CODE_POINT_LIMIT = 16 * 1024 * 1024;

    private ComposeConfigValidator() {}

    /**
     * 解析 Compose YAML 文本，仅接受映射根节点。
     *
     * @param content YAML 文本
     * @return 根映射
     */
    public static Map<?, ?> yaml(String content) {
        LoaderOptions options = new LoaderOptions();
        options.setCodePointLimit(CODE_POINT_LIMIT);
        Object result = new Yaml(new SafeConstructor(options)).load(content);
        if (!(result instanceof Map<?, ?> mapping)) {
            throw new IllegalArgumentException("Compose 格式无效");
        }
        return mapping;
    }

    /**
     * 校验每个服务的安全环境变量：至少一个服务携带完整密钥，Redis 密码不得为模板回退值。
     *
     * @param content 已展开的 Compose YAML 文本
     */
    public static void validateCompose(String content) {
        Object services = yaml(content).get(SERVICES);
        if (!(services instanceof Map<?, ?> serviceMap)) {
            throw new IllegalArgumentException("Compose 缺少服务");
        }
        boolean secured = false;
        for (Object service : serviceMap.values()) {
            Map<?, ?> environment = environment(service);
            if (environment.containsKey(DATA_ENCRYPTION_KEY)) {
                DeploymentKeyValidator.validateKeys(environment);
                secured = true;
            }
            if (environment.containsKey(REDIS_PASSWORD)) {
                DeploymentKeyValidator.validatePreflight(environment, DeploymentKeyValidator.TARGET_COMPOSE);
            }
        }
        if (!secured) {
            throw new IllegalArgumentException("Compose 配置缺少应用安全环境变量");
        }
    }

    /**
     * 构造 podman-compose 校验命令。
     *
     * @param root    仓库根目录
     * @param envFile 可选的环境变量文件，可为 null
     * @return 命令参数列表
     */
    public static List<String> composeCommand(Path root, Path envFile) {
        List<String> command = new ArrayList<>(
                List.of("podman-compose", "-f", root.resolve(COMPOSE_FILE).toString()));
        if (envFile != null) {
            command.addAll(
                    List.of("--env-file", envFile.toAbsolutePath().normalize().toString()));
        }
        command.add("config");
        return command;
    }

    /**
     * 调用 podman-compose config 并校验展开结果；失败时输出一律隐藏，避免泄露密钥。
     *
     * <p>调用方的环境变量必须透传给子进程：compose 模板以 {@code ${VAR:?...}} 声明
     * 必填密钥（如 {@code APEX_INTERNAL_AUTH_SECRET}），缺失会直接阻断编排。
     * 测试场景下父进程环境被显式清空，若不透传则必填变量永远无法解析。
     *
     * @param root        仓库根目录
     * @param envFile     可选的环境变量文件，可为 null
     * @param environment 透传给 podman-compose 的环境变量映射，可为 null
     * @throws Exception 进程启动失败、退出码非零或内容校验失败时抛出
     */
    public static void composeCheck(Path root, Path envFile, Map<?, ?> environment) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(composeCommand(root, envFile))
                .directory(root.toFile())
                .redirectErrorStream(true);
        if (environment != null) {
            // 只追加显式变量，保留 PATH 等父进程基础环境，避免破坏 podman-compose 自身启动
            environment.forEach((key, value) -> {
                if (key instanceof String name && value instanceof String text) {
                    builder.environment().put(name, text);
                }
            });
        }
        Process process = builder.start();
        byte[] output = process.getInputStream().readAllBytes();
        int status = process.waitFor();
        if (status != 0) {
            throw new IllegalArgumentException("Compose 配置验证失败；输出已隐藏");
        }
        validateCompose(new String(output, StandardCharsets.UTF_8));
    }

    /**
     * 读取服务环境变量映射，缺失或非映射时按空环境处理。
     *
     * @param service 单个服务定义
     * @return 环境变量映射
     */
    private static Map<?, ?> environment(Object service) {
        if (service instanceof Map<?, ?> entry && entry.get(ENVIRONMENT) instanceof Map<?, ?> environment) {
            return environment;
        }
        return Map.of();
    }
}
