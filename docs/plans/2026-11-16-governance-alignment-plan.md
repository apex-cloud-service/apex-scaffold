# 治理规范对齐整改方案（待业主确认后执行）

| 项 | 值 |
| --- | --- |
| 依据 | `docs/reviews/2026-11-16-governance-alignment-review.md`（同批次评审，结论：有条件通过） |
| 覆盖问题 | RV-01 ~ RV-14（P1 5 项、P2 5 项、P3 4 项） |
| 改动性质 | **纯文档 + 治理脚本**；业务代码 0 改动、图表 0 删除、历史归档正文 0 删改 |
| 预计改动文件 | 约 46 个（后端 40、前端 6），新增 4 个 |
| 建议执行顺序 | 批次 1 → 2 → 3 → 4 → 5 → 6（每批单独提交，可独立回滚） |
| 执行前置 | 业主对本文 §0 的 D1–D6 六个开关给出结论（未表态按「默认建议」执行） |

---

## 0. 待你决策的六个开关（回复编号即可）

| # | 决策 | 选项 | 默认建议 | 影响面 |
| --- | --- | --- | --- | --- |
| D1 | 历史归档断链（后端 15 条、前端 3 条） | ①规范登记豁免，正文不动 ②逐条修 | **①** | 规范新增 1 节；前端 3 条另计（易修） |
| D2 | 后端根目录 2 份文档 | ①迁入 `docs/` 并留指针 ②留在原地并登记 | **①** | 4 处引用需同步 |
| D3 | 治理自检增强 | ①新增私有路径/断链检查（默认告警、`--strict` 阻断） ②不加 | **①** | 两仓各 +1 脚本、+1 白名单 |
| D4 | 远端备份 | ①加远端并推送 ②保持本地 + 定期离线归档 | 需仓库地址 | 对外动作，需地址与授权 |
| D5 | `docs/records` 粒度 | ①同批次一条、须列全卡号（写入规范） ②一卡一条 | **①** | 规范 §2.5 一句话 |
| D6 | `.trae` 规则镜像 | ①补齐红线与收口（≈60 行） ②改为纯指针 | **①** | 1 个文件 |

---

## 1. 批次 1（P1）：规范正文自述与现状对齐

**解决**：RV-01、RV-02、RV-03。

### 1.1 后端 `docs/specs/project-governance.md`

| 位置 | 现状 | 改为 |
| --- | --- | --- |
| 第 175 行（§5.2 末条） | 「本轮改造期间 `MEMORY-CARD.md`、`daily/README.md`、`daily/2026-11-16-…md` 属他人工作，全程未动、也未纳入提交」 | 「他人未提交改动**默认不纳入**；确需纳入时，先经业主授权，再按 §3.3/§3.4 只做指针与索引对齐（正文保留），单独提交并在 `docs/records/` 留痕（示例：BE-019 / `14f6bc2a`）」 |
| 第 229 行（§9.1 第 2 条） | 「BE-001~007 待规划、BE-010~013 治理完成、BE-014~016 迁移完成、BE-017 技术债」 | 「BE-001~008 待规划、BE-010~013 治理完成、BE-014~016 迁移完成、BE-017 技术债、BE-018 规范正文入仓、BE-019 他人改动纳入」 |
| 第 251–256 行（§9.3 表） | 4 行，未含 BE-018/019 | 追加 2 行：`BE-018 规范正文入仓 | 已完成`、`BE-019 他人 3 项纳入 | 已完成` |

### 1.2 前端 `docs/specs/project-governance.md`

| 位置 | 现状 | 改为 |
| --- | --- | --- |
| 第 230 行（§9.1 第 2 条） | 「FE-001~007 待规划、FE-010~013 已完成」 | 「FE-001~007 待规划、FE-010~014 已完成（FE-014 为规范正文入仓）」 |
| 第 248–252 行（§9.3 表） | 3 行 | 追加 `FE-014 规范正文入仓 | 已完成` |

