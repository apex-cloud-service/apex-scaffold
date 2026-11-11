# 监控与可观测性（Monitoring）

Apex Scaffold 的监控配套统一存放在本目录，与应用代码解耦，随包发布。

## 目录结构

```
config/monitoring/
├── README.md                    # 本文件：监控配套总览
├── podman-compose.yml           # 一键部署 Prometheus + Alertmanager + Grafana + Zipkin + Blackbox
├── alertmanager/                # 告警通知渠道配置（钉钉/企业微信/邮件等）
│   └── README.md                # Alertmanager 接入说明与配置示例
├── prometheus/
│   ├── prometheus.yml           # 抓取配置（网关 + 六业务服务 + Sentinel 9092 + 健康探针 + Alertmanager）
│   └── alert-rules.yml          # 告警规则（服务/接口/JVM/DB/Redis/Sentinel）
└── grafana/
    ├── apex-dashboard.json      # 主监控大盘（apex_api_* 指标口径）
    ├── apex-dashboard-enhanced.json # 增强大盘（Reactor Netty + JVM 深度指标）
    ├── provisioning/            # Grafana 自动置备（数据源 + 大盘）
    │   ├── dashboards/          # ⚠️ Grafana 11 要求按组件子目录存放
    │   │   └── dashboards.yml
    │   └── datasources/         #    （放根目录会导致置备静默失败）
    │       └── datasources.yml
    └── README.md                # 大盘使用与自定义指南
```

## 快速开始

```bash
# 一键拉起监控栈（Prometheus + Alertmanager + Grafana + Zipkin + Blackbox Exporter）
podman-compose -f config/monitoring/podman-compose.yml up -d

# 访问
#   Grafana      http://localhost:3200   admin / admin
#   Prometheus   http://localhost:9090
#   Alertmanager http://localhost:9093
#   Zipkin       http://localhost:9411
#   Blackbox     http://localhost:9115
```

> 💡 监控组件（Prometheus/Alertmanager/Grafana/Zipkin/Blackbox）已同时整合进根目录
> `podman-compose.infra.yml`（与 MySQL/Redis/Nacos/RocketMQ/Sentinel 一起一键部署），
> 本独立编排供「仅需监控栈」的场景使用。Prometheus、Grafana、Alertmanager、Zipkin、Blackbox 均已启用；Alertmanager 的 receiver 仍是占位 webhook，通知触达需按实际渠道替换并验证。

应用侧无需额外配置：各服务通过 `spring-boot-starter-actuator` 在各自管理端口暴露
`/actuator/prometheus`，Prometheus 分别抓取网关（8080）、auth（8081）、system 管理端口（8083）、
workflow（8093）、office（8084）、file（8085）与 integration 管理端口（8097）；容器栈内改用服务名。
链路采样数据由应用按 `trace.zipkin-url`（默认 `http://localhost:9411`）上报 Zipkin；Redis 健康探针
（`redisson_healthy`）由应用侧 `RedissonMetricsBinder` 自动上报。

> **Sentinel 抓取边界**：`apex-sentinel` job 仅抓取 `system-service` 暴露的 9092 端口；当前没有
> 为 auth/workflow/office/file/integration 配置 Sentinel exporter。Sentinel 告警仅对 system 资源生效。
> 告警规则不包含 Redis 或独立可用性规则；`probe_success` 由 Blackbox 提供，但当前规则集未使用该指标。

## 能力清单

| 能力 | 组件 | 说明 |
|------|------|------|
| 指标采集 | Micrometer + `/actuator/prometheus`（各服务管理端口） | `apex_api_*` 自定义请求指标 + JVM 标准指标 + 连接池指标（业务服务统一 Hikari，`hikaricp_*`；仅 example 演示档用 Druid，`druid_*`） |
| Redis 健康 | `RedissonMetricsBinder` | `redisson_healthy`（节点 PING 探针）+ `redisson_nodes_total` |
| 治理指标 | Sentinel Prometheus exporter（9092，仅 `system-service`） | `sentinel_metrics{type="blockQps"}` / `type="rt"` 等 Gauge 快照；不参与其他服务抓取 |
| 健康探针 | Blackbox Exporter（9115） | 探测 `/actuator/health`，支撑 `probe_success` 告警 |
| 可视化 | Grafana + `apex-dashboard.json` / `apex-dashboard-enhanced.json` | 通过 `grafana/provisioning/` 自动置备数据源与两个大盘；主大盘 96 项面板、增强大盘 14 项 |
| 告警 | `alert-rules.yml` | 5 组 13 条规则，覆盖 JVM、Sentinel、慢请求、连接池、熔断器与慢 SQL；不包含 Redis/可用性规则 |
| 链路追踪 | Brave + Zipkin | X-Trace-Id / MDC / Zipkin 三处一致 |

## 详细文档

- 大盘面板与告警规则说明：[grafana/README.md](./grafana/README.md)
- 微服务演进与可观测性整体盘点：[docs/ARCHITECTURE_EVOLUTION.md](../../docs/02-architecture/architecture-evolution.md)
