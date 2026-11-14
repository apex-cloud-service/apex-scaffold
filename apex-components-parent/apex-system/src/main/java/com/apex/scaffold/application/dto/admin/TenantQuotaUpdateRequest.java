package com.apex.scaffold.application.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 租户配额更新请求体。
 *
 * <p>{@code maxUsers} 与 {@code maxStorageMb} 为 null 表示不限制。
 * {@code expectedVersion} 为客户端读取到的租户版本号，用于乐观锁并发控制。</p>
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "租户配额更新请求")
public class TenantQuotaUpdateRequest {

    @Schema(description = "最大用户数（NULL表示不限制）", example = "100")
    @Positive(message = "{tenant.max_users_positive}")
    private Integer maxUsers;

    @Schema(description = "最大存储容量MB（NULL表示不限制）", example = "10240")
    @Positive(message = "{tenant.max_storage_positive}")
    private Integer maxStorageMb;

    @Schema(description = "期望的租户版本号（乐观锁），为空时不做版本校验", example = "3")
    private Integer expectedVersion;
}
