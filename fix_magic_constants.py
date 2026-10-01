#!/usr/bin/env python3
"""批量修复魔法常量违规"""

from pathlib import Path
import re

# 定义需要添加常量的文件和对应的魔法值
magic_constants = {
    'OfficeAdminService.java': [
        ('100', 'DEFAULT_PAGE_SIZE', 'int'),
        ('"PENDING"', 'STATUS_PENDING', 'String'),
        ('"REVOKED"', 'STATUS_REVOKED', 'String'),
    ],
    'OfficeDocumentService.java': [
        ('"title"', 'FIELD_TITLE', 'String'),
        ('"url"', 'FIELD_URL', 'String'),
        ('"lastModifiedTime"', 'FIELD_LAST_MODIFIED_TIME', 'String'),
    ],
    'OfficeMessageService.java': [
        ('"FAILED"', 'STATUS_FAILED', 'String'),
    ],
    'OfficeMessageApplicationService.java': [
        ('"SUCCESS"', 'STATUS_SUCCESS', 'String'),
    ],
    'CaptchaService.java': [
        ('4', 'CAPTCHA_LENGTH', 'int'),
        ('40', 'CAPTCHA_WIDTH', 'int'),
    ],
    'AdminUserManagementService.java': [
        ('100', 'DEFAULT_PAGE_SIZE', 'int'),
        ('5', 'MAX_RETRY_COUNT', 'int'),
    ],
    'DataSourceManagementService.java': [
        ('"/"', 'PATH_SEPARATOR', 'String'),
    ],
    'DatabaseConfigService.java': [
        ('"Access denied"', 'ERROR_ACCESS_DENIED', 'String'),
        ('"Unknown database"', 'ERROR_UNKNOWN_DATABASE', 'String'),
        ('"Connection refused"', 'ERROR_CONNECTION_REFUSED', 'String'),
        ('"connect timed out"', 'ERROR_CONNECT_TIMEOUT', 'String'),
        ('"timeout"', 'ERROR_TIMEOUT', 'String'),
    ],
    'GenConfigService.java': [
        ('".."', 'PARENT_DIR', 'String'),
    ],
    'UserService.java': [
        ('100', 'DEFAULT_PAGE_SIZE', 'int'),
    ],
    'WorkflowService.java': [
        ('"PENDING"', 'STATUS_PENDING', 'String'),
        ('"COMPLETED"', 'STATUS_COMPLETED', 'String'),
        ('"REJECTED"', 'STATUS_REJECTED', 'String'),
        ('"SUSPENDED"', 'STATUS_SUSPENDED', 'String'),
    ],
    'WeChatCodeLoginStrategy.java': [
        ('2', 'WECHAT_OPENID_TYPE', 'int'),
    ],
    'CodeTemplateEngine.java': [
        ('"."', 'DOT_SEPARATOR', 'String'),
    ],
    'DataSourcePoolManager.java': [
        ('"-"', 'HYPHEN_SEPARATOR', 'String'),
    ],
    'MetadataReader.java': [
        ('"postgresql"', 'DB_TYPE_POSTGRESQL', 'String'),
    ],
    'OidcProtocolAdapter.java': [
        ('2', 'TOKEN_PARTS_COUNT', 'int'),
        ('"email"', 'CLAIM_EMAIL', 'String'),
        ('"name"', 'CLAIM_NAME', 'String'),
        ('"attributes"', 'CLAIM_ATTRIBUTES', 'String'),
    ],
    'SamlProtocolAdapter.java': [
        ('"Response"', 'SAML_RESPONSE_ELEMENT', 'String'),
        ('"SHA1withRSA"', 'SIGNATURE_ALGORITHM', 'String'),
    ],
}

def add_constant_to_class(file_path, magic_value, const_name, const_type):
    """在类中添加常量定义"""
    content = file_path.read_text(encoding='utf-8')
    
    # 检查是否已经定义了该常量
    if f'static final {const_type} {const_name}' in content or f'{const_type} {const_name} =' in content:
        return False
    
    # 替换魔法值为常量引用
    if const_type == 'String':
        # 对于字符串，需要精确匹配引号内的内容
        old_pattern = magic_value
        new_value = const_name
    else:
        # 对于数字，需要确保是独立的数字
        old_pattern = f'\\b{magic_value}\\b'
        new_value = const_name
    
    # 替换所有出现的魔法值
    if const_type == 'String':
        content = content.replace(magic_value, const_name)
    else:
        content = re.sub(old_pattern, const_name, content)
    
    # 查找类声明后的第一个位置插入常量定义
    class_match = re.search(r'(public\s+class\s+\w+[^{]*\{)', content)
    if not class_match:
        return False
    
    insert_pos = class_match.end()
    
    # 构建常量定义
    if const_type == 'String':
        const_def = f'\n\n    private static final {const_type} {const_name} = {magic_value};'
    else:
        const_def = f'\n\n    private static final {const_type} {const_name} = {magic_value};'
    
    # 插入常量定义
    new_content = content[:insert_pos] + const_def + content[insert_pos:]
    
    file_path.write_text(new_content, encoding='utf-8')
    return True

def find_file(base_dir, filename):
    """在目录树中查找文件"""
    results = list(base_dir.rglob(filename))
    return results[0] if results else None

def main():
    base_dir = Path('/Users/work/apex-scaffold/apex-application/src/main/java')
    
    print("修复魔法常量...")
    fixed_count = 0
    
    for filename, constants in magic_constants.items():
        file_path = find_file(base_dir, filename)
        if not file_path:
            print(f'  ⚠ 未找到文件: {filename}')
            continue
        
        print(f'\n处理文件: {filename}')
        for magic_value, const_name, const_type in constants:
            if add_constant_to_class(file_path, magic_value, const_name, const_type):
                print(f'  ✓ {magic_value} -> {const_name}')
                fixed_count += 1
            else:
                print(f'  - {const_name} 已存在或无需修复')
    
    print(f'\n共修复 {fixed_count} 个魔法常量')

if __name__ == '__main__':
    main()
