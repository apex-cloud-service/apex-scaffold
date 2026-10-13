# 阶段三后续实施指南

**项目**: Apex Scaffold 统一认证授权中心  
**阶段**: 权限管理与会话管理（阶段三）  
**文档版本**: v1.0  
**更新日期**: 2026-09-20

---

## 📋 执行总结

### ✅ 已完成核心服务（当前会话）

| 服务 | 文件路径 | 功能说明 |
|------|---------|---------|
| **RoleService** | [RoleService.java](../../../apex-auth/src/main/java/com/apex/scaffold/auth/application/service/RoleService.java) | 角色 CRUD、权限分配、用户角色管理 |
| **PermissionService** | [PermissionService.java](../../../apex-auth/src/main/java/com/apex/scaffold/auth/application/service/PermissionService.java) | 权限 CRUD、权限查询、批量操作 |
| **SessionService** | [SessionService.java](../../../apex-auth/src/main/java/com/apex/scaffold/auth/application/service/SessionService.java) | 会话创建、在线管理、并发控制 |
| **AuthorizationService** | [AuthorizationService.java](../../../apex-auth/src/main/java/com/apex/scaffold/auth/application/service/AuthorizationService.java) | RBAC 权限验证、资源访问控制 |

**核心功能**:
- ✅ 角色与权限管理完整生命周期
- ✅ 用户-角色-权限关联
- ✅ 会话管理与在线用户统计
- ✅ 并发会话限制（最大5个）
- ✅ 超级管理员自动权限
- ✅ 数据权限范围控制

---

## 🚧 待实施内容（下一会话）

### 1. DTO 定义（5个文件）

#### 1.1 角色管理 DTO

**文件**: `apex-auth-center/src/main/java/com/apex/scaffold/authcenter/application/dto/RoleDTO.java`

```java
package com.apex.scaffold.authcenter.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 角色DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleDTO {

    private Long id;

    @NotBlank(message = "租户ID不能为空")
    private String tenantId;

    @NotBlank(message = "角色编码不能为空")
    @Size(max = 50, message = "角色编码长度不能超过50")
    private String code;

    @NotBlank(message = "角色名称不能为空")
    @Size(max = 100, message = "角色名称长度不能超过100")
    private String name;

    @Size(max = 500, message = "描述长度不能超过500")
    private String description;

    private String dataScope;

    private Integer sortOrder;

    private Boolean enabled;

    private Set<Long> permissionIds;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
```

**文件**: `apex-auth-center/src/main/java/com/apex/scaffold/authcenter/application/dto/AssignRoleRequest.java`

```java
package com.apex.scaffold.authcenter.application.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.Set;
import lombok.Data;

/**
 * 分配角色请求
 */
@Data
public class AssignRoleRequest {

    @NotNull(message = "用户ID不能为空")
    private Long userId;

    @NotEmpty(message = "角色ID列表不能为空")
    private Set<Long> roleIds;

    private String tenantId;
}
```

#### 1.2 权限管理 DTO

**文件**: `apex-auth-center/src/main/java/com/apex/scaffold/authcenter/application/dto/PermissionDTO.java`

```java
package com.apex.scaffold.authcenter.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 权限DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PermissionDTO {

    private Long id;

    @NotBlank(message = "租户ID不能为空")
    private String tenantId;

    @NotBlank(message = "权限编码不能为空")
    @Size(max = 100, message = "权限编码长度不能超过100")
    private String code;

    @NotBlank(message = "权限名称不能为空")
    @Size(max = 100, message = "权限名称长度不能超过100")
    private String name;

    @Size(max = 500, message = "描述长度不能超过500")
    private String description;

    private String resourceType;

    private String resourceIdentifier;

    private String action;

    private Boolean enabled;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
```

#### 1.3 会话管理 DTO

**文件**: `apex-auth-center/src/main/java/com/apex/scaffold/authcenter/application/dto/SessionDTO.java`

```java
package com.apex.scaffold.authcenter.application.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 会话DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionDTO {

    private Long id;

    private String sessionId;

    private Long userId;

    private String tenantId;

    private String deviceType;

    private String deviceId;

    private String deviceName;

    private String ipAddress;

    private String location;

    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime lastActiveAt;

    private LocalDateTime expiresAt;

    private Boolean isCurrentSession;
}
```

**文件**: `apex-auth-center/src/main/java/com/apex/scaffold/authcenter/application/dto/OnlineUserDTO.java`

```java
package com.apex.scaffold.authcenter.application.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 在线用户DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OnlineUserDTO {

    private Long userId;

    private String username;

    private String tenantId;

    private Integer activeSessionCount;

    private LocalDateTime lastActiveAt;

    private String latestIpAddress;

    private String latestDeviceType;
}
```

