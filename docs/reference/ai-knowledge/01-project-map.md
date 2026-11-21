# 01 · 项目地图

> 采集：**2026-11-19**（源码 + `pom.xml` + `git log` 实测）。回答「有几个项目、怎么连、东西在哪」。

## 1. 三个活跃仓库

| 代号 | 路径 | 一句话定位 | 版本 | 主栈 |
| --- | --- | --- | --- | --- |
| **apex** | `/Users/work/apex-scaffold` | Java 后端基座：网关 + 六业务服务 + 任务代理/脱敏 + 可选知识库 + 组合部署档 | 1.3.1 | Java 21 / Spring Boot 3.5.15 / WebFlux / MyBatis-Plus / Flyway |
| **web** | `/Users/work/apex-admin-web` | 管理后台前端，前后端分离独立工程 | 1.2.0 | Vue 3.5 / Vite 6 / TS 5.7 / Pinia / Element Plus / ECharts |
| **cold** | `/Users/work/jsd-work-code/cold-chain` | 客户项目：全国冷链数智公共服务平台 | 未打 tag | Java 11 / Spring Boot 2.7.18 / 17 模块单体 + 五端前端 |

`/Users/work` 下其余 50 个左右目录（RuoYi、yudao、keycloak、SpringBoot-Labs 等）是参考资料，**不在活跃范围**。

三张入口卡：`apex-scaffold/MEMORY-CARD.md`、`apex-admin-web/MEMORY-CARD.md`、`cold-chain/MEMORY-CARD.md`。

## 2. 依赖关系

```text
 apex-admin-web  ──HTTP(/api/v1, /api/v2, /admin-api/v1, /api/workflow, /oauth2/*)──▶  apex-gateway
                                                                                        │
                        ┌───────────────────────────────────────────────────────────────┤
                        ▼                ▼               ▼              ▼                ▼
                  auth-service     system-service  workflow-service  office-service  file-service
                     :8081            :8082            :8093           :8084          :8085
                                                                        │
                                              job-service :8095 ────────┘（固定代理 system）
                                              masking-service :8096（无持久化，可选）
                                              apex-boot-all：单进程组合档（C9，不含网关）

 cold-chain  ✕ 与上述无任何代码依赖，独立交付线
```

- 只有 **apex ↔ web** 是强耦合对：后端 Router 路径、DTO 字段、`@RouterOperation` 即前端契约源；改契约必须同日评估 web 的 `src/api` 与页面。
- 网关是唯一入口，`/internal/**` 对外 403。

## 3. 服务 / 端口 / 库 / 迁移

| 模块 | 启动类 | 端口 | 数据库 | 迁移最高版本 | 迁移位置 |
| --- | --- | --- | --- | --- | --- |
| `apex-gateway` | `GatewayApplication` | 8080 | `apex_gateway`（访问日志，可选） | — | — |
| `apex-auth-service` | `AuthServiceApplication` | 8081 | `apex_auth`（dev `apex_auth_dev`） | **V65** | `apex-services-bom/apex-auth-service/src/main/resources/db/migration` |
| `apex-system-service` | `SystemServiceApplication` | 8082（管理端口缺省 8083） | `apex_system` | **V91** | **`apex-components-parent/apex-system`**/…/db/migration |
| `apex-workflow-service` | `WorkflowServiceApplication` | 8093（2026-11-07 起，原 8083） | `apex_workflow` | V6 | `apex-services-bom/apex-workflow-service` |
| `apex-office-service` | `OfficeServiceApplication` | 8084 | `apex_office` | V10 | `apex-services-bom/apex-office-service` |
| `apex-file-service` | `FileServiceApplication` | 8085 | `apex_file` | V7 | `apex-services-bom/apex-file-service` |
| `apex-integration-service` | `IntegrationServiceApplication` | 8094（管理端口缺省 8097） | `apex_integration` | V3 | **`apex-components-parent/apex-integration`**/…/db/migration |
| `apex-knowledge-service` | `KnowledgeServiceApplication` | 8100 | `apex_knowledge`（可选档） | V2 | `apex-services-bom/apex-knowledge-service`/…/db/migration |
| `apex-aggregation-service` | `AggregationServiceApplication` | 8098 | 无（只读聚合，无 DB/迁移/事务） | — | — |
| `apex-job-service` | `JobServiceApplication` | 8095 | 无（固定代理 system） | — | — |
| `apex-masking-service` | `MaskingServiceApplication` | 8096 | 无 | — | — |
| `apex-boot-all` | `BootAllApplication` | 缺省 8080（未覆写） | 单进程连多库 | 复用各组件迁移 | — |

> 合计 **MySQL 141 + PostgreSQL 141 = 282 个已跟踪迁移**（11-16 实测；11-14 起增 system V91 知识库菜单种子与 knowledge V1，11-15～11-16 增 auth V65 与 knowledge V2，双方言配对）；其中 `apex-integration` 的 V1–V3 已入库跟踪。
> 迁移**必须留在自己服务/业务库**的 `src/main/resources/db/migration/{mysql,postgresql}`，双方言齐备；跨服务加迁移会破坏 Flyway 历史。11-14 两级布局后模块目录带分组父目录，`maintain.py` 的迁移键相应变为 `分组/模块`（如 `apex-components-parent/apex-system`）。

## 4. 模块地图（根直接聚合 7 组、递归 44 个模块、含根 45 个 reactor 项目，2026-11-14）

