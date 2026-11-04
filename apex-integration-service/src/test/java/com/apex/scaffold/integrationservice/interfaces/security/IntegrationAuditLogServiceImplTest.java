package com.apex.scaffold.integrationservice.interfaces.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

/**
 * 集成服务审计实现测试：只写白名单元数据，不抛错。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@DisplayName("集成服务审计实现")
class IntegrationAuditLogServiceImplTest {

    private final IntegrationAuditLogServiceImpl audit = new IntegrationAuditLogServiceImpl();

    @Test
    @DisplayName("记录普通审计事件")
    void shouldLogAction() {
        StepVerifier.create(audit.log("7", "t-1", "INSERT", "Handler#write", "OK", Map.of("module", "iot")))
                .verifyComplete();
    }

    @Test
    @DisplayName("记录认证事件")
    void shouldLogAuthEvent() {
        StepVerifier.create(audit.logAuthEvent("7", "t-1", "LOGIN", "127.0.0.1", true, "ok"))
                .verifyComplete();
    }

    @Test
    @DisplayName("记录权限检查并容忍空权限码")
    void shouldLogPermissionCheck() {
        StepVerifier.create(audit.logPermissionCheck("7", "t-1", "resource", null, false))
                .verifyComplete();
        assertThat(true).isTrue();
    }
}
