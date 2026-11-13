# 02 网络物理部署架构 - 详细说明

> 文档版本: v1.1\
> 创建时间: 2026-09-04\
> 最近更新: 2026-10-28（标注历史单体拓扑，指向当前微服务架构）\
> 适用版本: apex-scaffold v1.3.1

> **⚠️ 架构演进说明**：本文档部分拓扑和配置示例描述的是**单体部署的历史形态**（单一 `apex-app` 实例，业务端口 8080、管理端口 8081）。当前运行架构已演进为 **网关 + 六个业务服务 + 只读查询聚合服务 + 任务代理/脱敏服务** 的微服务拆分，各服务独立端口（gateway:8080、auth:8081、system:8082/管理 8083、workflow:8093、office:8084、file:8085、integration:8094/管理 8097、aggregation:8098、job:8095、masking:8096）。当前架构以 [system-architecture.md](system-architecture.md) §1.2 运行拓扑和 §3.2 组件职责与端口为准，以下单体示例仅作历史参考。

## 架构图

![Apex Scaffold 网络物理部署架构图](../diagrams/02_physical_architecture.svg)

## 概述

本文档详细说明 Apex Scaffold 的网络物理部署架构，涵盖从客户端到基础设施的完整拓扑。架构设计基于以下原则：

- **Podman Compose 默认栈**：本地开发和测试环境使用 `podman-compose.yml` 和 `podman-compose.infra.yml` 一键启动

- **Kubernetes 扩展拓扑**：生产环境可选择 K8s 部署，支持 HPA、滚动发布、ConfigMap 等云原生能力

- **能力分级标注**：明确区分"默认启用"、"可选启用"、"建议拓扑"，避免将未验证能力描述为既定事实

- **配置优先**：所有组件状态以 `podman-compose.yml`、`application.yml` 和 `pom.xml` 为准

***

## 五层拓扑结构

### 1. 🌐 客户端层 (Internet)

**职责**：用户和外部系统接入点

| 节点      | 说明                      | 协议/端口       |
| ------- | ----------------------- | ----------- |
| Web 浏览器 | Vue 3 + Element Plus 前端 | HTTPS (443) |
| 移动端 APP | H5 / 小程序                | HTTPS (443) |
| 第三方系统   | OpenAPI 对接              | HTTPS (443) |
| 运维人员    | SSH / kubectl 运维访问      | SSH (22)    |
| CDN 加速  | 静态资源缓存                  | HTTPS (443) |
| DNS     | 域名解析                    | UDP (53)    |

**流量入口**：

- 前端静态资源：`CDN → Nginx` (生产) / `Vite Dev Server` (开发)

- API 请求：`Client → WAF → Nginx/ALB → API Gateway → Application`

***

### 2. 🛡️ 网络安全层 (DMZ)

**职责**：流量过滤、负载均衡、认证鉴权

| 节点          | 说明            | 部署方式                               |
| ----------- | ------------- | ---------------------------------- |
| WAF 防火墙     | SQL 注入、XSS 防护 | 云厂商 WAF / ModSecurity              |
| Nginx / ALB | HTTPS 终止、负载均衡 | K8s Ingress / ALB                  |
| API 网关      | 限流、熔断、鉴权      | 应用内置 (WebFlux)                     |
| Sentinel    | 流量治理中心        | Compose: `sentinel-dashboard` (可选) |
| SSO / IAM   | 统一身份认证        | 可选外部集成                             |

**安全策略**：

- SSL/TLS 1.3 强制加密

- JWT Bearer Token 无状态认证

- Resilience4j 熔断器：`CircuitBreaker`（路由级熔断回退）

- CORS 跨域控制：`application.yml` 配置白名单

**路由规则示例**：

```yaml
# Nginx upstream
upstream apex-backend {
  server apex-app-1:8080 weight=1 max_fails=3 fail_timeout=30s;
  server apex-app-2:8080 weight=1 max_fails=3 fail_timeout=30s;
  keepalive 64;
}
```

***

### 3. ⚙️ 应用服务层 (K8s Pods / Podman)

**职责**：业务逻辑处理、响应式编排

| 节点                  | 说明                      | 资源配置                            |
| ------------------- | ----------------------- | ------------------------------- |
| Apex Application #1 | Spring Boot WebFlux 主实例 | 按环境配置 (见下方)                     |
| Apex Application #2 | 水平扩展 Pod                | K8s HPA 自动伸缩                    |
| Apex Application #N | 多实例扩展 (建议)              | 滚动发布 (建议)                       |
| 消息消费者               | RocketMQ Consumer (可选)  | `APEX_MQ_CONSUMER_ENABLED=true` |

