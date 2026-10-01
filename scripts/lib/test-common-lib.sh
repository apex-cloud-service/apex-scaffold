#!/bin/bash
###############################################################################
# test-common-lib.sh - 测试公共函数库
#
# 用法：./scripts/lib/test-common-lib.sh
###############################################################################

set -euo pipefail

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
source "${SCRIPT_DIR}/common.sh"

TEST_ROOT=$(mktemp -d)
TEST_ROOT=$(CDPATH= cd -- "$TEST_ROOT" && pwd -P)
trap 'rm -rf -- "$TEST_ROOT"' EXIT
LIB="$SCRIPT_DIR/common.sh"
ASSERTIONS=0

assert_eq() {
    if [[ "$1" != "$2" ]]; then
        log_failure "$3: 期望 [$1]，实际 [$2]"
        exit 1
    fi
    ASSERTIONS=$((ASSERTIONS + 1))
}

assert_ok() {
    if "$@"; then
        ASSERTIONS=$((ASSERTIONS + 1))
    else
        log_failure "命令应成功: $*"
        exit 1
    fi
}

assert_fail() {
    local status
    if "$@" > "$TEST_ROOT/stdout" 2> "$TEST_ROOT/stderr"; then
        log_failure "命令应失败: $*"
        exit 1
    else
        status=$?
    fi
    ASSERTIONS=$((ASSERTIONS + 1))
    [[ $status -ne 0 ]]
}

mkdir -p "$TEST_ROOT/tree/visible" "$TEST_ROOT/tree/targeted" "$TEST_ROOT/apex one/src" "$TEST_ROOT/apex two/src"
printf 'hit\nhit\n-other\n' > "$TEST_ROOT/tree/visible/keep.java"
touch "$TEST_ROOT/tree/targeted/keep.java" "$TEST_ROOT/apex one/src/a.java" "$TEST_ROOT/apex two/src/b.java"
for excluded in "${EXCLUDE_DIRS[@]}"; do
    mkdir -p "$TEST_ROOT/tree/$excluded/deep"
    touch "$TEST_ROOT/tree/$excluded/deep/hidden.java"
done
expected=$(printf '%s\n' "$TEST_ROOT/tree/targeted/keep.java" "$TEST_ROOT/tree/visible/keep.java")
assert_eq "$expected" "$(safe_find "$TEST_ROOT/tree" '*.java' | sort)" "默认排除"
assert_eq "$expected" "$(safe_find_custom "$TEST_ROOT/tree" -name '*.java' -o -name 'hidden.java' | sort)" "OR 不得绕过排除"
assert_eq '' "$(safe_find "$TEST_ROOT/tree/target/deep" '*.java')" "排除目录内部起点"
assert_eq '' "$(safe_find "$TEST_ROOT/tree/target/" '*.java')" "排除根目录"
assert_eq '' "$(safe_find "$TEST_ROOT/tree" '*.missing')" "零匹配成功"
assert_eq "$(printf '%s\n' "$TEST_ROOT/apex one/src/a.java" "$TEST_ROOT/apex two/src/b.java")" "$(safe_find "$TEST_ROOT/apex */src" '*.java' | sort)" "含空格的 glob 根路径"
assert_eq "$expected" "$(safe_find_custom "$TEST_ROOT/tree" -type f -print | sort)" "显式输出不重复"
assert_eq "$expected" "$(safe_find_custom "$TEST_ROOT/tree" -type f -print0 | tr '\0' '\n' | sort)" "NUL 输出"
assert_ok bash -euo pipefail -c '
    source "$1"
    find() { return 87; }
    [[ -n $(safe_find "$2" "*.java") ]]
' _ "$LIB" "$TEST_ROOT/tree"
assert_fail safe_find "$TEST_ROOT/missing" '*.java'
assert_eq '' "$(cat "$TEST_ROOT/stdout")" "查找错误无伪成功输出"
assert_ok test -s "$TEST_ROOT/stderr"
assert_fail safe_find "$TEST_ROOT/no*/src" '*.java'
assert_fail safe_find
assert_fail safe_find_custom
assert_fail safe_find_custom "$TEST_ROOT/tree" -invalid-option
assert_fail safe_find_custom "$TEST_ROOT/tree" -depth
assert_fail safe_find_custom "$TEST_ROOT/tree" -delete
assert_ok test -f "$TEST_ROOT/tree/visible/keep.java"

mkdir -p "$TEST_ROOT/bin"
printf '#!/bin/bash\nprintf "%%s\\n" "$@" > "$FIND_ARGS"\nexit 37\n' > "$TEST_ROOT/bin/find"
chmod +x "$TEST_ROOT/bin/find"
assert_ok bash -euo pipefail -c '
    source "$1"
    export FIND_ARGS="$3" PATH="$4:$PATH"
    if safe_find "$2" "*.java"; then exit 1; else [[ $? == 37 ]]; fi
' _ "$LIB" "$TEST_ROOT/tree" "$TEST_ROOT/find-args" "$TEST_ROOT/bin"
assert_eq 1 "$(safe_count '^-prune$' "$TEST_ROOT/find-args")" "真实剪枝参数"
assert_eq "$(printf '%s\n' "$TEST_ROOT/tree" "$TEST_ROOT/tree/targeted" "$TEST_ROOT/tree/visible")" "$(safe_find_custom "$TEST_ROOT/tree" -type d -exec printf '%s\n' '{}' ';' | sort)" "遍历动作不进入排除目录"
assert_eq 9 "$(safe_count '^-name$' "$TEST_ROOT/find-args")" "统一剪枝目录列表"


