# Apex Scaffold 全面评审与规范化实施计划

## 📋 计划概览

**目标**：全面评审前后端代码实现、配置文件、文档、脚本和图表，确保项目的一致性、规范性和完整性。

**执行日期**：2026-09-17  
**项目版本**：v1.2.0  
**涉及范围**：后端（apex-scaffold）+ 前端（apex-admin-web）

---

## 一、当前状态分析

### 1.1 项目结构概况

#### 后端项目 (apex-scaffold)
- **技术栈**：Java 17 + Spring Boot 3.5.15 + Spring WebFlux
- **构建工具**：Maven Daemon (mvnd 2.0.0-rc-3)
- **架构模式**：DDD 四层架构（interfaces → application → domain ← infrastructure）
- **模块数量**：8 个核心模块 + 1 个生成器模块
- **数据库**：支持 MySQL 8.0 / PostgreSQL 15（双方言 Flyway 脚本）
- **Flyway 版本**：MySQL 39 个版本（V1-V39），PostgreSQL 38 个版本（缺失 V37）
- **当前版本**：v1.2.0（根 POM）
- **路由实现**：✅ 纯函数式 Router + Handler（17 个 Router 类，无 @Controller 注解）

#### 前端项目 (apex-admin-web)
- **技术栈**：Vue 3.5.13 + TypeScript 5.7.2 + Vite 6.0.7 + Element Plus 2.9.1
- **当前版本**：v1.1.4
- **状态管理**：Pinia 2.3.0
- **HTTP 客户端**：Axios 1.7.9
- **API 封装**：15 个模块化 API 文件

#### 部署方式
1. **java -jar**：直接运行可执行 jar
2. **Podman**：多阶段构建镜像（基于 eclipse-temurin:17-jre-alpine）
3. **Podman Compose**：应用 + MySQL + Redis + Meilisearch + Loki + Alloy

#### 文档体系
- **文档总量**：9 个一级目录 + 70+ SVG 架构图
- **文档类型**：快速开始、功能指南、架构设计、API 文档、开发规范、运维部署、测试报告、商业化、项目管理
- **归档目录**：`_archive/` 包含历史文档和过期内容

#### 脚本体系
- **脚本目录**：12 个分类目录（backup、bench、ci、codegen、deploy、diagnostics 等）
- **脚本总量**：40+ 个脚本文件

---

### 1.2 已识别问题

#### 问题 1：PostgreSQL Flyway 脚本缺失 V37
- **位置**：`apex-interfaces/src/main/resources/db/migration/postgresql/`
- **影响**：PostgreSQL 部署时 Flyway 迁移会失败（版本号不连续）
- **MySQL 对应版本**：`V37__add_email_and_keycloak_sub_to_sys_user.sql` 已存在

#### 问题 2：版本号不一致
- **根 POM 版本**：✅ v1.2.0（已确认）
- **README.md 标识**：✅ v1.2.0（已一致）
- **前端 package.json**：❌ v1.1.4（落后 1 个小版本）
- **后端 Containerfile**（原 Dockerfile）：历史检查记录为 BUILD_VERSION=1.1.3（落后 2 个小版本）
- **前端容器构建文件**：历史检查记录为未指定版本号（仅 Node 18，独立前端项目）

#### 问题 3：数据库初始化脚本统一性
- **当前状态**：
  - Podman Compose：通过 Flyway 自动迁移
  - java -jar：通过 Flyway 自动迁移
  - 需确认：三种部署方式是否共用同一套脚本

#### 问题 4：文档与代码一致性
- **需验证**：
  - 配置文件示例值是否与 `.env.example` 一致
  - API 文档路由是否与实际 Handler/Router 对应
  - 架构图是否反映当前代码实现
  - 部署文档端口号是否与配置文件一致

#### 问题 5：SVG 图表与文档关联
- **已知状态**：
  - 主图表：69 个 SVG 文件位于 `docs/diagrams/`
  - 时序图源码：25 个 Mermaid 文件位于 `docs/diagrams/sequence/`
  - ❌ 发现 3 个 `.svg.broken` 文件（疑似备份或已废弃）
- **需验证**：
  - ✅ 图表命名规范统一（编号 + 下划线 + 描述）
  - 图表与文档引用关系是否完整
  - 时序图 Mermaid 源码与 SVG 是否同步

