package com.apex.scaffold.contract.dto.internal;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/** 开放应用内部注册契约；租户只来自已验证的内部调用身份。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpenAppRegistrationRequest {
    @Size(max = 100)
    private String clientId;

    @ToString.Exclude
    @Size(max = 72)
    private String clientSecret;

    @Size(max = 200)
    private String appName;

    @Size(max = 1000)
    private String description;

    @Size(max = 50)
    private Set<@Size(max = 2000) String> redirectUris;

    @Size(max = 50)
    private Set<@Size(max = 100) String> scopes;

    @Size(max = 64)
    private String rateLimitTier;

    @Size(max = 200)
    private String contact;

    private Boolean enabled;

    @Positive
    private Long subjectUserId;

    private Boolean subjectValidated;
}
