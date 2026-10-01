#!/bin/bash

set -euo pipefail

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
V2_SCRIPT="${SCRIPT_DIR}/check-springdoc-annotations-v2.sh"

printf '%s\n' "警告：check-springdoc-annotations.sh 已兼容转发到 check-springdoc-annotations-v2.sh；JavaDoc 检查请使用 check-handler-javadoc.sh。" >&2
exec bash "$V2_SCRIPT" "$@"
