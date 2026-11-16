# Apex Scaffold 商业化完善方案（v3 · 2026-09-09）

> **文档更新**：2026-09-09更新，基于v1.2.0代码库重新评估  
> **核心发现**：项目已完成基础通用化（约90%），核心模块已通用化  
> **本文档定位**：作为「通用后端开发脚手架打包出售」的差距分析与优化路线图  
> **状态**：✅ 已更新（原详细方案未入库）

---

## ⚠️ 文档说明

本文档描述的是基于旧版本（v1.1.4）的分析结果。**最新的商业化方案**请参见：
- `.trae/documents/商业化脚手架重构方案.md`（私有目录原址，原文未入库）（详细实施方案）
- `.trae/documents/业务代码删除清单.md`（私有目录原址，原文未入库）（代码清理清单）

**核心结论更新**：
- ✅ 项目已基本完成通用化（90%）
- ✅ 核心模块（User/Role/Permission/Workflow/File）已通用
- ⚠️ 需优化：部分实体说明、微信集成标注、文档描述

---

## 0. 结论速览（TL;DR）

**总体判断：项目已达到"可售卖"基线（约 85/100），后端能力在同类脚手架中属第一梯队，但缺少「配套前端」这一最大卖点，直接拉低采购转化率。**

| # | 维度 | 状态 | 等级 |
|---|------|------|------|
| 1 | 后端功能完整性（系统管理/安全/三高/可观测性/部署） | ✅ 完善 | 无缺口 |
| 2 | 商业化机制（License 三档/双轨许可/FeatureGate） | ✅ 完善 | 无缺口 |
| 3 | 文档与测试体系 | ✅ 完善 | 无缺口 |
| 4 | **前端配套工程（Vue3 管理后台）** | ❌ 缺失 | 🔴 P0 |
| 5 | **example 演示覆盖面** | ⚠️ 单薄（仅用户 CRUD） | 🔴 P0 |
| 6 | **发布收尾（v1.1.4 未 tag、工作树 dirty）** | ⚠️ 进行中 | 🔴 P0 |
| 7 | **备份/恢复脚本** | ⚠️ 仅有文档无脚本 | 🔴 P0 |
| 8 | 一键初始化/脚手架模板化 | ❌ 缺失 | 🟡 P1 |
| 9 | Sentinel 规则动态下发 / 前端代码生成 / 采购材料包 / 灰度发布 | ⚠️ 部分 | 🟡 P1 |
| 10 | DB 读降级 / 分布式 bulkhead / 质量门禁 / 工作流 / 官网物料 | ❌ 缺失 | 🟢 P2 |

**核心结论**：优先补齐 P0 四项（前端 + example 增强 + 发布收尾 + 备份脚本），预计 3~4 周，即可形成"后端 + 前端 + 文档 + 部署"的完整可售商品；P1 用于拉开与竞品差距、支撑客单价；P2 按客户反馈择机实施。

---

## 1. 评估基线与方法

- **评估对象**：当前工作树（v1.1.4 进行中），非历史 tag。
- **评估方法**：代码/资源/脚本核验 + 工作记忆交叉验证 + 与主流竞品（RuoYi、JeecgBoot、pig、mall 系等）功能面横向对比。
- **评估视角**：买方视角（"拿到能不能直接用、好不好用"）+ 卖方视角（"能否支撑客单价与差异化"）。
- **评分口径**：后端能力 90/100，商品完整度 85/100，差异化 75/100，**总评 85/100（良好，达售卖线，未到碾压线）**。

---

## 2. 现状盘点（已具备能力清单）

以下能力已核验存在，方案不再重复投入：

### 2.1 架构与工程
- DDD 五层（common/domain/infrastructure/application/interfaces）+ 纯 WebFlux 函数式端点（无 servlet）
- Java 17 + Spring Boot 3.5 + MyBatis-Plus + MySQL/Redis/RocketMQ
- 代码生成器 apex-generator（13 个 FTL 模板：entity/po/mapper/repository/Impl/converter/service/handler/dto×2/路由片段/迁移片段/测试骨架）
- Flyway 唯一迁移集（当前 V1~V15，apex-interfaces）；CORS/多数据源/主从示例/Snowflake

