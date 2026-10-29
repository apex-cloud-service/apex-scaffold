# Apex Scaffold 部署架构文档

> 文档版本: v1.1
> 创建时间: 2026-09-04
> 最近更新: 2026-10-28（标注历史单体拓扑，修正 Java 版本和日志方案）
> 适用版本: apex-scaffold v1.3.1+
> 部署方式: Kubernetes / Podman / podman-compose

> **⚠️ 架构演进说明**：本文档中的 Containerfile、Deployment YAML 和 podman-compose 示例部分保留了**单体部署的历史形态**（单一 `apex-app` 镜像）。当前运行架构已拆分为网关 + 五业务服务 + 任务代理/脱敏服务的独立镜像，各服务独立端口。实际部署以仓库根目录的 `podman-compose.yml`、`podman-compose.infra.yml`、`Containerfile`/`Containerfile.quick` 和 [system-architecture.md](system-architecture.md) §3.2 为准。日志聚合当前使用 Grafana Loki + Alloy（非 ELK）。

> **配套架构图**:
> - [部署架构拓扑图](../diagrams/45_deployment_flow.svg)
> - [网络架构图](../diagrams/02_physical_architecture.svg)

---

## 一、部署概览

Apex Scaffold 采用**云原生架构**设计，支持多种部署方式，从本地开发到生产环境的平滑过渡。

### 1.1 部署方式对比

| 部署方式 | 适用场景 | 优势 | 劣势 |
|---------|---------|------|------|
| **Podman Compose** | 本地开发/单机测试 | 简单快速，一键启动 | 单机编排，扩容需外部控制器 |
| **Kubernetes** | 生产环境 (推荐) | 强大的编排能力，生态丰富 | 学习曲线陡峭，运维复杂 |
| **裸机部署** | 传统环境 | 性能最优 | 运维成本高，难以扩展 |

**推荐方案**: 本地开发使用 **Podman Compose**，生产环境使用 **Kubernetes**

---

## 二、生产环境部署架构

### 2.1 部署拓扑

```
┌─────────────────────────────────────────────────────────────┐
│  公网层 (Internet)                                           │
│  - CDN: 静态资源加速                                         │
│  - DNS: 域名解析 (GeoDNS智能调度)                           │
│  - WAF: SQL注入/XSS防护                                      │
└───────────────────┬─────────────────────────────────────────┘
                    │
┌───────────────────┴─────────────────────────────────────────┐
│  DMZ 区 (非军事化区)                                         │
│  - 负载均衡器: ALB / Nginx (443 HTTPS)                       │
│  - NAT 网关: 出网访问                                        │
│  - 跳板机: VPN + 2FA 认证                                    │
└───────────────────┬─────────────────────────────────────────┘
                    │
┌───────────────────┴─────────────────────────────────────────┐
│  应用服务层 (Kubernetes Cluster)                             │
│  - Apex Pod-1/2/3: apex:v1.3.1 (2C4G)                       │
│  - HPA: 自动扩缩容 (Min:3 Max:10)                            │
│  - ConfigMap: 配置注入                                       │
│  - Secret: 密钥管理                                          │
└───────────────────┬─────────────────────────────────────────┘
                    │
┌───────────────────┴─────────────────────────────────────────┐
│  数据存储层                                                  │
│  - MySQL: 主从复制 (Master + 2 Slave)                       │
│  - Redis: Cluster 3主3从                                     │
│  - MinIO / 火山云TOS: 对象存储                               │
└─────────────────────────────────────────────────────────────┘
```

### 2.2 核心组件清单

#### 应用服务

| 组件 | 版本 | 端口 | 副本数 | 资源配额 |
|------|------|------|--------|---------|
| apex-scaffold | v1.3.1 | 多服务独立端口 | 各服务 2+ (HPA) | 2C / 4G |

#### 数据存储

| 组件 | 版本 | 端口 | 部署模式 | 存储 |
|------|------|------|---------|------|
| MySQL | 8.0 | 3306 | 1主2从 | 500GB SSD |
| Redis | 7.x | 6379 | Cluster 3主3从 | 128GB 内存 |
| MinIO | RELEASE.2024 | 9000(API) / 9001(Console) | 4节点分布式 | 2TB SSD |

#### 中间件

