package com.apex.scaffold.infrastructure.office.persistence;

import com.apex.scaffold.domain.office.OfficePlatform;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("office_organization_mapping")
public class OfficeOrganizationMappingPO {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String tenantId;
    private OfficePlatform platform;
    private String externalOrgId;
    private String externalUserId;
    private String apexDeptId;
    private String apexUserId;
    private Integer version;

    @TableLogic
    private Integer deleted;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
