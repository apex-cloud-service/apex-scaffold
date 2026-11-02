package com.apex.scaffold.file.application.service;

import com.apex.scaffold.file.domain.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 文件存储统计服务（方案 D5 只读统计）。
 *
 * <p>提供租户维度的存储用量只读查询，供文件服务内部统计端点
 * （{@code GET /internal/v1/file/stats}，聚合门面消费）使用。
 *
 * <p>口径说明：与存储配额校验（方案批次 B5）保持一致，
 * 复用 {@link DocumentRepository#sumSizeByTenant(String)} 对
 * {@code file_document} 表按租户聚合 {@code file_size}，不在此重复定义
 * 其他聚合口径，避免配额与统计两套数字漂移。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.2
 */
@Service
@RequiredArgsConstructor
public class StorageStatsService {

    private final DocumentRepository documentRepository;

    /**
     * 查询租户已用存储字节数。
     *
     * @param tenantId 可信租户标识
     * @return 已用字节数（无数据时为 0）
     */
    public Mono<Long> tenantStorageUsage(String tenantId) {
        return documentRepository.sumSizeByTenant(tenantId);
    }
}
