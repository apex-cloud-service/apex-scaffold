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

LINK_MAPPINGS = {
    "QUICK_START.md": "00-getting-started/quickstart.md",
    "CONFIGURATION.md": "05-operations/configuration.md",
    "ENVIRONMENT_VARIABLES.md": "05-operations/configuration.md",
    "ARCHITECTURE_INDEX.md": "02-architecture/architecture-index.md",
    "BEST_PRACTICES.md": "01-guides/best-practices.md",
    "CODEGEN.md": "04-development/codegen.md",
    "LOMBOK_GUIDE.md": "04-development/lombok-guide.md",
    "JAVADOC_GUIDE.md": "04-development/javadoc-guide.md",
    "SECURITY_PROTECTIONS.md": "05-operations/security-protections.md",
    "DATA_SCOPE_USAGE.md": "01-guides/data-scope-usage.md",
    "DATA_SCOPE.md": "01-guides/data-scope-reference.md",
    "DATA_SOURCE_SWITCH_USAGE.md": "01-guides/data-source-switch-usage.md",
    "AUDIT_LOG_USAGE.md": "01-guides/audit-log-usage.md",
    "TROUBLESHOOTING.md": "01-guides/troubleshooting.md",
    "MYBATIS_PLUS_GUIDE.md": "01-guides/best-practices.md",
    "FAQ.md": "01-guides/troubleshooting.md",
    "KEYCLOAK_INTEGRATION_GUIDE.md": "01-guides/keycloak-integration-guide.md",
    "operations/deployment.md": "05-operations/deployment.md",
    "architecture/advanced-features/webflux-best-practices.md": "architecture/reactive-programming-guide.md",
    "diagrams/README.md": "../diagrams/README.md",
    "integration/README.md": "../_archive/integration/README.md",
    "./QUICK_START.md": "../00-getting-started/quickstart.md",
    "./CONFIGURATION.md": "../05-operations/configuration.md",
    "./ENVIRONMENT_VARIABLES.md": "../05-operations/configuration.md",
    "./ARCHITECTURE_INDEX.md": "../02-architecture/architecture-index.md",
    "./CODEGEN.md": "../04-development/codegen.md",
    "./BEST_PRACTICES.md": "../01-guides/best-practices.md",
    "./LOMBOK_GUIDE.md": "../04-development/lombok-guide.md",
    "./JAVADOC_GUIDE.md": "../04-development/javadoc-guide.md",
    "./SECURITY_PROTECTIONS.md": "../05-operations/security-protections.md",
    "./DATA_SCOPE_USAGE.md": "../01-guides/data-scope-usage.md",
    "./DATA_SOURCE_SWITCH_USAGE.md": "../01-guides/data-source-switch-usage.md",
    "./AUDIT_LOG_USAGE.md": "../01-guides/audit-log-usage.md",
    "./TROUBLESHOOTING.md": "../01-guides/troubleshooting.md",
    "./operations/deployment.md": "../05-operations/deployment.md"
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

def check_link_exists(link_path, current_file_dir, base_dir):
    if link_path.startswith("http://") or link_path.startswith("https://"):
        return True, link_path
    
    if "#" in link_path:
        link_path = link_path.split("#")[0]
    
    if not link_path or link_path.startswith("#"):
        return True, link_path
    
    if link_path.startswith("/"):
        full_path = Path(base_dir) / link_path.lstrip("/")
    else:
        full_path = (current_file_dir / link_path).resolve()
    
    return full_path.exists(), str(full_path)

def fix_link(link_text, link_path, current_file_dir, base_dir):
    basename = os.path.basename(link_path.split("#")[0])
    anchor = ""
    if "#" in link_path:
        anchor = "#" + link_path.split("#")[1]
    
    if link_path in LINK_MAPPINGS:
        new_path = LINK_MAPPINGS[link_path]
        return f"[{link_text}]({new_path}{anchor})", new_path
    
    if basename in LINK_MAPPINGS:
        new_relative = LINK_MAPPINGS[basename]
        target_path = Path(base_dir) / new_relative
        
        try:
            rel_path = os.path.relpath(target_path, current_file_dir)
            if not rel_path.startswith(".."):
                rel_path = "./" + rel_path
            return f"[{link_text}]({rel_path}{anchor})", rel_path
        except ValueError:
            return None, None
    
    return None, None

def process_file(file_path, base_dir):
    with open(file_path, 'r', encoding='utf-8') as f:
        content = f.read()
    
    original_content = content
    current_file_dir = file_path.parent
    
    links = extract_links(content)
    broken_links = []
    fixed_links = []
    
    for link_text, link_path in links:
        exists, full_path = check_link_exists(link_path, current_file_dir, base_dir)
        
        if not exists:
            fixed_link, new_path = fix_link(link_text, link_path, current_file_dir, base_dir)
            if fixed_link:
                content = content.replace(f"[{link_text}]({link_path})", fixed_link, 1)
                fixed_links.append((link_path, new_path))
            else:
                broken_links.append(link_path)
    
    if content != original_content:
        with open(file_path, 'w', encoding='utf-8') as f:
            f.write(content)
        return True, fixed_links, broken_links
    
    return False, [], broken_links

def main():
    base_dir = Path(__file__).parent
    os.chdir(base_dir)
    
    md_files = find_all_markdown_files(base_dir, TARGET_DIRS)
    
    total_files = len(md_files)
    modified_files = 0
    total_fixed = 0
    all_broken = defaultdict(list)
    
    print(f"扫描到 {total_files} 个 Markdown 文件")
    print("=" * 80)
    
    for md_file in md_files:
        modified, fixed_links, broken_links = process_file(md_file, base_dir)
        
        if modified:
            modified_files += 1
            total_fixed += len(fixed_links)
            rel_path = md_file.relative_to(base_dir)
            print(f"\n✅ 修复文件: {rel_path}")
            for old_path, new_path in fixed_links:
                print(f"   {old_path} → {new_path}")
        
        if broken_links:
            rel_path = md_file.relative_to(base_dir)
            all_broken[str(rel_path)].extend(broken_links)
    
    print("\n" + "=" * 80)
    print(f"\n总结:")
    print(f"  - 扫描文件: {total_files}")
    print(f"  - 修改文件: {modified_files}")
    print(f"  - 修复链接: {total_fixed}")
    
    if all_broken:
        print(f"\n⚠️  仍有 {sum(len(v) for v in all_broken.values())} 个链接未能自动修复:")
        for file_path, links in all_broken.items():
            print(f"\n  {file_path}:")
            for link in links:
                print(f"    - {link}")
    else:
        print("\n✅ 所有链接已成功修复!")

if __name__ == "__main__":
    main()
