# AGENT.md — Apex Scaffold（后端）

本文件为 AI 编码 Agent 在本仓库工作时的**行为准则与工程约定**。修改代码前请先通读，违反以下约定会导致构建失败或代码风格不一致。

## 1. 项目概览

**Apex Scaffold** 是面向微信 App 场景的企业级开发脚手架（Java 17 / Spring Boot 3 / WebFlux 响应式 / 多模块 Maven 工程），遵循 **DDD 五层架构**。

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

- 依赖：Spring Boot 3.5.15 / Spring Cloud 2025.0.0 / Spring Cloud Alibaba 2025.0.0.0 / Spring Security 6.5.0 / MyBatis-Plus 3.5.10.1 / Flyway / Redisson / RocketMQ / Sentinel / Resilience4j / MapStruct / **Lombok 1.18.36**

### 模块构成

| 模块                    | 职责                                                                                      | 关键约定                        |
| --------------------- | --------------------------------------------------------------------------------------- | --------------------------- |
| `apex-common`         | 公共模块：工具类、异常体系、统一响应 `Result<T>`、`ResultCode`、注解、常量、上下文、状态机、SPI                           | 无业务依赖，任何模块可引用               |
| `apex-domain`         | 领域层：聚合根、领域事件、仓库接口（Repository）、领域服务、网关端口（Gateway/Port）、枚举                                | **不依赖任何上层模块**               |
| `apex-application`    | 应用层：用例编排、事务边界、DTO 转换（Assembler）、服务                                                      | 依赖 domain                   |
| `apex-infrastructure` | 基础设施层：仓库实现（PO + Mapper + MapStruct Converter + RepositoryImpl）、缓存、MQ、外部服务               | 依赖 domain                   |
| `apex-interfaces`     | 接口层：**WebFlux 函数式端点**（Router + Handler）、安全、过滤器、AOP 切面、配置、i18n、Flyway 迁移（`db/migration`） | 启动模块，主入口 `Application.java` |
| `apex-example`        | 示例模块：完整 CRUD 示例 + 集成测试                                                                  | 可参考的最佳实践样例                  |
| `apex-generator`      | DDD 五层代码生成器（独立工具，offline 可构建）                                                           | FreeMarker 模板，不参与运行时        |
| `apex-license-*`      | 商业授权模块（starter / 工具）                                                                    | 授权校验相关，改动需谨慎                |

### 依赖方向（严格遵守）

```
apex-interfaces → apex-application → apex-domain ← apex-infrastructure
                                            ↑
                                     apex-common（被全部依赖）
```

- 依赖只能从上往下/从外向内，**禁止反向依赖**：domain 不得引用 application/interfaces。

- 架构规则由 `apex-infrastructure/src/test/java/com/apex/scaffold/arch/TenantIsolationArchTest.java`（ArchUnit）在测试期强制校验。

## 2. 核心架构约定

### 2.1 统一响应

所有接口返回 `Result<T>`：`{code, msg, data, traceId}`，业务成功 `code = 0`。错误码定义在 [ResultCode.java](apex-common/src/main/java/com/apex/scaffold/common/result/ResultCode.java)（枚举 + i18n messageKey，禁止散落魔法数字；新错误码按分类段追加：认证 1001~~1022、流量 2001~~2006、基础设施 3001\~3006、业务 4xxx、防重放 5xxx）。

### 2.2 WebFlux 函数式端点（非注解 Controller）

- 路由集中在 [ApiRouter.java](apex-interfaces/src/main/java/com/apex/scaffold/interfaces/router/ApiRouter.java)，每个 Bean 返回 `RouterFunction<ServerResponse>`，用 `RouterFunctions.route()` + `.path()` 按资源分组。

- 业务逻辑在 `handler` 包（如 `UserHandler`），通过 `ServerRequest` 取参、返回 `ServerResponse`。

- 分组前缀：

  - `/api/v1/**` — 业务（C 端 / 开放接口）

  - `/admin-api/v1/**` — 管理后台（配合 `@RequirePermission("system:xxx:*")` 权限点）

- 新增接口 = 新增 Handler 方法 + 在 ApiRouter 注册对应路由 Bean。

- **阻塞 IO（JDBC / MyBatis-Plus）必须放到阻塞线程池**（`BlockingIoSchedulers`，见 apex-common scheduler 包），禁止在 Reactor 事件循环线程直接执行阻塞调用。

### 2.3 分层开发步骤（新增业务模块的标准路径）

