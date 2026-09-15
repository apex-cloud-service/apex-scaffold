package com.apex.scaffold.application.office;

import com.apex.scaffold.domain.office.organization.Department;
import com.apex.scaffold.domain.office.organization.OrganizationMember;
import com.apex.scaffold.domain.office.organization.OrganizationRepository;
import com.apex.scaffold.domain.office.organization.OrganizationService;
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

import java.util.Arrays;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 组织同步服务测试
 *
 * @author Apex Team
 * @since 1.3.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("组织同步服务测试")
class OfficeOrganizationSyncServiceTest {

    @Mock
    private OrganizationService feishuService;

    @Mock
    private OrganizationService dingtalkService;

    @Mock
    private OrganizationService wecomService;

    @Mock
    private OrganizationRepository organizationRepository;

    @InjectMocks
    private OfficeOrganizationSyncService syncService;

    private Department mockDepartment;
    private OrganizationMember mockMember;

    @BeforeEach
    void setUp() {
        mockDepartment = Department.builder()
                .id(1L)
                .tenantId(1L)
                .platform("FEISHU")
                .platformDeptId("dept_001")
                .name("技术部")
                .parentId("0")
                .build();

        mockMember = OrganizationMember.builder()
                .id(1L)
                .tenantId(1L)
                .platform("FEISHU")
                .platformUserId("user_001")
                .name("张三")
                .email("zhangsan@example.com")
                .mobile("13800138000")
                .departmentIds(Arrays.asList("dept_001"))
                .build();
    }

    @Test
    @DisplayName("全量同步部门 - 成功")
    void testSyncDepartmentsFull_Success() {
        // Given
        Long tenantId = 1L;
        Long appId = 100L;
        String platform = "FEISHU";

        when(feishuService.listDepartments(eq(tenantId), eq(appId)))
                .thenReturn(Flux.just(mockDepartment));
        when(organizationRepository.saveDepartment(any(Department.class)))
                .thenReturn(Mono.just(mockDepartment));

        // When & Then
        StepVerifier.create(syncService.syncDepartments(tenantId, appId, platform, true))
                .expectNext(1)
                .verifyComplete();

        verify(feishuService, times(1)).listDepartments(eq(tenantId), eq(appId));
        verify(organizationRepository, times(1)).saveDepartment(any(Department.class));
    }

    @Test
    @DisplayName("全量同步成员 - 成功")
    void testSyncMembersFull_Success() {
        // Given
        Long tenantId = 1L;
        Long appId = 100L;
        String platform = "FEISHU";

        when(feishuService.listMembers(eq(tenantId), eq(appId), isNull()))
                .thenReturn(Flux.just(mockMember));
        when(organizationRepository.saveMember(any(OrganizationMember.class)))
                .thenReturn(Mono.just(mockMember));

        // When & Then
        StepVerifier.create(syncService.syncMembers(tenantId, appId, platform, null, true))
                .expectNext(1)
                .verifyComplete();

        verify(feishuService, times(1)).listMembers(eq(tenantId), eq(appId), isNull());
        verify(organizationRepository, times(1)).saveMember(any(OrganizationMember.class));
    }

    @Test
    @DisplayName("钉钉平台同步 - 成功")
    void testSyncDingtalk_Success() {
        // Given
        Long tenantId = 1L;
        Long appId = 100L;
        String platform = "DINGTALK";

        Department dingtalkDept = mockDepartment.toBuilder().platform("DINGTALK").build();
        
        when(dingtalkService.listDepartments(eq(tenantId), eq(appId)))
                .thenReturn(Flux.just(dingtalkDept));
        when(organizationRepository.saveDepartment(any(Department.class)))
                .thenReturn(Mono.just(dingtalkDept));

        // When & Then
        StepVerifier.create(syncService.syncDepartments(tenantId, appId, platform, true))
                .expectNext(1)
                .verifyComplete();

        verify(dingtalkService, times(1)).listDepartments(eq(tenantId), eq(appId));
    }

