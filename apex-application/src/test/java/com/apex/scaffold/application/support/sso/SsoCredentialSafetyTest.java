package com.apex.scaffold.application.support.sso;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class SsoCredentialSafetyTest {

    private static final String SECRET = "private-sso-credential";
    private static final String STATE = "private-csrf-state";
    private static final String CODE = "private-auth-code";
    private static final String TICKET = "private-cas-ticket";

    @ParameterizedTest
    @ValueSource(strings = {"oidc", "cas"})
    void authenticationFailureLogsExcludeCredentials(String protocol) {
        RuntimeException failure = new IllegalStateException(SECRET, new RuntimeException(SECRET));
        WebClient.Builder builder = WebClient.builder().exchangeFunction(request -> Mono.error(failure));
        SsoProtocolAdapter adapter = protocol.equals("oidc")
                ? new OidcProtocolAdapter(builder, new ObjectMapper(), redis(true))
                : new CasProtocolAdapter(builder);
        Logger logger = (Logger) LoggerFactory.getLogger(adapter.getClass());
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        Level previous = logger.getLevel();
        logger.setLevel(Level.DEBUG);
        try {
            StepVerifier.create(adapter.authenticate(
                            Map.of("tokenEndpoint", "https://sso.test/token", "serviceValidateUrl", "/validate"),
                            Map.of("code", CODE, "state", STATE, "ticket", TICKET, "service", SECRET)))
                    .expectErrorSatisfies(error -> assertThat(error).isSameAs(failure))
                    .verify();
            assertThat(appender.list).isNotEmpty().allSatisfy(event -> {
                assertThat(event.getFormattedMessage()).doesNotContain(SECRET, STATE, CODE, TICKET);
                assertThat(event.getThrowableProxy()).isNull();
            });
        } finally {
            logger.setLevel(previous);
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void invalidStateLogsExcludeState() {
        OidcProtocolAdapter adapter = new OidcProtocolAdapter(WebClient.builder(), new ObjectMapper(), redis(false));
        Logger logger = (Logger) LoggerFactory.getLogger(OidcProtocolAdapter.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            StepVerifier.create(adapter.authenticate(Map.of(), Map.of("code", CODE, "state", STATE)))
                    .expectError(SecurityException.class)
                    .verify();
            assertThat(appender.list).isNotEmpty().allSatisfy(event -> assertThat(event.getFormattedMessage())
                    .doesNotContain(STATE, CODE));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void initiateKeepsStateInProtocolResponseAndExcludesItFromLogs() {
        ReactiveRedisTemplate<String, String> template = redis(true);
        ReactiveValueOperations<String, String> operations = mock(ReactiveValueOperations.class);
        when(template.opsForValue()).thenReturn(operations);
        when(operations.set(any(String.class), any(String.class), any(Duration.class)))
                .thenReturn(Mono.just(true));
        OidcProtocolAdapter adapter = new OidcProtocolAdapter(WebClient.builder(), new ObjectMapper(), template);
        Logger logger = (Logger) LoggerFactory.getLogger(OidcProtocolAdapter.class);
        Level previous = logger.getLevel();
        logger.setLevel(Level.DEBUG);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            StepVerifier.create(adapter.initiate(
                            Map.of("authEndpoint", "https://sso.test/authorize", "clientId", "test-client"),
                            "https://app.test/callback"))
                    .assertNext(result -> {
                        String state = (String) result.get("state");
                        assertThat(state).isNotBlank();
                        assertThat(result.get("redirectUrl").toString()).contains("state=" + state);
                        assertThat(appender.list).isNotEmpty().allSatisfy(event -> assertThat(
                                        event.getFormattedMessage())
                                .doesNotContain(state));
                    })
                    .verifyComplete();
        } finally {
            logger.setLevel(previous);
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"oidc", "cas", "discovery"})
    void diagnosticResponsesExcludeRemoteExceptionMessages(String operation) {
        WebClient.Builder builder =
                WebClient.builder().exchangeFunction(request -> Mono.error(new IllegalStateException(SECRET)));
        SsoProtocolAdapter adapter = operation.equals("cas")
                ? new CasProtocolAdapter(builder)
                : new OidcProtocolAdapter(builder, new ObjectMapper(), redis(true));
        Map<String, Object> config =
                Map.of("authEndpoint", "https://sso.test", "discoveryUrl", "https://sso.test/discovery");
        Mono<Map<String, Object>> result =
                operation.equals("discovery") ? adapter.getMetadata(config) : adapter.testConnection(config);
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.toString()).doesNotContain(SECRET);
                    if (!operation.equals("discovery")) {
                        assertThat(response).containsEntry("success", false);
                    }
                })
                .verifyComplete();
    }

    @ParameterizedTest
    @ValueSource(strings = {"oidc", "cas"})
    void malformedAuthenticationResponsesExcludeRemoteContent(String protocol) throws Exception {
        String body = protocol.equals("cas")
                ? "<cas:authenticationFailure>" + SECRET + "</cas:authenticationFailure>"
                : "{\"access_token\":\"" + SECRET + "\",\"" + SECRET + "\": invalid}";
        WebClient.Builder builder = WebClient.builder()
                .exchangeFunction(request -> Mono.just(
                        ClientResponse.create(HttpStatus.OK).body(body).build()));
        ObjectMapper mapper = mock(ObjectMapper.class);
        when(mapper.readValue(any(String.class), org.mockito.ArgumentMatchers.eq(Map.class)))
                .thenThrow(new IllegalArgumentException(SECRET));
        SsoProtocolAdapter adapter = protocol.equals("cas")
                ? new CasProtocolAdapter(builder)
                : new OidcProtocolAdapter(builder, mapper, redis(true));
        StepVerifier.create(adapter.authenticate(
                        Map.of("tokenEndpoint", "https://sso.test/token", "serviceValidateUrl", "/validate"),
                        Map.of("code", CODE, "state", STATE, "ticket", TICKET)))
                .expectErrorSatisfies(error -> {
                    assertThat(error.getMessage()).doesNotContain(SECRET);
                    assertThat(error.getCause()).isNull();
                })
                .verify();
    }

    @Test
    void ldapConnectionFailureExcludesRemoteExceptionMessage() {
        LdapProtocolAdapter adapter = new LdapProtocolAdapter();
        Map<String, Object> config =
                Map.of("ldapUrl", "invalid://" + SECRET, "adminDn", "cn=admin", "adminPassword", SECRET);
        StepVerifier.create(adapter.testConnection(config))
                .assertNext(result -> {
                    assertThat(result).containsEntry("message", "LDAP 连接失败");
                    assertThat(result.toString()).doesNotContain(SECRET);
                })
                .verifyComplete();
    }

    @Test
    void samlResponseIsExcludedFromLogs() {
        String responseXml = "<Response><Assertion><NameID>" + SECRET + "</NameID></Assertion></Response>";
        String encoded = java.util.Base64.getEncoder()
                .encodeToString(responseXml.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        SamlProtocolAdapter adapter = new SamlProtocolAdapter(new ObjectMapper(), redis(true));
        Logger logger = (Logger) LoggerFactory.getLogger(SamlProtocolAdapter.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        Level previous = logger.getLevel();
        logger.setLevel(Level.DEBUG);
        try {
            StepVerifier.create(adapter.authenticate(
                            Map.of("certificate", SECRET, "entityId", "urn:test"), Map.of("SAMLResponse", encoded)))
                    .expectError()
                    .verify();
            assertThat(appender.list)
                    .allSatisfy(event -> assertThat(event.getFormattedMessage()).doesNotContain(SECRET));
        } finally {
            logger.setLevel(previous);
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"initiate", "logout", "metadata"})
    void samlXmlFailuresExcludeSensitiveExceptionDetails(String operation) {
        SamlProtocolAdapter adapter = new SamlProtocolAdapter(new ObjectMapper(), redis(true));
        Logger logger = (Logger) LoggerFactory.getLogger(SamlProtocolAdapter.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try (org.mockito.MockedStatic<javax.xml.transform.TransformerFactory> transformer =
                org.mockito.Mockito.mockStatic(javax.xml.transform.TransformerFactory.class)) {
            transformer
                    .when(javax.xml.transform.TransformerFactory::newInstance)
                    .thenThrow(new IllegalStateException(SECRET));
            Map<String, Object> config = Map.of(
                    "entityId",
                    "urn:test",
                    "acsUrl",
                    "https://app.test/callback",
                    "authEndpoint",
                    "https://sso.test/login",
                    "logoutEndpoint",
                    "https://sso.test/logout");
            Mono<Map<String, Object>> result =
                    switch (operation) {
                        case "initiate" -> adapter.initiate(config, "https://app.test/callback");
                        case "logout" -> adapter.logout(config, SECRET);
                        default -> adapter.getMetadata(config);
                    };
            StepVerifier.create(result)
                    .expectErrorSatisfies(error -> {
                        assertThat(error.getMessage()).doesNotContain(SECRET);
                        assertThat(error.getCause()).isNull();
                    })
                    .verify();
            assertThat(appender.list).isNotEmpty().allSatisfy(event -> {
                assertThat(event.getFormattedMessage()).doesNotContain(SECRET);
                assertThat(event.getThrowableProxy()).isNull();
            });
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @SuppressWarnings("unchecked")
    private ReactiveRedisTemplate<String, String> redis(boolean valid) {
        ReactiveRedisTemplate<String, String> template = mock(ReactiveRedisTemplate.class);
        when(template.hasKey(any(String.class))).thenReturn(Mono.just(valid));
        when(template.delete(any(String.class))).thenReturn(Mono.just(1L));
        return template;
    }
}
