# 前端配置数据库多适配方案 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 基于现有 Apex Scaffold 代码结构，设计一套通过前端页面配置来生成部署配置、支持 MySQL 与 PostgreSQL 首期适配、并可平滑扩展到更多主流数据库的实施方案。

**Architecture:** 方案采用“部署时选库 + 公共能力子集优先”的路径：前端只负责采集数据库类型与非敏感连接参数，后端根据配置生成对应环境配置并按数据库方言启动。数据库迁移按数据库类型分目录组织，核心持久化层通过可扩展的方言/适配器边界隔离 SQL、分页、类型映射和迁移差异，避免把差异散落到业务层。

**Tech Stack:** Java 17、Spring Boot 3.5、WebFlux、dynamic-datasource、MyBatis-Plus、Flyway、Druid、FreeMarker、Vue 3 + TypeScript + Element Plus（外部 `apex-admin-web` 工程）、MySQL 8、PostgreSQL 15+

---

## 1. 方案目标

1. 让用户通过前端配置页选择数据库类型，并填写部署所需的非敏感参数。
2. 生成后端可直接消费的部署配置，避免前端直接管理数据库密码或长期保存密钥。
3. 首期覆盖 MySQL 与 PostgreSQL，后续通过 SPI 或适配器机制继续扩展其他主流数据库。
4. 保持现有 MyBatis-Plus、Flyway、WebFlux、代码生成器和动态路由机制可复用，不推翻当前工程结构。
5. 明确首期验收边界：连接、迁移、CRUD、分页、逻辑删除、基础租户/权限约束。

## 2. 当前状态分析

### 2.1 后端现状

- 根工程是 Maven 多模块项目，入口在 `/Users/work/apex-scaffold/pom.xml`。
- 数据访问当前统一基于 JDBC 栈：dynamic-datasource + Druid + MyBatis-Plus + Flyway，配置主要在 `/Users/work/apex-scaffold/apex-interfaces/src/main/resources/application-dev.yml` 和 `/Users/work/apex-scaffold/apex-interfaces/src/main/resources/application-prod.yml`。
- MyBatis-Plus 拦截器、租户、数据权限和乐观锁已在 `/Users/work/apex-scaffold/apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/persistence/config/MybatisPlusConfig.java` 中集中配置。
- 迁移脚本当前集中在 `/Users/work/apex-scaffold/apex-interfaces/src/main/resources/db/migration/`，但现有结构是单目录单数据库视角。
- `apex-infrastructure/pom.xml` 已经依赖了 `flyway-core`、`flyway-mysql`、`mybatis-plus-jsqlparser`、`dynamic-datasource-spring-boot3-starter` 和 `druid-spring-boot-3-starter`，说明适配能力的基础设施已经具备。

### 2.2 前端现状

- 仓库内没有独立可运行的前端工程，前端模板集中在 `/Users/work/apex-scaffold/apex-generator/src/main/resources/templates/`。
- `frontendApi.ftl`、`frontendList.ftl`、`frontendForm.ftl`、`frontendRouteFragment.ftl` 已经固定了外部 `apex-admin-web` 的目录约定和动态路由接入方式。
- `CodeGenerator.generateFrontend(...)` 已支持输出前端 API、列表页、表单页和路由片段，说明“前端配置”最合适的落点是代码生成或部署配置生成，而不是在当前仓库内维护完整前端应用。
- 路由与菜单联动的约束已在模板中定义：`VIEW_REGISTRY`、`sys_menu.component`、`sys_menu.path` 和按钮权限码需要统一。

### 2.3 主要约束

- 前端不应接触数据库密码，敏感信息必须由环境变量或密钥中心注入。
- 首期不做运行时动态切换数据库连接池，避免把配置安全、连接生命周期和迁移流程一并复杂化。
- 不把所有数据库都做成完整原生方言适配，先保证公共子集可用，再预留扩展点。
- 现有工程里没有独立前端运行时，因此任何“前端页面配置”都必须明确为：配置采集页、部署配置导出、或生成器输入页。

## 3. Proposed Changes

### Task 1: 明确数据库适配边界与公共抽象

**Files:**
- Modify: `/Users/work/apex-scaffold/docs/architecture/database-design.md`
- Modify: `/Users/work/apex-scaffold/docs/DATA_SOURCE_SWITCH_USAGE.md`
- Modify: `/Users/work/apex-scaffold/docs/architecture/02-physical-architecture-detail.md`

- [ ] **Step 1: 在文档中定义首期支持范围**

```text
首期支持 MySQL + PostgreSQL。

兼容目标：连接、Flyway 迁移、分页、逻辑删除、租户、数据权限、基础 CRUD。
```

