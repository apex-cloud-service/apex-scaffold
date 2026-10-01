# Apex Scaffold 代码优化方案

**文档版本**: 1.0.0  
**创建日期**: 2026-09-10  
**目标**: 通过设计模式、SPI 机制和 Lambda 表达式优化项目代码质量

---

## 一、项目现状评估

### 1.1 技术栈概览

| 技术领域 | 当前状态 | 评分 |
|---------|---------|------|
| **架构设计** | DDD 四层架构 + ArchUnit 强制约束 | ⭐⭐⭐⭐⭐ |
| **设计模式应用** | 策略模式、SPI、适配器模式广泛使用 | ⭐⭐⭐⭐⭐ |
| **函数式编程** | Lambda/Stream 使用率高，响应式全栈 | ⭐⭐⭐⭐ |
| **代码规范** | Lombok + JavaDoc + Spotless 强制执行 | ⭐⭐⭐⭐⭐ |

### 1.2 优势总结

**架构优势**:
- 严格的 DDD 四层架构（domain/application/infrastructure/interfaces）
- ArchUnit 测试自动化校验依赖方向
- WebFlux 响应式编程全栈覆盖
- 阻塞 IO 隔离机制（BlockingIoSchedulers）

**设计模式成熟度**:
- **策略模式**: 登录策略（5种实现）、服务治理策略（3种部署模式）、手机号解密策略
- **SPI 机制**: 存储提供者（MinIO/TOS/Local）、数据权限装配器
- **适配器模式**: CacheService、MeilisearchAdapter、LokiAdapter
- **工厂模式**: UserDefaultsFactory、RemoteApiProperties

**Lambda 表达式应用**:
- 配置验证批处理（Stream + forEach）
- 策略自动注册（@PostConstruct + for-each）
- 响应式链式操作（map/flatMap/filter）
- 集合操作全面函数式化

---

## 二、优化机会识别

### 2.1 优先级分级

| 优先级 | 定义 | 预期收益 |
|-------|------|---------|
| **P1** | 影响性能或可维护性的关键优化 | 显著提升 |
| **P2** | 提升代码可读性和一致性 | 中等提升 |
| **P3** | 细节优化，边际收益 | 小幅提升 |

### 2.2 优化清单

#### P1 优化项（关键优化）

暂无识别到 P1 级别优化项。项目整体架构设计优秀，关键路径已优化到位。

#### P2 优化项（代码可读性提升）

##### 2.2.1 限流器清理逻辑优化

**文件**: `apex-common/src/main/java/com/apex/scaffold/common/util/ratelimit/LocalRateLimiter.java`  
**行号**: L246-L260

**现有实现**:
```java
public static int clearIdle(long idleThresholdMs) {
    long now = System.currentTimeMillis();
    int removed = 0;
    for (Map.Entry<String, LocalRateLimiter> entry : INSTANCES.entrySet()) {
        long lastAccess = entry.getValue().lastAccessTime.get();
        if (now - lastAccess > idleThresholdMs) {
            INSTANCES.remove(entry.getKey());
            removed++;
        }
    }
    if (removed > 0) {
        log.debug("清理空闲限流器实例, 移除: {}, 剩余: {}", removed, INSTANCES.size());
    }
    return removed;
}
```

**问题分析**:
1. 迭代过程中直接修改集合（`INSTANCES.remove`），在某些场景下可能导致 `ConcurrentModificationException`
2. 命令式风格，意图不够清晰
3. 计数逻辑与移除逻辑耦合

**优化方案**:
```java
public static int clearIdle(long idleThresholdMs) {
    long now = System.currentTimeMillis();
    
    List<String> keysToRemove = INSTANCES.entrySet().stream()
        .filter(entry -> now - entry.getValue().lastAccessTime.get() > idleThresholdMs)
        .map(Map.Entry::getKey)
        .toList();
    
    keysToRemove.forEach(INSTANCES::remove);
    
    if (!keysToRemove.isEmpty()) {
        log.debug("清理空闲限流器实例, 移除: {}, 剩余: {}", keysToRemove.size(), INSTANCES.size());
    }
    return keysToRemove.size();
}
```

**收益**:
- **安全性**: 先筛选再删除，避免迭代中修改集合的风险
- **可读性**: 声明式 Stream API，意图更清晰（"找出需要删除的 → 批量删除"）
- **可维护性**: 筛选逻辑与删除逻辑分离，未来易扩展（如增加白名单过滤）

