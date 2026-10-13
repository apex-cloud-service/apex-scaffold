#!/usr/bin/env bash
# 拉取基础设施镜像，网络失败最多重试四次。
set -u
IMAGES=(
  "docker.io/library/mysql:8.0"
  "docker.io/library/redis:7-alpine"
  "docker.io/nacos/nacos-server:v2.3.2"
  "docker.io/apache/rocketmq:5.3.1"
  "registry.cn-hangzhou.aliyuncs.com/zhengqing/sentinel-dashboard:1.8.2"
  "docker.io/openzipkin/zipkin:3.4"
  "docker.io/prom/prometheus:v2.53.0"
  "docker.io/grafana/grafana:11.1.0"
  "docker.io/prom/blackbox-exporter:v0.25.0"
)
MAX_RETRY=4
for img in "${IMAGES[@]}"; do
  if podman image inspect "${img}" >/dev/null 2>&1; then
    echo "[SKIP] ${img} 已存在"
    continue
  fi
  ok=0
  for ((i = 1; i <= MAX_RETRY; i++)); do
    echo "[PULL ${i}/${MAX_RETRY}] ${img} ..."
    if podman pull "${img}" >/dev/null 2>&1; then
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
podman images --format "{{.Repository}}:{{.Tag}}"
