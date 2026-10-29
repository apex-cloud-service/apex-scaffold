package com.apex.scaffold.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import reactor.core.publisher.Flux;

class JobExecutorTest {
    @Test
    void disabledByDefault() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(JobExecutorAutoConfiguration.class))
                .run(context -> assertThat(context).doesNotHaveBean(XxlJobSpringExecutor.class));
    }

    @Test
    void configuresExecutorWithoutStartingNetworkLifecycle() {
        JobExecutorProperties properties = validProperties();
        properties.setAddress("https://executor.internal");
        properties.setIp("127.0.0.1");
        properties.setLogPath("logs/xxl-test");
        properties.setLogRetentionDays(7);
        XxlJobSpringExecutor executor = new JobExecutorAutoConfiguration().xxlJobExecutor(properties);
        assertThat(executor).isNotNull();
        assertThat(properties.getPort()).isEqualTo(9999);
        assertThat(properties.getLogRetentionDays()).isEqualTo(7);
    }

    @Test
    void rejectsMissingCredentialsAndApplicationIdentity() {
        for (String token : new String[] {null, "short"}) {
            JobExecutorProperties properties = validProperties();
            properties.setAccessToken(token);
            assertRejected(properties);
        }
        for (String name : new String[] {null, " "}) {
            JobExecutorProperties properties = validProperties();
            properties.setAppName(name);
            assertRejected(properties);
        }
        for (String address : new String[] {null, " "}) {
            JobExecutorProperties properties = validProperties();
            properties.setAdminAddresses(address);
            assertRejected(properties);
        }
    }

    @Test
    void rejectsInsecureAddressesAndInvalidPorts() {
        for (String address :
                new String[] {"http://admin.internal", "https:/missing-host", "https://user:password@admin.internal"}) {
            JobExecutorProperties properties = validProperties();
            properties.setAdminAddresses(address);
            assertRejected(properties);
        }
        for (int port : new int[] {0, 65536}) {
            JobExecutorProperties properties = validProperties();
            properties.setPort(port);
            assertRejected(properties);
        }
    }

    @Test
    void waitsForLateFailure() {
        AtomicInteger count = new AtomicInteger();
        assertThatThrownBy(() -> JobCompletion.await(Flux.range(1, 3)
                        .doOnNext(value -> count.incrementAndGet())
                        .concatWith(Flux.error(new IllegalStateException("late failure")))))
                .hasMessageContaining("late failure");
        assertThat(count).hasValue(3);
    }

    private JobExecutorProperties validProperties() {
        JobExecutorProperties properties = new JobExecutorProperties();
        properties.setAccessToken("test-only-token-with-at-least-32-characters");
        properties.setAppName("apex-test");
        properties.setAdminAddresses("https://admin.internal, https://backup.internal");
        properties.setPort(9999);
        return properties;
    }

    private void assertRejected(JobExecutorProperties properties) {
        assertThatThrownBy(() -> new JobExecutorAutoConfiguration().xxlJobExecutor(properties))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
