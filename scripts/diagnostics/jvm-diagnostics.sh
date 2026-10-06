#!/usr/bin/env bash

###############################################################################
# jvm-diagnostics.sh - JVM 在线诊断信息采集（jcmd 封装）
# 用法：./scripts/diagnostics/jvm-diagnostics.sh  {flags|heap-info|thread-dump|jfr|all} PID [OUTPUT_DIR]；无效 action 退出码 2，--help 查看用法
# 作者：Apex Team    创建：2026-08-30
###############################################################################
set -euo pipefail

usage() {
  printf '%s\n' "Usage: $0 {flags|heap-info|thread-dump|jfr|all} PID [OUTPUT_DIR]"
}

fail() {
  printf 'error: %s\n' "$1" >&2
  exit 1
}

if [ "${1:-}" = "--help" ] || [ "${1:-}" = "-h" ]; then
  usage
  exit 0
fi

ACTION="${1:-}"
PID="${2:-}"
OUTPUT_DIR="${3:-./logs/jvm-diagnostics}"

case "${ACTION}" in
  flags|heap-info|thread-dump|jfr|all) ;;
  *) usage >&2; exit 2 ;;
esac
case "${PID}" in
  ''|*[!0-9]*) fail "PID must contain only digits" ;;
esac

command -v jcmd >/dev/null 2>&1 || fail "jcmd is required"
kill -0 "${PID}" 2>/dev/null || fail "process ${PID} is not running or is not accessible"
mkdir -p "${OUTPUT_DIR}"

run_jcmd() {
  local name="$1"
  shift
  jcmd "${PID}" "$@" > "${OUTPUT_DIR}/${PID}-${name}.txt"
}

case "${ACTION}" in
  flags)
    run_jcmd flags VM.flags
    ;;
  heap-info)
    run_jcmd heap-info GC.heap_info
    ;;
  thread-dump)
    run_jcmd thread-dump Thread.print -l
    ;;
  jfr)
    jcmd "${PID}" JFR.start name=apex-diagnostics settings=profile duration=60s filename="${OUTPUT_DIR}/${PID}-jfr.jfr" > "${OUTPUT_DIR}/${PID}-jfr-start.txt"
    ;;
  all)
    run_jcmd flags VM.flags
    run_jcmd heap-info GC.heap_info
    run_jcmd thread-dump Thread.print -l
    jcmd "${PID}" JFR.start name=apex-diagnostics settings=profile duration=60s filename="${OUTPUT_DIR}/${PID}-jfr.jfr" > "${OUTPUT_DIR}/${PID}-jfr-start.txt"
    ;;
esac

printf 'JVM diagnostics written to %s\n' "${OUTPUT_DIR}"
