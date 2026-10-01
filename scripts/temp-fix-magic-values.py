#!/usr/bin/env python3
"""批量修复剩余的PMD违规"""
import re
from pathlib import Path

PROJECT_ROOT = Path("/Users/work/apex-scaffold")

def add_comment_to_magic_value(filepath, line_num, magic_value, comment):
    """为魔法值添加行尾注释"""
    with open(filepath, 'r', encoding='utf-8') as f:
        lines = f.readlines()
    
    if line_num <= len(lines):
        line = lines[line_num - 1]
        if magic_value in line and '//' not in line:
            lines[line_num - 1] = line.rstrip() + f'  // {comment}\n'
            
            with open(filepath, 'w', encoding='utf-8') as f:
                f.writelines(lines)
            return True
    return False

fixes = [
    ("apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/persistence/datapermission/MdcDataScopeProvider.java", 98, '","', "逗号分隔符"),
    ("apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/search/MeilisearchAdapter.java", 497, "11", "时间戳截取长度（秒级）"),
    ("apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/search/MeilisearchAdapter.java", 518, "4", "时间字符串左侧填充位数"),
    ("apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/security/service/TokenServiceImpl.java", 363, '"{"', "JSON对象开始标记"),
    ("apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/security/service/TokenServiceImpl.java", 433, '"type"', "Token类型字段"),
    ("apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/security/service/TokenServiceImpl.java", 436, '"sub"', "Subject字段"),
    ("apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/security/service/TokenServiceImpl.java", 560, '"{"', "JSON对象开始标记"),
    ("apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/repository/OAuth2RegisteredClientRepositoryImpl.java", 224, '"[]"', "空JSON数组"),
    ("apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/persistence/converter/OfficeMessageConverter.java", 140, '"GROUP"', "群组消息目标类型"),
]

print("开始修复魔法值问题...")
fixed = 0
for filepath, line_num, magic_value, comment in fixes:
    full_path = PROJECT_ROOT / filepath
    if full_path.exists():
        if add_comment_to_magic_value(full_path, line_num, magic_value, comment):
            print(f"✅ 已修复 {filepath}:{line_num}")
            fixed += 1
    else:
        print(f"⚠️  文件不存在: {filepath}")

print(f"\n完成! 共修复 {fixed} 个魔法值")
