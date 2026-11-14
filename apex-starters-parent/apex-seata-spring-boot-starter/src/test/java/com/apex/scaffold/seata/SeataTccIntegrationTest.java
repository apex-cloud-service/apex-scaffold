package com.apex.scaffold.seata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.apex.scaffold.seata.tcc.AccountATccAction;
import com.apex.scaffold.seata.tcc.AccountATccActionImpl;
import com.apex.scaffold.seata.tcc.AccountBTccAction;
import com.apex.scaffold.seata.tcc.AccountBTccActionImpl;
import com.apex.scaffold.seata.tcc.JdbcTccReservation;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.LockSupport;
import javax.sql.DataSource;
import org.apache.seata.core.context.RootContext;
import org.apache.seata.core.model.BranchType;
import org.apache.seata.core.rpc.netty.RmNettyRemotingClient;
import org.apache.seata.core.rpc.netty.TmNettyRemotingClient;
import org.apache.seata.rm.DefaultResourceManager;
import org.apache.seata.rm.tcc.TCCResource;
import org.apache.seata.rm.tcc.TCCResourceManager;
import org.apache.seata.rm.tcc.api.BusinessActionContext;
import org.apache.seata.rm.tcc.api.BusinessActionContextUtil;
import org.apache.seata.rm.tcc.api.TwoPhaseBusinessAction;
import org.apache.seata.spring.annotation.GlobalTransactionScanner;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

/** 真实 TC、两个独立 MySQL 数据库及真实 Spring TCC 动作代理验证；禁止使用生产数据库。 */
@EnabledIfEnvironmentVariable(named = "APEX_SEATA_IT", matches = "true")
class SeataTccIntegrationTest {
    @RegisterExtension
    static final SeataRealCluster CLUSTER = SeataRealCluster.of("TCC");

    private static final int INITIAL_BALANCE = 100;
    private static final int AMOUNT = 10;
    private static final Duration PHASE_TWO_TIMEOUT = Duration.ofSeconds(20);

    private static ConfigurableApplicationContext context;
    private static AccountATccAction accountA;
    private static AccountBTccAction accountB;
    private static JdbcTccReservation reservationA;
    private static JdbcTccReservation reservationB;
    private static JdbcTemplate jdbcA;
    private static JdbcTemplate jdbcB;
    private static SeataTransactionExecutor executor;

    @BeforeAll
    static void setup() {
        assertThat(required("APEX_SEATA_IT_DB1"))
                .as("两个 TCC 分支必须使用不同测试数据库")
                .isNotEqualTo(required("APEX_SEATA_IT_DB2"));
        context = new SpringApplicationBuilder(TestConfiguration.class)
                .web(org.springframework.boot.WebApplicationType.NONE)
                .properties(
                        "spring.main.banner-mode=off",
                        "apex.seata.enabled=true",
                        "apex.seata.mode=TCC",
                        "apex.seata.application-id=apex-seata-it",
                        "apex.seata.transaction-service-group=apex_seata_it_group",
                        "apex.seata.timeout-millis=30000",
                        "apex.seata.propagation-secret=0123456789abcdef0123456789abcdef")
                .run();
        accountA = context.getBean(AccountATccAction.class);
        accountB = context.getBean(AccountBTccAction.class);
        reservationA = context.getBean("accountAReservation", JdbcTccReservation.class);
        reservationB = context.getBean("accountBReservation", JdbcTccReservation.class);
        jdbcA = context.getBean("accountAJdbc", JdbcTemplate.class);
        jdbcB = context.getBean("accountBJdbc", JdbcTemplate.class);
        executor = context.getBean(SeataTransactionExecutor.class);
    }

    @BeforeEach
    void reset() {
        reset(jdbcA);
        reset(jdbcB);
        reservationA.clearCalls();
        reservationB.clearCalls();
        RootContext.unbind();
        BusinessActionContextUtil.clear();
    }

    @Test
    void usesScannerProxyAndDistinctActionNames() throws Exception {
        assertThat(context).isNotNull();
        assertThat(context.getBean(GlobalTransactionScanner.class)).isNotNull();
        assertThat(AopUtils.isAopProxy(accountA)).isTrue();
        assertThat(AopUtils.isAopProxy(accountB)).isTrue();
        assertThat(actionName(AccountATccAction.class)).isEqualTo("accountATccAction");
        assertThat(actionName(AccountBTccAction.class)).isEqualTo("accountBTccAction");

        TCCResourceManager manager =
                (TCCResourceManager) DefaultResourceManager.get().getResourceManager(BranchType.TCC);
        Map<String, org.apache.seata.core.model.Resource> resources = manager.getManagedResources();
        assertThat(resources).containsKeys("accountATccAction", "accountBTccAction");
        assertThat(((TCCResource) resources.get("accountATccAction")).getTargetBean())
                .isNotSameAs(accountB);
        assertThat(((TCCResource) resources.get("accountBTccAction")).getTargetBean())
                .isNotSameAs(accountA);
    }