#### 问题 6：文档目录规范性
- **当前问题**：
  - `_archive/` 目录混杂大量历史文档
  - 部分文档重复（如多个实施计划、交付报告）
  - 文档命名不统一（中英文混用）
  - 临时文档未清理（如 `TEST_SVG_DISPLAY.md`）

#### 问题 7：脚本规范性
- **需检查**：
  - 脚本是否可执行（权限）
  - 脚本中的路径引用是否正确
  - 脚本文档化程度（README 是否完整）

---

## 二、评审任务分解

### 任务 1：数据库脚本与代码实现一致性验证与修复（P0）

**目标**：确保 MySQL 和 PostgreSQL Flyway 脚本版本完全一致，数据库表结构、字段、索引与代码实体类（PO）完全对应，三种部署方式共用同一套脚本。

#### 1.1 修复 PostgreSQL V37 缺失
- **MySQL V37 内容**：
  ```sql
  ALTER TABLE sys_user
      ADD COLUMN email VARCHAR(100) NULL COMMENT '用户邮箱' AFTER phone,
      ADD COLUMN keycloak_sub VARCHAR(100) NULL COMMENT 'Keycloak用户唯一标识' AFTER email;
  ALTER TABLE sys_user
      ADD UNIQUE INDEX uk_keycloak_sub (keycloak_sub);
  ```
- **操作**：适配 PostgreSQL 语法（注释语法、索引创建方式）
- **代码验证**：检查 `SysUserPO.java` 是否包含 `email` 和 `keycloakSub` 字段及对应注解
- **Mapper 验证**：检查 `SysUserMapper.xml` 的 resultMap 是否映射这两个字段

#### 1.2 数据库表结构与实体类一致性验证

**验证方法**：对比 Flyway 最终表结构与代码中的 PO 类

**核心表验证清单**：
1. **sys_user**：
   - 检查 `SysUserPO.java` 所有字段是否在数据库表中存在
   - 验证字段类型、长度、约束（NOT NULL、UNIQUE、DEFAULT）是否一致
   - 特别关注：`email`、`keycloak_sub`、`tenant_id`、`version`（乐观锁）、`deleted`（逻辑删除）
   
2. **sys_role**：
   - 验证 `data_scope` 字段（数据权限范围）
   - 验证 `tenant_id` 租户隔离字段

3. **sys_menu**：
   - 验证 `component` 字段（前端组件路径）与前端 `VIEW_REGISTRY` 对应关系
   - 验证 `permission` 字段（按钮权限标识）

4. **工作流表**（20+ 张）：
   - `wf_process_definition`、`wf_process_instance`、`wf_task` 等
   - 验证表结构与 Flyway V20、V21 脚本一致

5. **办公平台表**（10+ 张）：
   - `office_platform_binding`、`office_message`、`office_approval_process` 等
   - 验证表结构与 Flyway V29-V36 脚本一致

6. **OAuth2 表**：
   - `oauth2_authorization`、`oauth2_authorization_consent` 等
   - 验证表结构与 Flyway V25 脚本及 Spring Authorization Server 标准一致

**验证步骤**：
1. 读取所有 PO 类（`apex-infrastructure/src/main/java/**/persistence/po/`）
2. 提取字段列表、类型、注解（`@TableField`、`@TableId`、`@TableLogic`、`@Version`）
3. 对比 Flyway 最新脚本生成的表结构
4. 标记不一致的字段（缺失、类型不匹配、约束不一致）
5. **修复原则**：以代码实体类为准，调整 Flyway 脚本

#### 1.3 验证 Flyway 版本连续性
- **MySQL 验证**：V1 ~ V39 连续无断层
- **PostgreSQL 验证**：补充 V37 后 V1 ~ V39 连续无断层
- **幂等性验证**：每个脚本可重复执行不报错

#### 1.5 确认三种部署方式统一性
- **验证点**：
  - `podman-compose.yml` 中的 MySQL 初始化是否依赖 Flyway（✅ 已确认：通过应用启动时 Flyway 自动迁移）
  - `podman-compose.infra.yml` 中的 MySQL 是否有独立初始化脚本（✅ 已确认：无独立脚本，仅创建空库）
  - java -jar 启动时 Flyway 配置是否正确读取 `db/migration/`（✅ 已确认：通过 `spring.flyway.locations`）

