# 性能监控和基准测试指南

## 概述

本文档提供 Apex Scaffold 项目的性能监控、基准测试和优化验证指南。

---

## 一、性能优化总结

### 已完成的性能优化 (2026-09-20)

#### 1. 数据库索引优化（历史实施记录）
- **脚本位置**:
  - MySQL: `apex-system/src/main/resources/db/migration/mysql/V43__performance_optimization_indexes.sql`
  - PostgreSQL: `apex-system/src/main/resources/db/migration/postgresql/V43__performance_optimization_indexes.sql`
- **优化内容**: 历史记录为添加复合索引，覆盖核心业务表
- **效果口径**: 本节及第四节的百分比是历史参考目标，**不是 2026-11-10 当前实测结果**；应以真实压测和慢查询数据重新验收

**核心索引**:
- 消息表: `idx_recipient_status_sendtime`, `idx_sender_sendtime`
- 部门表: `idx_parent_status_sort`, `idx_sync_external`, `idx_tenant_status`
- 员工表: `idx_dept_status`, `idx_user_tenant`, `idx_code_tenant`
- 审批流程: `idx_initiator_status_time`, `idx_platform_instance`
- 日历事件: `idx_time_range_status`, `idx_organizer_time`, `idx_room_time`

#### 2. 缓存策略优化
- **配置位置**: `apex-infrastructure-cache/src/main/java/com/apex/scaffold/infrastructure/cache/config/MultiLevelCacheConfig.java`
- **架构**: L1 (Caffeine 本地缓存) + L2 (Redis 分布式缓存)
- **效果口径**: 历史目标为提升缓存命中率；当前命中率需以
  `apex_cache_gets_total{result="hit|miss"}` 实测，不能引用未复现的历史数字
- **TTL 口径**: 当前 `MultiLevelCacheConfig` 对本地缓存统一配置 `expireAfterWrite = 300s`；
  下表按缓存名的差异化 TTL **未实现**，为历史规划记录，实际以该配置类为准

**缓存规划（未实现的差异化 TTL，仅记录）**:
| 缓存名称 | 规划 TTL | 适用场景 |
|---------|-----|---------|
| user-info | 1小时 | 用户基本信息 |
| role-info | 1小时 | 角色权限信息 |
| menu-tree | 30分钟 | 菜单树结构 |
| dept-tree | 30分钟 | 部门树结构 |
| message-template | 2小时 | 消息模板 |
| table-metadata | 10分钟 | 数据库表元数据 |
| saml-certificates | 24小时 | SAML证书 |

#### 3. 代码生成性能优化（历史记录）
- **历史位置**: 原 `apex-application` 模块，现已退出 reactor；当前代码生成能力位于
  `apex-generator`，不要按旧模块路径检索
- **优化内容**（历史）:
  - 表元数据缓存 (`@Cacheable`, 10 分钟 TTL)
  - 模板编译缓存 (`ConcurrentHashMap`)
  - Reactor 级别缓存 (`.cache(Duration.ofMinutes(10))`)
- **效果口径**: 历史目标，当前未复测

**缓存管理API**:
```java
// 清除模板缓存
codeGenerationService.clearTemplateCache();

// 获取缓存统计
int size = codeGenerationService.getTemplateCacheSize();
```

#### 4. SSO 认证流程优化（历史记录）
- **历史位置**: 原 `apex-application` 模块，现已退出 reactor；当前认证实现位于
  `apex-auth` 与 `apex-auth-service`
- **优化内容**（历史）:
  - X.509 证书解析缓存 (PublicKey + X509Certificate)
  - 双重缓存: ConcurrentHashMap + Spring Cache (`@Cacheable`)
  - 避免重复解析证书，减少 CPU 开销
- **效果口径**: 历史目标，当前未复测

**缓存管理API**:
```java
// 清除证书缓存
samlProtocolAdapter.clearCertificateCache();

// 获取缓存统计
Map<String, Integer> stats = samlProtocolAdapter.getCacheStatistics();
```

---

## 二、性能监控方案

### 2.1 Prometheus + Grafana 监控

#### Prometheus 配置

**1. 确认依赖**（网关 `apex-gateway/pom.xml` 及各服务运行模块已包含
`spring-boot-starter-actuator` 与 `micrometer-registry-prometheus`）：
```xml
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

**2. 启用Actuator端点** (在 `application.yml`):
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  metrics:
    export:
      prometheus:
        enabled: true
    tags:
      application: apex-scaffold
```

**3. Prometheus 配置**：实际配置位于
`config/monitoring/prometheus/prometheus.yml`，按服务拆分为独立 job（如
`apex-gateway`、`apex-auth-service`、`apex-system-service` 等），所有应用指标带
`application="apex-scaffold"` 与 `module="..."` 标签。示例：
```yaml
scrape_configs:
  - job_name: 'apex-gateway'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['host.containers.internal:8080']
        labels:
          application: 'apex-scaffold'
          module: 'gateway'
```

