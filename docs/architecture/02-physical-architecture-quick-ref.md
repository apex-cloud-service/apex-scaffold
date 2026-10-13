# 02 网络物理部署架构 - 快速参考

> 文档版本: v1.0  
> 创建时间: 2026-09-04  
> 适用版本: apex-scaffold v1.2.0

## 架构图

![Apex Scaffold 网络物理部署架构图](../diagrams/02_physical_architecture.svg)

---

## 五层拓扑速查

| 层级 | 职责 | 核心组件 | 部署模式 |
|------|------|----------|----------|
| 🌐 **客户端层** | 用户接入 | Web/Mobile/API/CDN/DNS | Internet |
| 🛡️ **网络安全层** | 流量防护 | WAF/Nginx/ALB/Sentinel/SSO | DMZ |
| ⚙️ **应用服务层** | 业务处理 | Spring Boot WebFlux | K8s Pods / Podman |
| 💾 **数据存储层** | 持久化 | MySQL/Redis/MinIO/RocketMQ/Meilisearch | 高可用集群 |
| 🔍 **运维监控层** | 可观测性 | Prometheus/Grafana/Loki/Zipkin | 监控栈 |

---

## 核心端口速查

| 服务 | 端口 | 协议 | 用途 |
|------|------|------|------|
| 应用业务端口 | `8080` | HTTP | API 请求 |
| 应用管理端口 | `8081` | HTTP | `/actuator/*` |
| MySQL | `3306` | JDBC | 数据库连接 |
| Redis | `6379` | TCP | 缓存/分布式锁 |
| MinIO | `9000` | HTTP | 对象存储 API |
| RocketMQ NameServer | `9876` | TCP | 服务发现 |
| Meilisearch | `7700` | HTTP | 全文检索 |
| Prometheus | `9090` | HTTP | 指标查询 |
| Grafana | `3000` | HTTP | 监控面板 |
| Zipkin | `9411` | HTTP | 链路追踪 |
| Nginx/ALB | `443` | HTTPS | 外网入口 |

---

## 部署模式对比

| 能力 | Podman Compose | Kubernetes | 状态 |
|------|----------------|------------|------|
| 基础运行 | ✅ 单实例 | ✅ Deployment | 已验证 |
| 多实例扩展 | 手动 scale | ✅ HPA | 建议 |
| 滚动发布 | ❌ | ✅ RollingUpdate | 建议 |
| 配置管理 | .env | ✅ ConfigMap/Secret | 建议 |
| 服务发现 | Podman 网络 DNS | ✅ Service/Ingress | 建议 |
| 资源限制 | 弱隔离 | ✅ cgroups 强限制 | 建议 |

---

## 组件状态速查

### ✅ 默认启用 (Compose)
- MySQL 8.0 (`mysql:8.0`)
- Redis 7-alpine (`redis:7-alpine`)
- Meilisearch (`getmeili/meilisearch`)
- Prometheus (`prom/prometheus:v2.53.0`)
- Grafana (`grafana/grafana`)
- Loki / Alloy (`grafana/loki`, `grafana/alloy`)
- Zipkin (`openzipkin/zipkin:3.4`)

### 🔧 可选启用 (需配置)
- RocketMQ 消费者: `APEX_MQ_CONSUMER_ENABLED=true`
- Nacos 服务发现: `NACOS_DISCOVERY_ENABLED=true`
- Nacos 配置中心: `NACOS_CONFIG_ENABLED=true`
- Sentinel Dashboard: 配置 `SENTINEL_DASHBOARD_URL`

### 🚀 建议拓扑 (需验证)
- Kubernetes 多实例部署
- HPA 自动伸缩
- 滚动发布
- MySQL 主从复制 / MGR / 云 RDS
- Redis Sentinel / Cluster
- RocketMQ 主从 Broker
- MinIO 分布式部署
- CI/CD 流水线集成

---

## 快速启动命令

### 本地开发 (Podman Compose)
```bash
# 1. 启动基础设施
podman-compose -f podman-compose.infra.yml up -d

# 2. 等待 MySQL/Redis 就绪 (约 30s)
podman-compose -f podman-compose.infra.yml logs -f mysql redis

# 3. 启动应用
podman-compose -f podman-compose.yml up -d

# 4. 健康检查
curl http://localhost:8080/actuator/health

# 5. 查看日志
podman-compose -f podman-compose.yml logs -f apex-gateway

# 6. 访问监控
open http://localhost:3000  # Grafana (admin/admin)
open http://localhost:9090  # Prometheus
```