### 1.3 验收

```bash
cd /Users/work/apex-scaffold
grep -n "全程未动" docs/specs/project-governance.md          # 期望：无输出
grep -n "BE-019" docs/specs/project-governance.md            # 期望：≥1
cd /Users/work/apex-admin-web
grep -n "FE-014" docs/specs/project-governance.md            # 期望：≥1
bash scripts/governance/verify-docs.sh                        # 期望：通过
```

**回滚**：`git revert <批次1 sha>`。**风险**：仅改正文表述，无技术风险。

---

## 2. 批次 2（P1）：活文档指针修正（RV-04）

**范围**：后端 25 篇 / 56 行，前端 1 篇 / 1 行。

### 2.1 替换映射（工具可执行的权威对照表）

| # | 旧引用（前缀或整串） | 新引用 | 依据 |
| --- | --- | --- | --- |
| M1 | `.workbuddy/knowledge/` | `docs/reference/ai-knowledge/` | 11-16 已迁移（7 份，SHA-256 一致） |
| M2 | `apex-admin-web/.workbuddy/knowledge/` | `apex-admin-web/docs/reference/ai-knowledge/` | 前端仓已迁移（4 份） |
| M3 | `.trae/documents/microservice-migration-execution-plan.md` | `docs/plans/archived-ai-sessions/microservice-migration-execution-plan.md` | 已迁入（文件存在） |
| M4 | `.trae/documents/phase-2-3-implementation-plan.md` | `docs/08-project-management/plans/phase-2-3-implementation-plan.md` | 文件存在 |
| M5 | `.trae/documents/phase-2-3-quick-guide.md` | `docs/08-project-management/quick-guides/phase-2-3-quick-guide.md` | 文件存在 |
| M6 | `.trae/documents/phase-2-3-implementation-summary.md` | `docs/_archive/trae-reports/2026-09/phase-2-3-implementation-summary.md` | 已归档，文件存在 |
| M7 | `.trae/documents/phase-2-progress-report.md` | `docs/_archive/trae-reports/2026-09/phase-2-progress-report.md` | 已归档，文件存在 |
| M8 | `.trae/documents/SpringDoc注解完善工作总结.md` | `docs/_archive/trae-documents/SpringDoc注解完善工作总结.md` | 已归档，文件存在 |
| M9 | `phase-3-frontend-guide.md`、`前端配置数据库多适配方案`、`商业化脚手架重构方案`、`业务代码删除清单`、`系统架构设计文档`、`数据库设计文档`、`业务流程文档`、`部署运维文档`、`前后端API路由对比清单`、`配置文件完整性检查报告`、`Grafana监控面板增强方案` 等 `.trae/documents/*` | 原文**全仓不存在** → 改写为「（原文未入库，已随私有目录清理丢失）」 | `git ls-files` 全仓检索为空 |

> **M9 的处理原则**：不伪造路径、不指向 gitignore 目录；把「死指针」改成「明确的缺失说明」，让后来者知道去哪找替代材料（`docs/07-commercial/`、`docs/08-project-management/plans/`、`docs/_archive/trae-*`）。

### 2.2 执行脚本（可直接复制，先 `--dry-run` 看差异）

