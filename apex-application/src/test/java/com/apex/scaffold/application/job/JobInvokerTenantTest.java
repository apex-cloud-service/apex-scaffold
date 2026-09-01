package com.apex.scaffold.application.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.domain.model.entity.JobEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import reactor.core.publisher.Mono;

class JobInvokerTenantTest {

    private final ApplicationContext applicationContext = mock(ApplicationContext.class);
    private final JobInvoker invoker = new JobInvoker(applicationContext);
    private final Target target = new Target();

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void shouldRejectJobWithoutTenant() {
        JobEntity job = JobEntity.create("job", "SYSTEM", "target.run()", "0 0 2 * * ?");

        assertThatThrownBy(() -> invoker.invoke(job))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("租户任务缺少租户ID");
    }

    @Test
    void shouldUseJobTenantForSynchronousInvocationAndRestoreCallerTenant() {
        when(applicationContext.containsBean("target")).thenReturn(true);
        when(applicationContext.getBean("target")).thenReturn(target);
        TenantContext.setTenantId("caller-tenant");
        JobEntity job = JobEntity.create("job", "SYSTEM", "target.run()", "0 0 2 * * ?", "job-tenant");

        invoker.invoke(job);

        assertThat(target.seenTenant).isEqualTo("job-tenant");
        assertThat(TenantContext.currentTenantId()).contains("caller-tenant");
    }

    @Test
    void shouldProvideJobTenantToReactiveInvocation() {
        when(applicationContext.containsBean("target")).thenReturn(true);
        when(applicationContext.getBean("target")).thenReturn(target);
        JobEntity job = JobEntity.create("job", "SYSTEM", "target.reactiveRun()", "0 0 2 * * ?", "job-tenant");

        invoker.invoke(job);

        assertThat(target.reactiveTenant).isEqualTo("job-tenant");
    }

    public static class Target {
        private String seenTenant;
        private String reactiveTenant;

        public void run() {
            seenTenant = TenantContext.currentTenantId().orElse(null);
        }

        public Mono<Void> reactiveRun() {
            return Mono.deferContextual(context -> {
                reactiveTenant = context.get(TenantContext.REACTOR_CONTEXT_KEY);
                return Mono.empty();
            });
        }
    }
}
