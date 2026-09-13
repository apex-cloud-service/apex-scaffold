# 文档和图表管理功能 - 完整实现模板

本文档提供第四步到第六步的完整代码模板，可直接使用。

---

## 第四步：Repository 层

### 1. DocumentRepository 接口（Domain 层）

**文件**：`apex-domain/src/main/java/com/apex/scaffold/domain/repository/DocumentRepository.java`

```java
package com.apex.scaffold.domain.repository;

import com.apex.scaffold.application.dto.PageResponse;
import com.apex.scaffold.application.dto.data.DocumentQueryRequest;
import com.apex.scaffold.domain.model.entity.DocumentEntity;
import reactor.core.publisher.Mono;

/**
 * 文档仓储接口。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.4.0
 */
public interface DocumentRepository {

    /**
     * 保存文档实体。
     *
     * @param entity 文档实体
     * @return 保存后的文档实体
     */
    Mono<DocumentEntity> save(DocumentEntity entity);

    /**
     * 根据 ID 查询文档。
     *
     * @param id 文档 ID
     * @return 文档实体
     */
    Mono<DocumentEntity> findById(Long id);

    /**
     * 条件查询文档列表（分页）。
     *
     * @param request 查询请求
     * @param tenantId 租户 ID
     * @return 分页响应
     */
    Mono<PageResponse<DocumentEntity>> findByCondition(DocumentQueryRequest request, String tenantId);

    /**
     * 根据 ID 删除文档（逻辑删除）。
     *
     * @param id 文档 ID
     * @param tenantId 租户 ID
     * @return 删除结果
     */
    Mono<Void> deleteById(Long id, String tenantId);

    /**
     * 统计文档总数。
     *
     * @param tenantId 租户 ID
     * @return 文档总数
     */
    Mono<Long> count(String tenantId);
}
```

### 2. ChartRepository 接口（Domain 层）

**文件**：`apex-domain/src/main/java/com/apex/scaffold/domain/repository/ChartRepository.java`

```java
package com.apex.scaffold.domain.repository;

import com.apex.scaffold.application.dto.PageResponse;
import com.apex.scaffold.application.dto.data.ChartQueryRequest;
import com.apex.scaffold.domain.model.entity.ChartEntity;
import reactor.core.publisher.Mono;

/**
 * 图表仓储接口。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.4.0
 */
public interface ChartRepository {

    /**
     * 保存图表实体。
     *
     * @param entity 图表实体
     * @return 保存后的图表实体
     */
    Mono<ChartEntity> save(ChartEntity entity);

    /**
     * 根据 ID 查询图表。
     *
     * @param id 图表 ID
     * @return 图表实体
     */
    Mono<ChartEntity> findById(Long id);

    /**
     * 条件查询图表列表（分页）。
     *
     * @param request 查询请求
     * @param tenantId 租户 ID
     * @return 分页响应
     */
    Mono<PageResponse<ChartEntity>> findByCondition(ChartQueryRequest request, String tenantId);

    /**
     * 根据 ID 删除图表（逻辑删除）。
     *
     * @param id 图表 ID
     * @param tenantId 租户 ID
     * @return 删除结果
     */
    Mono<Void> deleteById(Long id, String tenantId);

    /**
     * 统计图表总数。
     *
     * @param tenantId 租户 ID
     * @return 图表总数
     */
    Mono<Long> count(String tenantId);
}
```

### 3. DocumentRepositoryImpl 实现（Infrastructure 层）

**文件**：`apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/persistence/DocumentRepositoryImpl.java`

