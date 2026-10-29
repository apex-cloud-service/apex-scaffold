# 03 技术架构全景图 - 详细说明

> 文档版本: v1.1  
> 最后更新: 2026-09-05  
> 适用版本: apex-scaffold v1.3.1+

## 架构图

![Apex Scaffold 技术架构全景图](../diagrams/03_technical_architecture.svg)

## 概述

本文档详细说明 Apex Scaffold 全栈技术选型和各层级组件依赖关系。架构设计遵循以下原则：

- **版本准确性**：所有版本号以 `pom.xml` 和 `package.json` 为准
- **能力真实性**：技术能力描述基于实际配置和源码验证
- **依赖关系清晰**：明确各层级技术栈如何协作支撑系统运行
- **扩展性设计**：标注可选组件和扩展点，支持按需启用

---

## 七层技术栈架构

### 1. 💻 前端层 - Vue 3 技术栈

**独立前端工程**: `/Users/work/apex-admin-web`

| 技术组件 | 版本 | 用途 | 配置位置 |
|----------|------|------|----------|
| **Vue** | 3.5.13 | 渐进式框架 | `package.json` |
| **TypeScript** | 5.7.2 | 类型安全 | `tsconfig.json` strict 模式 |
| **Vite** | 6.0.7 | 构建工具 | Rolldown 引擎 + HMR |
| **Element Plus** | 2.9.1 | UI 组件库 | 暗黑模式支持 |
| **Pinia** | 2.3.0 | 状态管理 | 持久化插件 |
| **Vue Router** | 4.x | 路由管理 | 动态路由 + 权限加载 |
| **Axios** | 1.7.9 | HTTP 客户端 | 请求/响应拦截器 |

**技术特性**：
- **Composition API**: `<script setup>` 语法，组合式函数复用
- **TypeScript strict 模式**: 类型安全，编译时错误检查
- **Vite 6**: Rolldown 构建引擎，冷启动 < 1s，HMR < 100ms
- **Element Plus 2.9**: 暗黑模式、国际化、主题定制
- **Pinia**: 类型安全的状态管理，支持 devtools 调试

**构建流程**：
```bash
# 开发环境
npm run dev  # Vite Dev Server (http://localhost:5173)

# 生产构建
npm run build  # 输出到 dist/
npm run preview  # 本地预览生产构建

# 类型检查
npm run type-check  # vue-tsc 类型检查

# 代码检查
npm run lint  # ESLint + Prettier
```

**与后端对接**：
- API Base URL: `/api/v1` (业务接口), `/admin-api/v1` (管理接口)
- 认证方式: `Authorization: Bearer <access_token>`
- 请求格式: `application/json`
- 响应格式: `{ code, message, data, timestamp, traceId }`

---

### 2. 🌐 接入层 - API 网关与认证

**职责**: 流量接入、认证鉴权、限流熔断、跨域处理

| 技术组件 | 版本 | 用途 | 配置位置 |
|----------|------|------|----------|
| **Nginx / ALB** | - | 反向代理 | SSL 终止 + 负载均衡 |
| **Sentinel** | 1.8.9 | 流量治理 | system 域流控/热点参数（SentinelConfig） |
| **Resilience4j** | 2.2.0 | 网关熔断器 | CircuitBreaker 路由级熔断回退 |
| **JWT / Bearer** | - | 无状态认证 | Access + Refresh 双令牌 |
| **Spring Security** | 6.5.0 | 安全框架 | SecurityWebFilterChain |
| **RouterFunction** | - | 函数式路由 | WebFlux 路由 |
| **CORS** | - | 跨域配置 | 白名单 + 预检缓存 |

**认证流程**：
```
客户端 → POST /api/v1/auth/login (username + password)
  ↓
后端验证 → 生成 JWT (access_token + refresh_token)
  ↓
客户端存储 → localStorage / sessionStorage
  ↓
后续请求 → Header: Authorization: Bearer <access_token>
  ↓
后端验证 → JWT 签名验证 + 过期检查
  ↓
通过 → 提取用户信息 → SecurityContext
  ↓
鉴权 → @RequirePermission("system:user:list")
```

**Resilience4j 熔断器配置**（网关路由级）：
```yaml
resilience4j:
  circuitbreaker:
    instances:
      default:
        registerHealthIndicator: true
        slidingWindowSize: 10
        failureRateThreshold: 50
        waitDurationInOpenState: 10s
        permittedNumberOfCallsInHalfOpenState: 3
```

