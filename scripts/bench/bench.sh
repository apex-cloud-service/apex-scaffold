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
# 说明: 覆盖健康检查与登录两类核心链路；登录需真实账号（见 README）
# ============================================================

set -euo pipefail

BASE_URL="${1:-http://127.0.0.1:8080}"
DURATION="${2:-30s}"
THREADS="${3:-4}"
CONNECTIONS="${4:-200}"

command -v wrk >/dev/null 2>&1 || { echo "❌ 未找到 wrk，请先安装"; exit 1; }

echo "=============================================="
echo "Apex Scaffold 性能压测"
echo "  目标: ${BASE_URL}  时长: ${DURATION}"
echo "  线程: ${THREADS}    连接: ${CONNECTIONS}"
echo "=============================================="

# 场景一：健康检查（公开端点，评估框架基线吞吐）
echo ""
echo "── 场景一: GET /api/v1/health（框架基线）──"
wrk -t"${THREADS}" -c"${CONNECTIONS}" -d"${DURATION}" \
  "${BASE_URL}/api/v1/health"

# 场景二：登录（认证链路，评估 JWT+验证码+DB/Redis 读写）
echo ""
echo "── 场景二: POST /api/v1/auth/login（认证链路）──"
LOGIN_BODY='{"loginType":"PASSWORD","username":"admin","password":"<replace>","deviceId":"bench","platform":"web"}'
wrk -t"${THREADS}" -c"${CONNECTIONS}" -d"${DURATION}" \
  -s <(cat << 'LUA'
wrk.method = "POST"
wrk.headers["Content-Type"] = "application/json"
LUA
) \
  -- "${BASE_URL}/api/v1/auth/login" 2>/dev/null \
  || echo "（登录场景需配置真实账号与验证码策略，跳过，见 scripts/bench/README.md）"

echo ""
echo "✅ 压测完成，请将结果填入 docs/sales/PERFORMANCE_REPORT.md 基线记录表"
