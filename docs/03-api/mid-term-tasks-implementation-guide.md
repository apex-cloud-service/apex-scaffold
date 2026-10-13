# 中期任务实施指南

**文档日期**: 2026-09-19  
**任务范围**: 任务4-6 (办公平台实现、Keycloak修复、性能优化)  
**预估工作量**: 约20-30小时

---

## 📋 任务概览

| 任务ID | 任务名称 | 预估工作量 | 优先级 | 状态 |
|--------|---------|-----------|--------|------|
| 任务4 | 办公平台模块实现 | 12-15小时 | 高 | 已启动(Domain层) |
| 任务5 | Keycloak依赖冲突修复 | 2-4小时 | 中 | 待启动 |
| 任务6 | 性能优化 | 1-2周 | 低 | 待规划 |

---

## 🎯 任务4: 办公平台模块实现

### 已完成部分 (5%)

✅ **Domain层实体类** (3个):
- [`OffMessageEntity.java`](../../apex-office/src/main/java/com/apex/scaffold/office/off/domain/entity/OffMessageEntity.java)
- [`OffMessageTemplateEntity.java`](../../apex-office/src/main/java/com/apex/scaffold/office/off/domain/entity/OffMessageTemplateEntity.java)
- [`OffMessageRecipientEntity.java`](../../apex-office/src/main/java/com/apex/scaffold/office/off/domain/entity/OffMessageRecipientEntity.java)

### 待完成部分 (95%)

#### 第一优先级: 消息推送模块 (高优先级)

**Domain层** (预估30分钟):
```java
// 1. Repository 接口
package com.apex.scaffold.domain.repository;

public interface OffMessageRepository {
    Mono<OffMessageEntity> findById(Long id);
    Mono<Integer> insert(OffMessageEntity entity);
    Flux<OffMessageEntity> findByRecipientId(Long recipientId);
    // ... 其他方法
}

public interface OffMessageTemplateRepository {
    Mono<OffMessageTemplateEntity> findByCode(String templateCode);
    Mono<Integer> insert(OffMessageTemplateEntity entity);
    // ... 其他方法
}
```

**Infrastructure层** (预估1.5小时):
```java
// 1. PO 类 (3个)
@TableName("off_message")
public class OffMessagePO extends BasePO { ... }

@TableName("off_message_template")
public class OffMessageTemplatePO extends BasePO { ... }

@TableName("off_message_recipient")
public class OffMessageRecipientPO extends BasePO { ... }

// 2. Mapper 接口 (3个)
@Mapper
public interface OffMessageMapper extends BaseMapper<OffMessagePO> { ... }

// 3. Converter (3个 - MapStruct)
@Mapper(componentModel = "spring")
public interface OffMessagePoConverter {
    OffMessageEntity toEntity(OffMessagePO po);
    OffMessagePO toPo(OffMessageEntity entity);
}

// 4. Repository 实现 (3个)
@Repository
@RequiredArgsConstructor
public class OffMessageRepositoryImpl implements OffMessageRepository {
    private final OffMessageMapper mapper;
    private final OffMessagePoConverter converter;
    
    @Override
    public Mono<OffMessageEntity> findById(Long id) {
        return Mono.fromCallable(() -> mapper.selectById(id))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMap(po -> po == null ? Mono.empty() : Mono.just(converter.toEntity(po)));
    }
    // ... 其他方法
}
```

