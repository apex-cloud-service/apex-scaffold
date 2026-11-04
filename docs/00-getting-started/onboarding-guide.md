# Apex Scaffold 架构图表新人培训指南

> 版本: v1.0 | 发布日期: 2026-09-10 | 培训时长: 2小时

本文档为新加入 Apex Scaffold 项目的团队成员提供系统化的架构图表学习路径,帮助快速理解项目架构。

---

## 🎯 培训目标

完成本培训后,你将能够:
- ✅ 理解 Apex Scaffold 的 DDD 四层架构设计
- ✅ 掌握前后端技术栈选型及版本信息
- ✅ 了解生产环境部署拓扑和运维流程
- ✅ 熟悉核心业务流程(登录认证、权限校验、工作流)
- ✅ 知道在哪里查找特定技术问题的架构图

---

## 📚 学习路径 (按阶段)

### 第1阶段: 架构基础 (30分钟)

**目标**: 建立整体认知,理解项目分层设计

#### 必读图表 (3张)

1. **[01_logical_architecture.svg](../diagrams/01_logical_architecture.svg)** - DDD四层逻辑架构
   - 📌 **为什么要看**: 这是理解整个项目的基础,展示了接口层、应用层、领域层、基础设施层的职责划分
   - 🔍 **重点关注**: 
     - 115 个 Handler 在接口层的职责
     - 34个应用服务如何编排业务逻辑
     - 112 个 Repository 接口如何定义数据访问规范
     - 依赖方向:只能从上往下/从外向内
   - 💡 **实践建议**: 打开 `apex-system/src/main/java/com/apex/scaffold/interfaces/handler/` 目录,对照图表理解Handler职责

2. **[03_technical_architecture.svg](../diagrams/03_technical_architecture.svg)** - 全栈技术选型全景图
   - 📌 **为什么要看**: 了解项目使用的60+技术组件及版本号
   - 🔍 **重点关注**:
     - 前端: Vue 3.5.13 + Vite 6 + TypeScript 5
     - 后端: Spring Boot 3.5.15 + WebFlux + MyBatis-Plus
     - 中间件: MySQL 9.0 + Redis 7 + RocketMQ 5.3.1
     - 监控: Prometheus + Grafana + Loki + Zipkin
   - 💡 **实践建议**: 打开 `pom.xml` 和 `package.json`,对照图表确认版本号

3. **[02_physical_architecture.svg](../diagrams/02_physical_architecture.svg)** - 网络物理部署架构
   - 📌 **为什么要看**: 理解生产环境的5层网络拓扑
   - 🔍 **重点关注**:
     - 端口分配:gateway 8080、auth 8081、system 8082（管理 8083）、workflow 8083、office 8084、file 8085、integration 8094（管理 8097）、job 8095、masking 8096
     - Podman Compose默认栈 vs Kubernetes生产扩展
     - Profile配置:basic/standard/enterprise
   - 💡 **实践建议**: 执行 `podman-compose -f podman-compose.yml up -d` 启动本地开发环境

#### 自测题

- [ ] 能画出DDD四层架构及依赖方向吗?
- [ ] 能说出Spring Boot和Vue的版本号吗?
- [ ] 知道本地开发环境如何一键启动吗?

---

### 第2阶段: 核心业务流程 (30分钟)

**目标**: 理解关键业务逻辑的实现流程

#### 必读图表 (4张)

4. **[04_login_authentication_flow.svg](../diagrams/04_login_authentication_flow.svg)** - JWT登录认证流程
   - 📌 **为什么要看**: 登录是最常用的功能,理解JWT双Token机制
   - 🔍 **重点关注**:
     - Access Token(2小时) + Refresh Token(7天)
     - 自动续期机制:剩余时间<30分钟触发刷新
     - 安全防护:IP限流、登录保护、防重放
   - 💡 **实践建议**: 用Postman测试 `POST /api/v1/auth/login` 接口,观察Token返回

5. **[05_permission_check_flow.svg](../diagrams/05_permission_check_flow.svg)** - RBAC权限校验流程
   - 📌 **为什么要看**: 理解三层权限模型(JWT认证→RBAC校验→数据权限)
   - 🔍 **重点关注**:
     - 5张关联表:user/role/menu/user_role/role_menu
     - `@RequirePermission("system:user:list")` 注解用法
     - Caffeine本地缓存60秒TTL
   - 💡 **实践建议**: 查看 `sys_menu` 表的 `perms` 字段,理解权限点命名规范

