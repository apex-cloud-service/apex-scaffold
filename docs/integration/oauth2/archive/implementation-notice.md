# OAuth2实现重要提示

## 技术限制说明

经过技术调研，**Spring Authorization Server 1.4.0当前版本仅支持Spring MVC(Servlet)架构，暂不支持Spring WebFlux(响应式)架构**。

### 当前项目状态
- 项目采用: **Spring Boot 3.5.15 + Spring WebFlux(响应式架构)**
- Authorization Server要求: **Spring MVC(Servlet架构)**

### 解决方案选择

#### 方案A: 混合架构(推荐 ✅)
在现有WebFlux项目中嵌入Spring MVC模块，OAuth2授权服务器使用MVC，业务接口继续使用WebFlux。

**优点**:
- 使用官方Spring Authorization Server,功能完整且持续维护
- OAuth2端点与业务端点隔离,互不影响
- 业务代码无需改动

**缺点**:
- 项目同时包含两种编程模型
- 需要额外配置MVC和WebFlux共存

**实现步骤**:
1. interfaces模块添加spring-boot-starter-web依赖
2. 配置DispatcherServlet处理/oauth2/**路径
3. WebFlux继续处理/api/**路径
4. Authorization Server配置使用Servlet API

#### 方案B: 第三方OAuth2库
使用纯响应式的OAuth2库(如自己实现或使用第三方库)。

**优点**:
- 架构统一,全部使用WebFlux

**缺点**:
- 需要自己实现或维护OAuth2协议
- 功能可能不如官方完整
- 安全性需要额外审查

#### 方案C: 独立授权服务
将OAuth2授权服务器拆分为独立的MVC项目。

**优点**:
- 关注点分离,授权服务独立部署
- 可以使用官方Spring Authorization Server

**缺点**:
- 需要维护两个项目
- 部署复杂度增加

### 推荐实施路径

**采用方案A(混合架构)**,具体步骤:

1. **修改pom.xml,添加MVC支持**
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
```

2. **配置MVC和WebFlux共存**
- MVC处理: `/oauth2/**`, `/login`, `/connect/**`
- WebFlux处理: `/api/**`, `/admin-api/**`

3. **创建独立的MVC配置包**
```
interfaces/
  ├── config/
  │   ├── WebFluxSecurityConfig.java (现有)
  │   └── oauth2/
  │       ├── OAuth2AuthorizationServerConfig.java (MVC)
  │       └── OAuth2MvcSecurityConfig.java (MVC)
```

4. **修改SecurityConfig**
- 将OAuth2相关端点排除在WebFlux Security之外
- 由MVC Security处理OAuth2端点

### 当前实施状态

已完成:
- ✅ 数据库设计(V25迁移脚本)
- ✅ 领域层(Entity, Repository接口)
- ✅ 基础设施层(PO, Mapper, Repository实现)
- ✅ RegisteredClientRepositoryAdapter

待调整:
- ⏳ Authorization Server配置(需改用MVC)
- ⏳ 添加MVC依赖
- ⏳ 配置MVC和WebFlux共存

### 下一步行动

1. 征求用户意见,确认采用方案A
2. 修改pom.xml添加MVC依赖
3. 调整Security配置支持MVC和WebFlux共存
4. 重写Authorization Server配置为MVC版本
5. 验证OAuth2流程

---

**建议**: 在继续实施前,请确认是否采用混合架构方案。如需完全响应式方案,建议采用方案B或C。
