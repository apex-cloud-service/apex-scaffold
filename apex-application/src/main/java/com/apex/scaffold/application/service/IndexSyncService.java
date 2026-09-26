package com.apex.scaffold.application.service;

import com.apex.scaffold.domain.search.IndexSyncPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 索引同步服务
 *
 * <p>提供索引管理功能，支持手动触发索引重建。
 *
 * @author Apex Team
 * @since 1.3.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IndexSyncService {

    private final IndexSyncPort indexSyncPort;

    /**
     * 索引单个资源
     *
     * @param resourceType 资源类型
     * @param resourceId   资源ID
     * @return 是否成功
     */
    public Mono<Boolean> indexResource(String resourceType, String resourceId) {
        log.info("Indexing resource: type={}, id={}", resourceType, resourceId);
        return indexSyncPort
                .indexResource(resourceType, resourceId)
                .doOnSuccess(success -> log.info(
                        "Index resource result: type={}, id={}, success={}", resourceType, resourceId, success));
    }

    /**
     * 重建索引
     *
     * @param resourceType 资源类型，null表示重建所有类型
     * @return 索引总数
     */
    public Mono<Long> rebuildIndex(String resourceType) {
        log.warn("Rebuilding index: type={}", resourceType);
        return indexSyncPort
                .rebuildIndex(resourceType)
                .doOnSuccess(count -> log.warn("Index rebuild completed: type={}, count={}", resourceType, count));
    }
}
