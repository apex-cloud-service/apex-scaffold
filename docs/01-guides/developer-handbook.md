# Apex Scaffold 开发者手册

**版本**: 2.0  
**更新日期**: 2026-09-10  
**项目版本**: Apex Scaffold 1.2.0  
**维护者**: Apex 开发团队

---

## 📚 文档导航

### 快速开始
- [5 分钟快速上手](../00-getting-started/quickstart.md)
- [项目配置指南](../05-operations/configuration.md)
- [环境变量指南](../05-operations/configuration.md)

### 核心架构
- [架构文档索引](../02-architecture/architecture-index.md) - 29 张架构图
- [DDD 四层架构详解](../architecture/01-logical-architecture-detail.md)
- [系统架构概览](../architecture/system-architecture.md)

### 开发指南
- 📘 [**WebFlux 响应式编程指南**](../architecture/reactive-programming-guide.md) ⭐ 新增
- 📘 [**策略模式实现指南**](../architecture/strategy-pattern-guide.md) ⭐ 新增
- [WebFlux Handler 实现总结](../_archive/webflux-handler-summary.md)
- [后端开发规范](../04-development/backend-development-standards.md)
- [代码生成器使用](../04-development/codegen.md)

### 最佳实践
- [开发最佳实践](../01-guides/best-practices.md)
- [Lombok 使用指南](../04-development/lombok-guide.md)
- [Javadoc 编写指南](../04-development/javadoc-guide.md)

### 功能模块
- [工作流引擎指南](../08-project-management/features/workflow-engine-guide.md)
- [安全防护机制](../05-operations/security-protections.md)
- [数据权限使用](../01-guides/data-scope-usage.md)
- [多数据源切换](../01-guides/data-source-switch-usage.md)
- [审计日志使用](../01-guides/audit-log-usage.md)

### 运维部署
- [部署指南](../05-operations/deployment.md)
- [监控体系](../_archive/operations/monitoring.md)
- [故障排查](./TROUBLESHOOTING.md)

---

## 一、技术栈概览

### 1.1 核心框架

| 技术 | 版本 | 用途 |
|------|------|------|
| Java | 17.0.12 | 编程语言 |
| Spring Boot | 3.5.15 | 应用框架 |
| Spring WebFlux | 响应式 Web | 非阻塞式 HTTP 服务 |
| Spring Security | 6.5.0 | 认证授权 |
| Project Reactor | 3.6.13 | 响应式编程 |

### 1.2 数据访问

| 技术 | 版本 | 用途 |
|------|------|------|
| MyBatis-Plus | 3.5.10.1 | ORM 框架 |
| MySQL | 8.x | 关系数据库 |
| Redisson | 3.36.0 | Redis 客户端 |
| Flyway | 10.15.0 | 数据库迁移 |

### 1.3 微服务治理

| 技术 | 版本 | 用途 |
|------|------|------|
| Sentinel | 1.8.9 | 流量控制 |
| Resilience4j | 2.2.0 | 熔断降级 |
| Spring Cloud | 2025.0.0 | 微服务生态 |

### 1.4 可观测性

| 技术 | 版本 | 用途 |
|------|------|------|
| Micrometer | 1.14.1 | 指标采集 |
| Prometheus | - | 指标存储 |
| Grafana | - | 可视化看板 |
| Loki | - | 日志聚合 |
| Zipkin | - | 链路追踪 |

---

## 二、开发环境搭建

### 2.1 必需工具

```bash
# 1. 安装 SDKMAN
curl -s "https://get.sdkman.io" | bash

# 2. 激活项目环境
cd /Users/work/apex-scaffold
sdk env

# 3. 验证环境
java -version    # 应显示 17.0.12
mvnd --version   # 应显示 2.0.0-rc-3
```

### 2.2 IDE 配置

**IntelliJ IDEA 推荐配置**:

1. **安装插件**:
   - Lombok
   - MapStruct Support
   - SonarLint
   - CheckStyle-IDEA

2. **代码风格**:
   - 导入 `config/idea-code-style.xml`
   - 启用 "Save Actions" 插件,保存时自动格式化

