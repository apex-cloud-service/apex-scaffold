package com.apex.scaffold.interfaces.handler;

import com.apex.scaffold.application.dto.admin.TenantPackageRequest;
import com.apex.scaffold.application.service.TenantPackageService;
import com.apex.scaffold.common.annotation.RequirePermission;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.domain.model.entity.TenantPackageEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * 租户套餐管理 Handler（方案批次 B6）。
 *
 * <p>承载 {@code /api/v2/tenant-packages} 套餐 CRUD：列表、详情、创建、更新
 * （菜单/权限/能力裁剪集合变更即时生效——既有用户权限随统一授权快照口径收敛）。
 * 平台管理接口：{@code default} 租户 ADMIN 专属权限码。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantPackageHandler {

    private final TenantPackageService tenantPackageService;

    /** 套餐查询（列表/详情）。 */
    @RequirePermission("tenant:package:read")
    public Mono<ServerResponse> listPackages(ServerRequest request) {
        return tenantPackageService
                .listPackages()
                .collectList()
                .flatMap(packages -> ServerResponse.ok().bodyValue(Result.success(packages)))
                .onErrorResume(this::asError);
    }

    /** 套餐详情。 */
    @RequirePermission("tenant:package:read")
    public Mono<ServerResponse> getPackage(ServerRequest request) {
        return tenantPackageService
                .getByCode(request.pathVariable("packageCode"))
                .flatMap(pkg -> ServerResponse.ok().bodyValue(Result.success(pkg)))
                .onErrorResume(this::asError);
    }

    /** 创建套餐。 */
    @RequirePermission("tenant:package:create")
    public Mono<ServerResponse> createPackage(ServerRequest request) {
        return request.bodyToMono(TenantPackageRequest.class)
                .flatMap(body -> tenantPackageService.createPackage(toEntity(body)))
                .flatMap(pkg -> ServerResponse.ok().bodyValue(Result.success(pkg)))
                .onErrorResume(this::asError);
    }

    /** 更新套餐。 */
    @RequirePermission("tenant:package:update")
    public Mono<ServerResponse> updatePackage(ServerRequest request) {
        return request.bodyToMono(TenantPackageRequest.class)
                .flatMap(body -> tenantPackageService.updatePackage(toEntity(body)))
                .flatMap(pkg -> ServerResponse.ok().bodyValue(Result.success(pkg)))
                .onErrorResume(this::asError);
    }

    private TenantPackageEntity toEntity(TenantPackageRequest body) {
        return TenantPackageEntity.builder()
                .packageCode(body.getPackageCode())
                .packageName(body.getPackageName())
                .menuIds(body.getMenuIds())
                .permissionCodes(body.getPermissionCodes())
                .features(body.getFeatures())
                .status(body.getStatus() == null ? 1 : body.getStatus())
                .remark(body.getRemark())
                .build();
    }

    private Mono<ServerResponse> asError(Throwable error) {
        if (error instanceof BusinessException businessException) {
            int status = businessException.getCode() == ResultCode.NOT_FOUND.getCode() ? 404 : 400;
            return ServerResponse.status(status).bodyValue(Result.error(businessException.getMessage()));
        }
        log.error("套餐管理处理异常", error);
        return ServerResponse.status(500).bodyValue(Result.error(ResultCode.ERROR.getMsg()));
    }
}
