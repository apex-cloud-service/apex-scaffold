# 📚 Apex Scaffold 文档中心

欢迎来到 Apex Scaffold 文档中心！这里汇集了系统架构、开发指南、API 接口、运维部署等全方位的技术文档。

---

## 本次联调

- [2026-11-17 后端联调修复方案](./specs/2026-11-17-integration-contract-repair.md) — BE-036，权限快照兼容、菜单范围、审批租户上下文及回归边界。
- [2026-11-18 前后端全量联调方案](./specs/2026-11-18-fullstack-integration.md) — FE-029 / BE-043，Podman Compose 联调环境、全量 API 对照与缺陷闭环。
- [2026-11-19 Podman-only 联调入口记录](./records/2026-11-19-podman-only-integration.md) — BE-043，Compose 配置与本轮前端镜像/模拟反代验证边界。

## 📖 文档导航

### [00. 快速开始](./00-getting-started/README.md)

快速入门指南和示例代码，帮助您在 5 分钟内启动项目。

- [快速开始指南](./00-getting-started/quickstart.md) - 5 分钟快速启动
- [示例指南](./00-getting-started/example-guide.md) - 示例代码说明
- [示例总结](./00-getting-started/examples-summary.md) - 示例模块汇总
- [入门架构指南](./00-getting-started/onboarding-guide.md) - 架构快速上手

### [01. 使用指南](./01-guides/README.md)

功能使用指南、最佳实践和问题排查。

- [最佳实践](./01-guides/best-practices.md) - 开发最佳实践
- [开发手册](./01-guides/developer-handbook.md) - 完整开发指南
- [故障排查](./01-guides/troubleshooting.md) - 常见问题解决
- [工作流指南](./01-guides/workflow-guide.md) - 工作流引擎使用
- [数据权限](./01-guides/data-scope-usage.md) - 数据权限过滤
- [SSO 集成](./01-guides/sso-implementation-guide.md) - 单点登录
- [消息中间件](./01-guides/messaging/README.md) - RocketMQ 使用与优化
- [安全加固](./01-guides/security-features-configuration.md) - 安全特性配置与修复指南
- [文档搜索](./01-guides/doc-search-guide.md) - 全文搜索

### [02. 架构设计](./02-architecture/README.md)

系统架构、技术选型和设计决策。

- [架构总览](./02-architecture/architecture-overview.md) - 系统架构概览
- [架构演进](./02-architecture/architecture-evolution.md) - 架构演进历程
- [逻辑架构](./02-architecture/architecture-index.md) - DDD 四层架构
- [物理架构](./architecture/02-physical-architecture-detail.md) - 部署架构
- [技术架构](./02-architecture/architecture-overview.md) - 技术栈全景
- [数据库设计](./architecture/database-design.md) - 数据模型设计
- [能力目录](./02-architecture/capability-catalog.md) - 八项中台能力、治理字段、计量维度与开放 scope
- [多级缓存](./05-operations/advanced-features.md) - 缓存架构

### [03. API 文档](./03-api/README.md)

API 接口文档、规范和版本管理。

- [路由映射](./03-api/api-routes.md) - 前后端路由对应
- [API 路由清单](./03-api/API_ROUTES.md) - 全部路由登记（主应用 + 认证中心）
- [API 版本管理](./03-api/api-version-management.md) - 版本控制策略
- [SpringDoc 注解](./03-api/springdoc-annotation-templates.md) - API 文档注解
- [数据管理 API](./03-api/README.md) - 数据管理接口

**在线文档**: 启动项目后访问
- Swagger UI（system-service、auth-service 均装配 springdoc）: http://localhost:8082/swagger-ui.html
- Knife4j UI（system-service）: http://localhost:8082/doc.html

### [04. 开发文档](./04-development/README.md)

代码生成、开发规范和工具使用。

- [代码生成](./04-development/codegen.md) - DDD 代码生成器
- [统一开发规范](./04-development/development-standards.md) - 后端开发规范、注释与 JavaDoc、Lombok、构建规范
- [JMH 基准测试](./04-development/jmh-benchmark-guide.md) - 性能测试
- [SVG 维护](./04-development/svg-maintenance-guide.md) - 图表维护
- [Mermaid 查看器](./04-development/mermaid-viewer-guide.md) - 本地图表查看工具
- [文档搜索工具](./04-development/doc-search.html) - 本地文档全文搜索页
- 前端开发文档：已迁移至独立前端仓库 `apex-admin-web` 的 `frontend-docs/` 目录

### [05. 运维部署](./05-operations/README.md)

生产部署、配置管理和监控告警。