### 2.2 系统管理（P0 全集）
- 菜单树+按钮权限、字典、参数配置、定时任务（自研 Cron + Reactor 动态调度）、登录日志、图形验证码（防重放）、在线用户、部门数据权限、操作审计（@OperationLog + OperationLogAspect）

### 2.3 安全与合规
- JWT + Spring Security + RBAC；登录保护（暴力破解封禁）、IP 限流封禁升级、防重放、SQL 注入防护、输入校验、数据脱敏（示例）
- CI 集成 OWASP Dependency Check（security-scan.yml）；等保友好设计

### 2.4 三高能力（2026-08-28 补强后已到位）
- 缓存：多级缓存（Redisson+Caffeine）、击穿 Single-Flight（InFlightRequestMerge）、穿透空值标记+TTL 抖动
- 全局响应超时（504 + REQUEST_TIMEOUT，SSE 豁免）
- 熔断：Sentinel（39 FlowRule + Degrade + System）+ Resilience4j 双轨
- 限流：接口/IP/用户/热点四维 + 自适应 + 本地降级
- 可靠性：Outbox 补偿、消费幂等（IdempotentAspect）、DLQ 自动处理、优雅停机、信号量并发控制

### 2.5 可观测性与部署
- 指标 apex_api_*（MetricsWebFilter）、Prometheus + 10 条告警规则 + Grafana 14 面板大盘、Zipkin 链路、TraceId 贯穿日志、钉钉异常告警
- 部署三方式（java -jar app.sh/systemd、Containerfile 多阶段、Compose）+ infra 9 组件一键栈 + k8s deployment.yaml + 健康检查双端点
- 环境变量全覆盖、prod 强制校验（JWT≥32 字节、Druid keepAlive、文件上传联动等）

### 2.6 商业化机制
- 三档 Profile（basic/standard/enterprise）+ apex-build.properties 产物标识
- License 签名（RSA，规范化 JSON）+ 容错降级 + FeatureGateAspect（starter 装配）+ 私钥工具 + 发放脚本 + LICENSE_MANAGEMENT.md
- 双轨许可证（MIT + LICENSE-COMMERCIAL.md）

### 2.7 质量与文档
- 测试 247 个测试文件 / 全量 1827 测试方法全绿；契约测试（RouterOpenApiCoverageTest、Knife4jIntegrationContractTest、ProductionReadinessConfigurationTest 等）
- docs/ 25 篇（QUICK_START/ARCHITECTURE/CONFIGURATION/BEST_PRACTICES/DEPLOYMENT/CODEGEN/SPI/JMH/LICENSE_MANAGEMENT/TROUBLESHOOTING 等）

---

## 3. 差距分析总表

> 🔴 P0 = 直接影响售卖与采购决策；🟡 P1 = 拉开竞争力、支撑客单价；🟢 P2 = 差异化/按需。

