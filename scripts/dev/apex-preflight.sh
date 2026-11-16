#!/usr/bin/env bash
# apex 一键开工/提交前预检（前后端双仓通用）
# 用法：
#   bash scripts/dev/apex-preflight.sh             # 全量预检（含治理脚本）
#   bash scripts/dev/apex-preflight.sh --fast      # 跳过治理脚本与网络
#   bash scripts/dev/apex-preflight.sh --strict    # 有 FAIL 则 exit 1
#   bash scripts/dev/apex-preflight.sh --commit    # 额外检查记忆台账是否已暂存
set -u

STRICT=0; FAST=0; COMMIT_MODE=0
while [ $# -gt 0 ]; do
  case "$1" in
    --strict) STRICT=1; shift ;;
    --fast) FAST=1; shift ;;
    --commit) COMMIT_MODE=1; shift ;;
    *) shift ;;
  esac
done

ROOT="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
cd "$ROOT" || exit 0

FAIL=0; WARN=0; OK=0
pass() { OK=$((OK + 1)); echo "  [OK]   $1"; }
warn() { WARN=$((WARN + 1)); echo "  [WARN] $1"; }
fail() { FAIL=$((FAIL + 1)); echo "  [FAIL] $1"; }

echo "== apex-preflight @ $(basename "$ROOT") =="

# 0. 仓库类型
if [ -f pom.xml ] && [ -d apex-shared-parent ]; then
  REPO=backend
elif [ -f package.json ] && [ -d src/views ]; then
  REPO=frontend
else
  REPO=unknown
fi
echo "  仓类型：${REPO}"

# 1. 分支
BRANCH="$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo unknown)"
if [ "$BRANCH" = "dev" ]; then pass "当前分支 dev"; else fail "当前分支为 ${BRANCH}，应为 dev（git checkout dev）"; fi

# 2. 工作区
DIRTY="$(git status --porcelain 2>/dev/null | wc -l | tr -d ' ')"
if [ "${DIRTY}" -eq 0 ]; then
  pass "工作区干净"
else
  warn "工作区有 ${DIRTY} 项未提交改动（确认都是自己改的，勿覆盖他人）："
  git status --porcelain 2>/dev/null | sed 's/^/      /' | head -20
fi

# 3. 与远端对齐（--fast 跳过网络）
if [ "$FAST" -eq 0 ]; then
  git fetch origin dev --quiet 2>/dev/null || warn "fetch origin dev 失败（网络/凭据问题，见 SSH 443 检查）"
  AHEAD="$(git rev-list --count origin/dev..HEAD 2>/dev/null || echo 0)"
  BEHIND="$(git rev-list --count HEAD..origin/dev 2>/dev/null || echo 0)"
  if [ "${BEHIND}" -gt 0 ]; then
    warn "落后 origin/dev ${BEHIND} 个提交（先 git pull --ff-only）"
  else
    pass "不落后 origin/dev（本地领先 ${AHEAD} 个）"
  fi
else
  warn "跳过远端对齐检查（--fast）"
fi

# 4. AGENTS.md 附录 A 完整性
if [ -f AGENTS.md ]; then
  if [ "$REPO" = "backend" ]; then H_LINES=306; H_EXPECT="aa0c25fcc3d4b32a79981efb4e01534ae68449fb320d37037263d4668d8960ed"
  else H_LINES=712; H_EXPECT="ae5e19db9675ad33556faae36ea7007fa2011fefa7cdb35ee703bccec49db53a"; fi
  H_ACTUAL="$(tail -n ${H_LINES} AGENTS.md | shasum -a 256 | awk '{print $1}')"
  if [ "$H_ACTUAL" = "$H_EXPECT" ]; then pass "AGENTS.md 附录 A 未被改动"; else fail "AGENTS.md 附录 A 哈希变化（实际 ${H_ACTUAL}），原文区不得改动"; fi
else
  fail "缺少 AGENTS.md"
fi

# 5. 远端与 SSH
REMOTE_URL="$(git remote get-url origin 2>/dev/null || echo '')"
case "$REMOTE_URL" in
  git@github.com:*) pass "origin 为 SSH 地址：${REMOTE_URL}" ;;
  '') fail "未配置 origin 远端" ;;
  *) warn "origin 非 SSH 形式：${REMOTE_URL}" ;;
esac
SSH_CMD="$(git config core.sshCommand 2>/dev/null || echo '')"
case "$SSH_CMD" in
  *Hostname=ssh.github.com*443*) pass "SSH 固定走 443（22 口被网络阻断时的必需配置）" ;;
  *) warn "未固定 443（git config core.sshCommand \"ssh -o Hostname=ssh.github.com -p 443\"）" ;;
esac

# 6. 记忆台账
MEM=""
for f in docs/reference/ai-knowledge/07-change-memory.md docs/reference/ai-knowledge/04-change-memory.md; do
  if [ -f "$f" ]; then MEM="$f"; break; fi
done
if [ -z "$MEM" ]; then
  fail "未找到记忆台账（07/04-change-memory.md）"
