# JavaDoc 编写指南

**Apex Scaffold 项目 JavaDoc 规范与最佳实践**

---

## 📋 目录

1. [规范概述](#规范概述)
2. [类级别 JavaDoc](#类级别-javadoc)
3. [方法级别 JavaDoc](#方法级别-javadoc)
4. [字段级别 JavaDoc](#字段级别-javadoc)
5. [枚举和常量 JavaDoc](#枚举和常量-javadoc)
6. [HTML 标签使用](#html-标签使用)
7. [JavaDoc 标签参考](#javadoc-标签参考)
8. [完整示例](#完整示例)
9. [常见问题](#常见问题)
10. [检查清单](#检查清单)

---

## 规范概述

### 🎯 核心原则

1. **完整性**：所有 `public` 和 `protected` 类、接口、方法、字段必须有 JavaDoc
2. **准确性**：描述必须与代码实现一致，参数、返回值、异常描述必须准确
3. **可读性**：使用中文描述业务逻辑，技术术语可保留英文，使用 HTML 标签提升可读性
4. **规范性**：遵循统一的格式和标签使用规范

### 📌 强制要求

- **必须**：`@author Apex Team`（固定作者）
- **必须**：`@version` 使用项目版本号（当前 1.2.0）
- **必须**：`@since` 标注首次出现的版本号
- **推荐**：使用 `{@code}`、`{@link}`、`{@see}` 等 JavaDoc 标签
- **推荐**：复杂类添加使用示例 `<pre>{@code}</pre>`

### ⚠️ 禁止事项

- ❌ 禁止手写空 JavaDoc（如 `/** */`）
- ❌ 禁止复制粘贴不准确的描述
- ❌ 禁止使用英文注释描述业务逻辑（技术术语除外）
- ❌ 禁止遗漏 `@param`、`@return`、`@throws` 标签

---

## 类级别 JavaDoc

### 📝 标准模板

```java
/**
 * [类的简短描述（一句话，建议 20-50 字）]
 *
 * <p>[类的详细说明，包括：
 * - 类的职责和作用
 * - 关键设计模式或架构考量
 * - 使用场景和限制
 * - 与其他组件的协作关系]
 *
 * <h2>主要功能</h2>
 * <ul>
 *   <li>功能点 1：说明</li>
 *   <li>功能点 2：说明</li>
 *   <li>功能点 3：说明</li>
 * </ul>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * // 代码示例
 * ExampleClass example = ExampleClass.builder()
 *     .field1("value1")
 *     .field2(123)
 *     .build();
 * example.doSomething();
 * }</pre>
 *
 * <h2>注意事项</h2>
 * <ul>
 *   <li>注意事项 1</li>
 *   <li>注意事项 2</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.0.0
 * @see RelatedClass
 */
```

### 🎨 不同类型的类级 JavaDoc

#### 1. 持久化对象（PO）

```java
/**
 * 用户持久化对象
 *
 * <p>对应数据库表 {@code sys_user}，存储用户的完整信息，包括：
 * <ul>
 *   <li>基本信息：用户名、昵称、头像、性别、生日、地理位置</li>
 *   <li>微信信息：OpenID、UnionID、AccessToken、RefreshToken</li>
 *   <li>认证信息：密码（BCrypt 哈希）、手机号</li>
 *   <li>组织信息：部门ID、租户ID（多租户隔离）</li>
 * </ul>
 *
 * <h2>表结构</h2>
 * <p>表名：{@code sys_user}<br>
 * 主键：{@code id}（继承自 {@link BasePO}）<br>
 * 唯一索引：{@code uk_open_id}（微信OpenID）、{@code uk_username}（登录账号）<br>
 * 普通索引：{@code idx_phone}（手机号）、{@code idx_dept_id}（部门ID）
 *
 * <h2>使用场景</h2>
 * <ul>
 *   <li>微信 APP 登录：通过 OpenID/UnionID 查询或创建用户</li>
 *   <li>账号密码登录：通过 username 查询用户并验证密码</li>
 *   <li>数据权限控制：通过 deptId 和 tenantId 实现部门级、租户级数据隔离</li>
 * </ul>
 *
 * <h2>注意事项</h2>
 * <ul>
 *   <li>密码字段必须存储 BCrypt 哈希值，禁止存储明文</li>
 *   <li>OpenID 和 UnionID 来自微信授权，不可手动修改</li>
 *   <li>多租户场景下 tenantId 必填，单租户场景可为 NULL</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.0.0
 * @see BasePO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
public class UserPO extends BasePO {
    // ...
}
```

#### 2. Mapper 接口

```java
/**
 * 用户数据 MyBatis Mapper 接口
 *
 * <p>负责 {@code sys_user} 表的持久化操作，提供用户记录的 CRUD 和特殊查询能力。
 * 继承 MyBatis-Plus {@link BaseMapper} 获得通用单表 CRUD 操作。
 *
 * <h2>核心功能</h2>
 * <ul>
 *   <li>通用 CRUD：继承自 {@link BaseMapper}，支持单表增删改查</li>
 *   <li>行级锁查询：{@link #selectByIdForUpdate} 提供 {@code SELECT ... FOR UPDATE} 悲观锁支持</li>
 *   <li>账号封禁：{@link #banForPhotoReports} 因举报照片审核失败而封禁账号</li>
 * </ul>
 *
 * <h2>使用场景</h2>
 * <ul>
 *   <li>并发更新：使用 {@code selectByIdForUpdate} 锁定记录后更新，防止并发冲突</li>
 *   <li>内容审核：照片举报审核通过后调用 {@code banForPhotoReports} 封禁违规用户</li>
 * </ul>
 *
 * <h2>注意事项</h2>
 * <ul>
 *   <li>行级锁查询必须在事务中使用，否则锁立即释放</li>
 *   <li>封禁操作会更新 {@code version} 字段（乐观锁）</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.0.0
 * @see UserPO
 * @see BaseMapper
 */
@Mapper
public interface UserMapper extends BaseMapper<UserPO> {
    // ...
}
```

#### 3. DTO 类

```java
/**
 * 登录请求 DTO
 *
 * <p>微信 APP 登录请求参数封装，支持多种登录方式：
 * <ul>
 *   <li>WX_CODE：微信授权码登录（OAuth 2.0 授权流程）</li>
 *   <li>WX_PHONE：微信一键登录（微信手机号快捷授权）</li>
 *   <li>WX_SMS：微信短信验证码登录（手机号 + 验证码）</li>
 * </ul>
 *
 * <h2>字段验证规则</h2>
 * <ul>
 *   <li>手机号：{@code ^1[3-9]\\d{9}$} 格式（中国大陆手机号）</li>
 *   <li>验证码：6 位数字</li>
 *   <li>用户名：1-50 字符</li>
 * </ul>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * LoginRequest request = LoginRequest.builder()
 *     .loginType("WX_CODE")
 *     .code("wx_auth_code_123456")
 *     .deviceId("device_uuid_123")
 *     .build();
 * }</pre>
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {
    // ...
}
```

#### 4. Service 类

```java
/**
 * 认证服务应用类
 *
 * <p>处理微信 APP 登录、Token 生成与刷新、退出登录等核心功能。
 * 作为应用层服务，协调领域层和基础设施层完成认证流程。
 *
 * <h2>核心功能</h2>
 * <ul>
 *   <li>微信 APP 登录：支持验证码登录、一键登录和短信验证码登录</li>
 *   <li>Token 刷新：使用 refreshToken 换取新的 accessToken</li>
 *   <li>退出登录：使当前 Token 失效并清理相关数据</li>
 * </ul>
 *
 * <h2>设计模式</h2>
 * <ul>
 *   <li>策略模式：登录方式通过 {@link LoginStrategy} 接口实现</li>
 * </ul>
 *
 * <h2>安全特性</h2>
 * <ul>
 *   <li>集成 Sentinel 限流保护登录和刷新接口</li>
 *   <li>Token 黑名单机制支持主动失效</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.0.0
 * @see TokenService
 * @see LoginStrategyProvider
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {
    // ...
}
```

---

## 方法级别 JavaDoc

### 📝 标准模板

```java
/**
 * [方法的简短描述（动词开头，建议 10-30 字）]
 *
 * <p>[方法的详细说明，包括：
 * - 方法的具体行为
 * - 业务逻辑流程
 * - 异常场景处理
 * - 性能考量或副作用]
 *
 * @param paramName 参数说明（说明参数的作用、约束、取值范围）
 * @return 返回值说明（说明返回内容、可能的状态）
 * @throws ExceptionType 异常说明（说明何时抛出、如何处理）
 * @since 1.1.0
 * @see RelatedMethod
 */
```

### 🎨 不同类型的方法级 JavaDoc

#### 1. 查询方法

```java
/**
 * 锁定用户记录（悲观锁）
 *
 * <p>使用 {@code SELECT ... FOR UPDATE} 行级锁锁定用户记录，
 * 防止并发修改。必须在事务中使用，事务提交或回滚时释放锁。
 *
 * <p><strong>使用场景</strong>：
 * <ul>
 *   <li>积分扣减：锁定账户余额后执行扣减操作</li>
 *   <li>状态变更：确保状态机转换的原子性</li>
 *   <li>并发写入：多个请求同时修改同一用户时避免冲突</li>
 * </ul>
 *
 * <p><strong>注意事项</strong>：
 * <ul>
 *   <li>必须在 {@code @Transactional} 事务中调用，否则锁立即释放</li>
 *   <li>锁等待超时时间由 MySQL {@code innodb_lock_wait_timeout} 控制（默认 50 秒）</li>
 *   <li>避免长时间持有锁，影响并发性能</li>
 * </ul>
 *
 * @param userId 用户ID，必须存在且未删除
 * @return 用户持久化对象，用户不存在返回 {@code null}
 */
@Select("SELECT * FROM sys_user WHERE id = #{userId} AND deleted = 0 FOR UPDATE")
UserPO selectByIdForUpdate(@Param("userId") Long userId);
```

#### 2. 更新方法

```java
/**
 * 停用账号并清空敏感数据（账号注销）
 *
 * <p>执行用户账号注销操作，符合 GDPR "被遗忘权" 要求：
 * <ul>
 *   <li>停用账号：{@code status=0, deleted=1}</li>
 *   <li>清空身份信息：{@code open_id, union_id} 设为 {@code NULL}</li>
 *   <li>清空个人信息：{@code nick_name, avatar_url, phone} 等设为 {@code NULL}</li>
 * </ul>
 *
 * <p><strong>注意事项</strong>：
 * <ul>
 *   <li>此操作不可逆，清空的数据无法恢复</li>
 *   <li>清空 {@code open_id} 后，用户可使用相同微信号重新注册</li>
 *   <li>保留用户ID（{@code id}）和创建时间（{@code create_time}）用于数据分析</li>
 * </ul>
 *
 * @param userId 用户ID，必须存在且未删除
 * @return 更新行数，成功返回 1，用户不存在返回 0
 */
int deactivateForAccountDeletion(@Param("userId") Long userId);
```

#### 3. 转换方法

```java
/**
 * 将用户领域实体转换为持久化对象
 *
 * <p>用于将内存中的领域实体保存到数据库前的转换。
 * 转换规则：同名字段自动映射，未匹配字段忽略。
 *
 * <p><strong>使用示例</strong>：
 * <pre>{@code
 * UserEntity entity = UserEntity.builder()
 *     .id(1L)
 *     .openId("wx_123456")
 *     .nickname("张三")
 *     .build();
 * UserPO po = converter.toPo(entity);
 * userMapper.insert(po);
 * }</pre>
 *
 * @param entity 用户领域实体，{@code null} 返回 {@code null}
 * @return 用户持久化对象，包含所有映射字段
 */
UserPO toPo(UserEntity entity);
```

#### 4. 业务方法

```java
/**
 * 创建用户
 *
 * <p>完成用户注册流程：
 * <ol>
 *   <li>校验用户名唯一性（查询数据库）</li>
 *   <li>加密密码（使用 BCrypt 算法）</li>
 *   <li>分配默认角色（普通用户角色）</li>
 *   <li>发送欢迎通知（异步调用通知服务）</li>
 * </ol>
 *
 * <p><strong>事务边界</strong>：本方法开启新事务，确保用户创建的原子性。
 *
 * <p><strong>异常处理</strong>：
 * <ul>
 *   <li>用户名已存在：抛出 {@link BusinessException}，错误码 {@code USER_ALREADY_EXISTS}</li>
 *   <li>密码加密失败：抛出 {@link BusinessException}，错误码 {@code INTERNAL_ERROR}</li>
 * </ul>
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

---

## 字段级别 JavaDoc

### 📝 标准模板

```java
/**
 * [字段说明（一句话）]
 *
 * <p>[字段详细描述，包括：
 * - 字段的业务含义
 * - 取值范围或格式要求
 * - 默认值说明
 * - 是否必填/可选]
 *
 * @since 1.0.0
 */
```

### 🎨 不同类型的字段级 JavaDoc

#### 1. 数据库字段（PO）

```java
/**
 * 租户ID（多租户隔离）
 *
 * <p>对应 {@code sys_user.tenant_id} 列，实现租户级数据隔离。
 * 配合 {@code @TenantAware} 注解和 {@code TenantContext} 自动填充和过滤。
 * 多租户场景必填，单租户场景可为 {@code null}。
 *
 * @since 2.5.0
 */
@TableField("tenant_id")
private String tenantId;
```

#### 2. DTO 字段

```java
/**
 * 微信授权码（code）
 *
 * <p>用户同意授权后，微信返回的授权码，有效期 5 分钟。
 * 用于 WX_CODE 登录方式，调用微信 API 换取 AccessToken 和 OpenID。
 *
 * <p><strong>格式要求</strong>：1-100 字符，字母数字组合
 */
@Schema(description = "微信授权码")
@Size(min = 1, max = 100, message = "{param.size_invalid}")
private String code;
```

#### 3. 配置字段

```java
/**
 * AccessToken 过期时间（毫秒）
 *
 * <p>JWT AccessToken 的有效期，默认 2 小时（7200000 毫秒）。
 * 过期后客户端需使用 RefreshToken 刷新。
 *
 * <p><strong>配置项</strong>：{@code jwt.access-token-expire}
 */
@Value("${jwt.access-token-expire:7200000}")
private long accessTokenExpireMs;
```

#### 4. 依赖注入字段

```java
/**
 * 用户仓储接口
 *
 * <p>领域层定义的用户仓储契约，由基础设施层实现。
 * 提供用户的持久化和查询能力，隔离领域层与数据库实现细节。
 */
private final UserRepository userRepository;
```

---

## 枚举和常量 JavaDoc

### 枚举类

```java
/**
 * 用户状态枚举
 *
 * <p>定义用户账号的所有可能状态，用于状态机转换和权限控制。
 *
 * <h2>状态转换规则</h2>
 * <ul>
 *   <li>INACTIVE → ACTIVE：用户激活账号</li>
 *   <li>ACTIVE → SUSPENDED：管理员封禁账号</li>
 *   <li>SUSPENDED → ACTIVE：管理员解封账号</li>
 *   <li>任意状态 → DELETED：用户注销账号（不可逆）</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.0.0
 */
public enum UserStatus {
    
    /**
     * 未激活
     *
     * <p>新注册用户的初始状态，需要验证邮箱或手机号后激活
     */
    INACTIVE(0, "未激活"),
    
    /**
     * 正常
     *
     * <p>账号已激活，可正常使用所有功能
     */
    ACTIVE(1, "正常"),
    
    /**
     * 已封禁
     *
     * <p>账号被管理员封禁，无法登录和使用功能。
     * 封禁原因记录在 {@code ban_reason} 字段
     */
    SUSPENDED(2, "已封禁"),
    
    /**
     * 已注销
     *
     * <p>用户主动注销账号，个人信息已清空，不可恢复
     */
    DELETED(3, "已注销");
    
    // ...
}
```

### 常量类

```java
/**
 * Redis 键前缀常量
 *
 * <p>统一管理 Redis 键的命名规范，避免键冲突和命名混乱。
 *
 * <h2>命名规则</h2>
 * <ul>
 *   <li>业务模块前缀：{@code apex:}<li>
 *   <li>功能模块：{@code user:}、{@code token:}、{@code sms:} 等</li>
 *   <li>具体键名：描述性名称，使用冒号分隔</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.0.0
 */
public class RedisKeyConstants {
    
    /**
     * Token 黑名单键前缀
     *
     * <p>格式：{@code apex:token:blacklist:{tokenId}}
     * <p>TTL：与 Token 过期时间一致（默认 2 小时）
     * <p>用途：退出登录时将 Token 加入黑名单，拒绝后续请求
     */
    public static final String TOKEN_BLACKLIST_PREFIX = "apex:token:blacklist:";
    
    /**
     * 短信验证码键前缀
     *
     * <p>格式：{@code apex:sms:code:{phone}}
     * <p>TTL：5 分钟
     * <p>用途：存储短信验证码，登录时验证
     */
    public static final String SMS_CODE_PREFIX = "apex:sms:code:";
}
```

---

## HTML 标签使用

### 常用 HTML 标签

| 标签 | 用途 | 示例 |
|------|------|------|
| `<p>` | 段落分隔 | `<p>这是第一段</p><p>这是第二段</p>` |
| `<ul><li>` | 无序列表 | `<ul><li>项目1</li><li>项目2</li></ul>` |
| `<ol><li>` | 有序列表 | `<ol><li>步骤1</li><li>步骤2</li></ol>` |
| `<h2>` | 二级标题 | `<h2>主要功能</h2>` |
| `<h3>` | 三级标题 | `<h3>子功能</h3>` |
| `<strong>` | 加粗强调 | `<strong>注意</strong>` |
| `<code>` | 行内代码 | `<code>userId</code>` |
| `<pre>` | 预格式化文本 | `<pre>多行代码块</pre>` |
| `<br>` | 换行 | `第一行<br>第二行` |
| `<table>` | 表格 | `<table><tr><td>单元格</td></tr></table>` |

### 使用示例

```java
/**
 * 用户状态转换
 *
 * <p>根据业务规则转换用户状态：
 * <table border="1">
 *   <tr>
 *     <th>当前状态</th>
 *     <th>目标状态</th>
 *     <th>触发条件</th>
 *   </tr>
 *   <tr>
 *     <td>INACTIVE</td>
 *     <td>ACTIVE</td>
 *     <td>验证邮箱或手机号</td>
 *   </tr>
 *   <tr>
 *     <td>ACTIVE</td>
 *     <td>SUSPENDED</td>
 *     <td>管理员封禁</td>
 *   </tr>
 * </table>
 *
 * @param userId 用户ID
 * @param targetStatus 目标状态
 * @return 是否转换成功
 */
```

---

## JavaDoc 标签参考

### 通用标签

| 标签 | 用途 | 示例 |
|------|------|------|
| `@author` | 作者信息 | `@author Apex Team` |
| `@version` | 版本号 | `@version 1.2.0` |
| `@since` | 首次出现版本 | `@since 1.0.0` |
| `@see` | 相关类或方法 | `@see UserEntity` |
| `@deprecated` | 标记已废弃 | `@deprecated 使用 {@link #newMethod()} 替代` |

### 方法标签

| 标签 | 用途 | 示例 |
|------|------|------|
| `@param` | 参数说明 | `@param userId 用户ID，必须存在` |
| `@return` | 返回值说明 | `@return 用户信息，不存在返回 null` |
| `@throws` | 异常说明 | `@throws BusinessException 用户不存在时抛出` |

### 内联标签

| 标签 | 用途 | 示例 |
|------|------|------|
| `{@code}` | 行内代码 | `{@code userId}` |
| `{@link}` | 链接到类或方法 | `{@link UserEntity}` |
| `{@linkplain}` | 纯文本链接 | `{@linkplain UserEntity 用户实体}` |
| `{@value}` | 常量值 | `{@value #MAX_SIZE}` |
| `{@literal}` | 字面文本 | `{@literal <html>}` |

---

## 完整示例

### 示例 1：完整的 PO 类

参考项目中的 [UserPO.java](file:///Users/work/apex-scaffold/apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/persistence/po/UserPO.java)

### 示例 2：完整的 Mapper 接口

参考项目中的 [UserMapper.java](file:///Users/work/apex-scaffold/apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/persistence/mapper/UserMapper.java)

### 示例 3：完整的 Converter 接口

参考项目中的 [UserPoConverter.java](file:///Users/work/apex-scaffold/apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/persistence/converter/UserPoConverter.java)

### 示例 4：完整的 DTO 类

参考项目中的 [LoginRequest.java](file:///Users/work/apex-scaffold/apex-application/src/main/java/com/apex/scaffold/application/dto/LoginRequest.java)

---

## 常见问题

### Q1：何时使用 `{@code}` 和 `<code>`？

**A**：
- `{@code}` 用于行内代码，会自动转义 HTML 字符：`{@code List<String>}`
- `<code>` 也用于行内代码，但不会自动转义：`<code>userId</code>`
- 推荐优先使用 `{@code}`

### Q2：何时使用 `{@link}` 和 `@see`？

**A**：
- `{@link}` 用于句子中引用类或方法：`使用 {@link UserService} 处理业务逻辑`
- `@see` 用于文档末尾列出相关类：`@see UserEntity`

### Q3：如何处理过长的方法描述？

**A**：
- 将描述分段，使用 `<p>` 标签分隔
- 使用 `<h2>` 二级标题组织内容
- 使用 `<ul>` 列表展示要点

### Q4：如何在 JavaDoc 中展示 SQL 语句？

**A**：
```java
/**
 * <p><strong>SQL 语义</strong>：
 * <pre>{@code
 * UPDATE sys_user 
 * SET status=0, update_time=NOW() 
 * WHERE id=#{userId}
 * }</pre>
 */
```

### Q5：private 方法需要 JavaDoc 吗？

**A**：
- **可选**：`private` 方法可以不写 JavaDoc
- **推荐**：复杂的 `private` 方法建议添加简短注释说明逻辑

---

## 检查清单

在提交代码前，请确保：

- [ ] 所有 `public` 类/接口有类级 JavaDoc
- [ ] 所有 `public` 方法有方法级 JavaDoc
- [ ] 所有 `public` 字段/常量有字段级 JavaDoc
- [ ] JavaDoc 包含 `@author Apex Team`
- [ ] JavaDoc 包含 `@version` 版本号
- [ ] JavaDoc 包含 `@since` 版本号
- [ ] JavaDoc 使用中文描述业务逻辑
- [ ] JavaDoc 使用 HTML 标签提升可读性
- [ ] 方法 JavaDoc 完整标注 `@param`、`@return`、`@throws`
- [ ] 复杂类添加使用示例 `<pre>{@code}</pre>`
- [ ] 执行 `mvnd javadoc:aggregate -DskipTests` 无警告

---

## 生成 JavaDoc

### 命令

```bash
# 生成完整 JavaDoc（聚合所有模块）
mvnd javadoc:aggregate -DskipTests

# 生成单模块 JavaDoc
mvnd javadoc:javadoc -pl apex-domain -DskipTests

# 输出目录
# target/site/apidocs/
```

### 常见错误修复

| 错误类型 | 说明 | 修复方式 |
|---------|------|---------|
| `@param` 不匹配 | 参数名与实际不符 | 修正参数名或删除多余 `@param` |
| `@return` 缺失 | 有返回值但无 `@return` | 添加 `@return` 说明 |
| HTML 标签未闭合 | `<ul>` 没有 `</ul>` | 补全闭合标签 |
| 非法字符 | `<` 未转义 | 使用 `{@code <}` 或 `&lt;` |

---

**最后更新**：2026-09-02  
**维护者**：Apex Team

如有疑问或建议，请联系团队负责人或在项目 Issue 中提出。