    @Test
    void commitsBothBranchesAndTcInvokesConfirm() {
        executor.execute("tcc-commit-two-databases", () -> {
                    assertThat(accountA.prepare(null, AMOUNT)).isTrue();
                    assertThat(accountB.prepare(null, AMOUNT)).isTrue();
                    return true;
                })
                .block(PHASE_TWO_TIMEOUT);

        awaitStates("CONFIRMED", "CONFIRMED");
        assertBalances(INITIAL_BALANCE - AMOUNT, INITIAL_BALANCE - AMOUNT);
        assertConfirmedByTc(reservationA, jdbcA);
        assertConfirmedByTc(reservationB, jdbcB);
        assertContextCleared();
    }

    @Test
    void callbackFailureRollsBackBothBranchesAndTcInvokesCancel() {
        assertThatThrownBy(() -> executor.execute("tcc-rollback-two-databases", () -> {
                            assertThat(accountA.prepare(null, AMOUNT)).isTrue();
                            assertThat(accountB.prepare(null, AMOUNT)).isTrue();
                            throw new IllegalStateException("force-global-rollback");
                        })
                        .block(PHASE_TWO_TIMEOUT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("force-global-rollback");

        awaitStates("CANCELLED", "CANCELLED");
        assertBalances(INITIAL_BALANCE, INITIAL_BALANCE);
        assertCancelledByTc(reservationA, jdbcA);
        assertCancelledByTc(reservationB, jdbcB);
        assertContextCleared();
    }

    @Test
    void failedTryLeavesNoReservationAndTcCancelsEarlierBranch() {
        assertThatThrownBy(() -> executor.execute("tcc-failed-second-try", () -> {
                            assertThat(accountA.prepare(null, AMOUNT)).isTrue();
                            assertThat(accountB.prepare(null, INITIAL_BALANCE + 1))
                                    .isFalse();
                            throw new IllegalStateException("second-try-rejected");
                        })
                        .block(PHASE_TWO_TIMEOUT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("second-try-rejected");

        awaitState(jdbcA, "CANCELLED");
        awaitCancelledCall(reservationB);
        // B 的 Try 返回 false 但已注册分支：TC 仍下达回滚，Cancel 必须幂等，不留预占行也不改余额。
        assertThat(phaseCount(jdbcB)).isZero();
        JdbcTccReservation.PhaseTwoCall cancelledB = reservationB.calls().get(0);
        assertThat(cancelledB.fromRm()).as("二阶段必须来自真实 RM").isTrue();
        assertThat(cancelledB.state()).isEqualTo("CANCELLED");
        assertCancelledByTc(reservationA, jdbcA);
        assertBalances(INITIAL_BALANCE, INITIAL_BALANCE);
        assertContextCleared();
    }

    /** 二阶段回调必须由真实 RM 接收 TC 指令后触发，且对应本分支 Try 写入的同一 XID／分支。 */
    private static void assertConfirmedByTc(JdbcTccReservation reservation, JdbcTemplate jdbc) {
        assertPhaseTwoMatches(reservation, jdbc, "CONFIRMED");
    }

    /** 二阶段回调经独立 RM 线程异步到达，先等待再断言次数与来源。 */
    private static void awaitCancelledCall(JdbcTccReservation reservation) {
        long deadline = System.nanoTime() + PHASE_TWO_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline && reservation.calls().isEmpty()) {
            LockSupport.parkNanos(Duration.ofMillis(100).toNanos());
        }
        assertThat(reservation.calls()).as("二阶段回调次数").hasSize(1);
    }

    private static void assertCancelledByTc(JdbcTccReservation reservation, JdbcTemplate jdbc) {
        assertPhaseTwoMatches(reservation, jdbc, "CANCELLED");
    }

    private static void assertPhaseTwoMatches(JdbcTccReservation reservation, JdbcTemplate jdbc, String state) {
        List<JdbcTccReservation.PhaseTwoCall> calls = reservation.calls();
        assertThat(calls).as("二阶段回调次数").hasSize(1);
        JdbcTccReservation.PhaseTwoCall call = calls.get(0);
        assertThat(call.fromRm()).as("二阶段必须来自真实 RM").isTrue();
        assertThat(call.state()).isEqualTo(state);
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT state FROM apex_seata_tcc_phase WHERE xid=? AND branch_id=?", call.xid(), call.branchId());
        assertThat(row.get("state")).as("二阶段回调必须对应 Try 登记的同一分支").isEqualTo(state);
    }

    private static String actionName(Class<?> actionType) throws NoSuchMethodException {
        Method method = actionType.getMethod("prepare", BusinessActionContext.class, int.class);
        return method.getAnnotation(TwoPhaseBusinessAction.class).name();
    }

    private static void reset(JdbcTemplate jdbc) {
        jdbc.execute("CREATE TABLE IF NOT EXISTS apex_seata_tcc_account("
                + "id BIGINT PRIMARY KEY, balance INT NOT NULL) ENGINE=InnoDB");
        jdbc.execute("CREATE TABLE IF NOT EXISTS apex_seata_tcc_phase("
                + "xid VARCHAR(128) NOT NULL, branch_id BIGINT NOT NULL, amount INT NOT NULL,"
                + "state VARCHAR(16) NOT NULL, PRIMARY KEY(xid, branch_id)) ENGINE=InnoDB");
        jdbc.update("DELETE FROM apex_seata_tcc_phase");
        jdbc.update(
                "INSERT INTO apex_seata_tcc_account(id,balance) VALUES(1,?) "
                        + "ON DUPLICATE KEY UPDATE balance=VALUES(balance)",
                INITIAL_BALANCE);
    }

    private static void awaitStates(String expectedA, String expectedB) {
        long deadline = System.nanoTime() + PHASE_TWO_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline
                && (!expectedA.equals(singleState(jdbcA)) || !expectedB.equals(singleState(jdbcB)))) {
            LockSupport.parkNanos(Duration.ofMillis(100).toNanos());
        }
        assertThat(singleState(jdbcA)).isEqualTo(expectedA);
        assertThat(singleState(jdbcB)).isEqualTo(expectedB);
    }

    private static void awaitState(JdbcTemplate jdbc, String expected) {
        long deadline = System.nanoTime() + PHASE_TWO_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline && !expected.equals(singleState(jdbc))) {
            LockSupport.parkNanos(Duration.ofMillis(100).toNanos());
        }
        assertThat(singleState(jdbc)).isEqualTo(expected);
    }

    private static String singleState(JdbcTemplate jdbc) {
        return jdbc.query(
                "SELECT state FROM apex_seata_tcc_phase", result -> result.next() ? result.getString(1) : null);
    }

    private static int phaseCount(JdbcTemplate jdbc) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM apex_seata_tcc_phase", Integer.class);
    }