---

### 2. MapStruct Assembler（3个文件）

#### 2.1 角色 Assembler

**文件**: `apex-auth-center/src/main/java/com/apex/scaffold/authcenter/application/assembler/RoleAssembler.java`

```java
package com.apex.scaffold.authcenter.application.assembler;

import com.apex.scaffold.authcenter.application.dto.RoleDTO;
import com.apex.scaffold.authcenter.domain.model.entity.RoleEntity;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * 角色实体与DTO转换器
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface RoleAssembler {

    RoleDTO toDto(RoleEntity entity);

    RoleEntity toEntity(RoleDTO dto);
}
```

#### 2.2 权限 Assembler

**文件**: `apex-auth-center/src/main/java/com/apex/scaffold/authcenter/application/assembler/PermissionAssembler.java`

```java
package com.apex.scaffold.authcenter.application.assembler;

import com.apex.scaffold.authcenter.application.dto.PermissionDTO;
import com.apex.scaffold.authcenter.domain.model.entity.PermissionEntity;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * 权限实体与DTO转换器
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PermissionAssembler {

    PermissionDTO toDto(PermissionEntity entity);

    PermissionEntity toEntity(PermissionDTO dto);
}
```

#### 2.3 会话 Assembler

**文件**: `apex-auth-center/src/main/java/com/apex/scaffold/authcenter/application/assembler/SessionAssembler.java`

```java
package com.apex.scaffold.authcenter.application.assembler;

import com.apex.scaffold.authcenter.application.dto.SessionDTO;
import com.apex.scaffold.authcenter.domain.model.entity.SessionEntity;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * 会话实体与DTO转换器
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface SessionAssembler {

    SessionDTO toDto(SessionEntity entity);

    SessionEntity toEntity(SessionDTO dto);
}
```

---

### 3. Handler 实现（3个文件）

#### 3.1 角色管理 Handler

**文件**: `apex-auth-center/src/main/java/com/apex/scaffold/authcenter/interfaces/handler/RoleHandler.java`

```java
package com.apex.scaffold.authcenter.interfaces.handler;

import com.apex.scaffold.authcenter.application.assembler.RoleAssembler;
import com.apex.scaffold.authcenter.application.dto.AssignRoleRequest;
import com.apex.scaffold.authcenter.application.dto.RoleDTO;
import com.apex.scaffold.authcenter.application.service.RoleService;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.common.util.TraceIdUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * 角色管理Handler
 *
 * <p>处理角色相关的HTTP请求
 *
 * @author Apex Scaffold Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RoleHandler {

    private final RoleService roleService;
    private final RoleAssembler roleAssembler;

    /**
     * 创建角色
     */
    public Mono<ServerResponse> createRole(ServerRequest request) {
        return request
                .bodyToMono(RoleDTO.class)
                .map(roleAssembler::toEntity)
                .flatMap(roleService::createRole)
                .map(roleAssembler::toDto)
                .flatMap(role -> ServerResponse.ok()
                        .bodyValue(Result.success(role, TraceIdUtil.getTraceId())));
    }

    /**
     * 获取角色详情
     */
    public Mono<ServerResponse> getRoleById(ServerRequest request) {
        Long roleId = Long.valueOf(request.pathVariable("id"));
        return roleService
                .getRoleById(roleId)
                .map(roleAssembler::toDto)
                .flatMap(role -> ServerResponse.ok()
                        .bodyValue(Result.success(role, TraceIdUtil.getTraceId())));
    }

    /**
     * 获取租户所有角色
     */
    public Mono<ServerResponse> getRolesByTenantId(ServerRequest request) {
        String tenantId = request.queryParam("tenantId")
                .orElseThrow(() -> new IllegalArgumentException("租户ID不能为空"));

        return roleService
                .getRolesByTenantId(tenantId)
                .map(roleAssembler::toDto)
                .collectList()
                .flatMap(roles -> ServerResponse.ok()
                        .bodyValue(Result.success(roles, TraceIdUtil.getTraceId())));
    }

    /**
     * 更新角色
     */
    public Mono<ServerResponse> updateRole(ServerRequest request) {
        return request
                .bodyToMono(RoleDTO.class)
                .map(roleAssembler::toEntity)
                .flatMap(roleService::updateRole)
                .map(roleAssembler::toDto)
                .flatMap(role -> ServerResponse.ok()
                        .bodyValue(Result.success(role, TraceIdUtil.getTraceId())));
    }

    /**
     * 删除角色
     */
    public Mono<ServerResponse> deleteRole(ServerRequest request) {
        Long roleId = Long.valueOf(request.pathVariable("id"));
        return roleService
                .deleteRole(roleId)
                .then(ServerResponse.ok()
                        .bodyValue(Result.success("角色删除成功", TraceIdUtil.getTraceId())));
    }

    /**
     * 为角色分配权限
     */
    public Mono<ServerResponse> assignPermissions(ServerRequest request) {
        Long roleId = Long.valueOf(request.pathVariable("id"));
        return request
                .bodyToMono(AssignRoleRequest.class)
                .flatMap(req -> roleService.assignPermissionsToRole(roleId, req.getRoleIds()))
                .then(ServerResponse.ok()
                        .bodyValue(Result.success("权限分配成功", TraceIdUtil.getTraceId())));
    }

    /**
     * 为用户分配角色
     */
    public Mono<ServerResponse> assignRolesToUser(ServerRequest request) {
        return request
                .bodyToMono(AssignRoleRequest.class)
                .flatMap(req -> roleService.assignRolesToUser(
                        req.getUserId(), req.getRoleIds(), req.getTenantId()))
                .then(ServerResponse.ok()
                        .bodyValue(Result.success("用户角色分配成功", TraceIdUtil.getTraceId())));
    }
}
```

