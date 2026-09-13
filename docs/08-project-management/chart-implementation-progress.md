# 文档和图表管理功能实施进度报告

## 📊 总体进度

- ✅ **第一步**：数据库设计（Flyway 迁移脚本）- 100%
- ✅ **第二步**：PO 实体类（Document 和 Chart）- 100%
- ✅ **第三步**：Request 和 Response DTO - 100%
- ⏳ **第四步**：Repository 层（接口和实现）- 0%
- ⏳ **第五步**：Service 层和业务逻辑 - 0%
- ⏳ **第六步**：Handler 和 Router 层 - 0%
- ⏳ **第七步**：编写单元测试 - 0%
- ⏳ **第八步**：编写集成测试 - 0%
- ⏳ **第九步**：创建 API 文档 - 0%
- ⏳ **第十步**：创建测试报告 - 0%

**总体完成度：30%**

---

## ✅ 已完成的工作

### 第一步：数据库设计（Flyway 迁移脚本）

#### MySQL 迁移脚本
**文件**：`apex-interfaces/src/main/resources/db/migration/mysql/V27__create_document_and_chart_tables.sql`
**大小**：3.7KB

**内容**：
- `doc_document` 表：文档管理表
- `doc_chart` 表：图表管理表
- 包含完整的索引设计
- 支持多租户隔离
- 支持逻辑删除
- 支持全文搜索（FULLTEXT 索引）

#### PostgreSQL 迁移脚本
**文件**：`apex-interfaces/src/main/resources/db/migration/postgresql/V27__create_document_and_chart_tables.sql`
**大小**：5.7KB

**内容**：
- 与 MySQL 版本功能对齐
- 使用 JSONB 类型存储 JSON 数据
- 使用 GIN 索引支持全文搜索
- 包含触发器自动更新 update_time

#### 表结构设计

**doc_document（文档表）**
- 基础字段：id, title, doc_type, category, content
- 文件关联：file_url, file_size
- 作者信息：author_id, author_name
- 状态管理：status（0=草稿 1=正常 2=归档）
- 扩展功能：tags（JSON）, view_count, download_count
- 租户隔离：tenant_id
- 审计字段：create_time, update_time, deleted

**doc_chart（图表表）**
- 基础字段：id, chart_name, chart_type, category, description
- 数据配置：data_source, config（JSON）
- 创建人信息：creator_id, creator_name
- 状态管理：status（0=草稿 1=正常）
- 统计字段：view_count
- 租户隔离：tenant_id
- 审计字段：create_time, update_time, deleted

---

### 第二步：PO 实体类

#### DocumentEntity.java
**文件**：`apex-domain/src/main/java/com/apex/scaffold/domain/model/entity/DocumentEntity.java`
**大小**：4.7KB

**核心特性**：
- 继承自 BaseEntity（DDD 架构）
- 使用 Lombok 注解（@Getter、@ToString、@EqualsAndHashCode）
- 包含完整的领域行为方法：
  - `create()` - 静态工厂方法
  - `updateInfo()` - 更新文档信息
  - `attachFile()` - 附加文件
  - `publish()` - 发布文档
  - `archive()` - 归档文档
  - `incrementViewCount()` - 增加查看次数
  - `incrementDownloadCount()` - 增加下载次数
- 内部枚举：
  - `DocumentStatus` - 文档状态
  - `DocType` - 文档类型
  - `Category` - 文档分类

#### ChartEntity.java
**文件**：`apex-domain/src/main/java/com/apex/scaffold/domain/model/entity/ChartEntity.java`
**大小**：9.5KB

**核心特性**：
- 完整的 Javadoc 注释（中文）
- 包含领域行为方法：
  - `create()` - 静态工厂方法
  - `updateInfo()` - 更新图表信息
  - `publish()` - 发布图表
  - `revertToDraft()` - 撤回为草稿
  - `incrementViewCount()` - 增加查看次数
- 内部枚举：
  - `ChartStatus` - 图表状态
  - `ChartType` - 图表类型（line/bar/pie/scatter/radar）
  - `Category` - 图表分类

---

### 第三步：Request 和 Response DTO

**目录**：`apex-application/src/main/java/com/apex/scaffold/application/dto/data/`

#### 文档相关 DTO

1. **DocumentRequest.java** (1.4KB)
   - 文档创建/更新请求体
   - 包含校验注解（@NotBlank、@Size）
   - 支持标题、类型、分类、内容、文件、标签、状态等字段

2. **DocumentQueryRequest.java** (660B)
   - 文档查询请求（继承 PageRequest）
   - 支持关键词、类型、分类、状态筛选

3. **DocumentResponse.java** (1.4KB)
   - 文档响应 DTO
   - 包含完整的文档信息和统计数据

#### 图表相关 DTO

