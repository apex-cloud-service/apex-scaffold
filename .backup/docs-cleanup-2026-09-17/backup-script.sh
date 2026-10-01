#!/bin/bash

BACKUP_DIR="/Users/work/apex-scaffold/.backup/docs-cleanup-2026-09-17"
SOURCE_DIR="/Users/work/apex-scaffold/docs"

echo "开始备份文件..."
echo "备份目录: $BACKUP_DIR"
echo "源目录: $SOURCE_DIR"
echo ""

BACKUP_COUNT=0

# 类别1: _archive/trae-reports/2026-09/ (38个文件)
echo "=== 类别1: 备份 _archive/trae-reports/2026-09/ ==="
FILES_CAT1=(
"_archive/trae-reports/2026-09/FINAL-REPORT.md"
"_archive/trae-reports/2026-09/FINAL_COMPLETION_SUMMARY.md"
"_archive/trae-reports/2026-09/TASK-COMPLETION-REPORT.md"
"_archive/trae-reports/2026-09/TASK-EXECUTION-SUMMARY.md"
"_archive/trae-reports/2026-09/TEST-REPORT.md"
"_archive/trae-reports/2026-09/change-summary.md"
"_archive/trae-reports/2026-09/documentation-optimization-plan.md"
"_archive/trae-reports/2026-09/documents-integration-report.md"
"_archive/trae-reports/2026-09/final-completion-report-v2.md"
"_archive/trae-reports/2026-09/final-completion-report-v3.md"
"_archive/trae-reports/2026-09/final-completion-report.md"
"_archive/trae-reports/2026-09/final-integration-completion-report.md"
"_archive/trae-reports/2026-09/frontend-code-cleanup-report.md"
"_archive/trae-reports/2026-09/frontend-implementation-gap-analysis.md"
"_archive/trae-reports/2026-09/frontend-integration-completion-report.md"
"_archive/trae-reports/2026-09/frontend-integration-final-report.md"
"_archive/trae-reports/2026-09/frontend-integration-report.md"
"_archive/trae-reports/2026-09/frontend-integration-summary.md"
"_archive/trae-reports/2026-09/frontend-message-approval-completion-report.md"
"_archive/trae-reports/2026-09/implementation-consistency-fix-report.md"
"_archive/trae-reports/2026-09/integration-verification-report.md"
"_archive/trae-reports/2026-09/office-integration-progress.md"
"_archive/trae-reports/2026-09/office-platform-completion-summary.md"
"_archive/trae-reports/2026-09/office-platform-delivery-report.md"
"_archive/trae-reports/2026-09/p0-fix-completion-report.md"
"_archive/trae-reports/2026-09/phase-2-3-backend-completion-report.md"
"_archive/trae-reports/2026-09/phase-2-3-final-summary.md"
"_archive/trae-reports/2026-09/phase-2-3-implementation-summary.md"
"_archive/trae-reports/2026-09/phase-2-progress-report.md"
"_archive/trae-reports/2026-09/phase1-issues-report.md"
"_archive/trae-reports/2026-09/phase4-assessment-report.md"
"_archive/trae-reports/2026-09/project-final-report.md"
"_archive/trae-reports/2026-09/project-reorganization-completion-report.md"
"_archive/trae-reports/2026-09/project-summary.md"
"_archive/trae-reports/2026-09/scripts-review-report.md"
"_archive/trae-reports/2026-09/short-term-optimization-summary.md"
"_archive/trae-reports/2026-09/svg-deep-verification-report.md"
"_archive/trae-reports/2026-09/svg-diagrams-review-report.md"
)

for file in "${FILES_CAT1[@]}"; do
    if [ -f "$SOURCE_DIR/$file" ]; then
        mkdir -p "$BACKUP_DIR/$(dirname "$file")"
        cp "$SOURCE_DIR/$file" "$BACKUP_DIR/$file"
        echo "✓ $file"
        ((BACKUP_COUNT++))
    else
        echo "✗ 文件不存在: $file"
    fi
done

# 类别2.1: 多版本重复报告 (15个)
echo ""
echo "=== 类别2.1: 备份多版本重复报告 ==="
FILES_CAT2_1=(
"_archive/reports/test-execution-complete-report.md"
"_archive/reports/test-execution-final-report.md"
"_archive/reports/test-execution-final-success-report.md"
"_archive/reports/test-execution-report-20260909.md"
"_archive/reports/delivery/当前对话完成总结.md"
"_archive/reports/delivery/最终交付总结_FINAL.md"
"_archive/reports/delivery/最终交付报告.md"
"_archive/reports/delivery/最终实施总结.md"
"_archive/reports/delivery/项目交付总结.md"
"_archive/reports/progress/前端集成完成总结.md"
"_archive/reports/progress/实施进度报告.md"
"_archive/reports/progress/最终进度报告.md"
"_archive/reports/archive/project-deliveries/task-completion-report.md"
"_archive/reports/archive/project-deliveries/task-final-completion.md"
"_archive/reports/archive/project-deliveries/task-final-summary.md"
)