3. **Maven 配置**:
   - 使用项目自带的 `.mvn/maven.config`
   - 设置 Maven runner 为 "Delegate to Maven"

### 2.3 本地运行

```bash
# 1. 启动基础服务 (MySQL + Redis)
docker-compose -f deployment/local/docker-compose.yml up -d

# 2. 运行应用 (basic profile)
mvnd spring-boot:run -pl apex-interfaces -P basic

# 3. 访问应用
# API: http://localhost:8080
# Actuator: http://localhost:8080/actuator
# OpenAPI: http://localhost:8080/swagger-ui.html
```

---

## 三、核心开发模式

### 3.1 WebFlux 函数式端点

#### 创建 Handler

```java
@Slf4j
@Component
@Tag(name = "用户管理")
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
     * 获取用户列表
     */
    @Operation(summary = "获取用户列表")
    public Mono<ServerResponse> listUsers(ServerRequest request) {
        int pageNum = Integer.parseInt(request.queryParam("pageNum").orElse("1"));
        int pageSize = Integer.parseInt(request.queryParam("pageSize").orElse("10"));
        
        return userService.listUsers(pageNum, pageSize)
                .flatMap(data -> ServerResponse
                        .ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Result.success(data)));
    }
    
    /**
     * 创建用户
     */
    @Operation(summary = "创建用户")
    public Mono<ServerResponse> createUser(ServerRequest request) {
        return request.bodyToMono(CreateUserRequest.class)
                .flatMap(this::validateAndThrow)    // Bean Validation
                .flatMap(userService::createUser)
                .flatMap(user -> ServerResponse
                        .status(HttpStatus.CREATED)
                        .bodyValue(Result.success(user)));
    }
}
```

#### 注册路由

```java
@Configuration
public class UserRouter {
    
    @Bean
    public RouterFunction<ServerResponse> userRoutes(UserHandler handler) {
        return RouterFunctions.route()
                .path("/api/v1/users", builder -> builder
                        .GET("", handler::listUsers)
                        .POST("", handler::createUser)
                        .GET("/{userId}", handler::getUserDetail)
                        .PUT("/{userId}", handler::updateUser)
                        .DELETE("/{userId}", handler::deleteUser))
                .build();
    }
}
```

📖 **详细指南**: [WebFlux 响应式编程指南](../architecture/reactive-programming-guide.md)

### 3.2 策略模式

#### 定义策略接口

```java
public interface LoginStrategy {
    Mono<LoginResponse> login(LoginRequest request);
    LoginType getLoginType();
}
```

#### 实现具体策略

```java
@Service
@RequiredArgsConstructor
public class PasswordLoginStrategy implements LoginStrategy {
    
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    
    @Override
    public Mono<LoginResponse> login(LoginRequest request) {
        return userRepository.findByUsername(request.getUsername())
                .filter(user -> passwordEncoder.matches(
                        request.getPassword(), user.getPassword()))
                .map(this::buildResponse)
                .switchIfEmpty(Mono.error(new BusinessException("用户名或密码错误")));
    }
    
    @Override
    public LoginType getLoginType() {
        return LoginType.PASSWORD;
    }
}
```

#### 使用策略

```java
@Service
@RequiredArgsConstructor
public class LoginStrategyProvider {
    
    private final List<LoginStrategy> strategies;
    private Map<LoginType, LoginStrategy> strategyMap;
    
    @PostConstruct
    public void init() {
        strategyMap = strategies.stream()
                .collect(Collectors.toMap(
                        LoginStrategy::getLoginType,
                        Function.identity()));
    }
    
    public LoginStrategy getStrategy(LoginType type) {
        return Optional.ofNullable(strategyMap.get(type))
                .orElseThrow(() -> new BusinessException("不支持的登录类型"));
    }
}
```

📖 **详细指南**: [策略模式实现指南](../architecture/strategy-pattern-guide.md)

---

## 四、DDD 四层架构

### 4.1 分层职责

