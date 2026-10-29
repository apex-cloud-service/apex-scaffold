# 架构演进与可观测性分析

> 版本：v1.3.1（2026-10-28）
> 主题：① 是否改造成微服务架构；② 是否增加监控 / 链路追踪等基础设施

## 一、微服务架构演进分析

### 1.1 现状评估

Apex Scaffold 当前为**模块化单体**（Modular Monolith）：

| 维度 | 现状 |
|---|---|
| 代码组织 | Maven 多模块（common → domain → infrastructure → application → interfaces），依赖单向、边界清晰 |
| 部署形态 | 单一可执行 JAR（spring-boot），无状态（JWT + Redis 会话） |
| 响应式 | 全链路 WebFlux，天然高并发、低资源占用 |
| 扩展点 | 通用能力全部内聚（认证/RBAC/系统管理/文件/缓存/MQ/监控） |

### 1.2 是否拆分？—— 当前结论：**不拆，保持模块化单体**

**不建议现在拆分的理由：**

1. **定位匹配**：脚手架面向中小企业，单体足够支撑早期业务规模（数百 QPS、单库单实例），拆分收益尚未出现；
2. **复杂度代价**：微服务引入服务发现、配置中心、分布式事务、网关、分布式链路排查等系统性成本，对中小团队是负担而非助力；
3. **领域边界已就绪**：DDD 五层 + 模块化已提供清晰的领域边界，未来按域拆分时只需"搬包 + 建独立进程"，无需重构业务代码；
4. **响应式单体吞吐足够**：WebFlux 单体能承载的量级远超多数业务早期需求；
5. **演进成本可控**：从模块化单体演进为微服务，比从"大泥球"单体演进容易一个数量级。

**触发拆分的信号（满足任一可考虑）：**
- 独立团队并行开发且发布节奏冲突严重（康威定律驱动）；
- 单库成为瓶颈，需要分域拆分数据（此时优先考虑分库/读写分离，而非立即微服务）；
- 存在明确的独立伸缩需求（如流量突变的独立模块）；
- 需要多技术栈混编（概率低，本脚手架技术栈统一）。

### 1.3 演进路径（预留的微服务友好能力）

```
模块化单体（现在）
   │  按领域包边界拆分（domain/infrastructure 依模块独立进程化）
   ▼
按域微服务（未来，按需）
   ├─ 网关层：可直接用 Spring Cloud Gateway（WebFlux 同源）
   ├─ 配置/注册：Nacos / Consul（配置已外置）
   ├─ 服务间调用：WebClient（响应式天然契合）
   ├─ 分布式链路：Zipkin 已接入，TraceId 贯穿（见第二节）
   └─ 分布式事务：优先规避（事件驱动 + Outbox 模式，项目已有 domain event outbox 表）
```

项目已具备的微服务友好基础：无状态认证（JWT + Redis）、统一响应/错误码、幂等支持（IdempotentService）、
事件表（sys_domain_event_outbox）、配置外置（环境变量覆盖）、独立的 Actuator 管理端口。

## 二、可观测性现状盘点（已具备）

**结论先行：本项目已内置一套完整、可上生产的可观测性基础设施**，无需从零增加。

| 能力 | 实现 | 状态 |
|---|---|---|
| 健康检查 | Actuator `/actuator/health`（独立端口 8081，K8s 探针启用） | ✅ |
| 指标采集 | Micrometer + `/actuator/metrics` + `/actuator/prometheus` | ✅ |
| 链路追踪 | Micrometer Tracing（Brave 桥接）+ Zipkin 上报（`trace.zipkin-url` 可配、采样率可调） | ✅ |
| 日志贯穿 | TraceId（MDC + Reactor Context）+ `TraceFilter` 注入 X-Trace-Id | ✅ |
| 请求指标 | `ApiGatewayFilter`（响应时间/请求计数/错误计数，`apex.api.*`） | ✅ |
| 安全指标 | `SecurityMetricsRecorder`（限流/防重放事件指标） | ✅ |
| 异常监控 | `ExceptionMonitor`（业务异常统计） | ✅ |
| 限流治理 | Sentinel（含 Prometheus exporter）+ Resilience4j CircuitBreaker | ✅ |
| 审计 | 操作日志（sys_operation_log）、登录日志（sys_login_log）、API 调用日志（sys_api_call_log） | ✅ |

## 三、可观测性增强建议（部署侧配套，非代码缺口）

代码侧已完备，以下为**运维/部署配套**建议（已随包落地于 `config/monitoring/`）：

1. **监控大盘**：`config/monitoring/grafana/apex-dashboard.json` —— Grafana 官方仪表盘
   （覆盖 JVM/HTTP/连接池/Sentinel 指标，指标口径 `apex_api_*`），podman-compose 一键导入；
2. **告警规则**：`config/monitoring/prometheus/alert-rules.yml` —— 10 条预置规则
   （服务宕机、接口 5xx 率、P95 延迟、JVM 堆、连接池水位、Redis/Sentinel）；
3. **日志采集**：对接 ELK / Loki（结构化日志 + TraceId 字段即可关联查询）；
4. **采样率调优**：默认 0.1（10%），高 QPS 可再下调，低流量可上调至 1.0；
5. **管理端口安全**：`management.server.port=8081` 独立端口，建议仅内网/服务网格暴露，生产关闭 prometheus 之外的多余端点。

> 一键拉起监控栈：`podman-compose -f config/monitoring/podman-compose.yml up -d`
> （Prometheus + Grafana + Zipkin，自动置备数据源与大盘，详见 `config/monitoring/README.md`）

## 四、结论

- **微服务**：现阶段保持模块化单体（边界已就绪、演进路径清晰），不建议拆分；按第二节触发信号按需演进。
- **监控与链路追踪**：已具备（Micrometer + Prometheus + Zipkin/Brave + TraceId + Sentinel/Resilience4j），
  部署侧配套（大盘/告警）已随包落地于 `config/monitoring/`，仅剩日志采集（ELK/Loki）按需对接。
