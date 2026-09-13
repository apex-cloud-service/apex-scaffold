#!/usr/bin/env python3
"""
自动修复 docs 目录中所有 markdown 文件的断裂链接
"""

import os
import re
from pathlib import Path
from typing import Dict, List, Tuple
import shutil

DOCS_DIR = Path("/Users/work/apex-scaffold/docs")
ROOT_DIR = Path("/Users/work/apex-scaffold")

OLD_TO_NEW_MAPPING = {
    'QUICKSTART.md': '00-getting-started/quickstart.md',
    'QUICK_START.md': '00-getting-started/quickstart.md',
    'EXAMPLE_GUIDE.md': '00-getting-started/example-guide.md',
    'EXAMPLES_SUMMARY.md': '00-getting-started/examples-summary.md',
    'ONBOARDING_ARCHITECTURE_GUIDE.md': '00-getting-started/onboarding-guide.md',
    
    'BEST_PRACTICES.md': '01-guides/best-practices.md',
    'DEVELOPER_HANDBOOK.md': '01-guides/developer-handbook.md',
    'TROUBLESHOOTING.md': '01-guides/troubleshooting.md',
    'WORKFLOW_GUIDE.md': '01-guides/workflow-guide.md',
    'DATA_SCOPE_USAGE.md': '01-guides/data-scope-usage.md',
    'DATA_SCOPE_REFERENCE.md': '01-guides/data-scope-reference.md',
    'SSO_IMPLEMENTATION_GUIDE.md': '01-guides/sso-implementation-guide.md',
    'SSO_OAUTH2_KEYCLOAK_COMPLETE.md': '01-guides/sso-oauth2-keycloak-complete.md',
    'OAUTH2_INTEGRATION_GUIDE.md': '01-guides/oauth2-integration-guide.md',
    'KEYCLOAK_INTEGRATION_GUIDE.md': '01-guides/keycloak-integration-guide.md',
    'DOC_SEARCH_GUIDE.md': '01-guides/doc-search-guide.md',
    'DISTRIBUTED_BULKHEAD_GUIDE.md': '01-guides/distributed-bulkhead-guide.md',
    'READ_DEGRADATION_GUIDE.md': '01-guides/read-degradation-guide.md',
    'SPI_PLUGIN_GUIDE.md': '01-guides/spi-plugin-guide.md',
    
    'ARCHITECTURE_INDEX.md': '02-architecture/architecture-index.md',
    'ARCHITECTURE_OVERVIEW.md': '02-architecture/architecture-overview.md',
    'ARCHITECTURE_EVOLUTION.md': '02-architecture/architecture-evolution.md',
    'architecture.md': '02-architecture/architecture-overview.md',
    
    'API_VERSION_MANAGEMENT.md': '03-api/api-version-management.md',
    'API_PATH_CORRECTIONS.md': '03-api/api-path-corrections.md',
    'API_ROUTES.md': '03-api/api-routes.md',
    'SpringDoc注解模板.md': '03-api/springdoc-annotation-templates.md',
    'SpringDoc注解审计.md': '03-api/springdoc-annotation-audit.md',
    
    'CODEGEN.md': '04-development/codegen.md',
    'JAVADOC_GUIDE.md': '04-development/javadoc-guide.md',
    'LOMBOK_GUIDE.md': '04-development/lombok-guide.md',
    'JMH_BENCHMARK_GUIDE.md': '04-development/jmh-benchmark-guide.md',
    'MVND_BUILD_STANDARD.md': '04-development/mvnd-build-standard.md',
    'SVG_MAINTENANCE_GUIDE.md': '04-development/svg-maintenance-guide.md',
    'MERMAID_VIEWER_GUIDE.md': '04-development/mermaid-viewer-guide.md',
    
    'DEPLOYMENT.md': '05-operations/deployment.md',
    'CONFIGURATION.md': '05-operations/configuration.md',
    'SECURITY_PROTECTIONS.md': '05-operations/security-protections.md',
    'ADVANCED_FEATURES.md': '05-operations/advanced-features.md',
    'monitoring.md': '_archive/operations/monitoring.md',
    'monitoring-dashboard-configuration.md': '_archive/operations/monitoring-dashboard-configuration.md',
    
    'TEST_EXECUTION_GUIDE.md': '06-testing/TEST_EXECUTION_GUIDE.md',
    'TEST_REPORT.md': '06-testing/TEST_REPORT.md',
    'integration-test-guide.md': '06-testing/integration-test-guide.md',
    'api-test-report.md': '06-testing/api-test-report.md',
    'api-test-plan.md': '06-testing/api-test-plan.md',
    
    'LICENSE_MANAGEMENT.md': '07-commercial/license-management.md',
    'COMMERCIAL_VERSIONS.md': '07-commercial/COMMERCIAL_VERSIONS.md',
    'RELEASE_v1.0.0.md': '07-commercial/RELEASE_v1.0.0.md',
    
    'P0_SYSTEM_MANAGEMENT.md': '08-project-management/p0-system-management.md',
    'SCAFFOLD_REVIEW_GUIDE.md': '08-project-management/scaffold-review-guide.md',
    
    'api/route-mapping.md': '_archive/api/route-mapping.md',
    'api/data-management-api.md': '_archive/api/data-management-api.md',
    'standards/backend-development-standards.md': '04-development/backend-development-standards.md',
    'standards/frontend-development-standards.md': '04-development/frontend-development-standards.md',
    'operations/deployment.md': '05-operations/deployment.md',
    'operations/configuration.md': '05-operations/configuration.md',
    'operations/monitoring.md': '_archive/operations/monitoring.md',
    'operations/monitoring-dashboard-configuration.md': '_archive/operations/monitoring-dashboard-configuration.md',
    'operations/deployment-checklist.md': '_archive/operations/deployment-checklist.md',
}