#### 3.2 权限管理 Handler

**文件**: `apex-auth-center/src/main/java/com/apex/scaffold/authcenter/interfaces/handler/PermissionHandler.java`

```java
package com.apex.scaffold.authcenter.interfaces.handler;

import com.apex.scaffold.authcenter.application.assembler.PermissionAssembler;
import com.apex.scaffold.authcenter.application.dto.PermissionDTO;
import com.apex.scaffold.authcenter.application.service.PermissionService;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.common.util.TraceIdUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * 权限管理Handler
 *
 * <p>处理权限相关的HTTP请求
 *
 * @author Apex Scaffold Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PermissionHandler {

    private final PermissionService permissionService;
    private final PermissionAssembler permissionAssembler;

    /**
     * 创建权限
     */
    public Mono<ServerResponse> createPermission(ServerRequest request) {
        return request
                .bodyToMono(PermissionDTO.class)
                .map(permissionAssembler::toEntity)
                .flatMap(permissionService::createPermission)
                .map(permissionAssembler::toDto)
                .flatMap(permission -> ServerResponse.ok()
                        .bodyValue(Result.success(permission, TraceIdUtil.getTraceId())));
    }

    /**
     * 获取权限详情
     */
    public Mono<ServerResponse> getPermissionById(ServerRequest request) {
        Long permissionId = Long.valueOf(request.pathVariable("id"));
        return permissionService
                .getPermissionById(permissionId)
                .map(permissionAssembler::toDto)
                .flatMap(permission -> ServerResponse.ok()
                        .bodyValue(Result.success(permission, TraceIdUtil.getTraceId())));
    }

    /**
     * 获取租户所有权限
     */
    public Mono<ServerResponse> getPermissionsByTenantId(ServerRequest request) {
        String tenantId = request.queryParam("tenantId")
                .orElseThrow(() -> new IllegalArgumentException("租户ID不能为空"));

        return permissionService
                .getPermissionsByTenantId(tenantId)
                .map(permissionAssembler::toDto)
                .collectList()
                .flatMap(permissions -> ServerResponse.ok()
                        .bodyValue(Result.success(permissions, TraceIdUtil.getTraceId())));
    }

    /**
     * 获取角色的权限
     */
    public Mono<ServerResponse> getPermissionsByRoleId(ServerRequest request) {
        Long roleId = Long.valueOf(request.pathVariable("roleId"));
        return permissionService
                .getPermissionsByRoleId(roleId)
                .map(permissionAssembler::toDto)
                .collectList()
                .flatMap(permissions -> ServerResponse.ok()
                        .bodyValue(Result.success(permissions, TraceIdUtil.getTraceId())));
    }

    /**
     * 更新权限
     */
    public Mono<ServerResponse> updatePermission(ServerRequest request) {
        return request
                .bodyToMono(PermissionDTO.class)
                .map(permissionAssembler::toEntity)
                .flatMap(permissionService::updatePermission)
                .map(permissionAssembler::toDto)
                .flatMap(permission -> ServerResponse.ok()
                        .bodyValue(Result.success(permission, TraceIdUtil.getTraceId())));
    }

    /**
     * 删除权限
     */
    public Mono<ServerResponse> deletePermission(ServerRequest request) {
        Long permissionId = Long.valueOf(request.pathVariable("id"));
        return permissionService
                .deletePermission(permissionId)
                .then(ServerResponse.ok()
                        .bodyValue(Result.success("权限删除成功", TraceIdUtil.getTraceId())));
    }
}
```

