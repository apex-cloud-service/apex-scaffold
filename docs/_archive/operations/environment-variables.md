# 环境变量管理指南

> **适用版本**: Apex Scaffold v1.2.0+  
> **最后更新**: 2026-09-09

---

## 一、概述

本文档是 Apex Scaffold 项目的环境变量完整参考手册，涵盖所有 172 个环境变量的详细说明、设置方式、优先级规则和最佳实践。

### 为什么使用环境变量？

1. **安全性** - 避免敏感信息（密码、密钥）硬编码在配置文件中
2. **灵活性** - 同一套代码适配不同环境（开发/测试/生产）
3. **可维护性** - 集中管理配置，减少配置文件修改
4. **云原生** - 符合 12-Factor App 原则，适配容器化部署

---

## 二、环境变量设置方式

### 2.1 操作系统环境变量（推荐生产环境）

**Linux / macOS**:
```bash
export JWT_SECRET="your-secret-key-at-least-32-bytes"
export DB_MASTER_PASSWORD="your-database-password"
```

**Windows**:
```powershell
$env:JWT_SECRET="your-secret-key-at-least-32-bytes"
$env:DB_MASTER_PASSWORD="your-database-password"
```

### 2.2 .env 文件（推荐开发环境）

创建 `.env` 文件（根目录）：
```bash
# 数据库配置
DB_MASTER_URL=jdbc:mysql://localhost:3306/apex_db
DB_MASTER_USERNAME=root
DB_MASTER_PASSWORD=your-password

# JWT 配置
JWT_SECRET=your-secret-key-at-least-32-bytes

# Redis 配置
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=your-redis-password
```

**注意**: `.env` 文件应加入 `.gitignore`，避免提交到版本控制。

### 2.3 Docker Compose

在 `docker-compose.yml` 中配置：
```yaml
services:
  apex-app:
    image: apex-scaffold:1.2.0
    environment:
      - SPRING_PROFILES_ACTIVE=prod,enterprise
      - DB_MASTER_URL=jdbc:mysql://mysql:3306/apex_db
      - DB_MASTER_PASSWORD=${DB_MASTER_PASSWORD}
      - JWT_SECRET=${JWT_SECRET}
    env_file:
      - .env
```

### 2.4 Kubernetes

创建 Secret：
```bash
kubectl create secret generic apex-secrets \
  --from-literal=jwt-secret='your-secret-key' \
  --from-literal=db-password='your-db-password'
```

在 Deployment 中引用：
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: apex-app
spec:
  template:
    spec:
      containers:
      - name: apex
        env:
        - name: JWT_SECRET
          valueFrom:
            secretKeyRef:
              name: apex-secrets
              key: jwt-secret
        - name: DB_MASTER_PASSWORD
          valueFrom:
            secretKeyRef:
              name: apex-secrets
              key: db-password
```

---

## 三、环境变量优先级

**优先级从高到低**：

1. **操作系统环境变量**（最高优先级）
2. **Java 系统属性** (`-Dkey=value`)
3. **命令行参数** (`--key=value`)
4. **`.env` 文件**（Spring Boot 自动加载）
5. **`application-{profile}.yml`**
6. **`application.yml`**（最低优先级，默认值）

**示例**：
```bash
# 环境变量会覆盖配置文件中的默认值
export REDIS_PORT=6380

