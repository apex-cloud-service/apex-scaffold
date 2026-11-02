#!/bin/bash
###############################################################################
# new-batch-report.sh
#
# 批次测试报告骨架生成（方案 §13 脚本交付物）。
#
# 按仓库既有惯例创建 docs/06-testing/<campaign>/<yyyyMMdd>-<batch>/report.md，
# 预填章节骨架供人工填写。脚本只生成结构，不预填任何未经验证的结论。
#
# 用法：
#   bash scripts/test/new-batch-report.sh <campaign> <batch> [标题]
# 示例：
#   bash scripts/test/new-batch-report.sh jwt-unification 20261102-m6 "批次 M6 测试报告"
#
# 注意：生成后须人工填写实际命令、结果与未验证项，禁止把静态核对写成运行验证。
###############################################################################
set -euo pipefail

SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=../lib/common.sh
source "${SCRIPT_DIR}/../lib/common.sh"

PROJECT_ROOT=$(get_project_root) || exit 1
cd "$PROJECT_ROOT" || exit 1

CAMPAIGN=${1:-}
BATCH=${2:-}
TITLE=${3:-"批次 ${BATCH} 测试报告"}

if [[ -z "$CAMPAIGN" || -z "$BATCH" ]]; then
    log_failure "用法：bash scripts/test/new-batch-report.sh <campaign> <batch> [标题]"
    exit 1
fi

TODAY=$(date +%Y%m%d)
REPORT_DIR="docs/06-testing/${CAMPAIGN}/${BATCH}"
REPORT_FILE="${REPORT_DIR}/report.md"

if [[ -f "$REPORT_FILE" ]]; then
    log_failure "报告已存在，不覆盖：${REPORT_FILE}"
    exit 1
fi

mkdir -p "$REPORT_DIR"
cat > "$REPORT_FILE" <<REPORT_EOF
# ${TITLE}

- 批次：方案批次 ${BATCH}
- 执行日期：$(date +%Y-%m-%d)
- 环境：macOS、JDK \`21.0.13.0+9-tem\`、mvnd \`2.0.0-rc-3\`（\`scripts/build/init-build-env.sh\` 校验通过）
- 状态：**待填写**

## 1. 已交付变更

<!-- 按模块/组件列出变更，附文件路径 -->

## 2. 测试

| 测试 | 结果 | 覆盖 |
| --- | --- | --- |
|  |  |  |

## 3. 过程缺陷与修复记录

<!-- 记录过程中发现并修复的真实缺陷及根因 -->

## 4. 结果

- （填写实际执行命令与结果，例如 mvnd 退出码、测试计数）

## 5. 未验证项与边界

<!-- 如实列出未验证项及原因，不得省略 -->
REPORT_EOF

log_success "报告骨架已生成：${REPORT_FILE}"
log_warn "请人工填写实际执行结果；静态核对不得写成运行验证"
