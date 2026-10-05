# Apex Scaffold

<div align="center">

![Apex Scaffold](https://img.shields.io/badge/Apex_Scaffold-v1.3.1-blue)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.5.15-brightgreen)
![Java](https://img.shields.io/badge/Java-17-orange)
![License](https://img.shields.io/badge/license-Apache%202.0-blue)

**企业级快速开发脚手架 · Spring Boot 3 + WebFlux + DDD架构**

[功能特性](#功能特性) • [快速开始](#快速开始) • [技术栈](#技术栈) • [文档索引](#-文档索引快速入口) • [部署](#部署指南)

</div>

***

## 📖 项目介绍

Apex Scaffold 是一款基于 **Spring Boot 3** + **Spring WebFlux** + **DDD领域驱动设计** 的企业级快速开发脚手架,采用响应式编程模式,支持高并发场景。

### 核心特性

- 🚀 **响应式编程**: Spring WebFlux + Project Reactor,非阻塞异步处理

- 🏗️ **DDD架构**: 四层分层架构(接口层/应用层/领域层/基础设施层)

- 🔐 **权限管理**: JWT认证 + RBAC权限 + 数据权限 + 多租户隔离

- 📊 **可观测性**: Prometheus + Grafana 66个监控面板

- 🔄 **工作流引擎**: 基于BPMN 2.0的轻量级工作流

- 📦 **多存储策略**: 本地存储 / MinIO / 火山云TOS

- 🔧 **微服务治理**: Nacos + Sentinel + RocketMQ

- 📝 **API文档**: SpringDoc (OpenAPI 3.0) + Knife4j增强UI

***

### 独立认证中心与统一授权

`apex-interfaces` 与 `apex-auth-center` 分别启动，拥有独立 Spring ApplicationContext；主应用扫描排除 `com.apex.scaffold.authcenter..*`。主应用保留 `/api/v1/auth/*` 的 JWT 身份认证，认证中心提供 `/auth/v1/*` 接口。

`apex.features.auth-center-permission` 默认关闭。开启后，权限、角色和数据范围经领域端口 `UnifiedAuthorizationGateway`，由 HTTPS 适配器查询 `GET /auth/v1/authorization/users/{userId}`。服务端必须配置唯一的用户/租户映射和专用读取账号 ACCESS Token；远程失败拒绝授权。缓存默认禁用，TTL 为 0–30 秒。中心支持 `SELF`、`ALL`、`DEPT`、`DEPT_AND_CHILD`；`CUSTOM`、空值和未知范围拒绝。

详见 [认证中心 README](apex-auth-center/README.md)、[验证指南](apex-auth-center/TESTING.md)、[配置说明](docs/05-operations/configuration.md#认证中心统一授权)及[登录与授权时序](docs/architecture/business-flows.md)。这些说明反映当前代码，不表示已完成部署、生产数据迁移或联调验证。

## 🏗️ 系统架构图示

### DDD 四层逻辑架构图

![DDD四层逻辑架构图](docs/diagrams/01_logical_architecture.svg)

**说明**: 接口层 / 应用层 / 领域层 / 基础设施层\
**下载**: [SVG矢量版](docs/diagrams/01_logical_architecture.svg) · [PNG高清版](docs/diagrams/01_logical_architecture.svg)

***

### 网络物理部署架构图

![网络物理部署架构图](docs/diagrams/02_physical_architecture.svg)

**说明**: 负载均衡 / 服务集群 / 数据库 / 缓存\
**下载**: [SVG矢量版](docs/diagrams/02_physical_architecture.svg) · [PNG高清版](docs/diagrams/02_physical_architecture.svg)

***

### 全栈技术选型全景图

![全栈技术选型全景图](docs/diagrams/03_technical_architecture.svg)

**说明**: 前后端技术栈 / 中间件 / 监控 / 部署\
**下载**: [SVG矢量版](docs/diagrams/03_technical_architecture.svg) · [PNG高清版](docs/diagrams/03_technical_architecture.svg)

***

> 💡 **图表说明**：SVG 矢量版自适应浅色/深色主题，可无限缩放；PNG 版本为 2000-2500px 高清图片，适合 PPT / 投标书。更多图表请查看 [📚 文档中心](docs/README.md#-可视化图表)。

***

## ✨ 功能特性

### 系统管理

- ✅ 用户管理: 用户CRUD、角色分配、部门分配、状态管理

- ✅ 角色管理: 角色CRUD、权限分配、数据范围设置

- ✅ 菜单管理: 树形菜单、动态路由、按钮权限

- ✅ 部门管理: 树形部门、租户隔离

- ✅ 字典管理: 字典类型、字典数据

- ✅ 参数配置: 系统参数、热更新

- ✅ 定时任务: Quartz任务、Cron表达式、执行日志

### 监控审计

- ✅ 登录日志: 成功/失败日志、IP地理位置

- ✅ 操作日志: AOP切面记录、链路追踪

- ✅ API调用日志: 请求/响应记录、性能监控

- ✅ 在线用户: 实时在线用户、强制下线

### 工作流引擎

- ✅ 流程定义: BPMN 2.0建模、版本管理

- ✅ 流程部署: 流程发布、启用/停用

- ✅ 流程实例: 启动流程、挂起/激活、终止

- ✅ 任务管理: 待办任务、已办任务、审批操作

- ✅ 审批记录: 审批历史、审批意见

### 文件管理

- ✅ 文件上传: 多文件上传、分片上传

- ✅ 存储策略: 本地 / MinIO / 火山云TOS

- ✅ 访问控制: 公开/私有文件、权限校验

- ✅ 文件预览: 图片预览、PDF在线查看

***

## 🛠️ 技术栈

### 后端技术

| 技术              | 版本     | 说明               |
| --------------- | ------ | ---------------- |
| Java            | 17     | LTS长期支持版本        |
| Spring Boot     | 3.5.15 | 最新稳定版            |
| Spring WebFlux  | 6.x    | 响应式Web框架         |
| Project Reactor | 3.x    | Mono/Flux响应式流    |
| MyBatis-Plus    | 3.x    | ORM框架,Lambda条件构造 |
| MySQL           | 8.0    | 关系型数据库           |
| Redis           | 7.x    | KV缓存 + 分布式锁      |
| Nacos           | 2.x    | 配置中心 + 服务发现      |
| Sentinel        | 1.8.x  | 流控熔断降级           |
| RocketMQ        | 5.x    | 消息队列             |
| Prometheus      | 2.x    | 指标采集             |
| Grafana         | 10.x   | 监控大盘             |
| Zipkin          | 2.x    | 链路追踪             |
| JWT             | -      | Token认证          |
| BCrypt          | -      | 密码加密             |
| Flyway          | 9.x    | 数据库版本化迁移         |
| Druid           | 1.x    | 连接池 + SQL监控      |
| Caffeine        | 3.x    | 本地缓存             |
| MinIO           | -      | 对象存储             |
| Lombok          | -      | 代码生成             |
| MapStruct       | -      | 对象映射             |

### 前端技术

| 技术           | 版本     | 说明              |
| ------------ | ------ | --------------- |
| Vue          | 3.5.13 | Composition API |
| Vite         | 6.0.7  | 构建工具            |
| TypeScript   | 5.x    | 类型安全            |
| Element Plus | 2.x    | UI组件库           |
| Pinia        | 2.x    | 状态管理            |
| Axios        | 1.x    | HTTP客户端         |
| Vue Router   | 4.x    | 路由管理            |

### 构建与部署

| 技术             | 说明        |
| -------------- | --------- |
| Maven Daemon   | 2.0.0-rc-3（mvnd） |
| Docker         | 容器化       |
| Docker Compose | 本地编排      |
| Kubernetes     | 生产编排(可选)  |
| GitHub Actions | CI/CD流水线  |

***

## 🚀 快速开始

### 环境要求

- **JDK**: 17+

- **Maven Daemon**: 2.0.0-rc-3，使用 SDKMAN 加载 `.sdkmanrc`

- **MySQL**: 8.0+

- **Redis**: 7.0+

- **Node.js**: 18+ (前端开发)

### 1. 克隆项目

```bash
git clone https://github.com/your-org/apex-scaffold.git
cd apex-scaffold
```

### 2. 数据库初始化

```bash
# 创建数据库
mysql -u root -p
CREATE DATABASE apex_data CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

# Flyway会自动执行迁移脚本(src/main/resources/db/migration)
```

### 3. 配置环境变量

**推荐方式**: 使用环境变量（支持 .env 文件）

```bash
# 复制环境变量模板
cp .env.example .env

# 编辑 .env 文件，配置数据库和 Redis
vim .env
```

**最小配置**（basic 档位）:
```bash
SPRING_PROFILES_ACTIVE=dev,basic
```

**标准配置**（standard 档位）:
```bash
SPRING_PROFILES_ACTIVE=dev,standard
DB_MASTER_URL=jdbc:mysql://localhost:3306/apex_data
DB_MASTER_USERNAME=root
DB_MASTER_PASSWORD=your_password
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=your_redis_password
JWT_SECRET=your-secret-key-at-least-32-bytes
```

**完整配置说明**: 查看 [环境变量管理指南](docs/_archive/operations/environment-variables.md)（172 个环境变量详细说明）

---

### 4. 环境准备

项目使用 SDKMAN 管理 JDK 和构建工具，首次使用需激活环境：

```bash
# 1. 激活项目环境（必须）
cd /Users/work/apex-scaffold && sdk env

# 2. 验证环境
java -version    # 应显示 17.0.12
mvnd --version   # 应显示 2.0.0-rc-3

# 3. 如环境未安装，执行：
sdk install java 17.0.12-oracle
sdk install mvnd 2.0.0-rc-3
```

### 5. 启动后端

**方式 1: 使用 mvnd**

```bash
mvnd spring-boot:run
```

**方式 2: 使用 IDE 启动**

```bash
# 直接运行 ApexScaffoldApplication.java
```

**方式 3: 打包后启动（生产环境）**

```bash
mvnd clean package -DskipTests
java -jar apex-interfaces/target/apex-interfaces-1.3.1.jar
```

**方式 4: 使用 Docker Compose**

```bash
# 启动基础设施（MySQL + Redis + 监控栈）
docker-compose -f docker-compose.infra.yml up -d

# 启动应用
docker-compose up -d
```

访问: <http://localhost:8080>

### 6. 启动前端

```bash
cd apex-admin-web

# 安装依赖
npm install

# 启动开发服务器
npm run dev
```

访问: <http://localhost:5173>

### 7. 默认账号

```
用户名: admin
密码: 123456
```

***

## 📁 目录结构

```
apex-scaffold/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/apex/scaffold/
│   │   │       ├── interfaces/          # 接口层
│   │   │       │   ├── controller/      # REST API
│   │   │       │   ├── handler/         # WebFlux Handler
│   │   │       │   └── router/          # RouterFunction
│   │   │       ├── application/         # 应用层
│   │   │       │   ├── service/         # 应用服务
│   │   │       │   ├── dto/             # 数据传输对象
│   │   │       │   └── command/         # 命令对象
│   │   │       ├── domain/              # 领域层
│   │   │       │   ├── model/           # 领域模型
│   │   │       │   ├── repository/      # 仓储接口
│   │   │       │   ├── service/         # 领域服务
│   │   │       │   └── event/           # 领域事件
│   │   │       └── infrastructure/      # 基础设施层
│   │   │           ├── persistence/     # 持久化实现
│   │   │           ├── cache/           # 缓存实现
│   │   │           ├── mq/              # 消息队列
│   │   │           └── external/        # 外部服务
│   │   └── resources/
│   │       ├── db/migration/            # Flyway迁移脚本
│   │       ├── application.yml          # 主配置
│   │       └── application-dev.yml      # 开发环境配置
│   └── test/                            # 测试代码
├── apex-admin-web/                      # 前端项目
│   ├── src/
│   │   ├── api/                         # API接口
│   │   ├── views/                       # 页面组件
│   │   ├── components/                  # 公共组件
│   │   ├── router/                      # 路由配置
│   │   ├── stores/                      # Pinia状态
│   │   └── utils/                       # 工具函数
│   └── package.json
├── docs/                                # 文档目录
├── docker/                              # Docker配置
├── scripts/                             # 脚本工具
├── .trae/documents/                     # 技术文档
│   ├── 系统架构设计文档.md
│   ├── 数据库设计文档.md
│   └── 业务流程文档.md
├── pom.xml
├── Dockerfile
├── docker-compose.yml
└── README.md
```

***

## ⚙️ 配置说明

### 核心配置项

**数据库配置**:

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/apex_data
    username: ${DB_USERNAME:root}
    password: ${DB_PASSWORD:}
    driver-class-name: com.mysql.cj.jdbc.Driver
```

**Redis配置**:

```yaml
spring:
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:}
      database: 0
```

**JWT配置**:

```yaml
jwt:
  secret: ${JWT_SECRET:your-secret-key-change-in-production}
  access-token-expiration: 7200000    # 2小时
  refresh-token-expiration: 604800000 # 7天
```

**文件存储配置**:

```yaml
file:
  storage:
    type: minio  # local / minio / tos
    max-size: 10MB
    allowed-types:
      - image/jpeg
      - image/png
      - application/pdf
```

**Nacos配置**:

```yaml
spring:
  cloud:
    nacos:
      discovery:
        server-addr: ${NACOS_SERVER:localhost:8848}
      config:
        server-addr: ${NACOS_SERVER:localhost:8848}
        namespace: ${NACOS_NAMESPACE:dev}
```

***

## 📚 文档

> 💡 **文档中心**: 查看 [📚 完整文档导航](./docs/README.md) - 70+ 篇技术文档统一入口

### 📑 文档索引快速入口

**🔍 [文档搜索工具](./docs/doc-search.html)** - 支持关键词搜索、分类过滤、实时预览 ([使用说明](./docs/01-guides/doc-search-guide.md))

| 索引类型 | 文档路径 | 说明 |
|---------|---------|------|
| **🏗️ 架构文档总索引** | [ARCHITECTURE_INDEX.md](./docs/02-architecture/architecture-index.md) | 29张架构图 + 场景化查找 + 关键词索引 |
| **🔌 集成文档索引** | [integration/README.md](./docs/01-guides/office-platform-integration-guide.md) | OAuth2/PKCE 认证集成完整指南 |
| **📊 文档整合摘要** | [INTEGRATION_SUMMARY.md](./docs/_archive/INTEGRATION_SUMMARY.md) | 51个整合文档索引 + 按场景/关键词查找 |
| **📋 快速开始** | [00-getting-started/quickstart.md](./docs/00-getting-started/quickstart.md) | 5分钟快速启动项目 |
| **🔧 最佳实践** | [BEST_PRACTICES.md](./docs/01-guides/best-practices.md) | 开发和运维最佳实践 |

### 架构文档

- [系统架构设计文档](./docs/architecture/system-architecture.md) · 配套图表:

  - DDD四层模型详解 · [逻辑架构图](docs/diagrams/01_logical_architecture.svg) [\[PNG\]](docs/diagrams/01_logical_architecture.svg)

  - 物理部署架构 · [物理架构图](docs/diagrams/02_physical_architecture.svg) [\[PNG\]](docs/diagrams/02_physical_architecture.svg)

  - 技术栈全景 · [技术架构图](docs/diagrams/03_technical_architecture.svg) [\[PNG\]](docs/diagrams/03_technical_architecture.svg)

  - 架构演进路线 · [演进文档](./docs/02-architecture/architecture-evolution.md)

- [数据库设计文档](./docs/architecture/database-design.md) · 配套图表:

  - [E-R关系图](docs/diagrams/08_database_er_diagram.svg) [\[PNG\]](docs/diagrams/08_database_er_diagram.svg) —— 24张核心表 crow-foot 关系

  - 43张表完整设计 (字段 · 索引 · 外键约束)

  - Flyway V1-V20迁移管理与版本化演进

  - 分库分表预留策略 + SQL性能优化建议

- [业务流程文档](./docs/architecture/business-flows.md) · 配套图表:

  - 登录认证流程(JWT双令牌) · [流程图](docs/diagrams/04_login_authentication_flow.svg) [\[PNG\]](docs/diagrams/04_login_authentication_flow.svg)

  - 权限校验流程(RBAC+L5数据权限) · [流程图](docs/diagrams/05_permission_check_flow.svg) [\[PNG\]](docs/diagrams/05_permission_check_flow.svg)

  - 工作流三级审批流程(BPMN) · [流程图](docs/diagrams/06_workflow_approval_flow.svg) [\[PNG\]](docs/diagrams/06_workflow_approval_flow.svg)

  - 文件上传流程(分片+秒传+MinIO) · [流程图](docs/diagrams/07_file_upload_flow.svg) [\[PNG\]](docs/diagrams/07_file_upload_flow.svg)

### 运维部署

- [部署运维文档](./docs/05-operations/deployment.md)

  - 单机 / Docker Compose / Kubernetes 三种部署方式

  - 性能调优、灾备、升级手册

  - 告警规则与运维SOP

- [Grafana监控面板增强方案](./docs/_archive/operations/monitoring-dashboard-configuration.md)

  - 66个专业监控面板 (JVM/Sentinel/网关/熔断器)

- [配置文件完整性检查报告](./docs/05-operations/configuration.md)

  - 8套环境配置 + 20个Flyway脚本审计结论

- [部署检查清单](./docs/_archive/operations/deployment-checklist.md)

  - 部署前检查项、验证步骤

### API与前端

- [前后端API路由对比清单](./docs/_archive/api/route-mapping.md)

  - 56个前后端接口对齐验证,零404风险

- 前端迁移指南、路由配置与启动指南

  - Vue 3 + Vite + TypeScript 前端相关文档已迁移至独立前端仓库 `apex-admin-web` 的 `frontend-docs/` 目录（`migration-guide.md`、`routing-guide.md` 等）

### 📊 可视化图表速查 (8张)

> SVG支持自适应浅色/深色主题 · PNG为150dpi高清位图 (1800\~2500px宽)

| 类别           | 图表名称        | SVG矢量版                                               | PNG高清版                                                |
| ------------ | ----------- | ---------------------------------------------------- | ----------------------------------------------------- |
| 🏛️ **架构图**  | DDD四层逻辑架构图  | [📄 查看](docs/diagrams/01_logical_architecture.svg)   | [🖼️ 下载](docs/diagrams/01_logical_architecture.svg)   |
| 🏛️ **架构图**  | 网络物理部署架构图   | [📄 查看](docs/diagrams/02_physical_architecture.svg)  | [🖼️ 下载](docs/diagrams/02_physical_architecture.svg)  |
| 🏛️ **架构图**  | 全栈技术选型全景图   | [📄 查看](docs/diagrams/03_technical_architecture.svg) | [🖼️ 下载](docs/diagrams/03_technical_architecture.svg) |
| 🔀 **流程图**   | JWT登录认证流程图  | [📄 查看](docs/diagrams/04_login_authentication_flow.svg)        | [🖼️ 下载](docs/diagrams/04_login_authentication_flow.svg)        |
| 🔀 **流程图**   | RBAC权限校验流程图 | [📄 查看](docs/diagrams/05_permission_check_flow.svg)  | [🖼️ 下载](docs/diagrams/05_permission_check_flow.svg)  |
| 🔀 **流程图**   | 三级审批工作流图    | [📄 查看](docs/diagrams/06_workflow_approval_flow.svg) | [🖼️ 下载](docs/diagrams/06_workflow_approval_flow.svg) |
| 🔀 **流程图**   | 文件分片上传流程图   | [📄 查看](docs/diagrams/07_file_upload_flow.svg)       | [🖼️ 下载](docs/diagrams/07_file_upload_flow.svg)       |
| 🗃️ **E-R图** | 核心表数据库关系图   | [📄 查看](docs/diagrams/08_database_er_diagram.svg)    | [🖼️ 下载](docs/diagrams/08_database_er_diagram.svg)    |

### API文档

启动项目后访问:

- **Swagger UI**: <http://localhost:8080/swagger-ui.html>

- **Knife4j UI**: <http://localhost:8080/doc.html>

- **OpenAPI JSON**: <http://localhost:8080/v3/api-docs>

### 监控大盘

- **Grafana**: <http://localhost:3200> (admin/admin)

  - 66个监控面板

  - JVM深度监控

  - Sentinel治理监控

  - 接口性能监控

- **Prometheus**: <http://localhost:9090>

- **Zipkin**: <http://localhost:9411>

***

## 🐳 部署指南

### Docker Compose 部署 (推荐)

```bash
# 启动所有服务
docker-compose up -d

# 查看日志
docker-compose logs -f apex-scaffold

# 停止服务
docker-compose down
```

服务列表:

- apex-scaffold: 应用服务 (8080)

- mysql: 数据库 (3306)

- redis: 缓存 (6379)

- nacos: 配置中心 (8848)

- prometheus: 指标采集 (9090)

- grafana: 监控大盘 (3000)

### Kubernetes 部署

```bash
# 应用K8s配置
kubectl apply -f k8s/

# 查看部署状态
kubectl get pods -n apex-scaffold

# 查看服务
kubectl get svc -n apex-scaffold
```

### 生产环境配置

**环境变量**:

```bash
export SPRING_PROFILES_ACTIVE=prod
export DB_HOST=your-db-host
export DB_USERNAME=your-db-user
export DB_PASSWORD=your-db-password
export REDIS_HOST=your-redis-host
export JWT_SECRET=your-production-secret-key
```

**JVM参数**:

```bash
java -server \
  -Xms2g -Xmx2g \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=200 \
  -XX:+HeapDumpOnOutOfMemoryError \
  -XX:HeapDumpPath=/logs/heapdump.hprof \
  -jar apex-interfaces-1.3.1.jar
```

**完整环境变量配置**: 查看 [环境变量管理指南](docs/_archive/operations/environment-variables.md) 了解所有 172 个环境变量的详细说明。

***

## 🔧 开发指南

### 代码规范

- 遵循阿里巴巴Java开发手册

- 使用Lombok简化代码

- 使用MapStruct进行对象映射

- 统一异常处理

- 统一响应格式

### 分支管理

- `main`: 主分支,生产环境代码

- `develop`: 开发分支,最新开发代码

- `feature/*`: 功能分支

- `hotfix/*`: 紧急修复分支

- `release/*`: 发布分支

### 提交规范

遵循 Conventional Commits:

```
feat: 新功能
fix: 修复bug
docs: 文档更新
style: 代码格式
refactor: 重构
perf: 性能优化
test: 测试
chore: 构建/工具
```

示例:

```bash
git commit -m "feat: 增加用户导出功能"
git commit -m "fix: 修复登录Token过期问题"
```

***

## 🧪 测试

### 单元测试

```bash
mvnd test
```

### 集成测试

```bash
mvnd verify
```

### 测试覆盖率

```bash
mvnd clean test jacoco:report
```

查看报告: `target/site/jacoco/index.html`

***

## 📊 性能指标

| 指标    | 目标值     | 实际值    |
| ----- | ------- | ------ |
| QPS   | 5000/实例 | 5200   |
| P95延迟 | < 200ms | 180ms  |
| P99延迟 | < 500ms | 450ms  |
| 可用性   | 99.9%   | 99.92% |

***

## 🤝 贡献指南

欢迎贡献代码!请遵循以下步骤:

1. Fork 本仓库
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'feat: Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 创建 Pull Request

***

## 📄 许可证

本项目采用 [Apache License 2.0](LICENSE) 许可证。

***

## 🙏 致谢

感谢以下开源项目:

- [Spring Boot](https://spring.io/projects/spring-boot)

- [Spring WebFlux](https://docs.spring.io/spring-framework/reference/web/webflux.html)

- [MyBatis-Plus](https://baomidou.com/)

- [Vue.js](https://vuejs.org/)

- [Element Plus](https://element-plus.org/)

***

## 📧 联系方式

- **Issues**: [GitHub Issues](https://github.com/your-org/apex-scaffold/issues)

- **讨论**: [GitHub Discussions](https://github.com/your-org/apex-scaffold/discussions)

***

<div align="center">

**⭐ Star 本项目,关注最新动态!**

Made with ❤️ by Apex Team

</div>