**Sentinel 流控配置**（system 域，`SentinelNacosDataSourceConfig.java`）：
```yaml
spring:
  cloud:
    sentinel:
      transport:
        dashboard: ${SENTINEL_DASHBOARD_URL:}  # 为空时使用本地规则
      datasource:
        flow:  # 流控规则
          nacos:
            rule-type: flow
        degrade:  # 熔断规则
          nacos:
            rule-type: degrade
```

**CORS 配置**：
```yaml
spring:
  webflux:
    cors:
      allowed-origins: ${CORS_ALLOWED_ORIGINS:http://localhost:5173}
      allowed-methods: GET,POST,PUT,DELETE,OPTIONS
      allowed-headers: "*"
      allow-credentials: true
      max-age: 3600
```

---

### 3. ☕ 后端核心 - Spring Boot 3 + DDD

**职责**: 业务逻辑处理、响应式编排、DDD 分层设计

| 技术组件 | 版本 | 用途 | 配置位置 |
|----------|------|------|----------|
| **Spring Boot** | 3.5.15 | 应用框架 | Java 21 + WebFlux |
| **MyBatis-Plus** | 3.5.10.1 | ORM 框架 | LambdaQuery + 逻辑删除 |
| **Resilience4j** | 2.2.0 | 弹性库 | CircuitBreaker + RateLimiter |
| **Flyway** | 10.15.0 | 数据库迁移 | V1-V20 版本化脚本 |
| **DDD 四层** | - | 架构模式 | 接口/应用/领域/基础设施 |
| **AOP / Aspect** | - | 切面编程 | 审计/数据权限/租户拦截 |

**第二行技术组件**：

| 技术组件 | 版本 | 用途 | 配置位置 |
|----------|------|------|----------|
| **TenantLine** | - | 多租户 | 行级隔离 (MyBatis-Plus) |
| **RBAC + 数据权限** | - | 权限控制 | 菜单+按钮+部门级权限 |
| **Validation** | 3.1 | 参数校验 | JSR380 注解校验 |
| **i18n Locale** | - | 国际化 | 中文/英文/日文 |
| **SpringDoc** | 2.8 | API 文档 | OpenAPI 3.0 |
| **Micrometer** | - | 指标输出 | Prometheus 格式 |
| **Zipkin Brave** | - | 链路追踪 | 分布式追踪 |

**DDD 四层架构**：
```
apex-system (接口层)
  ├── handler/          # Handler 薄层，权限注解 + 参数校验
  ├── router/           # WebFlux RouterFunction 路由
  └── config/           # Spring 配置

apex-system (应用层)
  ├── service/          # 用例编排，事务边界
  └── dto/              # DTO 转换

apex-domain (领域层)
  ├── model/            # 领域模型，业务规则
  ├── repository/       # Repository Port 定义
  └── event/            # 领域事件

apex-infrastructure (基础设施层)
  ├── persistence/      # MyBatis-Plus Mapper 实现
  ├── cache/            # Redis 缓存实现
  ├── storage/          # MinIO 对象存储实现
  ├── mq/               # RocketMQ 消息队列实现
  └── remote/           # 第三方 API 调用实现
```

**响应式编程模型**：
```java
// Handler 层
public Mono<ServerResponse> listTree(ServerRequest request) {
    return deptService.listTree()  // 返回 Mono<List<DeptTreeVO>>
        .flatMap(this::buildSuccess);  // 包装为统一响应
}

// Application 层
public Mono<List<DeptTreeVO>> listTree() {
    return deptRepository.findAll()  // 响应式查询
        .collectList()  // 收集为 List
        .map(this::buildTree);  // 构建树结构
}

// Infrastructure 层 (阻塞 JDBC 包装为响应式)
@Override
public Flux<DeptEntity> findAll() {
    return Mono.fromCallable(() -> deptMapper.selectList(null))
        .flatMapIterable(Function.identity())
        .subscribeOn(Schedulers.boundedElastic());  // 切换到阻塞线程池
}
```

**事务边界**：
```java
// 响应式事务
@Transactional
public Mono<Void> createUser(UserCreateDTO dto) {
    return userRepository.save(user)
        .then(roleRepository.saveUserRoles(userId, roleIds));
}

// 注意：返回 Mono/Flux 的方法不能直接使用 @Transactional(propagation = REQUIRED)
// 需要使用 ReactiveTransactionManager
```