```bash
cat > /tmp/fix_refs.py <<'PY'
import re, sys, pathlib

ROOT = pathlib.Path("/Users/work/apex-scaffold")
DRY  = "--apply" not in sys.argv

FILES = [
 "docs/01-guides/新对话启动文档.md",
 "docs/04-development/DATABASE_CONFIG_BACKEND_IMPLEMENTATION.md",
 "docs/04-development/DATABASE_CONFIG_QUICK_REFERENCE.md",
 "docs/07-commercial/COMMERCIAL_OPTIMIZATION_PROPOSAL.md",
 "docs/08-project-management/checklists/microservice-acceptance-checklist.md",
 "docs/08-project-management/checklists/phase-2-3-delivery-checklist.md",
 "docs/08-project-management/phase-2-3-delivery-checklist.md",
 "docs/08-project-management/phase-2-3-quick-guide.md",
 "docs/08-project-management/quick-guides/phase-2-3-quick-guide.md",
 "docs/08-project-management/periodic-reports/README.md",
 "docs/08-project-management/periodic-reports/daily/README.md",
 "docs/08-project-management/periodic-reports/weekly/README.md",
 "docs/08-project-management/periodic-reports/monthly/README.md",
 "docs/08-project-management/plans/MeilisearchAdapter完善实施指南.md",
 "docs/08-project-management/plans/RAG知识库评审方案.md",
 "docs/08-project-management/plans/microservice-migration-plan.md",
 "docs/08-project-management/plans/后续实施快速指南.md",
 "docs/plans/archived-ai-sessions/microservice-phase5-6-extraction-design.md",
 "docs/reference/ai-knowledge/04-admin-web.md",
 "docs/reference/ai-knowledge/05-cold-chain.md",
 "docs/reference/ai-knowledge/README.md",
 "docs/reports/javadoc-work-summary.md",
 "docs/reports/project-cleanup-execution-report-2026-09-20.md",
 "docs/reports/项目清理执行报告-2026-09-20.md",
 "scripts/dev/check/QUICKREF-springdoc-check.md",
]

# 有序替换：先长后短，避免前缀互相吃掉
MAP = [
 (r"apex-admin-web/\.workbuddy/knowledge/", "apex-admin-web/docs/reference/ai-knowledge/"),
 (r"\.workbuddy/knowledge/",               "docs/reference/ai-knowledge/"),
 (r"\.trae/documents/microservice-migration-execution-plan\.md",
  "docs/plans/archived-ai-sessions/microservice-migration-execution-plan.md"),
 (r"\.trae/documents/phase-2-3-implementation-plan\.md",
  "docs/08-project-management/plans/phase-2-3-implementation-plan.md"),
 (r"\.trae/documents/phase-2-3-quick-guide\.md",
  "docs/08-project-management/quick-guides/phase-2-3-quick-guide.md"),
 (r"\.trae/documents/phase-2-3-implementation-summary\.md",
  "docs/_archive/trae-reports/2026-09/phase-2-3-implementation-summary.md"),
 (r"\.trae/documents/phase-2-progress-report\.md",
  "docs/_archive/trae-reports/2026-09/phase-2-progress-report.md"),
 (r"\.trae/documents/SpringDoc注解完善工作总结\.md",
  "docs/_archive/trae-documents/SpringDoc注解完善工作总结.md"),
]

# 无宿主文件：保留文件名，追加缺失说明
MISSING = [
 "phase-3-frontend-guide.md","phase-2-3-final-summary.md","前端配置数据库多适配方案",
 "商业化脚手架重构方案","业务代码删除清单","系统架构设计文档","数据库设计文档",
 "业务流程文档","部署运维文档","前后端API路由对比清单","配置文件完整性检查报告",
 "Grafana监控面板增强方案","configuration-inventory.md","menu-config.sql",
]

def mark_missing(text):
    for name in MISSING:
        pat = re.compile(r"(`[^`\n]*" + re.escape(name) + r"[^`\n]*`)")
        text = pat.sub(lambda m: m.group(1) + "（原文未入库）", text)
    return text

changed_total = 0
for rel in FILES:
    p = ROOT / rel
    if not p.exists():
        print(f"[SKIP] 缺失: {rel}"); continue
    old = p.read_text(encoding="utf-8")
    new = old
    for pat, rep in MAP:
        new = re.sub(pat, rep, new)
    new = mark_missing(new)
    if new != old:
        n = sum(1 for a, b in zip(old.splitlines(), new.splitlines()) if a != b)
        print(f"[DIFF] {rel}  变化行≈{n}")
        changed_total += n
        if not DRY:
            p.write_text(new, encoding="utf-8")
print(f"\n合计变化行: {changed_total} | 模式: {'DRY-RUN' if DRY else 'APPLY'}")
PY
python3 /tmp/fix_refs.py            # 先看差异
# 确认后再执行：
# python3 /tmp/fix_refs.py --apply
```