**部署模式对比**：

| 能力    | Podman Compose             | Kubernetes           | 状态  |
| ----- | -------------------------- | -------------------- | --- |
| 基础运行  | ✅ 默认单实例                    | ✅ Deployment         | 已验证 |
| 多实例扩展 | 手动 `scale`                 | ✅ HPA                | 建议  |
| 滚动发布  | ❌                          | ✅ RollingUpdate      | 建议  |
| 配置管理  | `.env` / `application.yml` | ✅ ConfigMap / Secret | 建议  |
| 服务发现  | Podman 网络 DNS              | ✅ Service / Ingress  | 建议  |

**端口分离**：

- 业务端口：`8080` (对外)

- 管理端口：`8081` (`127.0.0.1` 绑定，K8s 可配置 `0.0.0.0`)

- 健康检查：`/actuator/health/liveness`、`/actuator/health/readiness`

- 指标采集：`/actuator/prometheus`

**资源配置示例** (需按环境实测)：

```yaml
# Kubernetes Deployment (示例，非默认)
resources:
  requests:
    memory: "512Mi"
    cpu: "500m"
  limits:
    memory: "1Gi"
    cpu: "1000m"
```

**RocketMQ 消费者状态**：

- 默认关闭：`APEX_MQ_CONSUMER_ENABLED=false`

- 启用条件：需配置 `ROCKETMQ_NAME_SERVER_ADDR` 并将变量改为 `true`

***

### 4. 💾 数据存储层 (持久化 · 高可用集群)

**职责**：业务数据、缓存、对象存储、消息队列、全文检索

| 组件            | 镜像/版本                   | 默认状态 | 生产建议                  |
| ------------- | ----------------------- | ---- | --------------------- |
| MySQL 9.0     | `mysql:9.0`             | ✅ 启用 | 主从复制 / MGR / 云 RDS    |
| Redis Cluster | `redis:7-alpine`        | ✅ 启用 | Sentinel / Cluster 模式 |
| MinIO / TOS   | `minio/minio` / 火山云 TOS | ✅ 启用 | 分布式 MinIO / 云对象存储     |
| RocketMQ      | `apache/rocketmq:5.3.1` | 可选   | 主从 Broker / Dledger   |
| Meilisearch   | `getmeili/meilisearch`  | ✅ 启用 | 多副本部署                 |

**MySQL 配置** (`podman-compose.infra.yml`):

```yaml
mysql:
  image: mysql:9.0
  environment:
    MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD}
    MYSQL_DATABASE: apex_data
  volumes:
    - mysql-data:/var/lib/mysql
  ports:
    - "3306:3306"
```

**Redis 配置**：

- Compose 默认：单节点 `redis:7-alpine`

- 生产建议：Redis Sentinel (1 主 2 从) 或 Redis Cluster (3 主 3 从)

**RocketMQ 架构** (可选启用)：

- NameServer：服务发现

- Broker Master：主写

- Broker Slave：主从同步 (生产需配置)

- 持久化：Compose 本地无持久化卷，生产需挂载 `commitlog`、`consumequeue`

**Meilisearch 配置**：

- 默认启用：`MEILISEARCH_ENABLED=true`

- 用途：日志归集、业务数据全文检索

- 端点：`http://meilisearch:7700`

***

### 5. 🔍 运维监控层 (可观测性)

**职责**：指标采集、日志聚合、链路追踪、告警通知

| 组件           | 镜像/版本                           | 默认状态 | 数据源                    |
| ------------ | ------------------------------- | ---- | ---------------------- |
| Prometheus   | `prom/prometheus:v2.53.0`       | ✅ 启用 | `/actuator/prometheus` |
| Grafana      | `grafana/grafana`               | ✅ 启用 | Prometheus + Loki      |
| Loki / Alloy | `grafana/loki`, `grafana/alloy` | ✅ 启用 | 应用日志推送                 |
| Zipkin       | `openzipkin/zipkin:3.4`         | ✅ 启用 | Brave 链路上报             |
| AlertManager | `prom/alertmanager`             | ✅ 启用 | Prometheus 告警规则        |
| CI/CD        | Jenkins / ArgoCD                | 建议   | 按组织流水线接入               |

**Prometheus 抓取配置** (`prometheus.yml`):

