package com.apex.scaffold.infrastructure.office.persistence;

import com.apex.scaffold.domain.office.OfficePlatform;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

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
