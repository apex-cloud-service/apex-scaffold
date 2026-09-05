# Apex Scaffold 技术栈完整清单（代码实现基准）

> **生成时间**: 2026-09-05  
> **项目版本**: v1.2.0  
> **数据来源**: pom.xml + docker-compose.yml + application.yml  
> **用途**: 作为SVG图表和文档对齐的基准参考

---

## 📋 核心框架

| 技术 | 版本 | 用途 | 来源 |
|------|------|------|------|
| **Java** | 17 LTS | 语言基础 | pom.xml:46 |
| **Spring Boot** | 3.5.15 | 应用框架 | pom.xml:36,51 |
| **Spring Cloud** | 2025.0.0 | 微服务套件 | pom.xml:52 |
| **Spring Cloud Alibaba** | 2025.0.0.0 | 阿里云套件 | pom.xml:53 |
| **Spring Security** | 6.5.0 | 安全框架 | pom.xml:55 |
| **Spring WebFlux** | 6.x (随Boot 3.5) | 响应式Web | pom.xml:146 |
| **Project Reactor** | 3.6.13 | 响应式核心 | pom.xml:78,365 |

---

## 💾 数据持久化

### 关系型数据库

| 技术 | 版本 | 用途 | 来源 |
|------|------|------|------|
| **MySQL** | 8.4.0 | 主数据库 | pom.xml:60 |
| **MySQL Connector** | 8.4.0 | JDBC驱动 | pom.xml:60,251 |
| **MyBatis-Plus** | 3.5.10.1 | ORM框架 | pom.xml:56,211-232 |
| **Druid** | 1.2.25 | 连接池+监控 | pom.xml:58,246 |
| **Dynamic Datasource** | 4.3.1 | 读写分离 | pom.xml:59,236 |
| **PageHelper** | 2.1.1 | 分页插件 | pom.xml:57,241 |
| **Flyway** | 10.15.0 | 数据库迁移 | pom.xml:76,424-431 |
| **P6Spy** | 3.9.1 | SQL监控 | pom.xml:119,399 |

### 缓存

| 技术 | 版本 | 用途 | 来源 |
|------|------|------|------|
| **Redis** | 7.x | 分布式缓存 | docker-compose.yml:148 |
| **Redisson** | 3.36.0 | Redis客户端 | pom.xml:61,255 |
| **Caffeine** | 3.1.8 | 本地缓存（L1） | pom.xml:84,394 |
| **Commons Pool2** | 2.12.0 | 连接池 | pom.xml:79,374 |

### 全文搜索

| 技术 | 版本 | 用途 | 来源 |
|------|------|------|------|
| **Meilisearch** | 0.17.1 | 全文搜索引擎 | pom.xml:82,557 |

---

## ☁️ 微服务治理

| 技术 | 版本 | 用途 | 来源 |
|------|------|------|------|
| **Nacos** | 2025.0.0.0 | 配置中心+服务发现 | pom.xml:53,195-201 |
| **Sentinel** | 1.8.9 | 流控+熔断+降级 | pom.xml:120-121,190,405-416 |
| **RocketMQ** | 2.3.3 (客户端) / 5.x (服务端) | 消息队列 | pom.xml:62,265 / 可选 |
| **Resilience4j** | 2.2.0 | 熔断器+重试 | pom.xml:85,447-460 |
| **Bucket4j** | 8.7.0 | 令牌桶限流 | pom.xml:83,389 |
| **Spring Cloud Bootstrap** | 5.0.0 | Bootstrap配置加载 | pom.xml:54,204 |

---

## 📊 可观测性

### 监控指标

| 技术 | 版本 | 用途 | 来源 |
|------|------|------|------|
| **Micrometer** | 1.14.1 | 指标门面 | pom.xml:72,182,339 |
| **Prometheus** | 2.x | 指标采集 | 集成通过Micrometer |
| **Grafana** | 10.x | 监控大盘 | 外部部署 |
| **Sentinel Prometheus Exporter** | 1.8.9 | Sentinel指标导出 | pom.xml:121,415 |
| **Simpleclient HTTPServer** | 0.16.0 | Prometheus HTTP服务器 | pom.xml:124,420 |

### 链路追踪

| 技术 | 版本 | 用途 | 来源 |
|------|------|------|------|
| **Micrometer Tracing** | 1.5.11 | 追踪门面 | pom.xml:73,345 |
| **Zipkin** | 2.26.0 | 链路追踪服务端 | pom.xml:75,359 |
| **Zipkin Reporter Brave** | 3.4.0 | Zipkin上报 | pom.xml:74,350-356 |

### 日志聚合

| 技术 | 版本 | 用途 | 来源 |
|------|------|------|------|
| **Logstash Encoder** | 7.4 | 结构化日志 | pom.xml:81,384 |
| **Loki** | 3.0.0 | 日志聚合 | docker-compose.yml:104-106 |
| **Alloy** | 1.0.0 | 日志采集器 | docker-compose.yml提及 |

---

## 🔧 工具库