#### 1.7 检查 Flyway 配置一致性
- **检查项**：
  - `application.yml` 中的 Flyway 配置
  - `application-dev.yml` / `application-prod.yml` 是否覆盖 Flyway 配置
  - 数据库类型切换（`apex.database.type`）是否正确路由到对应目录
  
#### 1.8 数据库初始化数据与代码一致性验证

**验证重点**：菜单、权限、角色种子数据与前后端代码对应

1. **菜单种子数据验证**：
   - 检查 Flyway 种子数据中的 `sys_menu.component` 字段
   - 对比前端 `VIEW_REGISTRY` 注册的组件路径
   - 验证 `sys_menu.path` 是否与前端路由路径一致
   - **修复原则**：以前端实际组件路径为准

2. **权限种子数据验证**：
   - 检查 Flyway 种子数据中的 `sys_menu.permission` 字段（按钮权限）
   - 对比后端 Handler 方法上的 `@PreAuthorize("hasAuthority('...')")` 注解
   - 验证权限字符串格式统一（如 `system:user:add`）
   - **修复原则**：以后端实际权限注解为准

3. **角色权限关联验证**：
   - 验证 `sys_role_menu` 关联表数据完整性
   - 确保超级管理员角色拥有所有菜单和权限

4. **字典数据验证**：
   - 验证 `sys_dict_type` 和 `sys_dict_data` 种子数据
   - 对比代码中使用的字典类型常量（如 `sys_user_gender`、`sys_yes_no`）
   - **修复原则**：以代码中实际使用的字典类型为准

---

### 任务 2：版本号统一（P0）

**目标**：统一前后端版本号，确保根 POM、README、Podman 构建参数一致。

#### 2.1 确定统一版本号
- **决策依据**：根 POM 版本为准
- **目标版本**：**v1.2.0**（已确认）

#### 2.2 更新版本号引用
- **后端**：
  - `/Users/work/apex-scaffold/pom.xml`（根 POM）
  - `/Users/work/apex-scaffold/README.md`
  - `/Users/work/apex-scaffold/Containerfile`（BUILD_VERSION）
  - `/Users/work/apex-scaffold/CHANGELOG.md`（如有）
- **前端**：
  - `/Users/work/apex-admin-web/package.json`
  - `/Users/work/apex-admin-web/README.md`（如有版本标识）

#### 2.3 验证版本号传播
- **检查点**：
  - Podman 镜像标签
  - Actuator `/actuator/info` 端点返回的版本信息
  - Grafana Dashboard 版本标识

---

### 任务 3：配置文件与文档一致性（P1）

**目标**：确保配置文件、环境变量示例、部署文档的配置项完全对齐。

#### 3.1 对比配置文件与 `.env.example`
- **对比项**：
  - `application.yml` 中的环境变量占位符 `${VAR:default}`
  - `.env.example` 中的变量名称、默认值、注释
  - `podman-compose.yml` 中的环境变量传递

#### 3.2 检查配置文档准确性
- **检查文档**：
  - `docs/05-operations/configuration.md`
  - `docs/05-operations/deployment.md`
  - `docs/00-getting-started/quickstart.md`
- **检查项**：
  - 端口号（8080、8081、3306、6379 等）
  - 数据库连接字符串格式
  - JWT 密钥长度要求（≥32 字节）
  - Redis 密码要求（prod 环境强制）

#### 3.3 验证 Profile 配置一致性
- **Profile 列表**：basic、standard、enterprise、dev、prod、performance、rate-limit
- **检查项**：
  - 每个 Profile 对应的 `application-{profile}.yml` 是否存在
  - Profile 之间的差异配置是否有文档说明
  - `spring.config.import` 引入的外部配置文件是否存在

---

### 任务 4：API 路由与文档对齐（P1）

**目标**：确保 API 文档、路由映射表与实际 Handler/Router 代码完全一致。

#### 4.1 扫描实际路由实现
- **已扫描结果**：
  - ✅ 发现 17 个 Router 类（纯函数式实现）
  - ✅ 确认无 @Controller/@RestController 注解式接口（仅 GlobalExceptionHandler 使用 @RestControllerAdvice）
  - 核心 Router：ApiRouter、ChartRouter、DocumentRouter、WorkflowRouter、OfficeAdminRouter 等
  - 办公平台 Router：5 个（Message、Approval、Calendar、Document、Organization）
