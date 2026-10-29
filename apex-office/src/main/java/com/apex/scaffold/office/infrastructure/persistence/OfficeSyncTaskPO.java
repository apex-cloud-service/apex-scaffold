package com.apex.scaffold.office.infrastructure.persistence;

import com.apex.scaffold.office.domain.OfficePlatform;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 办公平台同步任务持久化对象。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.0
 */
@Data
@TableName("office_sync_task")
public class OfficeSyncTaskPO {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String tenantId;
    private OfficePlatform platform;
    private String syncType;
    private String cursorValue;
    private String state;
    private String failureMessage;
    private Integer version;

    @TableLogic
    private Integer deleted;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
