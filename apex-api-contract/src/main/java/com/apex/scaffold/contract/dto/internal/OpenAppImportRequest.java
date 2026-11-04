package com.apex.scaffold.contract.dto.internal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * 旧注册源离线导入契约。源数据库只能由运维工具读取，不允许auth跨库读取。
 *
 * <p>importId是稳定的源行标识，sourceFingerprint为源导出内容的SHA256；
 * 两者与请求体一起受内部HMAC签名保护。重放不修改已经由管理面更新的目标。
 */
@Data
@NoArgsConstructor
public class OpenAppImportRequest {
    @Valid
    @NotNull
    private OpenAppRegistrationRequest registration;

    @NotBlank
    @Size(max = 128)
    private String importId;

    @NotBlank
    @Pattern(regexp = "[a-fA-F0-9]{64}")
    private String sourceFingerprint;

    @NotBlank
    @Pattern(regexp = "BCRYPT")
    private String hashAlgorithm;

    @NotBlank
    @ToString.Exclude
    @Size(max = 68)
    private String secretHash;
}
