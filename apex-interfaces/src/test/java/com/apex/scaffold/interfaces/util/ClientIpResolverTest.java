package com.apex.scaffold.interfaces.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.interfaces.filter.IpRateLimitProperties;
import java.net.InetSocketAddress;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * ClientIpResolverTest 单元测试。
 *
 * <p>覆盖核心正常流程、参数边界与失败分支，防止后续修改破坏既有行为。</p>
 *
 * @author Apex Team
 */
class ClientIpResolverTest {

    private final IpRateLimitProperties properties = new IpRateLimitProperties();
    private final ClientIpResolver resolver = new ClientIpResolver(properties);

    /**
     * 执行 {@code untrustedPeerCannotSpoofForwardedHeaders} 操作。
     */
    @Test
    void untrustedPeerCannotSpoofForwardedHeaders() {
        String result =
                resolver.resolve(new InetSocketAddress("203.0.113.9", 443), List.of("198.51.100.8"), "198.51.100.7");

        assertThat(result).isEqualTo("203.0.113.9");
    }

    /**
     * 执行 {@code trustedProxyUsesFirstUntrustedHopFromRight} 操作。
     */
    @Test
    void trustedProxyUsesFirstUntrustedHopFromRight() {
        properties.setTrustedProxyCidrs(List.of("10.0.0.0/8"));

        String result =
                resolver.resolve(new InetSocketAddress("10.0.0.5", 443), List.of("198.51.100.8, 10.0.0.4"), null);

        assertThat(result).isEqualTo("198.51.100.8");
    }
}
