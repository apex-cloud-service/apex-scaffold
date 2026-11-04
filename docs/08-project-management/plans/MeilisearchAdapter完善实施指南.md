# MeilisearchAdapter 完善实施指南

> **历史快照**：本文记录某一时点的状态，模块数、端口、路径与类名以当前代码为准，不作为现状依据。

> 基于当前MVP简化版本，完善真实搜索功能

## 📋 当前状态

**已完成**：
- ✅ 框架搭建完成（Domain/Infrastructure/Application/Interfaces层）
- ✅ 编译通过（Infrastructure层BUILD SUCCESS）
- ✅ 配置完整（Meilisearch、Podman Compose）
- ✅ 事件驱动索引和定时任务

**当前限制**：
- ⚠️ 所有方法返回空结果/默认值
- ⚠️ 未连接真实Meilisearch实例
- ⚠️ 未实现数据库查询逻辑

---

## 🎯 完善目标

将当前MVP版本升级为可用的真实搜索系统。

---

## 📚 前置准备

### 1. 查阅Meilisearch SDK文档

**官方文档**：
- GitHub：https://github.com/meilisearch/meilisearch-java
- 文档：https://www.meilisearch.com/docs/learn/getting_started/quick_start

**关键API参考**：
```java
// 创建客户端
Client client = new Client(new Config("http://localhost:7700", "masterKey"));

// 获取索引
Index index = client.index("movies");

// 搜索
SearchRequest searchRequest = SearchRequest.builder()
    .q("query")
    .attributesToRetrieve(new String[]{"*"})
    .attributesToHighlight(new String[]{"title"})
    .filter(new String[]{"status = 1"})
    .sort(new String[]{"createdAt:desc"})
    .offset(0)
    .limit(20)
    .build();

SearchResult result = index.search(searchRequest);

// 添加文档
index.addDocuments("[{\"id\": 1, \"title\": \"test\"}]");

// 批量添加
List<Map<String, Object>> docs = Arrays.asList(...);
String json = objectMapper.writeValueAsString(docs);
index.addDocuments(json);

// 删除文档
index.deleteDocument("doc_id");
```

### 2. 确认实际PO字段

参考 `/Users/work/apex-scaffold/.trae/documents/MeilisearchAdapter快速修复指南.md`

**关键字段差异**：
- ✅ `createTime` / `updateTime`（不是createdAt/updatedAt）
- ✅ `tenantId` 是 String 类型（不是Long）
- ⚠️ UserPO 没有 email、nickname 字段
- ⚠️ 各PO类的字段名称需要逐个确认

---

## 🔧 实施步骤

### 步骤一：完善 fetchResourceDocument() 方法

**目标**：从数据库读取资源详情并转换为Meilisearch文档格式

**当前代码**（临时简化版）：
```java
private Map<String, Object> fetchResourceDocument(String resourceType, String resourceId) {
    Map<String, Object> doc = new HashMap<>();
    doc.put("id", resourceType + "_" + resourceId);
    doc.put("resourceType", resourceType);
    doc.put("resourceId", resourceId);
    doc.put("title", "Resource_" + resourceId);
    // ... 其他默认值
    return doc;
}
```

**完善方案**：

