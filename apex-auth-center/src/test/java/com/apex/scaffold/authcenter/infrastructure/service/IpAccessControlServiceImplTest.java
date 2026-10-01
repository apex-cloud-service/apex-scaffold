package com.apex.scaffold.authcenter.infrastructure.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.apex.scaffold.authcenter.domain.model.IpAccessControl;
import com.apex.scaffold.authcenter.infrastructure.repository.IpAccessControlRepository;
import com.apex.scaffold.common.audit.AuditLogService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * IpAccessControlServiceImpl单元测试
 *
 * @author Apex Team
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("IP访问控制服务测试")
class IpAccessControlServiceImplTest {

    @Mock
    private IpAccessControlRepository repository;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private IpAccessControlServiceImpl ipAccessControlService;

    private final String testIp = "192.168.1.100";
    private final String tenantId = "tenant-001";
    private final String operator = "admin";

    private IpAccessControl whitelistRule;
    private IpAccessControl blacklistRule;

    @BeforeEach
    void setUp() {
        whitelistRule = IpAccessControl.builder()
                .id(1L)
                .tenantId(tenantId)
                .ipAddress("192.168.1.*")
                .type(IpAccessControl.AccessControlType.WHITELIST)
                .description("内网白名单")
                .enabled(true)
                .priority(10)
                .createdAt(LocalDateTime.now())
                .build();

        blacklistRule = IpAccessControl.builder()
                .id(2L)
                .tenantId(tenantId)
                .ipAddress("10.0.0.*")
                .type(IpAccessControl.AccessControlType.BLACKLIST)
                .description("测试黑名单")
                .enabled(true)
                .priority(5)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("检查IP访问权限 - 参数为空返回false")
    void isAccessAllowed_NullParams_ReturnsFalse() {
        StepVerifier.create(ipAccessControlService.isAccessAllowed(null, tenantId))
                .expectNext(false)
                .verifyComplete();

        StepVerifier.create(ipAccessControlService.isAccessAllowed(testIp, null))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    @DisplayName("检查IP访问权限 - 在黑名单中被拒绝")
    void isAccessAllowed_InBlacklist_Denied() {
        // Given
        when(repository.findByTenantIdAndType(tenantId, IpAccessControl.AccessControlType.BLACKLIST))
                .thenReturn(Flux.just(blacklistRule));
        when(repository.findByTenantIdAndType(tenantId, IpAccessControl.AccessControlType.WHITELIST))
                .thenReturn(Flux.empty());

        // When & Then
        StepVerifier.create(ipAccessControlService.isAccessAllowed("10.0.0.100", tenantId))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    @DisplayName("检查IP访问权限 - 在白名单中允许")
    void isAccessAllowed_InWhitelist_Allowed() {
        // Given
        when(repository.findByTenantIdAndType(tenantId, IpAccessControl.AccessControlType.BLACKLIST))
                .thenReturn(Flux.empty());
        when(repository.findByTenantIdAndType(tenantId, IpAccessControl.AccessControlType.WHITELIST))
                .thenReturn(Flux.just(whitelistRule));

        // When & Then
        StepVerifier.create(ipAccessControlService.isAccessAllowed(testIp, tenantId))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    @DisplayName("检查IP访问权限 - 不在白名单中被拒绝")
    void isAccessAllowed_NotInWhitelist_Denied() {
        // Given
        when(repository.findByTenantIdAndType(tenantId, IpAccessControl.AccessControlType.BLACKLIST))
                .thenReturn(Flux.empty());
        when(repository.findByTenantIdAndType(tenantId, IpAccessControl.AccessControlType.WHITELIST))
                .thenReturn(Flux.just(whitelistRule));

        // When & Then
        StepVerifier.create(ipAccessControlService.isAccessAllowed("10.0.0.100", tenantId))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    @DisplayName("添加IP到白名单 - 成功")
    void addToWhitelist_Success() {
        // Given
        when(repository.save(any(IpAccessControl.class))).thenReturn(Mono.just(whitelistRule));
        when(auditLogService.log(anyString(), anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(Mono.empty());

        // When & Then
        StepVerifier.create(ipAccessControlService.addToWhitelist(testIp, tenantId, "测试白名单", operator))
                .expectNextMatches(rule -> rule.getType() == IpAccessControl.AccessControlType.WHITELIST
                        && rule.getTenantId().equals(tenantId))
                .verifyComplete();

        ArgumentCaptor<IpAccessControl> captor = ArgumentCaptor.forClass(IpAccessControl.class);
        verify(repository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getIpAddress()).isEqualTo(testIp);
        assertThat(captor.getValue().getType()).isEqualTo(IpAccessControl.AccessControlType.WHITELIST);
    }

    @Test
    @DisplayName("添加IP到黑名单 - 永久封禁")
    void addToBlacklist_Permanent() {
        // Given
        when(repository.save(any(IpAccessControl.class))).thenReturn(Mono.just(blacklistRule));
        when(auditLogService.log(anyString(), anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(Mono.empty());

        // When & Then
        StepVerifier.create(ipAccessControlService.addToBlacklist(testIp, tenantId, "恶意IP", null, operator))
                .expectNextMatches(rule -> rule.getType() == IpAccessControl.AccessControlType.BLACKLIST
                        && rule.getTenantId().equals(tenantId))
                .verifyComplete();

        ArgumentCaptor<IpAccessControl> captor = ArgumentCaptor.forClass(IpAccessControl.class);
        verify(repository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getExpiresAt()).isNull();
    }

    @Test
    @DisplayName("添加IP到黑名单 - 临时封禁")
    void addToBlacklist_Temporary() {
        // Given
        when(repository.save(any(IpAccessControl.class))).thenReturn(Mono.just(blacklistRule));
        when(auditLogService.log(anyString(), anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(Mono.empty());

        // When & Then
        StepVerifier.create(ipAccessControlService.addToBlacklist(testIp, tenantId, "临时封禁", 60, operator))
                .expectNextCount(1)
                .verifyComplete();

        ArgumentCaptor<IpAccessControl> captor = ArgumentCaptor.forClass(IpAccessControl.class);
        verify(repository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getExpiresAt()).isNotNull();
    }

    @Test
    @DisplayName("从白名单移除IP - 成功")
    void removeFromWhitelist_Success() {
        // Given
        when(repository.deleteByIpAddressAndTenantIdAndType(
                        testIp, tenantId, IpAccessControl.AccessControlType.WHITELIST))
                .thenReturn(Mono.just(1L));

        // When & Then
        StepVerifier.create(ipAccessControlService.removeFromWhitelist(testIp, tenantId))
                .verifyComplete();

        verify(repository, times(1))
                .deleteByIpAddressAndTenantIdAndType(testIp, tenantId, IpAccessControl.AccessControlType.WHITELIST);
    }

    @Test
    @DisplayName("从黑名单移除IP - 成功")
    void removeFromBlacklist_Success() {
        // Given
        when(repository.deleteByIpAddressAndTenantIdAndType(
                        testIp, tenantId, IpAccessControl.AccessControlType.BLACKLIST))
                .thenReturn(Mono.just(1L));

        // When & Then
        StepVerifier.create(ipAccessControlService.removeFromBlacklist(testIp, tenantId))
                .verifyComplete();

        verify(repository, times(1))
                .deleteByIpAddressAndTenantIdAndType(testIp, tenantId, IpAccessControl.AccessControlType.BLACKLIST);
    }

    @Test
    @DisplayName("查询白名单 - 成功")
    void getWhitelist_Success() {
        // Given
        IpAccessControl rule1 = IpAccessControl.builder().priority(10).build();
        IpAccessControl rule2 = IpAccessControl.builder().priority(20).build();

        when(repository.findByTenantIdAndType(tenantId, IpAccessControl.AccessControlType.WHITELIST))
                .thenReturn(Flux.just(rule1, rule2));

        // When & Then
        StepVerifier.create(ipAccessControlService.getWhitelist(tenantId))
                .expectNextCount(2)
                .verifyComplete();
    }

    @Test
    @DisplayName("查询黑名单 - 成功")
    void getBlacklist_Success() {
        // Given
        when(repository.findByTenantIdAndType(tenantId, IpAccessControl.AccessControlType.BLACKLIST))
                .thenReturn(Flux.just(blacklistRule));

        // When & Then
        StepVerifier.create(ipAccessControlService.getBlacklist(tenantId))
                .expectNext(blacklistRule)
                .verifyComplete();
    }

    @Test
    @DisplayName("清理过期规则 - 成功")
    void cleanupExpiredRules_Success() {
        // Given
        when(repository.deleteExpired(any(LocalDateTime.class))).thenReturn(Mono.just(3L));

        // When & Then
        StepVerifier.create(ipAccessControlService.cleanupExpiredRules())
                .expectNext(3L)
                .verifyComplete();
    }

    @Test
    @DisplayName("启用规则 - 成功")
    void enableRule_Success() {
        // Given
        IpAccessControl disabledRule =
                IpAccessControl.builder().id(1L).enabled(false).build();

        when(repository.findById(1L)).thenReturn(Mono.just(disabledRule));
        when(repository.save(any(IpAccessControl.class))).thenReturn(Mono.just(whitelistRule));

        // When & Then
        StepVerifier.create(ipAccessControlService.enableRule(1L, operator)).verifyComplete();

        ArgumentCaptor<IpAccessControl> captor = ArgumentCaptor.forClass(IpAccessControl.class);
        verify(repository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getEnabled()).isTrue();
    }

    @Test
    @DisplayName("禁用规则 - 成功")
    void disableRule_Success() {
        // Given
        when(repository.findById(1L)).thenReturn(Mono.just(whitelistRule));
        when(repository.save(any(IpAccessControl.class))).thenReturn(Mono.just(whitelistRule));

        // When & Then
        StepVerifier.create(ipAccessControlService.disableRule(1L, operator)).verifyComplete();

        ArgumentCaptor<IpAccessControl> captor = ArgumentCaptor.forClass(IpAccessControl.class);
        verify(repository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getEnabled()).isFalse();
    }

    @Test
    @DisplayName("删除规则 - 成功")
    void deleteRule_Success() {
        // Given
        when(repository.findById(1L)).thenReturn(Mono.just(whitelistRule));
        when(repository.deleteById(1L)).thenReturn(Mono.empty());
        when(auditLogService.log(anyString(), anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(Mono.empty());

        // When & Then
        StepVerifier.create(ipAccessControlService.deleteRule(1L, operator)).verifyComplete();

        verify(repository, times(1)).deleteById(1L);
    }

    @Test
    @DisplayName("批量导入规则 - 成功")
    void batchImportRules_Success() {
        // Given
        IpAccessControl rule1 =
                IpAccessControl.builder().ipAddress("192.168.1.1").build();
        IpAccessControl rule2 =
                IpAccessControl.builder().ipAddress("192.168.1.2").build();

        when(repository.save(any(IpAccessControl.class)))
                .thenReturn(Mono.just(rule1))
                .thenReturn(Mono.just(rule2));

        // When & Then
        StepVerifier.create(ipAccessControlService.batchImportRules(Flux.just(rule1, rule2), operator))
                .expectNext(2L)
                .verifyComplete();

        verify(repository, times(2)).save(any(IpAccessControl.class));
    }
}
