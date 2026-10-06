#!/bin/bash

###############################################################################
# export-api-docs.sh - 从运行中的应用导出 OpenAPI 文档（JSON/YAML）
# 用法：./scripts/utils/export-api-docs.sh  可选环境变量 API_BASE_URL（默认 http://localhost:8080）、OUTPUT_DIR（默认 ./docs/api）
# 作者：Apex Team    创建：2026-10-01
###############################################################################

set -e

API_BASE_URL="${API_BASE_URL:-http://localhost:8080}"
OUTPUT_DIR="${OUTPUT_DIR:-./docs/api}"

mkdir -p "$OUTPUT_DIR"

echo "导出 OpenAPI 规范..."
curl -s "${API_BASE_URL}/v3/api-docs" > "${OUTPUT_DIR}/openapi.json"

echo "导出 OpenAPI YAML..."
curl -s "${API_BASE_URL}/v3/api-docs.yaml" > "${OUTPUT_DIR}/openapi.yaml"

echo "API 文档已导出到 $OUTPUT_DIR"
echo "在线文档: ${API_BASE_URL}/swagger-ui.html"
