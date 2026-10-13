# 最佳实践

本文档总结了使用 Apex Scaffold 开发企业级应用的最佳实践,帮助你避免常见陷阱,提升代码质量。

## 🏗️ 架构设计原则

### 1. 严格遵循DDD分层

```
Interfaces (接口层)
    ↓ 调用
Application (应用层)
    ↓ 调用
Domain (领域层)
    ↓ 依赖
Infrastructure (基础设施层)
```

**✅ 正确做法**:
- Interfaces → Application → Domain ← Infrastructure
- Domain层只定义接口,Infrastructure层实现
- 禁止跨层调用(如Interfaces直接调用Infrastructure)

**❌ 错误做法**:
```java
// 禁止: Controller直接调用Repository
@RestController
public class UserController {
    @Autowired
    private UserMapper userMapper; // ❌ 应该通过Service层
}
```

### 2. 领域模型设计规范

**实体(Entity)**:
```java
@AggregateRoot  // 标记聚合根
@Data
public class UserEntity extends BaseEntity {
    private String username;
    private String email;
    
    // 业务行为放在实体内
    public void changeEmail(String newEmail) {
        if (!isValidEmail(newEmail)) {
            throw new BusinessException("邮箱格式不正确");
        }
        this.email = newEmail;
        registerEvent(new UserEmailChangedEvent(this.id, newEmail));
    }
}
```

**值对象(Value Object)**:
```java
@Value
public class Email {
    private final String value;
    
    public Email(String value) {
        if (!isValidEmail(value)) {
            throw new IllegalArgumentException("无效邮箱");
        }
        this.value = value;
    }
}
```

### 3. 仓储模式(Repository)

**定义接口(Domain层)**:
```java
public interface UserRepository {
    Optional<UserEntity> findById(Long id);
    void save(UserEntity user);
    void deleteById(Long id);
    List<UserEntity> findByStatus(UserStatus status);
}
```

**实现接口(Infrastructure层)**:
```java
@Repository
public class UserRepositoryImpl implements UserRepository {
    
    @Autowired
    private UserMapper userMapper;
    
    @Override
    public Optional<UserEntity> findById(Long id) {
        return Optional.ofNullable(userMapper.selectById(id));
    }
    
    @Override
    public void save(UserEntity user) {
        if (user.getId() == null) {
            userMapper.insert(user);
        } else {
            userMapper.updateById(user);
        }
    }
}
```

## 💻 编码规范

### 1. 统一响应格式

**✅ 正确做法**:
```java
@GetMapping("/users/{id}")
public Mono<ServerResponse> getUser(ServerRequest request) {
    Long id = Long.parseLong(request.pathVariable("id"));
    return userService.findById(id)
        .map(user -> buildOk(Result.success(user)))
        .switchIfEmpty(Mono.defer(() -> 
            buildNotFound(Result.error(ResultCode.USER_NOT_FOUND))
        ));
}
```

**❌ 错误做法**:
```java
// 禁止返回原始对象或Map
@GetMapping("/users/{id}")
public Mono<User> getUser(@PathVariable Long id) { // ❌
    return userService.findById(id);
}
```

### 2. 异常处理

**业务异常**:
```java
if (user == null) {
    throw new BusinessException(ResultCode.USER_NOT_FOUND);
}
```

**全局异常处理器已自动捕获**,无需手动try-catch。

### 3. 参数校验

**使用JSR-303注解**:
```java
@Data
public class CreateUserRequest {
    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 20, message = "用户名长度3-20字符")
    private String username;
    
    @Email(message = "邮箱格式不正确")
    private String email;
    
    @Min(value = 18, message = "年龄必须大于18岁")
    @Max(value = 100, message = "年龄不能超过100岁")
    private Integer age;
}
```

### 4. 日志规范

**✅ 正确做法**:
```java
@Slf4j
@Component
public class UserService {
    
    public Mono<UserEntity> create(CreateUserRequest request) {
        log.info("开始创建用户, username={}", request.getUsername());
        
        return userRepository.save(user)
            .doOnSuccess(user -> 
                log.info("用户创建成功, userId={}", user.getId())
            )
            .doOnError(e -> 
                log.error("用户创建失败, username={}, error={}", 
                    request.getUsername(), e.getMessage(), e)
            );
    }
}
```

