package com.apex.scaffold.tools.deployment;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 本地部署离线运维工具：校验加密密钥与部署配置，生成 TOTP 敏感字段迁移包并可回滚。
 *
 * <p>子命令：{@code validate}、{@code preflight}、{@code compose}、{@code prepare}、{@code rollback}；
 * 退出码 0 表示通过，1 表示校验失败，2 表示参数无效。工具不连接数据库。
 *
 * @author Apex Team
 * @since 1.3.1
 */
public final class LocalDeployment {

    /** 工具自身定位仓库根目录的系统属性。 */
    public static final String ROOT_PROPERTY = "apex.root";

    /** 旧数据加密密钥变量名，用于密文轮换。 */
    private static final String OLD_DATA_ENCRYPTION_KEY = "TOTP_OLD_DATA_ENCRYPTION_KEY";

    /** 退出码：通过。 */
    private static final int EXIT_OK = 0;

    /** 退出码：校验或执行失败。 */
    private static final int EXIT_FAILURE = 1;

    /** 退出码：参数无效。 */
    private static final int EXIT_USAGE = 2;

    /** 使用说明。 */
    private static final String USAGE = "validate | preflight --target compose|nacos-datasource-prod"
            + " | compose [--env-file PATH]"
            + " | prepare --input PATH --output PATH"
            + " | rollback --bundle PATH --output PATH";

    /** 参数无效提示。 */
    private static final String USAGE_HINT = "参数无效；请使用 --help";

    /** 成功横幅。 */
    private static final String SUCCESS_BANNER = "验证完成；未连接或修改数据库";

    /** 失败提示：不输出敏感诊断信息。 */
    private static final String FAILURE_HINT = "操作失败：请检查输入格式、密钥、依赖和输出路径；敏感诊断信息已隐藏";

    /**
     * 参数解析结果：子命令、选项与需要的位置参数集合。
     *
     * @param name 命令名称
     * @param required 是否为必填命令
     * @param allowed 允许的子命令集合
     * @param options 选项说明
     */
    private record Command(String name, Set<String> required, Set<String> allowed, Map<String, String> options) {}

    private LocalDeployment() {}

    /**
     * 可测试入口：解析命令行、执行子命令并返回退出码。
     *
     * @param args        命令行参数
     * @param environment 环境变量映射
     * @param out         标准输出
     * @param err         标准错误
     * @return 退出码
     */
    public static int run(String[] args, Map<String, String> environment, PrintStream out, PrintStream err) {
        if (args.length == 1 && isHelp(args[0])) {
            out.println(USAGE);
            return EXIT_OK;
        }
        if (args.length == 0) {
            err.println(USAGE_HINT);
            return EXIT_USAGE;
        }
        Map<String, String> options = parseOptions(args);
        Command command = command(args[0], options);
        if (command == null || !validOptions(command)) {
            err.println(USAGE_HINT);
            return EXIT_USAGE;
        }
        try {
            execute(command, environment, out);
            out.println(SUCCESS_BANNER);
            return EXIT_OK;
        } catch (Exception error) {
            err.println("preflight".equals(command.name()) ? error.getMessage() : FAILURE_HINT);
            return EXIT_FAILURE;
        }
    }

    /**
     * 进程入口。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        System.exit(run(args, System.getenv(), System.out, System.err));
    }

    /**
     * 执行已验证的子命令。
     *
     * @param command     子命令
     * @param environment 环境变量映射
     * @param out         标准输出
     * @throws Exception 子命令执行失败时抛出
     */
    private static void execute(Command command, Map<String, String> environment, PrintStream out) throws Exception {
        switch (command.name()) {
            case "validate" -> DeploymentKeyValidator.validateKeys(environment);
            case "preflight" -> DeploymentKeyValidator.validatePreflight(
                    environment, command.options().get("--target"));
            case "compose" -> ComposeConfigValidator.composeCheck(root(), envFile(command.options()), environment);
            case "prepare" -> prepare(command.options(), environment, out);
            case "rollback" -> TotpMigrationBundle.rollback(
                    Path.of(command.options().get("--bundle")),
                    Path.of(command.options().get("--output")));
            default -> throw new IllegalArgumentException(USAGE_HINT);
        }
    }