# 即使 application.yml 中配置了 redis.port=6379
# 实际使用的是 6380
```

---

## 四、环境变量完整清单

### 4.1 核心配置（必填）

#### 🔴 Spring Boot 核心

| 环境变量 | 默认值 | 说明 | 必填 |
|---------|--------|------|------|
| `SPRING_PROFILES_ACTIVE` | - | 激活的 Profile（dev/prod,basic/standard/enterprise） | ✅ |
| `SERVER_PORT` | 8080 | 应用端口 | ❌ |
| `MANAGEMENT_SERVER_PORT` | 8081 | 管理端口（Actuator） | ❌ |
| `MANAGEMENT_SERVER_ADDRESS` | 127.0.0.1 | 管理端口监听地址 | ❌ |

**示例**：
```bash
SPRING_PROFILES_ACTIVE=prod,enterprise
SERVER_PORT=8080
MANAGEMENT_SERVER_PORT=8081
```

---

#### 🔴 数据库配置（MySQL）

| 环境变量 | 默认值 | 说明 | 必填 |
|---------|--------|------|------|
| `DB_MASTER_URL` | - | 主库 JDBC URL | ✅ standard+ |
| `DB_MASTER_USERNAME` | root | 主库用户名 | ✅ standard+ |
| `DB_MASTER_PASSWORD` | - | 主库密码 | ✅ standard+ |
| `DB_SLAVE_URL` | - | 从库 JDBC URL | ❌ enterprise |
| `DB_SLAVE_USERNAME` | - | 从库用户名 | ❌ enterprise |
| `DB_SLAVE_PASSWORD` | - | 从库密码 | ❌ enterprise |
| `DB_MAX_POOL_SIZE` | 20 | HikariCP 最大连接数 | ❌ |
| `DB_MIN_IDLE` | 5 | HikariCP 最小空闲连接数 | ❌ |
| `APEX_DATABASE_TYPE` | mysql | 数据库类型（mysql/postgresql） | ❌ |

**示例**：
```bash
DB_MASTER_URL=jdbc:mysql://localhost:3306/apex_db?useSSL=false&serverTimezone=Asia/Shanghai
DB_MASTER_USERNAME=apex_user
DB_MASTER_PASSWORD=your-secure-password
DB_MAX_POOL_SIZE=50
```

**⚠️ 安全提醒**：
- 生产环境必须使用强密码（至少 16 位，包含大小写字母、数字、特殊字符）
- 建议通过 Kubernetes Secret 或 Vault 管理密码
- 禁止在日志中输出数据库密码

---

#### 🔴 Redis 配置

| 环境变量 | 默认值 | 说明 | 必填 |
|---------|--------|------|------|
| `REDIS_HOST` | localhost | Redis 主机地址 | ✅ standard+ |
| `REDIS_PORT` | 6379 | Redis 端口 | ✅ standard+ |
| `REDIS_PASSWORD` | - | Redis 密码 | prod 必填 |
| `REDIS_DATABASE` | 0 | Redis 数据库索引 | ❌ |
| `APEX_REDIS_KEY_PREFIX` | "" | Redis 键统一前缀 | ❌ |
| `APEX_REDIS_LOCK_WATCHDOG_TIMEOUT` | 30000 | Redisson 看门狗超时（毫秒） | ❌ |

**示例**：
```bash
REDIS_HOST=redis.example.com
REDIS_PORT=6379
REDIS_PASSWORD=your-redis-password
APEX_REDIS_KEY_PREFIX=apex:
```

**⚠️ 生产环境强制要求**：
- `REDIS_PASSWORD` 不能为空
- 建议启用 Redis 的 TLS/SSL 连接

---

#### 🔴 JWT 安全配置

| 环境变量 | 默认值 | 说明 | 必填 |
|---------|--------|------|------|
| `JWT_SECRET` | change-me-in-production | JWT 签名密钥（≥32 字节） | prod 必填 |
| `JWT_ACCESS_EXPIRE` | 7200000 | Access Token 有效期（毫秒，2 小时） | ❌ |
| `JWT_REFRESH_EXPIRE` | 604800000 | Refresh Token 有效期（毫秒，7 天） | ❌ |
| `JWT_ACCESS_RENEW_THRESHOLD` | 1800000 | Access Token 自动续期阈值（毫秒） | ❌ |

**示例**：
```bash
JWT_SECRET=$(openssl rand -base64 32)
JWT_ACCESS_EXPIRE=7200000
JWT_REFRESH_EXPIRE=604800000
```

**⚠️ 安全要求**：
- **生产环境必须修改默认值**
- JWT_SECRET 至少 32 字节随机字符串
- 建议使用 `openssl rand -base64 32` 或 `uuidgen` 生成
- 禁止使用简单密码（如 "123456"、"password"）

---

### 4.2 消息队列配置（Enterprise）

#### RocketMQ

| 环境变量 | 默认值 | 说明 | 必填 |
|---------|--------|------|------|
| `ROCKETMQ_NAMESERVER` | - | RocketMQ NameServer 地址 | ✅ enterprise |
| `ROCKETMQ_PRODUCER_GROUP` | apex-producer-group | 生产者组名 | ❌ |
| `ROCKETMQ_SEND_TIMEOUT` | 30000 | 同步发送超时（毫秒） | ❌ |
| `ROCKETMQ_ACCESS_KEY` | - | RocketMQ AccessKey | ❌ |
| `ROCKETMQ_SECRET_KEY` | - | RocketMQ SecretKey | ❌ |
| `APEX_MQ_CONSUMER_ENABLED` | true | 业务消息监听器装配 | ❌ |
| `APEX_MQ_DLQ_ENABLED` | true | 启用死信队列处理 | ❌ |

**示例**：
```bash
ROCKETMQ_NAMESERVER=rocketmq-nameserver:9876
ROCKETMQ_PRODUCER_GROUP=apex-producer-group
APEX_MQ_CONSUMER_ENABLED=true
APEX_MQ_DLQ_ENABLED=true
```

#### Outbox 模式配置

| 环境变量 | 默认值 | 说明 |
|---------|--------|------|
| `OUTBOX_BATCH_SIZE` | 50 | 每次轮询批量取出的事件数 |
| `OUTBOX_MAX_ATTEMPTS` | 8 | 单事件最大投递尝试次数 |
| `OUTBOX_LOCK_TIMEOUT_SECONDS` | 120 | 分布式锁持有时长（秒） |
| `OUTBOX_PARALLELISM` | 4 | 并行投递线程数 |
| `OUTBOX_RETENTION_ENABLED` | true | 启用 Outbox 保留策略 |
| `OUTBOX_RETENTION_DELIVERED_HOURS` | 168 | 已投递事件保留时长（小时） |
| `OUTBOX_RETENTION_DEAD_DAYS` | 30 | 死信事件保留时长（天） |

---

### 4.3 限流熔断配置（Enterprise）

#### Sentinel

| 环境变量 | 默认值 | 说明 |
|---------|--------|------|
| `SENTINEL_DASHBOARD_URL` | - | Sentinel Dashboard 地址 |
| `SENTINEL_TRANSPORT_PORT` | 8719 | 客户端与 Dashboard 通信端口 |
| `SENTINEL_CLIENT_IP` | - | 客户端上报 IP |
| `SENTINEL_EAGER` | true | 启动即初始化 Sentinel 客户端 |
| `SENTINEL_NACOS_ENABLED` | false | 启用 Nacos 动态规则下发 |
| `SENTINEL_NACOS_GROUP` | SENTINEL_GROUP | Nacos 配置分组 |
| `SENTINEL_NACOS_FLOW_DATA_ID` | apex-sentinel-flow-rules | 限流规则 DataId |
| `SENTINEL_NACOS_DEGRADE_DATA_ID` | apex-sentinel-degrade-rules | 熔断规则 DataId |

**示例**：
```bash
SENTINEL_DASHBOARD_URL=http://sentinel-dashboard:8858
SENTINEL_TRANSPORT_PORT=8719
SENTINEL_EAGER=true
```

---

### 4.4 监控追踪配置

#### Prometheus + Grafana

| 环境变量 | 默认值 | 说明 |
|---------|--------|------|
| `GRAFANA_ENABLED` | false | 启用 Grafana 集成 |
| `GRAFANA_URL` | http://localhost:3200 | Grafana 服务地址 |
| `LOKI_ENABLED` | false | 启用 Loki 日志查询 |
| `LOKI_URL` | http://localhost:3100 | Loki 服务地址 |

#### Zipkin 链路追踪

| 环境变量 | 默认值 | 说明 |
|---------|--------|------|
| `TRACING_ENABLED` | true | 启用链路追踪 |
| `TRACE_SAMPLING_RATE` | 0.1 | 采样率（10%） |
| `ZIPKIN_BASE_URL` | http://localhost:9411 | Zipkin 上报端点 |

**示例**：
```bash
TRACING_ENABLED=true
TRACE_SAMPLING_RATE=0.1
ZIPKIN_BASE_URL=http://zipkin:9411
```

---

### 4.5 存储配置

| 环境变量 | 默认值 | 说明 |
|---------|--------|------|
| `STORAGE_DEFAULT_TYPE` | local | 默认存储实现：local/minio/volcengine |
| `STORAGE_BUCKET_NAME` | apex | 默认 Bucket 名称 |
| `STORAGE_PRESIGNED_EXPIRE` | 300 | 预签名 URL 有效期（秒） |
| `STORAGE_LOCAL_BASE_PATH` | ./data/storage | 本地存储根目录 |
| `STORAGE_LOCAL_PUBLIC_BASE_URL` | - | 文件对外访问基础 URL |

#### MinIO 配置

| 环境变量 | 默认值 | 说明 |
|---------|--------|------|
| `MINIO_ENDPOINT` | - | MinIO 端点 |
| `MINIO_ACCESS_KEY` | - | MinIO AccessKey |
| `MINIO_SECRET_KEY` | - | MinIO SecretKey |
| `MINIO_SECURE` | false | 是否使用 HTTPS |

#### 火山引擎 TOS

| 环境变量 | 默认值 | 说明 |
|---------|--------|------|
| `VOLC_ENGINE_ENABLED` | false | 启用火山引擎 TOS |
| `VOLC_ENGINE_ENDPOINT` | - | TOS 端点 |
| `VOLC_ENGINE_REGION` | - | TOS 区域 |
| `VOLC_ENGINE_ACCESS_KEY` | - | TOS AccessKey |
| `VOLC_ENGINE_SECRET_KEY` | - | TOS SecretKey |

---

### 4.6 全文搜索配置（可选）

#### Meilisearch

| 环境变量 | 默认值 | 说明 |
|---------|--------|------|
| `MEILISEARCH_ENABLED` | false | 启用 Meilisearch |
| `MEILISEARCH_URL` | http://localhost:7700 | Meilisearch 服务地址 |
| `MEILISEARCH_API_KEY` | - | Master API Key |
| `MEILISEARCH_INDEX_PREFIX` | apex_ | 索引名称前缀 |

**示例**：
```bash
MEILISEARCH_ENABLED=true
MEILISEARCH_URL=http://meilisearch:7700
MEILISEARCH_API_KEY=your-master-key
MEILISEARCH_INDEX_PREFIX=apex_
```

---

### 4.7 安全配置

| 环境变量 | 默认值 | 说明 |
|---------|--------|------|
| `APEX_CAPTCHA_ENABLED` | true | 启用图形验证码 |
| `APEX_CAPTCHA_LENGTH` | 4 | 验证码字符长度 |
| `APEX_CAPTCHA_TTL_SECONDS` | 120 | 验证码有效期（秒） |
| `APEX_USER_RATE_LIMIT_ENABLED` | true | 启用用户限流 |
| `APEX_ANTI_REPLAY_NONCE_TTL` | 360 | 防重放 nonce TTL（秒） |

#### 内容审核（百度 AI）

| 环境变量 | 默认值 | 说明 | 必填 |
|---------|--------|------|------|
| `BAIDU_TEXT_MODERATION_API_KEY` | - | 百度文本审核 API Key | prod 文件上传时必填 |
| `BAIDU_TEXT_MODERATION_SECRET_KEY` | - | 百度文本审核 Secret Key | prod 文件上传时必填 |
| `BAIDU_IMAGE_MODERATION_API_KEY` | - | 百度图片审核 API Key | prod 文件上传时必填 |
| `BAIDU_IMAGE_MODERATION_SECRET_KEY` | - | 百度图片审核 Secret Key | prod 文件上传时必填 |

---

<a id="48-服务治理配置"></a>

### 4.8 服务治理配置

#### Apex Governance（优雅关停）

| 环境变量 | 默认值 | 说明 |
|---------|--------|------|
| `APEX_GOVERNANCE_ENABLED` | true | 启用统一 readiness、资源排空和优雅关停协调 |
| `APEX_GOVERNANCE_DEPLOYMENT_MODE` | auto | 部署模式：auto/kubernetes/compose |
| `APEX_GOVERNANCE_AUTO_DETECTION_ENABLED` | true | 启用部署环境自动识别 |
| `APEX_GOVERNANCE_FAIL_ON_AMBIGUOUS` | true | 检测到多个平台证据时是否阻止启动 |
| `APEX_GOVERNANCE_REGISTRATION_PROVIDER` | auto | 注册方式：auto/none/nacos/platform |
| `APEX_GOVERNANCE_REGISTRATION_FAIL_MODE` | auto | 注册中心故障处理：auto/open/closed |
| `APEX_GOVERNANCE_MQ_DRAIN_TIMEOUT` | 30s | MQ 在途消息排空预算 |
| `APEX_GOVERNANCE_SHUTDOWN_TIMEOUT` | 90s | 所有资源共享的总关停预算 |
| `APEX_GOVERNANCE_READINESS_WITHDRAW_TIMEOUT` | 5s | readiness 摘流后的传播等待预算 |
| `APEX_GOVERNANCE_FORCE_SHUTDOWN` | true | 总预算耗尽后是否强制停止剩余资源 |

---

### 4.9 其他系统配置

#### Nacos 配置中心

| 环境变量 | 默认值 | 说明 |
|---------|--------|------|
| `NACOS_SERVER_ADDR` | 127.0.0.1:8848 | Nacos 服务器地址 |
| `NACOS_NAMESPACE` | public | Nacos 命名空间 |
| `NACOS_CONFIG_ENABLED` | true | 启用 Nacos 配置中心 |
| `NACOS_DISCOVERY_ENABLED` | true | 启用 Nacos 服务发现 |
| `NACOS_REGISTER_ENABLED` | true | 注册到 Nacos |

#### License 配置

| 环境变量 | 默认值 | 说明 |
|---------|--------|------|
| `APEX_LICENSE_ENABLED` | true | 启用 License 校验 |
| `APEX_LICENSE_FILE` | classpath:license/license-basic.json | License 文件路径 |
| `APEX_LICENSE_PUBLIC_KEY_FILE` | classpath:license/public.key | 公钥文件路径 |

#### 其他

| 环境变量 | 默认值 | 说明 |
|---------|--------|------|
| `FILE_UPLOAD_ENABLED` | false | 启用文件上传 |
| `FILE_UPLOAD_MAX_SIZE` | 10485760 | 单文件最大字节数（10MB） |
| `OPERATION_LOG_ENABLED` | true | 启用操作日志 |
| `APEX_RESPONSE_TIMEOUT` | 30s | 全局响应超时 |
| `APEX_SQL_SLOW_ENABLED` | true | 启用慢 SQL 指标采集 |
| `APEX_SQL_SLOW_THRESHOLD_MS` | 1000 | 慢 SQL 判定阈值（毫秒） |
| `MAX_IN_MEMORY_SIZE` | 10MB | 请求体/响应体内存缓冲上限 |

---

## 五、按 Profile 分类

### 5.1 basic Profile（本地开发）

**依赖**: 无外部服务

**必填环境变量**: 无

**可选环境变量**:
```bash
SERVER_PORT=8080
APEX_LICENSE_ENABLED=false
```

---

### 5.2 standard Profile（中型企业）

**依赖**: MySQL + Redis

**必填环境变量**:
```bash
# 数据库
DB_MASTER_URL=jdbc:mysql://localhost:3306/apex_db
DB_MASTER_USERNAME=root
DB_MASTER_PASSWORD=your-password

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379
# 生产环境必填
REDIS_PASSWORD=your-redis-password

