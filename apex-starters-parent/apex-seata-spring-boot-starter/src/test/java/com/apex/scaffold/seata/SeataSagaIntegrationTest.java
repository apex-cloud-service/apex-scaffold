package com.apex.scaffold.seata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.seata.saga.SagaDb2HttpFixture;
import com.apex.scaffold.seata.saga.SagaJdbcAction;
import com.apex.scaffold.seata.saga.SagaTcEvidence;
import java.net.URI;
import java.sql.SQLException;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import javax.sql.DataSource;
import org.apache.seata.config.ConfigurationFactory;
import org.apache.seata.core.context.RootContext;
import org.apache.seata.core.model.BranchType;
import org.apache.seata.core.model.GlobalStatus;
import org.apache.seata.rm.DefaultResourceManager;
import org.apache.seata.rm.datasource.SeataDataSourceProxy;
import org.apache.seata.saga.engine.StateMachineEngine;
import org.apache.seata.saga.engine.config.DbStateMachineConfig;
import org.apache.seata.saga.engine.impl.ProcessCtrlStateMachineEngine;
import org.apache.seata.saga.engine.store.db.DbAndReportTcStateLogStore;
import org.apache.seata.saga.rm.SagaResourceManager;
import org.apache.seata.saga.rm.StateMachineEngineHolder;
import org.apache.seata.tm.api.GlobalTransactionContext;
import org.apache.seata.tm.api.GlobalTransactionRole;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.publisher.Mono;

/** 真实 TC、双 MySQL 的单入口 SAGA 回归；只有引擎 context 初始化 Seata 客户端。 */
@EnabledIfEnvironmentVariable(named = "APEX_SEATA_IT", matches = "true")
class SeataSagaIntegrationTest {
    @RegisterExtension
    static final SeataRealCluster CLUSTER = SeataRealCluster.of("SAGA");

    private static final String MACHINE_NAME = "ApexTcTwoDatabaseSaga";
    private static final String APPLICATION_ID = "apex-seata-it";
    private static final String TX_GROUP = "apex_seata_it_group";
    private static final String MACHINE_RESOURCE = "saga/apex-two-database-tc-saga.json";
    private static final String BUSINESS_SCHEMA = "saga/apex-saga-tc-business.sql";
    private static final String SENSITIVE_INPUT = "synthetic-saga-input-not-for-snapshots";
    private static final int INITIAL_BALANCE = 100;
    private static final int AMOUNT = 10;
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private static AnnotationConfigApplicationContext engineContext;
    private static AnnotationConfigApplicationContext db2Context;
    private static SagaDb2HttpFixture db2Http;
    private static SagaExecutor executor;
    private static SeataTransactionExecutor transactions;
    private static DbStateMachineConfig config;
    private static SagaTcEvidence tcEvidence;
    private static JdbcTemplate jdbc1;
    private static JdbcTemplate jdbc2;
    private static String tenantId;

    @BeforeAll
    static void setup() {
        assertThat(databaseIdentity("APEX_SEATA_IT_DB1"))
                .as("SAGA 两个本地事务必须使用不同的专用 MySQL 测试库")
                .isNotEqualTo(databaseIdentity("APEX_SEATA_IT_DB2"));
        assertThat(System.getProperty("client.rm.sagaBranchRegisterEnable"))
                .as("统一真实测试入口必须启用 SAGA 分支注册")
                .isEqualTo("true");
        jdbc(() -> {
            db2Context = new AnnotationConfigApplicationContext(Db2Configuration.class);
            jdbc2 = db2Context.getBean(JdbcTemplate.class);
            db2Http = new SagaDb2HttpFixture(db2Context.getBean(SagaJdbcAction.class));

            engineContext = new AnnotationConfigApplicationContext();
            engineContext
                    .getEnvironment()
                    .getPropertySources()
                    .addFirst(new MapPropertySource(
                            "saga-it",
                            Map.of(
                                    "apex.seata.enabled",
                                    "true",
                                    "apex.seata.mode",
                                    "SAGA",
                                    "apex.seata.application-id",
                                    APPLICATION_ID,
                                    "apex.seata.transaction-service-group",
                                    TX_GROUP,
                                    "apex.seata.timeout-millis",
                                    "30000",
                                    "apex.seata.propagation-secret",
                                    UUID.randomUUID().toString(),
                                    "apex.seata.saga.state-machine-resources[0]",
                                    "classpath:" + MACHINE_RESOURCE)));
            engineContext.register(EngineConfiguration.class);
            engineContext.refresh();
            jdbc1 = engineContext.getBean(JdbcTemplate.class);
            executor = engineContext.getBean(SagaExecutor.class);
            transactions = engineContext.getBean(SeataTransactionExecutor.class);
            config = engineContext.getBean(DbStateMachineConfig.class);
            tenantId = config.getDefaultTenantId();
            DbAndReportTcStateLogStore store = (DbAndReportTcStateLogStore) config.getStateLogStore();
            assertThat(store.getSagaTransactionalTemplate())
                    .as("自动配置必须为完成上报安装角色归属修正")
                    .isInstanceOf(SagaReportOwnership.class);
            // 只观察归属修正之后、真正到达 Seata 的调用；所有事务/分支操作原样委托，不替换 RM。
            tcEvidence = new SagaTcEvidence(((SagaReportOwnership) store.getSagaTransactionalTemplate()).delegate());
            store.setSagaTransactionalTemplate(new SagaReportOwnership(tcEvidence));
            return true;
        });
    }

