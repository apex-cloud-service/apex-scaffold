#!/bin/bash
# 文档重组迁移脚本
# 此脚本将现有文档迁移到新的专业化目录结构

set -e

BASE_DIR="/Users/work/apex-scaffold/docs"
cd "$BASE_DIR"

echo "=== 开始文档迁移 ==="

# 00-getting-started: 快速开始和入门文档
echo "迁移快速开始文档..."
mv -n QUICKSTART.md 00-getting-started/quickstart.md 2>/dev/null || true
mv -n QUICK_START.md 00-getting-started/quick-start-alternative.md 2>/dev/null || true
mv -n ONBOARDING_ARCHITECTURE_GUIDE.md 00-getting-started/onboarding-guide.md 2>/dev/null || true
mv -n EXAMPLE_GUIDE.md 00-getting-started/example-guide.md 2>/dev/null || true
mv -n EXAMPLES_SUMMARY.md 00-getting-started/examples-summary.md 2>/dev/null || true

# 01-guides: 使用指南和最佳实践
echo "迁移使用指南..."
mv -n BEST_PRACTICES.md 01-guides/best-practices.md 2>/dev/null || true
mv -n DEVELOPER_HANDBOOK.md 01-guides/developer-handbook.md 2>/dev/null || true
mv -n TROUBLESHOOTING.md 01-guides/troubleshooting.md 2>/dev/null || true
mv -n guides/* 01-guides/ 2>/dev/null || true

# 功能指南
mv -n WORKFLOW_GUIDE.md 01-guides/workflow-guide.md 2>/dev/null || true
mv -n WORKFLOW_DESIGNER_GUIDE.md 01-guides/workflow-designer-guide.md 2>/dev/null || true
mv -n WORKFLOW_MONITOR_GUIDE.md 01-guides/workflow-monitor-guide.md 2>/dev/null || true
mv -n DATA_SCOPE_USAGE.md 01-guides/data-scope-usage.md 2>/dev/null || true
mv -n DATA_SCOPE.md 01-guides/data-scope-reference.md 2>/dev/null || true
mv -n DATA_SOURCE_SWITCH_USAGE.md 01-guides/data-source-switch-usage.md 2>/dev/null || true
mv -n AUDIT_LOG_USAGE.md 01-guides/audit-log-usage.md 2>/dev/null || true
mv -n MATERIALS_USAGE_GUIDE.md 01-guides/materials-usage-guide.md 2>/dev/null || true
mv -n SPI_PLUGIN_GUIDE.md 01-guides/spi-plugin-guide.md 2>/dev/null || true
mv -n DISTRIBUTED_BULKHEAD_GUIDE.md 01-guides/distributed-bulkhead-guide.md 2>/dev/null || true
mv -n READ_DEGRADATION_GUIDE.md 01-guides/read-degradation-guide.md 2>/dev/null || true
mv -n MESSAGE_MIDDLEWARE.md 01-guides/message-middleware.md 2>/dev/null || true

# SSO和认证
mv -n SSO_IMPLEMENTATION_GUIDE.md 01-guides/sso-implementation-guide.md 2>/dev/null || true
mv -n KEYCLOAK_INTEGRATION_GUIDE.md 01-guides/keycloak-integration-guide.md 2>/dev/null || true
mv -n OAUTH2_INTEGRATION_GUIDE.md 01-guides/oauth2-integration-guide.md 2>/dev/null || true
mv -n SSO_OAuth2_Keycloak_完整文档包.md 01-guides/sso-oauth2-keycloak-complete.md 2>/dev/null || true

# 文档搜索
mv -n DOC_SEARCH_GUIDE.md 01-guides/doc-search-guide.md 2>/dev/null || true
mv -n DOC_SEARCH_README.md 01-guides/doc-search-readme.md 2>/dev/null || true
mv -n DOC_SEARCH_FIX.md 01-guides/doc-search-fix.md 2>/dev/null || true
mv -n DOC_SEARCH_CHANGELOG.md 01-guides/doc-search-changelog.md 2>/dev/null || true

# 02-architecture: 架构设计文档
echo "迁移架构文档..."
# architecture 目录已经存在，保持不变
mv -n ARCHITECTURE_EVOLUTION.md 02-architecture/architecture-evolution.md 2>/dev/null || true
mv -n ARCHITECTURE_INDEX.md 02-architecture/architecture-index.md 2>/dev/null || true
mv -n ARCHITECTURE_USAGE_GUIDE.md 02-architecture/architecture-usage-guide.md 2>/dev/null || true
mv -n architecture.md 02-architecture/architecture-overview.md 2>/dev/null || true

# 03-api: API文档
echo "迁移API文档..."
# api 目录已经存在，保持不变
mv -n API_VERSION_MANAGEMENT.md 03-api/api-version-management.md 2>/dev/null || true
mv -n API_PATH_CORRECTIONS.md 03-api/api-path-corrections.md 2>/dev/null || true
mv -n API_ROUTES.md 03-api/api-routes.md 2>/dev/null || true
mv -n API_DOCUMENTATION_PREVIEW.md 03-api/api-documentation-preview.md 2>/dev/null || true
mv -n SpringDoc注解模板.md 03-api/springdoc-annotation-templates.md 2>/dev/null || true
mv -n SpringDoc注解完整性报告.md 03-api/springdoc-annotation-audit.md 2>/dev/null || true
mv -n springdoc-annotation-audit-report.md 03-api/springdoc-audit-report.md 2>/dev/null || true

# 04-development: 开发文档
echo "迁移开发文档..."
mv -n CODEGEN.md 04-development/codegen.md 2>/dev/null || true
mv -n JAVADOC_GUIDE.md 04-development/javadoc-guide.md 2>/dev/null || true
mv -n LOMBOK_GUIDE.md 04-development/lombok-guide.md 2>/dev/null || true
mv -n JMH_BENCHMARK_GUIDE.md 04-development/jmh-benchmark-guide.md 2>/dev/null || true
mv -n SVG_MAINTENANCE_GUIDE.md 04-development/svg-maintenance-guide.md 2>/dev/null || true
mv -n MERMAID_VIEWER_GUIDE.md 04-development/mermaid-viewer-guide.md 2>/dev/null || true
mv -n standards/* 04-development/ 2>/dev/null || true

# 数据库配置相关
mv -n DATABASE_CONFIG_*.md 04-development/ 2>/dev/null || true

# 前端开发
mkdir -p 04-development/frontend 2>/dev/null || true
mv -n frontend/* 04-development/frontend/ 2>/dev/null || true
mv -n FRONTEND_*.md 04-development/frontend/ 2>/dev/null || true
mv -n frontend-*.md 04-development/frontend/ 2>/dev/null || true

# 05-operations: 运维文档
echo "迁移运维文档..."
mv -n DEPLOYMENT.md 05-operations/deployment.md 2>/dev/null || true
mv -n CONFIGURATION.md 05-operations/configuration.md 2>/dev/null || true
mv -n SECURITY_PROTECTIONS.md 05-operations/security-protections.md 2>/dev/null || true
mv -n ADVANCED_FEATURES.md 05-operations/advanced-features.md 2>/dev/null || true

# 06-testing: 测试文档
echo "迁移测试文档..."
mv -n TEST_*.md 06-testing/ 2>/dev/null || true
mv -n integration-test-guide.md 06-testing/integration-test-guide.md 2>/dev/null || true
mv -n API接口测试报告.md 06-testing/api-test-report.md 2>/dev/null || true
mv -n API接口测试计划.md 06-testing/api-test-plan.md 2>/dev/null || true
mv -n API参数类型检查报告.md 06-testing/api-parameter-type-audit.md 2>/dev/null || true

# 07-commercial: 商业化文档
echo "迁移商业化文档..."
mv -n COMMERCIAL_*.md 07-commercial/ 2>/dev/null || true
mv -n LICENSE_MANAGEMENT.md 07-commercial/license-management.md 2>/dev/null || true
mv -n COMMERCIAL_VERSIONS.md 07-commercial/commercial-versions.md 2>/dev/null || true
mv -n RELEASE_*.md 07-commercial/ 2>/dev/null || true
# commercial 目录已经存在
mv -n commercial/* 07-commercial/ 2>/dev/null || true
# sales 目录已经存在
mv -n sales 07-commercial/sales 2>/dev/null || true

# 08-project-management: 项目管理文档
echo "迁移项目管理文档..."
mv -n P0_SYSTEM_MANAGEMENT.md 08-project-management/p0-system-management.md 2>/dev/null || true
mv -n SCAFFOLD_REVIEW_GUIDE.md 08-project-management/scaffold-review-guide.md 2>/dev/null || true
mv -n SCAFFOLD_ENHANCEMENT_REVIEW.md 08-project-management/scaffold-enhancement-review.md 2>/dev/null || true
mv -n BUSINESS_CODE_FUNCTION_ENHANCEMENT_REQUIREMENTS.md 08-project-management/function-enhancement-requirements.md 2>/dev/null || true
mv -n IMPLEMENTATION_TEMPLATES.md 08-project-management/implementation-templates.md 2>/dev/null || true
mv -n DOCUMENT_CHART_IMPLEMENTATION_PROGRESS.md 08-project-management/chart-implementation-progress.md 2>/dev/null || true
# plans 目录
mv -n plans 08-project-management/plans 2>/dev/null || true
# features 目录
mv -n features 08-project-management/features 2>/dev/null || true

# _archive: 归档文档
echo "归档临时报告和历史文档..."
# 所有 reports 目录下的文档
mv -n reports _archive/reports 2>/dev/null || true
# 所有进度报告、总结、修复报告
mv -n *报告.md _archive/ 2>/dev/null || true
mv -n *总结.md _archive/ 2>/dev/null || true
mv -n *summary.md _archive/ 2>/dev/null || true
mv -n work-summary.md _archive/ 2>/dev/null || true
mv -n implementation-summary.md _archive/ 2>/dev/null || true
mv -n backend-completion-checklist.md _archive/ 2>/dev/null || true
mv -n INTEGRATION_SUMMARY.md _archive/ 2>/dev/null || true
mv -n REFACTORING_COMPLETION_REPORT.md _archive/ 2>/dev/null || true
mv -n PHASE2_FIX_EXAMPLES.md _archive/ 2>/dev/null || true
mv -n SVG_OPTIMIZATION_REPORT.md _archive/ 2>/dev/null || true
mv -n MERMAID_VERIFICATION_SUMMARY.md _archive/ 2>/dev/null || true
mv -n MERMAID_VIEWER_VERIFICATION_REPORT.md _archive/ 2>/dev/null || true
# changes 和 integration 目录
mv -n changes _archive/ 2>/dev/null || true
mv -n integration _archive/ 2>/dev/null || true

# _templates: 模板文档
echo "整理模板文档..."
# IMPLEMENTATION_TEMPLATES.md 已移动到 08-project-management

echo "=== 文档迁移完成 ==="
echo "请检查新的目录结构"
