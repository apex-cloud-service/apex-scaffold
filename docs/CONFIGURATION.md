# 配置项详解

本文档详细说明了 Apex Scaffold 的所有可配置项,帮助你根据实际需求定制系统行为。

## 📁 配置文件位置

主配置文件: `apex-interfaces/src/main/resources/application.yml`

环境特定配置:
- `application-dev.yml` - 开发环境
- `application-test.yml` - 测试环境
- `application-prod.yml` - 生产环境

通过 `spring.profiles.active` 激活对应环境:
```bash
mvnd spring-boot:run -Dspring-boot.run.profiles=prod
```

## 🔐 安全认证配置

### JWT配置

```yaml
jwt:
  # JWT签名密钥(生产环境必须修改为强随机字符串,至少32字符)
  secret: ${JWT_SECRET:change-this-in-production-min-32-chars}

  # Access Token过期时间(毫秒),默认2小时
  access-token-expire: 7200000

  # Refresh Token过期时间(毫秒),默认7天
  refresh-token-expire: 604800000

  # JWT密钥最小长度
  min-secret-length: 32
  
  # Token请求头名称
  header: Authorization
  
  # Token前缀
  prefix: "Bearer "
```

**安全建议**:
- ⚠️ 生产环境务必通过环境变量设置 `JWT_SECRET`
- 使用强随机字符串生成器: `openssl rand -base64 48`
- 定期轮换密钥

### RBAC权限配置

```yaml
security:
  # 是否启用RBAC权限控制
  rbac-enabled: true
  
  # 超级管理员角色标识
  admin-role: ADMIN
  
  # 公开路径(无需认证即可访问)
  public-paths:
    - /api/v1/health
    - /api/v1/info
    - /swagger-ui.html
    - /doc.html
    - /v3/api-docs/**
```

## 💾 数据源配置

### MySQL配置

```yaml
spring:
  datasource:
    dynamic:
      # 默认数据源（dynamic-datasource，应用实际结构见 application-dev.yml / application-prod.yml）
      primary: master
      datasource:
        master:
          # 数据库URL（默认库 apex_data；dev 默认 root/apex-root，prod 必须环境变量注入）
          url: jdbc:mysql://127.0.0.1:3306/apex_data?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false
          # 用户名
          username: root
          # 密码(生产环境建议使用环境变量 DB_MASTER_PASSWORD)
          password: ${DB_MASTER_PASSWORD:apex-root}
          # 驱动类名
          driver-class-name: com.mysql.cj.jdbc.Driver
          # Druid连接池配置
          druid:
            # 初始连接数
            druid:
              # 初始连接数
              initial-size: 5
              # 最小空闲连接数
              min-idle: 5
              # 最大活跃连接数
              max-active: 20
              # 获取连接超时时间(毫秒)
              max-wait: 60000
              # 空闲连接检测间隔(毫秒)（须小于 keep-alive-between-time-millis）
              time-between-eviction-runs-millis: 30000
              # 连接保活间隔(毫秒)
              keep-alive-between-time-millis: 60000
              # 连接最小生存时间(毫秒)
              min-evictable-idle-time-millis: 300000
              # 是否开启PSCache
              pool-prepared-statements: true
              # PSCache大小
              max-pool-prepared-statement-per-connection-size: 20
              # 监控统计拦截的filters
              filters: stat,wall,slf4j
              # 合并多个DruidDataSource的监控数据
              use-global-data-source-stat: true
        # 从库（与 master 参数完全一致，仅环境变量前缀 DB_SLAVE_*；prod 必须注入真实只读副本地址）
        slave:
          url: jdbc:mysql://127.0.0.1:3306/apex_data?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false
          username: root
          # 密码(生产环境建议使用环境变量 DB_SLAVE_PASSWORD)
          password: ${DB_SLAVE_PASSWORD:apex-root}
          driver-class-name: com.mysql.cj.jdbc.Driver
          druid:
            # 初始连接数
            initial-size: 5
            # 最小空闲连接数
            min-idle: 5
            # 最大活跃连接数
            max-active: 20
            # 获取连接超时时间(毫秒)
            max-wait: 60000
            # 空闲连接检测间隔(毫秒)（须小于 keep-alive-between-time-millis）
            time-between-eviction-runs-millis: 30000
            # 连接保活间隔(毫秒)
            keep-alive-between-time-millis: 60000
            # 连接最小生存时间(毫秒)
            min-evictable-idle-time-millis: 300000
            # 是否开启PSCache
            pool-prepared-statements: true
            # PSCache大小
            max-pool-prepared-statement-per-connection-size: 20
            # 监控统计拦截的filters
            filters: stat,wall,slf4j
            # 合并多个DruidDataSource的监控数据
            use-global-data-source-stat: true
```

