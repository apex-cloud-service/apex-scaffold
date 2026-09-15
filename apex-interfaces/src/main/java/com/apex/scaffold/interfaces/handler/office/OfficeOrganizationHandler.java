package com.apex.scaffold.interfaces.handler.office;

import com.apex.scaffold.application.office.OfficeOrganizationSyncService;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.domain.office.OfficePlatform;
import com.apex.scaffold.interfaces.dto.office.organization.OrganizationMappingResponse;
import com.apex.scaffold.interfaces.dto.office.organization.SyncOrganizationRequest;
import com.apex.scaffold.interfaces.dto.office.organization.SyncOrganizationResponse;
import com.apex.scaffold.interfaces.util.RequestHandlerUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 办公平台组织同步处理器
 *
 * <p>提供组织架构同步相关的 HTTP 接口处理，包括：
 * <ul>
 *   <li>同步部门：从外部平台同步部门信息</li>
 *   <li>同步成员：从外部平台同步成员信息</li>
 *   <li>查询映射：查询组织映射关系</li>
 *   <li>同步历史：查询同步任务历史</li>
 * </ul>
 *
 * <p>接口路径：/api/v1/office/organization
 *
 * <p>权限要求：需要管理员权限
 *
 * @author Apex Team
 * @since 1.3.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Tag(name = "办公平台组织同步", description = "办公平台组织架构同步接口")
public class OfficeOrganizationHandler {

    private final OfficeOrganizationSyncService organizationSyncService;

    /**
     * 同步组织（部门或成员）
     *
     * <p>根据请求参数从外部办公平台同步部门或成员信息到 Apex 系统。
     * 支持全量同步和增量同步。
     *
     * @param request HTTP 请求
     * @return 同步结果，包含任务ID、同步数量和状态消息
     */
    @Operation(
        summary = "同步组织",
        description = "从外部办公平台同步部门或成员信息，支持全量和增量同步",
        tags = {"办公平台组织同步"}
    )
    public Mono<ServerResponse> syncOrganization(ServerRequest request) {
        return RequestHandlerUtil.validateAndMap(request, SyncOrganizationRequest.class)
            .flatMap(req -> {
                String tenantId = RequestHandlerUtil.getTenantId(request);
                OfficePlatform platform = OfficePlatform.valueOf(req.getPlatform());
                
                Mono<SyncOrganizationResponse> syncMono;
                if ("DEPARTMENT".equals(req.getSyncType())) {
                    syncMono = organizationSyncService.syncDepartments(tenantId, platform, req.getFullSync())
                        .map(this::toSyncResponse);
                } else if ("MEMBER".equals(req.getSyncType())) {
                    syncMono = organizationSyncService.syncMembers(tenantId, platform, req.getFullSync())
                        .map(this::toSyncResponse);
                } else {
                    return ServerResponse.badRequest()
                        .bodyValue(Result.failure("不支持的同步类型：" + req.getSyncType()));
                }
                
                return syncMono
                    .flatMap(response -> ServerResponse.ok()
                        .bodyValue(Result.success(response)));
            })
            .onErrorResume(RequestHandlerUtil::handleError);
    }

    /**
     * 查询部门映射
     *
     * <p>根据外部部门ID查询对应的 Apex 部门映射关系。
     *
     * @param request HTTP 请求
     * @return 组织映射信息
     */
    @Operation(
        summary = "查询部门映射",
        description = "根据外部部门ID查询对应的Apex部门映射关系",
        tags = {"办公平台组织同步"}
    )
    public Mono<ServerResponse> getDepartmentMapping(ServerRequest request) {
        String tenantId = RequestHandlerUtil.getTenantId(request);
        String platform = request.pathVariable("platform");
        String externalOrgId = request.pathVariable("externalOrgId");
        
        return organizationSyncService.getDepartmentMapping(
                tenantId,
                OfficePlatform.valueOf(platform),
                externalOrgId
            )
            .map(this::toMappingResponse)
            .flatMap(response -> ServerResponse.ok()
                .bodyValue(Result.success(response)))
            .switchIfEmpty(ServerResponse.ok()
                .bodyValue(Result.success(null)))
            .onErrorResume(RequestHandlerUtil::handleError);
    }

