# 配置文件深度分析 - 环境变量配置清单

**创建日期**: 2026-09-09  
**分析范围**: 所有配置文件（application*.yml, bootstrap.yml）+ Nacos 配置中心 dataId（见 §2.16）
**项目版本**: 1.3.1

---

## 一、环境变量统计概览

### 总体统计

| 指标 | 数量 |
|------|------|
| **`.env.example` 变量条目** | 511 行，其中 280 个有效赋值（其余为注释与分组说明，实际生效以部署注入为准） |
| **覆盖说明** | 模板覆盖部署常用变量；未列入模板的键按服务 `application*.yml` 默认值运行 |

### 按功能模块分类统计

| 模块 | 变量数 | 说明 |
|------|--------|------|
| **服务治理** | 13 | Apex Governance 相关配置 |
| **数据库** | 11 | MySQL 主从库配置 |
| **缓存** | 17 | Redis + Redisson 配置 |
| **消息队列** | 17 | RocketMQ + Outbox 配置 |
| **安全认证** | 18 | JWT + Keycloak + 验证码配置 |
| **监控追踪** | 10 | Prometheus + Grafana + Zipkin + Loki 配置 |
| **限流熔断** | 10 | Sentinel 配置 |
| **存储** | 以模板为准 | Local / MinIO / TOS / OSS / 七牛及 file 验签配置 |
| **内容审核** | 4 | 百度 AI 文本/图片审核 |
| **全文搜索** | 4 | Meilisearch 配置 |
| **告警通知** | 2 | 钉钉 Webhook 配置 |
| **邮件发送** | 4 | SMTP 配置 |
| **License** | 3 | 商业授权配置 |
| **第三方集成** | 22 | 微信/企业微信/创蓝短信 |
| **系统配置** | 27 | 其他系统级配置 |

---

## 二、环境变量详细清单

### 2.1 服务治理配置（Apex Governance）

| 环境变量 | 默认值 | 说明 | Profile | 必填 |
|---------|--------|------|---------|------|
| `APEX_GOVERNANCE_ENABLED` | `true` | 启用统一 readiness、资源排空和优雅关停协调 | 全部 | ❌ |
| `APEX_GOVERNANCE_DEPLOYMENT_MODE` | `auto` | 部署模式：auto/kubernetes/compose | 全部 | ❌ |
| `APEX_GOVERNANCE_AUTO_DETECTION_ENABLED` | `true` | 启用部署环境自动识别 | 全部 | ❌ |
| `APEX_GOVERNANCE_FAIL_ON_AMBIGUOUS` | `true` | 检测到多个平台证据时是否阻止启动 | 全部 | ❌ |
| `APEX_GOVERNANCE_REGISTRATION_PROVIDER` | `auto` | 注册方式：auto/none/nacos/platform | 全部 | ❌ |
| `APEX_GOVERNANCE_REGISTRATION_FAIL_MODE` | `auto` | 注册中心故障处理：auto/open/closed | 全部 | ❌ |
| `APEX_GOVERNANCE_MQ_DRAIN_TIMEOUT` | `30s` | MQ 在途消息排空预算 | 全部 | ❌ |
| `APEX_GOVERNANCE_SHUTDOWN_TIMEOUT` | `90s` | 所有资源共享的总关停预算 | 全部 | ❌ |
| `APEX_GOVERNANCE_READINESS_WITHDRAW_TIMEOUT` | `5s` | readiness 摘流后的传播等待预算 | 全部 | ❌ |
| `APEX_GOVERNANCE_FORCE_SHUTDOWN` | `true` | 总预算耗尽后是否强制停止剩余资源 | 全部 | ❌ |

**在 .env.example 中**: ✅ APEX_GOVERNANCE_* 10 项全部已覆盖

---

### 2.2 数据库配置（MySQL）

| 环境变量 | 默认值 | 说明 | Profile | 必填 |
|---------|--------|------|---------|------|
| `DB_MASTER_URL` | - | 主库 JDBC URL | standard+ | ✅ |
| `DB_MASTER_USERNAME` | `root` | 主库用户名 | standard+ | ✅ |
| `DB_MASTER_PASSWORD` | - | 主库密码 | standard+ | ✅ |
| `DB_SLAVE_URL` | - | 从库 JDBC URL | enterprise | ❌ |
| `DB_SLAVE_USERNAME` | - | 从库用户名 | enterprise | ❌ |
| `DB_SLAVE_PASSWORD` | - | 从库密码 | enterprise | ❌ |
| `DB_MAX_POOL_SIZE` | `20` | HikariCP 最大连接数 | 全部 | ❌ |
| `DB_MIN_IDLE` | `5` | HikariCP 最小空闲连接数 | 全部 | ❌ |
| `DB_MAX_ACTIVE` | `20` | Druid 最大活跃连接数 | prod | ❌ |
| `DB_MAX_WAIT_MILLIS` | `60000` | Druid 获取连接最大等待时间 | prod | ❌ |
| `APEX_DATABASE_TYPE` | `mysql` | 数据库类型：mysql/postgresql | 全部 | ❌ |

**在 .env.example 中**: ✅ DB_MASTER_* 已覆盖，其他缺失

---

### 2.3 缓存配置（Redis + Redisson）

