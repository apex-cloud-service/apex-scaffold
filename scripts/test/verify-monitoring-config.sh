#!/usr/bin/env bash
# =============================================================================
# Apex Scaffold - 监控配置静态验证脚本
# -----------------------------------------------------------------------------
# 功能:
#   1. 校验 Prometheus 告警规则数量、告警名与 PromQL 指标口径
#   2. 校验 Prometheus 抓取任务、应用/模块标签和 Sentinel 端口
#   3. 校验 Grafana 主大盘与增强大盘的结构、面板数量和禁止出现的旧指标
#   4. 校验 Alertmanager 配置可解析
#   5. promtool / amtool 可用时执行官方配置语法检查
#
# 说明:
#   本脚本只做静态校验，不连接 Prometheus、Grafana、Alertmanager 或真实服务。
#   工具缺失时输出 SKIP 并返回 3，不能将 SKIP 记为验证通过。
#
# 退出码:
#   0 - 全部静态检查通过且 promtool / amtool 均执行成功
#   1 - 静态检查失败或官方工具报告配置错误
#   3 - 静态检查通过，但存在因工具缺失产生的 SKIP
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
ROOT_DIR="$(cd -- "${SCRIPT_DIR}/../.." && pwd -P)"
# shellcheck source=../lib/common.sh
source "${ROOT_DIR}/scripts/lib/common.sh"

ALERT_RULES="${ROOT_DIR}/config/monitoring/prometheus/alert-rules.yml"
PROMETHEUS_CONFIG="${ROOT_DIR}/config/monitoring/prometheus/prometheus.yml"
ALERTMANAGER_CONFIG="${ROOT_DIR}/config/monitoring/prometheus/alertmanager.yml"
MAIN_DASHBOARD="${ROOT_DIR}/config/monitoring/grafana/apex-dashboard.json"
ENHANCED_DASHBOARD="${ROOT_DIR}/config/monitoring/grafana/apex-dashboard-enhanced.json"

SKIPPED=0
FAILED=0

skip() {
    log_warn "SKIP: $*"
    SKIPPED=$((SKIPPED + 1))
}

fail_check() {
    log_failure "$*"
    FAILED=$((FAILED + 1))
}

print_header "监控配置静态验证"

for file in "$ALERT_RULES" "$PROMETHEUS_CONFIG" "$ALERTMANAGER_CONFIG" "$MAIN_DASHBOARD" "$ENHANCED_DASHBOARD"; do
    if [[ ! -f "$file" ]]; then
        fail_check "缺少文件: ${file#${ROOT_DIR}/}"
    fi
done

if [[ $FAILED -gt 0 ]]; then
    exit 1
fi

if ! command -v python3 >/dev/null 2>&1; then
    skip "未安装 python3，无法解析 YAML"
elif ! python3 -c 'import yaml' >/dev/null 2>&1; then
    skip "python3 缺少 PyYAML，无法解析 YAML"
else
    if python3 - "$ALERT_RULES" "$PROMETHEUS_CONFIG" "$ALERTMANAGER_CONFIG" <<'PY'
import re
import sys
from pathlib import Path

import yaml

alert_path, prom_path, am_path = map(Path, sys.argv[1:4])
errors = []

try:
    alerts = yaml.safe_load(alert_path.read_text(encoding="utf-8"))
    prometheus = yaml.safe_load(prom_path.read_text(encoding="utf-8"))
    alertmanager = yaml.safe_load(am_path.read_text(encoding="utf-8"))
except Exception as exc:  # noqa: BLE001 - 向命令行输出解析失败原因
    print(f"YAML 解析失败: {exc}", file=sys.stderr)
    raise SystemExit(1)

groups = alerts.get("groups", []) if isinstance(alerts, dict) else []
all_rules = [rule for group in groups for rule in (group.get("rules") or [])]
alerts_found = [rule.get("alert") for rule in all_rules if rule.get("alert")]
expected_alerts = {
    "MetaspaceUsageHigh",
    "MetaspaceUsageCritical",
    "CodeCacheUsageHigh",
    "GCPauseTimeHigh",
    "GCPauseTimeCritical",
    "SentinelBlockQpsHigh",
    "SentinelResourceRtHigh",
    "SlowRequestsExcessive",
    "ConnectionPoolUsageHigh",
    "CircuitBreakerOpen",
    "CircuitBreakerFailureRateHigh",
    "DBConnectionPoolUsageHigh",
    "DBSlowQueriesExcessive",
}
if len(groups) != 5:
    errors.append(f"告警分组应为 5 组，实际 {len(groups)} 组")
