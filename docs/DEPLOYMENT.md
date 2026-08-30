# Apex Scaffold 部署使用指南

> 版本：1.1.3 ｜ 适用：dev / it / prod 全环境
> 本文档覆盖三种部署方式：**java -jar 直接部署**、**Docker 单容器**、**Docker Compose 编排**，并给出环境变量、健康检查、日志监控、License、升级回滚、安全清单等完整说明。

---

## 目录

- [1. 部署方式总览](#1-部署方式总览)
- [2. 前置条件](#2-前置条件)
- [3. 构建与产物](#3-构建与产物)
- [4. 部署方式一：java -jar 直接部署](#4-部署方式一java--jar-直接部署)
  - [4.1 最小启动](#41-最小启动)
  - [4.2 守护脚本 app.sh](#42-守护脚本-appsh)
  - [4.3 systemd 服务化（推荐生产）](#43-systemd-服务化推荐生产)
  - [4.4 多实例部署](#44-多实例部署)
- [5. 部署方式二：Docker 单容器](#5-部署方式二docker-单容器)
- [6. 部署方式三：Docker Compose 编排](#6-部署方式三docker-compose-编排)
  - [6.1 一键启动默认栈](#61-一键启动默认栈)
  - [6.2 生产部署流程](#62-生产部署流程)
  - [6.3 启用 RocketMQ（可选）](#63-启用-rocketmq可选)
  - [6.4 集成监控栈](#64-集成监控栈)
- [7. 环境变量完整参考表](#7-环境变量完整参考表)
- [8. 健康检查与就绪探针](#8-健康检查与就绪探针)
- [9. 日志与监控](#9-日志与监控)
- [10. License 商业授权部署](#10-license-商业授权部署)
- [11. 可选组件接入（Nacos / RocketMQ / Sentinel）](#11-可选组件接入nacos--rocketmq--sentinel)
- [12. 升级与回滚](#12-升级与回滚)
- [13. 备份与恢复](#13-备份与恢复)
- [14. 生产安全部署清单](#14-生产安全部署清单)
- [15. 常见问题 FAQ](#15-常见问题-faq)
- [16. 命令速查表](#16-命令速查表)

---

## 1. 部署方式总览

| 方式 | 适用场景 | 特点 | 依赖 |
|------|---------|------|------|
| **java -jar** | 传统服务器 / 已有 MySQL+Redis 的机房、云主机 | 最轻量、无 Docker 依赖、便于和 systemd/监控代理结合 | JDK 17 + MySQL + Redis |
| **Docker 单容器** | 已容器化的 K8s 以外的裸容器环境 | 镜像开箱即用，多实例互不干扰 | Docker + 外部 MySQL/Redis |
| **Docker Compose** | 一键拉起完整环境（开发/测试/生产单机） | 应用 + MySQL + Redis（+可选 RocketMQ）全部编排 | Docker + Compose v2 |

三者共用**同一套配置体系与环境变量**（见 [§7](#7-环境变量完整参考表)），切换部署方式无需修改代码或配置。

典型拓扑：

```
┌─────────────────────────────────────────────────────────┐
│  部署环境：dev / it / prod                                │
│                                                         │
│  java -jar / Docker / Docker Compose ──► 应用 :8080      │
│                                           管理 :8081     │
│      │                                                   │
│      ├──► MySQL（apex_data / apex_test）  ← Flyway 自动迁移│
│      ├──► Redis                                        │
│      ├──► [可选] RocketMQ Namesrv+Broker                │
│      ├──► [可选] Nacos 配置中心/服务发现                  │
│      └──► [可选] Prometheus + Grafana（config/monitoring）│
└─────────────────────────────────────────────────────────┘
```

> **关于 Nacos**：应用内置 Nacos 配置中心/服务发现能力，但**默认部署不强依赖**。无 Nacos 时设置 `NACOS_CONFIG_ENABLED=false`、`NACOS_DISCOVERY_ENABLED=false`、`NACOS_REGISTER_ENABLED=false` 即可完全关闭（见 [§11](#11-可选组件接入nacos--rocketmq--sentinel)）。

---

## 2. 前置条件

| 组件 | 版本要求 | 说明 |
|------|---------|------|
| JDK | 17+ | java -jar 部署必需 |
| MySQL | 8.0+ | 库由应用 Flyway 自动迁移建表，只需保证账号可建表/写数据 |
| Redis | 6.x / 7.x | 缓存 / Token / 分布式锁 |
| Docker | 20.10+ | Docker / Compose 部署必需 |
| Docker Compose | v2 | 随 Docker Desktop 自带；Linux 独立安装 |

基础环境自检：

```bash
java -version                 # openjdk 17+
mysql --version               # 8.0+
redis-cli --version
docker --version              # 20.10+
docker compose version
```

---

## 3. 构建与产物

### 3.1 档位说明（构建 profile）

项目提供三个**档位**（决定商业功能范围），与**环境 profile**（dev/it/prod）相互独立、叠加激活：

| 档位 | 命令 | 功能范围 |
|------|------|---------|
| basic | `-Pbasic` | JWT 认证 / 统一响应 / 参数校验 / WebFlux / MyBatis-Plus（MIT 开源） |
| standard | `-Pstandard` | 基础版 + RBAC / 多级缓存 / 监控告警 / 对象存储 / 短信邮件 |
| enterprise | `-Penterprise` | 标准版 + RocketMQ / 链路追踪 / Sentinel / 分布式锁 / 多数据源 |

### 3.2 构建命令

```bash
# 本地统一构建环境（sdkman + mvnd）
source ~/.sdkman/bin/sdkman-init.sh
./scripts/build/build.sh -Penterprise        # 或 -Pbasic / -Pstandard

# 或直接使用 Maven Daemon（CI / Docker）
mvnd clean package -DskipTests -Penterprise
```

### 3.3 构建产物

```
apex-interfaces/target/
├── apex-interfaces-1.1.3-executable.jar   # ✅ 可执行 fat jar（部署用）
├── apex-interfaces-1.1.3.jar              # 普通 jar（供 example 等下游依赖）
└── classes/apex-build.properties          # 构建标识
```

> **务必使用 `-executable.jar`**：普通 jar 不含 Spring Boot 启动器，`java -jar` 无法运行。
> 验证产物档位：`cat apex-interfaces/target/classes/apex-build.properties`
> 输出 `build.profile.id=enterprise` 与构建时选择的档位一致。

### 3.4 验证产物可启动（冒烟）

```bash
# 使用本地基础设施（docker compose -f docker-compose.infra.yml up -d mysql redis）
java -jar apex-interfaces/target/apex-interfaces-1.1.3-executable.jar \
  --spring.profiles.active=dev,standard
# 看到 "Started ApexApplication" 且 http://localhost:8080/api/v1/health 返回 UP 即通过
```

---

## 4. 部署方式一：java -jar 直接部署

> 适合已有 MySQL/Redis 的服务器，无需 Docker。推荐生产使用 systemd 托管（§4.3）。

### 4.1 最小启动

```bash
# 1. 上传可执行 jar 到服务器，例如 /opt/apex-scaffold/
scp apex-interfaces/target/apex-interfaces-1.1.3-executable.jar \
    user@server:/opt/apex-scaffold/app.jar

# 2. 注入环境变量并启动（前台，Ctrl+C 停止，调试用）
export SPRING_PROFILES_ACTIVE=prod,enterprise
export DB_MASTER_URL='jdbc:mysql://127.0.0.1:3306/apex_data?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false'
export DB_MASTER_USERNAME=apex
export DB_MASTER_PASSWORD='********'
export DB_SLAVE_URL='jdbc:mysql://127.0.0.1:3306/apex_data?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false'   # 从库（prod 已启用 slave，必须注入；可指向真实只读副本）
export DB_SLAVE_USERNAME=apex
export DB_SLAVE_PASSWORD='********'
export REDIS_HOST=127.0.0.1
export REDIS_PORT=6379
export REDIS_PASSWORD='********'   # prod 配置强制要求（spring.data.redis.password=${REDIS_PASSWORD}）
export JWT_SECRET='$(openssl rand -base64 48)'
export NACOS_CONFIG_ENABLED=false
export NACOS_DISCOVERY_ENABLED=false
export NACOS_REGISTER_ENABLED=false

# JVM 参数应按部署环境的内存上限调整；Kubernetes 1Gi limit 使用 768Mi heap，并预留堆外内存
export JAVA_OPTS='-Xms512m -Xmx768m -Xss512k -XX:MaxDirectMemorySize=128m -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:MetaspaceSize=256m -XX:MaxMetaspaceSize=512m -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/opt/apex-scaffold/logs/heapdump.hprof -XX:+ExitOnOutOfMemoryError -Xlog:gc*:file=/opt/apex-scaffold/logs/gc.log:time,uptime,level,tags:filecount=5,filesize=20m -Dfile.encoding=UTF-8 -Djava.security.egd=file:/dev/./urandom'
mkdir -p /opt/apex-scaffold/logs
java ${JAVA_OPTS} \
     -jar /opt/apex-scaffold/app.jar
```

启动成功标志：

```
Started ApexApplication in 12.345 seconds
Tomcat/Netty started on port 8080 (http)
Management port 8081
```

### 4.2 守护脚本 app.sh

仓库内置守护脚本，支持后台启动 / 停止 / 重启 / 状态 / 日志：

```bash
# 语法: ./scripts/deploy/app.sh {start|stop|restart|status|logs|console} [dev|it|prod] [basic|standard|enterprise]

./scripts/deploy/app.sh start prod enterprise     # 后台启动（dev/it 同理）
./scripts/deploy/app.sh status                    # 查看状态 + 健康检查
./scripts/deploy/app.sh logs                      # 跟随标准输出日志
./scripts/deploy/app.sh restart it standard       # 重启
./scripts/deploy/app.sh stop                      # 优雅停机（SIGTERM）
./scripts/deploy/app.sh console dev basic         # 前台调试运行
```

行为说明：

| 项 | 默认值 | 覆盖方式 |
|----|--------|---------|
| JVM 参数 | `-Xms512m -Xmx1024m -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:MetaspaceSize=256m -XX:MaxMetaspaceSize=512m -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=<logs>/heapdump.hprof -XX:+ExitOnOutOfMemoryError -Xlog:gc*:file=<logs>/gc.log:...（滚动 5×20m） -Dfile.encoding=UTF-8`（见 §4.5） | `JAVA_OPTS` |
| GC 日志 / 堆转储 | `<日志根>/gc.log`、`<日志根>/heapdump.hprof`（与 logback 业务日志同目录，`APEX_LOG_DIR` 可整体迁移） | `APEX_LOG_DIR` |
| PID 文件 | `<项目根>/logs/app.pid` | `APP_PID_FILE` |
| 标准输出日志 | `<项目根>/logs/app.out.log` | `APP_OUT_LOG` |
| 环境变量文件 | 无 | `APEX_ENV_FILE=/etc/apex-scaffold/apex.env`（每行 `KEY=VALUE`） |
| 健康检查端口 | 8080 | `SERVER_PORT` |

启动时脚本会轮询业务端点 `GET /api/v1/health` 最长 90 秒，用于确认应用 HTTP 服务已启动；该端点不等同于包含 DB、Redis 状态的 Actuator readiness。若超时请查看 `logs/app.out.log`。

停止时脚本发送 SIGTERM 并最多等待 60 秒，超时后执行强制终止。Spring Boot 的 `server.shutdown=graceful` 负责停止接收新请求并等待在途 HTTP 请求；`GracefulShutdownManager` 仅固定等待 15 秒，为 RocketMQ 已拉取消息的处理提供宽限期，不检测或确认消息已排空，并记录 `@Async` 线程池状态。线程池和连接池由各自 Bean 生命周期关闭。

### 4.3 systemd 服务化（推荐生产）

仓库已提供模板 `scripts/deploy/apex-scaffold.service`：

```bash
# 1. 放置产物与目录
sudo mkdir -p /opt/apex-scaffold /etc/apex-scaffold /var/log/apex-scaffold
sudo cp apex-interfaces/target/apex-interfaces-1.1.3-executable.jar /opt/apex-scaffold/app.jar
sudo useradd -r -s /usr/sbin/nologin apex || true

# 2. 创建环境变量文件（敏感值生产必改）
sudo tee /etc/apex-scaffold/apex.env > /dev/null <<'EOF'
SPRING_PROFILES_ACTIVE=prod,enterprise
DB_MASTER_URL=jdbc:mysql://127.0.0.1:3306/apex_data?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false
DB_MASTER_USERNAME=apex
DB_MASTER_PASSWORD=********
DB_SLAVE_URL=jdbc:mysql://127.0.0.1:3306/apex_data?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false
DB_SLAVE_USERNAME=apex
DB_SLAVE_PASSWORD=********
REDIS_HOST=127.0.0.1
REDIS_PORT=6379
JWT_SECRET=********
NACOS_CONFIG_ENABLED=false
NACOS_DISCOVERY_ENABLED=false
NACOS_REGISTER_ENABLED=false
APEX_LOG_DIR=/var/log/apex-scaffold
EOF

# 3. 安装并启用
sudo cp scripts/deploy/apex-scaffold.service /etc/systemd/system/
sudo chown -R apex:apex /opt/apex-scaffold /var/log/apex-scaffold
sudo systemctl daemon-reload
sudo systemctl enable --now apex-scaffold

# 4. 常用操作
systemctl status apex-scaffold        # 状态
journalctl -u apex-scaffold -f        # 日志
sudo systemctl restart apex-scaffold  # 重启（优雅停机，等待在途请求）
```

> 模板已配置：`Restart=on-failure` 崩溃自愈、`TimeoutStopSec=90` 优雅停机等待、
> `SuccessExitStatus=143` 兼容 SIGTERM 退出码。

### 4.4 多实例部署

同一台机器跑多实例时，通过端口与内存隔离：

```bash
# 实例 A（JVM 参数见 §4.5；多实例必须用各自独立的 GC 日志路径）
export JAVA_OPTS='-Xms512m -Xmx1024m -XX:+UseG1GC -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/opt/apex-scaffold/logs/heapdump-a.hprof -Xlog:gc*:file=/opt/apex-scaffold/logs/gc-a.log:time,uptime,level,tags:filecount=5,filesize=20m -Dfile.encoding=UTF-8'
SERVER_PORT=8080 MANAGEMENT_SERVER_ADDRESS=0.0.0.0 MANAGEMENT_SERVER_PORT=8081 \
  java ${JAVA_OPTS} -jar app.jar --spring.profiles.active=prod,enterprise

# 实例 B
export JAVA_OPTS='-Xms512m -Xmx1024m -XX:+UseG1GC -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/opt/apex-scaffold/logs/heapdump-b.hprof -Xlog:gc*:file=/opt/apex-scaffold/logs/gc-b.log:time,uptime,level,tags:filecount=5,filesize=20m -Dfile.encoding=UTF-8'
SERVER_PORT=8082 MANAGEMENT_SERVER_PORT=8083 \
  java ${JAVA_OPTS} -jar app.jar --spring.profiles.active=prod,enterprise \
  --spring.application.name=apex-scaffold-2
```

要点：

- 业务端口：`SERVER_PORT`；管理端口：`MANAGEMENT_SERVER_PORT`
- 多实例共用 MySQL/Redis 时业务数据天然一致；Redis 键带应用名前缀，互不干扰
- 多实例须通过 `JAVA_OPTS` 指定各自独立的 GC 日志 / 堆转储路径（否则写同一 `gc.log` 互相覆盖）
- 如需对外负载均衡，前端加 Nginx / SLB，健康检查指向 `GET /api/v1/health`

### 4.5 JVM 参数调优（全部部署方式通用）

三种部署方式（java -jar / Docker / Compose / k8s）内置同一套生产级 JVM 调优参数，
默认值如下，均可通过环境变量 `JAVA_OPTS` 整体覆盖：

| 参数 | 作用 | 默认值 |
|------|------|--------|
| `-Xms512m` | 堆内存初始值（最小堆） | 512m |
| `-Xmx1024m` | 堆内存最大值（最大堆） | 1024m |
| `-XX:+UseG1GC` | G1 垃圾回收器（JDK17 默认，显式声明） | 开启 |
| `-XX:MaxGCPauseMillis=200` | G1 目标最大 GC 停顿时间 | 200ms |
| `-XX:MetaspaceSize=256m` | 元空间初始阈值（避免频繁扩容触发 Full GC） | 256m |
| `-XX:MaxMetaspaceSize=512m` | 元空间上限 | 512m |
| `-XX:+HeapDumpOnOutOfMemoryError` | OOM 时自动堆转储（定位内存泄漏必备） | 开启 |
| `-XX:HeapDumpPath=<logs>/heapdump.hprof` | 堆转储文件路径 | 日志根目录 |
| `-XX:+ExitOnOutOfMemoryError` | OOM 后立即退出进程，便于 systemd/容器/k8s 自动重启自愈 | 开启 |
| `-Xlog:gc*:file=<logs>/gc.log:time,uptime,level,tags:filecount=5,filesize=20m` | 滚动 GC 日志（5 份 × 20m，JDK9+ 统一日志） | 开启 |
| `-Dfile.encoding=UTF-8` | 字符编码 | UTF-8 |
| `-Djava.security.egd=file:/dev/./urandom` | 非阻塞熵源，加速启动（Linux） | 开启 |

各载体的日志落盘位置：

| 部署方式 | GC 日志 / 堆转储位置 |
|----------|----------------------|
| `scripts/deploy/app.sh` | `<项目根>/logs/gc.log`、`heapdump.hprof`（`APEX_LOG_DIR` 可迁移） |
| systemd（`apex-scaffold.service`） | `/opt/apex-scaffold/logs/gc.log`、`heapdump.hprof` |
| Docker / Compose | `/app/logs/gc.log`、`heapdump.hprof`（镜像内已预创建；生产可挂 volume 持久化） |
| k8s | 容器内 `/app/logs/`（建议配置 emptyDir/挂载采集，或由日志代理收集 stdout） |

> 调整建议：内存充足（≥4G）的机器可将 `-Xms/-Xmx` 调至 2g~4g（两值相等可避免堆扩容抖动）；
> 需要时可用 `-XX:+PrintSafepointStatistics` 等扩展诊断，默认参数已是"开箱即用"的稳妥基线。
>
> 依赖组件（Nacos/RocketMQ/Sentinel/Zipkin）的 JVM 调优见 [§6.5](#65-本地基础设施一键部署全部中间件)，
> 参数思路一致，但组件镜像多为 JDK8（GC 日志用 `-Xloggc` 语法）。

---

## 5. 部署方式二：Docker 单容器

### 5.1 构建镜像

```bash
# 默认档位 enterprise；可 --build-arg BUILD_PROFILE=basic|standard|enterprise
docker build --build-arg BUILD_PROFILE=enterprise -t apex-scaffold:1.1.3 .
```

构建说明（Dockerfile）：

- 多阶段构建：builder 阶段预下载依赖（阿里云 Maven 镜像加速），runtime 仅含 JRE + 可执行 jar
- 非 root 用户 `appuser` 运行；时区默认 `Asia/Shanghai`（含 tzdata）
- 默认 JVM：生产级调优集（`-Xms512m -Xmx1024m -XX:+UseG1GC -XX:MaxGCPauseMillis=200` + OOM 堆转储/退出 + 滚动 GC 日志，详见 §4.5），可用 `JAVA_OPTS` 覆盖
- GC 日志与堆转储写入容器 `/app/logs/`（`gc.log` / `heapdump.hprof`，镜像已预创建）；生产建议挂 volume 持久化或由日志代理采集
- 暴露 `8080`（业务）、`8081`（管理/Actuator/Prometheus）

### 5.2 运行容器

```bash
docker run -d --name apex-scaffold \
  -p 8080:8080 -p 8081:8081 \
  -e SPRING_PROFILES_ACTIVE=prod,enterprise \
  -e DB_MASTER_URL='jdbc:mysql://<mysql-host>:3306/apex_data?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false' \
  -e DB_MASTER_USERNAME=apex \
  -e DB_MASTER_PASSWORD='********' \
  -e DB_SLAVE_URL='jdbc:mysql://<mysql-host>:3306/apex_data?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false' \
  -e DB_SLAVE_USERNAME=apex \
  -e DB_SLAVE_PASSWORD='********' \
  -e REDIS_HOST=<redis-host> \
  -e REDIS_PORT=6379 \
  -e REDIS_PASSWORD='********' \
  -e JWT_SECRET='********' \
  -e NACOS_CONFIG_ENABLED=false \
  -e NACOS_DISCOVERY_ENABLED=false \
  -e NACOS_REGISTER_ENABLED=false \
  -e APEX_LOG_DIR=/app/logs \
  -v apex-logs:/app/logs \
  apex-scaffold:1.1.3
```

### 5.3 常用操作

```bash
docker ps                                 # 查看容器（STATUS 应为 Up (healthy)）
docker logs -f apex-scaffold              # 日志
docker exec -it apex-scaffold sh          # 进入容器排查
curl http://localhost:8080/api/v1/health  # 健康检查
curl http://localhost:8081/actuator/health

# 停止 / 删除
docker stop apex-scaffold && docker rm apex-scaffold

# 升级：构建新镜像 → 重建容器
docker build -t apex-scaffold:1.2.0 .
docker stop apex-scaffold && docker rm apex-scaffold
docker run -d --name apex-scaffold ...（同上参数，镜像换 1.2.0）
```

> 容器内管理端口默认监听 `0.0.0.0`（镜像已设 `MANAGEMENT_SERVER_ADDRESS=0.0.0.0`），
> 便于 Prometheus 从宿主机抓取 `http://<host>:8081/actuator/prometheus`。

---

## 6. 部署方式三：Docker Compose 编排

> 一键拉起「应用 + MySQL + Redis（+可选 RocketMQ）」，适合本地开发、测试环境与生产单机。

### 6.1 一键启动默认栈

```bash
# 1.（可选）按需生成环境变量文件
cp .env.example .env

# 2. 构建并启动
docker compose up -d --build

# 3. 等待就绪（首次 MySQL 初始化约 30~60s）
docker compose ps                 # mysql/redis 应为 healthy，app 为 Up
docker compose logs -f apex-scaffold

# 4. 验证
curl http://localhost:8080/api/v1/health
# 浏览器打开 http://localhost:8080/doc.html（API 文档）
```

默认服务清单：

| 服务 | 镜像 | 端口（宿主机） | 说明 |
|------|------|---------------|------|
| apex-scaffold | 本地构建 | 8080 / 8081 | 应用（默认 `prod,enterprise`） |
| mysql | mysql:8.0 | 3306 | utf8mb4，数据卷 `mysql-data` |
| redis | redis:7-alpine | 6379 | AOF 持久化，数据卷 `redis-data` |

默认凭证（**仅限本地/测试，生产务必修改**）：

- MySQL：`root / apex-root`，库 `apex_data`（`MYSQL_ROOT_PASSWORD` 可覆盖）
- Redis：密码 `apex-redis-password`（`REDIS_PASSWORD` 可覆盖；prod 配置强制要求非空）
- JWT：`change-me-in-production-32chars-minimum`（`JWT_SECRET` 可覆盖；**prod 强制 ≥32 字节**，不足则启动失败）

### 6.2 生产部署流程

```bash
# 1. 生成生产环境变量文件并逐项修改（密码 / 密钥 / 地址）
cp .env.example .env.prod
vim .env.prod     # 至少修改: MYSQL_ROOT_PASSWORD / DB_MASTER_PASSWORD / DB_SLAVE_PASSWORD / JWT_SECRET
                  # 建议: APP_PORT / MANAGEMENT_PORT / MYSQL_PORT 按实际调整

# 2. 使用生产环境文件启动
docker compose --env-file .env.prod up -d --build

# 3. 验证
docker compose --env-file .env.prod ps
curl http://localhost:8080/api/v1/health
curl http://localhost:8081/actuator/health
```

> `.env.prod` 请加入 `.gitignore`，避免密钥入库。

### 6.3 启用 RocketMQ（可选）

企业版需要消息能力时，携带 profile 启动 RocketMQ（Namesrv + Broker），并在 `.env` 或命令行环境中注入 NameServer 与业务开关：

```bash
# .env 设置 name-server 与消费者开关
#   ROCKETMQ_NAMESERVER=rocketmq-namesrv:9876
#   APEX_MQ_CONSUMER_ENABLED=true
#   APEX_MQ_DLQ_ENABLED=true
#   APEX_MQ_DOMAIN_EVENT_ENABLED=true
# 3) 携带 profile 启动
docker compose --profile rocketmq --env-file .env up -d --build
```

> 公共配置始终提供 `rocketmq.name-server` 默认值。未部署 RocketMQ 时请保持
> `APEX_MQ_CONSUMER_ENABLED=false` 和 `APEX_MQ_DOMAIN_EVENT_ENABLED=false`；生产环境启用消息能力时，必须显式注入可达的 `ROCKETMQ_NAMESERVER` 或 `ROCKETMQ_NAMESRV_ADDR`。

RocketMQ 控制台访问（可选）：

```bash
# 临时起一个控制台容器查看消息状态
docker run -d --name rmq-console --network apex-scaffold_apex-network \
  -e JAVA_OPTS='-Drocketmq.namesrv.addr=rocketmq-namesrv:9876 -Dcom.rocketmq.sendMessageWithVIPChannel=false' \
  -p 8180:8080 apacherocketmq/rocketmq-dashboard:latest
```

> 应用侧无需再改配置——compose 已把 `ROCKETMQ_NAMESERVER` 注入应用容器；
> 生产大内存场景请调整 `rocketmq-broker` 的 `JAVA_OPT_EXT`（默认已调小到 1g）。

### 6.4 集成监控栈

可观测性栈（Prometheus + Grafana + Zipkin）独立编排，与应用栈并存：

```bash
docker compose -f config/monitoring/docker-compose.yml up -d

# 访问
# Grafana    http://localhost:3000   （admin / admin）
# Prometheus http://localhost:9090
# Zipkin     http://localhost:9411
```

抓取链路：Prometheus `scrape` 应用容器 `http://apex-scaffold:8081/actuator/prometheus`
（compose 网络内主机名即服务名 `apex-scaffold`）。

### 6.5 本地基础设施一键部署（全部中间件）

项目用到的所有中间件统一由 `docker-compose.infra.yml` 编排，本地一条命令拉起：

```bash
# 一键启动全部组件
docker compose -f docker-compose.infra.yml up -d

# 状态 / 日志 / 停止
docker compose -f docker-compose.infra.yml ps
docker compose -f docker-compose.infra.yml logs -f <service>
docker compose -f docker-compose.infra.yml down          # 保留数据卷
docker compose -f docker-compose.infra.yml down -v       # ⚠️ 连同数据一起删除
```

组件清单与访问入口：

| 组件 | 镜像 | 宿主机端口 | 账号 | 用途 |
|------|------|-----------|------|------|
| MySQL | mysql:8.0 | 3306 | root / apex-root | 主数据源（库 apex_data / apex_test） |
| Redis | redis:7-alpine | 6379 | 无密码 | 缓存 / Token / 分布式锁 |
| Nacos | nacos/nacos-server:v2.3.2 | 18848（控制台）/ 19848（gRPC） | nacos / nacos | 配置中心 + 服务发现 |
| RocketMQ | apache/rocketmq:5.3.1 | 9876（Namesrv）/ 10911（Broker） | — | 消息队列（企业版） |
| Sentinel Dashboard | zhengqing/sentinel-dashboard:1.8.2 | 8858 | sentinel / sentinel | 限流熔断控制台 |
| Zipkin | openzipkin/zipkin:3.4 | 9411 | — | 链路追踪 |
| Prometheus | prom/prometheus:v2.53.0 | 9090 | — | 指标采集 |
| Grafana | grafana/grafana:11.1.0 | 3000 | admin / admin | 可视化大盘 |
| Blackbox Exporter | prom/blackbox-exporter:v0.25.0 | 9115 | — | 健康探针 |

> **依赖组件的 JVM 调优**：Nacos / RocketMQ（Namesrv+Broker）/ Sentinel Dashboard / Zipkin 已在
> `docker-compose.infra.yml` 内置与 Apex 应用同思路的生产级 JVM 参数（G1 回收器 / GC 停顿目标 /
> 元空间 256m/512m / OOM 堆转储 + 退出自愈 / 滚动 GC 日志），详见文件内各服务注释。
> 注意：这些组件镜像大多基于 **JDK8**（Nacos / RocketMQ / Sentinel），GC 日志使用 `-Xloggc` +
> `PrintGCDetails` 语法（`-Xlog` 仅 JDK9+ 可用，Zipkin 镜像为 JDK21 使用 `-Xlog`）；
> Nacos 与 RocketMQ Namesrv 的启动脚本强制 CMS 回收器（再传 G1 会导致 JVM 启动崩溃
> "Conflicting collector combinations"），故不传 G1/停顿目标，仅加元空间/OOM/GC 日志。
> 日志落盘位置：Nacos → `/home/nacos/logs`（已挂卷持久化）、
> RocketMQ → `/home/rocketmq/logs`（`command` 已预建目录）、Sentinel/Zipkin → `/tmp`（本地开发用）。
> 调整方式：直接改 `docker-compose.infra.yml` 中对应服务的 `JAVA_OPT`/`JAVA_OPT_EXT`/`JAVA_OPTS`。
> 监控栈 `config/monitoring/docker-compose.yml` 中的 Zipkin 同样已配置。

与本地开发应用的连接约定（**dev 配置默认值即匹配，无需改任何配置**）：

- 本地 `java -jar`（dev 档）默认连 `127.0.0.1:3306/apex_data`、`127.0.0.1:6379` → 直接可用
- Nacos：dev 默认禁用；启用时设 `NACOS_CONFIG_ENABLED=true` 且 `NACOS_SERVER_ADDR=127.0.0.1:18848`
- RocketMQ：启用时设 `ROCKETMQ_NAMESERVER=127.0.0.1:9876`（详见 [§6.3](#63-启用-rocketmq可选)）
- Sentinel：`SENTINEL_DASHBOARD_URL=http://127.0.0.1:8858`（dev 默认禁用）
- Zipkin：`ZIPKIN_BASE_URL=http://127.0.0.1:9411`
- Prometheus 抓取宿主机应用：`host.docker.internal:8081`（已内置配置）

> ⚠️ 端口说明：若本机 8848 已被其他 Nacos 占用，本编排将 Nacos 映射为 **18848**；
> 若 8848 空闲，可自行改回 `8848:8848` + `9848:9848`。
> 数据持久化：MySQL / Redis / Nacos / Prometheus / Grafana 使用命名卷，`down` 不丢数据；
> RocketMQ 消息数据**不持久化**（镜像内 store 目录归属问题，本地测试消息可重建，生产需另行规划）。
> 首次启动：MySQL 自动建 `apex_data` 库，应用 Flyway 迁移建表；本地已有开发库可在
> `docker compose -f docker-compose.infra.yml up -d` 后导入备份：
> `docker exec -i apex-infra-mysql mysql -uroot -papex-root < backup.sql`

**两个编排栈的关系与差异**（避免混淆）：

| | `docker-compose.infra.yml`（本地基础设施） | `docker-compose.yml`（应用栈） |
|---|---|---|
| 定位 | 中间件全家桶，供本地 java -jar / 测试复用 | 应用 + 最小依赖（MySQL/Redis），一键体验 |
| Redis 密码 | **无密码**（dev 配置默认） | **带密码** `apex-redis-password`（prod 配置强制 `REDIS_PASSWORD`） |
| MySQL | root / apex-root，库 apex_data（+apex_test） | root / apex-root，库 apex_data |
| 应用 | 不包含 | 包含 |
| 组合使用 | 本地 java -jar 直连 infra 的 3306/6379 等 | 生产单机：应用 + 自带依赖 |

> ⚠️ **不要同时使用两个栈**（3306/6379 端口冲突）。本地开发用 infra 栈 + `java -jar`；
> 容器化部署用根 `docker-compose.yml`（其 Redis 带密码，与 infra 无密码 Redis 不同，勿混连）。

---

## 7. 环境变量完整参考表

> 所有变量均支持**环境变量 / docker -e / compose environment / systemd EnvironmentFile** 注入。
> 命名遵循 Spring relaxed binding，大写 + 下划线。

### 7.1 必配变量（生产）

| 变量 | 默认 | 说明 |
|------|------|------|
| `SPRING_PROFILES_ACTIVE` | dev | 激活 profile，格式 `<环境>,<档位>`，如 `prod,enterprise` |
| `DB_MASTER_URL` | dev: `jdbc:mysql://127.0.0.1:3306/apex_data...`；prod: **空（必填）** | 主库 JDBC URL；生产为空时启动校验直接失败，防止误连 |
| `DB_MASTER_USERNAME` | dev: `root`；prod: **空（必填）** | 数据库账号 |
| `DB_MASTER_PASSWORD` | dev: `apex-root`；prod: **空（必填）** | 数据库密码 |
| `DB_SLAVE_URL` | dev: `jdbc:mysql://127.0.0.1:3306/apex_data...`；prod: **空（必填）** | 从库 JDBC URL（prod 已启用 slave 数据源，必须注入；可指向只读副本）；生产为空时启动校验直接失败，防止误连 |
| `DB_SLAVE_USERNAME` | dev: `root`；prod: **空（必填）** | 从库数据库账号 |
| `DB_SLAVE_PASSWORD` | dev: `apex-root`；prod: **空（必填）** | 从库数据库密码 |
| `REDIS_HOST` | dev: `127.0.0.1`；prod: `127.0.0.1`（**必改**为真实地址） | Redis 地址 |
| `REDIS_PORT` | 6379 | Redis 端口 |
| `REDIS_PASSWORD` | dev: 无；prod: **空（必填）** | Redis 密码；**prod 配置强制非空**，需与 Redis 服务 `--requirepass` 一致 |
| `JWT_SECRET` | prod: **空（必填）** | JWT 签名密钥，**≥32 字节强随机**（prod 启动校验，不足即失败），生成：`openssl rand -base64 48` |

### 7.2 Nacos（无 Nacos 环境全部设 false）

| 变量 | 默认 | 说明 |
|------|------|------|
| `NACOS_CONFIG_ENABLED` | true | 配置中心开关；无 Nacos 设 `false` |
| `NACOS_DISCOVERY_ENABLED` | true | 服务发现开关；无 Nacos 设 `false` |
| `NACOS_REGISTER_ENABLED` | true | 是否注册实例；无 Nacos 设 `false` |
| `NACOS_SERVER_ADDR` | 内置默认 | Nacos 地址 `host:port` |
| `NACOS_NAMESPACE` | 内置默认 | 命名空间 ID（环境隔离） |

### 7.3 RocketMQ（可选）

| 变量 | 默认 | 说明 |
|------|------|------|
| `ROCKETMQ_NAMESERVER` / `ROCKETMQ_NAMESRV_ADDR` | 本地默认 `127.0.0.1:9876` | NameServer 地址 `host:port`；生产启用消息能力时必须注入可达地址 |
| `ROCKETMQ_PRODUCER_GROUP` | `apex-producer-group` | 生产者组 |
| `ROCKETMQ_SEND_TIMEOUT` | `30000` | 消息发送超时时间，单位为毫秒 |
| `APEX_MQ_CONSUMER_ENABLED` | false | 消费者总开关（`apex.mq.consumer.enabled`） |
| `APEX_MQ_DLQ_ENABLED` | false | 死信队列开关 |
| `APEX_MQ_DOMAIN_EVENT_ENABLED` | false | 领域事件 MQ 广播开关（关闭时走进程内事件） |
| `APEX_MQ_DOMAIN_EVENT_TOPIC` | `apex-domain-event` | 领域事件广播主题 |
| `MQ_IDEMPOTENT_TTL_MINUTES` | 30 | 消费幂等记录 TTL；必须为正数 |
| `ROCKETMQ_ACCESS_KEY` / `ROCKETMQ_SECRET_KEY` | 空 | 阿里云 RocketMQ 5.x 鉴权（可选） |
| `OUTBOX_BATCH_SIZE` | 50 | Outbox 每轮领取数量 |
| `OUTBOX_MAX_ATTEMPTS` | 8 | 单条 Outbox 最大投递次数 |
| `OUTBOX_LOCK_TIMEOUT_SECONDS` | 120 | Outbox worker 所有权超时 |
| `OUTBOX_PARALLELISM` | 4 | Outbox 并行投递数 |
| `OUTBOX_RETENTION_ENABLED` | true | 是否清理历史 Outbox |
| `OUTBOX_RETENTION_DELIVERED_HOURS` | 168 | DELIVERED 记录保留小时数 |
| `OUTBOX_RETENTION_DEAD_DAYS` | 30 | DEAD 记录保留天数 |
| `OUTBOX_RETENTION_BATCH_SIZE` | 5000 | 单轮清理数量 |

### 7.4 License（商业授权）

| 变量 | 默认 | 说明 |
|------|------|------|
| `APEX_LICENSE_ENABLED` | true | License 校验总开关 |
| `APEX_LICENSE_FILE` | `classpath:license/license-basic.json` | 授权文件路径，支持 classpath / 文件系统 / 容器挂载 |
| `APEX_LICENSE_PUBLIC_KEY_FILE` | `classpath:license/public.key` | 验签公钥（默认随包，一般无需改） |

### 7.5 运行/运维

| 变量 | 默认 | 说明 |
|------|------|------|
| `JAVA_OPTS` | `-Xms512m -Xmx1024m -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:MetaspaceSize=256m -XX:MaxMetaspaceSize=512m -XX:+HeapDumpOnOutOfMemoryError -XX:+ExitOnOutOfMemoryError -Xlog:gc*:file=<logs>/gc.log:...`（完整见 §4.5） | JVM 调优参数（Docker/systemd/java -jar 场景，`JAVA_OPTS` 覆盖） |
| `SERVER_PORT` | 8080 | 业务端口 |
| `MANAGEMENT_SERVER_PORT` | 8081 | 管理端口（Actuator） |
| `MANAGEMENT_SERVER_ADDRESS` | 127.0.0.1 | 管理端口监听地址；容器/远程采集设 `0.0.0.0` |
| `APEX_LOG_DIR` | `./logs` | 日志根目录（见 [§9](#9-日志与监控)） |
| `TZ` | Asia/Shanghai | 时区 |
| `APEX_REDIS_CLEANUP_ON_STARTUP` | false | 启动时是否清理 Redis 缓存 |
| `STORAGE_LOCAL_BASE_PATH` | `./data/storage` | 本地存储根目录（文件上传） |
| `FILE_UPLOAD_ENABLED` | true（prod 强制联动） | 文件上传开关；prod 下 `true` 必须同时开启百度云图片审核（`BAIDU_IMAGE_MODERATION_API_KEY` / `BAIDU_IMAGE_MODERATION_SECRET_KEY` / `image.moderation.baidu.strategy-id`），否则启动校验失败 |
| `SENTINEL_DASHBOARD_URL` | `http://127.0.0.1:8858` | Sentinel 控制台地址（可选） |
| `SENTINEL_TRANSPORT_PORT` | 8719 | Sentinel 通信端口，同机多实例须唯一 |

### 7.6 compose 编排变量（见 .env.example）

`BUILD_PROFILE` / `TAG` / `APP_PORT` / `MANAGEMENT_PORT` / `MYSQL_DATABASE` / `MYSQL_ROOT_PASSWORD` / `MYSQL_PORT` / `REDIS_PORT` 等，均为**编排层**变量，与应用配置解耦。

---

## 8. 健康检查与就绪探针

| 端点 | 端口 | 说明 |
|------|------|------|
| `GET /api/v1/health` | 8080 | 业务健康端点，返回 `code=0, data.status=UP`；`app.sh` 启动检查和外部负载均衡可使用 |
| `GET /actuator/health` | 8081 | Spring Boot Actuator 聚合健康，`show-details: when-authorized` |
| `GET /actuator/health/liveness` | 8081 | 存活探针，仅包含 `livenessState` |
| `GET /actuator/health/readiness` | 8081 | 就绪探针，包含 `readinessState`、DB 和 Redis；Docker HEALTHCHECK 使用此端点 |
| `GET /actuator/prometheus` | 8081 | Prometheus 指标抓取端点 |

验证：

```bash
curl -s http://localhost:8080/api/v1/health
# {"code":0,"msg":"成功","data":{"status":"UP","application":"apex-scaffold",...}}

curl -s http://localhost:8081/actuator/health/readiness
# {"status":"UP","components":{"db":{"status":"UP"},...}}
```

> **端点用途**：`/api/v1/health` 是不包含依赖状态的业务存活响应，当前 `app.sh` 用它确认 HTTP 服务启动；
> `/actuator/health/liveness` 用于进程存活判断；`/actuator/health/readiness` 包含就绪状态、DB 和 Redis，
> 供 Docker 或编排平台判断是否接收流量。Nacos 注册与摘除由服务发现组件生命周期处理，
> `GracefulShutdownManager` 不主动调用 deregister。

---

## 9. 日志与监控

### 9.1 日志

日志根目录由 `APEX_LOG_DIR` 控制（默认 `<工作目录>/logs`，优先级：`-Dapex.log.dir` > `APEX_LOG_DIR` > `apex.log.dir` 配置项 > 默认）。生产建议指向持久化目录。

```
logs/
├── apex-scaffold.log          # 主业务日志（按天/大小滚动）
├── error.log                  # 错误日志（ERROR 级别独立归档）
├── sql.log                    # SQL 日志（dev 环境 p6spy）
├── nacos/                     # Nacos 客户端日志（启用时）
├── rocketmq/                  # RocketMQ 客户端日志（启用时）
├── sentinel/                  # Sentinel 日志（启用时）
└── zipkin/                    # 链路追踪日志（启用时）
```

查看：

```bash
tail -f logs/apex-scaffold.log        # java -jar 部署
docker compose logs -f apex-scaffold  # compose 部署
journalctl -u apex-scaffold -f        # systemd 部署（同时有文件日志）
```

### 9.2 指标与大盘

- Prometheus 抓取：`http://<host>:8081/actuator/prometheus`，指标名 `apex.api.*`
- 一键监控栈：`docker compose -f config/monitoring/docker-compose.yml up -d`
  - Grafana 大盘（`apex_api_*` 口径）、Prometheus 告警规则（10 条）、Blackbox 健康探针
  - 详见 [`config/monitoring/README.md`](../config/monitoring/README.md)

---

## 10. License 商业授权部署

- 随包内置**基础版授权**（`classpath:license/license-basic.json`），基础功能开箱即用
- 购买标准版/企业版后，卖方发放 `.json` 授权文件与验签公钥，部署时挂载并注入：

```bash
# java -jar / systemd
export APEX_LICENSE_FILE=/etc/apex-scaffold/license-enterprise.json
export APEX_LICENSE_PUBLIC_KEY_FILE=/etc/apex-scaffold/public.key   # 官方公钥（可选）

# Docker
docker run ... \
  -v /opt/apex-licenses:/licenses:ro \
  -e APEX_LICENSE_FILE=/licenses/license-enterprise.json \
  -e APEX_LICENSE_PUBLIC_KEY_FILE=/licenses/public.key \
  apex-scaffold:1.1.3

# Compose（在 .env 中配置后挂载卷）
#   APEX_LICENSE_FILE=/licenses/license-enterprise.json
#   volumes: - /opt/apex-licenses:/licenses:ro
```

- 未检测到有效 License 时进入**降级模式**：基础功能放行、商业功能拒绝并输出 WARN
- 发放工具：`scripts/license/generate-license.sh`；详见 [`docs/LICENSE_MANAGEMENT.md`](LICENSE_MANAGEMENT.md)

---

## 11. 可选组件接入（Nacos / RocketMQ / Sentinel）

### 11.1 接入 Nacos 配置中心 + 服务发现

```bash
# 环境变量注入（三处开关打开 + 地址 + 命名空间）
export NACOS_CONFIG_ENABLED=true
export NACOS_DISCOVERY_ENABLED=true
export NACOS_REGISTER_ENABLED=true
export NACOS_SERVER_ADDR=10.0.0.10:8848
export NACOS_NAMESPACE=prod

# Nacos 控制台创建命名空间（ID 与 NACOS_NAMESPACE 一致），
# 并按 dataId=apex-scaffold-{profile}.yml、group=DEFAULT_GROUP 推送远端配置
```

配置加载顺序：`bootstrap.yml → Nacos 远端配置 → application.yml → application-{profile}.yml`；
`@RefreshScope` / `@Value` 支持运行时热更新。

### 11.2 接入 RocketMQ

同 [§6.3](#63-启用-rocketmq可选)；独立部署时：

```bash
export ROCKETMQ_NAMESERVER=10.0.0.11:9876
export APEX_MQ_CONSUMER_ENABLED=true
export APEX_MQ_DLQ_ENABLED=true
export APEX_MQ_DOMAIN_EVENT_ENABLED=true
```

### 11.3 接入 Sentinel 控制台

```bash
export SENTINEL_DASHBOARD_URL=http://10.0.0.12:8858
export SENTINEL_TRANSPORT_PORT=8719   # 同机多实例须唯一
```

---

## 12. 升级与回滚

### 12.1 升级步骤（三种方式通用）

1. **备份**：见 [§13](#13-备份与恢复)（数据库 dump + 授权文件副本）
2. 构建新版本产物（或新镜像）
3. 替换部署（见下）
4. 验证：`/api/v1/health` UP、登录一次、抽查核心接口、观察 `logs/error.log`
5. 如有失败回滚（见 12.3）

### 12.2 按部署方式替换

```bash
# java -jar（systemd）
sudo cp apex-interfaces-1.2.0-executable.jar /opt/apex-scaffold/app.jar
sudo systemctl restart apex-scaffold

# Docker 单容器
docker build -t apex-scaffold:1.2.0 .
docker stop apex-scaffold && docker rm apex-scaffold
docker run -d --name apex-scaffold ...（同 §5.2 参数，镜像 1.2.0）

# Docker Compose
docker compose build                                  # 用新代码重建
docker compose up -d                                  # 滚动重建受影响服务
docker compose ps
```

### 12.3 回滚

```bash
# java -jar：还原旧 jar 后重启
sudo cp /opt/apex-scaffold/backup/app-1.1.3.jar /opt/apex-scaffold/app.jar
sudo systemctl restart apex-scaffold

# Docker：用旧镜像标签重建容器
docker run -d --name apex-scaffold ... apex-scaffold:1.1.3

# Compose：改回旧 TAG 后重建
#   TAG=1.1.3（.env）→ docker compose up -d --build
```

> ⚠️ 数据库迁移由 Flyway 管理（`classpath:db/migration`，V1~V7）。**回滚到旧版本时**，
> Flyway 仅校验 schema 兼容性；新增迁移如已执行，旧版本可能因 checksum/缺列校验失败。
> 升级前务必 dump 数据库，回滚时按需还原数据。

### 12.4 灰度发布（K8s 金丝雀）

K8s 场景支持按权重/请求头灰度：稳定版与金丝雀版并存，Nginx Ingress 将
10% 流量导入新版本，验证通过后逐步放量至全量切换，失败即时回滚。

```bash
kubectl apply -f k8s/canary-deployment.yaml   # 金丝雀（默认 10% 流量）
# 验证 → 调整权重（canary-by-weight: 50/100）→ 全量切换 → 删除金丝雀
# 失败 → 删除金丝雀 Ingress 即回滚（流量 100% 回稳定版）
```

> 完整演练步骤、按请求头路由与 Argo Rollouts 进阶方案见 [k8s/README.md](../k8s/README.md)。

---

## 13. 备份与恢复

### 13.1 MySQL

```bash
# 备份（容器场景）
docker exec apex-mysql mysqldump -uroot -p'***' --single-transaction --routines \
  apex_data > apex_data_$(date +%Y%m%d).sql

# 恢复
docker exec -i apex-mysql mysql -uroot -p'***' apex_data < apex_data_20260828.sql

# java -jar 场景：直接使用 mysql 客户端 dump / restore，命令同上（无 docker exec）
```

### 13.2 Redis

```bash
# 容器数据卷备份（AOF 已开启）
docker run --rm -v apex-scaffold_redis-data:/data -v $(pwd):/backup \
  alpine tar czf /backup/redis-data.tar.gz -C /data .
```

### 13.3 其他

- `logs/`、`data/storage/`（本地存储文件）、`/etc/apex-scaffold/`（环境变量与 License）一并纳入备份策略

---

## 14. 生产安全部署清单

- [ ] `JWT_SECRET` 使用 `openssl rand -base64 48` 生成，长度 ≥32 字节
- [ ] `DB_MASTER_PASSWORD` / `DB_SLAVE_PASSWORD` / `MYSQL_ROOT_PASSWORD` 强密码，与默认值不同
- [ ] 无 Nacos 时显式设置 `NACOS_CONFIG_ENABLED=false` 等三个开关
- [ ] `MANAGEMENT_SERVER_ADDRESS`：公网裸跑建议保持 `127.0.0.1` 或经防火墙仅放行监控网段；容器/内网采集再设 `0.0.0.0`
- [ ] 管理端点仅暴露 `health,info,metrics,prometheus,circuitbreakers`（已内置，勿开启 `env/configprops`）
- [ ] CORS：生产配置白名单（`cors.allowed-origins` 逗号分隔），非 `allow-all-origins`
- [ ] 商业授权文件以只读卷挂载（`:ro`），私钥/公钥不入镜像与仓库
- [ ] 如需文件上传，`FILE_UPLOAD_ENABLED=true` 且百度云图片审核（API Key / Secret Key / strategy-id）已配置
- [ ] 日志目录、数据卷纳入备份；`.env.prod` 加入 `.gitignore`
- [ ] 通过 `curl /api/v1/health`、`/actuator/health/readiness` 确认 DB/Redis 均 UP
- [ ] 上线后观察 `logs/error.log` 与 Grafana 大盘（`apex.api.*`），确认告警规则生效

---

## 15. 常见问题 FAQ

**Q1 启动报错：`Failed to configure a DataSource` / 连接超时？**
检查 `DB_MASTER_URL/USERNAME/PASSWORD`（以及从库 `DB_SLAVE_URL/USERNAME/PASSWORD`）是否正确注入、MySQL 是否可达、库是否已创建
（`CREATE DATABASE apex_data DEFAULT CHARACTER SET utf8mb4;`）。生产环境三项均为必填，空值会触发启动校验失败（安全兜底）。

**Q2 启动时大量 Nacos 连接日志/卡顿？**
未使用 Nacos 却未关闭：设置 `NACOS_CONFIG_ENABLED=false`、`NACOS_DISCOVERY_ENABLED=false`、`NACOS_REGISTER_ENABLED=false` 后重启。

**Q3 使用普通 jar（非 -executable.jar）启动报 `no main manifest attribute`？**
必须使用 `apex-interfaces-*-executable.jar`（classifier=executable）。

**Q4 docker compose 启动后应用容器反复重启？**
`docker compose logs apex-scaffold` 查看原因；常见为 MySQL 未就绪时应用已启动（`depends_on` 健康等待已配置，首次初始化较慢可等待 60s）、或 `JWT_SECRET` 过短。

**Q5 8081 管理端口无法从外部访问？**
默认监听 `127.0.0.1`；容器/远程采集需设 `MANAGEMENT_SERVER_ADDRESS=0.0.0.0`（镜像与 compose 已默认设置）。

**Q6 启动失败：Redisson 无法连接 Redis / AUTH 报错？**
prod 配置强制 `REDIS_PASSWORD` 非空。请确认：① 已注入 `REDIS_PASSWORD`；② 该密码与 Redis 服务实际 `requirepass` 一致（compose 默认栈二者已联动）。无密码的 Redis 实例需设置密码或改配 non-prod profile。

**Q7 如何切换档位（如 basic → enterprise）？**
重建时改 `BUILD_PROFILE`（构建档位）+ `SPRING_PROFILES_ACTIVE`（运行档位），两者需一致；商业档位需配套 License。

**Q8 修改配置后如何生效？**
环境变量类：重启应用。Nacos 场景：`@RefreshScope`/`@Value` 支持热更新，无需重启。

**Q9 端口冲突？**
java -jar 改 `SERVER_PORT`；compose 改 `.env` 的 `APP_PORT/MANAGEMENT_PORT/MYSQL_PORT/REDIS_PORT`。

**Q10 数据库表没有自动创建？**
Flyway 会在应用启动时执行 `classpath:db/migration`（当前 V1~V15）。若日志出现 Flyway 报错，检查账号建表权限和已执行迁移 checksum；`flyway.clean-disabled=true` 防误删。

**Q11 中文字符乱码？**
MySQL 已强制 utf8mb4（compose 内置）；独立部署请确认库表字符集 `utf8mb4`，并在 JDBC URL 带 `characterEncoding=utf8`。

---

## 16. 命令速查表

| 操作 | java -jar | Docker | Compose |
|------|-----------|--------|---------|
| 启动 | `./scripts/deploy/app.sh start prod enterprise` | `docker run ...` | `docker compose up -d --build` |
| 停止 | `./scripts/deploy/app.sh stop` | `docker stop apex-scaffold` | `docker compose down` |
| 重启 | `./scripts/deploy/app.sh restart prod enterprise` | `docker restart apex-scaffold` | `docker compose restart` |
| 日志 | `./scripts/deploy/app.sh logs` | `docker logs -f apex-scaffold` | `docker compose logs -f apex-scaffold` |
| 状态 | `./scripts/deploy/app.sh status` | `docker ps` | `docker compose ps` |
| 健康 | `curl :8080/api/v1/health` | 同左 | 同左 |
| 删除数据卷 | — | — | `docker compose down -v`（⚠️ 数据丢失） |

---

*文档维护：Apex Team ｜ 如有出入以代码为准；构建脚本见 `scripts/build/build.sh`，监控栈见 `config/monitoring/`。*