| 组件 | 版本 | 端口 | 部署模式 | 说明 |
|------|------|------|---------|------|
| Nacos | 2.x | 8848 | 3节点集群 | 配置中心+服务发现 |
| Resilience4j | 2.2.0 | 内置 | 网关路由级 | 熔断回退 |
| RocketMQ NameServer | 5.x | 9876 | 3节点 | 路由注册 |
| RocketMQ Broker | 5.x | 10911 | 2主2从 | 消息存储 |

#### 监控运维

| 组件 | 版本 | 端口 | 说明 |
|------|------|------|------|
| Prometheus | 2.x | 9090 | 指标采集 (15s间隔) |
| Grafana | 10.x | 3000 | 66个监控面板 |
| Zipkin | 2.x | 9411 | 链路追踪 |
| AlertManager | 0.25.x | 9093 | 告警管理 |
| Elasticsearch | 8.x | 9200 | 日志存储 (30天) |
| Kibana | 8.x | 5601 | 日志查询 |

---

## 三、Kubernetes 部署

### 3.1 集群规划

**推荐配置** (生产环境):
- **Master 节点**: 3台 (4C8G)
- **Worker 节点**: 6台+ (8C16G)
- **存储**: 500GB SSD / 节点
- **网络**: Calico / Flannel

### 3.2 命名空间设计

```yaml
# 环境隔离
namespaces:
  - apex-dev        # 开发环境
  - apex-test       # 测试环境
  - apex-pre        # 预发环境
  - apex-prod       # 生产环境
  - apex-monitor    # 监控组件
  - apex-middleware # 中间件
```

### 3.3 Deployment 配置

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: apex-scaffold
  namespace: apex-prod
  labels:
    app: apex-scaffold
    version: v1.3.1
spec:
  replicas: 3
  selector:
    matchLabels:
      app: apex-scaffold
  template:
    metadata:
      labels:
        app: apex-scaffold
        version: v1.3.1
    spec:
      containers:
      - name: apex-scaffold
        image: harbor.example.com/apex/apex-scaffold:v1.3.1
        ports:
        - containerPort: 8080
          name: http
          protocol: TCP
        - containerPort: 8081
          name: management
          protocol: TCP
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "prod"
        - name: NACOS_SERVER_ADDR
          value: "nacos-svc.apex-middleware:8848"
        envFrom:
        - configMapRef:
            name: apex-config
        - secretRef:
            name: apex-secret
        resources:
          requests:
            cpu: "1000m"
            memory: "2Gi"
          limits:
            cpu: "2000m"
            memory: "4Gi"
        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 8081
          initialDelaySeconds: 60
          periodSeconds: 10
          timeoutSeconds: 5
          failureThreshold: 3
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8081
          initialDelaySeconds: 30
          periodSeconds: 5
          timeoutSeconds: 3
          failureThreshold: 3
        lifecycle:
          preStop:
            exec:
              command: ["/bin/sh", "-c", "sleep 30"]
```

### 3.4 Service 配置

```yaml
apiVersion: v1
kind: Service
metadata:
  name: apex-svc
  namespace: apex-prod
spec:
  type: ClusterIP
  selector:
    app: apex-scaffold
  ports:
  - name: http
    port: 8080
    targetPort: 8080
    protocol: TCP
  - name: management
    port: 8081
    targetPort: 8081
    protocol: TCP
```

### 3.5 HPA 自动扩缩容

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: apex-hpa
  namespace: apex-prod
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: apex-scaffold
  minReplicas: 3
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
  behavior:
    scaleDown:
      stabilizationWindowSeconds: 300
      policies:
      - type: Percent
        value: 50
        periodSeconds: 60
    scaleUp:
      stabilizationWindowSeconds: 0
      policies:
      - type: Percent
        value: 100
        periodSeconds: 30
      - type: Pods
        value: 2
        periodSeconds: 30
      selectPolicy: Max
```

### 3.6 ConfigMap 配置注入

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: apex-config
  namespace: apex-prod
data:
  application.yml: |
    server:
      port: 8080
    spring:
      application:
        name: apex-scaffold
      cloud:
        nacos:
          discovery:
            server-addr: nacos-svc.apex-middleware:8848
            namespace: prod
          config:
            server-addr: nacos-svc.apex-middleware:8848
            namespace: prod
            file-extension: yaml
    management:
      server:
        port: 8081
      endpoints:
        web:
          exposure:
            include: "*"
