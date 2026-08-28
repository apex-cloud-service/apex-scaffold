# Apex Scaffold - 企业级后端开发脚手架

[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/java-17+-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/spring--boot-3.5-brightgreen.svg)](https://spring.io/projects/spring-boot)

## 📖 简介

Apex Scaffold 是一个面向中小型企业的**轻量级企业级后端脚手架**,基于 Spring Boot 3 + WebFlux 响应式架构,提供开箱即用的基础设施组件,帮助团队快速启动新项目,减少重复开发成本。

### ✨ 核心特性

- 🏗️ **DDD分层架构** - 五层架构 + 纯 WebFlux 函数式端点（无 servlet 依赖）
- 🖥️ **开箱即用的系统管理** - 菜单/字典/参数/定时任务/登录日志管理 + 管理后台控制台
- ⚙️ **代码生成器** - 按表结构一键生成 DDD 五层代码（entity→Handler 全链路 + 迁移 + 测试）
- 🔐 **安全认证** - JWT + Spring Security + RBAC + 图形验证码 + 登录审计（等保友好）
- 💾 **多级缓存** - Redisson + Caffeine 二级缓存策略
- ⚡ **高可用保障** - Sentinel限流熔断 + Resilience4j容错
- 📨 **消息队列** - RocketMQ封装,支持异步解耦
- 📊 **可观测性** - Micrometer + Prometheus 指标,Zipkin(Brave)链路追踪,TraceId 贯穿日志
- 📝 **API文档** - SpringDoc OpenAPI + Knife4j可视化文档（/doc.html）
- 🔍 **SQL 日志** - dev 环境 P6Spy 格式化 SQL 日志（含参数值）；prod 用 Druid stat 监控
- 💼 **商业保护** - 三档版本(基础/标准/企业)构建 + License 授权校验 + 双轨许可证
- 🐳 **容器化部署** - Docker Compose一键启动完整环境
- 🌍 **国际化支持** - 内置i18n框架,支持多语言切换

### 🎯 适用场景

- ✅ 企业内部管理系统(CRM/ERP/OA等)
- ✅ SaaS平台后端服务
- ✅ 微服务架构中的独立服务
- ✅ 需要快速验证的MVP项目
- ❌ 不适合:超大型分布式系统、实时交易系统、IoT设备接入

## 🚀 快速开始

### 前置要求

- JDK 17+（推荐 sdkman 管理：`sdk install java 17.0.12-oracle`）
- mvnd 3.9+（Maven Daemon，构建加速；`sdk install mvnd`。传统 `mvn` 亦可，命令等价）
- Docker & Docker Compose (可选,用于容器化部署)

### 方式一:本地开发

```bash
# 1. 克隆项目
git clone https://github.com/your-org/apex-scaffold.git
cd apex-scaffold

# 2. 编译项目（统一构建环境：sdkman JDK + mvnd；可用 ./scripts/build/build.sh）
source ~/.sdkman/bin/sdkman-init.sh   # 加载 sdkman（JDK + mvnd）
mvnd clean package -DskipTests

# 3. 启动应用
mvnd spring-boot:run -pl apex-interfaces

# 4. 访问 API 文档
# Swagger UI: http://localhost:8080/swagger-ui.html
# Knife4j: http://localhost:8080/doc.html
```

### 方式二:Docker一键部署

```bash
# 启动所有服务(应用 + MySQL + Redis)
docker compose up -d --build

# 查看日志
docker compose logs -f apex-scaffold

# 停止服务
docker compose down
```

> 💡 **本地全部中间件一键部署**（MySQL/Redis/Nacos/RocketMQ/Sentinel/Zipkin/Prometheus/Grafana）：
> `docker compose -f docker-compose.infra.yml up -d`

### 方式三:java -jar 直接部署

```bash
# 1. 构建（-Pbasic / -Pstandard / -Penterprise）
./scripts/build/build.sh -Penterprise

# 2. 注入环境变量并启动（dev 环境示例；详见 docs/DEPLOYMENT.md）
export SPRING_PROFILES_ACTIVE=dev,enterprise
export NACOS_CONFIG_ENABLED=false NACOS_DISCOVERY_ENABLED=false NACOS_REGISTER_ENABLED=false
java -jar apex-interfaces/target/apex-interfaces-*-executable.jar
```

> 📖 **完整部署指南（三种方式 × dev/it/prod 全环境、环境变量、systemd、监控、升级回滚）请阅读 [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md)**

### 方式四:使用示例模块(⭐推荐)

我们提供了完整的示例模块(`apex-example`),以"用户管理"为业务载体,演示脚手架核心能力的标准用法。**这是新用户学习的最佳起点:**

```bash
# 0. 准备独立库（example 使用 apex_example 库，与主应用隔离）
mysql -h 127.0.0.1 -u root -papex-root -e "CREATE DATABASE IF NOT EXISTS apex_example DEFAULT CHARACTER SET utf8mb4;"

# 查看示例代码
cd apex-example/src/main/java/com/apex/example

# 运行示例（统一构建环境：source ~/.sdkman/bin/sdkman-init.sh）
mvnd spring-boot:run -pl apex-example

# 访问API文档
open http://localhost:8080/doc.html
```

**示例模块演示的能力**（均有集成测试覆盖）:

| 能力 | 示例位置 | 说明 |
|------|----------|------|
| WebFlux 函数式路由 | `config/ExampleRouterConfig` | RouterFunction 端点注册 |
| 参数校验 | `dto/CreateUserRequest` | Jakarta Validation 注解 + Handler `validateAndThrow` |
| 统一响应/错误码 | `service/ExampleUserService` | `Result` + `BusinessException` + `ResultCode`（4001/4002） |
| 分页查询 | `service/ExampleUserService#listUsers` | 统一 `PageResponse` 模型 + 手写 COUNT/LIMIT 分页 |
| 数据脱敏 | `service/ExampleUserService#maskPhone` | 手机号 `138****8000` 展示 |
| 逻辑删除 | `service/ExampleUserService#deleteUser` | MyBatis-Plus `deleteById`（deleted=1） |
| 商业功能授权 | `handler/ExampleUserHandler#premiumReport` | `@FeatureGate` + License 授权控制 |
| 独立应用配置 | `resources/application.yml` | dynamic-datasource / Flyway / Redis / CORS |

**重要提示**:
- ✅ **推荐使用**: `apex-example` 模块是纯净的教学示例,展示了从零开始使用脚手架的标准方式,可直接作为您业务的起步模板
- 📖 详细说明请查看: [apex-domain说明](apex-domain/README.md) | [apex-infrastructure说明](apex-infrastructure/README.md)

## 📦 项目结构

```
apex-scaffold/
├── apex-common/              # 公共模块(工具类、异常、统一响应)
├── apex-Domain/              # 领域层(聚合根、仓储接口、领域服务)
├── apex-Application/         # 应用层(用例编排、DTO转换)
├── apex-Infrastructure/      # 基础设施层(技术实现、第三方集成)
├── apex-Interfaces/          # 接口层(HTTP端点、安全配置)
├── apex-Example/             # 示例模块(展示如何使用脚手架)
├── docs/                     # 文档目录
│   ├── QUICK_START.md        # 快速开始指南
│   ├── ARCHITECTURE.md       # 架构设计说明
│   ├── CONFIGURATION.md      # 配置项详解
│   └── BEST_PRACTICES.md     # 最佳实践
├── scripts/                  # 脚本工具
├── config/                   # 配置文件模板
├── docker-compose.yml        # Docker编排文件
├── Dockerfile                # Docker镜像构建文件
├── pom.xml                   # Maven父POM
├── LICENSE                   # MIT许可证
└── README.md                 # 项目说明文档
```

## 🛠️ 核心功能清单

| 功能模块 | 说明 | 可配置开关 |
|---------|------|-----------|
| JWT认证 | 基于Token的身份验证 | ✅ |
| RBAC权限 | 角色-based访问控制 | ✅ |
| 多级缓存 | Redis + Caffeine二级缓存 | ✅ |
| 限流熔断 | Sentinel + Resilience4j | ✅ |
| 消息队列 | RocketMQ生产者/消费者封装 | ✅ |
| 文件存储 | Local/MinIO/S3抽象 | ✅ |
| 链路追踪 | Micrometer + Zipkin | ✅ |
| 操作日志 | AOP切面记录用户操作 | ✅ |
| 监控告警 | Prometheus指标暴露 | ✅ |
| API文档 | OpenAPI 3.0自动生成 | ✅ |
| 国际化 | MessageSource多语言支持 | ✅ |
| 全局异常 | 统一异常处理与响应格式 | ✅ |

## 📚 文档

- [快速开始指南](docs/QUICK_START.md) - 5分钟上手教程
- [架构设计说明](docs/ARCHITECTURE.md) - DDD分层架构详解
- [配置项详解](docs/CONFIGURATION.md) - 所有配置项说明
- [最佳实践](docs/BEST_PRACTICES.md) - 常见问题与解决方案
- [API参考](http://localhost:8080/doc.html) - 在线API文档(Knife4j)

## 🔧 配置说明

主要配置文件位于 `apex-interfaces/src/main/resources/application.yml`:

```yaml
# JWT配置
jwt:
  secret: ${JWT_SECRET:your-secret-key-change-in-production}
  expiration: 7200000  # 2小时(毫秒)

# 数据库配置（dynamic-datasource，example 使用独立库 apex_example，需先 CREATE DATABASE apex_example）
spring:
  datasource:
    dynamic:
      datasource:
        master:
          url: jdbc:mysql://localhost:3306/apex_example?useSSL=false&serverTimezone=Asia/Shanghai
          username: root
          password: ${DB_PASSWORD:apex-root}

# Redis配置
  data:
    redis:
      host: localhost
      port: 6379

# 缓存配置
cache:
  caffeine:
    max-size: 1000
    expire-after-write: 300s  # 5分钟
```

更多配置项请参考 [配置项详解](docs/CONFIGURATION.md)。

## 🧪 测试

```bash
# 运行单元测试（全量验证建议用 mvn：mvnd 并行偶发类加载问题，见 .workbuddy/memory）
mvnd test

# 生成覆盖率报告
mvnd jacoco:report

# 查看报告
open target/site/jacoco/index.html
```

## 🤝 贡献

欢迎提交 Issue 和 Pull Request!

1. Fork 本仓库
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 开启 Pull Request

## 💰 商业版本

Apex Scaffold 提供三个版本,满足不同规模企业的需求:

| 版本 | 价格 | 适用场景 | 技术支持 |
|------|------|---------|---------|
| **基础版** | 免费开源 | 个人项目、小型应用 | 社区支持 |
| **标准版** | ¥9,999/年 | 中型企业、创业公司 | 邮件支持 (5工作日响应) |
| **企业版** | ¥49,999/年 | 大型企业、高并发系统 | 专属客服 (2小时响应) |

### 版本对比

详细功能对比请查看 [商业版本说明](docs/COMMERCIAL_VERSIONS.md)

#### 基础版 (Basic)
- ✅ MIT License 完全开源
- ✅ JWT认证、统一响应、参数校验
- ✅ WebFlux函数式编程、MyBatis-Plus
- ❌ RBAC权限、缓存、消息队列、监控

#### 标准版 (Standard)
- ✅ 包含基础版全部功能
- ✅ RBAC权限管理、Redis缓存
- ✅ Prometheus监控、微信SDK集成
- ✅ 对象存储、邮件短信服务
- ✅ Excel导入导出

#### 企业版 (Enterprise)
- ✅ 包含标准版全部功能
- ✅ RocketMQ消息队列、Zipkin链路追踪
- ✅ Sentinel限流熔断、Resilience4j容错
- ✅ 分布式锁、多数据源、读写分离
- ✅ Flyway数据库迁移、K8s部署脚本
- ✅ 专属技术顾问、源码级调试支持

### 购买方式

- 🌐 官网: https://apex-scaffold.example.com/pricing
- 📧 邮箱: sales@apex-scaffold.example.com
- 📞 电话: 400-XXX-XXXX
- 🎁 试用: 标准版30天免费试用,企业版14天POC验证

## 📄 许可证

Apex Scaffold 采用**双轨许可证**模式：

- **基础版（Basic）**：基于 [MIT License](LICENSE) 完全开源，可自由使用、修改与再分发。
- **标准版（Standard）/ 企业版（Enterprise）**：商业授权，受 [LICENSE-COMMERCIAL.md](LICENSE-COMMERCIAL.md) 约束，需购买 License 授权文件后方可使用，**禁止二次分发**。

> ⚠️ 商业版内置 License 验证机制：未配置有效授权时，商业功能（如消息队列、链路追踪等）自动降级关闭，基础功能不受影响。

详细条款请查看 [LICENSE](LICENSE) 与 [LICENSE-COMMERCIAL.md](LICENSE-COMMERCIAL.md)，授权管理指南见 [docs/LICENSE_MANAGEMENT.md](docs/LICENSE_MANAGEMENT.md)。

## 🙏 致谢

感谢以下开源项目的支持:

- [Spring Boot](https://spring.io/projects/spring-boot)
- [MyBatis-Plus](https://baomidou.com/)
- [Redisson](https://redisson.org/)
- [RocketMQ](https://rocketmq.apache.org/)
- [Sentinel](https://sentinelguard.io/)

---

**Made with ❤️ by Apex Team**
