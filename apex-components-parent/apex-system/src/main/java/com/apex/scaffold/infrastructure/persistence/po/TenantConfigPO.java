package com.apex.scaffold.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 租户级参数配置持久化对象（sys_tenant_config，方案批次 B6）。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Data
@TableName("sys_tenant_config")
public class TenantConfigPO {

    /** 主键 ID（雪花算法） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 租户编码 */
    private String tenantId;

    /** 配置键 */
    private String configKey;

    /** 配置值 */
    private String configValue;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer version;

    /** 逻辑删除标记 */
    private Integer deleted;
}