- [ ] **Step 2: 定义公共能力子集**

```text
只允许业务层依赖公共能力：标准 CRUD、常见分页参数、统一时间/字符串/数值类型、统一逻辑删除语义。
```

- [ ] **Step 3: 预留扩展点说明**

```text
通过 DatabaseDialect / DatabaseProfile / DatabaseMigrationResolver 之类的抽象隔离方言差异，其他数据库后续只做 SPI 接入，不承诺首期上线。
```

### Task 2: 设计前端数据库配置页与导出流程

**Files:**
- Modify: `/Users/work/apex-scaffold/apex-generator/src/main/resources/templates/frontendForm.ftl`
- Modify: `/Users/work/apex-scaffold/apex-generator/src/main/resources/templates/frontendList.ftl`
- Modify: `/Users/work/apex-scaffold/apex-generator/src/main/resources/templates/frontendApi.ftl`
- Modify: `/Users/work/apex-scaffold/apex-generator/src/main/resources/templates/frontendRouteFragment.ftl`
- Modify: `/Users/work/apex-scaffold/apex-generator/src/main/java/com/apex/scaffold/generator/CodeGenerator.java`

- [ ] **Step 1: 将数据库配置页定义为部署配置采集页**

```text
字段只包含：数据库类型、主机、端口、库名、用户名、连接参数、连接测试按钮、环境选择。
密码不在前端长期保存，只允许一次性交给后端生成部署配置。
```

- [ ] **Step 2: 为生成器增加数据库配置输出能力**

```text
在 CodeGenerator 中新增导出 YAML / env / docker-compose 片段的能力，用于把前端采集结果转换成部署可用配置。
```

- [ ] **Step 3: 统一前端页面与路由约定**

```text
继续沿用 apex-admin-web 的 src/api、src/views 和 VIEW_REGISTRY 约定，新增数据库配置页面的生成路径和路由片段输出。
```

### Task 3: 设计后端数据库配置模型与绑定方式

**Files:**
- Modify: `/Users/work/apex-scaffold/apex-interfaces/src/main/resources/application-dev.yml`
- Modify: `/Users/work/apex-scaffold/apex-interfaces/src/main/resources/application-prod.yml`
- Modify: `/Users/work/apex-scaffold/apex-interfaces/src/main/resources/application.yml`
- Modify: `/Users/work/apex-scaffold/apex-interfaces/src/main/resources/bootstrap.yml`
- Modify: `/Users/work/apex-scaffold/apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/persistence/config/MybatisPlusConfig.java`

- [ ] **Step 1: 增加统一数据库配置前缀说明**

```text
在 apex.db 或 apex.database 命名空间下定义数据库类型、迁移目录、是否启用方言适配器、连接元信息等配置。
```

- [ ] **Step 2: 保持 dynamic-datasource 作为连接执行层**

```text
仍使用 master/slave 或单数据源模式完成实际 JDBC 访问，不在首期引入运行时多库管理面板。
```

- [ ] **Step 3: 明确 PostgreSQL 兼容配置要求**

```text
在配置说明中列出 PostgreSQL 所需驱动、分页方言、标识符转义、时间类型和默认 schema 规则。
```

### Task 4: 按数据库类型拆分 Flyway 迁移目录

**Files:**
- Modify: `/Users/work/apex-scaffold/apex-interfaces/src/main/resources/db/migration/V1__init_schema.sql`
- Create or Modify: `/Users/work/apex-scaffold/apex-interfaces/src/main/resources/db/migration/mysql/`
- Create or Modify: `/Users/work/apex-scaffold/apex-interfaces/src/main/resources/db/migration/postgresql/`
- Modify: `/Users/work/apex-scaffold/apex-example/src/test/resources/db/example-migration/V1__example_user.sql`
- Modify: `/Users/work/apex-scaffold/apex-example/src/test/resources/db/example-migration/V2__sys_operation_log.sql`

- [ ] **Step 1: 将公共初始化语义拆成数据库无关部分**

```text
抽出通用表结构、通用字段和初始化顺序规则，避免 MySQL 专有语法继续作为唯一基线。
```

- [ ] **Step 2: 为 MySQL 与 PostgreSQL 分别准备迁移目录**

```text
使用不同目录或不同版本分支组织方言 SQL，确保库类型不同但迁移版本可审计。
```

- [ ] **Step 3: 保持测试迁移和主迁移策略一致**

```text
示例测试目录也要按数据库类型组织，避免生产和测试出现不同初始化路径。
```

### Task 5: 补齐类型映射、分页与 SQL 方言差异说明