class LinkFixer:
    def __init__(self):
        self.files_modified = 0
        self.links_fixed = 0
        self.files_with_changes = []
        
    def calculate_relative_path(self, from_file: Path, to_file: Path) -> str:
        try:
            rel_path = os.path.relpath(to_file, from_file.parent)
            return rel_path
        except ValueError:
            return str(to_file.relative_to(DOCS_DIR))
    
    def find_target_file(self, broken_path: str) -> Path:
        path_without_anchor = broken_path.split('#')[0]
        basename = Path(path_without_anchor).name
        
        if basename in OLD_TO_NEW_MAPPING:
            return DOCS_DIR / OLD_TO_NEW_MAPPING[basename]
        
        if path_without_anchor in OLD_TO_NEW_MAPPING:
            return DOCS_DIR / OLD_TO_NEW_MAPPING[path_without_anchor]
        
        for md_file in DOCS_DIR.rglob("*.md"):
            if md_file.name == basename and md_file.exists():
                if '_archive' not in str(md_file) and 'Kubernetes-Architecture-Icons' not in str(md_file):
                    return md_file
        
        for md_file in DOCS_DIR.rglob("*.md"):
            if md_file.name == basename and md_file.exists():
                return md_file
        
        return None
    
    def fix_diagrams_path(self, source_file: Path, link_path: str) -> str:
        if not link_path.startswith('diagrams/'):
            return link_path
        
        anchor = ''
        if '#' in link_path:
            link_path, anchor = link_path.split('#', 1)
            anchor = '#' + anchor
        
        target_file = DOCS_DIR / link_path
        if target_file.exists():
            rel_path = self.calculate_relative_path(source_file, target_file)
            return rel_path + anchor
        
        return link_path + anchor
    
    def fix_link(self, source_file: Path, link_path: str) -> str:
        if link_path.startswith(('http://', 'https://', 'mailto:', 'file://')):
            return link_path
        
        if link_path.startswith('#'):
            return link_path
        
        anchor = ''
        if '#' in link_path:
            link_path_clean, anchor = link_path.split('#', 1)
            anchor = '#' + anchor
        else:
            link_path_clean = link_path
        
        if link_path_clean.startswith('diagrams/'):
            return self.fix_diagrams_path(source_file, link_path)
        
        if link_path_clean.startswith('/'):
            target_path = ROOT_DIR / link_path_clean.lstrip('/')
        else:
            target_path = (source_file.parent / link_path_clean).resolve()
        
        if target_path.exists():
            return link_path
        
        target_file = self.find_target_file(link_path_clean)
        if target_file and target_file.exists():
            rel_path = self.calculate_relative_path(source_file, target_file)
            return rel_path + anchor
        
        for old, new in OLD_TO_NEW_MAPPING.items():
            if old in link_path_clean:
                new_link = link_path_clean.replace(old, new)
                new_target = DOCS_DIR / new_link
                if new_target.exists():
                    rel_path = self.calculate_relative_path(source_file, new_target)
                    return rel_path + anchor
        
        return link_path
    
    def fix_file(self, md_file: Path) -> bool:
        try:
            content = md_file.read_text(encoding='utf-8')
            original_content = content
            
            link_pattern = re.compile(r'\[([^\]]+)\]\(([^)]+)\)')
            
            def replace_link(match):
                link_text = match.group(1)
                link_path = match.group(2)
                
                fixed_path = self.fix_link(md_file, link_path)
                
                if fixed_path != link_path:
                    self.links_fixed += 1
                    return f'[{link_text}]({fixed_path})'
                return match.group(0)
            
            content = link_pattern.sub(replace_link, content)
            
            if content != original_content:
                backup_file = md_file.with_suffix(md_file.suffix + '.backup')
                shutil.copy2(md_file, backup_file)
                
                md_file.write_text(content, encoding='utf-8')
                self.files_modified += 1
                self.files_with_changes.append(md_file.relative_to(DOCS_DIR))
                return True
            
            return False
        except Exception as e:
            print(f"   ⚠️  修复文件失败: {md_file} - {e}")
            return False
    
    def fix_all_files(self):
        print("🔧 开始修复所有文档链接...")
        
        all_files = sorted(DOCS_DIR.rglob("*.md"))
        total_files = len(all_files)
        
        for i, md_file in enumerate(all_files, 1):
            if md_file.name == 'LINK_FIX_REPORT.md':
                continue
            
            if i % 50 == 0:
                print(f"   处理进度: {i}/{total_files} ({i/total_files*100:.1f}%)")
            
            self.fix_file(md_file)
        
        print(f"\n✅ 修复完成！")
        print(f"   修改的文件数: {self.files_modified}")
        print(f"   修复的链接数: {self.links_fixed}")
    
    def generate_report(self):
        report_file = DOCS_DIR / "LINK_FIX_EXECUTION_REPORT.md"
        
        with open(report_file, 'w', encoding='utf-8') as f:
            f.write("# 文档链接修复执行报告\n\n")
            f.write(f"**执行时间**: 2026-09-12\n")
            f.write(f"**修复范围**: {DOCS_DIR}\n\n")
            f.write("---\n\n")
            
            f.write("## 📊 修复统计\n\n")
            f.write(f"- **修改的文件数**: {self.files_modified}\n")
            f.write(f"- **修复的链接数**: {self.links_fixed}\n")
            f.write(f"- **平均每文件修复**: {self.links_fixed / max(1, self.files_modified):.1f} 个链接\n\n")
            
            f.write("---\n\n")
            
            f.write("## 📝 修改的文件列表\n\n")
            if self.files_with_changes:
                for file_path in sorted(self.files_with_changes):
                    f.write(f"- {file_path}\n")
            else:
                f.write("无文件被修改\n")
            
            f.write("\n---\n\n")
            f.write("## 🔍 修复策略\n\n")
            f.write("1. **diagrams 路径修复**: 将 `diagrams/` 开头的路径转换为相对路径\n")
            f.write("2. **文档重组路径**: 使用映射表将旧路径转换为新的分类路径\n")
            f.write("3. **智能查找**: 对于无法直接映射的文件，搜索整个 docs 目录\n")
            f.write("4. **备份保护**: 所有修改的文件都创建了 `.backup` 备份\n\n")
            
            f.write("---\n\n")
            f.write("## ⚠️ 注意事项\n\n")
            f.write("- 原始文件已备份为 `.md.backup`\n")
            f.write("- 建议运行扫描脚本验证修复效果\n")
            f.write("- 如需回滚，可使用备份文件恢复\n\n")
            
            f.write("---\n\n")
            f.write("**报告结束**\n")
        
        print(f"\n📄 执行报告: {report_file}")
        return report_file

def main():
    print("🚀 开始自动修复文档链接\n")
    print("=" * 60)
    
    fixer = LinkFixer()
    fixer.fix_all_files()
    report_file = fixer.generate_report()
    
    print("\n" + "=" * 60)
    print("✅ 修复完成！")
    print(f"\n📄 详细报告: {report_file}")
    
    return 0

if __name__ == "__main__":
    exit(main())