    /**
     * 生成迁移包并打印统计数字。
     *
     * @param options     选项
     * @param environment 环境变量映射
     * @param out         标准输出
     * @throws Exception 密钥缺失或迁移失败时抛出
     */
    private static void prepare(Map<String, String> options, Map<String, String> environment, PrintStream out)
            throws Exception {
        byte[] key = DeploymentKeyValidator.decodeKey(environment.get("APEX_DATA_ENCRYPTION_KEY"));
        byte[] oldKey = environment.containsKey(OLD_DATA_ENCRYPTION_KEY)
                ? DeploymentKeyValidator.decodeKey(environment.get(OLD_DATA_ENCRYPTION_KEY))
                : null;
        var report = TotpMigrationBundle.prepare(
                Path.of(options.get("--input")), Path.of(options.get("--output")), key, oldKey);
        out.printf(
                "rows=%d, changed_rows=%d, changed_fields=%d%n",
                report.get("rows").asInt(),
                report.get("changed_rows").asInt(),
                report.get("changed_fields").asInt());
    }

    /**
     * 解析子命令及其必需、允许的选项。
     *
     * @param name    子命令名
     * @param options 已解析选项
     * @return 子命令定义，未知子命令返回 null
     */
    private static Command command(String name, Map<String, String> options) {
        return switch (name) {
            case "validate" -> new Command(name, Set.of(), Set.of(), options);
            case "compose" -> new Command(name, Set.of(), Set.of("--env-file"), options);
            case "preflight" -> new Command(name, Set.of("--target"), Set.of("--target"), options);
            case "prepare" -> new Command(name, Set.of("--input", "--output"), Set.of("--input", "--output"), options);
            case "rollback" -> new Command(
                    name, Set.of("--bundle", "--output"), Set.of("--bundle", "--output"), options);
            default -> null;
        };
    }

    /**
     * 校验选项集合与取值：不多不少，且 preflight 目标必须在允许范围内。
     *
     * @param command 子命令定义
     * @return 合法时返回 true
     */
    private static boolean validOptions(Command command) {
        Set<String> options = command.options().keySet();
        boolean matches =
                options.containsAll(command.required()) && command.allowed().containsAll(options);
        if (!matches) {
            return false;
        }
        if (!"preflight".equals(command.name())) {
            return true;
        }
        String target = command.options().get("--target");
        return DeploymentKeyValidator.TARGET_COMPOSE.equals(target)
                || DeploymentKeyValidator.TARGET_NACOS_DATASOURCE_PROD.equals(target);
    }

    /**
     * 按 `--name value` 成对解析选项；重复、缺失取值或非选项前缀均视为无效。
     *
     * @param args 命令行参数
     * @return 选项映射；格式无效时返回空映射，交由后续校验拒绝
     */
    private static Map<String, String> parseOptions(String[] args) {
        Map<String, String> options = new HashMap<>(args.length);
        boolean valid = args.length % 2 == 1;
        for (int i = 1; valid && i < args.length; i += 2) {
            if (i + 1 >= args.length || !args[i].startsWith("--") || options.put(args[i], args[i + 1]) != null) {
                valid = false;
            }
        }
        return valid ? options : Map.of("--invalid", "");
    }

    /**
     * 定位仓库根目录：优先使用 {@code -Dapex.root}，否则回退到当前工作目录。
     *
     * @return 仓库根目录绝对路径
     */
    private static Path root() {
        return Path.of(System.getProperty(ROOT_PROPERTY, ".")).toAbsolutePath().normalize();
    }

    /**
     * 读取可选的环境变量文件路径。
     *
     * @param options 选项映射
     * @return 环境变量文件路径；未指定时返回 null
     */
    private static Path envFile(Map<String, String> options) {
        String path = options.get("--env-file");
        return path == null ? null : Path.of(path);
    }

    /**
     * 判断是否为帮助参数。
     *
     * @param arg 第一个参数
     * @return 是帮助参数时返回 true
     */
    private static boolean isHelp(String arg) {
        return "--help".equals(arg) || "-h".equals(arg);
    }
}