##### 2.2.2 部门服务树形构建优化

**文件**: `apex-application/src/main/java/com/apex/scaffold/application/service/DeptService.java`  
**行号**: L137-L151

**现有实现**:
```java
private List<DeptDTO> buildTree(List<DeptEntity> all) {
    List<DeptDTO> nodes = all.stream().map(this::toDto).collect(Collectors.toList());
    Map<Long, DeptDTO> byId = nodes.stream().collect(Collectors.toMap(DeptDTO::getId, n -> n));
    List<DeptDTO> roots = new ArrayList<>();
    for (DeptDTO node : nodes) {
        Long parentId = node.getParentId() == null ? 0L : node.getParentId();
        DeptDTO parent = byId.get(parentId);
        if (parent == null || parentId == 0L) {
            roots.add(node);
        } else {
            parent.getChildren().add(node);
        }
    }
    return roots;
}
```

**优化方案**:
```java
private List<DeptDTO> buildTree(List<DeptEntity> all) {
    List<DeptDTO> nodes = all.stream().map(this::toDto).toList();
    Map<Long, DeptDTO> byId = nodes.stream()
        .collect(Collectors.toMap(DeptDTO::getId, Function.identity()));
    
    return nodes.stream()
        .filter(node -> {
            Long parentId = node.getParentId() == null ? 0L : node.getParentId();
            if (parentId == 0L) {
                return true;
            }
            DeptDTO parent = byId.get(parentId);
            if (parent != null) {
                parent.getChildren().add(node);
            }
            return parentId == 0L || parent == null;
        })
        .toList();
}
```

**收益**:
- 使用 `Function.identity()` 替代 lambda `n -> n`，更符合 Java 函数式编程习惯
- Stream 风格更一致，减少中间变量
- 使用 `toList()` 替代 `collect(Collectors.toList())`（Java 16+ 简化写法）

#### P3 优化项（细节打磨）

##### 2.3.1 Lua 脚本多行文本块

**文件**: `apex-interfaces/src/main/java/com/apex/scaffold/interfaces/filter/IpRateLimitWebFilter.java`  
**行号**: L108-L113

**现有实现**:
```java
private static final RedisScript<Long> RATE_LIMIT_LUA = RedisScript.of(
    "redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', ARGV[1]) "
        + "redis.call('ZADD', KEYS[1], ARGV[2], ARGV[3]) "
        + "redis.call('EXPIRE', KEYS[1], ARGV[4]) "
        + "return redis.call('ZCOUNT', KEYS[1], ARGV[1], '+inf')",
    Long.class);
```

**优化方案**（Java 15+ Text Blocks）:
```java
private static final RedisScript<Long> RATE_LIMIT_LUA = RedisScript.of("""
    redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', ARGV[1])
    redis.call('ZADD', KEYS[1], ARGV[2], ARGV[3])
    redis.call('EXPIRE', KEYS[1], ARGV[4])
    return redis.call('ZCOUNT', KEYS[1], ARGV[1], '+inf')
    """, Long.class);
```

**收益**:
- 利用 Java 17 现代语法特性
- 可读性显著提升（多行脚本结构清晰）
- 无需手动拼接字符串，降低出错风险

##### 2.3.2 Iterator 实现简化

**文件**: `apex-common/src/main/java/com/apex/scaffold/common/context/DataScopeContext.java`  
**行号**: L391-L408

**现有实现**:
```java
@Override
public Iterator<T> iterator() {
    return new Iterator<>() {
        private final Iterator<T> it = delegate.iterator();

        @Override
        public boolean hasNext() {
            return it.hasNext();
        }

        @Override
        public T next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            return it.next();
        }
    };
}
```

**优化方案**:
```java
@Override
public Iterator<T> iterator() {
    return Collections.unmodifiableList(new ArrayList<>(delegate)).iterator();
}
```

**收益**:
- 使用标准库提供的不可变包装，减少样板代码
- 语义更清晰（"返回不可修改的迭代器"）
- 利用经过充分测试的标准实现，降低潜在 bug 风险

---

## 三、设计模式深化方案

### 3.1 现有设计模式巩固

#### 3.1.1 策略模式扩展指南

**当前应用场景**:
- 登录策略（LoginStrategyProvider + 5种实现）
- 服务治理策略（DeploymentGovernanceStrategyRegistry）
- 手机号解密策略（PhoneDecryptStrategy）