- [部署指南](./05-operations/deployment.md) - Podman/K8s 部署
- [配置管理](./05-operations/configuration.md) - 配置项说明
- [安全防护](./05-operations/security-protections.md) - 安全体系
- [高级特性](./05-operations/advanced-features.md) - 高级功能
- [性能监控指南](./05-operations/performance-monitoring-guide.md) - 监控指标与实践
- [Reactor Netty 指标验证](./05-operations/reactor-netty-metrics-guide.md) - 响应式指标开启与验证

### [06. 测试文档](./06-testing/README.md)

测试策略、测试报告和质量保证。

- [集成测试指南](./06-testing/integration-test-guide.md) - 集成测试
- [API 测试报告](./06-testing/api-test-report.md) - 接口测试
- [API 测试计划](./06-testing/api-test-plan.md) - 测试计划
- [中台整改测试报告](./06-testing/middle-platform/20261110-completion/report.md) - G1–G8 代码/契约关闭与真实验证边界

### [07. 商业化文档](./07-commercial/README.md)

商业版本、授权管理和销售资料。

- [授权管理](./07-commercial/license-management.md) - 授权系统
- [商业版本](./07-commercial/COMMERCIAL_VERSIONS.md) - 版本对比
- [销售资料](./07-commercial/sales/) - 销售支持文档

### [08. 项目管理](./08-project-management/README.md)

项目管理、实施计划和需求管理。

- [P0 系统管理](./08-project-management/p0-system-management.md) - 核心系统
- [交付文档集](./08-project-management/delivery/README.md) - 交付范围、交付物、需求追踪、质量、风险、变更、验收与交接
- [脚手架审查](./08-project-management/scaffold-review-guide.md) - 代码审查
- [功能需求](./08-project-management/function-enhancement-requirements.md) - 需求管理
- [实施计划](./08-project-management/plans/) - 各类实施计划
- [微服务化迁移方案](./08-project-management/plans/microservice-migration-plan.md) - 单体到五服务架构迁移
- [文档与脚本目录结构规范](./08-project-management/DOCS_STRUCTURE_STANDARD.md) - docs/ 与 scripts/ 组织规范
- [设计模式 / Starter / SPI 优化方案](./08-project-management/plans/design-pattern-starter-spi-optimization-plan.md) - 评审结论与待实施调整方案
- [中台架构评审报告](./08-project-management/reports/middle-platform-architecture-review.md) - 八维度评审与整改关闭说明
- [中台架构整改方案](./08-project-management/plans/middle-platform-remediation-plan.md) - G1–G8 整改方案与实施结果
- [工作区治理](./08-project-management/workspace-governance/README.md) - 工作区管理规范与本地治理交付物

---

## 🗂 目录总览（全部一级目录，2026-11-16 增补）

本表是 `docs/` 的**唯一目录索引**：新增文档前先查本表确定去向，新增目录必须同步登记本表与 `scripts/governance/check-doc-layout.sh` 白名单。

