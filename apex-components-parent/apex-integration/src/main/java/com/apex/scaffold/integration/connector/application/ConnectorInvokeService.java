package com.apex.scaffold.integration.connector.application;

import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.integration.connector.domain.ConnectorDefinition;
import com.apex.scaffold.integration.connector.domain.OutboundCallLog;
import com.apex.scaffold.integration.connector.domain.repository.ConnectorConfigRepository;
import com.apex.scaffold.integration.connector.domain.repository.OutboundCallLogRepository;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 连接器出站调用门面（方案 M5 / 批次 E2）。
 *
 * <p>编排：加载租户连接器配置 → 解析凭据引用（明文瞬时注入）→ 经
 * {@link ConnectorExecutionService}（幂等键重试）执行 → 出站审计落
 * {@code int_outbound_call_log}（成败均记录，审计为旁路不阻断）。错误信息截断
 * 后落库，不包含凭据。
 *
 * <p>审计内联在响应式链中完成（不手动 {@code subscribe()}），与业务结果共享
 * 同一次订阅生命周期；订阅取消时审计自然不执行，不产生游离任务。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConnectorInvokeService {

    /** 审计错误信息最大长度（与 int_outbound_call_log.error_message 列宽对齐）。 */
    private static final int ERROR_MESSAGE_MAX_LENGTH = 1000;

    private final ConnectorExecutionService executionService;

    private final ConnectorConfigRepository configRepository;

    private final CredentialReferenceResolver credentialResolver;

    private final OutboundCallLogRepository auditRepository;

    /**
     * 执行一次出站调用。
     *
     * @param tenantId       租户编码
     * @param configKey      连接器配置标识（租户内唯一）
     * @param operation      操作标识（连接器自定义语义）
     * @param payload        请求载荷
     * @param idempotencyKey 幂等键（可空；为空则不重试）
     * @return 连接器响应载荷
     */
    public Mono<Map<String, Object>> execute(
            String tenantId, String configKey, String operation, Map<String, Object> payload, String idempotencyKey) {
        long start = System.currentTimeMillis();
        // 捕获命中的配置，供审计记录真实连接器类型（而非配置标识）
        AtomicReference<ConnectorDefinition> definitionRef = new AtomicReference<>();
        return configRepository
                .findActive(tenantId, configKey)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "连接器配置不存在或未启用: " + configKey)))
                .doOnNext(definitionRef::set)
                .flatMap(definition -> credentialResolver
                        .resolve(tenantId, definition.getConfigValue())
                        .flatMap(resolvedConfig -> executionService.execute(
                                definition.getConnectorType(), resolvedConfig, operation, payload, idempotencyKey)))
                .transform(mono -> audit(mono, tenantId, definitionRef, configKey, operation, idempotencyKey, start));
    }

    private Mono<Map<String, Object>> audit(
            Mono<Map<String, Object>> call,
            String tenantId,
            AtomicReference<ConnectorDefinition> definitionRef,
            String configKey,
            String operation,
            String idempotencyKey,
            long start) {
        return call.flatMap(result -> auditRepository
                        .insert(buildLog(
                                tenantId, definitionRef.get(), configKey, operation, idempotencyKey, start, null))
                        .onErrorResume(e -> {
                            log.warn("出站审计写入失败: configKey={}, operation={}", configKey, operation, e);
                            return Mono.empty();
                        })
                        .thenReturn(result))
                .onErrorResume(error -> auditRepository
                        .insert(buildLog(
                                tenantId,
                                definitionRef.get(),
                                configKey,
                                operation,
                                idempotencyKey,
                                start,
                                error.getMessage()))
                        .onErrorResume(e -> {
                            log.warn("出站审计写入失败: configKey={}, operation={}", configKey, operation, e);
                            return Mono.empty();
                        })
                        .then(Mono.error(error)));
    }

    private OutboundCallLog buildLog(
            String tenantId,
            ConnectorDefinition definition,
            String configKey,
            String operation,
            String idempotencyKey,
            long start,
            String errorMessage) {
        OutboundCallLog entry = new OutboundCallLog();
        entry.setTenantId(tenantId);
        // 配置加载失败时可能尚无 definition，此时以配置标识占位
        entry.setConnectorType(definition != null ? definition.getConnectorType() : configKey);
        entry.setOperation(operation);
        entry.setIdempotencyKey(idempotencyKey);
        entry.setStatus(errorMessage == null ? "SUCCESS" : "FAILED");
        entry.setDurationMs(System.currentTimeMillis() - start);
        entry.setErrorMessage(errorMessage == null ? null : truncate(errorMessage));
        return entry;
    }

    private String truncate(String message) {
        String message0 = message == null ? "unknown" : message;
        return message0.length() <= ERROR_MESSAGE_MAX_LENGTH
                ? message0
                : message0.substring(0, ERROR_MESSAGE_MAX_LENGTH);
    }
}
