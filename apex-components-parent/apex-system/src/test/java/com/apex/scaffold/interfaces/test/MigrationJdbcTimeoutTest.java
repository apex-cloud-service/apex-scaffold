package com.apex.scaffold.interfaces.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MigrationJdbcTimeoutTest {

    @ParameterizedTest
    @ValueSource(strings = {"mysql", "postgresql"})
    void shouldBoundUnresponsiveDatabaseHandshake(String vendor) throws Exception {
        try (var server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            var executor = Executors.newSingleThreadExecutor();
            var accepted = executor.submit(server::accept);
            try {
                String url = SystemDualDatabaseMigrationIntegrationTest.jdbcUrl(
                        vendor, Integer.toString(server.getLocalPort()), "migration_timeout");
                if (vendor.equals("postgresql")) {
                    url += (url.contains("?") ? "&" : "?") + "sslmode=disable";
                }
                String connectionUrl = url;
                long start = System.nanoTime();
                // 安全阀只用于缺陷回归时解除阻塞，不作为驱动超时的替代。
                var watchdog = Executors.newSingleThreadScheduledExecutor();
                try {
                    watchdog.schedule(
                            () -> {
                                try (var socket = accepted.get(1, TimeUnit.SECONDS)) {
                                    socket.shutdownOutput();
                                } catch (Exception exception) {
                                    throw new IllegalStateException(exception);
                                }
                            },
                            15,
                            TimeUnit.SECONDS);
                    assertThatThrownBy(() -> DriverManager.getConnection(connectionUrl, "timeout_test", "timeout_test"))
                            .isInstanceOf(SQLException.class);
                    assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(12));
                    assertThat(accepted.isDone())
                            .as("握手必须到达本地数据库端口，不能因代理地址解析提前失败")
                            .isTrue();
                } finally {
                    watchdog.shutdownNow();
                }
            } finally {
                server.close();
                if (accepted.isDone() && !accepted.isCancelled()) {
                    accepted.get(1, TimeUnit.SECONDS).close();
                }
                executor.shutdownNow();
            }
        }
    }
}