# JWT
JWT_SECRET=your-secret-key-at-least-32-bytes
```

---

### 5.3 enterprise Profile（生产环境）

**依赖**: MySQL + Redis + RocketMQ + Nacos + Zipkin

**必填环境变量**（除 standard 外额外需要）:
```bash
# RocketMQ
ROCKETMQ_NAMESERVER=rocketmq-nameserver:9876

# Nacos
NACOS_SERVER_ADDR=nacos:8848

# Zipkin
ZIPKIN_BASE_URL=http://zipkin:9411

# Sentinel
SENTINEL_DASHBOARD_URL=http://sentinel-dashboard:8858
```

---

## 六、环境变量命名规范

### 6.1 命名规则

| 前缀 | 用途 | 示例 |
|------|------|------|
| `APEX_*` | 应用自定义配置 | `APEX_GOVERNANCE_ENABLED` |
| `SPRING_*` | Spring 框架配置 | `SPRING_PROFILES_ACTIVE` |
| `DB_*` | 数据库配置 | `DB_MASTER_URL` |
| `REDIS_*` | Redis 配置 | `REDIS_HOST` |
| `ROCKETMQ_*` | RocketMQ 配置 | `ROCKETMQ_NAMESERVER` |
| `NACOS_*` | Nacos 配置 | `NACOS_SERVER_ADDR` |
| `SENTINEL_*` | Sentinel 配置 | `SENTINEL_DASHBOARD_URL` |
| `JWT_*` | JWT 配置 | `JWT_SECRET` |
| `STORAGE_*` | 存储配置 | `STORAGE_DEFAULT_TYPE` |

### 6.2 敏感配置后缀

| 后缀 | 用途 | 示例 |
|------|------|------|
| `*_PASSWORD` | 密码 | `DB_MASTER_PASSWORD` |
| `*_SECRET` / `*_SECRET_KEY` | 密钥 | `JWT_SECRET`, `REDIS_SECRET_KEY` |
| `*_TOKEN` | Token | `GITHUB_TOKEN` |
| `*_API_KEY` | API Key | `BAIDU_TEXT_MODERATION_API_KEY` |

---

## 七、安全最佳实践

### 7.1 敏感信息管理

❌ **禁止做法**:
```bash
# 不要在代码中硬编码
String password = "my-password-123";