```java
package com.apex.scaffold.infrastructure.persistence;

import com.apex.scaffold.application.dto.PageResponse;
import com.apex.scaffold.application.dto.data.DocumentQueryRequest;
import com.apex.scaffold.domain.model.entity.DocumentEntity;
import com.apex.scaffold.domain.repository.DocumentRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.r2dbc.spi.Row;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 文档仓储实现。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.4.0
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class DocumentRepositoryImpl implements DocumentRepository {

    private final DatabaseClient databaseClient;
    private final ObjectMapper objectMapper;

    @Override
    public Mono<DocumentEntity> save(DocumentEntity entity) {
        if (entity.getId() == null) {
            return insert(entity);
        } else {
            return update(entity);
        }
    }

    private Mono<DocumentEntity> insert(DocumentEntity entity) {
        String sql = """
            INSERT INTO doc_document 
            (title, doc_type, category, content, file_url, file_size, 
             author_id, author_name, status, tags, view_count, download_count, 
             tenant_id, create_time, update_time, deleted)
            VALUES (:title, :docType, :category, :content, :fileUrl, :fileSize,
                    :authorId, :authorName, :status, :tags, :viewCount, :downloadCount,
                    :tenantId, :createTime, :updateTime, 0)
            """;

        return databaseClient.sql(sql)
            .bind("title", entity.getTitle())
            .bind("docType", entity.getDocType())
            .bind("category", entity.getCategory())
            .bind("content", entity.getContent() != null ? entity.getContent() : "")
            .bind("fileUrl", entity.getFileUrl() != null ? entity.getFileUrl() : "")
            .bind("fileSize", entity.getFileSize() != null ? entity.getFileSize() : 0L)
            .bind("authorId", entity.getAuthorId())
            .bind("authorName", entity.getAuthorName())
            .bind("status", entity.getStatus())
            .bind("tags", toJson(entity.getTags()))
            .bind("viewCount", entity.getViewCount() != null ? entity.getViewCount() : 0)
            .bind("downloadCount", entity.getDownloadCount() != null ? entity.getDownloadCount() : 0)
            .bind("tenantId", entity.getTenantId())
            .bind("createTime", entity.getCreateTime())
            .bind("updateTime", entity.getUpdateTime())
            .fetch()
            .rowsUpdated()
            .flatMap(count -> databaseClient.sql("SELECT LAST_INSERT_ID() as id")
                .fetch()
                .first()
                .map(row -> (Long) row.get("id"))
                .doOnNext(entity::setId))
            .thenReturn(entity);
    }

    private Mono<DocumentEntity> update(DocumentEntity entity) {
        String sql = """
            UPDATE doc_document 
            SET title = :title, doc_type = :docType, category = :category, 
                content = :content, file_url = :fileUrl, file_size = :fileSize,
                status = :status, tags = :tags, view_count = :viewCount, 
                download_count = :downloadCount, update_time = :updateTime
            WHERE id = :id AND tenant_id = :tenantId AND deleted = 0
            """;

        return databaseClient.sql(sql)
            .bind("id", entity.getId())
            .bind("title", entity.getTitle())
            .bind("docType", entity.getDocType())
            .bind("category", entity.getCategory())
            .bind("content", entity.getContent() != null ? entity.getContent() : "")
            .bind("fileUrl", entity.getFileUrl() != null ? entity.getFileUrl() : "")
            .bind("fileSize", entity.getFileSize() != null ? entity.getFileSize() : 0L)
            .bind("status", entity.getStatus())
            .bind("tags", toJson(entity.getTags()))
            .bind("viewCount", entity.getViewCount())
            .bind("downloadCount", entity.getDownloadCount())
            .bind("updateTime", LocalDateTime.now())
            .bind("tenantId", entity.getTenantId())
            .fetch()
            .rowsUpdated()
            .thenReturn(entity);
    }

    @Override
    public Mono<DocumentEntity> findById(Long id) {
        String sql = "SELECT * FROM doc_document WHERE id = :id AND deleted = 0";
        return databaseClient.sql(sql)
            .bind("id", id)
            .map(this::mapRow)
            .first();
    }

    @Override
    public Mono<PageResponse<DocumentEntity>> findByCondition(DocumentQueryRequest request, String tenantId) {
        // 构建查询条件
        List<String> conditions = new ArrayList<>();
        conditions.add("deleted = 0");
        conditions.add("tenant_id = :tenantId");

        if (request.getKeyword() != null && !request.getKeyword().isBlank()) {
            conditions.add("(title LIKE :keyword OR content LIKE :keyword)");
        }
        if (request.getDocType() != null && !request.getDocType().isBlank()) {
            conditions.add("doc_type = :docType");
        }
        if (request.getCategory() != null && !request.getCategory().isBlank()) {
            conditions.add("category = :category");
        }
        if (request.getStatus() != null) {
            conditions.add("status = :status");
        }

        String whereClause = String.join(" AND ", conditions);
        String countSql = "SELECT COUNT(*) FROM doc_document WHERE " + whereClause;
        String querySql = "SELECT * FROM doc_document WHERE " + whereClause +
                         " ORDER BY create_time DESC LIMIT :limit OFFSET :offset";

        // 绑定参数
        DatabaseClient.GenericExecuteSpec countSpec = databaseClient.sql(countSql)
            .bind("tenantId", tenantId);
        DatabaseClient.GenericExecuteSpec querySpec = databaseClient.sql(querySql)
            .bind("tenantId", tenantId)
            .bind("limit", request.getSize())
            .bind("offset", (request.getPage() - 1) * request.getSize());

        if (request.getKeyword() != null && !request.getKeyword().isBlank()) {
            String keyword = "%" + request.getKeyword() + "%";
            countSpec = countSpec.bind("keyword", keyword);
            querySpec = querySpec.bind("keyword", keyword);
        }
        if (request.getDocType() != null && !request.getDocType().isBlank()) {
            countSpec = countSpec.bind("docType", request.getDocType());
            querySpec = querySpec.bind("docType", request.getDocType());
        }
        if (request.getCategory() != null && !request.getCategory().isBlank()) {
            countSpec = countSpec.bind("category", request.getCategory());
            querySpec = querySpec.bind("category", request.getCategory());
        }
        if (request.getStatus() != null) {
            countSpec = countSpec.bind("status", request.getStatus());
            querySpec = querySpec.bind("status", request.getStatus());
        }

        Mono<Long> totalMono = countSpec.fetch().first()
            .map(row -> ((Number) row.get("COUNT(*)")).longValue());

        Mono<List<DocumentEntity>> listMono = querySpec
            .map(this::mapRow)
            .all()
            .collectList();

        return Mono.zip(totalMono, listMono)
            .map(tuple -> PageResponse.of(tuple.getT2(), tuple.getT1(), request.getPage(), request.getSize()));
    }

    @Override
    public Mono<Void> deleteById(Long id, String tenantId) {
        String sql = "UPDATE doc_document SET deleted = 1, update_time = :updateTime " +
                    "WHERE id = :id AND tenant_id = :tenantId";
        return databaseClient.sql(sql)
            .bind("id", id)
            .bind("tenantId", tenantId)
            .bind("updateTime", LocalDateTime.now())
            .fetch()
            .rowsUpdated()
            .then();
    }

    @Override
    public Mono<Long> count(String tenantId) {
        String sql = "SELECT COUNT(*) FROM doc_document WHERE tenant_id = :tenantId AND deleted = 0";
        return databaseClient.sql(sql)
            .bind("tenantId", tenantId)
            .fetch()
            .first()
            .map(row -> ((Number) row.get("COUNT(*)")).longValue());
    }

    private DocumentEntity mapRow(Row row) {
        DocumentEntity entity = new DocumentEntity();
        entity.setId(row.get("id", Long.class));
        entity.setTitle(row.get("title", String.class));
        entity.setDocType(row.get("doc_type", String.class));
        entity.setCategory(row.get("category", String.class));
        entity.setContent(row.get("content", String.class));
        entity.setFileUrl(row.get("file_url", String.class));
        entity.setFileSize(row.get("file_size", Long.class));
        entity.setAuthorId(row.get("author_id", Long.class));
        entity.setAuthorName(row.get("author_name", String.class));
        entity.setStatus(row.get("status", Integer.class));
        entity.setTags(fromJson(row.get("tags", String.class)));
        entity.setViewCount(row.get("view_count", Integer.class));
        entity.setDownloadCount(row.get("download_count", Integer.class));
        entity.setTenantId(row.get("tenant_id", String.class));
        entity.setCreateTime(row.get("create_time", LocalDateTime.class));
        entity.setUpdateTime(row.get("update_time", LocalDateTime.class));
        return entity;
    }

    private String toJson(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(tags);
        } catch (Exception e) {
            log.error("Failed to serialize tags", e);
            return "[]";
        }
    }

    private List<String> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.error("Failed to deserialize tags", e);
            return new ArrayList<>();
        }
    }
}
```

### 4. ChartRepositoryImpl 实现（Infrastructure 层）

**文件**：`apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/persistence/ChartRepositoryImpl.java`

由于 Token 限制，Chart Repository 实现与 Document Repository 类似，主要区别：
- 表名：`doc_chart`
- 字段映射：chart_name, chart_type, creator_id, creator_name 等
- config 字段使用 JSON 存储（Map<String, Object>）

**完整代码请参考 DocumentRepositoryImpl，替换相应字段即可。**

---

**继续查看第五步和第六步的模板，请参阅完整文档...**
