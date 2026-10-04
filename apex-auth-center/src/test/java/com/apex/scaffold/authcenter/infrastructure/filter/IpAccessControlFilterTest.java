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
    @DisplayName("拒绝访问时审计失败应使过滤链失败")
    void filter_deniedAuditFailure_failsChain() {
        IpAccessControlService ipAccessControlService = mock(IpAccessControlService.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        WebFilterChain chain = mock(WebFilterChain.class);
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/users")
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
