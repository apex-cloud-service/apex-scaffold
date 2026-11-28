# Reactor Netty 指标验证指南

**适用版本**：Apex Scaffold 1.3.1（Java 21 / Spring Boot WebFlux）
**配置状态**：网关 `application.yml` 已显式配置
`management.metrics.enable.reactor.netty: true`。该配置已由静态契约测试锁定；
真实 `/actuator/prometheus` 端点是否暴露指标仍需启动网关后验证。

---

## 前置条件

### 1. 构建环境

在仓库根目录执行：

```bash
source scripts/build/init-build-env.sh
java -version   # 21.0.13.0+9-tem
mvnd --version  # 2.0.0-rc-3
```

### 2. Podman 与 Redis

```bash
podman ps
podman-compose -f podman-compose.infra.yml ps redis
```

macOS 上若 Podman 未启动：

```bash
podman machine list
podman machine start
```

Redis 未运行时可单独启动：

```bash
podman-compose -f podman-compose.infra.yml up -d redis
```

### 3. 启动网关

网关默认业务端口为 **8080**，Prometheus 指标通过同一端口的
`/actuator/prometheus` 暴露。

前台运行：

```bash
cd /Users/work/apex-scaffold
mvnd spring-boot:run -pl :apex-gateway
```

后台运行：

```bash
cd /Users/work/apex-scaffold
nohup mvnd spring-boot:run -pl :apex-gateway > app.log 2>&1 &
tail -f app.log
```

健康检查：

```bash
curl -fsS http://localhost:8080/actuator/health | jq .status
```

预期输出 `"UP"`。若本机未安装 `jq`，可直接执行：

```bash
curl -fsS http://localhost:8080/actuator/health
```

---

## 指标验证

### 步骤 1：检查 Actuator 指标端点

```bash
curl -fsS http://localhost:8080/actuator/prometheus | wc -l
```

预期看到 100 行以上指标数据。

### 步骤 2：验证 Reactor Netty 指标存在

```bash
curl -fsS http://localhost:8080/actuator/prometheus \
  | grep '^reactor_netty' | head -20
```

预期可见以下类型：

```text
# EventLoop 指标
reactor_netty_eventloop_threads{...} 16
reactor_netty_eventloop_pending_tasks{...} 0

# 连接池指标
reactor_netty_connection_provider_active_connections{...} 0
reactor_netty_connection_provider_pending_connections{...} 0
reactor_netty_connection_provider_idle_connections{...} 2
reactor_netty_connection_provider_total_connections{...} 2

# ByteBuf 内存指标
reactor_netty_bytebuf_allocator_used_direct_memory{...} 1048576
reactor_netty_bytebuf_allocator_used_heap_memory{...} 0
```

### 步骤 3：统计 Reactor Netty 指标数量

```bash
curl -fsS http://localhost:8080/actuator/prometheus \
  | grep -c '^reactor_netty'
```

预期为 10–20 条。结果为 0 时依次检查：

1. `management.metrics.enable.reactor.netty` 是否为 `true`；
2. 是否重启网关以加载配置；
3. 是否有更高优先级 profile 覆盖；
4. 当前运行实例是否为 WebFlux/Reactor Netty 栈。

### 步骤 4：验证 Prometheus 已采集

```bash
# 指标标签以 management.metrics.tags.application=${spring.application.name} 为准（无 module 标签）
curl -fsS -G \
  --data-urlencode 'query=reactor_netty_eventloop_threads{application="apex-gateway"}' \
  http://localhost:9090/api/v1/query | jq .
```

预期 `data.result` 非空。示例标签：

```json
{
  "status": "success",
  "data": {
    "resultType": "vector",
    "result": [
      {
        "metric": {
          "__name__": "reactor_netty_eventloop_threads",
          "application": "apex-gateway",
          "instance": "host.containers.internal:8080",
          "job": "apex-gateway",
          "name": "reactor-http"
        },
        "value": [1725723600, "16"]
      }
    ]
  }
}
```

若结果为空：

- 等待 15–30 秒后重试；
- 检查 `curl -fsS http://localhost:9090/api/v1/targets` 中 `apex-gateway` 是否 UP；
- 确认 `prometheus.yml` 的 target 指向宿主网关端口 8080。

### 步骤 5：在 Grafana 查看增强大盘

1. 打开 Grafana：<http://localhost:3200>（默认 `admin / admin`，生产必须修改）；
2. 打开增强大盘：<http://localhost:3200/d/apex-enhanced>；
3. 检查 WebFlux 网关部分与 JVM 深度监控部分是否有数据。

