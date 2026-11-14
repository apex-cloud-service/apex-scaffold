package com.apex.scaffold.infrastructure.search;

import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.domain.search.IndexSyncPort;
import com.apex.scaffold.domain.search.SearchRepository;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

/**
 * 全文搜索关闭时的降级装配。
 *
 * <p>{@link MeilisearchAdapter} 依赖 {@code com.meilisearch.sdk.Client}，而该 Bean 仅在
 * {@code meilisearch.enabled=true} 时由 {@code MeilisearchConfig} 提供。未启用全文搜索的
 * 环境（basic / it / 默认 prod）必须仍有 {@link SearchRepository} 与 {@link IndexSyncPort}
 * 的实现，否则搜索 Handler、索引事件监听器与索引重建任务会因缺少依赖导致整个上下文启动失败。
 *
 * <p>本配置在缺少上述两个端口实现时注册空实现：搜索返回空分页，索引同步为 no-op 并记录
 * 调试日志。语义为「功能未启用」，不是静默吞错——启用 Meilisearch 后由真实适配器替换。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
public class SearchDisabledConfiguration {

    /**
     * 未启用全文搜索时的空搜索实现。
     *
     * @return 返回空分页的搜索仓库
     */
    @Bean
    @ConditionalOnMissingBean(SearchRepository.class)
    public SearchRepository disabledSearchRepository() {
        log.info("全文搜索未启用（meilisearch.enabled=false）：搜索请求返回空结果");
        return (keyword, resourceTypes, tenantId, userId, pageNum, pageSize) ->
                Mono.just(PageResponse.of(List.of(), 0L, pageNum, pageSize));
    }

    /**
     * 未启用全文搜索时的空索引同步实现。
     *
     * @return no-op 索引同步端口
     */
    @Bean
    @ConditionalOnMissingBean(IndexSyncPort.class)
    public IndexSyncPort disabledIndexSyncPort() {
        return new IndexSyncPort() {
            @Override
            public Mono<Boolean> indexResource(String resourceType, String resourceId) {
                log.debug("全文搜索未启用，跳过索引写入: {}:{}", resourceType, resourceId);
                return Mono.just(false);
            }

            @Override
            public Mono<Integer> indexResourceBatch(String resourceType, List<String> resourceIds) {
                log.debug("全文搜索未启用，跳过批量索引写入: {}，{} 条", resourceType, resourceIds.size());
                return Mono.just(0);
            }

            @Override
            public Mono<Boolean> removeIndex(String resourceType, String resourceId) {
                return Mono.just(false);
            }

            @Override
            public Mono<Long> rebuildIndex(String resourceType) {
                return Mono.just(0L);
            }

            @Override
            public Mono<Long> syncIncremental(String resourceType, Long sinceTimestamp) {
                return Mono.just(0L);
            }
        };
    }
}