| 环境变量 | 默认值 | 说明 | Profile | 必填 |
|---------|--------|------|---------|------|
| `REDIS_HOST` | `localhost` | Redis 主机地址 | standard+ | ✅ |
| `REDIS_PORT` | `6379` | Redis 端口 | standard+ | ✅ |
| `REDIS_PASSWORD` | - | Redis 密码（prod 强制要求） | standard+ | prod 必填 |
| `REDIS_DATABASE` | `0` | Redis 数据库索引 | 全部 | ❌ |
| `REDIS_POOL_MAX_ACTIVE` | `8` | 连接池最大连接数 | 全部 | ❌ |
| `REDIS_POOL_MAX_IDLE` | `8` | 连接池最大空闲连接数 | 全部 | ❌ |
| `REDIS_POOL_MIN_IDLE` | `0` | 连接池最小空闲连接数 | 全部 | ❌ |
| `REDIS_POOL_MAX_WAIT` | `-1` | 连接池最大等待时间（毫秒） | 全部 | ❌ |
| `APEX_REDIS_KEY_PREFIX` | `""` | Redis 键统一前缀 | 全部 | ❌ |
| `APEX_REDIS_LOCK_WATCHDOG_TIMEOUT` | `30000` | Redisson 看门狗超时（毫秒） | 全部 | ❌ |
| `APEX_REDIS_COMMAND_TIMEOUT` | `3000` | Redis 命令超时（毫秒） | 全部 | ❌ |
| `APEX_REDIS_RETRY_ATTEMPTS` | `3` | 命令失败重试次数 | 全部 | ❌ |
| `APEX_REDIS_RETRY_INTERVAL` | `1500` | 重试间隔（毫秒） | 全部 | ❌ |
| `APEX_REDIS_CLEANUP_ON_STARTUP` | `true` | 启动时清理旧格式数据 | dev | ❌ |

**在 .env.example 中**: ✅ REDIS_HOST/PORT/PASSWORD/DATABASE/POOL_MAX_ACTIVE/MAX_IDLE/MIN_IDLE/MAX_WAIT 全部已覆盖

---

### 2.4 消息队列配置（RocketMQ + Outbox）

| 环境变量 | 默认值 | 说明 | Profile | 必填 |
|---------|--------|------|---------|------|
| `ROCKETMQ_NAMESERVER` | - | RocketMQ NameServer 地址 | enterprise | ✅ |
| `ROCKETMQ_NAMESRV_ADDR` | - | RocketMQ NameServer 地址（备用） | enterprise | ✅ |
| `ROCKETMQ_PRODUCER_GROUP` | `apex-producer-group` | 生产者组名 | enterprise | ❌ |
| `ROCKETMQ_SEND_TIMEOUT` | `30000` | 同步发送超时（毫秒） | enterprise | ❌ |
| `ROCKETMQ_ACCESS_KEY` | - | RocketMQ AccessKey | enterprise | ❌ |
| `ROCKETMQ_SECRET_KEY` | - | RocketMQ SecretKey | enterprise | ❌ |
| `ROCKETMQ_CONSUMER_ENABLED` | `true` | RocketMQ Starter 消费者装配 | enterprise | ❌ |
| `APEX_MQ_CONSUMER_ENABLED` | `true` | 业务消息监听器装配 | enterprise | ❌ |
| `APEX_MQ_DLQ_ENABLED` | `true` | 启用死信队列处理 | enterprise | ❌ |
| `APEX_MQ_DOMAIN_EVENT_ENABLED` | `true` | 领域事件 MQ 投递 | enterprise | ❌ |
| `APEX_MQ_DOMAIN_EVENT_TOPIC` | `apex-domain-event` | 领域事件 Topic 名称 | enterprise | ❌ |
| `MQ_IDEMPOTENT_TTL_MINUTES` | `30` | 消费幂等记录 TTL（分钟） | enterprise | ❌ |
| `OUTBOX_BATCH_SIZE` | `50` | Outbox 每次轮询批量取出的事件数 | 全部 | ❌ |
| `OUTBOX_MAX_ATTEMPTS` | `8` | 单事件最大投递尝试次数 | 全部 | ❌ |
| `OUTBOX_LOCK_TIMEOUT_SECONDS` | `120` | 分布式锁持有时长（秒） | 全部 | ❌ |
| `OUTBOX_PARALLELISM` | `4` | 并行投递线程数 | 全部 | ❌ |
| `OUTBOX_RETENTION_ENABLED` | `true` | 启用 Outbox 保留策略 | 全部 | ❌ |
| `OUTBOX_RETENTION_DELIVERED_HOURS` | `168` | 已投递事件保留时长（小时） | 全部 | ❌ |
| `OUTBOX_RETENTION_DEAD_DAYS` | `30` | 死信事件保留时长（天） | 全部 | ❌ |
| `OUTBOX_RETENTION_BATCH_SIZE` | `5000` | 每次清理批次大小（条） | 全部 | ❌ |

**在 .env.example 中**: ✅ 已覆盖（OUTBOX_BATCH_SIZE/MAX_ATTEMPTS/PARALLELISM/LOCK_TIMEOUT_SECONDS/RETENTION_* 共 8 项）

