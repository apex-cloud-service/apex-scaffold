package com.apex.scaffold.integration.device.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 轻量规则持久化对象（表 {@code iot_rule}）。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Data
@TableName("iot_rule")
public class DeviceRulePO {

    /** 主键（雪花算法）。 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 租户编码。 */
    private String tenantId;

    /** 规则名（租户内唯一）。 */
    private String ruleName;

    /** 结构化条件（JSON）。 */
    private String conditionJson;

    /** 动作类型（NOTIFY/WEBHOOK/EVENT）。 */
    private String actionType;

    /** 动作配置（JSON）。 */
    private String actionConfig;

    /** 启停（0-停用，1-启用）。 */
    private Integer enabled;

    /** 创建时间（DB 默认填充）。 */
    private LocalDateTime createTime;

    /** 更新时间（DB ON UPDATE 填充）。 */
    private LocalDateTime updateTime;

    /** 逻辑删除标记。 */
    @TableLogic
    private Integer deleted;
}