- **待执行**：
  - 提取每个 RouterFunction 的详细路由路径
  - 生成完整路由清单（路径、方法、Handler、参数类型）

#### 4.2 对比 API 文档
- **文档位置**：`docs/03-api/api-routes.md`
- **对比项**：
  - 路由路径是否遗漏或过时
  - HTTP 方法是否正确
  - 请求参数、响应格式是否与 Handler 代码一致

#### 4.3 检查 SpringDoc 注解完整性
- **检查项**：
  - Router 方法是否添加 `@RouterOperation` 注解
  - Handler 方法是否有中文 JavaDoc
  - Request DTO 是否有 `@Schema` 注解（含 `required` 标识）
  - Response DTO 是否有 `@Schema` 注解（含示例值）

#### 4.4 验证在线文档可访问性
- **验证点**：
  - Swagger UI：`http://localhost:8080/swagger-ui.html`
  - Knife4j：`http://localhost:8080/doc.html`
  - OpenAPI JSON：`http://localhost:8080/v3/api-docs`

---

### 任务 5：架构图与代码实现对齐（P1）

**目标**：确保 70+ SVG 架构图反映当前代码实现，时序图 Mermaid 源码与 SVG 同步。

#### 5.1 核心架构图验证
- **验证清单**：
  1. **01_logical_architecture.svg**：四层架构是否与实际模块划分一致
  2. **02_physical_architecture.svg**：部署架构是否与 Podman Compose 配置一致
  3. **03_technical_architecture.svg**：技术选型是否与 `pom.xml` 依赖对应
  4. **09_multi_level_cache_architecture.svg**：缓存实现是否与代码一致（Caffeine L1 + Redis L2）
  5. **10_rate_limit_circuit_breaker.svg**：限流熔断实现是否与 Sentinel/Resilience4j 配置一致
  6. **30-39 认证授权系列图**：OAuth2、SSO、Keycloak 流程是否与代码实现一致
  7. **60_workflow_engine_lifecycle.svg**：工作流引擎生命周期是否与 Flyway 表结构一致

#### 5.2 时序图源码同步
- **检查目录**：`docs/diagrams/sequence/`
- **对比项**：Mermaid 源码文件与对应的 SVG 图表
- **同步方式**：如果 SVG 有更新，重新从 Mermaid 源码生成

#### 5.3 图表文档关联
- **检查项**：
  - 每个图表是否在至少一篇文档中被引用
  - 图表文件名是否与引用路径一致
  - 图表说明文字是否准确

#### 5.4 清理无效图表
- **检查项**：
  - `*.broken` 文件：确认是否为临时备份或已废弃
  - 重复图表：如 `19_backend_technology_stack.svg.broken` 与 `19_frontend_vue3_architecture.svg`

---

### 任务 6：文档目录重组与规范化（P2）

**目标**：清理冗余文档、统一命名规范、优化目录结构。

#### 6.1 清理 `_archive/` 目录
- **清理策略**：
  - **保留**：有参考价值的历史实施计划、重大决策记录
  - **删除**：临时测试文档、重复的交付报告、过期的 API 测试集合
- **具体清理项**：
  - `_archive/reports/archive/` 多层嵌套报告（合并为单一最终报告）
  - `_archive/postman/` Postman 集合（如已迁移到自动化测试则删除）
  - `_archive/technical/tests/PNG_TEST.md` 临时测试文档

#### 6.2 统一文档命名规范
- **命名规则**：
  - 英文文档：小写 + 连字符（如 `api-routes.md`）
  - 中文文档：中文 + 下划线（如 `实施策略建议.md`）
  - 避免混用：不使用 `UPPERCASE_WITH_UNDERSCORES.md`
- **批量重命名**：
  - `AGENTS.md` → 保持（作为项目规范文件）
  - `README.md` → 保持（根目录必需）
  - 其他大写命名文档按规范重命名

#### 6.3 优化目录结构
- **调整方案**：
  - **合并**：`docs/architecture/` 和 `docs/02-architecture/` 内容重复（保留编号版本）
  - **迁移**：`docs/api/` 迁移到 `docs/03-api/`
  - **删除**：根目录下的临时文档（如 `DOCS_REORGANIZATION_REPORT.md`）

