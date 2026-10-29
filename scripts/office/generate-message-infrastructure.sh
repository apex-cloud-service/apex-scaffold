#!/bin/bash
###############################################################################
# generate-message-infrastructure.sh - 历史一次性站内信基础设施代码生成工具归档转发入口
# 用法：bash scripts/office/generate-message-infrastructure.sh [参数透传归档/目标脚本]
# 作者：Apex Team    创建：2026-09-15
###############################################################################
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)" || exit 1
printf '%s\n' '历史一次性工具，转交 scripts/archive/office/generate-message-infrastructure.sh；执行前请核对归档内容。' >&2
exec bash "$SCRIPT_DIR/../archive/office/generate-message-infrastructure.sh" "$@"
