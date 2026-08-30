#!/usr/bin/env zsh
# ============================================================
# Apex Scaffold 性能压测脚本（wrk）
#
# 用法:
#   ./scripts/bench/bench.sh [base_url] [duration] [threads] [connections]
#
# 示例:
#   ./scripts/bench/bench.sh http://127.0.0.1:8080 30s 4 200
#
# 依赖: wrk（macOS: brew install wrk；Linux: apt install wrk / dnf install wrk）
# 说明: 默认仅执行健康检查；登录场景需配置真实账号（见 docs/sales/PERFORMANCE_REPORT.md）
# ============================================================

set -euo pipefail

BASE_URL="${1:-http://127.0.0.1:8080}"
DURATION="${2:-30s}"
THREADS="${3:-4}"
CONNECTIONS="${4:-200}"
BENCH_SCENARIO="${BENCH_SCENARIO:-health}"
BENCH_LOGIN_ENABLED="${BENCH_LOGIN_ENABLED:-false}"
BENCH_USERNAME="${BENCH_USERNAME:-}"
BENCH_PASSWORD="${BENCH_PASSWORD:-}"

case "${BENCH_SCENARIO}" in
  health|login|all) ;;
  *) echo "❌ BENCH_SCENARIO 只能是 health、login 或 all，失败说明见 docs/sales/PERFORMANCE_REPORT.md"; exit 1 ;;
esac

case "${BENCH_LOGIN_ENABLED}" in
  true|TRUE|1|yes|YES) LOGIN_ENABLED=true ;;
  false|FALSE|0|no|NO) LOGIN_ENABLED=false ;;
  *) echo "❌ BENCH_LOGIN_ENABLED 只能是 true/false，失败说明见 docs/sales/PERFORMANCE_REPORT.md"; exit 1 ;;
esac

command -v wrk >/dev/null 2>&1 || { echo "❌ 未找到 wrk，请先安装；失败说明见 docs/sales/PERFORMANCE_REPORT.md"; exit 1; }

echo "=============================================="
echo "Apex Scaffold 性能压测"
echo "  目标: ${BASE_URL}  时长: ${DURATION}"
echo "  线程: ${THREADS}    连接: ${CONNECTIONS}"
echo "=============================================="

if [[ "${BENCH_SCENARIO}" == "health" || "${BENCH_SCENARIO}" == "all" ]]; then
  echo ""
  echo "── 场景一: GET /api/v1/health（框架基线）──"
  wrk -t"${THREADS}" -c"${CONNECTIONS}" -d"${DURATION}" \
    "${BASE_URL}/api/v1/health"
fi

if [[ "${BENCH_SCENARIO}" == "login" || "${BENCH_SCENARIO}" == "all" ]]; then
  if [[ "${LOGIN_ENABLED}" != "true" ]]; then
    echo "❌ 已选择登录场景，但 BENCH_LOGIN_ENABLED 未启用；失败说明见 docs/sales/PERFORMANCE_REPORT.md"
    exit 1
  fi
  if [[ -z "${BENCH_USERNAME}" || -z "${BENCH_PASSWORD}" || "${BENCH_PASSWORD}" == "<replace>" ]]; then
    echo "❌ 登录场景需要通过 BENCH_USERNAME/BENCH_PASSWORD 提供真实账号，禁止使用占位密码；失败说明见 docs/sales/PERFORMANCE_REPORT.md"
    exit 1
  fi

  echo ""
  echo "── 场景二: POST /api/v1/auth/login（认证链路）──"
  LOGIN_BODY=$(printf '{"loginType":"PASSWORD","username":"%s","password":"%s","deviceId":"bench","platform":"web"}' "${BENCH_USERNAME}" "${BENCH_PASSWORD}")
  LOGIN_BODY="${LOGIN_BODY}" wrk -t"${THREADS}" -c"${CONNECTIONS}" -d"${DURATION}" \
    -s <(cat << 'LUA'
wrk.method = "POST"
wrk.headers["Content-Type"] = "application/json"
wrk.body = os.getenv("LOGIN_BODY")
LUA
) \
    -- "${BASE_URL}/api/v1/auth/login"
fi

echo ""
echo "✅ 压测完成，请将结果填入 docs/sales/PERFORMANCE_REPORT.md 基线记录表"
