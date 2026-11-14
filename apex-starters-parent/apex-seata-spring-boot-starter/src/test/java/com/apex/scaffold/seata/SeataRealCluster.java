package com.apex.scaffold.seata;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * 真实 Seata 验证所需的容器环境：一个隔离 TC 与两个专用 MySQL 数据库。
 *
 * <p>全部编排都在测试 JVM 内通过 podman 完成，不再依赖 shell 脚本：
 *
 * <ol>
 *   <li>校验测试库容器（默认 {@code apex-infra-mysql}）健康且 3306 只映射到宿主环回地址；</li>
 *   <li>校验 TC 镜像已在本地存在，门禁内不做隐式联网拉取；</li>
 *   <li>为本测试类启动唯一命名的 TC 容器（容器内 file 存储），宿主端口由 podman 动态分配后读回；</li>
 *   <li>创建两个互不相同的专用库与专用账号，写入系统属性供测试读取；</li>
 *   <li>测试成功时删除容器、库与账号；失败时保留，供人工排查。</li>
 * </ol>
 *
 * <p>每个模式测试类各自持有一个 TC 与一套库，配合 Failsafe {@code reuseForks=false} 的独立 JVM，
 * 隔离 Seata 的静态 TM/RM 生命周期。
 */
final class SeataRealCluster implements BeforeAllCallback, ExtensionContext.Store.CloseableResource {
    /** 与 {@code seata-test.conf} 中的分组映射一致的测试事务分组。 */
    static final String TX_GROUP = "apex_seata_it_group";
    /** TC 容器内监听端口；宿主端口由 podman 分配，避免并行测试抢占固定端口。 */
    static final int TC_CONTAINER_PORT = 18091;

    private static final String TC_IMAGE = System.getProperty("apex.seata.it.image", "apache/seata-server:2.2.0");
    private static final String MYSQL_CONTAINER =
            System.getProperty("apex.seata.it.mysql-container", "apex-infra-mysql");
    private static final String MYSQL_HOST = "127.0.0.1";
    private static final Duration TC_READY_TIMEOUT = Duration.ofSeconds(120);
    /** Seata Server 启动完成的日志标记（2.2.0 输出 {@code seata server started in N millSeconds}）。 */
    private static final String TC_STARTED_MARKER = "seata server started";

    private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(60);
    private static final String PROVIDED_KEY = SeataRealCluster.class.getName() + ".provided";

    private final String mode;
    private final String suffix;
    private final List<String> createdDatabases = new ArrayList<>();
    private final List<String> createdUsers = new ArrayList<>();
    private String tcName;
    private int tcHostPort;
    private String databaseUser;
    private String databasePassword;
    private boolean ready;

    private SeataRealCluster(String mode) {
        this.mode = mode;
        this.suffix = mode.toLowerCase() + "_" + Long.toString(System.currentTimeMillis(), 36) + "_"
                + Integer.toHexString(new SecureRandom().nextInt(0xFFFF));
    }

    @Override
    public void beforeAll(ExtensionContext context) {
        // 测试库容器与 TC 镜像属于外部准备条件：缺失时直接失败，不隐式创建或联网。
        String health = exec("podman", "inspect", "--format", "{{.State.Health.Status}}", MYSQL_CONTAINER);
        if (!"healthy".equals(health)) {
            throw new IllegalStateException("测试 MySQL 容器 " + MYSQL_CONTAINER + " 必须处于 healthy 状态，实际为 " + health);
        }
        String mapping = exec("podman", "port", MYSQL_CONTAINER, "3306/tcp");
        if (!mapping.startsWith(MYSQL_HOST + ":")) {
            throw new IllegalStateException("测试 MySQL 容器必须把 3306 映射到 " + MYSQL_HOST + "，实际为 " + mapping);
        }
        int imageStatus = run(List.of("podman", "image", "inspect", "--format", "{{.Id}}", TC_IMAGE), null);
        if (imageStatus != 0) {
            throw new IllegalStateException("缺少 TC 镜像，请先执行 podman pull " + TC_IMAGE);
        }
        startTc();
        String databaseA;
        String databaseB;
        try {
            databaseA = createDatabase("a");
            databaseB = createDatabase("b");
            createUser();
            int mysqlHostPort = Integer.parseInt(mapping.substring(mapping.lastIndexOf(':') + 1));
            System.setProperty("APEX_SEATA_IT_DB1", jdbcUrl(mysqlHostPort, databaseA));
            System.setProperty("APEX_SEATA_IT_DB2", jdbcUrl(mysqlHostPort, databaseB));
            System.setProperty("APEX_SEATA_IT_USER", databaseUser);
            System.setProperty("APEX_SEATA_IT_PASSWORD", databasePassword);
            configureClient();
            ready = true;
        } catch (RuntimeException failure) {
            close();
            throw failure;
        }
        context.getStore(ExtensionContext.Namespace.GLOBAL).put(PROVIDED_KEY, this);
        System.err.printf("真实 %s 验证环境就绪：TC=127.0.0.1:%d，测试库=%s/%s%n", mode, tcHostPort, databaseA, databaseB);
    }

