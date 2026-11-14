#!/bin/bash
# 提取所有 application*.yml 中的环境变量引用

set -euo pipefail

CONFIG_DIR="apex-components-parent/apex-system/src/main/resources"
TEST_CONFIG_DIR="apex-components-parent/apex-system/src/test/resources"
OUTPUT_FILE="env-vars-extracted.txt"

echo "正在扫描配置文件中的环境变量..."

# 使用 grep 提取所有 ${VAR_NAME:default} 或 ${VAR_NAME} 格式的变量
find "$CONFIG_DIR" "$TEST_CONFIG_DIR" -name "application*.yml" -type f | while read -r file; do
    echo "# 来源: $file" >> "$OUTPUT_FILE"
    grep -oE '\$\{[A-Z_][A-Z0-9_]*[:\}]' "$file" | sed 's/\${//g' | sed 's/[:\}]//g' | sort -u >> "$OUTPUT_FILE" || true
done

# 去重并排序
sort -u "$OUTPUT_FILE" -o "$OUTPUT_FILE"

echo "环境变量已提取到 $OUTPUT_FILE"
cat "$OUTPUT_FILE" | wc -l
