package com.apex.scaffold.application.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 租户状态动作请求体。
 *
 * <p>统一承载租户生命周期动作（disable/enable/freeze/unfreeze/deactivate/archive）。
 * {@code expectedVersion} 为客户端读取到的租户版本号，用于乐观锁并发控制；
 * 不传时退化为无条件更新，仅保留状态机校验。</p>
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "租户状态动作请求")
public class TenantActionRequest {

    @Schema(description = "动作：disable/enable/freeze/unfreeze/deactivate/archive", example = "disable")
    @NotBlank(message = "{tenant.action_not_blank}")
    private String action;

    @Schema(description = "期望的租户版本号（乐观锁），为空时不做版本校验", example = "3")
    private Integer expectedVersion;
}
