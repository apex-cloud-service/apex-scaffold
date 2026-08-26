package com.apex.scaffold;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Resolves the application log directory before Spring Boot initializes Logback.
 *
 * <p>A relative {@code logs} directory is resolved against the process working directory. Maven,
 * an IDE, and an executable JAR can use different working directories, which previously created
 * multiple log trees. During development this resolver anchors logs to the Maven reactor root. In
 * deployed environments {@code APEX_LOG_DIR} (or {@code -Dapex.log.dir}) can select an explicit
 * location.
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
final class LogDirectoryConfigurer {

    static final String LOG_DIRECTORY_PROPERTY = "apex.log.dir";
    private static final String LOG_DIRECTORY_ENVIRONMENT_VARIABLE = "APEX_LOG_DIR";
    private static final String LOG_DIRECTORY_NAME = "logs";
    private static final String INTERFACES_MODULE = "apex-interfaces";

    /** 组件日志子目录列表，确保组件日志系统和 Logback RollingFileAppender 可写入 */
    private static final String[] COMPONENT_LOG_SUBDIRS = {"nacos", "sentinel", "rocketmq", "zipkin"};

    /**
     * Sentinel SDK 原生日志目录系统属性键。
     *
     * <p>Sentinel 的 BlockLog 与指标日志使用原生文件写入（{@code com.alibaba.csp.sentinel.log.LogBase}）。
     * 必须通过此 JVM 系统属性指定其输出目录，否则默认写入 {@code ${user.home}/logs/csp/}。
     * RecordLog 与 CommandCenterLog 不受此属性影响，已由 {@code sentinel-logging-slf4j}
     * 桥接到 SLF4J，由 Logback 负责路由。
     */
    private static final String SENTINEL_LOG_DIR_PROPERTY = "csp.sentinel.log.dir";

    /** Prevents instantiation of the startup utility. */
    private LogDirectoryConfigurer() {}

    /** Configures the absolute log directory system property if it was not already supplied. */
    static void configure() {
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
     * Resolves the default directory, preferring the Maven reactor root when it is available.
     *
     * @param workingDirectory process working directory
     * @return absolute normalized log directory
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
     * Resolves an explicitly configured absolute or working-directory-relative path.
     *
     * @param workingDirectory process working directory
     * @param configuredValue configured log path
     * @return absolute normalized log directory
     */
    private static Path resolveConfiguredDirectory(Path workingDirectory, String configuredValue) {
        Path configuredPath = Path.of(configuredValue);
        if (!configuredPath.isAbsolute()) {
            configuredPath = workingDirectory.resolve(configuredPath);
        }
        return configuredPath.toAbsolutePath().normalize();
    }

    /**
     * Checks whether a directory is the root of this Maven reactor.
     *
     * @param directory candidate directory
     * @return {@code true} when the root and interfaces module POM files exist
     */
    private static boolean isProjectRoot(Path directory) {
        return Files.isRegularFile(directory.resolve("pom.xml"))
                && Files.isRegularFile(directory.resolve(INTERFACES_MODULE).resolve("pom.xml"));
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
