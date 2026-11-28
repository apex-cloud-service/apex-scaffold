# Apex Scaffold SVG 架构图表使用指南

> 本索引登记部分既有 SVG 和 Mermaid 源文件；编号不是文件总数，登记不代表图表验证通过。

本目录包含 Apex Scaffold 项目的既有 SVG 图表和 `sequence/` 下的 Mermaid 源文件。部分图表保留历史设计，接口、配置和运行边界应以当前代码及配套文档为准。第一阶段 Maven 分组的当前入口为 128（聚合）、129（parent）与 95（生产依赖）；三种关系不得混用，管理组不是运行服务。

---

## 📊 图表总览

### 图表分类

| 分类           | 数量  | 图表编号       | 用途说明             |
| ------------ | --- | ---------- | ---------------- |
| 核心架构图        | 3张  | 01-03      | DDD分层、部署拓扑、技术选型 |
| 业务流程图        | 4张  | 04-07      | 登录认证、权限校验、工作流、文件上传 |
| 基础设施图        | 5张  | 08-12      | 数据库、缓存、限流、数据权限、安全 |
| 部署与微服务治理图    | 6张  | 13-18      | Podman/K8s、CI/CD、Nacos、Zipkin、Gateway |
| 前端技术栈与响应式编程图 | 5张  | 19-23      | Vue 3、Axios、WebFlux、i18n、OpenAPI |
| 监控可观测性图      | 2张  | 24-25      | Prometheus/Grafana、Loki日志 |
| 高级架构与技术栈图    | 5张  | 26-29      | 容灾、消息队列、全文搜索、技术栈总览 |
| 登记范围 | 部分登记 | 01-29、84-86、92 | 其他图表见架构索引和时序图索引 |

---

## 🎨 图表特性

### 设计原则

1. **深色企业级配色**: 使用专业的深色配色方案
   - 主品牌色: #4f46e5 (深靛蓝)
   - 应用层: #0891b2 (青色)
   - 领域层: #059669 (深绿)
   - 基础设施: #d97706 (琥珀)
   - 文字色: #0f172a / #475569

2. **跨平台兼容**: 移除所有 CSS 变量,使用静态颜色值
   - ✅ 支持 Chrome、Firefox、Safari、Edge
   - ✅ 支持 VSCode、Obsidian、Typora
   - ✅ 支持 GitHub、GitLab、Notion 在线预览
   - ✅ 支持打印输出和PDF转换

3. **核对实现边界**: 技术栈版本以 `pom.xml` 和实际配置为准
   - 图内数量、性能指标和部署拓扑可能属于历史记录或设计目标
   - 图表存在不表示对应服务已部署或联调通过
   - 当前统一授权契约见下方入口，旧图不覆盖全部当前行为

4. **专业视觉**: SVG矢量图形,高清缩放无损
   - 1400×700\~900 标准画布尺寸
   - 32px标题、14px小标题、11px正文、10px代码
   - 8px圆角、2px边框、统一间距

---

## 📂 图表清单

### 01-03 核心架构图

| 编号 | 文件名                               | 标题            | 说明                                 |
| -- | --------------------------------- | ------------- | ---------------------------------- |
| 01 | `01_logical_architecture.svg`     | DDD四层逻辑架构     | 历史分层设计；图内 31 模块等统计保留为旧快照，当前 Maven 数量见 128，运行边界见系统架构 §1.2。 |
| 02 | `02_physical_architecture.svg`    | 网络物理部署架构      | 五层拓扑(客户端/DMZ/应用/数据/监控),端口见各服务 application.yml    |
| 03 | `03_technical_architecture.svg`   | 全栈技术选型全景图     | 60+技术组件,Spring Boot 3.5.15 / Vue 3.5.13 |

### 04-07 业务流程图

| 编号 | 文件名                              | 标题         | 说明                     |
| -- | -------------------------------- | ---------- | ---------------------- |
| 04 | `04_login_authentication_flow.svg`         | JWT登录认证流程  | Access+Refresh Token双Token机制 |
| 05 | `05_permission_check_flow.svg`   | RBAC权限校验流程 | 三层权限模型,5张关联表          |
| 06 | `06_workflow_approval_flow.svg`  | 三级审批工作流    | DAG引擎,回退/转办/委派/挂起      |
| 07 | `07_file_upload_flow.svg`        | 文件分片上传流程   | 5MB分片,秒传/断点续传/MinIO存储  |