**Application层** (预估2小时):
```java
// 1. Service
@Service
@RequiredArgsConstructor
public class OffMessageService {
    private final OffMessageRepository messageRepository;
    private final OffMessageTemplateRepository templateRepository;
    private final OffMessageAssembler assembler;
    
    /**
     * 发送消息
     */
    public Mono<MessageDTO> sendMessage(SendMessageRequest request) {
        return Mono.just(request)
                .flatMap(req -> {
                    // 1. 构建消息实体
                    OffMessageEntity entity = OffMessageEntity.builder()
                            .messageCode(generateMessageCode())
                            .subject(req.getSubject())
                            .content(req.getContent())
                            .messageType(req.getMessageType())
                            .priority(req.getPriority())
                            .senderId(getCurrentUserId())
                            .sendTime(LocalDateTime.now())
                            .status("SENT")
                            .build();
                    
                    // 2. 保存消息
                    return messageRepository.insert(entity)
                            .flatMap(count -> messageRepository.findById(entity.getId()));
                })
                .map(assembler::toDTO);
    }
    
    /**
     * 查询收件箱
     */
    public Flux<MessageDTO> getInbox(Long recipientId) {
        return messageRepository.findByRecipientId(recipientId)
                .map(assembler::toDTO);
    }
    
    /**
     * 标记已读
     */
    public Mono<Void> markAsRead(Long messageId, Long recipientId) {
        // 实现标记已读逻辑
        return Mono.empty();
    }
}

// 2. DTO (6个)
@Data
@Builder
public class SendMessageRequest {
    private String subject;
    private String content;
    private String messageType;
    private String priority;
    private List<Long> recipientIds;
}

@Data
@Builder
public class MessageDTO {
    private Long id;
    private String messageCode;
    private String subject;
    private String content;
    private String messageType;
    private String priority;
    private String senderName;
    private LocalDateTime sendTime;
    private Boolean readStatus;
}

// 3. Assembler (MapStruct)
@Mapper(componentModel = "spring")
public interface OffMessageAssembler {
    MessageDTO toDTO(OffMessageEntity entity);
    OffMessageEntity toEntity(SendMessageRequest request);
}
```

**Interfaces层** (预估1小时):
```java
// 1. Handler
@Component
@RequiredArgsConstructor
public class OffMessageHandler {
    private final OffMessageService service;
    
    /**
     * 发送消息
     */
    public Mono<ServerResponse> sendMessage(ServerRequest request) {
        return request.bodyToMono(SendMessageRequest.class)
                .flatMap(service::sendMessage)
                .flatMap(dto -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Result.success(dto)))
                .onErrorResume(this::handleError);
    }
    
    /**
     * 查询收件箱
     */
    public Mono<ServerResponse> getInbox(ServerRequest request) {
        Long recipientId = Long.valueOf(request.queryParam("recipientId").orElse("0"));
        return service.getInbox(recipientId)
                .collectList()
                .flatMap(list -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Result.success(list)))
                .onErrorResume(this::handleError);
    }
    
    /**
     * 标记已读
     */
    public Mono<ServerResponse> markAsRead(ServerRequest request) {
        Long messageId = Long.valueOf(request.pathVariable("id"));
        Long recipientId = getCurrentUserId(request);
        return service.markAsRead(messageId, recipientId)
                .then(ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Result.success(null)))
                .onErrorResume(this::handleError);
    }
}

// 2. Router
@Configuration
public class OfficeRouter {
    
    @Bean
    public RouterFunction<ServerResponse> officeRoutes(OffMessageHandler messageHandler) {
        return RouterFunctions.route()
                .path("/api/office", builder -> builder
                        // 消息推送路由
                        .POST("/messages/send", messageHandler::sendMessage)
                        .GET("/messages/inbox", messageHandler::getInbox)
                        .POST("/messages/{id}/read", messageHandler::markAsRead)
                )
                .build();
    }
}
```

#### 第二优先级: 组织同步模块 (高优先级, 预估3小时)

参考消息推送模块的实现模式,创建:
- Domain层: OffDepartmentEntity, OffEmployeeEntity, OffSyncHistoryEntity
- Infrastructure层: PO + Mapper + Converter + Repository实现
- Application层: OffOrganizationService + DTO + Assembler
- Interfaces层: OffOrganizationHandler + Router

