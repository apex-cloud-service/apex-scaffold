package com.apex.scaffold.infrastructure.office.persistence;

import com.apex.scaffold.domain.office.OfficePlatform;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

/**
 * 办公平台身份绑定持久化对象。
 *
 * <p>对应 {@code office_identity_binding} 表，按“租户 + 平台 + 外部用户标识”维度
 * 存储钉钉、飞书、企业微信等外部身份与本地用户（userId）的绑定关系，
 * 包含绑定状态与启用标志；带乐观锁 version 与逻辑删除 deleted 字段。
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.2.0
 */
@Data
@TableName("office_identity_binding")
public class OfficeBindingPO {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String tenantId;
    private OfficePlatform platform;
    private Long userId;
    private String externalUserId;
    private String status;
    private boolean enabled;

    @Version
    private Integer version;

    @TableLogic
    private Integer deleted;
}