前端 1 处（单独改）：

```bash
cd /Users/work/apex-admin-web
# docs/features/database-config.md:153
# 旧：`apex-scaffold` 仓库 `.trae/documents/前端配置数据库多适配方案.md`
# 新：`apex-scaffold` 仓库 `docs/07-commercial/`（原方案未入库，待后端补建正文）
```

### 2.3 验收

```bash
cd /Users/work/apex-scaffold
python3 - <<'PY'
import subprocess,re
raw=subprocess.run(["git","ls-files","-z","*.md"],capture_output=True,text=True).stdout
files=[f for f in raw.split("\0") if f]
EXC=("docs/_archive/","docs/reports/LINK_FIX_REPORT","docs/records/","docs/reference/ai-knowledge/PROVENANCE.md",
"docs/archived/ai-sessions/zcode-plans/README.md","docs/plans/archived-ai-sessions/README.md","MEMORY-CARD.md",
"docs/08-project-management/plans/database/database-reorganization-plan.md","docs/06-testing/jwt-unification/","CHANGELOG.md",
"docs/08-project-management/periodic-reports/daily/2026-","docs/08-project-management/periodic-reports/monthly/2026-","docs/08-project-management/periodic-reports/weekly/2026-")
pat=re.compile(r'\.workbuddy/(knowledge|reports)/|\.zcode/plans/|\.trae/documents/')
left=[(f,len([l for l in open(f,encoding='utf-8',errors='ignore') if pat.search(l)])) for f in files if not any(f.startswith(e) for e in EXC)]
left=[x for x in left if x[1]]
print("剩余残留活文档:",len(left), left)
PY
bash scripts/ci/check-doc-links.sh | tail -4      # 断裂链接不应增加（期望 ≤ 15）
```

**回滚**：`git revert <批次2 sha>`（纯文本替换，可整批回退）。
**风险**：M9 类「标题化处理」是语义改动，需人工抽查 5 处（`COMMERCIAL_OPTIMIZATION_PROPOSAL`、`DATABASE_CONFIG_*`、`phase-2-3-delivery-checklist`、`QUICKREF-springdoc-check`）。

---

## 3. 批次 3（P1）：`.trae` 规则镜像补齐（RV-05）

**文件**：`/Users/work/apex-scaffold/.trae/rules/project_rules.md`（私有目录，不入库）

**改法**：在现有「构建环境（强制）」之后追加四节，内容与 `AGENTS.md` 第 0 章同源、只做压缩版：

1. **十条红线速查表**（B1–B10，一行一条，含「例外：BE-004 / BE-017」）。
2. **文档收口**：业务文档只进 `docs/`；六标准目录；图表禁动（SVG 217 / MMD 68）。
3. **多智能体边界**：`.trae/` 只放规则镜像与缓存，禁止放设计文档正文（正文在 `docs/plans/archived-ai-sessions/`）。
4. **提交纪律**：一类改动一提交、禁止 `reset --hard` / `push -f`、他人未提交改动默认不纳入。

**验收**：

```bash
grep -c '@Autowired\|@Controller\|RouterFunction' .trae/rules/project_rules.md   # ≥3
grep -c 'docs/' .trae/rules/project_rules.md                                    # ≥5
# 人工对读：与 AGENTS.md 0.2/0.4/0.5/0.6 无矛盾
```

**回滚**：私有目录，`cp .trae/rules/project_rules.md.bak` 还原即可（执行前备份）。

---

## 4. 批次 4（P2）：治理自检增强（RV-06）

