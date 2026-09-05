#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
SVG图表云原生图标批量升级脚本
版本: v2.0
日期: 2026-09-05
"""

import os
import re
import shutil
from pathlib import Path

DIAGRAM_DIR = Path("/Users/work/apex-scaffold/docs/diagrams")
ICON_LIB = DIAGRAM_DIR / "_templates" / "cloud-native-icons.svg"
BACKUP_SUFFIX = ".before-icons"

FILES_TO_UPGRADE = [
    "01_logical_architecture.svg",
    "02_physical_architecture.svg",
    "03_technical_architecture.svg",
    "04_login_auth_flow.svg",
    "05_permission_check_flow.svg",
    "06_workflow_approval_flow.svg",
    "07_file_upload_flow.svg",
    "08_database_er_diagram.svg",
    "09_sentinel_rate_limit_circuit_breaker.svg",
    "10_multi_level_cache_architecture.svg",
    "11_auto_retry_strategy.svg",
    "11_auto_retry_strategy_flow.svg",
    "12_security_protection_system.svg",
    "13_deployment_architecture.svg",
    "14_network_architecture.svg",
    "15_data_flow_architecture.svg",
    "16_mq_architecture.svg",
    "17_monitoring_architecture.svg",
    "18_microservice_governance.svg",
    "19_backend_technology_stack.svg",
    "20_infrastructure_components.svg",
    "21_technology_ecosystem.svg",
    "22_frontend_technology_stack.svg",
    "23_frontend_backend_interaction.svg",
    "24_grafana_dashboard_architecture.svg",
    "25_prometheus_monitoring_architecture.svg",
    "26_disaster_recovery_flow.svg",
    "27_cicd_pipeline_detail.svg",
    "28_api_gateway_detail.svg",
    "29_security_threat_model.svg",
]

def extract_icon_defs(icon_lib_path):
    """从图标库提取图标定义"""
    with open(icon_lib_path, 'r', encoding='utf-8') as f:
        content = f.read()
    
    # 提取 <g id="icon-xxx"> ... </g> 的所有定义
    pattern = r'(<g id="icon-[^"]+">.*?</g>)'
    icons = re.findall(pattern, content, re.DOTALL)
    
    return '\n    '.join(icons)

def upgrade_svg(filepath, icon_defs):
    """升级单个SVG文件"""
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()
    
    # 检查是否已经包含图标
    if 'id="icon-kubernetes"' in content or 'id="icon-springboot"' in content:
        return False, "已包含图标库"
    
    # 创建备份
    backup_path = str(filepath) + BACKUP_SUFFIX
    shutil.copy2(filepath, backup_path)
    
    # 准备图标注释
    icon_comment = '\n    <!-- 云原生专业图标库 - 自动添加 -->\n    '
    
    # 查找 <defs> 标签
    if '<defs>' in content:
        # 在 </defs> 前插入
        content = content.replace('</defs>', f'{icon_comment}{icon_defs}\n  </defs>')
    else:
        # 在 <svg> 后添加 <defs>
        svg_match = re.search(r'(<svg[^>]*>)', content)
        if svg_match:
            insert_pos = svg_match.end()
            defs_block = f'\n  <defs>{icon_comment}{icon_defs}\n  </defs>'
            content = content[:insert_pos] + defs_block + content[insert_pos:]
        else:
            return False, "未找到<svg>标签"
    
    # 写入文件
    with open(filepath, 'w', encoding='utf-8') as f:
        f.write(content)
    
    return True, "成功"

def main():
    print("=" * 50)
    print("  SVG 云原生图标批量升级工具 v2.0 (Python)")
    print("=" * 50)
    print()
    
    # 检查图标库
    if not ICON_LIB.exists():
        print(f"❌ 错误：图标库文件不存在: {ICON_LIB}")
        return
    
    print("✅ 图标库文件检查通过")
    print()
    
    # 提取图标定义
    print("📦 正在提取图标定义...")
    try:
        icon_defs = extract_icon_defs(ICON_LIB)
        icon_count = icon_defs.count('id="icon-')
        print(f"✅ 成功提取 {icon_count} 个图标定义")
    except Exception as e:
        print(f"❌ 提取失败: {e}")
        return
    
    print()
    print(f"🚀 开始批量升级 {len(FILES_TO_UPGRADE)} 个文件...")
    print()
    
    upgraded = 0
    skipped = 0
    failed = 0
    
    for filename in FILES_TO_UPGRADE:
        filepath = DIAGRAM_DIR / filename
        
        if not filepath.exists():
            print(f"⚠️  跳过: {filename} (文件不存在)")
            skipped += 1
            continue
        
        print(f"🔄 处理: {filename}")
        
        try:
            success, message = upgrade_svg(filepath, icon_defs)
            
            if success:
                print(f"   ✅ {message}")
                upgraded += 1
            else:
                print(f"   ⏭️  {message}")
                skipped += 1
        except Exception as e:
            print(f"   ❌ 失败: {e}")
            # 恢复备份
            backup_path = str(filepath) + BACKUP_SUFFIX
            if Path(backup_path).exists():
                shutil.copy2(backup_path, filepath)
            failed += 1
        
        print()
    
    print("=" * 50)
    print("          批量升级完成")
    print("=" * 50)
    print()
    print("📊 统计结果:")
    print(f"  - 总文件数: {len(FILES_TO_UPGRADE)}")
    print(f"  - 成功升级: {upgraded}")
    print(f"  - 跳过文件: {skipped}")
    print(f"  - 失败文件: {failed}")
    print()
    
    if failed == 0:
        print("🎉 所有文件升级成功！")
        print()
        print(f"📝 备份文件: *{BACKUP_SUFFIX}")
        print("💡 如需回滚，删除原文件并重命名备份文件")
    else:
        print("⚠️  部分文件升级失败，请检查日志")
    
    print()
    print("✅ 任务完成")

if __name__ == "__main__":
    main()
