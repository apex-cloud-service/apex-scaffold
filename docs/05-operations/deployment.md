# Apex Scaffold 部署使用指南

system 历史库升级至 V40 前须停写、备份并核对 `oauth2_authorization_consent`。MySQL 使用 [consent 前置修复](../../apex-components-parent/apex-system/src/main/resources/db/upgrade/mysql/consent_before_v40.sql)，通过 `mysql --default-character-set=utf8mb4` 在目标 system 库执行，禁止 `--force`，完成后再运行原 Flyway V40。脚本仅为缺失/空 ID 回填稳定值，保留已有非空 ID、业务键、权限与时间，先保护业务唯一键再换主键；MySQL DDL 隐式提交，失败后可重入，但必须保持停写。发现重复已有 ID、重复业务键或不支持结构时人工处理，不能删除记录或覆盖 ID。

PostgreSQL V40 为无条件 DDL，使用 [事务前置修复](../../apex-components-parent/apex-system/src/main/resources/db/upgrade/postgresql/consent_before_v40.sql) 后，须通过 [psql 包装器](../../apex-components-parent/apex-system/src/main/resources/db/upgrade/postgresql/run_v40_with_consent_backup.sql) 执行原 V40，均用 `psql -X -v ON_ERROR_STOP=1 -d <system数据库> -f <脚本路径>`。部署时保留 `db/upgrade/postgresql/` 与 `db/migration/postgresql/` 的相对目录结构，包装器的 `\ir ../../migration/postgresql/V40__fix_schema_inconsistencies_based_on_po.sql` 相对于包装器文件定位原 V40。隔离 PostgreSQL 已按该结构复制原始前置脚本、包装器与 V40，并实际用 `psql -f` 验证包含及重放。前置脚本遇到已有 `id` 非 `VARCHAR(100)` 时明确拒绝并回滚，须人工核对，不能自动转换。包装器事务内暂存 consent、执行原始 V40 并恢复 ID/数据/时间；异常整体回滚。只支持已审核的旧 V40 源字段齐全结构；其他表已部分重命名、非标准主键名、依赖外键/触发器/扩展列须先人工审阅，不能盲跑。包装器重入时跳过已重命名结构，不表示原 PostgreSQL V40 自身可重放。

这些显式运维脚本不更新 Flyway 历史。历史有 failed V40 时，DBA 必须先审查失败 DDL 与备份，按组织流程协调 V40 执行及历史记录；禁止自动 `repair`、改 checksum 或直接标记成功。PostgreSQL 包装器手工执行完成也不能继续让 Flyway 再执行同一 V40；上线前必须完成历史对账。本路径只验证隔离合成库，未授权执行生产升级。

MySQL V42 实际失败后的结构恢复使用 V42 离线恢复工具 [token-v42-recover.sh](../../scripts/utils/token-v42-recover.sh)（Java 实现，见 [TokenV42Recover.java](../../apex-tools-parent/apex-tools-deployment/src/main/java/com/apex/scaffold/tools/deployment/TokenV42Recover.java)）。仅支持 MySQL 9.0，需 JDK 21 与 mysql CLI；先停止全部业务写入、Flyway 和其他 schema 变更，验证备份可恢复。工具从仓库根目录定位 `db/migration/mysql/V42__add_token_encryption.sql` 并核对其 SHA-256。以下命令在仓库根目录执行，数据库名及受保护的客户端配置路径按实际替换，`--mysql-command` 必须最后传入，认证信息不要写入命令行：

```bash
# 默认仅预检并打印计划
bash scripts/utils/token-v42-recover.sh \
  --database system_db --offline \
  --mysql-command mysql --defaults-extra-file=/secure/mysql-client.cnf
# 审阅计划并保持离线后显式执行
bash scripts/utils/token-v42-recover.sh \
  --database system_db --offline --apply \
  --mysql-command mysql --defaults-extra-file=/secure/mysql-client.cnf
```

预检在任何新增 DDL 前检查既有列类型/空值/默认值/生成属性、索引定义、后三表完整列索引集合及 `v_token_health` 定义；缺 oauth2 表或视图依赖时间列、非 InnoDB/utf8mb4、触发器、CHECK/外键及不兼容对象均拒绝，须人工审阅。token 表可缺。工具只补缺失结构，不删除或覆盖 token、已有非空加密数据，也不回填加密内容；密钥版本 1 仅缺失时插入，既有 inactive 状态保留。MySQL DDL 隐式提交，执行中基础设施错误可能留下部分完成状态，应保持停写、核对后重入；这不是事务回滚或在线并发迁移工具。

原 V42 在 token 存在时 PREPARE 1064，之前 OAuth 10 列/4 索引已持久化；原文件重试报 1060。结构恢复**不能使原 V42 可重放**，不执行 Flyway repair、mark success 或 checksum 修改。完成后仍须 DBA 按组织流程单独协调失败迁移/history，禁止直接让 Flyway 再跑原 V42。验证范围是隔离合成 MySQL，未执行生产恢复；本次恢复不计原生覆盖，当前仍为 **489/586，剩97**，详见 [P0 报告](../06-testing/jdk21-unified-auth/20261016-p0/report.md)。

2026-10-19追加复核：上述489/586为前次记录；PG两批新增7+4后有效并集为 **500/586，剩86（MySQL70、PostgreSQL16）**，恢复仍新增0。当前恢复工具在apply后若postflight仍有计划即失败，并实际查询 `v_token_health` 后才输出完成；带空格引用标识符不被规范化为另一名称，force别名及空mysql命令均拒绝。最终MySQL8.0.46隔离证据为31场景（3恢复、28拒绝），验证已有inactive v1与active v2重入保留、清理成功；首轮外键fixture失败和旧22场景证据全部保留，hash及限制见[P0本轮报告](../06-testing/jdk21-unified-auth/20261016-p0/report.md)。恢复单文件当前9个测试方法，旧13项为跨文件组合；这些验证不替代Flyway历史协调、真实旧库升级及生产恢复验收。

文件服务上线前须按[对象存储接入指南](object-storage-integration.md)预创建云桶，配置七牛桶区域/绑定域名及 auth RS256 公钥。可通过 `FILE_JWT_PUBLIC_KEY_BASE64` 配置单公钥，或通过 `FILE_JWT_PUBLIC_KEYS_BASE64` 配置静态多公钥（格式 `kid=Base64PublicKey,kid=Base64PublicKey`，按 Token header 的 `kid` 选择，多公钥优先；缺少或未知 `kid` 拒绝）。file 不自动获取 JWKS，密钥轮换须更新配置并重启；auth 私钥不得进入仓库、镜像或 Compose 模板，真实云凭证只能放在受控部署配置中。Compose 显式注入存储变量，并将主机 `./data/storage` 挂载到容器 `/app/data/storage`；请预先校验目录权限与备份。Java 不会自动加载 `.env`。切换默认存储不迁移历史对象，不得删除旧策略配置或直接回退归属迁移。

[查看部署流程图](../diagrams/45_deployment_flow.svg)

> 版本：1.3.1 ｜ 适用：dev / it / prod 全环境
> 本文档覆盖微服务架构下的 **java -jar 直接部署**、**Podman 单容器**、**Podman Compose 编排** 三种方式，并给出微服务拓扑、环境变量、健康检查、日志监控、License、升级回滚、安全清单等完整说明。
> 旧单体 `apex-scaffold` 已退役：原 8080 统一入口由 **apex-gateway 网关** 承接，业务功能拆分到 6 个独立业务服务（system / auth / workflow / file / office / integration，见迁移方案 §13.1/§15.1/§15.3）。

---

## 目录

