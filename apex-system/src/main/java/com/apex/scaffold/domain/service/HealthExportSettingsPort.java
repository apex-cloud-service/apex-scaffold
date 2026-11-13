package com.apex.scaffold.domain.service;

import java.time.LocalDateTime;

/**
 * 体检快照异步导出参数端口。
 *
 * <p>导出上限、结果保留时长、重试次数与清理参数属于运行配置，配置对象位于基础设施层，
 * 应用层与调度层只能通过本端口读取，避免反向依赖具体配置类。</p>
 *
 * @author Apex Team
 * @version 1.1.0
 * @since 1.3.2
 */
public interface HealthExportSettingsPort {

    /**
     * 单任务最大生成行数。
     *
     * @return 最大行数
     */
    int maxRows();

    /**
     * 任务最大尝试次数。
     *
     * @return 最大尝试次数
     */
    int maxAttempts();

    /**
     * 结果保留小时数，超出后连同文件一起清理。
     *
     * @return 保留小时数
     */
    int resultTtlHours();

    /**
     * 异步导出分页大小，用于分批读取快照避免一次性加载。
     *
     * @return 分页大小
     */
    int batchSize();

    /**
     * 后台任务归属租户标识。
     *
     * <p>定时任务没有请求上下文，无法从认证信息取得租户，只能使用配置的平台租户。</p>
     *
     * @return 平台租户标识
     */
    String tenantId();

    /**
     * 计算导出结果的过期时间。
     *
     * @param now 当前时间
     * @return 完成时间早于该时刻的任务需要清理
     */
    default LocalDateTime resultExpireBefore(LocalDateTime now) {
        return now.minusHours(resultTtlHours());
    }
}
