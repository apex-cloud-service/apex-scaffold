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
 * 用户档案快照（内部 API 契约）.
 *
 * <p>由 system-service 提供，供 auth-service / office-service 等消费方做只读副本。字段集仅包含跨服务必要项，
 * 敏感字段（密码、手机号全量）不在契约中暴露。
 *
 * @author Apex Team
 * @since 1.3.1
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSnapshotDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 统一账户 ID（认证主体维度） */
    private Long accountId;

    /** 租户 ID */
    private Long tenantId;

    /** 用户名（展示用冗余字段） */
    private String username;

    /** 昵称 */
    private String nickname;

    /** 邮箱（脱敏后或全量，按消费场景约定） */
    private String email;

    /** 部门 ID */
    private Long deptId;

    /** 状态（启用/禁用等，沿用 common 枚举值） */
    private Integer status;

    /** 档案创建时间 */
    private LocalDateTime createdAt;

    /** 权限编码列表（如 {@code user:read}），仅当提供方按需展开 */
    private List<String> permissionCodes;
}