---

### 2.5 安全认证配置（JWT + Keycloak + 验证码）

| 环境变量 | 默认值 | 说明 | Profile | 必填 |
|---------|--------|------|---------|------|
| `JWT_SECRET` | `change-me-in-production` | JWT 签名密钥（≥32 字节） | 全部 | ✅ prod 必填 |
| `JWT_ACCESS_EXPIRE` | `7200000` | Access Token 有效期（毫秒，2 小时） | 全部 | ❌ |
| `JWT_REFRESH_EXPIRE` | `604800000` | Refresh Token 有效期（毫秒，7 天） | 全部 | ❌ |
| `JWT_ACCESS_RENEW_THRESHOLD` | `1800000` | Access Token 自动续期阈值（毫秒） | 全部 | ❌ |
| `KEYCLOAK_ENABLED` | `false` | 启用 Keycloak 集成 | 全部 | ❌ |
| `KEYCLOAK_SERVER_URL` | `http://localhost:8180` | Keycloak 服务器地址 | 全部 | ❌ |
| `KEYCLOAK_REALM` | `apex` | Keycloak Realm 名称 | 全部 | ❌ |
| `KEYCLOAK_ADMIN_USERNAME` | `admin` | Keycloak Admin 用户名 | 全部 | ❌ |
| `KEYCLOAK_ADMIN_PASSWORD` | `admin123` | Keycloak Admin 密码 | 全部 | ❌ |
| `KEYCLOAK_ADMIN_CLIENT_ID` | `admin-cli` | Admin Client ID | 全部 | ❌ |
| `KEYCLOAK_CLIENT_ID` | `apex-client` | OAuth2 Client ID | 全部 | ❌ |
| `KEYCLOAK_CLIENT_SECRET` | - | OAuth2 Client Secret | 全部 | ❌ |
| `KEYCLOAK_USER_SYNC_ENABLED` | `true` | 用户同步开关 | 全部 | ❌ |
| `KEYCLOAK_FRONTEND_CALLBACK_URL` | `http://localhost:3000/auth/callback` | 前端回调地址 | 全部 | ❌ |
| `APEX_CAPTCHA_ENABLED` | `true` | 启用图形验证码 | 全部 | ❌ |
| `APEX_CAPTCHA_LENGTH` | `4` | 验证码字符长度 | 全部 | ❌ |
| `APEX_CAPTCHA_TTL_SECONDS` | `120s` | 验证码有效期（秒） | 全部 | ❌ |
| `APEX_ANTI_REPLAY_NONCE_TTL` | `360` | 防重放 nonce TTL（秒） | 全部 | ❌ |

**在 .env.example 中**: ✅ JWT_SECRET, Keycloak 主要配置已覆盖，验证码和防重放配置缺失

---

### 2.6 监控追踪配置（Prometheus + Grafana + Zipkin + Loki）

| 环境变量 | 默认值 | 说明 | Profile | 必填 |
|---------|--------|------|---------|------|
| `MANAGEMENT_SERVER_ADDRESS` | `127.0.0.1` | 管理端口监听地址 | 全部 | ❌ |
| `MANAGEMENT_SERVER_PORT` | 见服务配置 | 管理端口；system-service 固定 8083、integration-service 固定 8097，gateway 复用业务端口 | 部分服务 | ❌ |
| `TRACING_ENABLED` | `true` | 启用链路追踪 | enterprise | ❌ |
| `TRACE_SAMPLING_RATE` | `0.1` | 采样率（10%） | enterprise | ❌ |
| `ZIPKIN_BASE_URL` | `http://localhost:9411` | Zipkin 上报端点 | enterprise | ❌ |
| `GRAFANA_ENABLED` | `false` | 启用 Grafana 集成 | 全部 | ❌ |
| `GRAFANA_URL` | `http://localhost:3200` | Grafana 服务地址 | 全部 | ❌ |
| `LOKI_ENABLED` | `false` | 启用 Loki 日志查询 | 全部 | ❌ |
| `LOKI_URL` | `http://localhost:3100` | Loki 服务地址 | 全部 | ❌ |
| `TRACE_SLOW_THRESHOLD_MS` | - | 慢请求判定阈值（毫秒） | 全部 | ❌ |

**在 .env.example 中**: ✅ 部分已覆盖——TRACING_ENABLED、TRACE_SAMPLING_RATE、ZIPKIN_BASE_URL、LOKI_ENABLED、GRAFANA_ENABLED 已列入；GRAFANA_URL、TRACE_SLOW_THRESHOLD_MS 未列入（按 yml 默认值运行）

---

### 2.7 限流熔断配置（Sentinel）

