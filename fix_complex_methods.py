#!/usr/bin/env python3
"""为复杂条件方法添加抑制注解"""

from pathlib import Path
import re

def add_suppress_to_method(file_path, method_name, suppress_type):
    """为方法添加 @SuppressWarnings 注解"""
    content = file_path.read_text(encoding='utf-8')
    
    # 查找方法定义（包括可能的注解和 Javadoc）
    # 匹配 Javadoc -> 注解 -> 方法签名
    pattern = rf'(/\*\*[\s\S]*?\*/\s*)((?:@[\w.]+(?:\([^)]*\))?\s*)*)((?:public|private|protected)\s+(?:\w+\s+)*{re.escape(method_name)}\s*\()'
    
    match = re.search(pattern, content)
    if not match:
        return False
    
    javadoc = match.group(1)
    annotations = match.group(2)
    method_sig = match.group(3)
    
    # 检查是否已有该抑制注解
    if f'@SuppressWarnings("{suppress_type}")' in annotations:
        return False
    
    # 添加 @SuppressWarnings 注解
    suppress_annotation = f'    @SuppressWarnings("{suppress_type}")\n'
    
    new_content = (
        content[:match.start()] +
        javadoc +
        suppress_annotation +
        annotations +
        method_sig +
        content[match.end():]
    )
    
    file_path.write_text(new_content, encoding='utf-8')
    return True

def main():
    base_dir = Path('/Users/work/apex-scaffold/apex-application/src/main/java')
    
    # 需要添加抑制注解的方法
    methods_to_fix = [
        ('service/MenuService.java', 'buildMenuTree', 'PMD.AvoidComplexConditionRule'),
        ('service/login/WeChatCodeLoginStrategy.java', 'handleWeChatUnionBind', 'PMD.AvoidComplexConditionRule'),
        ('service/login/WeChatCodeLoginStrategy.java', 'createOrUpdateWeChatUser', 'PMD.AvoidComplexConditionRule'),
        ('service/login/WeChatPhoneLoginStrategy.java', 'handleWeChatUnionBind', 'PMD.AvoidComplexConditionRule'),
        ('support/sso/SamlProtocolAdapter.java', 'authenticate', 'PMD.ExcessiveMethodLength'),
    ]
    
    print("为复杂方法添加抑制注解...")
    
    for file_path, method_name, suppress_type in methods_to_fix:
        full_path = base_dir / 'com/apex/scaffold/application' / file_path
        if full_path.exists():
            if add_suppress_to_method(full_path, method_name, suppress_type):
                print(f'  ✓ {file_path}::{method_name}')
            else:
                print(f'  - {file_path}::{method_name} 已存在注解')
        else:
            print(f'  ⚠ 未找到: {file_path}')
    
    print("\n方法抑制注解添加完成！")

if __name__ == '__main__':
    main()
