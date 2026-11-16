# 2026-11-16 · 文档治理与任务体系落地记录（apex-scaffold）

## 1. 目标与边界

| 项 | 说明 |
| --- | --- |
| 目标 | 按《项目治理与任务管理规范》改造文档结构、任务体系、自检机制与后端红线扫描 |
| 硬约束 | ① 不改业务代码（`src/main/java` 零改动）② 既有文档一字不少，**架构图/UML/时序图/流程图/SVG 全部保留** ③ 多 AI 工具目录并存时业务文档统一收口到 `docs/` |
| 允许动作 | 新增/迁移文档、调整引用链接、新增脚本与钩子、扩展 AGENTS.md |

## 2. 基线取证

| 项 | 值 |
| --- | --- |
| docs 文件数（改造前） | 1272（md=715、svg=217、mmd=68、json=139 等） |
| 图表基线 | SVG **217**、MMD **68**（另有 `docs/diagrams` 顶层 130 SVG + 38 MMD） |
| 基线哈希清单 | `baseline/apex-scaffold-docs-baseline.sha256`（1272 条） |
| AGENTS.md 原文 | 306 行 / 32694 字节 / `aa0c25fcc3d4b32a79981efb4e01534ae68449fb320d37037263d4668d8960ed` |
| 构建环境 | `.sdkmanrc` 固定 `java=21.0.13.0+9-tem`、`mvnd=2.0.0-rc-3` |
| git 状态 | main；动手前存在 3 处**他人未提交改动**（`MEMORY-CARD.md`、`periodic-reports/daily/README.md`、`daily/2026-11-16-all-projects-daily-report.md`）——本轮全程未触碰 |

## 3. 执行动作

### 3.1 AGENTS.md 增补（原文零删改）

- 前置新增「第 0 章 项目治理与任务管理规范」：
  - 0.1 范围与优先级（P0–P3 + 五状态）
  - 0.2 文档收口与六标准目录
  - 0.3 **图表资产保护**（禁删/禁批量移动/版本文件命名/`.mmd`↔`.svg` 成对）
  - 0.4 任务体系（单任务单职责、任务卡必填字段）
  - 0.5 后端 10 条编码红线（WebFlux + Handler/RouterFunction、构造器注入、Lombok、MapStruct、`@Slf4j`、Javadoc、SpringDoc、JDK21 + mvnd、禁 `@Controller`、禁 `@Autowired`）
  - 0.6 变更纪律　0.7 自检命令
- 原文下移为「附录 A：原始仓库开发规范（原文完整保留，未删改）」。
- 复核：`tail -n 306 AGENTS.md | shasum -a 256` = `aa0c25fc…60ed`，逐字节一致。新文件 389 行。

### 3.2 文档结构收口

- 新建 `docs/{tasks,specs,plans,records,reviews,notes}/` + 各 `README.md`。
- 新建任务大盘 `docs/tasks/project-tasks.md`：活跃 BE-001~007、已完成 BE-010~013（治理）与 BE-014~016（迁移）、新增 BE-017。
- `docs/README.md` 尾部追加治理索引区块，原文未动。

### 3.3 私有目录正文迁移（逐字节校验）

| 原位置 | 目标位置 | 数量 | 校验 |
| --- | --- | --- | --- |
| `.trae/documents/` | `docs/plans/archived-ai-sessions/` | 4（206/1022/315/199 行） | SHA-256 全一致 |
| `.workbuddy/knowledge/` | `docs/reference/ai-knowledge/` | 7 | SHA-256 全一致 |
| `.zcode/plans/` | `docs/archived/ai-sessions/zcode-plans/` | 13 | SHA-256 全一致 |
| `.workbuddy/reports/` | `docs/08-project-management/periodic-reports/{daily,weekly,monthly}/` | 3 | SHA-256 全一致，且与既有报告无重名 |

