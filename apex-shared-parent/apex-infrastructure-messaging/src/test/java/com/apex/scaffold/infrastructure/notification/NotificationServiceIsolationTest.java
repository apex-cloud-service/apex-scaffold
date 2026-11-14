package com.apex.scaffold.infrastructure.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.apex.scaffold.common.context.TenantContext;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import reactor.core.Disposable;
import reactor.test.StepVerifier;

class NotificationServiceIsolationTest {
    private final NotificationService service = new NotificationService(new SimpleMeterRegistry());

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void sameAccountAcrossTenantsMustNotShareEvents() {
        List<String> tenantA = new ArrayList<>();
        List<String> tenantB = new ArrayList<>();
        List<String> otherAccount = new ArrayList<>();
        Disposable a = service.stream("42")
                .contextWrite(TenantContext.withTenant("tenant-a"))
                .subscribe(message -> tenantA.add(message.content()));
        Disposable b = service.stream("42")
                .contextWrite(TenantContext.withTenant("tenant-b"))
                .subscribe(message -> tenantB.add(message.content()));
        Disposable other = service.stream("43")
                .contextWrite(TenantContext.withTenant("tenant-a"))
                .subscribe(message -> otherAccount.add(message.content()));
        try {
            TenantContext.setTenantId("tenant-a");
            service.publish("42", "a-only");
            TenantContext.setTenantId("tenant-b");
            service.publish("42", "b-only");
            assertThat(tenantA).containsExactly("a-only");
            assertThat(tenantB).containsExactly("b-only");
            assertThat(otherAccount).isEmpty();
        } finally {
            a.dispose();
            b.dispose();
            other.dispose();
        }
    }

    @Test
    void subscriptionMustRejectMissingTrustedTenantEvenWithThreadLocal() {
        TenantContext.setTenantId("stale-thread-tenant");
        StepVerifier.create(service.stream("42"))
                .expectError(IllegalArgumentException.class)
                .verify(Duration.ofSeconds(1));
    }

    @Test
    void publishingMustRejectMissingTenant() {
        assertThatThrownBy(() -> service.publish("42", "unscoped")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void publishingMustRejectUnscopedBroadcast() {
        TenantContext.setTenantId("tenant-a");
        assertThatThrownBy(() -> service.publish(null, "broadcast")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.publish(" ", "broadcast")).isInstanceOf(IllegalArgumentException.class);
    }
}