---

### 4. 💾 数据层 - 持久化与缓存

**职责**: 数据存储、缓存加速、对象存储、消息队列、全文检索

| 组件 | 镜像/版本 | 用途 | 默认状态 |
|------|-----------|------|----------|
| **MySQL** | 9.0 | 业务数据 | ✅ Compose 启用 |
| **Redis** | 7-alpine | 缓存 + 分布式锁 | ✅ Compose 启用 |
| **Caffeine** | 3.1.8 | 堆内一级缓存 | ✅ 默认启用 |
| **MinIO** | RELEASE.2024-08-17T01-24-54Z | S3 兼容对象存储 | ✅ Compose 启用 |
| **Druid** | 1.2.25 | 连接池 + SQL 监控 | ✅ 默认启用 |
| **RocketMQ** | 5.3.1 | 消息队列 | 🔧 可选启用 |
| **Meilisearch** | 1.10 | 全文检索 | ✅ Compose 启用 |
| **Volcengine TOS** | 2.6.1 | 云对象存储 | 🔧 可选启用 |
| **H2** | - | 单元测试内嵌 DB | ✅ 测试启用 |

**MySQL 配置**：
```yaml
spring:
  datasource:
    dynamic:
      primary: master
      datasource:
        master:
          url: jdbc:mysql://${DB_MASTER_HOST}:3306/${DB_NAME}
          username: ${DB_USERNAME}
          password: ${DB_PASSWORD}
          driver-class-name: com.mysql.cj.jdbc.Driver
    hikari:
      maximum-pool-size: ${DB_MAX_POOL_SIZE:20}
      minimum-idle: ${DB_MIN_IDLE:5}
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
```

**Flyway 迁移**：
```sql
-- V1__init_schema.sql
CREATE TABLE sys_user (
  id BIGINT PRIMARY KEY,
  username VARCHAR(50) NOT NULL,
  ...
);

-- V2__add_tenant.sql
ALTER TABLE sys_user ADD COLUMN tenant_id BIGINT;

-- 迁移策略: 版本化演进，禁止修改已发布脚本
```

**多级缓存架构**：
```
请求 → 查询 Caffeine L1 (堆内，< 1ms)
  ↓ Miss
查询 Redis L2 (网络，< 5ms)
  ↓ Miss
查询 MySQL (磁盘，< 50ms)
  ↓
写入 Redis + Caffeine
  ↓
返回数据
```

**缓存配置**：
```yaml
spring:
  cache:
    type: caffeine
    caffeine:
      spec: maximumSize=10000,expireAfterWrite=5m

  data:
    redis:
      host: ${REDIS_HOST:redis}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD}
      lettuce:
        pool:
          max-active: 16
          max-idle: 8
          min-idle: 4
```

**RocketMQ 配置** (可选)：
```yaml
rocketmq:
  name-server: ${ROCKETMQ_NAME_SERVER_ADDR:}
  producer:
    group: apex-producer-group
    send-message-timeout: 3000
  consumer:
    group: apex-consumer-group
    enabled: ${APEX_MQ_CONSUMER_ENABLED:false}  # 默认关闭
```

**Meilisearch 配置**：
```yaml
meilisearch:
  enabled: ${MEILISEARCH_ENABLED:true}
  url: ${MEILISEARCH_URL:http://meilisearch:7700}
  api-key: ${MEILISEARCH_API_KEY}
  indexes:
    - name: users
      primary-key: id
    - name: logs
      primary-key: id
```

---

### 5. 🐳 运行时基础设施

**职责**: JVM 运行时、容器编排、构建工具、测试框架

| 组件 | 版本 | 用途 | 配置位置 |
|------|------|------|----------|
| **JDK** | 21 LTS | Java 运行时 | JVM 参数按环境选择 |
| **Podman** | - | 容器化 | Containerfile 多阶段构建 + 镜像优化 |
| **Kubernetes** | >= 1.24 | 容器编排 | HPA + Ingress + ConfigMap |
| **podman-compose** | - | 本地开发 | 一键启动完整栈 |
| **Maven** | 3.9 | 构建工具 | 多模块构建 + Spotless |
| **JUnit** | 5 | 单元测试 | MockMvc + StepVerifier |
| **Logback** | - | 日志框架 | 结构化日志 + traceId |

