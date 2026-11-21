# WebSocket 站内信

当前网关 `/api/v1/messages/**` 属于 office，持久消息保存在 `off_message` 与 `off_message_recipient`。system 的系统通知读面与 SSE 是另一套能力（`/api/v1/notifications/**`），不承担此 WebSocket 链路。

## 配置与部署

office-service 必须配置 `OFFICE_JWT_PUBLIC_KEY_BASE64`（认证服务 RS256 公钥，X509 DER Base64）。仅在明确使用 HMAC 的过渡部署中配置 `OFFICE_JWT_SECRET`；同时配置公钥时以 RSA 为准。服务独立验签，不接受 `X-User-Id`、`X-Tenant-Id` 等客户端身份头。现有办公令牌工具的 `jwt.*` 配置仍须按原运行环境维护。

在 Podman Compose 的 `.env` 中设置 `OFFICE_MESSAGE_ALLOWED_ORIGINS`，填写允许的完整浏览器 Origin，以逗号分隔，例如 `https://app.example.com`。无 Origin、非白名单 Origin、缺少 WebSocket Upgrade 均拒绝。白名单不支持通配符，且不由 `CORS_ALLOWED_ORIGINS` 替代。

网关 `ROUTE_OFFICE_MESSAGE_URI` / `ROUTE_OFFICE_WS_URI` 缺省与 office 主路由同为 `lb://apex-office-service`（2026-11-07 起）；关闭 Nacos 的直连部署须显式注入两者，并指向网关可达且相同的 office 实例地址，不能只配置通用的 `ROUTE_OFFICE_URI`。外部生产入口使用 HTTPS/WSS，并让反向代理保留 Upgrade、Connection 和 Origin；代理空闲超时应大于 60 秒。ticket、消息发送 HTTP 请求及 WebSocket 必须到同一 office 实例。

票据出现在握手查询串中；反向代理、APM 和访问日志必须隐藏查询串中的 `ticket`，生产不得开启记录完整请求 URI 的 Web 调试日志。不要将完整访问 JWT 或 ticket 写入日志。

## 浏览器协议

1. 使用有效访问 JWT 调用 `POST /api/v1/messages/ws-ticket`，认证头为 `Authorization: Bearer <access-token>`。
2. 成功业务响应 `code=0`，`data` 包含 `ticket`、`expiresIn=30`；响应禁止缓存。
3. 立即建立 `wss://<gateway>/api/v1/messages/ws?ticket=<encodeURIComponent(ticket)>`。票据仅能消费一次，重连须重新签发。
4. 收到 `{"type":"message","messageId":"99"}` 后，通过现有 HTTP 详情接口获取消息。WebSocket 不传输消息正文。
5. 每约 20 秒收到 `{"type":"ping"}`，发送文本 `pong`。服务端按同一周期检查：距连接建立或最近一次 `pong` 超过 60 秒后，在下一次检查时关闭连接，正常调度下通常约 60–80 秒；连接最长一小时，客户端需重新取票重连。
6. 每次首次连接、重连以及提示后，通过 `GET /api/v1/messages/inbox` 与未读数量接口进行 HTTP 补偿，按消息 ID 去重。并发写入期间分页不提供快照语义，必要时重复查询近期页。

详情仅允许同租户发件人或接收人访问。收件、发件、已读和计数通过消息表租户 SQL 约束隔离。发送请求限制主题 200 字符、正文 16000 字符、接收人 1–100 个且每个 ID 为正数，重复接收人合并；每个收件人还必须属于当前租户、未删除且为 ACTIVE 的 `off_employee.user_id` 绑定。

## 事务与可靠性

消息主体和全部接收人通过同一个同步 JDBC 本地事务写入，并在专用 JDBC 调度器执行。只有事务完成后才发布本实例在线提示。推送失败不改变已经提交的发送结果。

连接表最多 1024 条连接、票据最多 4096 个，每连接通知队列最多 64 条，队列溢出会关闭慢连接。断开连接释放队列；票据 30 秒有效，签发时清理过期项，停机清理注册表。

这是单实例内存提示，不提供跨实例广播、消息提示持久化、客户端确认或重放。进程崩溃、断线、过载，以及提交完成到事件发出之间的窗口均可能丢失实时提示；数据库中的消息仍由 HTTP 查询补偿。多实例可靠实时分发需要另行设计共享票据、跨实例事件和持久投递机制，不能仅增加实例数量后声称实时必达。

## 验证范围

本地测试覆盖三层证据：`MessageDatabaseWebSocketTest` 使用真实 H2（MySQL mode）、MyBatis Mapper、租户插件和 JDBC 事务，经 HTTP 发送后验证提交事件与 WebSocket 提示、数据库约束失败时全部回滚且不发提示、已读时间与未读数，以及断线后 HTTP 收件箱补偿；`MessageGatewayDatabaseIntegrationTest` 启动独立 gateway 与 office Netty 端口，使用实际 Gateway 路由及生产身份过滤器，验证 HTTP/WS 转发到同一 office、伪造身份头清洗、Origin 与票据重放拒绝，以及提交后提示和收件箱查询。

`MessageSocketLifecycleTest` 通过真实网络帧检查 UTF-8 1024 字节边界、1025 字节帧以 1009 关闭、缺少 pong 的策略关闭、持续应答连接的寿命到期、通知队列溢出及连接释放；心跳测试注入较短周期，不代表生产 20 秒/60 秒/一小时参数已实测。注册表测试另行覆盖单次票据并发消费、64 条队列容量、1024 条连接容量及租户/用户隔离。原有 Mapper 替身事务测试仍保留，但不再是唯一数据库证据。

相关实现修复包括握手映射与 WebSocket 适配器优先级、消息与收件记录默认持久化字段、收件箱最新优先排序，以及入站结束和出站关闭的竞争处理。上述本地闭环不替代目标 MySQL/PostgreSQL 迁移与生产数据库、多实例、HTTPS/WSS 反向代理或全服务部署验收。完整门禁结果见[交付报告](../08-project-management/reports/infrastructure-websocket-delivery-report.md)。
