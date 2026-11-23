#!/bin/bash

set -u

###############################################################################
# check-springdoc-annotations-v2.sh - SpringDoc注解检查脚本（使用公共库重构版）
#
# 功能：检查 SpringDoc/Swagger 注解完整性
# 版本：v2.0.0（使用公共函数库）
#
# 使用方法：
#   ./check-springdoc-annotations-v2.sh
#   ./check-springdoc-annotations-v2.sh --verbose
#   ./check-springdoc-annotations-v2.sh --report report.txt
#
# 作者：Apex Scaffold Team
# 日期：2026-09-20
###############################################################################

# 加载公共函数库
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/../../lib/common.sh"

# 配置参数
VERBOSE=0
REPORT_FILE=""

# 统计变量
ROUTER_TOTAL=0
ROUTER_WITH_ANNOTATION=0
HANDLER_TOTAL=0
HANDLER_METHOD_TOTAL=0
HANDLER_METHOD_WITH_OPERATION=0
DTO_TOTAL=0
DTO_WITH_SCHEMA=0
SCHEMA_ANNOTATION_COUNT=0
WRITE_ENDPOINT_TOTAL=0
WRITE_ENDPOINT_WITH_BODY=0

###############################################################################
# 参数解析
###############################################################################

parse_arguments() {
    while [[ $# -gt 0 ]]; do
        case $1 in
            --verbose|-v)
                VERBOSE=1
                shift
                ;;
            --report|-r)
                if [[ $# -lt 2 || -z ${2:-} || $2 == -* ]]; then
                    log_error "$1 需要报告文件路径"
                    exit 1
                fi
                REPORT_FILE="$2"
                shift 2
                ;;
            --help|-h)
                show_usage
                exit 0
                ;;
            *)
                log_error "未知参数: $1"
                show_usage
                exit 1
                ;;
        esac
    done
}

show_usage() {
    cat << EOF
使用方法: $0 [选项]

选项:
    --verbose, -v       显示详细信息
    --report, -r FILE   将报告输出到指定文件
    --help, -h          显示此帮助信息

示例:
    $0
    $0 --verbose
    $0 --report /tmp/springdoc-report.txt
EOF
}

###############################################################################
# 报告输出函数
###############################################################################

log_to_report() {
    if [ -n "$REPORT_FILE" ]; then
        echo "$1" >> "$REPORT_FILE"
    fi
}

###############################################################################
# 剥离 Java 源码中的块注释与行注释（用于统计真实方法声明，避免 JavaDoc
# 代码示例中的 public Mono/Flux 被误计为端点方法）
###############################################################################

strip_java_comments() {
    awk '
    {
        line = $0
        out = ""
        while (1) {
            if (depth == 0) {
                i = index(line, "/*")
                if (i == 0) { out = out line; break }
                out = out substr(line, 1, i - 1)
                rest = substr(line, i + 2)
                j = index(rest, "*/")
                if (j > 0) { line = substr(rest, j + 2) } else { depth = 1; break }
            } else {
                j = index(line, "*/")
                if (j == 0) { break }
                line = substr(line, j + 2)
                depth = 0
            }
        }
        sub(/\/\/.*/, "", out)
        print out
    }' "$1" 2>/dev/null
}

###############################################################################
# 被扫描的模块根目录（相对仓库根）。新增含 HTTP 函数式端点或对外 DTO 的模块时
# 在此登记，覆盖率统计随之覆盖，避免像旧版那样只扫 apex-system 而漏掉其余模块。
###############################################################################
MODULE_ROOTS=(
    "apex-components-parent/apex-system"
    "apex-components-parent/apex-integration"
    "apex-services-bom/apex-auth-service"
    "apex-services-bom/apex-file-service"
    "apex-services-bom/apex-office-service"
    "apex-services-bom/apex-workflow-service"
    "apex-services-bom/apex-knowledge-service"
    "apex-services-bom/apex-masking-service"
    "apex-services-bom/apex-aggregation-service"
    "apex-applications-parent/apex-example"
    "apex-business-parent/apex-knowledge"
)

