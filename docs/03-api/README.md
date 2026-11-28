# 03. API 文档

本目录包含 API 接口文档、规范和版本管理相关内容。

---

## 📚 文档列表

### [API 路由清单](./API_ROUTES.md)

全部 HTTP 路由登记清单（主应用与认证中心），存在性由 `scripts/ci/check-doc-consistency.sh` 检查。**现行权威清单**。

### [内部 API 契约](./internal-api-contracts.md)

跨服务 `/internal/v1/**` 内部接口与事件契约清单（防漂移基线）：提供方、消费方、契约 DTO 与载体逐条登记

### [开放平台集成](./open-platform-integration.md)

开放应用（open-app）体系：注册/密钥/scope 目录、`/open-api/**` 开放端点、网关 scope 门禁与分档限流

### [集成服务 IoT API](./integration-iot-api.md)

设备接入域接口：设备注册/影子/遥测/命令、MQTT 接入（EMQX 外置）与凭据管理

### [API 路径与参数修正](./api-path-corrections.md)

历史路径修正记录（2026-09 快照，现行权威为 API_ROUTES.md）

### [前期路由快照](./api-routes.md)

早期接口快照（**非权威清单**，仅供考古对照；以 [API_ROUTES.md](./API_ROUTES.md) 为准）

### [路由映射](../_archive/api/route-mapping.md)

前后端路由对应关系和接口对齐验证

- 前端路由配置
- 后端接口映射
- 接口对齐验证清单
- 56 个接口完整对照

### [API 版本管理](./api-version-management.md)

API 版本控制策略和实践

- 版本控制策略
- URL 版本方案
- Header 版本方案
- 向后兼容性保证
- 废弃流程

### [SpringDoc 注解模板](./springdoc-annotation-templates.md)

OpenAPI/SpringDoc 注解使用模板

- 常用注解说明
- Controller 注解示例
- Request/Response 注解示例
- 错误响应注解
- 完整示例代码

### [SpringDoc 注解审计](./springdoc-annotation-audit.md)

SpringDoc 注解完整性审计报告

- 注解覆盖率统计
- 缺失注解列表
- 改进建议

### [数据管理 API](../_archive/api/data-management-api.md)

数据管理相关 API 接口文档

- 数据配置接口
- 数据源管理接口
- 数据权限接口

---

## 🌐 在线 API 文档

启动项目后可以访问以下在线文档：

### Swagger UI
```
http://localhost:8080/swagger-ui.html
```

标准 OpenAPI 界面，支持：
- API 接口列表
- 接口参数说明
- 在线测试
- 响应示例

### Knife4j UI
```
http://localhost:8080/doc.html
```

增强的 API 文档界面，支持：
- 更美观的界面
- 接口搜索
- 文档导出
- 调试历史

### OpenAPI JSON
```
http://localhost:8080/v3/api-docs
```

原始 OpenAPI 规范文档，可用于：
- 代码生成
- 第三方工具集成
- API 契约测试

---

## 📖 API 设计规范

### RESTful 规范

- 使用标准 HTTP 方法（GET、POST、PUT、DELETE）
- 资源命名使用复数形式
- URL 层级清晰，体现资源关系
- 统一的响应格式

### 统一响应格式

```json
{
  "code": 0,
  "msg": "操作成功",
  "data": {},
  "traceId": "trace-id-value"
}
```

> 注意：`code` 为业务码，`ResultCode.SUCCESS` 为 **0**（不是 HTTP 200）；OAuth2/JWKS/文件流/健康检查等既有协议响应保持原契约，不做业务包装。

### 错误码规范

- 2xx: 成功
- 4xx: 客户端错误
- 5xx: 服务端错误

详见 `ResultCode` 枚举定义

---

## 🚀 快速链接

- [返回文档中心](../README.md)
- [开发文档](../04-development/README.md)
- [测试文档](../06-testing/README.md)

---

**最后更新**: 2026-11-28
