# 退役模块过期引用清理任务（另立任务）

| 项 | 内容 |
| --- | --- |
| 状态 | 已立项，待 `maven-module-physical-nesting-plan.md`（第二阶段物理归位）B6 完成后独立执行 |
| 来源 | 2026-11-14 第二阶段文档侧调研顺带发现；用户拍板"另立任务、不混入本次" |
| 关联 | 第二阶段方案 §2.7 顺手甄别项 / §8-Q7（已确认） |

## 1. 问题与数据

第一阶段模块拆分（`apex-interfaces` / `apex-application` 并入 apex-system、`apex-auth-center` 拆为 apex-auth + apex-auth-service）与管理后台独立成项目后，有效文档区（`docs/_archive` 之外）仍存在大量指向已不存在模块的过期引用：

| 退役标识 | 引用处 | 其中 `-pl` 命令 | 现状真实落点 |
| --- | ---: | ---: | --- |
| `apex-interfaces` | 410 | 88 | apex-system 的 `interfaces` 包（router/handler/安全配置；D07 裁决协议层归属） |
| `apex-application` | 247 | 34 | apex-system 的 `application` 包 |
| `apex-auth-center` | 74 | 17 | apex-auth（业务库）+ apex-auth-service（当前入口） |
| `apex-admin-web` | 154 | — | 独立管理后台项目（不在本仓库）；引用应删除或改为"独立管理后台（独立仓库）"表述 |
| 合计 | **885** | **139** | |

## 2. 处理原则

- a 类（链接 / 命令 / 真实文件路径）必修：`scripts/ci/check-doc-links.sh` 门禁校验链接目标存在，退役路径链接一律改指现落点或删除。
- b 类（反引号/表格叙述路径）按文件性质甄别：有效指导文档改写，历史报告类豁免（与第二阶段 D5 口径一致）。
- 本任务替换集合（退役模块名）与第二阶段替换集合（现存模块路径加分组前缀）**不相交**，同一文件可先后应用、无语义冲突；仅执行顺序需错开以避免同文件编辑交叠。
- `.github/workflows/frontend-ci.yml` 指向 `apex-admin-web/**` 属 CI 配置而非文档，单独甄别（待确认：工作流是否随前端项目迁移或停用），不计入 885 处文档工作量。

## 3. 执行时机与步骤

1. 时机：第二阶段 B6（文档批次）完成后，作为独立批次立即执行，独立提交承载；不与第二阶段任一批次混改同一提交。
2. 步骤：rg 全量定位 885 处 → 按上表落点映射逐文件改写（链接改指 / 删除 / 表述化）→ 退役模块名扫描清零（豁免区除外）。
3. 豁免区：`docs/_archive/**`、`scripts/ci/source-archive/**`、`scripts/archive/**`、`CHANGELOG.md` 等历史记录（与第二阶段豁免清单一致）。

## 4. 验证门禁

- `scripts/ci/check-doc-links.sh` 全绿。
- `rg 'apex-(interfaces|application|auth-center|admin-web)/'` 在有效区清零（豁免区除外）。
- 不引入新的 Java/脚本改动（纯文档任务）；`git diff --check` 干净。

## 5. 工作量

885 处 / 预计 60–80 个文件；落点映射明确（4 条），机械替换为主，估 0.5–1 人日。

## 6. 执行记录（2026-11-14）

| 项 | 结果 |
| --- | --- |
| 改写范围 | 有效区 447 处 / 约 90 文件（apex-interfaces 241 → `apex-components-parent/apex-system/`；apex-application 156 → 同左；apex-auth-center 50 → `apex-services-bom/apex-auth-service/`）；原估 885 处中 364 处位于 `docs/_archive`（豁免区，保持原样），74 处 admin-web 豁免（跨仓有效路径） |
| 命令改写 | 有效区 -pl 退役命令全部冒号化：`-pl apex-interfaces`/`-pl apex-application` → `-pl :apex-system`；`-pl apex-auth-center` → `-pl :apex-auth-service`；106/85 两张 SVG 图内示意命令同步冒号化 |
| 门禁 | `check-doc-links.sh` 全绿（758 md + 17 html，3702 链接 0 断裂——含改写产生的 1 条新落点有效链接）；退役模块名清零扫描通过 |
| 提交 | `9d74067d`（用户在途 weekly 周报改动曾因 `git add docs` 误扫入，已 soft reset 拆出、保留工作区） |
