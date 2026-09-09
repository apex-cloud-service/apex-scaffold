# AGENTS.md — Apex Scaffold（后端）

本文件为 AI 编码 Agents 在本仓库工作时的**行为准则与工程约定**。修改代码前请先通读，违反以下约定会导致构建失败或代码风格不一致。

## 1. 项目概览

**Apex Scaffold** 是面向微信 App 场景的企业级开发脚手架（Java 17 / Spring Boot 3.5.15 / WebFlux 响应式 / 多模块 Maven 工程），遵循 **DDD 四层架构**。

- 版本：`1.2.0`（见根 `pom.xml`）

- **JDK**：**17.0.12-oracle**（通过 SDKMAN 管理，`.sdkmanrc` 已锁定）

  ```bash
  # 激活项目 JDK 环境
  cd /Users/work/apex-scaffold && sdk env

  # 验证 JDK 版本
  java -version
  # 输出应为：java version "17.0.12" 2024-07-16 LTS
  ```

- **构建工具**：**mvnd 2.0.0-rc-3**（Maven Daemon，构建速度优于传统 mvn）

  ```bash
  # 使用 mvnd 编译（推荐）
  mvnd clean compile -DskipTests

  # 使用 mvnd 运行测试
  mvnd clean test -P basic

  # 使用 mvnd 打包
  mvnd clean package -DskipTests

  # 注意：CI 环境使用 Maven 3.9+ 构建，本地开发强烈推荐 mvnd
  ```

### 技术栈版本清单

| 分类 | 组件 | 版本 |
|------|------|------|
| **核心框架** | Spring Boot | 3.5.15 |
| | Spring Cloud | 2025.0.0 |
| | Spring Cloud Alibaba | 2025.0.0.0 |
| | Spring Security | 6.5.0 |
| | Spring WebFlux | 响应式非阻塞 Web 框架 |
| | Reactor Core | 3.6.13 |
| **数据访问** | MyBatis-Plus | 3.5.10.1 |
| | MySQL Connector | 8.4.0 |
| | Druid | 1.2.25 |
| | Flyway | 10.15.0 |
| **缓存与消息** | Redisson | 3.36.0 |
| | Caffeine | 3.1.8 |
| | RocketMQ Spring | 2.3.3 |
| **微服务治理** | Sentinel | 1.8.9 |
| | Resilience4j | 2.2.0 |
| **搜索与存储** | Meilisearch | 0.17.1 |
| | MinIO | 8.6.0 |
| | 火山引擎 TOS | 2.6.1 |
| **监控与追踪** | Micrometer | 1.14.1 |
| | SpringDoc OpenAPI | 2.8.1 |
| | Zipkin | 链路追踪 |
| **代码生成** | Lombok | 1.18.36 |
| | MapStruct | 1.5.5.Final |
| | ArchUnit | 1.3.0（架构约束测试） |

### 模块构成

| 模块 | 职责 | 关键约定 |
|------|------|----------|
| `apex-common` | 公共模块：工具类、异常体系、统一响应 `Result<T>`、`ResultCode`、注解、常量、上下文、状态机、SPI | 无业务依赖，任何模块可引用 |
| `apex-domain` | 领域层：聚合根、领域事件、仓储接口（Repository）、领域服务、网关端口（Gateway/Port）、枚举 | **不依赖任何上层模块** |
| `apex-application` | 应用层：用例编排、事务边界、DTO 转换（Assembler）、服务 | 依赖 domain |
| `apex-infrastructure` | 基础设施层：仓储实现（PO + Mapper + MapStruct Converter + RepositoryImpl）、缓存、MQ、外部服务 | 依赖 domain |
| `apex-interfaces` | 接口层：**WebFlux 函数式端点**（Router + Handler）、安全、过滤器、AOP 切面、配置、i18n、Flyway 迁移（`db/migration`） | 启动模块，主入口 `Application.java` |
| `apex-example` | 示例模块：完整 CRUD 示例 + 集成测试 | 可参考的最佳实践样例 |
| `apex-generator` | DDD 四层代码生成器（独立工具，offline 可构建） | FreeMarker 模板，不参与运行时 |
| `apex-license-spring-boot-starter` | 商业授权 Starter | 授权校验相关，改动需谨慎 |
| `apex-license-tools` | 卖方内部工具模块 | 发行源码/发布包必须剥离 |

### 依赖方向（严格遵守）

```
apex-interfaces → apex-application → apex-domain ← apex-infrastructure
                                            ↑
                                     apex-common（被全部依赖）
```

