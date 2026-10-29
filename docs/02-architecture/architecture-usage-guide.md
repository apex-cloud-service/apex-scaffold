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

领域层,**需要谨慎选择**:

#### ✅ 可复用的通用代码

```
com.apex.scaffold.domain.model.entity
├── BaseEntity.java              # 基础实体(所有实体都应继承)
├── UserEntity.java              # 用户实体(RBAC核心)
├── RoleEntity.java              # 角色实体
├── PermissionEntity.java        # 权限实体
├── RolePermissionEntity.java    # 角色权限关联
└── UserRoleEntity.java          # 用户角色关联

com.apex.scaffold.domain.model.valueobject
├── PhoneNumber.java             # 手机号值对象
└── WeChatOpenId.java            # 微信OpenID值对象

com.apex.scaffold.domain.gateway.*   # 网关接口定义
com.apex.scaffold.domain.port.*      # 端口定义
com.apex.scaffold.domain.exception.* # 领域异常
com.apex.scaffold.domain.enums.*     # 通用枚举
```

#### ❌ 不可复用的示例业务代码

```
com.apex.scaffold.domain.interest.*           # 兴趣图谱(129个文件)
com.apex.scaffold.domain.model.entity.EloRatingEntity       # ELO评分
com.apex.scaffold.domain.model.entity.PhotoEntity           # 照片管理
com.apex.scaffold.domain.model.entity.BattleEntity          # 对战系统
com.apex.scaffold.domain.model.entity.ResonanceEntity       # 共鸣系统
com.apex.scaffold.domain.model.entity.AestheticPreferenceVectorEntity  # 美学偏好
com.apex.scaffold.domain.model.entity.CreditAccountEntity   # 积分账户
com.apex.scaffold.domain.model.entity.VoteEntity            # 投票系统
com.apex.scaffold.domain.model.entity.ChatEntity            # 聊天系统
... (约150+个业务实体)
```

**使用建议**: 
- 复制通用实体作为参考
- 创建自己的业务包: `com.yourcompany.yourproject.domain`
- 不要直接引用 interest/elo/photo 等业务包

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

### ⚠️ apex-infrastructure (部分可复用)

基础设施层,**需要谨慎选择**:

#### ✅ 可复用的通用实现

```
com.apex.scaffold.infrastructure.gateway
├── RedisGatewayImpl.java         # Redis缓存实现
├── StorageGatewayImpl.java       # 对象存储(MinIO/TOS)
├── WeChatGatewayImpl.java        # 微信SDK集成
├── EmailGatewayImpl.java         # 邮件发送
└── SmsGatewayImpl.java           # 短信服务

com.apex.scaffold.infrastructure.repository
├── UserRepositoryImpl.java       # 用户仓储
├── RoleRepositoryImpl.java       # 角色仓储
└── PermissionRepositoryImpl.java # 权限仓储

com.apex.scaffold.infrastructure.config
├── RedisConfig.java              # Redis配置
├── MyBatisPlusConfig.java        # MyBatis-Plus配置
├── SwaggerConfig.java            # Swagger配置
├── SecurityConfig.java           # 安全配置
└── WebFluxConfig.java            # WebFlux配置
```

#### ❌ 不可复用的示例业务实现

```
com.apex.scaffold.infrastructure.gateway
├── EloGatewayImpl.java           # ELO评分网关
├── FaceAnalysisGatewayImpl.java  # 人脸分析网关
├── InterestGatewayImpl.java      # 兴趣图谱网关
└── RecommendationGatewayImpl.java # 推荐系统网关

com.apex.scaffold.infrastructure.repository
├── PhotoRepositoryImpl.java      # 照片仓储
├── BattleRepositoryImpl.java     # 对战仓储
├── ResonanceRepositoryImpl.java  # 共鸣仓储
... (约100+个业务仓储)
```

**使用建议**: 
- 复制通用网关和配置类
- 创建自己的业务实现: `com.yourcompany.yourproject.infrastructure`
- 不要直接引用 elo/face/interest 等业务实现

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
cd apex-example
mvnd spring-boot:run
```

访问 http://localhost:8080/doc.html 查看API文档

### 第2步: 理解架构分层

阅读 `docs/ARCHITECTURE.md`,理解DDD五层架构:

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
# 1. 在父pom.xml中添加新模块
<module>my-business</module>

# 2. 复制apex-example的结构
cp -r apex-example my-business

# 3. 修改包名和业务逻辑
# com.apex.example → com.yourcompany.mybusiness

# 4. 实现自己的业务
```

### 第4步: 按需引入基础设施

从以下模块复制需要的代码:
- `apex-common` - 全部可用
- `apex-domain` - 只复制 BaseEntity、UserEntity 等通用实体
- `apex-infrastructure` - 只复制 RedisGateway、StorageGateway 等通用实现
- `apex-system` - 全部可用

---

## 🚫 常见错误

### ❌ 错误1: 直接引用业务代码

```java
// 错误! 这是特定业务实现
import com.apex.scaffold.domain.interest.InterestService;
import com.apex.scaffold.domain.model.entity.EloRatingEntity;
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
│  └─ ✅ 复制 apex-infrastructure 中的通用实现
│
├─ 是用户/角色/权限等RBAC功能?
│  └─ ✅ 复制 apex-domain 中的 UserEntity/RoleEntity
│
└─ 是特定业务逻辑(订单、支付、社交等)?
   └─ ⭐ 基于 apex-example 创建自己的实现
```

---

## 🔮 未来规划

我们计划在未来版本中:

1. **v1.1.0**: 将示例业务代码移至独立模块 `apex-demo-business`
2. **v1.3.1**: 提供脚手架生成器,自动创建纯净项目骨架
3. **v2.0.0**: 完全剥离示例业务,仅保留通用基础设施

在此之前,请遵循本文档的指导使用脚手架。

---

## 📞 需要帮助?

如果不确定某段代码是否可以复用:

1. 查看对应模块的 README.md (已标记可复用/不可复用)
2. 参考 `apex-example` 的实现方式
3. 提交 Issue 询问: https://github.com/apex-scaffold/apex-scaffold/issues

---

**最后更新**: 2026-08-25  
**维护者**: Apex Team