- [1. 部署模型与微服务拓扑](#1-部署模型与微服务拓扑)
- [2. 前置条件](#2-前置条件)
- [3. 构建与产物](#3-构建与产物)
- [4. 部署方式一：java -jar 直接部署](#4-部署方式一java--jar-直接部署)
  - [4.1 最小启动（单服务）](#41-最小启动单服务)
  - [4.2 守护脚本 app.sh](#42-守护脚本-appsh)
  - [4.3 systemd 服务化（推荐生产）](#43-systemd-服务化推荐生产)
- [5. 部署方式二：Podman 单容器](#5-部署方式二podman-单容器)
- [6. 部署方式三：Podman Compose 编排](#6-部署方式三podman-compose-编排)
  - [6.1 一键启动完整微服务栈](#61-一键启动完整微服务栈)
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

<a id="1-部署模型与微服务拓扑"></a>

## 1. 部署模型与微服务拓扑

| 方式 | 适用场景 | 特点 | 依赖 |
|------|---------|------|------|
| **java -jar** | 传统服务器 / 已有 MySQL+Redis+Nacos 的机房、云主机 | 最轻量、便于和 systemd/监控代理结合 | JDK 21 + MySQL + Redis + Nacos |
| **Podman 单容器** | 已容器化的 K8s 以外的裸容器环境 | 一镜像一服务，多服务互不干扰 | Podman + 外部 MySQL/Redis/Nacos |
| **Podman Compose** | 一键拉起完整微服务环境（开发/测试/生产单机） | 网关 + 6 个业务服务 + 查询聚合服务 + Nacos + MySQL + Redis（+可选 RocketMQ/Keycloak/任务代理/脱敏）全部编排 | Podman + podman-compose |

各服务共享**同一套配置体系与环境变量**（见 [§7](#7-环境变量完整参考表)），切换部署方式无需修改代码或配置。

微服务拓扑（每个服务一个独立进程/镜像/数据库）：

```
┌──────────────────────────────────────────────────────────────────┐
│                         统一入口 apex-gateway :8080              │
│               （统一 API 入口；按 lb:// 路由到各服务）            │
└───────────────┬───────────────────────────┬──────────────────────┘
                ▼ lb://                     ▼ lb://
┌─────────────────────────┐   ┌──────────────────────────────────┐
│ apex-auth-service:8081  │   │ apex-system-service:8082 (管理8083)│
│  DB apex_auth           │   │  DB apex_system                   │
├─────────────────────────┤   ├──────────────────────────────────┤
│ apex-workflow-service   │   │ apex-office-service:8084          │
│  :8093       │   │  DB apex_office                   │
│  DB apex_workflow       │   ├──────────────────────────────────┤
├─────────────────────────┤   │ apex-file-service:8085            │
│ apex-integration-service│   │  DB apex_file                     │
│  :8094 (管理 8097)      │   ├──────────────────────────────────┤
│  DB apex_integration    │   │ apex-aggregation-service:8098     │
├─────────────────────────┤   │  （无 DB，只读跨源聚合）           │
│  共享: Redis / Nacos /  │   │                                    │
│  RocketMQ / License     │   │                                    │
└─────────────────────────┘   └──────────────────────────────────┘
                          │
                          ▼
       Nacos:8848 / 9848  │  MySQL:3306（每服务独立库） │  Redis:6379
       前端 apex-admin-web :80（Nginx 反代 /api → 网关 8080）
```

> **关于 Nacos**：微服务拓扑下**默认启用 Nacos 服务发现**（网关 `lb://` 路由依赖实例注册）。
> 若需脱离 Nacos 独立部署单服务，设 `NACOS_CONFIG_ENABLED=false`、`NACOS_DISCOVERY_ENABLED=false`、
> `NACOS_REGISTER_ENABLED=false` 后，直连该服务端口测试（见 [§11](#11-可选组件接入nacos--rocketmq--sentinel)）。

---

<a id="2-前置条件"></a>

## 2. 前置条件

| 组件 | 版本要求 | 说明 |
|------|---------|------|
| JDK | 21（`.sdkmanrc` 固定 `21.0.13.0+9-tem`） | java -jar 部署必需 |
| MySQL | **9.0**（compose 镜像 `mysql:9.0`；V42 恢复工具亦仅支持 MySQL 9.0） | 各服务库由 Flyway 自动迁移建表（**system 的 dev 档明确关闭自动迁移**，首次空库须先显式启用或手工执行迁移），只需保证账号可建库/建表/写数据 |
| Redis | 6.x / 7.x | 缓存 / Token / 分布式锁；网关限流 |
| Nacos | 2.x | 服务发现 + 配置中心（微服务拓扑默认依赖） |
| Podman | 5.x | 容器部署必需；macOS 使用 Podman machine |
| podman-compose | 1.x | 独立 Compose CLI，需单独安装 |

基础环境自检：

```bash
java -version                 # openjdk 21+
mysql --version               # 9.0（compose 镜像口径）
redis-cli --version
podman --version
podman-compose --version
```

---

<a id="3-构建与产物"></a>

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
# 本地统一构建环境（sdkman + mvnd，见 .sdkmanrc）
cd /Users/work/apex-scaffold && sdk env
# 或直接使用 Maven Daemon（CI / 容器）
mvnd clean package -DskipTests -Penterprise     # 或 -Pbasic / -Pstandard
```

### 3.3 构建产物

每个微服务模块经 `spring-boot-maven-plugin` 重打包为**可执行 fat jar**（无 classifier，唯一 `*.jar`）：

```
apex-services-bom/apex-gateway/target/apex-gateway-1.3.1.jar            # 网关（统一入口）
apex-services-bom/apex-auth-service/target/apex-auth-service-1.3.1.jar  # 认证服务
apex-services-bom/apex-system-service/target/apex-system-service-1.3.1.jar
apex-services-bom/apex-workflow-service/target/apex-workflow-service-1.3.1.jar
apex-services-bom/apex-office-service/target/apex-office-service-1.3.1.jar
apex-services-bom/apex-file-service/target/apex-file-service-1.3.1.jar
apex-applications-parent/apex-example/target/apex-example-1.3.1.jar            # basic 档单体演示（非微服务）
```

> **务必使用各服务 target 目录下的可执行 jar**：其一端包含 Spring Boot 启动器，`java -jar apex-services-bom/apex-gateway/target/apex-gateway-1.3.1.jar` 即可运行对应微服务。
> 验证产物档位：`cat <module>/target/classes/apex-build.properties`，`build.profile.id` 与构建档位一致。

### 3.4 验证产物可启动（冒烟）

```bash
# 使用本地基础设施（podman-compose -f podman-compose.infra.yml up -d mysql redis）
SPRING_PROFILES_ACTIVE=dev,standard \
  java -jar apex-services-bom/apex-gateway/target/apex-gateway-1.3.1.jar
# 看到 "Started ... Application" 且 http://localhost:8080/api/v1/health 返回 UP 即通过
```

---

<a id="4-部署方式一java--jar-直接部署"></a>

## 4. 部署方式一：java -jar 直接部署

> 适合已有 MySQL/Redis/Nacos 的服务器，无需容器运行时。推荐生产使用 systemd 托管（§4.3）。
> 本方式按微服务逐个启动；请先启动 Nacos 与 MySQL，再依次启动各服务实现网关路由。

<a id="41-最小启动单服务"></a>

### 4.1 最小启动（单服务）

以认证服务为例：

```bash
# 1. 上传可执行 jar 到服务器，例如 /opt/apex-scaffold/apex-auth-service.jar
scp apex-services-bom/apex-auth-service/target/apex-auth-service-1.3.1.jar \
    user@server:/opt/apex-scaffold/apex-auth-service.jar

# 2. 注入环境变量并启动（前台，Ctrl+C 停止，调试用）
export SPRING_PROFILES_ACTIVE=prod,enterprise
# 数据源：DB_HOST/DB_PORT/DB_NAME（用户名/密码见 §7.1）
export DB_HOST=127.0.0.1
export DB_PORT=3306
export DB_NAME=apex_auth
export DB_USERNAME=apex
export DB_PASSWORD='********'
# Redis / JWT / 加密密钥 / Nacos
export REDIS_HOST=127.0.0.1
export REDIS_PORT=6379
export REDIS_PASSWORD='********'   # prod 配置强制要求（spring.data.redis.password=${REDIS_PASSWORD}）
# 密钥生成：必须用双引号或命令替换赋值，单引号会导出字面量 "$(openssl ...)"。
# 生成后请持久保存，重启/重建时复用同一组密钥；轮换需配套数据迁移。
export JWT_SECRET="$(openssl rand -base64 48)"
export APEX_DATA_ENCRYPTION_KEY="$(openssl rand -base64 32)"
export SECURITY_ENCRYPTION_KEY="$(openssl rand -base64 32)"
export CONFIG_ENCRYPTION_KEY="$(openssl rand -hex 16)"
export APEX_CONFIG_ENCRYPTION_KEY="$(openssl rand -base64 32)"
export NACOS_SERVER_ADDR=127.0.0.1:8848    # 默认 Nacos 地址

export JAVA_OPTS='-Xms512m -Xmx768m -Xss512k -XX:MaxDirectMemorySize=128m -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:MetaspaceSize=256m -XX:MaxMetaspaceSize=512m -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/opt/apex-scaffold/logs/heapdump.hprof -XX:+ExitOnOutOfMemoryError -Xlog:gc*:file=/opt/apex-scaffold/logs/gc.log:time,uptime,level,tags:filecount=5,filesize=20m -Dfile.encoding=UTF-8 -Djava.security.egd=file:/dev/./urandom'
mkdir -p /opt/apex-scaffold/logs
java ${JAVA_OPTS} -jar /opt/apex-scaffold/apex-auth-service.jar
```

启动成功标志：

```
Started AuthServiceApplication in 12.345 seconds
Netty started on port 8081
```

> **网关与各服务的端口 / 数据库**见 §1 拓扑。若使用 `SPRING_DATASOURCE_URL/USERNAME/PASSWORD`（Compose 采用的显式 JDBC URL），可覆盖 `DB_HOST/DB_PORT/DB_NAME/USERNAME/PASSWORD`。

<a id="42-守护脚本-appsh"></a>

### 4.2 守护脚本 app.sh

仓库内置守护脚本，支持单服务后台启动 / 停止 / 重启 / 状态 / 日志（模块经 `APEX_SERVICE_MODULE` 指定，见 `scripts/deploy/service/app.sh`）。`scripts/deploy/service/start.sh` 是 Compose 启动兼容入口。

```bash
# 语法: ./scripts/deploy/service/app.sh {start|stop|restart|status|logs|console} [dev|it|prod] [basic|standard|enterprise]
APEX_SERVICE_MODULE=apex-auth-service ./scripts/deploy/service/app.sh start prod enterprise
APEX_SERVICE_MODULE=apex-auth-service ./scripts/deploy/service/app.sh status
APEX_SERVICE_MODULE=apex-auth-service ./scripts/deploy/service/app.sh logs
APEX_SERVICE_MODULE=apex-auth-service ./scripts/deploy/service/app.sh restart it standard
APEX_SERVICE_MODULE=apex-auth-service ./scripts/deploy/service/app.sh stop
```

行为说明：

| 项 | 默认值 | 覆盖方式 |
|----|--------|---------|
| JVM 参数 | `-Xms512m -Xmx1024m -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:MetaspaceSize=256m -XX:MaxMetaspaceSize=512m -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=<logs>/heapdump.hprof -XX:+ExitOnOutOfMemoryError -Xlog:gc*:file=<logs>/gc.log:...（滚动 5×20m） -Dfile.encoding=UTF-8` | `JAVA_OPTS` |
| GC 日志 / 堆转储 | `<日志根>/gc.log`、`<日志根>/heapdump.hprof`（与 logback 业务日志同目录，`APEX_LOG_DIR` 可整体迁移） | `APEX_LOG_DIR` |
| 目标微服务模块 | `apex-gateway` | `APEX_SERVICE_MODULE` |
| JAR 产物 | `<模块>/target/<模块>-\*.jar` | `APEX_JAR` |
| PID 文件 | `<项目根>/logs/<模块>.pid` | `APP_PID_FILE` |
| 标准输出日志 | `<项目根>/logs/<模块>.out.log` | `APP_OUT_LOG` |
| 环境变量文件 | 无 | `APEX_ENV_FILE=/etc/apex-scaffold/apex.env`（每行 `KEY=VALUE`） |
| 业务端口 | 按模块选择，网关为 8080 | `SERVER_PORT` |
| 健康检查端口 | 默认复用业务端口，system 为 8083 | `MANAGEMENT_PORT` |

<a id="43-systemd-服务化推荐生产"></a>

### 4.3 systemd 服务化（推荐生产）

仓库已提供模板 `scripts/deploy/apex-scaffold.service`，`ExecStart` 固定执行 `/opt/apex-scaffold/app.jar`。以下示例部署一个认证服务；同机部署多个服务时，须为每个单元分别调整 `WorkingDirectory`、`ExecStart`、日志路径及 `EnvironmentFile`。

```bash
# 1. 放置产物与目录（以认证服务为例）
sudo mkdir -p /opt/apex-scaffold/logs /etc/apex-scaffold /var/log/apex-scaffold
sudo cp apex-services-bom/apex-auth-service/target/apex-auth-service-1.3.1.jar /opt/apex-scaffold/app.jar
sudo useradd -r -s /usr/sbin/nologin apex || true

# 2. 创建环境变量文件（敏感值生产必改）
sudo tee /etc/apex-scaffold/apex.env > /dev/null <<'EOF'
SPRING_PROFILES_ACTIVE=prod,enterprise
DB_HOST=127.0.0.1
DB_PORT=3306
DB_NAME=apex_auth
DB_USERNAME=apex
DB_PASSWORD=********
REDIS_HOST=127.0.0.1
REDIS_PORT=6379
REDIS_PASSWORD=********
JWT_SECRET=********
NACOS_SERVER_ADDR=127.0.0.1:8848
APEX_LOG_DIR=/var/log/apex-scaffold
EOF

# 3. 安装并启用认证服务单元
sudo cp scripts/deploy/apex-scaffold.service /etc/systemd/system/apex-auth-service.service
sudo chown -R apex:apex /opt/apex-scaffold /var/log/apex-scaffold
sudo systemctl daemon-reload
sudo systemctl enable --now apex-auth-service

# 4. 常用操作
systemctl status apex-auth-service        # 状态
journalctl -u apex-auth-service -f        # 日志
sudo systemctl restart apex-auth-service  # 重启（优雅停机，等待在途请求）
```

> 模板已配置 `Restart=on-failure` 崩溃重启和 `TimeoutStopSec=105` 停机等待；应用关闭总预算为 90 秒。
> 每个微服务使用独立单元、JAR、环境文件和日志路径，端口及数据库配置按服务设置。

---

<a id="5-部署方式二podman-单容器"></a>

## 5. 部署方式二：Podman 单容器

### 5.1 构建镜像

```bash
# 一个镜像只包含一个微服务；用 --build-arg SERVICE_MODULE 选择目标
# 默认档位 enterprise；可 --build-arg BUILD_PROFILE=basic|standard|enterprise
# 非网关服务必须同时传入端口参数：Containerfile 的默认值全部是 8080，不会按模块推导。
podman build -f Containerfile \
  --build-arg BUILD_PROFILE=enterprise \
  --build-arg SERVICE_MODULE=apex-gateway \
  -t apex-gateway:1.3.1 .
# 其余服务同理：apex-auth-service / apex-system-service / apex-workflow-service /
#              apex-office-service / apex-file-service
podman build -f Containerfile \
  --build-arg BUILD_PROFILE=enterprise \
  --build-arg SERVICE_MODULE=apex-auth-service \
  --build-arg SERVICE_PORT=8081 \
  --build-arg MANAGEMENT_PORT=8081 \
  --build-arg HEALTHCHECK_PORT=8081 \
  -t apex-auth-service:1.3.1 .
```

构建说明（Containerfile）：

- 多阶段构建：builder 阶段编译整个反应堆（`mvnd clean package -P<profile>`），runtime 仅含 JRE + 选中微服务的可执行 jar
- 业务端口 / 管理端口 / 健康检查端口均需通过 `--build-arg SERVICE_PORT/MANAGEMENT_PORT/HEALTHCHECK_PORT` 显式注入；
  三者默认都是 8080，未传时非网关镜像会探测错误端口（网关 8080，各服务见 §1）
- 非 root 用户 `appuser` 运行；时区默认 `Asia/Shanghai`（含 tzdata）
- 默认 JVM：生产级调优集（`-Xms512m -Xmx1024m -XX:+UseG1GC -XX:MaxGCPauseMillis=200` + OOM 堆转储/退出 + 滚动 GC 日志），可用 `JAVA_OPTS` 覆盖
- `HEALTHCHECK` 请求 `/actuator/health/readiness`，仅把容器标记为 healthy/unhealthy；
  自动重启由 Podman/Compose/systemd 的重启策略决定，`HEALTHCHECK` 本身不会重启进程

### 5.2 运行容器

```bash
podman run -d --name apex-auth-service \
  -p 8081:8081 \
  -e SPRING_PROFILES_ACTIVE=prod,enterprise \
  -e DB_HOST=<mysql-host> -e DB_PORT=3306 -e DB_NAME=apex_auth \
  -e DB_USERNAME=apex -e DB_PASSWORD='********' \
  -e REDIS_HOST=<redis-host> -e REDIS_PORT=6379 -e REDIS_PASSWORD='********' \
  -e JWT_SECRET='********' \
  -e NACOS_SERVER_ADDR=<nacos-host>:8848 \
  -e APEX_LOG_DIR=/app/logs \
  -v apex-logs:/app/logs \
  apex-auth-service:1.3.1
```

### 5.3 常用操作

```bash
podman ps                                 # 查看容器（STATUS 应为 Up (healthy)）
podman logs -f apex-auth-service          # 日志
podman exec -it apex-auth-service sh      # 进入容器排查
curl http://localhost:8081/actuator/health

# 停止 / 删除
podman stop apex-auth-service && podman rm apex-auth-service

# 升级：构建新镜像 → 重建容器
podman build -f Containerfile --build-arg SERVICE_MODULE=apex-auth-service -t apex-auth-service:1.4.0 .
podman stop apex-auth-service && podman rm apex-auth-service
podman run -d --name apex-auth-service ...（同上参数，镜像换 1.4.0）
```

> 容器内管理端口默认监听 `0.0.0.0`（镜像已设 `MANAGEMENT_SERVER_ADDRESS=0.0.0.0`），便于 Prometheus 抓取 `/actuator/prometheus`。
> 网关对外暴露 8080，其余服务一般无需对外暴露（经网关路由），调试时可暂映射。

---

<a id="6-部署方式三podman-compose-编排"></a>

## 6. 部署方式三：Podman Compose 编排

> 一键拉起「网关 + 6 个业务服务 + 查询聚合服务 + Nacos + MySQL + Redis（+可选 RocketMQ/Keycloak/前端）」，适合本地开发、测试环境与生产单机。
> 旧单体 `apex-scaffold` 服务已移除（见 `podman-compose.yml` 头部注释）。

<a id="61-一键启动完整微服务栈"></a>

### 6.1 一键启动完整微服务栈

```bash
# 1.（可选）按需生成环境变量文件（需先配置全部安全密钥，格式见 docs/05-operations/configuration.md）
cp .env.example .env

# 2. 构建离线 Java 21 工具并校验格式（启动前 security-key-check 复用同一 JAR）
source scripts/build/init-build-env.sh
mvnd -B -pl :apex-tools-deployment -am verify
bash scripts/utils/local-deployment.sh compose --env-file .env

# 发布包内的 security-key-check 默认挂载 tools/local-deployment.jar；
# 直接使用仓库源码启动 Compose 时，需指向模块产物：
export APEX_DEPLOY_TOOL_JAR="$PWD/apex-tools-parent/apex-tools-deployment/target/local-deployment.jar"

# 3. 先创建观测网络，再构建并启动应用栈
podman-compose -p apex-infra -f podman-compose.infra.yml up -d skywalking-oap skywalking-ui elasticsearch kibana filebeat
podman-compose -f podman-compose.yml up -d --build

# 4. 等待就绪（首次 MySQL 初始化约 30~60s；各微服务启动 60~90s）
podman-compose -f podman-compose.yml ps                 # mysql/redis/nacos 应为 healthy，各微服务 Up
podman-compose -f podman-compose.yml logs -f apex-gateway

# 5. 验证
curl http://localhost:8080/api/v1/health
# 浏览器打开 http://localhost:8082/doc.html（system-service 的 API 文档）
# 网关路由表不含 /doc.html、/swagger-ui/**、/v3/api-docs/**，不能假设网关代理 API 文档
```

> Compose 通过 `x-app-common-environment` 锚点把公共配置（安全密钥、Redis、Nacos、JWT、RocketMQ、Outbox、治理）注入所有微服务；各服务仅追加 `DB_NAME` 与 `SPRING_DATASOURCE_URL` 差异项。

默认服务清单：

| 服务 | 镜像 | 端口（宿主机） | 数据库 | 说明 |
|------|------|---------------|--------|------|
| apex-gateway | 本地构建 | `${APP_PORT:-8080}` | —（无库，Redis 限流） | 统一入口，无 DB |
| apex-auth-service | 本地构建 | `${AUTH_PORT:-8081}` | apex_auth | 认证/Token/会话 |
| apex-system-service | 本地构建 | `${SYSTEM_PORT:-8082}` / 管理 `${SYSTEM_MANAGEMENT_PORT:-8083}` | apex_system | 系统域 + 管理 8083 |
| apex-workflow-service | 本地构建 | `${WORKFLOW_PORT:-8093}` | apex_workflow | 工作流/审批 |
| apex-office-service | 本地构建 | `${OFFICE_PORT:-8084}` | apex_office | 组织/办公 |
| apex-file-service | 本地构建 | `${FILE_PORT:-8085}` | apex_file | 文件/文档 |
| apex-integration-service | 本地构建 | `${INTEGRATION_PORT:-8094}` / 管理 `${INTEGRATION_MANAGEMENT_PORT:-8097}` | apex_integration | 集成中心连接器/凭据 + 物联网设备/规则；MQTT 桥接默认关闭 |
| apex-job-service | 本地构建 | `${APEX_JOB_PORT:-8095}`（127.0.0.1 绑定） | —（无库，代理 system 九个任务接口） | 默认栈即启动（无 `profiles:`）；403 探针仅证明 HTTP 安全链存活，不证明上游 system 就绪 |
| apex-aggregation-service | 本地构建 | `${AGGREGATION_PORT:-8098}` | —（无库） | 只读跨源聚合（统一待办 /api/v2/todo），无数据库、无管理端口 |
| nacos | nacos/nacos-server:v2.4.3 | `${NACOS_PORT:-8848}` / gRPC `${NACOS_GRPC_PORT:-9848}` | — | 服务发现 + 配置中心 |
| mysql | mysql:9.0 | `${MYSQL_PORT:-3306}` | apex_system（引导库） | 各服务经 createDatabaseIfNotExist 自建独立库 |
| redis | redis:7-alpine | `${REDIS_PORT:-6379}` | — | AOF 持久化 |
| meilisearch | getmeili/meilisearch:v1.10 | `${MEILISEARCH_PORT:-7700}` | — | 全文搜索 |
| loki | grafana/loki:3.0.0 | `${LOKI_PORT:-3100}` | — | 日志聚合 |
| alloy | grafana/alloy:v1.0.0 | `${ALLOY_PORT:-12345}` | — | 日志采集 |
| apex-admin-web | apex-admin-web:`${TAG}`（预构建） | `${WEB_PORT:-80}` | — | 前端管理后台（可选） |

可选（--profile）：`rocketmq-namesrv` / `rocketmq-broker`（`--profile rocketmq`）、`keycloak`（`--profile keycloak`）、`apex-masking-service`（`--profile masking`，8096）、`xxl-job-admin`（`--profile xxl-job`，宿主 8099）、`seata-tc`（`--profile seata`）、`apex-knowledge-service`（`--profile knowledge`，8100）。

默认凭证（**仅限本地/测试，生产务必修改**）：

- MySQL：`root / apex-root`，各服务独立库（`MYSQL_ROOT_PASSWORD` 可覆盖；引导库默认 `apex_system`）
- Redis：密码 `apex-redis-password`（`REDIS_PASSWORD` 可覆盖；prod 配置强制要求非空）
- Nacos：默认启用（单机版，仅本地起步）；生产接入外部 Nacos 集群后注释 `nacos` 服务
- 加密密钥：`APEX_DATA_ENCRYPTION_KEY` / `SECURITY_ENCRYPTION_KEY` / `CONFIG_ENCRYPTION_KEY` / `APEX_CONFIG_ENCRYPTION_KEY` / `JWT_SECRET` 均为必填，缺失时 compose 拒绝渲染

<a id="62-生产部署流程"></a>

### 6.2 生产部署流程

```bash
# 1. 生成生产环境变量文件并逐项修改（密码 / 密钥 / 地址）
cp .env.example .env.prod
vim .env.prod     # 至少修改: MYSQL_ROOT_PASSWORD / DB_PASSWORD / REDIS_PASSWORD / JWT_SECRET /
                  #   APEX_DATA_ENCRYPTION_KEY / SECURITY_ENCRYPTION_KEY / CONFIG_ENCRYPTION_KEY /
                  #   APEX_CONFIG_ENCRYPTION_KEY
                  # 建议: APP_PORT / MYSQL_PORT / REDIS_PORT 按实际调整

# 2. 使用生产环境文件启动
podman-compose -f podman-compose.yml --env-file .env.prod up -d --build

# 3. 验证
podman-compose -f podman-compose.yml --env-file .env.prod ps
curl http://localhost:8080/api/v1/health
curl http://localhost:8083/actuator/health        # 系统服务管理端口
```

> `.env.prod` 请加入 `.gitignore`，避免密钥入库。

<a id="63-启用-rocketmq可选"></a>

### 6.3 启用 RocketMQ（可选）

企业版需要消息能力时，携带 profile 启动 RocketMQ（Namesrv + Broker），并注入 NameServer 与业务开关：

```bash
# .env 设置 name-server 与消费者开关
#   ROCKETMQ_NAMESERVER=rocketmq-namesrv:9876
#   APEX_MQ_CONSUMER_ENABLED=true
#   APEX_MQ_DLQ_ENABLED=true
#   APEX_MQ_DOMAIN_EVENT_ENABLED=true
# 携带 profile 启动
podman-compose -f podman-compose.yml --profile rocketmq --env-file .env up -d --build
```

> 口径分两层（2026-11-28 BE-066 校正）：yml 默认值为 `true`（`APEX_MQ_CONSUMER_ENABLED`/`APEX_MQ_DLQ_ENABLED`/`APEX_MQ_DOMAIN_EVENT_ENABLED` 未注入时），部署模板 `.env.example`/compose 公共锚点注入 `false` 作为部署层默认。未部署 RocketMQ 时请保持部署层注入的 `false`（或显式置 false）；生产启用消息能力时，必须显式注入可达的 `ROCKETMQ_NAMESERVER` 或 `ROCKETMQ_NAMESRV_ADDR`。

RocketMQ 控制台访问（可选）：

```bash
podman run -d --name rmq-console --network apex-scaffold_apex-network \
  -e JAVA_OPTS='-Drocketmq.namesrv.addr=rocketmq-namesrv:9876 -Dcom.rocketmq.sendMessageWithVIPChannel=false' \
  -p 8180:8080 docker.io/apacherocketmq/rocketmq-dashboard:latest
```

<a id="64-集成监控栈"></a>

### 6.4 集成监控栈

可观测性栈（Prometheus + Grafana + Zipkin）独立编排，与应用栈并存：

```bash
podman-compose -f config/monitoring/podman-compose.yml up -d

# 访问
# Grafana    http://localhost:3200   （admin / admin，宿主映射 3200:3000）
# Prometheus http://localhost:9090
# Zipkin     http://localhost:9411
```

抓取链路：Prometheus `scrape` 各微服务容器 `http://<服务名>:<管理端口>/actuator/prometheus`（compose 网络内主机名即服务名）。

> 本地基础设施全家桶（MySQL/Redis/Nacos/RocketMQ/Sentinel/Zipkin/Prometheus/Grafana）见 `podman-compose.infra.yml`，供 java -jar 直连开发复用；其 Nacos 宿主机端口映射为 `18848`/`19848`（本机 8848 被占用时），Redis 无密码（dev 配置默认）。两个编排栈勿同时使用（3306/6379 端口冲突）。

---

<a id="7-环境变量完整参考表"></a>

## 7. 环境变量完整参考表

> 所有变量均支持**环境变量 / podman run -e / compose environment / systemd EnvironmentFile** 注入。
> 命名遵循 Spring relaxed binding，大写 + 下划线。

### 7.1 数据源（微服务）

每个微服务使用**独立数据库**，通过统一变量指定（Compose 输出 `DB_NAME` 与 `SPRING_DATASOURCE_URL` 覆盖；java -jar/systemd 走 `DB_HOST/DB_PORT/DB_NAME`）：

| 变量 | 默认 | 说明 |
|------|------|------|
| `DB_HOST` | dev: `localhost`；prod: `127.0.0.1`（**必改**为真实地址） | 数据库主机 |
| `DB_PORT` | `3306` | 数据库端口 |
| `DB_NAME` | 各服务默认：`apex_auth` / `apex_system` / `apex_workflow` / `apex_office` / `apex_file`；网关无库 | 目标服务使用的库名 |
| `DB_USERNAME` | dev: `root`；prod: **空（必填）** | 数据库账号 |
| `DB_PASSWORD` | dev: `apex-root`；prod: **空（必填）** | 数据库密码 |
| `SPRING_DATASOURCE_URL` | 空 | 显式 JDBC URL（优先于 DB_HOST/DB_PORT/DB_NAME；Compose 使用 `jdbc:mysql://mysql:3306/<DB_NAME>?createDatabaseIfNotExist=true&...`） |
| `SPRING_DATASOURCE_USERNAME` | 同 DB_USERNAME | 数据源用户名（显式） |
| `SPRING_DATASOURCE_PASSWORD` | 同 DB_PASSWORD | 数据源密码（显式） |

> 各微服务 Flyway 迁移独立管理各自库（`apex_auth` 的 `classpath:db/migration` 等）。库名与所部署服务的 `DB_NAME` 一致。

### 7.2 必配变量（生产）

| 变量 | 默认 | 说明 |
|------|------|------|
| `SPRING_PROFILES_ACTIVE` | dev | 激活 profile，格式 `<环境>,<档位>`，如 `prod,enterprise` |
| `REDIS_HOST` | dev: `127.0.0.1`；prod: `127.0.0.1`（**必改**为真实地址） | Redis 地址 |
| `REDIS_PORT` | 6379 | Redis 端口 |
| `REDIS_PASSWORD` | dev: 无；prod: **空（必填）** | Redis 密码；**prod 配置强制非空** |
| `JWT_SECRET` | prod: **空（必填）** | JWT 签名密钥，**≥32 字节强随机**（prod 启动校验，不足即失败），生成：`openssl rand -base64 48` |
| `APEX_DATA_ENCRYPTION_KEY` | prod: **空（必填）** | Base64 32 字节数据加密密钥，`openssl rand -base64 32` |
| `SECURITY_ENCRYPTION_KEY` | prod: **空（必填）** | Base64 32 字节 Token 存储密钥，`openssl rand -base64 32` |
| `CONFIG_ENCRYPTION_KEY` | prod: **空（必填）** | UTF-8 恰好 32 字节公共配置密钥，`openssl rand -hex 16` |
| `APEX_CONFIG_ENCRYPTION_KEY` | prod: **空（必填）** | Base64 恰好 32 字节认证中心配置密钥，`openssl rand -base64 32` |

> 加密密钥、JWT 属`x-security-environment`锚点，Compose 启动前由 `security-key-check` 校验格式，缺失或非法即拒绝渲染。详见 [configuration.md](./configuration.md)。

### 7.3 Nacos（服务发现 + 配置中心）

| 变量 | 默认 | 说明 |
|------|------|------|
| `NACOS_CONFIG_ENABLED` | true | 配置中心开关；独立部署单服务可设 `false` |
| `NACOS_DISCOVERY_ENABLED` | true | 服务发现开关；独立部署单服务可设 `false` |
| `NACOS_REGISTER_ENABLED` | true | 是否注册实例；独立部署单服务可设 `false` |
| `NACOS_SERVER_ADDR` | Compose: `nacos:8848`；本地: `127.0.0.1:8848` | Nacos 地址 `host:port` |
| `NACOS_NAMESPACE` | Compose: `public` | 命名空间 ID（环境隔离） |

### 7.4 RocketMQ（可选）

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

### 7.5 License（商业授权）

| 变量 | 默认 | 说明 |
|------|------|------|
| `APEX_LICENSE_ENABLED` | true | License 校验总开关 |
| `APEX_LICENSE_FILE` | `classpath:license/license-basic.json` | 授权文件路径，支持 classpath / 文件系统 / 容器挂载 |
| `APEX_LICENSE_PUBLIC_KEY_FILE` | `classpath:license/public.key` | 验签公钥（默认随包，一般无需改） |

### 7.6 运行/运维

| 变量 | 默认 | 说明 |
|------|------|------|
| `JAVA_OPTS` | `-Xms512m -Xmx1024m -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:MetaspaceSize=256m -XX:MaxMetaspaceSize=512m -XX:+HeapDumpOnOutOfMemoryError -XX:+ExitOnOutOfMemoryError -Xlog:gc*:file=<logs>/gc.log:...` | JVM 调优参数 |
| `SERVER_PORT` | 网关 8080 | 业务端口 |
| `MANAGEMENT_SERVER_PORT` | 网关 8080；system 8083；integration 8097；其余同业务端口（aggregation 8098、knowledge 8100 等） | 管理端口（Actuator）；见 §8 探针端口表 |
| `MANAGEMENT_SERVER_ADDRESS` | 127.0.0.1 | 管理端口监听地址；容器/远程采集设 `0.0.0.0` |
| `APEX_LOG_DIR` | `./logs` | 日志根目录（见 [§9](#9-日志与监控)） |
| `TZ` | Asia/Shanghai | 时区 |
| `APEX_GOVERNANCE_DEPLOYMENT_MODE` | 代码/`.env` 默认 `auto`；Compose 注入默认 `compose` | 服务治理部署模式（`auto`/`compose`/`k8s`） |
| `APEX_GOVERNANCE_SHUTDOWN_TIMEOUT` | 90s | 统一优雅关停预算 |
| `APEX_REDIS_CLEANUP_ON_STARTUP` | true | 启动时是否清理 Redis 缓存 |
| `STORAGE_LOCAL_BASE_PATH` | yml 默认 `/root/data/apex/uploads`；Compose 固定 `/app/data/storage`；`.env` 模板值 `./data/storage` | 本地存储根目录（apex-file-service 文件上传） |
| `FILE_MAX_SIZE_BYTES` | 10485760 | 单文件最大字节数（文件域） |
| `MEILISEARCH_ENABLED` / `MEILISEARCH_URL` | yml 默认 `false` / `http://localhost:7700`；Compose 注入 `true` / `http://meilisearch:7700` | 全文搜索（系统服务） |
| `LOKI_ENABLED` / `LOKI_URL` | yml 默认 `false` / `http://localhost:3100`；Compose 注入 `true` / `http://loki:3100` | 日志查询（配合 Grafana） |

### 7.7 compose 编排变量（见 .env.example）

`BUILD_PROFILE` / `TAG` / `APP_PORT` / `AUTH_PORT` / `SYSTEM_PORT` / `SYSTEM_MANAGEMENT_PORT` / `WORKFLOW_PORT` / `OFFICE_PORT` / `FILE_PORT` / `WEB_PORT` / `NACOS_PORT` / `MYSQL_DATABASE` / `MYSQL_ROOT_PASSWORD` / `MYSQL_PORT` / `REDIS_PORT` 等，均为**编排层**变量，与应用配置解耦。

---

<a id="8-健康检查与就绪探针"></a>

## 8. 健康检查与就绪探针

| 端点 | 端口 | 说明 |
|------|------|------|
| `GET /api/v1/health` | 网关 8080 | 业务健康端点，返回 `code=0, data.status=UP`；外部负载均衡 / 前端探活可用 |
| `GET /actuator/health` | 各服务管理端口 | Spring Boot Actuator 聚合健康 |
| `GET /actuator/health/liveness` | 各服务管理端口 | 存活探针，仅包含 `livenessState` |
| `GET /actuator/health/readiness` | 各服务管理端口 | 就绪探针，包含 `readinessState`、DB 和 Redis；Containerfile HEALTHCHECK 使用此端点 |
| `GET /actuator/prometheus` | 各服务管理端口 | Prometheus 指标抓取端点 |

各服务探针端口（网关复用业务 8080；system/integration 走管理端口；其余同业务端口，2026-11-28 BE-066 补全）：

```
apex-gateway:8080  apex-auth-service:8081  apex-system-service:8083(管理)
apex-workflow-service:8093  apex-office-service:8084  apex-file-service:8085
apex-integration-service:8097(管理)  apex-aggregation-service:8098
```

验证：

```bash
curl -s http://localhost:8080/api/v1/health          # 经网关
curl -s http://localhost:8083/actuator/health/readiness   # 系统服务
# {"status":"UP","components":{"db":{"status":"UP"},...}}
```

---

<a id="9-日志与监控"></a>

## 9. 日志与监控

### 9.1 日志

日志根目录由 `APEX_LOG_DIR` 控制（默认 `<工作目录>/logs`）。每个微服务独立落盘：

```
logs/
├── apex-gateway.log             # 网关
├── apex-auth-service.log        # 认证服务
├── apex-system-service.log      # 系统服务
├── apex-workflow-service.log    # 工作流服务
├── apex-office-service.log      # 办公服务
├── apex-file-service.log        # 文件服务
├── error.log                    # 错误日志（ERROR 级别独立归档）
├── nacos/                       # Nacos 客户端日志（启用时）
└── rocketmq/                    # RocketMQ 客户端日志（启用时）
```

查看：

```bash
tail -f logs/<service>.log                  # java -jar 部署
podman-compose -f podman-compose.yml logs -f apex-<service>       # compose 部署
journalctl -u apex-<service> -f             # systemd 部署
```

### 9.2 指标与大盘

- Prometheus 抓取：`http://<服务名>:<管理端口>/actuator/prometheus`
- 一键监控栈：`podman-compose -f config/monitoring/podman-compose.yml up -d`
  - Grafana 大盘、Prometheus 告警规则、Blackbox 健康探针；compose 内 Loki/Alloy 已编排（§6.1）

---

<a id="10-license-商业授权部署"></a>

## 10. License 商业授权部署

- 随包内置**基础版授权**（`classpath:license/license-basic.json`），基础功能开箱即用
- 购买标准版/企业版后，卖方发放 `.json` 授权文件与验签公钥，部署时挂载并注入：

```bash
# java -jar / systemd
export APEX_LICENSE_FILE=/etc/apex-scaffold/license-enterprise.json
export APEX_LICENSE_PUBLIC_KEY_FILE=/etc/apex-scaffold/public.key   # 官方公钥（可选）

# Podman
podman run ... \
  -v /opt/apex-licenses:/licenses:ro \
  -e APEX_LICENSE_FILE=/licenses/license-enterprise.json \
  -e APEX_LICENSE_PUBLIC_KEY_FILE=/licenses/public.key \
  apex-auth-service:1.3.1

# Compose（在 .env 中配置后挂载卷）
#   APEX_LICENSE_FILE=/licenses/license-enterprise.json
#   volumes: - /opt/apex-licenses:/licenses:ro
```

- 未检测到有效 License 时进入**降级模式**：基础功能放行、商业功能拒绝并输出 WARN
- 发放工具：`scripts/license/generate-license.sh`；详见 [`docs/LICENSE_MANAGEMENT.md`](../07-commercial/license-management.md)

---

<a id="11-可选组件接入nacos--rocketmq--sentinel"></a>

## 11. 可选组件接入（Nacos / RocketMQ / Sentinel）

### 11.1 接入 Nacos 配置中心 + 服务发现

微服务拓扑默认启用；独立部署单服务或接入外部集群时注入：

```bash
export NACOS_CONFIG_ENABLED=true
export NACOS_DISCOVERY_ENABLED=true
export NACOS_REGISTER_ENABLED=true
export NACOS_SERVER_ADDR=10.0.0.10:8848
export NACOS_NAMESPACE=prod
```

配置加载顺序：`bootstrap.yml → Nacos 远端配置 → application.yml → application-{profile}.yml`；`@RefreshScope` / `@Value` 支持运行时热更新。

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

<a id="12-升级与回滚"></a>

## 12. 升级与回滚

### 12.1 升级步骤（三种方式通用）

1. **备份**：见 [§13](#13-备份与恢复)（各服务库 dump + 授权文件副本）
2. 构建新版本产物（或新镜像）
3. 替换部署（见下）
4. 验证：`/api/v1/health` UP、登录一次、抽查核心接口、观察各服务 `logs/error.log`
5. 如有失败回滚（见 12.3）

### 12.2 按部署方式替换

```bash
# java -jar（systemd）—— 以认证服务为例
sudo cp apex-auth-service-1.4.0.jar /opt/apex-scaffold/apex-auth-service.jar
sudo systemctl restart apex-auth-service

# Podman 单容器
podman build -f Containerfile --build-arg SERVICE_MODULE=apex-auth-service -t apex-auth-service:1.4.0 .
podman stop apex-auth-service && podman rm apex-auth-service
podman run -d --name apex-auth-service ...（同 §5.2 参数，镜像 1.4.0）

# Podman Compose
podman-compose -f podman-compose.yml build              # 用新代码重建受影响服务
podman-compose -f podman-compose.yml up -d              # 重建受影响容器
podman-compose -f podman-compose.yml ps
```

### 12.3 回滚

```bash
# java -jar：还原旧 jar 后重启
sudo cp /opt/apex-scaffold/backup/apex-auth-service-1.3.1.jar /opt/apex-scaffold/apex-auth-service.jar
sudo systemctl restart apex-auth-service

# Podman：用旧镜像标签重建容器
podman run -d --name apex-auth-service ... apex-auth-service:1.3.1

# Compose：改回旧 TAG 后重建
#   TAG=1.3.1（.env）→ podman-compose -f podman-compose.yml up -d --build
```

> ⚠️ 数据库迁移由各服务 Flyway 独立管理（各自 `classpath:db/migration`）。**回滚到旧版本时**，Flyway 仅校验 schema 兼容性；新增迁移如已执行，旧版本可能因 checksum/缺列校验失败。升级前务必 dump 各服务库，回滚时按需还原数据。

### 12.4 灰度发布（K8s 金丝雀）

K8s 场景支持按权重/请求头灰度：稳定版与金丝雀版并存，Nginx Ingress 将
10% 流量导入新版本，验证通过后逐步放量至全量切换，失败即时回滚。K8s 资产面向**网关 + 各微服务**。

```bash
./apex-tools-parent/apex-tools-build/bin/apex.sh deploy k8s apply --canary   # 先隔离部署，初始权重 0%
./apex-tools-parent/apex-tools-build/bin/apex.sh deploy k8s check-canary     # 复核端点后再人工调整权重
# 验证 → 调整权重（canary-by-weight: 50/100）→ 全量切换 → 删除金丝雀
# 失败 → 删除金丝雀 Ingress 即回滚（流量 100% 回稳定版）
```

---

<a id="13-备份与恢复"></a>

## 13. 备份与恢复

### 13.1 MySQL

每个微服务独立库，按库逐个备份（示例以 `apex_system` 为例）：

```bash
# 备份（容器场景；容器名以 podman ps 实际为准：
#   主栈 podman-compose 未固定 container_name，默认按项目名生成（如 apex-scaffold-mysql-1），
#   infra 栈固定为 apex-infra-mysql）
podman exec apex-scaffold-mysql-1 mysqldump -uroot -p'***' --single-transaction --routines \
  apex_system > apex_system_$(date +%Y%m%d).sql

# 恢复
podman exec -i apex-scaffold-mysql-1 mysql -uroot -p'***' apex_system < apex_system_20260828.sql

# java -jar 场景：直接使用 mysql 客户端 dump / restore，命令同上（无 podman exec）
```

### 13.2 Redis

```bash
# 容器数据卷备份（AOF 已开启）
podman run --rm -v apex-scaffold_redis-data:/data -v "$(pwd)":/backup \
  docker.io/library/alpine:latest tar czf /backup/redis-data.tar.gz -C /data .
```

### 13.3 其他

- `logs/`、`data/storage/`（本地存储文件）、`/etc/apex-scaffold/`（环境变量与 License）一并纳入备份策略

---

<a id="14-生产安全部署清单"></a>

## 14. 生产安全部署清单

- [ ] `JWT_SECRET` 使用 `openssl rand -base64 48` 生成，长度 ≥32 字节
- [ ] `APEX_DATA_ENCRYPTION_KEY` / `SECURITY_ENCRYPTION_KEY` / `CONFIG_ENCRYPTION_KEY` / `APEX_CONFIG_ENCRYPTION_KEY` 独立生成并安全保存（格式见 §7.2，Compose 由 `security-key-check` 校验）
- [ ] `DB_PASSWORD` / `MYSQL_ROOT_PASSWORD` / `REDIS_PASSWORD` 强密码，与默认值不同
- [ ] 接入外部 Nacos 集群（本地 `nacos` 单机服务仅用于起步）；确认 `NACOS_NAMESPACE` 按环境隔离
- [ ] `MANAGEMENT_SERVER_ADDRESS`：公网裸跑建议保持 `127.0.0.1` 或经防火墙仅放行监控网段；容器/内网采集再设 `0.0.0.0`
- [ ] 网关对外仅暴露 8080；各微服务管理端口不直接对外暴露（经网关路由）
- [ ] 管理端点按服务最小暴露（system 为 `health,info,metrics,prometheus,circuitbreakers`；gateway 为 `health,info,prometheus,gateway`；auth 为 `health,info,prometheus,circuitbreakers`；integration 为 `health,info,metrics,prometheus`；job/masking 更小），勿开启 `env/configprops`
- [ ] CORS：生产配置白名单（`cors.allowed-origins` 逗号分隔），非 `allow-all-origins`
- [ ] 商业授权文件以只读卷挂载（`:ro`），私钥/公钥不入镜像与仓库
- [ ] 确认文件域由 apex-file-service 独立提供（`FILE_MAX_SIZE_BYTES` / `STORAGE_*` 见 §7.6），并完成存储后端连通性校验
- [ ] 各服务库、日志目录、数据卷纳入备份；`.env.prod` 加入 `.gitignore`
- [ ] 通过 `curl /api/v1/health`（网关）与各服务 `/actuator/health/readiness` 确认 DB/Redis 均 UP
- [ ] 上线后观察各 `logs/error.log` 与 Grafana 大盘，确认告警规则生效

---

<a id="15-常见问题-faq"></a>

## 15. 常见问题 FAQ

**Q1 各服务启动报错：`Failed to configure a DataSource` / 连接超时？**
检查 `DB_HOST/DB_PORT/DB_NAME/USERNAME/PASSWORD`（或显式 `SPRING_DATASOURCE_URL/USERNAME/PASSWORD`）是否正确注入、MySQL 是否可达、库是否已创建（服务会自动 `createDatabaseIfNotExist`）。生产三项均为必填，空值会触发启动校验失败（安全兜底）。

**Q2 网关路由不到某服务（503 / lb:// 找不到实例）？**
微服务路由依赖 Nacos 服务发现：确认目标服务已启动并在 Nacos 注册（`curl http://localhost:8848/nacos/` 查看服务列表），且 `NACOS_SERVER_ADDR` 一致。未启用 Nacos 时需设 `NACOS_*_ENABLED=false` 并经 `spring.cloud.gateway.routes` 显式静态路由。

**Q3 启动时大量 Nacos 连接日志/卡顿？**
未接入 Nacos 时：设置 `NACOS_CONFIG_ENABLED=false`、`NACOS_DISCOVERY_ENABLED=false`、`NACOS_REGISTER_ENABLED=false` 后重启。

**Q4 使用普通 jar 启动报 `no main manifest attribute`？**
必须使用各微服务 `target/<模块>-1.3.1.jar`（spring-boot-maven-plugin 重打包的可执行 fat jar）。

**Q5 podman-compose 启动后某应用容器反复重启？**
`podman-compose -f podman-compose.yml logs <服务>` 查看原因；常见为 MySQL/Nacos 未就绪时应用已启动（`depends_on` 健康等待已配置，首次初始化较慢可等待 60~90s）、或安全密钥缺失 / `JWT_SECRET` 过短 / 加密密钥格式非法。

**Q6 系统服务管理端口（8083）无法从外部访问？**
默认监听 `127.0.0.1`；容器/远程采集需设 `MANAGEMENT_SERVER_ADDRESS=0.0.0.0`（镜像与 compose 已默认设置）。生产建议仅内网访问。

**Q7 启动失败：Redisson 无法连接 Redis / AUTH 报错？**
prod 配置强制 `REDIS_PASSWORD` 非空。请确认：① 已注入 `REDIS_PASSWORD`；② 该密码与 Redis 服务实际 `requirepass` 一致（compose 默认栈二者已联动）。无密码的 Redis 实例需设置密码或改配 non-prod profile。

**Q8 如何切换档位（如 basic → enterprise）？**
构建时改 `BUILD_PROFILE`（构建档位），运行 profile `SPRING_PROFILES_ACTIVE`（运行档位）需配套；商业档位需配套 License。注意 basic/standard 档沿用聚合包形态（Containerfile §注释），enterprise 按 gateway + 各 service 部署。

**Q9 端口冲突或如何调整端口？**
compose 改 `.env` 的 `APP_PORT/AUTH_PORT/SYSTEM_PORT/SYSTEM_MANAGEMENT_PORT/WORKFLOW_PORT/OFFICE_PORT/FILE_PORT/NACOS_PORT/MYSQL_PORT/REDIS_PORT`；java -jar 改 `SERVER_PORT` / `MANAGEMENT_SERVER_PORT`。

**Q10 数据库表没有自动创建？**
各服务 Flyway 在启动时执行各自的 `classpath:db/migration`。若日志出现 Flyway 报错，检查账号建表权限和已执行迁移 checksum；`flyway.clean-disabled=true` 防误删。

**Q11 中文字符乱码？**
MySQL 已强制 utf8mb4（compose 内置）；独立部署请确认库表字符集 `utf8mb4`，并在 JDBC URL 带 `characterEncoding=utf8`。

---

<a id="16-命令速查表"></a>

## 16. 命令速查表

| 操作 | java -jar（单服务） | Podman | Compose |
|------|-----------|--------|---------|
| 启动 | `APEX_SERVICE_MODULE=apex-auth-service ./scripts/deploy/service/start.sh start prod enterprise apex-auth-service` | `podman run ... apex-auth-service:1.3.1` | `podman-compose -f podman-compose.yml up -d --build` |
| 停止 | `./scripts/deploy/service/start.sh stop` | `podman stop apex-auth-service` | `podman-compose -f podman-compose.yml down` |
| 重启 | `./scripts/deploy/service/start.sh restart prod enterprise apex-auth-service` | `podman restart apex-auth-service` | `podman-compose -f podman-compose.yml restart` |
| 日志 | `./scripts/deploy/service/start.sh logs` | `podman logs -f apex-auth-service` | `podman-compose -f podman-compose.yml logs -f apex-auth-service` |
| 状态 | `./scripts/deploy/service/start.sh status` | `podman ps` | `podman-compose -f podman-compose.yml ps` |
| 健康 | `curl :8080/api/v1/health`（网关） | 同左 | 同左 |
| 删除数据卷 | — | — | `podman-compose -f podman-compose.yml down -v`（⚠️ 数据丢失） |

---

*文档维护：Apex Team ｜ 如有出入以代码为准；构建脚本见 `scripts/build/build.sh`，监控栈见 `config/monitoring/`，配置加密密钥说明见 [configuration.md](./configuration.md)。*