#### 6.4 补充缺失的 README
- **检查目录**：
  - `scripts/` 各子目录是否有 README.md
  - `docs/diagrams/sequence/` 是否有说明文档
  - `config/monitoring/` 是否有配置说明

---

### 任务 7：脚本规范性检查与修复（P2）

**目标**：确保所有脚本可执行、路径引用正确、文档化完整。

#### 7.1 脚本可执行性检查
- **检查命令**：`find scripts/ -type f -name "*.sh" ! -executable`
- **修复操作**：`chmod +x scripts/**/*.sh`

#### 7.2 脚本路径引用验证
- **检查项**：
  - 脚本中的相对路径是否从项目根目录开始
  - 引用的文件是否存在（如 `config/monitoring/prometheus.yml`）
  - 硬编码路径是否改为可配置变量

#### 7.3 脚本文档化
- **检查项**：
  - 每个脚本是否有头部注释说明用途
  - 复杂脚本是否有参数说明和使用示例
  - `scripts/` 根目录是否有总体 README

#### 7.4 脚本功能验证
- **验证方法**：
  - 构建脚本：`scripts/build/build.sh` 是否能成功构建
  - 部署脚本：`scripts/deploy/start.sh` 是否能正确启动应用
  - 测试脚本：`scripts/test/generate-test-report.sh` 是否能生成报告

---

### 任务 8：前端代码与后端接口对齐（P1）

**目标**：确保前端 API 调用与后端接口完全对应，避免 404 错误。

#### 8.1 前端 API 封装检查
- **已扫描结果**：
  - ✅ 15 个 API 模块文件
  - 核心模块：auth.ts、system.ts、user.ts、chart.ts、document.ts、workflow.ts
  - 办公平台：office.ts、office-new.ts、office-updated.ts（存在多版本，需确认是否为迭代产物）
  - 其他：oauth2.ts、file.ts、log.ts、monitor.ts、search.ts、database.ts
- **检查项**：
  - API 路径是否与后端 Router 一致
  - 请求方法（GET/POST/PUT/DELETE）是否匹配
  - office 模块三个版本的关系（是否需要合并或删除旧版本）
  - 响应数据结构是否与后端 Response DTO 对应

#### 8.2 前端路由与菜单对齐
- **检查项**：
  - `src/router/` 中的前端路由路径
  - `src/views/` 中的页面组件
  - 后端 Flyway 种子数据中的菜单 `component` 字段
  - 前端 `VIEW_REGISTRY` 组件注册表

#### 8.3 前端环境变量检查
- **检查文件**：
  - `.env.development`：开发环境后端地址是否正确（`http://127.0.0.1:8080`）
  - `.env.production`：生产环境后端地址配置
  - `vite.config.ts`：代理配置是否与环境变量一致

---

## 三、实施步骤

### 阶段 1：探索与评估（Phase 1 - Discovery）✅

**已完成**：
- ✅ 项目结构全面扫描（后端 8 模块 + 前端完整目录）
- ✅ 核心配置文件读取（application.yml、.env.example、podman-compose.yml）
- ✅ Flyway 脚本版本对比（MySQL 39 版本、PostgreSQL 38 版本）
- ✅ 文档目录结构分析（9 个一级目录 + 70+ SVG 图表 + 25 个 Mermaid 源码）
- ✅ 根 POM 版本号确认（v1.2.0）
- ✅ 路由实现扫描（17 个 Router 类，纯函数式）
- ✅ PostgreSQL V37 脚本内容识别（MySQL V37 已读取）
- ✅ 前端 API 模块扫描（15 个 API 文件）
- ✅ 时序图源码目录确认（25 个 Mermaid 文件）
- ✅ 历史版本号不一致点定位（前端 v1.1.4、后端容器构建文件 v1.1.3）

**关键发现**：
1. **PostgreSQL V37 缺失**：需创建脚本添加 email 和 keycloak_sub 字段
2. **版本号不统一**：需将前端和 Containerfile 统一到 v1.2.0
3. **前端 office API 多版本**：office.ts、office-new.ts、office-updated.ts 需确认关系
4. **SVG broken 文件**：3 个 .svg.broken 文件需确认是否删除
5. **路由实现规范**：✅ 已完全符合 WebFlux 函数式规范（无注解式 Controller）

