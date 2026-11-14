package com.apex.scaffold.infrastructure.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apex.scaffold.application.service.GeneratorTaskService;
import com.apex.scaffold.application.service.GeneratorWizardService;
import com.apex.scaffold.domain.model.entity.TenantEntity;
import com.apex.scaffold.domain.model.generator.GenTaskEntity;
import com.apex.scaffold.domain.repository.GenTaskRepository;
import com.apex.scaffold.domain.repository.TenantRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;

/**
 * 验证异步生成任务的认领式调度、租户异常隔离与保留期清理。
 *
 * <p>租户发现来自平台租户目录 {@code sys_tenant}（白名单表），认领成功后按主键重读
 * 数据库实体再执行，不使用候选查询的内存快照。
 */
class GeneratorTaskSchedulerTest {

    private static final int MAX_ATTEMPTS = 3;

    private static final int LEASE_MINUTES = 10;

    private static final int TENANT_BATCH = 20;

    private static final int RETENTION_HOURS = 24;

    private final GeneratorTaskService taskService = mock(GeneratorTaskService.class);

    private final GenTaskRepository genTaskRepository = mock(GenTaskRepository.class);

    private final TenantRepository tenantRepository = mock(TenantRepository.class);

    private GeneratorTaskScheduler scheduler() {
        return new GeneratorTaskScheduler(
                taskService,
                genTaskRepository,
                tenantRepository,
                MAX_ATTEMPTS,
                LEASE_MINUTES,
                TENANT_BATCH,
                RETENTION_HOURS);
    }

    @Test
    void pollSkipsWhenTenantDirectoryEmpty() {
        when(tenantRepository.findAll()).thenReturn(Mono.just(List.of()));

        scheduler().poll();

        verify(tenantRepository, timeout(2000)).findAll();
        verify(genTaskRepository, after(200).never()).findNextCandidate(any(), anyInt(), any());
    }

