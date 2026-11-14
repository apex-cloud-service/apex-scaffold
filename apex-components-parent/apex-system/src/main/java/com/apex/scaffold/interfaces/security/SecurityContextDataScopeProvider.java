package com.apex.scaffold.interfaces.security;

import com.apex.scaffold.common.context.DataScopeInfo;
import com.apex.scaffold.common.context.ReactiveDataScopeProvider;
import com.apex.scaffold.common.enums.DataScopeType;
import com.apex.scaffold.domain.model.entity.DeptEntity;
import com.apex.scaffold.domain.model.entity.RoleEntity;
import com.apex.scaffold.domain.repository.DeptRepository;
import com.apex.scaffold.domain.repository.RoleRepository;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * 从当前安全上下文解析用户数据权限的数据权限提供者。
 *
 * <p>从 {@link ReactiveSecurityContextHolder} 中取出已认证的 {@link AuthenticatedUser}，结合其角色
 * 的数据权限类型（全部/本人/本部门/本部门及子部门/自定义）解析出 {@link DataScopeInfo}，供数据层
 * 自动追加数据范围过滤条件。</p>
 *
 * @author Apex Team
 * @version 1.0
 * @since 1.0
 */
@Component
public class SecurityContextDataScopeProvider implements ReactiveDataScopeProvider {

    private final RoleRepository roleRepository;
    private final DeptRepository deptRepository;
    private final com.apex.scaffold.application.service.UnifiedAuthorizationService unifiedAuthorizationService;

    /**
     * 构建数据权限提供者（含统一授权服务，供 Spring 使用的主构造器）。
     *
     * @param roleRepository 角色仓储，用于查询用户角色及其数据权限
     * @param deptRepository 部门仓储，用于校验部门状态与解析部门及其子部门
     * @param unifiedAuthorizationService 统一授权服务，启用时优先经其查询角色，可为空
     */
    public SecurityContextDataScopeProvider(
            RoleRepository roleRepository,
            DeptRepository deptRepository,
            com.apex.scaffold.application.service.UnifiedAuthorizationService unifiedAuthorizationService) {
        this.unifiedAuthorizationService = unifiedAuthorizationService;
        this.roleRepository = roleRepository;
        this.deptRepository = deptRepository;
    }

    @Override
    public Mono<DataScopeInfo> resolve() {
        return ReactiveSecurityContextHolder.getContext()
                .map(context -> context.getAuthentication())
                .filter(Objects::nonNull)
                .filter(Authentication::isAuthenticated)
                .map(Authentication::getPrincipal)
                .ofType(AuthenticatedUser.class)
                .switchIfEmpty(Mono.error(new IllegalStateException("Authenticated user is required")))
                .flatMap(this::resolve);
    }

    private Mono<DataScopeInfo> resolve(AuthenticatedUser user) {
        if (user.userId() == null) {
            return Mono.error(new IllegalStateException("Authenticated user id is required"));
        }
        if (unifiedAuthorizationService != null && unifiedAuthorizationService.enabled()) {
            if (user.tenantId() == null || user.tenantId().isBlank()) {
                return Mono.error(new IllegalStateException("Trusted tenant required"));
            }
            return unifiedAuthorizationService
                    .roles(user.userId())
                    .flatMap(roles -> resolve(user, roles))
                    .transformDeferred(mono -> com.apex.scaffold.common.context.TenantContext.withTenant(mono))
                    .contextWrite(com.apex.scaffold.common.context.TenantContext.withTenant(user.tenantId()));
        }
        return roleRepository
                .findByUserId(user.userId())
                .switchIfEmpty(Mono.error(new IllegalStateException("No enabled role data scope")))
                .flatMap(roles -> resolve(user, roles));
    }

