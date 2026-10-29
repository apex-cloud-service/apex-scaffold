#!/bin/bash
# SVG图表云原生图标升级回滚脚本
# 版本: v1.0
# 日期: 2026-09-05

set -e

DIAGRAM_DIR="/Users/work/apex-scaffold/docs/diagrams"
BACKUP_SUFFIX=".before-icons"

echo "=========================================="
echo "  SVG 图标升级回滚工具 v1.0"
echo "=========================================="
echo ""

# 查找所有备份文件
backup_files=$(find "$DIAGRAM_DIR" -name "*${BACKUP_SUFFIX}" -type f)

if [ -z "$backup_files" ]; then
  echo "❌ 未找到备份文件"
  exit 1
fi

backup_count=$(echo "$backup_files" | wc -l | xargs)
echo "📦 找到 $backup_count 个备份文件"
echo ""

read -p "⚠️  确认要回滚所有文件吗？(yes/no): " confirm

if [ "$confirm" != "yes" ]; then
  echo "❌ 取消回滚"
  exit 0
fi

echo ""
echo "🔄 开始回滚..."
echo ""

restored=0

while IFS= read -r backup_file; do
  original_file="${backup_file%$BACKUP_SUFFIX}"
  
  if [ -f "$original_file" ]; then
    mv "$backup_file" "$original_file"
    echo "✅ 恢复: $(basename "$original_file")"
    restored=$((restored + 1))
  fi
done <<< "$backup_files"

echo ""
echo "=========================================="
echo "          回滚完成"
echo "=========================================="
echo ""
echo "📊 统计: 已恢复 $restored 个文件"
echo ""
echo "✅ 任务完成"