### MyBatis-Plus配置

```yaml
mybatis-plus:
  # Mapper XML文件位置
  mapper-locations: classpath*:mapper/**/*.xml
  
  # 实体类扫描包
  type-aliases-package: com.apex.**.model.entity
  
  # 全局配置
  global-config:
    db-config:
      # 主键类型(AUTO自增/INPUT手动输入/ASSIGN_ID雪花算法)
      id-type: ASSIGN_ID
      
      # 逻辑删除字段名
      logic-delete-field: deleted
      
      # 逻辑删除值(已删除)
      logic-delete-value: 1
      
      # 逻辑未删除值
      logic-not-delete-value: 0
  
  # 配置
  configuration:
    # 驼峰命名转换
    map-underscore-to-camel-case: true
    
    # 缓存
    cache-enabled: false
    
    # 日志实现
    log-impl: org.apache.ibatis.logging.slf4j.Slf4jImpl
```

## 🗄️ 缓存配置

### Redis配置

```yaml
spring:
  data:
    redis:
      # Redis主机
      host: localhost
      
      # Redis端口
      port: 6379
      
      # Redis密码(如果有)
      password: ${REDIS_PASSWORD:}
      
      # 数据库索引
      database: 0
      
      # 连接超时时间(毫秒)
      timeout: 3000
      
      # Lettuce连接池配置
      lettuce:
        pool:
          # 最大活跃连接数
          max-active: 8
          
          # 最大空闲连接数
          max-idle: 8
          
          # 最小空闲连接数
          min-idle: 0
          
          # 获取连接最大等待时间(毫秒)
          max-wait: -1
```

### Caffeine本地缓存配置

```yaml
cache:
  caffeine:
    # 最大缓存条目数
    max-size: 1000
    
    # 写入后过期时间(秒)
    expire-after-write: 300
    
    # 访问后过期时间(秒,0表示禁用)
    expire-after-access: 0
    
    # 是否统计命中率
    record-stats: true
```

### 多级缓存策略

```yaml
cache:
  multi-level:
    # 是否启用多级缓存
    enabled: true
    
    # L1缓存(Caffeine)TTL(秒)
    l1-ttl: 60
    
    # L2缓存(Redis)TTL(秒)
    l2-ttl: 300
    
    # 缓存更新策略(NONE/WRITE_THROUGH/WRITE_BEHIND)
    update-strategy: WRITE_THROUGH
```

## ⚡ 限流熔断配置

### 全局响应超时

```yaml
apex:
  api:
    # 单个请求（含过滤器链与业务处理）的最大处理时长
    # 超时返回 504 + REQUEST_TIMEOUT(2006) 统一错误响应，防止慢请求占用连接引发雪崩
    # SSE 长连接（Accept: text/event-stream）自动豁免；设为 0s 关闭
    response-timeout: ${APEX_RESPONSE_TIMEOUT:30s}
```

### Sentinel配置

**控制台通信（spring.cloud.sentinel）**：

