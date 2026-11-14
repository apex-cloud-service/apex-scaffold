package com.apex.scaffold.systemservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apex.scaffold.application.service.JobService;
import com.apex.scaffold.job.SystemJobPort;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 系统任务端口适配契约测试。
 *
 * @author Apex Team
 */
class SystemJobPortConfigurationTest {
    /** 验证任务 ID 与原始异步失败信号传递到调度调用方。 */
    @Test
    void delegatesJobIdAndPropagatesFailure() {
        JobService service = mock(JobService.class);
        IllegalStateException failure = new IllegalStateException("任务执行失败");
        Mono<Void> execution = Mono.error(failure);
        when(service.runOnce(42L)).thenReturn(execution);

        SystemJobPort port = new SystemJobPortConfiguration().systemJobPort(service);
        var result = port.runOnce(42L);

        assertThat(result).isSameAs(execution);
        StepVerifier.create(result)
                .expectErrorMatches(error -> error == failure)
                .verify();
        verify(service).runOnce(42L);
    }
}
