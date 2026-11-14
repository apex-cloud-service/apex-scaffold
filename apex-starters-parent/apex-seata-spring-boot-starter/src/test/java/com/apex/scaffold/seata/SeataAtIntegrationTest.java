package com.apex.scaffold.seata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import javax.sql.DataSource;
import org.apache.seata.core.context.RootContext;
import org.apache.seata.core.rpc.netty.RmNettyRemotingClient;
import org.apache.seata.core.rpc.netty.TmNettyRemotingClient;
import org.apache.seata.rm.RMClient;
import org.apache.seata.rm.datasource.DataSourceProxy;
import org.apache.seata.tm.TMClient;
import org.apache.seata.tm.api.GlobalTransactionContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

/** 外部真实 TC、两个独立 MySQL 数据库及两个 HTTP 分支服务；禁止使用生产数据库。 */
@EnabledIfEnvironmentVariable(named = "APEX_SEATA_IT", matches = "true")
class SeataAtIntegrationTest {
    @RegisterExtension
    static final SeataRealCluster CLUSTER = SeataRealCluster.of("AT");

    private static final SeataTransactionExecutor EXECUTOR =
            new SeataTransactionExecutor(GlobalTransactionContext::createNew, 30000);
    private static final HttpServer[] SERVERS = new HttpServer[2];
    private static final JdbcTemplate[] PLAIN = new JdbcTemplate[2];

    @BeforeAll
    static void setup() throws Exception {
        assertThat(required("APEX_SEATA_IT_DB1")).as("两个分支必须使用不同测试数据库").isNotEqualTo(required("APEX_SEATA_IT_DB2"));
        TMClient.init("apex-seata-it", "apex_seata_it_group");
        RMClient.init("apex-seata-it", "apex_seata_it_group");
        for (int index = 0; index < 2; index++) {
            String url = required("APEX_SEATA_IT_DB" + (index + 1));
            DataSource plain = new DriverManagerDataSource(
                    url, required("APEX_SEATA_IT_USER"), required("APEX_SEATA_IT_PASSWORD"));
            PLAIN[index] = new JdbcTemplate(plain);
            PLAIN[index].execute(
                    "CREATE TABLE IF NOT EXISTS undo_log (branch_id BIGINT NOT NULL, xid VARCHAR(128) NOT NULL,"
                            + " context VARCHAR(128) NOT NULL, rollback_info LONGBLOB NOT NULL, log_status INT NOT NULL,"
                            + " log_created DATETIME(6) NOT NULL, log_modified DATETIME(6) NOT NULL, UNIQUE KEY ux_undo_log(xid,branch_id)) ENGINE=InnoDB");
            PLAIN[index].execute(
                    "CREATE TABLE IF NOT EXISTS apex_seata_it_account(id BIGINT PRIMARY KEY, balance INT NOT NULL) ENGINE=InnoDB");
            PLAIN[index].execute("INSERT INTO apex_seata_it_account VALUES(1,100) ON DUPLICATE KEY UPDATE balance=100");
            DataSource proxy = new DataSourceProxy(plain);
            TransactionTemplate local = new TransactionTemplate(new DataSourceTransactionManager(proxy));
            JdbcTemplate jdbc = new JdbcTemplate(proxy);
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/branch", exchange -> {
                String xid = exchange.getRequestHeaders().getFirst("X-Test-Xid");
                try {
                    EXECUTOR.branch(() -> local.execute(status ->
                                    jdbc.update("UPDATE apex_seata_it_account SET balance=balance-10 WHERE id=1")))
                            .contextWrite(context -> context.put(SeataTransactionExecutor.XID_CONTEXT_KEY, xid))
                            .block();
                    exchange.sendResponseHeaders(204, -1);
                } catch (Exception failure) {
                    exchange.sendResponseHeaders(500, -1);
                } finally {
                    exchange.close();
                }
            });
            server.start();
            SERVERS[index] = server;
        }
    }

    @Test
    void commitsAndRollsBackAcrossTwoHttpServicesAndDatabases() {
        EXECUTOR.execute("commit-two-services", () -> {
                    invokeBoth();
                    return true;
                })
                .block();
        assertBalances(90);
        assertThatThrownBy(() -> EXECUTOR.execute("rollback-two-services", () -> {
                            invokeBoth();
                            throw new IllegalStateException("force-global-rollback");
                        })
                        .block())
                .isInstanceOf(IllegalStateException.class);
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        while (System.nanoTime() < deadline && (balance(0) != 90 || balance(1) != 90)) {
            java.util.concurrent.locks.LockSupport.parkNanos(
                    Duration.ofMillis(100).toNanos());
        }
        assertBalances(90);
    }

    private static void invokeBoth() throws Exception {
        HttpClient client =
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        for (HttpServer server : SERVERS) {
            HttpRequest request = HttpRequest.newBuilder(
                            URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/branch"))
                    .timeout(Duration.ofSeconds(10))
                    .header("X-Test-Xid", RootContext.getXID())
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
            assertThat(client.send(request, HttpResponse.BodyHandlers.discarding())
                            .statusCode())
                    .isEqualTo(204);
        }
    }

    private static int balance(int index) {
        return PLAIN[index].queryForObject("SELECT balance FROM apex_seata_it_account WHERE id=1", Integer.class);
    }

    private static void assertBalances(int expected) {
        assertThat(balance(0)).isEqualTo(expected);
        assertThat(balance(1)).isEqualTo(expected);
    }

    private static String required(String name) {
        return SeataRealCluster.required(name);
    }

    @AfterAll
    static void close() {
        for (HttpServer server : SERVERS) {
            if (server != null) {
                server.stop(0);
            }
        }
        TmNettyRemotingClient.getInstance().destroy();
        RmNettyRemotingClient.getInstance().destroy();
    }
}
