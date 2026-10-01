#!/usr/bin/env python3
"""批量修复 PMD 违规"""

import re
from pathlib import Path

# 定义需要修复的文件和对应的修复操作
fixes = {
    # 接口方法添加 Javadoc
    'assembler': {
        'CalendarEventAssembler': [
            ('toDto', '将日历事件实体转换为DTO', '@param entity 日历事件实体', '@return 日历事件DTO'),
            ('toEntity', '将日历事件DTO转换为实体', '@param dto 日历事件DTO', '@return 日历事件实体'),
        ],
        'DepartmentAssembler': [
            ('toDto', '将部门实体转换为DTO', '@param entity 部门实体', '@return 部门DTO'),
            ('toEntity', '将部门DTO转换为实体', '@param dto 部门DTO', '@return 部门实体'),
        ],
        'EmployeeAssembler': [
            ('toDto', '将员工实体转换为DTO', '@param entity 员工实体', '@return 员工DTO'),
            ('toEntity', '将员工DTO转换为实体', '@param dto 员工DTO', '@return 员工实体'),
        ],
        'OffMessageAssembler': [
            ('toDTO', '将消息实体转换为DTO', '@param entity 消息实体', '@return 消息DTO'),
            ('toDTOList', '将消息实体列表转换为DTO列表', '@param entities 消息实体列表', '@return 消息DTO列表'),
            ('toEntity', '将消息DTO转换为实体', '@param dto 消息DTO', '@return 消息实体'),
        ],
        'OffMessageTemplateAssembler': [
            ('toDTO', '将消息模板实体转换为DTO', '@param entity 消息模板实体', '@return 消息模板DTO'),
            ('toDTOList', '将消息模板实体列表转换为DTO列表', '@param entities 消息模板实体列表', '@return 消息模板DTO列表'),
            ('toEntity', '将消息模板DTO转换为实体', '@param dto 消息模板DTO', '@return 消息模板实体'),
        ],
        'SearchResultAssembler': [
            ('toDTO', '将搜索结果实体转换为DTO', '@param entity 搜索结果实体', '@return 搜索结果DTO'),
            ('formatDateTime', '格式化日期时间', '@param dateTime 日期时间', '@return 格式化后的日期时间字符串'),
        ],
        'SyncHistoryAssembler': [
            ('toDto', '将同步历史实体转换为DTO', '@param entity 同步历史实体', '@return 同步历史DTO'),
            ('toEntity', '将同步历史DTO转换为实体', '@param dto 同步历史DTO', '@return 同步历史实体'),
        ],
        'SysSsoAuditLogAssembler': [
            ('toDTO', '将SSO审计日志实体转换为DTO', '@param entity SSO审计日志实体', '@return SSO审计日志DTO'),
            ('toDTOList', '将SSO审计日志实体列表转换为DTO列表', '@param entities SSO审计日志实体列表', '@return SSO审计日志DTO列表'),
        ],
        'SysSsoProviderAssembler': [
            ('toEntity', '将SSO提供商DTO转换为实体', '@param dto SSO提供商DTO', '@return SSO提供商实体'),
            ('toDTO', '将SSO提供商实体转换为DTO', '@param entity SSO提供商实体', '@return SSO提供商DTO'),
            ('toDTOList', '将SSO提供商实体列表转换为DTO列表', '@param entities SSO提供商实体列表', '@return SSO提供商DTO列表'),
            ('map', '映射SSO提供商属性', '@param source 源对象', '@return 目标对象'),
        ],
        'SysSsoSessionAssembler': [
            ('toDTO', '将SSO会话实体转换为DTO', '@param entity SSO会话实体', '@return SSO会话DTO'),
            ('toDTOList', '将SSO会话实体列表转换为DTO列表', '@param entities SSO会话实体列表', '@return SSO会话DTO列表'),
        ],
    },
    'service_interfaces': {
        'ChartService': [
            ('create', '创建图表', '@param request 图表创建请求', '@return 创建的图表'),
            ('update', '更新图表', '@param id 图表ID', '@param request 图表更新请求', '@return 更新后的图表'),
            ('delete', '删除图表', '@param id 图表ID', '@return 删除结果'),
            ('getById', '根据ID获取图表', '@param id 图表ID', '@return 图表信息'),
            ('query', '查询图表列表', '@param request 查询请求', '@return 图表列表'),
            ('updateStatus', '更新图表状态', '@param id 图表ID', '@param status 新状态', '@return 更新结果'),
            ('getChartData', '获取图表数据', '@param id 图表ID', '@return 图表数据'),
            ('incrementViewCount', '增加图表查看次数', '@param id 图表ID', '@return 更新结果'),
        ],
        'DocumentService': [
            ('create', '创建文档', '@param request 文档创建请求', '@return 创建的文档'),
            ('update', '更新文档', '@param id 文档ID', '@param request 文档更新请求', '@return 更新后的文档'),
            ('delete', '删除文档', '@param id 文档ID', '@return 删除结果'),
            ('getById', '根据ID获取文档', '@param id 文档ID', '@return 文档信息'),
            ('query', '查询文档列表', '@param request 查询请求', '@return 文档列表'),
            ('updateStatus', '更新文档状态', '@param id 文档ID', '@param status 新状态', '@return 更新结果'),
            ('incrementViewCount', '增加文档查看次数', '@param id 文档ID', '@return 更新结果'),
            ('incrementDownloadCount', '增加文档下载次数', '@param id 文档ID', '@return 更新结果'),
        ],
        'OAuth2ClientSecretRotationService': [
            ('rotateClientSecret', '轮换客户端密钥', '@param clientId 客户端ID', '@return 轮换结果'),
            ('rotateClientSecretWithCustomExpiry', '使用自定义过期时间轮换客户端密钥', '@param clientId 客户端ID', '@param expiryDays 过期天数', '@return 轮换结果'),
            ('validateClientSecret', '验证客户端密钥', '@param clientId 客户端ID', '@param secret 密钥', '@return 验证结果'),
            ('findClientsWithExpiredSecrets', '查找密钥已过期的客户端', '@return 客户端列表'),
            ('findClientsWithExpiringSoonSecrets', '查找密钥即将过期的客户端', '@param days 天数', '@return 客户端列表'),
            ('scheduleSecretRotation', '调度密钥轮换', '@param clientId 客户端ID', '@param scheduledDate 调度日期', '@return 调度结果'),
            ('notifyClientSecretExpiring', '通知客户端密钥即将过期', '@param clientId 客户端ID', '@return 通知结果'),
        ],
    },
}

