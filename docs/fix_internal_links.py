#!/usr/bin/env python3

import os
import re
from pathlib import Path
from collections import defaultdict

docs_root = Path("/Users/work/apex-scaffold/docs")

broken_links = []
fixed_links = []
valid_links = []

md_files = list(docs_root.rglob("*.md"))
all_md_files_set = {f.relative_to(docs_root) for f in md_files}

svg_files = list((docs_root / "diagrams").rglob("*.svg"))
all_svg_files_set = {f.relative_to(docs_root) for f in svg_files}

link_pattern = re.compile(r'\[([^\]]+)\]\(([^)]+)\)')

def normalize_path(current_file, link_path):
    if link_path.startswith('http://') or link_path.startswith('https://') or link_path.startswith('file:///'):
        return None
    
    if '#' in link_path:
        link_path = link_path.split('#')[0]
    
    if not link_path or link_path.startswith('#'):
        return None
    
    current_dir = current_file.parent
    
    try:
        if link_path.startswith('/'):
            resolved = Path(link_path[1:])
        else:
            resolved = (current_dir / link_path).resolve().relative_to(docs_root.resolve())
        return resolved
    except (ValueError, OSError):
        return None

def find_correct_path(broken_path, current_file):
    filename = broken_path.name
    
    if broken_path.suffix == '.md':
        candidates = [f for f in all_md_files_set if f.name == filename]
    elif broken_path.suffix == '.svg':
        candidates = [f for f in all_svg_files_set if f.name == filename]
    else:
        return None
    
    if len(candidates) == 1:
        correct_path = candidates[0]
        current_dir = current_file.parent
        try:
            relative_path = os.path.relpath(docs_root / correct_path, docs_root / current_dir)
            return relative_path
        except ValueError:
            return None
    
    return None

def check_and_fix_links(md_file):
    relative_path = md_file.relative_to(docs_root)
    
    with open(md_file, 'r', encoding='utf-8') as f:
        content = f.read()
    
    original_content = content
    file_broken_links = []
    file_fixed_links = []
    file_valid_links = []
    
    for match in link_pattern.finditer(content):
        link_text = match.group(1)
        link_path = match.group(2)
        
        resolved = normalize_path(relative_path, link_path)
        
        if resolved is None:
            continue
        
        target_exists = False
        if resolved.suffix == '.md':
            target_exists = resolved in all_md_files_set
        elif resolved.suffix == '.svg':
            target_exists = resolved in all_svg_files_set
        else:
            target_exists = (docs_root / resolved).exists()
        
        if target_exists:
            file_valid_links.append({
                'file': str(relative_path),
                'link_text': link_text,
                'link_path': link_path,
                'resolved': str(resolved)
            })
        else:
            correct_path = find_correct_path(resolved, relative_path)
            
            if correct_path:
                old_link = f'[{link_text}]({link_path})'
                new_link = f'[{link_text}]({correct_path})'
                content = content.replace(old_link, new_link, 1)
                
                file_fixed_links.append({
                    'file': str(relative_path),
                    'link_text': link_text,
                    'old_path': link_path,
                    'new_path': correct_path,
                    'target': str(resolved.name)
                })
            else:
                file_broken_links.append({
                    'file': str(relative_path),
                    'link_text': link_text,
                    'link_path': link_path,
                    'resolved': str(resolved)
                })
    
    if content != original_content:
        with open(md_file, 'w', encoding='utf-8') as f:
            f.write(content)
    
    return file_broken_links, file_fixed_links, file_valid_links

print(f"扫描 {len(md_files)} 个 Markdown 文件...")
print(f"找到 {len(all_md_files_set)} 个 .md 文件")
print(f"找到 {len(all_svg_files_set)} 个 .svg 文件")
print()

for md_file in sorted(md_files):
    file_broken, file_fixed, file_valid = check_and_fix_links(md_file)
    broken_links.extend(file_broken)
    fixed_links.extend(file_fixed)
    valid_links.extend(file_valid)

print("=" * 80)
print("链接修复报告")
print("=" * 80)
print()

print(f"## 统计摘要")
print(f"- 扫描文件数: {len(md_files)}")
print(f"- 有效链接数: {len(valid_links)}")
print(f"- 已修复链接数: {len(fixed_links)}")
print(f"- 仍然失效链接数: {len(broken_links)}")
print()

if fixed_links:
    print("## 已修复的链接")
    print()
    by_file = defaultdict(list)
    for link in fixed_links:
        by_file[link['file']].append(link)
    
    for file_path in sorted(by_file.keys()):
        print(f"### {file_path}")
        for link in by_file[file_path]:
            print(f"- `{link['old_path']}` → `{link['new_path']}`")
        print()

if broken_links:
    print("## 仍然失效的链接")
    print()
    by_file = defaultdict(list)
    for link in broken_links:
        by_file[link['file']].append(link)
    
    for file_path in sorted(by_file.keys()):
        print(f"### {file_path}")
        for link in by_file[file_path]:
            print(f"- 链接文本: `{link['link_text']}`")
            print(f"  原始路径: `{link['link_path']}`")
            print(f"  解析路径: `{link['resolved']}`")
        print()

print("完成!")