    @Test
    void realTcRegistersBothDatabasesAndRollsBackASuccessfulSagaInAnOuterTransaction() {
        assertRealAssembly();
        assertSuccessfulSagaAndBusinessKeyIdempotence();
        assertLocalFailureCompensatesWithoutPretendingToBeTcRollback();
        assertOuterFailureUsesTcPhaseTwoRollback();
        assertContextCleared();
    }

    private static void assertRealAssembly() {
        assertThat(engineContext.getBeansOfType(DataSource.class)).hasSize(1);
        assertThat(db2Context.getBeansOfType(DataSource.class)).hasSize(1);
        assertThat(db2Context.getBeansOfType(SeataProperties.class)).isEmpty();
        assertThat(db2Context.getBeansOfType(StateMachineEngine.class)).isEmpty();
        assertThat(engineContext.getBean(StateMachineEngine.class))
                .as("SAGA RM 的实际取用入口：TC 二阶段补偿必须走本引擎")
                .isExactlyInstanceOf(ProcessCtrlStateMachineEngine.class)
                .isSameAs(StateMachineEngineHolder.getStateMachineEngine());

        assertThat(config.getDataSource())
                .isSameAs(engineContext.getBean(DataSource.class))
                .isNotInstanceOf(SeataDataSourceProxy.class);
        assertThat(ConfigurationFactory.getInstance().getBoolean("client.rm.sagaBranchRegisterEnable", false))
                .as("实际客户端配置，而非仅检查 JVM 参数")
                .isTrue();
        assertThat(config.isSagaBranchRegisterEnable()).isTrue();
        assertThat(DefaultResourceManager.get().getResourceManager(BranchType.SAGA))
                .as("SAGA 分支必须由显式注册的 org.apache SagaResourceManager 承接，兼容副本无法对接引擎持有者")
                .isExactlyInstanceOf(SagaResourceManager.class);
        assertThat(tenantId).isNotBlank();
    }

    private static void assertSuccessfulSagaAndBusinessKeyIdempotence() {
        String key = prepareAccounts("success");
        Map<String, Object> snapshot = start(key, false);
        String xid = (String) snapshot.get("id");
        assertSnapshot(snapshot, key, "SU", null);
        assertPersistedInstance(snapshot, key);
        assertBalances(key, INITIAL_BALANCE - AMOUNT, INITIAL_BALANCE - AMOUNT);
        assertRegisteredStates(xid, "SU", "SU");
        assertThat(tcEvidence.reports(xid)).singleElement().satisfies(report -> {
            assertThat(report.role()).isEqualTo(GlobalTransactionRole.Launcher);
            assertThat(report.status()).isEqualTo(GlobalStatus.Committed);
        });
        assertForwardAudit(jdbc1, key, xid);
        assertForwardAudit(jdbc2, key, xid);

        AtomicReference<Map<String, Object>> duplicate = new AtomicReference<>();
        Throwable rejected = catchThrowable(() -> duplicate.set(start(key, false)));
        if (rejected == null) {
            assertThat(duplicate.get()).containsEntry("id", xid);
        } else {
            assertThat(isDuplicateKeyFailure(rejected))
                    .as("重复业务键只能被唯一约束拒绝，不能接受无关执行异常")
                    .isTrue();
        }
        assertThat(jdbc(() -> jdbc1.queryForObject(
                        "SELECT COUNT(*) FROM seata_state_machine_inst WHERE business_key=? AND tenant_id=?",
                        Integer.class,
                        key,
                        tenantId)))
                .isEqualTo(1);
        assertBalances(key, INITIAL_BALANCE - AMOUNT, INITIAL_BALANCE - AMOUNT);
        assertForwardAudit(jdbc1, key, xid);
        assertForwardAudit(jdbc2, key, xid);
        assertSnapshot(executor.getInstance(xid).block(TIMEOUT), key, "SU", null);
    }

