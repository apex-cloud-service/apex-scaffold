package com.apex.scaffold.domain.service;

/**
 * 业务关键路径指标记录端口。
 *
 * <p>应用层和领域服务只在业务成功提交后调用该端口，具体指标实现由基础设施层提供。
 * 这样可以避免业务层依赖 Micrometer，同时保证监控数据来自真实的业务结果，而不是来自
 * 请求进入或方法尝试执行的数量。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public interface BusinessMetricsPort {

    /**
     * 记录一次已经成功落库的积分变动。
     *
     * <p>金额应为本次数据库更新实际增加的正数；达到余额上限而未增加的操作不应调用该方法。</p>
     *
     * @param type 积分变动原因或类型
     * @param amount 实际落库的积分增量
     */
    void recordCreditChange(String type, long amount);
}