| 环境变量 | 默认值 | 说明 | Profile | 必填 |
|---------|--------|------|---------|------|
| `SENTINEL_DASHBOARD_URL` | - | Sentinel Dashboard 地址 | enterprise | ❌ |
| `SENTINEL_TRANSPORT_PORT` | `8719` | 客户端与 Dashboard 通信端口 | enterprise | ❌ |
| `SENTINEL_CLIENT_IP` | - | 客户端上报 IP | enterprise | ❌ |
| `SENTINEL_EAGER` | `true` | 启动即初始化 Sentinel 客户端 | enterprise | ❌ |
| `SENTINEL_HOST` | - | Sentinel 主机地址 | enterprise | ❌ |
| `SENTINEL_PORT` | - | Sentinel 端口 | enterprise | ❌ |
| `SENTINEL_NACOS_ENABLED` | `false` | 启用 Nacos 动态规则下发 | enterprise | ❌ |
| `SENTINEL_NACOS_GROUP` | `SENTINEL_GROUP` | Nacos 配置分组 | enterprise | ❌ |
| `SENTINEL_NACOS_FLOW_DATA_ID` | `apex-sentinel-flow-rules` | 限流规则 DataId | enterprise | ❌ |
| `SENTINEL_NACOS_DEGRADE_DATA_ID` | `apex-sentinel-degrade-rules` | 熔断规则 DataId | enterprise | ❌ |

**在 .env.example 中**: ✅ 全部已覆盖（10 项 SENTINEL_* 变量）

---

### 2.8 存储配置（五后端）

当前字段已列入 `.env.example` 并由 Compose 显式注入 file 容器。完整厂商字段、默认值与认证限制见[对象存储接入指南](object-storage-integration.md)和[file 主配置](../../apex-services-bom/apex-file-service/src/main/resources/application.yml)。本节仅列关键变量，不能以历史总数代替模板核对。

| 环境变量 | 默认值 | 说明 | Profile | 必填 |
|---------|--------|------|---------|------|
| `STORAGE_DEFAULT_TYPE` | `local` | 默认实现：local/minio/volcengine/aliyun-oss/qiniu | 文件服务 | ❌ |
| `STORAGE_BUCKET_NAME` | `apex` | 默认 Bucket 名称 | 文件服务 | ❌ |
| `STORAGE_PRESIGNED_URL_EXPIRE_SECONDS` | `300` | 预签名 URL 有效期（秒） | 文件服务 | ❌ |
| `STORAGE_LOCAL_ENABLED` | `true` | 启用本地存储后端 | 文件服务 | ❌ |
| `STORAGE_LOCAL_BASE_PATH` | `./data/storage` | 本地存储根目录 | 文件服务 | ❌ |
| `FILE_JWT_PUBLIC_KEY_BASE64` | 空 | auth RS256 单公钥，X509 DER Base64；无 `kid` Token 兼容；多公钥配置存在时被覆盖 | 文件服务 | RS256 单/多公钥至少一项 |
| `FILE_JWT_PUBLIC_KEYS_BASE64` | 空 | 静态多公钥，格式 `kid=Base64PublicKey,kid=Base64PublicKey`，按 JWT header 的 `kid` 选择；缺少或未知 `kid` 拒绝，轮换需更新配置并重启 | 文件服务 | RS256 单/多公钥至少一项 |
| `FILE_JWT_SECRET` | 空 | 仅历史 HMAC 签发方使用，与签名密钥一致且至少 32 字节 | 文件服务 | HMAC 必填 |
| `MINIO_ENDPOINT` | - | MinIO 端点 | 文件服务 | ❌ |
| `MINIO_ACCESS_KEY` | - | MinIO AccessKey | 文件服务 | ❌ |
| `MINIO_SECRET_KEY` | - | MinIO SecretKey | 文件服务 | ❌ |
| `STORAGE_MINIO_ENABLED` | `false` | MinIO 开关；HTTPS 由 endpoint scheme 决定 | 文件服务 | ❌ |
| `STORAGE_ALIYUN_OSS_ENABLED` | `false` | OSS 开关 | 文件服务 | ❌ |
| `STORAGE_ALIYUN_OSS_ENDPOINT` | 空 | OSS 端点 | 文件服务 | 启用必填 |
| `STORAGE_ALIYUN_OSS_ACCESS_KEY` / `STORAGE_ALIYUN_OSS_SECRET_KEY` | 空 | OSS 凭据 | 文件服务 | 启用必填 |
| `STORAGE_QINIU_ENABLED` | `false` | 七牛开关 | 文件服务 | ❌ |
| `STORAGE_QINIU_REGION` / `STORAGE_QINIU_BUCKET` | 空 | 桶所属区域及桶名，须与业务桶一致 | 文件服务 | 启用必填 |
| `STORAGE_QINIU_DOWNLOAD_DOMAIN` | 空 | 绑定同一桶的 HTTPS 域名，无路径/尾斜杠 | 文件服务 | 启用必填 |
| `STORAGE_QINIU_ACCESS_KEY` / `STORAGE_QINIU_SECRET_KEY` | 空 | 七牛凭据 | 文件服务 | 启用必填 |
| `STORAGE_VOLCENGINE_ENABLED` | `false` | 启用火山引擎 TOS | 文件服务 | ❌ |
| `STORAGE_VOLCENGINE_ENDPOINT` | - | TOS 端点 | 文件服务 | ❌ |
| `STORAGE_VOLCENGINE_REGION` | - | TOS 区域 | 文件服务 | ❌ |
| `STORAGE_VOLCENGINE_ACCESS_KEY` | - | TOS AccessKey | 文件服务 | ❌ |
| `STORAGE_VOLCENGINE_SECRET_KEY` | - | TOS SecretKey | 文件服务 | 启用必填 |

