package com.apex.scaffold.jobservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.apex.scaffold.infrastructure.remote.config.ManagedWebClientFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.reactive.server.WebTestClient;

/** 验证默认启动不要求数据库、Redis 或 XXL 中心。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class JobServiceStartupTest {
    @LocalServerPort
    private int port;

    private final ManagedWebClientFactory factory;

    JobServiceStartupTest(ManagedWebClientFactory factory) {
        this.factory = factory;
    }

    @Test
    void startsWithoutExternalDependencies() {
        WebTestClient.bindToServer()
                .baseUrl("http://127.0.0.1:" + port)
                .build()
                .get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo("UP")
                .jsonPath("$.components")
                .doesNotExist();
    }

    @Test
    void acceptsFixedHttpAndHttpsUpstreams() {
        JobServiceApplication application = new JobServiceApplication();
        assertThat(application.jobProxy("http://system.internal:8082", factory)).isNotNull();
        assertThat(application.jobProxy("https://system.internal:8443", factory))
                .isNotNull();
    }

    @Test
    void rejectsUntrustedUpstreamConfiguration() {
        for (String address : new String[] {
            "http:/missing-host",
            "https://user:password@system.internal",
            "https://system.internal?target=external",
            "ftp://system.internal"
        }) {
            assertThatThrownBy(() -> new JobServiceApplication().jobProxy(address, factory))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
