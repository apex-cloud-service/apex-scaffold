#!/usr/bin/env bash
# 生成测试报告脚本

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
source "${SCRIPT_DIR}/../lib/common.sh"

REPORT_DIR="$PROJECT_ROOT/target/reports"
SUMMARY_DIR="$REPORT_DIR/summary"

mkdir -p "$SUMMARY_DIR"

PROJECT_VERSION=$(awk '/<parent>/,/<\/parent>/{next} /<version>/{print; exit}' "$PROJECT_ROOT/pom.xml" | sed 's/.*<version>\(.*\)<\/version>.*/\1/')

echo "开始生成测试报告（项目版本: ${PROJECT_VERSION}）..."

# 统计测试结果
total=0
passed=0
failed=0
errors=0
skipped=0

# 扫描所有测试报告XML
for xml in "$PROJECT_ROOT"/*/target/surefire-reports/TEST-*.xml; do
    if [ -f "$xml" ]; then
        t=$(grep -o 'tests="[0-9]*"' "$xml" | head -1 | grep -o '[0-9]*' || echo "0")
        f=$(grep -o 'failures="[0-9]*"' "$xml" | head -1 | grep -o '[0-9]*' || echo "0")
        e=$(grep -o 'errors="[0-9]*"' "$xml" | head -1 | grep -o '[0-9]*' || echo "0")
        s=$(grep -o 'skipped="[0-9]*"' "$xml" | head -1 | grep -o '[0-9]*' || echo "0")
        
        total=$((total + t))
        failed=$((failed + f))
        errors=$((errors + e))
        skipped=$((skipped + s))
    fi
done

passed=$((total - failed - errors - skipped))
pass_rate=$(awk "BEGIN {if($total>0) printf \"%.2f\", ($passed/$total)*100; else print \"0\"}")

timestamp=$(date '+%Y-%m-%d %H:%M:%S')

# 生成Markdown报告
cat > "$SUMMARY_DIR/test-report.md" << 'MDEOF'
# Apex Scaffold 测试执行报告

**生成时间**: TIMESTAMP_PLACEHOLDER  
**项目版本**: VERSION_PLACEHOLDER

---

## 执行摘要

### 测试统计

| 指标 | 数量 | 百分比 |
|------|------|--------|
| **总测试数** | TOTAL_PLACEHOLDER | 100% |
| ✅ **通过** | PASSED_PLACEHOLDER | PASS_PCT_PLACEHOLDER% |
| ❌ **失败** | FAILED_PLACEHOLDER | FAIL_PCT_PLACEHOLDER% |
| ⚠️ **错误** | ERRORS_PLACEHOLDER | ERROR_PCT_PLACEHOLDER% |
| ⏭️ **跳过** | SKIPPED_PLACEHOLDER | SKIP_PCT_PLACEHOLDER% |

**通过率**: PASS_RATE_PLACEHOLDER%

---

## 模块测试详情

MDEOF

# 替换占位符
sed -i.bak "s/TIMESTAMP_PLACEHOLDER/$timestamp/" "$SUMMARY_DIR/test-report.md"
sed -i.bak "s/VERSION_PLACEHOLDER/$PROJECT_VERSION/" "$SUMMARY_DIR/test-report.md"
sed -i.bak "s/TOTAL_PLACEHOLDER/$total/" "$SUMMARY_DIR/test-report.md"
sed -i.bak "s/PASSED_PLACEHOLDER/$passed/" "$SUMMARY_DIR/test-report.md"
sed -i.bak "s/FAILED_PLACEHOLDER/$failed/" "$SUMMARY_DIR/test-report.md"
sed -i.bak "s/ERRORS_PLACEHOLDER/$errors/" "$SUMMARY_DIR/test-report.md"
sed -i.bak "s/SKIPPED_PLACEHOLDER/$skipped/" "$SUMMARY_DIR/test-report.md"
sed -i.bak "s/PASS_RATE_PLACEHOLDER/$pass_rate/" "$SUMMARY_DIR/test-report.md"

if [ "$total" -gt 0 ]; then
    pass_pct=$(awk "BEGIN {printf \"%.1f\", ($passed/$total)*100}")
    fail_pct=$(awk "BEGIN {printf \"%.1f\", ($failed/$total)*100}")
    error_pct=$(awk "BEGIN {printf \"%.1f\", ($errors/$total)*100}")
    skip_pct=$(awk "BEGIN {printf \"%.1f\", ($skipped/$total)*100}")
else
    pass_pct="0.0"
    fail_pct="0.0"
    error_pct="0.0"
    skip_pct="0.0"
fi

sed -i.bak "s/PASS_PCT_PLACEHOLDER/$pass_pct/" "$SUMMARY_DIR/test-report.md"
sed -i.bak "s/FAIL_PCT_PLACEHOLDER/$fail_pct/" "$SUMMARY_DIR/test-report.md"
sed -i.bak "s/ERROR_PCT_PLACEHOLDER/$error_pct/" "$SUMMARY_DIR/test-report.md"
sed -i.bak "s/SKIP_PCT_PLACEHOLDER/$skip_pct/" "$SUMMARY_DIR/test-report.md"

rm -f "$SUMMARY_DIR/test-report.md.bak"

# 添加模块统计
echo "" >> "$SUMMARY_DIR/test-report.md"
echo "| 模块 | 总数 | 通过 | 失败 | 错误 | 跳过 |" >> "$SUMMARY_DIR/test-report.md"
echo "|------|------|------|------|------|------|" >> "$SUMMARY_DIR/test-report.md"

for module in apex-common apex-license-spring-boot-starter apex-license-tools apex-domain apex-infrastructure apex-application apex-workflow apex-interfaces apex-auth-center apex-example apex-generator; do
    m_total=0
    m_passed=0
    m_failed=0
    m_errors=0
    m_skipped=0
    
    for xml in "$PROJECT_ROOT/$module"/target/surefire-reports/TEST-*.xml; do
        if [ -f "$xml" ]; then
            t=$(grep -o 'tests="[0-9]*"' "$xml" | head -1 | grep -o '[0-9]*' || echo "0")
            f=$(grep -o 'failures="[0-9]*"' "$xml" | head -1 | grep -o '[0-9]*' || echo "0")
            e=$(grep -o 'errors="[0-9]*"' "$xml" | head -1 | grep -o '[0-9]*' || echo "0")
            s=$(grep -o 'skipped="[0-9]*"' "$xml" | head -1 | grep -o '[0-9]*' || echo "0")
            
            m_total=$((m_total + t))
            m_failed=$((m_failed + f))
            m_errors=$((m_errors + e))
            m_skipped=$((m_skipped + s))
        fi
    done
    
    m_passed=$((m_total - m_failed - m_errors - m_skipped))
    
    if [ "$m_total" -gt 0 ]; then
        echo "| $module | $m_total | $m_passed | $m_failed | $m_errors | $m_skipped |" >> "$SUMMARY_DIR/test-report.md"
    fi
done

# 添加报告位置
cat >> "$SUMMARY_DIR/test-report.md" << 'ENDMD'

---

## 报告位置

- **测试报告**: `target/reports/summary/test-report.md`
- **单元测试**: `*/target/surefire-reports/`
- **覆盖率报告**: `*/target/site/jacoco/`

ENDMD

# 生成JSON
cat > "$SUMMARY_DIR/test-summary.json" << JSONEOF
{
  "timestamp": "$timestamp",
  "total": $total,
  "passed": $passed,
  "failed": $failed,
  "errors": $errors,
  "skipped": $skipped,
  "passRate": $pass_rate
}
JSONEOF

echo ""
echo "✅ 测试报告生成完成!"
echo "📄 Markdown: $SUMMARY_DIR/test-report.md"
echo "📊 JSON: $SUMMARY_DIR/test-summary.json"
echo ""
echo "统计摘要:"
echo "  总测试: $total"
echo "  通过: $passed"
echo "  失败: $failed"
echo "  错误: $errors"
echo "  跳过: $skipped"
echo "  通过率: ${pass_rate}%"
