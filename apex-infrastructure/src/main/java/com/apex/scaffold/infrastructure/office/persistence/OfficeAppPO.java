package com.apex.scaffold.infrastructure.office.persistence;

import com.apex.scaffold.domain.office.OfficePlatform;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

/**
 * 办公平台应用配置持久化对象。
 *
 * <p>对应 {@code office_platform_app} 表，按“租户 + 平台”维度存储办公平台应用配置：
 * appId、密钥环境变量引用（secretReference）、OAuth 回跳地址、签名密钥引用、
 * 逗号分隔的平台能力标识（capabilities）与启用状态；
 * 带乐观锁 version 与逻辑删除 deleted 字段，支撑并发控制与软删除。
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.2.0
 */
@Data
@TableName("office_platform_app")
public class OfficeAppPO {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String tenantId;
    private OfficePlatform platform;
    private String appId;
    private String secretReference;
    private String redirectUri;
    private String signingSecretReference;
    private String capabilities;
    private boolean enabled;

    @Version
    private Integer version;

    @TableLogic
    private Integer deleted;
}
