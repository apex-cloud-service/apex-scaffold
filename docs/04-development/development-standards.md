# Apex Scaffold 代码开发规范

**版本**: v1.0.0\
**适用范围**: Apex Scaffold 后端多模块 Maven 工程\
**技术栈**: Java 21、Spring Boot 3.5.15、Spring WebFlux、Project Reactor、DDD、MyBatis-Plus、MySQL、PostgreSQL、Redis、Nacos、Sentinel、RocketMQ、MapStruct、Lombok\
**最后更新**: 2026-10-10

> 本文档由原有零散规范整合而成，统一收录：后端开发规范、注释规范、JavaDoc 指南、Lombok 指南、构建工具（mvnd）标准。文中所有约束均为强制性要求，与仓库根目录 [AGENTS.md](../../AGENTS.md) 及 [CONTRIBUTING.md](../../CONTRIBUTING.md) 保持一致；如有冲突，以 AGENTS.md 为准。

***

## 目录

1. [适用范围与基本原则](#1-适用范围与基本原则)
2. [项目架构](#2-项目架构)
3. [编码规范](#3-编码规范)
4. [注释与 JavaDoc 规范](#4-注释与-javadoc-规范)
5. [响应式编程规范](#5-响应式编程规范)
6. [数据访问规范](#6-数据访问规范)
7. [API 设计规范](#7-api-设计规范)
8. [安全规范](#8-安全规范)
9. [测试规范](#9-测试规范)
10. [性能与稳定性](#10-性能与稳定性)
11. [构建规范（mvnd）](#11-构建规范mvnd)
12. [提交与审查](#12-提交与审查)
13. [开发检查清单](#13-开发检查清单)

***

<a id="1-适用范围与基本原则"></a>

## 1. 适用范围与基本原则

本规范适用于 `apex-interfaces`、`apex-application`、`apex-domain`、`apex-infrastructure` 和 `apex-common` 模块中的新增代码、修改代码和测试代码。

基本原则：

- 业务规则优先放在领域层，接口层只负责协议适配。

- 响应式链路保持非阻塞，JDBC 和同步 SDK 必须隔离到专用线程池。

- 领域模型与数据库对象分离，禁止用 PO 代替 Entity 承载业务行为。

- 所有外部输入都要校验，所有敏感信息都要脱敏。

- 代码格式、测试和提交信息必须符合仓库已有工具链与 `CONTRIBUTING.md` 约定。

项目基础命令（构建工具统一使用 `mvnd`，禁止 `mvn`，详见第 11 节）：

```bash
mvnd clean test
mvnd spotless:check
mvnd spotless:apply
```

***

<a id="2-项目架构"></a>

## 2. 项目架构

### 2.1 DDD 四层结构

```text
apex-interfaces/
├── handler/
├── router/
├── filter/
└── config/

apex-application/
├── service/
├── dto/
└── assembler/

apex-domain/
├── model/
├── repository/
├── service/
├── event/
└── gateway/

apex-infrastructure/
├── persistence/
│   ├── mapper/
│   ├── po/
│   └── repository/
└── gateway/
```

依赖方向：

```text
interfaces → application → domain ← infrastructure
                              ↑
                              └── common
```

### 2.2 模块职责

| 模块                    | 负责内容                                         | 约束                          |
| --------------------- | -------------------------------------------- | --------------------------- |
| `apex-interfaces`     | RouterFunction、Handler、WebFilter、Web 配置、协议响应 | 不直接访问 Mapper 或数据库，不承载核心业务规则 |
| `apex-application`    | 用例编排、事务边界、DTO 和 Assembler                    | 不直接依赖具体持久化实现                |
| `apex-domain`         | Entity、值对象、聚合、Repository 接口、领域服务、领域事件        | 不依赖具体基础设施技术                 |
| `apex-infrastructure` | Mapper、PO、Repository 实现、Redis、第三方网关          | 实现接口，不向上层泄漏技术细节             |
| `apex-common`         | Result、异常、上下文、工具、公共注解                        | 只放跨模块稳定的公共能力                |

新增功能时先确定聚合和用例，再按 `Handler → Application Service → Domain → Repository/Gateway` 实现。禁止在 Handler 中直接拼接 SQL、操作 Redis 或调用第三方 SDK。

***

<a id="3-编码规范"></a>

## 3. 编码规范

### 3.1 命名

| 对象                  | 规则                             | 示例                       |
| ------------------- | ------------------------------ | ------------------------ |
| Handler             | 名词 + `Handler`                 | `AuthHandler`            |
| Application Service | 名词 + `Service`                 | `AuthService`            |
| Repository 接口       | 名词 + `Repository`              | `UserRepository`         |
| Repository 实现       | 名词 + `RepositoryImpl`          | `UserRepositoryImpl`     |
| Mapper              | 名词 + `Mapper`                  | `UserMapper`             |
| 领域实体                | 名词 + `Entity`                  | `UserEntity`             |
| 持久化对象               | 名词 + `PO`                      | `UserPO`                 |
| 请求与响应               | 名词 + `Request` / `Response`    | `LoginRequest`           |
| 转换器                 | 名词 + `Assembler` / `Converter` | `UserAssembler`          |
| 配置                  | 名词 + `Config` / `Properties`   | `JwtConfig`              |
| 常量                  | 全大写，下划线分隔                      | `TOKEN_BLACKLIST_TTL_MS` |

方法名要表达业务意图，例如 `login`、`refreshToken`、`findByPhone`、`deleteById`。避免使用 `process`、`handle`、`doSomething` 等无法说明行为的名称。

<a id="32-lombok"></a>

### 3.2 Lombok

项目级约定由根目录 `lombok.config` 统一控制，Lombok 版本为 `1.18.36`（`scope=provided`，仅编译期生效，零运行时开销）。

**按场景推荐注解：**

| 场景 | 推荐注解 | 说明 |
| --- | --- | --- |
| DTO / VO / Request / Response | `@Data` + `@Builder` + `@NoArgsConstructor` + `@AllArgsConstructor` | 支持流式构建与 Jackson 反序列化 |
| Domain Entity | `@Getter` + `@Builder` + 私有/受保护构造器 | 通过领域方法变更状态，不暴露通用 setter，**不使用 `@Data`** |
| Service / Component | `@Slf4j` + `@RequiredArgsConstructor` | `final` 字段依赖注入，单构造器免 `@Autowired` |
| PO | `@Data` + `@Builder` + 构造器 + `@EqualsAndHashCode(callSuper = true)` + `@TableName` | 配合 `@TableId`、`@TableLogic`、`@Version` |
| Converter（MapStruct） | 无 Lombok 注解 | 由 MapStruct 编译期生成实现 |
| 配置类 | `@Data` + `@ConfigurationProperties` | Spring Boot 配置绑定 |

**硬性要求：**

- DTO、VO、Request、Response：`@Data`、`@Builder`，按需要增加构造器。

- Domain Entity：`@Getter`、`@Builder`，通过领域方法变更状态，不暴露通用 setter。

- Service、Component：`@Slf4j`、`@RequiredArgsConstructor`。

- PO：`@Data`、`@TableName`，配合 `@TableId`、`@TableLogic`、`@Version` 等持久化注解。

- 继承场景必须设置 `@EqualsAndHashCode(callSuper = true)`，避免父类字段不参与 `equals/hashCode`。

- 敏感字段（密码、Token、身份证号等）必须在 `@ToString` 中 `exclude`。

- 不重复手写 Lombok 已生成的 getter、setter、构造器和日志字段。

- **禁止** `@SneakyThrows`（`lombok.sneakyThrows.flagUsage = error`）、`@Cleanup`、`val`、`var`、`@Synchronized`。

```java
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserEntity {
    private Long id;
    private UserStatus status;

    public void activate() {
        this.status = UserStatus.ACTIVE;
    }
}
```

`lombok.config` 关键项：

```properties
lombok.builder.toBuilder = true
lombok.equalsAndHashCode.callSuper = call
lombok.addLombokGeneratedAnnotation = true
lombok.log.fieldName = log
lombok.log.fieldIsStatic = true
lombok.sneakyThrows.flagUsage = error
lombok.synchronized.flagUsage = warning
lombok.cleanup.flagUsage = warning
lombok.val.flagUsage = warning
lombok.var.flagUsage = warning
```

**常见问题：**

- `@Data` + `@Builder` 一起使用时需显式声明 `@NoArgsConstructor` 与 `@AllArgsConstructor`，否则 `@Builder` 生成的全参构造器会覆盖无参构造器，导致 Jackson 反序列化失败。
- 继承类使用 `@Data` 时必须加 `@EqualsAndHashCode(callSuper = true)`。
- IDE 需安装并启用 Lombok 插件，并在 IntelliJ IDEA 中勾选 "Enable annotation processing"。

### 3.3 代码格式

项目使用 Spotless 2.43.0 与 Palantir Java Format：

- 使用 4 个空格缩进，不使用 Tab。

- 行宽上限 120 字符（与 `palantirJavaFormat` 一致）。

- 保持导入整洁，删除未使用导入。

- 修改 Java 文件后执行 `mvnd spotless:apply`，提交前执行 `mvnd spotless:check`。

### 3.4 异常与日志

- 可预期业务失败使用项目统一业务异常和错误码。

- 系统异常保留 cause，交由全局异常处理转换为统一响应。

- 禁止吞异常、空 catch 和直接返回模糊的 `null`。

- 使用日志占位符，不使用字符串拼接。

- `ERROR` 记录系统故障及堆栈，`WARN` 记录业务拒绝和降级，`INFO` 记录关键业务节点，`DEBUG` 记录诊断信息。

- 手机号、身份证、Token、Cookie、密码和第三方凭证禁止明文写入日志。

```java
log.info("User login, phone: {}", SensitiveDataUtil.maskPhone(phone));
log.error("External service call failed, traceId: {}", traceId, exception);
```

***

<a id="4-注释与-javadoc-规范"></a>

## 4. 注释与 JavaDoc 规范

### 4.1 总原则

- 适用：全部 Maven 模块的 `src/main/java`；`src/test/java`、SQL、Shell 脚本按对应小节的宽松规则执行。

- 三条总原则：
  1. **注释解释"为什么"，代码表达"是什么"**——能从代码和签名直接读出的信息不写进注释。
  2. **注释必须与代码同步演进**——改签名必查 `@param`，删成员必删其注释。
  3. **宁缺毋滥**——一个准确的单行注释，优于一段复制粘贴的套话。

### 4.2 注释语言

- 注释读者是团队内部开发者，统一使用**中文**；代码标识符、框架/协议术语（JWT、WebFlux、Flyway）、HTTP 方法与状态码、SQL 关键字保留英文原文，中英混排时术语两侧加空格。

- `@author` 标签值例外，使用固定署名 **`Apex Team`**；个人贡献由 git 记录，不写个人署名。

- 注释**不承担**用户可见文案：面向最终用户的提示一律走 i18n（`messages_*.properties`）；对外 API 文档（`@Schema`/`@Operation` 的 `description`）同样用中文。

### 4.3 文件头模板

#### 4.3.1 Java：类级 JavaDoc 即文件头

仓库约定全部 Java 文件以 `package` 开头、无独立 license 头，文档头职责由类型声明上方的类级 JavaDoc 承担，**不引入 package 前置文件头块**：

```java
package com.apex.scaffold.xxx.yyy;

import ...;

/**
 * 〈一句话用途：这个类/接口是什么、解决什么问题〉。
 *
 * <p>〈补充背景、关键业务规则、线程安全性、性能契约等；简单类可省略〉。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since  1.2.0
 * @created 2026-10-06
 */
@Service
public class XxxService {
```

字段约定：

| 字段 | 必填 | 说明 |
|---|---|---|
| 首句用途 | 必填 | 单句、陈述式，省略主语 |
| `@author` | 必填 | 固定值 `Apex Team` |
| `@version` | 可选 | 与该模块 pom 当前版本一致 |
| `@since` | 必填 | **该类型引入时的语义化版本**（如 `1.2.0`），禁止填日期 |
| `@created` | 可选 | 创建日期 `yyyy-MM-dd`；新增文件建议填写，既有文件不回填 |

标签顺序固定：`@author → @version → @since → @created`。

#### 4.3.2 SQL 迁移脚本

```sql
-- =============================================================================
-- V44__add_device_trust_tables.sql
-- 用途：认证中心设备信任模块建表与初始权限种子
-- 适用：mysql / postgresql（同版本号双库脚本必须同步维护）
-- 作者：Apex Team
-- 创建：2026-09-27
-- =============================================================================
```

#### 4.3.3 Shell 脚本

```bash
#!/bin/bash
###############################################################################
# check-xxx.sh - 一句话用途
# 用法：./check-xxx.sh [--verbose]   （含参数说明与退出码含义）
# 作者：Apex Team    创建：2026-10-06
###############################################################################
```

#### 4.3.4 其他类型

- `.ftl` 模板（apex-generator）、`pom.xml`、Grafana/OpenAPI 导出物：**不要求**文件头。

- `.yml` 应用配置：新增配置段用 `#` 段落注释说明每个新增 key 的含义，不要求整文件头。

### 4.4 类 / 方法 / 字段文档模板

#### 4.4.1 类与接口

简单类（DTO、PO、异常、枚举项少）：首句 + `@author` 系列即可，禁止堆砌空分节。复杂服务类可用 `<h2>` 分节，**只允许**以下节名：`职责范围`、`线程安全性`、`设计说明`、`使用示例`。

**强制要求：**

- **必须**：`@author Apex Team`（固定作者）。
- **必须**：`@since` 标注首次出现的语义化版本号。
- **推荐**：`@version` 使用该模块 pom 当前版本号。
- **推荐**：使用 `{@code}`、`{@link}`、`{@see}` 等标签；复杂类添加使用示例 `<pre>{@code}</pre>`。
- **禁止**：手写空 JavaDoc（如 `/** */`）；复制粘贴不准确的描述；使用英文描述业务逻辑（技术术语除外）。

持久化对象（PO）类级 JavaDoc 示例：

```java
/**
 * 用户持久化对象
 *
 * <p>对应数据库表 {@code sys_user}，存储用户的完整信息，包括基本信息、
 * 认证信息（密码 BCrypt 哈希）、组织信息（部门ID、租户ID）。
 *
 * <h2>表结构</h2>
 * <p>表名：{@code sys_user}<br>
 * 主键：{@code id}（继承自 {@link BasePO}）<br>
 * 唯一索引：{@code uk_username}（登录账号）
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.0.0
 * @see BasePO
 */
@Data
@Builder
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
public class UserPO extends BasePO {
    // ...
}
```

Service 类级 JavaDoc 示例：

```java
/**
 * 认证服务应用类
 *
 * <p>处理登录、Token 生成与刷新、退出登录等核心功能，
 * 作为应用层服务，协调领域层和基础设施层完成认证流程。
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>登录：支持多种登录策略（{@link LoginStrategy}）</li>
 *   <li>Token 刷新：使用 refreshToken 换取新的 accessToken</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.0.0
 * @see TokenService
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {
    // ...
}
```

#### 4.4.2 方法（核心模板）

```java
/**
 * 〈动词开头的单句概述〉。（首句必须；以下各项按需）
 *
 * <p>〈非直观业务规则、副作用、租户/数据权限语义、与谁交互〉。
 *
 * @param request      HTTP 请求对象（名称必须与签名逐字一致）
 * @param queryService 审计日志查询服务
 * @return 分页结果；无数据时返回空页而非错误（说明空值/空集合语义）
 * @throws BusinessException 参数非法（{@link ResultCode#PARAM_ERROR}），
 *         无权限（{@link ResultCode#FORBIDDEN}）
 * @implSpec 阻塞查询已用 BlockingIoSchedulers.jdbc() 隔离。
 *           （凡涉及调度器选择、超时、重试、降级语义时必写）
 * <pre>{@code
 *   // 示例：仅对"非直观 API"必填——加解密、签名、日期解析、复杂包装器等
 * }</pre>
 */
```

硬性规则：

1. **`@param` 逐字对齐签名**：每个参数一行，名称与签名完全一致；签名增删参数必须同步增删行。
2. **`void`/`Mono<Void>` 方法禁止 `@return`**。
3. **受检外异常一律写 `@throws`**，业务异常必须落到 `ResultCode` 常量。
4. **重载方法必须独立完整文档**（至少含 `@param`），或写一句"见 {@link #全参方法}"的委托说明。
5. **接口方法 100% 文档化**；实现类 `@Override` 方法**免写** JavaDoc。
6. **构造器**：DI 注入构造器逐参 `@param`；纯默认构造器免写。

业务方法示例：

```java
/**
 * 创建用户
 *
 * <p>完成用户注册流程：校验用户名唯一性、加密密码、分配默认角色、发送欢迎通知。
 *
 * <p><strong>事务边界</strong>：本方法开启新事务，确保用户创建的原子性。
 *
 * @param request 用户创建请求，包含用户名、密码、邮箱等信息
 * @return 创建成功的用户 DTO，包含用户ID、用户名、创建时间等
 * @throws BusinessException 用户名已存在或密码加密失败时抛出
 * @since 1.0.0
 */
public Mono<UserDTO> createUser(CreateUserRequest request) {
    // 实现
}
```

#### 4.4.3 字段级别

```java
/**
 * 租户ID（多租户隔离）
 *
 * <p>对应 {@code sys_user.tenant_id} 列，实现租户级数据隔离。
 * 配合 {@code @TenantAware} 注解和 {@code TenantContext} 自动填充和过滤。
 *
 * @since 2.5.0
 */
@TableField("tenant_id")
private String tenantId;
```

#### 4.4.4 枚举与常量

- 枚举：每个常量上一行 `/** 说明 */`；**删除常量必须同时删除其注释**。
- 常量类：类级 JavaDoc 说明命名规则，每个常量 `/** 说明 */`。

```java
/**
 * 用户状态枚举
 *
 * <p>定义用户账号的所有可能状态，用于状态机转换和权限控制。
 *
 * @author Apex Team
 * @since 1.0.0
 */
public enum UserStatus {
    /** 未激活 */
    INACTIVE(0, "未激活"),
    /** 正常 */
    ACTIVE(1, "正常"),
    /** 已封禁 */
    SUSPENDED(2, "已封禁"),
    /** 已注销 */
    DELETED(3, "已注销");
}
```

#### 4.4.5 record 与免文档白名单

- record：record 级 JavaDoc 用 `@param` 描述**组件**（JDK 标准写法）。
- 以下成员**不要求** JavaDoc；如确要写，只允许单行 `/** ... */`，且不得复述签名：
  - getter/setter/is 方法；
  - builder 模式的流式 setter；
  - Lombok/框架生成成员——**严禁为它们手写注释**（注释会变成悬空孤儿）。

### 4.5 行内注释规则

**何时写（只写以下四类）**：

1. 业务背景/为什么这么做；
2. 坑与约束（WebFlux 链中不得阻塞、Y2038 类时间约束等）；
3. 魔法值解释（正则、常量取值来源、第三方接口参数）；
4. 外部契约（与前端/第三方约定的字段含义、Flyway 版本号联动关系）。

**如何写**：

- 独立行 `// ` + 空格 + 中文短句，与被注释代码**同缩进**，放在代码**上方**；行尾注释仅用于极短标注（参数单位等）；
- 单行 ≤ **120 字符**；超过两行的说明升级为方法 JavaDoc；
- 分节横幅 `/* ==================== 概览 ==================== */` **允许**用于 500 行以上大文件分节，但横幅不是文档，不能替代 JavaDoc；
- `TODO`/`FIXME` 格式强制：`// TODO(Apex Team / 工单号): 〈可执行的下一步〉`。禁止没有负责人或不可执行的 TODO。

### 4.6 禁止的"坏注释"（红线）

以下反例均来自 2026-10-06 全仓注释审计的真实出处（已修复，保留作为回归样本）：

| # | 类型 | 反例（真实出处） | 规则 |
|---|---|---|---|
| 1 | 占位符文档 | `* TODO: Add method description` / `* @param jwtKey TODO`（曾泛滥于 auth-center 风控域服务与 AccountDeletion*Mapper 等 16 文件 404 行） | **绝对禁止**。没想好就先不写注释，禁止生成占位块 |
| 2 | 双块/叠块文档 | `AbstractStorageService`（真文档上叠 TODO 块）；`MaskType`/`ErrorCode`（两块类级文档叠放） | 一个声明只允许一个 JavaDoc 块 |
| 3 | 孤儿注释 | `ConfigPO` 等 16 个 PO 类体顶部的悬空"构造XX持久化对象"块；`MailService` 给 Lombok 字段的注释 | 注释必须紧贴真实声明；删除成员必删注释 |
| 4 | 复述式噪音 | `// 新增`（`BaseRepositoryImpl`）、`// 设置创建时间` | 禁止只翻译代码的注释 |
| 5 | 言行不一 | `// 保存私钥`（实际是 Base64 编码）；`@param auditLogService` vs 实参 `auditLogQueryService`（`AuditLogHandler`） | 注释与代码不符视同缺陷，修复优先级高于补缺 |
| 6 | 注释掉的尸体代码 | 无说明的成块注释代码 | 删除优先；确需保留必须含**原因 + 移除条件** |
| 7 | 畸形注释 | `* TODO: Add method/**`；未闭合的 HTML 标签 | 提交前 `mvnd spotless:check` 兜底 |
| 8 | 敏感信息入注释 | 密码、Token、内网地址、密钥写进注释 | 同 AGENTS.md 安全条款，禁止 |
| 9 | 套话分节 | 空的 `<h2>设计模式</h2>`、只有标题没有内容的分节 | 分节必须有实质内容，否则不写 |

### 4.7 与现有正确注释的兼容性（Grandfather 条款）

1. **不翻动存量**：不新增 package 前置文件头块；既有 `@author/@version/@since` 无需调整，缺 `@created` 不回填。
2. **合法既有风格白名单**（明确不算违规，禁止"顺手改掉"）：record 组件 `@param`、`/* == */` 分节横幅、单行 `/** 字段说明 */`、字段级多行 JavaDoc、`@SuppressWarnings("PMD.ClassMustHaveAuthorRule")` 豁免、`@Override` 方法无文档、测试代码无类文档（测试类新增时才按"一行类级说明"要求）。
3. **署名渐进收敛**：现存多种 `@author` 写法仅在文件因其他原因被大改时顺手统一为 `Apex Team`，不做专项批量替换。
4. **工具链判据对齐**：`scripts/dev/check/scan-javadoc-issues.sh` 的判据（类级 JavaDoc、`@author`、`@since`、非豁免 public 方法需 JavaDoc，`@Override` 与匿名类实现、getter/setter/is、builder 流式 setter 豁免）即本规范的机器可查子集，该脚本输出必须保持全绿；新增检查项先改脚本再改码，脚本入库并使用 `scripts/lib/common.sh`。
5. **与 AGENTS.md 的关系**：本节是 AGENTS.md "代码注释规范"一节的展开，冲突处以 AGENTS.md 为准。

### 4.8 JavaDoc 标签与 HTML 参考

**常用 HTML 标签：**

| 标签 | 用途 | 示例 |
|------|------|------|
| `<p>` | 段落分隔 | `<p>这是第一段</p><p>这是第二段</p>` |
| `<ul><li>` | 无序列表 | `<ul><li>项目1</li><li>项目2</li></ul>` |
| `<ol><li>` | 有序列表 | `<ol><li>步骤1</li><li>步骤2</li></ol>` |
| `<h2>` | 二级标题 | `<h2>主要功能</h2>` |
| `<strong>` | 加粗强调 | `<strong>注意</strong>` |
| `<code>` | 行内代码 | `<code>userId</code>` |
| `<pre>` | 预格式化文本 | `<pre>多行代码块</pre>` |
| `<br>` | 换行 | `第一行<br>第二行` |
| `<table>` | 表格 | `<table><tr><td>单元格</td></tr></table>` |

**JavaDoc 标签参考：**

| 标签 | 用途 | 示例 |
|------|------|------|
| `@author` | 作者信息 | `@author Apex Team` |
| `@version` | 版本号 | `@version 1.2.0` |
| `@since` | 首次出现版本 | `@since 1.0.0` |
| `@see` | 相关类或方法 | `@see UserEntity` |
| `@deprecated` | 标记已废弃 | `@deprecated 使用 {@link #newMethod()} 替代` |
| `@param` | 参数说明 | `@param userId 用户ID，必须存在` |
| `@return` | 返回值说明 | `@return 用户信息，不存在返回 null` |
| `@throws` | 异常说明 | `@throws BusinessException 用户不存在时抛出` |
| `@implSpec` | 实现契约 | `@implSpec 阻塞查询已隔离到专用调度器` |
| `{@code}` | 行内代码 | `{@code userId}` |
| `{@link}` | 链接到类或方法 | `{@link UserEntity}` |
| `{@linkplain}` | 纯文本链接 | `{@linkplain UserEntity 用户实体}` |
| `{@value}` | 常量值 | `{@value #MAX_SIZE}` |
| `{@literal}` | 字面文本 | `{@literal <html>}` |

**生成与校验：**

```bash
# 生成完整 JavaDoc（聚合所有模块）
mvnd javadoc:aggregate -DskipTests

# 生成单模块 JavaDoc
mvnd javadoc:javadoc -pl apex-domain -DskipTests
# 输出目录：target/site/apidocs/

# 注释问题扫描（必须全绿）
scripts/dev/check/scan-javadoc-issues.sh
```

**常见 JavaDoc 错误修复：**

| 错误类型 | 说明 | 修复方式 |
|---------|------|---------|
| `@param` 不匹配 | 参数名与实际不符 | 修正参数名或删除多余 `@param` |
| `@return` 缺失/多余 | 有返回值但无 `@return`；`void` 却有 `@return` | 增删 `@return` 说明 |
| HTML 标签未闭合 | `<ul>` 没有 `</ul>` | 补全闭合标签 |
| 非法字符 | `<` 未转义 | 使用 `{@code <}` 或 `&lt;` |

***

<a id="5-响应式编程规范"></a>

## 5. 响应式编程规范

### 5.1 返回类型

| 场景        | 返回类型                    |
| --------- | ----------------------- |
| 0 或 1 个结果 | `Mono<T>`               |
| 0 到多个结果   | `Flux<T>`               |
| 无返回值异步操作  | `Mono<Void>`            |
| 分页结果      | `Mono<PageResponse<T>>` |

仓储查询不到数据时返回 `Mono.empty()`，由应用服务决定是否转换为业务异常。

### 5.2 操作符选择

- `map`：同步的一对一转换。

- `flatMap`：调用另一个 `Mono` 或异步服务。

- `flatMapMany`：从一个 `Mono` 展开为 `Flux`。

- `zip` / `zipWith`：组合相互独立的异步结果。

- `switchIfEmpty`：处理查询为空的分支。

- `onErrorMap`：转换异常类型。

- `onErrorResume`：提供降级或替代响应。

- `doOnSuccess`、`doOnError`：记录指标和日志，不改变主链路语义。

```java
public Mono<UserDTO> getUserById(Long userId) {
    return userRepository.findById(userId)
        .switchIfEmpty(Mono.error(new BusinessException(ResultCode.USER_NOT_FOUND)))
        .map(userAssembler::toDTO);
}
```

### 5.3 禁止阻塞响应式线程

以下操作必须封装在 `Mono.fromCallable` 或 `Mono.fromRunnable` 中，并通过 `subscribeOn` 切换到 `BlockingIoSchedulers.jdbc()` 或 `BlockingIoSchedulers.io()`：

- MyBatis-Plus 和 JDBC 调用。

- 同步 HTTP 客户端和第三方 SDK。

- 文件读写和压缩解压。

- 调用 `.block()`、`.blockFirst()`、`.blockLast()`。

生产业务链路禁止调用上述 `block` 方法。线程切换应尽可能靠近阻塞调用封装，避免把整个业务链路误放入阻塞线程池。项目提供语义化调度器：`jdbc()`、`cache()`、`storage()`、`event()`、`remote()`、`general()`；**禁止使用 `Schedulers.boundedElastic()`**。

```java
public Mono<UserPO> findUser(Long userId) {
    return Mono.fromCallable(() -> userMapper.selectById(userId))
        .subscribeOn(BlockingIoSchedulers.jdbc());
}
```

### 5.4 订阅、取消与重试

- 业务方法返回 Publisher，不在 Service 内随意 `subscribe()`。

- 由 WebFlux、消息监听器或任务框架负责最终订阅。

- 重试只用于幂等且可恢复的临时故障。

- 重试必须限定次数、超时时间和异常类型，并使用固定延迟或指数退避。

- 不对参数错误、权限错误、业务冲突和不可恢复异常重试。

```java
return webClient.get()
    .retrieve()
    .bodyToMono(String.class)
    .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
        .maxBackoff(Duration.ofSeconds(10)));
```

***

<a id="6-数据访问规范"></a>

## 6. 数据访问规范

### 6.1 Entity、PO 与 DTO

- Entity 位于领域层，表达业务状态和行为，不包含 MyBatis-Plus 注解。

- PO 位于基础设施层，只表达数据库字段和映射关系。

- DTO 位于应用层或接口契约边界，不直接作为领域实体持久化。

- 使用 Assembler 或 Converter 完成 Entity、PO、DTO 之间的转换。

### 6.2 Mapper 与 Repository

Mapper 继承 `BaseMapper<PO>`，自定义 SQL 只放在基础设施层。Repository 接口放在领域层，返回领域对象和响应式类型。

```java
public interface UserRepository {
    Mono<UserEntity> findById(Long id);
    Mono<UserEntity> findByPhone(String phone);
    Flux<UserEntity> findByIds(List<Long> ids);
    Mono<Void> save(UserEntity entity);
}
```

```java
@Repository
@RequiredArgsConstructor
public class UserRepositoryImpl implements UserRepository {
    private final UserMapper userMapper;

    @Override
    public Mono<UserEntity> findById(Long id) {
        return Mono.fromCallable(() -> userMapper.selectById(id))
            .subscribeOn(BlockingIoSchedulers.jdbc())
            .map(UserConverter::toDomain);
    }
}
```

### 6.3 SQL 与事务

- 明确列名，避免无必要的 `SELECT *`。

- 查询条件优先使用索引字段，模糊查询避免前置 `%`。

- 分页、排序和批量操作必须限制规模。

- SQL 参数使用 MyBatis 参数绑定，禁止字符串拼接。

- `SELECT ... FOR UPDATE` 必须在明确的事务边界内使用。

- 事务边界通常放在应用服务，事务内避免远程调用、消息发送和长时间计算。

- 数据库变更使用 Flyway 迁移脚本，不直接修改生产表结构；同版本号双库脚本（MySQL/PostgreSQL）必须同步维护。

- 逻辑删除、乐观锁和租户字段必须遵循已有表结构和拦截器约定。

- 时间字段：MySQL 使用 `DATETIME`（禁止 `TIMESTAMP`），Java 侧使用 `LocalDateTime`。

```java
@Mapper
public interface UserMapper extends BaseMapper<UserPO> {
    @Select("SELECT * FROM sys_user WHERE id = #{id} FOR UPDATE")
    UserPO selectByIdForUpdate(@Param("id") Long id);
}
```

***

<a id="7-api-设计规范"></a>

## 7. API 设计规范

### 7.1 路由与 Handler

接口层使用函数式 `RouterFunction` 和 WebFlux `Handler`。业务接口使用 `/api/v1`，管理接口使用 `/admin-api/v1`。

```java
@Bean
public RouterFunction<ServerResponse> authRoutes(AuthHandler handler) {
    return RouterFunctions.route()
        .POST("/api/v1/auth/login", handler::handleLogin)
        .POST("/api/v1/auth/refresh", handler::handleRefreshToken)
        .POST("/api/v1/auth/logout", handler::handleLogout)
        .build();
}
```

Handler 负责读取请求、参数校验、调用应用服务和封装响应，不实现领域规则。

```java
public Mono<ServerResponse> handleLogin(ServerRequest request) {
    return requestHandlerUtil.extractAndValidate(request, LoginRequest.class)
        .flatMap(authService::login)
        .flatMap(response -> ServerResponse.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Result.success(response)))
        .onErrorResume(this::handleError);
}
```

### 7.2 REST 与统一响应

- URL 使用名词和版本前缀，资源层级清晰。

- GET 查询资源，POST 创建或触发动作，PUT 更新，DELETE 删除。

- 分页参数统一使用 `page` 和 `size`，并设置最大 `size`。

- 响应统一使用 `Result<T>` 或项目现有分页响应类型。

- HTTP 状态码和业务错误码同时表达协议状态与业务原因。

```java
public class Result<T> {
    private Integer code;
    private String message;
    private T data;
    private Long timestamp;
}
```

### 7.3 参数校验与错误处理

请求 DTO 使用 Bean Validation 注解，例如 `@NotBlank`、`@Size`、`@Pattern`。校验失败由统一异常处理转换，不能在每个 Handler 中重复拼接错误格式。

错误码应稳定、唯一、可检索（统一在 `apex-common` 的 `ResultCode` 中维护）。不要把数据库异常、堆栈信息或内部服务地址直接返回给客户端。

### 7.4 OpenAPI 与可观测性

新增或修改接口时同步维护 SpringDoc/OpenAPI 注解、请求字段、响应字段、权限码和错误码。关键请求应通过已有日志、TraceId、RequestId、业务指标和 API 调用日志能力实现可追踪。

***

<a id="8-安全规范"></a>

## 8. 安全规范

### 8.1 认证与会话

- 认证使用项目现有 JWT 机制，区分 access token 和 refresh token。

- Token 只从受信任的请求头或项目约定位置读取。

- 退出登录、主动失效和刷新场景要处理 Token 黑名单及 TTL。

- JWT 密钥、数据库密码、Redis 密码、第三方凭证只能来自安全配置或环境变量。

- 登录失败次数、验证码和刷新接口必须结合 Redis 与 Sentinel 做保护。

### 8.2 授权

- 在接口或应用服务边界使用 `@RequirePermission`、`@RequireRole` 等项目已有权限机制。

- 管理接口默认需要认证和权限校验。

- 数据权限通过 `@DataScope` 等已有机制执行，不在业务代码中手工拼接不一致的权限条件。

- 多租户查询和写入必须带租户上下文，禁止跨租户读取或修改数据。

```java
@RequirePermission("system:user:add")
public Mono<Result<Void>> addUser(CreateUserRequest request) {
    return userService.addUser(request);
}
```

### 8.3 输入、输出与日志安全

- 所有外部参数都做类型、长度、格式和范围校验。

- SQL 使用参数绑定，文件上传校验大小、类型、名称和存储路径。

- 密码、Token 和私钥不得明文存储或输出。

- 手机号、身份证、邮箱等敏感字段按场景脱敏或加密。

- 错误响应只返回客户端需要的信息，详细堆栈写入受控日志。

***

<a id="9-测试规范"></a>

## 9. 测试规范

### 9.1 测试分层

- 领域层：覆盖聚合状态转换、值对象校验和领域规则。

- 应用层：使用 Mockito 模拟 Repository、Gateway 和 Token 服务，覆盖成功、空结果、业务异常和外部失败。

- 接口层：使用 `WebTestClient` 验证路由、参数校验、认证、权限和统一响应。

- 基础设施层：针对 Mapper、Flyway、Redis 和外部集成编写集成测试。

### 9.2 响应式测试

使用 `StepVerifier` 验证元素、完成信号、错误类型和空结果，不用生产代码中的 `block()` 替代断言。

```java
StepVerifier.create(authService.login(request))
    .assertNext(response -> assertNotNull(response.getAccessToken()))
    .verifyComplete();
```

至少覆盖：

- `Mono.just` 和 `Mono.empty` 两种查询结果。

- 业务异常和系统异常。

- 重试耗尽、超时和降级分支。

- `Flux` 的数量、顺序、背压和取消行为。

### 9.3 覆盖率与验证命令

目标值：整体不低于 60%，核心业务不低于 80%，领域层不低于 85%，工具类不低于 90%。

```bash
mvnd clean test jacoco:report
mvnd -pl apex-interfaces -am compile -DskipTests
```

测试不得依赖真实生产凭证、真实用户数据或不可控的外部服务。

***

<a id="10-性能与稳定性"></a>

## 10. 性能与稳定性

- Redis 缓存必须定义 key 规则、TTL、空值策略和失效策略。

- Caffeine 只缓存适合本地副本的数据，限制最大容量和过期时间。

- 避免缓存穿透、击穿和无限增长；更新数据时同步考虑缓存一致性。

- 大批量读取使用分页或批量接口，使用 `buffer`、`limitRate` 控制内存和下游压力。

- `flatMap` 并发度按下游承载能力设置，不能无限并发。

- 外部调用必须设置连接超时、读取超时、重试上限和降级策略。

- 使用 Sentinel 对登录、刷新、管理查询和外部依赖设置限流、熔断和降级。

- 关键链路记录耗时、成功率、错误率和 traceId，避免在高频循环中打印 INFO 日志。

***

<a id="11-构建规范mvnd"></a>

## 11. 构建规范（mvnd）

### 11.1 强制要求

- ❌ **禁止使用** `mvn`（传统 Maven 命令）。
- ✅ **必须使用** `mvnd`（Maven Daemon，版本 `2.0.0-rc-3`）。
- 未找到 mvnd 时应直接失败并提示安装，**不得回退到系统 Maven**。

原因：mvnd 守护进程模式提供约 3 倍增量构建速度提升，并自动管理守护进程。

### 11.2 环境配置

项目已配置 `.sdkmanrc`：

```bash
# .sdkmanrc
java=21.0.13.0+9-tem
mvnd=2.0.0-rc-3
```

激活环境：

```bash
cd /Users/work/apex-scaffold
sdk env            # 依据 .sdkmanrc 加载 JDK 21.0.13.0+9-tem + mvnd 2.0.0-rc-3
# 或使用仓库脚本：source scripts/build/init-build-env.sh

# 验证
mvnd --version     # 输出应包含 Apache Maven Daemon (mvnd) 2.0.0-rc-3
```

- JDK 必须为 `21.0.13.0+9-tem`；禁止依赖 `~/.sdkman/candidates/java/current`（会随 `sdk use`/`sdk default` 漂移）。
- 如需显式指定 `JAVA_HOME`，必须使用绝对路径 `/Users/eric/.sdkman/candidates/java/21.0.13.0+9-tem`。
- 若此前用过其他 JDK 构建，先 `mvnd --stop` 清掉残留 daemon，再以正确的 `JAVA_HOME` 重新构建。

### 11.3 命令对照表

| 场景 | ❌ 错误（mvn） | ✅ 正确（mvnd） |
|------|---------------|----------------|
| 编译 | `mvn compile` | `mvnd compile` |
| 打包 | `mvn package` | `mvnd package` |
| 测试 | `mvn test` | `mvnd test` |
| 安装 | `mvn install` | `mvnd install` |
| 运行 | `mvn spring-boot:run` | `mvnd spring-boot:run` |

### 11.4 完整回归门禁

```bash
sdk env
mvnd -T4 -B clean verify
```

- 并行度建议 `-T4`，不要使用过高并行度（如 `-T1C`）。CPU 争用会使计时敏感的 SLA 集成测试偶发超时（典型为 `DualServiceAuthorizationIntegrationTest` 报 `TimeoutException 3000ms`），属负载 flake，重跑或降低并行度即可。
- 门禁通过标准：反应堆全模块 `BUILD SUCCESS`、测试 `Failures: 0 / Errors: 0`。

### 11.5 守护进程管理

```bash
mvnd --status   # 查看守护进程状态
mvnd --stop     # 停止所有守护进程
```

***

<a id="12-提交与审查"></a>

## 12. 提交与审查

### 12.1 分支

- `feature/*`：新功能。
- `bugfix/*`：一般缺陷修复。
- `hotfix/*`：紧急线上修复。
- `refactor/*`：重构。

使用特性分支开发，完成测试和审查后合并到开发分支，遵循 `CONTRIBUTING.md` 的 PR 流程。

### 12.2 Conventional Commits

```text
<type>(<scope>): <subject>
```

常用类型：`feat`、`fix`、`docs`、`style`、`refactor`、`test`、`chore`。

```text
feat(auth): add SMS login strategy
fix(user): handle empty repository result
```

提交前至少执行：

```bash
mvnd spotless:check
mvnd clean test
```

### 12.3 代码审查重点

- 分层依赖是否正确，领域规则是否泄漏到接口层。

- 是否存在响应式线程上的 JDBC、文件或同步 SDK 调用。

- 是否正确处理 `Mono.empty()`、异常和取消信号。

- 参数、权限、租户和数据范围是否完整。

- SQL、事务、缓存和重试是否具备边界。

- 注释是否符合第 4 节规范，日志是否脱敏，配置是否泄露密钥。

- 测试是否覆盖新增分支并通过格式检查。

***

<a id="13-开发检查清单"></a>

## 13. 开发检查清单

### 13.1 新增业务功能

- [ ] 明确聚合、用例、领域规则和事务边界。
- [ ] 按四层结构放置 Handler、Service、Entity、Repository 和基础设施实现。
- [ ] Entity、PO、DTO 已分离并完成显式转换。
- [ ] 所有阻塞调用已隔离到专用线程池（`BlockingIoSchedulers`）。
- [ ] API 已添加路由、校验、权限、OpenAPI 和统一响应。
- [ ] 已考虑租户、数据权限、日志脱敏和幂等性。
- [ ] 已补充单元测试、响应式测试或集成测试。

### 13.2 编码与注释

- [ ] DTO/VO/Request/Response 使用 `@Data` + `@Builder` + 构造器。
- [ ] Service/Component 使用 `@Slf4j` + `@RequiredArgsConstructor`。
- [ ] Domain Entity 使用 `@Getter` + `@Builder`（不使用 `@Data`）。
- [ ] 继承类使用 `@EqualsAndHashCode(callSuper = true)`；敏感字段在 `@ToString` 中排除。
- [ ] 移除所有手写的 getter/setter/constructor/toString/equals/hashCode。
- [ ] 新增/修改的公开类型与方法 JavaDoc 符合第 4 节模板。
- [ ] `@param` 名称与签名逐字一致；`void`/`Mono<Void>` 方法无 `@return`。
- [ ] 无第 4.6 节九类坏注释；一处声明只有一个 JavaDoc 块；删除成员时其注释已同步删除。
- [ ] `grep -rn "TODO: Add method description" --include="*.java" apex-*/src/main` 为空。

### 13.3 提交代码

- [ ] `mvnd spotless:check` 通过。
- [ ] `mvnd clean test` 通过。
- [ ] `scripts/dev/check/scan-javadoc-issues.sh` 全绿。
- [ ] 没有硬编码密钥、Token 或环境地址。
- [ ] 提交信息符合 Conventional Commits。
- [ ] PR 已说明变更范围、验证方式和已知风险。

***

## 参考资料

- [贡献指南](../../CONTRIBUTING.md)
- [项目 README](../../README.md)
- [Spring WebFlux 官方文档](https://docs.spring.io/spring-framework/reference/web/webflux.html)
- [Project Reactor 参考指南](https://projectreactor.io/docs/core/release/reference/)
- [MyBatis-Plus 官方文档](https://baomidou.com/)
- [Lombok 官方文档](https://projectlombok.org/features/)
- [Conventional Commits](https://www.conventionalcommits.org/)

***

**文档维护**: Apex Team\
**版本历史**: v1.0.0（2026-10-10，整合后端开发规范、注释规范、JavaDoc 指南、Lombok 指南与构建工具标准）