**在 .env.example 中**：已提供五后端变量；云桶须预创建。Local 不提供无认证预签名，旧 PUBLIC_BASE_URL、MINIO_SECURE 已移除。

---

### 2.9 内容审核配置（百度 AI）

| 环境变量 | 默认值 | 说明 | Profile | 必填 |
|---------|--------|------|---------|------|
| `BAIDU_TEXT_MODERATION_API_KEY` | - | 百度文本审核 API Key | 全部 | prod 文件上传时必填 |
| `BAIDU_TEXT_MODERATION_SECRET_KEY` | - | 百度文本审核 Secret Key | 全部 | prod 文件上传时必填 |
| `BAIDU_IMAGE_MODERATION_API_KEY` | - | 百度图片审核 API Key | 全部 | prod 文件上传时必填 |
| `BAIDU_IMAGE_MODERATION_SECRET_KEY` | - | 百度图片审核 Secret Key | 全部 | prod 文件上传时必填 |

**在 .env.example 中**: ❌ 全部缺失

---

### 2.10 全文搜索配置（Meilisearch）

| 环境变量 | 默认值 | 说明 | Profile | 必填 |
|---------|--------|------|---------|------|
| `MEILISEARCH_ENABLED` | `false` | 启用 Meilisearch | dev/enterprise | ❌ |
| `MEILISEARCH_URL` | `http://localhost:7700` | Meilisearch 服务地址 | 全部 | ❌ |
| `MEILISEARCH_API_KEY` | - | Master API Key | 全部 | ❌ |
| `MEILISEARCH_INDEX_PREFIX` | `apex_` | 索引名称前缀 | 全部 | ❌ |

**在 .env.example 中**: ✅ MEILISEARCH_ENABLED/URL/API_KEY/INDEX_PREFIX 已覆盖

---

### 2.11 告警通知配置（钉钉）

| 环境变量 | 默认值 | 说明 | Profile | 必填 |
|---------|--------|------|---------|------|
| `DINGDING_WEBHOOK` | - | 钉钉机器人 Webhook | 全部 | ❌ |
| `DINGDING_SECRET` | - | 钉钉加签密钥 | 全部 | ❌ |

**在 .env.example 中**: ❌ 全部缺失

---

### 2.12 邮件发送配置（SMTP）

| 环境变量 | 默认值 | 说明 | Profile | 必填 |
|---------|--------|------|---------|------|
| `MAIL_HOST` | - | SMTP 服务器 | 全部 | ❌ |
| `MAIL_PORT` | `465` | SMTP 端口 | 全部 | ❌ |
| `MAIL_USERNAME` | - | SMTP 用户名 | 全部 | ❌ |
| `MAIL_PASSWORD` | - | SMTP 密码 | 全部 | ❌ |

**在 .env.example 中**: ❌ 全部缺失

---

### 2.13 License 配置（商业授权）

| 环境变量 | 默认值 | 说明 | Profile | 必填 |
|---------|--------|------|---------|------|
| `APEX_LICENSE_ENABLED` | `true` | 启用 License 校验 | 全部 | ❌ |
| `APEX_LICENSE_FILE` | `classpath:license/license-basic.json` | License 文件路径 | 全部 | ❌ |
| `APEX_LICENSE_PUBLIC_KEY_FILE` | `classpath:license/public.key` | 公钥文件路径 | 全部 | ❌ |

**在 .env.example 中**: ✅ APEX_LICENSE_FILE 已覆盖

---

### 2.14 第三方集成配置（微信/企业微信/创蓝短信）

| 环境变量 | 默认值 | 说明 | Profile | 必填 |
|---------|--------|------|---------|------|
| `WECHAT_APP_ID` | - | 微信 AppId | 全部 | ❌ |
| `WECHAT_APP_SECRET` | - | 微信 AppSecret | 全部 | ❌ |
| `WECHAT_LOGIN_URL` | - | 微信登录 URL | 全部 | ❌ |
| `WECHAT_USER_INFO_URL` | - | 微信用户信息 URL | 全部 | ❌ |
| `WEWORK_CORP_ID` | - | 企业微信 CorpId | 全部 | ❌ |
| `WEWORK_AGENT_ID` | - | 企业微信 AgentId | 全部 | ❌ |
| `WEWORK_SECRET` | - | 企业微信 Secret | 全部 | ❌ |
| `WEWORK_TOKEN` | - | 企业微信 Token | 全部 | ❌ |
| `WEWORK_ENCODING_AES_KEY` | - | 企业微信 EncodingAESKey | 全部 | ❌ |
| `CL_SMS_HOST` | - | 创蓝短信主机 | 全部 | ❌ |
| `CL_SMS_PORT` | - | 创蓝短信端口 | 全部 | ❌ |
| `CL_SMS_ACCOUNT` | - | 创蓝短信账号 | 全部 | ❌ |
| `CL_SMS_PASSWORD` | - | 创蓝短信密码 | 全部 | ❌ |
| `CL_SMS_SIGNATURE` | - | 短信签名 | 全部 | ❌ |
| `CL_SMS_TEMPLATE_ID` | - | 短信模板 ID | 全部 | ❌ |
| `CL_SMS_SSL` | - | 是否使用 SSL | 全部 | ❌ |
| `CL_FLASH_*` | - | 创蓝闪信系列配置（11 个变量） | 全部 | ❌ |

