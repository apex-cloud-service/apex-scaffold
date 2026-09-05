package com.apex.scaffold.application.service;

import com.apex.scaffold.application.dto.search.SearchResultDTO;
import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.domain.search.SearchRepository;
import com.apex.scaffold.domain.search.SearchResultEntity;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 搜索服务
 *
 * <p>提供统一全文搜索功能，支持跨资源类型混合搜索。
 *
 * @author Apex Team
 * @since 1.3.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchService {

    private final SearchRepository searchRepository;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 执行全文搜索
     *
     * @param keyword       搜索关键词
     * @param resourceTypes 资源类型集合，null表示搜索所有类型
     * @param pageNum       页码
     * @param pageSize      每页大小
     * @return 搜索结果分页
     */
    public Mono<PageResponse<SearchResultDTO>> search(
            String keyword,
            Set<String> resourceTypes,
            int pageNum,
            int pageSize) {

        // 获取当前租户ID
        String tenantIdStr = TenantContext.currentTenantId().orElse(null);
        Long tenantId = tenantIdStr != null ? Long.parseLong(tenantIdStr) : null;

        log.debug(
                "Executing search: keyword={}, types={}, tenant={}, page={}, size={}",
                keyword,
                resourceTypes,
                tenantId,
                pageNum,
                pageSize);

        // 调用搜索仓储
        return searchRepository
                .search(keyword, resourceTypes, tenantId, null, pageNum, pageSize)
                .map(pageResponse -> {
                    List<SearchResultDTO> dtos =
                            pageResponse.getList().stream()
                                    .map(this::toDTO)
                                    .collect(Collectors.toList());
                    return PageResponse.of(dtos, pageResponse.getTotal(), pageNum, pageSize);
                })
                .doOnSuccess(result -> log.debug("Search completed: total={}", result.getTotal()));
    }

    /**
     * Entity 转 DTO
     */
    private SearchResultDTO toDTO(SearchResultEntity entity) {
        return SearchResultDTO.builder()
                .resourceType(entity.getResourceType())
                .resourceId(entity.getResourceId())
                .title(entity.getTitle())
                .subtitle(entity.getSubtitle())
                .description(entity.getDescription())
                .highlightSnippet(entity.getHighlightSnippet())
                .score(entity.getScore())
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt() != null ? entity.getCreatedAt().format(FORMATTER) : null)
                .updatedAt(entity.getUpdatedAt() != null ? entity.getUpdatedAt().format(FORMATTER) : null)
                .build();
    }
}
