# ELK 日志检索运维说明

> 适用范围：Apex Scaffold 结构化日志的复杂检索、长期留存与聚合分析。
> ELK（Elasticsearch + Logstash/Filebeat + Kibana）与既有 **Loki + Grafana 并存**，互不替代：
> - **Loki/Grafana**：实时流式日志、轻量标签检索（`service` 等标签）。
> - **ELK**：结构化字段的复杂组合查询（`traceId` / `tenantId` / `routeId` / `status`）、长期留存、聚合分析。

---

## 1. 组件与端口

| 组件 | 镜像 | 端口 | 说明 |
| --- | --- | --- | --- |
| Elasticsearch | `docker.elastic.co/elasticsearch/elasticsearch:8.13.4` | `9200` | 日志存储与检索（单节点） |
| Kibana | `docker.elastic.co/kibana/kibana:8.13.4` | `5601` | 可视化与检索 |
| Filebeat | `docker.elastic.co/beats/filebeat:8.13.4` | — | 采集应用 JSON 日志写入 ES |

编排定义：`docker-compose.infra.yml`（`elasticsearch` / `kibana` / `filebeat` 服务）。
数据卷：`es-data`（ES 数据持久化）、`apex-logs`（与各微服务共享的日志目录，Filebeat 只读挂载到 `/var/log/apex-scaffold`）。

---

## 2. 日志管道

```
应用 logback JSON_FILE_OUTPUT  →  /var/log/apex-scaffold/<service>-json.log
        ↓ Filebeat（filestream + ndjson 解析）
Elasticsearch（索引 apex-logs-yyyy.MM.dd，ILM 按天滚动、保留 30 天）
        ↓
Kibana Discover / 仪表盘
```

应用侧由 `JSON_FILE_OUTPUT`（LogstashEncoder，`includeMdc=true`）输出每行一条完整 JSON，
含字段：`@timestamp`、`level`、`logger_name`、`message`、`traceId`、`tid`、`serviceName`、`service`，
以及 MDC 透传的 `tenantId` / `requestId` / `userId` 等。契约由 `LogJsonSchemaTest` 守护。

---

## 3. 首次启动（索引模板 + ILM）

Filebeat 配置已关闭自带模板/ILM（`setup.template.enabled: false`、`setup.ilm.enabled: false`），
统一使用托管模板与策略。ES 就绪后执行一次：

```bash
# 1) 建 ILM 策略（按天滚动，保留 30 天）
curl -s -X PUT "http://localhost:9200/_ilm/policy/apex-logs-ilm" \
  -H 'Content-Type: application/json' \
  -d @config/monitoring/elk/ilm-policy.json

# 2) 建索引模板（traceId/tenantId/serviceName/routeId 为 keyword，message 为 text）
curl -s -X PUT "http://localhost:9200/_index_template/apex-logs" \
  -H 'Content-Type: application/json' \
  -d @config/monitoring/elk/index-template.json
```

确认：
```bash
curl -s "http://localhost:9200/_index_template/apex-logs" | jq .
curl -s "http://localhost:9200/_ilm/policy/apex-logs-ilm" | jq .
```

> 宿主机需满足 `vm.max_map_count >= 262144`（一次性：`sudo sysctl -w vm.max_map_count=262144`），否则 ES 启动失败。

---

## 4. Kibana Data View

打开 `http://localhost:5601` → Stack Management → Data Views → Import，
导入 `config/monitoring/kibana/apex-logs-data-view.ndjson`（Data View 名 `apex-logs-*`，时间字段 `@timestamp`）。

内置检索建议（Discover）：
- 按链路串联单请求全链路日志：`traceId : "某 traceId"`（跨网关与各微服务）。
- 按租户隔离：`tenantId : "acme-corp"`。
- 网关访问日志：`routeId : "某路由" AND status >= 400`。

---

## 5. 健康检查与索引管理

```bash
# 集群健康
curl -s "http://localhost:9200/_cluster/health?pretty"

# 索引列表与占用
curl -s "http://localhost:9200/_cat/indices/apex-logs-*?v&s=index"

# 手动触发某索引进入下一 ILM 阶段（如立即删除过期索引）
curl -s -X POST "http://localhost:9200/apex-logs-2026.09.01/_ilm/retry"
```

---

## 6. 与 Loki/Grafana 的分工

| 维度 | Loki + Grafana | ELK |
| --- | --- | --- |
| 定位 | 实时流式、标签检索 | 结构化复杂检索、长期留存、聚合 |
| 写入 | Grafana Alloy 采集 | Filebeat 采集 JSON 日志 |
| 索引 | 标签（`service` 等） | 字段映射（`traceId` 等 keyword） |
| 查询 | LogQL | Kibana Discover / DSL |

两者读取同一份应用日志的不同切面，可按需选用；排障时推荐先以 ELK 按 `traceId` 定位，再到 Grafana 看实时流。

---

## 7. 生产注意事项

- 开启 `xpack.security`：将 `elasticsearch.yml` 中 `xpack.security.enabled` 置 `true`，配置 TLS 与内置用户凭据，并在 `filebeat.yml` / `kibana.yml` 补齐 `username`/`password`/`protocol: https`。
- 调大 `ES_JAVA_OPTS` 堆内存（默认 512m，生产按数据量上调）。
- 生产建议存储切换为多节点集群或托管 ES，关闭 `discovery.type: single-node`。

@author Apex Team
@since 1.3.1
