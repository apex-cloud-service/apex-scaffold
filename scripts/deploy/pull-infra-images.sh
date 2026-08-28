#!/usr/bin/env bash
# ============================================================
# 拉取基础设施镜像（带重试，容忍镜像加速器 403 / 网络抖动）
# 用法: ./scripts/deploy/pull-infra-images.sh
# ============================================================
set -u

IMAGES=(
  "mysql:8.0"
  "redis:7-alpine"
  "nacos/nacos-server:v2.3.2"
  "apache/rocketmq:5.3.1"
  "registry.cn-hangzhou.aliyuncs.com/zhengqing/sentinel-dashboard:1.8.2"
  "openzipkin/zipkin:3.4"
  "prom/prometheus:v2.53.0"
  "grafana/grafana:11.1.0"
  "prom/blackbox-exporter:v0.25.0"
)

MAX_RETRY=4
for img in "${IMAGES[@]}"; do
  if docker image inspect "${img}" >/dev/null 2>&1; then
    echo "[SKIP] ${img} 已存在"
    continue
  fi
  ok=0
  for ((i = 1; i <= MAX_RETRY; i++)); do
    echo "[PULL ${i}/${MAX_RETRY}] ${img} ..."
    if docker pull "${img}" >/dev/null 2>&1; then
      echo "[OK] ${img}"
      ok=1
      break
    fi
    echo "[RETRY ${i}] ${img} 失败，5s 后重试"
    sleep 5
  done
  if [ "${ok}" = "0" ]; then
    echo "[FAIL] ${img} 多次重试仍失败，请检查网络"
  fi
done
echo "===== 拉取完成 ====="
docker images --format "{{.Repository}}:{{.Tag}}" | grep -E "mysql:8.0|redis:7-alpine|nacos-server:v2.3.2|rocketmq:5.3.1|sentinel-dashboard:1.8.8|zipkin:3.4|prometheus:v2.53.0|grafana:11.1.0|blackbox-exporter:v0.25.0"