#### 1.1 添加Mapper依赖注入

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class MeilisearchAdapter implements SearchRepository, IndexSyncPort {
    
    private final Client meilisearchClient;
    private final MeilisearchProperties properties;
    
    // 新增：注入Mapper
    private final com.apex.scaffold.infrastructure.persistence.mapper.UserMapper userMapper;
    private final com.apex.scaffold.infrastructure.persistence.mapper.RoleMapper roleMapper;
    private final com.apex.scaffold.infrastructure.persistence.mapper.MenuMapper menuMapper;
    private final com.apex.scaffold.infrastructure.persistence.mapper.DeptMapper deptMapper;
    private final com.apex.scaffold.infrastructure.persistence.mapper.DictDataMapper dictMapper;
    private final com.apex.scaffold.infrastructure.persistence.mapper.ConfigMapper configMapper;
    
    // ...
}
```

#### 1.2 实现真实的资源查询

```java
private Map<String, Object> fetchResourceDocument(String resourceType, String resourceId) {
    return switch (resourceType) {
        case "user" -> {
            UserPO user = userMapper.selectById(Long.parseLong(resourceId));
            if (user == null || user.getStatus() == 0) yield null;
            
            Map<String, Object> doc = new HashMap<>();
            doc.put("id", "user_" + resourceId);
            doc.put("resourceType", "user");
            doc.put("resourceId", resourceId);
            doc.put("title", user.getUsername());
            doc.put("subtitle", ""); // UserPO没有nickname字段
            doc.put("description", user.getPhone() != null ? maskPhone(user.getPhone()) : "");
            doc.put("status", user.getStatus());
            doc.put("tenantId", user.getTenantId() != null ? user.getTenantId() : "0");
            doc.put("createdAt", toTimestamp(user.getCreateTime())); // 注意：createTime不是createdAt
            doc.put("updatedAt", toTimestamp(user.getUpdateTime())); // 注意：updateTime不是updatedAt
            doc.put("extra", Map.of("phone", maskPhone(user.getPhone())));
            yield doc;
        }
        
        case "role" -> {
            RolePO role = roleMapper.selectById(Long.parseLong(resourceId));
            if (role == null || role.getStatus() == 0) yield null;
            
            Map<String, Object> doc = new HashMap<>();
            doc.put("id", "role_" + resourceId);
            doc.put("resourceType", "role");
            doc.put("resourceId", resourceId);
            doc.put("title", role.getName()); // 注意：name不是roleName
            doc.put("subtitle", role.getCode()); // 注意：code不是roleCode
            doc.put("description", role.getDescription() != null ? role.getDescription() : "");
            doc.put("status", role.getStatus());
            doc.put("tenantId", role.getTenantId() != null ? role.getTenantId() : "0");
            doc.put("createdAt", toTimestamp(role.getCreateTime()));
            doc.put("updatedAt", toTimestamp(role.getUpdateTime()));
            doc.put("extra", Map.of("dataScope", role.getDataScope()));
            yield doc;
        }
        
        // 其他资源类型类似实现...
        // menu, dept, dict, config
        
        default -> null;
    };
}

// 辅助方法
private Long toTimestamp(LocalDateTime dateTime) {
    return dateTime == null ? null :
        dateTime.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
}