###############################################################################
# 豁免清单：非端点基础设施类，显式登记而非隐式跳过（便于审计与复核）。
#   格式：<基类名>|<原因>
###############################################################################
EXEMPT_ROUTERS=(
    "MethodNotAllowedRouter|405 兜底路由，不是业务 API"
)
EXEMPT_HANDLERS=(
    "BaseHandler|抽象基类，只提供请求体校验等模板方法"
    "MultipartRequestParser|multipart 解析工具，不是端点"
    "OrgUserResolveAdapter|内部适配器，不是端点"
    "FileAuditFieldFillHandler|MyBatis 审计字段填充器"
    "AuditFieldFillHandler|MyBatis 审计字段填充器"
    "OidcAuthenticationSuccessHandler|Spring Security 认证成功处理器"
    "GlobalExceptionHandler|全局异常边界（BE-004 例外）"
    "SentinelExceptionHandler|Sentinel 异常边界（BE-004 例外）"
    "GatewayExceptionHandler|网关异常边界"
    "FallbackHandler|网关降级处理器"
    "WorkflowTimeoutHandler|调度任务处理器"
    "UserMessageDlqHandler|MQ 死信处理器"
)

###############################################################################
# 判断基类名是否在给定豁免清单中
#   $1 基类名；$2 清单项（<名>|<原因>）；命中返回 0
###############################################################################
is_exempt() {
    local name="$1"
    shift
    local entry
    for entry in "$@"; do
        [[ "${entry%%|*}" == "$name" ]] && return 0
    done
    return 1
}

###############################################################################
# 检查 Router 类的 @RouterOperations 注解
###############################################################################

check_router_annotations() {
    print_header "1. 检查 Router 类 @RouterOperations 注解"
    log_to_report "========================================"
    log_to_report "1. Router 类 @RouterOperations 注解检查"
    log_to_report "========================================"

    local module_root router_files router_file router_name found_any=0
    for module_root in "${MODULE_ROOTS[@]}"; do
        local scan_dir="${PROJECT_ROOT}/${module_root}/src/main/java"
        [ -d "$scan_dir" ] || continue
        if ! router_files=$(safe_find "$scan_dir" "*Router.java"); then
            local find_status=$?
            return "$find_status"
        fi
        [ -n "$router_files" ] && found_any=1

        while IFS= read -r router_file; do
            [[ -n "$router_file" ]] || continue
            router_name=$(basename "$router_file" .java)

            if is_exempt "$router_name" "${EXEMPT_ROUTERS[@]}"; then
                [ $VERBOSE -eq 1 ] && log_debug "注册豁免: $router_name"
                continue
            fi
            ROUTER_TOTAL=$((ROUTER_TOTAL + 1))

            if grep -q "@RouterOperations\|@RouterOperation" "$router_file" 2>/dev/null; then
                ROUTER_WITH_ANNOTATION=$((ROUTER_WITH_ANNOTATION + 1))
                log_success "$router_name"
                log_to_report "[✓] $router_name"

                if [ $VERBOSE -eq 1 ]; then
                    local op_count
                    if op_count=$(safe_count "@RouterOperation(" "$router_file"); then
                        log_info "  包含 $op_count 个路由操作"
                    else
                        local count_status=$?
                        return "$count_status"
                    fi
                fi
            else
                log_failure "$router_name 缺少 @RouterOperations 注解（$module_root）"
                log_to_report "[✗] $router_name - 缺少注解"
            fi
        done <<< "$router_files"
    done

    if [ "$found_any" -eq 0 ]; then
        log_error "未在已登记模块中找到任何 Router 类"
        return 1
    fi

    echo ""
    log_info "Router 类统计: 总数=$ROUTER_TOTAL, 已注解=$ROUTER_WITH_ANNOTATION, 缺失=$((ROUTER_TOTAL - ROUTER_WITH_ANNOTATION))"
    log_to_report ""
    log_to_report "统计: 总数=$ROUTER_TOTAL, 已注解=$ROUTER_WITH_ANNOTATION"
    echo ""
}