```yaml
spring:
  cloud:
    sentinel:
      # 是否启用 Sentinel 客户端
      enabled: true
      # 控制台地址（空 = 不连接控制台，仅本地规则生效）
      transport:
        dashboard: localhost:8858
        port: 8719
      # 启动即初始化客户端，避免首请求懒加载延迟
      eager: true
```

**规则 Nacos 动态下发（apex.sentinel.nacos，生产热更新）**：

```yaml
apex:
  sentinel:
    nacos:
      # 启用后限流/熔断规则从 Nacos 拉取（约 30s 热生效），静态规则作为启动兜底；
      # 默认关闭（无 Nacos 零影响）；生产接入 Nacos 后置 true
      enabled: ${SENTINEL_NACOS_ENABLED:false}
      server-addr: ${NACOS_SERVER_ADDR:127.0.0.1:18848}
      namespace: ${NACOS_NAMESPACE:}
      group-id: ${SENTINEL_NACOS_GROUP:SENTINEL_GROUP}
      flow-data-id: ${SENTINEL_NACOS_FLOW_DATA_ID:apex-sentinel-flow-rules}
      degrade-data-id: ${SENTINEL_NACOS_DEGRADE_DATA_ID:apex-sentinel-degrade-rules}
```

> ⚠️ 启用动态下发时，Nacos 的 DataId 需维护**完整规则集**（推送为整体替换语义）。
> JSON 模板与字段说明见 `config/sentinel/README.md`；当前内置静态基线为 43 条限流规则和 30 条降级规则，定义于 `SentinelConfig`，两个 Nacos DataId 必须分别发布完整集合。动态规则推送采用整体替换语义，不会与静态规则自动合并。

### Resilience4j 与远程调用配置

当前存在两条独立的断路器链路：

- `apex.circuit-breaker` 由 `CircuitBreakerService` 读取，供业务通过 `CircuitBreakerPort.withCircuitBreaker(name, mono)` 显式接入编程式断路器。
- `resilience4j.circuitbreaker.instances` 由 `RemoteApiClient` 使用的 `CircuitBreakerRegistry` 管理。实例名必须与 `remote.api.services` 的服务名一致，远程调用才会绑定对应断路器。

`RemoteApiClient` 的连接超时、读取超时和重试参数实际来自 `RemoteApiProperties`，即 `remote.api` 配置。重试由 Reactor Retry 执行：4xx 不重试，5xx 和未分类调用异常按配置进行退避重试。

```yaml
remote:
  api:
    default-connect-timeout: 5000
    default-read-timeout: 10000
    default-retry-count: 2
    default-retry-delay: 1000
    services:
      example-service:
        base-url: https://api.example.com
        connect-timeout: 3000
        read-timeout: 8000
        retry-count: 2
        retry-delay: 1000

resilience4j:
  circuitbreaker:
    instances:
      example-service:
        sliding-window-size: 10
        failure-rate-threshold: 50
        slow-call-rate-threshold: 50
        slow-call-duration-threshold: 3s
        wait-duration-in-open-state: 60s
        permitted-number-of-calls-in-half-open-state: 10

apex:
  circuit-breaker:
    default:
      failure-rate-threshold: 50
      wait-duration-in-open-state: 60s
      sliding-window-size: 100
      minimum-number-of-calls: 10
      permitted-calls-in-half-open: 5
```

`resilience4j.retry`、`resilience4j.timelimiter` 和 `resilience4j.ratelimiter` 即使存在于配置中，也不会自动作用于当前 `RemoteApiClient` 调用链；当前远程重试和超时分别由 Reactor Retry 与 `remote.api` 参数控制。

### 接口级限流