6. **[06_workflow_approval_flow.svg](../diagrams/06_workflow_approval_flow.svg)** - 三级审批工作流
   - 📌 **为什么要看**: 工作流是业务系统的核心,理解DAG引擎设计
   - 🔍 **重点关注**:
     - 三级审批:部门主管→分管领导→总经理
     - 高级功能:回退、转办、委派、挂起、超时处理
     - 4张表:flow_definition/instance/task/history
   - 💡 **实践建议**: 在管理后台创建一个请假流程,体验完整审批流程

7. **[07_file_upload_flow.svg](../diagrams/07_file_upload_flow.svg)** - 文件分片上传流程
   - 📌 **为什么要看**: 理解大文件上传的工程实践(秒传、断点续传)
   - 🔍 **重点关注**:
     - 5MB分片策略
     - MD5秒传机制
     - MinIO对象存储
   - 💡 **实践建议**: 上传一个100MB文件,观察浏览器Network面板的分片请求

#### 自测题

- [ ] 能说出Access Token和Refresh Token的有效期吗?
- [ ] 知道权限点命名规范(如`system:user:list`)吗?
- [ ] 理解工作流的回退和转办的区别吗?

---

### 第3阶段: 基础设施与安全 (30分钟)

**目标**: 理解数据访问、缓存、安全防护机制

#### 必读图表 (5张)

8. **[08_database_er_diagram.svg](../diagrams/08_database_er_diagram.svg)** - 数据库ER图
   - 📌 **为什么要看**: 理解40+张表的设计及ER关系
   - 🔍 **重点关注**:
     - 核心RBAC表(7张)
     - Flyway迁移历史(V1-V26)
     - 租户隔离字段 `tenant_id`
   - 💡 **实践建议**: 打开MySQL Workbench,对照图表理解表结构

9. **[09_multi_level_cache_architecture.svg](../diagrams/09_multi_level_cache_architecture.svg)** - 多级缓存架构
   - 📌 **为什么要看**: 理解L1 Caffeine + L2 Redis的缓存策略
   - 🔍 **重点关注**:
     - L1缓存:10k条/5分钟TTL/微秒级响应
     - L2缓存:7天TTL/1-5ms响应
     - 防击穿、防穿透、防雪崩机制
   - 💡 **实践建议**: 查看Grafana缓存命中率面板(80-95%是正常值)

10. **[10_rate_limit_circuit_breaker.svg](../diagrams/10_rate_limit_circuit_breaker.svg)** - 限流熔断机制
    - 📌 **为什么要看**: 理解Sentinel+Resilience4j双重保障
    - 🔍 **重点关注**:
      - `@RateLimiter` 通用限流注解
      - `@HotSpotRateLimit` 热点参数限流
      - 三档熔断策略:默认/严格/宽松
    - 💡 **实践建议**: 访问 http://localhost:8858 查看Sentinel控制台

11. **[11_data_permission_filter.svg](../diagrams/11_data_permission_filter.svg)** - 数据权限过滤
    - 📌 **为什么要看**: 理解 `@DataScope` 如何实现行级权限控制
    - 🔍 **重点关注**:
      - 5种数据范围:ALL/DEPT/DEPT_AND_CHILD/SELF/CUSTOM
      - AOP拦截 + MyBatis-Plus SQL动态注入
    - 💡 **实践建议**: 用不同角色登录,查看用户列表的数据范围差异

12. **[12_security_protection_system.svg](../diagrams/12_security_protection_system.svg)** - 安全防护体系
    - 📌 **为什么要看**: 理解7层安全防护机制
    - 🔍 **重点关注**:
      - `@AntiReplay` 防重放攻击
      - `@IdempotentApi` 接口幂等
      - XSS过滤、SQL注入防护
    - 💡 **实践建议**: 在Postman中尝试重放请求,观察5001错误码

#### 自测题

- [ ] 能说出Caffeine和Redis的TTL配置吗?
- [ ] 知道 `@DataScope` 的5种数据范围吗?
- [ ] 理解防重放攻击的时间戳+Nonce机制吗?

---

### 第4阶段: 前端技术栈 (如果是前端开发,30分钟)

