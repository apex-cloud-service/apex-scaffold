# 架构使用指南 - 如何正确使用Apex Scaffold

本文档帮助用户理解脚手架的架构设计,避免误用示例业务代码。

## 🎯 核心原则

**Apex Scaffold 是一个"展示型"脚手架**,它包含两部分内容:

1. **通用基础设施** - 您可以在任何项目中复用的代码
2. **示例业务实现** - 仅用于展示DDD架构完整性,不应直接使用

## 📦 模块职责说明

### ✅ apex-common (完全可复用)

公共模块,**100%可以安全使用**:

- `result/` - 统一响应格式(Result类)
- `exception/` - 全局异常处理
- `util/` - 工具类(JWT、加密、日期等)
- `constant/` - 常量定义
- `enums/` - 通用枚举

**使用建议**: 直接依赖此模块,无需修改。

---

### ⚠️ apex-domain (部分可复用)

领域层（共享 domain），**只承载跨域复用契约**，需谨慎选择：

#### ✅ 当前实际内容（2026-11-28 实测，共 19 个类）

```
com.apex.scaffold.domain.model.entity
└── BaseEntity.java              # 基础实体（各业务库实体继承）

com.apex.scaffold.domain.event
├── DomainEventBus.java          # 领域事件总线契约
├── DomainEventHandler.java      # 事件处理器契约
└── TokenRevokedEvent.java       # 令牌撤销事件

com.apex.scaffold.domain.gateway
├── ChuanglanGateway.java        # 创蓝短信网关契约
├── WeChatGateway.java           # 微信网关契约
├── CircuitBreakerPort.java      # 熔断端口
└── RemoteApiException / RemoteClientFailure / RemoteClientInputException  # 远程调用异常契约

com.apex.scaffold.domain.service  # 服务端口：TokenService / TokenBlacklistService /
                                  # CacheService / CacheConsistencyPort / SmsCodeService /
                                  # SmsCodePurpose(Service) / DomainEventPublisher / BusinessMetricsPort
```

#### ❌ 注意

- 业务实体（UserEntity/RoleEntity 等）在各业务库自己的 `domain` 包（如 `apex-system`、`apex-auth`），**不在共享 domain**
- 共享 domain 不反向依赖任何具体业务库（AGENTS.md 分层红线）

**使用建议**:
- 各业务库实体继承 BaseEntity，端口/网关契约按需复用
- 新增业务专属实体放在所属业务库 domain，不放共享层

---

### ⚠️ apex-system (部分可复用)

应用层,**主要作为参考**:

#### ✅ 可复用的模式

- DTO转换逻辑(MapStruct使用方式)
- 用例编排模式(Command/Query分离)
- 事务管理模式

#### ❌ 不可复用的内容

- 具体的业务用例实现
- 特定业务的DTO定义

**使用建议**: 学习架构模式,但不要复制具体实现。

---

### ⚠️ apex-infrastructure (旧坐标兼容，无源码)

> **模块拆分后现状**（2026-11-28 实测）：`apex-infrastructure` 已退役为**旧坐标兼容空聚合**——仅保留兼容 POM（聚合依赖五个能力 JAR）与 README，`src` 下无任何 Java 源码。新消费者应显式选择能力模块，不再引用旧坐标。

#### ✅ 按能力选型（五个能力 JAR）

| 能力 JAR | 职责 |
|---|---|
| `apex-infrastructure-persistence` | 持久化：MyBatis-Plus、多数据源、Flyway、租户拦截等持久化辅助 |
| `apex-infrastructure-cache` | 缓存：多级缓存（Caffeine + Redis/Redisson）、缓存切面与一致性维护 |
| `apex-infrastructure-messaging` | 消息：RocketMQ 生产/消费封装、死信处理、Outbox 可靠投递 |
| `apex-infrastructure-remote` | 远程：远程 SDK/HTTP 客户端封装与熔断契约 |
| `apex-infrastructure-integration` | 集成：第三方集成能力（短信/微信/内容审核等） |

业务库的仓储实现（`UserRepositoryImpl` 等）分布在**各业务库**的 `infrastructure/persistence` 包（如 `apex-components-parent/apex-system`、`apex-components-parent/apex-auth`），不在共享层。

**使用建议**:
- 直接依赖所需能力 JAR，不再引入旧坐标 `apex-infrastructure`
- 学习参考实现：五个能力 JAR 源码 + 各业务库 `infrastructure` 包

---

### ✅ apex-system (完全可复用)

接口层,**100%可以安全使用**:

- HTTP端点定义(WebFlux Handler + Router)
- 安全配置(JWT过滤器、CORS配置)
- Swagger文档配置
- 全局异常处理器

**使用建议**: 直接参考或复制,根据需求调整路由。

---

### ⭐ apex-example (最佳学习对象)

示例模块,**强烈推荐从这里开始**:

