package com.apex.scaffold.officeservice.interfaces.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * 站内信票据与定向投递的安全契约测试。
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
class MessageSocketRegistryTest {
    /** 验证票据一次性使用且身份包含租户。 */
    @Test
    void ticketIsSingleUseAndBoundToIdentity() {
        var registry = new MessageSocketRegistry();
        var user = new AuthenticatedUser(7L, null, "tenant-a");
        String ticket = registry.issue(user);
        assertThat(registry.consume(ticket)).isEqualTo(user);
        assertThat(registry.consume(ticket)).isNull();
        assertThat(registry.consume("forged")).isNull();
    }

    /** 并发握手竞争同一票据时只能有一个成功认证。 */
    @Test
    void consumesTicketAtomicallyAcrossConcurrentHandshakes() throws Exception {
        var registry = new MessageSocketRegistry();
        var user = new AuthenticatedUser(7L, null, "a");
        String ticket = registry.issue(user);
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(8);
        try {
            var results = new ArrayList<Future<AuthenticatedUser>>();
            for (int i = 0; i < 8; i++) {
                results.add(executor.submit(() -> {
                    assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                    return registry.consume(ticket);
                }));
            }
            start.countDown();
            int authenticated = 0;
            for (var result : results) {
                if (result.get(5, TimeUnit.SECONDS) != null) {
                    authenticated++;
                }
            }
            assertThat(authenticated).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    /** 队列恰好容纳六十四条提示，第六十五条触发慢连接关闭标记。 */
    @Test
    void boundsQueueAndReleasesBufferedMessages() {
        var registry = new MessageSocketRegistry();
        var connection = registry.connect(new AuthenticatedUser(7L, null, "a"));
        for (long id = 1; id <= 64; id++) {
            registry.publish("a", 7L, id);
        }
        assertThat(connection.queue()).hasSize(64);
        assertThat(connection.overflow()).isFalse();
        registry.publish("a", 7L, 65L);
        assertThat(connection.overflow()).isTrue();
        assertThat(connection.queue()).hasSize(64);
        registry.disconnect(connection);
        assertThat(connection.queue()).isEmpty();
        assertThat(registry.connectionCount()).isZero();
    }

    /** 达到连接上限时拒绝新连接，释放一个名额后可以重新接入。 */
    @Test
    void boundsConnectionCapacityAndReusesReleasedSlot() {
        var registry = new MessageSocketRegistry();
        var user = new AuthenticatedUser(7L, null, "a");
        var first = registry.connect(user);
        for (int index = 1; index < 1024; index++) {
            registry.connect(user);
        }
        assertThat(registry.connectionCount()).isEqualTo(1024);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> registry.connect(user))
                .isInstanceOf(IllegalStateException.class);
        assertThat(registry.connectionCount()).isEqualTo(1024);
        registry.disconnect(first);
        registry.connect(user);
        assertThat(registry.connectionCount()).isEqualTo(1024);
        registry.close();
        assertThat(registry.connectionCount()).isZero();
    }

    /** 验证不同租户与用户拥有独立连接队列且关闭时清理。 */
    @Test
    void routesOnlyToMatchingTenantAndUser() {
        var registry = new MessageSocketRegistry();
        var first = registry.connect(new AuthenticatedUser(7L, null, "a"));
        var otherTenant = registry.connect(new AuthenticatedUser(7L, null, "b"));
        var otherUser = registry.connect(new AuthenticatedUser(8L, null, "a"));
        registry.publish("a", 7L, 99L);
        assertThat(first.queue().poll()).contains("99");
        assertThat(otherTenant.queue()).isEmpty();
        assertThat(otherUser.queue()).isEmpty();
        registry.disconnect(first);
        assertThat(registry.connectionCount()).isEqualTo(2);
    }
}
