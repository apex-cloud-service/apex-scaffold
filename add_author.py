#!/usr/bin/env python3
"""批量为 Java 文件添加 @author 标签"""

import re
import sys
from pathlib import Path

def add_author_to_file(file_path):
    """为单个文件添加 @author 标签"""
    try:
        content = file_path.read_text(encoding='utf-8')
        
        # 如果已经有 @author，跳过
        if '@author' in content:
            return False
        
        # 查找类级别的 Javadoc（在 public class/interface/enum 之前的最后一个 /** ... */）
        # 匹配模式：/** ... */ 后面跟着可选的注解，然后是 public class/interface/enum
        pattern = r'(/\*\*.*?\*/)\s*\n(\s*@[\w.]+[^\n]*\n)*(\s*public\s+(class|interface|enum)\s+\w+)'
        
        def add_author(match):
            javadoc = match.group(1)
            annotations = match.group(2) if match.group(2) else ''
            class_decl = match.group(3)
            
            # 在 */ 之前添加 @author
            if javadoc.endswith('*/'):
                new_javadoc = javadoc[:-2] + ' *\n * @author Apex Team\n */'
            else:
                return match.group(0)
            
            return new_javadoc + '\n' + annotations + class_decl
        
        new_content = re.sub(pattern, add_author, content, flags=re.DOTALL)
        
        if new_content != content:
            file_path.write_text(new_content, encoding='utf-8')
            return True
        
        return False
        
    except Exception as e:
        print(f"Error processing {file_path}: {e}", file=sys.stderr)
        return False

def main():
    base_dir = Path('/Users/work/apex-scaffold/apex-application/src/main/java')
    
    if not base_dir.exists():
        print(f"Directory not found: {base_dir}")
        return 1
    
    count = 0
    for java_file in base_dir.rglob('*.java'):
        if add_author_to_file(java_file):
            print(f"✓ {java_file.relative_to(base_dir)}")
            count += 1
    
    print(f"\n总共修复了 {count} 个文件")
    return 0

if __name__ == '__main__':
    sys.exit(main())
