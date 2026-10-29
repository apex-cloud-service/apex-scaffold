package com.apex.scaffold.auth.application.dto;

import com.apex.scaffold.auth.domain.model.entity.SessionEntity;
import com.apex.scaffold.common.result.Result;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 认证中心 OpenAPI 专用响应模型，保留实际响应的泛型数据结构。
 *
 * @author Apex Scaffold Team
 * @since 1.2.0
 */
@Schema(description = "认证中心文档响应模型，仅用于描述实际响应的泛型数据")
@NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
public final class AuthCenterApiResponses {
    /** RoleResult 统一响应包装，data 为 RoleDTO。 */
    @Schema(name = "RoleResult", description = "统一响应，data 类型为 RoleDTO")
    public static class RoleResult extends Result<RoleDTO> {}

    /** RoleListResult 统一响应包装，data 为 RoleDTO 列表。 */
    @Schema(name = "RoleListResult", description = "统一响应，data 类型为 List<RoleDTO>")
    public static class RoleListResult extends Result<List<RoleDTO>> {}

    /** PermissionResult 统一响应包装，data 为 PermissionDTO。 */
    @Schema(name = "PermissionResult", description = "统一响应，data 类型为 PermissionDTO")
    public static class PermissionResult extends Result<PermissionDTO> {}

    /** PermissionListResult 统一响应包装，data 为 PermissionDTO 列表。 */
    @Schema(name = "PermissionListResult", description = "统一响应，data 类型为 List<PermissionDTO>")
    public static class PermissionListResult extends Result<List<PermissionDTO>> {}

    /** PermissionIdsResult 统一响应包装，data 为 Long 集合。 */
    @Schema(name = "PermissionIdsResult", description = "统一响应，data 类型为 Set<Long>")
    public static class PermissionIdsResult extends Result<Set<Long>> {}

    /** SessionResult 统一响应包装，data 为 SessionDTO。 */
    @Schema(name = "SessionResult", description = "统一响应，data 类型为 SessionDTO")
    public static class SessionResult extends Result<SessionDTO> {}

    /** SessionListResult 统一响应包装，data 为 SessionDTO 列表。 */
    @Schema(name = "SessionListResult", description = "统一响应，data 类型为 List<SessionDTO>")
    public static class SessionListResult extends Result<List<SessionDTO>> {}

    /** OnlineSessionsResult 统一响应包装，data 为 SessionEntity 列表。 */
    @Schema(name = "OnlineSessionsResult", description = "统一响应，data 类型为 List<SessionEntity>")
    public static class OnlineSessionsResult extends Result<List<SessionEntity>> {}

    /** OnlineStatisticsResult 统一响应包装，data 为 OnlineStatistics。 */
    @Schema(name = "OnlineStatisticsResult", description = "统一响应，data 类型为 OnlineStatistics")
    public static class OnlineStatisticsResult extends Result<OnlineStatistics> {}

    /** EmptyResult 无数据统一响应包装。 */
    @Schema(name = "EmptyResult", description = "统一响应，data 类型为 Void")
    public static class EmptyResult extends Result<Void> {}

    /** CountResult 统一响应包装，data 为 Long。 */
    @Schema(name = "CountResult", description = "统一响应，data 类型为 Long")
    public static class CountResult extends Result<Long> {}

    /** AuthConfigResult 统一响应包装，data 为 AuthConfigDTO。 */
    @Schema(name = "AuthConfigResult", description = "统一响应，data 类型为 AuthConfigDTO")
    public static class AuthConfigResult extends Result<AuthConfigDTO> {}

    /** AuthConfigListResult 统一响应包装，data 为 AuthConfigDTO 列表。 */
    @Schema(name = "AuthConfigListResult", description = "统一响应，data 类型为 List<AuthConfigDTO>")
    public static class AuthConfigListResult extends Result<List<AuthConfigDTO>> {}

    /** AuditLogResult 统一响应包装，data 为 AuditLogDTO。 */
    @Schema(name = "AuditLogResult", description = "统一响应，data 类型为 AuditLogDTO")
    public static class AuditLogResult extends Result<AuditLogDTO> {}

