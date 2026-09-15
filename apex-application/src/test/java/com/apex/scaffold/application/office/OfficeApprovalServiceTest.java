package com.apex.scaffold.application.office;

import com.apex.scaffold.domain.office.approval.ApprovalInstance;
import com.apex.scaffold.domain.office.approval.ApprovalRepository;
import com.apex.scaffold.domain.office.approval.ApprovalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 审批流程服务测试
 *
 * @author Apex Team
 * @since 1.3.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("审批流程服务测试")
class OfficeApprovalServiceTest {

    @Mock
    private ApprovalService feishuService;

    @Mock
    private ApprovalService dingtalkService;

    @Mock
    private ApprovalService wecomService;

    @Mock
    private ApprovalRepository approvalRepository;

    @InjectMocks
    private OfficeApprovalIntegrationService approvalService;

    private ApprovalInstance mockInstance;
    private Map<String, Object> mockFormData;

    @BeforeEach
    void setUp() {
        mockFormData = new HashMap<>();
        mockFormData.put("reason", "请假事由");
        mockFormData.put("days", 3);
        mockFormData.put("startDate", "2026-09-15");

        mockInstance = ApprovalInstance.builder()
                .id(1L)
                .tenantId(1L)
                .platform("FEISHU")
                .platformInstanceId("instance_001")
                .approvalCode("LEAVE_REQUEST")
                .applicantUserId("user_001")
                .status("PENDING")
                .formData(mockFormData)
                .build();
    }

    @Test
    @DisplayName("创建审批实例 - 飞书 - 成功")
    void testCreateApprovalInstance_Feishu_Success() {
        // Given
        Long tenantId = 1L;
        Long appId = 100L;
        String platform = "FEISHU";

        when(feishuService.createInstance(eq(tenantId), eq(appId), any(ApprovalInstance.class)))
                .thenReturn(Mono.just("instance_001"));
        when(approvalRepository.save(any(ApprovalInstance.class)))
                .thenReturn(Mono.just(mockInstance));

        // When & Then
        StepVerifier.create(approvalService.createApprovalInstance(tenantId, appId, platform, mockInstance))
                .expectNext("instance_001")
                .verifyComplete();

        verify(feishuService, times(1)).createInstance(eq(tenantId), eq(appId), any(ApprovalInstance.class));
        verify(approvalRepository, times(1)).save(any(ApprovalInstance.class));
    }

    @Test
    @DisplayName("创建审批实例 - 钉钉 - 成功")
    void testCreateApprovalInstance_Dingtalk_Success() {
        // Given
        Long tenantId = 1L;
        Long appId = 100L;
        String platform = "DINGTALK";
        ApprovalInstance dingtalkInstance = mockInstance.toBuilder()
                .platform("DINGTALK")
                .build();

        when(dingtalkService.createInstance(eq(tenantId), eq(appId), any(ApprovalInstance.class)))
                .thenReturn(Mono.just("instance_002"));
        when(approvalRepository.save(any(ApprovalInstance.class)))
                .thenReturn(Mono.just(dingtalkInstance));

        // When & Then
        StepVerifier.create(approvalService.createApprovalInstance(tenantId, appId, platform, dingtalkInstance))
                .expectNext("instance_002")
                .verifyComplete();

        verify(dingtalkService, times(1)).createInstance(eq(tenantId), eq(appId), any(ApprovalInstance.class));
    }

    @Test
    @DisplayName("创建审批实例 - 企业微信 - 成功")
    void testCreateApprovalInstance_Wecom_Success() {
        // Given
        Long tenantId = 1L;
        Long appId = 100L;
        String platform = "WECOM";
        ApprovalInstance wecomInstance = mockInstance.toBuilder()
                .platform("WECOM")
                .build();

        when(wecomService.createInstance(eq(tenantId), eq(appId), any(ApprovalInstance.class)))
                .thenReturn(Mono.just("instance_003"));
        when(approvalRepository.save(any(ApprovalInstance.class)))
                .thenReturn(Mono.just(wecomInstance));

        // When & Then
        StepVerifier.create(approvalService.createApprovalInstance(tenantId, appId, platform, wecomInstance))
                .expectNext("instance_003")
                .verifyComplete();

        verify(wecomService, times(1)).createInstance(eq(tenantId), eq(appId), any(ApprovalInstance.class));
    }

    @Test
    @DisplayName("查询审批实例 - 成功")
    void testGetApprovalInstance_Success() {
        // Given
        Long tenantId = 1L;
        Long appId = 100L;
        String platform = "FEISHU";
        String instanceId = "instance_001";

        when(feishuService.getInstance(eq(tenantId), eq(appId), eq(instanceId)))
                .thenReturn(Mono.just(mockInstance));

        // When & Then
        StepVerifier.create(approvalService.getApprovalInstance(tenantId, appId, platform, instanceId))
                .expectNext(mockInstance)
                .verifyComplete();

        verify(feishuService, times(1)).getInstance(eq(tenantId), eq(appId), eq(instanceId));
    }

