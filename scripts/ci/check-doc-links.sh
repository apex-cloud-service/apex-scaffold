#!/bin/bash
set -euo pipefail

DOCS_DIR="/Users/work/apex-scaffold/docs"
REPORT_FILE="/Users/work/apex-scaffold/docs/LINK_FIX_REPORT.md"
BROKEN_LINKS_FILE="/tmp/broken_links.txt"
ALL_LINKS_FILE="/tmp/all_links.txt"

echo "🔍 开始扫描 docs 目录中的内部链接..."

> "$BROKEN_LINKS_FILE"
> "$ALL_LINKS_FILE"

find "$DOCS_DIR" -name "*.md" -type f | while read -r file; do
    grep -oE '\[([^\]]+)\]\(([^)]+)\)' "$file" | while read -r link; do
        link_text=$(echo "$link" | sed -E 's/\[([^\]]+)\].*/\1/')
        link_path=$(echo "$link" | sed -E 's/.*\(([^)]+)\)/\1/')
        
        if [[ "$link_path" =~ ^https?:// ]] || [[ "$link_path" =~ ^mailto: ]]; then
            continue
        fi
        
        if [[ "$link_path" =~ ^# ]]; then
            continue
        fi
        
        echo "${file}|${link_text}|${link_path}" >> "$ALL_LINKS_FILE"
        
        if [[ "$link_path" =~ ^/ ]]; then
            target="${link_path%%#*}"
        else
            file_dir=$(dirname "$file")
            target="${file_dir}/${link_path%%#*}"
        fi
        
        target=$(cd "$(dirname "$target")" 2>/dev/null && pwd)/$(basename "$target") 2>/dev/null || echo "$target"
        
        if [[ ! -e "$target" ]]; then
            echo "${file}|${link_text}|${link_path}|${target}" >> "$BROKEN_LINKS_FILE"
        fi
    done
done

total_links=$(wc -l < "$ALL_LINKS_FILE" | tr -d ' ')
broken_links=$(wc -l < "$BROKEN_LINKS_FILE" | tr -d ' ')

echo "✅ 扫描完成"
echo "   总链接数: ${total_links}"
echo "   断裂链接: ${broken_links}"

if [[ $broken_links -gt 0 ]]; then
    echo ""
    echo "⚠️  发现 ${broken_links} 个断裂的链接"
    exit 1
else
    echo ""
    echo "🎉 所有链接都是有效的！"
    exit 0
fi
