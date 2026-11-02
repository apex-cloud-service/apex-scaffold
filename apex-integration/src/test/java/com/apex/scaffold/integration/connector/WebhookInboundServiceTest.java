package com.apex.scaffold.integration.connector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.domain.service.DomainEventPublisher;
import com.apex.scaffold.integration.connector.application.CredentialReferenceResolver;
import com.apex.scaffold.integration.connector.application.WebhookInboundService;
import com.apex.scaffold.integration.connector.domain.ConnectorDefinition;
import com.apex.scaffold.integration.connector.domain.InboundEvent;
import com.apex.scaffold.integration.connector.domain.connector.WebhookSignatures;
import com.apex.scaffold.integration.connector.domain.event.WebhookEventReceived;
import com.apex.scaffold.integration.connector.domain.repository.ConnectorConfigRepository;
import com.apex.scaffold.integration.connector.domain.repository.InboundEventRepository;
import com.apex.scaffold.integration.connector.domain.repository.WebhookDedupRepository;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 入站 webhook 受理测试（方案 M5 / 批次 E2）。
 *
 * <p>反例矩阵：伪造签名、过期时间戳（±5 分钟防重放）、缺少签名头、配置缺密钥；
 * 正例：验签通过落库并发布事件；重复投递幂等成功且不重复发布。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@DisplayName("入站 webhook 验签/防重放/去重测试")
class WebhookInboundServiceTest {

    private static final String SECRET = "webhook-shared-secret";

    private static final String BODY = "{\"eventType\":\"push\",\"eventId\":\"evt-1\",\"data\":{\"n\":1}}";

    private ConnectorConfigRepository configRepository;

    private CredentialReferenceResolver credentialResolver;

    private InboundEventRepository inboundEventRepository;

    private WebhookDedupRepository dedupRepository;

    private DomainEventPublisher eventPublisher;

    private WebhookInboundService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        configRepository = mock(ConnectorConfigRepository.class);
        credentialResolver = mock(CredentialReferenceResolver.class);
        inboundEventRepository = mock(InboundEventRepository.class);
        dedupRepository = mock(WebhookDedupRepository.class);
        eventPublisher = mock(DomainEventPublisher.class);
        service = new WebhookInboundService(
                configRepository,
                credentialResolver,
                inboundEventRepository,
                dedupRepository,
                eventPublisher,
                new com.fasterxml.jackson.databind.ObjectMapper());

        ConnectorDefinition definition = new ConnectorDefinition();
        definition.setTenantId("t1");
        definition.setConnectorType("WEBHOOK");
        definition.setConfigKey("github-hook");
        definition.setConfigValue("{\"url\":\"https://cb.example.com\"}");
        when(configRepository.findActive("t1", "github-hook")).thenReturn(Mono.just(definition));
        when(credentialResolver.resolve(eq("t1"), anyString()))
                .thenReturn(Mono.just(Map.of("url", "https://cb.example.com", "secret", SECRET)));
        when(inboundEventRepository.insert(any(InboundEvent.class))).thenAnswer(invocation -> {
            InboundEvent event = invocation.getArgument(0);
            event.setId(100L);
            return Mono.just(event);
        });
        when(inboundEventRepository.markProcessed(anyString(), any())).thenReturn(Mono.empty());
        when(dedupRepository.tryInsert(anyString(), anyString(), any(LocalDateTime.class)))
                .thenReturn(Mono.just(true));
        when(eventPublisher.publish(any())).thenReturn(Mono.empty());
    }

    private String sign(String timestamp) {
        return WebhookSignatures.hmacSha256Hex(SECRET, timestamp + "." + BODY);
    }

    @Test
    @DisplayName("验签通过：落库 signature_valid=1，发布事件并标记已处理")
    void acceptsValidDelivery() {
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        StepVerifier.create(service.accept("t1", "github-hook", sign(timestamp), timestamp, BODY))
                .assertNext(ack -> {
                    assertThat(ack.accepted()).isTrue();
                    assertThat(ack.duplicate()).isFalse();
                    assertThat(ack.eventId()).isEqualTo(100L);
                })
                .verifyComplete();

        ArgumentCaptor<InboundEvent> eventCaptor = ArgumentCaptor.forClass(InboundEvent.class);
        verify(inboundEventRepository).insert(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getSignatureValid()).isEqualTo(1);
        assertThat(eventCaptor.getValue().getEventType()).isEqualTo("push");
        verify(eventPublisher).publish(any(WebhookEventReceived.class));
        verify(inboundEventRepository).markProcessed("t1", 100L);
    }

    @Test
    @DisplayName("伪造签名：落库 signature_valid=0 并以 401 拒绝，不发布事件")
    void rejectsForgedSignature() {
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String forged = WebhookSignatures.hmacSha256Hex("wrong-secret", timestamp + "." + BODY);
        StepVerifier.create(service.accept("t1", "github-hook", forged, timestamp, BODY))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(BusinessException.class);
                    assertThat(((BusinessException) error).getCode()).isEqualTo(ResultCode.UNAUTHORIZED.getCode());
                })
                .verify();

        ArgumentCaptor<InboundEvent> eventCaptor = ArgumentCaptor.forClass(InboundEvent.class);
        verify(inboundEventRepository).insert(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getSignatureValid()).isZero();
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    @DisplayName("过期时间戳（超 ±5 分钟）：防重放拒绝，不发布事件")
    void rejectsExpiredTimestamp() {
        String stale = String.valueOf(System.currentTimeMillis() / 1000 - 400);
        StepVerifier.create(service.accept("t1", "github-hook", sign(stale), stale, BODY))
                .expectError(BusinessException.class)
                .verify();
        verify(eventPublisher, never()).publish(any());
        ArgumentCaptor<InboundEvent> eventCaptor = ArgumentCaptor.forClass(InboundEvent.class);
        verify(inboundEventRepository).insert(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getSignatureValid()).isZero();
    }

    @Test
    @DisplayName("重复投递（去重键冲突）：幂等成功且不再发布事件")
    void deduplicatesRepeatedDelivery() {
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        when(dedupRepository.tryInsert(anyString(), anyString(), any(LocalDateTime.class)))
                .thenReturn(Mono.just(false));
        StepVerifier.create(service.accept("t1", "github-hook", sign(timestamp), timestamp, BODY))
                .assertNext(ack -> {
                    assertThat(ack.accepted()).isTrue();
                    assertThat(ack.duplicate()).isTrue();
                    assertThat(ack.eventId()).isNull();
                })
                .verifyComplete();
        verify(inboundEventRepository, never()).insert(any());
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    @DisplayName("配置缺少签名密钥：参数错误拒绝")
    void rejectsConfigWithoutSecret() {
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        when(credentialResolver.resolve(eq("t1"), anyString()))
                .thenReturn(Mono.just(Map.of("url", "https://cb.example.com")));
        StepVerifier.create(service.accept("t1", "github-hook", sign(timestamp), timestamp, BODY))
                .expectError(BusinessException.class)
                .verify();
    }

    @Test
    @DisplayName("无法确定租户：以未授权拒绝")
    void rejectsWithoutTenant() {
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        StepVerifier.create(service.accept("", "github-hook", sign(timestamp), timestamp, BODY))
                .expectError(BusinessException.class)
                .verify();
    }
}