    private static void assertLocalFailureCompensatesWithoutPretendingToBeTcRollback() {
        String key = prepareAccounts("local-fail");
        Map<String, Object> snapshot = start(key, true);
        String xid = (String) snapshot.get("id");
        // IsForUpdate 状态实例失败时引擎按保守语义判 UN（结果不确定），补偿本身可完整成功。
        assertSnapshot(snapshot, key, "UN", "SU");
        assertPersistedInstance(snapshot, key);
        assertBalances(key, INITIAL_BALANCE, INITIAL_BALANCE);
        assertRegisteredStates(xid, "SU", "FA");
        assertThat(audits(jdbc1, key)).extracting(SagaJdbcAction.Audit::phase).containsExactly("FORWARD", "COMPENSATE");
        // DB2 在扣款和审计写入后主动抛错；两次写入必须一起回滚。
        assertThat(audits(jdbc2, key)).isEmpty();
        assertThat(audits(jdbc1, key))
                .filteredOn(row -> "COMPENSATE".equals(row.phase()))
                .allSatisfy(row -> assertThat(row.fromTcRollback()).isFalse());
        assertCompensationLink(xid, "DebitDb1");
    }

    private static void assertOuterFailureUsesTcPhaseTwoRollback() {
        String key = prepareAccounts("outer-fail");
        AtomicReference<String> outerXid = new AtomicReference<>();
        AtomicReference<Map<String, Object>> completed = new AtomicReference<>();
        AtomicReference<GlobalStatus> tcStatusBeforeFailure = new AtomicReference<>();
        AtomicReference<List<Integer>> balancesBeforeFailure = new AtomicReference<>();
        Throwable failure = catchThrowable(() -> transactions
                .execute("saga-outer-failure", () -> {
                    String xid = RootContext.getXID();
                    outerXid.set(xid);
                    completed.set(executor.start(MACHINE_NAME, tenantId, key, parameters(key, false))
                            .contextWrite(context -> context.put(SeataTransactionExecutor.XID_CONTEXT_KEY, xid))
                            .block(TIMEOUT));
                    balancesBeforeFailure.set(jdbc(() -> List.of(balance(jdbc1, key), balance(jdbc2, key))));
                    tcStatusBeforeFailure.set(
                            GlobalTransactionContext.getCurrent().getStatus());
                    throw new ForcedOuterFailure();
                })
                .block(TIMEOUT));
        assertThat(failure).isInstanceOf(ForcedOuterFailure.class);
        // Reactor 在 block 传播错误时会附加 cause 为空的诊断标记；它不是回滚失败。
        // 回滚是否真实执行以 TC 侧状态与补偿审计为准（下方 assertTcCompensation* 验证）。
        assertThat(failure.getSuppressed())
                .as("外层真实 TC 回滚调用不得失败；block 的诊断标记不算回滚失败")
                .allSatisfy(s -> assertThat(s)
                        .hasMessage("#block terminated with an error")
                        .hasNoCause());
        assertThat(outerXid.get()).isNotBlank();
        assertSnapshot(completed.get(), key, "SU", null);
        assertThat(balancesBeforeFailure.get()).containsExactly(INITIAL_BALANCE - AMOUNT, INITIAL_BALANCE - AMOUNT);
        assertThat(completed.get().get("id"))
                .as(
                        "SagaExecutor 必须把 Reactor Context 的外层 XID 绑定到引擎 JDBC 线程；TC 证据: %s",
                        tcEvidence.registrations((String) completed.get().get("id")))
                .isEqualTo(outerXid.get());
        assertThat(tcEvidence.reports(outerXid.get()))
                .as("参与者完成报告必须在真正到达 TC 前被过滤")
                .isEmpty();
        assertThat(tcStatusBeforeFailure.get())
                .as("成功的参与者状态机不得提前结束外层 TC 事务；实际完成报告: %s", tcEvidence.reports(outerXid.get()))
                .isEqualTo(GlobalStatus.Begin);
        assertRegisteredStates(outerXid.get(), "SU", "SU");

        Map<String, Object> compensated = awaitTcCompensation(outerXid.get(), key);
        assertSnapshot(compensated, key, "SU", "SU");
        assertPersistedInstance(compensated, key);
        assertBalances(key, INITIAL_BALANCE, INITIAL_BALANCE);
        assertTcCompensationAudit(jdbc1, key, outerXid.get());
        assertTcCompensationAudit(jdbc2, key, outerXid.get());
        assertCompensationLink(outerXid.get(), "DebitDb1");
        assertCompensationLink(outerXid.get(), "DebitDb2");
    }