if len(all_rules) != 13:
    errors.append(f"告警规则应为 13 条，实际 {len(all_rules)} 条")
if set(alerts_found) != expected_alerts:
    missing = sorted(expected_alerts - set(alerts_found))
    unexpected = sorted(set(alerts_found) - expected_alerts)
    errors.append(f"告警名集合不一致，缺少={missing}，多出={unexpected}")

expressions = "\n".join(str(rule.get("expr", "")) for rule in all_rules)
required_fragments = {
    "apex_api_response_time_seconds_bucket": "慢请求告警",
    "apex_sql_slow_count_total": "慢 SQL 告警",
    "jvm_gc_pause_seconds_max": "GC 暂停告警",
    'sentinel_metrics{type="blockQps"}': "Sentinel 限流告警",
}
for fragment, purpose in required_fragments.items():
    if fragment not in expressions:
        errors.append(f"{purpose}未使用真实指标 {fragment}")

forbidden = {
    "jdbc_query_duration_seconds": "已退役的 JDBC 慢查询指标",
    "sentinel_blocked_qps": "不存在的 Sentinel 指标",
    'quantile=': "GC 分位数指标不存在",
}
for fragment, purpose in forbidden.items():
    if fragment in expressions:
        errors.append(f"告警规则包含{purpose}: {fragment}")

if re.search(r'job\s*=\s*"apex-scaffold"', expressions):
    errors.append('告警规则不得使用 job="apex-scaffold" 作为应用过滤标签')

scrape_configs = prometheus.get("scrape_configs", []) if isinstance(prometheus, dict) else []
jobs = {job.get("job_name"): job for job in scrape_configs}
expected_jobs = {
    "apex-gateway",
    "apex-auth-service",
    "apex-system-service",
    "apex-workflow-service",
    "apex-office-service",
    "apex-file-service",
    "apex-integration-service",
    "apex-sentinel",
    "apex-scaffold-health",
}
for job in sorted(expected_jobs - set(jobs)):
    errors.append(f"Prometheus 缺少抓取任务 {job}")
if "apex-scaffold" in jobs:
    errors.append("Prometheus 不应再使用聚合任务 apex-scaffold；应按服务拆分 job")

for job_name in sorted(expected_jobs - {"apex-sentinel", "apex-scaffold-health"}):
    job = jobs.get(job_name)
    if not job:
        continue
    labels = []
    for static in job.get("static_configs", []):
        labels.append(static.get("labels") or {})
    if not labels or any(label.get("application") != "apex-scaffold" for label in labels):
        errors.append(f"{job_name} 缺少 application=apex-scaffold 标签")
    if not labels or any(not label.get("module") for label in labels):
        errors.append(f"{job_name} 缺少 module 标签")

sentinel = jobs.get("apex-sentinel") or {}
sentinel_targets = []
for static in sentinel.get("static_configs", []):
    sentinel_targets.extend(static.get("targets", []))
if not sentinel_targets:
    errors.append("apex-sentinel 缺少抓取目标")
for target in sentinel_targets:
    if not str(target).endswith(":9092"):
        errors.append(f"apex-sentinel 目标端口必须为 9092，实际 {target}")

health = jobs.get("apex-scaffold-health") or {}
health_targets = []
for static in health.get("static_configs", []):
    health_targets.extend(static.get("targets", []))
expected_ports = {"8080", "8081", "8083", "8093", "8084", "8085", "8097"}
health_ports = {
    re.search(r":(\d+)/actuator/health", str(target)).group(1)
    for target in health_targets
    if re.search(r":(\d+)/actuator/health", str(target))
}
if health_ports != expected_ports:
    errors.append(f"健康探测端口应为 {sorted(expected_ports)}，实际 {sorted(health_ports)}")