```
┌────────────────────────────────────────┐
│ apex-interfaces (接口层)                │
│ Handler + Router + Filter + Config      │
├────────────────────────────────────────┤
│ apex-application (应用层)               │
│ Service + DTO + Assembler               │
├────────────────────────────────────────┤
│ apex-domain (领域层)                    │
│ Entity + Repository接口 + 领域服务       │
├────────────────────────────────────────┤
│ apex-infrastructure (基础设施层)         │
│ PO + Mapper + RepositoryImpl + 外部服务  │
└────────────────────────────────────────┘
           ↑
    apex-common (公共模块)
```

### 4.2 开发流程

#### Step 1: 领域层 (apex-domain)

```java
// 聚合根
@Getter
@Builder
public class User {
    private Long id;
    private String username;
    private String password;
    private UserStatus status;
    
    // 领域方法
    public void activate() {
        if (this.status == UserStatus.ACTIVE) {
            throw new BusinessException("用户已激活");
        }
        this.status = UserStatus.ACTIVE;
    }
}

// Repository 接口
public interface UserRepository {
    Mono<User> findById(Long id);
    Mono<User> save(User user);
}
```

#### Step 2: 基础设施层 (apex-infrastructure)

```java
// PO (持久化对象)
@Data
@TableName("sys_user")
public class UserPO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String password;
    private String status;
}

// Mapper
@Mapper
public interface UserMapper extends BaseMapper<UserPO> {
}

// Converter (MapStruct)
@Mapper(componentModel = "spring")
public interface UserConverter {
    User toDomain(UserPO po);
    UserPO toPO(User domain);
}

// Repository 实现
@Repository
@RequiredArgsConstructor
public class UserRepositoryImpl implements UserRepository {
    
    private final UserMapper userMapper;
    private final UserConverter userConverter;
    
    @Override
    public Mono<User> findById(Long id) {
        return Mono.fromCallable(() -> userMapper.selectById(id))
                .subscribeOn(Schedulers.boundedElastic())
                .map(userConverter::toDomain);
    }
}
```

#### Step 3: 应用层 (apex-application)

```java
// DTO
@Data
@Builder
public class UserDTO {
    private Long id;
    private String username;
    private String status;
}

// Assembler (转换器)
@Component
public class UserAssembler {
    public UserDTO toDTO(User user) {
        return UserDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .status(user.getStatus().name())
                .build();
    }
}

// Service
@Service
@RequiredArgsConstructor
@TenantAware
public class UserService {
    
    private final UserRepository userRepository;
    private final UserAssembler userAssembler;
    
    public Mono<UserDTO> getUserById(Long id) {
        return userRepository.findById(id)
                .map(userAssembler::toDTO);
    }
    
    @Transactional
    public Mono<UserDTO> activateUser(Long id) {
        return userRepository.findById(id)
                .doOnNext(User::activate)      // 调用领域方法
                .flatMap(userRepository::save)
                .map(userAssembler::toDTO);
    }
}
```

#### Step 4: 接口层 (apex-interfaces)

```java
// Handler
@Component
@RequiredArgsConstructor
public class UserHandler extends BaseHandler {
    
    private final UserService userService;
    
    public Mono<ServerResponse> getUser(ServerRequest request) {
        Long userId = Long.valueOf(request.pathVariable("userId"));
        
        return userService.getUserById(userId)
                .flatMap(user -> ServerResponse
                        .ok()
                        .bodyValue(Result.success(user)));
    }
}

// Router
@Configuration
public class UserRouter {
    
    @Bean
    public RouterFunction<ServerResponse> userRoutes(UserHandler handler) {
        return RouterFunctions.route()
                .GET("/api/v1/users/{userId}", handler::getUser)
                .build();
    }
}
```

---

## 五、常用开发任务

### 5.1 新增接口

```bash
# 1. 创建 Request DTO
# apex-interfaces/src/main/java/com/apex/scaffold/interfaces/dto/user/CreateUserRequest.java

# 2. 在 Handler 中添加方法
# UserHandler.java

# 3. 在 Router 中注册路由
# UserRouter.java

# 4. 编写集成测试
# UserHandlerIntegrationTest.java

# 5. 运行测试
mvnd test -Dtest=UserHandlerIntegrationTest
```

### 5.2 数据库迁移

