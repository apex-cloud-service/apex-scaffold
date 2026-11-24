package com.apex.scaffold.file.domain.service;

import reactor.core.publisher.Mono;

/**
 * 存储配额断言端口（方案批次 B5）。
 *
 * <p>上传链路前置校验租户存储配额：软上限（先检后写），实现侧负责配额来源
 * （租户中心内部端点）与本租户已用字节聚合的缓存口径。配额服务不可用时按
 * 软上限语义放行（不因配额设施故障阻断业务），并保留告警。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
public interface StorageQuotaPort {

    /**
     * 断言租户存储配额可容纳本次写入。
     *
     * @param tenantCode     租户编码
     * @param incrementBytes 本次写入字节数
     * @throws com.apex.scaffold.common.exception.BusinessException 超限时以
     *         {@code TENANT_QUOTA_EXCEEDED} 终止
     * @return 操作完成信号，失败时传播错误
     */
    Mono<Void> assertStorageQuota(String tenantCode, long incrementBytes);
}