```yaml
rate-limit:
  # 是否启用限流
  enabled: true
  
  # 默认QPS限制
  default-qps: 100
  
  # 用户级别限流
  user:
    # 每用户每秒请求数
    qps-per-user: 10
    
    # 限流维度(USER/IP/GLOBAL)
    dimension: USER
  
  # 热点参数限流
  hot-spot:
    # 是否启用
    enabled: true
    
    # 统计窗口时长(秒)
    window-length: 60
    
    # 最大访问次数
    max-count: 1000
```

## 📨 消息队列配置

### RocketMQ配置

> ⚠️ rocketmq-spring-boot 2.3.3 依据 `rocketmq.name-server` 属性**是否存在**装配生产者组件：
> 不注入 `ROCKETMQ_NAMESERVER` 时全部跳过（应用可无 MQ 启动），启用时注入即可。

```yaml
rocketmq:
  # NameServer地址（启用消息能力时注入；本地 infra 栈为 127.0.0.1:9876）
  name-server: 127.0.0.1:9876
  
  # 生产者配置
  producer:
    # 生产者组名
    group: apex-producer-group
    
    # 发送消息超时时间(毫秒)
    send-message-timeout: ${ROCKETMQ_SEND_TIMEOUT:30000}
    
    # 消息最大长度(字节)
    max-message-size: 4194304
    
    # 同步发送失败重试次数
    retry-times-when-send-failed: 2
    
    # 异步发送失败重试次数
    retry-times-when-send-async-failed: 2
  
  # 消费者配置
  consumer:
    # 消费者组名
    group: apex-consumer-group
    
    # 每次拉取消息数量
    pull-batch-size: 32
    
    # 消费线程数
    consume-thread-min: 10
    consume-thread-max: 20

mq:
  consumer:
    # 消费幂等记录保留时间（分钟）
    idempotent-ttl-minutes: ${MQ_IDEMPOTENT_TTL_MINUTES:30}

apex:
  mq:
    # 是否通过 RocketMQ 广播领域事件
    domain-event-enabled: ${APEX_MQ_DOMAIN_EVENT_ENABLED:false}
    domain-event-topic: ${APEX_MQ_DOMAIN_EVENT_TOPIC:apex-domain-event}
  events:
    outbox:
      batch-size: ${OUTBOX_BATCH_SIZE:50}
      max-attempts: ${OUTBOX_MAX_ATTEMPTS:8}
      lock-timeout-seconds: ${OUTBOX_LOCK_TIMEOUT_SECONDS:120}
      parallelism: ${OUTBOX_PARALLELISM:4}
      retention-enabled: ${OUTBOX_RETENTION_ENABLED:true}
      retention-delivered-hours: ${OUTBOX_RETENTION_DELIVERED_HOURS:168}
      retention-dead-days: ${OUTBOX_RETENTION_DEAD_DAYS:30}
      retention-batch-size: ${OUTBOX_RETENTION_BATCH_SIZE:5000}
```

生产消息使用稳定的业务 message ID，同时写入消息 header 和 RocketMQ `KEYS`。消费者以 Redis token 竞争处理权，状态从 `PROCESSING:<token>` 变为 `SUCCESS`；业务处理或最终确认失败时释放当前 token 并抛出异常，使 RocketMQ 触发重消费。

### 自动重试配置

`@AutoRetry` 适用于幂等或可安全重复执行的业务方法，支持同步返回值、`Mono` 和 `Flux`：

```java
@AutoRetry(maxAttempts = 3, retryDelayMs = 200, maxDelayMs = 5000,
        backoffMultiplier = 2.0, retryOn = {TransientDataAccessException.class})
public Mono<Result> refreshCache(String key) {
    return cacheClient.refresh(key);
}
```

`maxAttempts` 包含首次调用；仅 `retryOn` 指定的异常会重试，其他异常立即传播。响应式方法在订阅时执行退避，不应在注解方法内部自行 `subscribe()`。

### Outbox 配置与生命周期

