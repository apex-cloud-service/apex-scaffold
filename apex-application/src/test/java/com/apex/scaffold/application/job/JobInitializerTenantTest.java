package com.apex.scaffold.application.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.apex.scaffold.domain.model.entity.JobEntity;
import com.apex.scaffold.domain.repository.JobRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import reactor.core.publisher.Mono;

class JobInitializerTenantTest {

    private final JobRepository repository = mock(JobRepository.class);
    private final JobScheduler scheduler = mock(JobScheduler.class);
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(JobRepository.class, () -> repository)
            .withBean(JobScheduler.class, () -> scheduler)
            .withUserConfiguration(JobInitializer.class);

    @Test
    void shouldNotCreateInitializerWhenSchedulingIsDisabled() {
        contextRunner.withPropertyValues("apex.scheduling.enabled=false").run(context -> {
            assertThat(context).doesNotHaveBean(JobInitializer.class);
            verifyNoInteractions(repository, scheduler);
        });
    }

    @Test
    void shouldCreateInitializerWhenSchedulingPropertyIsMissing() {
        when(repository.findEnabled()).thenReturn(Mono.just(List.of()));

        contextRunner.run(context -> assertThat(context).hasSingleBean(JobInitializer.class));
    }

    @Test
    void shouldSkipEnabledJobWithoutTenant() {
        JobRepository repository = mock(JobRepository.class);
        JobScheduler scheduler = mock(JobScheduler.class);
        JobEntity job = JobEntity.create("legacy", "DEFAULT", "bean.run()", "0 0 2 * * ?");
        when(repository.findEnabled()).thenReturn(Mono.just(List.of(job)));
        when(scheduler.register(job)).thenReturn(Mono.empty());

        new JobInitializer(repository, scheduler).run(null);

        verifyNoInteractions(scheduler);
    }

    @Test
    void shouldRegisterEnabledJobWithTenant() {
        JobRepository repository = mock(JobRepository.class);
        JobScheduler scheduler = mock(JobScheduler.class);
        JobEntity job = JobEntity.create("tenant-job", "DEFAULT", "bean.run()", "0 0 2 * * ?", "tenant-a");
        when(repository.findEnabled()).thenReturn(Mono.just(List.of(job)));
        when(scheduler.register(job)).thenReturn(Mono.empty());

        new JobInitializer(repository, scheduler).run(null);

        verify(scheduler).register(job);
    }
}