**❌ 错误做法**:
```java
System.out.println("debug info"); // ❌ 禁止使用System.out
log.info("用户登录, password={}", password); // ❌ 禁止记录敏感信息
log.debug("user=" + user.toString()); // ❌ 使用占位符而非拼接
```

## 🔐 安全最佳实践

### 1. JWT密钥管理

**✅ 正确做法**:
```yaml
jwt:
  secret: ${JWT_SECRET}  # 从环境变量读取
```

```bash
export JWT_SECRET=$(openssl rand -base64 48)
mvnd spring-boot:run
```

**❌ 错误做法**:
```yaml
jwt:
  secret: my-secret-key  # ❌ 硬编码
```

### 2. 密码存储

**使用BCrypt加密**:
```java
@Service
public class AuthService {
    
    @Autowired
    private PasswordEncoder passwordEncoder;
    
    public Mono<UserEntity> register(RegisterRequest request) {
        String encodedPassword = passwordEncoder.encode(request.getPassword());
        user.setPassword(encodedPassword);
        return userRepository.save(user);
    }
}
```

### 3. SQL注入防护

**✅ 正确做法**:
```xml
<select id="findByUsername">
    SELECT * FROM user WHERE username = #{username}
</select>
```

**❌ 错误做法**:
```xml
<select id="findByUsername">
    SELECT * FROM user WHERE username = '${username}'  <!-- ❌ -->
</select>
```

## ⚡ 性能优化

### 1. 缓存策略

**多级缓存使用**:
```java
@Service
public class ProductService {
    
    @Cacheable(value = "product", key = "#id", unless = "#result == null")
    public Mono<ProductEntity> findById(Long id) {
        return productRepository.findById(id);
    }
    
    @CacheEvict(value = "product", key = "#id")
    public Mono<Void> update(Long id, ProductUpdateRequest request) {
        // 更新后清除缓存
        return productRepository.updateById(product);
    }
}
```

### 2. 数据库查询优化

**避免N+1问题**:
```java
// ❌ N+1查询
List<Order> orders = orderRepository.findAll();
orders.forEach(order -> {
    User user = userRepository.findById(order.getUserId()); // 每次循环都查DB
});

// ✅ 批量查询
List<Long> userIds = orders.stream()
    .map(Order::getUserId)
    .distinct()
    .collect(Collectors.toList());
Map<Long, User> userMap = userRepository.findByIdIn(userIds)
    .stream()
    .collect(Collectors.toMap(User::getId, u -> u));
```

**使用分页**:
```java
public Mono<PageResponse<UserEntity>> listUsers(int page, int size) {
    Page<UserEntity> userPage = new Page<>(page, size);
    return Mono.fromCallable(() -> userMapper.selectPage(userPage, null))
        .map(p -> PageResponse.of(p.getRecords(), p.getTotal(), page, size));
}
```

### 3. 异步处理

**使用WebFlux响应式**:
```java
@Service
public class OrderService {
    
    public Mono<OrderResult> createOrder(CreateOrderRequest request) {
        return Mono.zip(
            userRepository.findById(request.getUserId()),
            productRepository.findById(request.getProductId())
        ).flatMap(tuple -> {
            User user = tuple.getT1();
            Product product = tuple.getT2();
            // 并行查询后合并结果
            return processOrder(user, product);
        });
    }
}
```

## 🧪 测试最佳实践

### 1. 单元测试

```java
@ExtendWith(MockitoExtension.class)
class UserServiceTest {
    
    @Mock
    private UserRepository userRepository;
    
    @InjectMocks
    private UserService userService;
    
    @Test
    void testCreateUser() {
        // Given
        CreateUserRequest request = new CreateUserRequest();
        request.setUsername("testuser");
        
        UserEntity expectedUser = new UserEntity();
        expectedUser.setId(1L);
        expectedUser.setUsername("testuser");
        
        when(userRepository.save(any(UserEntity.class)))
            .thenReturn(Mono.just(expectedUser));
        
        // When
        Mono<UserEntity> result = userService.create(request);
        
        // Then
        StepVerifier.create(result)
            .expectNextMatches(user -> 
                user.getId().equals(1L) && 
                user.getUsername().equals("testuser")
            )
            .verifyComplete();
    }
}
```

