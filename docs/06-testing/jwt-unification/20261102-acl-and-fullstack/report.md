# 未验证事项最终收口报告：ACL、并发基线、全栈冒烟

> **历史快照**：本文记录某一时点的状态，模块数、端口、路径与类名以当前代码为准，不作为现状依据。

- 范围：接续 [20261102-live-verification](../20261102-live-verification/report.md) §4 的剩余未验证项
- 执行日期：2026-11-02
- 环境：macOS、JDK `21.0.13.0+9-tem`、mvnd `2.0.0-rc-3`、Docker（EMQX 5.8.6 / MySQL 9.0 / Redis 7）
- 状态：**3 项已收口，并发现并修复 4 个仅在真实启动时暴露的缺陷**

## 1. EMQX broker 层 ACL 强制隔离（原「仅应用层策略」缺口）

### 发现的问题

初次验证发现关键缺口：**EMQX 未配置授权钩子时，跨租户订阅在 broker 层畅通无阻**（实测租户 A 订阅租户 B topic 收到了消息）。原有代码只提供认证钩子（`/internal/v1/iot/mqtt/auth` 返回 `authenticated`），**没有授权（ACL）端点**，`authorizeTopic` 虽已实现但无 HTTP 入口。

### 交付

| 变更 | 内容 |
| --- | --- |
| `MqttAclRequest` | EMQX HTTP Authorize 请求 DTO（username / topic / action） |
| `IotInternalHandler.mqttAcl` | 按 `username = {tenantCode}/{deviceKey}` 解析身份，调 `authorizeTopic` 判定，返回 EMQX 规范 `{"result":"allow"\|"deny"}`；解析失败/越权一律 deny（fail-closed） |
| 路由 | `POST /internal/v1/iot/mqtt/acl` |
| `MqttTopicAclEnforcementTest` | **3/3 通过**：15 类越权订阅矩阵（跨租户/跨设备/通配符注入/空段/非法方向/空参数）全部拒绝；合法命名空间放行；真实 broker 越权订阅验证 |

### 真实 broker 验证（决定性证据）

以 HTTP 授权钩子启动 EMQX 并对接真实运行的 integration-service：

```bash
docker run -d -p 1884:1883 \
  -e EMQX_AUTHORIZATION__SOURCES__1__TYPE=http \
  -e EMQX_AUTHORIZATION__SOURCES__1__URL="http://host.docker.internal:8094/internal/v1/iot/mqtt/acl" \
  -e EMQX_AUTHORIZATION__NO_MATCH=deny \
  emqx/emqx:5.8
```

EMQX 日志实证越权订阅被 broker 拒绝：

```
tag: AUTHZ, msg: authorization_permission_denied,
  topic: apex/tenant-acl-b/device-acl-b/up, action: SUBSCRIBE(Q1), source: http
```

端点直测：合法 topic → `{"result":"allow"}`；越权 topic → `{"result":"deny"}`。

**结论**：broker 层强制隔离闭环。生产部署只需把 EMQX 授权源指向该端点（已写入 API 契约文档）。

## 2. 多连接并发吞吐基线（原「仅单连接」缺口）

`IotMqttConcurrencyBaselineTest`：10 设备并发 × 100 消息 = 1000 条，QoS 1，单共享订阅方消费。

```
【并发基线】broker=127.0.0.1:1885, 设备数=10, 每设备=100, 总消息=1000, QoS=1,
           耗时=1.102s, 吞吐=907 msg/s
```

| 场景 | 消息数 | 耗时 | 吞吐 | 丢失 |
| --- | --- | --- | --- | --- |
| 单连接单设备 | 1000 | 3.022s | **331 msg/s** | 0 |
| 10 设备并发 | 1000 | 1.102s | **907 msg/s** | 0 |

并发上行吞吐约为单连接的 **2.7 倍**（共享订阅 + 多连接并行收益）。**仅容量参考，不构成 SLA。**