**Files:**
- Modify: `/Users/work/apex-scaffold/apex-generator/src/main/java/com/apex/scaffold/generator/CodeGenerator.java`
- Modify: `/Users/work/apex-scaffold/apex-generator/src/main/resources/templates/entity.ftl`
- Modify: `/Users/work/apex-scaffold/apex-generator/src/main/resources/templates/po.ftl`
- Modify: `/Users/work/apex-scaffold/docs/architecture/database-design.md`

- [ ] **Step 1: 明确 Java-to-SQL 类型映射表**

```text
补充 MySQL / PostgreSQL 在字符串、时间、布尔、数值、自增主键和 JSON 字段上的映射差异。
```

- [ ] **Step 2: 统一分页语义**

```text
对外仅暴露统一分页模型，内部由方言适配分页 SQL，避免页面层知道数据库差异。
```

- [ ] **Step 3: 处理关键 SQL 差异**

```text
列出 ILIKE、LIMIT/OFFSET、RETURNING、序列/自增、大小写敏感和标识符引号的差异处理规则。
```

### Task 6: 定义验收测试与风险控制

**Files:**
- Modify: `/Users/work/apex-scaffold/apex-infrastructure/pom.xml`
- Modify: `/Users/work/apex-scaffold/apex-interfaces/pom.xml`
- Modify: `/Users/work/apex-scaffold/apex-example/src/test/java/com/apex/example/integration/BaseIntegrationTest.java`
- Modify: `/Users/work/apex-scaffold/docs/architecture/database-design.md`
- Modify: `/Users/work/apex-scaffold/docs/DATA_SOURCE_SWITCH_USAGE.md`

- [ ] **Step 1: 定义首期验收测试矩阵**

```text
MySQL：连接、迁移、CRUD、分页、逻辑删除、租户查询。
PostgreSQL：连接、迁移、CRUD、分页、逻辑删除、租户查询。
```

- [ ] **Step 2: 定义配置导出验证**

```text
验证前端配置页导出的 YAML/env 是否能直接驱动后端启动，而不需要手工补字段。
```

- [ ] **Step 3: 定义失败回退与安全检查**

```text
当数据库类型不支持、迁移目录缺失或密码配置不完整时，必须在启动前失败，并给出明确错误信息。
```

## 4. Assumptions & Decisions

1. 首期只承诺 MySQL 与 PostgreSQL，其他数据库只预留 SPI/适配器接口，不进入首期验收。
2. 前端页面的角色是“部署配置采集与导出”，不是运行时数据库管理控制台。
3. 密码不通过前端长期保存，敏感信息仍由环境变量或密钥系统注入。
4. Flyway 迁移按数据库目录分流，避免单套 SQL 同时兼容所有方言造成不可维护。
5. 业务层只面向公共能力子集，不把数据库方言泄漏到 Controller、Service 或 Domain 层。
6. 现有动态数据源、MyBatis-Plus、Druid 和 Flyway 继续复用，不引入全新的 ORM 或连接管理方案。

## 5. Verification

1. 文档自检：确认方案与现有仓库结构一致，所有提到的文件路径都能在当前仓库中找到。
2. 配置验证：检查前端导出的数据库配置能否对应当前 `application-dev.yml` / `application-prod.yml` 的结构，并明确新的命名空间。
3. 迁移验证：核对 MySQL 与 PostgreSQL 的迁移目录、版本号和表结构是否可独立执行。
4. 集成验证：首期以 MySQL 与 PostgreSQL 分别跑通启动、Flyway、CRUD、分页、逻辑删除和租户查询作为通过标准。
5. 回归验证：确认生成器输出的前端页面仍符合 `apex-admin-web` 的 `src/api`、`src/views` 和动态路由接入约定。

## 6. Implementation Notes for Executors

- 优先阅读：`/Users/work/apex-scaffold/apex-generator/src/main/java/com/apex/scaffold/generator/CodeGenerator.java`、`/Users/work/apex-scaffold/apex-interfaces/src/main/resources/application-dev.yml`、`/Users/work/apex-scaffold/apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/persistence/config/MybatisPlusConfig.java`、`/Users/work/apex-scaffold/apex-interfaces/src/main/resources/db/migration/V1__init_schema.sql`。
- 实施顺序建议：先定配置模型和迁移目录，再补生成器和前端模板，最后补测试与文档。
- 不要把方案扩成运行时多数据库控制台；那会把部署配置、密钥管理、动态迁移和连接生命周期混在一起，首期风险过高。
- 如果需要加入更多数据库，建议在首期完成后通过新增方言包、迁移目录和验证矩阵继续扩展，而不是在同一轮内把所有数据库一次性铺开。