1. `apex-domain`：定义聚合根实体、Repository 接口、领域事件、枚举；
2. `apex-infrastructure`：PO（`@TableName`）、Mapper、MapStruct Converter、`RepositoryImpl`（调度到阻塞线程池）；
3. `apex-application`：Assembler（DTO ↔ 实体）、应用 Service（用例编排 + 事务）；
4. `apex-interfaces`：Handler + ApiRouter 注册 + `@RequirePermission` 权限点；
5. 新增 Flyway 迁移 `V{n}__*.sql`（**只追加，不改已执行的迁移脚本**；当前 V1\~V19）。

### 2.4 代码生成器（模板开发）

`apex-generator` 可按表结构 JSON 一键生成整套 CRUD 五层代码 + Vue3 管理页面：

```bash
./scripts/codegen/generate.sh -i scripts/codegen/examples/product.json -o ./generated -v 3
# 同时生成前端页面：加 -f /Users/work/apex-admin-web
```

生成产物含：domain/repository、PO/Mapper/Converter/RepositoryImpl、Request/DTO、Service、Handler、路由片段、Flyway 片段、ServiceTest、前端 api/views。用法详见 [docs/CODEGEN.md](docs/CODEGEN.md)。

## 3. 数据与基础设施

- **数据库**：MySQL 8.x，MyBatis-Plus；表结构变更一律走 Flyway（`apex-interfaces/src/main/resources/db/migration/`，MySQL 版 V1-V19）。

- **缓存**：多级缓存 Caffeine(L1) + Redis(L2)（Redisson）；Redis key 前缀见 `apex-common` 的 `RedisKeyPrefix`/`CacheConstants`。

- **多租户**：`TenantContext`/`@TenantAware` + `V14~V19` 租户隔离迁移；租户相关改动必须有 ArchUnit 测试保护（`TenantIsolationArchTest`）。

- **消息队列**：RocketMQ（spring-rocketmq 2.3.3），优先发件箱（Outbox）模式保证可靠投递。

- **存储抽象**：`StorageProvider` SPI（Local / MinIO / S3），extension 机制选择实现。

- **可观测性**：Micrometer Tracing（Brave/Zipkin）+ Prometheus + Grafana + Logback（traceId 贯穿）；日志目录由 `LogDirectoryConfigurer` 处理（`logs/`）。

- 配置 Profile：`basic`（基础）/ `standard`（标准）/ `enterprise`（企业）/ `performance`（性能）；`bootstrap.yml` + `application-{profile}.yml`。

## 4. 安全与治理（重要）

接口层有大量 AOP 切面与 WebFilter，改动接口/POST 类逻辑时注意以下约束：

- `@RequirePermission` / `@RequireRole`：管理接口权限控制（Aspect: RequirePermissionAspect）。

- `@RateLimiter` / `@HotSpotRateLimit` / `@UserRateLimit`：Sentinel 限流。

- `@AntiReplay`：防重放（时间戳 + nonce，5001）。

- `@IdempotentApi`：写接口幂等（幂等键头，5002/5003）。

- `@DataScope` / `@DataSource` / `@TenantAware`：数据权限、多数据源、租户隔离。

- `@OperationLog`：操作审计日志。

- Spring Security + JWT（Access + Refresh）；SMS/验证码、内容审核（先审后发）等安全能力见 `docs/SECURITY_PROTECTIONS.md`。

新增管理端点时**必须**：加权限注解 + 在 `sys_menu`/权限码种子迁移中登记（参照 V13/V8 迁移写法）。

## 5. 代码风格与注解规范

### 5.1 JavaDoc 注释规范（强制执行）

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

#### 字段级别注释模板

