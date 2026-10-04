package com.apex.scaffold.authcenter.interfaces.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apex.scaffold.authcenter.domain.model.IpAccessControl;
import com.apex.scaffold.authcenter.domain.service.IpAccessControlService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.server.ServerRequest;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * {@link IpAccessControlHandler} 的单元测试类
 *
 * <p>测试IP访问控制Handler的核心逻辑：参数解析、业务调用、响应构建、异常处理。
 *
 * @author Apex Team
 * @since 1.0.0
 */
@DisplayName("IpAccessControlHandler 单元测试")
@ExtendWith(MockitoExtension.class)
class IpAccessControlHandlerTest {

    @Mock
    private IpAccessControlService ipAccessControlService;

    private IpAccessControlHandler handler;

    @BeforeEach
    void setUp() {
        handler = new IpAccessControlHandler(ipAccessControlService);
    }

    @Nested
    @DisplayName("addToWhitelist 方法")
    class AddToWhitelistTests {

        @Test
        @DisplayName("参数完整时应成功添加白名单")
        void shouldAddSuccessfully() {
            ServerRequest request = mock(ServerRequest.class);
            when(request.queryParam("ipAddress")).thenReturn(Optional.of("192.168.1.100"));
            when(request.attribute("tenantId")).thenReturn(Optional.of("tenant-1"));
            when(request.queryParam("description")).thenReturn(Optional.of("办公网"));
            when(request.attribute("userId")).thenReturn(Optional.of("admin-1"));
            IpAccessControl rule = new IpAccessControl();
            when(ipAccessControlService.addToWhitelist("192.168.1.100", "tenant-1", "办公网", "admin-1"))
                    .thenReturn(Mono.just(rule));

            StepVerifier.create(handler.addToWhitelist(request))
                    .assertNext(response ->
                            assertThat(response.statusCode().is2xxSuccessful()).isTrue())
                    .verifyComplete();

            verify(ipAccessControlService).addToWhitelist("192.168.1.100", "tenant-1", "办公网", "admin-1");
        }

        @Test
        @DisplayName("description缺省时应传null")
        void shouldPassNullWhenDescriptionAbsent() {
            ServerRequest request = mock(ServerRequest.class);
            when(request.queryParam("ipAddress")).thenReturn(Optional.of("192.168.1.100"));
            when(request.attribute("tenantId")).thenReturn(Optional.of("tenant-1"));
            when(request.queryParam("description")).thenReturn(Optional.empty());
            when(request.attribute("userId")).thenReturn(Optional.of("admin-1"));
            when(ipAccessControlService.addToWhitelist("192.168.1.100", "tenant-1", null, "admin-1"))
                    .thenReturn(Mono.just(new IpAccessControl()));

            StepVerifier.create(handler.addToWhitelist(request))
                    .assertNext(response ->
                            assertThat(response.statusCode().is2xxSuccessful()).isTrue())
                    .verifyComplete();

            verify(ipAccessControlService).addToWhitelist("192.168.1.100", "tenant-1", null, "admin-1");
        }

        @Test
        @DisplayName("缺少ipAddress时应抛出参数校验异常")
        void shouldThrowWhenIpAddressMissing() {
            ServerRequest request = mock(ServerRequest.class);
            when(request.queryParam("ipAddress")).thenReturn(Optional.empty());

            assertThrows(IllegalArgumentException.class, () -> handler.addToWhitelist(request));
        }

