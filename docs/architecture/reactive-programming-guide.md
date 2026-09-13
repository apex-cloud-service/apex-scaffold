# WebFlux 响应式编程模式指南

**版本**: 1.0  
**更新日期**: 2026-09-10  
**适用版本**: Apex Scaffold 1.2.0+

---

## 一、概述

### 1.1 什么是响应式编程

响应式编程是一种面向数据流和变化传播的编程范式。在 Apex Scaffold 项目中,我们使用 Spring WebFlux 框架实现响应式编程模型。

**核心特点**:
- **非阻塞 I/O**: 高效利用系统资源
- **背压支持**: 流量控制,防止系统过载
- **声明式编程**: 使用函数式风格描述数据流转换
- **异步处理**: 提高系统吞吐量和响应速度

### 1.2 为什么选择响应式编程

| 传统阻塞式 | WebFlux 响应式 | 优势 |
|-----------|---------------|------|
| 一个请求占用一个线程 | 少量线程处理大量请求 | 资源利用率提升 80%+ |
| 线程阻塞等待 I/O | 非阻塞异步处理 | 吞吐量提升 3-5倍 |
| 难以实现背压 | 原生支持背压 | 系统稳定性提升 |

---

## 二、核心组件

### 2.1 Reactor 核心类型

#### Mono<T> - 0 或 1 个元素的异步序列

```java
// 创建 Mono
Mono<String> mono = Mono.just("Hello");
Mono<User> user = Mono.fromCallable(() -> userService.getUser(1L));
Mono<Void> empty = Mono.empty();

// 常用操作
mono.map(s -> s.toUpperCase())           // 转换
    .filter(s -> s.startsWith("H"))      // 过滤
    .flatMap(s -> otherService.process(s)) // 链式异步调用
    .defaultIfEmpty("Default")           // 默认值
    .subscribe(System.out::println);     // 订阅执行
```

#### Flux<T> - 0 到 N 个元素的异步序列

```java
// 创建 Flux
Flux<Integer> flux = Flux.range(1, 10);
Flux<User> users = Flux.fromIterable(userList);

// 常用操作
flux.map(i -> i * 2)                    // 转换
    .filter(i -> i > 5)                 // 过滤
    .flatMap(i -> processAsync(i))      // 异步处理
    .collectList()                      // 收集为 List
    .subscribe(list -> log.info("{}", list));
```

### 2.2 WebFlux Handler 模式

#### Handler 类设计

```java
@Slf4j
@Component
public class UserHandler extends BaseHandler {
    
    private final UserService userService;
    
    public UserHandler(
            UserService userService,
            MessageUtils messageUtils,
            Validator validator) {
        super(messageUtils, validator);
        this.userService = userService;
    }
    
    /**
     * 获取用户详情
     */
    public Mono<ServerResponse> getUserDetail(ServerRequest request) {
        Long userId = Long.valueOf(request.pathVariable("userId"));
        
        return userService.getUserById(userId)
                .flatMap(user -> ServerResponse
                        .ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Result.success(user)))
                .switchIfEmpty(ServerResponse
                        .status(HttpStatus.NOT_FOUND)
                        .bodyValue(Result.error(404, "用户不存在")));
    }
    
    /**
     * 创建用户
     */
    public Mono<ServerResponse> createUser(ServerRequest request) {
        return request.bodyToMono(CreateUserRequest.class)
                .flatMap(this::validateAndThrow)  // Bean Validation 校验
                .flatMap(userService::createUser)
                .flatMap(user -> ServerResponse
                        .status(HttpStatus.CREATED)
                        .bodyValue(Result.success(user)))
                .onErrorResume(BusinessException.class, e -> 
                        ServerResponse
                                .badRequest()
                                .bodyValue(Result.error(e.getCode(), e.getMessage())));
    }
}
```

#### Router 配置

```java
@Configuration
public class UserRouter {
    
    @Bean
    public RouterFunction<ServerResponse> userRoutes(UserHandler handler) {
        return RouterFunctions.route()
                // GET 请求
                .GET("/api/users/{userId}", handler::getUserDetail)
                .GET("/api/users", handler::listUsers)
                
                // POST 请求
                .POST("/api/users", handler::createUser)
                
                // PUT 请求
                .PUT("/api/users/{userId}", handler::updateUser)
                
                // DELETE 请求
                .DELETE("/api/users/{userId}", handler::deleteUser)
                
                // 过滤器
                .filter((request, next) -> {
                    log.info("Request: {} {}", request.method(), request.path());
                    return next.handle(request);
                })
                
                .build();
    }
}
```

