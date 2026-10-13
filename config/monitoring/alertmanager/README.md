# Alertmanager 配置（告警通知渠道）

本目录用于放置 Alertmanager 告警通知配置（`alertmanager.yml`），与
`../prometheus/alert-rules.yml` 的告警规则配合，将 Prometheus 触发的告警
推送到通知渠道（钉钉 / 企业微信 / 邮件 / Slack 等）。

> 告警规则在 `../prometheus/alert-rules.yml` 中定义；规则产生告警后由
> Alertmanager 负责**路由、分组、抑制与通知**。本地 Podman Compose 演示栈
> （`../podman-compose.yml`）默认未启用 Alertmanager，如需演示告警通知，
> 可将 `prom/prometheus` 命令追加 `--web.external-url` 并在此目录添加
> `alertmanager.yml` 后将 `alertmanager:9093` 加入 compose 网络。

## 配置示例（钉钉机器人）

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
      - url: "https://oapi.dingtalk.com/robot/send?access_token=YOUR_TOKEN"
        send_resolved: true
```

生产环境按实际渠道替换 `receivers` 并妥善管理 Token（建议通过环境变量注入，
勿提交明文密钥）。
