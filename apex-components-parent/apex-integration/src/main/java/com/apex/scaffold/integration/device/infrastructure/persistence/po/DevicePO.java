package com.apex.scaffold.integration.device.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 物联网设备持久化对象（表 {@code iot_device}）。
 *
 * <p>{@code secretHash} 为 PBKDF2 哈希串，明文不落库。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Data
@TableName("iot_device")
public class DevicePO {

    /** 主键（雪花算法）。 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 租户编码。 */
    private String tenantId;

    /** 设备标识（租户内唯一）。 */
    private String deviceKey;

    /** 设备凭据哈希（PBKDF2）。 */
    private String secretHash;

    /** X.509 证书指纹（可空）。 */
    private String certFingerprint;

    /** 状态（online/offline）。 */
    private String status;

    /** 固件版本。 */
    private String firmwareVersion;

    /** 标签（JSON 对象）。 */
    private String labels;

    /** 最后活跃时间。 */
    private LocalDateTime lastActiveAt;

    /** 创建时间（DB 默认填充）。 */
    private LocalDateTime createTime;

    /** 更新时间（DB ON UPDATE 填充）。 */
    private LocalDateTime updateTime;

    /** 逻辑删除标记。 */
    @TableLogic
    private Integer deleted;
}
