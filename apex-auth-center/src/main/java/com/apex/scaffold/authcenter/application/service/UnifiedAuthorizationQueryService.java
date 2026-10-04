package com.apex.scaffold.authcenter.application.service;

import com.apex.scaffold.authcenter.domain.model.entity.PermissionEntity;
import com.apex.scaffold.authcenter.domain.repository.PermissionRepository;
import com.apex.scaffold.authcenter.domain.repository.RoleRepository;
import com.apex.scaffold.authcenter.domain.repository.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 查询同租户有效账号的授权快照。
 *
 * @author Apex Scaffold Team
 */
@Service
@RequiredArgsConstructor
public class UnifiedAuthorizationQueryService {
    private final UserRepository users;
    private final RoleRepository roles;
    private final PermissionRepository permissions;

    public Mono<Authorization> queryForReader(String tenant, Long readerId, Long userId) {
        if (tenant == null || tenant.isBlank() || readerId == null || readerId <= 0) {
            return Mono.error(new org.springframework.security.access.AccessDeniedException("Forbidden"));
        }
        return users.findById(readerId)
                .filter(user -> tenant.equals(user.getTenantId())
                        && user.isActive()
                        && !user.isLocked()
                        && Integer.valueOf(0).equals(user.getDeleted()))
                .switchIfEmpty(Mono.error(new org.springframework.security.access.AccessDeniedException("Forbidden")))
                .flatMap(reader -> query(tenant, userId));
    }

    public Mono<Authorization> query(String tenant, Long userId) {
        if (tenant == null || tenant.isBlank() || userId == null || userId <= 0) {
            return Mono.error(new org.springframework.security.access.AccessDeniedException("Forbidden"));
        }
        return users.findById(userId)
                .filter(user -> tenant.equals(user.getTenantId())
                        && user.isActive()
                        && !user.isLocked()
                        && Integer.valueOf(0).equals(user.getDeleted()))
                .switchIfEmpty(Mono.error(new org.springframework.security.access.AccessDeniedException("Forbidden")))
                .flatMap(user -> roles.findByUserId(userId, tenant)
                        .filter(role -> tenant.equals(role.getTenantId()) && role.isValid())
                        .collectList())
                .flatMap(enabledRoles -> reactor.core.publisher.Flux.fromIterable(enabledRoles)
                        .flatMap(role -> permissions.findByRoleId(role.getId()))
                        .filter(permission -> tenant.equals(permission.getTenantId()) && permission.isValid())
                        .map(PermissionEntity::getCode)
                        .distinct()
                        .collectList()
                        .map(codes -> new Authorization(
                                tenant,
                                userId,
                                codes,
                                enabledRoles.stream()
                                        .map(role -> new Role(role.getCode(), role.getDataScope()))
                                        .toList())));
    }

    public record Authorization(String tenantId, Long userId, List<String> permissions, List<Role> roles) {}

    public record Role(String code, String dataScope) {}
}