**JVM 参数配置** (生产建议)：
```bash
JAVA_OPTS="
  -Xms2g -Xmx2g  # 堆内存
  -XX:+UseG1GC  # G1 垃圾收集器
  -XX:MaxGCPauseMillis=200  # 最大 GC 停顿时间
  -XX:+HeapDumpOnOutOfMemoryError  # OOM 时生成堆转储
  -XX:HeapDumpPath=/tmp/heapdump.hprof
  -Dcom.sun.management.jmxremote  # JMX 监控
  -Dcom.sun.management.jmxremote.port=9999
  -Dcom.sun.management.jmxremote.authenticate=false
  -Dcom.sun.management.jmxremote.ssl=false
"
```

**Containerfile 多阶段构建**（历史单体示例；实际构建使用仓库根目录 `Containerfile`）：
```dockerfile
# 阶段 1: Maven 构建
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src ./src
RUN mvn clean package -DskipTests

# 阶段 2: 运行时
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
EXPOSE 8080 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**Maven Profile**：
```xml
<profiles>
  <profile>
    <id>basic</id>
    <!-- 基础版: 核心功能 -->
  </profile>
  <profile>
    <id>standard</id>
    <activation><activeByDefault>true</activeByDefault></activation>
    <!-- 标准版: 基础 + 缓存 + 对象存储 -->
  </profile>
  <profile>
    <id>enterprise</id>
    <!-- 企业版: 标准 + RocketMQ + Nacos + 完整监控 -->
  </profile>
</profiles>
```

**测试框架**：
```java
// 单元测试
@SpringBootTest
@AutoConfigureMockMvc
class UserServiceTest {
    @Test
    void testCreateUser() {
        StepVerifier.create(userService.create(dto))
            .expectNextMatches(user -> user.getId() != null)
            .verifyComplete();
    }
}

// 集成测试 (使用 Testcontainers)
@Testcontainers
class IntegrationTest {
    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:9.0");
    
    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine");
}
```

---

### 6. 🔍 可观测性与运维

**职责**: 指标采集、日志聚合、链路追踪、告警通知

| 组件 | 版本 | 用途 | 配置位置 |
|------|------|------|----------|
| **Prometheus** | 2.54.1 | TSDB 时序数据库 | 指标采集 + 告警规则 |
| **Grafana** | 11.2.0 | 可视化 | Dashboard JSON 配置 |
| **Grafana Loki** | 3.0.0 | 日志聚合 | 日志查询 + 告警 |
| **Grafana Alloy** | 1.0.0 | OpenTelemetry Collector | 统一遥测数据收集 |
| **Zipkin** | 2.27.4 | 链路追踪 | 分布式追踪 |
| **Micrometer** | 1.14.2 | 指标门面 | 统一指标抽象 |
| **Logback** | 1.5.12 | 日志框架 | 异步 Appender |
| **AlertManager** | - | 告警管理 | 飞书/邮件/短信 |
| **ArchUnit** | - | 架构测试 | 依赖规则验证 |
| **Spotless** | - | 代码格式 | 统一代码风格 |

**Prometheus 指标示例**：
```
# JVM 指标
jvm_memory_used_bytes{area="heap",id="G1 Old Gen"} 524288000
jvm_gc_pause_seconds_sum{action="end of major GC",cause="Metadata GC Threshold"} 0.234

# HTTP 指标
http_server_requests_seconds_count{method="GET",uri="/api/v1/users",status="200"} 1234
http_server_requests_seconds_sum{method="GET",uri="/api/v1/users",status="200"} 12.34

# 数据库连接池指标
hikaricp_connections_active{pool="master"} 5
hikaricp_connections_idle{pool="master"} 15

# Redis 指标
redis_commands_duration_seconds_sum{command="get"} 0.123
redis_commands_duration_seconds_count{command="get"} 456
```

**Grafana Dashboard 配置**：
```json
{
  "dashboard": {
    "title": "Apex Scaffold 监控大盘",
    "panels": [
      {
        "title": "QPS",
        "targets": [
          {
            "expr": "rate(http_server_requests_seconds_count[1m])"
          }
        ]
      },
      {
        "title": "P99 响应时间",
        "targets": [
          {
            "expr": "histogram_quantile(0.99, rate(http_server_requests_seconds_bucket[1m]))"
          }
        ]
      }
    ]
  }
}
```

**Loki 日志查询**：
```logql
# 查询错误日志
{app="apex-system"} |= "ERROR"