#### Grafana Dashboard

**推荐监控指标**:
- JVM 内存使用: `jvm_memory_used_bytes`
- GC 暂停时间: `jvm_gc_pause_seconds_max`（最大暂停）及
  `jvm_gc_pause_seconds_sum` / `jvm_gc_pause_seconds_count`（平均值与频率）
- HTTP 请求量: `apex_api_request_count_total`
- HTTP 请求延迟: `apex_api_response_time_seconds_bucket`
- 慢 SQL: `apex_sql_slow_count_total` / `apex_sql_slow_max_ms`
- 数据库连接池: `hikaricp_connections_active`（prod 视配置可能为 `druid_*`）
- 缓存命中率: `apex_cache_gets_total{result="hit|miss"}`
- 线程池队列: `executor_queue_remaining`（如对应执行器已注册）

**Grafana Dashboard模板**:
- Spring Boot 2.x / Webflux: Dashboard ID `12900`
- JVM (Micrometer): Dashboard ID `4701`

### 2.2 应用性能监控 (APM)

#### 推荐工具
- **SkyWalking**: 国产开源APM,支持分布式追踪
- **Zipkin**: 轻量级分布式追踪系统
- **Pinpoint**: 企业级APM,无侵入

#### SkyWalking 快速接入
```bash
# 下载SkyWalking Agent
wget https://archive.apache.org/dist/skywalking/java-agent/9.0.0/apache-skywalking-java-agent-9.0.0.tgz
tar -zxvf apache-skywalking-java-agent-9.0.0.tgz

# 启动应用时添加Agent
java -javaagent:/path/to/skywalking-agent.jar \
     -Dskywalking.agent.service_name=apex-gateway \
     -Dskywalking.collector.backend_service=localhost:11800 \
     -jar apex-gateway/target/apex-gateway.jar
```

---

## 三、JMH基准测试

### 3.1 添加JMH依赖

在被测模块（例如 `apex-common` 或具体业务库）的 `pom.xml` 添加测试作用域依赖；
不要引用已退出 reactor 的 `apex-application`：
```xml
<dependency>
    <groupId>org.openjdk.jmh</groupId>
    <artifactId>jmh-core</artifactId>
    <version>1.37</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.openjdk.jmh</groupId>
    <artifactId>jmh-generator-annprocess</artifactId>
    <version>1.37</version>
    <scope>test</scope>
</dependency>
```

### 3.2 基准测试示例

**缓存性能基准测试**:
```java
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Thread)
@Fork(1)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
public class CacheBenchmark {

    private CodeGenerationService codeGenerationService;
    
    @Setup
    public void setup() {
        // 初始化服务
    }
    
    @Benchmark
    public void testTableMetadataCache() {
        // 测试表元数据缓存性能
        codeGenerationService.getTableColumns(1L, "sys_user").block();
    }
    
    @Benchmark
    public void testTemplateCompilationCache() {
        // 测试模板编译缓存性能
        codeGenerationService.generate(1L).block();
    }
}
```

**运行基准测试**:
```bash
mvnd -pl <被测模块> -Dtest=CacheBenchmark test
```

---

## 四、性能验证清单

### 4.1 数据库性能验证

**1. 查看索引是否创建成功**:
```sql
-- MySQL
SHOW INDEX FROM off_message;
SHOW INDEX FROM off_department;
SHOW INDEX FROM office_approval_process;

-- PostgreSQL
\d+ off_message
\d+ off_department
\d+ office_approval_process
```

**2. 使用EXPLAIN分析查询计划**:
```sql
-- 优化前后对比
EXPLAIN ANALYZE 
SELECT * FROM off_message 
WHERE recipient_id = 1 AND status = 'UNREAD' 
ORDER BY send_time DESC 
LIMIT 20;
```

**3. 监控慢查询日志**:
```sql
-- MySQL
SET GLOBAL slow_query_log = 'ON';
SET GLOBAL long_query_time = 1;

-- 查看慢查询
SELECT * FROM mysql.slow_log ORDER BY query_time DESC LIMIT 10;
```

### 4.2 缓存性能验证

**1. 查看缓存统计**：项目当前没有对外暴露 `CacheAdminController`，不要按该示例
新增管理端接口。应通过 Actuator/Micrometer 指标
`apex_cache_gets_total{result="hit|miss"}` 计算命中率，或在受控的诊断环境中使用
Caffeine/Redis 自身指标。

**2. Redis缓存键监控**:
```bash
# 查看所有缓存键
redis-cli KEYS "*"

# 查看缓存键TTL
redis-cli TTL "user-info::1"

# 查看缓存命中统计
redis-cli INFO stats
```

