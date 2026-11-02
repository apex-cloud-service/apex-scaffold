#!/bin/bash
###############################################################################
# run-mqtt-integration.sh
#
# MQTT 真实 broker 联调（方案 §13 脚本交付物 / E3）。
#
# 拉起 EMQX 5.8 容器并运行 IoT MQTT 相关测试：
#   - IotMqttBrokerLiveTest           设备上行/租户隔离/共享订阅
#   - MqttTopicAclEnforcementTest     越权订阅拒绝（ACL）
#   - IotMqttThroughputBaselineTest   单连接吞吐基线
#   - IotMqttConcurrencyBaselineTest  多设备并发基线
#
# 用法：
#   bash scripts/test/run-mqtt-integration.sh          # 无 ACL（基线 + 冒烟）
#   bash scripts/test/run-mqtt-integration.sh acl      # 带 HTTP 授权钩子（需服务可访问）
#
# 依赖：Docker、JDK 21、mvnd；broker 不可达时相关测试自动跳过（assumeTrue）
###############################################################################
set -euo pipefail

SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=../lib/common.sh
source "${SCRIPT_DIR}/../lib/common.sh"

PROJECT_ROOT=$(get_project_root) || exit 1
cd "$PROJECT_ROOT" || exit 1

MODE=${1:-plain}
CONTAINER="apex-emqx-it"
PORT=1883

if [[ -f scripts/build/init-build-env.sh ]]; then
    # shellcheck source=../build/init-build-env.sh
    source scripts/build/init-build-env.sh
else
    log_failure "缺少 scripts/build/init-build-env.sh"
    exit 1
fi

cleanup() {
    if docker ps --format '{{.Names}}' | grep -q "^${CONTAINER}$"; then
        log_info "清理 EMQX 容器 ${CONTAINER}"
        docker rm -f "$CONTAINER" >/dev/null 2>&1 || true
    fi
}
trap cleanup EXIT

if ! docker info >/dev/null 2>&1; then
    log_failure "Docker 不可用，无法拉起 EMQX"
    exit 1
fi

if [[ "$MODE" == "acl" ]]; then
    ACL_URL=${APEX_MQTT_ACL_URL:-http://host.docker.internal:8094/internal/v1/iot/mqtt/acl}
    PORT=1884
    log_info "模式：ACL（HTTP 授权钩子 → ${ACL_URL}）"
    docker run -d --rm --name "$CONTAINER" -p "${PORT}:1883" \
        -e EMQX_AUTHORIZATION__SOURCES__1__TYPE=http \
        -e EMQX_AUTHORIZATION__SOURCES__1__ENABLE=true \
        -e EMQX_AUTHORIZATION__SOURCES__1__URL="$ACL_URL" \
        -e EMQX_AUTHORIZATION__SOURCES__1__METHOD=post \
        -e EMQX_AUTHORIZATION__NO_MATCH=deny \
        emqx/emqx:5.8 >/dev/null
    log_warn "ACL 模式需要 integration-service 在 8094 运行且授权钩子可达"
else
    log_info "模式：plain（无 ACL，用于吞吐基线与连通性冒烟）"
    docker run -d --rm --name "$CONTAINER" -p "${PORT}:1883" emqx/emqx:5.8 >/dev/null
fi

log_info "等待 EMQX 就绪（最多 90 秒）"
for _ in $(seq 1 45); do
    if (exec 3<>"/dev/tcp/127.0.0.1/${PORT}") 2>/dev/null; then
        exec 3<&- 2>/dev/null || true
        break
    fi
    sleep 2
done

if ! (exec 3<>"/dev/tcp/127.0.0.1/${PORT}") 2>/dev/null; then
    log_failure "EMQX 未在 ${PORT} 就绪"
    exit 1
fi

TESTS="IotMqttBrokerLiveTest,IotMqttThroughputBaselineTest,IotMqttConcurrencyBaselineTest"
if [[ "$MODE" == "acl" ]]; then
    TESTS="${TESTS},MqttTopicAclEnforcementTest"
fi

log_info "运行 MQTT 集成测试：${TESTS}"
mvnd -T1 -B -pl apex-integration test \
    -Dtest="$TESTS" \
    -Dsurefire.failIfNoSpecifiedTests=false \
    -Djacoco.skip=true \
    -Dapex.mqtt.port="$PORT" \
    -Dapex.mqtt.acl.port="$PORT"

log_success "MQTT 集成测试完成（吞吐数值见测试输出中的「吞吐基线」「并发基线」）"