## 3. 全栈冒烟（真实进程 + 真实 MySQL/Redis）

启动真实 `apex-integration-service`（`java -jar`，50.5 秒就绪）对接 MySQL 9 + Redis 7：

| 冒烟项 | 结果 |
| --- | --- |
| 服务启动 | ✅ `Started IntegrationServiceApplication in 50.498 seconds` |
| Flyway 迁移 | ✅ 真实 MySQL 中 `iot_%` 表 **5 张**已建立 |
| 未认证访问受保护端点 | ✅ **401**（安全链生效） |
| ACL 端点合法 topic | ✅ `{"result":"allow"}` |
| ACL 端点越权 topic | ✅ `{"result":"deny"}` |
| EMQX 实际调用 ACL 钩子 | ✅ 日志确认拒绝越权订阅 |

## 4. 真实启动暴露并修复的 4 个缺陷

这些缺陷**在单元测试与模块测试中均无法发现**，只有真实进程启动才暴露：

| # | 缺陷 | 根因 | 修复 |
| --- | --- | --- | --- |
| 1 | `CredentialReferenceResolver` 无 Bean | 类上只有 `@RequiredArgsConstructor`，**缺 `@Component`** | 补 `@Component` |
| 2 | `RemoteApiProperties` 无 Bean | `RemoteApiClientConfig` 是 `@AutoConfiguration` 但未 `@EnableConfigurationProperties`，依赖消费方 `@ConfigurationPropertiesScan` 偶然覆盖 | 显式 `@EnableConfigurationProperties(RemoteApiProperties.class)` |
| 3 | `ApiCallLogService` 无 Bean | 同上，`@Service` 组件在 `...remote.logging` 包，消费方未扫描该包 | `@ComponentScan("...remote.logging")`（精确限定，避免与 `@ConfigurationProperties` 重复注册——实测扫整个包会报 "required a single bean, but 2 were found"） |
| 4 | 数据源启动失败 | (a) `application.yml` 使用标准 `spring.datasource` 但 classpath 有 dynamic-datasource，缺 `primary`；(b) MySQL 连接缺 `allowPublicKeyRetrieval` | 补 `spring.datasource.dynamic.primary` 与 master 数据源声明 |

**第 2、3 项是共享设施层的通用缺陷**——任何未显式扫描 `com.apex.scaffold.infrastructure.remote` 的消费方都会启动失败。已修复，受益范围超出 integration-service。

## 5. 门禁结果

| 项 | 结果 |
| --- | --- |
| `MqttTopicAclEnforcementTest` | **3/3** |
| `IotMqttConcurrencyBaselineTest` | **1/1**（907 msg/s，0 丢失） |
| `IotMqttBrokerLiveTest`（真实 EMQX） | 3/3 |
| `IotMqttThroughputBaselineTest` | 1/1（331 msg/s） |
| `HttpConnectorLiveEndpointTest` | 6/6 |
| `TenantSqlRewriteDialectTest` | 5/5 |
| 三模块及上游 `test` | **BUILD SUCCESS** |
| `spotless:check` / `git diff --check` / doc-links | 全部通过 |

## 6. 剩余未验证项（如实保留，均有明确原因）

| 项 | 原因 | 建议 |
| --- | --- | --- |
| **六服务全栈 Compose 冒烟** | 需要构建 32 个服务的镜像（含前端 `apex-admin-web` 镜像，前端不在本仓），单次构建为数小时 | 部署阶段由 CI 构建镜像后执行；本次已验证单服务真实启动链路作为替代 |
| **第三方真实业务系统联调** | 微信/创蓝/第三方 REST 均为沙箱或模拟，无客户真实系统可用 | 交付前按客户实际系统联调 |
| **前端页面** | 按用户明确要求排除 | 独立前端仓库实施 |
| **超大规模压测**（100+ 设备） | 本次 10 设备已达 907 msg/s；更大规模需专用压测环境与长时间运行 | 容量规划阶段按需执行 |