```

### 3.7 Secret 密钥管理

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: apex-secret
  namespace: apex-prod
type: Opaque
data:
  MYSQL_PASSWORD: <base64-encoded>
  REDIS_PASSWORD: <base64-encoded>
  JWT_SECRET: <base64-encoded>
```

**创建 Secret**:
```bash
kubectl create secret generic apex-secret \
  --from-literal=MYSQL_PASSWORD='your-mysql-password' \
  --from-literal=REDIS_PASSWORD='your-redis-password' \
  --from-literal=JWT_SECRET='your-jwt-secret' \
  -n apex-prod
```

---

## 四、Podman Compose 部署 (本地开发)

### 4.1 podman-compose.yml

以下为历史单体拓扑示例，不应覆盖仓库配置；实际部署以仓库根目录的 `podman-compose.yml` 和 `podman-compose.infra.yml` 为准。

```yaml
version: '3.8'

services:
  # 应用服务
  apex-app:
    image: apex-scaffold:latest
    build:
      context: .
      dockerfile: Containerfile
    container_name: apex-app
    ports:
      - "8080:8080"
      - "8081:8081"
    environment:
      - SPRING_PROFILES_ACTIVE=dev
      - SPRING_DATASOURCE_URL=jdbc:mysql://mysql:3306/apex_db
      - SPRING_REDIS_HOST=redis
      - NACOS_SERVER_ADDR=nacos:8848
    depends_on:
      mysql:
        condition: service_healthy
      redis:
        condition: service_started
      nacos:
        condition: service_started
    networks:
      - apex-network
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8081/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3

  # MySQL 9.0
  mysql:
    image: mysql:9.0
    container_name: apex-mysql
    ports:
      - "3306:3306"
    environment:
      - MYSQL_ROOT_PASSWORD=root
      - MYSQL_DATABASE=apex_db
      - TZ=Asia/Shanghai
    volumes:
      - mysql-data:/var/lib/mysql
      # 初始化 SQL 由部署方另行提供；仓库默认栈不挂载该历史初始化目录
    networks:
      - apex-network
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost"]
      interval: 10s
      timeout: 5s
      retries: 5

  # Redis 7.x
  redis:
    image: redis:7-alpine
    container_name: apex-redis
    ports:
      - "6379:6379"
    command: redis-server --appendonly yes --requirepass redis123
    volumes:
      - redis-data:/data
    networks:
      - apex-network
    restart: unless-stopped

  # Nacos 2.x
  nacos:
    image: nacos/nacos-server:v2.2.3
    container_name: apex-nacos
    ports:
      - "8848:8848"
      - "9848:9848"
    environment:
      - MODE=standalone
      - SPRING_DATASOURCE_PLATFORM=mysql
      - MYSQL_SERVICE_HOST=mysql
      - MYSQL_SERVICE_DB_NAME=nacos_config
      - MYSQL_SERVICE_USER=root
      - MYSQL_SERVICE_PASSWORD=root
    depends_on:
      - mysql
    networks:
      - apex-network
    restart: unless-stopped

  # RocketMQ NameServer
  rocketmq-namesrv:
    image: apache/rocketmq:5.1.4
    container_name: apex-rocketmq-namesrv
    ports:
      - "9876:9876"
    command: sh mqnamesrv
    environment:
      - JAVA_OPT=-server -Xms512m -Xmx512m
    networks:
      - apex-network
    restart: unless-stopped

  # RocketMQ Broker
  rocketmq-broker:
    image: apache/rocketmq:5.1.4
    container_name: apex-rocketmq-broker
    ports:
      - "10911:10911"
      - "10912:10912"
    command: sh mqbroker -n rocketmq-namesrv:9876
    environment:
      - JAVA_OPT=-server -Xms1g -Xmx1g
    depends_on:
      - rocketmq-namesrv
    # 集群自定义配置需另行提供；默认栈见 podman-compose.infra.yml
    networks:
      - apex-network
    restart: unless-stopped

  # Prometheus
  prometheus:
    image: prom/prometheus:latest
    container_name: apex-prometheus
    ports:
      - "9090:8083"
    command:
      - '--config.file=/etc/prometheus/prometheus.yml'
      - '--storage.tsdb.path=/prometheus'
      - '--storage.tsdb.retention.time=15d'
    volumes:
      - ./config/monitoring/prometheus/prometheus.yml:/etc/prometheus/prometheus.yml:ro
      - prometheus-data:/prometheus
    networks:
      - apex-network
    restart: unless-stopped

  # Grafana
  grafana:
    image: grafana/grafana:latest
    container_name: apex-grafana
    ports:
      - "3000:3000"
    environment:
      - GF_SECURITY_ADMIN_USER=admin
      - GF_SECURITY_ADMIN_PASSWORD=admin
    volumes:
      - grafana-data:/var/lib/grafana
      - ./config/monitoring/grafana/provisioning:/etc/grafana/provisioning:ro
    networks:
      - apex-network
    restart: unless-stopped

networks:
  apex-network:
    driver: bridge

volumes:
  mysql-data:
  redis-data:
  prometheus-data:
  grafana-data:
```

