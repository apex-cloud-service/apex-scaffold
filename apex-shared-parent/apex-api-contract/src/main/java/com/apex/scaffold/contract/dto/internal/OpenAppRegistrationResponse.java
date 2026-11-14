package com.apex.scaffold.contract.dto.internal;

import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * auth 注册源响应。
 *
 * <p>密钥哈希永不出现在返回值中；{@code clientSecret} 仅在创建与轮换时携带一次明文。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpenAppRegistrationResponse {
    private String clientId;
    private String appName;
    private String description;
    private Set<String> redirectUris;
    private Set<String> scopes;
    private String rateLimitTier;
    private String contact;
    private Boolean enabled;
    private String createdAt;

    @ToString.Exclude
    private String clientSecret;

    private Long subjectUserId;
}
