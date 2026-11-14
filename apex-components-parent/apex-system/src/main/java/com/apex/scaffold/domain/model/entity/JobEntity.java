package com.apex.scaffold.domain.model.entity;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * 定时任务聚合根实体。
 *
 * <p>表示一条可动态注册、启停与执行的定时任务定义。任务通过
 * {@code invokeTarget} 指定要调用的 Spring Bean 方法与参数，
 * 通过 {@code cronExpression} 定义执行周期，由调度器在应用内
 * 动态注册执行。</p>
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>调用目标格式为 {@code beanName.methodName} 或 {@code beanName.methodName("arg", 1)}</li>
 *   <li>Cron 表达式采用 6/7 段标准格式（秒-周[年]），非法表达式拒绝保存</li>
 *   <li>任务可被启用或停用，停用后调度器取消注册，不再触发执行</li>
 *   <li>{@code concurrent} 控制同一任务是否允许并发执行，禁止并发时上一轮未完成会跳过本轮</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see JobLogEntity
 * @see BaseEntity
 */
@Getter
@lombok.Setter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class JobEntity extends BaseEntity {

    /**
     * 序列化版本号。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 默认任务分组。
     */
    public static final String DEFAULT_GROUP = "DEFAULT";

    private String tenantId;

    /**
     * 任务名称。
     *
     * <p>任务的显示名称，用于管理界面展示与执行日志标识。</p>
     */
    private String jobName;

    /**
     * 任务分组。
     *
     * <p>用于对任务分类管理，缺省为 {@link #DEFAULT_GROUP}。</p>
     */
    private String jobGroup;

    /**
     * 调用目标。
     *
     * <p>Spring Bean 方法与参数描述，格式为
     * {@code beanName.methodName} 或 {@code beanName.methodName("arg", 1)}。</p>
     */
    private String invokeTarget;

    /**
     * Cron 表达式。
     *
     * <p>6/7 段标准格式，定义任务的执行周期。</p>
     */
    private String cronExpression;

    /**
     * 错失执行策略。
     *
     * <p>取值说明：0-默认（错过即跳过），1-立即补执行，2-放弃。</p>
     */
    private Integer misfirePolicy;

    /**
     * 是否允许并发执行。
     *
     * <p>取值说明：0-禁止（上一轮未完成时跳过本轮），1-允许。</p>
     */
    private Integer concurrent;

    /**
     * 任务状态。
     *
     * <p>取值说明：0-停用，1-正常。停用的任务不参与调度。</p>
     */
    private Integer status;

    /**
     * 备注。
     */
    private String remark;

    /**
     * 默认构造函数。
     */
    public JobEntity() {
        super();
    }

    /**
     * 静态工厂方法：创建定时任务。
     *
     * @param jobName 任务名称
     * @param jobGroup 任务分组
     * @param invokeTarget 调用目标
     * @param cronExpression Cron 表达式
     * @return 新创建的定时任务实体，默认允许并发且启用
     */
    public static JobEntity create(String jobName, String jobGroup, String invokeTarget, String cronExpression) {
        return create(jobName, jobGroup, invokeTarget, cronExpression, null);
    }

    /**
     * 静态工厂方法：创建定时任务（指定租户）。
     *
     * <p>在 {@link #create(String, String, String, String)} 的基础上额外指定租户归属，
     * 便于多租户场景下按租户隔离任务调度。</p>
     *
     * @param jobName 任务名称
     * @param jobGroup 任务分组
     * @param invokeTarget 调用目标
     * @param cronExpression Cron 表达式
     * @param tenantId 租户 ID，可为 null 表示不归属具体租户
     * @return 新创建的定时任务实体，默认允许并发且启用
     */
    public static JobEntity create(
            String jobName, String jobGroup, String invokeTarget, String cronExpression, String tenantId) {
        JobEntity entity = new JobEntity();
        entity.tenantId = tenantId;
        entity.jobName = jobName;
        entity.jobGroup = jobGroup == null || jobGroup.isBlank() ? DEFAULT_GROUP : jobGroup;
        entity.invokeTarget = invokeTarget;
        entity.cronExpression = cronExpression;
        entity.misfirePolicy = MisfirePolicy.DEFAULT.getCode();
        entity.concurrent = ConcurrentMode.ALLOWED.getCode();
        entity.status = JobState.ENABLED.getCode();
        entity.initTimestamps();
        return entity;
    }

    /**
     * 领域行为：更新任务核心信息。
     *
     * @param jobName 任务名称
     * @param jobGroup 任务分组
     * @param invokeTarget 调用目标
     * @param cronExpression Cron 表达式
     * @param remark 备注
     */
    public void updateInfo(String jobName, String jobGroup, String invokeTarget, String cronExpression, String remark) {
        this.jobName = jobName;
        this.jobGroup = jobGroup == null || jobGroup.isBlank() ? DEFAULT_GROUP : jobGroup;
        this.invokeTarget = invokeTarget;
        this.cronExpression = cronExpression;
        this.remark = remark;
        touch();
    }

    /**
     * 领域行为：启用任务。
     */
    public void enable() {
        this.status = JobState.ENABLED.getCode();
        touch();
    }

    /**
     * 领域行为：停用任务。
     */
    public void disable() {
        this.status = JobState.DISABLED.getCode();
        touch();
    }

    /**
     * 领域行为：检查任务是否已启用。
     *
     * @return true-已启用，false-已停用
     */
    public boolean isEnabled() {
        return status != null && status.equals(JobState.ENABLED.getCode());
    }

    /**
     * 领域行为：检查是否允许并发执行。
     *
     * @return true-允许并发，false-禁止并发
     */
    public boolean isConcurrentAllowed() {
        return concurrent == null || concurrent.equals(ConcurrentMode.ALLOWED.getCode());
    }

    /**
     * 任务状态枚举。
     */
    @Getter
    public enum JobState {
        /** 停用 */
        DISABLED(0),
        /** 正常 */
        ENABLED(1);

        private final Integer code;

        /**
         * 创建 {@code JobState} 实例。
         *
         * @param code 枚举编码
         */
        JobState(Integer code) {
            this.code = code;
        }
    }

    /**
     * 错失执行策略枚举。
     */
    @Getter
    public enum MisfirePolicy {
        /** 默认：错过即跳过 */
        DEFAULT(0),
        /** 立即补执行 */
        FIRE_ONCE(1),
        /** 放弃 */
        IGNORE(2);

        private final Integer code;

        /**
         * 创建 {@code MisfirePolicy} 实例。
         *
         * @param code 枚举编码
         */
        MisfirePolicy(Integer code) {
            this.code = code;
        }
    }

    /**
     * 并发模式枚举。
     */
    @Getter
    public enum ConcurrentMode {
        /** 禁止并发 */
        FORBIDDEN(0),
        /** 允许并发 */
        ALLOWED(1);

        private final Integer code;

        /**
         * 创建 {@code ConcurrentMode} 实例。
         *
         * @param code 枚举编码
         */
        ConcurrentMode(Integer code) {
            this.code = code;
        }
    }
}