- 依赖只能从上往下/从外向内，**禁止反向依赖**：domain 不得引用 application/interfaces。
- `apex-common` 是横向公共模块，不计入分层，提供跨模块稳定能力。
- 架构规则由 `apex-infrastructure/src/test/java/com/apex/scaffold/arch/TenantIsolationArchTest.java`（ArchUnit）在测试期强制校验。

## 2. 核心架构约定

### 2.1 DDD 四层架构

Apex Scaffold 采用 **DDD（领域驱动设计）四层架构**，结合 Ports and Adapters 模式，实现业务规则与技术实现的解耦。详见 [DDD 四层架构详解](docs/architecture/01-logical-architecture-detail.md)。

| 层次 | Maven 模块 | 核心职责 | 允许的依赖 |
|------|------------|----------|------------|
| 接口层 | `apex-interfaces` | 协议适配、请求校验、安全入口、响应转换、OpenAPI | 应用层、领域层 |
| 应用层 | `apex-application` | 用例编排、事务边界协调、DTO 转换、跨聚合协作 | 领域层、公共模块 |
| 领域层 | `apex-domain` | 聚合根、值对象、领域服务、Repository 接口、领域事件 | 公共模块（不依赖基础设施技术） |
| 基础设施层 | `apex-infrastructure` | RepositoryImpl、Mapper、PO、Cache、MQ、Storage、外部服务适配器 | 领域层（实现接口）、公共模块 |

### 2.2 统一响应

所有接口返回 `Result<T>`：`{code, msg, data, traceId}`，业务成功 `code = 0`。错误码定义在 [ResultCode.java](apex-common/src/main/java/com/apex/scaffold/common/result/ResultCode.java)（枚举 + i18n messageKey，禁止散落魔法数字；新错误码按分类段追加：认证 1001~~1022、流量 2001~~2006、基础设施 3001~~3006、业务 4xxx、防重放 5xxx）。

### 2.3 WebFlux 函数式端点（非注解 Controller）

- 路由集中在 [ApiRouter.java](apex-interfaces/src/main/java/com/apex/scaffold/interfaces/router/ApiRouter.java)，每个 Bean 返回 `RouterFunction<ServerResponse>`，用 `RouterFunctions.route()` + `.path()` 按资源分组。
- 业务逻辑在 `handler` 包（如 `UserHandler`），通过 `ServerRequest` 取参、返回 `ServerResponse`。
- 分组前缀：
  - `/api/v1/**` — 业务（C 端 / 开放接口）
  - `/admin-api/v1/**` — 管理后台（配合 `@RequirePermission("system:xxx:*")` 权限点）
- 新增接口 = 新增 Handler 方法 + 在 ApiRouter 注册对应路由 Bean。

### 2.4 响应式编程模型（WebFlux + Reactor）

#### 核心原则

1. **非阻塞**：所有 I/O 操作返回 `Mono`/`Flux`，禁止在事件循环线程阻塞
2. **背压支持**：消费者控制生产速度
3. **事件驱动**：声明式数据流转换

#### 阻塞 IO 隔离（重要！）

JDBC、文件 IO、同步 HTTP 客户端属于阻塞操作，**必须**调度到专用线程池（`BlockingIoSchedulers`，见 apex-common scheduler 包）：

```java
// ✅ 正确：隔离 JDBC 到 boundedElastic 线程池
@Override
public Mono<User> findById(Long id) {
    return Mono.fromCallable(() -> userMapper.selectById(id))
        .subscribeOn(Schedulers.boundedElastic());
}

// ❌ 错误：直接在响应式链中阻塞，阻塞 event-loop 线程
@Override
public Mono<User> findById(Long id) {
    User user = userMapper.selectById(id);
    return Mono.just(user);
}
```

**阻塞操作清单**：JDBC（MyBatis-Plus Mapper 调用）、Redis 同步客户端、文件读写、同步 HTTP 客户端、`Thread.sleep`。

**线程池配置**（`application.yml`）：
```yaml
apex:
  blocking-io:
    jdbc:
      threads: 32    # JDBC 线程池大小
    cache:
      threads: 4     # 缓存操作线程池
    storage:
      threads: 8     # 文件存储线程池
```

#### 常用响应式操作

