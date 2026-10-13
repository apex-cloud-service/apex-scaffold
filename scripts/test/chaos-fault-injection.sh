#!/bin/bash
###############################################################################
# 混沌故障注入演练脚本（Phase 7 / 强化 §7.1）
# 用途：一键演练「杀 auth 看降级」「杀业务服务看其余域正常」，并输出断言结果。
# 作者：Apex Scaffold Team
# 日期：2026-10-10
# 依赖：bash、curl
# 兼容：macOS 自带 bash 3.2（不使用关联数组）
# 用法：
#   ./scripts/test/chaos-fault-injection.sh --scenario auth-down [--yes]
#   ./scripts/test/chaos-fault-injection.sh --scenario workflow-down [--yes]
# 说明：--yes 跳过人工确认（供 CI 非交互调用，退出码 0=通过 1=失败）。
###############################################################################

set -euo pipefail

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

SCENARIO=""
ASSUME_YES=false

# 服务端口映射（与目标架构一致；bash 3.2 无关联数组，改用 case 查询）
service_port() {
  case "$1" in
    gateway)  echo 8080 ;;
    auth)     echo 8081 ;;
    system)   echo 8082 ;;
    workflow) echo 8083 ;;
    office)   echo 8084 ;;
    file)     echo 8085 ;;
    *)        echo "" ;;
  esac
}

# 服务进程关键字（用于 pkill 定位）
service_proc() {
  case "$1" in
    auth)     echo "apex-auth-service" ;;
    workflow) echo "apex-workflow-service" ;;
    file)     echo "apex-file-service" ;;
    *)        echo "" ;;
  esac
}

usage() {
    echo "用法: $0 --scenario <auth-down|workflow-down> [--yes]"
    echo "  --scenario  演练场景"
    echo "  --yes       跳过人工确认（CI/非交互）"
    exit 1
}

# 解析参数
while [[ $# -gt 0 ]]; do
  case "$1" in
    --scenario) SCENARIO="${2:-}"; shift 2 ;;
    --yes) ASSUME_YES=true; shift ;;
    *) echo "⚠ 未知参数: $1"; usage ;;
  esac
done

[[ -z "$SCENARIO" ]] && { echo "❌ 缺少 --scenario"; usage; }
if [[ "$SCENARIO" != "auth-down" && "$SCENARIO" != "workflow-down" ]]; then
  echo "❌ 仅支持 auth-down / workflow-down"; usage
fi

# 健康检查函数（管理端口 /actuator/health，返回 0=健康）
health_ok() {
  local port="$1"
  curl -sf --max-time 3 "http://127.0.0.1:${port}/actuator/health" >/dev/null 2>&1
}

# 确认待杀服务对应端口当前健康（避免误杀已停止实例）
do_confirm() {
  local svc="$1"
  local port
  port="$(service_port "$svc")"
  if health_ok "$port"; then
    echo "— 发现 $svc (:$port) 健康，即将注入 kill -9"
  else
    echo "⚠ $svc (:$port) 当前不可达，演练仍继续（断言以其余域为准）"
  fi
  if [[ "$ASSUME_YES" != "true" ]]; then
    read -r -p "确认执行混沌演练 $SCENARIO？(y/N) " ans
    case "$ans" in
      y|Y) ;;
      *) echo "已取消"; exit 0 ;;
    esac
  fi
}

# 断言函数：期望某服务健康
assert_healthy() {
  local svc="$1"
  local port
  port="$(service_port "$svc")"
  if health_ok "$port"; then
    echo -e "${GREEN}✓${NC} $svc (:$port) 健康"
  else
    echo -e "${RED}✗${NC} $svc (:$port) 不健康"
    return 1
  fi
}

# 断言函数：期望某服务不可达（已注入故障）
assert_down() {
  local svc="$1"
  local port
  port="$(service_port "$svc")"
  if health_ok "$port"; then
    echo -e "${RED}✗${NC} $svc (:$port) 仍可访问（注入失败或自愈过快）"
    return 1
  else
    echo -e "${GREEN}✓${NC} $svc (:$port) 已按预期摘除（混沌注入生效）"
  fi
}

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  Chaos 混沌故障注入演练  ${SCENARIO}${NC}"
echo -e "${BLUE}========================================${NC}"

FAILED=0

case "$SCENARIO" in
  auth-down)
    do_confirm auth
    echo ""
    echo "── 注入：kill -9 apex-auth-service ──"
    pkill -9 -f "$(service_proc auth)" || echo "（无匹配进程或已停止）"
    sleep 3
    echo ""
    echo "── 断言：auth 已被摘除 ──"
    assert_down auth || FAILED=1
    echo ""
    echo "── 断言：其余域（workflow/file）独立可服务 ──"
    assert_healthy workflow || FAILED=1
    assert_healthy file || FAILED=1
    echo ""
    echo "── 断言：网关其余路由不受 auth 宕机拖垮 ──"
    if health_ok "$(service_port gateway)"; then
      echo -e "${GREEN}✓${NC} 网关 (:$(service_port gateway)) 健康"
    else
      echo -e "${YELLOW}⚠${NC} 网关未运行（本机未启动，跳过网关断言）"
    fi
    echo ""
    echo "恢复提示: 重启 apex-auth-service 后重新注册 Nacos 即完成恢复。"
    ;;
  workflow-down)
    do_confirm workflow
    echo ""
    echo "── 注入：kill -9 apex-workflow-service ──"
    pkill -9 -f "$(service_proc workflow)" || echo "（无匹配进程或已停止）"
    sleep 3
    echo ""
    echo "── 断言：workflow 已被摘除 ──"
    assert_down workflow || FAILED=1
    echo ""
    echo "── 断言：其余域（file/auth）独立可服务 ──"
    assert_healthy file || FAILED=1
    assert_healthy auth || FAILED=1
    echo ""
    echo "恢复提示: 重启 apex-workflow-service 后重新注册 Nacos 即完成恢复。"
    ;;
esac

echo ""
if [[ "$FAILED" -eq 0 ]]; then
  echo -e "${GREEN}✔ 混沌演练通过：故障注入符合预期，隔离性成立。${NC}"
  exit 0
else
  echo -e "${RED}✘ 混沌演练未通过：存在断言失败，请检查降级/隔离配置。${NC}"
  exit 1
fi