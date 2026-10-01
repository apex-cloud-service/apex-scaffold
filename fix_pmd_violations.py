#!/usr/bin/env python3
"""
批量修复 PMD 违规的脚本
"""

import re
import sys
from pathlib import Path

def add_author_tag(file_path):
    """添加 @author 标签到类级别的 Javadoc"""
    content = file_path.read_text(encoding='utf-8')
    
    # 查找类级别的 Javadoc（在 @Slf4j、@Service 等注解之前）
    pattern = r'(/\*\*[\s\S]*?\*/)\s*(@\w+[\s\S]*?)(public\s+(class|interface|enum)\s+\w+)'
    
    def replace_javadoc(match):
        javadoc = match.group(1)
        annotations = match.group(2)
        class_decl = match.group(3)
        
        # 如果已经有 @author，跳过
        if '@author' in javadoc:
            return match.group(0)
        
        # 在 javadoc 结束前添加 @author
        new_javadoc = javadoc.replace(' */', ' *\n * @author Apex Team\n */')
        return new_javadoc + '\n' + annotations + class_decl
    
    new_content = re.sub(pattern, replace_javadoc, content)
    
    if new_content != content:
        file_path.write_text(new_content, encoding='utf-8')
        return True
    return False

def fix_magic_numbers_in_file(file_path):
    """修复文件中的魔法数字"""
    content = file_path.read_text(encoding='utf-8')
    original = content
    
    # 替换常见的魔法数字
    replacements = [
        (r'new ArrayList<>\(16\)', 'new ArrayList<>(DEFAULT_CAPACITY)'),
        (r'new HashMap<>\(16\)', 'new HashMap<>(DEFAULT_CAPACITY)'),
        (r'new HashSet<>\(16\)', 'new HashSet<>(DEFAULT_CAPACITY)'),
    ]
    
    for pattern, replacement in replacements:
        content = re.sub(pattern, replacement, content)
    
    if content != original:
        file_path.write_text(content, encoding='utf-8')
        return True
    return False

def main():
    base_path = Path('/Users/work/apex-scaffold')
    
    # 修复 apex-application 模块
    app_path = base_path / 'apex-application' / 'src' / 'main' / 'java'
    
    files_fixed = 0
    for java_file in app_path.rglob('*.java'):
        if add_author_tag(java_file):
            print(f'Added @author to {java_file.name}')
            files_fixed += 1
    
    print(f'\n修复了 {files_fixed} 个文件')

if __name__ == '__main__':
    main()
