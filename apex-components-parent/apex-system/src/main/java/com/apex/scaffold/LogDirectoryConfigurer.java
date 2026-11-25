package com.apex.scaffold;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 在 Spring Boot 初始化 Logback 之前解析应用日志目录。
 *
 * <p>相对路径 {@code logs} 按进程工作目录解析；Maven、IDE 与可执行 JAR 的工作目录可能不同，
 * 曾因此产生多份日志目录树。开发期本类把日志锚定到 Maven 反应堆根目录，部署环境可用
 * {@code APEX_LOG_DIR}（或 {@code -Dapex.log.dir}）指定明确位置。
 *
 * <p>此外，本类负责设置 Sentinel SDK 的内部日志目录（{@code csp.sentinel.log.dir}）。
 * Sentinel 的 BlockLog 与指标日志使用原生文件写入，必须通过 JVM 系统属性指定其输出目录，
 * 否则默认写入 {@code ~/logs/csp/}。RecordLog 与 CommandCenterLog 由
 * {@code sentinel-logging-slf4j} 桥接到 SLF4J，由 Logback 路由至
 * {@code logs/sentinel/sentinel.log}（见 {@code logback-spring.xml}）。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public final class LogDirectoryConfigurer {

    static final String LOG_DIRECTORY_PROPERTY = "apex.log.dir";
    private static final String LOG_DIRECTORY_ENVIRONMENT_VARIABLE = "APEX_LOG_DIR";
    private static final String LOG_DIRECTORY_NAME = "logs";
    private static final String SYSTEM_MODULE = "apex-system";

    /** 组件日志子目录列表，确保组件日志系统和 Logback RollingFileAppender 可写入 */
    private static final String[] COMPONENT_LOG_SUBDIRS = {"nacos", "sentinel", "rocketmq"};

    /**
     * Sentinel SDK 原生日志目录系统属性键。
     *
     * <p>Sentinel 的 BlockLog 与指标日志使用原生文件写入（{@code com.alibaba.csp.sentinel.log.LogBase}）。
     * 必须通过此 JVM 系统属性指定其输出目录，否则默认写入 {@code ${user.home}/logs/csp/}。
     * RecordLog 与 CommandCenterLog 不受此属性影响，已由 {@code sentinel-logging-slf4j}
     * 桥接到 SLF4J，由 Logback 负责路由。
     */
    private static final String SENTINEL_LOG_DIR_PROPERTY = "csp.sentinel.log.dir";

    /** 工具类禁止实例化。 */
    private LogDirectoryConfigurer() {}

    /**
     * 在系统属性尚未提供时解析并写入绝对日志目录
     *
     * <p>优先读取系统属性 {@code apex.log.dir}，命中时按进程工作目录将相对路径解析为绝对路径并回写；
     * 否则依次回退到环境变量 {@code APEX_LOG_DIR}，最终锚定到最近的 Maven 反应堆根目录（找不到则退回工作目录）。
     * 解析完成后统一回写系统属性、创建组件日志子目录并设置 Sentinel 原生日志目录。
     *
     * <p>该方法为静态初始化入口，目录创建失败不抛出异常，避免阻断应用启动；重复调用结果一致。
     */
    public static void configure() {
        String systemValue = System.getProperty(LOG_DIRECTORY_PROPERTY);
        if (systemValue != null && !systemValue.isBlank()) {
            Path logDir = resolveConfiguredDirectory(Path.of(System.getProperty("user.dir")), systemValue);
            System.setProperty(LOG_DIRECTORY_PROPERTY, logDir.toString());
            createComponentSubdirectories(logDir);
            configureSentinelLogDir(logDir);
            return;
        }

        Path workingDirectory = Path.of(System.getProperty("user.dir"));
        String environmentValue = System.getenv(LOG_DIRECTORY_ENVIRONMENT_VARIABLE);
        Path logDirectory = environmentValue == null || environmentValue.isBlank()
                ? resolveDefaultDirectory(workingDirectory)
                : resolveConfiguredDirectory(workingDirectory, environmentValue);
        System.setProperty(LOG_DIRECTORY_PROPERTY, logDirectory.toString());
        createComponentSubdirectories(logDirectory);
        configureSentinelLogDir(logDirectory);
    }

    /**
     * 设置 Sentinel SDK 原生日志输出目录到项目 {@code logs/sentinel/}。
     *
     * <p>仅在系统属性未显式设置时生效，避免覆盖用户通过 {@code -Dcsp.sentinel.log.dir}
     * 传入的自定义值。调用此方法后，Sentinel 原生写入的 {@code sentinel-block.log}
     * 和指标日志将落入项目日志目录下的 {@code sentinel/} 子目录。
     * RecordLog 与 CommandCenterLog 已桥接到 SLF4J，其输出由 {@code logback-spring.xml}
     * 中的 {@code SENTINEL_FILE} appender 决定，不受此属性控制。
     *
     * @param logDirectory 已解析的日志根目录
     */
    private static void configureSentinelLogDir(Path logDirectory) {
        if (System.getProperty(SENTINEL_LOG_DIR_PROPERTY) != null) {
            return;
        }
        Path sentinelLogDir = logDirectory.resolve("sentinel");
        try {
            Files.createDirectories(sentinelLogDir);
            System.setProperty(SENTINEL_LOG_DIR_PROPERTY, sentinelLogDir.toString());
        } catch (Exception ignored) {
            // 创建失败时 Sentinel 将回退到默认 ~/logs/csp/ 目录
        }
    }

    /**
     * 解析默认日志目录，可用时优先锚定到 Maven 反应堆根目录。
     *
     * @param workingDirectory 进程工作目录
     * @return 绝对且规范化后的日志目录
     */
    static Path resolveDefaultDirectory(Path workingDirectory) {
        Path normalizedWorkingDirectory = workingDirectory.toAbsolutePath().normalize();
        Path current = normalizedWorkingDirectory;
        while (current != null) {
            if (isProjectRoot(current)) {
                return current.resolve(LOG_DIRECTORY_NAME);
            }
            current = current.getParent();
        }
        return normalizedWorkingDirectory.resolve(LOG_DIRECTORY_NAME);
    }

    /**
     * 解析显式配置的绝对路径或相对工作目录的路径。
     *
     * @param workingDirectory 进程工作目录
     * @param configuredValue 配置的日志路径
     * @return 绝对且规范化后的日志目录
     */
    private static Path resolveConfiguredDirectory(Path workingDirectory, String configuredValue) {
        Path configuredPath = Path.of(configuredValue);
        if (!configuredPath.isAbsolute()) {
            configuredPath = workingDirectory.resolve(configuredPath);
        }
        return configuredPath.toAbsolutePath().normalize();
    }

    /**
     * 判断目录是否为本 Maven 反应堆根目录。
     *
     * @param directory 待判定目录
     * @return 根 POM 与 system 模块 POM 同时存在时为 {@code true}
     */
    private static boolean isProjectRoot(Path directory) {
        return Files.isRegularFile(directory.resolve("pom.xml"))
                && Files.isRegularFile(directory.resolve(SYSTEM_MODULE).resolve("pom.xml"));
    }

    /**
     * 确保组件日志子目录存在，避免 RollingFileAppender 因父目录缺失而写入失败。
     *
     * @param logDirectory 已解析的日志根目录
     */
    private static void createComponentSubdirectories(Path logDirectory) {
        for (String subdir : COMPONENT_LOG_SUBDIRS) {
            try {
                Path dir = logDirectory.resolve(subdir);
                Files.createDirectories(dir);
            } catch (Exception ignored) {
                // 创建失败不影响主流程启动，日志会降级到控制台
            }
        }
    }
}
