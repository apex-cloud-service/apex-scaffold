#!/usr/bin/env bash
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

run_python_check() {
    log_info "运行 Python 链接验证..."
    
    python3 << 'EOF' | tee -a "$LOG_FILE"
import re
from pathlib import Path

docs_dir = Path("docs")
broken_links = []
total_links = 0

for md_file in docs_dir.rglob("*.md"):
    try:
        content = md_file.read_text(encoding='utf-8')
        links = re.findall(r'\[([^\]]+)\]\(([^)]+)\)', content)
        
        for link_text, link_path in links:
            if link_path.startswith(('http://', 'https://', 'mailto:', '#')):
                continue
            
            total_links += 1
            clean_path = link_path.split('#')[0]
            if not clean_path:
                continue
            
            if clean_path.startswith('/'):
                target = Path(clean_path[1:])
            else:
                target = (md_file.parent / clean_path).resolve()
            
            if not target.exists():
                broken_links.append({
                    'file': str(md_file),
                    'path': link_path
                })
    except Exception as e:
        pass

active_broken = [l for l in broken_links if not any(x in l['file'] for x in ['_archive', 'templates', '_templates', 'plans/ai-assisted'])]

print(f"总链接数: {total_links}")
print(f"断裂链接: {len(broken_links)}")
print(f"活跃文档断裂链接: {len(active_broken)}")
print(f"有效率: {((total_links - len(broken_links)) / total_links * 100):.2f}%")

if len(active_broken) > 0:
    print("\n⚠️ 活跃文档断裂链接:")
    for link in active_broken[:10]:
        print(f"  - {Path(link['file']).relative_to(docs_dir)}")
        print(f"    → {link['path']}")

exit(0 if len(active_broken) == 0 else 1)
EOF
}

generate_report() {
    log_info "生成检查报告..."
    
    local status="PASSED"
    if ! run_link_check || ! run_python_check; then
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
