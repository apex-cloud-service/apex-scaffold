#!/usr/bin/env python3

import os
import re
from pathlib import Path
from collections import defaultdict

TARGET_DIRS = [
    "00-getting-started",
    "01-guides",
    "02-architecture",
    "03-api",
    "04-development",
    "05-operations"
]

ADDITIONAL_MAPPINGS = {
    "WORKFLOW_GUIDE.md": "workflow-guide.md",
    "WORKFLOW_MONITOR_GUIDE.md": "workflow-monitor-guide.md",
    "WORKFLOW_DESIGNER_GUIDE.md": "workflow-designer-guide.md",
    "./WORKFLOW_GUIDE.md": "./workflow-guide.md",
    "./WORKFLOW_MONITOR_GUIDE.md": "./workflow-monitor-guide.md",
    "./WORKFLOW_DESIGNER_GUIDE.md": "./workflow-designer-guide.md",
    "MESSAGE_MIDDLEWARE.md": "message-middleware.md",
    "./MESSAGE_MIDDLEWARE.md": "./message-middleware.md",
    "OAUTH2_INTEGRATION_GUIDE.md": "01-guides/oauth2-integration-guide.md",
    "SSO_IMPLEMENTATION_GUIDE.md": "01-guides/sso-implementation-guide.md",
    "operations/configuration.md": "05-operations/configuration.md",
    "operations/deployment-checklist.md": "05-operations/deployment.md",
    "README.md": "../README.md",
    "SpringDoc注解模板.md": "springdoc-annotation-templates.md",
    "./SpringDoc注解模板.md": "./springdoc-annotation-templates.md",
    "DOC_SEARCH_GUIDE.md": "doc-search-guide.md",
    "DOC_SEARCH_FIX.md": "doc-search-fix.md",
    "DOC_SEARCH_CHANGELOG.md": "doc-search-changelog.md",
    "ARCHITECTURE_USAGE_GUIDE.md": "../02-architecture/architecture-usage-guide.md",
    "LICENSE_MANAGEMENT.md": "../07-commercial/license-management.md"
}

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

def process_file(file_path, base_dir):
    with open(file_path, 'r', encoding='utf-8') as f:
        content = f.read()
    
    original_content = content
    current_file_dir = file_path.parent
    
    links = extract_links(content)
    fixed_links = []
    
    for link_text, link_path in links:
        if link_path.startswith("http://") or link_path.startswith("https://"):
            continue
        
        if link_path.startswith("/Users/work/"):
            continue
        
        if link_path.startswith("file:///"):
            continue
        
        anchor = ""
        if "#" in link_path:
            anchor = "#" + link_path.split("#")[1]
            link_path_base = link_path.split("#")[0]
        else:
            link_path_base = link_path
        
        if link_path_base in ADDITIONAL_MAPPINGS:
            new_path = ADDITIONAL_MAPPINGS[link_path_base]
            old_link = f"[{link_text}]({link_path})"
            new_link = f"[{link_text}]({new_path}{anchor})"
            content = content.replace(old_link, new_link, 1)
            fixed_links.append((link_path, new_path + anchor))
    
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
    
    print(f"第二阶段: 扫描 {total_files} 个 Markdown 文件")
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
    print(f"\n第二阶段总结:")
    print(f"  - 扫描文件: {total_files}")
    print(f"  - 修改文件: {modified_files}")
    print(f"  - 修复链接: {total_fixed}")

if __name__ == "__main__":
    main()