for key in ("route", "receivers"):
    if key not in (alertmanager or {}):
        errors.append(f"Alertmanager 配置缺少 {key}")
receiver_names = {
    receiver.get("name") for receiver in (alertmanager or {}).get("receivers", [])
}
for receiver in {"default", "critical", "warning", "info"}:
    if receiver not in receiver_names:
        errors.append(f"Alertmanager 缺少 receiver {receiver}")

if errors:
    for error in errors:
        print(f"FAIL: {error}", file=sys.stderr)
    raise SystemExit(1)

print(f"PASS: 告警规则 {len(all_rules)} 条 / {len(groups)} 组")
print(f"PASS: Prometheus 抓取任务 {len(jobs)} 个，Sentinel 端口 9092")
print("PASS: Alertmanager 路由与 4 个 receiver 可解析")
PY
    then
        log_success "YAML 静态契约校验通过"
    else
        fail_check "YAML 静态契约校验失败"
    fi
fi

if ! command -v jq >/dev/null 2>&1; then
    skip "未安装 jq，无法校验 Grafana 大盘结构"
else
    if jq -e '
        (.title == "Apex Scaffold 监控大盘") and
        (.uid == "apex-scaffold-dashboard") and
        ((.panels | length) == 96) and
        (([.panels[] | select(.type == "row")] | length) == 12) and
        (([.panels[] | select(.type != "row")] | length) == 84)
    ' "$MAIN_DASHBOARD" >/dev/null; then
        log_success "主大盘结构通过：96 项 / 12 row / 84 数据面板"
    else
        fail_check "主大盘结构不符合 96 项 / 12 row / 84 数据面板"
    fi

    if jq -e '
        (.title == "Apex Scaffold - 增强监控（WebFlux + JVM）") and
        (.uid == "apex-enhanced") and
        ((.panels | length) == 14) and
        (([.panels[] | select(.type == "row")] | length) == 2) and
        (([.panels[] | select(.type != "row")] | length) == 12)
    ' "$ENHANCED_DASHBOARD" >/dev/null; then
        log_success "增强大盘结构通过：14 项 / 2 row / 12 数据面板"
    else
        fail_check "增强大盘结构不符合 14 项 / 2 row / 12 数据面板"
    fi

    if jq -r '.. | objects | select(has("expr")) | .expr' "$MAIN_DASHBOARD" "$ENHANCED_DASHBOARD" \
        | grep -Eq 'jdbc_query_duration_seconds|sentinel_blocked_qps|quantile='; then
        fail_check "Grafana 大盘仍包含退役指标或 GC quantile"
    else
        log_success "Grafana 大盘未包含退役指标与 GC quantile"
    fi

    if jq -r '.. | objects | select(has("expr")) | .expr' "$MAIN_DASHBOARD" \
        | grep -q 'apex_sql_slow_count_total'; then
        log_success "主大盘慢 SQL 面板使用 apex_sql_slow_count_total"
    else
        fail_check "主大盘慢 SQL 面板未使用 apex_sql_slow_count_total"
    fi
fi

if command -v promtool >/dev/null 2>&1; then
    if promtool check rules "$ALERT_RULES"; then
        log_success "promtool check rules 通过"
    else
        fail_check "promtool check rules 失败"
    fi
    if promtool check config "$PROMETHEUS_CONFIG"; then
        log_success "promtool check config 通过"
    else
        fail_check "promtool check config 失败"
    fi
else
    skip "未安装 promtool，未执行 Prometheus 官方语法检查"
fi

if command -v amtool >/dev/null 2>&1; then
    if amtool check-config "$ALERTMANAGER_CONFIG"; then
        log_success "amtool check-config 通过"
    else
        fail_check "amtool check-config 失败"
    fi
else
    skip "未安装 amtool，未执行 Alertmanager 官方语法检查"
fi

echo ""
if [[ $FAILED -gt 0 ]]; then
    log_failure "监控配置验证失败：${FAILED} 项失败，${SKIPPED} 项 SKIP"
    exit 1
fi
if [[ $SKIPPED -gt 0 ]]; then
    log_warn "监控配置静态校验通过，但有 ${SKIPPED} 项 SKIP；不能记为完整验证通过"
    exit 3
fi
log_success "监控配置全部验证通过"
