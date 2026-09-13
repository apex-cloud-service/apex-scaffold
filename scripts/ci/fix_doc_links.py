#!/usr/bin/env python3
"""
扫描并修复 docs 目录中所有 markdown 文件的内部链接
"""

import os
import re
from pathlib import Path
from typing import Dict, List, Tuple, Set
import json

DOCS_DIR = Path("/Users/work/apex-scaffold/docs")
ROOT_DIR = Path("/Users/work/apex-scaffold")

class LinkScanner:
    def __init__(self):
        self.all_files: Set[Path] = set()
        self.all_links: List[Tuple[Path, str, str]] = []
        self.broken_links: List[Tuple[Path, str, str, str]] = []
        self.fixed_links: Dict[str, str] = {}
        
    def scan_all_markdown_files(self):
        """扫描所有 markdown 文件"""
        print("📁 扫描所有 markdown 文件...")
        for md_file in DOCS_DIR.rglob("*.md"):
            if md_file.is_file():
                self.all_files.add(md_file)
        print(f"   找到 {len(self.all_files)} 个 markdown 文件")
    
    def extract_links(self):
        """提取所有内部链接"""
        print("\n🔗 提取所有内部链接...")
        link_pattern = re.compile(r'\[([^\]]+)\]\(([^)]+)\)')
        
        for md_file in self.all_files:
            try:
                content = md_file.read_text(encoding='utf-8')
                for match in link_pattern.finditer(content):
                    link_text = match.group(1)
                    link_path = match.group(2)
                    
                    if link_path.startswith(('http://', 'https://', 'mailto:', 'file://')):
                        continue
                    
                    if link_path.startswith('#'):
                        continue
                    
                    self.all_links.append((md_file, link_text, link_path))
            except Exception as e:
                print(f"   ⚠️  读取文件失败: {md_file} - {e}")
        
        print(f"   找到 {len(self.all_links)} 个内部链接")
    
    def verify_links(self):
        """验证链接是否有效"""
        print("\n✓ 验证链接有效性...")
        
        for source_file, link_text, link_path in self.all_links:
            link_without_anchor = link_path.split('#')[0]
            
            if not link_without_anchor:
                continue
            
            if link_without_anchor.startswith('/'):
                target_path = ROOT_DIR / link_without_anchor.lstrip('/')
            else:
                target_path = (source_file.parent / link_without_anchor).resolve()
            
            if not target_path.exists():
                self.broken_links.append((source_file, link_text, link_path, str(target_path)))
        
        print(f"   发现 {len(self.broken_links)} 个断裂的链接")
    
    def build_file_mapping(self) -> Dict[str, Path]:
        """构建文件名到路径的映射"""
        mapping = {}
        for md_file in self.all_files:
            filename = md_file.name
            if filename in mapping:
                print(f"   ⚠️  重复文件名: {filename}")
                print(f"      路径1: {mapping[filename]}")
                print(f"      路径2: {md_file}")
            else:
                mapping[filename] = md_file
        return mapping
    
    def suggest_fix(self, source_file: Path, broken_path: str) -> str:
        """为断裂的链接建议修复路径"""
        link_without_anchor = broken_path.split('#')[0]
        anchor = '#' + broken_path.split('#')[1] if '#' in broken_path else ''
        
        target_basename = Path(link_without_anchor).name
        
        file_mapping = self.build_file_mapping()
        
        if target_basename in file_mapping:
            target_file = file_mapping[target_basename]
            try:
                rel_path = os.path.relpath(target_file, source_file.parent)
                return rel_path + anchor
            except ValueError:
                return str(target_file.relative_to(DOCS_DIR)) + anchor
        
        old_to_new = {
            'QUICKSTART.md': '00-getting-started/quickstart.md',
            'QUICK_START.md': '00-getting-started/quickstart.md',
            'BEST_PRACTICES.md': '01-guides/best-practices.md',
            'DEVELOPER_HANDBOOK.md': '01-guides/developer-handbook.md',
            'TROUBLESHOOTING.md': '01-guides/troubleshooting.md',
            'ARCHITECTURE_INDEX.md': '02-architecture/architecture-index.md',
            'ARCHITECTURE_OVERVIEW.md': '02-architecture/architecture-overview.md',
            'architecture.md': '02-architecture/architecture-overview.md',
            'API_VERSION_MANAGEMENT.md': '03-api/api-version-management.md',
            'API_PATH_CORRECTIONS.md': '03-api/api-path-corrections.md',
            'CODEGEN.md': '04-development/codegen.md',
            'JAVADOC_GUIDE.md': '04-development/javadoc-guide.md',
            'LOMBOK_GUIDE.md': '04-development/lombok-guide.md',
            'DEPLOYMENT.md': '05-operations/deployment.md',
            'CONFIGURATION.md': '05-operations/configuration.md',
            'SECURITY_PROTECTIONS.md': '05-operations/security-protections.md',
            'LICENSE_MANAGEMENT.md': '07-commercial/license-management.md',
        }
        
        for old, new in old_to_new.items():
            if old in broken_path or target_basename == old:
                new_path = broken_path.replace(old, new)
                return new_path
        
        return None
    
    def generate_report(self):
        """生成修复报告"""
        print("\n📊 生成链接修复报告...")
        
        report_file = DOCS_DIR / "LINK_FIX_REPORT.md"
        
        with open(report_file, 'w', encoding='utf-8') as f:
            f.write("# 文档链接修复报告\n\n")
            f.write(f"**生成时间**: 2026-09-12\n")
            f.write(f"**扫描范围**: {DOCS_DIR}\n\n")
            f.write("---\n\n")
            
            f.write("## 📊 统计摘要\n\n")
            f.write(f"- **扫描文件总数**: {len(self.all_files)}\n")
            f.write(f"- **内部链接总数**: {len(self.all_links)}\n")
            f.write(f"- **断裂链接数量**: {len(self.broken_links)}\n")
            f.write(f"- **有效链接数量**: {len(self.all_links) - len(self.broken_links)}\n")
            f.write(f"- **链接有效率**: {(len(self.all_links) - len(self.broken_links)) / len(self.all_links) * 100:.2f}%\n\n")
            
            f.write("---\n\n")
            
            if self.broken_links:
                f.write("## ⚠️ 断裂的链接\n\n")
                f.write("以下链接需要修复：\n\n")
                
                broken_by_file = {}
                for source_file, link_text, link_path, target in self.broken_links:
                    rel_source = source_file.relative_to(DOCS_DIR)
                    if rel_source not in broken_by_file:
                        broken_by_file[rel_source] = []
                    broken_by_file[rel_source].append((link_text, link_path, target))
                
                for source_file, links in sorted(broken_by_file.items()):
                    f.write(f"### 📄 {source_file}\n\n")
                    f.write("| 链接文本 | 原路径 | 建议修复 |\n")
                    f.write("|----------|---------|----------|\n")
                    
                    for link_text, link_path, target in links:
                        suggested = self.suggest_fix(DOCS_DIR / source_file, link_path)
                        if suggested:
                            f.write(f"| {link_text} | `{link_path}` | `{suggested}` |\n")
                        else:
                            f.write(f"| {link_text} | `{link_path}` | ❌ 未找到目标 |\n")
                    
                    f.write("\n")
            else:
                f.write("## ✅ 所有链接都是有效的\n\n")
                f.write("恭喜！所有内部链接都指向有效的文档。\n\n")
            
            f.write("---\n\n")
            f.write("## 📋 常见链接模式\n\n")
            
            link_patterns = {}
            for source_file, link_text, link_path in self.all_links:
                pattern = None
                if link_path.startswith('../'):
                    pattern = '相对路径（上级目录）'
                elif link_path.startswith('./'):
                    pattern = '相对路径（当前目录）'
                elif '/' in link_path:
                    pattern = '相对路径（子目录）'
                else:
                    pattern = '同级文件'
                
                if pattern not in link_patterns:
                    link_patterns[pattern] = 0
                link_patterns[pattern] += 1
            
            f.write("| 链接模式 | 数量 | 占比 |\n")
            f.write("|----------|------|------|\n")
            for pattern, count in sorted(link_patterns.items(), key=lambda x: -x[1]):
                ratio = count / len(self.all_links) * 100
                f.write(f"| {pattern} | {count} | {ratio:.1f}% |\n")
            
            f.write("\n---\n\n")
            f.write("**报告结束**\n")
        
        print(f"   报告已生成: {report_file}")
        return report_file


def main():
    print("🚀 开始扫描和修复文档链接\n")
    print("=" * 60)
    
    scanner = LinkScanner()
    scanner.scan_all_markdown_files()
    scanner.extract_links()
    scanner.verify_links()
    report_file = scanner.generate_report()
    
    print("\n" + "=" * 60)
    print("✅ 扫描完成！")
    print(f"\n📄 详细报告: {report_file}")
    
    if scanner.broken_links:
        print(f"\n⚠️  发现 {len(scanner.broken_links)} 个断裂的链接")
        print("   请查看报告以获取修复建议")
        return 1
    else:
        print("\n🎉 所有链接都是有效的！")
        return 0


if __name__ == "__main__":
    exit(main())