1. **ChartRequest.java** (1.3KB)
   - 图表创建/更新请求体
   - 包含校验注解
   - 支持图表配置（Map<String, Object>）

2. **ChartQueryRequest.java** (659B)
   - 图表查询请求（继承 PageRequest）
   - 支持关键词、类型、分类、状态筛选

3. **ChartResponse.java** (1.3KB)
   - 图表响应 DTO
   - 包含完整的图表信息

4. **ChartDataResponse.java** (1.1KB)
   - 图表数据响应 DTO
   - 包含 X 轴数据和数据系列
   - 内部类 ChartSeries 定义数据系列结构

**所有 DTO 共同特性**：
- 使用 Lombok 注解（@Data、@Builder、@NoArgsConstructor、@AllArgsConstructor）
- 使用 Swagger OpenAPI 注解（@Schema）
- 符合项目 DTO 规范

---

## ⏳ 待完成的工作

### 第四步：Repository 层

需要创建的文件：

#### 1. Repository 接口（Domain 层）
- `apex-domain/src/main/java/com/apex/scaffold/domain/repository/DocumentRepository.java`
- `apex-domain/src/main/java/com/apex/scaffold/domain/repository/ChartRepository.java`

**接口定义**：
```java
public interface DocumentRepository {
    Mono<DocumentEntity> save(DocumentEntity entity);
    Mono<DocumentEntity> findById(Long id);
    Mono<Page<DocumentEntity>> findByCondition(DocumentQueryRequest request);
    Mono<Void> deleteById(Long id);
    Mono<Long> count();
}

public interface ChartRepository {
    Mono<ChartEntity> save(ChartEntity entity);
    Mono<ChartEntity> findById(Long id);
    Mono<Page<ChartEntity>> findByCondition(ChartQueryRequest request);
    Mono<Void> deleteById(Long id);
    Mono<Long> count();
}
```

#### 2. Repository 实现（Infrastructure 层）
- `apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/persistence/DocumentRepositoryImpl.java`
- `apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/persistence/ChartRepositoryImpl.java`

**实现要点**：
- 使用 R2DBC DatabaseClient 执行响应式查询
- 实现分页查询
- 实现多维度筛选（关键词、类型、分类、状态）
- 实现租户隔离（自动注入 tenant_id）
- 实现逻辑删除（deleted = 0）

---

### 第五步：Service 层

需要创建的文件：

#### 1. Application Service（应用服务）
- `apex-application/src/main/java/com/apex/scaffold/application/service/DocumentService.java`
- `apex-application/src/main/java/com/apex/scaffold/application/service/ChartService.java`

**DocumentService 方法**：
```java
public interface DocumentService {
    Mono<DocumentResponse> create(DocumentRequest request);
    Mono<DocumentResponse> update(Long id, DocumentRequest request);
    Mono<Void> delete(Long id);
    Mono<DocumentResponse> getById(Long id);
    Mono<PageResponse<DocumentResponse>> query(DocumentQueryRequest request);
    Mono<Void> updateStatus(Long id, Integer status);
    Mono<String> downloadUrl(Long id);
}
```

**ChartService 方法**：
```java
public interface ChartService {
    Mono<ChartResponse> create(ChartRequest request);
    Mono<ChartResponse> update(Long id, ChartRequest request);
    Mono<Void> delete(Long id);
    Mono<ChartResponse> getById(Long id);
    Mono<PageResponse<ChartResponse>> query(ChartQueryRequest request);
    Mono<Void> updateStatus(Long id, Integer status);
    Mono<ChartDataResponse> getChartData(Long id);
}
```

#### 2. 实现类
- `DocumentServiceImpl.java`
- `ChartServiceImpl.java`

**实现要点**：
- 实体与 DTO 之间的转换
- 调用 Repository 层方法
- 业务逻辑处理（状态校验、权限校验等）
- 图表数据生成逻辑（根据 chartType 返回不同格式）

---

### 第六步：Handler 和 Router 层

**重要提示**：本项目使用 WebFlux 函数式路由，**不使用 Controller**。

需要创建的文件：

#### 1. Handler（处理器）
- `apex-interfaces/src/main/java/com/apex/scaffold/interfaces/handler/DocumentHandler.java`
- `apex-interfaces/src/main/java/com/apex/scaffold/interfaces/handler/ChartHandler.java`

**Handler 方法示例**：
```java
@Component
public class DocumentHandler {
    private final DocumentService documentService;

    public Mono<ServerResponse> create(ServerRequest request) {
        return request.bodyToMono(DocumentRequest.class)
            .flatMap(documentService::create)
            .flatMap(result -> ServerResponse.ok()
                .bodyValue(Result.success(result)));
    }

    public Mono<ServerResponse> query(ServerRequest request) {
        // 从查询参数构建 DocumentQueryRequest
        // 调用 service.query()
        // 返回分页响应
    }

    // ... 其他方法
}
```

