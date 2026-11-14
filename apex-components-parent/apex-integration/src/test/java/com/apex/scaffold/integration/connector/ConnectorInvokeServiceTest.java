package com.apex.scaffold.integration.connector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.integration.connector.application.ConnectorExecutionService;
import com.apex.scaffold.integration.connector.application.ConnectorInvokeService;
import com.apex.scaffold.integration.connector.application.CredentialReferenceResolver;
import com.apex.scaffold.integration.connector.domain.ConnectorDefinition;
import com.apex.scaffold.integration.connector.domain.OutboundCallLog;
import com.apex.scaffold.integration.connector.domain.repository.ConnectorConfigRepository;
import com.apex.scaffold.integration.connector.domain.repository.OutboundCallLogRepository;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 连接器出站门面测试（方案 M5 / 批次 E2）。
 *
 * <p>覆盖：配置缺失按 404 并落失败审计、成功审计记录真实连接器类型、
 * 失败审计记录截断后的错误信息、审计旁路失败不阻断业务结果。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@DisplayName("连接器出站门面测试")
class ConnectorInvokeServiceTest {

    private ConnectorExecutionService executionService;

    private ConnectorConfigRepository configRepository;

    private CredentialReferenceResolver credentialResolver;

    private OutboundCallLogRepository auditRepository;

    private ConnectorInvokeService service;