    private Mono<DataScopeInfo> resolve(AuthenticatedUser user, List<RoleEntity> roles) {
        List<RoleEntity> scopedRoles = roles.stream()
                .filter(RoleEntity::isEnabled)
                .filter(role -> role.getDataScope() != null)
                .toList();
        if (scopedRoles.isEmpty()) {
            return Mono.error(new IllegalStateException("No enabled role data scope"));
        }

        List<DataScopeType> types =
                scopedRoles.stream().map(RoleEntity::getDataScope).distinct().toList();
        if (types.size() != 1) {
            return Mono.error(new IllegalStateException("Conflicting data scopes"));
        }

        DataScopeType type = types.get(0);
        return switch (type) {
            case ALL -> Mono.just(DataScopeInfo.all());
            case SELF -> Mono.just(DataScopeInfo.ofSelf(user.userId()));
            case DEPT -> resolveDept(user);
            case DEPT_AND_CHILD -> resolveDeptAndChildren(user);
            case CUSTOM -> resolveCustom(scopedRoles);
        };
    }

    private Mono<Long> requireDept(AuthenticatedUser user) {
        return Mono.justOrEmpty(user.deptId())
                .switchIfEmpty(Mono.error(new IllegalStateException("Authenticated user department is required")));
    }

    private <T> Mono<T> nullSafeMono(Supplier<Mono<T>> supplier) {
        return Objects.requireNonNullElseGet(supplier.get(), () -> Mono.empty());
    }

    private Mono<DataScopeInfo> resolveDept(AuthenticatedUser user) {
        return requireDept(user).flatMap(deptId -> nullSafeMono(() -> deptRepository.findById(deptId))
                .filter(this::isEnabled)
                .map(department -> DataScopeInfo.ofDept(deptId))
                .switchIfEmpty(Mono.error(new IllegalStateException("No enabled department data scope"))));
    }

    private Mono<DataScopeInfo> resolveDeptAndChildren(AuthenticatedUser user) {
        return requireDept(user).flatMap(deptId -> nullSafeMono(deptRepository::findAll)
                .switchIfEmpty(Mono.error(new IllegalStateException("No enabled department data scope")))
                .flatMap(departments -> {
                    if (!hasEnabledMainDepartment(departments, deptId)) {
                        return Mono.error(new IllegalStateException("No enabled department data scope"));
                    }
                    List<Long> ids = collectEnabledDepartmentIds(departments, deptId);
                    return Mono.just(DataScopeInfo.ofDeptAndChild(deptId, ids));
                }));
    }

    private boolean hasEnabledMainDepartment(List<DeptEntity> departments, Long deptId) {
        return departments.stream()
                .filter(dept -> Objects.equals(dept.getId(), deptId))
                .anyMatch(this::isEnabled);
    }

    private List<Long> collectEnabledDepartmentIds(List<DeptEntity> departments, Long deptId) {
        return departments.stream()
                .filter(this::isEnabled)
                .filter(dept -> Objects.equals(dept.getId(), deptId) || containsAncestor(dept.getAncestors(), deptId))
                .map(DeptEntity::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private Mono<DataScopeInfo> resolveCustom(List<RoleEntity> roles) {
        List<Long> roleIds = roles.stream()
                .map(RoleEntity::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (roleIds.isEmpty()) {
            return Mono.error(new IllegalStateException("Custom data scope roles are required"));
        }
        return roleRepository
                .findDataScopeDeptIds(roleIds)
                .switchIfEmpty(Mono.error(new IllegalStateException("No enabled custom department data scope")))
                .flatMap(authorizedIds -> nullSafeMono(deptRepository::findAll)
                        .switchIfEmpty(Mono.error(new IllegalStateException("No enabled custom department data scope")))
                        .flatMap(departments -> {
                            List<Long> enabledIds = departments.stream()
                                    .filter(this::isEnabled)
                                    .map(DeptEntity::getId)
                                    .filter(authorizedIds::contains)
                                    .distinct()
                                    .toList();
                            if (enabledIds.isEmpty()) {
                                return Mono.error(new IllegalStateException("No enabled custom department data scope"));
                            }
                            return Mono.just(DataScopeInfo.ofCustom(enabledIds));
                        }));
    }

    private boolean isEnabled(DeptEntity department) {
        return Integer.valueOf(1).equals(department.getStatus());
    }

    private boolean containsAncestor(String ancestors, Long deptId) {
        if (ancestors == null || ancestors.isBlank()) {
            return false;
        }
        String expected = String.valueOf(deptId);
        return Arrays.stream(ancestors.split(",")).map(String::trim).anyMatch(expected::equals);
    }
}