```java
// 并行查询并合并结果
Mono<UserWithRoles> getUserWithRoles(Long userId) {
    Mono<User> userMono = userRepository.findById(userId);
    Mono<List<Role>> rolesMono = roleRepository.findByUserId(userId);
    return Mono.zip(userMono, rolesMono)
        .map(tuple -> new UserWithRoles(tuple.getT1(), tuple.getT2()));
}

// 超时控制 + 降级
Mono<String> callRemoteApi() {
    return webClient.get()
        .uri("/api/data")
        .retrieve()
        .bodyToMono(String.class)
        .timeout(Duration.ofSeconds(10))
        .onErrorResume(TimeoutException.class, e -> Mono.just("fallback"));
}
```

### 2.5 分层开发步骤（新增业务模块的标准路径）

1. `apex-domain`：定义聚合根实体、Repository 接口、领域事件、枚举；
2. `apex-infrastructure`：PO（`@TableName`）、Mapper、MapStruct Converter、`RepositoryImpl`（调度到阻塞线程池）；
3. `apex-application`：Assembler（DTO ↔ 实体）、应用 Service（用例编排 + 事务）；
4. `apex-interfaces`：Handler + ApiRouter 注册 + `@RequirePermission` 权限点；
5. 新增 Flyway 迁移 `V{n}__*.sql`（**只追加，不改已执行的迁移脚本**；当前 V1~V22）。

### 2.6 代码生成器

`apex-generator` 可按表结构 JSON 一键生成整套 CRUD 四层代码 + Vue3 管理页面：

```bash
./scripts/codegen/generate.sh -i scripts/codegen/examples/product.json -o ./generated -v 3
# 同时生成前端页面：加 -f /Users/work/apex-admin-web
```

生成产物含：domain/repository、PO/Mapper/Converter/RepositoryImpl、Request/DTO、Service、Handler、路由片段、Flyway 片段、ServiceTest、前端 api/views。用法详见 [docs/CODEGEN.md](docs/CODEGEN.md)。

## 3. 核心能力

### 3.1 认证授权

- Spring Security + JWT（Access Token + Refresh Token），支持无感续期
- RBAC 基于角色的权限控制：`@RequirePermission` / `@RequireRole`
- 数据权限：`@DataScope` 按部门/角色过滤数据
- 多租户隔离：`TenantContext` / `@TenantAware` + MybatisPlus `TenantLineInnerInterceptor`
- 防重放：`@AntiReplay`（时间戳 + nonce）
- 写接口幂等：`@IdempotentApi`
- 验证码、短信限流、IP 限流、登录保护

### 3.2 系统管理

用户/角色/菜单/部门/字典/配置/定时任务（Quartz）/通知消息（SSE 推送）/在线用户管理。

### 3.3 工作流引擎

自研轻量工作流引擎，支持流程定义（BPMN 建模）、流程实例（启动/挂起/终止）、任务审批（待办/已办/审批操作）。详见 [docs/features/workflow-engine-guide.md](docs/features/workflow-engine-guide.md)。

### 3.4 全文搜索（Meilisearch）

系统管理数据（用户/角色/菜单/部门/字典/配置）统一全文索引，支持 CJK 分词、批量索引、实时同步。配置开关 `meilisearch.enabled`，详见 [docs/plans/全文搜索与运行日志查询实施方案.md](docs/plans/全文搜索与运行日志查询实施方案.md)。

### 3.5 监控体系

项目内置完整监控体系（66 个 Grafana 面板），覆盖应用、JVM、数据库、中间件：

- **Prometheus**：指标采集，应用指标前缀 `apex_*`（慢 SQL、慢请求、缓存命中率、熔断器状态等）
- **Grafana**：可视化看板，访问 `http://localhost:3000`（admin/admin）
- **Loki**：日志聚合查询，通过 Grafana Explore（`/explore`）使用 LogQL 查询
- **Zipkin**：全链路追踪，采样率默认 10%

### 3.6 限流熔断

- **Sentinel**：入口限流、热点参数限流（`@RateLimiter` / `@HotSpotRateLimit` / `@UserRateLimit`）
- **Resilience4j**：熔断器（默认/严格/宽松三档）、重试策略、超时控制
- 三级容错保障系统稳定性，Redis 不可用时自动降级为进程内本地限流

### 3.7 消息队列

- RocketMQ 生产者/消费者封装，幂等消费保证（Redis 去重窗口 30 分钟）
- 发件箱（Outbox）模式保证可靠投递，进程崩溃后定时补偿重发
- 死信队列处理

### 3.8 文件存储

策略模式支持多种存储后端：Local（本地文件系统）/ MinIO（对象存储）/ 火山引擎 TOS。通过 `storage.default-type` 配置选择实现。

