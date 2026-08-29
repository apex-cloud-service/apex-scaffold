# 监控与可观测性（Monitoring）

Apex Scaffold 的监控配套统一存放在本目录，与应用代码解耦，随包发布。

## 目录结构

```
config/monitoring/
├── README.md                    # 本文件：监控配套总览
├── docker-compose.yml           # 一键部署 Prometheus + Grafana + Zipkin + Blackbox
├── alertmanager/                # 告警通知渠道配置（钉钉/企业微信/邮件等）
│   └── README.md                # Alertmanager 接入说明与配置示例
├── prometheus/
│   ├── prometheus.yml           # 抓取配置（应用 8081 + Sentinel 8720 + 健康探针）
│   └── alert-rules.yml          # 告警规则（服务/接口/JVM/DB/Redis/Sentinel）
└── grafana/
    ├── apex-dashboard.json      # 监控大盘（apex_api_* 指标口径）
    ├── provisioning/            # Grafana 自动置备（数据源 + 大盘）
    │   ├── dashboards/          # ⚠️ Grafana 11 要求按组件子目录存放
    │   │   └── dashboards.yml
    │   └── datasources/         #    （放根目录会导致置备静默失败）
    │       └── datasources.yml
    └── README.md                # 大盘使用与自定义指南
```

## 快速开始

```bash
# 一键拉起监控栈（Prometheus + Grafana + Zipkin + Blackbox Exporter）
docker compose -f config/monitoring/docker-compose.yml up -d

# 访问
#   Grafana    http://localhost:3000   admin / admin
#   Prometheus http://localhost:9090
#   Zipkin     http://localhost:9411
#   Blackbox   http://localhost:9115
```

> 💡 监控组件（Prometheus/Grafana/Zipkin/Blackbox）已同时整合进根目录
> `docker-compose.infra.yml`（与 MySQL/Redis/Nacos/RocketMQ/Sentinel 一起一键部署），
> 本独立编排供「仅需监控栈」的场景使用，两者端口与配置完全一致。

应用侧无需额外配置：`spring-boot-starter-actuator` 已在独立管理端口 **8081**
暴露 `/actuator/prometheus`，Prometheus 启动即开始抓取（本地应用经
`host.docker.internal:8081` 访问）；链路采样数据由应用按
`trace.zipkin-url`（默认 `http://localhost:9411`）上报 Zipkin；Redis 健康探针
（`redisson_healthy`）由应用侧 `RedissonMetricsBinder` 自动上报。

## 能力清单

| 能力 | 组件 | 说明 |
|------|------|------|
| 指标采集 | Micrometer + `/actuator/prometheus`（8081） | `apex_api_*` 自定义请求指标 + JVM 标准指标 + 连接池指标（dev 用 `hikaricp_*`，prod 用 `druid_*`） |
| Redis 健康 | `RedissonMetricsBinder` | `redisson_healthy`（节点 PING 探针）+ `redisson_nodes_total` |
| 治理指标 | Sentinel Prometheus exporter（8720） | 限流/熔断拦截 QPS |
| 健康探针 | Blackbox Exporter（9115） | 探测 `/actuator/health`，支撑 `probe_success` 告警 |
| 可视化 | Grafana + `apex-dashboard.json` | 自动置备，开箱即用；DB 面板双连接池口径 |
| 告警 | `alert-rules.yml` | 12 条规则，覆盖可用性/质量/JVM/DB/Redis/Sentinel |
| 链路追踪 | Brave + Zipkin | X-Trace-Id / MDC / Zipkin 三处一致 |

## 详细文档

- 大盘面板与告警规则说明：[grafana/README.md](./grafana/README.md)
- 微服务演进与可观测性整体盘点：[docs/ARCHITECTURE_EVOLUTION.md](../../docs/ARCHITECTURE_EVOLUTION.md)