| ID | 差距项 | 现状 | 缺口影响 | 优先级 |
|----|--------|------|---------|--------|
| G01 | **Vue3 管理前端工程** | 仅有静态 HTML 控制台 `static/admin/index.html` | 竞品标配前端；采购方无法直接交付管理界面，转化率与客单价直接受损 | 🔴 P0 |
| G02 | **example 模块演示覆盖** | 仅用户 CRUD 10 个用例 | 买方无法低成本验证 MQ/缓存/OSS/SSE/Excel/租户/审计/三高等卖点 | 🔴 P0 |
| G03 | **v1.1.4 发布收尾** | 工作树 dirty（AuditLog→@OperationLog 重构进行中）、未 tag | 交付物版本不可复现；CHANGELOG 与 git 不一致 | 🔴 P0 |
| G04 | **备份/恢复脚本** | DEPLOYMENT.md 有章节，无 `scripts/backup/*.sh` | 企业运维硬需求缺失，交付即被质疑 | 🔴 P0 |
| G05 | **一键初始化（模板化新建项目）** | 需手动拷贝+改名+改包名 | 开发者体验差；竞品均有 init 方式 | 🟡 P1 |
| G06 | **Sentinel 规则 Nacos 动态下发** | 静态规则文件 | 生产规则调整需重启；企业级卖点缺失 | 🟡 P1 |
| G07 | **前端代码生成** | generator 仅后端 13 模板 | CRUD 前后端无法一次生成，开发效率卖点打折 | 🟡 P1 |
| G08 | **采购材料包**（安全白皮书/压测报告/API 规范/Demo 环境） | 无独立材料 | 中大型企业采购流程（安全评审/性能评审）无法通过 | 🟡 P1 |
| G09 | **灰度发布（k8s canary + HPA + Ingress）** | 仅 deployment.yaml | 运维演进能力展示不足 | 🟡 P1 |
| G10 | **DB 故障读降级** | 遗留项（业务语义耦合） | 极端容灾卖点缺失 | 🟢 P2 |
| G11 | **分布式 bulkhead（信号量舱壁）** | 仅本地信号量 | 多实例场景隔离能力 | 🟢 P2 |
| G12 | **质量门禁（覆盖率/静态扫描）** | jacoco 有配置无门槛；无 Checkstyle/SpotBugs 门禁 | 工程质量可量化性不足 | 🟢 P2 |
| G13 | **工作流引擎**（Flowable 审批） | 无 | OA/审批类采购硬需求；Enterprise 档差异化 | 🟢 P2（决策点） |
| G14 | **官网/落地页 + 在线 Demo** | README 中官网为 example.com 占位 | 无转化载体 | 🟢 P2 |
| G15 | **信创数据库适配**（达梦/人大金仓） | 仅 MySQL | 政企/金融行业采购受限 | 🟢 P2 |
| G16 | **License 在线激活/机器指纹绑定** | 仅离线文件激活 | 防滥用能力可加强 | 🟢 P2 |

---

## 4. 🔴 P0 方案详述（建议 3~4 周完成，形成完整可售商品）

### 4.1 G01 Vue3 管理前端工程（最大缺口，投入最大）

**现状**：`apex-components-parent/apex-system/src/main/resources/static/admin/index.html` 为单文件静态控制台（用户/菜单/字典/参数/任务/登录日志 + 验证码登录），可演示但不可交付为"管理后台"。

**目标**：交付独立前端工程 `apex-admin-vue3`（随包分发，Standard/Enterprise 档随源码交付），与后端 API 全量打通。

**技术选型**：
- Vue 3.5 + Vite 6 + TypeScript + Pinia + Vue Router + Element Plus + Axios + ECharts（仪表盘）
- 不引入重型组件库（无 vben admin 等超大依赖），保持脚手架"轻量可控"定位；代码自研，无版权风险