def add_method_javadoc(file_path, method_name, description, *params):
    """为方法添加 Javadoc"""
    content = file_path.read_text(encoding='utf-8')
    
    # 查找方法定义（处理可能的泛型、注解等）
    pattern = rf'(\s*)((?:@[\w.]+(?:\([^)]*\))?\s*)*)(.*?\s+{re.escape(method_name)}\s*\()'
    
    matches = list(re.finditer(pattern, content, re.MULTILINE))
    if not matches:
        return False
    
    # 从最后一个匹配开始替换（避免索引变化）
    modified = False
    for match in reversed(matches):
        indent = match.group(1)
        annotations = match.group(2)
        method_sig = match.group(3)
        
        # 检查是否已有 Javadoc
        before_method = content[:match.start()]
        if '/**' in before_method[-200:] and '*/' in before_method[-50:]:
            continue
        
        # 构建 Javadoc
        javadoc_lines = [
            f'{indent}/**',
            f'{indent} * {description}。',
            f'{indent} *',
        ]
        
        for param in params:
            javadoc_lines.append(f'{indent} * {param}')
        
        javadoc_lines.append(f'{indent} */')
        javadoc = '\n'.join(javadoc_lines) + '\n'
        
        # 插入 Javadoc
        new_content = (
            content[:match.start()] +
            javadoc +
            indent + annotations + method_sig +
            content[match.end():]
        )
        content = new_content
        modified = True
    
    if modified:
        file_path.write_text(content, encoding='utf-8')
    return modified

def main():
    base_dir = Path('/Users/work/apex-scaffold/apex-application/src/main/java')
    
    # 修复 assembler 接口
    print("修复 Assembler 接口...")
    for class_name, methods in fixes['assembler'].items():
        file_path = base_dir / 'com/apex/scaffold/application/assembler' / f'{class_name}.java'
        if file_path.exists():
            for method_info in methods:
                if add_method_javadoc(file_path, *method_info):
                    print(f'  ✓ {class_name}.{method_info[0]}')
    
    # 修复 service 接口
    print("\n修复 Service 接口...")
    for class_name, methods in fixes['service_interfaces'].items():
        file_path = base_dir / 'com/apex/scaffold/application/service' / f'{class_name}.java'
        if file_path.exists():
            for method_info in methods:
                if add_method_javadoc(file_path, *method_info):
                    print(f'  ✓ {class_name}.{method_info[0]}')
    
    print("\n接口方法 Javadoc 修复完成！")

if __name__ == '__main__':
    main()
