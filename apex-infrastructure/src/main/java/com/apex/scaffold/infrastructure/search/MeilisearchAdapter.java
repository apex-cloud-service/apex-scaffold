package com.apex.scaffold.infrastructure.search;

import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.domain.search.IndexSyncPort;
import com.apex.scaffold.domain.search.SearchRepository;
import com.apex.scaffold.domain.search.SearchResultEntity;
import com.apex.scaffold.infrastructure.config.MeilisearchConfig.MeilisearchProperties;
import com.apex.scaffold.infrastructure.persistence.mapper.*;
import com.apex.scaffold.infrastructure.persistence.po.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.Index;
import com.meilisearch.sdk.SearchRequest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
@Component
@RequiredArgsConstructor
public class MeilisearchAdapter implements SearchRepository, IndexSyncPort {

    private final Client meilisearchClient;
    private final MeilisearchProperties properties;
    private final ObjectMapper objectMapper;
    
    // Mappers
    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final MenuMapper menuMapper;
    private final DeptMapper deptMapper;
    private final DictDataMapper dictMapper;
    private final ConfigMapper configMapper;
    
    private static final String INDEX_NAME = "system_resources";

    @Override
    public Mono<PageResponse<SearchResultEntity>> search(
            String keyword,
            Set<String> resourceTypes,
            Long tenantId,
            Long userId,
            int pageNum,
            int pageSize) {
        
        return Mono.fromCallable(() -> {
            Index index = meilisearchClient.index(properties.getIndexPrefix() + INDEX_NAME);
            
            List<String> filters = new ArrayList<>();
            if (tenantId != null) {
                filters.add("tenantId = '" + tenantId + "'");
            }
            if (resourceTypes != null && !resourceTypes.isEmpty()) {
                String typeFilter = resourceTypes.stream()
                    .map(type -> "resourceType = '" + type + "'")
                    .collect(Collectors.joining(" OR "));
                filters.add("(" + typeFilter + ")");
            }
            filters.add("status = 1");
            
            String filterStr = filters.isEmpty() ? null : String.join(" AND ", filters);
            
            SearchRequest request = new SearchRequest(keyword)
                .setOffset((pageNum - 1) * pageSize)
                .setLimit(Math.min(pageSize, 100));
            
            if (filterStr != null) {
                request.setFilter(new String[]{filterStr});
            }
            
            com.meilisearch.sdk.model.Searchable searchResult = index.search(request);
            
            List<SearchResultEntity> entities = new ArrayList<>();
            for (Object hit : searchResult.getHits()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> doc = (Map<String, Object>) hit;
                entities.add(mapToEntity(doc));
            }
            
            long total = searchResult.getHits() != null ? searchResult.getHits().size() : 0;
            return PageResponse.of(entities, total, pageNum, pageSize);
            
        }).subscribeOn(Schedulers.boundedElastic())
          .doOnError(e -> log.error("Search failed: keyword={}", keyword, e))
          .onErrorReturn(PageResponse.of(Collections.emptyList(), 0L, pageNum, pageSize));
    }

    @Override
    public Mono<Boolean> indexResource(String resourceType, String resourceId) {
        return Mono.fromCallable(() -> {
            Map<String, Object> document = fetchResourceDocument(resourceType, resourceId);
            if (document == null) {
                log.warn("Resource not found: type={}, id={}", resourceType, resourceId);
                return false;
            }
            
            String json = objectMapper.writeValueAsString(Collections.singletonList(document));
            Index index = meilisearchClient.index(properties.getIndexPrefix() + INDEX_NAME);
            index.addDocuments(json);
            
            log.debug("Indexed resource: type={}, id={}", resourceType, resourceId);
            return true;
        }).subscribeOn(Schedulers.boundedElastic())
          .doOnError(e -> log.error("Index failed: type={}, id={}", resourceType, resourceId, e))
          .onErrorReturn(false);
    }

    @Override
    public Mono<Integer> indexResourceBatch(String resourceType, List<String> resourceIds) {
        if (resourceIds == null || resourceIds.isEmpty()) {
            return Mono.just(0);
        }
        
        return Mono.fromCallable(() -> {
            List<Map<String, Object>> documents = resourceIds.stream()
                .map(id -> fetchResourceDocument(resourceType, id))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
            
            if (documents.isEmpty()) {
                return 0;
            }
            
            String json = objectMapper.writeValueAsString(documents);
            Index index = meilisearchClient.index(properties.getIndexPrefix() + INDEX_NAME);
            index.addDocuments(json);
            
            log.info("Batch indexed: type={}, count={}", resourceType, documents.size());
            return documents.size();
        }).subscribeOn(Schedulers.boundedElastic())
          .doOnError(e -> log.error("Batch index failed: type={}", resourceType, e))
          .onErrorReturn(0);
    }

