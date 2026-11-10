# Alertmanager 配置（告警通知渠道）

本目录是 Alertmanager 的说明入口。当前 Compose 实际加载的配置文件位于
`config/monitoring/prometheus/alertmanager.yml`，它与
`config/monitoring/prometheus/alert-rules.yml` 中的告警规则配合，负责告警的
路由、分组、抑制与通知。

## 当前状态

- `config/monitoring/podman-compose.yml` **已启用 Alertmanager**（容器
  `apex-alertmanager`，宿主机端口 `9093`）。
- Prometheus 已配置向 `alertmanager:9093` 发送告警。
- `alertmanager.yml` 当前定义 4 个 receiver：`default`、`critical`、`warning`、`info`。
- 4 个 receiver 目前均指向**占位 webhook**：
  - `http://localhost:8080/webhook/alert`
  - `http://localhost:8080/webhook/alert/critical`
  - `http://localhost:8080/webhook/alert/warning`
  - `http://localhost:8080/webhook/alert/info`
- 容器内的 `localhost` 指向 Alertmanager 自身，这些地址不是可直接使用的通知渠道。
- **真实告警触达尚未验证**。本仓库只证明告警规则与路由配置可被加载，不证明短信、
  钉钉、企业微信或邮件已经送达。

生产环境必须把 receiver 替换为可达的真实渠道，并完成一次从 Prometheus 规则触发到
通知接收的端到端验证；Token、Webhook 地址和证书通过环境变量或外部密钥管理系统注入，
不得提交明文密钥。

## 路由约定

| receiver | 用途 |
|---|---|
| `default` | 未匹配更具体路由时的兜底 |
| `critical` | P0 告警，要求即时触达 |
| `warning` | P1 告警，要求 1 小时内响应 |
| `info` | P2 告警，用于工作时段复盘 |

真实渠道示例（钉钉机器人）：

```yaml
global:
  resolve_timeout: 5m

route:
  receiver: dingtalk
  group_by: ["alertname", "severity"]
  group_wait: 30s
  group_interval: 5m
  repeat_interval: 4h

receivers:
  - name: dingtalk
    webhook_configs:
      - url: "${ALERTMANAGER_DINGTALK_WEBHOOK:?must be set}"
        send_resolved: true
```

## 验证入口

```bash
# 启动监控栈（含 Alertmanager）
podman-compose -f config/monitoring/podman-compose.yml up -d

# 查看 Alertmanager 状态
curl -fsS http://localhost:9093/-/ready
curl -fsS http://localhost:9093/api/v2/status

# 查看当前告警与静默
curl -fsS http://localhost:9093/api/v2/alerts
curl -fsS http://localhost:9093/api/v2/silences
```

上述命令只验证 Alertmanager 可访问及 API 返回，不验证外部通知送达。真实渠道测试应在
隔离环境主动触发一条测试告警，并核对接收端日志与告警恢复通知。