#### 3.3 会话管理 Handler

**文件**: `apex-auth-center/src/main/java/com/apex/scaffold/authcenter/interfaces/handler/SessionHandler.java`

```java
package com.apex.scaffold.authcenter.interfaces.handler;

import com.apex.scaffold.authcenter.application.assembler.SessionAssembler;
import com.apex.scaffold.authcenter.application.service.SessionService;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.common.util.TraceIdUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * 会话管理Handler
 *
 * <p>处理会话相关的HTTP请求
 *
 * @author Apex Scaffold Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionHandler {

    private final SessionService sessionService;
    private final SessionAssembler sessionAssembler;

    /**
     * 获取用户的所有会话
     */
    public Mono<ServerResponse> getUserSessions(ServerRequest request) {
        Long userId = Long.valueOf(request.pathVariable("userId"));
        String tenantId = request.queryParam("tenantId")
                .orElseThrow(() -> new IllegalArgumentException("租户ID不能为空"));

        return sessionService
                .getUserSessions(userId, tenantId)
                .map(sessionAssembler::toDto)
                .collectList()
                .flatMap(sessions -> ServerResponse.ok()
                        .bodyValue(Result.success(sessions, TraceIdUtil.getTraceId())));
    }

    /**
     * 获取用户的活跃会话
     */
    public Mono<ServerResponse> getActiveSessions(ServerRequest request) {
        Long userId = Long.valueOf(request.pathVariable("userId"));
        String tenantId = request.queryParam("tenantId")
                .orElseThrow(() -> new IllegalArgumentException("租户ID不能为空"));

        return sessionService
                .getActiveSessions(userId, tenantId)
                .map(sessionAssembler::toDto)
                .collectList()
                .flatMap(sessions -> ServerResponse.ok()
                        .bodyValue(Result.success(sessions, TraceIdUtil.getTraceId())));
    }

    /**
     * 终止指定会话
     */
    public Mono<ServerResponse> terminateSession(ServerRequest request) {
        String sessionId = request.pathVariable("sessionId");
        return sessionService
                .terminateSession(sessionId)
                .then(ServerResponse.ok()
                        .bodyValue(Result.success("会话终止成功", TraceIdUtil.getTraceId())));
    }

    /**
     * 终止用户的所有会话
     */
    public Mono<ServerResponse> terminateUserSessions(ServerRequest request) {
        Long userId = Long.valueOf(request.pathVariable("userId"));
        String tenantId = request.queryParam("tenantId")
                .orElseThrow(() -> new IllegalArgumentException("租户ID不能为空"));

        return sessionService
                .terminateUserSessions(userId, tenantId)
                .then(ServerResponse.ok()
                        .bodyValue(Result.success("用户所有会话已终止", TraceIdUtil.getTraceId())));
    }

    /**
     * 踢出会话
     */
    public Mono<ServerResponse> kickOutSession(ServerRequest request) {
        String sessionId = request.pathVariable("sessionId");
        return sessionService
                .kickOutSession(sessionId)
                .then(ServerResponse.ok()
                        .bodyValue(Result.success("会话已踢出", TraceIdUtil.getTraceId())));
    }

    /**
     * 获取在线用户数量
     */
    public Mono<ServerResponse> getOnlineUserCount(ServerRequest request) {
        String tenantId = request.queryParam("tenantId")
                .orElseThrow(() -> new IllegalArgumentException("租户ID不能为空"));

        return sessionService
                .getOnlineUserCount(tenantId)
                .flatMap(count -> ServerResponse.ok()
                        .bodyValue(Result.success(count, TraceIdUtil.getTraceId())));
    }
}
```

---

### 4. Router 配置（3个文件）

#### 4.1 角色路由

**文件**: `apex-auth-center/src/main/java/com/apex/scaffold/authcenter/interfaces/router/RoleRouter.java`

