package com.apex.scaffold.authcenter.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.*;

/**
 * 认证数据持久化映射。
 *
 * @author Apex Team
 * @since 1.2.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("auth_role")
public class RolePO {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String tenantId;
    private String code;
    private String name;
    private String description;
    private String dataScope;
    private Integer sortOrder;
    private Integer enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long createdBy;
    private Long updatedBy;

    @TableLogic
    private Integer deleted;

    private Integer version;
}
