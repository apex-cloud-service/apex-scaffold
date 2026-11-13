# 交付范围与基线

> **版本**: v1.0
> **核对日期**: 2026-10-30
> **基线提交**: `b8e5733`（`main`）
> **项目版本**: 1.3.1（根 [pom.xml](../../../pom.xml)）
> **当前结构同步**: 2026-11-13（第 2 节七组/递归模块口径；历史基线与验证记录不追溯改写）
> **维护者**: Apex Team

本文档定义一次交付的边界：包含哪些模块、依赖哪些运行组件、数据库处在什么版本、哪些内容**不在**本次交付内。范围和基线变化时先改本文档。

---

## 1. 交付形态

仓库同时支持三种交付形态，选择依据是构建档位与部署方式，两者相互独立（详见[部署指南](../../05-operations/deployment.md) §3.1）。

| 形态 | 构建档位 | 运行组件 | 适用场景 |
| --- | --- | --- | --- |
| 单体演示包 | `-Pbasic` / `-Pstandard`（`SERVICE_MODULE=apex-example`） | 单个可执行 jar | 本地评估、功能演示 |
| 微服务部署 | `-Penterprise` 或任意档位 + 分服务镜像 | 网关 + 六个业务服务 + 只读查询聚合服务 | 生产与私有化交付 |
| 源码交付 | 三档均支持 | 客户自行构建 | 标准版 / 企业版源码授权 |

> 档位决定功能范围与依赖裁剪，不决定服务数量。`basic` 档仍以 `apex-example` 作为聚合装配模块，不提供微服务拓扑。

---

## 2. 范围内的 Maven 模块

