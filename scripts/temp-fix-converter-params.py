#!/usr/bin/env python3
"""修复Converter JavaDoc中缺少的参数注释"""
import re
from pathlib import Path

PROJECT_ROOT = Path("/Users/work/apex-scaffold")
CONVERTER_DIR = PROJECT_ROOT / "apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/persistence/converter"

FILES_TO_FIX = [
    "ApprovalRecordPoConverter.java",
    "ChartPoConverter.java",
    "DocumentPoConverter.java",
    "OffDepartmentPoConverter.java",
    "OffEmployeePoConverter.java",
    "OffMessagePoConverter.java",
    "OffMessageRecipientPoConverter.java",
    "OffMessageTemplatePoConverter.java",
    "OffSyncHistoryPoConverter.java",
    "OfficeApprovalProcessPoConverter.java",
    "OfficeCalendarEventPoConverter.java",
    "ProcessInstancePoConverter.java",
    "RolePoConverter.java",
    "SysJwtConfigPoConverter.java",
    "SysSecurityPolicyPoConverter.java",
    "SysSessionConfigPoConverter.java",
    "SysSsoAuditLogPoConverter.java",
    "TaskPoConverter.java",
    "WorkflowDefinitionPoConverter.java",
]

def fix_javadoc_params(content):
    """为JavaDoc添加@param注解"""
    # 匹配方法声明及其JavaDoc
    pattern = r'(/\*\*\s*\n\s*\* 对象转换方法\s*\n\s*\*\s*\n\s*\* @param source 源对象\s*\n\s*\* @return 目标对象\s*\n\s*\*/)\s*\n\s*(\w+.*?\s+\w+)\(([^)]+)\);'
    
    def replacer(match):
        javadoc = match.group(1)
        method_sig = match.group(2)
        params = match.group(3)
        
        # 提取参数名
        param_match = re.search(r'(\w+)\s+(\w+)$', params.strip())
        if param_match:
            param_type = param_match.group(1)
            param_name = param_match.group(2)
            
            # 根据参数名生成合适的描述
            if param_name in ['entity', 'entities']:
                param_desc = '领域实体' if param_name == 'entity' else '领域实体列表'
            elif param_name in ['po', 'pos']:
                param_desc = '持久化对象' if param_name == 'po' else '持久化对象列表'
            elif param_name == 'source':
                param_desc = '源对象'
            else:
                param_desc = '参数对象'
            
            # 替换JavaDoc中的@param
            new_javadoc = javadoc.replace(
                '@param source 源对象',
                f'@param {param_name} {param_desc}'
            )
            
            return f'{new_javadoc}\n    {method_sig}({params});'
        
        return match.group(0)
    
    return re.sub(pattern, replacer, content, flags=re.MULTILINE)

def process_file(filepath):
    print(f"处理: {filepath.name}")
    
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()
    
    original_content = content
    
    # 修复JavaDoc参数注释
    content = fix_javadoc_params(content)
    
    if content != original_content:
        with open(filepath, 'w', encoding='utf-8') as f:
            f.write(content)
        print(f"  ✅ 已修复")
        return True
    else:
        print(f"  ⏭️  无需修复")
        return False

def main():
    print("开始修复Converter JavaDoc参数注释...")
    fixed_count = 0
    
    for filename in FILES_TO_FIX:
        filepath = CONVERTER_DIR / filename
        if filepath.exists():
            if process_file(filepath):
                fixed_count += 1
        else:
            print(f"⚠️  文件不存在: {filename}")
    
    print(f"\n✅ 完成! 共修复 {fixed_count} 个文件")

if __name__ == "__main__":
    main()