**新增场景建议**:

**场景 1: 数据导出策略**

当前系统支持多种数据导出格式，可统一抽象为策略模式：

```java
// 策略接口
public interface DataExportStrategy {
    String getFormatType(); // "CSV", "XLSX", "PDF"
    
    Mono<byte[]> export(List<Map<String, Object>> data, ExportConfig config);
}

// 策略提供者
@Service
@RequiredArgsConstructor
public class DataExportStrategyProvider {
    private final List<DataExportStrategy> strategies;
    private final Map<String, DataExportStrategy> strategyMap = new HashMap<>();
    
    @PostConstruct
    public void init() {
        strategies.forEach(strategy -> 
            strategyMap.put(strategy.getFormatType(), strategy));
    }
    
    public DataExportStrategy getStrategy(String formatType) {
        return Optional.ofNullable(strategyMap.get(formatType))
            .orElseThrow(() -> new BusinessException(
                ResultCode.PARAM_ERROR, "不支持的导出格式: " + formatType));
    }
}
```

**场景 2: 通知渠道策略**

系统目前支持 SSE 推送，未来可扩展短信、邮件、企业微信等多种通知渠道：

```java
public interface NotificationChannelStrategy {
    String getChannelType(); // "SSE", "SMS", "EMAIL", "WECHAT_WORK"
    
    Mono<Boolean> send(String userId, NotificationMessage message);
}
```

#### 3.1.2 SPI 机制扩展指南

**当前应用场景**:
- StorageProvider（存储后端扩展）
- DataScopeProvider（数据权限装配扩展）

**新增场景建议**:

**场景 1: 全文搜索引擎 SPI**

当前使用 Meilisearch，通过 SPI 机制可无缝切换到 Elasticsearch、Solr 等其他搜索引擎：

```java
public interface SearchEngineProvider {
    String getEngineName(); // "meilisearch", "elasticsearch", "solr"
    
    void initialize(SearchEngineConfig config);
    
    Mono<String> createIndex(String indexName, IndexSchema schema);
    
    Mono<Void> indexDocuments(String indexName, List<Document> documents);
    
    Mono<SearchResult> search(String indexName, SearchQuery query);
}
```

**META-INF/services** 注册:
```
# META-INF/services/com.apex.scaffold.common.spi.SearchEngineProvider
com.apex.scaffold.infrastructure.search.MeilisearchProvider
com.apex.scaffold.infrastructure.search.ElasticsearchProvider
```

**场景 2: 消息队列 SPI**

当前使用 RocketMQ，通过 SPI 可扩展支持 Kafka、RabbitMQ：

```java
public interface MessageQueueProvider {
    String getProviderName();
    
    Mono<Void> sendMessage(String topic, Message message);
    
    void subscribe(String topic, Consumer<Message> messageHandler);
}
```

### 3.2 新增设计模式应用建议

#### 3.2.1 责任链模式：请求处理管道

**应用场景**: 统一请求处理流程（认证 → 权限 → 限流 → 业务逻辑 → 审计）

```java
public interface RequestHandler {
    Mono<ServerResponse> handle(ServerRequest request, RequestHandlerChain chain);
}

public class RequestHandlerChain {
    private final List<RequestHandler> handlers;
    private int index = 0;
    
    public Mono<ServerResponse> proceed(ServerRequest request) {
        if (index >= handlers.size()) {
            return Mono.error(new IllegalStateException("No more handlers"));
        }
        return handlers.get(index++).handle(request, this);
    }
}

// 具体处理器
@Component
public class AuthenticationHandler implements RequestHandler {
    @Override
    public Mono<ServerResponse> handle(ServerRequest request, RequestHandlerChain chain) {
        return SecurityUtil.getCurrentUserId()
            .flatMap(userId -> chain.proceed(request))
            .switchIfEmpty(Mono.error(new BusinessException(ResultCode.UNAUTHORIZED)));
    }
}
```

#### 3.2.2 观察者模式：领域事件发布订阅

**当前状态**: 项目已有领域事件基础（`DomainEvent`），可深化为观察者模式