###############################################################################
# 检查 Handler 方法的 @Operation 注解
###############################################################################

check_handler_annotations() {
    print_header "2. 检查 Handler 方法 @Operation 注解"
    log_to_report ""
    log_to_report "========================================"
    log_to_report "2. Handler 方法 @Operation 注解检查"
    log_to_report "========================================"

    local module_root handler_files handler_file handler_name found_any=0
    for module_root in "${MODULE_ROOTS[@]}"; do
        local scan_dir="${PROJECT_ROOT}/${module_root}/src/main/java"
        [ -d "$scan_dir" ] || continue
        if ! handler_files=$(safe_find "$scan_dir" "*Handler.java"); then
            local find_status=$?
            return "$find_status"
        fi
        [ -n "$handler_files" ] && found_any=1

        while IFS= read -r handler_file; do
            [[ -n "$handler_file" ]] || continue
            handler_name=$(basename "$handler_file" .java)

            if is_exempt "$handler_name" "${EXEMPT_HANDLERS[@]}"; then
                [ $VERBOSE -eq 1 ] && log_debug "注册豁免: $handler_name"
                continue
            fi
            HANDLER_TOTAL=$((HANDLER_TOTAL + 1))

            # 剥离注释后统计真实端点方法（排除 JavaDoc 代码示例误计），
            # 并排除 Sentinel BlockException 兜底方法（非 REST 端点）
            local method_count
            method_count=$(strip_java_comments "$handler_file" | grep -cE '^[[:space:]]*public[[:space:]]+(Mono|Flux)' || true)
            local excluded_count
            excluded_count=$(strip_java_comments "$handler_file" | grep -cE '^[[:space:]]*public[[:space:]]+(Mono|Flux).*BlockException' || true)
            method_count=$(printf '%s' "$method_count" | tr -d '[:space:]')
            excluded_count=$(printf '%s' "$excluded_count" | tr -d '[:space:]')
            [ -z "$method_count" ] && method_count=0
            [ -z "$excluded_count" ] && excluded_count=0
            method_count=$((method_count - excluded_count))
            HANDLER_METHOD_TOTAL=$((HANDLER_METHOD_TOTAL + method_count))

            local operation_count
            operation_count=$(strip_java_comments "$handler_file" | grep -c "@Operation(" || true)
            operation_count=$(printf '%s' "$operation_count" | tr -d '[:space:]')
            [ -z "$operation_count" ] && operation_count=0
            if [ "$operation_count" -gt "$method_count" ]; then
                operation_count=$method_count
            fi
            HANDLER_METHOD_WITH_OPERATION=$((HANDLER_METHOD_WITH_OPERATION + operation_count))
            local missing=$((method_count - operation_count))

            if safe_eq "$missing" 0; then
                log_success "$handler_name (方法:$method_count, @Operation:$operation_count)"
                log_to_report "[✓] $handler_name - 方法:$method_count, 注解:$operation_count"
            else
                log_warn "$handler_name (方法:$method_count, @Operation:$operation_count, 缺失:$missing)"
                log_to_report "[⚠] $handler_name - 方法:$method_count, @Operation:$operation_count, 缺失:$missing"
            fi
        done <<< "$handler_files"
    done

    if [ "$found_any" -eq 0 ]; then
        log_error "未在已登记模块中找到任何 Handler 类"
        return 1
    fi

    echo ""
    log_info "Handler 统计: 类数=$HANDLER_TOTAL, 方法总数=$HANDLER_METHOD_TOTAL"
    log_info "             @Operation:$HANDLER_METHOD_WITH_OPERATION, 缺失:$((HANDLER_METHOD_TOTAL - HANDLER_METHOD_WITH_OPERATION))"
    log_to_report ""
    log_to_report "统计: 类数=$HANDLER_TOTAL, 方法总数=$HANDLER_METHOD_TOTAL, 已注解=$HANDLER_METHOD_WITH_OPERATION"
    echo ""
}

