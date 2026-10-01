#!/usr/bin/env python3
import os
import re
from pathlib import Path

def check_operation_log_annotations(base_dirs):
    """检查所有 Handler 文件中的 public Mono<ServerResponse> 方法是否有 @OperationLog 注解"""
    
    results = {
        'apex-auth-center': {'methods': [], 'missing': []},
        'apex-interfaces': {'methods': [], 'missing': []}
    }
    
    for project_name, base_dir in base_dirs.items():
        handler_dir = Path(base_dir)
        
        # 查找所有 Handler 文件
        handler_files = []
        for handler_file in handler_dir.rglob("*Handler.java"):
            # 排除 BaseHandler, AbstractStorageProxyHandler 和测试文件
            if (handler_file.name not in ['BaseHandler.java', 'AbstractStorageProxyHandler.java'] 
                and '/test/' not in str(handler_file)):
                handler_files.append(handler_file)
        
        # 检查每个文件
        for handler_file in sorted(handler_files):
            with open(handler_file, 'r', encoding='utf-8') as f:
                lines = f.readlines()
            
            # 查找所有 public Mono<ServerResponse> 方法
            for i, line in enumerate(lines, start=1):
                if re.search(r'public\s+Mono<ServerResponse>', line):
                    # 提取方法名
                    match = re.search(r'public\s+Mono<ServerResponse>\s+(\w+)\s*\(', line)
                    if match:
                        method_name = match.group(1)
                        
                        # 检查前面 5 行是否有 @OperationLog
                        has_annotation = False
                        for j in range(max(0, i-6), i):
                            if '@OperationLog' in lines[j]:
                                has_annotation = True
                                break
                        
                        # 记录结果
                        method_info = {
                            'file': str(handler_file.relative_to(Path(base_dir).parent)),
                            'line': i,
                            'method': method_name,
                            'has_annotation': has_annotation
                        }
                        
                        results[project_name]['methods'].append(method_info)
                        
                        if not has_annotation:
                            results[project_name]['missing'].append(method_info)
    
    return results

def print_report(results):
    """打印检查报告"""
    print("\n" + "="*80)
    print("Handler 方法 @OperationLog 注解检查报告")
    print("="*80 + "\n")
    
    total_methods = 0
    total_annotated = 0
    total_missing = 0
    
    for project_name in ['apex-auth-center', 'apex-interfaces']:
        project_results = results[project_name]
        methods = project_results['methods']
        missing = project_results['missing']
        
        project_total = len(methods)
        project_missing = len(missing)
        project_annotated = project_total - project_missing
        
        total_methods += project_total
        total_missing += project_missing
        total_annotated += project_annotated
        
        print(f"\n【{project_name}】")
        print(f"  总方法数: {project_total}")
        print(f"  已添加注解: {project_annotated}")
        print(f"  缺少注解: {project_missing}")
        
        if missing:
            print(f"\n  缺少 @OperationLog 注解的方法:")
            for method_info in missing:
                print(f"    {method_info['file']}:{method_info['line']}:{method_info['method']}")
    
    print("\n" + "-"*80)
    print("【总计】")
    print(f"  总方法数: {total_methods}")
    print(f"  已添加注解: {total_annotated}")
    print(f"  缺少注解: {total_missing}")
    print(f"  完成率: {total_annotated}/{total_methods} ({100*total_annotated/total_methods:.1f}%)")
    
    print("\n" + "="*80)
    if total_missing > 0:
        print("⚠️  需要修复：有 {} 个方法缺少 @OperationLog 注解".format(total_missing))
    else:
        print("✅ 检查通过：所有方法都已添加 @OperationLog 注解")
    print("="*80 + "\n")

if __name__ == '__main__':
    base_dirs = {
        'apex-auth-center': '/Users/work/apex-scaffold/apex-auth-center/src/main/java/com/apex/scaffold/authcenter/interfaces/handler',
        'apex-interfaces': '/Users/work/apex-scaffold/apex-interfaces/src/main/java/com/apex/scaffold/interfaces/handler'
    }
    
    results = check_operation_log_annotations(base_dirs)
    print_report(results)
