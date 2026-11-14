package com.apex.scaffold.workflowservice.interfaces.handler;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.workflow.application.scheduler.WorkflowTimeoutHandler;
import com.apex.scaffold.workflow.application.service.WorkflowService;
import com.apex.scaffold.workflow.domain.model.Task;
import com.apex.scaffold.workflow.domain.repository.ScanLease;
import com.apex.scaffold.workflow.domain.repository.TimeoutTaskScan;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** 真实 H2 上的跨实例扫描租约：同一时刻只允许一个实例扫描，释放或到期后可接管。 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "security.config.encryption.key=01234567890123456789012345678901",
            "spring.datasource.url=jdbc:h2:mem:workflow-scan-lease-it;MODE=MySQL;DB_CLOSE_DELAY=-1",
            "spring.cloud.nacos.config.enabled=false",
            "spring.cloud.nacos.discovery.enabled=false",
            "spring.cloud.nacos.discovery.register-enabled=false"
        })
@ActiveProfiles("basic")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class WorkflowScanLeaseIntegrationTest {
    private static final String LEASE = "workflow-timeout-scan";
    private static final String OTHER = "other-instance";

    private final JdbcTemplate jdbc;
    private final WorkflowService service;
    private final TimeoutTaskScan scan;
    private final ScanLease lease;

    WorkflowScanLeaseIntegrationTest(
            JdbcTemplate jdbc, WorkflowService service, TimeoutTaskScan scan, ScanLease lease) {
        this.jdbc = jdbc;
        this.service = service;
        this.scan = scan;
        this.lease = lease;
    }

    @BeforeEach
    void seed() {
        jdbc.update("DELETE FROM wf_task");
        jdbc.update("INSERT INTO wf_task (id,tenant_id,process_instance_id,node_code,task_name,assignee_id,"
                + "timeout_at,timeout_strategy) VALUES (6001,'lease-A',4001,'approve','审批',100,"
                + "TIMESTAMP '2020-01-01 00:00:00','AUTO_COMPLETE'),"
                + "(6002,'lease-B',4002,'approve','审批',200,TIMESTAMP '2020-01-01 00:00:00','AUTO_COMPLETE')");
        jdbc.update(
                "UPDATE wf_scheduler_lease SET owner='', lease_until=TIMESTAMP '1970-01-01 00:00:00' "
                        + "WHERE lock_name=?",
                LEASE);
    }

    @Test
    void onlyOneConcurrentInstanceAcquiresTheLease() throws Exception {
        Duration ttl = Duration.ofMinutes(5);
        var first = lease.acquire(LEASE, "instance-1", ttl).toFuture();
        var second = lease.acquire(LEASE, "instance-2", ttl).toFuture();

        assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                .as("并发抢占只能有一个实例成功")
                .containsExactlyInAnyOrder(true, false);
        assertThat(owner()).isIn("instance-1", "instance-2");
    }

    @Test
    void instanceHoldingTheLeaseCanRenewAndOthersCannot() {
        assertThat(lease.acquire(LEASE, OTHER, Duration.ofMinutes(1)).block(Duration.ofSeconds(10)))
                .isTrue();
        assertThat(lease.acquire(LEASE, OTHER, Duration.ofMinutes(1)).block(Duration.ofSeconds(10)))
                .as("同一持有者可续租")
                .isTrue();
        assertThat(lease.acquire(LEASE, "third-instance", Duration.ofMinutes(1)).block(Duration.ofSeconds(10)))
                .as("租约未到期时其他实例无法抢占")
                .isFalse();
    }

    @Test
    void scanIsSkippedWhileAnotherInstanceHoldsTheLease() {
        lease.acquire(LEASE, OTHER, Duration.ofMinutes(5)).block(Duration.ofSeconds(10));

        handler(scan).scanTimeoutTasks().block(Duration.ofSeconds(10));

        assertThat(jdbc.queryForList("SELECT status FROM wf_task ORDER BY id", String.class))
                .as("未持有租约的实例不得处理任务")
                .containsOnly("PENDING");
        assertThat(owner()).isEqualTo(OTHER);
    }

    @Test
    void releasedLeaseAllowsImmediateTakeover() {
        lease.acquire(LEASE, OTHER, Duration.ofMinutes(5)).block(Duration.ofSeconds(10));
        lease.release(LEASE, OTHER).block(Duration.ofSeconds(10));
        assertThat(owner()).isEmpty();

        handler(scan).scanTimeoutTasks().block(Duration.ofSeconds(10));

        assertThat(jdbc.queryForList("SELECT status FROM wf_task ORDER BY id", String.class))
                .containsOnly("COMPLETED");
        assertThat(owner()).as("扫描结束后租约应被释放").isEmpty();
    }

    @Test
    void expiredLeaseCanBeTakenOverEvenIfOwnerDidNotRelease() {
        lease.acquire(LEASE, "crashed-instance", Duration.ofSeconds(-60)).block(Duration.ofSeconds(10));

        handler(scan).scanTimeoutTasks().block(Duration.ofSeconds(10));

        assertThat(jdbc.queryForList("SELECT status FROM wf_task ORDER BY id", String.class))
                .as("持有者崩溃后租约到期即可被接管")
                .containsOnly("COMPLETED");
    }

    @Test
    void failingScanReleasesTheLeaseAndKeepsTheFailureObservable() {
        List<Task> snapshot = scan.findPendingTasks()
                .sort(java.util.Comparator.comparing(Task::getId))
                .collectList()
                .block(Duration.ofSeconds(10));
        WorkflowTimeoutHandler failing = new WorkflowTimeoutHandler(
                () -> Flux.error(new IllegalStateException("扫描失败")), service, lease, false, false, 300);

        var result = failing.scanTimeoutTasks().materialize().block(Duration.ofSeconds(10));

        assertThat(result).isNotNull();
        assertThat(result.isOnError()).isTrue();
        assertThat(result.getThrowable()).hasMessage("扫描失败");
        assertThat(owner()).as("扫描失败后必须释放租约").isEmpty();

        // 释放后可立即接管，且快照仍可正常处理，证明失败未污染任务数据。
        handler(() -> Flux.fromIterable(snapshot)).scanTimeoutTasks().block(Duration.ofSeconds(10));
        assertThat(jdbc.queryForList("SELECT status FROM wf_task ORDER BY id", String.class))
                .containsOnly("COMPLETED");
    }

    @Test
    void staleReleaseCannotClearANewerRound() {
        assertThat(lease.acquire(LEASE, "old-round", Duration.ofSeconds(-60)).block(Duration.ofSeconds(10)))
                .isTrue();
        assertThat(lease.acquire(LEASE, "new-round", Duration.ofMinutes(5)).block(Duration.ofSeconds(10)))
                .isTrue();

        lease.release(LEASE, "old-round").block(Duration.ofSeconds(10));

        assertThat(owner()).isEqualTo("new-round");
        lease.release(LEASE, "new-round").block(Duration.ofSeconds(10));
        assertThat(owner()).isEmpty();
    }

    @Test
    void cancelledScanReleasesDatabaseLease() throws Exception {
        CountDownLatch scanning = new CountDownLatch(1);
        CountDownLatch released = new CountDownLatch(1);
        ScanLease observedLease = new ScanLease() {
            @Override
            public Mono<Boolean> acquire(String name, String owner, Duration ttl) {
                return lease.acquire(name, owner, ttl);
            }

            @Override
            public Mono<Void> release(String name, String owner) {
                return lease.release(name, owner).doOnSuccess(ignored -> released.countDown());
            }
        };
        WorkflowTimeoutHandler waiting = new WorkflowTimeoutHandler(
                () -> Flux.<Task>never().doOnSubscribe(ignored -> scanning.countDown()),
                service,
                observedLease,
                false,
                false,
                300);
        Disposable subscription = waiting.scanTimeoutTasks().subscribe();
        try {
            assertThat(scanning.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(owner()).isNotEmpty();
        } finally {
            subscription.dispose();
        }
        assertThat(released.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(owner()).isEmpty();
    }

    private WorkflowTimeoutHandler handler(TimeoutTaskScan source) {
        return new WorkflowTimeoutHandler(source, service, lease, false, false, 300);
    }

    private String owner() {
        return jdbc.queryForObject("SELECT owner FROM wf_scheduler_lease WHERE lock_name=?", String.class, LEASE);
    }
}