### 2. 集成测试

```java
@SpringBootTest
@AutoConfigureWebTestClient
class UserControllerIntegrationTest {
    
    @Autowired
    private WebTestClient webTestClient;
    
    @Test
    void testGetUser() {
        webTestClient.get()
            .uri("/api/users/1")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.code").isEqualTo(200)
            .jsonPath("$.data.username").isEqualTo("testuser");
    }
}
```

## 📨 重试与消息可靠性

### 1. 自动重试边界

`@AutoRetry` 只应标注幂等或具备幂等保护的操作，例如缓存刷新、可重复查询和临时性基础设施调用。写入数据库、发送外部请求等操作必须先确认重复执行不会产生重复副作用。

通过 `retryOn` 限定可重试异常；外部 HTTP 4xx、参数校验失败和业务规则异常通常不应重试。响应式方法应返回 `Mono` 或 `Flux`，由调用链订阅触发重试，不要在业务代码中 fire-and-forget `subscribe()`。

### 2. RocketMQ 消费确认

listener 只有在业务副作用完成后才能返回成功。处理失败应抛出异常，让 RocketMQ 按消费策略重试；不能先返回成功再异步执行数据库或缓存操作。

生产消息应复用或生成稳定 message ID，并同时写入消息 header 和 RocketMQ `KEYS`。消费者使用 Redis token 状态机：实例先以 `PROCESSING:<token>` 竞争处理权，业务完成且确认仍由当前实例持有时才转为 `SUCCESS`。其他实例持有不同 token 时应返回失败，避免并发重复处理。

### 3. Outbox 投递

事务内先保存领域事件和 Outbox 记录，worker 通过 claim token 获取所有权后投递。状态按 `PENDING -> PROCESSING -> DELIVERED` 流转，超时处理中的记录可回收，连续失败达到上限后进入 `DEAD`。RocketMQ callback 成功前禁止标记 `DELIVERED`，否则会丢失消息。

## 📦 部署最佳实践

### 1. 容器镜像优化

```dockerfile
# 多阶段构建减小镜像体积
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvnd clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### 2. 健康检查

```yaml
# 与项目 Containerfile 一致：探测管理端口 8081 的就绪探针（含 DB + Redis）
healthcheck:
  test: ["CMD", "wget", "-qO-", "http://127.0.0.1:8081/actuator/health/readiness", "|", "grep", "-q", "UP"]
  interval: 30s
  timeout: 5s
  retries: 3
  start_period: 90s
```

### 3. 资源限制

```yaml
services:
  apex-scaffold:
    deploy:
      resources:
        limits:
          cpus: '2'
          memory: 2G
        reservations:
          cpus: '1'
          memory: 1G
```

## ❓ 常见问题排查

### Q1: 启动慢怎么办?

1. 关闭不必要的功能模块(`features.*: false`)
2. 减少日志级别(`logging.level.root: WARN`)
3. 禁用链路追踪(`spring.zipkin.enabled: false`)

### Q2: 内存溢出(OOM)?

1. 调整JVM参数: `-Xms512m -Xmx2g`
2. 检查缓存配置(`cache.caffeine.max-size`)
3. 监控连接池大小(`spring.datasource.druid.max-active`)

### Q3: 接口响应慢?

1. 启用缓存(`@Cacheable`)
2. 检查SQL执行计划(EXPLAIN)
3. 添加索引(高频查询字段)
4. 使用异步处理(`Mono.zip`并行查询)

---

**持续更新中...** 欢迎提交PR补充更多最佳实践!
> 配套图表：[50 DDD 四层开发流程](../diagrams/50_ddd_development_flow.svg)
