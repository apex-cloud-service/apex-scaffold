package com.apex.scaffold.contract.dto.internal;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 开放应用启停请求。 */
@Data
@NoArgsConstructor
public class OpenAppStatusRequest {
    @NotNull
    private Boolean enabled;
}
