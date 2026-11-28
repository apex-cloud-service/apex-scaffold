<a id="apex-scaffold-时序图集"></a>

# Apex Scaffold 时序图集

> **版本**: v1.0  
> **创建日期**: 2026-09-09  
> **格式**: Mermaid 语法

本目录包含业务时序图、状态机和架构图的 Mermaid 源文件。以下清单按实际文件登记；本轮没有重新渲染或验证这些源文件。旧图中的缓存和登录机制可能属于历史设计，当前行为见配套文档。

---

## 📋 时序图清单

| 编号 | 文件 | 主题（按文件名） |
| --- | --- | --- |
| 30 | [30_user_login_sequence.mmd](30_user_login_sequence.mmd) | user_login_sequence |
| 31 | [31_permission_check_sequence.mmd](31_permission_check_sequence.mmd) | permission_check_sequence |
| 32 | [32_workflow_approval_sequence.mmd](32_workflow_approval_sequence.mmd) | workflow_approval_sequence |
| 33 | [33_file_upload_sequence.mmd](33_file_upload_sequence.mmd) | file_upload_sequence |
| 70 | [70_workflow_engine_state_machine.mmd](70_workflow_engine_state_machine.mmd) | workflow_engine_state_machine |
| 71 | [71_graceful_shutdown_state_machine.mmd](71_graceful_shutdown_state_machine.mmd) | graceful_shutdown_state_machine |
| 72 | [72_multi_tenant_isolation_architecture.mmd](72_multi_tenant_isolation_architecture.mmd) | multi_tenant_isolation_architecture |
| 73 | [73_event_driven_architecture.mmd](73_event_driven_architecture.mmd) | event_driven_architecture |
| 74 | [74_webflux_reactive_call_chain.mmd](74_webflux_reactive_call_chain.mmd) | webflux_reactive_call_chain |
| 75 | [75_message_queue_reliability_architecture.mmd](75_message_queue_reliability_architecture.mmd) | message_queue_reliability_architecture |
| 76 | [76_data_scope_filter_sequence.mmd](76_data_scope_filter_sequence.mmd) | data_scope_filter_sequence |
| 77 | [77_multi_level_cache_architecture.mmd](77_multi_level_cache_architecture.mmd) | multi_level_cache_architecture |
| 78 | [78_auto_retry_mechanism_sequence.mmd](78_auto_retry_mechanism_sequence.mmd) | auto_retry_mechanism_sequence |
| 79 | [79_datasource_switch_architecture.mmd](79_datasource_switch_architecture.mmd) | datasource_switch_architecture |
| 80 | [80_audit_log_async_batch_sequence.mmd](80_audit_log_async_batch_sequence.mmd) | audit_log_async_batch_sequence |
| 81 | [81_api_gateway_request_flow_sequence.mmd](81_api_gateway_request_flow_sequence.mmd) | api_gateway_request_flow_sequence |
| 82 | [82_sentinel_circuit_breaker_flow.mmd](82_sentinel_circuit_breaker_flow.mmd) | sentinel_circuit_breaker_flow |
| 83 | [83_resilience4j_state_machine.mmd](83_resilience4j_state_machine.mmd) | resilience4j_state_machine |
| 84 | [84_rate_limit_hotspot_sequence.mmd](84_rate_limit_hotspot_sequence.mmd) | rate_limit_hotspot_sequence |
| 85 | [85_auto_retry_architecture.mmd](85_auto_retry_architecture.mmd) | auto_retry_architecture |
| 86 | [86_audit_log_architecture.mmd](86_audit_log_architecture.mmd) | audit_log_architecture |
| 87 | [87_api_gateway_architecture.mmd](87_api_gateway_architecture.mmd) | api_gateway_architecture |
| 88 | [88_webflux_reactive_architecture.mmd](88_webflux_reactive_architecture.mmd) | webflux_reactive_architecture |
| 89 | [89_multi_tenant_data_isolation_sequence.mmd](89_multi_tenant_data_isolation_sequence.mmd) | multi_tenant_data_isolation_sequence |
| 90 | [90_login_biometric_mfa.mmd](90_login_biometric_mfa.mmd) | login_biometric_mfa（v1 历史，参与者/端点已退役，保留不改） |
| 90 | [90_login_biometric_mfa_v2.mmd](90_login_biometric_mfa_v2.mmd) | login_biometric_mfa_v2（2026-11-28 BE-066 修订：对齐 apex-auth-service 主链与 MFA 现行端点） |
| 91 | [91_sso_keycloak_flow.mmd](91_sso_keycloak_flow.mmd) | sso_keycloak_flow（v1 历史，保留不改） |
| 91 | [91_sso_keycloak_flow_v2.mmd](91_sso_keycloak_flow_v2.mmd) | sso_keycloak_flow_v2（2026-11-28 BE-066 修订：OIDC 客户端归属 apex-system，令牌端点兜底 auth-service） |
| 92 | [92_workflow_submit_approve.mmd](92_workflow_submit_approve.mmd) | workflow_submit_approve |
| 93 | [93_rate_limit_sentinel.mmd](93_rate_limit_sentinel.mmd) | rate_limit_sentinel |
| 94 | [94_cache_consistency.mmd](94_cache_consistency.mmd) | cache_consistency |
| 95 | [95_message_mq_publish_consume.mmd](95_message_mq_publish_consume.mmd) | message_mq_publish_consume |
| 133 | [133_iot_device_telemetry_command_sequence.mmd](133_iot_device_telemetry_command_sequence.mmd) | iot_device_telemetry_command_sequence（2026-11-22 BE-047 新增） |
| 138 | [138_knowledge_rag_chat_sequence.mmd](138_knowledge_rag_chat_sequence.mmd) | knowledge_rag_chat_sequence（2026-11-22 BE-047 新增） |
| 145 | [145_office_approval_cancel_sequence.mmd](145_office_approval_cancel_sequence.mmd) | office_approval_cancel_sequence（2026-11-22 BE-047 新增） |

