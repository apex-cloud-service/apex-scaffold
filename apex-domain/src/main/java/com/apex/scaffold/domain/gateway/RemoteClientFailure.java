package com.apex.scaffold.domain.gateway;

/**
 * Failure contract exposed by remote adapters to application services.
 *
 * <p>Keeping this classification at the domain boundary prevents application
 * code from importing an infrastructure exception merely to distinguish a
 * deterministic client-input rejection from an ambiguous network failure.</p>
 * @author weiqiang.ling
 */
/**
 * RemoteClientFailure 远程客户端异常/失败契约。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public interface RemoteClientFailure {

    /**
     * Whether the remote system rejected the request as deterministic client input.
     *
     * @return {@code true} for a 4xx-style input rejection
     */
    boolean isClientError();
}