    private static Map<String, Object> awaitTcCompensation(String xid, String key) {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        Map<String, Object> snapshot = Map.of();
        while (System.nanoTime() < deadline) {
            snapshot = executor.getInstance(xid).block(TIMEOUT);
            if (snapshot != null
                    && "SU".equals(snapshot.get("compensationStatus"))
                    && audits(jdbc1, key).stream().anyMatch(SagaJdbcAction.Audit::fromTcRollback)
                    && audits(jdbc2, key).stream().anyMatch(SagaJdbcAction.Audit::fromTcRollback)) {
                return snapshot;
            }
            LockSupport.parkNanos(Duration.ofMillis(100).toNanos());
        }
        throw new AssertionError("真实 TC 未完成 SAGA 二阶段补偿；实例=" + snapshot + ", 分支注册=" + tcEvidence.registrations(xid)
                + ", 完成报告=" + tcEvidence.reports(xid));
    }

    private static void assertRegisteredStates(String xid, String db1Status, String db2Status) {
        List<Map<String, Object>> states = jdbc(() -> jdbc1.queryForList(
                "SELECT id,name,status FROM seata_state_inst WHERE machine_inst_id=? AND service_method='debit'", xid));
        assertThat(states).hasSize(2);
        assertThat(tcEvidence.registrations(xid)).hasSize(2).allSatisfy(registration -> {
            assertThat(registration.branchId()).isPositive();
            assertThat(registration.xid()).isEqualTo(xid);
            assertThat(registration.thread()).startsWith("apex.jdbc");
            assertThat(states).anySatisfy(state -> {
                assertThat(state.get("id")).isEqualTo(Long.toString(registration.branchId()));
                assertThat(registration.resourceId()).isEqualTo(MACHINE_NAME + '#' + state.get("name"));
            });
        });
        assertThat(states)
                .anySatisfy(
                        row -> assertThat(row).containsEntry("name", "DebitDb1").containsEntry("status", db1Status));
        assertThat(states)
                .anySatisfy(
                        row -> assertThat(row).containsEntry("name", "DebitDb2").containsEntry("status", db2Status));
    }

    private static void assertCompensationLink(String xid, String forwardState) {
        List<Map<String, Object>> rows = jdbc(() -> jdbc1.queryForList(
                "SELECT c.status,c.state_id_compensated_for,f.id AS forward_id FROM seata_state_inst c "
                        + "JOIN seata_state_inst f ON f.machine_inst_id=c.machine_inst_id AND f.id=c.state_id_compensated_for "
                        + "WHERE c.machine_inst_id=? AND c.service_method='restore' AND f.name=?",
                xid,
                forwardState));
        assertThat(rows).singleElement().satisfies(row -> assertThat(row)
                .containsEntry("status", "SU")
                .containsEntry("state_id_compensated_for", row.get("forward_id")));
    }

