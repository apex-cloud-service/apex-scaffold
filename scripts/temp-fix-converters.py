#!/usr/bin/env python3
import re
import os
from pathlib import Path

PROJECT_ROOT = Path("/Users/work/apex-scaffold")
CONVERTER_DIR = PROJECT_ROOT / "apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/persistence/converter"

FILES_TO_FIX = [
    "OffMessagePoConverter.java",
    "OffMessageRecipientPoConverter.java",
    "OffMessageTemplatePoConverter.java",
    "OffSyncHistoryPoConverter.java",
    "OfficeApprovalProcessPoConverter.java",
    "OfficeCalendarEventPoConverter.java",
    "SysJwtConfigPoConverter.java",
    "SysSecurityPolicyPoConverter.java",
    "SysSessionConfigPoConverter.java",
    "SysSsoAuditLogPoConverter.java",
]

def add_javadoc_to_methods(content):
    lines = content.split('\n')
    result = []
    i = 0
    
    while i < len(lines):
        line = lines[i]
        
        if re.match(r'^\s+\w+.*\s+to\w+\([^)]+\);?\s*$', line) and not any(
            lines[j].strip().startswith('/**') for j in range(max(0, i-5), i)
        ):
            indent = re.match(r'^(\s+)', line).group(1)
            result.append(f'{indent}/**')
            result.append(f'{indent} * 对象转换方法')
            result.append(f'{indent} *')
            result.append(f'{indent} * @param source 源对象')
            result.append(f'{indent} * @return 目标对象')
            result.append(f'{indent} */')
        
        result.append(line)
        i += 1
    
    return '\n'.join(result)

def add_author_to_class(content):
    pattern = r'(/\*\*\n(?:[^\n]*\n)*?)(\s*\*/)'
    
    def replacer(match):
        javadoc = match.group(1)
        closing = match.group(2)
        
        if '@author' not in javadoc:
            return javadoc + ' *\n * @author Apex Team\n' + closing
        return match.group(0)
    
    return re.sub(pattern, replacer, content, count=1)

def process_file(filepath):
    print(f"处理: {filepath.name}")
    
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()
    
    original_content = content
    
    if '@Mapper' in content and 'public interface' in content:
        if '@author' not in content or '@author' not in content[:500]:
            content = add_author_to_class(content)
        
        content = add_javadoc_to_methods(content)
    
    if content != original_content:
        with open(filepath, 'w', encoding='utf-8') as f:
            f.write(content)
        print(f"  ✅ 已修复")
        return True
    else:
        print(f"  ⏭️  无需修复")
        return False

def main():
    print("开始批量修复Converter文件...")
    fixed_count = 0
    
    for filename in FILES_TO_FIX:
        filepath = CONVERTER_DIR / filename
        if filepath.exists():
            if process_file(filepath):
                fixed_count += 1
        else:
            print(f"文件不存在: {filename}")
    
    print(f"\n完成! 共修复 {fixed_count} 个文件")

if __name__ == "__main__":
    main()