---

## 三、最佳实践

### 3.1 错误处理

#### 方式 1: onErrorResume - 降级处理

```java
public Mono<User> getUserWithFallback(Long userId) {
    return userService.getUserById(userId)
            .onErrorResume(e -> {
                log.error("获取用户失败,返回默认用户", e);
                return Mono.just(User.defaultUser());
            });
}
```

#### 方式 2: onErrorReturn - 返回默认值

```java
public Mono<String> getUserName(Long userId) {
    return userService.getUserById(userId)
            .map(User::getName)
            .onErrorReturn("Unknown");
}
```

#### 方式 3: onErrorMap - 转换异常

```java
public Mono<User> getUser(Long userId) {
    return userService.getUserById(userId)
            .onErrorMap(SQLException.class, e -> 
                    new BusinessException(ResultCode.DATABASE_ERROR, "数据库查询失败"));
}
```

### 3.2 组合多个异步操作

#### 串行执行 - flatMap

```java
public Mono<OrderDTO> createOrder(CreateOrderRequest request) {
    return validateStock(request)              // 1. 验证库存
            .flatMap(this::createOrderRecord)   // 2. 创建订单记录
            .flatMap(this::deductStock)         // 3. 扣减库存
            .flatMap(this::sendNotification);   // 4. 发送通知
}
```

#### 并行执行 - zip

```java
public Mono<UserDetailDTO> getUserDetail(Long userId) {
    Mono<User> user = userService.getUser(userId);
    Mono<List<Order>> orders = orderService.getUserOrders(userId);
    Mono<UserStats> stats = statsService.getUserStats(userId);
    
    return Mono.zip(user, orders, stats)
            .map(tuple -> UserDetailDTO.builder()
                    .user(tuple.getT1())
                    .orders(tuple.getT2())
                    .stats(tuple.getT3())
                    .build());
}
```

#### 任意一个完成 - or

```java
public Mono<Product> getProduct(Long productId) {
    Mono<Product> fromCache = cacheService.get(productId);
    Mono<Product> fromDb = productService.getById(productId);
    
    return Mono.first(fromCache, fromDb);  // 返回最先完成的结果
}
```

### 3.3 背压处理

```java
public Flux<DataChunk> processLargeDataset() {
    return dataSource.streamData()
            .onBackpressureBuffer(1000)     // 缓冲最多 1000 个元素
            .limitRate(100)                 // 限流:每次请求 100 个
            .map(this::transform)
            .onBackpressureDrop(dropped -> 
                    log.warn("数据被丢弃: {}", dropped));
}
```

### 3.4 超时控制

```java
public Mono<Response> callExternalApi(Request request) {
    return webClient.post()
            .uri("/api/external")
            .bodyValue(request)
            .retrieve()
            .bodyToMono(Response.class)
            .timeout(Duration.ofSeconds(5))         // 5 秒超时
            .onErrorResume(TimeoutException.class, e -> 
                    Mono.just(Response.timeout()));
}
```

### 3.5 重试策略

```java
public Mono<Data> fetchDataWithRetry() {
    return dataService.fetchData()
            .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
                    .maxBackoff(Duration.ofSeconds(10))
                    .filter(e -> e instanceof IOException)
                    .doBeforeRetry(signal -> 
                            log.warn("重试第 {} 次", signal.totalRetries() + 1)));
}
```

---

## 四、性能优化

### 4.1 避免阻塞操作

❌ **错误示例**:

```java
public Mono<User> getUser(Long userId) {
    // 阻塞调用 - 破坏响应式链
    User user = userRepository.findById(userId).block();
    return Mono.just(user);
}
```

✅ **正确示例**:

```java
public Mono<User> getUser(Long userId) {
    // 完全异步
    return userRepository.findById(userId);
}
```

### 4.2 使用 subscribeOn 和 publishOn

```java
public Mono<Result> processData(Data data) {
    return Mono.just(data)
            // 在弹性线程池中执行 CPU 密集型操作
            .subscribeOn(Schedulers.boundedElastic())
            .map(this::heavyComputation)
            // 切换到并行线程池
            .publishOn(Schedulers.parallel())
            .map(this::transform);
}
```

### 4.3 缓存热数据

