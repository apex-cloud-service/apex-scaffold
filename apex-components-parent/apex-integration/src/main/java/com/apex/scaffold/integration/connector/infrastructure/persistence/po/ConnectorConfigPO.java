package com.apex.scaffold.integration.connector.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 连接器配置持久化对象（表 {@code int_connector_config}）。
 *
 * <p>{@code configValue} 为 JSON 配置；敏感项以凭据引用（credentialRef）表示，明文不落本表。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Data
@TableName("int_connector_config")
public class ConnectorConfigPO {

    /** 主键（雪花算法）。 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 租户编码。 */
    private String tenantId;

    /** 连接器类型（HTTP/WEBHOOK/WECHAT/CHUANGLAN/...）。 */
    private String connectorType;

    /** 配置标识（租户内唯一）。 */
    private String configKey;

    /** 连接配置（JSON；敏感项以凭据引用表示）。 */
    private String configValue;

    /** 状态（0-停用，1-正常）。 */
    private Integer status;

    /** 备注。 */
    private String remark;

    /** 创建时间（DB 默认填充）。 */
    private LocalDateTime createTime;

    /** 更新时间（DB ON UPDATE 填充）。 */
    private LocalDateTime updateTime;

    /** 乐观锁版本号。 */
    @Version
    private Integer version;

    /** 逻辑删除标记。 */
    @TableLogic
    private Integer deleted;
}
