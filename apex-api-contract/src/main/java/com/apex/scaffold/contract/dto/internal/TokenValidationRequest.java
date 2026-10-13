package com.apex.scaffold.contract.dto.internal;

import java.io.Serial;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Token 校验请求（内部 API 契约）.
 *
 * <p>对应 auth-service 的 {@code POST /internal/v1/tokens/validate} 请求体。
 * 过渡期供 system-service 校验存量主应用 Token 的语义信息；
 * 请求中仅携带待校验 Token 与可选上下文，不携带敏感密钥。
 *
 * @author Apex Team
 * @since 1.3.1
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenValidationRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 待校验的原始 Token（访问令牌，可为 JWT 或存量 opaque token） */
    private String token;

    /** 所属租户 ID（可选，用于缩小校验范围与多租户隔离） */
    private Long tenantId;

    /** 请求来源标识（可选，便于审计，如 {@code system-service}） */
    private String source;
}