for file in "${FILES_CAT2_1[@]}"; do
    if [ -f "$SOURCE_DIR/$file" ]; then
        mkdir -p "$BACKUP_DIR/$(dirname "$file")"
        cp "$SOURCE_DIR/$file" "$BACKUP_DIR/$file"
        echo "✓ $file"
        ((BACKUP_COUNT++))
    else
        echo "✗ 文件不存在: $file"
    fi
done

# 类别2.2: SVG图表优化相关 (13个)
echo ""
echo "=== 类别2.2: 备份SVG图表优化相关报告 ==="
FILES_CAT2_2=(
"_archive/reports/2026-09-03-diagram-fix.md"
"_archive/reports/2026-09-03-png-display-fix.md"
"_archive/reports/2026-09-03-png-to-svg-migration.md"
"_archive/reports/2026-09-03-vscode-png-fix.md"
"_archive/reports/2026-09-05-batch-upgrade-complete-report.md"
"_archive/reports/2026-09-05-cloud-native-icons-final-report.md"
"_archive/reports/2026-09-05-cloud-native-icons-summary.md"
"_archive/reports/2026-09-05-svg-display-fix-report.md"
"_archive/reports/2026-09-05-svg-optimization-report.md"
"_archive/reports/phase3-svg-fix-report.md"
"_archive/reports/svg-color-optimization-plan.md"
"_archive/reports/svg-docs-alignment-plan.md"
"_archive/reports/修复SVG图表显示问题及清理备份文件.md"
)

for file in "${FILES_CAT2_2[@]}"; do
    if [ -f "$SOURCE_DIR/$file" ]; then
        mkdir -p "$BACKUP_DIR/$(dirname "$file")"
        cp "$SOURCE_DIR/$file" "$BACKUP_DIR/$file"
        echo "✓ $file"
        ((BACKUP_COUNT++))
    else
        echo "✗ 文件不存在: $file"
    fi
done

# 类别2.3: 已完成的任务清单和进度报告 (24个)
echo ""
echo "=== 类别2.3: 备份已完成任务清单和进度报告 ==="
FILES_CAT2_3=(
"_archive/reports/backend-completion-summary.md"
"_archive/reports/frontend-completion-summary.md"
"_archive/reports/task-completion-summary.md"
"_archive/reports/project-final-summary.md"
"_archive/reports/short-term-goals-completion-report.md"
"_archive/reports/long-term-optimization-completion-report.md"
"_archive/reports/doc-search-completion-report.md"
"_archive/reports/doc-search-v2-completion-report.md"
"_archive/reports/code-changes-summary.md"
"_archive/reports/delivery-summary.md"
"_archive/reports/execution-summary.md"
"_archive/reports/workflow-implementation.md"
"_archive/reports/workflow-monitor-complete.md"
"_archive/reports/workflow-progress.md"
"_archive/reports/system-optimization-plan.md"
"_archive/reports/frontend-creation-report.md"
"_archive/reports/frontend-code-files.md"
"_archive/reports/documentation-update-checklist.md"
"_archive/reports/pre-commit-checklist.md"
"_archive/reports/archive/project-deliveries/execution-status.md"
"_archive/reports/archive/project-deliveries/final-delivery-report.md"
"_archive/reports/archive/project-deliveries/final-execution-status.md"
"_archive/reports/archive/project-deliveries/final-project-summary.md"
"_archive/reports/archive/project-deliveries/optimization-completion.md"
)

for file in "${FILES_CAT2_3[@]}"; do
    if [ -f "$SOURCE_DIR/$file" ]; then
        mkdir -p "$BACKUP_DIR/$(dirname "$file")"
        cp "$SOURCE_DIR/$file" "$BACKUP_DIR/$file"
        echo "✓ $file"
        ((BACKUP_COUNT++))
    else
        echo "✗ 文件不存在: $file"
    fi
done

# 类别3.1: 过时的操作和部署指南 (10个)
echo ""
echo "=== 类别3.1: 备份过时的操作和部署指南 ==="
FILES_CAT3_1=(
"_archive/operations/configuration.md"
"_archive/operations/deployment.md"
"_archive/operations/deployment-checklist.md"
"_archive/operations/environment-variables.md"
"_archive/operations/monitoring.md"
"_archive/operations/mysql-startup-guide.md"
"_archive/operations/sql-execution-guide.md"
"_archive/operations/grafana-status-summary.md"
"_archive/operations/monitoring-dashboard-configuration.md"
"_archive/operations/k8s-production-optimization.md"
)

for file in "${FILES_CAT3_1[@]}"; do
    if [ -f "$SOURCE_DIR/$file" ]; then
        mkdir -p "$BACKUP_DIR/$(dirname "$file")"
        cp "$SOURCE_DIR/$file" "$BACKUP_DIR/$file"
        echo "✓ $file"
        ((BACKUP_COUNT++))
    else
        echo "✗ 文件不存在: $file"
    fi
done