    private static void assertPersistedInstance(Map<String, Object> snapshot, String key) {
        Map<String, Object> instance = jdbc(() -> jdbc1.queryForMap(
                "SELECT machine_id,business_key,tenant_id,status,compensation_status,start_params "
                        + "FROM seata_state_machine_inst WHERE id=?",
                snapshot.get("id")));
        assertThat(instance)
                .containsEntry("machine_id", snapshot.get("machineId"))
                .containsEntry("business_key", key)
                .containsEntry("tenant_id", tenantId)
                .containsEntry("status", snapshot.get("status"))
                .containsEntry("compensation_status", snapshot.get("compensationStatus"));
        assertThat(String.valueOf(instance.get("start_params")).contains(SENSITIVE_INPUT))
                .isTrue();
        Map<String, Object> definition = jdbc(() -> jdbc1.queryForMap(
                "SELECT name,tenant_id,ver,status,content FROM seata_state_machine_def WHERE id=?",
                snapshot.get("machineId")));
        assertThat(definition)
                .containsEntry("name", MACHINE_NAME)
                .containsEntry("tenant_id", tenantId)
                .containsEntry("ver", "1.0.0")
                .containsEntry("status", "AC");
        assertThat(String.valueOf(definition.get("content")))
                .contains("DebitDb1", "DebitDb2", "RestoreDb1", "RestoreDb2");
    }

    private static void assertSnapshot(Map<String, Object> snapshot, String key, String status, String compensation) {
        assertThat(snapshot)
                .isNotNull()
                .containsOnlyKeys(
                        "id",
                        "machineId",
                        "businessKey",
                        "tenantId",
                        "status",
                        "compensationStatus",
                        "startedAt",
                        "endAt");
        assertThat(snapshot)
                .containsEntry("businessKey", key)
                .containsEntry("tenantId", tenantId)
                .containsEntry("status", status)
                .containsEntry("compensationStatus", compensation);
        assertThat(snapshot.get("id")).isInstanceOf(String.class);
        assertThat(snapshot.get("endAt")).isNotNull();
        assertThat(snapshot.values().stream()
                        .anyMatch(value -> String.valueOf(value).contains(SENSITIVE_INPUT)))
                .isFalse();
    }

    private static void assertForwardAudit(JdbcTemplate template, String key, String xid) {
        assertThat(audits(template, key)).singleElement().satisfies(row -> {
            assertThat(row.phase()).isEqualTo("FORWARD");
            assertThat(row.xid()).isEqualTo(xid);
            assertThat(row.branchType()).isEqualTo("SAGA");
            assertThat(row.amount()).isEqualTo(AMOUNT);
            assertThat(row.jdbcThread()).startsWith("apex.jdbc");
        });
    }