    @Override
    public Mono<Boolean> removeIndex(String resourceType, String resourceId) {
        return Mono.fromCallable(() -> {
            String documentId = resourceType + "_" + resourceId;
            Index index = meilisearchClient.index(properties.getIndexPrefix() + INDEX_NAME);
            index.deleteDocument(documentId);
            
            log.debug("Removed index: type={}, id={}", resourceType, resourceId);
            return true;
        }).subscribeOn(Schedulers.boundedElastic())
          .doOnError(e -> log.error("Remove index failed: type={}, id={}", resourceType, resourceId, e))
          .onErrorReturn(false);
    }

    @Override
    public Mono<Long> rebuildIndex(String resourceType) {
        return Mono.fromCallable(() -> {
            log.info("Starting index rebuild: type={}", resourceType);
            
            List<String> types = resourceType == null
                    ? Arrays.asList("user", "role", "menu", "dept", "dict", "config")
                    : Collections.singletonList(resourceType);
            
            long totalCount = 0;
            for (String type : types) {
                try {
                    List<String> ids = fetchAllResourceIds(type);
                    log.info("Rebuilding index for type={}, total={}", type, ids.size());
                    
                    for (int i = 0; i < ids.size(); i += properties.getBatchSize()) {
                        List<String> batch = ids.subList(i, Math.min(i + properties.getBatchSize(), ids.size()));
                        Integer batchCount = indexResourceBatch(type, batch).block();
                        totalCount += (batchCount != null ? batchCount : 0);
                    }
                    
                    log.info("Completed index rebuild for type={}, count={}", type, totalCount);
                } catch (Exception e) {
                    log.error("Failed to rebuild index for type={}", type, e);
                }
            }
            
            log.info("Index rebuild completed: type={}, total={}", resourceType, totalCount);
            return totalCount;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Long> syncIncremental(String resourceType, Long sinceTimestamp) {
        return Mono.fromCallable(() -> {
            log.info("Starting incremental sync: type={}, since={}", resourceType, sinceTimestamp);
            
            List<String> ids = fetchIncrementalResourceIds(resourceType, sinceTimestamp);
            
            if (ids.isEmpty()) {
                log.info("No incremental changes: type={}", resourceType);
                return 0L;
            }
            
            long totalCount = 0;
            for (int i = 0; i < ids.size(); i += properties.getBatchSize()) {
                List<String> batch = ids.subList(i, Math.min(i + properties.getBatchSize(), ids.size()));
                Integer batchCount = indexResourceBatch(resourceType, batch).block();
                totalCount += (batchCount != null ? batchCount : 0);
            }
            
            log.info("Incremental sync completed: type={}, count={}", resourceType, totalCount);
            return totalCount;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private Map<String, Object> fetchResourceDocument(String resourceType, String resourceId) {
        return switch (resourceType) {
            case "user" -> fetchUserDocument(resourceId);
            case "role" -> fetchRoleDocument(resourceId);
            case "menu" -> fetchMenuDocument(resourceId);
            case "dept" -> fetchDeptDocument(resourceId);
            case "dict" -> fetchDictDocument(resourceId);
            case "config" -> fetchConfigDocument(resourceId);
            default -> null;
        };
    }

    private Map<String, Object> fetchUserDocument(String resourceId) {
        UserPO user = userMapper.selectById(Long.parseLong(resourceId));
        if (user == null || user.getStatus() == 0) return null;
        
        Map<String, Object> doc = new HashMap<>();
        doc.put("id", "user_" + resourceId);
        doc.put("resourceType", "user");
        doc.put("resourceId", resourceId);
        doc.put("title", user.getUsername());
        doc.put("subtitle", "");
        doc.put("description", user.getPhone() != null ? maskPhone(user.getPhone()) : "");
        doc.put("status", user.getStatus());
        doc.put("tenantId", user.getTenantId() != null ? user.getTenantId() : "0");
        doc.put("createdAt", toTimestamp(user.getCreateTime()));
        doc.put("updatedAt", toTimestamp(user.getUpdateTime()));
        doc.put("extra", Map.of("phone", maskPhone(user.getPhone())));
        return doc;
    }

    private Map<String, Object> fetchRoleDocument(String resourceId) {
        RolePO role = roleMapper.selectById(Long.parseLong(resourceId));
        if (role == null || role.getStatus() == 0) return null;
        
        Map<String, Object> doc = new HashMap<>();
        doc.put("id", "role_" + resourceId);
        doc.put("resourceType", "role");
        doc.put("resourceId", resourceId);
        doc.put("title", role.getName());
        doc.put("subtitle", role.getCode());
        doc.put("description", role.getDescription() != null ? role.getDescription() : "");
        doc.put("status", role.getStatus());
        doc.put("tenantId", "0");
        doc.put("createdAt", toTimestamp(role.getCreateTime()));
        doc.put("updatedAt", toTimestamp(role.getUpdateTime()));
        doc.put("extra", Map.of("dataScope", role.getDataScope()));
        return doc;
    }

    private Map<String, Object> fetchMenuDocument(String resourceId) {
        MenuPO menu = menuMapper.selectById(Long.parseLong(resourceId));
        if (menu == null || menu.getStatus() == 0) return null;
        
        Map<String, Object> doc = new HashMap<>();
        doc.put("id", "menu_" + resourceId);
        doc.put("resourceType", "menu");
        doc.put("resourceId", resourceId);
        doc.put("title", menu.getMenuName());
        doc.put("subtitle", menu.getPath() != null ? menu.getPath() : "");
        doc.put("description", menu.getComponent() != null ? menu.getComponent() : "");
        doc.put("status", menu.getStatus());
        doc.put("tenantId", "0");
        doc.put("createdAt", toTimestamp(menu.getCreateTime()));
        doc.put("updatedAt", toTimestamp(menu.getUpdateTime()));
        Map<String, Object> extra = new HashMap<>();
        extra.put("menuType", menu.getMenuType());
        if (menu.getIcon() != null) extra.put("icon", menu.getIcon());
        extra.put("sort", menu.getSort());
        doc.put("extra", extra);
        return doc;
    }

    private Map<String, Object> fetchDeptDocument(String resourceId) {
        DeptPO dept = deptMapper.selectById(Long.parseLong(resourceId));
        if (dept == null || dept.getStatus() == 0) return null;
        
        Map<String, Object> doc = new HashMap<>();
        doc.put("id", "dept_" + resourceId);
        doc.put("resourceType", "dept");
        doc.put("resourceId", resourceId);
        doc.put("title", dept.getName());
        doc.put("subtitle", "");
        doc.put("description", buildDeptDescription(dept));
        doc.put("status", dept.getStatus());
        doc.put("tenantId", dept.getTenantId() != null ? dept.getTenantId() : "0");
        doc.put("createdAt", toTimestamp(dept.getCreateTime()));
        doc.put("updatedAt", toTimestamp(dept.getUpdateTime()));
        doc.put("extra", Map.of("orderNum", dept.getOrderNum()));
        return doc;
    }

    private Map<String, Object> fetchDictDocument(String resourceId) {
        DictDataPO dict = dictMapper.selectById(Long.parseLong(resourceId));
        if (dict == null || dict.getStatus() == 0) return null;
        
        Map<String, Object> doc = new HashMap<>();
        doc.put("id", "dict_" + resourceId);
        doc.put("resourceType", "dict");
        doc.put("resourceId", resourceId);
        doc.put("title", dict.getDictLabel());
        doc.put("subtitle", dict.getDictValue());
        doc.put("description", dict.getDictType());
        doc.put("status", dict.getStatus());
        doc.put("tenantId", "0");
        doc.put("createdAt", toTimestamp(dict.getCreateTime()));
        doc.put("updatedAt", toTimestamp(dict.getUpdateTime()));
        doc.put("extra", Map.of("sort", dict.getDictSort()));
        return doc;
    }

    private Map<String, Object> fetchConfigDocument(String resourceId) {
        ConfigPO config = configMapper.selectById(Long.parseLong(resourceId));
        if (config == null) return null;
        
        Map<String, Object> doc = new HashMap<>();
        doc.put("id", "config_" + resourceId);
        doc.put("resourceType", "config");
        doc.put("resourceId", resourceId);
        doc.put("title", config.getConfigName());
        doc.put("subtitle", config.getConfigKey());
        doc.put("description", config.getConfigValue() != null ? maskSensitiveValue(config.getConfigValue()) : "");
        doc.put("status", 1);
        doc.put("tenantId", "0");
        doc.put("createdAt", toTimestamp(config.getCreateTime()));
        doc.put("updatedAt", toTimestamp(config.getUpdateTime()));
        doc.put("extra", Map.of("configType", config.getConfigType()));
        return doc;
    }

    private List<String> fetchAllResourceIds(String resourceType) {
        return switch (resourceType) {
            case "user" -> userMapper.selectList(new LambdaQueryWrapper<UserPO>().select(UserPO::getId).eq(UserPO::getStatus, 1))
                .stream().map(u -> u.getId().toString()).collect(Collectors.toList());
            case "role" -> roleMapper.selectList(new LambdaQueryWrapper<RolePO>().select(RolePO::getId).eq(RolePO::getStatus, 1))
                .stream().map(r -> r.getId().toString()).collect(Collectors.toList());
            case "menu" -> menuMapper.selectList(new LambdaQueryWrapper<MenuPO>().select(MenuPO::getId).eq(MenuPO::getStatus, 1))
                .stream().map(m -> m.getId().toString()).collect(Collectors.toList());
            case "dept" -> deptMapper.selectList(new LambdaQueryWrapper<DeptPO>().select(DeptPO::getId).eq(DeptPO::getStatus, 1))
                .stream().map(d -> d.getId().toString()).collect(Collectors.toList());
            case "dict" -> dictMapper.selectList(new LambdaQueryWrapper<DictDataPO>().select(DictDataPO::getId).eq(DictDataPO::getStatus, 1))
                .stream().map(d -> d.getId().toString()).collect(Collectors.toList());
            case "config" -> configMapper.selectList(new LambdaQueryWrapper<ConfigPO>().select(ConfigPO::getId))
                .stream().map(c -> c.getId().toString()).collect(Collectors.toList());
            default -> Collections.emptyList();
        };
    }

    private List<String> fetchIncrementalResourceIds(String resourceType, Long sinceTimestamp) {
        LocalDateTime since = LocalDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(sinceTimestamp),
            java.time.ZoneId.systemDefault()
        );
        
        return switch (resourceType) {
            case "user" -> userMapper.selectList(new LambdaQueryWrapper<UserPO>().select(UserPO::getId)
                .gt(UserPO::getUpdateTime, since).eq(UserPO::getStatus, 1))
                .stream().map(u -> u.getId().toString()).collect(Collectors.toList());
            case "role" -> roleMapper.selectList(new LambdaQueryWrapper<RolePO>().select(RolePO::getId)
                .gt(RolePO::getUpdateTime, since).eq(RolePO::getStatus, 1))
                .stream().map(r -> r.getId().toString()).collect(Collectors.toList());
            case "menu" -> menuMapper.selectList(new LambdaQueryWrapper<MenuPO>().select(MenuPO::getId)
                .gt(MenuPO::getUpdateTime, since).eq(MenuPO::getStatus, 1))
                .stream().map(m -> m.getId().toString()).collect(Collectors.toList());
            case "dept" -> deptMapper.selectList(new LambdaQueryWrapper<DeptPO>().select(DeptPO::getId)
                .gt(DeptPO::getUpdateTime, since).eq(DeptPO::getStatus, 1))
                .stream().map(d -> d.getId().toString()).collect(Collectors.toList());
            case "dict" -> dictMapper.selectList(new LambdaQueryWrapper<DictDataPO>().select(DictDataPO::getId)
                .gt(DictDataPO::getUpdateTime, since).eq(DictDataPO::getStatus, 1))
                .stream().map(d -> d.getId().toString()).collect(Collectors.toList());
            case "config" -> configMapper.selectList(new LambdaQueryWrapper<ConfigPO>().select(ConfigPO::getId)
                .gt(ConfigPO::getUpdateTime, since))
                .stream().map(c -> c.getId().toString()).collect(Collectors.toList());
            default -> Collections.emptyList();
        };
    }

    private SearchResultEntity mapToEntity(Map<String, Object> doc) {
        return SearchResultEntity.builder()
            .resourceType((String) doc.get("resourceType"))
            .resourceId((String) doc.get("resourceId"))
            .title((String) doc.get("title"))
            .subtitle((String) doc.get("subtitle"))
            .description((String) doc.get("description"))
            .highlightSnippet("")
            .score(1.0)
            .status((Integer) doc.get("status"))
            .createdAt(toLocalDateTime((Long) doc.get("createdAt")))
            .updatedAt(toLocalDateTime((Long) doc.get("updatedAt")))
            .build();
    }

    private Long toTimestamp(LocalDateTime dateTime) {
        return dateTime == null ? null :
            dateTime.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private LocalDateTime toLocalDateTime(Long timestamp) {
        return timestamp == null ? null :
            LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(timestamp),
                java.time.ZoneId.systemDefault()
            );
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() != 11) return phone;
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }

    private String buildDeptDescription(DeptPO dept) {
        StringBuilder desc = new StringBuilder();
        if (dept.getLeader() != null) {
            desc.append("负责人：").append(dept.getLeader());
        }
        if (dept.getPhone() != null) {
            if (desc.length() > 0) desc.append(" / ");
            desc.append("电话：").append(dept.getPhone());
        }
        return desc.toString();
    }

    private String maskSensitiveValue(String value) {
        if (value == null || value.length() <= 4) return value;
        return value.substring(0, 2) + "***" + value.substring(value.length() - 2);
    }
}