# 按 traceId 查询完整调用链
{app="apex-system"} | json | traceId="a1b2c3d4e5f6"

# 统计 5xx 错误
sum(rate({app="apex-system"} | json | status >= 500 [1m]))
```

**Zipkin 链路追踪**：
```
Span 1: HTTP GET /api/v1/users
  ├─ Span 2: DeptService.listTree()
  │   ├─ Span 3: DeptRepository.findAll()
  │   │   └─ Span 4: MySQL SELECT * FROM sys_dept
  │   └─ Span 5: BuildTree (业务逻辑)
  └─ Span 6: Redis Cache (miss)
```

---

## 技术栈依赖关系

### 前端 → 后端
```
Vue 3 (Axios) → Spring WebFlux (RouterFunction)
  ├─ 认证: JWT Bearer Token
  ├─ 请求格式: application/json
  ├─ 响应格式: { code, message, data, timestamp, traceId }
  └─ 错误处理: 统一异常拦截 + 前端 Toast 提示
```

### 后端 → 数据库
```
MyBatis-Plus (HikariCP) → MySQL
  ├─ 连接池: 20 最大连接, 5 最小空闲
  ├─ 读写分离: Dynamic Datasource (master + slave)
  ├─ 事务管理: @Transactional (阻塞) / ReactiveTransactionManager (响应式)
  └─ 迁移管理: Flyway 版本化脚本
```

### 后端 → 缓存
```
Spring Cache + Caffeine (L1) + Redis (L2)
  ├─ L1: 堆内缓存, < 1ms, 最大 10000 条
  ├─ L2: Redis 分布式缓存, < 5ms, TTL 可配置
  ├─ 更新策略: Cache-Aside Pattern (先删缓存再更新 DB)
  └─ 分布式锁: Redisson (Watch Dog 自动续期)
```

### 后端 → 消息队列 (可选)
```
RocketMQ Producer/Consumer
  ├─ 领域事件发布: DomainEventPublisher
  ├─ 消费者幂等: 消息去重 + 数据库唯一索引
  ├─ 重试策略: 最多 3 次, 指数退避
  └─ 死信队列: DLQ 兜底处理
