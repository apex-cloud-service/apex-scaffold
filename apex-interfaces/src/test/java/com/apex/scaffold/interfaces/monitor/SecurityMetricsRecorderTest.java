package com.apex.scaffold.interfaces.monitor;

import static org.mockito.Mockito.verify;

import io.micrometer.core.instrument.Counter;
import org.junit.jupiter.api.Test;

/**
 * Tests that every security event increments its exported counter.
 *
 * @author Apex Team
 */
class SecurityMetricsRecorderTest {

    /**
     * 验证 {@code shouldIncrementEveryCounter} 场景。
     */
    @Test
    void shouldIncrementEveryCounter() {
        Counter rateLimit = org.mockito.Mockito.mock(Counter.class);
        Counter ipBlocked = org.mockito.Mockito.mock(Counter.class);
        Counter loginFailed = org.mockito.Mockito.mock(Counter.class);
        Counter xss = org.mockito.Mockito.mock(Counter.class);
        Counter antiReplay = org.mockito.Mockito.mock(Counter.class);
        Counter circuitBreaker = org.mockito.Mockito.mock(Counter.class);
        SecurityMetricsRecorder recorder =
                new SecurityMetricsRecorder(rateLimit, ipBlocked, loginFailed, xss, antiReplay, circuitBreaker);

        recorder.recordRateLimitRejected();
        recorder.recordIpBlocked();
        recorder.recordLoginFailed();
        recorder.recordXssIntercepted();
        recorder.recordAntiReplayRejected();
        recorder.recordCircuitBreakerOpened();

        verify(rateLimit).increment();
        verify(ipBlocked).increment();
        verify(loginFailed).increment();
        verify(xss).increment();
        verify(antiReplay).increment();
        verify(circuitBreaker).increment();
    }
}
