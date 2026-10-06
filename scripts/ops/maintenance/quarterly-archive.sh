#!/usr/bin/env bash

###############################################################################
# quarterly-archive.sh - 季度归档：将 docs/reports 过时报告移入 docs/_archive/reports
# 用法：./scripts/ops/maintenance/quarterly-archive.sh  无参数；归档 90 天前的报告文件
# 作者：Apex Team    创建：2026-10-01
###############################################################################
set -euo pipefail

readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly PROJECT_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
readonly LOG_FILE="$PROJECT_ROOT/logs/quarterly-archive-$(date +%Y%m).log"
readonly ARCHIVE_DIR="$PROJECT_ROOT/docs/_archive"
readonly REPORTS_DIR="$PROJECT_ROOT/docs/reports"

mkdir -p "$PROJECT_ROOT/logs"
mkdir -p "$ARCHIVE_DIR/reports"

log_info() {
    echo "[$(date +'%Y-%m-%d %H:%M:%S')] [INFO] $*" | tee -a "$LOG_FILE"
}

log_warn() {
    echo "[$(date +'%Y-%m-%d %H:%M:%S')] [WARN] $*" | tee -a "$LOG_FILE"
}

archive_old_reports() {
    log_info "检查过时报告..."
    
    local cutoff_date
    cutoff_date=$(date -v-90d +%Y%m%d 2>/dev/null || date -d '90 days ago' +%Y%m%d)
    
    log_info "归档日期截止: $cutoff_date"
    
    local report_count=0
    
    if [ ! -d "$REPORTS_DIR" ]; then
        log_info "报告目录不存在，跳过"
        return
    fi
    
    find "$REPORTS_DIR" -type f -name "*.md" | while read -r report; do
        local basename
        basename=$(basename "$report")
        
        local report_date=""
        if [[ $basename =~ ([0-9]{4})-([0-9]{2})-([0-9]{2}) ]]; then
            report_date="${BASH_REMATCH[1]}${BASH_REMATCH[2]}${BASH_REMATCH[3]}"
        elif [[ $basename =~ ([0-9]{8}) ]]; then
            report_date="${BASH_REMATCH[1]}"
        fi
        
        if [ -n "$report_date" ] && [ "$report_date" -lt "$cutoff_date" ]; then
            log_info "归档报告: $basename (日期: $report_date)"
            mv "$report" "$ARCHIVE_DIR/reports/"
            report_count=$((report_count + 1))
        fi
    done
    
    if [ "$report_count" -gt 0 ]; then
        log_info "已归档 $report_count 个过时报告"
    else
        log_info "✓ 未发现需要归档的报告"
    fi
}

check_archive_size() {
    log_info "检查归档目录大小..."
    
    if [ ! -d "$ARCHIVE_DIR" ]; then
        log_info "归档目录不存在"
        return
    fi
    
    local size
    size=$(du -sh "$ARCHIVE_DIR" | awk '{print $1}')
    log_info "归档目录大小: $size"
    
    local file_count
    file_count=$(find "$ARCHIVE_DIR" -type f | wc -l | tr -d ' ')
    log_info "归档文件数量: $file_count"
}

generate_archive_report() {
    log_info "生成归档报告..."
    
    local report_file="$PROJECT_ROOT/docs/reports/archive-summary-$(date +%Y%m%d).md"
    
    cat > "$report_file" << EOF
# 归档总结报告

**生成时间**: $(date +'%Y-%m-%d %H:%M:%S')

## 归档统计

### 目录大小

\`\`\`
$(du -sh "$ARCHIVE_DIR" 2>/dev/null || echo "N/A")
\`\`\`

### 文件分布

| 类型 | 数量 |
|-----|------|
EOF

    if [ -d "$ARCHIVE_DIR" ]; then
        echo "| Markdown 文件 | $(find "$ARCHIVE_DIR" -name "*.md" | wc -l | tr -d ' ') |" >> "$report_file"
        echo "| 报告文件 | $(find "$ARCHIVE_DIR/reports" -name "*.md" 2>/dev/null | wc -l | tr -d ' ') |" >> "$report_file"
        echo "| 其他文件 | $(find "$ARCHIVE_DIR" -type f ! -name "*.md" | wc -l | tr -d ' ') |" >> "$report_file"
    else
        echo "| 归档目录不存在 | 0 |" >> "$report_file"
    fi
    
    cat >> "$report_file" << EOF

## 维护建议

- ✅ 定期审查归档内容，删除不再需要的历史文档
- ✅ 保持归档目录结构清晰
- ✅ 重要历史文档可考虑迁移到外部存储

---

*此报告由季度归档脚本自动生成*
EOF
    
    log_info "归档报告已生成: $report_file"
}

main() {
    log_info "========================================"
    log_info "开始季度归档维护"
    log_info "========================================"
    
    cd "$PROJECT_ROOT"
    
    archive_old_reports
    check_archive_size
    generate_archive_report
    
    log_info "========================================"
    log_info "季度归档维护完成"
    log_info "详细日志: $LOG_FILE"
    log_info "========================================"
}

main "$@"