# 不要在配置文件中明文存储
application.yml:
  datasource:
    password: my-password-123

# 不要提交 .env 文件到 Git
git add .env  # 危险！
```

✅ **推荐做法**:
```bash
# 1. 使用环境变量
export DB_MASTER_PASSWORD=$(cat /run/secrets/db-password)

# 2. 使用 Kubernetes Secret
kubectl create secret generic db-creds --from-literal=password='...'

# 3. 使用 HashiCorp Vault
vault kv get secret/apex/db-password

# 4. 使用云服务商的密钥管理服务
# AWS Secrets Manager
# Azure Key Vault
# Google Secret Manager
```

### 7.2 密码强度要求

| 配置项 | 最低要求 | 推荐 |
|--------|---------|------|
| `JWT_SECRET` | ≥32 字节 | 64 字节随机字符串 |
| `DB_MASTER_PASSWORD` | ≥16 位，包含大小写字母+数字+特殊字符 | ≥20 位 |
| `REDIS_PASSWORD` | ≥16 位 | ≥20 位 |
| `ROCKETMQ_SECRET_KEY` | ≥32 位 | 64 位 |

**生成强密码**:
```bash
# JWT Secret（Base64 编码）
openssl rand -base64 64

# 通用密码
openssl rand -hex 32

# UUID
uuidgen
```

### 7.3 环境隔离

| 环境 | 配置策略 | 示例 |
|------|---------|------|
| **开发环境** | 可使用弱密码，允许 `.env` 文件 | `JWT_SECRET=dev-secret` |
| **测试环境** | 使用中等强度密码，独立数据库 | `DB_MASTER_PASSWORD=$(vault read ...)` |
| **生产环境** | 强制强密码，禁止 `.env` 文件，使用密钥管理服务 | 所有敏感配置通过 K8s Secret/Vault 注入 |

---

## 八、故障排查

### 8.1 常见问题

#### 问题 1: 环境变量未生效

**现象**: 修改环境变量后应用仍使用旧值

**原因**:
1. 环境变量优先级低于其他配置源
2. 应用未重启
3. 环境变量名称错误（大小写敏感）

**解决方案**:
```bash
# 1. 验证环境变量已设置
echo $JWT_SECRET