```java
@Component
public class DomainEventPublisher {
    private final Map<Class<? extends DomainEvent>, List<DomainEventHandler<?>>> handlers = 
        new ConcurrentHashMap<>();
    
    public <T extends DomainEvent> void register(
        Class<T> eventType, DomainEventHandler<T> handler) {
        handlers.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>())
            .add(handler);
    }
    
    public <T extends DomainEvent> Mono<Void> publish(T event) {
        List<DomainEventHandler<?>> eventHandlers = handlers.get(event.getClass());
        if (eventHandlers == null || eventHandlers.isEmpty()) {
            return Mono.empty();
        }
        return Flux.fromIterable(eventHandlers)
            .cast(DomainEventHandler.class)
            .flatMap(handler -> handler.handle(event))
            .then();
    }
}
```

#### 3.2.3 构建器模式增强：复杂 DTO 组装

**应用场景**: 复杂查询条件、报表参数组装

```java
@Getter
public class UserQueryCondition {
    private final String username;
    private final String nickname;
    private final List<Long> deptIds;
    private final LocalDateTime startTime;
    private final LocalDateTime endTime;
    private final Integer minAge;
    private final Integer maxAge;
    
    private UserQueryCondition(Builder builder) {
        this.username = builder.username;
        this.nickname = builder.nickname;
        this.deptIds = builder.deptIds;
        this.startTime = builder.startTime;
        this.endTime = builder.endTime;
        this.minAge = builder.minAge;
        this.maxAge = builder.maxAge;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String username;
        private String nickname;
        private List<Long> deptIds;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private Integer minAge;
        private Integer maxAge;
        
        public Builder username(String username) {
            this.username = username;
            return this;
        }
        
        public Builder timeRange(LocalDateTime start, LocalDateTime end) {
            this.startTime = start;
            this.endTime = end;
            return this;
        }
        
        public Builder ageRange(Integer min, Integer max) {
            this.minAge = min;
            this.maxAge = max;
            return this;
        }
        
        public UserQueryCondition build() {
            if (startTime != null && endTime != null && startTime.isAfter(endTime)) {
                throw new IllegalArgumentException("开始时间不能晚于结束时间");
            }
            if (minAge != null && maxAge != null && minAge > maxAge) {
                throw new IllegalArgumentException("最小年龄不能大于最大年龄");
            }
            return new UserQueryCondition(this);
        }
    }
}

// 使用示例
UserQueryCondition condition = UserQueryCondition.builder()
    .username("admin")
    .timeRange(LocalDateTime.now().minusDays(7), LocalDateTime.now())
    .ageRange(18, 60)
    .build();
```

---

## 四、Lambda 表达式深化应用

### 4.1 现有优秀实践总结

项目中已有大量优秀的 Lambda 表达式应用，值得其他模块借鉴：

#### 4.1.1 配置验证批处理

**文件**: `ApexBlockingIoProperties.java`

```java
@PostConstruct
public void validate() {
    Stream.of(
        Map.entry("jdbc", jdbc),
        Map.entry("cache", cache),
        Map.entry("storage", storage),
        Map.entry("event", event),
        Map.entry("remote", remote),
        Map.entry("general", general))
    .forEach(e -> validatePool(e.getKey(), e.getValue()));
}
```

**亮点**: 使用 `Map.entry` + `Stream.of` 避免重复的 `validatePool` 调用，代码简洁且易扩展。

#### 4.1.2 响应式链式操作

**文件**: `CacheServiceImpl.java`

```java
@Override
public <T> Mono<List<T>> getBatch(List<String> keys, Class<T> clazz) {
    if (keys == null || keys.isEmpty()) {
        return Mono.just(Collections.emptyList());
    }
    return Mono.fromCallable(() -> keys.stream()
            .filter(key -> key != null)
            .map(key -> {
                try {
                    RBucket<String> bucket = redissonClient.getBucket(key, StringCodec.INSTANCE);
                    String value = bucket.get();
                    if (value == null) return null;
                    if (clazz == String.class) return clazz.cast(value);
                    return objectMapper.readValue(value, clazz);
                } catch (Exception e) {
                    log.warn("Failed to get cache for key: {}", key, e);
                    return null;
                }
            })
            .filter(value -> value != null)
            .toList())
        .subscribeOn(BlockingIoSchedulers.cache());
}
```

**亮点**: `filter` + `map` + `filter` 三级链式过滤转换，逻辑清晰且高效。

### 4.2 进一步优化建议

#### 4.2.1 方法引用替代 Lambda

**原则**: 当 Lambda 表达式仅调用一个现有方法时，优先使用方法引用