### 3.9 降级快照

接口降级时将最近一次成功响应快照存入本地/Redis，降级期间优先返回快照，提升可用性。通过 `apex.degrade.snapshot.enabled` 控制。

## 4. 数据与基础设施

- **数据库**：MySQL 8.x，MyBatis-Plus；表结构变更一律走 Flyway（`apex-interfaces/src/main/resources/db/migration/`，当前 V1-V22）。

- **缓存**：多级缓存 Caffeine(L1) + Redis(L2)（Redisson）；Redis key 前缀见 `apex-common` 的 `RedisKeyPrefix`/`CacheConstants`。运行时 Redis key 前缀为 `apex:`。

- **多租户**：`TenantContext`/`@TenantAware` + `V14~V19` 租户隔离迁移；按 `apex.tenant.enabled` 配置条件化注册，拦截顺序为多租户→数据权限→乐观锁。租户相关改动必须有 ArchUnit 测试保护（`TenantIsolationArchTest`）。生产端通过 `TenantContext` 获取租户头，确保外部 `tenantId` 无法伪造覆盖。消费者幂等 key 必须增加租户作用域。

- **消息队列**：RocketMQ（spring-rocketmq 2.3.3），优先发件箱（Outbox）模式保证可靠投递。

- **存储抽象**：`StorageProvider` SPI（Local / MinIO / TOS），extension 机制选择实现。

- **可观测性**：Micrometer Tracing（Brave/Zipkin）+ Prometheus + Grafana + Loki + Logback（traceId 贯穿）；日志目录由 `LogDirectoryConfigurer` 处理（`logs/`）。

- 配置 Profile：`basic`（基础）/ `standard`（标准）/ `enterprise`（企业）/ `performance`（性能）；`bootstrap.yml` + `application-{profile}.yml`。

| Profile | 适用场景 | 启用功能 | 依赖服务 |
|---------|----------|----------|----------|
| `basic` | 本地开发、功能验证 | 内存队列、单机缓存 | 无外部依赖 |
| `standard` | 生产环境基础版 | MySQL、Redis、基础监控 | MySQL + Redis |
| `enterprise` | 生产环境企业版 | RocketMQ、Nacos、Sentinel、链路追踪 | MySQL + Redis + RocketMQ + Nacos + Zipkin |
| `performance` | 性能压测 | 全功能 + 性能优化参数 | 完整基础设施 |

## 5. 安全与治理（重要）

接口层有大量 AOP 切面与 WebFilter，改动接口/POST 类逻辑时注意以下约束：

- `@RequirePermission` / `@RequireRole`：管理接口权限控制（Aspect: RequirePermissionAspect）。

- `@RateLimiter` / `@HotSpotRateLimit` / `@UserRateLimit`：Sentinel 限流。

- `@AntiReplay`：防重放（时间戳 + nonce，5001）。

- `@IdempotentApi`：写接口幂等（幂等键头，5002/5003）。

- `@DataScope` / `@DataSource` / `@TenantAware`：数据权限、多数据源、租户隔离。

- `@OperationLog`：操作审计日志（异步批量落库）。

- Spring Security + JWT（Access + Refresh）；SMS/验证码、内容审核（先审后发）等安全能力见 `docs/SECURITY_PROTECTIONS.md`。

- 安全守卫：UA 检查、扫描流量拦截（`/.git`、`/phpmyadmin`、`/actuator/env` 等探测路径阻断）、IP 限流、登录保护（暴力破解防护）、短信发送限流、输入校验（SQL 注入/XSS）、安全响应头、防重放攻击。

新增管理端点时**必须**：加权限注解 + 在 `sys_menu`/权限码种子迁移中登记（参照 V13/V8/V22 迁移写法）。

## 6. 代码风格与注解规范

### 6.1 JavaDoc 注释规范（强制执行）

**所有** **`public`** **和** **`protected`** **类、接口、方法、字段必须有完整的 JavaDoc 注释**，遵循以下规范：

#### 类级别注释模板

```java
/**
 * [类的简短描述（一句话）]
 *
 * <p>[类的详细说明，包括职责、设计模式、使用场景等]
 *
 * <h2>主要功能</h2>
 * <ul>
 *   <li>功能点1：说明</li>
 *   <li>功能点2：说明</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.0.0
 */
```

#### 方法级别注释模板