# 2. 重启应用
./scripts/deploy/app.sh restart prod enterprise

# 3. 检查应用启动日志
tail -f logs/apex-app.log | grep JWT_SECRET
```

---

#### 问题 2: 数据库连接失败

**现象**: `Cannot create PoolableConnectionFactory`

**检查清单**:
```bash
# 1. 验证数据库环境变量
echo $DB_MASTER_URL
echo $DB_MASTER_USERNAME
echo $DB_MASTER_PASSWORD

# 2. 测试数据库连通性
mysql -h localhost -P 3306 -u root -p

# 3. 检查 JDBC URL 格式
# 正确格式：
jdbc:mysql://host:port/database?useSSL=false&serverTimezone=Asia/Shanghai
```

---

#### 问题 3: Redis 连接超时

**现象**: `RedisConnectionException: Unable to connect to Redis`

**检查清单**:
```bash
# 1. 验证 Redis 环境变量
echo $REDIS_HOST
echo $REDIS_PORT
echo $REDIS_PASSWORD

# 2. 测试 Redis 连通性
redis-cli -h $REDIS_HOST -p $REDIS_PORT -a $REDIS_PASSWORD ping

# 3. 检查防火墙规则
telnet $REDIS_HOST $REDIS_PORT
```

---

#### 问题 4: JWT Token 验证失败

**现象**: `JWT signature does not match`

**原因**: JWT_SECRET 不一致

**解决方案**:
```bash
# 1. 确保所有应用实例使用相同的 JWT_SECRET
# 2. 检查 JWT_SECRET 长度 >= 32 字节
# 3. 重新生成 Token
```

---

### 8.2 调试技巧

#### 查看应用实际使用的配置值

**方法 1: Actuator Endpoint**（仅开发环境）
```bash
# 访问配置端点（需要管理员权限）
curl http://localhost:8081/actuator/configprops