assert_eq 2 "$(safe_count hit "$TEST_ROOT/tree/visible/keep.java")" "匹配行数"
assert_eq 0 "$(safe_count absent "$TEST_ROOT/tree/visible/keep.java")" "零匹配严格为 0"
assert_eq 1 "$(safe_count -other "$TEST_ROOT/tree/visible/keep.java")" "连字符模式"
assert_eq 3 "$(safe_count '' "$TEST_ROOT/tree/visible/keep.java")" "空正则"
assert_fail safe_count hit "$TEST_ROOT/missing"
assert_eq '' "$(cat "$TEST_ROOT/stdout")" "读取错误不输出 0"
assert_fail safe_count '[' "$TEST_ROOT/tree/visible/keep.java"
assert_fail safe_count
assert_ok bash -euo pipefail -c 'source "$1"; [[ $(safe_count absent "$2") == 0 ]]; [[ $(clean_number abc) == 0 ]]' _ "$LIB" "$TEST_ROOT/tree/visible/keep.java"
assert_eq 12 "$(clean_number 'abc0012 xyz34')" "提取首个数字"
assert_eq 0 "$(clean_number '')" "空数字"
assert_ok safe_gt 10 5
assert_ok safe_lt 3 8
assert_ok safe_eq 7 7
assert_fail safe_gt 5 10
assert_fail safe_lt 8 3
assert_fail safe_eq 7 8
assert_eq 72 "$(calculate_percentage 13 18)" "百分比"
assert_eq 0 "$(calculate_percentage 1 0)" "零分母"
assert_eq 50 "$(calculate_percentage 08 016)" "前导零"
assert_ok bash -euo pipefail -c 'source "$1"; CURRENT_LOG_LEVEL=3; source "$1"; [[ $CURRENT_LOG_LEVEL == 3 ]]' _ "$LIB"

original_pwd=$PWD
get_script_dir > "$TEST_ROOT/script-dir"
assert_eq "$SCRIPT_DIR" "$(cat "$TEST_ROOT/script-dir")" "调用脚本目录"
assert_eq "$original_pwd" "$PWD" "路径函数不切换工作目录"
get_project_root > "$TEST_ROOT/project-root"
assert_eq "$(CDPATH= cd -- "$SCRIPT_DIR/../.." && pwd -P)" "$(cat "$TEST_ROOT/project-root")" "项目根目录"
assert_eq "$original_pwd" "$PWD" "根目录函数不切换工作目录"
mkdir -p "$TEST_ROOT/project space/a/b/c/d/e/f"
touch "$TEST_ROOT/project space/pom.xml"
printf 'source "$1"\nget_project_root\n' > "$TEST_ROOT/project space/a/b/c/d/e/f/caller.sh"
assert_eq "$TEST_ROOT/project space" "$(CDPATH=/tmp bash "$TEST_ROOT/project space/a/b/c/d/e/f/caller.sh" "$LIB")" "深层调用者定位"
printf 'source "$1"\nget_project_root\n' > "$TEST_ROOT/no-project.sh"
assert_fail bash "$TEST_ROOT/no-project.sh" "$LIB"

mkdir -p "$TEST_ROOT/literal[1]" "$TEST_ROOT/-root"
touch "$TEST_ROOT/literal[1]/file.java" "$TEST_ROOT/-root/file.java"
assert_eq "$TEST_ROOT/literal[1]/file.java" "$(safe_find "$TEST_ROOT/literal[1]" '*.java')" "字面 glob 字符路径优先"
assert_eq './-root/file.java' "$(cd -- "$TEST_ROOT"; safe_find -root '*.java')" "连字符根路径"
assert_ok bash -euo pipefail -c 'source "$1"; set -f; shopt -s failglob; [[ -n $(safe_find "$2/apex */src" "*.java") ]]; [[ $- == *f* ]]; shopt -q failglob' _ "$LIB" "$TEST_ROOT"
mkdir -p "$TEST_ROOT/paths"
(
    cd -- "$TEST_ROOT/paths"
    touch -- '-leading' 'space name' 'quote"name' 'semi;touch INJECTED' '$(touch INJECTED)' $'line\nbreak'
    bash -euo pipefail -c 'source "$1"; shift; setup_temp_cleanup "$@"' _ "$LIB" '-leading' 'space name' 'quote"name' 'semi;touch INJECTED' '$(touch INJECTED)' $'line\nbreak'
)
assert_eq '' "$(safe_find_custom "$TEST_ROOT/paths" -mindepth 1 -print)" "trap 路径安全且无注入"
assert_ok test ! -e "$TEST_ROOT/paths/INJECTED"
assert_ok bash -euo pipefail -c 'source "$1"; setup_temp_cleanup' _ "$LIB"
temp_file=$(create_temp_file apex-common-test)
assert_ok test -f "$temp_file"
assert_ok cleanup_temp_files "$temp_file"
assert_ok test ! -e "$temp_file"
assert_ok require_file "$LIB"
assert_ok require_directory "$SCRIPT_DIR"
assert_fail require_file "$TEST_ROOT/missing"
assert_fail require_directory "$TEST_ROOT/missing"
assert_eq v1.0.0 "$(get_common_lib_version)" "版本接口"
log_success "全部 $ASSERTIONS 个断言通过"
