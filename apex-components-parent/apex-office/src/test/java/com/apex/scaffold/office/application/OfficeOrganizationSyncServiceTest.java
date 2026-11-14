package com.apex.scaffold.office.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.office.application.port.OfficeOrgUserQueryPort;
import com.apex.scaffold.office.application.port.OfficeOrgUserSnapshot;
import com.apex.scaffold.office.domain.OfficeDepartment;
import com.apex.scaffold.office.domain.OfficeMember;
import com.apex.scaffold.office.domain.OfficeOrganizationMapping;
import com.apex.scaffold.office.domain.OfficeOrganizationService;
import com.apex.scaffold.office.domain.OfficeOrganizationService.OrganizationSyncResult;
import com.apex.scaffold.office.domain.OfficePlatform;
import com.apex.scaffold.office.domain.OfficeRepository;
import com.apex.scaffold.office.domain.OfficeSyncTask;
import com.apex.scaffold.office.off.domain.entity.OffDepartmentEntity;
import com.apex.scaffold.office.off.domain.entity.OffEmployeeEntity;
import com.apex.scaffold.office.off.domain.repository.OffDepartmentRepository;
import com.apex.scaffold.office.off.domain.repository.OffEmployeeRepository;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class OfficeOrganizationSyncServiceTest {
    private static final String TENANT = "tenant";

    @Mock
    private OfficeRepository repository;

    @Mock
    private OfficeOrganizationService organizationService;

    @Mock
    private OffDepartmentRepository offDepartmentRepository;

    @Mock
    private OffEmployeeRepository offEmployeeRepository;

    @Mock
    private OfficeOrgUserQueryPort orgUserQueryPort;

    @Mock
    private TransactionalOperator transactionalOperator;

    @InjectMocks
    private OfficeOrganizationSyncService service;

    @Test
    void getDepartmentMapping_delegatesToRepository() {
        when(repository.findDepartmentMapping(TENANT, OfficePlatform.FEISHU, "dept"))
                .thenReturn(Mono.empty());
        StepVerifier.create(service.getDepartmentMapping(TENANT, OfficePlatform.FEISHU, "dept"))
                .verifyComplete();
        verify(repository).findDepartmentMapping(TENANT, OfficePlatform.FEISHU, "dept");
    }

    @Test
    void syncDepartments_fullSyncReusesExistingTaskRow() {
        OfficeSyncTask existing = OfficeSyncTask.builder()
                .id(7L)
                .tenantId(TENANT)
                .platform(OfficePlatform.FEISHU)
                .syncType(OfficeOrganizationSyncService.SYNC_TYPE_DEPARTMENT)
                .state("SUCCESS")
                .cursor("c1")
                .version(3)
                .deleted(0)
                .build();
        when(repository.findLatestSyncTask(
                        TENANT, OfficePlatform.FEISHU, OfficeOrganizationSyncService.SYNC_TYPE_DEPARTMENT))
                .thenReturn(Mono.just(existing));
        stubSaveSyncTaskPassthrough();
        when(organizationService.fetchDepartments(TENANT, OfficePlatform.FEISHU, null))
                .thenReturn(Mono.just(OrganizationSyncResult.<OfficeDepartment>empty()));
        stubTransactionalPassthrough();

        StepVerifier.create(service.syncDepartments(TENANT, OfficePlatform.FEISHU, true))
                .assertNext(result -> {
                    assertThat(result.success()).isTrue();
                    assertThat(result.taskId()).isEqualTo(7L);
                })
                .verifyComplete();

        ArgumentCaptor<OfficeSyncTask> captor = ArgumentCaptor.forClass(OfficeSyncTask.class);
        verify(repository, atLeastOnce()).saveSyncTask(captor.capture());
        assertThat(captor.getAllValues())
                .allSatisfy(task -> assertThat(task.getId()).isEqualTo(7L));
        OfficeSyncTask reset = captor.getAllValues().get(0);
        assertThat(reset.getState()).isEqualTo("RUNNING");
        assertThat(reset.getCursor()).isNull();
    }

    @Test
    void retrySyncTask_resumesSameTaskFromCheckpoint() {
        OfficeSyncTask failed = OfficeSyncTask.builder()
                .id(9L)
                .tenantId(TENANT)
                .platform(OfficePlatform.DINGTALK)
                .syncType(OfficeOrganizationSyncService.SYNC_TYPE_MEMBER)
                .state("FAILED")
                .cursor("c2")
                .failureMessage("boom")
                .version(1)
                .deleted(0)
                .build();
        when(repository.findSyncTaskById(TENANT, 9L)).thenReturn(Mono.just(failed));
        stubSaveSyncTaskPassthrough();
        when(organizationService.fetchMembers(TENANT, OfficePlatform.DINGTALK, "c2"))
                .thenReturn(Mono.just(OrganizationSyncResult.<OfficeMember>empty()));
        stubTransactionalPassthrough();

        StepVerifier.create(service.retrySyncTask(TENANT, 9L))
                .assertNext(result -> {
                    assertThat(result.success()).isTrue();
                    assertThat(result.taskId()).isEqualTo(9L);
                })
                .verifyComplete();

        verify(repository, never()).findLatestSyncTask(any(), any(), any());
        verify(organizationService).fetchMembers(TENANT, OfficePlatform.DINGTALK, "c2");
        ArgumentCaptor<OfficeSyncTask> captor = ArgumentCaptor.forClass(OfficeSyncTask.class);
        verify(repository, atLeastOnce()).saveSyncTask(captor.capture());
        assertThat(captor.getAllValues())
                .allSatisfy(task -> assertThat(task.getId()).isEqualTo(9L));
        OfficeSyncTask first = captor.getAllValues().get(0);
        assertThat(first.getState()).isEqualTo("RUNNING");
        assertThat(first.getCursor()).isEqualTo("c2");
        assertThat(captor.getValue().getState()).isEqualTo("SUCCESS");
    }

    @Test
    void retrySyncTask_taskInvisibleAcrossTenants() {
        // 仓储按租户过滤后，其他租户的任务对当前租户不可见；服务层不得回退为无租户查询。
        when(repository.findSyncTaskById(TENANT, 9L)).thenReturn(Mono.empty());

        StepVerifier.create(service.retrySyncTask(TENANT, 9L))
                .expectError(BusinessException.class)
                .verify();

        verify(repository).findSyncTaskById(TENANT, 9L);
        verify(repository, never()).findSyncTaskById(ArgumentMatchers.eq("other-tenant"), any());
        verifyNoInteractions(organizationService);
        verify(repository, never()).saveSyncTask(any());
    }

    @Test
    void retrySyncTask_rejectsNonFailedTask() {
        OfficeSyncTask done = OfficeSyncTask.builder()
                .id(5L)
                .tenantId(TENANT)
                .platform(OfficePlatform.FEISHU)
                .syncType(OfficeOrganizationSyncService.SYNC_TYPE_DEPARTMENT)
                .state("SUCCESS")
                .version(2)
                .deleted(0)
                .build();
        when(repository.findSyncTaskById(TENANT, 5L)).thenReturn(Mono.just(done));

        StepVerifier.create(service.retrySyncTask(TENANT, 5L))
                .expectError(BusinessException.class)
                .verify();

        verifyNoInteractions(organizationService);
        verify(repository, never()).saveSyncTask(any());
    }

    @Test
    void syncDepartments_continuesWhenPageEmptyButHasMore() {
        when(repository.findLatestSyncTask(
                        TENANT, OfficePlatform.FEISHU, OfficeOrganizationSyncService.SYNC_TYPE_DEPARTMENT))
                .thenReturn(Mono.empty());
        stubSaveSyncTaskPassthrough();
        when(organizationService.fetchDepartments(TENANT, OfficePlatform.FEISHU, null))
                .thenReturn(Mono.just(OrganizationSyncResult.<OfficeDepartment>of(List.of(), "p2", true)));
        when(organizationService.fetchDepartments(TENANT, OfficePlatform.FEISHU, "p2"))
                .thenReturn(Mono.just(OrganizationSyncResult.ofFinal(List.of(department("od-1", "研发部")))));
        stubDepartmentMappingChain("od-1", 100L);
        stubTransactionalPassthrough();

        StepVerifier.create(service.syncDepartments(TENANT, OfficePlatform.FEISHU, false))
                .assertNext(result -> {
                    assertThat(result.success()).isTrue();
                    assertThat(result.syncedCount()).isEqualTo(1);
                })
                .verifyComplete();

        verify(organizationService).fetchDepartments(TENANT, OfficePlatform.FEISHU, "p2");
    }

    @Test
    void syncDepartments_stopsOnRepeatedCursorAcrossResume() {
        OfficeSyncTask resumed = OfficeSyncTask.builder()
                .id(8L)
                .tenantId(TENANT)
                .platform(OfficePlatform.FEISHU)
                .syncType(OfficeOrganizationSyncService.SYNC_TYPE_DEPARTMENT)
                .state("FAILED")
                .cursor("p1")
                .version(1)
                .deleted(0)
                .build();
        when(repository.findLatestSyncTask(
                        TENANT, OfficePlatform.FEISHU, OfficeOrganizationSyncService.SYNC_TYPE_DEPARTMENT))
                .thenReturn(Mono.just(resumed));
        stubSaveSyncTaskPassthrough();
        when(organizationService.fetchDepartments(TENANT, OfficePlatform.FEISHU, "p1"))
                .thenReturn(Mono.just(OrganizationSyncResult.<OfficeDepartment>of(List.of(), "p1", true)));
        stubTransactionalPassthrough();

        StepVerifier.create(service.syncDepartments(TENANT, OfficePlatform.FEISHU, false))
                .expectErrorSatisfies(error ->
                        assertThat(error).isInstanceOf(BusinessException.class).hasMessageContaining("重复分页游标"))
                .verify();

        verify(organizationService, times(1)).fetchDepartments(any(), any(), any());
    }

    @Test
    void syncDepartments_errorsWhenHasMoreWithoutCursor() {
        when(repository.findLatestSyncTask(
                        TENANT, OfficePlatform.FEISHU, OfficeOrganizationSyncService.SYNC_TYPE_DEPARTMENT))
                .thenReturn(Mono.empty());
        stubSaveSyncTaskPassthrough();
        when(organizationService.fetchDepartments(TENANT, OfficePlatform.FEISHU, null))
                .thenReturn(Mono.just(OrganizationSyncResult.<OfficeDepartment>of(List.of(), null, true)));
        stubTransactionalPassthrough();

        StepVerifier.create(service.syncDepartments(TENANT, OfficePlatform.FEISHU, false))
                .expectErrorSatisfies(error ->
                        assertThat(error).isInstanceOf(BusinessException.class).hasMessageContaining("nextCursor"))
                .verify();

        ArgumentCaptor<OfficeSyncTask> captor = ArgumentCaptor.forClass(OfficeSyncTask.class);
        verify(repository, atLeastOnce()).saveSyncTask(captor.capture());
        assertThat(captor.getValue().getState()).isEqualTo("FAILED");
    }

    @Test
    void syncDepartments_updatesExistingMirror() {
        when(repository.findLatestSyncTask(
                        TENANT, OfficePlatform.FEISHU, OfficeOrganizationSyncService.SYNC_TYPE_DEPARTMENT))
                .thenReturn(Mono.empty());
        stubSaveSyncTaskPassthrough();
        when(organizationService.fetchDepartments(TENANT, OfficePlatform.FEISHU, null))
                .thenReturn(Mono.just(OrganizationSyncResult.ofFinal(List.of(department("od-1", "研发部")))));
        when(repository.findDepartmentMapping(TENANT, OfficePlatform.FEISHU, "od-1"))
                .thenReturn(Mono.empty());
        OffDepartmentEntity mirror = new OffDepartmentEntity();
        mirror.setId(42L);
        mirror.setDeptName("旧名称");
        when(offDepartmentRepository.findByExternalId("FEISHU", "od-1")).thenReturn(Flux.just(mirror));
        when(offDepartmentRepository.update(any())).thenReturn(Mono.just(1));
        when(offDepartmentRepository.findBySyncSource("FEISHU")).thenReturn(Flux.just(mirror));
        when(repository.saveOrganizationMapping(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        stubTransactionalPassthrough();

        StepVerifier.create(service.syncDepartments(TENANT, OfficePlatform.FEISHU, false))
                .assertNext(result -> {
                    assertThat(result.success()).isTrue();
                    assertThat(result.syncedCount()).isEqualTo(1);
                })
                .verifyComplete();

        verify(offDepartmentRepository, never()).insert(any());
        ArgumentCaptor<OffDepartmentEntity> mirrorCaptor = ArgumentCaptor.forClass(OffDepartmentEntity.class);
        verify(offDepartmentRepository, atLeastOnce()).update(mirrorCaptor.capture());
        assertThat(mirrorCaptor.getAllValues())
                .anySatisfy(updated -> assertThat(updated.getDeptName()).isEqualTo("研发部"));

        ArgumentCaptor<OfficeOrganizationMapping> mappingCaptor =
                ArgumentCaptor.forClass(OfficeOrganizationMapping.class);
        verify(repository).saveOrganizationMapping(mappingCaptor.capture());
        assertThat(mappingCaptor.getValue().getApexDeptId()).isEqualTo("42");
    }

    @Test
    void syncDepartments_rebuildsParentHierarchyFromCollectedDepartments() {
        // 父部门在第二页才返回，逐条写入时无法解析父镜像，同步收尾需统一重算层级。
        when(repository.findLatestSyncTask(
                        TENANT, OfficePlatform.FEISHU, OfficeOrganizationSyncService.SYNC_TYPE_DEPARTMENT))
                .thenReturn(Mono.empty());
        stubSaveSyncTaskPassthrough();
        when(organizationService.fetchDepartments(TENANT, OfficePlatform.FEISHU, null))
                .thenReturn(Mono.just(OrganizationSyncResult.<OfficeDepartment>of(
                        List.of(department("od-child", "研发一组", "od-root")), "p2", true)));
        when(organizationService.fetchDepartments(TENANT, OfficePlatform.FEISHU, "p2"))
                .thenReturn(Mono.just(OrganizationSyncResult.ofFinal(
                        List.of(department("od-root", "研发中心"), department("od-other", "销售部", "od-missing")))));
        stubDepartmentMappingChain(Map.of("od-child", 11L, "od-root", 12L, "od-other", 13L));
        OffDepartmentEntity child = mirror(11L, "od-child");
        OffDepartmentEntity root = mirror(12L, "od-root");
        OffDepartmentEntity other = mirror(13L, "od-other");
        when(offDepartmentRepository.findBySyncSource("FEISHU")).thenReturn(Flux.just(child, root, other));
        when(offDepartmentRepository.update(any())).thenReturn(Mono.just(1));
        stubTransactionalPassthrough();

        StepVerifier.create(service.syncDepartments(TENANT, OfficePlatform.FEISHU, false))
                .assertNext(result -> assertThat(result.success()).isTrue())
                .verifyComplete();

        assertThat(child.getParentId()).isEqualTo(12L);
        assertThat(child.getDeptLevel()).isEqualTo(2);
        assertThat(child.getDeptPath()).isEqualTo("/12/11/");
        assertThat(root.getParentId()).isNull();
        assertThat(root.getDeptLevel()).isEqualTo(1);
        // 父镜像缺失时按根部门处理，避免产生悬挂引用
        assertThat(other.getParentId()).isNull();
        assertThat(other.getDeptLevel()).isEqualTo(1);
    }

    @Test
    void syncMembers_assignsDepartmentAndRealSystemUserId() {
        when(repository.findLatestSyncTask(
                        TENANT, OfficePlatform.FEISHU, OfficeOrganizationSyncService.SYNC_TYPE_MEMBER))
                .thenReturn(Mono.empty());
        stubSaveSyncTaskPassthrough();
        when(organizationService.fetchMembers(TENANT, OfficePlatform.FEISHU, null))
                .thenReturn(Mono.just(OrganizationSyncResult.ofFinal(
                        List.of(member("ou-1", "张三", "od-1"), member("ou-2", "李四", "od-1")))));
        Map<String, OfficeOrganizationMapping> savedMappings = new ConcurrentHashMap<>();
        when(repository.findMemberMapping(eq(TENANT), eq(OfficePlatform.FEISHU), any()))
                .thenAnswer(invocation -> Mono.justOrEmpty(savedMappings.get(invocation.getArgument(2))));
        OffDepartmentEntity dept = mirror(21L, "od-1");
        dept.setDeptName("研发部");
        when(offDepartmentRepository.findByExternalId("FEISHU", "od-1")).thenReturn(Flux.just(dept));
        OffEmployeeEntity insertedFirst = new OffEmployeeEntity();
        OffEmployeeEntity insertedSecond = new OffEmployeeEntity();
        when(offEmployeeRepository.findByExternalId(eq("FEISHU"), any())).thenAnswer(invocation -> {
            String externalId = invocation.getArgument(1);
            OffEmployeeEntity matched = "ou-1".equals(externalId) ? insertedFirst : insertedSecond;
            return matched.getExternalId() == null ? Flux.empty() : Flux.just(matched);
        });
        when(offEmployeeRepository.insert(any())).thenAnswer(invocation -> {
            OffEmployeeEntity entity = invocation.getArgument(0);
            if ("ou-1".equals(entity.getExternalId())) {
                entity.setId(31L);
                copyEmployee(entity, insertedFirst);
            } else {
                entity.setId(32L);
                copyEmployee(entity, insertedSecond);
            }
            return Mono.just(1);
        });
        when(offEmployeeRepository.update(any())).thenAnswer(invocation -> Mono.just(1));
        when(repository.saveOrganizationMapping(any())).thenAnswer(invocation -> {
            OfficeOrganizationMapping mapping = invocation.getArgument(0);
            if (mapping.getExternalUserId() != null) {
                savedMappings.put(mapping.getExternalUserId(), mapping);
            }
            return Mono.just(mapping);
        });
        when(orgUserQueryPort.resolveUsers(eq(TENANT), any()))
                .thenReturn(Mono.just(List.of(
                        OfficeOrgUserSnapshot.builder()
                                .externalUserId("ou-1")
                                .userId(900L)
                                .deptId(21L)
                                .matchedBy("MOBILE")
                                .build(),
                        OfficeOrgUserSnapshot.builder()
                                .externalUserId("ou-2")
                                .userId(901L)
                                .deptId(21L)
                                .matchedBy("EMAIL")
                                .build())));
        stubTransactionalPassthrough();

        StepVerifier.create(service.syncMembers(TENANT, OfficePlatform.FEISHU, false))
                .assertNext(result -> {
                    assertThat(result.success()).isTrue();
                    assertThat(result.syncedCount()).isEqualTo(2);
                })
                .verifyComplete();

        ArgumentCaptor<OffEmployeeEntity> employeeCaptor = ArgumentCaptor.forClass(OffEmployeeEntity.class);
        verify(offEmployeeRepository, times(2)).update(employeeCaptor.capture());
        assertThat(employeeCaptor.getAllValues())
                .anySatisfy(employee -> {
                    assertThat(employee.getExternalId()).isEqualTo("ou-1");
                    assertThat(employee.getUserId()).isEqualTo(900L);
                })
                .anySatisfy(employee -> {
                    assertThat(employee.getExternalId()).isEqualTo("ou-2");
                    assertThat(employee.getUserId()).isEqualTo(901L);
                });
        ArgumentCaptor<OffEmployeeEntity> insertCaptor = ArgumentCaptor.forClass(OffEmployeeEntity.class);
        verify(offEmployeeRepository, times(2)).insert(insertCaptor.capture());
        assertThat(insertCaptor.getAllValues()).allSatisfy(employee -> {
            assertThat(employee.getDeptId()).isEqualTo(21L);
            assertThat(employee.getDeptName()).isEqualTo("研发部");
            assertThat(employee.getPosition()).isEqualTo("工程师");
        });
        assertThat(insertedFirst.getUserId()).isEqualTo(900L);
        assertThat(insertedSecond.getUserId()).isEqualTo(901L);

        ArgumentCaptor<OfficeOrganizationMapping> mappingCaptor =
                ArgumentCaptor.forClass(OfficeOrganizationMapping.class);
        // 分页阶段先写映射（此时未解析用户），收尾阶段按解析结果补写 apexUserId
        verify(repository, times(4)).saveOrganizationMapping(mappingCaptor.capture());
        assertThat(mappingCaptor.getAllValues())
                .anySatisfy(mapping -> {
                    assertThat(mapping.getExternalUserId()).isEqualTo("ou-1");
                    assertThat(mapping.getApexDeptId()).isEqualTo("21");
                    assertThat(mapping.getApexUserId()).isNull();
                })
                .anySatisfy(mapping -> {
                    assertThat(mapping.getExternalUserId()).isEqualTo("ou-1");
                    assertThat(mapping.getApexUserId()).isEqualTo("900");
                })
                .anySatisfy(mapping -> {
                    assertThat(mapping.getExternalUserId()).isEqualTo("ou-2");
                    assertThat(mapping.getApexUserId()).isEqualTo("901");
                });
    }

    @Test
    void syncMembers_skipsMemberWhoseDepartmentMissing() {
        // off_employee.dept_id 为必填列，部门未同步时跳过成员镜像而不是写入空归属。
        when(repository.findLatestSyncTask(
                        TENANT, OfficePlatform.FEISHU, OfficeOrganizationSyncService.SYNC_TYPE_MEMBER))
                .thenReturn(Mono.empty());
        stubSaveSyncTaskPassthrough();
        when(organizationService.fetchMembers(TENANT, OfficePlatform.FEISHU, null))
                .thenReturn(Mono.just(OrganizationSyncResult.ofFinal(List.of(member("ou-9", "王五", "od-x")))));
        when(repository.findMemberMapping(TENANT, OfficePlatform.FEISHU, "ou-9"))
                .thenReturn(Mono.empty());
        when(offDepartmentRepository.findByExternalId("FEISHU", "od-x")).thenReturn(Flux.empty());
        when(orgUserQueryPort.resolveUsers(eq(TENANT), any())).thenReturn(Mono.just(List.of()));
        stubTransactionalPassthrough();

        StepVerifier.create(service.syncMembers(TENANT, OfficePlatform.FEISHU, false))
                .assertNext(result -> {
                    assertThat(result.success()).isTrue();
                    assertThat(result.syncedCount()).isZero();
                })
                .verifyComplete();

        verify(offEmployeeRepository, never()).insert(any());
        verify(repository, never()).saveOrganizationMapping(any());
    }

    @Test
    void syncMembers_degradesWhenUserResolutionReturnsEmpty() {
        when(repository.findLatestSyncTask(
                        TENANT, OfficePlatform.FEISHU, OfficeOrganizationSyncService.SYNC_TYPE_MEMBER))
                .thenReturn(Mono.empty());
        stubSaveSyncTaskPassthrough();
        when(organizationService.fetchMembers(TENANT, OfficePlatform.FEISHU, null))
                .thenReturn(Mono.just(OrganizationSyncResult.ofFinal(List.of(member("ou-1", "张三", "od-1")))));
        when(repository.findMemberMapping(TENANT, OfficePlatform.FEISHU, "ou-1"))
                .thenReturn(Mono.empty());
        OffDepartmentEntity dept = mirror(21L, "od-1");
        dept.setDeptName("研发部");
        when(offDepartmentRepository.findByExternalId("FEISHU", "od-1")).thenReturn(Flux.just(dept));
        when(offEmployeeRepository.findByExternalId("FEISHU", "ou-1")).thenReturn(Flux.empty());
        when(offEmployeeRepository.insert(any())).thenAnswer(invocation -> {
            OffEmployeeEntity entity = invocation.getArgument(0);
            entity.setId(31L);
            return Mono.just(1);
        });
        when(repository.saveOrganizationMapping(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(orgUserQueryPort.resolveUsers(eq(TENANT), any())).thenReturn(Mono.just(List.of()));
        stubTransactionalPassthrough();

        StepVerifier.create(service.syncMembers(TENANT, OfficePlatform.FEISHU, false))
                .assertNext(result -> assertThat(result.syncedCount()).isEqualTo(1))
                .verifyComplete();

        ArgumentCaptor<OfficeOrganizationMapping> mappingCaptor =
                ArgumentCaptor.forClass(OfficeOrganizationMapping.class);
        verify(repository).saveOrganizationMapping(mappingCaptor.capture());
        assertThat(mappingCaptor.getValue().getApexUserId()).isNull();
        assertThat(mappingCaptor.getValue().getApexDeptId()).isEqualTo("21");
    }

    // ========== 测试辅助 ==========

    private OfficeDepartment department(String externalDeptId, String name) {
        return department(externalDeptId, name, null);
    }

    private OfficeDepartment department(String externalDeptId, String name, String parentId) {
        return OfficeDepartment.builder()
                .externalDeptId(externalDeptId)
                .name(name)
                .parentId(parentId)
                .order(1)
                .build();
    }

    private OfficeMember member(String externalUserId, String name, String deptId) {
        return OfficeMember.builder()
                .externalUserId(externalUserId)
                .name(name)
                .deptIds(List.of(deptId))
                .position("工程师")
                .status("ACTIVE")
                .build();
    }

    private OffDepartmentEntity mirror(Long id, String externalId) {
        OffDepartmentEntity entity = new OffDepartmentEntity();
        entity.setId(id);
        entity.setExternalId(externalId);
        return entity;
    }

    /** 复制员工镜像的可观测字段，用于断言同步收尾阶段对已插入镜像的回填。 */
    private void copyEmployee(OffEmployeeEntity source, OffEmployeeEntity target) {
        target.setId(source.getId());
        target.setExternalId(source.getExternalId());
        target.setUserId(source.getUserId());
        target.setDeptId(source.getDeptId());
    }

    private void stubSaveSyncTaskPassthrough() {
        when(repository.saveSyncTask(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
    }

    /**
     * 部门同步的公共桩：映射不存在、镜像不存在时插入并按外部 ID 分配镜像主键、保存映射透传。
     *
     * <p>层级重算会按同步来源重新查询镜像表，未显式声明时返回空集，
     * 避免 Mockito 对未声明返回值返回 null 导致空指针。</p>
     */
    private void stubDepartmentMappingChain(Map<String, Long> mirrorIdsByExternalId) {
        when(repository.findDepartmentMapping(eq(TENANT), eq(OfficePlatform.FEISHU), any()))
                .thenReturn(Mono.empty());
        when(offDepartmentRepository.findByExternalId(eq("FEISHU"), any())).thenReturn(Flux.empty());
        when(offDepartmentRepository.insert(any())).thenAnswer(invocation -> {
            OffDepartmentEntity entity = invocation.getArgument(0);
            Long mirrorId = mirrorIdsByExternalId.get(entity.getExternalId());
            if (mirrorId == null) {
                throw new IllegalStateException("未声明镜像主键：" + entity.getExternalId());
            }
            entity.setId(mirrorId);
            return Mono.just(1);
        });
        when(repository.saveOrganizationMapping(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(offDepartmentRepository.findBySyncSource("FEISHU")).thenReturn(Flux.empty());
    }

    private void stubDepartmentMappingChain(String externalDeptId, Long mirrorId) {
        stubDepartmentMappingChain(Map.of(externalDeptId, mirrorId));
    }

    private void stubTransactionalPassthrough() {
        when(transactionalOperator.transactional(
                        ArgumentMatchers.<Mono<OfficeOrganizationSyncService.SyncTaskResult>>any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }
}