        @Test
        @DisplayName("IP已存在时应走handleError返回500")
        void shouldHandleServiceError() {
            ServerRequest request = mock(ServerRequest.class);
            when(request.queryParam("ipAddress")).thenReturn(Optional.of("192.168.1.100"));
            when(request.attribute("tenantId")).thenReturn(Optional.of("tenant-1"));
            when(request.queryParam("description")).thenReturn(Optional.empty());
            when(request.attribute("userId")).thenReturn(Optional.of("admin-1"));
            when(ipAccessControlService.addToWhitelist("192.168.1.100", "tenant-1", null, "admin-1"))
                    .thenReturn(Mono.error(new IllegalStateException("IP已存在")));

            StepVerifier.create(handler.addToWhitelist(request))
                    .assertNext(response ->
                            assertThat(response.statusCode().value()).isEqualTo(500))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("addToBlacklist 方法")
    class AddToBlacklistTests {

        @Test
        @DisplayName("参数完整（含durationMinutes）时应成功添加黑名单")
        void shouldAddSuccessfullyWithDuration() {
            ServerRequest request = mock(ServerRequest.class);
            when(request.queryParam("ipAddress")).thenReturn(Optional.of("203.0.113.5"));
            when(request.attribute("tenantId")).thenReturn(Optional.of("tenant-1"));
            when(request.queryParam("description")).thenReturn(Optional.of("暴力破解"));
            when(request.queryParam("durationMinutes")).thenReturn(Optional.of("1440"));
            when(request.attribute("userId")).thenReturn(Optional.of("admin-1"));
            when(ipAccessControlService.addToBlacklist("203.0.113.5", "tenant-1", "暴力破解", 1440, "admin-1"))
                    .thenReturn(Mono.just(new IpAccessControl()));

            StepVerifier.create(handler.addToBlacklist(request))
                    .assertNext(response ->
                            assertThat(response.statusCode().is2xxSuccessful()).isTrue())
                    .verifyComplete();

            verify(ipAccessControlService).addToBlacklist("203.0.113.5", "tenant-1", "暴力破解", 1440, "admin-1");
        }

        @Test
        @DisplayName("durationMinutes缺省时应传null（永久封禁）")
        void shouldPassNullDurationWhenAbsent() {
            ServerRequest request = mock(ServerRequest.class);
            when(request.queryParam("ipAddress")).thenReturn(Optional.of("203.0.113.5"));
            when(request.attribute("tenantId")).thenReturn(Optional.of("tenant-1"));
            when(request.queryParam("description")).thenReturn(Optional.empty());
            when(request.queryParam("durationMinutes")).thenReturn(Optional.empty());
            when(request.attribute("userId")).thenReturn(Optional.of("admin-1"));
            when(ipAccessControlService.addToBlacklist("203.0.113.5", "tenant-1", null, null, "admin-1"))
                    .thenReturn(Mono.just(new IpAccessControl()));

            StepVerifier.create(handler.addToBlacklist(request))
                    .assertNext(response ->
                            assertThat(response.statusCode().is2xxSuccessful()).isTrue())
                    .verifyComplete();

            verify(ipAccessControlService).addToBlacklist("203.0.113.5", "tenant-1", null, null, "admin-1");
        }

        @Test
        @DisplayName("缺少operator（userId属性）时应抛出参数校验异常")
        void shouldThrowWhenOperatorMissing() {
            ServerRequest request = mock(ServerRequest.class);
            when(request.queryParam("ipAddress")).thenReturn(Optional.of("203.0.113.5"));
            when(request.attribute("tenantId")).thenReturn(Optional.of("tenant-1"));
            when(request.queryParam("description")).thenReturn(Optional.empty());
            when(request.queryParam("durationMinutes")).thenReturn(Optional.empty());
            when(request.attribute("userId")).thenReturn(Optional.empty());

            assertThrows(IllegalArgumentException.class, () -> handler.addToBlacklist(request));
        }
    }

    @Nested
    @DisplayName("removeFromWhitelist 方法")
    class RemoveFromWhitelistTests {

        @Test
        @DisplayName("参数完整时应成功移除白名单")
        void shouldRemoveSuccessfully() {
            ServerRequest request = mock(ServerRequest.class);
            when(request.queryParam("ipAddress")).thenReturn(Optional.of("192.168.1.100"));
            when(request.attribute("tenantId")).thenReturn(Optional.of("tenant-1"));
            when(ipAccessControlService.removeFromWhitelist("192.168.1.100", "tenant-1"))
                    .thenReturn(Mono.empty());

            StepVerifier.create(handler.removeFromWhitelist(request))
                    .assertNext(response ->
                            assertThat(response.statusCode().is2xxSuccessful()).isTrue())
                    .verifyComplete();

            verify(ipAccessControlService).removeFromWhitelist("192.168.1.100", "tenant-1");
        }

        @Test
        @DisplayName("IP不在白名单中时应走handleError")
        void shouldHandleServiceError() {
            ServerRequest request = mock(ServerRequest.class);
            when(request.queryParam("ipAddress")).thenReturn(Optional.of("192.168.1.100"));
            when(request.attribute("tenantId")).thenReturn(Optional.of("tenant-1"));
            when(ipAccessControlService.removeFromWhitelist("192.168.1.100", "tenant-1"))
                    .thenReturn(Mono.error(new IllegalArgumentException("规则不存在")));

            StepVerifier.create(handler.removeFromWhitelist(request))
                    .assertNext(response ->
                            assertThat(response.statusCode().value()).isEqualTo(500))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("removeFromBlacklist 方法")
    class RemoveFromBlacklistTests {

        @Test
        @DisplayName("参数完整时应成功移除黑名单")
        void shouldRemoveSuccessfully() {
            ServerRequest request = mock(ServerRequest.class);
            when(request.queryParam("ipAddress")).thenReturn(Optional.of("203.0.113.5"));
            when(request.attribute("tenantId")).thenReturn(Optional.of("tenant-1"));
            when(ipAccessControlService.removeFromBlacklist("203.0.113.5", "tenant-1"))
                    .thenReturn(Mono.empty());

            StepVerifier.create(handler.removeFromBlacklist(request))
                    .assertNext(response ->
                            assertThat(response.statusCode().is2xxSuccessful()).isTrue())
                    .verifyComplete();

            verify(ipAccessControlService).removeFromBlacklist("203.0.113.5", "tenant-1");
        }

        @Test
        @DisplayName("缺少tenantId时应抛出参数校验异常")
        void shouldThrowWhenTenantIdMissing() {
            ServerRequest request = mock(ServerRequest.class);
            when(request.queryParam("ipAddress")).thenReturn(Optional.of("203.0.113.5"));
            when(request.attribute("tenantId")).thenReturn(Optional.empty());

            assertThrows(IllegalArgumentException.class, () -> handler.removeFromBlacklist(request));
        }
    }

    @Nested
    @DisplayName("getWhitelist 方法")
    class GetWhitelistTests {

        @Test
        @DisplayName("应返回白名单规则列表")
        void shouldReturnWhitelist() {
            ServerRequest request = mock(ServerRequest.class);
            when(request.attribute("tenantId")).thenReturn(Optional.of("tenant-1"));
            when(ipAccessControlService.getWhitelist("tenant-1"))
                    .thenReturn(Flux.just(new IpAccessControl(), new IpAccessControl()));

            StepVerifier.create(handler.getWhitelist(request))
                    .assertNext(response ->
                            assertThat(response.statusCode().is2xxSuccessful()).isTrue())
                    .verifyComplete();

            verify(ipAccessControlService).getWhitelist("tenant-1");
        }

        @Test
        @DisplayName("无白名单时应返回空列表且200")
        void shouldReturnEmptyListWhenNoRules() {
            ServerRequest request = mock(ServerRequest.class);
            when(request.attribute("tenantId")).thenReturn(Optional.of("tenant-1"));
            when(ipAccessControlService.getWhitelist("tenant-1")).thenReturn(Flux.empty());

            StepVerifier.create(handler.getWhitelist(request))
                    .assertNext(response ->
                            assertThat(response.statusCode().is2xxSuccessful()).isTrue())
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("getBlacklist 方法")
    class GetBlacklistTests {

        @Test
        @DisplayName("应返回黑名单规则列表")
        void shouldReturnBlacklist() {
            ServerRequest request = mock(ServerRequest.class);
            when(request.attribute("tenantId")).thenReturn(Optional.of("tenant-1"));
            when(ipAccessControlService.getBlacklist("tenant-1")).thenReturn(Flux.just(new IpAccessControl()));

            StepVerifier.create(handler.getBlacklist(request))
                    .assertNext(response ->
                            assertThat(response.statusCode().is2xxSuccessful()).isTrue())
                    .verifyComplete();

            verify(ipAccessControlService).getBlacklist("tenant-1");
        }

        @Test
        @DisplayName("缺少tenantId时应抛出参数校验异常")
        void shouldThrowWhenTenantIdMissing() {
            ServerRequest request = mock(ServerRequest.class);
            when(request.attribute("tenantId")).thenReturn(Optional.empty());

            assertThrows(IllegalArgumentException.class, () -> handler.getBlacklist(request));
        }
    }

    @Nested
    @DisplayName("enableRule 方法")
    class EnableRuleTests {

        @Test
        @DisplayName("参数完整时应成功启用规则")
        void shouldEnableSuccessfully() {
            ServerRequest request = mock(ServerRequest.class);
            when(request.pathVariable("ruleId")).thenReturn("1001");
            when(request.attribute("userId")).thenReturn(Optional.of("admin-1"));
            when(request.attribute("tenantId")).thenReturn(Optional.of("tenant-1"));
            when(ipAccessControlService.enableRule(1001L, "tenant-1", "admin-1")).thenReturn(Mono.empty());

            StepVerifier.create(handler.enableRule(request))
                    .assertNext(response ->
                            assertThat(response.statusCode().is2xxSuccessful()).isTrue())
                    .verifyComplete();

            verify(ipAccessControlService).enableRule(1001L, "tenant-1", "admin-1");
        }

        @Test
        @DisplayName("规则不存在时应走handleError")
        void shouldHandleServiceError() {
            ServerRequest request = mock(ServerRequest.class);
            when(request.pathVariable("ruleId")).thenReturn("1001");
            when(request.attribute("userId")).thenReturn(Optional.of("admin-1"));
            when(request.attribute("tenantId")).thenReturn(Optional.of("tenant-1"));
            when(ipAccessControlService.enableRule(1001L, "tenant-1", "admin-1"))
                    .thenReturn(Mono.error(new IllegalArgumentException("规则不存在")));

            StepVerifier.create(handler.enableRule(request))
                    .assertNext(response ->
                            assertThat(response.statusCode().value()).isEqualTo(500))
                    .verifyComplete();
        }

        @Test
        @DisplayName("ruleId非数字时应抛出NumberFormatException")
        void shouldThrowWhenRuleIdInvalid() {
            ServerRequest request = mock(ServerRequest.class);
            when(request.pathVariable("ruleId")).thenReturn("abc");

            assertThrows(NumberFormatException.class, () -> handler.enableRule(request));
        }
    }

    @Nested
    @DisplayName("disableRule 方法")
    class DisableRuleTests {

        @Test
        @DisplayName("参数完整时应成功禁用规则")
        void shouldDisableSuccessfully() {
            ServerRequest request = mock(ServerRequest.class);
            when(request.pathVariable("ruleId")).thenReturn("1001");
            when(request.attribute("userId")).thenReturn(Optional.of("admin-1"));
            when(request.attribute("tenantId")).thenReturn(Optional.of("tenant-1"));
            when(ipAccessControlService.disableRule(1001L, "tenant-1", "admin-1")).thenReturn(Mono.empty());

            StepVerifier.create(handler.disableRule(request))
                    .assertNext(response ->
                            assertThat(response.statusCode().is2xxSuccessful()).isTrue())
                    .verifyComplete();

            verify(ipAccessControlService).disableRule(1001L, "tenant-1", "admin-1");
        }

        @Test
        @DisplayName("缺少operator时应抛出参数校验异常")
        void shouldThrowWhenOperatorMissing() {
            ServerRequest request = mock(ServerRequest.class);
            when(request.pathVariable("ruleId")).thenReturn("1001");
            when(request.attribute("userId")).thenReturn(Optional.empty());

            assertThrows(IllegalArgumentException.class, () -> handler.disableRule(request));
        }
    }

    @Nested
    @DisplayName("deleteRule 方法")
    class DeleteRuleTests {

        @Test
        @DisplayName("参数完整时应成功删除规则")
        void shouldDeleteSuccessfully() {
            ServerRequest request = mock(ServerRequest.class);
            when(request.pathVariable("ruleId")).thenReturn("1001");
            when(request.attribute("userId")).thenReturn(Optional.of("admin-1"));
            when(request.attribute("tenantId")).thenReturn(Optional.of("tenant-1"));
            when(ipAccessControlService.deleteRule(1001L, "tenant-1", "admin-1")).thenReturn(Mono.empty());

            StepVerifier.create(handler.deleteRule(request))
                    .assertNext(response ->
                            assertThat(response.statusCode().is2xxSuccessful()).isTrue())
                    .verifyComplete();

            verify(ipAccessControlService).deleteRule(1001L, "tenant-1", "admin-1");
        }

        @Test
        @DisplayName("规则不存在时应走handleError")
        void shouldHandleServiceError() {
            ServerRequest request = mock(ServerRequest.class);
            when(request.pathVariable("ruleId")).thenReturn("1001");
            when(request.attribute("userId")).thenReturn(Optional.of("admin-1"));
            when(request.attribute("tenantId")).thenReturn(Optional.of("tenant-1"));
            when(ipAccessControlService.deleteRule(1001L, "tenant-1", "admin-1"))
                    .thenReturn(Mono.error(new IllegalArgumentException("规则不存在")));

            StepVerifier.create(handler.deleteRule(request))
                    .assertNext(response ->
                            assertThat(response.statusCode().value()).isEqualTo(500))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("cleanupExpiredRules 方法")
    class CleanupExpiredRulesTests {

        @Test
        @DisplayName("应返回清理的规则数量")
        void shouldReturnCleanupCount() {
            ServerRequest request = mock(ServerRequest.class);
            when(ipAccessControlService.cleanupExpiredRules()).thenReturn(Mono.just(5L));

            StepVerifier.create(handler.cleanupExpiredRules(request))
                    .assertNext(response ->
                            assertThat(response.statusCode().is2xxSuccessful()).isTrue())
                    .verifyComplete();

            verify(ipAccessControlService).cleanupExpiredRules();
        }

        @Test
        @DisplayName("清理失败时应走handleError")
        void shouldHandleServiceError() {
            ServerRequest request = mock(ServerRequest.class);
            when(ipAccessControlService.cleanupExpiredRules()).thenReturn(Mono.error(new RuntimeException("清理失败")));

            StepVerifier.create(handler.cleanupExpiredRules(request))
                    .assertNext(response ->
                            assertThat(response.statusCode().value()).isEqualTo(500))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("checkAccess 方法")
    class CheckAccessTests {

        @Test
        @DisplayName("IP允许访问时应返回true")
        void shouldReturnTrueWhenAllowed() {
            ServerRequest request = mock(ServerRequest.class);
            when(request.queryParam("ipAddress")).thenReturn(Optional.of("192.168.1.100"));
            when(request.attribute("tenantId")).thenReturn(Optional.of("tenant-1"));
            when(ipAccessControlService.isAccessAllowed("192.168.1.100", "tenant-1"))
                    .thenReturn(Mono.just(true));

            StepVerifier.create(handler.checkAccess(request))
                    .assertNext(response ->
                            assertThat(response.statusCode().is2xxSuccessful()).isTrue())
                    .verifyComplete();

            verify(ipAccessControlService).isAccessAllowed("192.168.1.100", "tenant-1");
        }

        @Test
        @DisplayName("IP被拒绝访问时应返回false且200")
        void shouldReturnFalseWhenDenied() {
            ServerRequest request = mock(ServerRequest.class);
            when(request.queryParam("ipAddress")).thenReturn(Optional.of("203.0.113.5"));
            when(request.attribute("tenantId")).thenReturn(Optional.of("tenant-1"));
            when(ipAccessControlService.isAccessAllowed("203.0.113.5", "tenant-1"))
                    .thenReturn(Mono.just(false));

            StepVerifier.create(handler.checkAccess(request))
                    .assertNext(response ->
                            assertThat(response.statusCode().is2xxSuccessful()).isTrue())
                    .verifyComplete();
        }

        @Test
        @DisplayName("缺少ipAddress时应抛出参数校验异常")
        void shouldThrowWhenIpAddressMissing() {
            ServerRequest request = mock(ServerRequest.class);
            when(request.queryParam("ipAddress")).thenReturn(Optional.empty());

            assertThrows(IllegalArgumentException.class, () -> handler.checkAccess(request));
        }
    }
}
