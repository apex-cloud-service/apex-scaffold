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

编排定义：`podman-compose.infra.yml`（`elasticsearch` / `kibana` / `filebeat` 服务）。
数据卷：`es-data` 持久化 ES 数据，`filebeat-data` 挂载到 `/usr/share/filebeat/data`，持久化 registry 与采集进度。
应用服务统一设置 `LOG_PATH=/app/logs`，分别将宿主机 `./logs/<service>` 挂载到 `/app/logs`；Filebeat 与 Alloy 均将宿主机 `./logs` 只读挂载到 `/var/log/apex-scaffold`。启动前准备各服务日志子目录并确保镜像非 root 用户 `appuser` 可写。

---

## 2. 日志管道

```
应用 logback JSON_FILE_OUTPUT → /app/logs/*-json.log
        ↓ bind mount：宿主 logs/<service>/*-json.log
Filebeat /var/log/apex-scaffold/**/*-json.log（递归 filestream + ndjson 解析）
        ↓
Elasticsearch（Filebeat 按日期生成 apex-logs-yyyy.MM.dd，ILM 在索引创建满 30 天后删除）
        ↓
Kibana Discover / 仪表盘
```

应用侧由 `JSON_FILE_OUTPUT`（LogstashEncoder，`includeMdc=true`）输出每行一条完整 JSON，
含字段：`@timestamp`、`level`、`logger_name`、`message`、`traceId`、`serviceName`、`service`，
以及 MDC 透传的 `tenantId` / `requestId` / `userId` 等。SkyWalking Toolkit Provider 输出真实探针字段 `TID`，Filebeat 在入库前将其重命名为 `tid`，并优先于同名 MDC 字段；业务 `traceId` 保持独立。无 Agent 时 Provider 的占位值不代表有效追踪，真实关联仍需 Agent/OAP 运行验收。应用 JSON 基础契约由 `LogJsonSchemaTest` 守护。

---

## 3. 首次启动（索引模板 + ILM）

Filebeat 配置已关闭自带模板/ILM（`setup.template.enabled: false`、`setup.ilm.enabled: false`），
统一使用托管模板与策略。必须在首次启动 Filebeat 或投放日志前，待 ES 就绪后执行一次：

```bash
# 1) 建 ILM 策略（日期索引不使用 rollover，创建满 30 天后删除）
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

# 查看索引的 ILM 阶段与失败原因
curl -s "http://localhost:9200/apex-logs-2026.09.01/_ilm/explain"

# 仅在修复失败原因后重试失败的 ILM 步骤；不会强制进入下一阶段
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

两者挂载同一宿主日志根目录。Filebeat 与 Alloy 均递归发现服务子目录中的 `*-json.log`；Alloy 通过 `local.file_match` 发现文件，从 JSON 提取 `service` 与 `level` 标签，追踪标识保留在日志内容中，避免高基数标签。

---

## 7. 生产注意事项

- 开启 `xpack.security`：将 `elasticsearch.yml` 中 `xpack.security.enabled` 置 `true`，配置 TLS 与内置用户凭据，并在 `filebeat.yml` / `kibana.yml` 补齐 `username`/`password`/`protocol: https`。
- 调大 `ES_JAVA_OPTS` 堆内存（默认 512m，生产按数据量上调）。
- 生产建议存储切换为多节点集群或托管 ES，关闭 `discovery.type: single-node`。

## 8. 2026-10-13 运行验收记录

本次检查时，Podman rootful 连接中既有容器均已停止，rootless 连接无容器；旧设施包含 ES 7.14.1、OAP 9.2.0，不能作为目标版本验收。使用独立网络 `apex-observe-acceptance-20261013` 和 `apex-accept-*-20261013` 容器进行验证，不挂载旧数据卷。日志为临时合成样本，通过复制到采集目录投放，未直接调用 ES/Loki 写入日志接口；因此仅证明采集和查询管道，不证明真实应用或探针关联。

| 项目 | 真实结果 |
| --- | --- |
| ES 版本 | `GET http://127.0.0.1:19200/` 返回 `8.13.4`，build hash `da95df118650b55a500dcc181889ac35c6d8da7c` |
| Filebeat 版本/配置 | `filebeat version` 返回 `8.13.4`；仓库配置 `filebeat test config -strict.perms=false` 返回 `Config OK` |
| 字段转换 | 样本含 `TID=probe-tid-20261013`、`tid=mdc-should-be-replaced`、`traceId=business-trace-20261013`；ES 检索返回 1 条，`tid=probe-tid-20261013`、无 `TID`，业务 `traceId` 不变 |
| 递归采集 | ES 的 `log.file.path` 为 `/var/log/apex-scaffold/acceptance/nested/acceptance-json.log` |
| 模板与 ILM | 仓库策略及模板 PUT 均返回 `acknowledged=true`；模板安装后 Filebeat 以仅验收用输出索引覆盖写入 `apex-logs-acceptance-2026.10.13`，`tid/traceId=keyword`、`message=text`；`_ilm/explain` 为 `managed=true`、策略 `apex-logs-ilm`、hot 阶段 complete |
| 初始化顺序反例 | 首次样本先于模板安装，默认日期索引 `apex-logs-2026.10.13` 使用动态 text 映射且 `managed=false`。安装模板不追溯修复既有索引；本次未删除或改写该索引，以新索引复验。部署时必须先初始化再启动采集 |
| Alloy/Loki | 镜像二进制分别返回 `v1.0.0`、`3.0.0`。仓库 Alloy 配置成功加载；仓库 Loki 配置校验通过并重启验收实例，LogQL `{service="apex-observe-acceptance"} |= "repository-loki-config"` 返回 1 条。标签包含 `cluster=local/env=dev/job=apex-scaffold-logs/level=INFO/service`，追踪标识仅在内容中 |
| OAP | GraphQL `{version}` 返回 `9.7.0-066457b`；`/health`、`/healthcheck` 均为 404，`checkHealth` 报 `health-checker missing`。编排改用 GraphQL 版本响应探针，并在目标镜像内执行通过；此探针仅检查查询服务 |

复查命令（隔离验收端口，不能直接当作正式部署端口）：

```bash
curl -sS 'http://127.0.0.1:19200/apex-logs-acceptance-2026.10.13/_search?q=tid:probe-tid-20261013'
curl -sS 'http://127.0.0.1:19200/apex-logs-acceptance-2026.10.13/_ilm/explain'
curl -sS -X POST http://127.0.0.1:12801/graphql \
  -H 'Content-Type: application/json' -d '{"query":"{version}"}'
curl -sS -G http://127.0.0.1:13100/loki/api/v1/query_range \
  --data-urlencode 'query={service="apex-observe-acceptance"} |= "repository-loki-config"' \
  --data-urlencode 'since=1h'
```

未完成：仓库未找到 Agent JAR，且无带探针运行的应用；`queryTrace(traceId:"probe-tid-20261013")` 返回空 spans，样本标识本身也是合成值。真实 Toolkit `TID`、跨服务 `sw8`、OAP trace/拓扑、SkyWalking UI、Kibana Discover、Grafana UI、实际应用 bind mount 链路及 30 天删除执行均未验收。本次未运行 Java 构建或全仓回归，不将接口可用或 ILM hot 阶段等同于这些项目通过。

验收容器在验证后停止并保留，网络与数据均未删除；复查前仅启动本次验收实例，LogQL 应使用覆盖样本日期的绝对时间范围。正式数据与既有容器未改动。

@author Apex Team
@since 1.3.1