### 生产部署 (Kubernetes)
```bash
# 1. 构建镜像
podman build -f Containerfile -t apex-scaffold:1.2.0 .

# 2. 推送镜像
podman tag apex-scaffold:1.2.0 registry.example.com/apex-scaffold:1.2.0
podman push registry.example.com/apex-scaffold:1.2.0

# 3. 部署应用
kubectl apply -f k8s/deployment.yaml
kubectl apply -f k8s/service.yaml
kubectl apply -f k8s/ingress.yaml

# 4. 检查状态
kubectl get pods -l app=apex-application
kubectl logs -f deployment/apex-application

# 5. 配置 HPA
kubectl apply -f k8s/hpa.yaml
kubectl get hpa
```

---

## 健康检查端点

### 存活探针 (Liveness)
```bash
curl http://localhost:8081/actuator/health/liveness
```
**响应**:
```json
{"status":"UP"}
```

### 就绪探针 (Readiness)
```bash
curl http://localhost:8081/actuator/health/readiness
```
**响应** (包含 DB、Redis 状态):
```json
{
  "status":"UP",
  "components":{
    "db":{"status":"UP"},
    "redis":{"status":"UP"}
  }
}
```

### Prometheus 指标
```bash
curl http://localhost:8081/actuator/prometheus | grep jvm_memory_used_bytes
```

---

## 网络连接链路

### 正常业务请求链
```
用户 → HTTPS(443) → WAF → Nginx/ALB → API Gateway 
    → HTTP(8080) → Apex Application 
    → JDBC(3306) → MySQL
    → TCP(6379) → Redis
    → HTTP(9000) → MinIO
```

### 监控数据采集链
```
Apex Application:8081 → Prometheus (Pull 指标)
Apex Application → Alloy → Loki (Push 日志)
Apex Application → Zipkin (Report 链路)
```

---

## 环境变量速查

### 数据库配置
```bash
DB_MASTER_URL=jdbc:mysql://mysql:3306/apex_data
DB_USERNAME=root
DB_PASSWORD=${MYSQL_ROOT_PASSWORD}
DB_MAX_POOL_SIZE=20
DB_MIN_IDLE=5
```

### 缓存配置
```bash
REDIS_HOST=redis
REDIS_PORT=6379
REDIS_PASSWORD=${REDIS_PASSWORD}
REDIS_DATABASE=0
```

### 对象存储配置
```bash
MINIO_ENDPOINT=http://minio:9000
MINIO_ACCESS_KEY=${MINIO_ACCESS_KEY}
MINIO_SECRET_KEY=${MINIO_SECRET_KEY}
MINIO_BUCKET=apex-files
```

### RocketMQ 配置 (可选)
```bash
ROCKETMQ_NAME_SERVER_ADDR=rocketmq-namesrv:9876
APEX_MQ_CONSUMER_ENABLED=false  # 改为 true 启用
APEX_MQ_DOMAIN_EVENT_ENABLED=false
```

### Nacos 配置 (可选)
```bash
NACOS_SERVER_ADDR=nacos:8848
NACOS_CONFIG_ENABLED=false  # 改为 true 启用
NACOS_DISCOVERY_ENABLED=false
NACOS_REGISTER_ENABLED=false
```

### 监控配置
```bash
MEILISEARCH_ENABLED=true
MEILISEARCH_URL=http://meilisearch:7700
LOKI_ENABLED=true
LOKI_URL=http://loki:3100
GRAFANA_ENABLED=false  # 应用侧集成
ZIPKIN_BASE_URL=http://zipkin:9411
```

---

## 故障排查速查

### 应用启动失败
```bash
# 1. 查看日志
podman-compose -f podman-compose.yml logs -f apex-gateway

# 2. 检查数据库连接
podman-compose -f podman-compose.yml exec apex-gateway wget -qO- http://localhost:8081/actuator/health

# 3. 进入容器调试
podman-compose -f podman-compose.yml exec apex-gateway sh
```

### 数据库连接失败
```bash
# 1. 检查 MySQL 状态
podman-compose -f podman-compose.infra.yml ps mysql
podman-compose -f podman-compose.infra.yml logs mysql

# 2. 测试连接
podman-compose -f podman-compose.infra.yml exec mysql mysql -uroot -p${MYSQL_ROOT_PASSWORD} -e "SELECT 1"

# 3. 检查容器网络
podman inspect apex-infra-mysql --format '{{json .NetworkSettings.Networks}}'
```