```java
package com.apex.scaffold.authcenter.interfaces.router;

import com.apex.scaffold.authcenter.interfaces.handler.RoleHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

/**
 * 角色管理路由配置
 *
 * @author Apex Scaffold Team
 */
@Configuration
@RequiredArgsConstructor
public class RoleRouter {

    private final RoleHandler roleHandler;

    @Bean
    public RouterFunction<ServerResponse> roleRoutes() {
        return RouterFunctions.route()
                .POST("/auth/v1/roles",
                        RequestPredicates.accept(MediaType.APPLICATION_JSON),
                        roleHandler::createRole)
                .GET("/auth/v1/roles/{id}",
                        RequestPredicates.accept(MediaType.APPLICATION_JSON),
                        roleHandler::getRoleById)
                .GET("/auth/v1/roles",
                        RequestPredicates.accept(MediaType.APPLICATION_JSON),
                        roleHandler::getRolesByTenantId)
                .PUT("/auth/v1/roles/{id}",
                        RequestPredicates.accept(MediaType.APPLICATION_JSON),
                        roleHandler::updateRole)
                .DELETE("/auth/v1/roles/{id}",
                        RequestPredicates.accept(MediaType.APPLICATION_JSON),
                        roleHandler::deleteRole)
                .POST("/auth/v1/roles/{id}/permissions",
                        RequestPredicates.accept(MediaType.APPLICATION_JSON),
                        roleHandler::assignPermissions)
                .POST("/auth/v1/roles/assign",
                        RequestPredicates.accept(MediaType.APPLICATION_JSON),
                        roleHandler::assignRolesToUser)
                .build();
    }
}
```

#### 4.2 权限路由

**文件**: `apex-auth-center/src/main/java/com/apex/scaffold/authcenter/interfaces/router/PermissionRouter.java`

```java
package com.apex.scaffold.authcenter.interfaces.router;

import com.apex.scaffold.authcenter.interfaces.handler.PermissionHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

/**
 * 权限管理路由配置
 *
 * @author Apex Scaffold Team
 */
@Configuration
@RequiredArgsConstructor
public class PermissionRouter {

    private final PermissionHandler permissionHandler;

    @Bean
    public RouterFunction<ServerResponse> permissionRoutes() {
        return RouterFunctions.route()
                .POST("/auth/v1/permissions",
                        RequestPredicates.accept(MediaType.APPLICATION_JSON),
                        permissionHandler::createPermission)
                .GET("/auth/v1/permissions/{id}",
                        RequestPredicates.accept(MediaType.APPLICATION_JSON),
                        permissionHandler::getPermissionById)
                .GET("/auth/v1/permissions",
                        RequestPredicates.accept(MediaType.APPLICATION_JSON),
                        permissionHandler::getPermissionsByTenantId)
                .GET("/auth/v1/roles/{roleId}/permissions",
                        RequestPredicates.accept(MediaType.APPLICATION_JSON),
                        permissionHandler::getPermissionsByRoleId)
                .PUT("/auth/v1/permissions/{id}",
                        RequestPredicates.accept(MediaType.APPLICATION_JSON),
                        permissionHandler::updatePermission)
                .DELETE("/auth/v1/permissions/{id}",
                        RequestPredicates.accept(MediaType.APPLICATION_JSON),
                        permissionHandler::deletePermission)
                .build();
    }
}
```

#### 4.3 会话路由

**文件**: `apex-auth-center/src/main/java/com/apex/scaffold/authcenter/interfaces/router/SessionRouter.java`

```java
package com.apex.scaffold.authcenter.interfaces.router;

import com.apex.scaffold.authcenter.interfaces.handler.SessionHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

/**
 * 会话管理路由配置
 *
 * @author Apex Scaffold Team
 */
@Configuration
@RequiredArgsConstructor
public class SessionRouter {

    private final SessionHandler sessionHandler;

    @Bean
    public RouterFunction<ServerResponse> sessionRoutes() {
        return RouterFunctions.route()
                .GET("/auth/v1/users/{userId}/sessions",
                        RequestPredicates.accept(MediaType.APPLICATION_JSON),
                        sessionHandler::getUserSessions)
                .GET("/auth/v1/users/{userId}/sessions/active",
                        RequestPredicates.accept(MediaType.APPLICATION_JSON),
                        sessionHandler::getActiveSessions)
                .DELETE("/auth/v1/sessions/{sessionId}",
                        RequestPredicates.accept(MediaType.APPLICATION_JSON),
                        sessionHandler::terminateSession)
                .DELETE("/auth/v1/users/{userId}/sessions",
                        RequestPredicates.accept(MediaType.APPLICATION_JSON),
                        sessionHandler::terminateUserSessions)
                .POST("/auth/v1/sessions/{sessionId}/kickout",
                        RequestPredicates.accept(MediaType.APPLICATION_JSON),
                        sessionHandler::kickOutSession)
                .GET("/auth/v1/online/count",
                        RequestPredicates.accept(MediaType.APPLICATION_JSON),
                        sessionHandler::getOnlineUserCount)
                .build();
    }
}
```

---

### 5. 集成测试（3个文件）

#### 5.1 角色服务测试

**文件**: `apex-auth-center/src/test/java/com/apex/scaffold/authcenter/application/service/RoleServiceTest.java`

