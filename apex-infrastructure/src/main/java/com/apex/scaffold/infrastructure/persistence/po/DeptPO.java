package com.apex.scaffold.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 部门持久化对象（sys_dept）。
 *
 * <p>树形部门结构，{@code parent_id} 自关联、{@code ancestors} 存祖级链，
 * 支撑数据权限（C3）按部门层级过滤。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 2.2.0
 */
@TableName("sys_dept")
@Data
public class DeptPO {

    @TableField("tenant_id")
    private String tenantId;

    /** 主键 ID（雪花算法） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 父部门 ID（0 为根部门） */
    @TableField("parent_id")
    private Long parentId;

    /** 祖级列表（逗号分隔，如 0,100） */
    private String ancestors;

    /** 部门名称 */
    private String name;

    /** 显示顺序 */
    @TableField("order_num")
    private Integer orderNum;

    /** 负责人 */
    private String leader;

    /** 联系电话 */
    private String phone;

    /** 邮箱 */
    private String email;

    /** 状态（0-停用，1-正常） */
    private Integer status;

    /** 创建时间 */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 版本号（乐观锁） */
    @Version
    @TableField(value = "version", fill = FieldFill.INSERT)
    private Integer version;

    /** 逻辑删除（0-未删，1-已删） */
    @TableLogic
    private Integer deleted;
}