- **只在必要时修链接**：
  - `.workbuddy/knowledge` 迁入后修 `../rag|ops/README.md` → `../../../.workbuddy/rag|ops/README.md`（rag/ops 属工具基础设施，继续留在私有目录）。
  - `.trae/documents` 因历史引用众多，保留 4 个同名 3 行指针文件 + `MIGRATED.md`，并修正 3 处仍在使用的引用：`apex-shared-parent/apex-common/README.md`、`docs/06-testing/gateway-module-test-report.md`、`docs/architecture/service-governance-solution.md`。
  - 其余目录删除正文，留 `MIGRATED.md` 指针。

### 3.4 治理脚本

| 脚本 | 检查项 | 结果 |
| --- | --- | --- |
| `scripts/governance/verify-docs.sh` | 六目录与索引、任务大盘口径、治理章节、私有目录边界、**图表规模门槛 217/68**、`.mmd`↔`.svg` 配对、归档索引可达 | 通过（1 项存量图表告警，见 §4） |
| `scripts/governance/audit-backend-rules.sh` | 行首精确注解匹配：`@Autowired`、`@Controller`/`@RestController`、`@RestControllerAdvice`（例外）、`BeanUtils`、生产代码手写 Logger（测试单列不阻断）、`.sdkmanrc`/WebFlux 依赖、MVC starter 冲突 | 1 项阻断（见 §4） |
| `.githooks/pre-commit` | 治理范围改动时跑自检 + 图表删除告警 + 大文件提醒，**永不阻断提交** | 自测通过（exit 0） |

- 启用：`git config core.hooksPath .githooks`。
- 关键修正：初版用「关键词粗匹配」造成大量误报（Javadoc 中的 `@Autowired` 说明文字、`@RestControllerAdvice` 被 `@Controller` 前缀误配、`/test/` 下的 `LoggerFactory`），已改为 `^[[:space:]]*@...` 行首精确匹配，并区分生产/测试。

## 4. 红线扫描真实发现（仅报告，未改代码）

| 类别 | 发现 | 处置 |
| --- | --- | --- |
| 真实 `@RestControllerAdvice` | `apex-system` 的 `GlobalExceptionHandler`、`SentinelExceptionHandler`（共 2 个文件） | **例外登记 BE-004**，评估前禁止改动；异常处理不改变接口契约 |
| 生产代码手写 Logger | `apex-shared-parent/apex-common/.../scheduler/VirtualThreadBoundedExecutor.java:72` | 登记 **BE-017（P3）**，改 `@Slf4j` 前需确认该模块 Lombok 可用性与日志字段名兼容 |
| `@Autowired` | 0（16 处命中全部是 Javadoc 中的“无需 `@Autowired`”说明） | 且仓库自带 ArchUnit 守护测试 `NoAutowiredArchTest`，与红线一致 |
| 注解式控制器 | 0 | 115 个文件使用 `RouterFunction`，架构符合 WebFlux 要求 |
| `BeanUtils.copyProperties` | 0 | — |
| 手工 getter/setter 抽样 | 10 个（`AuthenticatedUser`、`*Properties` 等） | 多为不可变载体/配置绑定，`@ConfigurationProperties` 场景合法，不阻断 |
| `.mmd` 无同名 `.svg` | **30 个**（如 `diagrams/sequence/72_multi_tenant_isolation_architecture.mmd`） | 存量状态，登记 **BE-001** 补导出；**禁止删除这些源文件** |
| 构建环境 | `.sdkmanrc` 存在、根 POM 有 WebFlux、无 MVC starter | 合规 |

## 5. 改造后规模

| 项 | 改造前 | 改造后 |
| --- | --- | --- |
| docs 文件数 | 1272 | 1309 |
| `.md` | 715 | 752 |
| `.svg` | 217 | **217（未减少）** |
| `.mmd` | 68 | **68（未减少）** |

## 6. 不影响功能的验证

