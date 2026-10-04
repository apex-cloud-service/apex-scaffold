package com.apex.scaffold.authcenter.interfaces.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 已通过租户与账号校验的中心授权快照。
 *
 * @param tenantId Token 所属中心租户
 * @param userId 中心用户标识
 * @param permissions 有效权限编码
 * @param roles 有效角色及其数据范围
 * @author Apex Scaffold Team
 * @version 1.0.0
 * @since 1.2.0
 */
@Schema(description = "中心授权快照")
public record UnifiedAuthorizationResponse(
        @Schema(description = "Token 所属中心租户", example = "center", requiredMode = Schema.RequiredMode.REQUIRED)
                String tenantId,
        @Schema(description = "中心用户 ID", example = "99", requiredMode = Schema.RequiredMode.REQUIRED)
                Long userId,
        @Schema(description = "有效权限编码", example = "[\"user:read\"]", requiredMode = Schema.RequiredMode.REQUIRED)
                List<String> permissions,
        @Schema(description = "有效角色及数据范围", requiredMode = Schema.RequiredMode.REQUIRED) List<Role> roles) {
    public UnifiedAuthorizationResponse {
        permissions = List.copyOf(permissions);
        roles = List.copyOf(roles);
    }

    /**
     * 标准响应封装。
     *
     * @param code 业务响应码
     * @param msg 响应消息
     * @param data 授权快照
     * @param traceId 请求追踪标识
     * @author Apex Scaffold Team
     * @version 1.0.0
     * @since 1.2.0
     */
    @Schema(name = "UnifiedAuthorizationResult", description = "Result 封装的中心授权快照")
    public record UnifiedAuthorizationResult(
            @Schema(description = "业务响应码", example = "200", requiredMode = Schema.RequiredMode.REQUIRED)
                    Integer code,
            @Schema(description = "响应消息", example = "success", requiredMode = Schema.RequiredMode.REQUIRED)
                    String msg,
            @Schema(description = "授权快照", requiredMode = Schema.RequiredMode.REQUIRED)
                    UnifiedAuthorizationResponse data,
            @Schema(description = "请求追踪标识", requiredMode = Schema.RequiredMode.REQUIRED)
                    String traceId) {}

    /**
     * 中心角色及其数据范围声明。
     *
     * @param code 角色编码
     * @param dataScope SELF、ALL、DEPT 或 DEPT_AND_CHILD
     * @author Apex Scaffold Team
     * @version 1.0.0
     * @since 1.2.0
     */
    @Schema(description = "中心角色")
    public record Role(
            @Schema(description = "角色编码", example = "ADMIN", requiredMode = Schema.RequiredMode.REQUIRED)
                    String code,
            @Schema(
                            description = "数据范围：SELF、ALL、DEPT 或 DEPT_AND_CHILD；其他值拒绝授权",
                            example = "SELF")
                    String dataScope) {}
}
