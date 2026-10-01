#!/usr/bin/env python3
import re
import os
from pathlib import Path

PROJECT_ROOT = Path("/Users/work/apex-scaffold")
CONVERTER_DIR = PROJECT_ROOT / "apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/persistence/converter"

FILES_TO_FIX = [
    "TaskPoConverter.java",
    "WorkflowDefinitionPoConverter.java",
    "ApprovalRecordPoConverter.java",
    "ProcessInstancePoConverter.java",
    "ChartPoConverter.java",
    "DocumentPoConverter.java",
    "RolePoConverter.java",
]

def add_javadoc_to_methods(content):
    """为没有JavaDoc的方法添加注释"""
    lines = content.split('\n')
    result = []
    i = 0
    
    while i < len(lines):
        line = lines[i]
        
        # 检测方法声明（没有JavaDoc的情况）
        if re.match(r'^\s+\w+.*\s+\w+\([^)]*\);?\s*$', line):
            # 检查前面是否已有JavaDoc
            has_javadoc = False
            for j in range(max(0, i-6), i):
                if j < len(lines) and '/**' in lines[j]:
                    has_javadoc = True
                    break
            
            if not has_javadoc:
                indent = re.match(r'^(\s+)', line).group(1) if re.match(r'^(\s+)', line) else '    '
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
    """为类JavaDoc添加@author"""
    # 匹配第一个JavaDoc注释块
    pattern = r'(/\*\*\n(?:(?!\*/).)*?)(\n \*/)'
    
    def replacer(match):
        javadoc = match.group(1)
        closing = match.group(2)
        
        if '@author' not in javadoc:
            # 在最后添加@author
            return javadoc + '\n *\n * @author Apex Team' + closing
        return match.group(0)
    
    return re.sub(pattern, replacer, content, count=1, flags=re.DOTALL)

def process_file(filepath):
    print(f"处理: {filepath.name}")
    
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()
    
    original_content = content
    
    # 检查是否是Converter接口
    if '@Mapper' in content and 'interface' in content:
        # 添加@author
        content = add_author_to_class(content)
        
        # 为方法添加JavaDoc
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
    print("开始批量修复剩余的Converter文件...")
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
