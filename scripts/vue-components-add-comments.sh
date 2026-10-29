#!/bin/bash
###############################################################################
# vue-components-add-comments.sh - 历史一次性前端组件注释工具归档转发入口
# 用法：bash scripts/vue-components-add-comments.sh [参数透传归档/目标脚本]
# 作者：Apex Team    创建：2026-10-01
###############################################################################
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)" || exit 1
printf '%s\n' '历史一次性工具，转交 scripts/archive/frontend/vue-components-add-comments.sh；执行前请核对归档内容。' >&2
exec bash "$SCRIPT_DIR/archive/frontend/vue-components-add-comments.sh" "$@"