增强大盘当前包含 14 项元素、2 个 row、12 个数据面板。JVM 区包含：

- JVM Heap 内存详情
- JVM NonHeap 内存详情
- GC 暂停时间（Max / Avg / 5m Max）
- GC 频率
- JVM 线程状态
- JVM 类加载
- JVM 缓冲池

可生成测试流量：

```bash
for i in {1..100}; do
  curl -fsS http://localhost:8080/api/v1/auth/captcha >/dev/null || true
  sleep 0.1
done
```

---

## 一键验证脚本

仓库已提供：

```bash
bash scripts/ops/monitoring/verify-reactor-metrics.sh
```

默认参数：

| 变量 | 默认值 | 说明 |
|---|---|---|
| `APP_URL` | `http://localhost:8080` | 网关地址 |
| `PROMETHEUS_URL` | `http://localhost:9090` | Prometheus 地址 |
| `GRAFANA_URL` | `http://localhost:3200` | Grafana 地址 |

示例：

```bash
APP_URL=http://127.0.0.1:8080 \
PROMETHEUS_URL=http://127.0.0.1:9090 \
bash scripts/ops/monitoring/verify-reactor-metrics.sh
```

脚本行为：

- 应用不可达或指标为 0：退出码 1；
- 应用指标存在但未安装 `jq`：Prometheus 解析步骤输出 **SKIP**，不得视为通过；
- Prometheus 尚未采集：输出警告，不伪造通过结论；
- 只验证端点与指标存在，不替代 Grafana 面板人工检查或告警触达验证。

---

## 常见问题排查

### 问题 1：网关无法启动

**症状**：`Connection refused: localhost/127.0.0.1:6379`

**原因**：Redis 未运行。

**解决**：

```bash
podman-compose -f /Users/work/apex-scaffold/podman-compose.infra.yml up -d redis
```

### 问题 2：没有 Reactor Netty 指标

**症状**：

```bash
curl -fsS http://localhost:8080/actuator/prometheus | grep reactor_netty
```

无输出。

**排查**：

```bash
grep -A 8 '^management:' /Users/work/apex-scaffold/apex-gateway/src/main/resources/application.yml
grep -r 'reactor.netty' /Users/work/apex-scaffold/apex-gateway/src/main/resources/
```

应确认存在：

```yaml
management:
  metrics:
    enable:
      reactor.netty: true
```

并确认网关已重启。

### 问题 3：Grafana 面板显示 No Data

可能原因：

1. Prometheus 尚未采集（等待 15–30 秒）；
2. 网关没有请求流量，EventLoop/连接指标尚未更新；
3. 查询标签与真实采集标签不一致（应为 `application="apex-gateway"`，无 `module` 标签）。

发送测试流量：

```bash
for i in {1..50}; do
  curl -fsS http://localhost:8080/api/v1/auth/captcha >/dev/null || true
done
```

---

## 预期指标范围

| 指标 | 正常参考 | 说明 |
|---|---|---|
| `reactor_netty_eventloop_threads` | 8–32 | 通常为 CPU 核心数 × 2 |
| `reactor_netty_eventloop_pending_tasks` | 0–100 | 持续偏高说明 EventLoop 积压 |
| `reactor_netty_connection_provider_active_connections` | 0–500 | 活跃连接数 |
| `reactor_netty_connection_provider_idle_connections` | 1–50 | 空闲连接数 |
| `reactor_netty_bytebuf_allocator_used_direct_memory` | 0–100MB | Direct 内存使用 |
| `reactor_netty_bytebuf_allocator_used_heap_memory` | 0–50MB | Heap 内存使用 |

以上为经验参考值，不是本项目已实测的容量结论；生产阈值应基于真实压测和业务 SLA 调整。

---

## 验证检查清单

- [ ] 网关健康检查返回 UP（`http://localhost:8080/actuator/health`）
- [ ] `curl -fsS http://localhost:8080/actuator/prometheus | grep -c '^reactor_netty'` 大于 10
- [ ] Prometheus 查询 `reactor_netty_eventloop_threads{application="apex-gateway"}` 有结果
- [ ] Grafana 增强大盘 WebFlux 部分有数据
- [ ] Grafana 增强大盘 JVM 部分有数据
- [ ] 未安装 `jq` 时，Prometheus 步骤明确标记 SKIP，而非通过

---

**维护者**：Apex Team
**最后更新**：2026-11-10
**相关文档**：

- [增强监控面板使用指南](../../config/monitoring/grafana/README-ENHANCED.md)
- [监控配置总览](../../config/monitoring/README.md)