```

### 后端 → 监控系统
```
应用 → Micrometer → Prometheus (Pull 指标)
应用 → Logback → Alloy → Loki (Push 日志)
应用 → Brave → Zipkin (Report 链路)
Prometheus → AlertManager → 飞书/邮件 (告警)
```

---

## Maven Profile 能力矩阵

| 能力分类 | 组件 | basic | standard | enterprise |
|----------|------|:-----:|:--------:|:----------:|
| **核心功能** | Spring Boot WebFlux | ✅ | ✅ | ✅ |
| **数据库** | MySQL + MyBatis-Plus | ✅ | ✅ | ✅ |
| **缓存** | Caffeine L1 | ✅ | ✅ | ✅ |
| **缓存** | Redis L2 | ❌ | ✅ | ✅ |
| **对象存储** | MinIO | ❌ | ✅ | ✅ |
| **全文检索** | Meilisearch | ❌ | ✅ | ✅ |
| **消息队列** | RocketMQ | ❌ | ❌ | ✅ |
| **服务发现** | Nacos | ❌ | ❌ | ✅ |
| **流量治理** | Resilience4j | ✅ | ✅ | ✅ |
| **链路追踪** | Zipkin | ❌ | ✅ | ✅ |
| **监控大盘** | Prometheus + Grafana | ❌ | ✅ | ✅ |

**说明**：
- ✅ = 依赖已包含
- ❌ = 依赖未包含
- 实际启用状态还需要检查 `application.yml` 和环境变量配置

---

## 技术选型理由

### 为什么选择 Spring WebFlux？
- ✅ 响应式编程: 高并发场景下资源利用率更高
- ✅ 函数式路由: RouterFunction 相比 @RequestMapping 更灵活
- ✅ 背压支持: Flux/Mono 自动处理背压
- ⚠️ 学习曲线: 响应式编程思维转变需要时间

### 为什么选择 MyBatis-Plus？
- ✅ Lambda 风格: 类型安全的查询条件构建
- ✅ 代码生成: 快速生成 Mapper/Service/Controller
- ✅ 逻辑删除: 软删除自动处理
- ✅ 多租户: TenantLineInnerInterceptor 行级隔离
- ⚠️ 阻塞 I/O: 需要手动切换到阻塞线程池

### 为什么选择 Vue 3 + TypeScript？
- ✅ Composition API: 逻辑复用更清晰
- ✅ TypeScript: 类型安全, IDE 提示友好
- ✅ Vite: 构建速度快, HMR 体验好
- ✅ 生态成熟: Element Plus/Pinia/Vue Router 完善

### 为什么选择 Caffeine + Redis 二级缓存？
- ✅ L1 堆内缓存: 极低延迟 (< 1ms)
- ✅ L2 分布式缓存: 集群间共享
- ✅ 缓存击穿防护: Caffeine 自动加载 + Redis 分布式锁
- ⚠️ 一致性问题: Cache-Aside 模式可能短暂不一致

---

## 架构约束与限制

### WebFlux 与阻塞 I/O 隔离
**约束**: JDBC/MyBatis-Plus 是阻塞 I/O,不能直接在 Netty 事件循环线程执行

**正确做法**:
```java
@Override
public Flux<DeptEntity> findAll() {
    return Mono.fromCallable(() -> deptMapper.selectList(null))
        .flatMapIterable(Function.identity())
        .subscribeOn(Schedulers.boundedElastic());  // 切换到阻塞线程池
}
```

**错误做法** (会阻塞 Netty 线程):
```java
// ❌ 错误: 直接在 Reactor 链中调用阻塞方法
return Flux.fromIterable(deptMapper.selectList(null));
```

### 响应式事务边界
**约束**: 返回 Mono/Flux 的方法不能直接使用 `@Transactional`

**正确做法**:
```java
@Transactional  // 在响应式方法上使用
public Mono<Void> createUser(UserCreateDTO dto) {
    return userRepository.save(user)
        .then(roleRepository.saveUserRoles(userId, roleIds));
}
```

### ArchUnit 架构规则
**约束**: 通过 ArchUnit 测试固化架构规则

```java
@AnalyzeClasses(packages = "com.apex.scaffold")
public class ArchitectureTest {
    
    @ArchTest
    public static final ArchRule domain_should_not_depend_on_infrastructure =
        noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("..infrastructure..", "..interfaces..");
    
    @ArchTest
    public static final ArchRule services_should_be_public =
        classes()
            .that().resideInAPackage("..service..")
            .and().haveSimpleNameEndingWith("Service")
            .should().bePublic();
}
```

---

## 常见问题

### Q1: 如何启用 RocketMQ？
**A**: 修改 `pom.xml` Profile 为 `enterprise`,并配置环境变量:
```yaml
APEX_MQ_CONSUMER_ENABLED: "true"
ROCKETMQ_NAME_SERVER_ADDR: "rocketmq-namesrv:9876"
```

### Q2: 如何切换数据库？
**A**: 修改 `application.yml`:
```yaml
spring:
  datasource:
    url: jdbc:postgresql://postgres:5432/apex_data  # PostgreSQL
    # url: jdbc:oracle:thin:@oracle:1521:xe  # Oracle
```

### Q3: 前端如何对接后端 API？
**A**: 在 `vite.config.ts` 配置代理:
```typescript
export default defineConfig({
  server: {
    proxy: {
      '/api': 'http://localhost:8080',
      '/admin-api': 'http://localhost:8080'
    }
  }
})
```

### Q4: 如何查看链路追踪？
**A**: 
1. 访问 Zipkin: `http://localhost:9411`
2. 根据 traceId 查询: 从日志中提取 traceId
3. 查看完整调用链: Span 依赖关系 + 耗时分析

---

## 相关文档

- [01 DDD 四层逻辑架构 - 详细说明](01-logical-architecture-detail.md)
- [02 网络物理部署架构 - 详细说明](02-physical-architecture-detail.md)
- [03 技术架构全景图 - 快速参考](03-technical-architecture-quick-ref.md)
- [03 技术架构全景图 - 架构决策](03-technical-architecture-decision.md)
- [03 技术架构全景图 - 商业价值](03-technical-architecture-business-value.md)

---

**下一步**: 阅读 [快速参考](03-technical-architecture-quick-ref.md) 查看技术栈速查表。