---

### 阶段 2：问题修复（Phase 2 - Fix Critical Issues）

#### Step 2.1：数据库脚本与代码一致性验证
1. **扫描所有 PO 类**：提取字段、类型、注解信息
2. **对比 Flyway 脚本**：逐表对比表结构定义
3. **生成差异报告**：标记不一致的字段和约束
4. **修复 Flyway 脚本**：以代码实体类为准，创建补丁脚本（新增 V40 等）
5. **验证菜单权限数据**：对比前端组件路径和后端权限注解
6. **修复种子数据**：调整菜单 `component`、权限 `permission` 字段

#### Step 2.2：修复 PostgreSQL V37 缺失脚本
1. 读取 MySQL V37 脚本内容（✅ 已读取）
2. 验证 `SysUserPO.java` 是否包含 `email` 和 `keycloakSub` 字段
3. 适配 PostgreSQL 语法（注释语法、索引创建方式）
4. 创建 PostgreSQL V37 脚本
5. 验证幂等性

#### Step 2.3：统一版本号
1. 读取根 POM 确定基准版本
2. 批量更新版本号引用
3. 提交版本号统一的变更

#### Step 2.4：修复配置文件不一致
1. 对比 `application.yml` 与 `.env.example`
2. 补充缺失的环境变量说明
3. 修正默认值不一致的项

---

### 阶段 3：文档与代码对齐（Phase 3 - Documentation Sync）

#### Step 3.1：API 文档更新
1. 生成实际路由清单
2. 对比 `docs/03-api/api-routes.md`
3. 更新路由映射表

#### Step 3.2：架构图验证
1. 逐一检查核心架构图
2. 标记需要更新的图表
3. 重新生成时序图 SVG

#### Step 3.3：配置文档更新
1. 更新 `docs/05-operations/configuration.md`
2. 更新 `docs/00-getting-started/quickstart.md`
3. 确保端口号、连接字符串示例准确

---

### 阶段 4：文档规范化（Phase 4 - Documentation Cleanup）

#### Step 4.1：清理 `_archive/`
1. 识别可删除的重复报告
2. 保留有价值的历史决策文档
3. 删除临时测试文档

#### Step 4.2：统一文档命名
1. 批量重命名不规范文档
2. 更新内部链接引用

#### Step 4.3：补充 README
1. 为缺失 README 的目录创建说明
2. 更新脚本使用文档

---

### 阶段 5：脚本规范化（Phase 5 - Scripts Standardization）

#### Step 5.1：脚本权限修复
1. 批量添加可执行权限

#### Step 5.2：脚本路径修复
1. 检查并修正硬编码路径
2. 使用变量替代绝对路径

#### Step 5.3：脚本文档补充
1. 为脚本添加头部注释
2. 创建脚本使用手册

---

### 阶段 6：前端对齐（Phase 6 - Frontend Alignment）

#### Step 6.1：前端 API 对齐
1. 对比前端 API 封装与后端路由
2. 修复路径不一致的 API 调用

#### Step 6.2：前端路由对齐
1. 对比前端路由与菜单种子数据
2. 补充缺失的组件注册

---

### 阶段 7：验证与测试（Phase 7 - Verification）

#### Step 7.1：构建验证
- [ ] 后端构建：`mvnd clean package -DskipTests`
- [ ] 前端构建：`cd apex-admin-web && pnpm build`
- [ ] Podman 镜像构建：`podman build -f Containerfile -t apex-scaffold:test .`

#### Step 7.2：启动验证
- [ ] java -jar 启动测试
- [ ] Podman Compose 启动测试（基础栈）
- [ ] Podman Compose 启动测试（完整栈）

#### Step 7.3：Flyway 迁移验证
- [ ] MySQL Flyway 迁移测试（从空库到 V39）
- [ ] PostgreSQL Flyway 迁移测试（从空库到 V39）
- [ ] 验证表结构与 PO 类一致性
- [ ] 验证初始化数据完整性（菜单、权限、角色、字典）
- [ ] 验证菜单 `component` 字段与前端组件路径对应
- [ ] 验证权限 `permission` 字段与后端注解对应

