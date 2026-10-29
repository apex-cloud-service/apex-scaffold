#!/usr/bin/env bash

###############################################################################
# weekly-link-check.sh - 周度文档链接检查并生成 Markdown 报告
# 用法：./scripts/ops/maintenance/weekly-link-check.sh  无参数；报告写 docs/reports/link-check-YYYYMMDD.md
# 链接校验统一由 scripts/ci/check-doc-links.sh（Java 实现）承担；原内嵌 Python
# 粗粒度复查为弱断言重复检查，已按"测试与校验代码用 Java"的规范移除。
# 作者：Apex Team    创建：2026-10-01
###############################################################################
set -euo pipefail

readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly PROJECT_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
readonly LOG_FILE="$PROJECT_ROOT/logs/link-check-$(date +%Y%m%d).log"
readonly REPORT_FILE="$PROJECT_ROOT/docs/reports/link-check-$(date +%Y%m%d).md"

mkdir -p "$PROJECT_ROOT/logs"
mkdir -p "$PROJECT_ROOT/docs/reports"

log_info() {
    echo "[$(date +'%Y-%m-%d %H:%M:%S')] [INFO] $*" | tee -a "$LOG_FILE"
}

log_warn() {
    echo "[$(date +'%Y-%m-%d %H:%M:%S')] [WARN] $*" | tee -a "$LOG_FILE"
}

log_error() {
    echo "[$(date +'%Y-%m-%d %H:%M:%S')] [ERROR] $*" | tee -a "$LOG_FILE"
}

run_link_check() {
    log_info "运行文档链接检查..."

    cd "$PROJECT_ROOT"

    if [ ! -f "$PROJECT_ROOT/scripts/ci/check-doc-links.sh" ]; then
        log_error "链接检查脚本不存在"
        return 1
    fi

    if bash "$PROJECT_ROOT/scripts/ci/check-doc-links.sh" >> "$LOG_FILE" 2>&1; then
        log_info "✓ 链接检查通过"
        return 0
    else
        log_warn "链接检查发现问题"
        return 1
    fi
}

generate_report() {
    log_info "生成检查报告..."

    local status="PASSED"
    if ! run_link_check; then
        status="FAILED"
    fi
    
    cat > "$REPORT_FILE" << EOF
# 文档链接检查报告

**检查时间**: $(date +'%Y-%m-%d %H:%M:%S')  
**状态**: $status

## 检查结果

详见日志文件: \`logs/link-check-$(date +%Y%m%d).log\`

## 修复建议

如果发现断裂链接，可使用以下工具检查：

\`\`\`bash
# 链接检查工具
./scripts/ci/check-doc-links.sh
\`\`\`

---

*此报告由每周链接检查脚本自动生成*
EOF
    
    log_info "检查报告已生成: $REPORT_FILE"
}

main() {
    log_info "========================================"
    log_info "开始每周链接检查"
    log_info "========================================"
    
    cd "$PROJECT_ROOT"
    
    generate_report
    
    log_info "========================================"
    log_info "每周链接检查完成"
    log_info "详细日志: $LOG_FILE"
    log_info "检查报告: $REPORT_FILE"
    log_info "========================================"
}

main "$@"