###############################################################################
# 检查 DTO 的 @Schema 注解
###############################################################################

check_dto_schema_annotations() {
    print_header "3. 检查 DTO @Schema 注解"
    log_to_report ""
    log_to_report "========================================"
    log_to_report "3. DTO @Schema 注解检查"
    log_to_report "========================================"

    local module_root dto_dir found_any=0
    for module_root in "${MODULE_ROOTS[@]}"; do
        local java_root="${PROJECT_ROOT}/${module_root}/src/main/java"
        [ -d "$java_root" ] || continue

        # 自动发现模块内全部 dto 目录（任意层级的 */dto），避免硬编码路径随重构失效
        local dto_dirs
        dto_dirs=$(find "$java_root" -type d -name dto 2>/dev/null | sort)
        [ -n "$dto_dirs" ] || continue
        found_any=1

        while IFS= read -r dto_dir; do
            [[ -n "$dto_dir" ]] || continue

            local module_name
            module_name=$(echo "$dto_dir" | sed -e "s#${PROJECT_ROOT}/##" -e 's#/src/main/java.*##')
            log_info "检查模块: $module_name (${dto_dir#"${PROJECT_ROOT}"/})"
            log_to_report ""
            log_to_report "模块: $module_name (${dto_dir#"${PROJECT_ROOT}"/})"

            local dto_files dto_candidates
            if dto_candidates=$(safe_find_custom "$dto_dir" -name "*.java"); then
                :
            else
                local find_status=$?
                return "$find_status"
            fi
            dto_files=$(printf '%s\n' "$dto_candidates" | grep -v "PO.java" | grep -v "Entity.java" | grep -v "DO.java" || true)
            [ -n "$dto_files" ] || continue

            local module_dto_count=0
            local module_with_schema=0
            local module_schema_count=0

            while IFS= read -r dto_file; do
                [[ -n "$dto_file" ]] || continue
                local dto_name
                dto_name=$(basename "$dto_file" .java)
                module_dto_count=$((module_dto_count + 1))
                DTO_TOTAL=$((DTO_TOTAL + 1))

                local schema_count
                schema_count=$(safe_count "@Schema" "$dto_file")

                if safe_gt "$schema_count" 0; then
                    module_with_schema=$((module_with_schema + 1))
                    module_schema_count=$((module_schema_count + schema_count))
                    DTO_WITH_SCHEMA=$((DTO_WITH_SCHEMA + 1))
                    SCHEMA_ANNOTATION_COUNT=$((SCHEMA_ANNOTATION_COUNT + schema_count))

                    [ $VERBOSE -eq 1 ] && log_success "$dto_name (@Schema:$schema_count)"
                else
                    log_warn "$dto_name 没有 @Schema 注解"
                    log_to_report "[⚠] $dto_name - 缺少 @Schema 注解"
                fi
            done <<< "$dto_files"

            log_info "$module_name: DTO=$module_dto_count, 有注解=$module_with_schema, @Schema总数=$module_schema_count"
            log_to_report "  统计: DTO=$module_dto_count, 有注解=$module_with_schema, @Schema总数=$module_schema_count"
            echo ""
        done <<< "$dto_dirs"
    done

    if [ "$found_any" -eq 0 ]; then
        log_warn "未在已登记模块中发现任何 dto 目录"
    fi

    log_info "DTO 总统计: 总数=$DTO_TOTAL, 有@Schema=$DTO_WITH_SCHEMA, 缺失=$((DTO_TOTAL - DTO_WITH_SCHEMA))"
    log_info "           @Schema注解总数=$SCHEMA_ANNOTATION_COUNT"
    log_to_report ""
    log_to_report "总统计: DTO总数=$DTO_TOTAL, 有注解=$DTO_WITH_SCHEMA, @Schema总数=$SCHEMA_ANNOTATION_COUNT"
    echo ""
}

