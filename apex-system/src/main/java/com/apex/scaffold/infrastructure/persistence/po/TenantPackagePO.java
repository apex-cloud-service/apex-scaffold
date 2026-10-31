package com.apex.scaffold.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 租户套餐持久化对象（sys_tenant_package，平台级定义表，方案批次 B3）。
 *
 * <p>menu_ids/permission_codes/features 以 JSON 字符串存取，实体转换在 Converter 完成。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Data
@TableName("sys_tenant_package")
public class TenantPackagePO {

    /** 主键 ID（雪花算法） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 套餐编码（全局唯一） */
    private String packageCode;

    /** 套餐名称 */
    private String packageName;

    /** 可用菜单 ID 集合（JSON 数组文本；NULL/空 = 全量） */
    private String menuIds;

    /** 可用权限码集合（JSON 数组文本；NULL/空 = 全量） */
    private String permissionCodes;

    /** 能力订阅集合（JSON 数组文本） */
    private String features;

    /** 状态（0-停用，1-正常） */
    private Integer status;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private java.time.LocalDateTime createTime;

    /** 更新时间 */
    private java.time.LocalDateTime updateTime;

    /** 乐观锁版本号 */
    @TableField(updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS)
    private Integer version;

    /** 逻辑删除标记 */
    private Integer deleted;
}