新增一条检查：**活文档私有路径残留 + 断链（带白名单）**，两仓同步。

### 4.1 新脚本 `scripts/governance/check-doc-refs.sh`

```bash
#!/usr/bin/env bash
# 检查 1：活文档中残留指向 AI 私有目录的引用
# 检查 2：Markdown 相对链接是否可达（白名单豁免历史归档）
# 默认只告警；--strict 时非零退出
set -uo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
ALLOW="$ROOT/scripts/governance/doc-refs-allowlist.txt"
STRICT=0; [ "${1:-}" = "--strict" ] && STRICT=1
BAD=0

echo "== 1. 私有路径残留扫描 =="
while IFS= read -r f; do
  while IFS=: read -r ln _; do
    echo "  [WARN] $f:$ln 引用私有目录（正文应在 docs/）"
    BAD=$((BAD+1))
  done < <(grep -nE '\.workbuddy/(knowledge|reports)/|\.zcode/plans/|\.trae/documents/' "$f" || true)
done < <(git -C "$ROOT" ls-files '*.md' | grep -vxF -f "$ALLOW" || true)

echo "== 2. Markdown 相对链接可达性 =="
python3 - "$ROOT" "$ALLOW" <<'PY'
import sys,re,pathlib
root,allow=pathlib.Path(sys.argv[1]),sys.argv[2]
skip={l.strip() for l in open(allow,encoding="utf-8") if l.strip() and not l.startswith("#")}
bad=0
for f in (p for p in root.rglob("*.md") if ".git" not in str(p)):
    rel=str(f.relative_to(root))
    if rel in skip: continue
    try: t=f.read_text(encoding="utf-8",errors="ignore")
    except: continue
    for m in re.finditer(r"\]\(([^)#]+\.md)(#[^)]*)?\)",t):
        tgt=m.group(1)
        if tgt.startswith("http"): continue
        if not (f.parent/tgt).exists():
            print(f"  [WARN] 断链 {rel}:{t[:m.start()].count(chr(10))+1} -> {tgt}"); bad+=1
print(f"  断裂链接: {bad}")
sys.exit(0)
PY

echo "== 结果：$BAD 项私有路径残留 =="
if [ "$STRICT" = "1" ] && [ "$BAD" -gt 0 ]; then exit 1; fi
exit 0
```

### 4.2 白名单 `scripts/governance/doc-refs-allowlist.txt`（初始内容）

```text
# 历史归档：记录当时真实路径，正文不改（规范 §3.6 豁免）
CHANGELOG.md
docs/records/2026-11-16-docs-governance.md
docs/reference/ai-knowledge/PROVENANCE.md
docs/archived/ai-sessions/zcode-plans/README.md
docs/plans/archived-ai-sessions/README.md
docs/08-project-management/plans/database/database-reorganization-plan.md
docs/reports/LINK_FIX_REPORT.md
docs/reports/LINK_FIX_REPORT_DETAILED.md
docs/_archive/reports/2026-09-03-documentation-migration.md
docs/reports/INTERNAL_LINK_FIX_REPORT.md
docs/06-testing/jwt-unification/20261031-a1-a2/report.md
docs/08-project-management/periodic-reports/daily/2026-11-02-apex-scaffold-daily-report.md
docs/08-project-management/periodic-reports/monthly/2026-10-apex-scaffold-monthly-report.md
```

### 4.3 接进 `verify-docs.sh`

在两仓 `scripts/governance/verify-docs.sh` 末尾（汇总前）插入第 8 项：

```bash
echo "== 8. 活文档私有路径与断链 =="
bash "$(dirname "$0")/check-doc-refs.sh" || true
```

并在两仓 `scripts/governance/README.md` 增加该脚本的判据说明（为什么告警不阻断、白名单如何维护）。

### 4.4 验收

