# 监控与可观测性（Monitoring）

Apex Scaffold 的监控配套统一存放在本目录，与应用代码解耦，随包发布。

## 目录结构

```
config/monitoring/
├── README.md                    # 本文件：监控配套总览
├── docker-compose.yml           # 一键部署 Prometheus + Grafana + Zipkin
├── alertmanager/                # 告警通知渠道配置（钉钉/企业微信/邮件等）
│   └── README.md                # Alertmanager 接入说明与配置示例
├── prometheus/
│   ├── prometheus.yml           # 抓取配置（应用 8081 + Sentinel 8720）
│   └── alert-rules.yml          # 告警规则（服务/接口/JVM/DB/Redis/Sentinel）
└── grafana/
    ├── apex-dashboard.json      # 监控大盘（apex_api_* 指标口径）
    ├── provisioning/            # Grafana 自动置备（数据源 + 大盘）
    │   ├── datasources.yml
    │   └── dashboards.yml
    └── README.md                # 大盘使用与自定义指南
```

## 快速开始

```bash
# 一键拉起监控栈（Prometheus + Grafana + Zipkin）
docker compose -f config/monitoring/docker-compose.yml up -d

# 访问
#   Grafana    http://localhost:3000   admin / admin
#   Prometheus http://localhost:9090
#   Zipkin     http://localhost:9411
```

应用侧无需额外配置：`spring-boot-starter-actuator` 已在独立管理端口 **8081**
暴露 `/actuator/prometheus`，Prometheus 启动即开始抓取；链路采样数据由应用按
`trace.zipkin-url`（默认 `http://localhost:9411`）上报 Zipkin。

## 能力清单

| 能力 | 组件 | 说明 |
|------|------|------|
| 指标采集 | Micrometer + `/actuator/prometheus`（8081） | `apex_api_*` 自定义请求指标 + JVM/HikariCP 标准指标 |
| 治理指标 | Sentinel Prometheus exporter（8720） | 限流/熔断拦截 QPS |
| 可视化 | Grafana + `apex-dashboard.json` | 自动置备，开箱即用 |
| 告警 | `alert-rules.yml` | 10 条规则，覆盖可用性/质量/JVM/DB/Redis |
| 链路追踪 | Brave + Zipkin | X-Trace-Id / MDC / Zipkin 三处一致 |

## 详细文档

- 大盘面板与告警规则说明：[grafana/README.md](./grafana/README.md)
- 微服务演进与可观测性整体盘点：[docs/ARCHITECTURE_EVOLUTION.md](../../docs/ARCHITECTURE_EVOLUTION.md)