这是一个**纯净的、从零构建的示例**,展示了:
- 如何定义实体(Entity)
- 如何编写DTO(Request/Response)
- 如何实现Mapper(MyBatis-Plus)
- 如何编写Service(业务逻辑)
- 如何编写Handler(WebFlux端点)
- 如何配置Router(路由)
- 如何编写单元测试
- 如何编写集成测试

**使用建议**:
1. 先运行 `apex-example`,理解完整流程
2. 基于此模块复制,修改为自己的业务
3. 不要从其他模块复制业务代码

---

## 🗺️ 正确的项目启动路径

### 第1步: 学习示例模块

```bash
cd apex-applications-parent/apex-example
mvnd spring-boot:run
```

示例模块（8080）未装配 springdoc/knife4j，无 Swagger 页面；接口验证直接 curl（如 `curl http://localhost:8080/api/example/users`），或查看 system-service（8082）的文档页了解注解规范。

### 第2步: 理解架构分层

阅读 [docs/02-architecture/architecture-overview.md](architecture-overview.md),理解DDD分层架构:

```
Interfaces (HTTP端点)
    ↓
Application (用例编排)
    ↓
Domain (业务逻辑)
    ↓
Infrastructure (技术实现)
    ↓
Common (通用工具)
```

### 第3步: 创建自己的业务模块

```bash
# 1. 业务库放入所属分组父目录（如 apex-business-parent/），并在该分组 POM 的 <modules> 登记一次；
#    根 POM 只登记顶层分组（详见 AGENTS.md 模块边界）
# 2. 复制apex-example的结构作为参考
cp -r apex-applications-parent/apex-example my-business

# 3. 修改包名和业务逻辑
# com.apex.example → com.yourcompany.mybusiness

# 4. 实现自己的业务
```

### 第4步: 按需引入基础设施

从以下模块复制需要的代码:
- `apex-common` - 全部可用
- `apex-domain` - 只复制 BaseEntity 与端口/网关契约（业务实体在各业务库 domain）
- 五个 `apex-infrastructure-*` 能力 JAR - 按需依赖（`apex-infrastructure` 旧坐标仅为兼容空聚合）
- `apex-system` - 全部可用

---

## 🚫 常见错误

### ❌ 错误1: 直接引用业务代码

```java
// 错误! 跨业务库引用他库领域实现
import com.apex.scaffold.system.domain.entity.SomeEntity;   // system 专属实体
import com.apex.scaffold.auth.domain.service.AuthXxxService; // auth 专属服务
```

**正确做法**:
```java
// 创建自己的业务包
package com.yourcompany.myproject.domain.service;

public class MyBusinessService {
    // 您的业务逻辑
}
```

### ❌ 错误2: 复制整个模块

```bash
# 错误! 这会带入大量无用业务代码
cp -r apex-domain ../my-project/
```

**正确做法**:
```bash
# 只复制需要的文件
cp apex-domain/src/main/java/com/apex/scaffold/domain/model/entity/BaseEntity.java \
   ../my-project/src/main/java/com/yourcompany/myproject/domain/model/entity/
```

### ❌ 错误3: 忽略示例模块

```bash
# 错误! 直接从domain/infrastructure开始,容易误用业务代码
cd apex-domain
```

**正确做法**:
```bash
# 先从纯净的示例开始
cd apex-example
```

---

## 📊 代码复用决策树

```
需要某个功能?
│
├─ 是通用工具(JWT、加密、日期)?
│  └─ ✅ 使用 apex-common
│
├─ 是HTTP端点、安全配置?
│  └─ ✅ 参考 apex-system
│
├─ 是Redis/存储/微信等基础设施?
│  └─ ✅ 依赖对应 apex-infrastructure-* 能力 JAR
│
├─ 是用户/角色/权限等RBAC功能?
│  └─ ✅ 参考各业务库 domain（apex-system/apex-auth），共享 domain 仅 BaseEntity 与契约
│
└─ 是特定业务逻辑(订单、支付、社交等)?
   └─ ⭐ 基于 apex-example 创建自己的实现
```

---

## 🔮 已落地的演进（原"未来规划"现状核对 2026-11-28）

1. ✅ 脚手架生成器已提供：`apex-tools-parent/apex-generator`（代码生成器，含 DDD 分层模板）
2. ✅ 业务实现与共享设施已物理分离：业务代码在各业务库，通用能力在 `apex-common` / 共享 domain / 五个 `apex-infrastructure-*` 能力 JAR
3. 后续演进以 AGENTS.md 与任务大盘为准

请继续遵循本文档的指导使用脚手架。

---

## 📞 需要帮助?

如果不确定某段代码是否可以复用:

1. 查看对应模块的 README.md (已标记可复用/不可复用)
2. 参考 `apex-example` 的实现方式
3. 提交 Issue 询问: https://github.com/apex-cloud-service/apex-scaffold/issues

---

**最后更新**: 2026-11-28（对齐 44/45 模块两级布局与共享设施拆分现状）
**维护者**: Apex Team