```java
/**
 * [字段说明]
 *
 * <p>[字段详细描述：业务含义、取值范围、默认值、是否必填]
 *
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

### 5.2 Lombok 使用规范（强制执行）

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

| 场景                      | 推荐注解                                         | 说明                                        |
| ----------------------- | -------------------------------------------- | ----------------------------------------- |
| DTO/VO/Request/Response | `@Data`                                      | 生成 getter/setter/toString/equals/hashCode |
| 需要 Builder 模式           | `@Builder`                                   | 配合 `@Data` 使用，提供流式构建                      |
| 需要全参/无参构造               | `@AllArgsConstructor` + `@NoArgsConstructor` | 显式提供构造器                                   |
| Domain Entity           | `@Getter` + `@Builder`                       | 只读属性，通过领域方法修改                             |
| Service/Component       | `@RequiredArgsConstructor`                   | 自动注入 `final` 字段（依赖注入）                     |
| 日志记录                    | `@Slf4j`                                     | 自动生成 `log` 字段                             |

**Lombok 配置**：项目根目录的 `lombok.config` 文件统一配置 Lombok 行为。

### 5.3 其他代码规范

- **MapStruct**：PO ↔ 实体/DTO 转换使用 Converter 接口。

- **异常处理**：错误抛 `BusinessException(ResultCode.XXX)`，禁止裸 `throw new RuntimeException`。

- **命名空间**：根包 `com.apex.scaffold.*`（example 模块为 `com.apex.example`）；**禁止残留旧项目/原产项目命名**。

- **常量管理**：新枚举/常量收敛，不散落魔法字符串。

## 6. 构建、质量与测试

### 6.1 环境准备

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

### 6.2 常用构建命令（使用 mvnd）

```bash
# 全量编译（跳过测试）- 推荐日常开发
mvnd clean compile -DskipTests

# 单元测试（Profile basic）
mvnd clean test -P basic

# 集成测试（需 MySQL + Redis，Profile standard）
mvnd verify -P standard -Dskip.unit.tests=true

# 完整打包（生成可执行 JAR）
mvnd clean package -DskipTests

# 启动接口模块（开发模式）
mvnd spring-boot:run -pl apex-interfaces

# 单模块构建（如接口层 + 依赖模块）
mvnd -pl apex-interfaces -am compile -DskipTests

# 代码格式化检查
mvnd spotless:check

# 代码格式化修复
mvnd spotless:apply

# 生成 JavaDoc（聚合所有模块）
mvnd javadoc:aggregate -DskipTests
```

### 6.3 构建性能对比

| 命令                        | mvnd  | mvn   | 提升   |
| ------------------------- | ----- | ----- | ---- |
| clean compile -DskipTests | \~8s  | \~25s | 3.1x |
| clean test -P basic       | \~15s | \~45s | 3.0x |
| clean package -DskipTests | \~12s | \~38s | 3.2x |

**注意**：

- **本地开发必须使用 mvnd**，构建速度提升 3 倍以上

- **CI 环境使用 Maven 3.9+**（`.github/workflows/ci-cd.yml` 配置）

- 首次构建需下载依赖，mvnd 会启动后台守护进程（重启后自动恢复）

### 质量门槛（CI 强制执行）

- `spotless:check`：格式校验（**对标 spotless 格式，改动后必须能过**）。

- PMD 静态扫描、SonarQube（jacoco 覆盖率）。

- 单元测试：JUnit 5 + Mockito（+ Byte Buddy agent，见根 pom argLine）+ Reactor StepVerifier。

- 集成测试：`apex-example` 的 `integration` 包（`@SpringBootTest` + MySQL/Redis），ArchUnit 架构约束测试。

- CI 流水线见 `.github/workflows/ci-cd.yml`（code-quality → unit-test → integration-test → build-image/release）。

- 每个新增/修改的 Service 建议补充对应单测；BEST\_PRACTICES 见 [docs/BEST\_PRACTICES.md](docs/BEST_PRACTICES.md)。

## 7. 目录速查（文档）

`docs/` 下按需查阅：`architecture.md`（DDD 五层）、`QUICK_START.md`、`CONFIGURATION.md`、`CODEGEN.md`、`DEPLOYMENT.md`、`SECURITY_PROTECTIONS.md`、`DATA_SCOPE_USAGE.md`、`DATA_SOURCE_SWITCH_USAGE.md`、`API_VERSION_MANAGEMENT.md`、`ARCHITECTURE_USAGE_GUIDE.md`、`TROUBLESHOOTING.md`。

## 8. 前端联动须知

管理后台前端为独立工程 `/Users/work/apex-admin-web`（Vue 3 + Vite，与我方 `/api/v1`、`/admin-api/v1` 对接）：

- **新增菜单/页面时**：Flyway 种子菜单 `component` 字段必须与前端 `VIEW_REGISTRY` 中注册的组件名**完全一致**（如 `monitor/api-call-log/index`），否则前端动态路由解析不到页面。

- **接口契约变更**：保持后端 DTO camelCase 字段与前端 `src/types/index.ts` 对齐；统一响应 `Result` 结构勿破坏（前端按 `code===0` 解包）。

- 后端不再托管 `static/admin/` 静态前端，`/admin` 不再提供重定向。

## 9. 变更检查清单（提交前）

- [ ] 遵循五层依赖方向，未反向依赖；

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

