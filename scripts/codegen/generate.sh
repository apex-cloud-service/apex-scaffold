#!/usr/bin/env zsh
# ============================================================
# Apex Scaffold 代码生成器（DDD 五层模板开发工具）
#
# 用法:
#   ./scripts/codegen/generate.sh -i <definition.json> [-o <outputDir>] [-v <migrationVersion>] [-f <frontendDir>]
#
# 示例:
#   ./scripts/codegen/generate.sh -i examples/product.json
#   ./scripts/codegen/generate.sh -i examples/product.json -o ./generated -v 3
#   ./scripts/codegen/generate.sh -i examples/product.json -f /path/to/apex-admin-web   # 同时生成 Vue3 页面
#
# 依赖: sdkman 默认 JDK 21 + mvnd（本项目统一构建环境）
# ============================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]:-$0}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

INPUT_FILE=""
OUTPUT_DIR="${PROJECT_ROOT}/generated"
FRONTEND_DIR=""
MIGRATION_VERSION=2

while [[ $# -gt 0 ]]; do
  case "$1" in
    -i) INPUT_FILE="$2"; shift 2 ;;
    -o) OUTPUT_DIR="$2"; shift 2 ;;
    -v) MIGRATION_VERSION="$2"; shift 2 ;;
    -f) FRONTEND_DIR="$2"; shift 2 ;;
    *) echo "未知参数: $1"; exit 2 ;;
  esac
done

if [[ -z "${INPUT_FILE}" ]]; then
  echo "用法: generate.sh -i <definition.json> [-o <outputDir>] [-v <migrationVersion>] [-f <frontendDir>]"
  exit 2
fi

if [[ ! -f "${INPUT_FILE}" ]]; then
  echo "错误: 定义文件不存在: ${INPUT_FILE}"
  exit 1
fi

# 加载 sdkman 环境（JDK + mvnd）
if [[ -f "$HOME/.sdkman/bin/sdkman-init.sh" ]]; then
  # shellcheck disable=SC1091
  source "$HOME/.sdkman/bin/sdkman-init.sh"
fi

echo "=== 构建 apex-generator 模块 ==="
(cd "${PROJECT_ROOT}" && MAVEN_OPTS="-Xmx512m" mvnd -o -q install -pl apex-generator -DskipTests)

echo "=== 生成代码 ==="
CP_FILE="$(mktemp)"
(cd "${PROJECT_ROOT}" && mvnd -o -q dependency:build-classpath -pl apex-generator -Dmdep.outputFile="${CP_FILE}" 2>/dev/null)
CP="apex-generator/target/classes:$(cat "${CP_FILE}")"
rm -f "${CP_FILE}"
(cd "${PROJECT_ROOT}" && java -cp "${CP}" \
  com.apex.scaffold.generator.GeneratorCli -i "${INPUT_FILE}" -o "${OUTPUT_DIR}" -v "${MIGRATION_VERSION}" \
  ${FRONTEND_DIR:+ -f "${FRONTEND_DIR}"})

echo "=== 完成 ==="
echo "后端输出目录: ${OUTPUT_DIR}"
[[ -n "${FRONTEND_DIR}" ]] && echo "前端输出目录: ${FRONTEND_DIR}"