    @Test
    @DisplayName("企业微信平台同步 - 成功")
    void testSyncWecom_Success() {
        // Given
        Long tenantId = 1L;
        Long appId = 100L;
        String platform = "WECOM";

        Department wecomDept = mockDepartment.toBuilder().platform("WECOM").build();
        
        when(wecomService.listDepartments(eq(tenantId), eq(appId)))
                .thenReturn(Flux.just(wecomDept));
        when(organizationRepository.saveDepartment(any(Department.class)))
                .thenReturn(Mono.just(wecomDept));

        // When & Then
        StepVerifier.create(syncService.syncDepartments(tenantId, appId, platform, true))
                .expectNext(1)
                .verifyComplete();

        verify(wecomService, times(1)).listDepartments(eq(tenantId), eq(appId));
    }

    @Test
    @DisplayName("同步失败 - 平台服务异常")
    void testSync_PlatformServiceError() {
        // Given
        Long tenantId = 1L;
        Long appId = 100L;
        String platform = "FEISHU";

        when(feishuService.listDepartments(eq(tenantId), eq(appId)))
                .thenReturn(Flux.error(new RuntimeException("平台服务异常")));

        // When & Then
        StepVerifier.create(syncService.syncDepartments(tenantId, appId, platform, true))
                .expectError(RuntimeException.class)
                .verify();
    }

    @Test
    @DisplayName("同步失败 - 数据库保存异常")
    void testSync_RepositoryError() {
        // Given
        Long tenantId = 1L;
        Long appId = 100L;
        String platform = "FEISHU";

        when(feishuService.listDepartments(eq(tenantId), eq(appId)))
                .thenReturn(Flux.just(mockDepartment));
        when(organizationRepository.saveDepartment(any(Department.class)))
                .thenReturn(Mono.error(new RuntimeException("数据库异常")));

        // When & Then
        StepVerifier.create(syncService.syncDepartments(tenantId, appId, platform, true))
                .expectError(RuntimeException.class)
                .verify();
    }

    @Test
    @DisplayName("指定部门同步 - 成功")
    void testSyncMembersByDepartment_Success() {
        // Given
        Long tenantId = 1L;
        Long appId = 100L;
        String platform = "FEISHU";
        String departmentId = "dept_001";

        when(feishuService.listMembers(eq(tenantId), eq(appId), eq(departmentId)))
                .thenReturn(Flux.just(mockMember));
        when(organizationRepository.saveMember(any(OrganizationMember.class)))
                .thenReturn(Mono.just(mockMember));

        // When & Then
        StepVerifier.create(syncService.syncMembers(tenantId, appId, platform, departmentId, false))
                .expectNext(1)
                .verifyComplete();

        verify(feishuService, times(1)).listMembers(eq(tenantId), eq(appId), eq(departmentId));
    }

    @Test
    @DisplayName("批量同步 - 多个部门")
    void testSyncMultipleDepartments_Success() {
        // Given
        Long tenantId = 1L;
        Long appId = 100L;
        String platform = "FEISHU";

        Department dept1 = mockDepartment;
        Department dept2 = mockDepartment.toBuilder()
                .id(2L)
                .platformDeptId("dept_002")
                .name("产品部")
                .build();

        when(feishuService.listDepartments(eq(tenantId), eq(appId)))
                .thenReturn(Flux.just(dept1, dept2));
        when(organizationRepository.saveDepartment(any(Department.class)))
                .thenReturn(Mono.just(dept1), Mono.just(dept2));

        // When & Then
        StepVerifier.create(syncService.syncDepartments(tenantId, appId, platform, true))
                .expectNext(2)
                .verifyComplete();

        verify(organizationRepository, times(2)).saveDepartment(any(Department.class));
    }

    @Test
    @DisplayName("空结果同步 - 返回0")
    void testSyncEmptyResult() {
        // Given
        Long tenantId = 1L;
        Long appId = 100L;
        String platform = "FEISHU";

        when(feishuService.listDepartments(eq(tenantId), eq(appId)))
                .thenReturn(Flux.empty());

        // When & Then
        StepVerifier.create(syncService.syncDepartments(tenantId, appId, platform, true))
                .expectNext(0)
                .verifyComplete();

        verify(organizationRepository, never()).saveDepartment(any(Department.class));
    }
}