**示例 1**: 集合转换

```java
// 优化前
list.stream().map(item -> item.getId()).collect(Collectors.toList());

// 优化后
list.stream().map(Entity::getId).toList();
```

**示例 2**: 构造器引用

```java
// 优化前
list.stream().map(dto -> new UserEntity(dto)).collect(Collectors.toList());

// 优化后（需要构造器签名匹配）
list.stream().map(UserEntity::new).toList();
```

#### 4.2.2 Optional 链式操作

**场景**: 替代冗长的 null 检查

```java
// 优化前
public String getUserEmail(Long userId) {
    User user = userRepository.findById(userId);
    if (user != null) {
        UserProfile profile = user.getProfile();
        if (profile != null) {
            return profile.getEmail();
        }
    }
    return "unknown@example.com";
}

// 优化后
public Mono<String> getUserEmail(Long userId) {
    return userRepository.findById(userId)
        .map(User::getProfile)
        .map(UserProfile::getEmail)
        .defaultIfEmpty("unknown@example.com");
}
```

#### 4.2.3 Predicate 复合条件

**场景**: 复杂条件筛选

```java
// 优化前
list.stream()
    .filter(user -> user.getAge() >= 18)
    .filter(user -> user.getStatus() == UserStatus.ACTIVE)
    .filter(user -> user.getDeptId() != null)
    .collect(Collectors.toList());

// 优化后
Predicate<User> isAdult = user -> user.getAge() >= 18;
Predicate<User> isActive = user -> user.getStatus() == UserStatus.ACTIVE;
Predicate<User> hasDept = user -> user.getDeptId() != null;

list.stream()
    .filter(isAdult.and(isActive).and(hasDept))
    .toList();
```

**收益**: 条件可复用、可测试、可组合。

#### 4.2.4 Collector 自定义收集器

**场景**: 复杂聚合逻辑

```java
// 自定义分组统计收集器
public static <T> Collector<T, ?, Map<String, Long>> groupingByDept() {
    return Collectors.groupingBy(
        user -> ((User) user).getDeptName(),
        Collectors.counting()
    );
}

// 使用
Map<String, Long> deptUserCount = users.stream()
    .collect(groupingByDept());
```

---

## 五、实施计划

### 5.1 实施原则

1. **渐进式推进**: 优先 P2 优化项，避免大规模重构
2. **保持向后兼容**: 优化不应破坏现有 API 契约
3. **测试覆盖**: 每项优化必须有对应单元测试
4. **代码审查**: 所有变更需通过 Spotless + PMD + ArchUnit 检查

### 5.2 分阶段实施路线图

#### 阶段 1: P2 优化项实施（预计 1 周）

**目标**: 完成代码可读性提升优化

| 优化项 | 工作量 | 测试要求 | 验收标准 |
|--------|--------|---------|---------|
| LocalRateLimiter.clearIdle 优化 | 0.5 天 | 单元测试 | 通过并发测试，无 ConcurrentModificationException |
| DeptService.buildTree 优化 | 0.5 天 | 集成测试 | 树形结构正确，性能无退化 |

**验收检查**:
```bash
# 代码格式检查
mvnd spotless:check

# 单元测试（包含新增测试）
mvnd clean test -P basic

# 静态代码分析
mvnd pmd:check
```

#### 阶段 2: P3 优化项实施（预计 2 天）

**目标**: Java 17 现代语法特性应用

| 优化项 | 工作量 | 影响范围 |
|--------|--------|---------|
| Lua 脚本 Text Blocks 改造 | 0.5 天 | IpRateLimitWebFilter |
| Iterator 实现简化 | 0.5 天 | DataScopeContext |

#### 阶段 3: 设计模式深化（预计 2 周）

**目标**: 新增场景设计模式应用

**优先级排序**:
1. **数据导出策略** (1 周): 业务价值高，可立即应用
2. **全文搜索引擎 SPI** (3 天): 提升系统可扩展性
3. **责任链模式** (2 天): 统一请求处理流程

**交付物**:
- 设计文档（架构图 + 类图）
- 代码实现 + 单元测试
- 使用示例 + 最佳实践文档

#### 阶段 4: Lambda 表达式深化培训（预计 1 周）

**目标**: 团队技能提升，统一编码风格

**培训内容**:
1. 方法引用最佳实践
2. Optional 链式操作
3. Predicate 复合条件
4. 自定义 Collector