private String maskPhone(String phone) {
    if (phone == null || phone.length() != 11) return phone;
    return phone.substring(0, 3) + "****" + phone.substring(7);
}
```

### 步骤二：完善 fetchAllResourceIds() 方法

**当前代码**：
```java
private List<String> fetchAllResourceIds(String resourceType) {
    return Collections.emptyList();
}
```

**完善方案**：

```java
private List<String> fetchAllResourceIds(String resourceType) {
    return switch (resourceType) {
        case "user" -> userMapper.selectList(
            new LambdaQueryWrapper<UserPO>()
                .select(UserPO::getId)
                .eq(UserPO::getStatus, 1)
        ).stream().map(u -> u.getId().toString()).collect(Collectors.toList());
        
        case "role" -> roleMapper.selectList(
            new LambdaQueryWrapper<RolePO>()
                .select(RolePO::getId)
                .eq(RolePO::getStatus, 1)
        ).stream().map(r -> r.getId().toString()).collect(Collectors.toList());
        
        case "menu" -> menuMapper.selectList(
            new LambdaQueryWrapper<MenuPO>()
                .select(MenuPO::getId)
                .eq(MenuPO::getStatus, 1)
        ).stream().map(m -> m.getId().toString()).collect(Collectors.toList());
        
        case "dept" -> deptMapper.selectList(
            new LambdaQueryWrapper<DeptPO>()
                .select(DeptPO::getId)
                .eq(DeptPO::getStatus, 1)
        ).stream().map(d -> d.getId().toString()).collect(Collectors.toList());
        
        case "dict" -> dictMapper.selectList(
            new LambdaQueryWrapper<DictDataPO>()
                .select(DictDataPO::getId)
                .eq(DictDataPO::getStatus, 1)
        ).stream().map(d -> d.getId().toString()).collect(Collectors.toList());
        
        case "config" -> configMapper.selectList(
            new LambdaQueryWrapper<ConfigPO>()
                .select(ConfigPO::getId)
        ).stream().map(c -> c.getId().toString()).collect(Collectors.toList());
        
        default -> Collections.emptyList();
    };
}
```

### 步骤三：完善 fetchIncrementalResourceIds() 方法

**当前代码**：
```java
private List<String> fetchIncrementalResourceIds(String resourceType, Long sinceTimestamp) {
    return Collections.emptyList();
}
```

**完善方案**：

```java
private List<String> fetchIncrementalResourceIds(String resourceType, Long sinceTimestamp) {
    LocalDateTime since = LocalDateTime.ofInstant(
        java.time.Instant.ofEpochMilli(sinceTimestamp),
        java.time.ZoneId.systemDefault()
    );
    
    return switch (resourceType) {
        case "user" -> userMapper.selectList(
            new LambdaQueryWrapper<UserPO>()
                .select(UserPO::getId)
                .gt(UserPO::getUpdateTime, since) // 注意：updateTime不是updatedAt
                .eq(UserPO::getStatus, 1)
        ).stream().map(u -> u.getId().toString()).collect(Collectors.toList());
        
        case "role" -> roleMapper.selectList(
            new LambdaQueryWrapper<RolePO>()
                .select(RolePO::getId)
                .gt(RolePO::getUpdateTime, since)
                .eq(RolePO::getStatus, 1)
        ).stream().map(r -> r.getId().toString()).collect(Collectors.toList());
        
        // 其他类型类似...
        
        default -> Collections.emptyList();
    };
}
```

### 步骤四：完善 search() 方法

**当前代码**：
```java
public Mono<PageResponse<SearchResultEntity>> search(...) {
    return Mono.just(PageResponse.of(Collections.emptyList(), 0L, pageNum, pageSize));
}
```

**完善方案**：

```java
@Override
public Mono<PageResponse<SearchResultEntity>> search(
        String keyword,
        Set<String> resourceTypes,
        Long tenantId,
        Long userId,
        int pageNum,
        int pageSize) {
    
    return Mono.fromCallable(() -> {
        // 1. 获取索引
        Index index = meilisearchClient.index(properties.getIndexPrefix() + INDEX_NAME);
        
        // 2. 构建过滤条件
        List<String> filters = new ArrayList<>();
        
        // 2.1 租户过滤
        if (tenantId != null) {
            filters.add("tenantId = '" + tenantId + "'");
        }
        
        // 2.2 资源类型过滤
        if (resourceTypes != null && !resourceTypes.isEmpty()) {
            String typeFilter = resourceTypes.stream()
                .map(type -> "resourceType = '" + type + "'")
                .collect(Collectors.joining(" OR "));
            filters.add("(" + typeFilter + ")");
        }
        
        // 2.3 状态过滤（仅启用的资源）
        filters.add("status = 1");
        
        String filterStr = filters.isEmpty() ? null : String.join(" AND ", filters);
        
        // 3. 构建搜索请求
        SearchRequest.Builder builder = SearchRequest.builder()
            .q(keyword)
            .offset((pageNum - 1) * pageSize)
            .limit(Math.min(pageSize, 100));
        
        if (filterStr != null) {
            builder.filter(new String[]{filterStr});
        }
        
        SearchRequest request = builder.build();
        
        // 4. 执行搜索
        SearchResult result = index.search(request);
        
        // 5. 转换结果
        List<SearchResultEntity> entities = new ArrayList<>();
        for (Object hit : result.getHits()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> doc = (Map<String, Object>) hit;
            entities.add(mapToEntity(doc));
        }
        
        // 6. 构建分页响应
        return PageResponse.of(entities, (long) result.getEstimatedTotalHits(), pageNum, pageSize);
        
    }).subscribeOn(Schedulers.boundedElastic())
      .doOnError(e -> log.error("Search failed: keyword={}", keyword, e))
      .onErrorReturn(PageResponse.of(Collections.emptyList(), 0L, pageNum, pageSize));
}

private SearchResultEntity mapToEntity(Map<String, Object> doc) {
    return SearchResultEntity.builder()
        .resourceType((String) doc.get("resourceType"))
        .resourceId((String) doc.get("resourceId"))
        .title((String) doc.get("title"))
        .subtitle((String) doc.get("subtitle"))
        .description((String) doc.get("description"))
        .highlightSnippet("") // TODO: 从highlight中提取
        .score(1.0) // TODO: 从_rankingScore中提取
        .status((Integer) doc.get("status"))
        .createdAt(toLocalDateTime((Long) doc.get("createdAt")))
        .updatedAt(toLocalDateTime((Long) doc.get("updatedAt")))
        .build();
}