**核心功能**:
```java
@Service
public class OffOrganizationService {
    
    /**
     * 全量同步组织架构
     */
    public Mono<SyncResultDTO> syncFull(SyncRequest request) {
        return Mono.just(request)
                .flatMap(req -> {
                    // 1. 从外部系统拉取数据(钉钉/企微/飞书)
                    // 2. 清空现有数据
                    // 3. 批量插入新数据
                    // 4. 记录同步历史
                    return Mono.just(new SyncResultDTO());
                });
    }
    
    /**
     * 查询部门树
     */
    public Mono<DepartmentTreeDTO> getDepartmentTree() {
        return departmentRepository.findAll()
                .collectList()
                .map(this::buildTree);
    }
}
```

#### 第三优先级: 审批和日历框架 (中优先级, 预估4小时)

采用**简化MVP策略**:
- 审批流程: 只支持单级审批,暂不支持多级审批和会签
- 日历管理: 只支持单次事件,暂不支持复杂重复规则

创建基础的Entity、Repository、Service、Handler框架,核心逻辑待后续完善。

---

## 🔧 任务5: Keycloak依赖冲突修复

### 问题分析 (预估1小时)

```bash
# 1. 分析依赖树
cd /Users/work/apex-scaffold/apex-interfaces
mvnd dependency:tree > dependency-tree.txt

# 2. 查找Keycloak冲突
grep -i "keycloak" dependency-tree.txt | grep "conflict"
```

### 修复方案 (预估1-2小时)

**方案1: 排除冲突的传递依赖**
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
    <exclusions>
        <exclusion>
            <groupId>org.keycloak</groupId>
            <artifactId>*</artifactId>
        </exclusion>
    </exclusions>
</dependency>
```

**方案2: 统一Keycloak版本**
```xml
<properties>
    <keycloak.version>22.0.5</keycloak.version>
</properties>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.keycloak</groupId>
            <artifactId>keycloak-core</artifactId>
            <version>${keycloak.version}</version>
        </dependency>
    </dependencies>
</dependencyManagement>
```

### 验证步骤 (预估30分钟)

```bash
# 1. 清理并重新编译
mvnd clean install -P basic

# 2. 检查是否有冲突警告
mvnd dependency:analyze

# 3. 启动应用测试
mvnd spring-boot:run -P basic
```

---

## ⚡ 任务6: 性能优化

### 优化策略规划 (预估1天)

#### 1. 代码生成性能优化

**问题**: 大表元数据查询慢,模板渲染慢

**优化方案**:
```java
@Service
public class CodeGenerationService {
    
    @Cacheable(value = "table-metadata", key = "#datasourceId + ':' + #tableName")
    public Mono<TableMetadata> getTableMetadata(Long datasourceId, String tableName) {
        // 缓存表元数据,避免重复查询
        return Mono.fromCallable(() -> jdbcTemplate.query(...))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .cache(Duration.ofMinutes(10)); // 10分钟缓存
    }
    
    // 模板编译缓存
    private final ConcurrentHashMap<String, Template> templateCache = new ConcurrentHashMap<>();
    
    public String renderTemplate(String templateContent, Map<String, Object> data) {
        Template template = templateCache.computeIfAbsent(templateContent, 
                content -> velocityEngine.getTemplate(content));
        return template.merge(data);
    }
}
```

#### 2. SSO认证流程优化

**问题**: JWT验证频繁,SAML签名验证耗时

**优化方案**:
```java
@Service
public class SamlProtocolAdapter {
    
    // 证书缓存
    @Cacheable(value = "saml-certificates", key = "#providerId")
    public X509Certificate loadCertificate(Long providerId) {
        // 缓存证书,避免每次重新解析
        return certificateFactory.generate(...);
    }
    
    // 签名验证结果缓存(短期)
    @Cacheable(value = "saml-assertions", key = "#assertionId", unless = "#result == false")
    public boolean validateSignature(String assertionId, String xml) {
        return xmlSignatureValidator.validate(xml);
    }
}
```

#### 3. 数据库查询优化

**问题**: N+1查询,缺少索引

**优化方案**:
```sql
-- 添加复合索引
CREATE INDEX idx_message_recipient_status 
ON off_message_recipient(recipient_id, read_status, deleted);