**功能清单（与后端 P0 系统管理一一对应）**：
| 模块 | 页面 | 后端接口 |
|------|------|---------|
| 认证 | 登录（验证码）、登出、Token 刷新 | /api/v1/auth/* |
| 工作台 | 仪表盘（指标卡片 + ECharts） | /api/v1/dashboard（新增薄接口） |
| 用户 | 列表/新增/编辑/删除/重置密码/角色分配/部门树 | /admin-api/v1/users* |
| 角色 | 列表/权限分配（菜单树勾选） | /admin-api/v1/roles* |
| 菜单 | 树表 CRUD、按钮权限、路由动态生成 | /admin-api/v1/menus* |
| 部门 | 树表 CRUD（数据权限联动） | /admin-api/v1/depts* |
| 字典 | 类型/数据双表 CRUD | /admin-api/v1/dicts* |
| 参数 | CRUD | /admin-api/v1/configs* |
| 定时任务 | CRUD/启停/手动触发/执行日志 | /admin-api/v1/jobs* |
| 审计 | 操作日志查询、登录日志查询 | /admin-api/v1/audit-logs、login-logs |
| 在线用户 | 列表/强退 | /admin-api/v1/online-users |
| 个人中心 | 修改密码/个人信息 | /api/v1/profile* |

**关键设计**：
- 权限模型：菜单表驱动前端路由（后端返回可访问菜单树 → 动态注册路由）+ 按钮级 `v-permission` 指令
- 请求封装：统一 Result 解析、错误码映射、401 拦截刷新、traceId 透传
- 主题：浅色/深色切换，与后端 i18n 键对齐（zh/en）
- 构建产物 `dist/` 可被后端 `static/admin/` 托管（替换现有静态控制台），或独立 Nginx 部署——两种交付方式文档化

**交付物**：`apex-admin-vue3/` 工程 + `docs/FRONTEND_README.md`（联调/部署/权限接入指南）+ 与后端契约测试对齐（OpenAPI 生成 TS 类型可选）

**工作量**：核心页面 12~15 人日（单人 2~3 周）；含联调与文档 3~4 周。

**验收标准**：
- [ ] 与后端全量接口联调通过（Postman 集 + 自动化冒烟脚本）
- [ ] 菜单树动态路由 + 按钮权限指令生效
- [ ] 验证码登录、401 自动刷新、强退联动
- [ ] `dist` 由后端静态托管可一键预览；独立部署模式文档可复现
- [ ] 深色主题 + 中英文案切换

---

### 4.2 G02 example 模块增强（低成本高回报）

**现状**：apex-example 仅演示用户 CRUD（分页/校验/错误码/脱敏/逻辑删除/@FeatureGate），测试 10 例。

**目标**：把 example 变成"能力地图"——每个核心能力一个可运行的最小示例，买方 30 分钟跑通全链路。

**新增示例（对应现有资产，全部有后端支撑，无需新造轮子）**：

| 示例 | 演示能力 | 复用资产 |
|------|---------|---------|
| 缓存示例 | 多级缓存读写 + 击穿合并 | MultiLevelCacheService.getWithLoader / InFlightRequestMerge |
| MQ 示例 | 发消息/消费/幂等/Outbox | MqProducerService / IdempotentAspect / Outbox 表 |
| 文件示例 | 上传/下载（Local/MinIO/S3 抽象） | FileStorageService（或现有存储 SPI） |
| SSE 示例 | 实时通知 | NotificationSseHandler |
| Excel 示例 | 导入导出 | ExcelExportService / EasyExcel |
| 多租户示例 | 租户隔离 CRUD | 现有租户上下文 |
| 审计示例 | @OperationLog 写审计、查询 | OperationLogAspect |
| 限流/熔断示例 | 接口限流、熔断降级 | 现有切面/规则 |
| 灰度特性开关示例 | @FeatureGate 双档行为 | FeatureGateAspect |
| 登录保护示例 | 暴力破解封禁演示 | 登录保护模块 |

**交付物**：example 新增 8~10 个 handler/路由 + 集成测试用例（≥25 例）+ `docs/EXAMPLE_GUIDE.md`（示例清单与运行路径）

**工作量**：5~7 人日。

**验收标准**：
- [ ] 每个示例有独立端点、文档注释、集成测试
- [ ] `mvn -o test` 全量绿（含新增）
- [ ] EXAMPLE_GUIDE 给出"5 分钟逐个跑通"路径

---

### 4.3 G03 v1.1.4 发布收尾（当天可完成）

**现状**：工作树 dirty（OperationLog 重构迁移中，删除旧 AuditLog 注解/AOP/PO，涉及 10+ 文件）；git v1.1.3-13；CHANGELOG 已写 [1.1.4]。

**动作**：
1. 完成 OperationLog 体系迁移（确认旧 AuditLog* 类全部删除、引用清零、bias 门禁零命中）
2. 全量 `mvn -o test`（1827 例基线）绿
3. 三档构建冒烟（basic/standard/enterprise 产物可启动）
4. `scripts/release/release.sh` 走发布流程 → tag `v1.1.4`
5. 更新 README 徽章/版本引用、docs 中版本号残留

**验收标准**：
- [ ] git tag v1.1.4 存在，工作树干净（或仅剩待提交说明）
- [ ] 全量测试绿 + 三档启动冒烟通过
- [ ] CHANGELOG 与 tag 一致

---

### 4.4 G04 备份/恢复脚本（1~2 人日）

**现状**：DEPLOYMENT.md 第 13 章有备份恢复说明，但 `scripts/` 无实际脚本。

**交付物**（`scripts/backup/`）：
- `db-backup.sh`：mysqldump 全库 + 保留 N 天轮转 + 可选上传 OSS/MinIO + 备份前一致性校验
- `db-restore.sh`：指定备份文件恢复（先建库/校验，再导入）
- `app-backup.sh`（可选）：日志/上传文件目录打包
- `README.md`：用法 + cron 示例 + 恢复演练步骤

**验收标准**：
- [ ] 本机对 apex_data 实测备份→清空→恢复全链路成功
- [ ] 脚本 bash -n 通过、错误分支（无权限/磁盘满）有明确报错

---

## 5. 🟡 P1 方案详述（建议 1~2 月，拉开竞争力）

### 5.1 G05 一键初始化脚本（2~3 人日）

**目标**：`scripts/init/new-project.sh` —— 输入项目名/包名/端口/库名，复制脚手架 → 全局替换命名 → 生成初始配置 → 输出启动指引。让"买来即用"成为现实。

**要点**：
- 支持交互式参数 + 默认值；复制后清理 example 演示数据、重置版本号
- 生成 `.sdkmanrc`、环境变量模板、README 首屏（项目名替换）
- 提供"替换后验证清单"（构建 + 启动 + 冒烟）

**验收标准**：从一个全新目录按脚本走完 → 新项目名构建启动成功、bias/旧名零命中。

### 5.2 G06 Sentinel 规则 Nacos 动态下发（3~5 人日）

**现状**：Sentinel 规则静态编码（43 条 FlowRule、30 条 DegradeRule 等），改规则需发版重启。

**方案**：
- 引入 `sentinel-datasource-nacos`，FlowRule/DegradeRule/ParamFlowRule 数据源接入 Nacos
- 保留静态规则为兜底默认；提供规则 JSON 模板与配置说明
- 控制台/文档更新；Enterprise 档启用、Basic/Standard 走静态（档位隔离）

**验收标准**：Nacos 修改规则 JSON → 30s 内规则热生效（集成测试/文档步骤可复现）；静态兜底在 Nacos 不可用时仍生效。

### 5.3 G07 前端代码生成（3~5 人日）

**目标**：generator 扩展 —— 后端生成后，按表结构追加生成 Vue3 页面（列表页 + 表单弹窗 + API 封装），输出到 `apex-admin-vue3/src/views/xxx`。

**要点**：新增 4~5 个 FTL（list/api/form/route 片段）；生成结果可直接被前端工程消费；文档说明接入步骤。

**验收标准**：以 sys_config 表跑通"后端 + 前端页面一次生成 → 手动微调 → 页面可用"。

### 5.4 G08 采购材料包（3~5 人日，多为文档与测试工作）

面向中大型企业采购流程的材料（这些是"能不能进供应商库"的门票）：

| 材料 | 内容 | 来源 |
|------|------|------|
| 安全白皮书 | 认证/授权/传输/存储/审计/依赖漏洞管理/等保自评 | 现有实现提炼 |
| 性能报告 | 核心链路压测（wrk/k6 场景：登录/列表/写操作 × 并发梯度），QPS/P99/错误率 + 硬件说明 | 新增压测脚本 `scripts/bench/` + 报告模板 |
| API 规范文档 | 统一响应/错误码/鉴权/幂等/分页约定 | 现有约定整理 |
| 交付清单 | 源码包结构/环境要求/上线 checklist/服务等级说明 | 现有文档整合 |
| 在线 Demo 环境 | 可部署的演示实例（前端+后端+infra 栈） | 前端完成后搭一套 |

**验收标准**：5 份材料在 docs/sales/ 目录齐备，每份有版本号与维护人；压测报告含可复现命令。

### 5.5 G09 灰度发布（2~3 人日）

**方案**：k8s 目录扩展 —— `hpa.yaml`（基于 apex_api 指标/CPU）、`canary-deployment.yaml` + 说明（Ingress 流量分割或 Argo Rollouts 两种方式）、`ingress.yaml`（路由 + 限流注解示例）；DEPLOYMENT.md 增补"升级回滚与灰度"章节。

**验收标准**：清单文件 `kubectl apply --dry-run=client` 校验通过；文档步骤可复现。

---

## 6. 🟢 P2 方案详述（可选差异化，按客户反馈择机）

### 6.1 G10+G11 极端容灾（DB 读降级 + 分布式 bulkhead）
- DB 读降级：沿用 DegradeFallbackService 模式，缓存读降级为本地降级数据 + 标记，读多写少场景的兜底（3~4 人日）
- 分布式 bulkhead：Redisson 信号量封装 `@ConcurrencyControl` 分布式版（2~3 人日）
- 均为 Enterprise 档卖点，工作量可控

### 6.2 G12 质量门禁
- jacoco 覆盖率门槛（如 line ≥ 70% 起步、核心模块 ≥ 80%）接入 CI；新增 Checkstyle/SpotBugs（或 Spotless 格式化门禁）；security-scan 结果失败阻断

### 6.3 G13 工作流引擎（决策点，见第 11 章）
- 若上：Flowable 7 与 WebFlux 的适配成本需评估（Flowable 为阻塞模型，可独立线程池桥接或选择 Flowable 异步执行器）；范围为"审批模型 + 发起 + 审批 + 我的待办"最小闭环，Enterprise 档专属

### 6.4 G14 官网/落地页 + 在线 Demo
- 静态营销单页（特性/版本对比/价格/文档入口/演示环境按钮），可托管 EdgeOne Pages/CloudStudio；文档站（VitePress）收纳 docs/

### 6.5 G15 信创数据库适配
- MyBatis-Plus 对达梦/人大金仓兼容层 + 方言配置 + 迁移 SQL 兼容说明；以"适配验证报告"交付，非全量改造（3~5 人日）

### 6.6 G16 License 在线激活
- 增加机器指纹绑定（网卡/CPU 指纹哈希）+ 在线激活 API（可选，配合授权服务器）；离线激活保留为降级

---

## 7. 执行路线图

```
Phase 0（当天~2 天）        Phase 1（P0，第 1~4 周）          Phase 2（P1，第 5~8 周）        Phase 3（P2，按需）
─────────────              ────────────────────              ─────────────────────         ──────────────────
G03 发布收尾 v1.1.4        G04 备份脚本(先，独立交付)          G06 Sentinel 动态规则          G10/G11 极端容灾
(阻塞其他交付)              G01 前端工程(主线程，2~3 周)       G07 前端代码生成               G12 质量门禁
                           G02 example 增强(并行)             G05 一键初始化                 G13 工作流(决策点)
                                                            G08 采购材料包(压测并行)        G14 官网/Demo
                                                            G09 灰度发布                    G15 信创适配
                                                                                          G16 在线激活
```

**依赖关系**：
- G01 前端联调依赖后端接口稳定 → 建议在 G03（v1.1.4 发布）后启动
- G02 与 G01 无强依赖，可并行；G02 部分示例（审计/限流）依赖 G03 收尾
- G07 依赖 G01 工程定型（模板按实际页面结构生成）
- G08 压测依赖整体稳定，放 Phase 2

**里程碑**：
- M1（~2 天）：v1.1.4 发布（tag 可复现）
- M2（~2 周）：备份脚本 + example 增强完成，全量测试绿
- M3（~4 周）：前端工程核心页面完成联调，商品化完整版成型（后端+前端+文档+部署）
- M4（~8 周）：P1 五项完成，可支撑提价与政府采购流程
- M5（按需）：P2 差异化

---

## 8. 验证与质量保障策略

- **基线回归**：每次交付前全量 `mvn -o test`（当前 1827 例基线），mvnd 仅日常单模块
- **门禁**：bias 门禁零命中；新模块遵循命名空间 apex.*；契约测试（OpenAPI/配置）不回归
- **前端验证**：构建产物静态托管冒烟 + 独立 Nginx 模式各验证一次；权限/路由自动化冒烟脚本
- **三档验证**：basic/standard/enterprise 三档产物各自启动冒烟（档位功能矩阵日志校验）
- **发布流程**：统一走 scripts/release/release.sh；CHANGELOG 与 tag 同步
- **文档对齐**：每项交付同步更新 CONFIGURATION/DEPLOYMENT/README，避免文档漂移（沿用 ✅❌⚠️💡🔒 注释规范）

---

## 9. 商业价值评估

| 改进项 | 预计对客单价影响 | 对采购决策影响 | 投入 | ROI |
|--------|----------------|--------------|------|-----|
| G01 前端工程 | +30%~50%（可从"后端代码"变为"完整系统"定价） | ⭐⭐⭐⭐⭐ | 3~4 周 | 极高 |
| G02 example 增强 | 缩短售前演示周期，减少"看不懂"流失 | ⭐⭐⭐⭐ | 5~7 人日 | 高 |
| G03 发布收尾 | 保障交付物可信度（可复现版本） | ⭐⭐⭐⭐ | 1 人日 | 高 |
| G04 备份脚本 | 消除运维评审否决项 | ⭐⭐⭐ | 1~2 人日 | 高 |
| G05~G09（P1） | 支撑 Standard→Enterprise 提价与政企准入 | ⭐⭐⭐⭐ | ~15 人日 | 中高 |
| G10~G16（P2） | 差异化卖点、行业准入 | ⭐⭐ | 按需 | 中 |

**核心论点**：P0 完成后，产品从"后端脚手架"升级为"全栈脚手架"，可直接对标 RuoYi/JeecgBoot 的售卖形态；P1 的 Sentinel 动态规则 + 前端代码生成 + 采购材料包恰好命中中型企业"要控成本、要走采购流程"的两个痛点。

---

## 10. 风险与假设

| 风险 | 等级 | 应对 |
|------|------|------|
| 前端工程工作量被低估（联调/细节打磨耗时） | 中 | 核心页优先，仪表盘/主题等延后；页面清单锁定 M3 范围 |
| WebFlux 与某些前端模式（SSE/流式）对接细节 | 低 | 现有 NotificationSseHandler 作参考实现 |
| 并行开发导致配置漂移 | 低 | 沿用文档对齐审计（08-28 已验证流程） |
| Flowable 与 WebFlux 集成成本高 | 中 | 作为决策点先评估再定；不进入 P0/P1 承诺 |
| 买方对 License/双轨许可的疑虑 | 低 | LICENSE_MANAGEMENT.md 已覆盖；售前话术文档随 G08 输出 |

**假设**：
1. 目标市场仍为中小企业 + 部分政企，前端技术栈接受 Vue3 + Element Plus
2. 前端工程随源码包交付（Standard/Enterprise），Basic 档可获取只读演示
3. 不引入重量级商业组件库（保持轻量 + 版权干净）

---

## 11. 待确认决策点（请拍板）

| # | 决策点 | 选项 | 建议 |
|---|--------|------|------|
| D1 | **前端工程形态** | A. 自研 Vue3 精简后台（推荐） / B. 基于开源 admin 二次开发（快但重、版权/升级成本） / C. 暂不交付前端，仅升级静态控制台 | A |
| D2 | **前端是否进本仓库** | A. 同仓 `apex-admin-vue3/`（推荐，随档位分发） / B. 独立仓库 + 发布物捆绑 | A |
| D3 | **example 增强范围** | A. 全量 10 项（推荐） / B. 先 6 项核心（缓存/MQ/文件/SSE/Excel/审计） | A |
| D4 | **工作流引擎** | A. 暂不做（推荐，评估成本后再定） / B. Flowable 最小闭环进 Enterprise | A |
| D5 | **执行节奏** | A. 一次性 P0→P1 连续执行（推荐） / B. 仅 P0，P1 后议 | A |
| D6 | **备份脚本存储目标** | A. 本地目录 + 可选 OSS/MinIO（推荐） / B. 仅本地 | A |

> 确认方式：直接回复"确认"或逐条回复决策点编号（如 D1-A, D4-A）。确认后按第 7 章路线图开工，首日先做 G03 发布收尾。

---

**编制**：Apex Scaffold 交付组
**日期**：2026-08-28
**下次复审**：M3 里程碑（前端联调完成）后
