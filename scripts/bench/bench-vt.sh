#!/usr/bin/env bash
# ============================================================
# Apex Scaffold 虚拟线程压测脚本（阻塞 I/O 池 A/B 对比）
#
# 作用:
#   1) 通过 JVM 参数开启 apex.blocking-io.<pool>.virtual-threads，不修改任何配置文件
#   2) 用 wrk 对同一接口分别压测"平台线程"与"虚拟线程"两种模式
#   3) 采集 apex.blocking.io.* 指标与 JFR 虚拟线程启动事件，确认虚拟线程是否真正生效
#   4) 在 target/bench-vt/<时间戳>/ 输出 summary.md 与 results.tsv 对比吞吐与延迟
#
# 用法:
#   ./scripts/bench/bench-vt.sh                      # 默认 compare：起服务跑两轮对比
#   BENCH_MODE=report  ./scripts/bench/bench-vt.sh   # 只检查已在运行的服务，不启停
#   BENCH_MODE=baseline ./scripts/bench/bench-vt.sh  # 只跑平台线程轮
#   BENCH_MODE=virtual ./scripts/bench/bench-vt.sh   # 只跑虚拟线程轮
#
# 依赖: wrk、curl、java(>=21)、jcmd/jfr(可选，缺失时跳过 JFR 与线程 Dump)
# 前置: MySQL 与 Redis 已就绪，业务端口 / 管理端口未被占用
# 说明: 仅供本机或预发压测；脚本会临时启动 system-service 并在退出时关闭
# ============================================================

set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
# shellcheck source=../lib/common.sh
source "${SCRIPT_DIR}/../lib/common.sh"

PROJECT_ROOT="$(get_project_root)" || {
    log_error "无法定位项目根目录（未找到 pom.xml）"
    exit 1
}

# ---------------- 可配置项（全部支持环境变量覆盖）----------------
BENCH_MODE="${BENCH_MODE:-compare}"
BENCH_BASE_URL="${BENCH_BASE_URL:-http://127.0.0.1:8082}"
BENCH_PATH="${BENCH_PATH:-/api/v1/health}"
BENCH_ADMIN_URL="${BENCH_ADMIN_URL:-http://127.0.0.1:8083}"
BENCH_DURATION="${BENCH_DURATION:-30s}"
BENCH_THREADS="${BENCH_THREADS:-4}"
BENCH_CONNECTIONS="${BENCH_CONNECTIONS:-200}"
BENCH_POOLS="${BENCH_POOLS:-event,cache}"
BENCH_CONCURRENCY="${BENCH_CONCURRENCY:-16}"
BENCH_STARTUP_TIMEOUT="${BENCH_STARTUP_TIMEOUT:-180}"
BENCH_JAVA_OPTS="${BENCH_JAVA_OPTS:-}"
BENCH_TRACE_PINNED="${BENCH_TRACE_PINNED:-false}"
BENCH_SAMPLE_GUARD="${BENCH_SAMPLE_GUARD:-true}"
BENCH_PID="${BENCH_PID:-}"
BENCH_JAR="${BENCH_JAR:-}"
BENCH_OUTPUT_DIR="${BENCH_OUTPUT_DIR:-${PROJECT_ROOT}/target/bench-vt/$(date +%Y%m%d-%H%M%S)}"

SERVICE_PID=""
SERVICE_LOG=""
OUTPUT_DIR="${BENCH_OUTPUT_DIR}"
RESULTS_TSV="${OUTPUT_DIR}/results.tsv"
SUMMARY_MD="${OUTPUT_DIR}/summary.md"

PROPS=()
JAVA_ARGS=()

# ---------------- 工具函数 ----------------

usage() {
    sed -n '2,20p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
}

fail() {
    log_failure "$*"
    exit 1
}

upper() {
    printf '%s' "$1" | tr '[:lower:]' '[:upper:]'
}

duration_to_seconds() {
    local raw="$1"
    case "$raw" in
        *ms) fail "BENCH_DURATION 至少为 1 秒，不支持毫秒：${raw}" ;;
        *s) printf '%s' "${raw%s}" ;;
        *m) printf '%s' $(( ${raw%m} * 60 )) ;;
        *h) printf '%s' $(( ${raw%h} * 3600 )) ;;
        ''|*[!0-9]*) fail "无法解析 BENCH_DURATION：${raw}（示例：30s、1m、90）" ;;
        *) printf '%s' "$raw" ;;
    esac
}