```java
/**
 * [方法简短描述（动词开头）]
 *
 * <p>[方法详细说明，包括业务逻辑、异常场景等]
 *
 * @param paramName 参数说明（作用、约束、取值范围）
 * @return 返回值说明（内容、可能状态）
 * @throws ExceptionType 异常说明（触发条件、处理方式）
 * @since 1.0.0
 */
```

**注释原则**：

- 使用中文描述业务逻辑，技术术语可保留英文
- 使用 HTML 标签（`<p>`, `<ul>`, `<h2>`）提升可读性
- 固定使用 `@author Apex Team`
- `@version` 使用项目版本号（当前 1.2.0）
- `@since` 标注首次出现版本
- 使用 `{@code}`, `{@link}`, `{@see}` 等 JavaDoc 标签
- 复杂类添加使用示例 `<pre>{@code}</pre>`

### 6.2 Lombok 使用规范（强制执行）

**必须使用 Lombok 注解替代样板代码**，禁止手写 getter/setter/constructor/toString/equals/hashCode。

#### DTO/VO/Request/Response 类

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDTO {
    private Long id;
    private String username;
}
```

#### Domain Entity（领域实体）

```java
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {
    private Long id;
    private String username;
    
    // 领域方法
    public void changeStatus(UserStatus newStatus) {
        // 业务逻辑
    }
}
```

#### Service/Component 类

```java
@Service
@Slf4j
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final TokenService tokenService;
}
```

**Lombok 注解选择指南**：

| 场景 | 推荐注解 | 说明 |
|------|----------|------|
| DTO/VO/Request/Response | `@Data` | 生成 getter/setter/toString/equals/hashCode |
| 需要 Builder 模式 | `@Builder` | 配合 `@Data` 使用，提供流式构建 |
| 需要全参/无参构造 | `@AllArgsConstructor` + `@NoArgsConstructor` | 显式提供构造器 |
| Domain Entity | `@Getter` + `@Builder` | 只读属性，通过领域方法修改 |
| Service/Component | `@RequiredArgsConstructor` | 自动注入 `final` 字段（依赖注入） |
| 日志记录 | `@Slf4j` | 自动生成 `log` 字段 |

**禁止**：`@SneakyThrows`、`val`、`var` 和 `@Synchronized`。**Lombok 配置**：项目根目录的 `lombok.config` 文件统一配置。

### 6.3 其他代码规范

- **MapStruct**：PO ↔ 实体/DTO 转换使用 Converter 接口。

- **异常处理**：错误抛 `BusinessException(ResultCode.XXX)`，禁止裸 `throw new RuntimeException`。

- **命名空间**：根包 `com.apex.scaffold.*`（example 模块为 `com.apex.example`）；**禁止残留旧项目/原产项目命名**。

- **常量管理**：新枚举/常量收敛，不散落魔法字符串。

- **Spotless 格式**：改动后必须能过 `mvnd spotless:check`，格式化用 `mvnd spotless:apply`。

## 7. 构建、质量与测试

### 7.1 环境准备

**必须使用 SDKMAN 管理的 JDK 和构建工具**：

```bash
# 1. 安装 SDKMAN（如未安装）
curl -s "https://get.sdkman.io" | bash

# 2. 激活项目环境（会自动加载 .sdkmanrc 配置）
cd /Users/work/apex-scaffold
sdk env

# 3. 验证环境
java -version    # 应显示 17.0.12
mvnd --version   # 应显示 2.0.0-rc-3

# 4. 如环境未安装，执行：
sdk install java 17.0.12-oracle
sdk install mvnd 2.0.0-rc-3
```

### 7.2 常用构建命令（使用 mvnd）

```bash
# 全量编译（跳过测试）- 推荐日常开发
mvnd clean compile -DskipTests

# 单元测试（Profile basic）
mvnd clean test -P basic

# 集成测试（需 MySQL + Redis，Profile standard）
mvnd verify -P standard -Dskip.unit.tests=true

# 完整打包（生成可执行 JAR）
mvnd clean package -DskipTests

# 按档位打包
mvnd clean package -P basic       # 基础版（内存队列 + 本地缓存）
mvnd clean package -P standard     # 标准版（MySQL + Redis）
mvnd clean package -P enterprise   # 企业版（完整功能）

# 启动接口模块（开发模式）
mvnd spring-boot:run -pl apex-interfaces

# 单模块构建（如接口层 + 依赖模块）
mvnd -pl apex-interfaces -am compile -DskipTests

# 代码格式化检查 / 修复
mvnd spotless:check
mvnd spotless:apply

# PMD 静态检查
mvnd pmd:check

