package com.apex.scaffold.office.notify.domain;

import com.apex.scaffold.common.dto.PageResponse;
import reactor.core.publisher.Mono;

/**
 * 投递请求留痕仓储端口（统一消息门面 / 批次 D1）。
 *
 * <p>投递记录为追加式留痕：仅支持写入与查询，不提供删除或状态回写。
 * 所有方法按租户隔离，数据库访问在 JDBC 调度器执行。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 * @see NotifyRequestRecord
 */
public interface NotifyRequestRecordRepository {

    /**
     * 插入投递留痕记录并回填主键。
     *
     * @param record 留痕记录
     * @return 插入后的记录（含主键）
     */
    Mono<NotifyRequestRecord> insert(NotifyRequestRecord record);

    /**
     * 分页查询投递留痕。
     *
     * @param tenantId     租户标识
     * @param pageNum      页码，从 1 开始
     * @param pageSize     每页大小
     * @param channel      通道过滤，为 null 表示不限
     * @param templateCode 模板编码过滤，为 null 表示不限
     * @param status       投递状态过滤，为 null 表示不限
     * @return 分页结果
     */
    Mono<PageResponse<NotifyRequestRecord>> findPage(
            String tenantId, Integer pageNum, Integer pageSize, String channel, String templateCode, String status);
}
