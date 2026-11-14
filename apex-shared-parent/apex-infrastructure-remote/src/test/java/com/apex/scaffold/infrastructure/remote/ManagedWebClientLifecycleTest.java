package com.apex.scaffold.infrastructure.remote;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.infrastructure.remote.config.ManagedWebClientFactory;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;

/** 保护受管客户端的容器级资源所有权，防止静态池跨容器共享。 */
class ManagedWebClientLifecycleTest {
    @Test
    void bindsConfigurationBeforeConsumerAndDisposesPoolOnContextClose() {
        reactor.netty.http.server.HttpServer server = reactor.netty.http.server.HttpServer.create();
        reactor.netty.DisposableServer endpoint = server.port(0)
                .handle((request, response) -> response.sendString(reactor.core.publisher.Mono.just("ok")))
                .bindNow();
        java.util.concurrent.atomic.AtomicReference<reactor.netty.resources.ConnectionProvider> pool =
                new java.util.concurrent.atomic.AtomicReference<>();
        try {
            new org.springframework.boot.test.context.runner.ApplicationContextRunner()
                    .withUserConfiguration(
                            com.apex.scaffold.infrastructure.remote.config.ManagedWebClientInitializer.class)
                    .withPropertyValues("remote.api.pool-max-connections=7")
                    .run(context -> {
                        ManagedWebClientFactory factory = context.getBean(ManagedWebClientFactory.class);
                        reactor.netty.resources.ConnectionProvider provider =
                                (reactor.netty.resources.ConnectionProvider)
                                        org.springframework.test.util.ReflectionTestUtils.getField(factory, "provider");
                        pool.set(provider);
                        assertThat(provider.maxConnections()).isEqualTo(7);
                        assertThat(factory.create("http://127.0.0.1:" + endpoint.port())
                                        .get()
                                        .retrieve()
                                        .bodyToMono(String.class)
                                        .block(java.time.Duration.ofSeconds(5)))
                                .isEqualTo("ok");
                        assertThat(provider.isDisposed()).isFalse();
                    });
            assertThat(pool.get().isDisposed()).isTrue();
        } finally {
            endpoint.disposeNow();
        }
    }

    @Test
    void factoryIsAnInstanceResource() throws Exception {
        assertThat(Modifier.isStatic(
                        ManagedWebClientFactory.class.getMethod("create").getModifiers()))
                .isFalse();
        assertThat(AutoCloseable.class.isAssignableFrom(ManagedWebClientFactory.class))
                .isTrue();
    }
}