领域事件先写入 Outbox，再由 worker 领取并投递。记录按 `PENDING -> PROCESSING -> DELIVERED` 流转；超时的 `PROCESSING` 会被回收，失败达到 `max-attempts` 后进入 `DEAD`。只有 RocketMQ callback 成功后才标记 `DELIVERED`。

## 📁 文件存储配置

### 多存储后端支持

```yaml
storage:
  # 当前使用的存储类型(LOCAL/MINIO/S3/VOLCENGINE_TOS)
  default-type: LOCAL
  
  # 本地存储配置
  local:
    # 存储根路径
    base-path: /data/uploads
    
    # 访问URL前缀
    url-prefix: /uploads
  
  # MinIO配置
  minio:
    # 服务端点
    endpoint: http://localhost:9000
    
    # Access Key
    access-key: ${MINIO_ACCESS_KEY:minioadmin}
    
    # Secret Key
    secret-key: ${MINIO_SECRET_KEY:minioadmin}
    
    # Bucket名称
    bucket: apex-files
    
    # 是否启用HTTPS
    secure: false
  
  # AWS S3配置
  s3:
    # 区域
    region: cn-north-1
    
    # Access Key
    access-key: ${AWS_ACCESS_KEY:}
    
    # Secret Key
    secret-key: ${AWS_SECRET_KEY:}
    
    # Bucket名称
    bucket: your-bucket-name
    
    # CDN域名(可选)
    cdn-domain: https://cdn.example.com
```

## 📊 监控告警配置

### Prometheus配置

```yaml
management:
  endpoints:
    web:
      exposure:
        # 暴露的端点
        include: health,info,prometheus,metrics
    
  metrics:
    export:
      prometheus:
        # 是否启用
        enabled: true
    
    tags:
      # 应用标签
      application: ${spring.application.name}
```

### Zipkin链路追踪配置

```yaml
spring:
  zipkin:
    # Zipkin服务器地址
    base-url: http://localhost:9411
    
    # 采样率(0.0-1.0)
    sampler:
      probability: 1.0
  
  sleuth:
    # 采样率
    sampler:
      probability: 1.0
```

### Grafana告警配置

```yaml
monitoring:
  grafana:
    # 是否启用告警
    alert-enabled: false
    
    # Webhook地址
    webhook-url: https://hooks.slack.com/services/YOUR/WEBHOOK/URL
    
    # 告警阈值
    thresholds:
      # CPU使用率(%)
      cpu-usage: 80
      
      # 内存使用率(%)
      memory-usage: 85
      
      # 响应时间P99(毫秒)
      response-time-p99: 2000
      
      # 错误率(%)
      error-rate: 5
```

## 🌍 国际化配置

```yaml
spring:
  messages:
    # 资源文件基础名
    basename: i18n/messages
    
    # 编码格式
    encoding: UTF-8
    
    # 缓存秒数(-1表示永久缓存)
    cache-duration: -1
    
    #  fallback语言
    fallback-to-system-locale: true
```

资源文件位置: `src/main/resources/i18n/`
- `messages.properties` - 默认语言
- `messages_zh_CN.properties` - 简体中文
- `messages_en_US.properties` - 英文

## 📝 API文档配置

```yaml
springdoc:
  # API文档标题
  api-docs:
    title: Apex Scaffold API
    description: 企业级后端脚手架API文档
    version: 1.0.0
    
  # Swagger UI配置
  swagger-ui:
    # 是否启用
    enabled: true
    
    # 路径
    path: /swagger-ui.html
    
    # 默认展开
    operations-sorter: method
    
    # 标签排序
    tags-sorter: alpha
  
  # Knife4j配置
  knife4j:
    # 是否启用
    enable: true
    
    # 基本认证
    basic:
      enable: false
      username: admin
      password: 123456
```

## 🔄 功能开关

通过以下配置可以按需启用/禁用功能模块:

```yaml
features:
  # JWT认证
  jwt-auth: true
  
  # RBAC权限
  rbac: true
  
  # 多级缓存
  multi-level-cache: true
  
  # 限流熔断
  rate-limit: true
  
  # 消息队列
  message-queue: false  # 不需要时关闭以简化部署
  
  # 链路追踪
  tracing: false  # 开发环境可关闭提升性能
  
  # 操作日志
  operation-log: true
  
  # 监控指标
  monitoring: true
  
  # API文档
  api-docs: true
  
  # 国际化
  i18n: true
```

## 🎯 环境特定配置示例

### 开发环境 (application-dev.yml)

```yaml
spring:
  profiles: dev
  
logging:
  level:
    root: DEBUG
    com.apex: DEBUG

# 关闭不必要的功能提升启动速度
features:
  tracing: false
  monitoring: false
```

### 生产环境 (application-prod.yml)

```yaml
spring:
  profiles: prod
  
logging:
  level:
    root: WARN
    com.apex: INFO
  
  file:
    name: /var/log/apex/application.log

# 启用所有功能
features:
  tracing: true
  monitoring: true
  alert-enabled: true

# 安全加固
jwt:
  secret: ${JWT_SECRET}  # 必须从环境变量读取

spring:
  datasource:
    password: ${DB_PASSWORD}  # 必须从环境变量读取
```

## 📈 性能压测脚本

脚本位置为 `scripts/bench/bench.sh`，依赖 `wrk`。基础用法如下：

```bash
./scripts/bench/bench.sh [base_url] [duration] [threads] [connections]
```

脚本支持以下环境变量：

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `BENCH_SCENARIO` | `health` | 执行场景：`health`、`login` 或 `all` |
| `BENCH_LOGIN_ENABLED` | `false` | 是否允许执行登录场景，支持 `true`/`false`、`1`/`0`、`yes`/`no` |
| `BENCH_USERNAME` | 空 | 登录场景使用的真实用户名 |
| `BENCH_PASSWORD` | 空 | 登录场景使用的真实密码 |

示例：

```bash
BENCH_SCENARIO=health ./scripts/bench/bench.sh http://127.0.0.1:8080 30s 4 200
BENCH_SCENARIO=login BENCH_LOGIN_ENABLED=true BENCH_USERNAME=real-user BENCH_PASSWORD='real-password' ./scripts/bench/bench.sh
BENCH_SCENARIO=all BENCH_LOGIN_ENABLED=true BENCH_USERNAME=real-user BENCH_PASSWORD='real-password' ./scripts/bench/bench.sh
```

安全要求：默认只执行健康检查，不会使用占位密码执行登录压测。登录场景必须显式设置 `BENCH_LOGIN_ENABLED=true`，并通过环境变量提供真实账号和密码；不要把密码写入脚本、文档、命令历史或提交到版本库。完整脚本说明和失败排查见 `docs/sales/PERFORMANCE_REPORT.md`。

## ❓ 常见问题

### Q1: 如何覆盖默认配置?

优先级从高到低:
1. 命令行参数: `--server.port=8081`
2. 环境变量: `SERVER_PORT=8081`
3. `application-{profile}.yml`
4. `application.yml`

### Q2: 配置不生效怎么办?

1. 检查 `spring.profiles.active` 是否正确
2. 查看启动日志确认加载的配置文件
3. 使用 Actuator `/actuator/env` 端点查看当前生效配置

### Q3: 如何加密敏感配置?

推荐使用 Jasypt:

```yaml
# 添加依赖
# <dependency>
#   <groupId>com.github.ulisesbocchio</groupId>
#   <artifactId>jasypt-spring-boot-starter</artifactId>
# </dependency>

# 配置加密
jasypt:
  encryptor:
    password: ${JASYPT_ENCRYPTOR_PASSWORD}

# 使用加密值
spring:
  datasource:
    password: ENC(encrypted_value_here)
```

---

**提示**: 生产环境部署前,请务必审查所有配置项,特别是安全相关的配置!