###############################################################################
# 检查写操作（POST/PUT/PATCH）是否声明 @RequestBody
#
# 函数式端点下 SpringDoc 无法从 Handler 签名推断请求体，必须在方法上显式声明
# @RequestBody 才能让在线文档出现完整请求体。本项按 Router 实际注册的写路由
# 反查对应 Handler 方法，逐条核验。
###############################################################################

check_write_request_body_annotations() {
    print_header "4. 检查写操作 @RequestBody 声明"
    log_to_report ""
    log_to_report "========================================"
    log_to_report "4. 写操作 @RequestBody 声明检查"
    log_to_report "========================================"

    local module_root
    for module_root in "${MODULE_ROOTS[@]}"; do
        local scan_dir="${PROJECT_ROOT}/${module_root}/src/main/java"
        [ -d "$scan_dir" ] || continue

        local router_files
        if ! router_files=$(safe_find "$scan_dir" "*Router.java"); then
            local find_status=$?
            return "$find_status"
        fi
        [ -n "$router_files" ] || continue

        local router_file
        while IFS= read -r router_file; do
            [[ -n "$router_file" ]] || continue

            # 提取 beanClass/beanMethod 绑定：形如 beanClass = XxxHandler.class 与 beanMethod = "method"
            # 同一 Router 内按出现顺序配对，用于定位写操作对应的 Handler 方法。
            local pairs
            pairs=$(awk '
                /beanClass[[:space:]]*=[[:space:]]*[A-Za-z0-9_.]+\.class/ {
                    if (match($0, /[A-Za-z0-9_.]+\.class/)) {
                        cls = substr($0, RSTART, RLENGTH - 6)
                        sub(/.*\./, "", cls)
                        pending = cls
                    }
                }
                match($0, /method[[:space:]]*=[[:space:]]*RequestMethod\.[A-Z]+/) {
                    v = substr($0, RSTART, RLENGTH)
                    gsub(/.*RequestMethod\./, "", v)
                    verb = v
                }
                match($0, /beanMethod[[:space:]]*=[[:space:]]*"[A-Za-z0-9_]+"/) {
                    m = substr($0, RSTART, RLENGTH)
                    gsub(/.*beanMethod[[:space:]]*=[[:space:]]*"/, "", m)
                    gsub(/"/, "", m)
                    if (pending != "" && m != "") { print pending "|" m "|" verb; pending = ""; verb = "" }
                }
            ' "$router_file")

            # 写操作路由：同一 Router 内 POST/PUT/PATCH 的数量，用于判定该 Router 是否有写端点
            local write_routes
            write_routes=$(grep -cE 'RequestMethod\.(POST|PUT|PATCH)' "$router_file" || true)
            write_routes=$(printf '%s' "$write_routes" | tr -d '[:space:]')
            [ -z "$write_routes" ] && write_routes=0

            # 该 Router 声明的写操作方法集合（按 beanMethod 出现顺序取前 write_routes 个并不准确，
            # 因此改为：只要方法在 Handler 中存在 @RequestBody 即视为已声明，逐方法精确核验）
            [ -n "$pairs" ] || continue

            local pair handler_cls handler_method handler_verb
            while IFS= read -r pair; do
                [[ -n "$pair" ]] || continue
                handler_cls="${pair%%|*}"
                local rest="${pair#*|}"
                handler_method="${rest%%|*}"
                handler_verb="${rest##*|}"

                # 仅校验写操作（POST/PUT/PATCH）；GET/DELETE 按契约不应带请求体
                case "$handler_verb" in
                    POST|PUT|PATCH) ;;
                    *) continue ;;
                esac

                local handler_file
                handler_file=$(find "$scan_dir" -name "${handler_cls}.java" 2>/dev/null | head -1)
                [ -n "$handler_file" ] || continue

                if ! grep -qE "beanMethod[[:space:]]*=[[:space:]]*\"${handler_method}\"" "$router_file"; then
                    continue
                fi

                # 取出方法体前的注解块，检查是否含 @RequestBody
                # 注解块 = 上一个端点方法声明之后到本方法声明之间的区间；
                # 该区间只含 Javadoc 与注解，因此可精确判定本方法是否声明了 @RequestBody。
                local has_body
                has_body=$(awk -v m="${handler_method}" '
                    { lines[NR] = $0 }
                    END {
                        decl = 0
                        prev = 0
                        for (i = 1; i <= NR; i++) {
                            if (lines[i] ~ /^[[:space:]]*public[[:space:]]+(Mono|Flux)/) {
                                if (lines[i] ~ ("[[:space:]]" m "\\(")) { decl = i; break }
                                prev = i
                            }
                        }
                        if (decl == 0) { print "SKIP"; exit }
                        block = ""
                        for (j = prev + 1; j < decl; j++) block = block "\n" lines[j]
                        # 仅匹配注解形式 @RequestBody( ；允许全限定写法
                        # （@io.swagger.v3.oas.annotations.parameters.RequestBody），
                        # 避免 Javadoc/注释中的文字提及造成误判
                        if (block ~ /@([A-Za-z0-9_.]+\.)?RequestBody[[:space:]]*\(/) print "YES"; else print "NO"
                    }
                ' "$handler_file")

                [ "$has_body" = "SKIP" ] && continue

                # 该 Handler 方法是否真的读取请求体（bodyToMono / handleRequestBody /
                # bodyToFlux / multipartData）。只处理路径参数或查询参数的写端点不声明
                # 请求体是正确的（GET/DELETE 同理），不应计入缺失。
                local reads_body
                reads_body=$(awk -v m="${handler_method}" '
                    { lines[NR] = $0 }
                    END {
                        decl = 0
                        for (i = 1; i <= NR; i++) {
                            if (lines[i] ~ /^[[:space:]]*public[[:space:]]+(Mono|Flux)/) {
                                if (lines[i] ~ ("[[:space:]]" m "\\(")) { decl = i; break }
                            }
                        }
                        if (decl == 0) { print "NO"; exit }
                        # 从方法声明扫到下一个方法声明，限定在该方法体内
                        ended = NR
                        for (i = decl + 1; i <= NR; i++) {
                            if (lines[i] ~ /^[[:space:]]*public[[:space:]]+(Mono|Flux)/) { ended = i; break }
                        }
                        body = ""
                        for (i = decl; i < ended; i++) body = body "\n" lines[i]
                        if (body ~ /bodyToMono|handleRequestBody|bodyToFlux|multipartData|readBody/) print "YES"; else print "NO"
                    }
                ' "$handler_file")

                [ "$reads_body" = "NO" ] && continue

                # 该方法的 operationId 若已由 apex-system 的集中式
                # OpenApiDocumentationCustomizer.JSON_REQUEST_TYPES 声明请求体，
                # 则在线文档同样具备完整请求体，不算缺失（该模块采用集中维护策略）。
                local customizer="${PROJECT_ROOT}/apex-components-parent/apex-system/src/main/java/com/apex/scaffold/interfaces/config/OpenApiDocumentationCustomizer.java"
                if [ -f "$customizer" ] && [ "$has_body" = "NO" ]; then
                    local op_id
                    op_id=$(awk -v m="${handler_method}" '
                        { lines[NR] = $0 }
                        END {
                            decl = 0
                            for (i = 1; i <= NR; i++) {
                                if (lines[i] ~ /^[[:space:]]*public[[:space:]]+(Mono|Flux)/) {
                                    if (lines[i] ~ ("[[:space:]]" m "\\(")) { decl = i; break }
                                }
                            }
                            if (decl == 0) { print ""; exit }
                            for (j = decl - 1; j >= 1; j--) {
                                if (lines[j] ~ /^[[:space:]]*$/) continue
                                if (lines[j] ~ /operationId[[:space:]]*=[[:space:]]*"[A-Za-z0-9_]+"/) {
                                    v = lines[j]
                                    if (match(v, /operationId[[:space:]]*=[[:space:]]*"[A-Za-z0-9_]+"/)) {
                                        s = substr(v, RSTART, RLENGTH)
                                        gsub(/.*operationId[[:space:]]*=[[:space:]]*"/, "", s)
                                        gsub(/".*/, "", s)
                                        print s
                                        exit
                                    }
                                }
                                if (lines[j] ~ /^[[:space:]]*public[[:space:]]+(Mono|Flux)/) break
                            }
                            print ""
                        }
                    ' "$handler_file")
                    if [ -n "$op_id" ] && grep -q "\"${op_id}\"" "$customizer"; then
                        WRITE_ENDPOINT_TOTAL=$((WRITE_ENDPOINT_TOTAL + 1))
                        WRITE_ENDPOINT_WITH_BODY=$((WRITE_ENDPOINT_WITH_BODY + 1))
                        [ $VERBOSE -eq 1 ] && log_success "${handler_cls}.${handler_method} 请求体由集中式 Customizer 声明"
                        continue
                    fi
                fi

                WRITE_ENDPOINT_TOTAL=$((WRITE_ENDPOINT_TOTAL + 1))
                if [ "$has_body" = "YES" ]; then
                    WRITE_ENDPOINT_WITH_BODY=$((WRITE_ENDPOINT_WITH_BODY + 1))
                    [ $VERBOSE -eq 1 ] && log_success "${handler_cls}.${handler_method} 已声明 @RequestBody"
                else
                    log_warn "${handler_cls}.${handler_method} 读取请求体但未声明 @RequestBody（${module_root}）"
                    log_to_report "[⚠] ${handler_cls}.${handler_method} - 读取请求体但未声明 @RequestBody"
                fi
            done <<< "$pairs"
        done <<< "$router_files"
    done

    echo ""
    log_info "写操作统计: 绑定端点=$WRITE_ENDPOINT_TOTAL, 已声明 @RequestBody=$WRITE_ENDPOINT_WITH_BODY, 缺失=$((WRITE_ENDPOINT_TOTAL - WRITE_ENDPOINT_WITH_BODY))"
    log_to_report ""
    log_to_report "写操作统计: 绑定端点=$WRITE_ENDPOINT_TOTAL, 已声明@RequestBody=$WRITE_ENDPOINT_WITH_BODY"
    echo ""
}

###############################################################################
# 生成最终报告
###############################################################################

generate_summary() {
    print_header "检查总结"
    log_to_report ""
    log_to_report "========================================"
    log_to_report "检查总结"
    log_to_report "========================================"

    echo ""
    echo "┌─────────────────────────────────────────────┐"
    echo "│          SpringDoc 注解完整性报告            │"
    echo "├─────────────────────────────────────────────┤"

    # 使用公共函数库的 print_stats
    printf "│ %-20s │ %8s │ %8s │\n" "项目" "总数" "已注解"
    echo "├─────────────────────────────────────────────┤"
    print_stats "Router 类" "$ROUTER_TOTAL" "$ROUTER_WITH_ANNOTATION" | sed 's/^/│ /' | sed 's/$/│/'
    print_stats "Handler 方法" "$HANDLER_METHOD_TOTAL" "$HANDLER_METHOD_WITH_OPERATION" | sed 's/^/│ /' | sed 's/$/│/'
    print_stats "DTO 类" "$DTO_TOTAL" "$DTO_WITH_SCHEMA" | sed 's/^/│ /' | sed 's/$/│/'
    print_stats "写操作 @RequestBody" "$WRITE_ENDPOINT_TOTAL" "$WRITE_ENDPOINT_WITH_BODY" | sed 's/^/│ /' | sed 's/$/│/'
    echo "├─────────────────────────────────────────────┤"
    printf "│ %-20s │ %18d │\n" "@Schema 注解总数" "$SCHEMA_ANNOTATION_COUNT"
    echo "└─────────────────────────────────────────────┘"
    echo ""

    # 使用公共函数库的 calculate_percentage
    echo "覆盖率统计:"
    local router_coverage=$(calculate_percentage "$ROUTER_WITH_ANNOTATION" "$ROUTER_TOTAL")
    local handler_coverage=$(calculate_percentage "$HANDLER_METHOD_WITH_OPERATION" "$HANDLER_METHOD_TOTAL")
    local dto_coverage=$(calculate_percentage "$DTO_WITH_SCHEMA" "$DTO_TOTAL")
    local write_body_coverage=$(calculate_percentage "$WRITE_ENDPOINT_WITH_BODY" "$WRITE_ENDPOINT_TOTAL")
    echo "  Router @RouterOperations: ${router_coverage}%"
    echo "  Handler @Operation: ${handler_coverage}%"
    echo "  DTO @Schema: ${dto_coverage}%"
    echo "  写操作 @RequestBody: ${write_body_coverage}%"
    echo ""
    log_to_report "覆盖率:"
    log_to_report "  Router @RouterOperations: ${router_coverage}%"
    log_to_report "  Handler @Operation: ${handler_coverage}%"
    log_to_report "  DTO @Schema: ${dto_coverage}%"
    log_to_report "  写操作 @RequestBody: ${write_body_coverage}%"

    # 判断是否通过
    local router_missing=$((ROUTER_TOTAL - ROUTER_WITH_ANNOTATION))
    local handler_missing=$((HANDLER_METHOD_TOTAL - HANDLER_METHOD_WITH_OPERATION))
    local dto_missing=$((DTO_TOTAL - DTO_WITH_SCHEMA))

    if safe_eq "$router_missing" 0 && safe_eq "$handler_missing" 0 && safe_lt "$dto_missing" 5; then
        log_success "✓ SpringDoc 注解检查通过！"
        return 0
    else
        log_failure "✗ SpringDoc 注解检查未通过，发现缺失注解"
        return 1
    fi
}

###############################################################################
# 主函数
###############################################################################

main() {
    parse_arguments "$@"

    # 获取项目根目录
    PROJECT_ROOT=$(get_project_root) || exit 1
    cd "$PROJECT_ROOT"

    echo ""
    print_header "SpringDoc/Swagger 注解完整性检查"
    echo "项目路径: $PROJECT_ROOT"
    echo "检查时间: $(date '+%Y-%m-%d %H:%M:%S')"
    echo ""

    # 初始化报告文件
    if [ -n "$REPORT_FILE" ]; then
        if [ -d "$REPORT_FILE" ] || ! : > "$REPORT_FILE"; then
            log_error "报告文件不可写: $REPORT_FILE"
            exit 1
        fi
        log_to_report "SpringDoc/Swagger 注解完整性检查报告"
        log_to_report "生成时间: $(date '+%Y-%m-%d %H:%M:%S')"
        log_to_report "项目路径: $PROJECT_ROOT"
        log_to_report ""
    fi

    # 执行检查
    local check_status=0
    check_router_annotations
    local router_status=$?
    if [ "$router_status" -ne 0 ]; then
        check_status=$router_status
    fi
    check_handler_annotations
    local handler_status=$?
    if [ "$handler_status" -ne 0 ] && [ "$check_status" -eq 0 ]; then
        check_status=$handler_status
    fi
    check_dto_schema_annotations
    local dto_status=$?
    if [ "$dto_status" -ne 0 ] && [ "$check_status" -eq 0 ]; then
        check_status=$dto_status
    fi
    check_write_request_body_annotations
    local body_status=$?
    if [ "$body_status" -ne 0 ] && [ "$check_status" -eq 0 ]; then
        check_status=$body_status
    fi

    # 生成报告
    generate_summary
    local result=$?
    if [ "$check_status" -ne 0 ]; then
        result=$check_status
    fi

    [ -n "$REPORT_FILE" ] && log_info "详细报告已保存到: $REPORT_FILE"

    exit $result
}

# 运行主函数
main "$@"