#### 2. Router（路由配置）
- `apex-interfaces/src/main/java/com/apex/scaffold/interfaces/router/DocumentRouter.java`
- `apex-interfaces/src/main/java/com/apex/scaffold/interfaces/router/ChartRouter.java`

**Router 配置示例**：
```java
@Configuration
public class DocumentRouter {
    
    @Bean
    public RouterFunction<ServerResponse> documentRoutes(DocumentHandler handler) {
        return RouterFunctions.route()
            .path("/admin-api/v1/documents", builder -> builder
                .GET("", handler::query)
                .POST("", handler::create)
                .GET("/{id}", handler::getById)
                .PUT("/{id}", handler::update)
                .DELETE("/{id}", handler::delete)
                .PUT("/{id}/status", handler::updateStatus)
                .GET("/{id}/download", handler::download)
            )
            .build();
    }
}
```

**API 路径**：
- 文档管理：`/admin-api/v1/documents`
- 图表管理：`/admin-api/v1/charts`
- 图表数据：`/admin-api/v1/charts/{id}/data`

---

### 第七步：单元测试

需要创建的测试文件：

#### 1. Entity 层测试
- `DocumentEntityTest.java`
- `ChartEntityTest.java`

**测试内容**：
- 实体创建
- 领域行为方法
- 状态转换
- 枚举值

#### 2. Service 层测试
- `DocumentServiceTest.java`
- `ChartServiceTest.java`

**测试内容**：
- CRUD 操作
- 分页查询
- 状态更新
- 异常处理
- 使用 Mockito Mock Repository

#### 3. DTO 校验测试
- `DocumentRequestTest.java`
- `ChartRequestTest.java`

**测试内容**：
- 必填字段校验
- 长度限制校验
- 格式校验

**测试覆盖率目标**：> 80%

---

### 第八步：集成测试

需要创建的测试文件：

#### 1. Handler 集成测试
- `DocumentHandlerIntegrationTest.java`
- `ChartHandlerIntegrationTest.java`

**测试内容**：
- 使用 WebTestClient 测试所有 API 端点
- 测试完整的请求响应流程
- 测试错误处理
- 测试权限控制

**测试场景**：
- 创建文档/图表
- 查询列表（分页、筛选）
- 查询详情
- 更新文档/图表
- 删除文档/图表
- 更新状态
- 下载文档
- 获取图表数据

#### 2. Repository 集成测试
- `DocumentRepositoryIntegrationTest.java`
- `ChartRepositoryIntegrationTest.java`

**测试内容**：
- 使用测试数据库
- 测试 CRUD 操作
- 测试复杂查询
- 测试事务

---

### 第九步：API 文档

需要创建的文档：

#### 1. API 接口文档
**文件**：`docs/api/document-chart-api.md`

**内容**：
- 接口列表
- 请求参数说明
- 响应示例
- 错误码说明
- 使用示例（curl 命令）

**接口列表**：

**文档管理**：
- `GET /admin-api/v1/documents` - 分页查询文档列表
- `POST /admin-api/v1/documents` - 创建文档
- `GET /admin-api/v1/documents/{id}` - 查询文档详情
- `PUT /admin-api/v1/documents/{id}` - 更新文档
- `DELETE /admin-api/v1/documents/{id}` - 删除文档
- `PUT /admin-api/v1/documents/{id}/status` - 更新文档状态
- `GET /admin-api/v1/documents/{id}/download` - 下载文档

**图表管理**：
- `GET /admin-api/v1/charts` - 分页查询图表列表
- `POST /admin-api/v1/charts` - 创建图表
- `GET /admin-api/v1/charts/{id}` - 查询图表详情
- `GET /admin-api/v1/charts/{id}/data` - 查询图表数据
- `PUT /admin-api/v1/charts/{id}` - 更新图表
- `DELETE /admin-api/v1/charts/{id}` - 删除图表
- `PUT /admin-api/v1/charts/{id}/status` - 更新图表状态

#### 2. Swagger/OpenAPI 文档
- 确保所有 Handler 方法都有 @Operation 注解
- 确保所有 DTO 字段都有 @Schema 注解
- 访问地址：`http://localhost:8080/swagger-ui.html`

---

### 第十步：测试报告

需要创建的文档：

#### 1. 测试报告
**文件**：`docs/reports/document-chart-test-report.md`

**内容**：
- 测试环境说明
- 测试覆盖率统计
- 单元测试结果
- 集成测试结果
- 性能测试结果（可选）
- 已知问题列表
- 测试结论

#### 2. 测试覆盖率报告
- 使用 JaCoCo 生成覆盖率报告
- 目标：总体覆盖率 > 80%
- 生成 HTML 报告

---

## 📝 实施建议

