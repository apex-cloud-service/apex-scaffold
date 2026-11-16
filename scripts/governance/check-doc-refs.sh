#!/usr/bin/env bash
# 活文档指针自检（apex-scaffold）
# 用法：bash scripts/governance/check-doc-refs.sh [--strict]
# 检查 1：受控 Markdown 中仍指向 AI 私有目录的引用（正文应在 docs/）
# 检查 2：Markdown 相对链接是否可达
# 豁免：docs/_archive/ 前缀整体豁免；白名单文件；显式标注「原文未入库」的行
# 默认只告警（退出码 0）；--strict 时发现问题退出码 1
set -u
STRICT=0
[ "${1:-}" = "--strict" ] && STRICT=1
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
exec python3 "$ROOT/scripts/governance/check-doc-refs.py" "$ROOT" "$STRICT"