    /**
     * 查询成员映射
     *
     * <p>根据外部用户ID查询对应的 Apex 用户映射关系。
     *
     * @param request HTTP 请求
     * @return 组织映射信息
     */
    @Operation(
        summary = "查询成员映射",
        description = "根据外部用户ID查询对应的Apex用户映射关系",
        tags = {"办公平台组织同步"}
    )
    public Mono<ServerResponse> getMemberMapping(ServerRequest request) {
        String tenantId = RequestHandlerUtil.getTenantId(request);
        String platform = request.pathVariable("platform");
        String externalUserId = request.pathVariable("externalUserId");
        
        return organizationSyncService.getMemberMapping(
                tenantId,
                OfficePlatform.valueOf(platform),
                externalUserId
            )
            .map(this::toMappingResponse)
            .flatMap(response -> ServerResponse.ok()
                .bodyValue(Result.success(response)))
            .switchIfEmpty(ServerResponse.ok()
                .bodyValue(Result.success(null)))
            .onErrorResume(RequestHandlerUtil::handleError);
    }

    /**
     * 查询组织映射列表
     *
     * <p>查询指定平台的所有组织映射关系，可用于核对和调试。
     *
     * @param request HTTP 请求
     * @return 组织映射列表
     */
    @Operation(
        summary = "查询组织映射列表",
        description = "查询指定平台的所有组织映射关系",
        tags = {"办公平台组织同步"}
    )
    public Mono<ServerResponse> listOrganizationMappings(ServerRequest request) {
        String tenantId = RequestHandlerUtil.getTenantId(request);
        String platformParam = request.queryParam("platform").orElse(null);
        
        OfficePlatform platform = platformParam != null ? 
            OfficePlatform.valueOf(platformParam) : null;
        
        return organizationSyncService.listOrganizationMappings(tenantId, platform)
            .map(list -> list.stream()
                .map(this::toMappingResponse)
                .collect(Collectors.toList()))
            .flatMap(responses -> ServerResponse.ok()
                .bodyValue(Result.success(responses)))
            .onErrorResume(RequestHandlerUtil::handleError);
    }

    /**
     * 查询同步任务列表
     *
     * <p>查询历史同步任务记录，包含任务状态、同步时间、游标等信息。
     * 支持分页查询。
     *
     * @param request HTTP 请求
     * @return 同步任务列表
     */
    @Operation(
        summary = "查询同步任务列表",
        description = "查询历史同步任务记录，支持分页",
        tags = {"办公平台组织同步"}
    )
    public Mono<ServerResponse> listSyncTasks(ServerRequest request) {
        String tenantId = RequestHandlerUtil.getTenantId(request);
        String platformParam = request.queryParam("platform").orElse(null);
        int page = Integer.parseInt(request.queryParam("page").orElse("1"));
        int size = Integer.parseInt(request.queryParam("size").orElse("20"));
        
        OfficePlatform platform = platformParam != null ? 
            OfficePlatform.valueOf(platformParam) : null;
        
        return organizationSyncService.listSyncTasks(tenantId, platform, page, size)
            .flatMap(tasks -> ServerResponse.ok()
                .bodyValue(Result.success(tasks)))
            .onErrorResume(RequestHandlerUtil::handleError);
    }

    // ========== 私有方法：对象转换 ==========

    private SyncOrganizationResponse toSyncResponse(
        OfficeOrganizationSyncService.SyncTaskResult result
    ) {
        return SyncOrganizationResponse.builder()
            .taskId(result.taskId())
            .syncedCount(result.syncedCount())
            .message(result.message())
            .success(result.success())
            .build();
    }

    private OrganizationMappingResponse toMappingResponse(
        com.apex.scaffold.domain.office.OfficeOrganizationMapping mapping
    ) {
        return OrganizationMappingResponse.builder()
            .id(mapping.getId())
            .platform(mapping.getPlatform().name())
            .externalOrgId(mapping.getExternalOrgId())
            .externalUserId(mapping.getExternalUserId())
            .apexDeptId(mapping.getApexDeptId())
            .apexUserId(mapping.getApexUserId())
            .createdAt(mapping.getCreatedAt())
            .updatedAt(mapping.getUpdatedAt())
            .build();
    }
}