当前统一授权时序见 [业务流程文档](../../architecture/business-flows.md)，配置见 [统一授权配置](../../05-operations/configuration.md#认证中心统一授权)。主应用保留 `/api/v1/auth/*`，认证中心使用 `/auth/v1/*`，二者独立启动。

认证相关既有 SVG 位于父目录：[安全链](../84_security_filter_chain.svg)、[认证中心](../85_auth_center_architecture.svg)、[Token 生命周期](../86_token_lifecycle_management.svg)。本目录没有它们的同名 `.mmd` 源文件；84–86 的 Mermaid 文件主题分别是限流、重试和审计，不能按编号关联。

---

## 🚀 使用方式

### 方式 1: 在 Markdown 中渲染（推荐）

大多数现代 Markdown 编辑器和平台（GitHub、GitLab、Typora、VS Code）都原生支持 Mermaid：

```markdown
# 标题

\`\`\`mermaid
sequenceDiagram
    participant A as Alice
    participant B as Bob
    A->>B: Hello Bob
    B-->>A: Hi Alice
\`\`\`
```

### 方式 2: 转换为 SVG

使用 Mermaid CLI 将 `.mmd` 文件转换为 SVG：

```bash
# 安装 Mermaid CLI
npm install -g @mermaid-js/mermaid-cli

# 转换单个文件
mmdc -i 30_user_login_sequence.mmd -o 30_user_login_sequence.svg

# 批量转换
for file in *.mmd; do
  mmdc -i "$file" -o "${file%.mmd}.svg"
done
```

### 方式 3: 在线编辑器

访问 [Mermaid Live Editor](https://mermaid.live/) 在线编辑和预览。

---

## 📊 时序图详细说明

### 30. 用户登录认证时序图

该源文件保留历史登录设计，图内固定 TTL 和自动续期描述不作为当前认证中心契约。主应用登录为 `POST /api/v1/auth/login`；认证中心登录为 `POST /auth/v1/login`，刷新为 `POST /auth/v1/refresh`。认证中心刷新撤销旧 Refresh Token 并返回新 Token 对，客户端必须保存新的 Token 对。

认证中心登出为 `POST /auth/v1/logout/{userId}`，要求 Bearer ACCESS Token，路径用户必须是当前认证用户，租户来自可信认证上下文，成功返回 HTTP 200 空响应体。路由与响应契约见 [认证接口路由源码](../../../apex-services-bom/apex-auth-service/src/main/java/com/apex/scaffold/authservice/interfaces/router/AuthenticationRouter.java)。

---

### 31. RBAC 权限校验时序图

该源文件描述本地 RBAC 和 1 小时权限缓存，属于历史设计，不能用于配置当前统一授权。当前 `apex.features.auth-center-permission` 默认关闭，关闭时保留本地授权；开启时使用主租户/用户到中心租户/用户的显式映射，通过 HTTPS 查询 `/auth/v1/authorization/users/{userId}`。

远程读取使用专用账号 ACCESS Token；认证中心校验读取账号和目标账号同租户、启用、未锁定、未软删除。主应用只显式映射 `SELF`/`ALL` 数据范围。统一授权缓存默认关闭，TTL 为 0–30 秒，键包含主租户和主用户，快照独立校验 `expiresAt`。远程失败不回退本地权限或过期缓存；3 秒超时和 Resilience4j 默认熔断器用于保护远程调用。

---

### 32. 工作流审批时序图

**核心流程**:
1. 用户发起审批申请
2. 创建流程实例（解析 BPMN 流程定义）
3. 创建待办任务（计算任务分配对象）
4. 发送领域事件（TaskCreatedEvent）
5. 异步发送通知（站内通知 + 邮件）
6. 审批人处理任务（同意/驳回/转办）
7. 流程引擎计算下一个节点
8. 创建下一个待办任务或结束流程
9. 流程监控与查询

**涉及组件**:
- WorkflowService
- WorkflowEngine
- TaskService
- NotificationService
- RocketMQ
- Redis
- Database

**关键配置**:
- `ROCKETMQ_NAMESERVER`: RocketMQ 地址
- `APEX_MQ_CONSUMER_ENABLED`: 是否启用消费者

**状态机**:
```
PENDING → RUNNING → COMPLETED / REJECTED
```

**领域事件**:
- `TaskCreatedEvent`: 任务创建
- `ProcessRejectedEvent`: 流程驳回
- `ProcessCompletedEvent`: 流程完成
- `TaskTimeoutEvent`: 任务超时

---

### 33. 文件上传时序图

**核心流程**:
1. 前端文件校验（大小、类型）
2. 服务端二次校验
3. 生成唯一文件名（UUID + timestamp）
4. 选择存储策略（Local / MinIO / 火山引擎 TOS）
5. 调用 SPI 接口上传文件
6. 内容审核（百度 AI - 可选）
7. 保存文件记录到数据库
8. 缓存文件信息到 Redis
9. 发送文件上传事件（异步生成缩略图）
10. 返回文件 URL

**下载流程**:
1. 查询文件信息（缓存优先）
2. 检查下载权限
3. 调用 SPI 获取文件流
4. 记录下载日志
5. 返回文件流

**预签名 URL**:
- 生成临时访问链接（有效期 5 分钟）
- 无需后端鉴权，直接访问存储服务

**涉及组件**:
- StorageService
- StorageProvider (SPI)
- LocalProvider / MinIOProvider
- AuditService (百度 AI)
- RocketMQ
- Redis
- Database

**关键配置**:
- `STORAGE_DEFAULT_TYPE`: 存储类型（local/minio/volcengine）
- `STORAGE_BUCKET_NAME`: Bucket 名称
- `FILE_MAX_SIZE_BYTES`: 单文件大小上限（10MB，apex-file-service 文件域）
- `BAIDU_IMAGE_MODERATION_API_KEY`: 百度图片审核 API Key
- `BAIDU_TEXT_MODERATION_API_KEY`: 百度文本审核 API Key

---

## 🎨 Mermaid 语法参考

### 参与者定义

```mermaid
participant A as 客户端
participant B as 服务端
```

### 消息类型

| 语法 | 说明 | 示例 |
|------|------|------|
| `A->>B:` | 同步调用 | `A->>B: 调用接口` |
| `A-->>B:` | 异步返回 | `A-->>B: 返回结果` |
| `A->>+B:` | 激活生命线 | `A->>+B: 开始处理` |
| `B-->>-A:` | 结束生命线 | `B-->>-A: 处理完成` |

### 条件分支

```mermaid
alt 条件 1
    A->>B: 分支 1
else 条件 2
    A->>B: 分支 2
end
```

### 注释

```mermaid
Note right of A: 右侧注释
Note left of A: 左侧注释
Note over A,B: 跨越注释
```

---

## 🔄 更新指南

### 何时更新时序图

1. **核心流程变更** - 业务逻辑发生重大调整
2. **新增交互组件** - 引入新的服务或中间件
3. **安全机制调整** - 认证、授权、审计流程变化
4. **性能优化** - 缓存策略、异步处理等优化

### 更新步骤

1. 修改对应的 `.mmd` 文件
2. 更新版本号和修改日期
3. 运行转换命令生成新的 SVG
4. 提交 Git 并说明修改内容

---

## 📚 相关文档

- [架构文档索引](../../02-architecture/architecture-index.md) - 既有图表及验证边界
- [DDD 四层架构](../../architecture/01-logical-architecture-detail.md)
- [工作流引擎指南](../../01-guides/workflow-guide.md)
- [存储 SPI 指南](../../01-guides/spi-plugin-guide.md)

---

## 🆘 常见问题

### Q1: 如何在 GitHub 上预览 Mermaid 图表？

**A**: GitHub 原生支持 Mermaid。直接在 Markdown 中使用 ````mermaid` 代码块即可自动渲染。

### Q2: 为什么选择 Mermaid 而不是手绘 SVG？

**A**: Mermaid 的优势：
- ✅ 纯文本格式，易于版本控制
- ✅ 语法简单，学习成本低
- ✅ 自动布局，无需手工调整
- ✅ 易于维护和更新

### Q3: 如何将 Mermaid 转换为深色系 SVG？

**A**: 使用 Mermaid CLI 配置主题：

```bash
mmdc -i input.mmd -o output.svg -t dark -b transparent
```

### Q4: 时序图过长如何优化？

**A**: 
1. 拆分为多个子流程图
2. 使用 `opt`、`alt` 折叠次要分支
3. 提取公共流程为独立图表
4. 增加注释说明，减少不必要的消息

---

**文档版本**: 1.0.0  
**最后更新**: 2026-09-09  
**维护者**: Apex Team

---

<div align="center">

**[⬆️ 返回顶部](#apex-scaffold-时序图集)**

</div>
