#!/usr/bin/env bash
# 目录规范化自检（2026-11-16 新增）
# 断言四件事：
#   1) docs/ 下每个一级目录都有 README.md
#   2) docs/README.md 的「目录总览」表覆盖全部一级目录
#   3) 仓库根目录不允许散落业务 Markdown（白名单之外一律报缺）
#   4) 一级目录与白名单一致（新增目录必须同步登记本脚本与总览表）
# 只读扫描，不改文件；默认警告，--strict 有问题 exit 1
set -uo pipefail
cd "$(dirname "$0")/../.." || exit 2

STRICT=0
[ "${1:-}" = "--strict" ] && STRICT=1
FAIL=0
warn() { echo "  [WARN] $*"; FAIL=$((FAIL + 1)); }
pass() { echo "  [OK]   $*"; }

# 允许出现在 docs/ 一级的目录（新增必须同步这两处）
EXPECTED="00-getting-started 01-guides 02-architecture 03-api 04-development 05-operations 06-testing 07-commercial 08-project-management _archive api architecture archived cache diagrams fix-reports migration notes plans records reference reports reviews specs tasks"

echo "== 1. docs 一级目录都有 README =="
missing_readme=""
for d in $(ls -d docs/*/ 2>/dev/null | xargs -n1 basename | sort); do
  [ -f "docs/$d/README.md" ] || missing_readme="$missing_readme $d"
done
if [ -z "$missing_readme" ]; then pass "全部一级目录有 README"; else warn "缺 README：$missing_readme"; fi

echo "== 2. 未登记的一级目录 =="
unexpected=""
for d in $(ls -d docs/*/ 2>/dev/null | xargs -n1 basename | sort); do
  case " $EXPECTED " in *" $d "*) : ;; *) unexpected="$unexpected $d" ;; esac
done
if [ -z "$unexpected" ]; then pass "一级目录与登记表一致（$(echo $EXPECTED | wc -w | tr -d ' ') 个）"; else warn "未登记目录：$unexpected（请同步 docs/README.md 总览与本文 EXPECTED）"; fi

echo "== 3. 目录总览表覆盖 =="
noindex=""
for d in $(ls -d docs/*/ 2>/dev/null | xargs -n1 basename | sort); do
  pat=$(printf '`%s/`' "$d")
  grep -qF "$pat" docs/README.md || noindex="$noindex $d"
done
if [ -z "$noindex" ]; then pass "docs/README.md 总览已覆盖全部一级目录"; else warn "总览未覆盖：$noindex"; fi

echo "== 4. 根目录无散落业务 Markdown =="
# 白名单：仓库门面与许可类文档
ROOT_ALLOW="AGENTS.md CHANGELOG.md CODE_OF_CONDUCT.md CONTRIBUTING.md LICENSE.md LICENSE-COMMERCIAL.md MEMORY-CARD.md README.md aggregation-optimization-plan.md architecture-review-report.md"
stray=""
for f in *.md; do
  [ -f "$f" ] || continue
  case " $ROOT_ALLOW " in *" $f "*) : ;; *) stray="$stray $f" ;; esac
done
if [ -z "$stray" ]; then pass "根目录仅保留门面文档与既有指针文件"; else warn "根目录散落业务文档：$stray（请迁入 docs/ 对应目录）"; fi

echo
if [ "$FAIL" -eq 0 ]; then echo "  目录结构自检通过（0 项）"; exit 0; fi
echo "  发现 ${FAIL} 项"
[ "$STRICT" -eq 1 ] && exit 1
exit 0
