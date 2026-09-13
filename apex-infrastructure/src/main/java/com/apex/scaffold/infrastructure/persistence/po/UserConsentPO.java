package com.apex.scaffold.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 用户协议同意记录持久化对象。
 *
 * <p>映射数据库表 biz_user_consent，记录用户对各类策略/协议的同意情况。
 * 注意：本表 version 列是 VARCHAR 类型的业务版本号（协议版本），不是乐观锁 INT 字段，
 * 因此不继承 BasePO（BasePO 的 version 为 INT 乐观锁，与本表冲突）。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@TableName("biz_user_consent")
/**n * UserConsentPO - 持久化对象n *n * <p>对应数据库表：TODO: 补充表名n *n * @author Apex Teamn * @version 1.0.0n * @since 1.0.0n */
public class UserConsentPO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键 ID（雪花算法生成）。
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 用户 ID。
     */
    @TableField("user_id")
    private Long userId;

    /**
     * 策略类型。
     */
    @TableField("policy_type")
    private String policyType;

    /**
     * 同意的协议版本号（VARCHAR 类型，非乐观锁）。
     */
    @TableField("version")
    private String consentVersion;

    /**
     * 是否同意（0 表示不同意，1 表示同意）。
     */
    @TableField("agreed")
    private Integer agreed;

    /**
     * 同意时间。
     */
    @TableField("agreed_at")
    private LocalDateTime agreedAt;

    /**
     * 渠道。
     */
    @TableField("channel")
    private String channel;

    /**
     * 设备 ID 摘要。
     */
    @TableField("device_id")
    private String deviceId;

    /**
     * 创建时间。
     */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间。
     */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * 逻辑删除标记（0 表示未删除，1 表示已删除）。
     */
    @TableLogic
    @TableField(value = "deleted")
    private Integer deleted;
}