    private static void assertTcCompensationAudit(JdbcTemplate template, String key, String xid) {
        assertThat(audits(template, key))
                .extracting(SagaJdbcAction.Audit::phase)
                .containsExactly("FORWARD", "COMPENSATE");
        assertThat(audits(template, key))
                .filteredOn(row -> "COMPENSATE".equals(row.phase()))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.xid()).isEqualTo(xid);
                    assertThat(row.branchType()).isEqualTo("SAGA");
                    assertThat(row.amount()).isEqualTo(AMOUNT);
                    assertThat(row.jdbcThread()).startsWith("apex.jdbc");
                    assertThat(row.callStack())
                            .contains(
                                    "org.apache.seata.saga.rm.SagaResourceManager.branchRollback(",
                                    "org.apache.seata.rm.AbstractRMHandler.doBranchRollback(");
                    assertThat(row.sourceThread()).isNotEqualTo(row.jdbcThread());
                });
    }

    private static List<SagaJdbcAction.Audit> audits(JdbcTemplate template, String key) {
        return jdbc(() -> SagaJdbcAction.audits(template, key));
    }

    private static String prepareAccounts(String scenario) {
        String key = scenario + '-' + UUID.randomUUID().toString().replace("-", "");
        jdbc(() -> {
            jdbc1.update("INSERT INTO apex_saga_tc_account(business_key,balance) VALUES(?,?)", key, INITIAL_BALANCE);
            jdbc2.update("INSERT INTO apex_saga_tc_account(business_key,balance) VALUES(?,?)", key, INITIAL_BALANCE);
            return true;
        });
        return key;
    }

    private static Map<String, Object> start(String key, boolean failSecond) {
        return executor.start(MACHINE_NAME, tenantId, key, parameters(key, failSecond))
                .doOnNext(ignored -> assertContextCleared())
                .block(TIMEOUT);
    }

    private static Map<String, Object> parameters(String key, boolean failSecond) {
        // Seata createMachineInstance 会向 startParams 写入实例 XID，必须提供可变 Map。
        Map<String, Object> params = new HashMap<>();
        params.put("businessKey", key);
        params.put("amount", AMOUNT);
        params.put("failSecond", failSecond);
        params.put("sensitiveInput", SENSITIVE_INPUT);
        return params;
    }

    private static void assertBalances(String key, int expected1, int expected2) {
        assertThat(jdbc(() -> List.of(balance(jdbc1, key), balance(jdbc2, key))))
                .containsExactly(expected1, expected2);
    }

    private static int balance(JdbcTemplate template, String key) {
        return template.queryForObject(
                "SELECT balance FROM apex_saga_tc_account WHERE business_key=?", Integer.class, key);
    }

    private static boolean isDuplicateKeyFailure(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && sql.getErrorCode() == 1062) {
                return true;
            }
        }
        return false;
    }

    private static void assertContextCleared() {
        assertThat(RootContext.getXID()).isNull();
        assertThat(RootContext.getBranchType()).isNull();
    }

    private static <T> T jdbc(Callable<T> work) {
        return Mono.fromCallable(work).subscribeOn(BlockingIoSchedulers.jdbc()).block(TIMEOUT);
    }

    private static DataSource dataSource(String name, boolean stateStore) {
        DriverManagerDataSource source = new DriverManagerDataSource(
                required(name), required("APEX_SEATA_IT_USER"), required("APEX_SEATA_IT_PASSWORD"));
        String catalog = new JdbcTemplate(source).queryForObject("SELECT DATABASE()", String.class);
        assertThat(catalog != null && catalog.startsWith("apex_seata_it_"))
                .as("仅允许统一入口创建的 apex_seata_it_ 专用库，不得写生产数据库")
                .isTrue();
        ResourceDatabasePopulator schema = new ResourceDatabasePopulator(new ClassPathResource(BUSINESS_SCHEMA));
        if (stateStore) {
            schema.addScript(new ClassPathResource("sql/saga/mysql/seata_state_machine.sql"));
        }
        schema.execute(source);
        return source;
    }

    private static String databaseIdentity(String name) {
        String url = required(name);
        if (!url.startsWith("jdbc:mysql://")) {
            throw new IllegalStateException("SAGA 集成测试只接受 MySQL JDBC URL");
        }
        URI uri = URI.create(url.substring("jdbc:".length()));
        if (uri.getUserInfo() != null
                || uri.getHost() == null
                || uri.getPath() == null
                || !uri.getPath().matches("/apex_seata_it_[A-Za-z0-9_]+")) {
            throw new IllegalStateException("必须使用无内嵌凭据的专用 MySQL 测试库 URL");
        }
        return uri.getHost() + ':' + (uri.getPort() < 0 ? 3306 : uri.getPort()) + uri.getPath();
    }

    private static String required(String name) {
        return SeataRealCluster.required(name);
    }

    @AfterAll
    static void close() {
        jdbc(() -> {
            try {
                if (engineContext != null) {
                    engineContext.close();
                }
            } finally {
                try {
                    if (db2Http != null) {
                        db2Http.close();
                    }
                } finally {
                    if (db2Context != null) {
                        db2Context.close();
                    }
                    RootContext.unbind();
                    RootContext.unbindBranchType();
                }
            }
            return true;
        });
    }

    private static final class ForcedOuterFailure extends IllegalStateException {
        private ForcedOuterFailure() {
            super("force-outer-failure-after-successful-saga");
        }
    }

    @Configuration(proxyBeanMethods = false)
    @Import(SeataAutoConfiguration.class)
    static class EngineConfiguration {
        @Bean
        DataSource sagaDataSource() {
            return dataSource("APEX_SEATA_IT_DB1", true);
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource source) {
            return new JdbcTemplate(source);
        }

        @Bean("sagaDb1Action")
        SagaJdbcAction sagaDb1Action(DataSource source, JdbcTemplate template) {
            return new SagaJdbcAction(template, new TransactionTemplate(new DataSourceTransactionManager(source)));
        }

        @Bean("sagaDb2Action")
        SagaDb2HttpFixture sagaDb2Action() {
            return db2Http;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class Db2Configuration {
        @Bean
        DataSource sagaDataSource() {
            return dataSource("APEX_SEATA_IT_DB2", false);
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource source) {
            return new JdbcTemplate(source);
        }

        @Bean
        SagaJdbcAction sagaDb2Action(DataSource source, JdbcTemplate template) {
            return new SagaJdbcAction(template, new TransactionTemplate(new DataSourceTransactionManager(source)));
        }
    }
}
