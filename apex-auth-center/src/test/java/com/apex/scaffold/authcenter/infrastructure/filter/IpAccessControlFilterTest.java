package com.apex.scaffold.authcenter.infrastructure.filter;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.apex.scaffold.authcenter.domain.service.IpAccessControlService;
import com.apex.scaffold.common.audit.AuditLogService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@DisplayName("IpAccessControlFilter 单元测试")
class IpAccessControlFilterTest {
    @Test
    void shouldKeepFailureLogsSafeAndSingle() {
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(IpAccessControlFilter.class);
        var originalLevel = logger.getLevel();
        var appender = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start();
        logger.setLevel(ch.qos.logback.classic.Level.DEBUG);
        logger.addAppender(appender);
        try {
            var service = mock(IpAccessControlService.class);
            var audit = mock(AuditLogService.class);
            var chain = mock(WebFilterChain.class);
            var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/users?token=log-secret")
                    .header("X-Tenant-Id", "tenant1")
                    .build());
            when(service.isAccessAllowed(anyString(), anyString()))
                    .thenReturn(Mono.error(new IllegalStateException("token=log-secret")));
            when(audit.log(anyString(), anyString(), anyString(), anyString(), anyString(), any()))
                    .thenReturn(Mono.empty());
            StepVerifier.create(new IpAccessControlFilter(service, audit).filter(exchange, chain))
                    .verifyComplete();
            org.assertj.core.api.Assertions.assertThat(
                            exchange.getResponse().getStatusCode().value())
                    .isEqualTo(403);
            org.assertj.core.api.Assertions.assertThat(appender.list).hasSize(1);
            org.assertj.core.api.Assertions.assertThat(appender.list.get(0).getFormattedMessage())
                    .doesNotContain("log-secret");
            org.assertj.core.api.Assertions.assertThat(appender.list.get(0).getThrowableProxy())
                    .isNull();
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(originalLevel);
            appender.stop();
        }
    }

    @Test
    @DisplayName("拒绝访问时审计失败应使过滤链失败")
    void filter_deniedAuditFailure_failsChain() {
        IpAccessControlService ipAccessControlService = mock(IpAccessControlService.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        WebFilterChain chain = mock(WebFilterChain.class);
        ServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/users")
                .header("X-Tenant-Id", "tenant1")
                .remoteAddress(new java.net.InetSocketAddress("127.0.0.1", 8080))
                .build());

        when(ipAccessControlService.isAccessAllowed(anyString(), anyString())).thenReturn(Mono.just(false));
        when(auditLogService.log(anyString(), anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(Mono.error(new IllegalStateException("审计服务不可用")));

        IpAccessControlFilter filter = new IpAccessControlFilter(ipAccessControlService, auditLogService);

        StepVerifier.create(filter.filter(exchange, chain))
                .expectErrorMessage("审计服务不可用")
                .verify();
    }
}