| 目录 | 内容 | 性质 | 收新内容 | 入口 |
| --- | --- | --- | --- | --- |
| `00-getting-started/` | 快速开始、示例 | 编号体系（历史主线，继续有效） | 是（入门类） | [README](./00-getting-started/README.md) |
| `01-guides/` | 使用指南、最佳实践、排障 | 编号体系 | 是（指南类） | [README](./01-guides/README.md) |
| `02-architecture/` | 架构总览、演进、能力目录 | 编号体系（**架构类的权威落点**） | 是 | [README](./02-architecture/README.md) |
| `03-api/` | 接口文档（按业务域） | 编号体系（**接口类的权威落点**） | 是 | [README](./03-api/README.md) |
| `04-development/` | 开发规范、构建、编码约定 | 编号体系 | 是 | [README](./04-development/README.md) |
| `05-operations/` | 部署、运维、分支保护等操作手册 | 编号体系 | 是 | [README](./05-operations/README.md) |
| `06-testing/` | 测试策略、用例、测试报告 | 编号体系（**测试报告权威落点**） | 是 | [README](./06-testing/README.md) |
| `07-commercial/` | 商业化、许可、授权 | 编号体系 | 是 | [README](./07-commercial/README.md) |
| `08-project-management/` | 周期报告、里程碑、项目台账 | 编号体系（**周期报告权威落点**） | 是 | [README](./08-project-management/README.md) |
| `tasks/` | 任务大盘（唯一的任务登记处） | 规范六目录 | 是（强制） | [README](./tasks/README.md) |
| `specs/` | 规范正文、角色卡 | 规范六目录 | 是 | [README](./specs/README.md) |
| `plans/` | 方案/计划（含已确认整改方案） | 规范六目录 | 是 | [README](./plans/README.md) |
| `records/` | 执行记录、验收证据、复盘 | 规范六目录 | 是 | [README](./records/README.md) |
| `reviews/` | 评审、审计、评估报告 | 规范六目录 | 是 | [README](./reviews/README.md) |
| `notes/` | 调研备忘、临时纪要（可升格为正式文档） | 规范六目录 | 是 | [README](./notes/README.md) |
| `reference/` | 受控参考：AI 记忆与知识库 | 受控参考 | 仅 `ai-knowledge/` | [ai-knowledge](./reference/ai-knowledge/README.md) |
| `diagrams/` | 图表资产：`.mmd` 源 + 同名 `.svg` 成对 | 资产目录 | 是（必须成对） | [README](./diagrams/README.md) |
| `architecture/` | 分层架构详解（29 篇 + `_templates`、`advanced-features`） | 专题历史（BE-003 后作为细分深度材料） | 否（新内容进 `02-architecture/`） | [README](./architecture/README.md) |
| `api/` | 早期单接口文档（如定时任务分页） | 专题历史 | 否（新内容进 `03-api/`） | [README](./api/README.md) |
| `cache/` | 缓存专题（设计、评审、优化记录） | 专题历史 | 否（新评审进 `reviews/`） | [README](./cache/README.md) |
| `reports/` | 早期实施/优化报告（39 篇） | 专题历史 | 否（新报告进 `records/` 或 `06-testing/`） | [README](./reports/README.md) |
| `fix-reports/` | Bug 修复报告（历史快照） | 专题历史 | 否（新记录进 `records/`） | [README](./fix-reports/README.md) |
| `migration/` | 数据/版本迁移记录 | 专题历史 | 否（新方案进 `plans/`） | [README](./migration/README.md) |
| `archived/` | AI 会话归档（`ai-sessions/`） | 归档 | 否 | [README](./archived/README.md) |
| `_archive/` | 已下线文档总仓（235 篇，按子目录分组） | 归档（只读） | 否 | [README](./_archive/README.md) |

**三条归置规则**

1. **新产出只进上位「收新内容 = 是」的目录**；专题历史目录只读、不追加（避免同一主题两份口径）。
2. **同一主题只允许一个权威落点**：架构 → `02-architecture/`；接口 → `03-api/`；测试报告 → `06-testing/`；周期报告 → `08-project-management/periodic-reports/`；评审 → `reviews/`。
3. **归档不改正文**（规范 §3.6）：`_archive/`、`archived/` 里的旧路径与旧口径保持原样，仅由 README 说明「以当前代码为准」。

## 🎨 架构图表

完整的架构图表位于 [diagrams](./diagrams/README.md) 目录，包含 200+ 张 SVG 图表（含图标源素材）：

- **系统架构**: 逻辑架构、物理架构、技术架构
- **业务流程**: 登录认证、权限校验、工作流审批、文件上传
- **技术架构**: 缓存架构、限流熔断、自动重试、安全防护
- **部署架构**: Podman Compose、Kubernetes、CI/CD 流水线
- **监控体系**: Prometheus、Grafana、Zipkin、日志聚合

详见 [diagrams/README.md](./diagrams/README.md)

---

## 🚀 快速链接

### 新手入门

1. [快速开始](./00-getting-started/quickstart.md) - 5 分钟启动项目
2. [架构总览](./02-architecture/architecture-overview.md) - 了解系统架构
3. [示例代码](./00-getting-started/example-guide.md) - 学习示例代码
4. [最佳实践](./01-guides/best-practices.md) - 开发规范

### 开发者

1. [开发手册](./01-guides/developer-handbook.md) - 完整开发指南
2. [代码生成器](./04-development/codegen.md) - 快速生成代码
3. [API 文档](./03-api/README.md) - 接口文档
4. [测试指南](./06-testing/integration-test-guide.md) - 测试实践

### 运维人员

1. [部署指南](./05-operations/deployment.md) - 生产部署
2. [配置管理](./05-operations/configuration.md) - 配置说明
3. [故障排查](./01-guides/troubleshooting.md) - 问题诊断
4. [监控告警](./diagrams/README.md) - 监控体系

---

## 📝 文档规范

本文档库遵循以下规范：

- **Markdown 格式**: 所有文档使用 Markdown 编写
- **图表标准**: 使用 SVG 格式，支持云原生图标
- **链接规范**: 使用相对路径，便于文档迁移
- **版本控制**: 文档随代码一起版本管理
- **持续更新**: 代码变更时同步更新文档

---

## 🔍 搜索文档

使用以下方式快速查找文档：

1. **GitHub 搜索**: 在仓库中搜索关键词
2. **IDE 搜索**: 使用 IDE 的文件搜索功能
3. **文档导航**: 使用本页面的导航链接
4. **目录索引**: 查看各子目录的 README.md

