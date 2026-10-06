#!/usr/bin/env bash

###############################################################################
# jvm-diagnostics-test.sh - jvm-diagnostics.sh 的自动化测试
# 用法：./scripts/test/jvm-diagnostics-test.sh  无参数；使用临时目录与假 jcmd 验证各子命令行为
# 作者：Apex Team    创建：2026-08-30
###############################################################################
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
SCRIPT="${ROOT_DIR}/scripts/diagnostics/jvm-diagnostics.sh"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "${TMP_DIR}"' EXIT

assert_contains() {
  case "$1" in
    *"$2"*) ;;
    *) printf 'expected [%s] to contain [%s]\n' "$1" "$2" >&2; exit 1 ;;
  esac
}

help_output="$(${SCRIPT} --help)"
assert_contains "${help_output}" "Usage:"

if "${SCRIPT}" invalid >/dev/null 2>&1; then
  printf 'invalid action unexpectedly succeeded\n' >&2
  exit 1
fi

FAKE_BIN="${TMP_DIR}/bin"
mkdir -p "${FAKE_BIN}"
cat > "${FAKE_BIN}/jcmd" <<'EOF'
#!/usr/bin/env bash
printf '%s\n' "$*"
for argument in "$@"; do
  case "${argument}" in
    filename=*) : > "${argument#filename=}" ;;
  esac
done
EOF
chmod +x "${FAKE_BIN}/jcmd"

PATH="${FAKE_BIN}:${PATH}" "${SCRIPT}" all $$ "${TMP_DIR}/output" >/dev/null
for name in flags heap-info thread-dump; do
  test -s "${TMP_DIR}/output/$$-${name}.txt"
done

test -f "${TMP_DIR}/output/$$-jfr.jfr"
printf 'jvm diagnostics tests passed\n'
