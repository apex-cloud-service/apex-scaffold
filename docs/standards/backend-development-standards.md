# Apex Scaffold 后端开发规范

**版本**: v1.0.0\
**适用范围**: Apex Scaffold 后端多模块 Maven 工程\
**技术栈**: Java 17、Spring Boot 3.5.15、Spring WebFlux、Project Reactor、DDD、MyBatis-Plus、MySQL、Redis\
**最后更新**: 2026-09-03

***

## 目录

1. [适用范围与基本原则](#1-适用范围与基本原则)
2. [项目架构](#2-项目架构)
3. [编码规范](#3-编码规范)
4. [响应式编程规范](#4-响应式编程规范)
5. [数据访问规范](#5-数据访问规范)
6. [API 设计规范](#6-api-设计规范)
7. [安全规范](#7-安全规范)
8. [测试规范](#8-测试规范)
9. [性能与稳定性](#9-性能与稳定性)
10. [提交与审查](#10-提交与审查)
11. [开发检查清单](#11-开发检查清单)

***

## 1. 适用范围与基本原则

本规范适用于 `apex-interfaces`、`apex-application`、`apex-domain`、`apex-infrastructure` 和 `apex-common` 模块中的新增代码、修改代码和测试代码。

基本原则：

- 业务规则优先放在领域层，接口层只负责协议适配。

- 响应式链路保持非阻塞，JDBC 和同步 SDK 必须隔离到专用线程池。

- 领域模型与数据库对象分离，禁止用 PO 代替 Entity 承载业务行为。

- 所有外部输入都要校验，所有敏感信息都要脱敏。

- 代码格式、测试和提交信息必须符合仓库已有工具链与 `CONTRIBUTING.md` 约定。

项目基础命令：

```bash
mvnd clean test
mvnd spotless:check
mvnd spotless:apply
```

***

## 2. 项目架构

### 2.1 DDD 四层结构

```text
apex-interfaces/
├── handler/
├── router/
├── filter/
└── config/

apex-application/
├── service/
├── dto/
└── assembler/

apex-domain/
├── model/
├── repository/
├── service/
├── event/
└── gateway/

apex-infrastructure/
├── persistence/
│   ├── mapper/
│   ├── po/
│   └── repository/
└── gateway/
```

依赖方向：

```text
interfaces → application → domain ← infrastructure
                              ↑
                              └── common
```

### 2.2 模块职责

| 模块                    | 负责内容                                         | 约束                          |
| --------------------- | -------------------------------------------- | --------------------------- |
| `apex-interfaces`     | RouterFunction、Handler、WebFilter、Web 配置、协议响应 | 不直接访问 Mapper 或数据库，不承载核心业务规则 |
| `apex-application`    | 用例编排、事务边界、DTO 和 Assembler                    | 不直接依赖具体持久化实现                |
| `apex-domain`         | Entity、值对象、聚合、Repository 接口、领域服务、领域事件        | 不依赖具体基础设施技术                 |
| `apex-infrastructure` | Mapper、PO、Repository 实现、Redis、第三方网关          | 实现接口，不向上层泄漏技术细节             |
| `apex-common`         | Result、异常、上下文、工具、公共注解                        | 只放跨模块稳定的公共能力                |

新增功能时先确定聚合和用例，再按 `Handler → Application Service → Domain → Repository/Gateway` 实现。禁止在 Handler 中直接拼接 SQL、操作 Redis 或调用第三方 SDK。

***

## 3. 编码规范

### 3.1 命名

| 对象                  | 规则                             | 示例                       |
| ------------------- | ------------------------------ | ------------------------ |
| Handler             | 名词 + `Handler`                 | `AuthHandler`            |
| Application Service | 名词 + `Service`                 | `AuthService`            |
| Repository 接口       | 名词 + `Repository`              | `UserRepository`         |
| Repository 实现       | 名词 + `RepositoryImpl`          | `UserRepositoryImpl`     |
| Mapper              | 名词 + `Mapper`                  | `UserMapper`             |
| 领域实体                | 名词 + `Entity`                  | `UserEntity`             |
| 持久化对象               | 名词 + `PO`                      | `UserPO`                 |
| 请求与响应               | 名词 + `Request` / `Response`    | `LoginRequest`           |
| 转换器                 | 名词 + `Assembler` / `Converter` | `UserAssembler`          |
| 配置                  | 名词 + `Config` / `Properties`   | `JwtConfig`              |
| 常量                  | 全大写，下划线分隔                      | `TOKEN_BLACKLIST_TTL_MS` |

方法名要表达业务意图，例如 `login`、`refreshToken`、`findByPhone`、`deleteById`。避免使用 `process`、`handle`、`doSomething` 等无法说明行为的名称。

### 3.2 Lombok

项目级约定由 `lombok.config` 统一控制：

- DTO、VO、Request、Response：`@Data`、`@Builder`，按需要增加构造器。

- Domain Entity：`@Getter`、`@Builder`，通过领域方法变更状态，不暴露通用 setter。

- Service、Component：`@Slf4j`、`@RequiredArgsConstructor`。

- PO：`@Data`、`@TableName`，配合 `@TableId`、`@TableLogic`、`@Version` 等持久化注解。

- 禁止 `@SneakyThrows`、`val`、`var` 和 `@Synchronized`。

- 不重复手写 Lombok 已生成的 getter、setter、构造器和日志字段。

```java
@Getter
@Builder
public class UserEntity {
    private Long id;
    private UserStatus status;

    public void activate() {
        this.status = UserStatus.ACTIVE;
    }
}
```

### 3.3 注释与格式

公开类和复杂公开方法使用 Javadoc，说明职责、参数、返回值和异常。注释解释设计原因或业务约束，不重复描述代码字面含义。

项目使用 Spotless 和 Palantir Java Format：

- 使用 4 个空格缩进，不使用 Tab。

- 保持导入整洁，删除未使用导入。

- 修改 Java 文件后执行 `mvnd spotless:apply`，提交前执行 `mvnd spotless:check`。

### 3.4 异常与日志

- 可预期业务失败使用项目统一业务异常和错误码。

- 系统异常保留 cause，交由全局异常处理转换为统一响应。

- 禁止吞异常、空 catch 和直接返回模糊的 `null`。

- 使用日志占位符，不使用字符串拼接。

- `ERROR` 记录系统故障及堆栈，`WARN` 记录业务拒绝和降级，`INFO` 记录关键业务节点，`DEBUG` 记录诊断信息。

- 手机号、身份证、Token、Cookie、密码和第三方凭证禁止明文写入日志。

```java
log.info("User login, phone: {}", SensitiveDataUtil.maskPhone(phone));
log.error("External service call failed, traceId: {}", traceId, exception);
```

***

## 4. 响应式编程规范

### 4.1 返回类型

| 场景        | 返回类型                    |
| --------- | ----------------------- |
| 0 或 1 个结果 | `Mono<T>`               |
| 0 到多个结果   | `Flux<T>`               |
| 无返回值异步操作  | `Mono<Void>`            |
| 分页结果      | `Mono<PageResponse<T>>` |

仓储查询不到数据时返回 `Mono.empty()`，由应用服务决定是否转换为业务异常。

### 4.2 操作符选择

- `map`：同步的一对一转换。

- `flatMap`：调用另一个 `Mono` 或异步服务。

- `flatMapMany`：从一个 `Mono` 展开为 `Flux`。

- `zip` / `zipWith`：组合相互独立的异步结果。

- `switchIfEmpty`：处理查询为空的分支。

- `onErrorMap`：转换异常类型。

- `onErrorResume`：提供降级或替代响应。

- `doOnSuccess`、`doOnError`：记录指标和日志，不改变主链路语义。

```java
public Mono<UserDTO> getUserById(Long userId) {
    return userRepository.findById(userId)
        .switchIfEmpty(Mono.error(new BusinessException(ResultCode.USER_NOT_FOUND)))
        .map(userAssembler::toDTO);
}
```

### 4.3 禁止阻塞响应式线程

以下操作必须封装在 `Mono.fromCallable` 或 `Mono.fromRunnable` 中，并通过 `subscribeOn` 切换到 `BlockingIoSchedulers.jdbc()` 或 `BlockingIoSchedulers.io()`：

- MyBatis-Plus 和 JDBC 调用。

- 同步 HTTP 客户端和第三方 SDK。

- 文件读写和压缩解压。

- 调用 `.block()`、`.blockFirst()`、`.blockLast()`。

生产业务链路禁止调用上述 `block` 方法。线程切换应尽可能靠近阻塞调用封装，避免把整个业务链路误放入阻塞线程池。

```java
public Mono<UserPO> findUser(Long userId) {
    return Mono.fromCallable(() -> userMapper.selectById(userId))
        .subscribeOn(BlockingIoSchedulers.jdbc());
}
```

### 4.4 订阅、取消与重试

- 业务方法返回 Publisher，不在 Service 内随意 `subscribe()`。

- 由 WebFlux、消息监听器或任务框架负责最终订阅。

- 重试只用于幂等且可恢复的临时故障。

- 重试必须限定次数、超时时间和异常类型，并使用固定延迟或指数退避。

- 不对参数错误、权限错误、业务冲突和不可恢复异常重试。

```java
return webClient.get()
    .retrieve()
    .bodyToMono(String.class)
    .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
        .maxBackoff(Duration.ofSeconds(10)));
```

***

## 5. 数据访问规范

### 5.1 Entity、PO 与 DTO

- Entity 位于领域层，表达业务状态和行为，不包含 MyBatis-Plus 注解。

- PO 位于基础设施层，只表达数据库字段和映射关系。

- DTO 位于应用层或接口契约边界，不直接作为领域实体持久化。

- 使用 Assembler 或 Converter 完成 Entity、PO、DTO 之间的转换。

### 5.2 Mapper 与 Repository

Mapper 继承 `BaseMapper<PO>`，自定义 SQL 只放在基础设施层。Repository 接口放在领域层，返回领域对象和响应式类型。

```java
public interface UserRepository {
    Mono<UserEntity> findById(Long id);
    Mono<UserEntity> findByPhone(String phone);
    Flux<UserEntity> findByIds(List<Long> ids);
    Mono<Void> save(UserEntity entity);
}
```

```java
@Repository
@RequiredArgsConstructor
public class UserRepositoryImpl implements UserRepository {
    private final UserMapper userMapper;

    @Override
    public Mono<UserEntity> findById(Long id) {
        return Mono.fromCallable(() -> userMapper.selectById(id))
            .subscribeOn(BlockingIoSchedulers.jdbc())
            .map(UserConverter::toDomain);
    }
}
```

### 5.3 SQL 与事务

- 明确列名，避免无必要的 `SELECT *`。

- 查询条件优先使用索引字段，模糊查询避免前置 `%`。

- 分页、排序和批量操作必须限制规模。

- SQL 参数使用 MyBatis 参数绑定，禁止字符串拼接。

- `SELECT ... FOR UPDATE` 必须在明确的事务边界内使用。

- 事务边界通常放在应用服务，事务内避免远程调用、消息发送和长时间计算。

- 数据库变更使用 Flyway 迁移脚本，不直接修改生产表结构。

- 逻辑删除、乐观锁和租户字段必须遵循已有表结构和拦截器约定。

```java
@Mapper
public interface UserMapper extends BaseMapper<UserPO> {
    @Select("SELECT * FROM sys_user WHERE id = #{id} FOR UPDATE")
    UserPO selectByIdForUpdate(@Param("id") Long id);
}
```

***

## 6. API 设计规范

### 6.1 路由与 Handler

接口层使用函数式 `RouterFunction` 和 WebFlux `Handler`。业务接口使用 `/api/v1`，管理接口使用 `/admin-api/v1`。

```java
@Bean
public RouterFunction<ServerResponse> authRoutes(AuthHandler handler) {
    return RouterFunctions.route()
        .POST("/api/v1/auth/login", handler::handleLogin)
        .POST("/api/v1/auth/refresh", handler::handleRefreshToken)
        .POST("/api/v1/auth/logout", handler::handleLogout)
        .build();
}
```

Handler 负责读取请求、参数校验、调用应用服务和封装响应，不实现领域规则。

```java
public Mono<ServerResponse> handleLogin(ServerRequest request) {
    return requestHandlerUtil.extractAndValidate(request, LoginRequest.class)
        .flatMap(authService::login)
        .flatMap(response -> ServerResponse.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Result.success(response)))
        .onErrorResume(this::handleError);
}
```

### 6.2 REST 与统一响应

- URL 使用名词和版本前缀，资源层级清晰。

- GET 查询资源，POST 创建或触发动作，PUT 更新，DELETE 删除。

- 分页参数统一使用 `page` 和 `size`，并设置最大 `size`。

- 响应统一使用 `Result<T>` 或项目现有分页响应类型。

- HTTP 状态码和业务错误码同时表达协议状态与业务原因。

```java
public class Result<T> {
    private Integer code;
    private String message;
    private T data;
    private Long timestamp;
}
```

### 6.3 参数校验与错误处理

请求 DTO 使用 Bean Validation 注解，例如 `@NotBlank`、`@Size`、`@Pattern`。校验失败由统一异常处理转换，不能在每个 Handler 中重复拼接错误格式。

错误码应稳定、唯一、可检索。不要把数据库异常、堆栈信息或内部服务地址直接返回给客户端。

### 6.4 OpenAPI 与可观测性

新增或修改接口时同步维护 SpringDoc/OpenAPI 注解、请求字段、响应字段、权限码和错误码。关键请求应通过已有日志、TraceId、RequestId、业务指标和 API 调用日志能力实现可追踪。

***

## 7. 安全规范

### 7.1 认证与会话

- 认证使用项目现有 JWT 机制，区分 access token 和 refresh token。

- Token 只从受信任的请求头或项目约定位置读取。

- 退出登录、主动失效和刷新场景要处理 Token 黑名单及 TTL。

- JWT 密钥、数据库密码、Redis 密码、第三方凭证只能来自安全配置或环境变量。

- 登录失败次数、验证码和刷新接口必须结合 Redis 与 Sentinel 做保护。

### 7.2 授权

- 在接口或应用服务边界使用 `@RequirePermission`、`@RequireRole` 等项目已有权限机制。

- 管理接口默认需要认证和权限校验。

- 数据权限通过 `@DataScope` 等已有机制执行，不在业务代码中手工拼接不一致的权限条件。

- 多租户查询和写入必须带租户上下文，禁止跨租户读取或修改数据。

```java
@RequirePermission("system:user:add")
public Mono<Result<Void>> addUser(CreateUserRequest request) {
    return userService.addUser(request);
}
```

### 7.3 输入、输出与日志安全

- 所有外部参数都做类型、长度、格式和范围校验。

- SQL 使用参数绑定，文件上传校验大小、类型、名称和存储路径。

- 密码、Token 和私钥不得明文存储或输出。

- 手机号、身份证、邮箱等敏感字段按场景脱敏或加密。

- 错误响应只返回客户端需要的信息，详细堆栈写入受控日志。

***

## 8. 测试规范

### 8.1 测试分层

- 领域层：覆盖聚合状态转换、值对象校验和领域规则。

- 应用层：使用 Mockito 模拟 Repository、Gateway 和 Token 服务，覆盖成功、空结果、业务异常和外部失败。

- 接口层：使用 `WebTestClient` 验证路由、参数校验、认证、权限和统一响应。

- 基础设施层：针对 Mapper、Flyway、Redis 和外部集成编写集成测试。

### 8.2 响应式测试

使用 `StepVerifier` 验证元素、完成信号、错误类型和空结果，不用生产代码中的 `block()` 替代断言。

```java
StepVerifier.create(authService.login(request))
    .assertNext(response -> assertNotNull(response.getAccessToken()))
    .verifyComplete();
```

至少覆盖：

- `Mono.just` 和 `Mono.empty` 两种查询结果。

- 业务异常和系统异常。

- 重试耗尽、超时和降级分支。

- `Flux` 的数量、顺序、背压和取消行为。

### 8.3 覆盖率与验证命令

目标值：整体不低于 60%，核心业务不低于 80%，领域层不低于 85%，工具类不低于 90%。

```bash
mvnd clean test jacoco:report
mvnd -pl apex-interfaces -am compile -DskipTests
```

测试不得依赖真实生产凭证、真实用户数据或不可控的外部服务。

***

## 9. 性能与稳定性

- Redis 缓存必须定义 key 规则、TTL、空值策略和失效策略。

- Caffeine 只缓存适合本地副本的数据，限制最大容量和过期时间。

- 避免缓存穿透、击穿和无限增长；更新数据时同步考虑缓存一致性。

- 大批量读取使用分页或批量接口，使用 `buffer`、`limitRate` 控制内存和下游压力。

- `flatMap` 并发度按下游承载能力设置，不能无限并发。

- 外部调用必须设置连接超时、读取超时、重试上限和降级策略。

- 使用 Sentinel 对登录、刷新、管理查询和外部依赖设置限流、熔断和降级。

- 关键链路记录耗时、成功率、错误率和 traceId，避免在高频循环中打印 INFO 日志。

***

## 10. 提交与审查

### 10.1 分支

- `feature/*`：新功能。

- `bugfix/*`：一般缺陷修复。

- `hotfix/*`：紧急线上修复。

- `refactor/*`：重构。

使用特性分支开发，完成测试和审查后合并到开发分支，遵循 `CONTRIBUTING.md` 的 PR 流程。

### 10.2 Conventional Commits

```text
<type>(<scope>): <subject>
```

常用类型：`feat`、`fix`、`docs`、`style`、`refactor`、`test`、`chore`。

```text
feat(auth): add SMS login strategy
fix(user): handle empty repository result
```

提交前至少执行：

```bash
mvnd spotless:check
mvnd clean test
```

### 10.3 代码审查重点

- 分层依赖是否正确，领域规则是否泄漏到接口层。

- 是否存在响应式线程上的 JDBC、文件或同步 SDK 调用。

- 是否正确处理 `Mono.empty()`、异常和取消信号。

- 参数、权限、租户和数据范围是否完整。

- SQL、事务、缓存和重试是否具备边界。

- 日志是否脱敏，配置是否泄露密钥。

- 测试是否覆盖新增分支并通过格式检查。

***

## 11. 开发检查清单

### 新增业务功能

- [ ] 明确聚合、用例、领域规则和事务边界。

- [ ] 按四层结构放置 Handler、Service、Entity、Repository 和基础设施实现。

- [ ] Entity、PO、DTO 已分离并完成显式转换。

- [ ] 所有阻塞调用已隔离到专用线程池。

- [ ] API 已添加路由、校验、权限、OpenAPI 和统一响应。

- [ ] 已考虑租户、数据权限、日志脱敏和幂等性。

- [ ] 已补充单元测试、响应式测试或集成测试。

### 提交代码

- [ ] `mvnd spotless:check` 通过。

- [ ] `mvnd clean test` 通过。

- [ ] 没有硬编码密钥、Token 或环境地址。

- [ ] 提交信息符合 Conventional Commits。

- [ ] PR 已说明变更范围、验证方式和已知风险。

***

## 参考资料

- [贡献指南](../../CONTRIBUTING.md)

- [项目 README](../../README.md)

- [Spring WebFlux 官方文档](https://docs.spring.io/spring-framework/reference/web/webflux.html)

- [Project Reactor 参考指南](https://projectreactor.io/docs/core/release/reference/)

- [MyBatis-Plus 官方文档](https://baomidou.com/)

- [Conventional Commits](https://www.conventionalcommits.org/)

**文档维护**: Apex Team\
**版本历史**: v1.0.0（2026-09-03，初始版本）