### 08-12 基础设施图

| 编号 | 文件名                                   | 标题        | 说明                     |
| -- | ------------------------------------- | --------- | ---------------------- |
| 08 | `08_database_er_diagram.svg`          | 数据库ER图    | 40+表结构,Flyway V1-V58完整迁移历史(mysql 为准) |
| 09 | `09_multi_level_cache_architecture.svg` | 多级缓存架构    | Caffeine L1 + Redis L2,80-95%命中率 |
| 10 | `10_rate_limit_circuit_breaker.svg`   | 限流熔断机制    | Sentinel+Resilience4j,三档熔断策略 |
| 11 | `11_data_permission_filter.svg`       | 数据权限过滤    | @DataScope AOP + MyBatis-Plus SQL注入 |
| 12 | `12_security_protection_system.svg`   | 安全防护体系    | 7层防护:防重放/幂等/XSS/SQL注入/审计日志 |

### 13-18 部署与微服务治理图

| 编号 | 文件名                                      | 标题                   | 说明                            |
| -- | ---------------------------------------- | -------------------- | ----------------------------- |
| 13 | `13_docker_compose_local_dev.svg`        | Podman Compose本地开发环境 | 一键启动完整技术栈,健康检查+数据持久化          |
| 13 | `13_docker_compose_local_dev_v2.mmd/.svg` | Podman Compose本地开发环境（v2） | 2026-11-28 BE-071 重绘：现行主栈/可选 Profile/infra 栈实测拓扑（v1 为单体时代旧口径，保留不改） |
| 14 | `14_kubernetes_production_deployment.svg` | Kubernetes生产部署架构     | HPA自动伸缩,ConfigMap/Secret,滚动发布 |
| 15 | `15_cicd_pipeline.svg`                   | CI/CD流水线架构           | GitHub Actions,安全扫描+多环境部署     |
| 16 | `16_nacos_service_discovery.svg`         | Nacos服务注册与发现         | 服务注册+配置中心,3节点Raft集群          |
| 17 | `17_zipkin_distributed_tracing.svg`      | Zipkin分布式链路追踪        | Micrometer Tracing,10%采样率    |
| 18 | `18_spring_cloud_gateway.svg`            | Spring Cloud Gateway  | 动态路由+限流熔断+跨域CORS             |

### 19-23 前端技术栈与响应式编程图

| 编号 | 文件名                                    | 标题             | 说明                            |
| -- | -------------------------------------- | -------------- | ----------------------------- |
| 19 | `19_frontend_vue3_architecture.svg`    | 前端Vue3组件架构     | Composition API + Pinia + Element Plus |
| 20 | `20_axios_request_interceptor.svg`     | Axios请求拦截与封装   | 请求/响应拦截器,JWT自动续期,防重复提交        |
| 21 | `21_webflux_reactive_programming.svg`  | WebFlux响应式编程    | Reactor 3,Mono/Flux操作符,线程池隔离   |
| 22 | `22_i18n_internationalization.svg`     | 国际化i18n多语言支持   | MessageSource + Vue-i18n,错误码国际化 |
| 23 | `23_openapi_documentation.svg`         | OpenAPI文档生成     | SpringDoc + Swagger UI在线调试     |

### 24-25 监控可观测性图

| 编号 | 文件名                                         | 标题                    | 说明                      |
| -- | ------------------------------------------- | --------------------- | ----------------------- |
| 24 | `24_prometheus_grafana_monitoring.svg`      | Prometheus+Grafana监控体系 | 66个监控面板,apex_*指标前缀,告警规则 |
| 25 | `25_loki_log_aggregation.svg`               | Loki日志聚合系统            | Fluent Bit采集,LogQL查询,7天保留 |

### 26-29 高级架构与技术栈图

| 编号 | 文件名                                         | 标题             | 说明                       |
| -- | ------------------------------------------- | -------------- | ------------------------ |
| 26 | `26_disaster_recovery_high_availability.svg` | 容灾与高可用架构       | 多副本+故障自愈+异地多活,99.95%可用性 |
| 27 | `27_rocketmq_message_queue.svg`             | RocketMQ消息队列    | 顺序/延迟/事务消息,幂等消费+死信队列    |
| 28 | `28_meilisearch_fulltext_search.svg`        | Meilisearch全文搜索 | CJK分词+拼音搜索,毫秒级响应        |
| 29 | `29_technology_stack_overview.svg`          | 技术栈总览与版本管理     | 60+组件,BOM管理,依赖冲突解决      |