```bash
# 1. 创建迁移脚本
# apex-interfaces/src/main/resources/db/migration/V27__add_user_email.sql

CREATE TABLE IF NOT EXISTS sys_user_email (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    email VARCHAR(255) NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

# 2. 启动应用,Flyway 自动执行迁移
mvnd spring-boot:run -pl apex-interfaces
```

### 5.3 代码生成

```bash
# 使用代码生成器快速生成 CRUD 代码
./scripts/codegen/generate.sh \
    -i scripts/codegen/examples/product.json \
    -o ./generated \
    -v 3

# 生成内容:
# - Domain Entity + Repository 接口
# - PO + Mapper + Converter + RepositoryImpl
# - Request/Response DTO
# - Service + Assembler
# - Handler + Router
# - 集成测试
```

📖 **详细指南**: [代码生成器使用](../04-development/codegen.md)

---

## 六、测试

### 6.1 单元测试

```java
@ExtendWith(MockitoExtension.class)
class UserServiceTest {
    
    @Mock
    private UserRepository userRepository;
    
    @InjectMocks
    private UserService userService;
    
    @Test
    void testGetUser() {
        User user = User.builder()
                .id(1L)
                .username("test")
                .build();
        
        when(userRepository.findById(1L))
                .thenReturn(Mono.just(user));
        
        StepVerifier.create(userService.getUserById(1L))
                .assertNext(dto -> {
                    assertThat(dto.getId()).isEqualTo(1L);
                    assertThat(dto.getUsername()).isEqualTo("test");
                })
                .verifyComplete();
    }
}
```

### 6.2 集成测试

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
        UserDTO user = UserDTO.builder()
                .id(1L)
                .username("test")
                .build();
        
        when(userService.getUserById(1L))
                .thenReturn(Mono.just(user));
        
        webTestClient.get()
                .uri("/api/v1/users/1")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.code").isEqualTo(0)
                .jsonPath("$.data.username").isEqualTo("test");
    }
}
```

### 6.3 运行测试

```bash
# 单元测试
mvnd test -P basic

# 集成测试
mvnd verify -P standard

# 测试覆盖率
mvnd verify -P jacoco

# 单个测试类
mvnd test -Dtest=UserServiceTest
```

---

## 七、代码规范

### 7.1 命名规范

| 类型 | 规范 | 示例 |
|------|------|------|
| Handler | `*Handler` | `UserHandler` |
| Service | `*Service` | `UserService` |
| Repository | `*Repository` | `UserRepository` |
| DTO | `*DTO` / `*Request` / `*Response` | `UserDTO`, `CreateUserRequest` |
| PO | `*PO` | `UserPO` |
| Mapper | `*Mapper` | `UserMapper` |
| Converter | `*Converter` | `UserConverter` |

### 7.2 Lombok 使用

```java
// DTO/VO/Request/Response
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDTO {
    private Long id;
    private String username;
}

// Domain Entity
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {
    private Long id;
    private String username;
    
    // 领域方法
    public void activate() {
        // ...
    }
}

// Service/Component
@Service
@Slf4j
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
}
```

📖 **详细指南**: [Lombok 使用指南](../04-development/lombok-guide.md)

### 7.3 Javadoc 规范

```java
/**
 * 用户管理服务
 *
 * <p>提供用户的 CRUD 操作、状态管理等核心功能。
 *
 * <h2>主要功能</h2>
 * <ul>
 *   <li>用户查询：支持按 ID、用户名查询</li>
 *   <li>用户创建：校验用户名唯一性</li>
 *   <li>状态管理：激活、禁用用户</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.0.0
 */
@Service
@RequiredArgsConstructor
public class UserService {
    
