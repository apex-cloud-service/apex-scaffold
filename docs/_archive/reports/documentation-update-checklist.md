# 文档更新清单 - Documentation Update Checklist

**创建时间**: 2026-09-05  
**状态**: 待执行  
**基于**: Phase 1 技术栈清单 (tech-stack-inventory.md)

---

## 📋 更新范围

### 高优先级文档（需要立即更新）

#### 1. 架构文档 (12篇)

**技术架构相关 (4篇)**:
- [x] `docs/architecture/03-technical-architecture-detail.md` - ✅ 已在Phase 3修复图表时同步
- [ ] `docs/architecture/03-technical-architecture-quick-ref.md` - 快速参考需更新版本号
- [ ] `docs/architecture/03-technical-architecture-decision.md` - 技术选型决策
- [ ] `docs/architecture/03-technical-architecture-business-value.md` - 商业价值说明

**物理架构相关 (4篇)**:
- [ ] `docs/architecture/02-physical-architecture-detail.md`
- [ ] `docs/architecture/02-physical-architecture-quick-ref.md`
- [ ] `docs/architecture/02-physical-architecture-decision.md`
- [ ] `docs/architecture/02-physical-architecture-business-value.md`

**逻辑架构相关 (4篇)**:
- [ ] `docs/architecture/01-logical-architecture-detail.md`
- [ ] `docs/architecture/01-logical-architecture-quick-ref.md`
- [ ] `docs/architecture/01-logical-architecture-decision.md`
- [ ] `docs/architecture/01-logical-architecture-business-value.md`

#### 2. 技术栈文档 (3篇)

- [ ] `docs/architecture/backend-technology-stack.md` - **核心文档**
- [ ] `docs/architecture/frontend-technology-stack.md`
- [ ] `docs/architecture/infrastructure-components.md`

#### 3. 系统文档 (2篇)

- [ ] `docs/architecture/system-architecture.md` - 系统架构总览
- [ ] `docs/ARCHITECTURE_INDEX.md` - 架构索引（需更新图表链接）

---

## 🔧 需要更新的核心内容

### A. 版本号更新（70+组件）

基于 `tech-stack-inventory.md`，以下版本号需要在文档中更新：

**核心框架**:
```
Java 17
Spring Boot 3.5.15
Spring Framework 6.2.5
Spring Security 6.5.0
Spring WebFlux (Reactor 3.6.13)
```

**数据持久化**:
```
MySQL 8.4.0 + Connector 8.4.0
MyBatis-Plus 3.5.10.1
Druid 1.2.25 ⭐ 重要
Flyway 10.15.0 (V1-V22, 22个迁移脚本)
```

**缓存与搜索**:
```
Redis 7.x + Redisson 3.36.0
Caffeine 3.1.8
Meilisearch 0.17.1 ⭐ 新技术
```

**可观测性**:
```
Loki 3.0.0 + Alloy 1.0.0 ⭐ 新技术
Prometheus + Grafana (66个面板)
Zipkin 2.26.0
Micrometer 1.14.3
```

**对象存储**:
```
MinIO 8.5.15
Volcengine TOS 2.6.1 ⭐ 新增
```

**微服务治理**:
```
Nacos 2025.0.0.0
Sentinel 1.8.9
RocketMQ 5.x (可选)
```

### B. 新增组件说明

以下组件需要在文档中补充说明：

1. **Druid 1.2.25**
   - 角色：数据库连接池 + SQL监控
   - 位置：数据持久化层
   - 特性：主从读写分离、SQL性能监控、DruidDataSource

2. **Volcengine TOS 2.6.1**
   - 角色：火山引擎对象存储
   - 位置：基础设施层
   - 特性：云原生存储、S3兼容API

3. **Meilisearch 0.17.1**
   - 角色：轻量级全文搜索引擎（替代Elasticsearch）
   - 位置：数据层
   - 特性：快速搜索、易部署、低资源消耗

4. **Loki + Alloy**
   - 角色：日志聚合与采集（替代ELK）
   - Loki 3.0.0：日志存储与查询
   - Alloy 1.0.0：日志采集器
   - 特性：轻量级、与Prometheus集成

### C. Profile分级配置

需要在相关文档中说明：

```yaml
basic:      # 基础版
  - JWT认证
  - 适合小型项目

standard:   # 标准版（默认）
  - JWT + RBAC
  - 缓存 + 监控
  - 适合中型企业

enterprise: # 企业版
  - 完整功能
  - MQ + 链路追踪
  - 适合大型系统
```

### D. DDD模块结构

需要在架构文档中更新：

```
apex-scaffold/
├── apex-common              # 公共工具
├── apex-domain              # 领域层
├── apex-infrastructure      # 基础设施层
├── apex-application         # 应用层
├── apex-interfaces          # 接口层
├── apex-example             # 示例模块
├── apex-generator           # 代码生成器
├── apex-license-spring-boot-starter  # License授权
└── apex-license-tools       # ⚠️ 发布时移除
```

---

## 📝 更新模板

### 技术栈文档更新模板

```markdown
## 技术组件清单

### 核心框架
- **Java**: 17 (LTS)
- **Spring Boot**: 3.5.15
- **Spring Framework**: 6.2.5
- **Spring Security**: 6.5.0
- **Spring WebFlux**: Reactor 3.6.13

### 数据持久化
- **MySQL**: 8.4.0 (Connector 8.4.0)
- **MyBatis-Plus**: 3.5.10.1
- **Druid**: 1.2.25 - 连接池 + SQL监控
- **Flyway**: 10.15.0 (22个迁移脚本)

### 缓存与搜索
- **Redis**: 7.x + Redisson 3.36.0
- **Caffeine**: 3.1.8 (本地缓存)
- **Meilisearch**: 0.17.1 (全文搜索)

### 对象存储
- **MinIO**: 8.5.15 (开源)
- **Volcengine TOS**: 2.6.1 (火山云)

### 可观测性
- **监控**: Prometheus + Grafana (66面板)
- **日志**: Loki 3.0.0 + Alloy 1.0.0
- **追踪**: Zipkin 2.26.0
- **指标**: Micrometer 1.14.3

### 微服务治理
- **注册中心**: Nacos 2025.0.0.0
- **限流熔断**: Sentinel 1.8.9
- **消息队列**: RocketMQ 5.x (enterprise profile)
```

---

## ✅ 验证清单

更新完成后，需要验证：

- [ ] 所有版本号与 pom.xml 一致
- [ ] 新增组件（Druid、Volcengine TOS、Meilisearch、Loki+Alloy）已说明
- [ ] Profile分级配置已说明
- [ ] DDD模块结构准确
- [ ] 图表链接有效
- [ ] Markdown格式正确

---

## 🚀 执行建议

**分批执行**：
1. **第一批**（1小时）：3篇技术栈文档 + ARCHITECTURE_INDEX.md
2. **第二批**（2小时）：12篇架构详细文档
3. **第三批**（3-4小时）：其他相关文档（按需）

**优先级**：
- P0: backend-technology-stack.md（最核心）
- P1: 03-technical-architecture系列（4篇）
- P2: ARCHITECTURE_INDEX.md + system-architecture.md
- P3: 其他架构文档

---

## 📊 统计

- **总文档数**: 200+ 个Markdown文件
- **需要更新**: ~15-20篇（核心文档）
- **预计时间**: 6-8小时（全量） / 1-2小时（核心）
- **基准参考**: `docs/reports/tech-stack-inventory.md`

---

**备注**: 本清单基于Phase 1代码审查和Phase 2差异分析的结果生成。
