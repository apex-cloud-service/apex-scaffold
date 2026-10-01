#!/bin/bash

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
