package com.apex.scaffold.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.*;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 审计日志实体
 *
 * <p>记录用户的关键操作,用于安全审计和合规检查。
 *
 * @author Apex Team
 * @version 1.0.0
 */
@Data
@TableName("audit_log")
public class AuditLogPO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 操作人ID
     */
    private String operatorId;

    /**
     * 操作人姓名
     */
    private String operatorName;

    /**
     * 操作人IP地址
     */
    private String operatorIp;

    /**
     * 操作模块
     */
    private String module;

    /**
     * 操作类型(CREATE/UPDATE/DELETE/QUERY等)
     */
    private String operationType;

    /**
     * 操作描述
     */
    private String description;

    /**
     * 请求方法(全限定名)
     */
    private String method;

    /**
     * 请求URL
     */
    private String requestUrl;

    /**
     * 请求参数(JSON格式)
     */
    private String requestParams;

    /**
     * 响应结果(JSON格式)
     */
    private String responseResult;

    /**
     * 执行时长(毫秒)
     */
    private Long duration;

    /**
     * 操作状态(SUCCESS/FAIL)
     */
    private String status;

    /**
     * 错误信息
     */
    private String errorMsg;

    /**
     * 业务ID(可选,关联具体业务数据)
     */
    private String businessId;

    /**
     * 租户ID(多租户场景)
     */
    private String tenantId;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 逻辑删除(0-未删除, 1-已删除)
     */
    @TableLogic
    private Integer deleted;
}
