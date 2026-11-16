#!/usr/bin/env bash
# 记忆文档同步校验（警告模式，永不阻断提交）
# 规则来源：docs/specs/project-governance.md §5.4「记忆文档同步（强制）」
# 用法：
#   bash scripts/governance/check-memory-sync.sh            # 检查已暂存改动是否带记忆登记（警告）
#   bash scripts/governance/check-memory-sync.sh --strict   # 缺登记时 exit 1（可接 CI）
#   bash scripts/governance/check-memory-sync.sh --msg "<提交信息>"   # 额外校验提交信息是否带 memory 标记
set -u

ROOT="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
cd "$ROOT" || exit 0

STRICT=0
MSG=""
while [ $# -gt 0 ]; do
  case "$1" in
    --strict) STRICT=1; shift ;;
    --msg) MSG="${2:-}"; shift 2 ;;
    *) shift ;;
  esac
done

# 记忆台账定位：后端 07-change-memory.md / 前端 04-change-memory.md（同一脚本两仓通用）
MEMORY_FILE=""
for f in docs/reference/ai-knowledge/07-change-memory.md docs/reference/ai-knowledge/04-change-memory.md; do
  if [ -f "$f" ]; then MEMORY_FILE="$f"; break; fi
done
if [ -z "$MEMORY_FILE" ]; then
  echo "[memory] 未找到记忆台账（07-change-memory.md / 04-change-memory.md），跳过"
  exit 0
fi
MEM_NAME="$(basename "$MEMORY_FILE")"

STAGED="$(git diff --cached --name-only --diff-filter=ACMR 2>/dev/null || true)"
if [ -z "$STAGED" ]; then
  echo "[memory] 无暂存改动，跳过"
  exit 0
fi

FAIL=0
TOTAL=$(printf '%s\n' "$STAGED" | wc -l | tr -d ' ')

if printf '%s\n' "$STAGED" | grep -q "^$MEMORY_FILE"; then
  echo "[memory] OK：本次提交已登记记忆台账（${MEM_NAME}）"
else
  echo "[memory] ⚠️ 本次提交共 $TOTAL 个文件改动，但未暂存记忆台账 ${MEM_NAME}"
  echo "[memory]    规则（规范 §5.4）：任何改动都要在台账追加一条，六字段 = 日期/类型/摘要/提交/证据/影响面"
  echo "[memory]    修复：编辑 $MEMORY_FILE 追加条目后 git add 同一文件再提交；"
  echo "[memory]          确为纯机械改动（格式化/改错字）则在提交信息末行写 memory: n/a（原因）"
  printf '%s\n' "$STAGED" | sed 's/^/      - /'
  FAIL=1
fi

if [ -n "$MSG" ]; then
  if printf '%s\n' "$MSG" | grep -q "memory:"; then
    echo "[memory] OK：提交信息带 memory 标记"
  else
    echo "[memory] ⚠️ 提交信息缺少 memory 标记（末行应写 memory: ${MEM_NAME}#<条目编号>）"
    FAIL=1
  fi
fi

if [ "$FAIL" -eq 0 ]; then
  echo "[memory] 结果：0 项"
else
  if [ "$STRICT" -eq 1 ]; then
    echo "[memory] 结果：存在缺登记项（--strict 模式下 exit 1）"
    exit 1
  fi
  echo "[memory] 结果：警告（不阻断提交）"
fi
exit 0
