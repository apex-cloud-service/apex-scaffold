package com.apex.scaffold.application.job;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.apex.scaffold.application.dto.admin.JobRequest;
import com.apex.scaffold.application.service.JobService;
import com.apex.scaffold.application.service.TenantGuard;
import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.domain.model.entity.JobEntity;
import com.apex.scaffold.domain.repository.JobRepository;
import com.apex.scaffold.domain.search.IndexSyncPort;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticApplicationContext;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class ManagedJobBehaviorTest {
    @Test
    void realSearchJobIsExecutableAndPropagatesFailure() {
        IndexSyncPort port = mock(IndexSyncPort.class);
        when(port.syncIncremental(eq("user"), anyLong())).thenReturn(Mono.error(new IllegalStateException("索引失败")));
        try (StaticApplicationContext context = new StaticApplicationContext()) {
            context.getBeanFactory()
                    .registerSingleton("searchIndexReconciliationJob", new SearchIndexReconciliationJob(port));
            JobInvoker invoker = new JobInvoker(context);
            String target = "searchIndexReconciliationJob.manualSync('user')";
            invoker.validateTarget(target);
            JobEntity job = JobEntity.create("索引", "SYSTEM", target, "0 0 2 * * ?", "tenant");
            assertThatThrownBy(() -> invoker.invoke(job)).hasMessageContaining("索引失败");
            verify(port).syncIncremental(eq("user"), anyLong());
            assertThatThrownBy(() -> invoker.validateTarget("searchIndexReconciliationJob.toString()"))
                    .hasMessageContaining("未授权");
        }
    }

    @Test
    void validatesAndExecutesProxiedManagedMethod() {
        IndexSyncPort port = mock(IndexSyncPort.class);
        when(port.syncIncremental(eq("user"), anyLong())).thenReturn(Mono.just(1L));
        org.springframework.aop.framework.ProxyFactory factory =
                new org.springframework.aop.framework.ProxyFactory(new SearchIndexReconciliationJob(port));
        factory.setProxyTargetClass(true);
        try (StaticApplicationContext context = new StaticApplicationContext()) {
            context.getBeanFactory().registerSingleton("search", factory.getProxy());
            JobInvoker invoker = new JobInvoker(context);
            invoker.validateTarget("search.manualSync('user')");
            invoker.invoke(JobEntity.create("索引", "SYSTEM", "search.manualSync('user')", "0 0 2 * * ?", "tenant"));
            verify(port).syncIncremental(eq("user"), anyLong());
        }
    }

    @Test
    void rejectsUnauthorizedCreateAndUpdateBeforePersistence() {
        JobRepository repository = mock(JobRepository.class);
        JobScheduler scheduler = mock(JobScheduler.class);
        try (StaticApplicationContext context = new StaticApplicationContext()) {
            context.getBeanFactory().registerSingleton("arbitrary", new Object());
            TenantGuard tenantGuard = mock(TenantGuard.class);
            when(tenantGuard.assertActive()).thenReturn(Mono.empty());
            JobService service =
                    new JobService(repository, scheduler, null, null, new JobInvoker(context), tenantGuard);
            JobRequest request = new JobRequest();
            request.setJobName("非法任务");
            request.setCronExpression("0 0 2 * * ?");
            request.setInvokeTarget("arbitrary.toString()");
            TenantContext.setTenantId("tenant");
            try {
                StepVerifier.create(service.createJob(request))
                        .expectErrorMessage("任务方法未授权")
                        .verify();
                verifyNoInteractions(repository, scheduler);
                JobEntity existing = JobEntity.create("原任务", "SYSTEM", "old.run()", "0 0 2 * * ?", "tenant");
                when(repository.findById(1L)).thenReturn(Mono.just(existing));
                StepVerifier.create(service.updateJob(1L, request))
                        .expectErrorMessage("任务方法未授权")
                        .verify();
                verify(repository, never()).updateById(any());
                assertThat(existing.getInvokeTarget()).isEqualTo("old.run()");
            } finally {
                TenantContext.clear();
            }
        }
    }
}