validate_pool() {
    case "$1" in
        jdbc|cache|storage|event|remote|general) ;;
        *) fail "BENCH_POOLS 含非法池名：$1（可选 jdbc/cache/storage/event/remote/general）" ;;
    esac
}

# 单个池的并发上限：BENCH_CONCURRENCY_<POOL> 优先，其次 BENCH_CONCURRENCY
concurrency_for() {
    local pool="$1"
    local override_var="BENCH_CONCURRENCY_$(upper "$pool")"
    local override_value="${!override_var:-}"
    if [[ -n "$override_value" ]]; then
        printf '%s' "$override_value"
    else
        printf '%s' "$BENCH_CONCURRENCY"
    fi
}

collect_pool_props() {
    local mode="$1" pool conc
    local pools
    IFS=',' read -r -a pools <<< "$BENCH_POOLS"
    for pool in "${pools[@]}"; do
        if [[ -z "$pool" ]]; then
            continue
        fi
        validate_pool "$pool"
        conc="$(concurrency_for "$pool")"
        if [[ ! "$conc" =~ ^[1-9][0-9]*$ ]]; then
            fail "并发上限必须是正整数：${pool}=${conc}"
        fi
        if [[ "$mode" == "virtual" ]]; then
            PROPS+=("-Dapex.blocking-io.${pool}.virtual-threads=true")
            PROPS+=("-Dapex.blocking-io.${pool}.max-concurrency=${conc}")
        else
            # 平台线程轮使用相同的并发上限，保证两轮只有"线程模型"不同
            PROPS+=("-Dapex.blocking-io.${pool}.threads=${conc}")
        fi
    done
}

# 压测期间关闭会污染结果的网关级保护（IP 限流 429、Sentinel 系统规则 503）
collect_guard_props() {
    if [[ "$BENCH_SAMPLE_GUARD" == "true" ]]; then
        PROPS+=("-Dapex.security.ip-rate-limit.enabled=false")
        PROPS+=("-Dapex.sentinel.system-rules.enabled=false")
        PROPS+=("-Dapex.security.guard.ua-check-enabled=false")
    fi
}

check_java() {
    require_command java "JDK 21+" || exit 1
    local version
    version="$(java -XshowSettings:properties -version 2>&1 \
        | awk -F'= ' '/java.specification.version/ {print $2; exit}')"
    version="${version#1.}"
    if [[ -z "$version" ]]; then
        fail "无法识别 java.specification.version，请确认 JAVA_HOME 指向 JDK 21+"
    fi
    if (( version < 21 )); then
        fail "虚拟线程需要 JDK 21+，当前为 ${version}；请先执行 sdk env 激活 21.0.13.0+9-tem"
    fi
    log_info "JDK 版本：${version}"
}

resolve_jar() {
    if [[ -n "$BENCH_JAR" ]]; then
        require_file "$BENCH_JAR" "BENCH_JAR" || exit 1
        return 0
    fi
    local candidate
    candidate="$(ls -1t "${PROJECT_ROOT}"/apex-services-bom/apex-system-service/target/apex-system-service-*.jar 2>/dev/null \
        | grep -v '\.original$' | head -n 1 || true)"
    if [[ -z "$candidate" ]]; then
        fail "未找到可执行 JAR，请先执行：mvnd -T4 -B -pl :apex-system-service -am package，或用 BENCH_JAR 指定路径"
    fi
    BENCH_JAR="$candidate"
    log_info "使用 JAR：${BENCH_JAR}"
}

service_reachable() {
    curl -fsS -o /dev/null --max-time 2 "${BENCH_BASE_URL}${BENCH_PATH}" 2>/dev/null
}

wait_ready() {
    local deadline=$(( $(date +%s) + BENCH_STARTUP_TIMEOUT ))
    while :; do
        if ! kill -0 "$SERVICE_PID" 2>/dev/null; then
            log_error "服务进程已退出，最近日志如下"
            tail -n 20 "$SERVICE_LOG" >&2 || true
            return 1
        fi
        if service_reachable; then
            return 0
        fi
        if (( $(date +%s) >= deadline )); then
            log_error "等待服务就绪超时（${BENCH_STARTUP_TIMEOUT}s），最近日志如下"
            tail -n 20 "$SERVICE_LOG" >&2 || true
            return 1
        fi
        sleep 2
    done
}