else
  ENTRIES="$(grep -c '^| [0-9]' "$MEM" 2>/dev/null || true)"
  pass "记忆台账存在：${MEM}（条目 ${ENTRIES}）"
  if [ "$COMMIT_MODE" -eq 1 ]; then
    if git diff --cached --name-only 2>/dev/null | grep -q "^${MEM}$"; then
      pass "--commit 模式：台账已暂存"
    else
      fail "--commit 模式：台账未暂存，提交前须追加一条并 git add"
    fi
  fi
fi

# 7. 治理脚本
GOV="scripts/governance"
if [ ! -d "$GOV" ]; then
  fail "缺少 ${GOV}"
else
  for s in verify-docs.sh check-doc-refs.sh check-memory-sync.sh; do
    [ -f "${GOV}/${s}" ] && pass "存在 ${GOV}/${s}" || fail "缺少 ${GOV}/${s}"
  done
  if [ "$REPO" = "backend" ]; then
    [ -f "${GOV}/audit-backend-rules.sh" ] && pass "存在 audit-backend-rules.sh" || fail "缺少 audit-backend-rules.sh"
  else
    [ -f "${GOV}/audit-frontend-rules.sh" ] && pass "存在 audit-frontend-rules.sh" || fail "缺少 audit-frontend-rules.sh"
  fi
  if [ "$FAST" -eq 0 ]; then
    echo "  -- 治理自检（每次数秒，失败即视为未通过）--"
    for s in verify-docs.sh check-doc-refs.sh; do
      if [ -f "${GOV}/${s}" ]; then
        OUT="$(bash "${GOV}/${s}" 2>&1 | tail -1)"
        case "$OUT" in
          *"0 项"*|*"通过"*) pass "${s}：$(echo "$OUT" | sed 's/^[[:space:]]*//')" ;;
          *) fail "${s}：$(echo "$OUT" | sed 's/^[[:space:]]*//')" ;;
        esac
      fi
    done
    if [ "$REPO" = "backend" ] && [ -f "${GOV}/audit-backend-rules.sh" ]; then
      OUT="$(bash "${GOV}/audit-backend-rules.sh" 2>&1 | tail -1)"
      case "$OUT" in
        *"无阻断"*|*"阻断 0"*|*"0 阻断"*|*"通过"*) pass "audit-backend-rules.sh 无阻断" ;;
        *) warn "audit-backend-rules.sh：$(echo "$OUT" | sed 's/^[[:space:]]*//')（提交后仍需手跑一次）" ;;
      esac
    fi
  else
    warn "跳过治理自检（--fast）"
  fi
fi

# 8. 图表基线（仅后端有图表）
if [ "$REPO" = "backend" ]; then
  MMD="$(find docs -name '*.mmd' 2>/dev/null | wc -l | tr -d ' ')"
  SVG="$(find docs -name '*.svg' 2>/dev/null | wc -l | tr -d ' ')"
  if [ "${SVG}" -ge 247 ] && [ "${MMD}" -ge 68 ]; then
    pass "图表基线达标：mmd=${MMD} svg=${SVG}（基线 68/247）"
  elif [ "${SVG}" -ge 243 ] && [ "${MMD}" -ge 68 ]; then
    # 允许少量差异（历史上 68/247 为 BE-001 后基线）；低于即告警
    warn "图表数量低于基线：mmd=${MMD} svg=${SVG}（基线 68/247，只允许增加）"
  else
    fail "图表数量明显低于基线：mmd=${MMD} svg=${SVG}（基线 68/247）"
  fi
  if [ -f scripts/utils/render-mermaid-svg.sh ]; then pass "存在图表渲染脚本（默认 mmdc 10）"; else warn "缺少 render-mermaid-svg.sh"; fi
fi

# 9. 工具链
if [ "$REPO" = "backend" ]; then
  if command -v mvnd >/dev/null 2>&1; then pass "mvnd 可用：$(mvnd --version 2>/dev/null | head -1)"; else fail "mvnd 不可用（先 source scripts/build/init-build-env.sh）"; fi
  JAVA_V="$(java -version 2>&1 | head -1)"
  case "$JAVA_V" in
    *\"21*) pass "JDK 21：${JAVA_V}" ;;
    *) warn "当前 JDK 可能不是 21：${JAVA_V}" ;;
  esac
else
  if command -v node >/dev/null 2>&1; then pass "node 可用：$(node --version)"; else fail "node 不可用"; fi
  if [ -f package.json ]; then
    if grep -q '"build"' package.json; then pass "package.json 有 build 脚本（含 vue-tsc 类型检查）"; else warn "package.json 缺 build 脚本"; fi
  fi
fi

# 10. 分支模型一致性（不阻塞）
if [ "$FAST" -eq 0 ]; then
  for b in master test; do
    if git rev-parse --verify "$b" >/dev/null 2>&1; then
      GAP="$(git rev-list --count ${b}..dev 2>/dev/null || echo 0)"
      if [ "${GAP}" -eq 0 ]; then pass "${b} 与 dev 同点"; else warn "${b} 落后 dev ${GAP} 个提交（发布时 --ff-only 合入）"; fi
    fi
  done
fi

echo "== 汇总：OK=${OK} WARN=${WARN} FAIL=${FAIL} =="
if [ "${FAIL}" -gt 0 ]; then
  echo "== 结论：存在 FAIL，先修再提交 =="
  [ "$STRICT" -eq 1 ] && exit 1
else
  echo "== 结论：通过（WARN 项请人工确认） =="
fi
exit 0