### Redis 连接失败
```bash
# 1. 检查 Redis 状态
podman-compose -f podman-compose.infra.yml exec redis redis-cli PING

# 2. 查看连接数
podman-compose -f podman-compose.infra.yml exec redis redis-cli CLIENT LIST

# 3. 查看内存使用
podman-compose -f podman-compose.infra.yml exec redis redis-cli INFO memory
```

### Prometheus 无数据
```bash
# 1. 检查 Prometheus targets
open http://localhost:9090/targets

# 2. 测试指标端点
curl http://localhost:8081/actuator/prometheus

# 3. 检查 Prometheus 配置
podman-compose -f podman-compose.infra.yml exec prometheus cat /etc/prometheus/prometheus.yml
```

---

## Kubernetes 资源速查

### Deployment
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: apex-application
spec:
  replicas: 3
  template:
    spec:
      containers:
      - name: apex-app
        image: apex-scaffold:1.2.0
        ports:
        - containerPort: 8080
        - containerPort: 8081
```

### Service
```yaml
apiVersion: v1
kind: Service
metadata:
  name: apex-application
spec:
  selector:
    app: apex-application
  ports:
  - name: http
    port: 80
    targetPort: 8080
  - name: management
    port: 8081
    targetPort: 8081
```

### Ingress
```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: apex-application
spec:
  rules:
  - host: api.example.com
    http:
      paths:
      - path: /
        pathType: Prefix
        backend:
          service:
            name: apex-application
            port:
              number: 80
```

### HPA
```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: apex-application-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: apex-application
  minReplicas: 2
  maxReplicas: 10
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
```

---

## 生产部署检查清单

### 部署前
- [ ] 镜像已推送到私有仓库
- [ ] 配置文件已创建 (ConfigMap/Secret)
- [ ] 数据库已初始化 (Flyway 迁移)
- [ ] SSL 证书已配置
- [ ] 防火墙白名单已开通

### 部署中
- [ ] 滚动发布配置正确 (maxSurge/maxUnavailable)
- [ ] 健康检查探针配置正确
- [ ] 资源限制配置合理 (requests/limits)
- [ ] 日志输出到 stdout/stderr

### 部署后
- [ ] Pod 状态全部 Running
- [ ] 健康检查通过
- [ ] Prometheus 指标正常采集
- [ ] Grafana Dashboard 显示正常
- [ ] 告警规则已触发测试
- [ ] 日志正常输出到 Loki
- [ ] 链路追踪数据正常上报 Zipkin

---

## 性能调优速查

### JVM 参数 (生产建议)
```bash
JAVA_OPTS="-Xms1g -Xmx2g \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=200 \
  -XX:+HeapDumpOnOutOfMemoryError \
  -XX:HeapDumpPath=/tmp/heapdump.hprof \
  -Dcom.sun.management.jmxremote \
  -Dcom.sun.management.jmxremote.port=9999 \
  -Dcom.sun.management.jmxremote.authenticate=false \
  -Dcom.sun.management.jmxremote.ssl=false"
```

### 数据库连接池
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20  # CPU * 2 + 磁盘数
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
```

### Redis 连接池
```yaml
spring:
  data:
    redis:
      lettuce:
        pool:
          max-active: 16
          max-idle: 8
          min-idle: 4
          max-wait: 3000ms
```

### Netty 线程池
```yaml
spring:
  reactor:
    netty:
      ioWorkerCount: 8  # CPU 核心数
      shutdown-quiet-period: 2s
      shutdown-timeout: 15s
```

---

## 相关文档

- **详细说明**: [02 网络物理部署架构 - 详细说明](02-physical-architecture-detail.md)
- **架构决策**: [02 网络物理部署架构 - 架构决策](02-physical-architecture-decision.md)
- **商业价值**: [02 网络物理部署架构 - 商业价值](02-physical-architecture-business-value.md)
- **逻辑架构**: [01 DDD 四层逻辑架构 - 快速参考](01-logical-architecture-quick-ref.md)
- **技术架构**: [03 技术架构全景图 - 快速参考](03-technical-architecture-quick-ref.md)

---

**提示**: 本文档为速查手册，详细配置和原理请参考详细说明文档。