    private void startTc() {
        tcName = "apex-seata-it-" + suffix;
        exec(
                "podman",
                "run",
                "-d",
                "--pull=never",
                "--name",
                tcName,
                "--label",
                "apex.seata-it=true",
                "-p",
                MYSQL_HOST + "::" + TC_CONTAINER_PORT,
                "-e",
                "SEATA_IP=" + MYSQL_HOST,
                "-e",
                "SEATA_PORT=" + TC_CONTAINER_PORT,
                "-e",
                "STORE_MODE=file",
                TC_IMAGE);
        String mapping = exec("podman", "port", tcName, TC_CONTAINER_PORT + "/tcp");
        String port = mapping.substring(mapping.lastIndexOf(':') + 1).trim();
        tcHostPort = Integer.parseInt(port);
        awaitTcStarted();
        awaitTcPortAccepts();
    }

    /**
     * 等待容器内日志确认 Seata Server 已真正启动。
     *
     * <p>podman 的端口转发（gvproxy）在容器启动瞬间就会接受宿主连接，仅探测端口会把「尚未初始化完成」
     * 误判为就绪，客户端随后在 TM/RM 注册时收到 {@code StacklessClosedChannelException}。因此就绪判据
     * 必须是服务自身输出的启动完成日志。
     */
    private void awaitTcStarted() {
        long deadline = System.nanoTime() + TC_READY_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            String logs = exec("podman", "logs", tcName);
            if (logs.contains(TC_STARTED_MARKER)) {
                return;
            }
            if (!"true".equals(exec("podman", "inspect", "--format", "{{.State.Running}}", tcName))) {
                throw new IllegalStateException("隔离 TC 容器提前退出：" + tcName + "，日志尾部：" + tail(logs));
            }
            sleep(Duration.ofSeconds(2));
        }
        throw new IllegalStateException("隔离 TC 未在 " + TC_READY_TIMEOUT.toSeconds() + " 秒内完成启动：" + tcName);
    }

    /** 服务已启动后再确认宿主侧端口转发可用，避免容器内监听但宿主不可达。 */
    private void awaitTcPortAccepts() {
        long deadline = System.nanoTime() + TC_READY_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(MYSQL_HOST, tcHostPort), 1000);
                return;
            } catch (IOException notForwarded) {
                // 端口转发建立期间拒绝连接属于预期，继续等待直到超时。
            }
            sleep(Duration.ofSeconds(1));
        }
        throw new IllegalStateException("隔离 TC 端口 " + MYSQL_HOST + ":" + tcHostPort + " 在超时内不可达");
    }

    private static String tail(String text) {
        String[] lines = text.strip().split("\\R");
        int from = Math.max(0, lines.length - 5);
        return String.join(" | ", List.of(lines).subList(from, lines.length));
    }

    private String createDatabase(String tag) {
        String database = "apex_seata_it_" + suffix + "_" + tag;
        adminSql("CREATE DATABASE `" + database + "`;");
        createdDatabases.add(database);
        return database;
    }

    private void createUser() {
        databaseUser = "seata_it_" + suffix;
        databasePassword = HexFormat.of().formatHex(randomBytes(24));
        adminSql("CREATE USER `" + databaseUser + "`@`%` IDENTIFIED BY '" + databasePassword + "';");
        createdUsers.add(databaseUser);
        for (String database : createdDatabases) {
            adminSql("GRANT ALL ON `" + database + "`.* TO `" + databaseUser + "`@`%`;");
        }
        // MySQL 要求非 SUPER 账号显式持有 XA_RECOVER_ADMIN 才能执行 XA RECOVER，
        // 否则返回 XAE03 / 1401 XAER_RMERR（本机 MySQL 8.0.46 实测复现）。
        adminSql("GRANT XA_RECOVER_ADMIN ON *.* TO `" + databaseUser + "`@`%`;");
    }

    private static byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        new SecureRandom().nextBytes(bytes);
        return bytes;
    }

    private static String jdbcUrl(int port, String database) {
        return "jdbc:mysql://" + MYSQL_HOST + ":" + port + "/" + database
                + "?useSSL=false&allowPublicKeyRetrieval=true";
    }

    /**
     * 直连 TC 所需的客户端系统属性；必须在任何 Seata 类初始化之前设置。
     *
     * <p>file 配置类型只读本地 {@code seata-test.conf} 并按系统属性覆盖，不依赖注册中心。
     */
    private void configureClient() {
        System.setProperty("registry.type", "file");
        System.setProperty("config.type", "file");
        System.setProperty("seata.config.name", "seata-test");
        System.setProperty("service.vgroupMapping." + TX_GROUP, "default");
        System.setProperty("service.default.grouplist", MYSQL_HOST + ":" + tcHostPort);
        if ("SAGA".equals(mode)) {
            // SAGA 的分支注册默认关闭，真实双库验证必须显式打开。
            System.setProperty("client.rm.sagaBranchRegisterEnable", "true");
        }
    }

    private void adminSql(String sql) {
        int status = run(
                List.of(
                        "podman",
                        "exec",
                        "-i",
                        MYSQL_CONTAINER,
                        "sh",
                        "-c",
                        "MYSQL_PWD=\"$MYSQL_ROOT_PASSWORD\" mysql -uroot"),
                sql + System.lineSeparator());
        if (status != 0) {
            // 客户端错误信息可能回显含口令的整条语句，因此不输出原始 stderr。
            throw new IllegalStateException("MySQL 管理语句执行失败（退出码 " + status + "），已丢弃客户端原始输出");
        }
    }

    @Override
    public void close() {
        if (ready) {
            ready = false;
            List<String> failures = new ArrayList<>();
            if (tcName != null && run(List.of("podman", "rm", "-f", tcName), null) != 0) {
                failures.add("TC 容器 " + tcName);
            }
            for (String database : createdDatabases) {
                if (run(
                                List.of(
                                        "podman",
                                        "exec",
                                        "-i",
                                        MYSQL_CONTAINER,
                                        "sh",
                                        "-c",
                                        "MYSQL_PWD=\"$MYSQL_ROOT_PASSWORD\" mysql -uroot"),
                                "DROP DATABASE `" + database + "`;")
                        != 0) {
                    failures.add("测试库 " + database);
                }
            }
            for (String user : createdUsers) {
                if (run(
                                List.of(
                                        "podman",
                                        "exec",
                                        "-i",
                                        MYSQL_CONTAINER,
                                        "sh",
                                        "-c",
                                        "MYSQL_PWD=\"$MYSQL_ROOT_PASSWORD\" mysql -uroot"),
                                "DROP USER `" + user + "`@`%`;")
                        != 0) {
                    failures.add("账号 " + user);
                }
            }
            if (!failures.isEmpty()) {
                throw new IllegalStateException("验证已执行但隔离资源清理失败，需人工排查：" + String.join("、", failures));
            }
        } else if (tcName != null) {
            run(List.of("podman", "rm", "-f", tcName), null);
        }
    }

    /** 上一次 {@code beforeAll} 是否已完成环境准备，用于 {@code @AfterAll} 判断能否断言清理结果。 */
    boolean isReady() {
        return ready;
    }

    /**
     * 创建扩展实例，供测试类以 {@code @RegisterExtension} 声明。
     *
     * @param mode 事务模式名称，用于容器、库与账号命名
     * @return 尚未启动的扩展
     */
    static SeataRealCluster of(String mode) {
        return new SeataRealCluster(mode);
    }

    /**
     * 读取必填的集成测试参数；由本扩展写入系统属性，缺失即为环境准备失败。
     *
     * @param name 参数名
     * @return 参数值
     */
    static String required(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("缺少真实集成测试参数: " + name);
        }
        return value;
    }

    private static String exec(String... command) {
        StringBuilder output = new StringBuilder();
        int status = run(List.of(command), null, output);
        if (status != 0) {
            throw new IllegalStateException(String.join(" ", command) + " 执行失败（退出码 " + status + "）：" + output);
        }
        return output.toString().trim();
    }

    private static int run(List<String> command, String stdin) {
        return run(command, stdin, new StringBuilder());
    }

    private static int run(List<String> command, String stdin, StringBuilder output) {
        try {
            Process process =
                    new ProcessBuilder(command).redirectErrorStream(true).start();
            if (stdin != null) {
                try (var writer = process.getOutputStream()) {
                    writer.write(stdin.getBytes(StandardCharsets.UTF_8));
                }
            } else {
                process.getOutputStream().close();
            }
            output.append(new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
            if (!process.waitFor(COMMAND_TIMEOUT.toSeconds(), TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IllegalStateException(String.join(" ", command) + " 超时未结束");
            }
            return process.exitValue();
        } catch (IOException failure) {
            throw new UncheckedIOException("无法执行命令：" + String.join(" ", command), failure);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待命令结束时被中断：" + String.join(" ", command), interrupted);
        }
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待隔离 TC 就绪时被中断", interrupted);
        }
    }
}