start_service() {
    local mode="$1" label="$2"
    PROPS=()
    JAVA_ARGS=()
    collect_pool_props "$mode"
    collect_guard_props
    if [[ -n "$BENCH_JAVA_OPTS" ]]; then
        local -a user_opts
        IFS=' ' read -r -a user_opts <<< "$BENCH_JAVA_OPTS"
        if [[ ${#user_opts[@]} -gt 0 ]]; then
            JAVA_ARGS+=("${user_opts[@]}")
        fi
    fi
    if [[ ${#PROPS[@]} -gt 0 ]]; then
        JAVA_ARGS+=("${PROPS[@]}")
    fi
    if [[ "$BENCH_TRACE_PINNED" == "true" ]]; then
        JAVA_ARGS+=("-Djdk.tracePinnedThreads=full")
    fi

    SERVICE_LOG="${OUTPUT_DIR}/service-${label}.log"
    local -a command=("java")
    if [[ ${#JAVA_ARGS[@]} -gt 0 ]]; then
        command+=("${JAVA_ARGS[@]}")
    fi
    command+=(-jar "$BENCH_JAR")

    printf '%s\n' "${command[*]}" > "${OUTPUT_DIR}/jvm-args-${label}.txt"
    log_info "启动服务（${mode}）：${command[*]}"
    "${command[@]}" > "$SERVICE_LOG" 2>&1 &
    SERVICE_PID=$!
    if ! wait_ready; then
        stop_service
        fail "服务未能就绪（mode=${mode}），详见 ${SERVICE_LOG}"
    fi
    log_success "服务就绪（${mode}，pid=${SERVICE_PID}）"
}

stop_service() {
    if [[ -z "$SERVICE_PID" ]]; then
        return 0
    fi
    if kill -0 "$SERVICE_PID" 2>/dev/null; then
        log_info "关闭服务（pid=${SERVICE_PID}）"
        kill -TERM "$SERVICE_PID" 2>/dev/null || true
        local deadline=$(( $(date +%s) + 30 ))
        while kill -0 "$SERVICE_PID" 2>/dev/null && (( $(date +%s) < deadline )); do
            sleep 1
        done
        if kill -0 "$SERVICE_PID" 2>/dev/null; then
            log_warn "优雅停机超时，强制结束"
            kill -KILL "$SERVICE_PID" 2>/dev/null || true
        fi
        wait "$SERVICE_PID" 2>/dev/null || true
    fi
    SERVICE_PID=""
}

sample_prometheus() {
    local label="$1" phase="$2"
    local target="${OUTPUT_DIR}/prom-${label}-${phase}.txt"
    if ! curl -fsS --max-time 5 "${BENCH_ADMIN_URL}/actuator/prometheus" -o "$target" 2>/dev/null; then
        log_warn "无法读取 ${BENCH_ADMIN_URL}/actuator/prometheus（管理端口未开放或地址不对）"
        : > "$target"
    fi
}

# 从 Prometheus 输出中取指定指标与池标签的数值
prom_value() {
    local file="$1" metric="$2" pool="$3" line
    if [[ ! -s "$file" ]]; then
        return 0
    fi
    line="$(grep -E "^${metric}\{[^}]*pool=\"${pool}\"" "$file" | head -n 1 || true)"
    if [[ -z "$line" ]]; then
        return 0
    fi
    local value="${line##* }"
    # 快照尚未生成的 Gauge 会被 Micrometer 渲染为 NaN，按“无数据”处理
    case "$value" in
        ''|NaN|nan|+Inf|-Inf) return 0 ;;
    esac
    printf '%s' "$value"
}

is_number() {
    awk -v v="$1" 'BEGIN { exit (v ~ /^-?[0-9]+(\.[0-9]+)?$/ ? 0 : 1) }'
}

# 平台线程 Dump：Thread.dump_to_file 的文本格式会省略虚拟线程栈，只留数量汇总，
# 因此虚拟线程的证据另由 vt_event_count（JFR）提供。
thread_dump() {
    local label="$1" file="${OUTPUT_DIR}/threads-${label}.txt"
    if ! command -v jcmd >/dev/null 2>&1; then
        log_warn "未找到 jcmd，跳过线程 Dump（虚拟线程证据仅剩指标与 JFR）"
        return 0
    fi
    if [[ -z "$SERVICE_PID" ]] || ! kill -0 "$SERVICE_PID" 2>/dev/null; then
        log_warn "服务进程不存在，跳过线程 Dump"
        return 0
    fi
    if ! jcmd "$SERVICE_PID" Thread.dump_to_file -format=text "$file" >/dev/null 2>&1 \
        || [[ ! -s "$file" ]]; then
        jcmd "$SERVICE_PID" Thread.dump_to_file "$file" >/dev/null 2>&1 || true
    fi
    if [[ -s "$file" ]]; then
        printf '%s' "$file"
    else
        log_warn "线程 Dump 失败，跳过"
    fi
    return 0
}

# 解析 wrk 输出：rps / latency_avg / p50 / p90 / p99 / non2xx（延迟统一换算为毫秒）
parse_wrk() {
    awk '
        function to_ms(tok,   num, unit) {
            num = tok
            sub(/(us|ms|s)$/, "", num)
            unit = substr(tok, length(num) + 1)
            if (unit == "us") return num / 1000
            if (unit == "s") return num * 1000
            return num
        }
        BEGIN { rps = 0; lat = 0; p50 = 0; p90 = 0; p99 = 0; non2xx = 0; in_lat = 0 }
        /Requests\/sec:/ { rps = $2 }
        /Latency / && $2 ~ /^[0-9.]+(us|ms|s)$/ { lat = to_ms($2) }
        # wrk 仅在 --latency 时输出该分节，按分节范围读取百分位，避免误匹配其它表格
        /Latency Distribution/ { in_lat = 1; next }
        in_lat && /^ *[0-9]+%/ { p[$1] = to_ms($2); next }
        in_lat { in_lat = 0 }
        /Non-2xx or 3xx responses:/ { non2xx = $5 }
        END {
            p50 = p["50%"]; p90 = p["90%"]; p99 = p["99%"]
            printf "%s\t%.2f\t%.2f\t%.2f\t%.2f\t%d", rps, lat, p50, p90, p99, non2xx
        }
    ' "$1"
}

# 统计虚拟线程：JFR 的 jdk.VirtualThreadStart 每启动一条虚拟线程记录一次，
# 按事件里记录的线程名匹配池前缀，作为“该池确实在用虚拟线程执行任务”的直接证据。
vt_event_count() {
    local label="$1" pool="$2" from_epoch="$3"
    local file="${OUTPUT_DIR}/vt-start-${label}.jfr"
    if [[ -z "$SERVICE_PID" ]] || [[ ! -s "$file" ]]; then
        printf ''
        return 0
    fi
    jfr print --events jdk.VirtualThreadStart --json "$file" 2>/dev/null \
        | awk -v prefix="apex.${pool}-vt-" -v from="$from_epoch" '
            /"startTime"[ \t]*:/ {
                ts = $0
                sub(/.*"startTime"[ \t]*:[ \t]*"/, "", ts)
                sub(/".*/, "", ts)
                in_win = (ts >= from)
            }
            /"javaName"[ \t]*:/ {
                name = $0
                sub(/.*"javaName"[ \t]*:[ \t]*"/, "", name)
                sub(/".*/, "", name)
                if (in_win && index(name, prefix) == 1) count++
            }
            END { printf "%d", count }
        '
}

record_result() {
    local label="$1" mode="$2" from_epoch="$3" parsed rps lat p50 p90 p99 non2xx
    local mid="${OUTPUT_DIR}/prom-${label}-mid.txt"
    local pre="${OUTPUT_DIR}/prom-${label}-pre.txt"
    parsed="$(parse_wrk "${OUTPUT_DIR}/wrk-${label}.txt")"
    rps="$(printf '%s' "$parsed" | cut -f1)"
    lat="$(printf '%s' "$parsed" | cut -f2)"
    p50="$(printf '%s' "$parsed" | cut -f3)"
    p90="$(printf '%s' "$parsed" | cut -f4)"
    p99="$(printf '%s' "$parsed" | cut -f5)"
    non2xx="$(printf '%s' "$parsed" | cut -f6)"

    local pools pool max_threads queue_capacity saturation completed_pre completed_mid completed_delta vt_threads
    IFS=',' read -r -a pools <<< "$BENCH_POOLS"
    for pool in "${pools[@]}"; do
        if [[ -z "$pool" ]]; then
            continue
        fi
        max_threads="$(prom_value "$mid" "apex_blocking_io_max_threads" "$pool")"
        queue_capacity="$(prom_value "$mid" "apex_blocking_io_queue_capacity" "$pool")"
        saturation="$(prom_value "$mid" "apex_blocking_io_saturation" "$pool")"
        completed_pre="$(prom_value "$pre" "apex_blocking_io_completed_tasks" "$pool")"
        completed_mid="$(prom_value "$mid" "apex_blocking_io_completed_tasks" "$pool")"
        # 平台线程模式的 Reactor 调度器不暴露累计完成数，只在两端都可读时计算增量
        completed_delta="n/a"
        if is_number "$completed_pre" && is_number "$completed_mid"; then
            completed_delta="$(awk -v a="$completed_mid" -v b="$completed_pre" 'BEGIN { printf "%.0f", a - b }')"
        fi
        vt_threads="$(vt_event_count "$label" "$pool" "$from_epoch")"
        printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
            "$mode" "$pool" "$rps" "$lat" "$p50" "$p90" "$p99" "$non2xx" \
            "${max_threads:-n/a}" "${queue_capacity:-n/a}" "${saturation:-n/a}" \
            "$completed_delta" "${vt_threads:-n/a}" >> "$RESULTS_TSV"
    done

    log_info "${mode} 完成：rps=${rps}, avg=${lat}ms, p99=${p99}ms, non2xx=${non2xx}"
}

# 等待池指标首次刷新：服务刚就绪时 Gauge 已注册但数值仍是 NaN，
# 只判断“行存在”会立即返回，导致 completed_tasks 的窗口增量算不出来，
# 因此这里要求至少有一个池的 max_threads 是可解析的数字。
wait_metrics_ready() {
    local deadline=$(( $(date +%s) + 30 )) probe="${OUTPUT_DIR}/prom-probe.txt"
    local pool value ready
    while :; do
        if curl -fsS --max-time 5 "${BENCH_ADMIN_URL}/actuator/prometheus" -o "$probe" 2>/dev/null; then
            ready=false
            for pool in $(printf '%s' "$BENCH_POOLS" | tr ',' ' '); do
                value="$(prom_value "$probe" "apex_blocking_io_max_threads" "$pool")"
                if is_number "$value"; then
                    ready=true
                    break
                fi
            done
            if [[ "$ready" == true ]]; then
                rm -f "$probe"
                return 0
            fi
        fi
        if (( $(date +%s) >= deadline )); then
            rm -f "$probe"
            log_warn "等待阻塞 I/O 池指标超时，相关指标将显示 n/a"
            return 0
        fi
        sleep 1
    done
}

run_round() {
    local mode="$1" label="$2" dur_seconds="$3" sample_delay="$4"

    wait_metrics_ready
    sample_prometheus "$label" pre
    # 每轮单独录一段 JFR：只开虚拟线程启动事件，用来统计压测窗口内该池启动了多少条虚拟线程
    start_vt_recording "$label"
    # JFR 的 startTime 是“本地时间+偏移”（如 2026-10-27T15:23:02.53+08:00），
    # 因此窗口起点也取本地时间，才能按前缀做字符串比较；用 UTC 会整体偏移 8 小时。
    local from_epoch
    from_epoch="$(date '+%Y-%m-%dT%H:%M:%S')"
    log_info "wrk: -t${BENCH_THREADS} -c${BENCH_CONNECTIONS} -d${BENCH_DURATION} --latency ${BENCH_BASE_URL}${BENCH_PATH}"
    wrk -t"${BENCH_THREADS}" -c"${BENCH_CONNECTIONS}" -d"${BENCH_DURATION}" --latency \
        "${BENCH_BASE_URL}${BENCH_PATH}" > "${OUTPUT_DIR}/wrk-${label}.txt" 2>&1 &
    local wrk_pid=$!

    sleep "$sample_delay"
    sample_prometheus "$label" mid
    thread_dump "$label" >/dev/null || true

    if ! wait "$wrk_pid"; then
        log_warn "wrk 退出码非 0，请检查 ${OUTPUT_DIR}/wrk-${label}.txt"
    fi
    stop_vt_recording "$label"
    record_result "$label" "$mode" "$from_epoch"
}

start_vt_recording() {
    local label="$1"
    if [[ -z "$SERVICE_PID" ]] || ! command -v jfr >/dev/null 2>&1; then
        log_warn "缺少 jcmd/jfr 或服务进程，跳过 JFR 录制（虚拟线程证据仅剩指标）"
        return 0
    fi
    : > "${OUTPUT_DIR}/vt-start-${label}.jfr"
    # profile 默认不开启 jdk.VirtualThreadStart，必须显式打开，否则事件数恒为 0
    jcmd "$SERVICE_PID" JFR.start name="vt-${label}" settings=profile \
        jdk.VirtualThreadStart#enabled=true \
        filename="${OUTPUT_DIR}/vt-start-${label}.jfr" >/dev/null 2>&1 || {
        log_warn "JFR 启动失败，跳过虚拟线程启动事件统计"
        return 0
    }
}

stop_vt_recording() {
    local label="$1"
    if [[ -z "$SERVICE_PID" ]] || ! command -v jcmd >/dev/null 2>&1; then
        return 0
    fi
    jcmd "$SERVICE_PID" JFR.stop name="vt-${label}" >/dev/null 2>&1 || true
    # 录制丢失时避免沿用空文件或残留片段
    if [[ -s "${OUTPUT_DIR}/vt-start-${label}.jfr" ]] \
        && command -v jfr >/dev/null 2>&1 \
        && ! jfr summary "${OUTPUT_DIR}/vt-start-${label}.jfr" >/dev/null 2>&1; then
        : > "${OUTPUT_DIR}/vt-start-${label}.jfr"
    fi
}

# ---------------- 报告 ----------------

tsv_value() {
    local mode="$1" field="$2"
    awk -F'\t' -v m="$mode" -v f="$field" '$1 == m { print $f; exit }' "$RESULTS_TSV"
}

write_summary() {
    local dur_seconds="$1" sample_delay="$2"
    {
        echo "# 虚拟线程压测报告"
        echo ""
        echo "- 时间：$(date '+%Y-%m-%d %H:%M:%S %Z')"
        echo "- 目标：\`${BENCH_BASE_URL}${BENCH_PATH}\`"
        echo "- 压测参数：wrk -t${BENCH_THREADS} -c${BENCH_CONNECTIONS} -d${BENCH_DURATION} --latency（采样点 ${sample_delay}s）"
        echo "- 压测池：\`${BENCH_POOLS}\`"
        echo "- 并发上限：基准 \`BENCH_CONCURRENCY=${BENCH_CONCURRENCY}\`，可用 \`BENCH_CONCURRENCY_<POOL>\` 覆盖单个池"
        echo "- 保护开关：\`BENCH_SAMPLE_GUARD=${BENCH_SAMPLE_GUARD}\`（true 时关闭 IP 限流与 Sentinel 系统规则）"
        echo "- JAR：\`${BENCH_JAR}\`"
        echo ""
        echo "## 吞吐与延迟"
        echo ""
        echo "| 模式 | Requests/sec | 平均延迟(ms) | p50(ms) | p90(ms) | p99(ms) | 非 2xx |"
        echo "| --- | --- | --- | --- | --- | --- | --- |"
        local mode
        for mode in baseline virtual; do
            if [[ -z "$(tsv_value "$mode" 3)" ]]; then
                continue
            fi
            printf '| %s | %s | %s | %s | %s | %s | %s |\n' \
                "$mode" "$(tsv_value "$mode" 3)" "$(tsv_value "$mode" 4)" \
                "$(tsv_value "$mode" 5)" "$(tsv_value "$mode" 6)" \
                "$(tsv_value "$mode" 7)" "$(tsv_value "$mode" 8)"
        done
        echo ""
        echo "## 阻塞 I/O 池指标（压测中途采样，$(( dur_seconds / 2 ))s 附近）"
        echo ""
        echo "| 模式 | 池 | max_threads | queue_capacity | saturation | 采样窗口完成数 | 压测窗口内启动的 \`apex.<pool>-vt-*\` 数（JFR） |"
        echo "| --- | --- | --- | --- | --- | --- | --- |"
        awk -F'\t' 'NR > 1 { printf "| %s | %s | %s | %s | %s | %s | %s |\n", $1, $2, $9, $10, $11, $12, $13 }' "$RESULTS_TSV"
        echo ""
        echo "> 「采样窗口完成数」= 压测开始时到采样点的 \`completed_tasks\` 增量；平台线程模式下 Reactor 调度器不暴露该计数，显示 \`n/a\`。"
        echo "> 「启动的虚拟线程数」= 该轮 JFR 记录到的 \`jdk.VirtualThreadStart\` 事件数（仅统计压测开始之后、名称前缀匹配该池的事件）；平台线程模式为 0。"
        echo ""
        echo "## 结论"
        echo ""
        local base_rps vt_rps base_p99 vt_p99 base_non2xx vt_non2xx
        base_rps="$(tsv_value baseline 3)"
        vt_rps="$(tsv_value virtual 3)"
        base_p99="$(tsv_value baseline 7)"
        vt_p99="$(tsv_value virtual 7)"
        base_non2xx="$(tsv_value baseline 8)"
        vt_non2xx="$(tsv_value virtual 8)"
        if [[ -n "$base_rps" && -n "$vt_rps" ]]; then
            awk -v a="$base_rps" -v b="$vt_rps" -v pa="$base_p99" -v pb="$vt_p99" 'BEGIN {
                if (a > 0) printf "- 吞吐：%s → %s req/s（%+.1f%%）\n", a, b, (b - a) * 100 / a
                else printf "- 吞吐：基准轮无有效数据，无法比较\n"
                if (pa > 0) printf "- p99：%s ms → %s ms（%+.1f%%）\n", pa, pb, (pb - pa) * 100 / pa
                else printf "- p99：基准轮无有效数据，无法比较\n"
            }'
        else
            echo "- 未同时具备基准轮与虚拟线程轮数据，无法给出对比结论（单轮结果见上表）"
        fi
        if [[ "${base_non2xx:-0}" != "0" || "${vt_non2xx:-0}" != "0" ]]; then
            echo "- ⚠️ 存在非 2xx 响应（429/503），结果不可用于对比；请确认 \`BENCH_SAMPLE_GUARD=true\` 并复核 IP 限流与 Sentinel 配置"
        fi
        awk -F'\t' 'NR > 1 && $12 == 0 { print "- ⚠️ 池 " $2 " 在采样窗口内没有完成任务，该场景不覆盖此池，结论不适用于它" }' "$RESULTS_TSV"
        echo ""
        echo "## 判定虚拟线程是否生效"
        echo ""
        echo "1. 该池在压测窗口内启动了虚拟线程（\`jdk.VirtualThreadStart\` 事件数 > 0，线程名 \`apex.<pool>-vt-*\`），即该池已切换到虚拟线程执行器；平台线程模式只有 \`apex.<pool>-N\`，事件数为 0。"
        echo "   \`jcmd Thread.dump_to_file -format=text\` 的文本格式不含虚拟线程栈（仅有总数汇总），不能用来数虚拟线程。"
        echo "2. \`apex_blocking_io_max_threads\` 在虚拟线程模式等于配置的 \`max-concurrency\`，平台线程模式等于 \`threads\`。"
        echo "3. 用 \`-Djdk.tracePinnedThreads=full\`（\`BENCH_TRACE_PINNED=true\`）可在服务日志观察 \`synchronized\` 阻塞导致的载体线程钉住。"
        echo ""
        echo "## 产物"
        echo ""
        echo "- \`results.tsv\`：逐池结果明细"
        echo "- \`wrk-<mode>.txt\`：wrk 原始输出"
        echo "- \`prom-<mode>-pre.txt\` / \`prom-<mode>-mid.txt\`：压测前与压测中的 Prometheus 快照"
        echo "- \`vt-start-<mode>.jfr\`：该轮 JFR 录制，含 \`jdk.VirtualThreadStart\` 事件"
        echo "- \`threads-<mode>.txt\`：平台线程 Dump（不含虚拟线程栈）"
        echo "- \`jvm-args-<mode>.txt\` / \`service-<mode>.log\`：启动参数与启动日志"
    } > "$SUMMARY_MD"
}

print_metric_table() {
    local label="$1"
    local mid="${OUTPUT_DIR}/prom-${label}-mid.txt"
    local pools pool max_threads queue_capacity saturation completed vt_started
    printf '%-10s %-8s %-12s %-14s %-12s %-10s\n' "POOL" "MAX" "QUEUE_CAP" "SATURATION" "COMPLETED" "VT_STARTED"
    IFS=',' read -r -a pools <<< "$BENCH_POOLS"
    for pool in "${pools[@]}"; do
        if [[ -z "$pool" ]]; then
            continue
        fi
        max_threads="$(prom_value "$mid" "apex_blocking_io_max_threads" "$pool")"
        queue_capacity="$(prom_value "$mid" "apex_blocking_io_queue_capacity" "$pool")"
        saturation="$(prom_value "$mid" "apex_blocking_io_saturation" "$pool")"
        completed="$(prom_value "$mid" "apex_blocking_io_completed_tasks" "$pool")"
        # report 模式不压测，只统计进程启动以来该池累计启动的虚拟线程数
        vt_started="$(vt_event_count "$label" "$pool" "1970-01-01T00:00:00")"
        printf '%-10s %-8s %-12s %-14s %-12s %-10s\n' \
            "$pool" "${max_threads:-n/a}" "${queue_capacity:-n/a}" \
            "${saturation:-n/a}" "${completed:-n/a}" "${vt_started:-n/a}"
    done
}

run_report_mode() {
    mkdir -p "$OUTPUT_DIR"
    print_header "虚拟线程状态检查（report 模式，不启停服务）"
    if ! service_reachable; then
        fail "服务不可达：${BENCH_BASE_URL}${BENCH_PATH}，请先启动服务或改用 BENCH_MODE=compare"
    fi
    log_success "服务可达：${BENCH_BASE_URL}${BENCH_PATH}"
    sample_prometheus "report" mid
    if [[ -n "$BENCH_PID" ]]; then
        SERVICE_PID="$BENCH_PID"
        start_vt_recording "report"
        sleep 1
        stop_vt_recording "report"
        thread_dump "report" >/dev/null || true
    fi
    print_metric_table "report"
    echo ""
    log_info "若 max_threads 等于配置的 max-concurrency 且 VT_STARTED 持续增长，则该池已启用虚拟线程"
    log_info "开启方式：-Dapex.blocking-io.<pool>.virtual-threads=true -Dapex.blocking-io.<pool>.max-concurrency=<n>"
    log_info "产物目录：${OUTPUT_DIR}"
}

run_compare_mode() {
    local dur_seconds="$1" sample_delay="$2" mode
    local -a modes=("baseline" "virtual")
    if [[ "$BENCH_MODE" == "baseline" || "$BENCH_MODE" == "virtual" ]]; then
        modes=("$BENCH_MODE")
    fi

    for mode in "${modes[@]}"; do
        print_section "运行 ${mode} 轮"
        if service_reachable; then
            fail "${BENCH_BASE_URL} 已有服务在监听；请先停止，或用 BENCH_MODE=report 检查既有实例"
        fi
        start_service "$mode" "$mode"
        run_round "$mode" "$mode" "$dur_seconds" "$sample_delay"
        stop_service
    done

    if [[ ${#modes[@]} -gt 1 ]]; then
        write_summary "$dur_seconds" "$sample_delay"
        print_header "压测完成"
        cat "$SUMMARY_MD"
        log_success "汇总报告：${SUMMARY_MD}"
        log_success "明细数据：${RESULTS_TSV}"
    else
        log_success "单轮完成，明细：${RESULTS_TSV}"
    fi
}

main() {
    case "${1:-}" in
        -h|--help) usage; exit 0 ;;
    esac

    case "$BENCH_MODE" in
        report|baseline|virtual|compare) ;;
        *) fail "BENCH_MODE 只能是 report、baseline、virtual 或 compare，当前：${BENCH_MODE}" ;;
    esac

    if [[ "$BENCH_MODE" == "report" ]]; then
        run_report_mode
        return 0
    fi

    require_command wrk "wrk" || exit 1
    require_command curl "curl" || exit 1
    check_java
    resolve_jar
    mkdir -p "$OUTPUT_DIR"
    printf 'mode\tpool\trps\tlatency_avg_ms\tp50_ms\tp90_ms\tp99_ms\tnon2xx\tmax_threads\tqueue_capacity\tsaturation\tcompleted_in_window\tvt_threads\n' \
        > "$RESULTS_TSV"

    local dur_seconds sample_delay
    dur_seconds="$(duration_to_seconds "$BENCH_DURATION")"
    if (( dur_seconds < 10 )); then
        fail "BENCH_DURATION 至少 10 秒，否则采样点与结果都不稳定（当前 ${dur_seconds}s）"
    fi
    sample_delay=$(( dur_seconds / 2 ))
    if (( sample_delay < 5 )); then
        sample_delay=5
    fi

    print_header "虚拟线程压测（mode=${BENCH_MODE}）"
    log_info "输出目录：${OUTPUT_DIR}"
    trap 'stop_service' EXIT INT TERM

    run_compare_mode "$dur_seconds" "$sample_delay"
}

main "$@"