### 开发顺序
1. ✅ 数据库设计（第一步）
2. ✅ 实体类（第二步）
3. ✅ DTO 类（第三步）
4. ⏳ Repository 层（第四步）**← 下一步**
5. ⏳ Service 层（第五步）
6. ⏳ Handler/Router 层（第六步）
7. ⏳ 单元测试（第七步）
8. ⏳ 集成测试（第八步）
9. ⏳ API 文档（第九步）
10. ⏳ 测试报告（第十步）

### 开发规范
1. **代码注释**：所有类、方法、字段都需要中文注释
2. **命名规范**：遵循项目现有命名规范
3. **异常处理**：统一使用 Result<T> 包装响应
4. **日志记录**：关键操作记录日志
5. **事务管理**：使用 @Transactional 注解
6. **权限控制**：使用 @PreAuthorize 注解

### 测试规范
1. **单元测试**：每个 Service 方法都需要单元测试
2. **集成测试**：每个 API 端点都需要集成测试
3. **测试数据**：使用独立的测试数据库
4. **测试隔离**：每个测试用例独立，不依赖执行顺序

### 文档规范
1. **API 文档**：使用 Markdown 格式
2. **代码注释**：使用 Javadoc 格式
3. **Swagger 注解**：所有 DTO 和 Handler 都需要注解
4. **版本管理**：文档需要标注版本号

---

## 🔗 相关文件

### 已创建的文件

**数据库迁移脚本**：
- `apex-interfaces/src/main/resources/db/migration/mysql/V27__create_document_and_chart_tables.sql`
- `apex-interfaces/src/main/resources/db/migration/postgresql/V27__create_document_and_chart_tables.sql`

**实体类**：
- `apex-domain/src/main/java/com/apex/scaffold/domain/model/entity/DocumentEntity.java`
- `apex-domain/src/main/java/com/apex/scaffold/domain/model/entity/ChartEntity.java`

**DTO 类**：
- `apex-application/src/main/java/com/apex/scaffold/application/dto/data/DocumentRequest.java`
- `apex-application/src/main/java/com/apex/scaffold/application/dto/data/DocumentQueryRequest.java`
- `apex-application/src/main/java/com/apex/scaffold/application/dto/data/DocumentResponse.java`
- `apex-application/src/main/java/com/apex/scaffold/application/dto/data/ChartRequest.java`
- `apex-application/src/main/java/com/apex/scaffold/application/dto/data/ChartQueryRequest.java`
- `apex-application/src/main/java/com/apex/scaffold/application/dto/data/ChartResponse.java`
- `apex-application/src/main/java/com/apex/scaffold/application/dto/data/ChartDataResponse.java`

### 待创建的文件

**Repository 层**：
- `apex-domain/src/main/java/com/apex/scaffold/domain/repository/DocumentRepository.java`
- `apex-domain/src/main/java/com/apex/scaffold/domain/repository/ChartRepository.java`
- `apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/persistence/DocumentRepositoryImpl.java`
- `apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/persistence/ChartRepositoryImpl.java`

**Service 层**：
- `apex-application/src/main/java/com/apex/scaffold/application/service/DocumentService.java`
- `apex-application/src/main/java/com/apex/scaffold/application/service/ChartService.java`
- `apex-application/src/main/java/com/apex/scaffold/application/service/impl/DocumentServiceImpl.java`
- `apex-application/src/main/java/com/apex/scaffold/application/service/impl/ChartServiceImpl.java`

**Handler/Router 层**：
- `apex-interfaces/src/main/java/com/apex/scaffold/interfaces/handler/DocumentHandler.java`
- `apex-interfaces/src/main/java/com/apex/scaffold/interfaces/handler/ChartHandler.java`
- `apex-interfaces/src/main/java/com/apex/scaffold/interfaces/router/DocumentRouter.java`
- `apex-interfaces/src/main/java/com/apex/scaffold/interfaces/router/ChartRouter.java`

**测试文件**：
- 单元测试：6-8 个测试类
- 集成测试：4-6 个测试类

**文档文件**：
- `docs/api/document-chart-api.md`
- `docs/reports/document-chart-test-report.md`

---

## 📅 预计完成时间

- **第四步**（Repository 层）：2-3 小时
- **第五步**（Service 层）：3-4 小时
- **第六步**（Handler/Router 层）：2-3 小时
- **第七步**（单元测试）：3-4 小时
- **第八步**（集成测试）：2-3 小时
- **第九步**（API 文档）：1-2 小时
- **第十步**（测试报告）：1 小时

**预计总时间**：14-22 小时

---

## 📊 当前状态

- **完成进度**：30%
- **剩余工作**：70%
- **下一步行动**：创建 Repository 层

---

**文档最后更新时间**：2026-09-12
**维护者**：开发团队
