package com.apex.scaffold.infrastructure.office.persistence;

import com.apex.scaffold.domain.office.OfficePlatform;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

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
