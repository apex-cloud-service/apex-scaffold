package com.apex.scaffold.contract.dto.internal;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 开放应用启停请求。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@Data
@NoArgsConstructor
public class OpenAppStatusRequest {
    @NotNull
    private Boolean enabled;
}