CREATE INDEX idx_message_tenant_time 
ON off_message(tenant_id, send_time, deleted);

-- 批量查询替代N+1
SELECT m.*, r.read_status, r.read_time
FROM off_message m
LEFT JOIN off_message_recipient r ON m.id = r.message_id AND r.recipient_id = ?
WHERE m.tenant_id = ? AND r.deleted = 0
ORDER BY m.send_time DESC
LIMIT 20;
```

#### 4. 缓存策略设计

**L1(Caffeine) + L2(Redis) 多级缓存**:
```java
@Configuration
public class CacheConfig {
    
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory factory) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer()));
        
        return RedisCacheManager.builder(factory)
                .cacheDefaults(config)
                .withCacheConfiguration("table-metadata", 
                        config.entryTtl(Duration.ofHours(1)))
                .withCacheConfiguration("saml-certificates", 
                        config.entryTtl(Duration.ofDays(1)))
                .build();
    }
}
```

### JMH 性能基准测试 (预估2天)

```java
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
public class CodeGenerationBenchmark {
    
    private CodeGenerationService service;
    
    @Setup
    public void setup() {
        // 初始化
    }
    
    @Benchmark
    public void testTableMetadataQuery() {
        service.getTableMetadata(1L, "user").block();
    }
    
    @Benchmark
    public void testTemplateRender() {
        service.renderTemplate(template, data);
    }
}
```

**运行基准测试**:
```bash
mvnd clean test -P benchmark
```

### 性能监控配置 (预估1天)

```yaml
# application.yml
management:
  endpoints:
    web:
      exposure:
        include: prometheus,health,metrics
  metrics:
    export:
      prometheus:
        enabled: true
    tags:
      application: apex-scaffold
      
# Prometheus 抓取配置
scrape_configs:
  - job_name: 'apex-scaffold'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['localhost:8080']
```

**Grafana仪表板**:
- JVM 内存使用
- HTTP 请求响应时间(P50/P95/P99)
- 数据库连接池状态
- 缓存命中率
- API 错误率

---

## ✅ 验收标准

### 任务4验收

- [ ] 消息推送: 发送消息、收件箱、已读标记功能正常
- [ ] 组织同步: 全量同步、部门树查询功能正常
- [ ] 审批流程: 基础框架完成,可创建和查询审批
- [ ] 日历管理: 基础框架完成,可创建和查询事件
- [ ] 所有模块通过编译和基础测试

### 任务5验收

- [ ] 项目可正常编译,无依赖冲突警告
- [ ] 应用可正常启动
- [ ] OAuth2功能正常

### 任务6验收

- [ ] 代码生成性能提升30%+
- [ ] SSO认证性能提升50%+
- [ ] API P95响应时间<500ms
- [ ] 缓存命中率>80%
- [ ] JMH基准测试报告完成

---

## 📋 实施时间表

| 周次 | 主要任务 | 交付物 |
|------|---------|--------|
| 第1周 | 任务4-消息推送+组织同步 | 代码+基础测试 |
| 第2周 | 任务4-审批+日历框架 | 代码+基础测试 |
| 第3周 | 任务5-Keycloak修复 | 修复报告 |
| 第4-5周 | 任务6-性能优化 | 优化报告+基准测试 |

---

## 📚 参考资料

- 办公平台规划: [`office-platform-enhancement-plan.md`](office-platform-enhancement-plan.md)
- 办公平台总结: [`office-platform-summary.md`](office-platform-summary.md)
- 后续工作指导: [`next-steps-guide.md`](next-steps-guide.md)
- 数据库脚本: `V42__add_office_platform_tables.sql`（目标不存在，无已核实替代文件；原路径：`../../apex-interfaces/src/main/resources/db/migration/mysql/V42__add_office_platform_tables.sql`）

---

**文档编写**: Apex Team  
**最后更新**: 2026-09-19 21:00:00  
**文档版本**: v1.0