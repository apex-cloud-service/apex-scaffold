package com.apex.scaffold.integration.connector.application;

import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.integration.connector.domain.IntegrationConnector;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

/**
 * 连接器执行服务（方案 M5 / 批次 E1）。
 *
 * <p>统一出站调用编排：连接器查找 → 熔断/超时 → 指数退避重试（仅幂等操作）→
 * 结果与耗时返回。审计与凭据解密由上层适配器负责（本类不接触密文）。
 *
 * <p>重试口径：
 * <ul>
 *   <li>提供幂等键时最多重试 2 次（指数退避 200ms 起）；</li>
 *   <li>未提供幂等键时**不重试**（避免非幂等副作用重复执行）；</li>
 *   <li>参数错误（{@link BusinessException}）不重试。</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConnectorExecutionService {

    /** 连接器注册表（类型 → 实现），由装配层注入。 */
    private final Map<String, IntegrationConnector> connectors;

    /**
     * 执行一次出站调用。
     *
     * @param connectorType  连接器类型
     * @param config         已解密的连接配置
     * @param operation      操作标识
     * @param payload        请求载荷
     * @param idempotencyKey 幂等键（可空；为空则不重试）
     * @return 响应载荷
     */
    public Mono<Map<String, Object>> execute(
            String connectorType,
            Map<String, Object> config,
            String operation,
            Map<String, Object> payload,
            String idempotencyKey) {
        IntegrationConnector connector = connectors == null ? null : connectors.get(connectorType);
        if (connector == null) {
            return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "连接器未启用或未知: " + connectorType));
        }
        if (operation == null || operation.isBlank()) {
            return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "操作标识不能为空"));
        }
        String effectiveKey = idempotencyKey == null || idempotencyKey.isBlank()
                ? UUID.randomUUID().toString()
                : idempotencyKey;
        Mono<Map<String, Object>> call = Mono.defer(() -> connector.execute(config, operation, payload, effectiveKey));
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return call;
        }
        return call.retryWhen(Retry.backoff(2, Duration.ofMillis(200))
                        .filter(error -> !(error instanceof BusinessException))
                        .onRetryExhaustedThrow((spec, signal) -> signal.failure()))
                .doOnError(error -> log.warn(
                        "连接器调用失败（已重试）: type={}, operation={}, key={}, reason={}",
                        connectorType,
                        operation,
                        effectiveKey,
                        error.getMessage()));
    }

    /**
     * 已启用的连接器类型。
     *
     * @return 类型列表
     */
    public java.util.List<String> availableTypes() {
        return connectors == null ? java.util.List.of() : java.util.List.copyOf(connectors.keySet());
    }
}
