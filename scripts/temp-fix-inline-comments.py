#!/usr/bin/env python3
"""批量修复行尾注释为行上方注释"""
import re
from pathlib import Path

PROJECT_ROOT = Path("/Users/work/apex-scaffold")

# 需要修复的文件和行号
fixes = [
    ("apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/search/MeilisearchAdapter.java", 497, "时间戳截取长度（秒级）"),
    ("apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/search/MeilisearchAdapter.java", 518, "时间字符串左侧填充位数"),
    ("apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/security/service/TokenServiceImpl.java", 363, "JSON对象开始标记"),
    ("apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/security/service/TokenServiceImpl.java", 433, "Token类型字段"),
    ("apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/security/service/TokenServiceImpl.java", 436, "Subject字段"),
]

def move_comment_above_line(filepath, line_num, comment_text):
    """将行尾注释移动到行上方"""
    with open(filepath, 'r', encoding='utf-8') as f:
        lines = f.readlines()
    
    if line_num <= len(lines):
        line = lines[line_num - 1]
        # 移除行尾注释
        line_without_comment = re.sub(r'\s*//.*$', '', line)
        
        # 获取缩进
        indent_match = re.match(r'^(\s*)', line_without_comment)
        indent = indent_match.group(1) if indent_match else ''
        
        # 在上一行添加注释
        new_comment_line = f'{indent}// {comment_text}\n'
        lines[line_num - 1] = line_without_comment
        
        # 插入注释行
        lines.insert(line_num - 1, new_comment_line)
        
        with open(filepath, 'w', encoding='utf-8') as f:
            f.writelines(lines)
        return True
    return False

print("开始修复行尾注释...")
fixed = 0
for filepath, line_num, comment in fixes:
    full_path = PROJECT_ROOT / filepath
    if full_path.exists():
        if move_comment_above_line(full_path, line_num, comment):
            print(f"✅ 已修复 {filepath}:{line_num}")
            fixed += 1
    else:
        print(f"⚠️  文件不存在: {filepath}")

print(f"\n完成! 共修复 {fixed} 个行尾注释")
