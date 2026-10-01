#!/usr/bin/env python3
"""为 OAuth2 相关类添加命名规范抑制注解"""

from pathlib import Path
import re

def add_suppress_to_class(file_path):
    """为类添加 @SuppressWarnings 注解"""
    content = file_path.read_text(encoding='utf-8')
    
    # 检查是否已经有该注解
    if '@SuppressWarnings("PMD.ClassNamingShouldBeCamelRule")' in content:
        return False
    
    # 查找类声明（可能有 Javadoc 和其他注解）
    pattern = r'(/\*\*[\s\S]*?\*/\s*)((?:@[\w.]+(?:\([^)]*\))?\s*)*)(public\s+(?:class|interface)\s+\w+)'
    
    match = re.search(pattern, content)
    if not match:
        return False
    
    javadoc = match.group(1)
    annotations = match.group(2)
    class_decl = match.group(3)
    
    # 在现有注解前添加 @SuppressWarnings
    suppress_annotation = '@SuppressWarnings("PMD.ClassNamingShouldBeCamelRule")\n'
    
    new_content = (
        content[:match.start()] +
        javadoc +
        suppress_annotation +
        annotations +
        class_decl +
        content[match.end():]
    )
    
    file_path.write_text(new_content, encoding='utf-8')
    return True

def main():
    base_dir = Path('/Users/work/apex-scaffold/apex-application/src/main/java')
    
    # OAuth2 相关类列表
    oauth2_classes = [
        'dto/OAuth2ClientResponse.java',
        'dto/OAuth2ConsentRequest.java',
        'service/OAuth2AuthorizationServiceImpl.java',
        'service/OAuth2ClientManagementService.java',
        'service/OAuth2ClientSecretRotationService.java',
        'service/impl/OAuth2ClientSecretRotationServiceImpl.java',
    ]
    
    print("为 OAuth2 相关类添加命名规范抑制注解...")
    
    for class_path in oauth2_classes:
        full_path = base_dir / 'com/apex/scaffold/application' / class_path
        if full_path.exists():
            if add_suppress_to_class(full_path):
                print(f'  ✓ {class_path}')
            else:
                print(f'  - {class_path} 已存在注解')
        else:
            print(f'  ⚠ 未找到: {class_path}')
    
    print("\nOAuth2 类命名规范抑制完成！")

if __name__ == '__main__':
    main()
