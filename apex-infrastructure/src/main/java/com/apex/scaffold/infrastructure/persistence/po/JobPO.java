package com.apex.scaffold.infrastructure.persistence.po;

import com.apex.scaffold.common.persistence.po.BasePO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 定时任务持久化对象。
 *
 * <p>对应数据库中的 {@code sys_job} 表，存储定时任务定义。
 * 任务通过 Cron 表达式与调用目标描述执行计划与逻辑。</p>
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
@TableName("sys_job")
public class JobPO extends BasePO {

    /**
     * 构造定时任务持久化对象。
     */
    private String tenantId;

    /**
     * 任务名称。
     */
    private String jobName;

    /**
     * 任务分组。
     */
    private String jobGroup;

    /**
     * 调用目标（beanName.methodName 或带参数形式）。
     */
    private String invokeTarget;

    /**
     * Cron 表达式。
     */
    private String cronExpression;

    /**
     * 错失执行策略（0-默认，1-立即补执行，2-放弃）。
     */
    private Integer misfirePolicy;

    /**
     * 是否允许并发（0-禁止，1-允许）。
     */
    private Integer concurrent;

    /**
     * 任务状态（0-停用，1-正常）。
     */
    private Integer status;

    /**
     * 备注。
     */
    private String remark;
}