根 POM 直接聚合七个管理组；第一阶段递归模块 42 个、含根 reactor 43 个项目（8 个 POM、35 个既有 JAR）。当前分组与父链见 [README 分组说明](../../../README.md#maven-分组与父链)，既有生产依赖边界不因 parent 分组而改变。

| 管理组 | 聚合范围 | 交付影响 |
| --- | --- | --- |
| `apex-shared-parent` | 9 个共享基础/领域/契约/设施 JAR | 新 parent 仅构建期，无可执行产物 |
| `apex-components-parent` | 六个脚手架能力库 | 原有业务库与能力不变 |
| `apex-starters-parent` | 四个存储/授权/任务/事务 starter | 原有普通依赖 JAR 不变 |
| `apex-services-bom` | 10 个运行模块 | 标准发布仍为八服务，job/masking 仍可选 |
| `apex-applications-parent` | example、boot-all | 示例/组合装配行为不变，boot-all 保持薄 JAR |
| `apex-tools-parent` | license-tools、generator、tools-deployment、tools-build | 不扩大工具交付或授权范围 |
| `apex-business-parent` | 第一阶段为空 | 只建立项目业务 JAR 管理入口，没有新增业务库、服务或数据库 |

七组均继承根，22 个既有叶子直接 parent 迁移；13 个 services-bom 子继承不变。api-contract / integration / boot-all 分别聚合在 shared / components / applications，仍继承 services-bom，其余 10 个运行模块聚合与继承均在 services。所有叶子目录、资源和 target 仍在根级；分组 parent 不是运行服务，也不代表新增交付 JAR。

后续业务库只聚合并继承 business，领域专属适配留在该库，通用框架复用其他支撑模块；独立运行模块聚合并继承 services，不重复登记、不自动纳入现有八服务发布范围。后续数量依实际新增项目更新，本阶段 42 / 43 不包含尚未确认的项目业务。

以下按既有模块职责列出交付口径。

| 分类 | 模块 | 是否随交付包发布 |
| --- | --- | --- |
| 共享基础 | `apex-common` | 作为依赖 |
| 共享领域 | `apex-domain` | 作为依赖 |
| 共享设施 | `apex-infrastructure-persistence`、`-cache`、`-messaging`、`-remote`、`-integration` | 作为依赖 |
| 兼容聚合 | `apex-infrastructure` | 作为依赖（旧坐标） |
| 跨服务契约 | `apex-api-contract` | 作为依赖 |
| 分组父 POM | 六个 `apex-*-parent` 与复用的 `apex-services-bom` | 仅构建期，不进可执行交付清单；Maven 构件/源码交付须保留所需 parent 链 |
| 业务库 | `apex-system`、`apex-auth`、`apex-workflow`、`apex-office`、`apex-file`、`apex-integration` | 作为依赖 |
| 运行服务 | `apex-gateway`、`apex-system-service`、`apex-auth-service`、`apex-workflow-service`、`apex-office-service`、`apex-file-service`、`apex-integration-service`、`apex-aggregation-service` | **可执行 jar** |
| 服务增强 | `apex-job-spring-boot-starter`、`apex-seata-spring-boot-starter`、`apex-job-service`、`apex-masking-service` | jar（前两者为依赖，后两者可选部署） |
| 对象存储 | `apex-storage-spring-boot-starter` | 作为依赖 |
| 授权 | `apex-license-spring-boot-starter` | 作为依赖 |
| 授权工具 | `apex-license-tools` | **不发布**（卖方专属） |
| 示例与工具 | `apex-example`、`apex-generator` | 示例 jar / 构建工具 |
| 组合部署 | `apex-boot-all` | 普通薄 JAR，不是现有发布脚本的可执行服务 JAR |
| 运维工具 | `apex-tools-deployment` | 以 **`tools/local-deployment.jar`** 随既有发布脚本交付，不作为业务运行服务 |
| 构建管理 | `apex-tools-build` | **不发布**（模块注册表与构建/打包/部署编排入口，见 [构建发布管理模块方案](../plans/build-management-plan.md)） |

> 发布脚本实际打包的服务清单为网关、六个业务服务与只读查询聚合服务（`SERVICES` 数组共 8 项），见 [release.sh](../../../scripts/release/release.sh) 的 `SERVICES` 数组。`apex-job-service` 与 `apex-masking-service` 需按需单独构建部署。分组 parent 不加入该数组，部署工具仍以 `tools/local-deployment.jar` 随包交付。

源码或未来 Maven 构件交付须包含根及所需分组 parent POM；客户源码包剥离卖方 `apex-license-tools` 时同步 tools parent 的聚合清单，不能恢复卖方工具以满足构建。构建仍从总根加载固定环境后使用 `mvnd -pl <叶子列表> -am`。本节更新的是结构和范围，不代表新结构的构建、性能或运行验收已经通过；历史基线与旧数字保留在第 7 节记录。

---

## 3. 服务端点基线

端口取自各服务 `application.yml` 的默认值；容器编排会做端口映射，见 [.env.example](../../../.env.example)。

| 服务 | 模块 | 业务端口 | 管理端口 | 说明 |
| --- | --- | --- | --- | --- |
| 网关 | `apex-gateway` | 8080 | 复用 8080 | 统一入口，路由见 [application.yml](../../../apex-gateway/src/main/resources/application.yml) |
| 认证服务 | `apex-auth-service` | 8081 | 未单独配置 | 服务 `/auth/v1/**`、`/oauth2/**`、`/.well-known/**` |
| 系统服务 | `apex-system-service` | 8082 | 8083 | 系统管理、监控与其余接口兜底 |
| 工作流服务 | `apex-workflow-service` | 8093 | 未单独配置 | 工作流引擎与审批（2026-11-07 起，原 8083 与 system 管理端口冲突已消除） |
| 办公服务 | `apex-office-service` | 8084 | 未单独配置 | 含站内信 HTTP 与 WebSocket |
| 文件服务 | `apex-file-service` | 8085 | 未单独配置 | 含对象存储与文件分享 |
| 集成服务 | `apex-integration-service` | 8094 | 8097 | 连接器与设备接入 |
| 聚合服务 | `apex-aggregation-service` | 8098 | 未单独配置 | 只读聚合 workflow 与 office 内部待办，无 DB/迁移/事务 |
| 任务代理 | `apex-job-service` | 8095 | — | 可选，固定 system 地址 |
| 脱敏服务 | `apex-masking-service` | 8096 | — | 可选，无持久化 |

> 工作流服务业务端口为 8093（2026-11-07 调整前为 8083，与 system 管理端口同号，靠 Compose 宿主映射规避）。

---

## 4. 数据库基线

五个业务域各自独立数据库与独立 Flyway 版本链，**不可按全仓最大版本号统一编排**。

| 业务域 | 数据库 | 迁移目录 | 当前最新版本 |
| --- | --- | --- | --- |
| system | `apex_system` | [mysql](../../../apex-system/src/main/resources/db/migration/mysql/) / [postgresql](../../../apex-system/src/main/resources/db/migration/postgresql/) | V72 |
| auth | `apex_auth` | [mysql](../../../apex-auth-service/src/main/resources/db/migration/mysql/) / [postgresql](../../../apex-auth-service/src/main/resources/db/migration/postgresql/) | V59 |
| workflow | `apex_workflow` | [mysql](../../../apex-workflow-service/src/main/resources/db/migration/mysql/) / [postgresql](../../../apex-workflow-service/src/main/resources/db/migration/postgresql/) | V5 |
| file | `apex_file` | [mysql](../../../apex-file-service/src/main/resources/db/migration/mysql/) / [postgresql](../../../apex-file-service/src/main/resources/db/migration/postgresql/) | V6 |
| office | `apex_office` | [mysql](../../../apex-office-service/src/main/resources/db/migration/mysql/) / [postgresql](../../../apex-office-service/src/main/resources/db/migration/postgresql/) | V7 |

**基线注意**：上表按工作区当前文件统计。其中 system V70～V72 尚未提交到 Git（见 [风险登记册](./risk-register.md) R-01），以提交后的版本为准；已执行迁移禁止修改或重排，破坏 Flyway checksum 会导致存量环境启动失败。

---

## 5. 运行依赖基线

| 组件 | 必需性 | 说明 |
| --- | --- | --- |
| MySQL 8.x 或 PostgreSQL | 必需 | 按迁移目录选择，各业务域数据库独立（system/auth/workflow/file/office/integration） |
| Redis 6.2+ / 7.x | 必需（认证、系统、网关） | 会话、缓存、限流、幂等键 |
| Nacos | 可选 | 关闭时须把网关 `lb://` 路由改为明确 HTTP 地址 |
| RocketMQ | 可选 | 默认关闭，企业版消息能力 |
| Sentinel | 可选 | 限流规则可由 Nacos 下发 |
| XXL-JOB | 可选 | 执行器默认禁用 |
| Seata TC | 可选 | 分布式事务默认关闭 |
| 对象存储 | 可选 | 默认 Local，支持 MinIO / TOS / OSS / 七牛 |
| Keycloak | 可选 | OIDC 登录，默认关闭 |

> “可选”指服务可在不接入时启动，不等于该能力已交付。启用条件与拒绝行为以对应运维文档为准。

---

## 6. 明确不在本次交付范围

以下内容在仓库中**不存在实现**或**不作为交付物**，对外承诺时不得列入：

1. **管理后台前端源码**。仓库不包含 `apex-admin-web`；Compose 中的前端是独立镜像。
2. **License 发放能力**。`scripts/license/`、`apex-license-tools` 为卖方专属，不进入发行包。
3. **新增管理 parent 的可执行服务产物**。七个管理组只提供 POM，既有部署工具仍以 `tools/local-deployment.jar` 随包交付，不是新增业务服务。
4. **私钥与真实凭据**。`config/license/private.key` 被 `.gitignore` 排除；交付包只允许出现公钥。
5. **未提交的本地改动**。工作区中未提交的代码、迁移与配置不属于 `b8e5733` 基线。

### 6.1 需求条目的实现边界

[功能增强需求方案](../function-enhancement-requirements.md) 中部分条目**尚未落地**，交付说明与验收时须按未实现处理：

| 需求条目 | 现状 |
| --- | --- |
| BIZ-003 统一状态机 | 仅工作流定义图校验与租户状态机存在，无通用状态机组件 |
| BIZ-004 批量操作与异步任务 | 无统一批量请求/结果结构；任务类异步能力分散在生成器与健康导出 |
| BIZ-005 导入导出中心 | 只有 `ExcelExportService` 与服务用户导出，无导入、无预览校验、无错误行回传 |
| SYS-001 岗位/职级模型 | 有用户、部门、角色；无岗位、职级实体 |
| SYS-003 字典多语言 | 字典类型/数据 CRUD 存在，未发现多语言字段 |
| COMP-001 通知中心 | 有站内信与 SSE 通知，无统一通知中心编排 |
| INT-002 国产化适配 | 未发现达梦、人大金仓、openGauss 等适配代码 |
| INT-004 规则引擎集成 | 未发现 Drools、EasyRules 或规则引擎接入 |

完整逐条判定见 [需求追踪矩阵](./requirements-traceability.md)。

---

## 7. 基线与变更记录

| 日期 | 基线 | 变更 |
| --- | --- | --- |
| 2026-10-30 | `b8e5733` + 工作区未提交 V70～V72 | 建立本文档；登记数据库基线缺口 |
| 2026-11-12 | 工作区（未提交） | 新增只读查询聚合服务 `apex-aggregation-service`（端口 8098，无 DB）；模块数 35→36，`SERVICES` 数组 7→8 |
| 2026-11-13 | 第一阶段分组实施（未提交） | 根直接 36 项收拢为七组，新增六个 parent，递归 42 模块/含根 43 项，原 35 个 JAR 与八服务发布集合不变；同步当前说明，验证结果另据实际执行 |

---

**维护者**: Apex Team
**下次核对**: 交付前或基线提交变化时
