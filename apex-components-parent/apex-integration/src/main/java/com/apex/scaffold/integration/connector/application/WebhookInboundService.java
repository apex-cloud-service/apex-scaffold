package com.apex.scaffold.integration.connector.application;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.domain.service.DomainEventPublisher;
import com.apex.scaffold.integration.connector.domain.InboundEvent;
import com.apex.scaffold.integration.connector.domain.connector.WebhookSignatures;
import com.apex.scaffold.integration.connector.domain.event.WebhookEventReceived;
import com.apex.scaffold.integration.connector.domain.repository.ConnectorConfigRepository;
import com.apex.scaffold.integration.connector.domain.repository.InboundEventRepository;
import com.apex.scaffold.integration.connector.domain.repository.WebhookDedupRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 入站 webhook 受理用例（方案 M5 / 批次 E2）。
 *
 * <p>受理链：租户解析 → 加载连接器配置并解密签名密钥 → 时间戳窗口校验（±5 分钟
 * 防重放）→ HMAC-SHA256 验签 → 幂等去重（唯一键冲突即重复投递，幂等成功且不再
 * 重复发布）→ 落库 {@code int_inbound_event} → 发布领域事件 → 标记已处理。
 * 验签失败的投递以 {@code signature_valid=0} 落库供审计后拒绝。
 *
 * <p>租户口径：入站 webhook 无认证用户，租户取
 * {@link TenantContext#currentTenantId()}（由网关注入的可信上下文），缺失时回退
 * 查询参数 {@code tenant}——该参数仅限定配置查找与落库作用域，投递方仍须持有
 * 该租户配置的 HMAC 密钥才能通过验签，不构成租户越权。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookInboundService {

    private static final String SIGNATURE_SEPARATOR = ".";

    /** 时间戳允许窗口（秒）：±5 分钟防重放。 */
    static final long TIMESTAMP_WINDOW_SECONDS = 300;

    /** 去重键保留时长（过期行交由维护任务清理）。 */
    private static final Duration DEDUP_TTL = Duration.ofHours(24);

    /** 去重键最大长度（与 int_webhook_dedup.dedup_key 列宽对齐）。 */
    private static final int DEDUP_KEY_MAX_LENGTH = 128;

    private final ConnectorConfigRepository configRepository;

    private final CredentialReferenceResolver credentialResolver;

    private final InboundEventRepository inboundEventRepository;

    private final WebhookDedupRepository dedupRepository;

    private final DomainEventPublisher eventPublisher;

    private final ObjectMapper objectMapper;

    /**
     * 受理一次入站 webhook 投递。
     *
     * @param fallbackTenantId 查询参数回退租户（可空）
     * @param connectorKey     webhook 配置标识（路径变量）
     * @param signature        X-Signature 头（十六进制 HMAC）
     * @param timestamp        X-Timestamp 头（Unix 秒）
     * @param body             原始请求体
     * @return 受理结果（含是否重复投递）
     */
    public Mono<WebhookAck> accept(
            String fallbackTenantId, String connectorKey, String signature, String timestamp, String body) {
        String tenantId = TenantContext.currentTenantId().orElseGet(() -> trimToEmpty(fallbackTenantId));
        if (tenantId.isBlank()) {
            return Mono.error(new BusinessException(ResultCode.UNAUTHORIZED, "无法确定投递租户"));
        }
        if (connectorKey == null || connectorKey.isBlank()) {
            return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "webhook 标识不能为空"));
        }
        // 显式装配租户上下文：入站无认证用户，行级拦截器（fail-closed）依赖本上下文
        return TenantContext.withTenant(doAccept(tenantId, connectorKey, signature, timestamp, body)
                .contextWrite(TenantContext.withTenant(tenantId)));
    }

    private Mono<WebhookAck> doAccept(
            String tenantId, String connectorKey, String signature, String timestamp, String body) {
        return configRepository
                .findActive(tenantId, connectorKey)
                .switchIfEmpty(
                        Mono.error(new BusinessException(ResultCode.NOT_FOUND, "webhook 配置不存在或未启用: " + connectorKey)))
                .flatMap(definition -> credentialResolver
                        .resolve(tenantId, definition.getConfigValue())
                        .flatMap(resolved -> verifyAndDispatch(
                                tenantId, definition.getConnectorType(), resolved, signature, timestamp, body)));
    }

    private Mono<WebhookAck> verifyAndDispatch(
            String tenantId,
            String connectorType,
            Map<String, Object> resolvedConfig,
            String signature,
            String timestamp,
            String body) {
        String secret = resolvedConfig.get("secret") instanceof String value ? value : null;
        if (secret == null || secret.isBlank()) {
            return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "webhook 配置缺少签名密钥"));
        }
        if (!withinWindow(timestamp)) {
            return recordFailure(tenantId, connectorType, body)
                    .then(Mono.error(new BusinessException(ResultCode.UNAUTHORIZED, "webhook 时间戳超出允许窗口")));
        }
        if (!WebhookSignatures.verify(secret, timestamp + SIGNATURE_SEPARATOR + body, signature)) {
            return recordFailure(tenantId, connectorType, body)
                    .then(Mono.error(new BusinessException(ResultCode.UNAUTHORIZED, "webhook 签名校验失败")));
        }
        String dedupKey = buildDedupKey(signature, timestamp, body);
        return dedupRepository
                .tryInsert(tenantId, dedupKey, LocalDateTime.now().plus(DEDUP_TTL))
                .flatMap(firstDelivery -> firstDelivery
                        ? recordAndPublish(tenantId, connectorType, body)
                        : Mono.just(new WebhookAck(true, true, null, "重复投递已幂等受理")));
    }

    private boolean withinWindow(String timestamp) {
        try {
            long deliveredAt = Long.parseLong(timestamp == null ? "" : timestamp.trim());
            long now = System.currentTimeMillis() / 1000;
            return Math.abs(now - deliveredAt) <= TIMESTAMP_WINDOW_SECONDS;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** 首次投递：落库（待处理）→ 发布领域事件 → 标记已处理。 */
    private Mono<WebhookAck> recordAndPublish(String tenantId, String connectorType, String body) {
        String eventType = extractTextField(body, "eventType", "event");
        InboundEvent event = new InboundEvent();
        event.setTenantId(tenantId);
        event.setConnectorType(connectorType);
        event.setEventType(eventType);
        event.setPayload(body);
        event.setSignatureValid(1);
        event.setProcessed(0);
        return inboundEventRepository
                .insert(event)
                .flatMap(saved -> eventPublisher
                        .publish(new WebhookEventReceived(
                                tenantId,
                                connectorType,
                                saved.getEventType(),
                                body,
                                saved.getId() == null ? null : String.valueOf(saved.getId())))
                        .then(inboundEventRepository.markProcessed(tenantId, saved.getId()))
                        .thenReturn(new WebhookAck(true, false, saved.getId(), "已受理")))
                .doOnSubscribe(ignored -> log.debug("webhook 首次投递受理中: connectorType={}", connectorType));
    }

    /** 验签失败投递：以 signature_valid=0 落库供审计，再拒绝。 */
    private Mono<Void> recordFailure(String tenantId, String connectorType, String body) {
        InboundEvent event = new InboundEvent();
        event.setTenantId(tenantId);
        event.setConnectorType(connectorType);
        event.setEventType("signature_invalid");
        event.setPayload(body);
        event.setSignatureValid(0);
        event.setProcessed(0);
        return inboundEventRepository
                .insert(event)
                .doOnNext(saved ->
                        log.warn("webhook 验签失败已记录: connectorType={}, eventId={}", connectorType, saved.getId()))
                .then();
    }

    /**
     * 去重键：SHA-256(signature|timestamp|eventIdOrBodyHash)，截断至列宽。
     * 无显式事件 ID 时以载荷摘要兜底，同一投递重放必得同一键。
     */
    private String buildDedupKey(String signature, String timestamp, String body) {
        String eventId = extractTextField(body, "eventId", "event_id");
        String tail = eventId != null ? eventId : WebhookSignatures.sha256Hex(body == null ? "" : body);
        String key = WebhookSignatures.sha256Hex(signature + "|" + timestamp + "|" + tail);
        return key.length() <= DEDUP_KEY_MAX_LENGTH ? key : key.substring(0, DEDUP_KEY_MAX_LENGTH);
    }

    /** 从 JSON 载荷提取首个非空文本字段（解析失败返回默认值）。 */
    private String extractTextField(String body, String... fieldNames) {
        if (body == null || body.isBlank()) {
            return "inbound";
        }
        try {
            Map<String, Object> payload = objectMapper.readValue(body, new TypeReference<Map<String, Object>>() {});
            for (String fieldName : fieldNames) {
                if (payload.get(fieldName) instanceof String value && !value.isBlank()) {
                    return value;
                }
            }
        } catch (Exception e) {
            // 非 JSON 载荷按默认事件类型受理，原文仍完整落库
        }
        return "inbound";
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * 入站受理结果。
     *
     * @param accepted  是否受理成功（含重复投递的幂等成功）
     * @param duplicate 是否重复投递（未再次发布事件）
     * @param eventId   入站事件主键（重复投递时为 null）
     * @param message   结果说明
     */
    public record WebhookAck(boolean accepted, boolean duplicate, Long eventId, String message) {}
}