```bash
cd /Users/work/apex-scaffold && bash scripts/governance/check-doc-refs.sh        # 私有路径 0 项、断链 15 条（均在白名单外？见下）
cd /Users/work/apex-admin-web && bash scripts/governance/check-doc-refs.sh
bash scripts/governance/verify-docs.sh                                          # 第 8 项输出可见
```

> 后端断链 15 条中 12 条属 `_archive`、3 条属 `docs/reports/INTERNAL_LINK_FIX_REPORT.md` → 白名单已覆盖；前端 3 条在 `docs/archived/project-reports/` → 由批次 5 修掉，不豁免。

**回滚**：`git revert <批次4 sha>`，删两仓 `doc-refs-allowlist.txt` 即回到原状。

---

## 5. 批次 5（P2/P3）：结构收口与易修断链（RV-07、RV-12）

### 5.1 前端 3 条断链（相对层级多一层）

```bash
cd /Users/work/apex-admin-web
python3 - <<'PY'
import pathlib
for f in pathlib.Path("docs/archived/project-reports").glob("*.md"):
    t=f.read_text(encoding="utf-8")
    n=t.replace("](../../../../AGENTS.md)", "](../../../AGENTS.md)")
    if n!=t: f.write_text(n,encoding="utf-8"); print("fixed:",f)
PY
```

### 5.2 后端根目录 2 份文档迁移（D2 选项①）

```bash
cd /Users/work/apex-scaffold
set -e
BASE=$(shasum -a 256 aggregation-optimization-plan.md | awk '{print $1}')
test "$BASE" = "$(shasum -a 256 aggregation-optimization-plan.md | awk '{print $1}')"
# 1) 迁入（保留原文件名，历史材料不改名——规范 §3.2 类推）
git mv aggregation-optimization-plan.md docs/plans/aggregation-optimization-plan.md
git mv architecture-review-report.md   docs/reviews/architecture-review-report.md
# 2) 逐字节校验
shasum -a 256 docs/plans/aggregation-optimization-plan.md docs/reviews/architecture-review-report.md
# 3) 根目录留 3 行指针
printf '%s\n' '# 已迁移' '' '本文已迁至 `docs/plans/aggregation-optimization-plan.md`，根目录不再保留正文。' > aggregation-optimization-plan.md
printf '%s\n' '# 已迁移' '' '本文已迁至 `docs/reviews/architecture-review-report.md`，根目录不再保留正文。' > architecture-review-report.md
# 4) 修引用（先查再改）
grep -rIl --exclude-dir=.git "aggregation-optimization-plan.md\|architecture-review-report.md" . | head -20
```

**验收**：`grep -rn "aggregation-optimization-plan.md" docs | wc -l` 指向新路径；`verify-docs.sh` 通过；两份文件 SHA-256 与迁移前一致（记录在 `docs/records/`）。

---

## 6. 批次 6（P3）：口径落规范 + 闭环留痕（RV-08/09/10/11/13/14）

| 动作 | 文件 | 内容 |
| --- | --- | --- |
| 新增 §3.6「历史归档断链豁免」 | 两仓 `docs/specs/project-governance.md` | 历史归档（`docs/_archive/`、`docs/reports/LINK_FIX_REPORT*`、`docs/archived/`）内断链不改正文，登记到 `scripts/governance/doc-refs-allowlist.txt` |
| §2.5 补记录粒度口径（D5） | 后端规范 §2.5 第 5 条 | 「同批次任务可合并一条记录，但必须列出全部卡号与对应文件」 |
| §5.1 补远端与备份（D4） | 两仓规范 §5.3 | 「每仓至少 1 个远端；无远端时须每周做一次离线归档（tar + SHA-256）」 |
| RV-13 私有目标链接 | `docs/reference/ai-knowledge/README.md:19,21,67,68` | 链接改为文字说明「`.workbuddy/rag|ops/` 属私有目录，换机后需重新生成，不入库」 |
| RV-14 历史脚注 | `CHANGELOG.md:76`、`docs/reports/*` 4 处 | 原句后加「（原文未入库，见 `docs/_archive/trae-*`）」 |
| RV-08 评审闭环 | `docs/reviews/README.md` | 登记本评审报告与整改验收记录（第 1、2 篇） |
| 任务登记 | 两仓 `docs/tasks/project-tasks.md` | 后端 `BE-020`「治理评审整改（批次1–6）」待规划；前端 `FE-015`「治理评审整改（前端项）」待规划 |