# 查看环境变量
curl http://localhost:8081/actuator/env
```

**方法 2: 启动日志**
```bash
# 应用启动时会打印激活的 Profile 和关键配置
grep "The following profiles are active" logs/apex-app.log
```

**方法 3: 代码中打印**（临时调试）
```java
@Value("${jwt.secret}")
private String jwtSecret;

@PostConstruct
public void init() {
    log.info("JWT_SECRET length: {}", jwtSecret.length());
}
```

---

## 九、参考资源

### 9.1 内部文档

- [配置清单](../../05-operations/configuration-inventory.md) - 172 个环境变量完整清单
- [部署指南](deployment.md) - 生产部署完整流程
- [安全防护](../../05-operations/security-protections.md) - 安全配置详解
- [AGENTS.md](../../../AGENTS.md) - AI 编码规范（包含环境变量使用规范）

### 9.2 外部资源

- [Spring Boot Externalized Configuration](https://docs.spring.io/spring-boot/reference/features/external-config.html)
- [12-Factor App - Config](https://12factor.net/config)
- [Kubernetes Secrets](https://kubernetes.io/docs/concepts/configuration/secret/)
- [Docker Compose Environment Variables](https://docs.docker.com/compose/environment-variables/)

---

## 十、快速参考

### 10.1 最小启动配置（basic）

```bash
SPRING_PROFILES_ACTIVE=dev,basic
```

### 10.2 标准启动配置（standard）

```bash
SPRING_PROFILES_ACTIVE=prod,standard
DB_MASTER_URL=jdbc:mysql://localhost:3306/apex_db
DB_MASTER_USERNAME=root
DB_MASTER_PASSWORD=your-password
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=your-redis-password
JWT_SECRET=your-secret-key-at-least-32-bytes
```

### 10.3 企业启动配置（enterprise）

```bash
SPRING_PROFILES_ACTIVE=prod,enterprise
# 数据库
DB_MASTER_URL=jdbc:mysql://mysql:3306/apex_db
DB_MASTER_USERNAME=apex_user
DB_MASTER_PASSWORD=your-secure-password
# Redis
REDIS_HOST=redis
REDIS_PORT=6379
REDIS_PASSWORD=your-redis-password
# JWT
JWT_SECRET=your-secret-key-at-least-32-bytes
# RocketMQ
ROCKETMQ_NAMESERVER=rocketmq:9876
# Nacos
NACOS_SERVER_ADDR=nacos:8848
# Zipkin
ZIPKIN_BASE_URL=http://zipkin:9411
```

---

**文档版本**: 1.0.0  
**最后更新**: 2026-09-09  
**维护者**: Apex Team