### 4.2 启动命令

```bash
# 在仓库根目录启动应用栈
podman-compose -f podman-compose.yml up -d --build

# 查看日志
podman-compose -f podman-compose.yml logs -f

# 停止服务
podman-compose -f podman-compose.yml down

# 清理数据（会删除卷）
podman-compose -f podman-compose.yml down -v
```

---

## 五、部署流程

### 5.1 镜像构建

**Containerfile**（以下为历史单体镜像示例；实际构建使用仓库根目录的多阶段 `Containerfile`）:
```dockerfile
FROM eclipse-temurin:21-jre-alpine

LABEL maintainer="apex-team"

# 时区设置
RUN apk add --no-cache tzdata && \
    cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    echo "Asia/Shanghai" > /etc/timezone

# 创建应用目录
WORKDIR /app

# 复制应用JAR
COPY target/apex-scaffold-*.jar app.jar

# 健康检查
HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
  CMD wget --quiet --tries=1 --spider http://localhost:8081/actuator/health || exit 1

# JVM 参数优化
ENV JAVA_OPTS="-Xms2g -Xmx4g -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+HeapDumpOnOutOfMemoryError"

# 启动应用
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

EXPOSE 8080 8081
```

**构建镜像**:
```bash
# Maven 打包
mvn clean package -DskipTests

# Podman 构建（仓库根目录）
podman build -f Containerfile -t apex-scaffold:v1.3.1 .

# 推送到Harbor
podman tag apex-scaffold:v1.3.1 harbor.example.com/apex/apex-scaffold:v1.3.1
podman push harbor.example.com/apex/apex-scaffold:v1.3.1
```

### 5.2 Kubernetes 部署流程

```bash
# 1. 创建命名空间
kubectl create namespace apex-prod

# 2. 应用ConfigMap和Secret
kubectl apply -f k8s/configmap.yaml
kubectl apply -f k8s/secret.yaml

# 3. 部署应用
kubectl apply -f k8s/deployment.yaml
kubectl apply -f k8s/service.yaml
kubectl apply -f k8s/hpa.yaml

# 4. 查看状态
kubectl get pods -n apex-prod
kubectl get svc -n apex-prod

# 5. 查看日志
kubectl logs -f deployment/apex-scaffold -n apex-prod

# 6. 端口转发 (本地测试)
kubectl port-forward svc/apex-svc 8080:8080 -n apex-prod
```

### 5.3 滚动更新

```bash
# 更新镜像
kubectl set image deployment/apex-scaffold \
  apex-scaffold=harbor.example.com/apex/apex-scaffold:v2.5.1 \
  -n apex-prod

# 查看更新状态
kubectl rollout status deployment/apex-scaffold -n apex-prod

# 回滚
kubectl rollout undo deployment/apex-scaffold -n apex-prod
```

### 5.4 金丝雀发布

```yaml
# canary-deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: apex-scaffold-canary
  namespace: apex-prod
spec:
  replicas: 1  # 仅1个金丝雀实例
  selector:
    matchLabels:
      app: apex-scaffold
      version: v2.5.1  # 新版本
  template:
    metadata:
      labels:
        app: apex-scaffold
        version: v2.5.1
    spec:
      # ... (与主Deployment配置相同，镜像为新版本)
```

**金丝雀发布流程**:
1. 部署1个金丝雀实例 (新版本)
2. 观察监控指标 (错误率/延迟)
3. 逐步增加金丝雀实例比例 (10% → 50% → 100%)
4. 全量替换为新版本

---

## 六、高可用保障

### 6.1 应用层高可用

- ✅ **无状态设计**: 应用实例无状态，会话存储在Redis
- ✅ **多副本部署**: 最少3个实例，跨节点分布
- ✅ **健康检查**: Liveness + Readiness 探针
- ✅ **优雅下线**: PreStop Hook延迟30s
- ✅ **自动扩缩容**: HPA根据CPU/内存自动调整