```java
package com.apex.scaffold.authcenter.application.service;

import com.apex.scaffold.authcenter.domain.model.entity.RoleEntity;
import com.apex.scaffold.authcenter.domain.repository.RoleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.test.StepVerifier;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 角色服务集成测试
 */
@SpringBootTest
class RoleServiceTest {

    @Autowired
    private RoleService roleService;

    @Autowired
    private RoleRepository roleRepository;

    @Test
    void testCreateRole() {
        RoleEntity role = RoleEntity.builder()
                .code("TEST_ROLE")
                .name("测试角色")
                .tenantId("default")
                .dataScope("SELF")
                .enabled(true)
                .build();

        StepVerifier.create(roleService.createRole(role))
                .assertNext(created -> {
                    assertThat(created.getId()).isNotNull();
                    assertThat(created.getCode()).isEqualTo("TEST_ROLE");
                })
                .verifyComplete();
    }

    @Test
    void testAssignPermissionsToRole() {
        // 先创建角色
        RoleEntity role = RoleEntity.builder()
                .code("PERMISSION_TEST")
                .name("权限测试")
                .tenantId("default")
                .enabled(true)
                .build();

        StepVerifier.create(
                roleService.createRole(role)
                        .flatMap(created -> roleService.assignPermissionsToRole(
                                created.getId(), Set.of(1L, 2L, 3L))
                                .thenReturn(created.getId()))
                        .flatMap(roleRepository::findById)
        )
                .assertNext(updated -> {
                    assertThat(updated.getPermissionIds()).hasSize(3);
                })
                .verifyComplete();
    }
}
```

#### 5.2 权限服务测试

**文件**: `apex-auth-center/src/test/java/com/apex/scaffold/authcenter/application/service/PermissionServiceTest.java`

```java
package com.apex.scaffold.authcenter.application.service;

import com.apex.scaffold.authcenter.domain.model.entity.PermissionEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 权限服务集成测试
 */
@SpringBootTest
class PermissionServiceTest {

    @Autowired
    private PermissionService permissionService;

    @Test
    void testCreatePermission() {
        PermissionEntity permission = PermissionEntity.builder()
                .code("test:read")
                .name("测试读取权限")
                .tenantId("default")
                .resourceType("API")
                .resourceIdentifier("/test")
                .action("READ")
                .enabled(true)
                .build();

        StepVerifier.create(permissionService.createPermission(permission))
                .assertNext(created -> {
                    assertThat(created.getId()).isNotNull();
                    assertThat(created.getCode()).isEqualTo("test:read");
                })
                .verifyComplete();
    }

    @Test
    void testGetEnabledPermissions() {
        StepVerifier.create(permissionService.getEnabledPermissions("default"))
                .expectNextCount(0) // 根据实际种子数据调整
                .verifyComplete();
    }
}
```

#### 5.3 会话服务测试

**文件**: `apex-auth-center/src/test/java/com/apex/scaffold/authcenter/application/service/SessionServiceTest.java`

```java
package com.apex.scaffold.authcenter.application.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 会话服务集成测试
 */
@SpringBootTest
class SessionServiceTest {

    @Autowired
    private SessionService sessionService;

    @Test
    void testCreateSession() {
        StepVerifier.create(sessionService.createSession(
                1L, "default", 1L, "WEB", "device-001", "127.0.0.1", "Test-Agent"
        ))
                .assertNext(session -> {
                    assertThat(session.getId()).isNotNull();
                    assertThat(session.getSessionId()).isNotNull();
                    assertThat(session.getStatus()).isEqualTo("ACTIVE");
                })
                .verifyComplete();
    }

    @Test
    void testGetActiveSessions() {
        StepVerifier.create(sessionService.getActiveSessions(1L, "default"))
                .expectNextCount(0) // 根据实际数据调整
                .verifyComplete();
    }

    @Test
    void testTerminateSession() {
        // 先创建会话
        StepVerifier.create(
                sessionService.createSession(1L, "default", 1L, "WEB", "d1", "127.0.0.1", "UA")
                        .flatMap(created -> sessionService.terminateSession(created.getSessionId()))
        )
                .verifyComplete();
    }
}
```

---

### 6. 定时任务（1个文件）

**文件**: `apex-auth-center/src/main/java/com/apex/scaffold/authcenter/config/SessionCleanupScheduler.java`

```java
package com.apex.scaffold.authcenter.config;

import com.apex.scaffold.authcenter.application.service.SessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 会话清理定时任务
 *
 * <p>每小时清理过期会话
 *
 * @author Apex Scaffold Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionCleanupScheduler {

    private final SessionService sessionService;

    /**
     * 每小时清理过期会话
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void cleanupExpiredSessions() {
        log.info("开始清理过期会话");
        sessionService.cleanupExpiredSessions()
                .subscribe(
                        count -> log.info("过期会话清理完成: count={}", count),
                        error -> log.error("过期会话清理失败", error)
                );
    }
}
```