**在 .env.example 中**: ❌ 全部缺失

---

### 2.15 其他系统配置

| 环境变量 | 默认值 | 说明 | Profile | 必填 |
|---------|--------|------|---------|------|
| `NACOS_SERVER_ADDR` | `127.0.0.1:8848` | Nacos 服务器地址 | 全部 | ❌ |
| `NACOS_NAMESPACE` | `public` | Nacos 命名空间 | 全部 | ❌ |
| `NACOS_CONFIG_ENABLED` | `true` | 启用 Nacos 配置中心 | 全部 | ❌ |
| `NACOS_DISCOVERY_ENABLED` | `true` | 启用 Nacos 服务发现 | 全部 | ❌ |
| `NACOS_REGISTER_ENABLED` | `true` | 注册到 Nacos | 全部 | ❌ |
| `FILE_MAX_SIZE_BYTES` | `10485760` | 单文件最大字节数（10MB，apex-file-service 文件域） | 文件服务 | ❌ |
| `OPERATION_LOG_ENABLED` | `true` | 启用操作日志 | 全部 | ❌ |
| `APEX_RESPONSE_TIMEOUT` | `30s` | 全局响应超时 | 全部 | ❌ |
| `APEX_SQL_SLOW_ENABLED` | `true` | 启用慢 SQL 指标采集 | 全部 | ❌ |
| `APEX_SQL_SLOW_THRESHOLD_MS` | `1000` | 慢 SQL 判定阈值（毫秒） | 全部 | ❌ |
| `APEX_USER_RATE_LIMIT_ENABLED` | `true` | 启用用户限流 | 全部 | ❌ |
| `REMOTE_API_CONNECT_TIMEOUT` | `5000` | 远程 API 连接超时（毫秒） | 全部 | ❌ |
| `REMOTE_API_READ_TIMEOUT` | `10000` | 远程 API 读取超时（毫秒） | 全部 | ❌ |
| `REMOTE_API_RETRY_COUNT` | `2` | 远程 API 重试次数 | 全部 | ❌ |
| `REMOTE_API_RETRY_DELAY` | `1000` | 远程 API 重试间隔（毫秒） | 全部 | ❌ |
| `REMOTE_API_POOL_MAX_CONNECTIONS` | `100` | 远程 API 连接池最大连接数 | 全部 | ❌ |
| `MAX_IN_MEMORY_SIZE` | `10MB` | 请求体/响应体内存缓冲上限 | 全部 | ❌ |
| `WEBCLIENT_CONNECT_TIMEOUT` | `5000` | WebClient 连接超时（毫秒） | 全部 | ❌ |
| `WEBCLIENT_READ_TIMEOUT` | `10000` | WebClient 读取超时（毫秒） | 全部 | ❌ |
| `WEBCLIENT_WRITE_TIMEOUT` | `10000` | WebClient 写入超时（毫秒） | 全部 | ❌ |
| `APEX_GENERATOR_OUTPUT_ROOT` | `/tmp/apex-gen` | 代码生成产物输出根目录，须挂载持久卷（system-service） | 全部 | ❌ |
| `ENV_NAME` | - | 环境名称标识 | 全部 | ❌ |
| `VAR` | - | 通用变量占位符 | 全部 | ❌ |

**在 .env.example 中**: ✅ 已覆盖——APEX_GENERATOR_OUTPUT_ROOT、ENV_NAME、NACOS_* 全部列入；STORAGE_*、FILE_MAX_SIZE_BYTES、FILE_JWT_SECRET 亦已覆盖（见存储与文件章节）

### 2.16 Nacos 配置中心 Data ID 清单（微服务化）

> 来源：迁移方案 §12.1 + 执行计划 Phase 1.4；模板见 [`config/nacos/`](../../config/nacos/README.md)。
> 约定：`{service}` = `spring.application.name`（服务名一律 `apex-xxx-service`）；
> 组 `DEFAULT_GROUP`（Sentinel 规则为 `SENTINEL_GROUP`）；密钥一律环境变量 / KMS，禁明文。

| Data ID | 类型 | 引用服务 | 说明 |
|---------|------|----------|------|
| `apex-common.yaml` | 共享配置 | 全部微服务 | Result/i18n 开关、日志级别基线、JWKs 地址、公共请求头约定 |
| `apex-datasource-{env}.yaml` | 共享配置 | 有库服务（dev/prod） | 各服务数据源连接（口令走 `DB_PASSWORD_{SERVICE}` 环境变量） |
| `apex-gateway.yaml` | 服务私有 | `apex-gateway` | 路由规则、灰度、JWKS 密钥清单、限流参数、CORS 白名单 |
| `apex-auth-service.yaml` | 服务私有 | `apex-auth-service` | Phase 2 引入（登录/JWT/MFA 等动态项） |
| `apex-system-service.yaml` | 服务私有 | `apex-system-service` | Phase 6 引入 |
| `apex-workflow-service.yaml` | 服务私有 | `apex-workflow-service` | Phase 3 引入 |
| `apex-office-service.yaml` | 服务私有 | `apex-office-service` | Phase 5 引入 |
| `apex-file-service.yaml` | 服务私有 | `apex-file-service` | Phase 4 引入 |
| `{service}-flow-rules` | Sentinel 规则 | 各微服务 | 限流规则（FlowRule JSON 数组），整体替换语义 |
| `{service}-degrade-rules` | Sentinel 规则 | 各微服务 | 熔断规则（DegradeRule JSON 数组），整体替换语义 |