    @BeforeEach
    void setUp() {
        executionService = mock(ConnectorExecutionService.class);
        configRepository = mock(ConnectorConfigRepository.class);
        credentialResolver = mock(CredentialReferenceResolver.class);
        auditRepository = mock(OutboundCallLogRepository.class);
        service = new ConnectorInvokeService(executionService, configRepository, credentialResolver, auditRepository);
        when(auditRepository.insert(any(OutboundCallLog.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
    }

    @Test
    @DisplayName("配置缺失：404 且以配置标识占位记录失败审计")
    void missingConfigIsNotFoundAndAudited() {
        when(configRepository.findActive("t1", "cfg-1")).thenReturn(Mono.empty());

        StepVerifier.create(service.execute("t1", "cfg-1", "send", Map.of(), "idem-1"))
                .expectErrorSatisfies(error ->
                        assertThat(((BusinessException) error).getCode()).isEqualTo(ResultCode.NOT_FOUND.getCode()))
                .verify();

        OutboundCallLog log = captureAudit();
        assertThat(log.getStatus()).isEqualTo("FAILED");
        assertThat(log.getConnectorType()).isEqualTo("cfg-1");
        assertThat(log.getIdempotencyKey()).isEqualTo("idem-1");
        assertThat(log.getErrorMessage()).contains("连接器配置不存在或未启用");
    }

    @Test
    @DisplayName("成功：审计记录真实连接器类型与成功状态")
    void successAuditsConnectorType() {
        ConnectorDefinition definition = definition("HTTP");
        when(configRepository.findActive("t1", "cfg-1")).thenReturn(Mono.just(definition));
        when(credentialResolver.resolve("t1", definition.getConfigValue())).thenReturn(Mono.just(Map.of()));
        when(executionService.execute(anyString(), any(), anyString(), any(), anyString()))
                .thenReturn(Mono.just(Map.of("ok", true)));

        StepVerifier.create(service.execute("t1", "cfg-1", "send", Map.of("a", 1), "idem-1"))
                .assertNext(result -> assertThat(result).containsEntry("ok", true))
                .verifyComplete();

        OutboundCallLog log = captureAudit();
        assertThat(log.getStatus()).isEqualTo("SUCCESS");
        assertThat(log.getConnectorType()).isEqualTo("HTTP");
        assertThat(log.getOperation()).isEqualTo("send");
        assertThat(log.getErrorMessage()).isNull();
        assertThat(log.getDurationMs()).isNotNegative();
    }

    @Test
    @DisplayName("成功但审计写入失败：旁路失败不影响调用结果")
    void successSurvivesAuditFailure() {
        ConnectorDefinition definition = definition("HTTP");
        when(configRepository.findActive("t1", "cfg-1")).thenReturn(Mono.just(definition));
        when(credentialResolver.resolve("t1", definition.getConfigValue())).thenReturn(Mono.just(Map.of()));
        when(executionService.execute(anyString(), any(), anyString(), any(), any()))
                .thenReturn(Mono.just(Map.of("ok", true)));
        when(auditRepository.insert(any(OutboundCallLog.class)))
                .thenReturn(Mono.error(new IllegalStateException("audit unavailable")));

        StepVerifier.create(service.execute("t1", "cfg-1", "send", Map.of(), null))
                .assertNext(result -> assertThat(result).containsEntry("ok", true))
                .verifyComplete();
    }

    @Test
    @DisplayName("调用失败：落失败审计并透传原始错误")
    void failureAuditsAndPropagates() {
        ConnectorDefinition definition = definition("HTTP");
        when(configRepository.findActive("t1", "cfg-1")).thenReturn(Mono.just(definition));
        when(credentialResolver.resolve("t1", definition.getConfigValue())).thenReturn(Mono.just(Map.of()));
        when(executionService.execute(anyString(), any(), anyString(), any(), any()))
                .thenReturn(Mono.error(new IllegalStateException("远端超时")));

        StepVerifier.create(service.execute("t1", "cfg-1", "send", Map.of(), null))
                .expectErrorMessage("远端超时")
                .verify();

        OutboundCallLog log = captureAudit();
        assertThat(log.getStatus()).isEqualTo("FAILED");
        assertThat(log.getErrorMessage()).isEqualTo("远端超时");
    }

    @Test
    @DisplayName("失败且错误信息超长：入库前截断到 1000 字符")
    void failureTruncatesErrorMessage() {
        ConnectorDefinition definition = definition("HTTP");
        when(configRepository.findActive("t1", "cfg-1")).thenReturn(Mono.just(definition));
        when(credentialResolver.resolve("t1", definition.getConfigValue())).thenReturn(Mono.just(Map.of()));
        when(executionService.execute(anyString(), any(), anyString(), any(), any()))
                .thenReturn(Mono.error(new IllegalStateException("x".repeat(1500))));

        StepVerifier.create(service.execute("t1", "cfg-1", "send", Map.of(), null))
                .expectError(IllegalStateException.class)
                .verify();

        assertThat(captureAudit().getErrorMessage()).hasSize(1000);
    }

    @Test
    @DisplayName("失败且无错误信息：不落错误文本")
    void failureWithoutMessageKeepsNull() {
        ConnectorDefinition definition = definition("HTTP");
        when(configRepository.findActive("t1", "cfg-1")).thenReturn(Mono.just(definition));
        when(credentialResolver.resolve("t1", definition.getConfigValue())).thenReturn(Mono.just(Map.of()));
        when(executionService.execute(anyString(), any(), anyString(), any(), any()))
                .thenReturn(Mono.error(new IllegalStateException()));

        StepVerifier.create(service.execute("t1", "cfg-1", "send", Map.of(), null))
                .expectError(IllegalStateException.class)
                .verify();

        assertThat(captureAudit().getErrorMessage()).isNull();
    }

    @Test
    @DisplayName("失败且审计写入失败：仍透传原始错误")
    void failureSurvivesAuditFailure() {
        ConnectorDefinition definition = definition("HTTP");
        when(configRepository.findActive("t1", "cfg-1")).thenReturn(Mono.just(definition));
        when(credentialResolver.resolve("t1", definition.getConfigValue())).thenReturn(Mono.just(Map.of()));
        when(executionService.execute(anyString(), any(), anyString(), any(), any()))
                .thenReturn(Mono.error(new IllegalStateException("远端超时")));
        when(auditRepository.insert(any(OutboundCallLog.class)))
                .thenReturn(Mono.error(new IllegalStateException("audit unavailable")));

        StepVerifier.create(service.execute("t1", "cfg-1", "send", Map.of(), null))
                .expectErrorMessage("远端超时")
                .verify();
    }

    private OutboundCallLog captureAudit() {
        ArgumentCaptor<OutboundCallLog> captor = ArgumentCaptor.forClass(OutboundCallLog.class);
        verify(auditRepository).insert(captor.capture());
        return captor.getValue();
    }

    private ConnectorDefinition definition(String connectorType) {
        ConnectorDefinition definition = new ConnectorDefinition();
        definition.setTenantId("t1");
        definition.setConnectorType(connectorType);
        definition.setConfigKey("cfg-1");
        definition.setConfigValue("{}");
        definition.setStatus(1);
        return definition;
    }
}