```yaml
scrape_configs:
  - job_name: 'apex-system'
    static_configs:
      - targets: ['apex-app:8081']
    metrics_path: '/actuator/prometheus'
    scrape_interval: 15s
```

**Grafana Dashboard**：

- 配置路径：`config/monitoring/grafana/apex-dashboard.json`

- 面板分组：JVM、HTTP、数据库、缓存、中间件、业务指标

- 数据源：`datasources.yml` 自动加载

**Loki 日志流**：

- Alloy (原 Promtail) 采集应用日志

- 日志格式：JSON 结构化日志，包含 `traceId`、`spanId`

- 查询：Grafana Explore → LogQL

**告警渠道**：

- 飞书 Webhook

- 邮件 SMTP

- 短信 (需外部对接)

**CI/CD 集成建议**：

- Jenkins：使用 Podman 构建 OCI 镜像 → 推送镜像仓库 → 触发 K8s 滚动更新

- ArgoCD：GitOps 声明式部署，监听 Git 仓库变更自动同步

***

## 网络连接关系

### 客户端 → 安全层

```
Web/Mobile/API → HTTPS (443) → WAF → Nginx/ALB → API Gateway
```

### 安全层 → 应用层

```
Nginx/ALB → HTTP (8080) → Apex Application (内网负载均衡)
```

### 应用层 → 数据层

```
Apex Application → JDBC (3306) → MySQL
Apex Application → Redis (6379) → Redis
Apex Application → HTTP (9000) → MinIO
Apex Application → TCP (9876) → RocketMQ NameServer (可选)
Apex Application → HTTP (7700) → Meilisearch
```

### 应用层 → 监控层

```
Apex Application:8081 → Prometheus (指标拉取)
Apex Application → Loki (日志推送，通过 Alloy)
Apex Application → Zipkin (链路上报)
```

***

## Podman Compose 默认栈启动流程

### 1. 启动基础设施

```bash
cd /Users/work/apex-scaffold
podman-compose -f podman-compose.infra.yml up -d
```

**启动组件**：

- MySQL 9.0

- Redis 7-alpine

- Nacos (standalone, 默认关闭注册)

- RocketMQ NameServer + Broker (可选)

- Meilisearch

- Zipkin

- Prometheus

- Grafana

- Loki

- Grafana Alloy

### 2. 启动应用

```bash
podman-compose -f podman-compose.yml up -d --build
```

**环境变量**：

- `SPRING_PROFILES_ACTIVE=prod,enterprise` (默认)

- `NACOS_CONFIG_ENABLED=false` (默认)

- `NACOS_DISCOVERY_ENABLED=false` (默认)

- `APEX_MQ_CONSUMER_ENABLED=false` (默认)

- `MEILISEARCH_ENABLED=true` (默认)

- `LOKI_ENABLED=true` (默认)

### 3. 健康检查

```bash
# 应用健康
curl http://localhost:8080/actuator/health

# Prometheus 指标
curl http://localhost:8081/actuator/prometheus

# Grafana 监控
open http://localhost:3000  # admin/admin
```

***

## Kubernetes 扩展拓扑 (建议)

### Deployment 示例

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: apex-system
spec:
  replicas: 3
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 1
      maxUnavailable: 0
  template:
    spec:
      containers:
      - name: apex-app
        image: apex-scaffold:1.3.1
        ports:
        - containerPort: 8080
          name: http
        - containerPort: 8081
          name: management
        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 8081
          initialDelaySeconds: 30
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8081
          initialDelaySeconds: 20
          periodSeconds: 5
```

### HPA 自动伸缩

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: apex-system-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: apex-system
  minReplicas: 2
  maxReplicas: 10
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
  - type: Resource
    resource:
      name: memory
      target:
        type: Utilization
        averageUtilization: 80
```

***

## 能力状态说明

### ✅ 默认栈能力 (Podman Compose，运行结果需按环境验证)

- MySQL 9.0 单实例

- Redis 7-alpine 单实例

- Meilisearch 默认启用

- Prometheus + Grafana 监控

- Loki + Alloy 日志聚合

- Zipkin 链路追踪

- 应用单实例运行

### 🔧 可选启用能力 (需配置)

- RocketMQ 消息队列：`APEX_MQ_CONSUMER_ENABLED=true`

- Nacos 服务发现：`NACOS_DISCOVERY_ENABLED=true`

- Nacos 配置中心：`NACOS_CONFIG_ENABLED=true`

- Resilience4j（内置） 集成：配置 `SENTINEL_DASHBOARD_URL`