private LocalDateTime toLocalDateTime(Long timestamp) {
    return timestamp == null ? null :
        LocalDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(timestamp),
            java.time.ZoneId.systemDefault()
        );
}
```

### 步骤五：完善其他索引方法

#### indexResource()

```java
@Override
public Mono<Boolean> indexResource(String resourceType, String resourceId) {
    return Mono.fromCallable(() -> {
        // 1. 读取资源
        Map<String, Object> document = fetchResourceDocument(resourceType, resourceId);
        if (document == null) {
            log.warn("Resource not found: type={}, id={}", resourceType, resourceId);
            return false;
        }
        
        // 2. 转换为JSON
        String json = new ObjectMapper().writeValueAsString(Collections.singletonList(document));
        
        // 3. 添加到索引
        Index index = meilisearchClient.index(properties.getIndexPrefix() + INDEX_NAME);
        index.addDocuments(json);
        
        log.debug("Indexed resource: type={}, id={}", resourceType, resourceId);
        return true;
    }).subscribeOn(Schedulers.boundedElastic())
      .doOnError(e -> log.error("Index failed: type={}, id={}", resourceType, resourceId, e))
      .onErrorReturn(false);
}
```

#### indexResourceBatch()

```java
@Override
public Mono<Integer> indexResourceBatch(String resourceType, List<String> resourceIds) {
    if (resourceIds == null || resourceIds.isEmpty()) {
        return Mono.just(0);
    }
    
    return Mono.fromCallable(() -> {
        // 1. 批量读取
        List<Map<String, Object>> documents = resourceIds.stream()
            .map(id -> fetchResourceDocument(resourceType, id))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        
        if (documents.isEmpty()) {
            return 0;
        }
        
        // 2. 转换为JSON
        String json = new ObjectMapper().writeValueAsString(documents);
        
        // 3. 批量添加
        Index index = meilisearchClient.index(properties.getIndexPrefix() + INDEX_NAME);
        index.addDocuments(json);
        
        log.info("Batch indexed: type={}, count={}", resourceType, documents.size());
        return documents.size();
    }).subscribeOn(Schedulers.boundedElastic())
      .doOnError(e -> log.error("Batch index failed: type={}", resourceType, e))
      .onErrorReturn(0);
}
```

#### removeIndex()

```java
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
```

#### rebuildIndex() 和 syncIncremental()

保持当前实现，它们会调用上面完善的 fetchAllResourceIds()、fetchIncrementalResourceIds() 和 indexResourceBatch()。

---

## ✅ 验证步骤

### 1. 启动Meilisearch

```bash
podman-compose -f podman-compose.yml up -d meilisearch
```

### 2. 编译应用

```bash
mvn clean compile -DskipTests
```

### 3. 启动应用

```bash
mvn spring-boot:run -pl apex-interfaces -Dspring-boot.run.profiles=dev
```

### 4. 测试索引重建

```bash
curl -X POST "http://localhost:8080/admin-api/v1/search/rebuild?type=user" \
  -H "Authorization: Bearer <token>"
```

### 5. 测试搜索

```bash
curl "http://localhost:8080/admin-api/v1/search?keyword=admin&types=user&page=1&size=20" \
  -H "Authorization: Bearer <token>"
```

---

## 📝 注意事项

### 1. ObjectMapper依赖

需要注入Jackson ObjectMapper：

```java
private final ObjectMapper objectMapper;
```

或创建局部实例：
```java
ObjectMapper mapper = new ObjectMapper();
```

### 2. 异常处理

Meilisearch SDK可能抛出`MeilisearchException`，需要捕获：

```java
try {
    index.addDocuments(json);
} catch (MeilisearchException e) {
    log.error("Meilisearch error", e);
    throw e;
}
```

### 3. 索引初始化

首次使用前需要创建索引：

```java
try {
    meilisearchClient.createIndex(properties.getIndexPrefix() + INDEX_NAME);
} catch (MeilisearchApiException e) {
    if (e.getCode().equals("index_already_exists")) {
        // 索引已存在，忽略
    } else {
        throw e;
    }
}
```

### 4. 高亮支持

如需高亮功能，在SearchRequest中添加：

```java
.attributesToHighlight(new String[]{"title", "subtitle", "description"})
.highlightPreTag("<em>")
.highlightPostTag("</em>")
```

然后从结果中提取：
```java
@SuppressWarnings("unchecked")
Map<String, Object> formatted = (Map<String, Object>) doc.get("_formatted");
String highlightedTitle = (String) formatted.get("title");
```

---

## 🎯 估计工作量

- 步骤一：2小时（实现6种资源类型的fetchResourceDocument）
- 步骤二：0.5小时（实现fetchAllResourceIds）
- 步骤三：0.5小时（实现fetchIncrementalResourceIds）
- 步骤四：1小时（实现search方法）
- 步骤五：1小时（实现其他索引方法）
- 验证测试：1小时

**总计**：约6小时

---

**完善指南完成。建议按照步骤逐个实施，每完成一步验证一次，确保功能正常。**
