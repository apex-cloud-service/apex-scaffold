package com.apex.scaffold.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.apex.scaffold.application.assembler.AdminUserAssembler;
import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.domain.model.entity.RoleEntity;
import com.apex.scaffold.domain.model.entity.UserEntity;
import com.apex.scaffold.domain.repository.RoleRepository;
import com.apex.scaffold.domain.repository.UserRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * AdminUserManagementService 单元测试。
 *
 * <p>覆盖超级管理员用户检索与后台角色查询等通用管理能力。</p>
 *
 * @author Apex Team
 */
@ExtendWith(MockitoExtension.class)
class AdminUserManagementServiceTest {

    @Mock
    private UserRepository users;

    @Mock
    private RoleRepository roles;

    @Mock
    private AdminUserAssembler adminUserAssembler;

    private AdminUserManagementService service;

    /**
     * 构造被测服务，仅注入本测试用到的仓储依赖。
     */
    @BeforeEach
    void setUp() {
        service = new AdminUserManagementService(
                users, roles, null, null, null, null, null, null, null, null, null, adminUserAssembler);
    }

    /**
     * 超级管理员可分页检索用户并附带角色摘要。
     */
    @Test
    void search_ReturnsSummariesForSuperAdmin() {
        authorizeSuperAdmin();
        UserEntity target = UserEntity.create("target-open-id", null);
        target.setId(2L);
        target.setNickName("目标用户");
        when(users.searchForAdministration("目标", null, 1, 10))
                .thenReturn(Mono.just(PageResponse.of(List.of(target), 1L, 1, 10)));
        when(roles.findByUserId(2L)).thenReturn(Mono.just(List.of(RoleEntity.create("审核员", "PHOTO_REVIEWER", ""))));

        StepVerifier.create(service.search("admin-open-id", "目标", null, 1, 10))
                .assertNext(page -> {
                    assertThat(page.getTotal()).isEqualTo(1L);
                    assertThat(page.getList()).hasSize(1);
                    assertThat(page.getList().get(0).nickname()).isEqualTo("目标用户");
                })
                .verifyComplete();
    }

    /**
     * 非超级管理员访问被拒绝。
     */
    @Test
    void search_RejectsNonSuperAdmin() {
        when(users.findByOpenId("admin-open-id")).thenReturn(Mono.just(admin()));
        when(roles.findByUserId(1L)).thenReturn(Mono.just(List.of(RoleEntity.create("普通用户", "USER", ""))));
        when(users.searchForAdministration(null, null, 1, 10))
                .thenReturn(Mono.just(PageResponse.of(List.of(), 0L, 1, 10)));

        StepVerifier.create(service.search("admin-open-id", null, null, 1, 10))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(BusinessException.class);
                    assertThat(((BusinessException) error).getCode()).isEqualTo(ResultCode.FORBIDDEN.getCode());
                })
                .verify();
    }

    /**
     * 仅返回受后台管理的角色。
     */
    @Test
    void manageableRoles_ReturnsOnlyManagedRoles() {
        authorizeSuperAdmin();
        RoleEntity managed = RoleEntity.create("审核管理员", "REVIEW_MANAGER", "");
        RoleEntity unmanaged = RoleEntity.create("普通用户", "USER", "");
        when(roles.findAll()).thenReturn(Mono.just(List.of(managed, unmanaged)));
        when(adminUserAssembler.toRoleResponseList(any())).thenAnswer(invocation -> {
            List<RoleEntity> entities = invocation.getArgument(0);
            return entities.stream()
                    .map(e -> new com.apex.scaffold.application.dto.admin.AdminRoleResponse(
                            e.getId(), e.getName(), e.getCode()))
                    .toList();
        });

        StepVerifier.create(service.manageableRoles("admin-open-id"))
                .assertNext(result -> {
                    assertThat(result).hasSize(1);
                    assertThat(result.get(0).code()).isEqualTo("REVIEW_MANAGER");
                })
                .verifyComplete();
    }

    /** 构造具备超级管理员角色的操作者。 */
    private void authorizeSuperAdmin() {
        when(users.findByOpenId("admin-open-id")).thenReturn(Mono.just(admin()));
        when(roles.findByUserId(1L)).thenReturn(Mono.just(List.of(RoleEntity.create("超级管理员", "SUPER_ADMIN", ""))));
    }

    private UserEntity admin() {
        UserEntity admin = UserEntity.create("admin-open-id", "管理员");
        admin.setId(1L);
        return admin;
    }
}
