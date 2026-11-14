package com.apex.scaffold.application.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.apex.scaffold.domain.model.entity.JobEntity;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import reactor.core.publisher.Flux;

class JobInvokerCompletionTest {
    @Test
    void waitsForEveryElementAndLateFailure() {
        ApplicationContext context = mock(ApplicationContext.class);
        Target target = new Target();
        when(context.containsBean("target")).thenReturn(true);
        when(context.getBean("target")).thenReturn(target);
        JobInvoker invoker = new JobInvoker(context);
        JobEntity job = JobEntity.create("job", "SYSTEM", "target.run()", "0 0 2 * * ?", "tenant");
        assertThatThrownBy(() -> invoker.invoke(job)).hasMessageContaining("late failure");
        assertThat(target.count).hasValue(3);
    }

    @Test
    void rejectsUnregisteredMethods() {
        ApplicationContext context = mock(ApplicationContext.class);
        when(context.containsBean("target")).thenReturn(true);
        when(context.getBean("target")).thenReturn(new Target());
        JobEntity job = JobEntity.create("job", "SYSTEM", "target.toString()", "0 0 2 * * ?", "tenant");
        assertThatThrownBy(() -> new JobInvoker(context).invoke(job)).hasMessageContaining("未授权");
    }

    public static class Target {
        final AtomicInteger count = new AtomicInteger();

        @ManagedJob
        public Flux<Integer> run() {
            return Flux.range(1, 3)
                    .doOnNext(value -> count.incrementAndGet())
                    .concatWith(Flux.error(new IllegalStateException("late failure")));
        }
    }
}
