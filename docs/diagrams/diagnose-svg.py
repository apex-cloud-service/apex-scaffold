#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
SVG文件完整性诊断和修复工具
版本: v1.0
日期: 2026-09-05
功能: 检查SVG文件的常见问题并自动修复
"""

import os
import re
import xml.etree.ElementTree as ET
from pathlib import Path

DIAGRAM_DIR = Path("/Users/work/apex-scaffold/docs/diagrams")

def check_xml_validity(filepath):
    """检查XML格式有效性"""
    try:
        ET.parse(filepath)
        return True, "XML格式正确"
    except ET.ParseError as e:
        return False, f"XML解析错误: {str(e)}"
    except Exception as e:
        return False, f"未知错误: {str(e)}"

def check_unescaped_ampersand(filepath):
    """检查未转义的&符号"""
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()
    
    # 查找文本内容中未转义的&
    # 排除已转义的 &amp; &lt; &gt; &quot; &apos;
    pattern = r'>([^<]*&(?!amp;|lt;|gt;|quot;|apos;|#)[^<]*)<'
    matches = re.findall(pattern, content)
    
    if matches:
        return False, f"发现{len(matches)}个未转义的&符号"
    return True, "无未转义的&符号"

def check_viewbox(filepath):
    """检查viewBox属性"""
    try:
        tree = ET.parse(filepath)
        root = tree.getroot()
        
        # 移除命名空间前缀
        if '}' in root.tag:
            ns = {'svg': root.tag.split('}')[0].strip('{')}
            svg_elem = root
        else:
            svg_elem = root
        
        has_viewbox = 'viewBox' in svg_elem.attrib
        has_width = 'width' in svg_elem.attrib
        has_height = 'height' in svg_elem.attrib
        
        if not has_viewbox and not (has_width and has_height):
            return False, "缺少viewBox或width/height属性"
        
        return True, "尺寸属性正确"
    except Exception as e:
        return False, f"检查失败: {str(e)}"

def check_icon_definitions(filepath):
    """检查是否包含图标定义"""
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()
    
    icon_count = content.count('id="icon-')
    
    if icon_count == 0:
        return False, "未包含图标定义"
    
    return True, f"包含{icon_count}个图标定义"

def fix_unescaped_ampersand(filepath):
    """修复未转义的&符号"""
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()
    
    # 只替换文本内容中的&，不替换属性中的
    # 使用正则表达式匹配 >text content<
    def replace_amp(match):
        text = match.group(1)
        # 替换未转义的&
        text = re.sub(r'&(?!amp;|lt;|gt;|quot;|apos;|#)', '&amp;', text)
        return f'>{text}<'
    
    fixed_content = re.sub(r'>([^<]+)<', replace_amp, content)
    
    # 只有内容改变时才写入
    if fixed_content != content:
        # 创建备份
        backup_path = str(filepath) + '.fix-backup'
        with open(backup_path, 'w', encoding='utf-8') as f:
            f.write(content)
        
        # 写入修复后的内容
        with open(filepath, 'w', encoding='utf-8') as f:
            f.write(fixed_content)
        
        return True, "已修复并创建备份"
    
    return False, "无需修复"

def diagnose_file(filepath):
    """诊断单个文件"""
    results = {}
    
    # 1. XML有效性
    results['xml'] = check_xml_validity(filepath)
    
    # 2. 未转义字符
    results['ampersand'] = check_unescaped_ampersand(filepath)
    
    # 3. 尺寸属性
    results['viewbox'] = check_viewbox(filepath)
    
    # 4. 图标定义
    results['icons'] = check_icon_definitions(filepath)
    
    return results

def main():
    print("=" * 60)
    print("  SVG 文件完整性诊断工具 v1.0")
    print("=" * 60)
    print()
    
    # 获取所有SVG文件
    svg_files = []
    for f in DIAGRAM_DIR.glob("*.svg"):
        if 'before-icons' not in f.name and 'backup' not in f.name:
            svg_files.append(f)
    
    svg_files.sort()
    
    print(f"📁 找到 {len(svg_files)} 个 SVG 文件")
    print()
    
    total_issues = 0
    files_with_issues = 0
    
    for filepath in svg_files:
        filename = filepath.name
        results = diagnose_file(filepath)
        
        # 统计问题
        issues = [k for k, (status, _) in results.items() if not status]
        
        if issues:
            files_with_issues += 1
            total_issues += len(issues)
            
            print(f"⚠️  {filename}")
            for key, (status, message) in results.items():
                icon = "✅" if status else "❌"
                print(f"  {icon} {key}: {message}")
            print()
            
            # 尝试自动修复&符号问题
            if 'ampersand' in issues:
                print(f"  🔧 尝试自动修复...")
                fixed, msg = fix_unescaped_ampersand(filepath)
                if fixed:
                    print(f"  ✅ {msg}")
                print()
    
    print("=" * 60)
    print("          诊断完成")
    print("=" * 60)
    print()
    print("📊 统计结果:")
    print(f"  - 检查文件: {len(svg_files)}")
    print(f"  - 有问题的文件: {files_with_issues}")
    print(f"  - 总问题数: {total_issues}")
    print()
    
    if files_with_issues == 0:
        print("🎉 所有文件都正常！")
    else:
        print(f"⚠️  发现 {files_with_issues} 个文件有问题")
        print("💡 部分问题已自动修复，请重新运行验证")
    
    print()
    print("✅ 任务完成")

if __name__ == "__main__":
    main()