- `src/main/java`、`src/test/java` 下 **0 个文件被修改**（未触碰任何 Java 代码）。
- 仅修改 3 个既有文件的引用链接（README/测试报告/治理方案），未改其正文结论。
- 他人未提交的 3 处改动全程未触碰。
- 建议提交前跑一次 `source .sdkmanrc && sdk env && mvnd -q -DskipTests=false verify`（或按模块验证）作为回归确认。

## 7. 回滚方案

```bash
cd /Users/work/apex-scaffold
git restore AGENTS.md docs/README.md .gitignore apex-shared-parent/apex-common/README.md \
  docs/06-testing/gateway-module-test-report.md docs/architecture/service-governance-solution.md
rm -rf docs/tasks docs/specs docs/records docs/reviews docs/notes docs/reference docs/archived \
  docs/plans/archived-ai-sessions scripts/governance .githooks
git config --unset core.hooksPath
# 私有目录正文：从 docs 对应目标目录拷回（哈希见 baseline/apex-scaffold-docs-baseline.sha256）
```

## 8. 遗留与后续

| 项 | 状态 |
| --- | --- |
| BE-004 异常处理器 | 待评估（禁止先改） |
| BE-017 `VirtualThreadBoundedExecutor` | 待规划，评估 Lombok 化 |
| BE-001 30 个 MMD 缺导出件 | 待分批补 `.svg` |
| 根目录散落文档（`aggregation-optimization-plan.md`、`architecture-review-report.md`） | 未擅动，按「先出方案再迁移」处理，登记入盘 |

---

## 9. 二次增补（同日 v1.1：规范正文入仓 + 工具目录/忽略规则条款）

| # | 动作 | 文件 |
| --- | --- | --- |
| 1 | 新建**规范正文**（可独立阅读的完整版） | `docs/specs/project-governance.md`（294 行） |
| 2 | `AGENTS.md` 0.1 增「完整规范正文」指针；0.4 增「工具目录映射表 + `.gitignore` 三条硬规则 + 角色卡」；0.7 增脚本说明与钩子启用方式 | `AGENTS.md`（389 → 407 行，**附录 A 原文哈希仍为 `aa0c25fc…60ed`**） |
| 3 | 新建脚本说明文档（判据、已知结论、与 pre-commit 关系、bash 3.2 注意项） | `scripts/governance/README.md` |
| 4 | `docs/README.md` 增「规范正文」区块与脚本说明链接；`docs/specs/README.md` 增规范正文登记表 | 两文件均为**追加**，原内容未动 |
| 5 | `docs/specs/ai-agents/` 五张后端角色卡建立引用 | `docs/specs/ai-agents/` |

### 9.1 新增条款要点

1. **多智能体目录映射表**：明确各工具私有目录的允许/禁止内容（含已迁移的 `.trae/documents` 仅留指针、`.workbuddy/knowledge` 已迁、`.zcode/plans` 已迁）。
2. **`.gitignore` 三条硬规则**：`docs/` 全量入库；私有目录整体忽略；忽略项带注释，**禁止裸 `*.md` 模糊规则**（会误伤 `docs/` 归档件）。
3. **角色卡与复核**：R1–R5 跨工具复用，复核默认 R5。
4. **他人未提交改动保护**写进规范正文 §5.2（点名本轮 3 个文件）。

### 9.2 二次回归

| 项 | 结果 |
| --- | --- |
| AGENTS.md 附录 A | `tail -n 306` = `aa0c25fcc3d4b32a79981efb4e01534ae68449fb320d37037263d4668d8960ed`，逐字节一致 |
| 图表 | SVG 217 / MMD 68，未减少 |
| 原有文档 | 仅 `docs/README.md`、`docs/specs/README.md` 追加，零删除 |
| Java/POM | 0 改动 |
| 自检 | `verify-docs.sh` 通过（30 个 MMD 缺导出件为存量告警）；`audit-backend-rules.sh` 1 项阻断（BE-017） |

