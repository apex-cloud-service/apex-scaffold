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
 * 操作日志持久化对象。
 *
 * <p>对应数据库中的sys_operation_log表，存储系统操作日志信息。
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
@TableName("sys_operation_log")
/**n * OperationLogPO - 持久化对象n *n * <p>对应数据库表：TODO: 补充表名n *n * @author Apex Teamn * @version 1.0.0n * @since 1.0.0n */
public class OperationLogPO extends BasePO {

    /**
     * 构造操作日志持久化对象。
     */

    /**
     * 操作模块。
     */
    @TableField("module")
    private String module;

    /**
     * 操作类型。
     */
    @TableField("operation_type")
    private String operationType;

    /**
     * 操作描述。
     */
    @TableField("description")
    private String description;

    /**
     * 请求方法。
     */
    @TableField("request_method")
    private String requestMethod;

    /**
     * 请求URI。
     */
    @TableField("request_uri")
    private String requestUri;

    /**
     * 请求参数。
     */
    @TableField("request_params")
    private String requestParams;

    /**
     * 响应结果。
     */
    @TableField("response_result")
    private String responseResult;

    /**
     * 操作状态（0-成功，1-失败）。
     */
    @TableField("status")
    private Integer status;

    /**
     * 错误信息。
     */
    @TableField("error_msg")
    private String errorMsg;

    /**
     * 执行时间（毫秒）。
     */
    @TableField("execution_time")
    private Long executionTime;

    /**
     * 操作IP。
     */
    @TableField("ip")
    private String ip;

    /**
     * 操作地点。
     */
    @TableField("location")
    private String location;

    /**
     * 操作用户ID。
     */
    @TableField("user_id")
    private Long userId;

    /**
     * 操作用户名。
     */
    @TableField("user_name")
    private String userName;

    /**
     * 操作时间。
     */
    @TableField("operation_time")
    private LocalDateTime operationTime;

    /**
     * 设备类型。
     */
    @TableField("device_type")
    private String deviceType;

    /**
     * 浏览器类型。
     */
    @TableField("browser")
    private String browser;

    /**
     * 操作系统。
     */
    @TableField("os")
    private String os;

    /**
     * 追踪ID。
     */
    @TableField("trace_id")
    private String traceId;
}