Maven 结构：根 POM 直接聚合 `apex-shared-parent`(9) / `apex-components-parent`(6) / `apex-starters-parent`(4) / `apex-services-bom`(11) / `apex-applications-parent`(2) / `apex-tools-parent`(4) / `apex-business-parent`(1)，共 6 个新 POM + 1 个复用 BOM；8 个 POM 含根、37 个 JAR。**聚合、parent 继承、物理目录与生产依赖是不同关系**：2026-11-14 第二阶段已把 37 个叶子目录物理移入各自分组父目录（如 `apex-components-parent/apex-system`），分组 POM 以同目录 `<module>apex-xxx</module>` 聚合；22 个叶子迁移 parent、13 个保留 services-bom 继承（含 api-contract / integration / boot-all 三个例外，`relativePath` 跨两级指向 `../../apex-services-bom/pom.xml`）。详见根 `README.md`「Maven 分组与父链」与 `docs/diagrams/128`、`129`。

| 分组 | 模块 | 职责 |
| --- | --- | --- |
| 共享基础 | `apex-common` | 响应/异常/上下文/工具/审计/ID/调度器/阻塞 I/O 调度/安全（JWT、JWKS 组件）/SPI |
| 共享领域 | `apex-domain` | 基础实体、通用事件、端口与 Gateway 契约 |
| 共享设施 | `apex-infrastructure-persistence`、`-cache`、`-messaging`、`-remote`、`-integration` | 持久化、缓存、消息、远程调用、第三方集成 |
| 旧坐标兼容 | `apex-infrastructure` | 仅聚合上述 5 个能力 JAR，**无实现源码** |
| 跨服务契约 | `apex-api-contract` | 内部 API 与共享 DTO（C2 已清理死代码） |
| 服务父 POM | `apex-services-bom` | 11 个运行模块的聚合与 parent；api-contract / integration / boot-all 仍继承它但分别聚合在 shared / components / applications |
| 业务库 | `apex-system`、`apex-auth`、`apex-workflow`、`apex-file`、`apex-office`、`apex-integration`、`apex-knowledge` | 各域领域 + 应用 + 专属基础设施（C7 起提供组件入口自动装配）；`apex-knowledge` 含知识空间/文档摄取（Tika 解析、分块、向量化）/Meilisearch 混合检索/RAG 问答编排 |
| 运行模块 | `apex-gateway` + 六个业务 `*-service` + `apex-aggregation-service` + `apex-knowledge-service`（可选，8100） | 启动、配置、装配 |
| 组合部署 | **`apex-boot-all`** | 单进程装配全部业务组件（C9，basic/示例档；标准交付为八服务拆分） |
| 服务增强 | `apex-job-spring-boot-starter`、`apex-seata-spring-boot-starter`、`apex-job-service`、`apex-masking-service` | XXL 执行器、多模式分布式事务、任务代理、脱敏 |
| 集成域 | **`apex-integration`** | 连接器框架（E1）：`IntegrationConnector` / `ConnectorDefinition` / `ConnectorLoader` / `ConnectorExecutionService` + `V1__init_integration_schema.sql`（迁移 V1–V3 已入库跟踪） |
| 查询聚合 | **`apex-aggregation-service`** | 跨源统一待办**只读**聚合（8098）：无 DB / 迁移 / 事务，仅以 `WebClient` 调 workflow 与 office 内部接口，单源降级（舱壁隔离）；11-12 `9f375e46` 已入库（18 个跟踪文件） |
| 对象存储 | `apex-storage-spring-boot-starter` | 五后端（Local/MinIO/TOS/OSS/七牛）自动装配 |
| 授权 | `apex-license-spring-boot-starter`、`apex-license-tools` | 许可证校验与发照 |
| 示例/工具 | `apex-example`、`apex-generator` | 开发样例、代码生成器 |
| 离线运维 | `apex-tools-deployment` | 部署密钥校验、TOTP 迁移包、文档链接检查、本地文档服务（不参与运行期装配） |
| 构建管理 | **`apex-tools-build`** | `build-manifest.yaml` 模块注册表（单一事实来源）+ `bin/apex.sh` 编排入口；`BuildManifestSyncTest` 校验与根 POM/打包插件/`release.sh` 一致（不参与运行期装配，不进发布包） |

**`apex-system` 仍是四层合一巨库**（`interfaces`/`application`/`domain`/`infrastructure` 同包，Java 包名 `com.apex.scaffold.*`）。方案 D07/C4 决定将 interfaces 层迁往 `apex-system-service`，**尚未执行**。

## 5. 文档入口（按用途；`docs/` 下共 736 个 Markdown，含各期报告）

| 想知道 | 去哪 |
| --- | --- |
| 怎么跑起来 | `README.md` §快速开始、`docs/00-getting-started/quickstart.md` |
| 协作约束（唯一权威） | `AGENTS.md` |
| 架构总览与演进 | `docs/02-architecture/architecture-overview.md`、`architecture-evolution.md` |
| 当前执行批次 | `docs/08-project-management/plans/unified-auth-tenant-platform-remediation-plan.md` |
| 路由登记 | `docs/03-api/API_ROUTES.md` |
| 开放平台接入契约 | `docs/03-api/open-platform-integration.md` |
| 开发规范（注释/JavaDoc/Lombok） | `docs/04-development/development-standards.md` |
| 部署与配置 | `docs/05-operations/deployment.md`、`configuration.md` |
| 各批次测试报告 | `docs/06-testing/jwt-unification/2026MMDD-*/report.md`、`docs/06-testing/README.md` |
| 文档目录规范 | `docs/08-project-management/DOCS_STRUCTURE_STANDARD.md` |
| 前端工程 | `apex-admin-web/MEMORY-CARD.md` → `AGENTS.md` → `docs/` |
| 冷链客户项目 | `cold-chain/MEMORY-CARD.md` → `docs/memory/` → 外仓 `cold-chain-docs` |