| 技术 | 版本 | 用途 | 来源 |
|------|------|------|------|
| **Lombok** | 1.18.36 | 代码生成 | pom.xml:70,324 |
| **MapStruct** | 1.5.5.Final | 对象映射 | pom.xml:68,296-304 |
| **Hutool** | 5.8.36 | Java工具类 | pom.xml:67,291 |
| **JWT (JJWT)** | 0.12.6 | Token生成解析 | pom.xml:69,307-321 |
| **SpringDoc** | 2.8.1 | OpenAPI文档 | pom.xml:71,329-336 |
| **Javassist** | 3.30.2-GA | 字节码操作 | pom.xml:80,379 |

---

## 📦 对象存储

| 技术 | 版本 | 用途 | 来源 |
|------|------|------|------|
| **MinIO** | 8.6.0 | 对象存储（S3兼容） | pom.xml:65,281 |
| **Volcengine TOS** | 2.6.1 | 火山引擎对象存储 | pom.xml:66,286 |

---

## 🔗 三方集成

| 技术 | 版本 | 用途 | 来源 |
|------|------|------|------|
| **WeChat Java SDK (miniapp)** | 4.8.0 | 微信小程序 | pom.xml:64,271 |
| **WeChat Java SDK (mp)** | 4.8.0 | 微信公众号 | pom.xml:64,277 |
| **EasyExcel** | 3.3.4 | Excel导入导出 | pom.xml:546 |
| **Commons IO** | 2.16.1 | 文件操作 | pom.xml:553 |

**注**: 微信支付SDK已于v1.2.0移除（pom.xml:63注释）

---

## 🧪 测试框架

| 技术 | 版本 | 用途 | 来源 |
|------|------|------|------|
| **JUnit Jupiter** | 5.10.2 | 单元测试 | pom.xml:87,464-479 |
| **JUnit Platform** | 1.10.2 | 测试平台 | pom.xml:88,482-496 |
| **Mockito** | 5.11.0 | Mock框架 | pom.xml:89,499-508 |
| **AssertJ** | 3.25.3 | 断言库 | pom.xml:101,529 |
| **ArchUnit** | 1.3.0 | 架构测试 | pom.xml:100,523 |
| **Reactor Test** | 3.6.13 | 响应式测试 | pom.xml:78,370 |
| **Embedded Redis** | 1.4.3 | 内嵌Redis | pom.xml:86,442 |
| **Spring Boot Test** | 3.5.15 | 集成测试 | pom.xml:512 |
| **Spring Security Test** | 6.5.0 | 安全测试 | pom.xml:518 |

---

## 🏗️ 构建工具

| 技术 | 版本 | 用途 | 来源 |
|------|------|------|------|
| **Maven** | 3.9.16 | 构建工具 | pom.xml:107 |
| **Maven Compiler** | 3.13.0 | 编译插件 | pom.xml:104,959 |
| **Maven Surefire** | 3.2.5 | 单元测试 | pom.xml:103,1058 |
| **Maven Failsafe** | 3.2.5 | 集成测试 | pom.xml:103,1081 |
| **JaCoCo** | 0.8.12 | 代码覆盖率 | pom.xml:102,1013 |
| **Maven Enforcer** | 3.4.1 | 构建约束 | pom.xml:117,1129 |
| **Spotless** | 2.43.0 | 代码格式化 | pom.xml:118,1152 |
| **Maven PMD** | 3.19.0 | 代码规范检查 | pom.xml:116,916 |
| **P3C PMD** | 2.1.1 | 阿里编码规约 | pom.xml:125,952 |
| **SonarQube** | 3.11.0.3922 | 代码质量 | pom.xml:106,1007 |
| **Maven Javadoc** | 3.6.3 | 文档生成 | pom.xml:105,1104 |

---

## 🐳 容器与部署

### Docker镜像

| 服务 | 镜像 | 版本 | 来源 |
|------|------|------|------|
| **apex-scaffold** | 自构建 | 1.2.0 | docker-compose.yml:40 |
| **MySQL** | mysql | 8.0 | docker-compose.yml:123 |
| **Redis** | redis | 7-alpine | docker-compose.yml:148 |
| **Meilisearch** | (可选) | latest | docker-compose.yml提及 |

### 端口配置

| 服务 | 端口 | 用途 | 来源 |
|------|------|------|------|
| **应用业务端口** | 8080 | HTTP API | docker-compose.yml:43 |
| **应用管理端口** | 8081 | Actuator | docker-compose.yml:44 |
| **MySQL** | 3306 | 数据库 | docker-compose.yml:133 |
| **Redis** | 6379 | 缓存 | docker-compose.yml:62 |
| **Meilisearch** | 7700 | 全文搜索 | docker-compose.yml:102 |
| **Loki** | 3100 | 日志聚合 | docker-compose.yml:106 |

---

## 📦 DDD 模块结构