#### Step 7.4：功能验证
- [ ] 登录认证流程
- [ ] API 调用测试
- [ ] 前端页面访问测试

---

## 四、预期成果

### 4.1 代码层面
- ✅ MySQL 和 PostgreSQL Flyway 脚本版本完全一致（V1-V39）
- ✅ **数据库表结构与 PO 实体类 100% 对应**（字段、类型、约束、索引）
- ✅ **菜单种子数据与前端组件路径 100% 对应**
- ✅ **权限种子数据与后端权限注解 100% 对应**
- ✅ **字典种子数据与代码使用的字典类型 100% 对应**
- ✅ 前后端版本号统一
- ✅ 三种部署方式共用同一套数据库脚本
- ✅ 配置文件与环境变量完全对应

### 4.2 文档层面
- ✅ API 文档与实际路由 100% 对齐
- ✅ 架构图反映当前代码实现
- ✅ 配置文档示例值准确
- ✅ 文档目录结构清晰，无冗余

### 4.3 脚本层面
- ✅ 所有脚本可执行
- ✅ 脚本路径引用正确
- ✅ 脚本文档化完整

### 4.4 图表层面
- ✅ SVG 图表与文档关联清晰
- ✅ 时序图源码与 SVG 同步
- ✅ 图表命名规范统一

---

## 五、风险与注意事项

### 5.1 数据库脚本风险
- **风险**：PostgreSQL V37 脚本适配可能遇到语法差异
- **缓解**：先在本地 PostgreSQL 测试环境验证

### 5.2 版本号变更风险
- **风险**：版本号变更可能影响 CI/CD 流水线
- **缓解**：检查 GitHub Actions 工作流是否硬编码版本号

### 5.3 文档删除风险
- **风险**：删除 `_archive/` 中的文档可能丢失历史信息
- **缓解**：删除前先备份，或仅移动到单独的归档分支

### 5.4 脚本修改风险
- **风险**：修改脚本路径可能导致 CI/CD 失败
- **缓解**：先在本地验证脚本可用性，再提交

---

## 六、执行检查清单

### 阶段 1：探索与评估 ✅
- [x] 项目结构扫描
- [x] 配置文件读取
- [x] Flyway 版本对比
- [x] 文档目录分析

### 阶段 2：问题修复
- [ ] **验证数据库表结构与 PO 类一致性（核心任务）**
- [ ] **验证菜单、权限种子数据与代码一致性（核心任务）**
- [ ] 创建 PostgreSQL V37 脚本
- [ ] 统一版本号（POM、README、Containerfile、package.json）
- [ ] 修复配置文件不一致

### 阶段 3：文档与代码对齐
- [ ] 生成实际路由清单
- [ ] 更新 API 文档
- [ ] 验证核心架构图
- [ ] 同步时序图源码

### 阶段 4：文档规范化
- [ ] 清理 `_archive/` 重复文档
- [ ] 统一文档命名
- [ ] 补充缺失 README

### 阶段 5：脚本规范化
- [ ] 批量添加脚本可执行权限
- [ ] 修正脚本路径引用
- [ ] 补充脚本文档

### 阶段 6：前端对齐
- [ ] 对比前端 API 与后端路由
- [ ] 对齐前端路由与菜单
- [ ] 验证环境变量配置

### 阶段 7：验证与测试
- [ ] 后端构建测试
- [ ] 前端构建测试
- [ ] Podman 镜像构建测试
- [ ] Flyway 迁移测试（MySQL + PostgreSQL）
- [ ] 三种部署方式启动测试
- [ ] 功能冒烟测试

---

## 七、总结

本计划全面覆盖了 Apex Scaffold 项目的代码、配置、文档、脚本和图表的评审与规范化工作。通过分阶段执行，将确保：

1. **数据库一致性**：MySQL 和 PostgreSQL 脚本版本完全对齐
2. **部署统一性**：java -jar、Podman、Podman Compose 三种方式共用同一套脚本
3. **文档准确性**：API 文档、架构图、配置文档与代码实现 100% 一致
4. **目录规范性**：文档、脚本目录结构清晰，命名规范统一
5. **可维护性**：脚本可执行、文档化完整、图表与文档关联清晰

执行完成后，项目将达到商业化交付标准，便于客户部署和维护。