---

## 7. 总验收清单（每批提交前跑一次）

```bash
# 资产与原文（必须不变或只增不减）
find /Users/work/apex-scaffold/docs -name '*.svg' | wc -l          # 217
find /Users/work/apex-scaffold/docs -name '*.mmd' | wc -l          # 68
tail -n 306 /Users/work/apex-scaffold/AGENTS.md | shasum -a 256    # aa0c25fc…60ed
tail -n 712 /Users/work/apex-admin-web/AGENTS.md  | shasum -a 256  # ae5e19db…53a
# 治理与红线
cd /Users/work/apex-scaffold  && bash scripts/governance/verify-docs.sh
cd /Users/work/apex-scaffold  && bash scripts/governance/audit-backend-rules.sh   # 仅 BE-017
cd /Users/work/apex-admin-web && bash scripts/governance/verify-docs.sh
cd /Users/work/apex-admin-web && bash scripts/governance/audit-frontend-rules.sh  # 0 阻断
# 代码零改动
git -C /Users/work/apex-scaffold diff --stat HEAD~1 -- '*.java' '*/pom.xml' | wc -l   # 0
git -C /Users/work/apex-admin-web diff --stat HEAD~1 -- 'src/' | wc -l              # 0
```

**每批提交规范**：`docs(governance): 批次N …`（批次 4 用 `chore(governance): …`）；一批一提交；提交后跑上面清单并把输出摘要写进 `docs/records/2026-11-16-governance-alignment-remediation.md`。

---

## 8. 工作量与风险

| 批次 | 改动文件 | 预估耗时 | 风险 | 回滚 |
| --- | --- | --- | --- | --- |
| 1 规范自洽 | 2 | 10 min | 极低 | `git revert` |
| 2 活文档指针 | 26 | 60 min（含抽查） | 中（M9 语义化处理） | 整批 `git revert` |
| 3 规则镜像 | 1（私有） | 20 min | 低 | 备份还原 |
| 4 自检增强 | 6（2 脚本 + 2 白名单 + 2 README） | 45 min | 低（默认只告警） | `git revert` |
| 5 结构收口 | 8 | 30 min | 中（`git mv` + 引用同步） | `git revert` + SHA 校验 |
| 6 口径与留痕 | 8 | 30 min | 低 | `git revert` |
| **合计** | **≈46** | **≈3.5 h** | — | 分批可回滚 |

**不做的事（明确边界）**：
1. 不改任何业务代码、`pom.xml`、`.sdkmanrc`、迁移 SQL。
2. 不删除/覆盖/移动任何 SVG/MMD 图表。
3. 不删改 `docs/_archive/` 与历史报告的正文（仅加豁免登记或脚注）。
4. 不 `git add -A`；不 `reset --hard`；不 `push -f`。
5. 不触碰其他 AI 工具的私有目录内容（除 D6 授权的 `.trae/rules` 镜像）。

---

## 9. 执行确认

回复格式建议：

```text
D1=① D2=① D3=① D5=① D6=① ；D4 仓库地址=…（或「暂缓」）
批准批次：1,2,3,4,5,6（可只勾部分）
```

收到确认后，我按批次执行、每批一次提交，并在 `docs/records/2026-11-16-governance-alignment-remediation.md` 累积留痕，全部完成后提交复审（`docs/reviews/2026-11-16-governance-alignment-review.md` 追加复审结论）。