### 🚀 建议拓扑能力 (需环境验证)

- Kubernetes 多实例部署

- HPA 自动伸缩

- 滚动发布 (RollingUpdate)

- MySQL 主从复制 / MGR

- Redis Sentinel / Cluster

- RocketMQ 主从 Broker

- MinIO 分布式部署

- CI/CD 流水线集成

***

## 生产部署检查清单

### 网络安全

- [ ] SSL 证书配置 (HTTPS)

- [ ] WAF 规则启用

- [ ] 防火墙白名单配置

- [ ] 内网隔离 (数据层不对外暴露)

### 应用层

- [ ] 多实例部署 (至少 2 个)

- [ ] 健康检查配置

- [ ] 优雅停机配置 (`server.shutdown=graceful`)

- [ ] JVM 参数调优 (堆内存、GC)

### 数据层

- [ ] MySQL 主从复制 / 云 RDS

- [ ] Redis 持久化 (AOF + RDB)

- [ ] MinIO 分布式部署 / 云对象存储

- [ ] 数据库备份策略

### 监控告警

- [ ] Prometheus 告警规则配置

- [ ] Grafana Dashboard 导入

- [ ] AlertManager 通知渠道配置

- [ ] 日志保留策略 (Loki retention)

### 高可用

- [ ] 负载均衡配置 (Nginx / ALB)

- [ ] 跨可用区部署

- [ ] 数据库连接池调优

- [ ] 限流熔断规则验证

***

## 架构约束与限制

### Podman Compose 限制

1. **单机部署**：无法跨节点，不支持真正的高可用
2. **资源隔离**：容器共享宿主机资源，需配置 cgroups 资源限制
3. **无自动伸缩**：手动使用 `podman-compose -f podman-compose.yml up -d --scale <服务名>=<副本数>`，并先处理固定容器名和宿主机端口冲突
4. **网络性能**：容器网络有性能损耗，生产需按环境评估网络方案

### Kubernetes 扩展要求

1. **集群环境**：需已有 K8s 集群 (版本 >= 1.24)
2. **镜像仓库**：需配置私有镜像仓库 (Harbor / ACR / ECR)
3. **持久化存储**：需配置 StorageClass (Ceph / NFS / 云盘)
4. **Ingress Controller**：需部署 Nginx Ingress / Traefik

### 数据库高可用限制

1. **Compose MySQL**：单实例，无主从，生产需外部数据库
2. **Redis 持久化**：Compose 默认无 AOF，需手动配置
3. **RocketMQ 存储**：Compose 本地无持久化卷，重启数据丢失

***

## 常见问题

### Q1: 如何启用 RocketMQ 消息消费？

**A**: 修改 `podman-compose.yml`:

```yaml
environment:
  APEX_MQ_CONSUMER_ENABLED: "true"
  ROCKETMQ_NAME_SERVER_ADDR: "rocketmq-namesrv:9876"
```

### Q2: Kubernetes 如何配置管理端口暴露？

**A**: 修改 `application-k8s.yml`:

```yaml
management:
  server:
    address: "0.0.0.0"  # 允许 K8s Service 访问
```

### Q3: 生产环境如何配置 MySQL 主从？

**A**: 不使用 Compose MySQL，改用云 RDS 或自建 MGR：

```yaml
DB_MASTER_URL: jdbc:mysql://rds-master.xxx.com:3306/apex_data
DB_SLAVE_URL: jdbc:mysql://rds-slave.xxx.com:3306/apex_data
```

### Q4: 如何验证监控系统正常？

**A**:

1. 访问 Grafana: `http://localhost:3000` (admin/admin)
2. 检查 Data Source → Prometheus 连接状态
3. 导入 Dashboard: `config/monitoring/grafana/apex-dashboard.json`
4. 查看 JVM 面板数据是否正常

***

## 相关文档

- [01 DDD 四层逻辑架构 - 详细说明](01-logical-architecture-detail.md)

- [03 技术架构全景图 - 详细说明](03-technical-architecture-detail.md)

- [部署架构文档](deployment-architecture.md)

- [网络架构文档](network-architecture.md)

- [监控指南](../_archive/operations/monitoring.md)

***

**下一步**：

- 阅读 [02 网络物理部署架构 - 快速参考](02-physical-architecture-quick-ref.md)

- 了解 [02 网络物理部署架构 - 架构决策](02-physical-architecture-decision.md)

- 查看 [02 网络物理部署架构 - 商业价值](02-physical-architecture-business-value.md)