### 当前认证与统一授权入口

| 文件 | 用途与边界 |
| --- | --- |
| [84_security_filter_chain.svg](84_security_filter_chain.svg) | 既有安全过滤器链图，运行时顺序以安全配置为准 |
| [85_auth_center_architecture.svg](85_auth_center_architecture.svg) | 既有认证中心架构图，主应用与认证中心是独立 Spring ApplicationContext |
| [86_token_lifecycle_management.svg](86_token_lifecycle_management.svg) | 既有 Token 生命周期图，认证中心刷新会撤销旧 Refresh Token 并签发新 Token 对 |
| [业务流程与统一授权时序](../architecture/business-flows.md) | 本轮按当前代码同步的登录、登出和远程授权流程；Mermaid 未重新渲染 |
| [时序图索引](sequence/README.md) | Mermaid 文件清单及旧权限缓存图的适用边界 |
| [统一授权配置](../05-operations/configuration.md#认证中心统一授权) | HTTPS、显式映射、读取账号 Token、缓存和失败策略 |

84–86 的以上 SVG 在 `sequence/` 下没有同名 Mermaid 源文件；同编号的其他 `.mmd` 是不同主题，不能互作生成源。30–39 的 OAuth2、SSO、Keycloak 图为既有专题设计，不能据此声称主应用登录已迁移到认证中心。图 31 的本地权限查询、1 小时缓存和图 67 的读快照降级不代表当前远程统一授权策略：统一授权默认关闭，启用后走 HTTPS，缓存默认关闭、TTL 为 0–30 秒，失败拒绝且不使用过期缓存或本地权限回退。

### 92 办公平台集成

| 编号 | 文件名                                         | 标题             | 说明                       |
| -- | ------------------------------------------- | -------------- | ------------------------ |
| 92 | `92_office_platform_architecture.svg`       | 办公平台集成架构      | 飞书/钉钉/企业微信,端口-适配器,消息/审批/日历/文档四大子域,多租户隔离 |

> 注：本清单当前仅完整登记至 29 号，30–83、87–91 号图表（工作流、多租户、缓存、消息、License 等）未在本清单逐项登记。

### 当前 Maven 管理结构与历史拓扑入口

| 编号 | 图源 / SVG | 关系与适用边界 |
| --- | --- | --- |
| 95 | [Mermaid](95_module_dependency_graph.mmd) / [SVG](95_module_dependency_graph.svg) | **35 个 JAR 的直接内部生产依赖**。当前均为 compile scope；SVG 用逐模块邻接清单表示全部 104 条边，排除 parent、聚合、版本管理、传递依赖、HTTP 调用、test 与第三方依赖。aggregation 仅依赖 common / api-contract，不依赖 workflow / office 业务 JAR。 |
| 128 | [Mermaid](128_maven_module_groups.mmd) / [SVG](128_maven_module_groups.svg) | **根 → 七管理组的 Maven 聚合**。6 个新 POM + 现有 services-bom，分组叶子为 9/6/4/11/2/4/1；44 个后代、45 个 reactor 项目含根、37 个 JAR、8 个 POM 含根；叶子物理嵌套于分组父目录。卡内清单不是展开的独立图节点。 |
| 129 | [Mermaid](129_maven_parent_inheritance.mmd) / [SVG](129_maven_parent_inheritance.svg) | **子项目 → 直接 parent**。22 个叶子迁移 parent，13 个保留 services-bom；api-contract / integration / boot-all 分别聚合在 shared / components / applications，直接 parent 仍为 services-bom。 |
| 93 / 94 | [历史系统图](93_overall_system_architecture.svg) / [历史部署图](94_deployment_topology.svg) | 微服务化迁移前视图，旧模块、端口与单库口径保留为历史；不作为当前部署证据，也不因 Maven 分组重绘。当前运行拓扑见[系统架构 §1.2](../architecture/system-architecture.md#12-当前运行拓扑系统架构图)。 |
| 107–127 | 下表 UML / ER / 流程图 | 第一阶段不改变类、表、业务状态机或时序；只核对与新管理分组有关的模块标签，不把管理 POM 画为领域类或运行服务。 |

第二阶段（2026-11-14）后所有叶子模块物理位于各自分组父目录之下，管理 POM 以 `<module>apex-xxx</module>` 聚合同目录叶子；生产坐标、JAR、依赖与服务拓扑不变。business 组聚合项目专属业务库 `apex-knowledge`；未来独立运行模块只归 services 聚合与继承，不重复登记到 business。新增 N 个项目后须重新递归统计，不能沿用 42/43 的历史基线。

历史图不因本次分组改写：`01_logical_architecture.svg` 图内“Maven模块: 全仓 31 个”等脚注是**历史快照**，保留原图不动；当前模块数量、聚合与 parent 关系分别以 128、129 和本节口径为准。93 / 94 的旧模块、端口与单库口径同样只作历史视图。

### 图表双源规范（重要）

本目录有单源 SVG、自动渲染双源和手写配对三种维护方式，不能仅凭同名 `.mmd` / `.svg` 就假定自动同源：

| 类型 | 载体 | 编辑方式 | 覆盖范围 |
| --- | --- | --- | --- |
| 手写设计稿 | `.svg`（自定义画布与 CSS 类） | 直接编辑 SVG 文本，保留历史定位 | 01–92 等历史设计稿，无同名 `.mmd` 源 |
| Mermaid 自动渲染双源 | `.mmd` 源 + Mermaid 导出的 `.svg` | 先改 `.mmd`，再用本地可用 mermaid-cli 重渲染该图 | 107–127 等已有 Mermaid 导出 SVG（可见 Mermaid 的类 / 布局标记） |
| 手写语义配对 | `.mmd` + 自定义 SVG 布局 | 两份分别维护相同节点、成员集合、箭头语义与说明；变更后核对内容并验收 SVG | 95、96、128、129；93–106 中其他图也需先检查载体，不保证全区间自动导出 |

本地已安装 mermaid-cli 时的单图重渲染示例（不要求下载工具）：

```bash
mmdc -i docs/diagrams/107_uml_auth_center_domain.mmd \
    -o docs/diagrams/107_uml_auth_center_domain.svg
```


#### 2026-11-16 批量补导出记录（BE-001）

`docs/diagrams/sequence/` 原有 30 个 `.mmd` 缺同名 `.svg`，已全部补齐，**68 个源图 100% 有导出件**（仓库 SVG 总数 217 → 247）。

```bash
# 只补缺导出件（幂等，已有的跳过）
bash scripts/utils/render-mermaid-svg.sh
# 强制重渲染全部
bash scripts/utils/render-mermaid-svg.sh -f
# 单图重渲染
bash scripts/utils/render-mermaid-svg.sh -f docs/diagrams/sequence/31_permission_check_sequence.mmd
```

渲染口径与踩坑（复现时必须知道）：

1. **版本用 10，不用 11**：mermaid-cli 11 对 `activate/deactivate` 嵌套、`&lt;`/`&gt;` 实体转义更严格，本仓 12 个历史源文件在 11 下解析失败、在 10 下正常。脚本默认 `MMDC_VERSION=10`，需要 11 时显式覆盖。
2. **6 个源文件做了语法归一化**（只动激活盒标记，参与者与消息文本一字未改）：
   - `78_auto_retry_mechanism_sequence`、`84_rate_limit_hotspot_sequence`、`90_login_biometric_mfa`、`93_rate_limit_sentinel`、`94_cache_consistency`、`95_message_mq_publish_consume`：删除不合法的 `activate X` / `deactivate X` 行并去掉箭头上的 `+`/`-` 标记（原文件激活盒嵌套不满足 Mermaid「后进先出」要求）。
   - `95_message_mq_publish_consume` 另有 2 处 `&lt;=` 写法会被解析成箭头，已改为 `≤`（其余 `&lt;`/`&gt;` 改为全角 `＜`/`＞`）。
3. 上述 5 张图因此**没有激活盒（activation bar）**，只剩默认生命线；内容语义完整，若要恢复激活盒需人工重排（登记 BE-021）。
4. 渲染通过**不等于**业务验证：SVG 仅用于文档展示，不改任何运行代码。

自动渲染双源以 `.mmd` 为语义源；手写配对需要同时更新两份。95 的 SVG 以邻接清单替代密集连线，与 `.mmd` 的 35 JAR / 104 条生产边对应；128 / 129 将叶子合并为清单卡片，并不把全 43 项目塞成一张节点图。不得只改一侧、将 parent 箭头作为 compile 依赖，或为本次 Maven 分组全仓重渲染历史图。SVG 可用本地 `rsvg-convert` 渲染后检查文本与连线；渲染通过不等于业务运行验证。

### 107–123 UML 类图 / ER 表图 / 模块结构图

以下图表同时提供 `.mmd` 源文件与 `.svg` 渲染产物，使用 `theme:base` + 企业四色规范。本节既有类、表与字段说明不是第一阶段 Maven 分组的重新验证结果；本次没有改变相关业务实现。

#### UML 类图（107–120）

| 编号 | 文件（`.mmd` / `.svg`） | 标题 | 覆盖域 | 说明 |
| -- | ------ | ---- | ------ | ---- |
| 107 | `107_uml_auth_center_domain` | auth 域 UML 类图 | auth | 10 类实体 + 值对象 + 领域服务；已删除 `kid/publicKey/privateKey/deviceInfo/toRecord/recoveryCodes` 等虚构成员，补齐 `accountId`、`PasswordRecoveryEntity` |
| 108 | `108_uml_workflow_domain` | workflow 域 UML 类图 | workflow | WorkflowDefinition/ProcessInstance/Task/ApprovalRecord + 4 仓储端口 + WorkflowTaskTransaction |
| 109 | `109_uml_office_domain` | office 域 UML 类图 | office | OfficeMessage/ApprovalProcess/CalendarEvent/DocumentInfo + 四端口(MessagePort/ApprovalPort/CalendarPort/DocumentPort) + AbstractOfficeClient |
| 110 | `110_uml_core_aggregate` | 核心聚合基类 UML | common | BaseEntity/BasePO/LogicalDeletion 基类 |
| 110b | `110b_uml_system_domain` | **system 域 UML 类图（新增）** | system | 17 类：User/Role/Permission/Dept/Menu/Tenant/Dict/Config/Job + 日志 + 安全策略 + TenantQuota VO |
| 119 | `119_uml_file_domain` | **file 域 UML 类图（新增）** | file | FileEntity/FileChunkEntity/FileShareEntity/DocumentEntity/ChartEntity + 5 仓储 + StorageService/StorageServiceProvider/TenantStatusPort |
| 120 | `120_uml_common_infrastructure` | **common 模块关键类 UML（新增）** | common | Result/ResultCode/ErrorCode/BusinessException/BaseException + TenantContext/DataScopeContext/DataSourceContext + BlockingIoSchedulers 六池 |

#### ER 表关系图（111–121）

| 编号 | 文件（`.mmd` / `.svg`） | 标题 | 覆盖域 | 说明 |
| -- | ------ | ---- | ------ | ---- |
| 111 | `111_er_core_tables` | 核心表 ER 图 | system | sys_user/sys_role/sys_permission/sys_menu/sys_dept/sys_tenant + 关联表 |
| 112 | `112_er_auth_center_tables` | auth 表 ER 图 | auth | system 库 OAuth2/JWT/登录安全表 + auth 库 auth_* 核心表（RBAC/会话/Token/TOTP/恢复/审计） |
| 113 | `113_er_workflow_tables` | workflow 表 ER 图 | workflow | WF_WORKFLOW_DEFINITION/PROCESS_INSTANCE/TASK/APPROVAL_RECORD/WF_SCHEDULER_LEASE 共 5 表 |
| 114 | `114_er_office_tables` | office 表 ER 图 | office | OFFICE_PLATFORM_APP/MESSAGE/APPROVAL/CALENDAR/DOCUMENT/NOTIFY/MEETING_ROOM 等 18 表 |
| 121 | `121_er_file_tables` | **file 域表 ER 图（新增）** | file | file_record/file_chunk/file_share/file_document/file_chart，V1–V7 迁移链 |

> 表数量为按 Flyway 迁移脚本（mysql）`CREATE TABLE` 语句实测去重统计（不含 undo_log/sys_domain_event_outbox 基础设施表；2026-11-22 BE-046 复测，口径与脚本见[对齐战役记录](../records/2026-11-22-alignment-campaign.md)）：system 100（含已拆分服务的历史遗留表；`audit_log` 已由 V12 删除，按 CREATE TABLE 口径仍计入）、auth 19、workflow 5、file 5、office 19（含 V9/V10 撤销编排表）、integration 10、knowledge 7，跨域去重后全仓唯一表名 142 张。

#### 模块结构图（97–106, 122–123）

| 编号 | 文件名 | 标题 | 覆盖模块 | 说明 |
| -- | ------ | ---- | -------- | ---- |
| 97–106 | `97–106_module_*.mmd` | 共享/业务模块结构图 | common/domain/infrastructure/application/interfaces/auth/workflow/example/generator/license | 既有四层包结构；101 / 103 的 application / interfaces 是 system 库内包，不是恢复退役叶子模块，100 的设施布局亦不代表单一父 POM。 |
| 122 | `122_module_file_structure.mmd` | **apex-file 模块结构图（新增）** | file | interfaces→application→domain→infrastructure 四层 + 五后端存储策略 |
| 123 | `123_module_system_structure.mmd` | **apex-system 模块结构图（新增）** | system | 四层 + office/governance/monitor/security 子包 + 45 实体域模型 |
| 124 | `124_aggregation_todo_architecture.mmd` | **查询聚合服务与统一待办架构图（新增）** | aggregation | 网关路由 / 自验 JWT / 舱壁 / 内部签名调用 workflow 与 office 待办端点 |
| 125 | `125_unified_todo_sequence.mmd` | **统一待办查询时序图（新增）** | aggregation | /api/v2/todo 双源并行聚合、单源降级、不缓存不合并语义 |
| 126 | `126_approval_cancel_state_machine.mmd` | **审批撤销幂等编排状态机（新增）** | office | PENDING/REMOTE_CANCELLED/COMPLETED/FAILED + 补偿重放（V9/V10 编排表） |
| 127 | `127_overview_bulkhead_sequence.mmd` | **概览聚合舱壁时序图（新增）** | system | 概览门面三源并行、短 TTL 缓存、请求合并与舱壁降级 |

### 130–145 当前口径补缺图（2026-11-22 BE-047 新增）

以下图表同时提供 `.mmd` 源文件与 `.svg` 导出件（mermaid-cli 10 渲染），覆盖此前四类全缺的域与关键链路；类名、端口、配置键均以当前代码为准，事实来源与证据路径见 [对齐战役记录](../records/2026-11-22-alignment-campaign.md)。

| 编号 | 文件（`.mmd` / `.svg`） | 图型 | 覆盖域 | 说明 |
| -- | ------ | ---- | ------ | ---- |
| 130 | `130_current_runtime_topology` | 系统架构 | 全仓 | 当前运行拓扑：8 标准运行模块 + job/masking/knowledge 可选 + 中间件；端口对齐 build-manifest.yaml；历史拓扑见 02/93/94 |
| 131 | `131_integration_functional_architecture` | 功能架构 | integration | connector/device 两子域四层 + 组件级开关（interfaces-enabled 等）+ WebClient/可选 MQTT |
| 132 | `132_integration_tables_er` | ER | integration | 10 张业务表（无物理外键，tenant_id+业务键逻辑关联；undo_log 不计入） |
| 133 | `sequence/133_iot_device_telemetry_command_sequence` | 时序 | integration | 遥测上行（影子乐观锁→规则引擎）与命令下行（幂等→ACK/TIMEOUT 兜底） |
| 134 | `134_connector_invoke_webhook_flow` | 业务流程 | integration | 出站调用（凭据解密→SPI 分发→审计）与 Webhook 入站（时间窗→验签→去重→落库→发布） |
| 135 | `135_knowledge_functional_architecture` | 功能架构 | knowledge | 摄取/检索/问答编排 + 三级能力降级开关 + REST 直调 hybrid |
| 136 | `136_knowledge_tables_er` | ER | knowledge | kb_* 7 表（space→document→chunk、conversation→message、租约与清理凭证） |
| 137 | `137_knowledge_ingestion_flow` | 业务流程 | knowledge | 上传→租约认领→Tika→分块→向量化→索引，含降级分支与对账 |
| 138 | `sequence/138_knowledge_rag_chat_sequence` | 时序 | knowledge | RAG SSE 问答：citations→delta→done/error，hybrid 降级链 |
| 139 | `139_masking_service_architecture` | 功能架构 | masking | 无持久化脱敏：启动配置策略、JWT 租户、JWKS 鉴权 |
| 140 | `140_boot_all_assembly_architecture` | 装配架构 | boot-all | 父上下文+按域子上下文装配六业务库（薄 jar，不含 knowledge） |
| 141 | `141_job_starter_service_module` | 模块架构 | job | starter 默认禁用装配 vs job-service 无状态九接口代理 |
| 142 | `142_seata_starter_modes` | 模式决策 | seata | AT/TCC/SAGA/XA 选择与共同约束（undo_log/三表/拒绝动态数据源/Outbox 守护） |
| 143 | `143_storage_starter_backends` | 模块架构 | storage | 五后端 Bean + SPI 第三方合并、重复 type 拒绝 |
| 144 | `144_office_message_flow` | 业务流程 | office | 站内信 WS 票据链、进程内提交事件推送、断线 HTTP 补偿 |
| 145 | `sequence/145_office_approval_cancel_sequence` | 时序 | office | 审批撤销同步两步编排，衔接 126 号状态机 |

> 豁免说明：shared 五个 infrastructure 能力 JAR 与 job/seata/storage starter 无业务表，不设 ER 图（141–143 为模块/模式图）；141 图覆盖 job 域。146 号后如需扩图按编号顺延。

#### 配色规范

所有 `.mmd` 文件统一使用 `theme:base` + 企业四色，按域分配主色：

| 域 | primaryColor | primaryBorderColor | 适用图表 |
| -- | ------------ | ------------------ | -------- |
| auth / common / 通用 | `#eef2ff` | `#4f46e5` (靛蓝) | 107, 110, 115–118, 120, 模块结构, sequence, 130 拓扑 |
| system / integration | `#ecfeff` | `#0891b2` (青) | 110b, 131–134 |
| workflow / office / knowledge | `#ecfdf5` | `#059669` (绿) | 108, 109, 135–138, 144, 145 |
| file / infrastructure / 支撑模块（masking/job/seata/storage/boot-all） | `#fffbeb` | `#d97706` (琥珀) | 119, 122 (file), 139–143 |
| ER 表图 | `#eff6ff` | `#3b82f6` (蓝) | 111–114, 121, 132, 136 |

#### 校验状态

- **第二阶段结构口径**：根聚合七管理组；44 个后代、45 个 reactor 项目含根、37 个 JAR、8 个 POM 含根，叶子物理嵌套于分组父目录。95 覆盖全部 37 个 JAR 的 109 条直接内部生产依赖，common 被 30 个 JAR 直接依赖、domain 被 19 个。
- **历史统计不作现状证明**：旧版 Handler / Service / Router / Repository / 表数量及全量 XML、Mermaid 渲染记录不是本次门禁结果；Maven 分组也不改变类或表的数量。93 / 94 的退役模块名是历史图内容，不应以全局替换清除。
- **本次静态核对范围**：95 / 128 / 129 的 SVG XML 与源内容配对；本地 librsvg 测得文本包围盒无画布 / 所属卡片越界、无文本包围盒重叠。107 等 UML 未因 parent 分组重绘。**视觉验收未完成**（当前环境无法查看渲染图像）；文本几何检查不是视觉验收，更不是 Java 构建 / 启动或完整门禁结果。

---

## 💡 使用建议

### 场景1: 新人入职 - 快速了解系统

**推荐顺序**:
1. `01_logical_architecture.svg` - 理解DDD四层分层设计
2. `03_technical_architecture.svg` - 了解技术栈全貌
3. `02_physical_architecture.svg` - 理解部署拓扑
4. `04_login_authentication_flow.svg` - 理解登录认证流程
5. `19_frontend_vue3_architecture.svg` - 理解前端架构(前端开发)

### 场景2: 生产部署 - 上线环境配置

**推荐顺序**:
1. `13_docker_compose_local_dev.svg` - 理解本地开发环境
2. `14_kubernetes_production_deployment.svg` - 理解K8s生产部署
3. `15_cicd_pipeline.svg` - 理解CI/CD流水线
4. `24_prometheus_grafana_monitoring.svg` - 配置监控大盘
5. `26_disaster_recovery_high_availability.svg` - 理解容灾方案

### 场景3: 性能优化 - 排查瓶颈问题

**推荐顺序**:
1. `09_multi_level_cache_architecture.svg` - 检查缓存命中率
2. `10_rate_limit_circuit_breaker.svg` - 检查限流熔断状态
3. `17_zipkin_distributed_tracing.svg` - 分析调用链路性能
4. `21_webflux_reactive_programming.svg` - 优化响应式流水线
5. `24_prometheus_grafana_monitoring.svg` - 查看监控指标

### 场景4: 前后端协作 - 理解交互流程

**推荐顺序**:
1. `19_frontend_vue3_architecture.svg` - 前端组件架构
2. `20_axios_request_interceptor.svg` - 请求拦截流程
3. `18_spring_cloud_gateway.svg` - API网关路由
4. `04_login_authentication_flow.svg` - 登录认证完整流程
5. `23_openapi_documentation.svg` - API文档规范

### 场景5: 安全审计 - 系统安全检查

**推荐顺序**:
1. `12_security_protection_system.svg` - 7层安全防护体系
2. `04_login_authentication_flow.svg` - JWT认证机制
3. `05_permission_check_flow.svg` - RBAC权限模型
4. `11_data_permission_filter.svg` - 数据权限过滤
5. `02_physical_architecture.svg` - 网络安全分区

---

## 🔧 查看工具推荐

### 在线查看
- **GitHub/GitLab**: 代码仓库直接预览
- **VS Code**: 内置SVG预览
- **浏览器**: 直接拖入Chrome/Firefox/Safari

### 本地编辑器
- **Obsidian**: 双向链接笔记,支持SVG内嵌
- **Typora**: Markdown编辑器,支持SVG预览
- **VS Code**: 安装"SVG Preview"扩展

### 专业工具
- **Figma**: 导入SVG进行二次设计
- **Adobe Illustrator**: 专业矢量图编辑
- **Inkscape**: 开源SVG编辑工具

### PDF转换
```bash
# 使用 librsvg (推荐)
rsvg-convert -f pdf -o output.pdf input.svg

# 使用 Inkscape
inkscape input.svg --export-pdf=output.pdf

# 使用 Chrome headless
chrome --headless --print-to-pdf=output.pdf input.svg
```

---

## 📝 技术规格

### 文件规格

| 属性     | 值                  | 说明              |
| ------ | ------------------ | --------------- |
| 格式     | SVG 1.1            | 标准SVG格式         |
| 编码     | UTF-8              | 支持中文字符          |
| 画布尺寸   | 1400×700\~900      | 16:9或2:1宽高比      |
| 文件大小   | 5\~15 KB           | 平均10KB,高效加载     |
| 颜色模式   | RGB                | 静态颜色值,无CSS变量    |
| 字体     | sans-serif / monospace | 系统默认字体,确保跨平台兼容性 |

### 颜色规范

| 用途     | 颜色代码      | 说明    |
| ------ | --------- | ----- |
| 标题文字   | #0f172a   | 深灰色   |
| 次要文字   | #64748b   | 中灰色   |
| 正文文字   | #475569   | 浅灰色   |
| 代码文字   | #2563eb   | 蓝色    |
| 主品牌色   | #4f46e5   | 深靛蓝   |
| 应用层    | #0891b2   | 青色    |
| 领域层    | #059669   | 深绿色   |
| 基础设施层  | #d97706   | 琥珀色   |
| 背景色    | #eff6ff   | 浅蓝背景  |
| 边框     | #3b82f6   | 蓝色边框  |

### 文字规范

| 元素   | 字号   | 字重   | 字体          |
| ---- | ---- | ---- | ----------- |
| 标题   | 32px | 700  | sans-serif  |
| 小标题  | 14px | 500  | sans-serif  |
| 正文   | 11px | 400  | sans-serif  |
| 代码   | 10px | 400  | monospace   |
| 图表标题 | 14px | 600  | sans-serif  |

---

## ⚡ 快速查找

### 按关键词查找

| 关键词          | 相关图表编号         |
| ------------ | -------------- |
| Vue 3        | 19, 20, 22     |
| Spring Boot  | 01, 03, 21     |
| Podman       | 13, 14         |
| Kubernetes   | 14, 26         |
| 监控           | 17, 24, 25     |
| 安全           | 04, 05, 11, 12 |
| 缓存           | 09             |
| 消息队列         | 27             |
| 数据库          | 08             |
| CI/CD        | 15             |
| 网关           | 18             |
| 全文搜索         | 28             |

---

## 📞 支持与反馈

- **项目仓库**: [GitHub/GitLab地址]
- **技术文档**: [../02-architecture/architecture-index.md](../02-architecture/architecture-index.md)
- **问题反馈**: 提交Issue或联系架构团队
- **版本历史**: 查看 `ARCHITECTURE_INDEX.md` 更新记录

---

**文档维护者**: 架构团队  
**最后更新**: 2026-09-10  
**文档版本**: v3.0  
**登记状态**: 部分登记；不声明全量验证通过

---

© 2026 Apex Team · 图表包含历史设计，当前契约以代码、配置和配套文档为准
