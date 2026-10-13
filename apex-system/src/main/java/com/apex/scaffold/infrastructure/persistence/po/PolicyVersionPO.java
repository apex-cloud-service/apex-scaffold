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
 * 协议与政策版本持久化对象。
 *
 * <p>注意：本表 version 列是 VARCHAR 类型的业务版本号，不是乐观锁 INT 字段，
 * 因此不继承 BasePO（BasePO 的 version 为 INT 乐观锁，与本表冲突）。</p>
 *
 * <p>对应数据库表 biz_policy_version。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@TableName("biz_policy_version")
public class PolicyVersionPO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID（雪花算法）。 */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /** 协议/政策类型（USER_AGREEMENT/PRIVACY_POLICY）。 */
    @TableField("policy_type")
    private String policyType;

    /** 业务版本号（VARCHAR 类型，非乐观锁）。 */
    @TableField("version")
    private String policyVersion;

    /** 生效日期字符串。 */
    @TableField("effective_at")
    private String effectiveAt;

    /** 内容 URL。 */
    @TableField("content_url")
    private String contentUrl;

    /** 创建时间。 */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间。 */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 逻辑删除标记（0-未删除，1-已删除）。 */
    @TableLogic
    @TableField(value = "deleted")
    private Integer deleted;
}