**配置启用定时任务**（在 `AuthCenterApplication` 或新建配置类添加）:
```java
@EnableScheduling
```

---

### 7. 种子数据（1个文件）

**文件**: `apex-auth-center/src/main/resources/db/migration/mysql/V47__insert_roles_and_permissions.sql`

```sql
-- 插入角色
INSERT INTO auth_role (tenant_id, code, name, description, data_scope, enabled, created_at, updated_at, deleted)
VALUES
    ('default', 'SUPER_ADMIN', '超级管理员', '拥有系统所有权限', 'ALL', 1, NOW(), NOW(), 0),
    ('default', 'ADMIN', '管理员', '拥有大部分管理权限', 'DEPT_AND_SUB', 1, NOW(), NOW(), 0),
    ('default', 'USER', '普通用户', '普通业务权限', 'SELF', 1, NOW(), NOW(), 0);

-- 插入权限
INSERT INTO auth_permission (tenant_id, code, name, resource_type, resource_identifier, action, enabled, created_at, updated_at, deleted)
VALUES
    ('default', 'user:read', '用户查询', 'API', '/users', 'READ', 1, NOW(), NOW(), 0),
    ('default', 'user:write', '用户编辑', 'API', '/users', 'WRITE', 1, NOW(), NOW(), 0),
    ('default', 'user:delete', '用户删除', 'API', '/users', 'DELETE', 1, NOW(), NOW(), 0),
    ('default', 'role:read', '角色查询', 'API', '/roles', 'READ', 1, NOW(), NOW(), 0),
    ('default', 'role:write', '角色管理', 'API', '/roles', 'WRITE', 1, NOW(), NOW(), 0),
    ('default', 'permission:read', '权限查询', 'API', '/permissions', 'READ', 1, NOW(), NOW(), 0),
    ('default', 'permission:write', '权限管理', 'API', '/permissions', 'WRITE', 1, NOW(), NOW(), 0),
    ('default', 'session:read', '会话查询', 'API', '/sessions', 'READ', 1, NOW(), NOW(), 0),
    ('default', 'session:write', '会话管理', 'API', '/sessions', 'WRITE', 1, NOW(), NOW(), 0);

-- 为 SUPER_ADMIN 分配所有权限
INSERT INTO auth_role_permission (tenant_id, role_id, permission_id, created_at)
SELECT 'default', r.id, p.id, NOW()
FROM auth_role r, auth_permission p
WHERE r.code = 'SUPER_ADMIN' AND r.tenant_id = 'default' AND p.tenant_id = 'default';

-- 为 ADMIN 分配部分权限（除删除外）
INSERT INTO auth_role_permission (tenant_id, role_id, permission_id, created_at)
SELECT 'default', r.id, p.id, NOW()
FROM auth_role r, auth_permission p
WHERE r.code = 'ADMIN' AND r.tenant_id = 'default'
  AND p.code NOT LIKE '%:delete' AND p.tenant_id = 'default';

-- 为 USER 分配只读权限
INSERT INTO auth_role_permission (tenant_id, role_id, permission_id, created_at)
SELECT 'default', r.id, p.id, NOW()
FROM auth_role r, auth_permission p
WHERE r.code = 'USER' AND r.tenant_id = 'default'
  AND p.code LIKE '%:read' AND p.tenant_id = 'default';

-- 为测试用户分配角色
INSERT INTO auth_user_role (tenant_id, user_id, role_id, created_at)
SELECT 'default', u.id, r.id, NOW()
FROM auth_user u, auth_role r
WHERE u.username = 'admin' AND u.tenant_id = 'default'
  AND r.code = 'SUPER_ADMIN' AND r.tenant_id = 'default';

INSERT INTO auth_user_role (tenant_id, user_id, role_id, created_at)
SELECT 'default', u.id, r.id, NOW()
FROM auth_user u, auth_role r
WHERE u.username = 'user' AND u.tenant_id = 'default'
  AND r.code = 'USER' AND r.tenant_id = 'default';
```

**PostgreSQL 版本**: `apex-auth-center/src/main/resources/db/migration/postgresql/V47__insert_roles_and_permissions.sql`（内容相同）

---

## 🔧 实施步骤

### 步骤 1：创建 DTO 和 Assembler
```bash
# 创建 5 个 DTO 文件
# 创建 3 个 Assembler 接口
# 验证 MapStruct 生成实现类
mvnd clean compile -pl apex-auth-center
```

