package com.apex.scaffold.integration.device.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 设备影子持久化对象（表 {@code iot_device_shadow}）。
 *
 * <p>{@code version} 为乐观锁版本：{@code reported} 更新必须以条件更新
 * （{@code WHERE version = 期望值}）提交并递增，冲突由仓储抛
 * {@code DeviceShadowConflictException}。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Data
@TableName("iot_device_shadow")
public class DeviceShadowPO {

    /** 主键（雪花算法）。 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 租户编码。 */
    private String tenantId;

    /** 设备标识（租户内唯一）。 */
    private String deviceKey;

    /** 期望状态（JSON）。 */
    private String desired;

    /** 上报状态（JSON）。 */
    private String reported;

    /** 影子版本（乐观锁）。 */
    private Integer version;

    /** 创建时间（DB 默认填充）。 */
    private LocalDateTime createTime;

    /** 更新时间（DB ON UPDATE 填充）。 */
    private LocalDateTime updateTime;
}