**bootstrap.yml 命名说明**：`spring.application.name` 是 Nacos 注册名与配置
dataId 的权威来源，也是网关路由 `lb://{service}` 的解析目标。当前
`apex-auth-service` 的 `bootstrap.yml` 与 `application.yml` 均已采用 `apex-auth-service`，
注册名与网关 `lb://apex-auth-service` 路由目标一致。

---

## 三、按 Profile 分类汇总

### 3.1 basic Profile 特有配置

| 配置项 | 说明 |
|--------|------|
| 无 | basic Profile 不依赖外部服务，使用所有默认值 |

### 3.2 standard Profile 特有配置

| 配置项 | 说明 |
|--------|------|
| `DB_MASTER_*` | MySQL 主库连接 |
| `REDIS_*` | Redis 连接 |

### 3.3 enterprise Profile 特有配置

| 配置项 | 说明 |
|--------|------|
| `ROCKETMQ_*` | RocketMQ 连接 |
| `SENTINEL_*` | Sentinel 限流熔断 |
| `TRACING_ENABLED` | 链路追踪 |
| `ZIPKIN_BASE_URL` | Zipkin 端点 |

### 3.4 prod Profile 强制要求

| 配置项 | 说明 |
|--------|------|
| `JWT_SECRET` | 必须 ≥32 字节随机密钥 |
| `REDIS_PASSWORD` | Redis 密码不能为空 |
| `BAIDU_*_MODERATION_*` | 文件上传启用时必填 |

---

## 四、环境变量命名规范建议

### 4.1 当前命名风格分析

| 前缀 | 数量 | 说明 | 示例 |
|------|------|------|------|
| `APEX_*` | 32 | 应用自定义配置 | `APEX_GOVERNANCE_ENABLED` |
| `SPRING_*` | 0 | Spring 框架配置（未使用） | - |
| 组件名 | 140 | 直接用组件名开头 | `REDIS_HOST`, `MYSQL_*`, `NACOS_*` |

### 4.2 建议的统一规范

#### 规范 1：应用自定义配置
- **格式**: `APEX_{MODULE}_{CONFIG_NAME}`
- **示例**: `APEX_GOVERNANCE_ENABLED`, `APEX_MQ_CONSUMER_ENABLED`

#### 规范 2：Spring 框架配置
- **格式**: `SPRING_{CONFIG_PATH}`
- **示例**: `SPRING_PROFILES_ACTIVE`, `SPRING_APPLICATION_NAME`

#### 规范 3：第三方组件配置
- **格式**: `{COMPONENT}_{CONFIG_NAME}`
- **示例**: `REDIS_HOST`, `MYSQL_URL`, `NACOS_SERVER_ADDR`

#### 规范 4：敏感配置后缀
- **密码**: `*_PASSWORD`
- **密钥**: `*_SECRET`, `*_SECRET_KEY`
- **Token**: `*_TOKEN`
- **API Key**: `*_API_KEY`

---

## 五、.env.example 扩展建议

### 5.1 当前 .env.example 覆盖率

| 模块 | 覆盖率 | 说明 |
|------|--------|------|
| 构建与运行 | 100% | BUILD_PROFILE, TAG, SPRING_PROFILES_ACTIVE |
| 端口映射 | 100% | APP_PORT, MANAGEMENT_PORT |
| MySQL | 100% | MYSQL_DATABASE, MYSQL_ROOT_PASSWORD, MYSQL_PORT |
| Redis | 50% | REDIS_PORT, REDIS_PASSWORD（缺 HOST, DATABASE 等） |
| 数据库连接 | 100% | DB_MASTER_URL, DB_MASTER_USERNAME, DB_MASTER_PASSWORD |
| JWT | 100% | JWT_SECRET |
| Nacos | 100% | NACOS_CONFIG_ENABLED, NACOS_DISCOVERY_ENABLED 等 |
| RocketMQ | 100% | ROCKETMQ_NAMESERVER 等 |
| Keycloak | 100% | KEYCLOAK_PORT, KEYCLOAK_ADMIN_USERNAME 等 |
| License | 100% | APEX_LICENSE_FILE |
| 其他配置 | 未整体复核 | 存储变量已补齐；其他类别仍需逐项与模板核对 |

**总体覆盖率**: ~23%

### 5.2 建议新增的环境变量分组

建议在 `.env.example` 中新增以下分组：

