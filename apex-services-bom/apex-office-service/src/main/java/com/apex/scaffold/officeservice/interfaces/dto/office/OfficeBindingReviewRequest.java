package com.apex.scaffold.officeservice.interfaces.dto.office;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 办公平台身份绑定审批请求。
 *
 * @author Apex Team
 * @since 1.2.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "办公平台身份绑定审批请求")
public class OfficeBindingReviewRequest {
    @NotNull(message = "审批决定不能为空")
    @Schema(description = "是否批准", example = "true", required = true)
    private Boolean approved;

    @NotNull(message = "版本号不能为空")
    @Schema(description = "乐观锁版本号", example = "0", required = true)
    private Integer version;
}