### 6.2 数据层高可用

**MySQL**:
- 主从复制 (1主2从)
- 半同步复制保证数据一致性
- MHA/Orchestrator自动故障转移

**Redis**:
- Cluster模式 (3主3从)
- 哨兵自动故障转移
- 主从切换 < 1s

**RocketMQ**:
- NameServer 3节点无状态集群
- Broker 2主2从 + 同步复制
- DLedger模式支持自动主从切换

### 6.3 网络层高可用

- ✅ **负载均衡器**: ALB多可用区部署
- ✅ **CDN**: 边缘节点分发，就近访问
- ✅ **DNS**: GeoDNS智能解析，故障切换

---

## 七、监控与告警

### 7.1 监控指标

**应用监控**:
- JVM: 堆内存/GC/线程/CPU
- HTTP: QPS/延迟/错误率/状态码分布
- 数据库: 连接池/慢查询/主从延迟
- 缓存: 命中率/内存使用/慢查询

**基础设施监控**:
- 节点: CPU/内存/磁盘/网络
- Pod: 状态/重启次数/资源使用
- 中间件: MySQL/Redis/RocketMQ健康状态

### 7.2 告警规则

| 级别 | 规则 | 阈值 | 通知方式 |
|------|------|------|---------|
| P0 | 服务不可用 | 5xx错误率>10% | 电话+短信 |
| P1 | QPS异常 | QPS < 正常值50% 持续5分钟 | 钉钉+邮件 |
| P2 | 延迟告警 | P95延迟>500ms 持续10分钟 | 钉钉 |
| P3 | 资源告警 | CPU>80% 或 内存>85% | 邮件 |

---

## 八、运维最佳实践

### 8.1 日志管理

- ✅ 集中式日志: Grafana Alloy → Loki
- ✅ 日志保留: 30天，自动轮转
- ✅ 日志级别: 生产环境 INFO，问题排查时动态调整为DEBUG
- ✅ TraceID关联: 全链路日志关联

### 8.2 备份策略

**数据库备份**:
- 全量备份: 每天凌晨3点
- 增量备份: 每小时1次
- 保留策略: 全量30天，增量7天
- 异地灾备: 备份文件同步到对象存储

**配置备份**:
- Nacos配置自动导出
- Git版本管理
- 重要配置变更审计

### 8.3 安全加固

- ✅ 最小权限原则: Pod ServiceAccount权限最小化
- ✅ 镜像扫描: Harbor自动扫描漏洞
- ✅ 密钥管理: 敏感配置使用Secret加密存储
- ✅ 网络隔离: NetworkPolicy限制Pod间通信
- ✅ 安全审计: 所有运维操作审计日志

---

## 九、故障排查

### 9.1 常见问题

**Pod一直处于Pending状态**:
- 检查节点资源是否充足
- 检查持久化存储是否可用
- 检查镜像拉取权限

**Pod频繁重启**:
- 查看Pod日志: `kubectl logs pod-name -n apex-prod --previous`
- 检查健康检查配置是否合理
- 检查资源限制是否过小

**服务无法访问**:
- 检查Service配置
- 检查Ingress规则
- 检查防火墙/安全组规则

### 9.2 排查命令

```bash
# 查看Pod详情
kubectl describe pod <pod-name> -n apex-prod

# 查看事件
kubectl get events -n apex-prod --sort-by='.lastTimestamp'

# 进入容器
kubectl exec -it <pod-name> -n apex-prod -- /bin/sh

# 查看资源使用
kubectl top pod -n apex-prod
kubectl top node

# 查看日志
kubectl logs -f <pod-name> -n apex-prod --tail=100
```

---

## 十、参考资料

### 10.1 内部文档

- [网络架构文档](network-architecture.md)
- [系统架构设计文档](system-architecture.md)
- [业务流程文档](business-flows.md)

### 10.2 外部参考

- [Kubernetes官方文档](https://kubernetes.io/docs/)
- [Podman官方文档](https://docs.podman.io/)
- [Spring Boot部署指南](https://docs.spring.io/spring-boot/docs/current/reference/html/deployment.html)

---

**文档维护者**: 架构团队
**审核状态**: 待人工审核
**下次更新**: 部署架构变更后同步更新