| 模块 | 职责 | 来源 |
|------|------|------|
| **apex-common** | 公共注解、工具类、DTO | pom.xml:131 |
| **apex-domain** | 领域模型、仓储接口、领域服务 | pom.xml:135 |
| **apex-infrastructure** | 持久化、缓存、MQ、外部服务 | pom.xml:136 |
| **apex-application** | 应用服务、业务编排 | pom.xml:137 |
| **apex-interfaces** | REST API、路由、过滤器 | pom.xml:138 |
| **apex-example** | 示例模块 | pom.xml:139 |
| **apex-generator** | 代码生成器 | pom.xml:140 |
| **apex-license-spring-boot-starter** | License授权 | pom.xml:132 |
| **apex-license-tools** | License工具（发布时移除） | pom.xml:134注释 |

---

## 🎯 Profile 分级

| Profile | 功能特性 | 用途 | 来源 |
|---------|---------|------|------|
| **basic** | JWT认证 | 小型项目 | pom.xml:616-718 |
| **standard** | JWT + RBAC + 缓存 + 监控 | 中型企业（**默认**） | pom.xml:721-736 |
| **enterprise** | 完整功能 + MQ + 链路追踪 | 大型系统 | pom.xml:739-751 |

**功能矩阵**:

| 功能 | basic | standard | enterprise |
|------|-------|----------|------------|
| JWT认证 | ✅ | ✅ | ✅ |
| RBAC权限 | ❌ | ✅ | ✅ |
| 缓存 | ❌ | ✅ | ✅ |
| 消息队列 | ❌ | ❌ | ✅ |
| 监控 | ❌ | ✅ | ✅ |
| 链路追踪 | ❌ | ❌ | ✅ |

**来源**: pom.xml:623-629 (basic), 729-735 (standard), 743-749 (enterprise)

---

## 🔒 环境变量配置

### 核心环境变量

| 变量名 | 默认值 | 用途 | 来源 |
|--------|--------|------|------|
| **SPRING_PROFILES_ACTIVE** | prod,enterprise | 激活Profile | docker-compose.yml:47 |
| **DB_MASTER_URL** | jdbc:mysql://mysql:3306/apex_data | 主库地址 | docker-compose.yml:53 |
| **DB_MASTER_USERNAME** | root | 主库用户名 | docker-compose.yml:54 |
| **DB_MASTER_PASSWORD** | apex-root | 主库密码 | docker-compose.yml:55 |
| **REDIS_HOST** | redis | Redis地址 | docker-compose.yml:61 |
| **REDIS_PASSWORD** | apex-redis-password | Redis密码 | docker-compose.yml:65 |
| **JWT_SECRET** | (必须32字符以上) | JWT密钥 | docker-compose.yml:67 |
| **MEILISEARCH_ENABLED** | true | 启用搜索 | docker-compose.yml:101 |
| **LOKI_ENABLED** | true | 启用日志聚合 | docker-compose.yml:105 |

---

## 📊 关键配置参数

### JVM参数

```bash
-Xms512m -Xmx1024m 
-XX:+UseG1GC 
-XX:MaxGCPauseMillis=200 
-XX:MetaspaceSize=256m 
-XX:MaxMetaspaceSize=512m 
-XX:+HeapDumpOnOutOfMemoryError 
-XX:HeapDumpPath=/app/logs/heapdump.hprof
```

**来源**: docker-compose.yml:51

### Netty参数

- 连接超时: 5000ms
- 读取超时: 30s
- 写入超时: 60s
- 空闲超时: 20s
- HTTP/2: ✅ 启用
- GZip压缩: ✅ 启用（级别6，最小1KB）

**来源**: application.yml:20-50

### 数据库连接池

- 最大连接数: 40 (Druid) / 20 (HikariCP)
- 最小空闲: 15 (Druid) / 5 (HikariCP)
- 连接超时: 3000ms (Druid) / 30000ms (HikariCP)

**来源**: pom.xml提及Druid配置

---

## ⚠️ 重要变更记录

| 变更 | 版本 | 说明 |
|------|------|------|
| 移除微信支付SDK | v1.2.0 | weixin-java-pay已移除，后续按需引入 | 
| 新增Meilisearch | v1.2.0 | 轻量级全文搜索替代Elasticsearch |
| 新增Loki+Alloy | v1.2.0 | 轻量级日志聚合替代ELK |

---

## 🎯 核心特性总结

1. **响应式架构**: Spring WebFlux + Project Reactor
2. **DDD分层**: 4层架构 + 依赖倒置
3. **读写分离**: Dynamic Datasource + 主从复制
4. **二级缓存**: Caffeine (L1) + Redis (L2)
5. **微服务治理**: Nacos + Sentinel + RocketMQ(可选)
6. **可观测性**: Prometheus + Grafana + Zipkin + Loki
7. **商业化分级**: basic / standard / enterprise
8. **容器化部署**: Docker Compose / Kubernetes(可选)

---

**清单生成时间**: 2026-09-05  
**审查范围**: pom.xml + docker-compose.yml + application.yml + 模块结构  
**用途**: SVG图表和文档对齐的准确基准  
**维护**: 代码变更时同步更新
