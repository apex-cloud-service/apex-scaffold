#!/usr/bin/env python3

import os
import re
from pathlib import Path

TARGET_DIRS = [
    "00-getting-started",
    "01-guides",
    "02-architecture",
    "03-api",
    "04-development",
    "05-operations"
]

def find_all_markdown_files(base_dir, target_dirs):
    md_files = []
    for target_dir in target_dirs:
        dir_path = Path(base_dir) / target_dir
        if dir_path.exists():
            md_files.extend(dir_path.rglob("*.md"))
    return md_files

def extract_links(content):
    pattern = r'\[([^\]]+)\]\(([^\)]+)\)'
    return re.findall(pattern, content)

def fix_relative_link(link_path, current_file_dir, base_dir):
    """修复相对路径链接"""
    if link_path.startswith("http://") or link_path.startswith("https://"):
        return None
    if link_path.startswith("file:///"):
        return None
    if link_path.startswith("/Users/"):
        return None
    if not link_path or link_path.startswith("#"):
        return None
    
    anchor = ""
    if "#" in link_path:
        anchor = "#" + link_path.split("#")[1]
        link_path_base = link_path.split("#")[0]
    else:
        link_path_base = link_path
    
    if not link_path_base:
        return None
    
    # 检查链接是否已经存在
    if link_path_base.startswith("/"):
        full_path = Path(base_dir) / link_path_base.lstrip("/")
    else:
        full_path = (current_file_dir / link_path_base).resolve()
    
    if full_path.exists():
        return None
    
    # 尝试修复路径
    basename = os.path.basename(link_path_base)
    
    # 在 docs 目录下搜索这个文件
    for target_dir in TARGET_DIRS:
        search_dir = base_dir / target_dir
        if search_dir.exists():
            for found_file in search_dir.rglob(basename):
                try:
                    rel_path = os.path.relpath(found_file, current_file_dir)
                    if not rel_path.startswith(".."):
                        rel_path = "./" + rel_path
                    return rel_path + anchor
                except ValueError:
                    continue
    
    return None

def process_file(file_path, base_dir):
    with open(file_path, 'r', encoding='utf-8') as f:
        content = f.read()
    
    original_content = content
    current_file_dir = file_path.parent
    
    links = extract_links(content)
    fixed_links = []
    
    for link_text, link_path in links:
        new_path = fix_relative_link(link_path, current_file_dir, base_dir)
        
        if new_path:
            old_link = f"[{link_text}]({link_path})"
            new_link = f"[{link_text}]({new_path})"
            content = content.replace(old_link, new_link, 1)
            fixed_links.append((link_path, new_path))
    
    if content != original_content:
        with open(file_path, 'w', encoding='utf-8') as f:
            f.write(content)
        return True, fixed_links
    
    return False, []

def main():
    base_dir = Path(__file__).parent
    os.chdir(base_dir)
    
    md_files = find_all_markdown_files(base_dir, TARGET_DIRS)
    
    total_files = len(md_files)
    modified_files = 0
    total_fixed = 0
    
    print(f"第三阶段: 扫描 {total_files} 个 Markdown 文件")
    print("=" * 80)
    
    for md_file in md_files:
        modified, fixed_links = process_file(md_file, base_dir)
        
        if modified:
            modified_files += 1
            total_fixed += len(fixed_links)
            rel_path = md_file.relative_to(base_dir)
            print(f"\n✅ 修复文件: {rel_path}")
            for old_path, new_path in fixed_links:
                print(f"   {old_path} → {new_path}")
    
    print("\n" + "=" * 80)
    print(f"\n第三阶段总结:")
    print(f"  - 扫描文件: {total_files}")
    print(f"  - 修改文件: {modified_files}")
    print(f"  - 修复链接: {total_fixed}")

if __name__ == "__main__":
    main()
