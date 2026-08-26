package com.apex.scaffold.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 黑名单持久化对象。
 *
 * <p>映射数据库表 biz_blacklist，记录用户之间的拉黑关系，
 * 支持软删除（通过 enabled 字段控制）。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_blacklist")
public class BlacklistPO extends BasePO {

    /**
     * 创建拉黑关系实体对象。
     *
     */
    public BlacklistPO() {}

    /**
     * 拉黑者用户 ID。
     */
    @TableField("user_id")
    private Long userId;

    /**
     * 被拉黑用户 ID。
     */
    @TableField("target_user_id")
    private Long targetUserId;

    /**
     * 是否有效（0 表示无效，1 表示有效）。
     */
    @TableField("enabled")
    private Integer enabled;
}