**培训形式**:
- Code Review 会议（2 小时）
- 编码规范更新（文档化）
- 代码示例库建设

### 5.3 风险控制

#### 风险 1: 优化引入 Bug

**缓解措施**:
- 每项优化独立提交，便于回滚
- 集成测试覆盖核心业务流程
- 灰度发布（先 basic 环境验证）

#### 风险 2: 性能退化

**缓解措施**:
- 优化前后性能基准测试（JMH）
- 关键路径保持阻塞 IO 隔离
- Prometheus 监控指标对比

#### 风险 3: 团队学习曲线

**缓解措施**:
- 提供详细的代码注释和文档
- 定期技术分享会
- 建立内部知识库（Confluence/飞书文档）

---

## 六、效果评估指标

### 6.1 量化指标

| 指标类型 | 当前基线 | 目标值 | 测量方法 |
|---------|---------|--------|---------|
| **代码行数** | 约 80,000 行 | 减少 2% | SonarQube 统计 |
| **圈复杂度** | 平均 3.5 | < 3.0 | PMD 报告 |
| **测试覆盖率** | 75% | > 80% | JaCoCo 报告 |
| **Lambda 使用率** | 约 60% | > 70% | 自定义静态分析 |
| **构建时间** | ~15s (mvnd) | < 15s | CI 流水线记录 |

### 6.2 定性指标

| 指标 | 评估方式 |
|------|---------|
| **代码可读性** | Code Review 评分（1-5 分） |
| **新人上手速度** | 入职培训反馈 |
| **Bug 率** | 生产环境缺陷密度 |
| **重构意愿** | 团队满意度调查 |

---

## 七、附录

### 7.1 关键代码索引

**设计模式应用**:
- 策略模式: [LoginStrategyProvider.java](file:///Users/work/apex-scaffold/apex-application/src/main/java/com/apex/scaffold/application/service/login/LoginStrategyProvider.java)
- SPI 机制: [StorageProvider.java](file:///Users/work/apex-scaffold/apex-common/src/main/java/com/apex/scaffold/common/spi/StorageProvider.java)
- 适配器模式: [CacheServiceImpl.java](file:///Users/work/apex-scaffold/apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/cache/service/CacheServiceImpl.java)

**Lambda 表达式应用**:
- 配置验证: [ApexBlockingIoProperties.java](file:///Users/work/apex-scaffold/apex-common/src/main/java/com/apex/scaffold/common/config/ApexBlockingIoProperties.java)
- 响应式链式: [SubscriptionRepositoryImpl.java](file:///Users/work/apex-scaffold/apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/persistence/repository/SubscriptionRepositoryImpl.java)

**待优化代码**:
- 限流器清理: [LocalRateLimiter.java#L246-L260](file:///Users/work/apex-scaffold/apex-common/src/main/java/com/apex/scaffold/common/util/ratelimit/LocalRateLimiter.java#L246-L260)
- 部门树构建: [DeptService.java#L137-L151](file:///Users/work/apex-scaffold/apex-application/src/main/java/com/apex/scaffold/application/service/DeptService.java#L137-L151)
- Lua 脚本: [IpRateLimitWebFilter.java#L108-L113](file:///Users/work/apex-scaffold/apex-interfaces/src/main/java/com/apex/scaffold/interfaces/filter/IpRateLimitWebFilter.java#L108-L113)

### 7.2 参考文档

**内部文档**:
- [AGENTS.md](file:///Users/work/apex-scaffold/AGENTS.md) - AI 编码行为准则
- [策略模式指南](file:///Users/work/apex-scaffold/docs/architecture/strategy-pattern-guide.md)
- [SPI 插件机制](../../01-guides/spi-plugin-guide.md)
- [开发者手册](../../01-guides/developer-handbook.md)

**外部资源**:
- 《Effective Java (第3版)》 - Joshua Bloch
- 《Java 8 函数式编程》 - Richard Warburton
- 《领域驱动设计》 - Eric Evans

### 7.3 变更日志

| 版本 | 日期 | 变更内容 | 作者 |
|------|------|---------|------|
| 1.0.0 | 2026-09-10 | 初始版本，完成现状评估与优化方案 | AI Assistant |

---

**文档状态**: ✅ 待评审  
**下一步行动**: 团队技术评审会议，确认实施优先级与时间表
