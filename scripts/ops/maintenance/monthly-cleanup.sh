#!/usr/bin/env bash

###############################################################################
# monthly-cleanup.sh - 月度清理：备份文件与临时产物检查清理，日志写 logs/maintenance-YYYYMM.log
# 用法：./scripts/ops/maintenance/monthly-cleanup.sh  无参数；删除前交互确认
# 作者：Apex Team    创建：2026-10-01
###############################################################################
set -euo pipefail

readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly PROJECT_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
readonly LOG_FILE="$PROJECT_ROOT/logs/maintenance-$(date +%Y%m).log"

mkdir -p "$PROJECT_ROOT/logs"

log_info() {
    echo "[$(date +'%Y-%m-%d %H:%M:%S')] [INFO] $*" | tee -a "$LOG_FILE"
}

log_warn() {
    echo "[$(date +'%Y-%m-%d %H:%M:%S')] [WARN] $*" | tee -a "$LOG_FILE"
}

log_error() {
    echo "[$(date +'%Y-%m-%d %H:%M:%S')] [ERROR] $*" | tee -a "$LOG_FILE"
}

find_backup_files() {
    log_info "检查备份文件..."
    
    local backup_count=0
    backup_count=$(find "$PROJECT_ROOT" -type f \( -name "*.bak" -o -name "*.backup" -o -name "*~" \) 2>/dev/null | wc -l | tr -d ' ')
    
    if [ "$backup_count" -gt 0 ]; then
        log_warn "发现 $backup_count 个备份文件:"
        find "$PROJECT_ROOT" -type f \( -name "*.bak" -o -name "*.backup" -o -name "*~" \) -exec ls -lh {} \; | tee -a "$LOG_FILE"
        
        read -p "是否删除这些备份文件? (y/N) " -n 1 -r
        echo
        if [[ $REPLY =~ ^[Yy]$ ]]; then
            find "$PROJECT_ROOT" -type f \( -name "*.bak" -o -name "*.backup" -o -name "*~" \) -delete
            log_info "已删除 $backup_count 个备份文件"
        else
            log_info "跳过删除备份文件"
        fi
    else
        log_info "✓ 未发现备份文件"
    fi
}

find_duplicate_files() {
    log_info "检查重复文件..."
    
    local duplicates
    duplicates=$(find "$PROJECT_ROOT/docs" -type f -name "*副本*" -o -name "*copy*" -o -name "*_2.*" 2>/dev/null | wc -l | tr -d ' ')
    
    if [ "$duplicates" -gt 0 ]; then
        log_warn "发现 $duplicates 个可能的重复文件:"
        find "$PROJECT_ROOT/docs" -type f \( -name "*副本*" -o -name "*copy*" -o -name "*_2.*" \) -exec ls -lh {} \; | tee -a "$LOG_FILE"
        log_info "请手动检查这些文件是否需要删除"
    else
        log_info "✓ 未发现重复文件"
    fi
}

find_empty_dirs() {
    log_info "检查空目录..."
    
    local empty_dirs
    empty_dirs=$(find "$PROJECT_ROOT" -type d -empty -not -path "*/node_modules/*" -not -path "*/.git/*" 2>/dev/null)
    
    if [ -n "$empty_dirs" ]; then
        local count
        count=$(echo "$empty_dirs" | wc -l | tr -d ' ')
        log_warn "发现 $count 个空目录:"
        echo "$empty_dirs" | tee -a "$LOG_FILE"
        
        read -p "是否删除这些空目录? (y/N) " -n 1 -r
        echo
        if [[ $REPLY =~ ^[Yy]$ ]]; then
            echo "$empty_dirs" | xargs rm -rf
            log_info "已删除空目录"
        else
            log_info "跳过删除空目录"
        fi
    else
        log_info "✓ 未发现空目录"
    fi
}

check_large_files() {
    log_info "检查大文件 (>10MB)..."
    
    local large_files
    large_files=$(find "$PROJECT_ROOT" -type f -size +10M -not -path "*/node_modules/*" -not -path "*/.git/*" -not -path "*/target/*" 2>/dev/null)
    
    if [ -n "$large_files" ]; then
        log_warn "发现大文件:"
        echo "$large_files" | xargs ls -lh | tee -a "$LOG_FILE"
        log_info "请检查这些大文件是否需要移除或归档"
    else
        log_info "✓ 未发现大文件"
    fi
}

check_temp_files() {
    log_info "检查临时文件..."
    
    local temp_count=0
    temp_count=$(find "$PROJECT_ROOT" -type f \( -name "*.tmp" -o -name "*.temp" -o -name ".DS_Store" \) 2>/dev/null | wc -l | tr -d ' ')
    
    if [ "$temp_count" -gt 0 ]; then
        log_warn "发现 $temp_count 个临时文件"
        find "$PROJECT_ROOT" -type f \( -name "*.tmp" -o -name "*.temp" -o -name ".DS_Store" \) -delete
        log_info "已删除临时文件"
    else
        log_info "✓ 未发现临时文件"
    fi
}

main() {
    log_info "========================================"
    log_info "开始月度清理维护"
    log_info "========================================"
    
    cd "$PROJECT_ROOT"
    
    find_backup_files
    find_duplicate_files
    find_empty_dirs
    check_large_files
    check_temp_files
    
    log_info "========================================"
    log_info "月度清理维护完成"
    log_info "详细日志: $LOG_FILE"
    log_info "========================================"
}

main "$@"