    /**
     * 根据 ID 获取用户
     *
     * <p>从数据库查询用户详情,返回 DTO 对象。
     *
     * @param id 用户 ID,必须大于 0
     * @return 用户 DTO 的 Mono,用户不存在时返回空 Mono
     * @since 1.0.0
     */
    public Mono<UserDTO> getUserById(Long id) {
        // ...
    }
}
```

📖 **详细指南**: [Javadoc 编写指南](../04-development/javadoc-guide.md)

---

## 八、常见问题

### Q1: 如何处理阻塞操作?

A: 使用 `Schedulers.boundedElastic()` 调度到专用线程池:

```java
@Override
public Mono<User> findById(Long id) {
    return Mono.fromCallable(() -> userMapper.selectById(id))
            .subscribeOn(Schedulers.boundedElastic());
}
```

### Q2: 如何调试响应式代码?

A: 使用 `doOnXxx` 系列方法:

```java
public Mono<User> getUser(Long id) {
    return userRepository.findById(id)
            .doOnSubscribe(s -> log.info("开始查询: {}", id))
            .doOnNext(user -> log.info("查询成功: {}", user))
            .doOnError(e -> log.error("查询失败", e));
}
```

### Q3: 如何在响应式链中执行事务?

A: 使用 `@Transactional` 注解:

```java
@Service
@RequiredArgsConstructor
public class OrderService {
    
    @Transactional
    public Mono<Order> createOrder(CreateOrderRequest request) {
        return orderRepository.save(buildOrder(request))
                .flatMap(order -> productService.deductStock(order.getProductId()))
                .then(Mono.just(order));
    }
}
```

---

## 九、工具脚本

### 9.1 常用脚本

| 脚本 | 用途 |
|------|------|
| `scripts/ci/check-doc-consistency.sh` | 文档一致性检查 ⭐ 新增 |
| `scripts/build/build-all.sh` | 完整构建 |
| `scripts/deploy/deploy-k8s.sh` | K8s 部署 |
| `scripts/codegen/generate.sh` | 代码生成 |
| `scripts/test/test-workflow-api.sh` | 工作流 API 测试 |

### 9.2 Maven 命令

```bash
# 编译
mvnd clean compile -DskipTests

# 测试
mvnd clean test -P basic

# 打包
mvnd clean package -DskipTests

# 格式化
mvnd spotless:apply

# 静态检查
mvnd pmd:check

# 启动应用
mvnd spring-boot:run -pl apex-interfaces
```

---

## 十、学习路径

### 10.1 新手入门 (1-2 周)

1. ✅ 阅读 [快速上手](../00-getting-started/quickstart.md)
2. ✅ 阅读 [DDD 四层架构详解](../architecture/01-logical-architecture-detail.md)
3. ✅ 阅读 [WebFlux 响应式编程指南](../architecture/reactive-programming-guide.md)
4. ✅ 运行示例项目 `apex-example`
5. ✅ 完成第一个 CRUD 接口开发

### 10.2 进阶学习 (2-4 周)

1. ✅ 阅读 [策略模式实现指南](../architecture/strategy-pattern-guide.md)
2. ✅ 阅读 [后端开发规范](../04-development/backend-development-standards.md)
3. ✅ 学习工作流引擎使用
4. ✅ 掌握 Sentinel 限流熔断
5. ✅ 掌握多租户数据隔离

### 10.3 高级主题 (持续学习)

1. ✅ 微服务治理与服务网格
2. ✅ 性能优化与调优
3. ✅ 生产环境部署与运维
4. ✅ 监控告警体系搭建
5. ✅ 架构演进与重构

---

## 十一、获取帮助

### 11.1 文档资源

- 📖 [在线文档](https://docs.apex-scaffold.com)
- 📁 [项目文档目录](../)
- 📊 [架构图集](../02-architecture/)

### 11.2 社区支持

- 💬 技术讨论群
- 📧 邮件列表: dev@apex-scaffold.com
- 🐛 问题反馈: GitHub Issues

---

## 十二、版本历史

| 版本 | 日期 | 主要变更 |
|------|------|----------|
| 2.0 | 2026-09-10 | 新增响应式编程指南、策略模式指南、CI自动化检查 |
| 1.0 | 2026-09-04 | 初始版本,基于项目实践整理 |

---

**版权声明**: © 2026 Apex Team  
**许可协议**: 内部文档,仅供团队使用  
**维护者**: Apex 开发团队
> 配套图表：[53 质量门禁流水线](../diagrams/53_quality_gate_pipeline.svg)