**3. 缓存命中率计算**:
```
命中率 = hits / (hits + misses) * 100%
```

### 4.3 应用性能验证

**1. JVM监控**:
```bash
# 查看GC统计
jstat -gc <pid> 1000

# 生成堆转储
jmap -dump:format=b,file=heap.bin <pid>

# 分析线程栈
jstack <pid> > thread_dump.txt
```

**2. HTTP接口性能测试**:
```bash
# 使用wrk进行压测
wrk -t12 -c400 -d30s http://localhost:8080/api/messages

# 使用ab进行压测
ab -n 10000 -c 100 http://localhost:8080/api/messages
```

**3. 关键指标对比**:
> 下表为 2026-09-20 历史参考值/示例，**不是 2026-11-10 当前实测结果**。当前验收须重新
> 执行压测并记录真实 P95、错误率与缓存命中率，不能直接引用历史数字。

| 指标 | 历史参考优化前 | 历史参考优化后 | 历史参考提升 |
|------|-------|-------|------|
| 收件箱查询 (P95) | 200ms | 80ms | 60% |
| 部门树查询 (P95) | 150ms | 50ms | 67% |
| 代码生成 (P95) | 3000ms | 1500ms | 50% |
| SAML认证 (P95) | 500ms | 300ms | 40% |
| 缓存命中率 | 45% | 85% | +40% |

---

## 五、性能调优建议

### 5.1 JVM调优参数

**推荐JVM参数** (适用于4核8G环境):
```bash
java -server \
  -Xms2g -Xmx2g \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=200 \
  -XX:+HeapDumpOnOutOfMemoryError \
  -XX:HeapDumpPath=/var/logs/heap_dump.hprof \
  -Xlog:gc*:file=/var/logs/gc.log:time,uptime,level,tags:filecount=5,filesize=20m \
  -jar apex-gateway/target/apex-gateway.jar
```

### 5.2 连接池调优

**HikariCP配置**:
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
      leak-detection-threshold: 60000
```

**Redis连接池配置**:
```yaml
spring:
  data:
    redis:
      lettuce:
        pool:
          max-active: 20
          max-idle: 10
          min-idle: 5
          max-wait: 2000ms
```

### 5.3 WebFlux调优

**Reactor Netty EventLoop 数量**（`spring.reactor.netty.*` 不是 Spring Boot 配置命名空间，项目经系统属性控制，见 apex-system `application.yml` 相关注释）：

```bash
# 以 JVM 系统属性方式启动
java -Dreactor.netty.ioWorkerCount=8 -jar apex-system-service-*.jar
```

---

## 六、问题排查指南

### 6.1 性能问题排查步骤

1. **确认问题**: 收集慢日志、用户反馈、监控告警
2. **定位瓶颈**: 使用APM工具分析调用链,找出慢的环节
3. **分析原因**: 数据库慢查询? 缓存未命中? 外部服务超时?
4. **制定方案**: 添加索引? 调整缓存? 异步化? 限流降级?
5. **验证效果**: 压测对比、监控指标、用户反馈

### 6.2 常见性能问题

**问题1: 数据库查询慢**
- 检查是否缺少索引 (`EXPLAIN` 分析)
- 检查是否存在全表扫描
- 检查是否有JOIN过多的情况
- 考虑分页、分库分表

**问题2: 缓存命中率低**
- 检查缓存键是否正确
- 检查TTL是否过短
- 检查是否有缓存穿透/击穿
- 考虑预热热点数据

**问题3: 内存泄漏**
- 使用MAT分析堆转储
- 检查是否有大对象未释放
- 检查是否有ThreadLocal未清理
- 检查缓存是否有上限

**问题4: 线程阻塞**
- 使用jstack分析线程栈
- 检查是否有死锁
- 检查是否有阻塞IO未隔离
- 检查数据库连接池是否耗尽

---

## 七、参考资料

- [Spring Boot Actuator文档](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)
- [Prometheus监控指南](https://prometheus.io/docs/introduction/overview/)
- [Grafana Dashboard配置](https://grafana.com/docs/grafana/latest/)
- [JMH基准测试教程](https://openjdk.org/projects/code-tools/jmh/)
- [HikariCP性能优化](https://github.com/brettwooldridge/HikariCP/wiki/About-Pool-Sizing)
- [Reactor性能调优](https://projectreactor.io/docs/core/release/reference/#faq.wrap-blocking)

---

## 更新日志

- 2026-09-20: 初始版本，记录数据库索引、缓存策略、代码生成与 SSO 认证优化
- 2026-11-10: 修正退役模块路径、GC/JVM 参数、真实指标名与历史性能数据口径
