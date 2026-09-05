#!/bin/bash
# SVG图表云原生图标批量升级脚本
# 版本: v1.0
# 日期: 2026-09-05
# 功能: 将云原生专业图标库集成到所有SVG文件中

set -e

DIAGRAM_DIR="/Users/work/apex-scaffold/docs/diagrams"
ICON_LIB="$DIAGRAM_DIR/_templates/cloud-native-icons.svg"
BACKUP_SUFFIX=".before-icons"

echo "=========================================="
echo "  SVG 云原生图标批量升级工具 v1.0"
echo "=========================================="
echo ""

# 检查图标库文件
if [ ! -f "$ICON_LIB" ]; then
  echo "❌ 错误：图标库文件不存在: $ICON_LIB"
  exit 1
fi

echo "✅ 图标库文件检查通过"
echo ""

# 提取图标定义
echo "📦 正在提取图标定义..."
ICON_DEFS=$(sed -n '/<defs>/,/<\/defs>/p' "$ICON_LIB" | grep -v '<?xml' | grep -v '<svg')

if [ -z "$ICON_DEFS" ]; then
  echo "❌ 错误：无法提取图标定义"
  exit 1
fi

echo "✅ 成功提取 $(echo "$ICON_DEFS" | grep -c 'id="icon-') 个图标定义"
echo ""

# 升级文件列表（排除特殊文件）
FILES_TO_UPGRADE=(
  "01_logical_architecture.svg"
  "02_physical_architecture.svg"
  "03_technical_architecture.svg"
  "04_login_auth_flow.svg"
  "05_permission_check_flow.svg"
  "06_workflow_approval_flow.svg"
  "07_file_upload_flow.svg"
  "08_database_er_diagram.svg"
  "09_sentinel_rate_limit_circuit_breaker.svg"
  "10_multi_level_cache_architecture.svg"
  "11_auto_retry_strategy_flow.svg"
  "12_security_protection_system.svg"
  "13_deployment_architecture.svg"
  "14_network_architecture.svg"
  "15_data_flow_architecture.svg"
  "16_mq_architecture.svg"
  "17_monitoring_architecture.svg"
  "18_microservice_governance.svg"
  "19_backend_technology_stack.svg"
  "20_infrastructure_components.svg"
  "21_technology_ecosystem.svg"
  "22_frontend_technology_stack.svg"
  "23_frontend_backend_interaction.svg"
  "24_grafana_dashboard_architecture.svg"
  "25_prometheus_monitoring_architecture.svg"
  "26_disaster_recovery_flow.svg"
  "27_cicd_pipeline_detail.svg"
  "28_api_gateway_detail.svg"
  "29_security_threat_model.svg"
)

total_files=${#FILES_TO_UPGRADE[@]}
upgraded=0
skipped=0
failed=0

echo "🚀 开始批量升级 $total_files 个文件..."
echo ""

for file in "${FILES_TO_UPGRADE[@]}"; do
  filepath="$DIAGRAM_DIR/$file"
  
  # 检查文件是否存在
  if [ ! -f "$filepath" ]; then
    echo "⚠️  跳过: $file (文件不存在)"
    skipped=$((skipped + 1))
    continue
  fi
  
  # 检查是否已经包含图标库
  if grep -q 'id="icon-kubernetes"' "$filepath" 2>/dev/null || \
     grep -q 'id="icon-springboot"' "$filepath" 2>/dev/null; then
    echo "⏭️  跳过: $file (已包含图标库)"
    skipped=$((skipped + 1))
    continue
  fi
  
  echo "🔄 处理: $file"
  
  # 创建备份
  cp "$filepath" "${filepath}${BACKUP_SUFFIX}"
  
  # 检查是否已有 <defs> 标签
  if grep -q '<defs>' "$filepath"; then
    # 在现有 <defs> 中插入图标定义
    # 使用临时文件
    tmpfile=$(mktemp)
    
    # 在 </defs> 前插入图标定义
    awk -v icons="$ICON_DEFS" '
      /<\/defs>/ {
        print "    <!-- 云原生专业图标库 - 自动添加 -->"
        print icons
      }
      { print }
    ' "$filepath" > "$tmpfile"
    
    mv "$tmpfile" "$filepath"
  else
    # 在 <svg> 后添加 <defs> 和图标定义
    tmpfile=$(mktemp)
    
    awk -v icons="$ICON_DEFS" '
      /<svg[^>]*>/ && !found {
        print
        print "  <defs>"
        print "    <!-- 云原生专业图标库 - 自动添加 -->"
        print icons
        print "  </defs>"
        found=1
        next
      }
      { print }
    ' "$filepath" > "$tmpfile"
    
    mv "$tmpfile" "$filepath"
  fi
  
  # 验证XML格式
  if xmllint --noout "$filepath" 2>/dev/null; then
    echo "   ✅ 成功升级"
    upgraded=$((upgraded + 1))
  else
    echo "   ❌ 格式错误，恢复备份"
    mv "${filepath}${BACKUP_SUFFIX}" "$filepath"
    failed=$((failed + 1))
  fi
  
  echo ""
done

echo "=========================================="
echo "          批量升级完成"
echo "=========================================="
echo ""
echo "📊 统计结果:"
echo "  - 总文件数: $total_files"
echo "  - 成功升级: $upgraded"
echo "  - 跳过文件: $skipped"
echo "  - 失败文件: $failed"
echo ""

if [ $failed -eq 0 ]; then
  echo "🎉 所有文件升级成功！"
  echo ""
  echo "📝 备份文件位置: *.before-icons"
  echo "💡 如需回滚，运行: ./rollback-icons.sh"
else
  echo "⚠️  部分文件升级失败，请检查日志"
fi

echo ""
echo "✅ 任务完成"
