# Apex Scaffold 时序图集

> **版本**: v1.0  
> **创建日期**: 2026-09-09  
> **格式**: Mermaid 语法

本目录包含 Apex Scaffold 项目核心业务流程的时序图，使用 Mermaid 语法编写。

---

## 📋 时序图清单

| 序号 | 文件名 | 说明 | 参与者数量 |
|------|--------|------|-----------|
| 30 | `30_user_login_sequence.mmd` | 用户登录认证时序图 | 6 个 |
| 31 | `31_permission_check_sequence.mmd` | RBAC 权限校验时序图 | 8 个 |
| 32 | `32_workflow_approval_sequence.mmd` | 工作流审批时序图 | 10 个 |
| 33 | `33_file_upload_sequence.mmd` | 文件上传时序图 | 12 个 |

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

**核心流程**:
1. 客户端提交登录请求（用户名、密码、验证码）
2. 验证图形验证码（Redis 缓存）
3. 查询用户信息并验证密码（BCrypt）
4. 生成 JWT Token（Access Token + Refresh Token）
5. 缓存 Token 到 Redis（2 小时 + 7 天）
6. 返回 Token 给客户端
7. 后续请求携带 Token 验证
8. Token 自动续期机制（剩余 < 30 分钟时）

**涉及组件**:
- API Gateway
- AuthService
- JwtService
- Redis
- Database

**关键配置**:
- `JWT_SECRET`: JWT 签名密钥
- `JWT_ACCESS_EXPIRE`: Access Token 有效期（2 小时）
- `JWT_REFRESH_EXPIRE`: Refresh Token 有效期（7 天）
- `APEX_CAPTCHA_ENABLED`: 是否启用验证码

---

### 31. RBAC 权限校验时序图

**核心流程**:
1. 请求进入权限拦截器
2. 解析 JWT Token 获取用户 ID
3. 查询用户权限（优先缓存，缓存未命中查库）
4. 检查用户是否有访问权限
5. AOP 切面二次校验 `@RequirePermission` 注解
6. 数据权限过滤（`@DataScope` 注解）
7. 动态拼接 SQL 实现部门级数据隔离
8. 响应数据脱敏（`@DataMask` 注解）

**涉及组件**:
- API Gateway
- Interceptor
- PermissionService
- AOP Aspect
- Redis
- Database

**关键配置**:
- `APEX_REDIS_KEY_PREFIX`: Redis 键前缀
- 权限缓存 TTL: 1 小时

**权限缓存键**:
```
user:permissions:{userId}
```

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
- `FILE_UPLOAD_MAX_SIZE`: 最大文件大小（10MB）
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

- [架构文档索引](../../ARCHITECTURE_INDEX.md) - 29 张架构图完整索引
- [DDD 四层架构](../../architecture/01-logical-architecture-detail.md)
- [工作流引擎指南](../../WORKFLOW_GUIDE.md)
- [存储 SPI 指南](../../SPI_PLUGIN_GUIDE.md)

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
