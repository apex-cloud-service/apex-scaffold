# 值班手册与故障处置 Runbook（On-Call Runbook）

> **性质**：Phase 7 加固配套运维文档（执行计划 §7.3「告警调优 + runbook、值班手册」）。
> **关联**：告警规则 `config/monitoring/prometheus/alert-rules.yml`；总览见
> `docs/05-operations/microservice-hardening-guide.md`。
> **适用**：网关 + 5 业务微服务（auth / system / workflow / office / file）生产运维。

---

## 1. 值班职责与分级

| 级别 | 触发 | 响应时限 | 处理人 |
|---|---|---|---|
| P0 critical | 全站不可用 / 网关全挂 / 数据库主库宕机 | 15 分钟内 | 主值班 + 备份 |
| P1 warning | 单服务不可用 / 熔断打开 / 连接池打满 | 1 小时内 | 主值班 |
| P2 info | 限流频繁 / 慢请求偏多 / 指标异常 | 次工作日 | 运维 |

**升级路径**：主值班 15 分钟未定位 → 备份值班 → 架构负责人 → 研发 Owner。

---

## 2. 通用处置流程（SOP）

```
告警触发
  │
  ├─ 1. 确认（0-5min）  在 Grafana/Prometheus 复核指标，确认非误报
  │
  ├─ 2. 定位（5-15min） 判断影响面：网关 / DB / Redis / MQ / 单服务
  │                     取一次 traceId 到 Zipkin 看链路断点
  │
  ├─ 3. 止血（15-30min）优先恢复：重启 / 扩容 / 灰度回滚 / Nacos 调参
  │
  ├─ 4. 根因（30min+）  抓 heapdump/gc.log/慢 SQL/线程栈，进标准排查
  │
  └─ 5. 复盘           归档到 docs/08-project-management/，更新本 Runbook
```

---

## 3. 常见告警处置卡（Runbook）

### 3.1 `CircuitBreakerOpen`（熔断器打开，critical）

- **现象**：`resilience4j_circuitbreaker_state{state="open"} == 1`。
- **定位**：确定熔断器名（`name` 标签）对应的下游服务；查该服务 `/actuator/health` 与连接池。
- **处置**：
  1. 若下游已宕机 → 按对应服务卡片处理；
  2. 下游恢复后熔断器自动半开→关闭，无需人工；
  3. 频繁误熔断 → 调整 Resilience4j 阈值（`failureRateThreshold` / `slowCallDurationThreshold`）。
- **恢复验证**：熔断器回到 `closed`，失败率回落至基线。

### 3.2 `SlowRequestsExcessive`（慢请求 >3s 过多，warning）

- **定位**：`jdbc_query_duration_seconds_bucket` 慢 SQL；`reactor_netty_connection_provider_active_connections` 排队。
- **处置**：定位慢接口 → 查 SQL 执行计划 / 加索引；连接池排队 → 扩容副本或调 pool-size。

### 3.3 `DBConnectionPoolUsageHigh`（连接池水位 >80%，warning）

- **定位**：`hikaricp_connections_active / hikaricp_connections_max`（prod 为 `druid_*`）。
- **处置**：查是否存在连接泄漏（`hikaricp_connections_timeout_total` 增长）；临时扩容池；必要时滚动重启。

### 3.4 Redis 不可达（`redisson_healthy == 0`）

- **定位**：Redis 节点连通性 / 主从切换。
- **处置**：恢复 Redis；期间读多写少服务依赖本地缓存降级（stale-while-revalidate），不阻断核心链路。

### 3.5 `SentinelFlowControlFrequent`（限流频繁，info）

- **定位**：`sentinel_blocked_qps` 对应 `resource`。
- **处置**：确认是流量突增还是规则过严 → Nacos 调整 `config/nacos/sentinel/{service}-flow-rules.json`（支持热更新）。

### 3.6 `GCPauseTimeCritical` / `MetaspaceUsageCritical`（JVM，critical）

- **定位**：`jvm_gc_pause_seconds{quantile="0.99"}` / Metaspace 使用率。
- **处置**：按 `microservice-hardening-guide.md` §2.1 扩容 JVM 堆/Metaspace；必要时水平扩容。

### 3.7 Outbox 堆积 / MQ 消费追赶

- **定位**：Outbox 表积压行数、`mq_consumer_lag`。
- **处置**：确认消费者存活；临时提高 `OUTBOX_PARALLELISM` / `OUTBOX_BATCH_SIZE`；DLQ 消息人工重投。

---

## 4. 灰度切流与一键回滚

- **切流**：`GrayRouteFilter` 按 `tenantId` 白名单 → 用户 ID 尾号百分比 → Header 强制，逐级 1%→10%→50%→100%。
- **回滚**：Nacos 将灰度比例置 0 / 删除灰度路由，流量立即回落单体；无需重启。
- **演练**：每次大版本发布前执行一次切流+回滚演练并归档记录。

---

## 5. 关键命令速查

```bash
# 服务健康（健康端口 8081/8082/8084/8085/8093）
curl -s http://127.0.0.1:8081/actuator/health   # auth
curl -s http://127.0.0.1:8093/actuator/health   # workflow
curl -s http://127.0.0.1:8085/actuator/health   # file

# 混沌演练（场景化，退出码 0=通过）
./scripts/test/chaos-fault-injection.sh --scenario auth-down --yes

# 压测
./scripts/bench/bench.sh http://127.0.0.1:8080 60s 8 400

# 监控栈
podman-compose -f config/monitoring/podman-compose.yml up -d

# 构建门禁（一律 mvnd）
mvnd -pl <service> -am verify
```

---

## 6. 交接清单（班次交接）

- [ ] 当前未闭环告警及进展
- [ ] 进行中的变更/灰度比例
- [ ] 已知风险与规避手段
- [ ] 待跟进复盘项