    private static void assertBalances(int expectedA, int expectedB) {
        assertThat(balance(jdbcA)).isEqualTo(expectedA);
        assertThat(balance(jdbcB)).isEqualTo(expectedB);
    }

    private static int balance(JdbcTemplate jdbc) {
        return jdbc.queryForObject("SELECT balance FROM apex_seata_tcc_account WHERE id=1", Integer.class);
    }

    private static void assertContextCleared() {
        assertThat(RootContext.getXID()).isNull();
        assertThat(BusinessActionContextUtil.getContext()).isNull();
    }

    private static String required(String name) {
        return SeataRealCluster.required(name);
    }

    @AfterAll
    static void close() {
        if (context != null) {
            context.close();
        }
        TmNettyRemotingClient.getInstance().destroy();
        RmNettyRemotingClient.getInstance().destroy();
        RootContext.unbind();
        BusinessActionContextUtil.clear();
    }

    /**
     * 动作 Bean 以 {@code @Import} 类定义注册，不能用 {@code @Bean} 工厂方法：Seata 扫描器遍历
     * {@code BeanDefinition#getBeanClassName()}，工厂方法定义的类名为 null 会被跳过，动作拿不到代理。
     */
    @Configuration(proxyBeanMethods = false)
    @Import({SeataAutoConfiguration.class, AccountATccActionImpl.class, AccountBTccActionImpl.class})
    static class TestConfiguration {
        @Bean("accountADataSource")
        DataSource accountADataSource() {
            return dataSource("APEX_SEATA_IT_DB1");
        }

        @Bean("accountBDataSource")
        DataSource accountBDataSource() {
            return dataSource("APEX_SEATA_IT_DB2");
        }

        @Bean("accountAJdbc")
        JdbcTemplate accountAJdbc(@Qualifier("accountADataSource") DataSource source) {
            return new JdbcTemplate(source);
        }

        @Bean("accountBJdbc")
        JdbcTemplate accountBJdbc(@Qualifier("accountBDataSource") DataSource source) {
            return new JdbcTemplate(source);
        }

        @Bean("accountATransaction")
        TransactionTemplate accountATransaction(@Qualifier("accountADataSource") DataSource source) {
            return new TransactionTemplate(new DataSourceTransactionManager(source));
        }

        @Bean("accountBTransaction")
        TransactionTemplate accountBTransaction(@Qualifier("accountBDataSource") DataSource source) {
            return new TransactionTemplate(new DataSourceTransactionManager(source));
        }

        @Bean("accountAReservation")
        JdbcTccReservation accountAReservation(
                @Qualifier("accountAJdbc") JdbcTemplate jdbc,
                @Qualifier("accountATransaction") TransactionTemplate transaction) {
            return new JdbcTccReservation(jdbc, transaction);
        }

        @Bean("accountBReservation")
        JdbcTccReservation accountBReservation(
                @Qualifier("accountBJdbc") JdbcTemplate jdbc,
                @Qualifier("accountBTransaction") TransactionTemplate transaction) {
            return new JdbcTccReservation(jdbc, transaction);
        }

        private static DataSource dataSource(String urlName) {
            return new DriverManagerDataSource(
                    required(urlName), required("APEX_SEATA_IT_USER"), required("APEX_SEATA_IT_PASSWORD"));
        }
    }
}
