package com.apex.scaffold.seata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import javax.sql.DataSource;
import org.apache.seata.core.context.RootContext;
import org.apache.seata.core.model.BranchType;
import org.apache.seata.core.rpc.netty.RmNettyRemotingClient;
import org.apache.seata.core.rpc.netty.TmNettyRemotingClient;
import org.apache.seata.rm.DefaultResourceManager;
import org.apache.seata.rm.RMClient;
import org.apache.seata.rm.datasource.xa.DataSourceProxyXA;
import org.apache.seata.rm.datasource.xa.ResourceManagerXA;
import org.apache.seata.tm.TMClient;
import org.apache.seata.tm.api.GlobalTransactionContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 真实 TC、两个独立 MySQL 数据库及真实 XA 两阶段提交验证；禁止使用生产数据库。
 *
 * <p>使用 {@link DataSourceProxyXA} 注册 XA 分支资源，证据分三段，全部来自本轮真实运行：
 *
 * <ol>
 *   <li>两个分支完成后、全局二阶段之前，用 MySQL {@code XA RECOVER} 读取 prepared 事务：共有且仅有
 *       两条，其 gtrid 等于本次全局事务 XID、formatId 为 {@value #SEATA_XA_FORMAT_ID}、
 *       branchQualifier 为 {@code "-" + branchId} 且两者互不相同，证明两个分支确实由真实 RM 以 XA
 *       一阶段 prepare 落库。</li>
 *   <li>全局提交后两库余额均扣减，全局回滚后两库余额均复原；两库余额变化只能由各自分支的二阶段
 *       驱动，因此这一条同时证明两个 prepared 分支分别落在两个真实数据库。</li>
 *   <li>全局二阶段结束后 {@code XA RECOVER} 中不得残留任何 prepared 事务，证明清理由 TC 驱动的
 *       真实 XA 二阶段完成，而非本地提交或手工调用 XA commit/rollback。</li>
 * </ol>
 */
@EnabledIfEnvironmentVariable(named = "APEX_SEATA_IT", matches = "true")
class SeataXaIntegrationTest {
    @RegisterExtension
    static final SeataRealCluster CLUSTER = SeataRealCluster.of("XA");

    private static final int INITIAL_BALANCE = 100;
    private static final int AMOUNT = 10;
    private static final Duration PHASE_TWO_TIMEOUT = Duration.ofSeconds(20);
    private static final int SEATA_XA_FORMAT_ID = 9752;
    private static final String DEBIT_SQL = "UPDATE apex_seata_xa_account SET balance=balance-? WHERE id=1";

    private static final SeataTransactionExecutor EXECUTOR =
            new SeataTransactionExecutor(GlobalTransactionContext::createNew, 30000);
    private static JdbcTemplate plainA;
    private static JdbcTemplate plainB;
    private static DataSource rawA;
    private static DataSource rawB;
    private static JdbcTemplate jdbcA;
    private static JdbcTemplate jdbcB;
    private static TransactionTemplate localA;
    private static TransactionTemplate localB;

    @BeforeAll
    static void setup() {
        assertThat(required("APEX_SEATA_IT_DB1")).as("两个 XA 分支必须使用不同测试数据库").isNotEqualTo(required("APEX_SEATA_IT_DB2"));
        TMClient.init("apex-seata-it", "apex_seata_it_group");
        RMClient.init("apex-seata-it", "apex_seata_it_group");
        // 手动模式下 RMClient.init 不自动注册 XA 分支资源管理器；此处放入的是真实的
        // ResourceManagerXA 实例（方法名为历史遗留的 mock 命名），二阶段回调由它经真实 TC 驱动。
        DefaultResourceManager.mockResourceManager(BranchType.XA, new ResourceManagerXA());

        rawA = rawDataSource("APEX_SEATA_IT_DB1");
        rawB = rawDataSource("APEX_SEATA_IT_DB2");
        plainA = new JdbcTemplate(rawA);
        plainB = new JdbcTemplate(rawB);
        plainA.execute("CREATE TABLE IF NOT EXISTS apex_seata_xa_account("
                + "id BIGINT PRIMARY KEY, balance INT NOT NULL) ENGINE=InnoDB");
        plainB.execute("CREATE TABLE IF NOT EXISTS apex_seata_xa_account("
                + "id BIGINT PRIMARY KEY, balance INT NOT NULL) ENGINE=InnoDB");

        DataSourceProxyXA proxyA = new DataSourceProxyXA(rawA);
        DataSourceProxyXA proxyB = new DataSourceProxyXA(rawB);
        jdbcA = new JdbcTemplate(proxyA);
        jdbcB = new JdbcTemplate(proxyB);
        localA = new TransactionTemplate(new DataSourceTransactionManager(proxyA));
        localB = new TransactionTemplate(new DataSourceTransactionManager(proxyB));
    }

    @BeforeEach
    void reset() {
        resetBalance(plainA);
        resetBalance(plainB);
        RootContext.unbind();
        assertThat(recoverPrepared()).as("测试开始前不得残留 prepared XA 事务").isEmpty();
    }

    @Test
    void globalCommitPreparesThenClearsBothXaBranches() {
        AtomicReference<String> xid = new AtomicReference<>();
        AtomicReference<List<PreparedXa>> prepared = new AtomicReference<>();

        EXECUTOR.execute("xa-commit-two-databases", () -> {
                    xid.set(RootContext.getXID());
                    debit(localA, jdbcA);
                    debit(localB, jdbcB);
                    // 两个分支均已 XA PREPARE，此刻读取 prepared 事务（全局二阶段之前）。
                    prepared.set(recoverPrepared());
                    return true;
                })
                .block(PHASE_TWO_TIMEOUT);

        awaitBalances(INITIAL_BALANCE - AMOUNT, INITIAL_BALANCE - AMOUNT);
        assertTwoPreparedBranches(prepared.get(), xid.get());
        assertNoPreparedXa();
        assertThat(RootContext.getXID()).isNull();
    }

    @Test
    void globalRollbackPreparesThenClearsBothXaBranches() {
        int beforeA = balance(plainA);
        int beforeB = balance(plainB);
        AtomicReference<String> xid = new AtomicReference<>();
        AtomicReference<List<PreparedXa>> prepared = new AtomicReference<>();

        assertThatThrownBy(() -> EXECUTOR.execute("xa-rollback-two-databases", () -> {
                            xid.set(RootContext.getXID());
                            debit(localA, jdbcA);
                            debit(localB, jdbcB);
                            // 回滚场景同样必须先有两个分支的 prepared 事务，才能证明二阶段撤销了它们。
                            prepared.set(recoverPrepared());
                            throw new IllegalStateException("force-xa-global-rollback");
                        })
                        .block(PHASE_TWO_TIMEOUT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("force-xa-global-rollback");

        awaitBalances(beforeA, beforeB);
        assertTwoPreparedBranches(prepared.get(), xid.get());
        assertNoPreparedXa();
        assertThat(RootContext.getXID()).isNull();
    }

    private static void debit(TransactionTemplate local, JdbcTemplate jdbc) {
        EXECUTOR.branch(() -> local.execute(status -> jdbc.update(DEBIT_SQL, AMOUNT)))
                .contextWrite(context -> context.put(SeataTransactionExecutor.XID_CONTEXT_KEY, RootContext.getXID()))
                .block(PHASE_TWO_TIMEOUT);
    }

    private static DataSource rawDataSource(String urlName) {
        return new DriverManagerDataSource(
                required(urlName), required("APEX_SEATA_IT_USER"), required("APEX_SEATA_IT_PASSWORD"));
    }

    private static void resetBalance(JdbcTemplate jdbc) {
        jdbc.update(
                "INSERT INTO apex_seata_xa_account(id,balance) VALUES(1,?) "
                        + "ON DUPLICATE KEY UPDATE balance=VALUES(balance)",
                INITIAL_BALANCE);
    }

    private static int balance(JdbcTemplate jdbc) {
        return jdbc.queryForObject("SELECT balance FROM apex_seata_xa_account WHERE id=1", Integer.class);
    }

    private static void awaitBalances(int expectedA, int expectedB) {
        long deadline = System.nanoTime() + PHASE_TWO_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline && (balance(plainA) != expectedA || balance(plainB) != expectedB)) {
            LockSupport.parkNanos(Duration.ofMillis(100).toNanos());
        }
        assertThat(balance(plainA)).as("A 库余额").isEqualTo(expectedA);
        assertThat(balance(plainB)).as("B 库余额").isEqualTo(expectedB);
    }

    /**
     * 读取数据库层面的 prepared XA 事务。
     *
     * <p>MySQL 的 {@code XA RECOVER} 是<b>服务器级</b>视图：无论从哪个库的执行上下文发起，都会返回该
     * MySQL 实例上全部 prepared 分支，因此一次调用即可看到两个库的分支，不存在「按库过滤」的语义。
     *
     * <p>此外，非 {@code SUPER} 账号执行 {@code XA RECOVER} 要求显式持有 {@code XA_RECOVER_ADMIN}
     * 权限，否则返回 {@code XAE03 / 1401 XAER_RMERR}（本机 MySQL 8.0.46 实测复现）；该权限由
     * {@link SeataRealCluster} 在建号时授予。读取还必须经新开连接进行，不能复用分支或经
     * {@link DataSourceProxyXA} 代理的连接。
     *
     * @return 该 MySQL 实例当前处于 prepared 状态的 XA 事务
     */
    private static List<PreparedXa> recoverPrepared() {
        return new JdbcTemplate(rawA).query("XA RECOVER", (resultSet, rowNumber) -> {
            // MySQL XA RECOVER 固定列序：formatID、gtrid_length、bqual_length、data。
            int gtridLength = resultSet.getInt(2);
            int branchQualifierLength = resultSet.getInt(3);
            byte[] data = resultSet.getBytes(4);
            if (data == null || gtridLength + branchQualifierLength > data.length) {
                throw new IllegalStateException("XA RECOVER 返回的 XID 编码不完整");
            }
            return new PreparedXa(
                    resultSet.getInt(1),
                    Arrays.copyOfRange(data, 0, gtridLength),
                    new String(data, gtridLength, branchQualifierLength, StandardCharsets.UTF_8));
        });
    }

    /** 二阶段之前：两个分支都必须以本次全局事务 XID 处于 prepared 状态，且 branchId 互不相同。 */
    private static void assertTwoPreparedBranches(List<PreparedXa> prepared, String xid) {
        assertThat(prepared).as("全局二阶段之前必须有两个分支的 prepared XA 事务").hasSize(2);
        assertThat(prepared).allSatisfy(record -> {
            assertThat(record.formatId()).as("XA 数据源的 formatId").isEqualTo(SEATA_XA_FORMAT_ID);
            assertThat(record.gtrid())
                    .as("prepared 分支的 gtrid 必须是本次全局事务 XID")
                    .isEqualTo(xid.getBytes(StandardCharsets.UTF_8));
            assertThat(record.branchQualifier())
                    .as("prepared 分支的 branchQualifier 必须是 -branchId")
                    .startsWith("-");
            assertThat(record.branchId()).as("prepared 分支的 branchId").isPositive();
        });
        assertThat(prepared)
                .extracting(PreparedXa::branchId)
                .as("两个 XA 分支必须由 TC 分配不同 branchId")
                .doesNotHaveDuplicates();
    }

    /** 二阶段之后：不得残留 prepared 事务，证明 XA 二阶段由真实 RM 完成。 */
    private static void assertNoPreparedXa() {
        assertThat(recoverPrepared()).as("全局事务结束后不得残留 prepared XA 事务").isEmpty();
    }

    private static String required(String name) {
        return SeataRealCluster.required(name);
    }

    @AfterAll
    static void close() {
        TmNettyRemotingClient.getInstance().destroy();
        RmNettyRemotingClient.getInstance().destroy();
        RootContext.unbind();
    }

    /** 数据库中一条 prepared XA 事务的编码形态。 */
    private record PreparedXa(int formatId, byte[] gtrid, String branchQualifier) {
        long branchId() {
            return Long.parseLong(branchQualifier.substring(1));
        }
    }
}
