package com.apex.scaffold.contract.dto.internal;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Token 校验结果（内部 API 契约）.
 *
 * <p>由 auth-service 的 {@code POST /internal/v1/tokens/validate} 提供，
 * 过渡期供 system-service 查询存量主应用 Token 的语义信息（是否有效、归属主体、权限快照）。
 * 契约仅暴露校验结论与必要身份字段，**不返回任何原始凭证或密钥**。
 *
 * @author Apex Team
 * @since 1.3.1
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenValidationDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Token 是否有效（验签通过且未过期、未被撤销） */
    private Boolean valid;

    /** 失效原因（当 {@link #valid} 为 false 时给出，如 {@code EXPIRED} / {@code REVOKED} / {@code INVALID_SIGNATURE}） */
    private String reason;

    /** 统一账户 ID（校验通过时返回） */
    private Long accountId;

    /** 租户 ID（校验通过时返回） */
    private Long tenantId;

    /** 客户端类型（如 {@code WEB} / {@code MINIAPP}，校验通过时返回） */
    private String clientType;

    /** 权限编码列表（可选，按需展开，避免每次校验都返回大列表） */
    private List<String> permissionCodes;

    /** Token 过期时间（校验通过时返回，供消费方做续期判断） */
    private LocalDateTime expiresAt;
}