### 步骤 2：创建 Handler 和 Router
```bash
# 创建 3 个 Handler 文件
# 创建 3 个 Router 配置文件
# 验证路由注册
mvnd clean compile -pl apex-auth-center
```

### 步骤 3：添加种子数据
```bash
# 创建 V47 Flyway 脚本（MySQL 和 PostgreSQL）
# 启动应用自动执行迁移
mvnd spring-boot:run
```

### 步骤 4：编写集成测试
```bash
# 创建 3 个测试类
# 运行测试验证功能
mvnd test -pl apex-auth-center
```

### 步骤 5：添加定时任务
```bash
# 创建 SessionCleanupScheduler
# 启用 @EnableScheduling
# 验证定时任务执行
```

---

## ✅ 验证清单

### 编译验证
- [ ] 所有代码编译通过
- [ ] MapStruct 生成实现类
- [ ] 无 PMD 违规

### 功能验证
- [ ] 角色 CRUD 接口正常
- [ ] 权限 CRUD 接口正常
- [ ] 会话管理接口正常
- [ ] 角色权限分配成功
- [ ] 用户角色分配成功
- [ ] 权限校验正确

### 测试验证
- [ ] 单元测试全部通过
- [ ] 集成测试全部通过
- [ ] 覆盖率达到 60%+

### 数据验证
- [ ] Flyway V47 执行成功
- [ ] 种子角色和权限插入
- [ ] 测试用户分配角色

---

## 📊 API 接口清单

### 角色管理
```
POST   /auth/v1/roles                    创建角色
GET    /auth/v1/roles/{id}               获取角色详情
GET    /auth/v1/roles                    获取租户所有角色
PUT    /auth/v1/roles/{id}               更新角色
DELETE /auth/v1/roles/{id}               删除角色
POST   /auth/v1/roles/{id}/permissions   为角色分配权限
POST   /auth/v1/roles/assign             为用户分配角色
```

### 权限管理
```
POST   /auth/v1/permissions              创建权限
GET    /auth/v1/permissions/{id}         获取权限详情
GET    /auth/v1/permissions              获取租户所有权限
GET    /auth/v1/roles/{roleId}/permissions  获取角色的权限
PUT    /auth/v1/permissions/{id}         更新权限
DELETE /auth/v1/permissions/{id}         删除权限
```

### 会话管理
```
GET    /auth/v1/users/{userId}/sessions           获取用户所有会话
GET    /auth/v1/users/{userId}/sessions/active    获取用户活跃会话
DELETE /auth/v1/sessions/{sessionId}              终止指定会话
DELETE /auth/v1/users/{userId}/sessions           终止用户所有会话
POST   /auth/v1/sessions/{sessionId}/kickout      踢出会话
GET    /auth/v1/online/count                      获取在线用户数
```

---

## 📝 测试用例示例

### 测试角色创建
```bash
curl -X POST http://localhost:8081/auth/v1/roles \
  -H "Content-Type: application/json" \
  -d '{
    "code": "DEVELOPER",
    "name": "开发者",
    "tenantId": "default",
    "dataScope": "DEPT",
    "enabled": true
  }'
```

### 测试权限分配
```bash
curl -X POST http://localhost:8081/auth/v1/roles/1/permissions \
  -H "Content-Type: application/json" \
  -d '{
    "roleIds": [1, 2, 3]
  }'
```

### 测试会话查询
```bash
curl -X GET "http://localhost:8081/auth/v1/users/1/sessions?tenantId=default"
```

---

## 🎯 完成标准

### 代码质量
- ✅ 所有代码符合项目规范
- ✅ 使用构造器注入（禁止 @Autowired）
- ✅ 完整的中文 JavaDoc 注释
- ✅ MapStruct 自动转换
- ✅ 响应式编程（Mono/Flux）

### 功能完整性
- ✅ 角色管理完整流程
- ✅ 权限管理完整流程
- ✅ 会话管理完整流程
- ✅ RBAC 权限验证
- ✅ 数据权限控制

### 测试覆盖
- ✅ 单元测试覆盖核心逻辑
- ✅ 集成测试覆盖主要流程
- ✅ 覆盖率 60%+

---

## 🚀 后续计划

完成阶段三后，项目整体进度将达到 **约 50-55%**。

**阶段四**（OAuth2 / SSO 多协议支持）规划：
1. OAuth2 授权服务器
2. SSO 协议适配器（OIDC/SAML/CAS）
3. LDAP/AD 集成
4. Keycloak 对接

**阶段五**（高级特性）规划：
1. MFA 多因子认证
2. 风险检测与设备指纹
3. 验证码集成
4. 审计日志查询与告警

---

**文档生成时间**: 2026-09-20  
**状态**: 核心服务已完成，待实施接口层与测试
