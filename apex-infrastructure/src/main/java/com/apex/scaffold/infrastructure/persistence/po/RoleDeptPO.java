package com.apex.scaffold.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_role_dept")
public class RoleDeptPO extends BasePO {

    private String tenantId;

    private Long roleId;

    private Long deptId;
}