# 测试覆盖率
mvnd verify -P jacoco

# 生成 JavaDoc（聚合所有模块）
mvnd javadoc:aggregate -DskipTests
```

### 7.3 构建性能对比

| 命令 | mvnd | mvn | 提升 |
|------|------|-----|------|
| clean compile -DskipTests | ~8s | ~25s | 3.1x |
| clean test -P basic | ~15s | ~45s | 3.0x |
| clean package -DskipTests | ~12s | ~38s | 3.2x |

**注意**：本地开发必须使用 mvnd；CI 环境使用 Maven 3.9+（`.github/workflows/ci-cd.yml` 配置）。

### 7.4 质量门槛（CI 强制执行）

- `spotless:check`：格式校验（**对标 spotless 格式，改动后必须能过**）。
- PMD 静态扫描、SonarQube（jacoco 覆盖率）。
- 单元测试：JUnit 5 + Mockito（+ Byte Buddy agent，见根 pom argLine）+ Reactor StepVerifier。
- 集成测试：`apex-example` 的 `integration` 包（`@SpringBootTest` + MySQL/Redis），ArchUnit 架构约束测试。
- CI 流水线见 `.github/workflows/ci-cd.yml`（code-quality → unit-test → integration-test → build-image/release）。
- 每个新增/修改的 Service 建议补充对应单测；BEST_PRACTICES 见 [docs/BEST_PRACTICES.md](docs/BEST_PRACTICES.md)。

## 8. 目录速查（文档）

`docs/` 下按需查阅：

- **架构**：[architecture/01-logical-architecture-detail.md](docs/architecture/01-logical-architecture-detail.md)（DDD 四层详解）、[architecture/system-architecture.md](docs/architecture/system-architecture.md)、[ARCHITECTURE_INDEX.md](docs/ARCHITECTURE_INDEX.md)
- **开发规范**：[standards/backend-development-standards.md](docs/standards/backend-development-standards.md)
- **快速上手**：`QUICK_START.md`、`CONFIGURATION.md`、`CODEGEN.md`、`DEPLOYMENT.md`
- **安全**：`SECURITY_PROTECTIONS.md`、`DATA_SCOPE_USAGE.md`、`DATA_SOURCE_SWITCH_USAGE.md`
- **运维**：`TROUBLESHOOTING.md`、`operations/monitoring.md`、`operations/deployment.md`
- **功能**：`features/workflow-engine-guide.md`、`WORKFLOW_GUIDE.md`、`MESSAGE_MIDDLEWARE.md`
- **其他**：`API_VERSION_MANAGEMENT.md`、`ARCHITECTURE_USAGE_GUIDE.md`、`BEST_PRACTICES.md`、`LOMBOK_GUIDE.md`、`JAVADOC_GUIDE.md`

## 9. 前端联动须知

管理后台前端为独立工程 `/Users/work/apex-admin-web`（Vue 3 + Vite，与我方 `/api/v1`、`/admin-api/v1` 对接）：

- **新增菜单/页面时**：Flyway 种子菜单 `component` 字段必须与前端 `VIEW_REGISTRY` 中注册的组件名**完全一致**（如 `monitor/api-call-log/index`），否则前端动态路由解析不到页面。

- **接口契约变更**：保持后端 DTO camelCase 字段与前端 `src/types/index.ts` 对齐；统一响应 `Result` 结构勿破坏（前端按 `code===0` 解包）。

- 后端不再托管 `static/admin/` 静态前端，`/admin` 不再提供重定向。

## 10. 变更检查清单（提交前）

- [ ] 遵循四层依赖方向，未反向依赖；

- [ ] 新增代码含完整 JavaDoc（类/方法/字段）、中文注释、Lombok/MapStruct 风格一致；

- [ ] 所有 DTO/VO/Entity 使用 Lombok 注解，无手写 getter/setter/constructor；

- [ ] 错误码来自 `ResultCode`，消息走 i18n key；

- [ ] Flyway 只追加新版本脚本，未改动已执行迁移；

- [ ] 阻塞 IO 已调度到 `BlockingIoSchedulers`；

- [ ] 管理端点已加权限注解并登记菜单/权限种子；

- [ ] 使用 SDKMAN 激活环境：`cd /Users/work/apex-scaffold && sdk env`；

- [ ] `mvnd clean compile -DskipTests` 通过；

- [ ] `mvnd clean test -P basic` 单元测试通过（或至少 `-pl 相关模块 -am` 范围通过）；

- [ ] `mvnd spotless:check` 通过。