```bash
# ---------- Sentinel 限流熔断 ----------
SENTINEL_DASHBOARD_URL=
SENTINEL_TRANSPORT_PORT=8719
SENTINEL_CLIENT_IP=
SENTINEL_EAGER=true
SENTINEL_NACOS_ENABLED=false
SENTINEL_NACOS_GROUP=SENTINEL_GROUP
SENTINEL_NACOS_FLOW_DATA_ID=apex-sentinel-flow-rules
SENTINEL_NACOS_DEGRADE_DATA_ID=apex-sentinel-degrade-rules

# ---------- Meilisearch 全文搜索 ----------
MEILISEARCH_ENABLED=false
MEILISEARCH_URL=http://localhost:7700
MEILISEARCH_API_KEY=
MEILISEARCH_INDEX_PREFIX=apex_

# ---------- Loki 日志查询 ----------
LOKI_ENABLED=false
LOKI_URL=http://localhost:3100

# ---------- Grafana 监控 ----------
GRAFANA_ENABLED=false
GRAFANA_URL=http://localhost:3200

# ---------- 存储（apex-file-service 文件域） ----------
STORAGE_DEFAULT_TYPE=local
STORAGE_BUCKET_NAME=apex
STORAGE_PRESIGNED_URL_EXPIRE_SECONDS=300
STORAGE_LOCAL_ENABLED=true
STORAGE_LOCAL_BASE_PATH=./data/storage
# Local 不提供公开 URL 配置；通过业务授权内容接口下载。

# MinIO
MINIO_ENDPOINT=
MINIO_ACCESS_KEY=
MINIO_SECRET_KEY=
STORAGE_MINIO_ENABLED=false
# HTTPS 由 MINIO_ENDPOINT 的 scheme 控制；其他厂商变量见 .env.example。

# 火山引擎 TOS
STORAGE_VOLCENGINE_ENABLED=false
STORAGE_VOLCENGINE_ENDPOINT=
STORAGE_VOLCENGINE_REGION=
STORAGE_VOLCENGINE_ACCESS_KEY=
STORAGE_VOLCENGINE_SECRET_KEY=

# ---------- 内容审核（独立内容安全能力） ----------
BAIDU_TEXT_MODERATION_API_KEY=
BAIDU_TEXT_MODERATION_SECRET_KEY=
BAIDU_IMAGE_MODERATION_API_KEY=
BAIDU_IMAGE_MODERATION_SECRET_KEY=

# ---------- 告警通知 ----------
DINGDING_WEBHOOK=
DINGDING_SECRET=

# ---------- 邮件发送 ----------
MAIL_HOST=
MAIL_PORT=465
MAIL_USERNAME=
MAIL_PASSWORD=

# ---------- 安全配置 ----------
APEX_CAPTCHA_ENABLED=true
APEX_CAPTCHA_LENGTH=4
APEX_CAPTCHA_TTL_SECONDS=120
APEX_USER_RATE_LIMIT_ENABLED=true
APEX_ANTI_REPLAY_NONCE_TTL=360

# ---------- 监控追踪 ----------
MANAGEMENT_SERVER_ADDRESS=127.0.0.1
TRACING_ENABLED=true
TRACE_SAMPLING_RATE=0.1
ZIPKIN_BASE_URL=http://localhost:9411

# ---------- 其他系统配置 ----------
FILE_MAX_SIZE_BYTES=10485760
OPERATION_LOG_ENABLED=true
APEX_RESPONSE_TIMEOUT=30s
APEX_SQL_SLOW_ENABLED=true
APEX_SQL_SLOW_THRESHOLD_MS=1000
```

---

## 六、配置管理最佳实践

### 6.1 环境变量优先级

1. **操作系统环境变量**（最高优先级）
2. **`.env` 文件**（Podman Compose）
3. **`application-{profile}.yml`**
4. **`application.yml`**（默认值，最低优先级）

### 6.2 敏感配置处理

#### 开发环境
```bash
# 使用默认值或明文配置（仅限本地）
JWT_SECRET=dev-secret-key-only-for-local
REDIS_PASSWORD=
```

#### 生产环境
```bash
# 通过 Kubernetes Secret / Podman Secret 注入
JWT_SECRET=${K8S_SECRET_JWT}
REDIS_PASSWORD=${K8S_SECRET_REDIS_PASSWORD}
```

### 6.3 配置验证

建议在应用启动时验证关键配置：

- ✅ `JWT_SECRET` 长度 ≥ 32 字节（prod 环境）
- ✅ `REDIS_PASSWORD` 非空（prod 环境）
- ✅ `DB_MASTER_URL` 格式正确

---

## 七、后续行动

### 立即行动

1. ✅ **扩展 .env.example**
   - 补充所有 132 个缺失的环境变量
   - 按功能模块分组
   - 添加详细注释说明

2. ✅ **创建环境变量参考文档**
   - `docs/ENV_VARS_REFERENCE.md`
   - 包含所有变量的完整说明
   - 标注必填/可选、默认值、Profile 范围

3. ✅ **创建配置管理文档**
   - `docs/_archive/operations/environment-variables.md`
   - 环境变量设置方式
   - 优先级规则
   - 最佳实践
   - 故障排查

### 后续优化

4. ⏳ **统一环境变量命名**
   - 制定命名规范文档
   - 逐步重构不规范的变量名
   - 保持向后兼容（支持旧名称）

5. ⏳ **配置验证增强**
   - 增加启动时配置校验
   - 提供清晰的错误提示
   - 生成配置报告

---

**文档生成时间**: 2026-09-09  
**分析工具**: grep + 人工分析  
**环境变量总数**: 172 个（去重后）