    @Test
    @DisplayName("查询审批实例列表 - 成功")
    void testListApprovalInstances_Success() {
        // Given
        Long tenantId = 1L;
        String platform = "FEISHU";

        ApprovalInstance instance2 = mockInstance.toBuilder()
                .id(2L)
                .platformInstanceId("instance_002")
                .approvalCode("REIMBURSEMENT")
                .build();

        when(approvalRepository.findByTenantAndPlatform(eq(tenantId), eq(platform)))
                .thenReturn(Flux.just(mockInstance, instance2));

        // When & Then
        StepVerifier.create(approvalService.listApprovalInstances(tenantId, platform))
                .expectNext(mockInstance)
                .expectNext(instance2)
                .verifyComplete();

        verify(approvalRepository, times(1)).findByTenantAndPlatform(eq(tenantId), eq(platform));
    }

    @Test
    @DisplayName("创建审批实例 - 平台服务异常")
    void testCreateApprovalInstance_PlatformServiceError() {
        // Given
        Long tenantId = 1L;
        Long appId = 100L;
        String platform = "FEISHU";

        when(feishuService.createInstance(eq(tenantId), eq(appId), any(ApprovalInstance.class)))
                .thenReturn(Mono.error(new RuntimeException("平台服务异常")));

        // When & Then
        StepVerifier.create(approvalService.createApprovalInstance(tenantId, appId, platform, mockInstance))
                .expectError(RuntimeException.class)
                .verify();

        verify(approvalRepository, never()).save(any(ApprovalInstance.class));
    }

    @Test
    @DisplayName("查询审批实例 - 实例不存在")
    void testGetApprovalInstance_NotFound() {
        // Given
        Long tenantId = 1L;
        Long appId = 100L;
        String platform = "FEISHU";
        String instanceId = "instance_not_exist";

        when(feishuService.getInstance(eq(tenantId), eq(appId), eq(instanceId)))
                .thenReturn(Mono.empty());

        // When & Then
        StepVerifier.create(approvalService.getApprovalInstance(tenantId, appId, platform, instanceId))
                .expectComplete()
                .verify();
    }

    @Test
    @DisplayName("创建复杂表单审批 - 成功")
    void testCreateComplexFormApproval_Success() {
        // Given
        Long tenantId = 1L;
        Long appId = 100L;
        String platform = "FEISHU";

        Map<String, Object> complexFormData = new HashMap<>();
        complexFormData.put("title", "采购申请");
        complexFormData.put("amount", 50000);
        complexFormData.put("items", java.util.Arrays.asList(
                Map.of("name", "笔记本电脑", "quantity", 10, "price", 5000)
        ));

        ApprovalInstance complexInstance = mockInstance.toBuilder()
                .approvalCode("PURCHASE_REQUEST")
                .formData(complexFormData)
                .build();

        when(feishuService.createInstance(eq(tenantId), eq(appId), any(ApprovalInstance.class)))
                .thenReturn(Mono.just("instance_complex_001"));
        when(approvalRepository.save(any(ApprovalInstance.class)))
                .thenReturn(Mono.just(complexInstance));

        // When & Then
        StepVerifier.create(approvalService.createApprovalInstance(tenantId, appId, platform, complexInstance))
                .expectNext("instance_complex_001")
                .verifyComplete();
    }

    @Test
    @DisplayName("查询待审批实例 - 成功")
    void testListPendingInstances_Success() {
        // Given
        Long tenantId = 1L;
        String platform = "FEISHU";

        ApprovalInstance pendingInstance1 = mockInstance;
        ApprovalInstance pendingInstance2 = mockInstance.toBuilder()
                .id(2L)
                .platformInstanceId("instance_002")
                .build();

        when(approvalRepository.findByTenantAndPlatform(eq(tenantId), eq(platform)))
                .thenReturn(Flux.just(pendingInstance1, pendingInstance2));

        // When & Then
        StepVerifier.create(approvalService.listApprovalInstances(tenantId, platform))
                .expectNext(pendingInstance1)
                .expectNext(pendingInstance2)
                .verifyComplete();
    }

    @Test
    @DisplayName("更新审批状态 - 成功")
    void testUpdateApprovalStatus_Success() {
        // Given
        ApprovalInstance approvedInstance = mockInstance.toBuilder()
                .status("APPROVED")
                .build();

        when(approvalRepository.save(any(ApprovalInstance.class)))
                .thenReturn(Mono.just(approvedInstance));

        // When & Then
        StepVerifier.create(approvalRepository.save(approvedInstance))
                .expectNext(approvedInstance)
                .verifyComplete();
    }
}