# 类别3.2: Postman测试集合 (7个)
echo ""
echo "=== 类别3.2: 备份Postman测试集合 ==="
FILES_CAT3_2=(
"_archive/postman/README.md"
"_archive/postman/QUICKSTART.md"
"_archive/postman/Apex-Scaffold-API.postman_collection.json"
"_archive/postman/Development.postman_environment.json"
"_archive/postman/Test.postman_environment.json"
"_archive/postman/run-tests.sh"
"_archive/postman/run-tests.bat"
)

for file in "${FILES_CAT3_2[@]}"; do
    if [ -f "$SOURCE_DIR/$file" ]; then
        mkdir -p "$BACKUP_DIR/$(dirname "$file")"
        cp "$SOURCE_DIR/$file" "$BACKUP_DIR/$file"
        echo "✓ $file"
        ((BACKUP_COUNT++))
    else
        echo "✗ 文件不存在: $file"
    fi
done

# 类别3.3: 根文档目录下的过时报告 (1个)
echo ""
echo "=== 类别3.3: 备份根文档目录下的过时报告 ==="
FILES_CAT3_3=(
"_archive/final-completion-report.md"
)

for file in "${FILES_CAT3_3[@]}"; do
    if [ -f "$SOURCE_DIR/$file" ]; then
        mkdir -p "$BACKUP_DIR/$(dirname "$file")"
        cp "$SOURCE_DIR/$file" "$BACKUP_DIR/$file"
        echo "✓ $file"
        ((BACKUP_COUNT++))
    else
        echo "✗ 文件不存在: $file"
    fi
done

# 类别4: docs/根目录下的临时报告 (12个)
echo ""
echo "=== 类别4: 备份docs/根目录下的临时报告 ==="
FILES_CAT4=(
"fix-summary-2026-09-17.md"
"frontend-implementation-guide.md"
"monitoring-dashboard-final-delivery.md"
"monitoring-dashboard-frontend-updates.md"
"monitoring-dashboard-optimization-summary.md"
"job-query-feature-guide.md"
"job-query-implementation-summary.md"
"DOCS_REORGANIZATION_REPORT.md"
"INTERNAL_LINK_FIX_REPORT.md"
"LINK_FIX_FINAL_REPORT.md"
"LINK_FIX_REPORT_DETAILED.md"
"LINK_FIX_REPORT.md"
)

for file in "${FILES_CAT4[@]}"; do
    if [ -f "$SOURCE_DIR/$file" ]; then
        mkdir -p "$BACKUP_DIR/$(dirname "$file")"
        cp "$SOURCE_DIR/$file" "$BACKUP_DIR/$file"
        echo "✓ $file"
        ((BACKUP_COUNT++))
    else
        echo "✗ 文件不存在: $file"
    fi
done

# 类别5.1: 技术补丁和临时说明 (6个)
echo ""
echo "=== 类别5.1: 备份技术补丁和临时说明 ==="
FILES_CAT5_1=(
"_archive/technical/autowired-springdoc-fix-summary.md"
"_archive/technical/springdoc_annotation_audit_plan.md"
"_archive/technical/test-completeness-plan.md"
"_archive/technical/业务日志统一查询补充说明.md"
"_archive/technical/构建工具不一致问题分析与修复方案.md"
"_archive/technical/编译错误修复补丁.md"
)

for file in "${FILES_CAT5_1[@]}"; do
    if [ -f "$SOURCE_DIR/$file" ]; then
        mkdir -p "$BACKUP_DIR/$(dirname "$file")"
        cp "$SOURCE_DIR/$file" "$BACKUP_DIR/$file"
        echo "✓ $file"
        ((BACKUP_COUNT++))
    else
        echo "✗ 文件不存在: $file"
    fi
done

# 类别5.2: 临时总结和修复报告 (9个)
echo ""
echo "=== 类别5.2: 备份临时总结和修复报告 ==="
FILES_CAT5_2=(
"_archive/INTEGRATION_SUMMARY.md"
"_archive/REFACTORING_COMPLETION_REPORT.md"
"_archive/MERMAID_VERIFICATION_SUMMARY.md"
"_archive/MERMAID_VIEWER_VERIFICATION_REPORT.md"
"_archive/SVG_OPTIMIZATION_REPORT.md"
"_archive/webflux-handler-summary.md"
"_archive/work-summary.md"
"_archive/implementation-summary.md"
"_archive/Kiro-AI署名修复报告.md"
)

for file in "${FILES_CAT5_2[@]}"; do
    if [ -f "$SOURCE_DIR/$file" ]; then
        mkdir -p "$BACKUP_DIR/$(dirname "$file")"
        cp "$SOURCE_DIR/$file" "$BACKUP_DIR/$file"
        echo "✓ $file"
        ((BACKUP_COUNT++))
    else
        echo "✗ 文件不存在: $file"
    fi
done

echo ""
echo "================================"
echo "备份完成! 总计备份文件: $BACKUP_COUNT 个"
echo "备份目录: $BACKUP_DIR"
echo "================================"
