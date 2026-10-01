#!/usr/bin/env python3
"""
文档链接自动修复工具
修复 file:// 协议链接和过时的 scripts 路径
"""
import os
import re
from pathlib import Path
from typing import List, Tuple

def convert_file_url_to_relative(base_file: Path, file_url: str) -> str:
    """
    将 file:// URL 转换为相对路径
    
    Args:
        base_file: 当前文档文件路径
        file_url: file:// 协议的URL
    
    Returns:
        相对路径
    """
    # 提取文件路径（移除 file:// 和锚点）
    if not file_url.startswith('file:///'):
        return file_url
    
    # 提取路径和锚点
    match = re.match(r'file:///([^#]+)(#.*)?', file_url)
    if not match:
        return file_url
    
    target_path = Path('/' + match.group(1))
    anchor = match.group(2) or ''
    
    # 检查目标文件是否存在
    if not target_path.exists():
        print(f"  ⚠️  目标文件不存在: {target_path}")
        return file_url
    
    # 计算相对路径
    try:
        # 获取两个文件的绝对路径
        base_dir = base_file.parent.resolve()
        target_abs = target_path.resolve()
        
        # 计算相对路径
        rel_path = os.path.relpath(target_abs, base_dir)
        return rel_path + anchor
    except Exception as e:
        print(f"  ❌ 转换失败: {e}")
        return file_url

def update_script_paths(content: str) -> Tuple[str, int]:
    """
    更新 scripts 目录路径
    
    Returns:
        (更新后的内容, 修改次数)
    """
    changes = 0
    
    # 路径映射
    path_mappings = {
        'scripts/grafana/': 'scripts/ops/monitoring/grafana/',
        'scripts/batch-add-lombok.sh': 'scripts/dev/format/batch-add-lombok.sh',
        'scripts/batch-add-javadoc.sh': 'scripts/dev/format/batch-add-javadoc.sh',
        'scripts/scan-javadoc-issues.sh': 'scripts/dev/check/scan-javadoc-issues.sh',
        'scripts/scan-lombok-issues.sh': 'scripts/dev/check/scan-lombok-issues.sh',
        'scripts/check_javadoc.sh': 'scripts/dev/check/check_javadoc.sh',
        'scripts/backup/db-': 'scripts/data/backup/db-',
    }
    
    updated_content = content
    for old_path, new_path in path_mappings.items():
        if old_path in updated_content:
            updated_content = updated_content.replace(old_path, new_path)
            changes += updated_content.count(new_path) - content.count(new_path)
    
    return updated_content, changes

def fix_file_links(md_file: Path, dry_run: bool = False) -> Tuple[int, int]:
    """
    修复单个文件中的链接
    
    Returns:
        (file:// 链接修复数, scripts 路径修复数)
    """
    try:
        content = md_file.read_text(encoding='utf-8')
        original_content = content
        
        file_url_fixes = 0
        script_path_fixes = 0
        
        # 1. 修复 file:// 协议链接
        def replace_file_url(match):
            nonlocal file_url_fixes
            link_text = match.group(1)
            file_url = match.group(2)
            
            if file_url.startswith('file:///'):
                rel_path = convert_file_url_to_relative(md_file, file_url)
                if rel_path != file_url:
                    file_url_fixes += 1
                    return f'[{link_text}]({rel_path})'
            
            return match.group(0)
        
        content = re.sub(r'\[([^\]]+)\]\((file:///[^)]+)\)', replace_file_url, content)
        
        # 2. 修复 scripts 路径
        content, script_path_fixes = update_script_paths(content)
        
        # 写入文件
        if content != original_content and not dry_run:
            md_file.write_text(content, encoding='utf-8')
        
        return file_url_fixes, script_path_fixes
    
    except Exception as e:
        print(f"  ❌ 处理文件出错: {e}")
        return 0, 0

def main():
    docs_dir = Path("docs")
    
    print("🔧 开始修复文档链接...\n")
    
    total_files = 0
    total_file_url_fixes = 0
    total_script_fixes = 0
    
    for md_file in sorted(docs_dir.rglob("*.md")):
        file_url_fixes, script_fixes = fix_file_links(md_file, dry_run=False)
        
        if file_url_fixes > 0 or script_fixes > 0:
            total_files += 1
            total_file_url_fixes += file_url_fixes
            total_script_fixes += script_fixes
            
            print(f"✅ {md_file}")
            if file_url_fixes > 0:
                print(f"   - 修复 {file_url_fixes} 个 file:// 链接")
            if script_fixes > 0:
                print(f"   - 修复 {script_fixes} 个 scripts 路径")
    
    print(f"\n📊 修复完成:")
    print(f"   修改文件数: {total_files}")
    print(f"   file:// 链接修复: {total_file_url_fixes}")
    print(f"   scripts 路径修复: {total_script_fixes}")
    print(f"   总修复数: {total_file_url_fixes + total_script_fixes}")

if __name__ == "__main__":
    main()
