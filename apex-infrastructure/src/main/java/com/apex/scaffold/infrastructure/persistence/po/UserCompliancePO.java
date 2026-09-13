package com.apex.scaffold.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 用户合规状态持久化对象。
 *
 * <p>映射数据库表 biz_user_compliance，记录用户的合规相关配置，
 * 包括青少年模式开关及设置时间等。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@TableName("biz_user_compliance")
/**n * UserCompliancePO - 持久化对象n *n * <p>对应数据库表：TODO: 补充表名n *n * @author Apex Teamn * @version 1.0.0n * @since 1.0.0n */
public class UserCompliancePO extends BasePO {

    /** 用户 ID */
    @TableField("user_id")
    private Long userId;

    /**
     * 是否开启青少年模式（0 表示关闭，1 表示开启）。
     */
    @TableField("teen_mode")
    private Integer teenMode;

    /**
     * 青少年模式设置时间。
     */
    @TableField("teen_mode_set_at")
    private LocalDateTime teenModeSetAt;

    /**
     * 影子封禁阶段。
     *
     * <p>可用值为 {@code NONE}、{@code STAGE_1}、{@code STAGE_2} 和
     * {@code STAGE_3}，与领域模型中的影子封禁阶段保持一致。</p>
     *
     */
    @TableField("shadow_ban_stage")
    private String shadowBanStage;

    /**
     * 最近一次进入或升级影子封禁阶段的时间。
     *
     */
    @TableField("shadow_ban_at")
    private LocalDateTime shadowBanAt;
}