    /** AuditLogListResult 统一响应包装，data 为 AuditLogDTO 列表。 */
    @Schema(name = "AuditLogListResult", description = "统一响应，data 类型为 List<AuditLogDTO>")
    public static class AuditLogListResult extends Result<List<AuditLogDTO>> {}

    /** MfaSetupResult 统一响应包装，data 为 MfaSetupResponse。 */
    @Schema(name = "MfaSetupResult", description = "统一响应，data 类型为 MfaSetupResponse")
    public static class MfaSetupResult extends Result<MfaSetupResponse> {}

    /** MfaEnabledResult 统一响应包装，data 为 MfaEnabled。 */
    @Schema(name = "MfaEnabledResult", description = "统一响应，data 类型为 MfaEnabled")
    public static class MfaEnabledResult extends Result<MfaEnabled> {}

    /** MfaVerifiedResult 统一响应包装，data 为 MfaVerified。 */
    @Schema(name = "MfaVerifiedResult", description = "统一响应，data 类型为 MfaVerified")
    public static class MfaVerifiedResult extends Result<MfaVerified> {}

    /** MfaDisabledResult 统一响应包装，data 为 MfaDisabled。 */
    @Schema(name = "MfaDisabledResult", description = "统一响应，data 类型为 MfaDisabled")
    public static class MfaDisabledResult extends Result<MfaDisabled> {}

    /** MfaStatusResult 统一响应包装，data 为 MfaStatus。 */
    @Schema(name = "MfaStatusResult", description = "统一响应，data 类型为 MfaStatus")
    public static class MfaStatusResult extends Result<MfaStatus> {}

    /** PasswordRecoveryInitiateResult 统一响应包装，data 为 PasswordRecoveryInitiateResponse。 */
    @Schema(name = "PasswordRecoveryInitiateResult", description = "统一响应，data 类型为 PasswordRecoveryInitiateResponse")
    public static class PasswordRecoveryInitiateResult extends Result<PasswordRecoveryInitiateResponse> {}

    /** PasswordRecoveryVerifyResult 统一响应包装，data 为 PasswordRecoveryVerifyResponse。 */
    @Schema(name = "PasswordRecoveryVerifyResult", description = "统一响应，data 类型为 PasswordRecoveryVerifyResponse")
    public static class PasswordRecoveryVerifyResult extends Result<PasswordRecoveryVerifyResponse> {}

    /** PasswordRecoveryConfirmResult 统一响应包装，data 为 PasswordRecoveryConfirmResponse。 */
    @Schema(name = "PasswordRecoveryConfirmResult", description = "统一响应，data 类型为 PasswordRecoveryConfirmResponse")
    public static class PasswordRecoveryConfirmResult extends Result<PasswordRecoveryConfirmResponse> {}

    /** OnlineStatistics 响应数据模型。 */
    @Data
    @Schema(name = "OnlineStatistics", description = "OnlineStatistics 响应数据")
    public static class OnlineStatistics {
        @Schema(description = "在线会话数量")
        private Long totalOnlineUsers;

        @Schema(description = "统计时间（本地时间，无时区）")
        private LocalDateTime timestamp;
    }

    /** MfaEnabled 响应数据模型。 */
    @Data
    @Schema(name = "MfaEnabled", description = "MfaEnabled 响应数据")
    public static class MfaEnabled {
        @Schema(description = "是否已启用")
        private Boolean enabled;
    }

    /** MfaVerified 响应数据模型。 */
    @Data
    @Schema(name = "MfaVerified", description = "MfaVerified 响应数据")
    public static class MfaVerified {
        @Schema(description = "是否验证成功")
        private Boolean verified;
    }

    /** MfaDisabled 响应数据模型。 */
    @Data
    @Schema(name = "MfaDisabled", description = "MfaDisabled 响应数据")
    public static class MfaDisabled {
        @Schema(description = "是否禁用成功")
        private Boolean disabled;
    }

    /** MfaStatus 响应数据模型。 */
    @Data
    @Schema(name = "MfaStatus", description = "MfaStatus 响应数据")
    public static class MfaStatus {
        @Schema(description = "是否已启用")
        private Boolean enabled;

        @Schema(description = "MFA 类型：TOTP 或 NONE")
        private String mfaType;
    }
}