    @Test
    void pollClaimsRereadsAndProcessesCandidate() {
        GenTaskEntity candidate = candidate(7L, "t-1");
        GenTaskEntity claimed = candidate(7L, "t-1");
        claimed.setStatus("RUNNING");
        claimed.setLockedBy("generator-task-other");
        claimed.setAttempts(1);
        when(tenantRepository.findAll()).thenReturn(Mono.just(List.of(tenant("t-1"))));
        when(genTaskRepository.findNextCandidate(eq("t-1"), eq(MAX_ATTEMPTS), any(LocalDateTime.class)))
                .thenReturn(Mono.just(candidate));
        when(genTaskRepository.claim(
                        eq(7L), eq("t-1"), anyString(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Mono.just(true));
        when(genTaskRepository.findById(7L, "t-1")).thenReturn(Mono.just(claimed));
        when(taskService.processTask(claimed, "t-1"))
                .thenReturn(Mono.just(new GeneratorWizardService.TaskOutcome(55L, 4, 0, false)));

        scheduler().poll();

        ArgumentCaptor<LocalDateTime> deadline = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(genTaskRepository, timeout(2000))
                .claim(eq(7L), eq("t-1"), anyString(), any(LocalDateTime.class), deadline.capture());
        verify(genTaskRepository, timeout(2000)).findById(7L, "t-1");
        verify(taskService, timeout(2000)).processTask(claimed, "t-1");
        assertThat(deadline.getValue()).isBefore(LocalDateTime.now().minusMinutes(LEASE_MINUTES - 1));
    }

    @Test
    void pollSkipsProcessingWhenClaimLoses() {
        when(tenantRepository.findAll()).thenReturn(Mono.just(List.of(tenant("t-1"))));
        when(genTaskRepository.findNextCandidate(eq("t-1"), eq(MAX_ATTEMPTS), any(LocalDateTime.class)))
                .thenReturn(Mono.just(candidate(7L, "t-1")));
        when(genTaskRepository.claim(any(), any(), anyString(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Mono.just(false));

        scheduler().poll();

        verify(genTaskRepository, timeout(2000))
                .claim(eq(7L), eq("t-1"), anyString(), any(LocalDateTime.class), any(LocalDateTime.class));
        verify(taskService, after(300).never()).processTask(any(), any());
    }

    @Test
    void pollSkipsProcessingWhenClaimedTaskMissing() {
        when(tenantRepository.findAll()).thenReturn(Mono.just(List.of(tenant("t-1"))));
        when(genTaskRepository.findNextCandidate(eq("t-1"), eq(MAX_ATTEMPTS), any(LocalDateTime.class)))
                .thenReturn(Mono.just(candidate(7L, "t-1")));
        when(genTaskRepository.claim(any(), any(), anyString(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Mono.just(true));
        when(genTaskRepository.findById(7L, "t-1")).thenReturn(Mono.empty());

        scheduler().poll();

        verify(genTaskRepository, timeout(2000)).findById(7L, "t-1");
        verify(taskService, after(300).never()).processTask(any(), any());
    }

    @Test
    void pollIsolatesFailingTenant() {
        GenTaskEntity candidate = candidate(8L, "b");
        when(tenantRepository.findAll()).thenReturn(Mono.just(List.of(tenant("a"), tenant("b"))));
        when(genTaskRepository.findNextCandidate(eq("a"), eq(MAX_ATTEMPTS), any(LocalDateTime.class)))
                .thenReturn(Mono.error(new IllegalStateException("db down")));
        when(genTaskRepository.findNextCandidate(eq("b"), eq(MAX_ATTEMPTS), any(LocalDateTime.class)))
                .thenReturn(Mono.just(candidate));
        when(genTaskRepository.claim(eq(8L), eq("b"), anyString(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Mono.just(true));
        when(genTaskRepository.findById(8L, "b")).thenReturn(Mono.just(candidate));
        when(taskService.processTask(candidate, "b"))
                .thenReturn(Mono.just(new GeneratorWizardService.TaskOutcome(66L, 1, 0, false)));

        scheduler().poll();

        verify(taskService, timeout(2000)).processTask(candidate, "b");
    }

    @Test
    void pollLimitsTenantsPerRound() {
        when(tenantRepository.findAll()).thenReturn(Mono.just(List.of(tenant("a"), tenant("b"), tenant("c"))));
        when(genTaskRepository.findNextCandidate(any(), anyInt(), any(LocalDateTime.class)))
                .thenReturn(Mono.empty());

        new GeneratorTaskScheduler(
                        taskService,
                        genTaskRepository,
                        tenantRepository,
                        MAX_ATTEMPTS,
                        LEASE_MINUTES,
                        2,
                        RETENTION_HOURS)
                .poll();

        verify(genTaskRepository, timeout(2000)).findNextCandidate(eq("a"), anyInt(), any());
        verify(genTaskRepository, timeout(2000)).findNextCandidate(eq("b"), anyInt(), any());
        verify(genTaskRepository, after(300).never()).findNextCandidate(eq("c"), anyInt(), any());
    }

    @Test
    void purgeDeletesFinishedTasksByRetention() {
        when(tenantRepository.findAll()).thenReturn(Mono.just(List.of(tenant("t-1"))));
        when(genTaskRepository.purgeFinishedBefore(eq("t-1"), any(LocalDateTime.class), eq(500)))
                .thenReturn(Mono.just(3));

        scheduler().purge();

        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(genTaskRepository, timeout(2000)).purgeFinishedBefore(eq("t-1"), cutoff.capture(), eq(500));
        assertThat(cutoff.getValue()).isBefore(LocalDateTime.now().minusHours(RETENTION_HOURS - 1));
    }

    @Test
    void purgeSkipsWhenTenantDirectoryEmpty() {
        when(tenantRepository.findAll()).thenReturn(Mono.just(List.of()));

        scheduler().purge();

        verify(tenantRepository, timeout(2000)).findAll();
        verify(genTaskRepository, after(200).never()).purgeFinishedBefore(any(), any(), anyInt());
    }

    @Test
    void pollSkipsBlankTenantCode() {
        TenantEntity blank = tenant(" ");
        when(tenantRepository.findAll()).thenReturn(Mono.just(List.of(blank, tenant("t-1"))));
        when(genTaskRepository.findNextCandidate(eq("t-1"), eq(MAX_ATTEMPTS), any(LocalDateTime.class)))
                .thenReturn(Mono.empty());

        scheduler().poll();

        verify(genTaskRepository, timeout(2000)).findNextCandidate(eq("t-1"), anyInt(), any());
        verify(genTaskRepository, never()).findNextCandidate(eq(" "), anyInt(), any());
    }

    /**
     * 构建租户目录实体。
     *
     * @param code 租户编码
     * @return 激活状态租户实体
     */
    private static TenantEntity tenant(String code) {
        TenantEntity entity = new TenantEntity();
        entity.setTenantCode(code);
        entity.setTenantName("tenant-" + code);
        return entity;
    }

    /**
     * 构建候选任务夹具。
     *
     * @param id     任务 ID
     * @param tenant 租户标识
     * @return 待执行任务实体
     */
    private static GenTaskEntity candidate(long id, String tenant) {
        GenTaskEntity entity = new GenTaskEntity();
        entity.setId(id);
        entity.setTenantId(tenant);
        entity.setStatus("PENDING");
        entity.setTotalCount(1);
        entity.setProcessedCount(0);
        entity.setFailedCount(0);
        entity.setAttempts(0);
        return entity;
    }
}
