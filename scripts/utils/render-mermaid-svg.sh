#!/usr/bin/env bash
# 渲染 Mermaid 源文件为同名 SVG（BE-001 补导出的可复用脚本）
#
# 用法：
#   bash scripts/utils/render-mermaid-svg.sh              # 只处理缺同名 .svg 的 .mmd
#   bash scripts/utils/render-mermaid-svg.sh -f           # 强制重渲染全部 .mmd
#   bash scripts/utils/render-mermaid-svg.sh -f path.mmd  # 只重渲染指定文件
#
# 依赖：node（npx 可用）+ 本机 Chrome；首次运行会由 npx 拉取 mermaid-cli。
# 版本：默认 mermaid-cli 10（11 对 activate/deactivate 嵌套与 &lt;/&gt; 转义更严格，
#       会让本仓部分历史源文件解析失败；如需 11 用 MMDC_VERSION=11 覆盖）。
set -u
SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
ROOT="$(cd "$SCRIPT_DIR/../.." && pwd -P)"
MMDC_VERSION="${MMDC_VERSION:-10}"

FORCE=0
TARGET=""
for arg in "$@"; do
  case "$arg" in
    -f) FORCE=1 ;;
    *)  TARGET="$arg" ;;
  esac
done

# 定位浏览器（mermaid-cli 依赖 puppeteer）
if [ -z "${PUPPETEER_EXECUTABLE_PATH:-}" ]; then
  for c in "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" \
             "/Applications/Chromium.app/Contents/MacOS/Chromium" \
             "$(command -v google-chrome || true)" \
             "$(command -v chromium || true)"; do
    [ -n "$c" ] && [ -x "$c" ] && { export PUPPETEER_EXECUTABLE_PATH="$c"; break; }
  done
fi
if [ -z "${PUPPETEER_EXECUTABLE_PATH:-}" ]; then
  echo "[FAIL] 未找到 Chrome/Chromium，请设置 PUPPETEER_EXECUTABLE_PATH" >&2
  exit 1
fi

cd "$ROOT" || exit 1
RENDER() {
  npx --yes "@mermaid-js/mermaid-cli@$MMDC_VERSION" -i "$1" -o "${1%.mmd}.svg" >/tmp/render-mermaid-one.log 2>&1
}

ok=0; fail=0
if [ -n "$TARGET" ]; then
  if RENDER "$TARGET"; then echo "[OK]   $TARGET"; ok=$((ok+1)); else echo "[FAIL] $TARGET"; grep -m1 "Error" /tmp/render-mermaid-one.log; fail=$((fail+1)); fi
else
  while IFS= read -r f; do
    if RENDER "$f"; then echo "[OK]   ${f%.mmd}.svg"; ok=$((ok+1)); else echo "[FAIL] $f"; grep -m1 "Error" /tmp/render-mermaid-one.log; fail=$((fail+1)); fi
  done < <(python3 - "$FORCE" <<'PY'
import pathlib, sys
force = sys.argv[1] == "1"
for m in sorted(pathlib.Path("docs").rglob("*.mmd")):
    if force or not m.with_suffix(".svg").exists():
        print(m)
PY
)
fi

echo "== 渲染结果：成功 ${ok}，失败 ${fail}（mermaid-cli ${MMDC_VERSION}）=="
[ "$fail" -eq 0 ] || exit 1