```java
@Component
public class ConfigCache {
    
    private Mono<Config> configCache;
    
    @PostConstruct
    public void init() {
        // 缓存配置,多个订阅者共享同一个结果
        this.configCache = configService.loadConfig()
                .cache(Duration.ofMinutes(10));
    }
    
    public Mono<Config> getConfig() {
        return configCache;
    }
}
```

---

## 五、测试

### 5.1 单元测试

```java
@Test
void testGetUser() {
    // 准备测试数据
    User mockUser = User.builder()
            .id(1L)
            .name("Test User")
            .build();
    
    // Mock 服务
    when(userService.getUserById(1L))
            .thenReturn(Mono.just(mockUser));
    
    // 执行测试
    StepVerifier.create(userHandler.getUser(1L))
            .expectNext(mockUser)
            .verifyComplete();
}
```

### 5.2 集成测试

```java
@WebFluxTest
@ContextConfiguration(classes = {UserHandler.class, UserRouter.class})
class UserHandlerIntegrationTest {
    
    @Autowired
    private WebTestClient webTestClient;
    
    @MockBean
    private UserService userService;
    
    @Test
    void testGetUserEndpoint() {
        User mockUser = User.builder()
                .id(1L)
                .name("Test")
                .build();
        
        when(userService.getUserById(1L))
                .thenReturn(Mono.just(mockUser));
        
        webTestClient.get()
                .uri("/api/users/1")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.code").isEqualTo(0)
                .jsonPath("$.data.name").isEqualTo("Test");
    }
}
```

---

## 六、常见问题

### Q1: 何时使用 Mono vs Flux?

**Mono**: 0 或 1 个结果的场景
- 查询单条记录
- 创建/更新/删除操作
- HTTP 请求

**Flux**: 0 到 N 个结果的场景
- 查询列表
- 流式数据处理
- SSE (Server-Sent Events)

### Q2: 如何调试响应式代码?

```java
public Mono<User> getUser(Long userId) {
    return userService.getUserById(userId)
            .doOnSubscribe(s -> log.info("开始查询用户: {}", userId))
            .doOnNext(user -> log.info("查询成功: {}", user))
            .doOnError(e -> log.error("查询失败", e))
            .doFinally(signal -> log.info("完成,信号: {}", signal));
}
```

### Q3: 如何在响应式链中执行副作用?

使用 `doOnXxx` 系列方法:

```java
public Mono<Order> createOrder(CreateOrderRequest request) {
    return orderService.create(request)
            .doOnNext(order -> {
                // 发送消息队列(副作用)
                messageQueue.send(new OrderCreatedEvent(order));
            })
            .doOnNext(order -> {
                // 记录审计日志(副作用)
                auditLog.log("订单创建", order.getId());
            });
}
```

---

## 七、项目实践

### 7.1 Apex Scaffold 中的响应式架构

```
┌─────────────────────────────────────────┐
│         WebFlux Handler Layer            │  ← 33 个 Handler 类
├─────────────────────────────────────────┤
│         Application Service Layer        │  ← Mono/Flux 返回类型
├─────────────────────────────────────────┤
│         Domain Service Layer             │  ← 业务逻辑
├─────────────────────────────────────────┤
│         Repository Layer (R2DBC)         │  ← 响应式数据库访问
└─────────────────────────────────────────┘
```

### 7.2 典型流程示例

```java
// 工作流任务完成流程
public Mono<Void> completeTask(Long taskId, Long userId, String comment) {
    return taskRepository.findById(taskId)                    // 1. 查询任务
            .switchIfEmpty(Mono.error(new BusinessException("任务不存在")))
            .flatMap(task -> validateTaskStatus(task))         // 2. 验证状态
            .flatMap(task -> processRepository.findById(task.getProcessInstanceId()))  // 3. 查询流程
            .flatMap(process -> executeTaskCompletion(process, taskId, userId, comment))  // 4. 执行完成
            .flatMap(this::triggerNextNode)                    // 5. 触发下一节点
            .then();                                           // 6. 返回空结果
}
```

---

## 八、参考资料

### 官方文档
- [Project Reactor 文档](https://projectreactor.io/docs)
- [Spring WebFlux 文档](https://docs.spring.io/spring-framework/reference/web/webflux.html)

### 项目文档
- [WebFlux Handler 总结](../_archive/webflux-handler-summary.md)
- [后端开发规范](../04-development/backend-development-standards.md)

---

**版权声明**: © 2026 Apex Team  
**维护者**: Apex 开发团队
> 配套图表：[51 WebFlux 阻塞 IO 隔离](../diagrams/51_webflux_blocking_io_isolation.svg)