---

## 🤝 贡献文档

欢迎贡献文档改进！

1. Fork 本仓库
2. 创建文档分支
3. 编写或修改文档
4. 提交 Pull Request

文档贡献指南请参考项目根目录的 CONTRIBUTING.md

---

## 📮 反馈与支持

- **问题反馈**: [GitHub Issues](https://github.com/your-org/apex-scaffold/issues)
- **功能建议**: [GitHub Discussions](https://github.com/your-org/apex-scaffold/discussions)
- **安全问题**: security@your-org.com

---

**文档版本**: 2.0.0  
**最后更新**: 2026-09-12  
**维护团队**: Apex Team

---

<div align="center">

**📖 开始您的 Apex Scaffold 之旅！**

[快速开始](./00-getting-started/quickstart.md) • [架构设计](./02-architecture/README.md) • [API 文档](./03-api/README.md) • [运维部署](./05-operations/README.md)

</div>

---

## 🏛 项目治理与任务管理（2026-11-16 增补）· 2026-11-18 多智能体协作增补

> 本区块为治理规范新增入口。既有编号目录（00–08）与专题目录（`architecture`/`api`/`cache`/`diagrams`/`migration`/`reports`/`_archive`）**全部保持不变**，不搬迁、不改名。

### 规范与规则

- [项目治理规范（AGENTS.md 第 0 章）](../AGENTS.md) — 文档收口、任务体系、多智能体隔离、R0 租约与并行准入、后端编码红线、图表保护
- [任务大盘](tasks/project-tasks.md) — 全量任务、优先级、状态与验收标准（唯一可信源）

### 新增标准目录

| 目录 | 用途 | 入口 |
| --- | --- | --- |
| `docs/tasks/` | 任务大盘与迭代任务 | [tasks/README](tasks/README.md) |
| `docs/specs/` | 需求与技术方案（评审后实施） | [specs/README](specs/README.md) |
| `docs/plans/` | 实施与迁移计划 | [plans/README](plans/README.md) |
| `docs/records/` | 变更、改造、缺陷留痕 | [records/README](records/README.md) |
| `docs/reviews/` | 方案评审、代码评审 | [reviews/README](reviews/README.md) |
| `docs/notes/` | 调研、对接备忘、风险纪要 | [notes/README](notes/README.md) |

### 图表资产（247 SVG / 68 MMD，禁止批量移动与删除）

- [图表主库](diagrams/) — 逻辑/物理/技术架构、登录鉴权、权限校验、工作流审批、文件上传、ER 图、多级缓存、限流熔断，以及 100+ 模块结构与 UML 领域模型
- [架构设计文档](02-architecture/README.md) — DDD 四层架构、部署架构、架构演进
- 更新规则见 [AGENTS.md 第 0.3 节](../AGENTS.md)：新增版本文件 + 记录差异，不改历史图。

### 规范正文（完整、可独立阅读）

- [项目任务管理规范](specs/project-governance.md) — v1.2 任务租约、并行准入、worktree 隔离、标准交接、独立复核、集成关闭、文档收口与后端红线
- [AI 角色卡 R0–R5](specs/ai-agents/README.md) — 主控编排 / 需求拆解 / 后端实现 / 契约守门 / 测试回归 / 独立评审
- [多智能体并行协作治理记录](records/2026-11-18-multi-agent-governance.md) — BE-042 的范围、关键规则、R5 首轮驳回、返工复审与最终关闭证据
- [BE-042 独立 R5 评审报告](reviews/2026-11-18-be-042-r5-review.md) — 首轮 `REJECT` 后增量复审 `APPROVE_WITH_NITS`，MF-01–MF-05 已清零

### 迁入的历史资料（自 AI 私有目录迁出，内容零删改）

- [AI 会话设计文档归档](plans/archived-ai-sessions/README.md) — 原 `.trae/documents`：文档对齐计划、网关韧性方案、微服务迁移执行计划、Phase 5/6 抽取设计
- [ZCode 会话计划归档](archived/ai-sessions/zcode-plans/README.md) — 原 `.zcode/plans` 的 14 份历史计划（逐字节一致，私有目录仅保留短指针）

### 治理自检

```bash
source scripts/build/init-build-env.sh        # 固定 JDK 21.0.13.0+9-tem + mvnd 2.0.0-rc-3
bash scripts/governance/verify-docs.sh        # 目录、索引、任务大盘、私有目录边界、图表计数
bash scripts/governance/audit-backend-rules.sh     # 后端红线扫描（默认警告模式）
```

脚本用法与判据见 [scripts/governance/README.md](../scripts/governance/README.md)；配合 `.githooks/pre-commit`（`git config core.hooksPath .githooks`）在提交前自动提示。