**目标**: 理解Vue 3前端架构和与后端的交互

#### 必读图表 (5张)

19. **[19_frontend_vue3_architecture.svg](../diagrams/19_frontend_vue3_architecture.svg)** - 前端Vue3组件架构
20. **[20_axios_request_interceptor.svg](../diagrams/20_axios_request_interceptor.svg)** - Axios请求拦截与封装
21. **[21_webflux_reactive_programming.svg](../diagrams/21_webflux_reactive_programming.svg)** - WebFlux响应式编程
22. **[22_i18n_internationalization.svg](../diagrams/22_i18n_internationalization.svg)** - 国际化i18n多语言
23. **[23_openapi_documentation.svg](../diagrams/23_openapi_documentation.svg)** - OpenAPI文档生成

---

## 🔧 实践任务

### 任务1: 本地环境搭建 (15分钟)

```bash
# 1. 启动 Podman Compose
podman-compose -f podman-compose.yml up -d

# 2. 查看服务启动状态
podman-compose -f podman-compose.yml ps

# 3. 访问服务
open http://localhost:8080/doc.html      # Swagger文档
open http://localhost:3000               # Grafana监控(admin/admin)
open http://localhost:9411               # Zipkin链路追踪

# 4. 测试登录接口
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'
```

### 任务2: 创建第一个接口 (30分钟)

按照DDD四层架构,创建一个 `GET /api/v1/products/{id}` 接口:

1. **领域层** (`apex-domain`): 定义 `Product` 实体和 `ProductRepository` 接口
2. **基础设施层** (`apex-system` 的 `infrastructure` 包): 实现 `ProductPO`/`ProductMapper`/`ProductRepositoryImpl`
3. **应用层** (`apex-system` 的 `application` 包): 实现 `ProductService`
4. **接口层** (`apex-system` 的 `interfaces` 包): 实现 `ProductHandler` + 注册路由

参考代码生成器: `./scripts/codegen/generate.sh -i examples/product.json`

### 任务3: 查看监控大盘 (10分钟)

1. 访问 Grafana: http://localhost:3000 (admin/admin)
2. 查看 "Apex Scaffold - 应用概览" 面板
3. 观察 QPS、响应时间P99、错误率、缓存命中率
4. 制造一个慢请求,观察"慢请求Top10"面板

---

## 📖 进阶学习资源

完成基础培训后,根据你的角色深入学习:

### 后端开发
- [后端开发规范](../04-development/development-standards.md)
- [WebFlux响应式编程最佳实践](../architecture/reactive-programming-guide.md)
- [MyBatis-Plus使用指南](../01-guides/best-practices.md)

### 前端开发
- 前端开发规范：已迁移至独立前端仓库 `apex-admin-web` 的 `frontend-docs/frontend-development-standards.md`
- [Vue 3 Composition API实践](../architecture/frontend-technology-stack.md)
- [API路由映射](../_archive/api/route-mapping.md)

### 运维部署
- [部署指南](../05-operations/deployment.md)
- [Kubernetes生产部署最佳实践](../architecture/deployment-architecture.md)
- [监控告警配置](../_archive/operations/monitoring-dashboard-configuration.md)

---

## 🎓 培训考核

完成以下任务,即可通过新人培训考核:

- [ ] 能独立启动本地开发环境
- [ ] 能按照DDD四层架构创建一个CRUD接口
- [ ] 能在Grafana中查看监控指标
- [ ] 能解释JWT双Token机制和自动续期流程
- [ ] 能说出项目使用的核心技术栈及版本号
- [ ] 能找到特定技术问题对应的架构图表

---

## 💬 获取帮助

- **架构文档索引**: [ARCHITECTURE_INDEX.md](../02-architecture/architecture-index.md)
- **SVG图表使用指南**: [diagrams/README.md](../diagrams/README.md)
- **常见问题**: [FAQ.md](../01-guides/troubleshooting.md)
- **团队沟通**: 企业微信 Apex 开发群
- **代码审查**: 提交 PR 后 @架构团队 进行Code Review

---

**培训文档维护者**: 架构团队  
**版本**: v1.0  
**发布日期**: 2026-09-10  
**预计培训时长**: 2小时

---

© 2026 Apex Team · 帮助新人快速融入团队,理解项目架构
