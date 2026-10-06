package com.apex.scaffold.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.apex.scaffold.application.assembler.SysSsoProviderAssembler;
import com.apex.scaffold.application.assembler.SysSsoSessionAssembler;
import com.apex.scaffold.application.dto.admin.SsoProviderDTO;
import com.apex.scaffold.application.support.sso.SsoProtocolAdapter;
import com.apex.scaffold.application.support.sso.SsoProtocolAdapterFactory;
import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.domain.model.entity.SysSsoProviderEntity;
import com.apex.scaffold.domain.model.entity.SysSsoSessionEntity;
import com.apex.scaffold.domain.model.sso.SsoProtocolType;
import com.apex.scaffold.domain.model.sso.SsoSessionStatus;
import com.apex.scaffold.domain.repository.SysSsoProviderRepository;
import com.apex.scaffold.domain.repository.SysSsoSessionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class SsoServiceCredentialSafetyTest {
    private static final String SECRET = "private-sso-configuration";
    private final SysSsoProviderRepository providers = mock(SysSsoProviderRepository.class);
    private final SysSsoSessionRepository sessions = mock(SysSsoSessionRepository.class);
    private final SsoProtocolAdapterFactory factory = mock(SsoProtocolAdapterFactory.class);
    private final SsoProtocolAdapter adapter = mock(SsoProtocolAdapter.class);
    private final SysSsoProviderService providerService =
            new SysSsoProviderService(providers, mock(SysSsoProviderAssembler.class), factory, new ObjectMapper());
    private final SysSsoSessionService sessionService = new SysSsoSessionService(
            sessions, providers, mock(SysSsoSessionAssembler.class), factory, new ObjectMapper());
    private SysSsoProviderEntity provider;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId("1");
        provider = new SysSsoProviderEntity();
        provider.setTenantId("1");
        provider.setProviderType(SsoProtocolType.OIDC);
        provider.setConfig("{}");
        when(providers.findById(1L)).thenReturn(Mono.just(provider));
        when(factory.getAdapter("OIDC")).thenReturn(adapter);
        SysSsoSessionEntity session = new SysSsoSessionEntity();
        session.setTenantId("1");
        session.setProviderId(1L);
        session.setExternalId("external-session");
        session.setStatus(SsoSessionStatus.ACTIVE);
        when(sessions.findById(2L)).thenReturn(Mono.just(session));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @ParameterizedTest
    @ValueSource(strings = {"connection", "metadata", "revoke", "build"})
    void malformedConfigurationIsExcludedFromLogs(String operation) {
        provider.setConfig("{\"clientSecret\":\"" + SECRET + "\", invalid}");
        Class<?> serviceClass = operation.equals("revoke") ? SysSsoSessionService.class : SysSsoProviderService.class;
        Logger logger = (Logger) LoggerFactory.getLogger(serviceClass);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            if (operation.equals("connection")) {
                StepVerifier.create(providerService.testConnection(1L))
                        .assertNext(result -> assertThat(result.toString()).doesNotContain(SECRET))
                        .verifyComplete();
            } else if (operation.equals("build")) {
                SsoProviderDTO dto = SsoProviderDTO.builder()
                        .id(1L)
                        .configJson(provider.getConfig())
                        .build();
                assertThatThrownBy(() -> providerService.buildProviderConfig(dto))
                        .isInstanceOf(BusinessException.class)
                        .hasMessageNotContaining(SECRET);
            } else {
                Mono<?> result = operation.equals("metadata")
                        ? providerService.getMetadata(1L)
                        : sessionService.revokeSession(2L);
                StepVerifier.create(result)
                        .expectErrorSatisfies(error -> {
                            assertThat(error.getMessage()).doesNotContain(SECRET);
                            assertThat(error.getCause()).isNull();
                        })
                        .verify();
            }
            assertThat(appender.list).isNotEmpty().allSatisfy(event -> {
                assertThat(event.getFormattedMessage()).doesNotContain(SECRET);
                assertThat(event.getThrowableProxy()).isNull();
            });
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "connection-sync",
                "connection-async",
                "metadata-sync",
                "metadata-async",
                "revoke-sync",
                "revoke-async"
            })
    void adapterFailuresDoNotEscapeServiceBoundary(String operation) {
        RuntimeException failure = new IllegalArgumentException(SECRET, new RuntimeException(SECRET));
        boolean synchronous = operation.endsWith("sync") && !operation.endsWith("async");
        if (operation.startsWith("connection")) {
            if (synchronous) {
                when(adapter.testConnection(Map.of())).thenThrow(failure);
            } else {
                when(adapter.testConnection(Map.of())).thenReturn(Mono.error(failure));
            }
            StepVerifier.create(providerService.testConnection(1L))
                    .assertNext(result -> {
                        assertThat(result).containsEntry("success", false);
                        assertThat(result.toString()).doesNotContain(SECRET);
                    })
                    .verifyComplete();
        } else {
            if (operation.startsWith("metadata")) {
                if (synchronous) {
                    when(adapter.getMetadata(Map.of())).thenThrow(failure);
                } else {
                    when(adapter.getMetadata(Map.of())).thenReturn(Mono.error(failure));
                }
            } else if (synchronous) {
                when(adapter.logout(Map.of(), "external-session")).thenThrow(failure);
            } else {
                when(adapter.logout(Map.of(), "external-session")).thenReturn(Mono.error(failure));
            }
            Mono<?> result = operation.startsWith("metadata")
                    ? providerService.getMetadata(1L)
                    : sessionService.revokeSession(2L);
            StepVerifier.create(result)
                    .expectErrorSatisfies(error -> {
                        assertThat(error).isInstanceOf(BusinessException.class);
                        assertThat(error.getMessage()).doesNotContain(SECRET);
                        assertThat(error.getCause()).isNull();
                    })
                    .verify();
        }
    }

    @Test
    void validConfigurationRemainsAvailableToProtocol() {
        SsoProviderDTO dto = SsoProviderDTO.builder()
                .id(1L)
                .configJson("{\"clientSecret\":\"" + SECRET + "\"}")
                .build();
        assertThat(providerService.buildProviderConfig(dto)).containsEntry("clientSecret", SECRET);
    }
}
