# 08 · 智能体与工具（已安装清单）

> **采样日**：2026-11-16 · 覆盖 `apex-scaffold`（后端）与 `apex-admin-web`（前端）
> **定位**：登记「为了提效/维护/测试」已经在用哪些 Skill 与 Agent、装在哪、怎么重装、怎么验证生效。
> 依规范 §5.4，安装/变更同样要登记到 `07-change-memory.md`。

---

## 1. 已安装的项目专属 Skill（用户级，跨会话生效）

存放位置：`/Users/eric/DoubaoWork/skills/<name>/SKILL.md`（**用户级技能根，不入版本库**，换机器需按下表重建）

| Skill | 覆盖 | 何时触发 | 关键内容 |
| --- | --- | --- | --- |
| `apex-scaffold-backend` | 后端仓开发与验证 | 在本仓写代码、加 Router+Handler 接口、构建失败、改模块、准备提交/发布 | 开工三查（分支/工作区/附录A哈希）、`source init-build-env.sh` + `mvnd -pl :<模块> -am -B verify`、B1–B10 红线、SpringDoc 覆盖检查、图表渲染（mmdc 10）、台账登记、归档命令、macOS 坑 |
| `apex-admin-web-frontend` | 前端仓开发与验证 | 改页面/组件/API、契约对齐、跑测试、准备提交 | **真实存在的 npm 脚本**（明确「没有 lint / 没有独立 typecheck」）、加页面四步套路、`apiUrl`/`showError` 等硬约束、SSE 头、台账与分支纪律 |
| `apex-governance-ops` | 双仓治理与运维 | 文档收口/迁移、补图表、跑治理自检、提交推送、改名分支、打归档、评审整改 | 五个自检脚本与判据、台账六字段、迁移「正文一字不改 + 索引可达 + 指针 + 单独提交」、`dev→master→test` 快进、SSH 443、`git archive` 归档、禁 `push -f`/`reset --hard` |

**与已有通用技能的差异**：`/Users/eric/.agents/skills/` 下已有 `java-backend-dev`、`java-troubleshooting`，但其默认栈是 **Spring MVC + MyBatis-Plus + Knife4j**；本仓是 **WebFlux 函数式路由 + DDD + mvnd 多模块**，所以另建项目专属技能，避免拿着 MVC 套路改响应式代码。

## 2. 已安装的 Agent 定义

| 仓 | 位置（私有目录，不入库） | Agent | 职责 |
| --- | --- | --- | --- |
| 后端 | `.workbuddy/agents/` | `java-backend-expert.md`（既有） | 后端实现与重构 |
| 后端 | 同上 | `qa-engineer.md`（既有） | 全量/模块/压测、质量门禁 |
| 后端 | 同上 | `codegen-expert.md`（既有） | 代码生成 |
| 后端 | 同上 | `docs-release-engineer.md`（既有） | 文档与发布 |
| 后端 | 同上 | **`doc-governance-auditor.md`（新增）** | 文档收口审查、图表基线守护、台账强制、7 项审查清单 |
| 后端 | 同上 | **`release-guardian.md`（新增）** | 推送前四查、`--ff-only` 发布、归档、5 条硬红线 |
| 前端 | `/Users/work/apex-admin-web/.workbuddy/agents/` | **`frontend-expert.md`（新增）** | 页面/组件/API/契约/构建/体积治理 |
| 前端 | 同上 | **`qa-engineer.md`（新增）** | vitest/覆盖率/E2E/性能，禁止改断言迁就实现 |
| 前端 | 同上 | **`release-guardian.md`（新增，副本）** | 同后端发布纪律 |

> 私有目录（`.workbuddy/`、`.zcode/`、`.trae/`）**不入版本库**，因此本节是它们的登记出处；换机器按本表重建即可（内容要点见 §3）。

## 3. Agent 重建要点（一句话版）

| Agent | 必须写进定义的内容 |
| --- | --- |
| `doc-governance-auditor` | 权威顺序、7 项审查清单（标准目录/索引可达/私有边界/图表基线/断链/台账/原文哈希）、禁止删源与 `git add -A`、附录 A 不得触碰 |
| `release-guardian` | 推送前四查、带口令私钥的处理、`dev→master→test` 快进的四条命令、`git archive`+`bundle` 归档、5 条硬红线（禁强推/禁 reset/异源历史合并/删远端分支前确认默认分支/推送失败即归档） |
| `frontend-expert` | 真实命令清单（含"没有 lint"）、加页面四步、`apiUrl`/`showError`/SSE 头、台账与分支纪律 |
| `qa-engineer`（前端） | 测试命令清单、以实测为准不硬编码数字、E2E 打桩不等于全面覆盖、结论写台账 |

## 4. 插件市场核查结论

- 用 `find_plugin`（关键词：代码审查 / 测试 / Spring Boot / Vue / 项目质量检测）查询 **无可用结果**，故本轮不安装第三方插件。
- 替代方案：把"插件级"能力落在**仓库内脚本**（已在用，无需额外安装）：

| 能力 | 现成入口 |
| --- | --- |
| 静态红线扫描 | `scripts/governance/audit-backend-rules.sh`（后端）、`audit-frontend-rules.sh`（前端） |
| 文档治理/断链/台账 | `verify-docs.sh`、`check-doc-refs.sh`、`check-memory-sync.sh` |
| 接口文档覆盖 | `scripts/ci/check-springdoc-coverage.sh`（后端） |
| 图表渲染与质量 | `scripts/utils/render-mermaid-svg.sh`、`batch-verify-svg.sh`、`verify-svg-quality.sh` |
| 测试与报告 | 后端 `scripts/test/*`、前端 `npm run test:report` |
| 部署后自检 | 前端 `scripts/verify/post-deploy-check.sh`（C1–C13） |

## 5. 生效验证（换机器/新会话后跑一遍）

```bash
# 1) Skill 是否存在且 frontmatter 完整
ls /Users/eric/DoubaoWork/skills/{apex-scaffold-backend,apex-admin-web-frontend,apex-governance-ops}/SKILL.md
head -4 /Users/eric/DoubaoWork/skills/apex-governance-ops/SKILL.md   # 应看到 name/description

# 2) Agent 定义是否存在
ls /Users/work/apex-scaffold/.workbuddy/agents/
ls /Users/work/apex-admin-web/.workbuddy/agents/

# 3) 技能里写的命令是否真的可跑（抽查两条）
cd /Users/work/apex-scaffold && bash scripts/governance/verify-docs.sh | tail -1
cd /Users/work/apex-admin-web && python3 -c "import json;print(list(json.load(open('package.json'))['scripts'])[:6])"
```

判定：Skill 文件存在 + Agent 定义存在 + 抽查命令输出正常 = 工具链就绪。

## 6. 维护

- 任何 Skill/Agent 的增删改，都要在 `07-change-memory.md` 追加一条（类型写 `脚本` 或 `治理`），并同步本节表格。
- 技能内容与仓库命令**必须一致**：仓库脚本改名/参数变化时，同日更新对应 `SKILL.md`，否则技能会给出错误命令（比没有技能更糟）。
