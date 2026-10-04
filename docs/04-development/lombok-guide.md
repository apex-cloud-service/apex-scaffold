# Lombok 使用指南

**Apex Scaffold 项目 Lombok 规范与最佳实践**

---

## 📋 目录

1. [Lombok 简介](#lombok-简介)
2. [核心注解详解](#核心注解详解)
3. [不同场景的最佳实践](#不同场景的最佳实践)
4. [Lombok 配置](#lombok-配置)
5. [IDE 插件安装](#ide-插件安装)
6. [常见问题](#常见问题)
7. [注意事项](#注意事项)
8. [检查清单](#检查清单)

---

## Lombok 简介

### 🎯 什么是 Lombok

Lombok 是一个 Java 库，通过注解在编译时自动生成样板代码（getter/setter/constructor/toString/equals/hashCode 等），减少代码冗余，提升开发效率。

### ✨ 核心优势

- **减少样板代码**：自动生成 getter/setter/constructor 等方法，减少 80% 以上的样板代码
- **提升可读性**：类结构更清晰，关注业务逻辑而非样板代码
- **降低维护成本**：字段变更时无需手动更新相关方法
- **编译时生成**：零运行时开销，性能与手写代码一致

### 📦 项目依赖

```xml
<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <version>1.18.36</version>
    <scope>provided</scope>
</dependency>
```

**注意**：`scope` 为 `provided`，仅编译时需要，不会打包到最终 JAR 中。

---

## 核心注解详解

### 1. @Data

**功能**：生成 getter/setter/toString/equals/hashCode/requiredArgsConstructor

**适用场景**：DTO、VO、Request、Response 等数据传输对象

**生成的方法**：
- 所有字段的 `getter` 方法
- 所有非 `final` 字段的 `setter` 方法
- `toString()` 方法
- `equals()` 和 `hashCode()` 方法
- `final` 字段的构造器（如果有 `final` 字段）

**示例**：

```java
@Data
public class UserDTO {
    private Long id;
    private String username;
    private String nickname;
}

// 等价于手写：
public class UserDTO {
    private Long id;
    private String username;
    private String nickname;
    
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    
    @Override
    public String toString() { /* ... */ }
    
    @Override
    public boolean equals(Object o) { /* ... */ }
    
    @Override
    public int hashCode() { /* ... */ }
}
```

---

### 2. @Getter / @Setter

**功能**：单独生成 getter 或 setter 方法

**适用场景**：需要精细控制字段访问权限时

**示例**：

```java
// 类级别：为所有字段生成 getter
@Getter
public class User {
    private Long id;
    private String username;
    // 只读，不生成 setter
}

// 字段级别：单独控制
public class User {
    @Getter @Setter
    private Long id;
    
    @Getter  // 只读
    private String username;
    
    private String password;  // 私有，不生成 getter/setter
}
```

**访问级别控制**：

```java
@Getter(AccessLevel.PROTECTED)  // 生成 protected getter
@Setter(AccessLevel.PRIVATE)    // 生成 private setter
private String internalField;
```

---

### 3. @Builder

**功能**：生成 Builder 模式的构建器

**适用场景**：需要流式 API 构建对象，尤其是字段较多的类

**示例**：

```java
@Builder
public class UserDTO {
    private Long id;
    private String username;
    private String nickname;
}

// 使用方式
UserDTO user = UserDTO.builder()
    .id(1L)
    .username("zhangsan")
    .nickname("张三")
    .build();
```

**与 @Data 组合**：

```java
@Data
@Builder
public class UserDTO {
    private Long id;
    private String username;
}
```

**注意**：`@Builder` 会生成全参构造器，与 `@NoArgsConstructor` 冲突，需显式声明：

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDTO {
    // ...
}
```

---

### 4. @NoArgsConstructor / @AllArgsConstructor

**功能**：
- `@NoArgsConstructor`：生成无参构造器
- `@AllArgsConstructor`：生成全参构造器

**适用场景**：
- DTO/VO：需要 Jackson/Jackson 序列化/反序列化
- JPA Entity：需要无参构造器

**示例**：

```java
@NoArgsConstructor
@AllArgsConstructor
public class UserDTO {
    private Long id;
    private String username;
}

// 等价于
public class UserDTO {
    private Long id;
    private String username;
    
    public UserDTO() {}
    
    public UserDTO(Long id, String username) {
        this.id = id;
        this.username = username;
    }
}
```

**访问级别控制**：

```java
@NoArgsConstructor(access = AccessLevel.PROTECTED)  // protected 构造器
@AllArgsConstructor(access = AccessLevel.PRIVATE)   // private 构造器（配合 Builder）
```

---

### 5. @RequiredArgsConstructor

**功能**：生成包含所有 `final` 字段和 `@NonNull` 字段的构造器

**适用场景**：Service/Component 类的依赖注入

**示例**：

```java
@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final TokenService tokenService;
}

// 等价于
@Service
public class UserService {
    private final UserRepository userRepository;
    private final TokenService tokenService;
    
    public UserService(UserRepository userRepository, TokenService tokenService) {
        this.userRepository = userRepository;
        this.tokenService = tokenService;
    }
}
```

**Spring 依赖注入**：
- Spring 4.3+ 单构造器可省略 `@Autowired`
- `@RequiredArgsConstructor` + `final` 字段 = 最佳实践

---

### 6. @Slf4j

**功能**：自动生成 `log` 字段（SLF4J Logger）

**适用场景**：需要日志记录的类

**示例**：

```java
@Slf4j
@Service
public class UserService {
    public void createUser(UserDTO dto) {
        log.info("Creating user: {}", dto.getUsername());
        log.debug("User details: {}", dto);
        log.error("Failed to create user", exception);
    }
}

// 等价于
@Service
public class UserService {
    private static final org.slf4j.Logger log = 
        org.slf4j.LoggerFactory.getLogger(UserService.class);
    
    public void createUser(UserDTO dto) {
        log.info("Creating user: {}", dto.getUsername());
    }
}
```

**其他日志框架**：
- `@Log`：java.util.logging.Logger
- `@Log4j`：Apache Log4j
- `@Log4j2`：Apache Log4j2
- `@CommonsLog`：Apache Commons Logging

---

### 7. @EqualsAndHashCode

**功能**：生成 `equals()` 和 `hashCode()` 方法

**适用场景**：需要自定义 equals/hashCode 逻辑，或继承关系处理

**示例**：

```java
@EqualsAndHashCode
public class User {
    private Long id;
    private String username;
}

// 继承场景
@EqualsAndHashCode(callSuper = true)
public class UserPO extends BasePO {
    private String username;
}

// 排除字段
@EqualsAndHashCode(exclude = {"password", "accessToken"})
public class User {
    private Long id;
    private String username;
    private String password;      // 不参与 equals/hashCode
    private String accessToken;   // 不参与 equals/hashCode
}

// 仅包含指定字段
@EqualsAndHashCode(of = {"id"})
public class User {
    private Long id;
    private String username;  // 不参与 equals/hashCode
}
```

**注意**：
- `@Data` 默认包含 `@EqualsAndHashCode`
- 继承时必须设置 `callSuper = true`

---

### 8. @ToString

**功能**：生成 `toString()` 方法

**适用场景**：需要自定义 toString 格式或排除敏感字段

**示例**：

```java
@ToString
public class User {
    private Long id;
    private String username;
}
// 输出：User(id=1, username=zhangsan)

// 排除敏感字段
@ToString(exclude = {"password", "accessToken"})
public class User {
    private Long id;
    private String username;
    private String password;      // 不输出
    private String accessToken;   // 不输出
}

// 包含父类字段
@ToString(callSuper = true)
public class UserPO extends BasePO {
    private String username;
}
// 输出：UserPO(super=BasePO(id=1, createTime=...), username=zhangsan)
```

**注意**：
- `@Data` 默认包含 `@ToString`
- 敏感字段（密码、Token）必须排除

---

### 9. @NonNull

**功能**：参数非空检查，自动生成 `if (param == null) throw new NullPointerException()`

**适用场景**：方法参数非空校验

**示例**：

```java
public void setUsername(@NonNull String username) {
    this.username = username;
}

// 等价于
public void setUsername(String username) {
    if (username == null) {
        throw new NullPointerException("username is marked non-null but is null");
    }
    this.username = username;
}
```

---

### 10. @Cleanup

**功能**：自动调用资源的 `close()` 方法（类似 try-with-resources）

**适用场景**：需要手动管理资源关闭（不推荐，优先使用 try-with-resources）

**示例**：

```java
public void readFile(String path) throws IOException {
    @Cleanup InputStream in = new FileInputStream(path);
    // 自动调用 in.close()
}

// 等价于
public void readFile(String path) throws IOException {
    try (InputStream in = new FileInputStream(path)) {
        // ...
    }
}
```

**注意**：项目中已禁用 `@Cleanup`（见 `lombok.config`），优先使用 try-with-resources。

---

## 不同场景的最佳实践

### 场景 1：DTO / VO / Request / Response 类

**推荐注解**：`@Data` + `@Builder` + 构造器

```java
/**
 * 用户信息响应 DTO
 *
 * <p>封装用户基本信息，用于接口返回。
 *
 * @author Apex Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDTO {
    
    /**
     * 用户ID
     *
     * <p>系统唯一标识
     */
    private Long id;
    
    /**
     * 用户名
     *
     * <p>登录账号，3-20字符
     */
    private String username;
    
    /**
     * 用户昵称
     *
     * <p>显示名称，支持中文
     */
    private String nickname;
}
```

**为什么这样做**：
- `@Data`：自动生成 getter/setter/toString/equals/hashCode
- `@Builder`：支持流式构建，代码更优雅
- `@NoArgsConstructor`：支持 Jackson 反序列化
- `@AllArgsConstructor`：支持全参构造（Builder 需要）

---

### 场景 2：Domain Entity（领域实体）

**推荐注解**：`@Getter` + `@Builder` + 私有构造器

```java
/**
 * 用户聚合根
 *
 * <p>DDD 领域层用户实体，封装用户核心业务逻辑。
 *
 * @author Apex Team
 * @since 1.0.0
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {
    
    /**
     * 用户ID
     */
    private Long id;
    
    /**
     * 用户名
     */
    private String username;
    
    /**
     * 用户状态
     */
    private UserStatus status;
    
    /**
     * 修改用户状态
     *
     * <p>领域方法，封装状态变更业务规则。
     *
     * @param newStatus 新状态
     * @throws BusinessException 状态转换不合法时抛出
     */
    public void changeStatus(UserStatus newStatus) {
        // 业务逻辑：状态转换校验
        if (!canTransitionTo(newStatus)) {
            throw new BusinessException(ResultCode.INVALID_STATUS_TRANSITION);
        }
        this.status = newStatus;
    }
    
    private boolean canTransitionTo(UserStatus newStatus) {
        // 状态转换规则
        return true;
    }
}
```

**为什么这样做**：
- `@Getter`：只读属性，通过领域方法修改
- `@Builder`：支持流式构建实体
- `@AllArgsConstructor(access = PRIVATE)`：Builder 专用
- `@NoArgsConstructor(access = PROTECTED)`：ORM 框架需要
- 不使用 `@Data`：避免自动生成 setter，保持封装性

---

### 场景 3：Service / Component 类

**推荐注解**：`@Slf4j` + `@RequiredArgsConstructor`

```java
/**
 * 用户应用服务
 *
 * <p>协调领域层和基础设施层，编排用户相关用例。
 *
 * @author Apex Team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {
    
    /**
     * 用户仓储接口
     */
    private final UserRepository userRepository;
    
    /**
     * Token 服务
     */
    private final TokenService tokenService;
    
    /**
     * 创建用户
     *
     * <p>完成用户注册流程：
     * <ul>
     *   <li>校验用户名唯一性</li>
     *   <li>加密密码</li>
     *   <li>分配默认角色</li>
     *   <li>发送欢迎通知</li>
     * </ul>
     *
     * @param request 用户创建请求
     * @return 创建成功的用户 DTO
     * @throws BusinessException 用户名已存在时抛出
     */
    public Mono<UserDTO> createUser(CreateUserRequest request) {
        log.info("Creating user: {}", request.getUsername());
        
        return userRepository.findByUsername(request.getUsername())
            .flatMap(existingUser -> {
                log.warn("Username already exists: {}", request.getUsername());
                return Mono.error(new BusinessException(ResultCode.USER_ALREADY_EXISTS));
            })
            .switchIfEmpty(Mono.defer(() -> {
                // 创建用户逻辑
                return Mono.just(new UserDTO());
            }));
    }
}
```

**为什么这样做**：
- `@Slf4j`：自动生成日志字段
- `@RequiredArgsConstructor`：依赖注入，final 字段不可变
- Spring 4.3+ 单构造器自动注入，无需 `@Autowired`

---

### 场景 4：PO（持久化对象）

**推荐注解**：`@Data` + `@Builder` + 构造器 + `@EqualsAndHashCode(callSuper = true)`

```java
/**
 * 用户持久化对象
 *
 * <p>对应数据库表 {@code sys_user}。
 *
 * @author Apex Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
public class UserPO extends BasePO {
    
    /**
     * 用户名
     *
     * <p>登录账号，唯一索引 {@code uk_username}
     */
    @TableField("username")
    private String username;
    
    /**
     * 密码
     *
     * <p>BCrypt 哈希值，禁止存储明文
     */
    @TableField("password")
    private String password;
}
```

**为什么这样做**：
- `@Data`：PO 需要完整的 getter/setter/equals/hashCode
- `@EqualsAndHashCode(callSuper = true)`：包含父类字段（id/createTime 等）
- `@Builder`：方便构建测试数据

---

### 场景 5：Converter / Mapper 接口（MapStruct）

**推荐注解**：无（MapStruct 自己处理）

```java
/**
 * 用户实体与持久化对象转换器（MapStruct）
 *
 * @author Apex Team
 * @since 1.0.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface UserPoConverter {
    
    /**
     * 将用户领域实体转换为持久化对象
     *
     * @param entity 用户领域实体
     * @return 用户持久化对象
     */
    UserPO toPo(UserEntity entity);
    
    /**
     * 将用户持久化对象转换为领域实体
     *
     * @param po 用户持久化对象
     * @return 用户领域实体
     */
    UserEntity toEntity(UserPO po);
}
```

**为什么这样做**：
- MapStruct 接口不需要 Lombok 注解
- MapStruct 编译时生成实现类

---

### 场景 6：配置类

**推荐注解**：`@Data` + `@ConfigurationProperties`

```java
/**
 * JWT 配置属性
 *
 * <p>从配置文件加载 JWT 相关配置。
 *
 * @author Apex Team
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {
    
    /**
     * JWT 密钥
     *
     * <p>用于签名和验证 Token，建议 256 位以上
     */
    private String secret = "default-secret-change-me";
    
    /**
     * AccessToken 过期时间（毫秒）
     *
     * <p>默认 2 小时
     */
    private long accessTokenExpire = 7200000L;
    
    /**
     * RefreshToken 过期时间（毫秒）
     *
     * <p>默认 7 天
     */
    private long refreshTokenExpire = 604800000L;
}
```

**为什么这样做**：
- `@Data`：配置类需要 getter/setter
- `@ConfigurationProperties`：Spring Boot 配置绑定

---

## Lombok 配置

项目根目录的 `lombok.config` 文件统一配置 Lombok 行为：

```properties
# Lombok 配置文件
# 详见：https://projectlombok.org/features/configuration

# 为所有 @Builder 生成 toBuilder() 方法
lombok.builder.toBuilder = true

# 为所有 @EqualsAndHashCode 和 @Data 类生成 canEqual() 方法
lombok.equalsAndHashCode.callSuper = call

# 为所有 Lombok 生成的方法添加 @lombok.Generated 注解
lombok.addLombokGeneratedAnnotation = true

# 日志框架配置（统一使用 Slf4j）
lombok.log.fieldName = log
lombok.log.fieldIsStatic = true

# @Accessors 配置（链式调用）
lombok.accessors.chain = false
lombok.accessors.fluent = false

# 禁用某些不推荐使用的 Lombok 功能
lombok.sneakyThrows.flagUsage = error
lombok.synchronized.flagUsage = warning
lombok.cleanup.flagUsage = warning
lombok.val.flagUsage = warning
lombok.var.flagUsage = warning
```

---

## IDE 插件安装

### IntelliJ IDEA

1. **安装插件**：
   ```
   Settings → Plugins → Marketplace → 搜索 "Lombok" → Install
   ```

2. **启用注解处理**：
   ```
   Settings → Build, Execution, Deployment → Compiler → Annotation Processors
   → 勾选 "Enable annotation processing"
   ```

3. **验证安装**：
   - 打开任意使用 Lombok 的类
   - IDE 应识别生成的方法（如 getter/setter）
   - 无红色波浪线错误提示

### Eclipse

1. **安装插件**：
   ```
   Help → Eclipse Marketplace → 搜索 "Lombok" → Install
   ```

2. **或下载 JAR 安装**：
   - 下载 `lombok.jar`：https://projectlombok.org/download
   - 双击 `lombok.jar` 运行安装程序
   - 选择 Eclipse 安装目录
   - 重启 Eclipse

### VS Code

1. **安装插件**：
   ```
   Extensions → 搜索 "Lombok Annotations Support" → Install
   ```

2. **配置 Language Support for Java**：
   - 安装 "Language Support for Java(TM) by Red Hat"
   - Lombok 插件会自动集成

---

## 常见问题

### Q1：@Data 和 @Builder 一起使用报错？

**A**：需要显式声明 `@NoArgsConstructor` 和 `@AllArgsConstructor`：

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDTO {
    // ...
}
```

**原因**：`@Builder` 生成全参构造器，会覆盖默认无参构造器，导致 Jackson 反序列化失败。

---

### Q2：继承类使用 @Data 报错？

**A**：需要设置 `@EqualsAndHashCode(callSuper = true)`：

```java
@Data
@EqualsAndHashCode(callSuper = true)
public class UserPO extends BasePO {
    // ...
}
```

**原因**：默认 `callSuper = false`，不包含父类字段，可能导致 equals/hashCode 不正确。

---

### Q3：IDE 不识别 Lombok 生成的方法？

**A**：
1. 确认安装了 Lombok 插件
2. 启用注解处理（IntelliJ IDEA）
3. 重启 IDE
4. 执行 `mvnd clean compile` 重新编译

---

### Q4：@Slf4j 报错 "log cannot be resolved"？

**A**：
1. 确认项目依赖了 SLF4J：
   ```xml
   <dependency>
       <groupId>org.slf4j</groupId>
       <artifactId>slf4j-api</artifactId>
   </dependency>
   ```
2. 确认 Lombok 插件已安装并启用

---

### Q5：@RequiredArgsConstructor 不生成构造器？

**A**：确保字段声明为 `final`：

```java
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;  // 必须是 final
    private final TokenService tokenService;      // 必须是 final
}
```

---

### Q6：@Builder 生成的类无法序列化？

**A**：添加 `@NoArgsConstructor` 和 `@AllArgsConstructor`：

```java
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDTO implements Serializable {
    private static final long serialVersionUID = 1L;
    // ...
}
```

---

### Q7：JPA Entity 使用 Lombok 注意事项？

**A**：
- 不要使用 `@Data`（`equals`/`hashCode` 可能导致问题）
- 使用 `@Getter` + `@Setter`
- `@EqualsAndHashCode` 仅包含 `id` 字段：
  ```java
  @Getter
  @Setter
  @Entity
  @EqualsAndHashCode(of = "id")
  public class UserEntity {
      @Id
      private Long id;
  }
  ```

---

## 注意事项

### ⚠️ 禁止使用的注解

以下注解已在 `lombok.config` 中禁用，不要使用：

- `@SneakyThrows`：隐藏检查异常，违反异常处理最佳实践
- `@Synchronized`：生成的同步代码不够灵活，推荐手动使用 `synchronized`
- `@Cleanup`：可能导致资源泄露，使用 try-with-resources 替代
- `@val` / `@var`：类型推断降低代码可读性

### 🔒 敏感字段处理

敏感字段（密码、Token、身份证号等）必须在 `@ToString` 中排除：

```java
@Data
@ToString(exclude = {"password", "accessToken", "idCard"})
public class User {
    private Long id;
    private String username;
    private String password;     // 敏感
    private String accessToken;  // 敏感
    private String idCard;       // 敏感
}
```

### 🎯 性能考虑

- Lombok 是编译时注解处理，**零运行时开销**
- 生成的代码与手写代码性能完全一致
- 不会增加 JAR 包大小（`scope=provided`）

### 📦 版本兼容

- Lombok 1.18.36（项目当前版本）
- 支持 Java 8 - Java 21
- 兼容 Spring Boot 3.x / Spring 6.x

---

## 检查清单

在提交代码前，请确保：

- [ ] 所有 DTO/VO/Request/Response 使用 `@Data` + `@Builder` + 构造器
- [ ] 所有 Service/Component 使用 `@Slf4j` + `@RequiredArgsConstructor`
- [ ] 所有 Domain Entity 使用 `@Getter` + `@Builder`（不使用 `@Data`）
- [ ] 继承类使用 `@EqualsAndHashCode(callSuper = true)`
- [ ] 敏感字段在 `@ToString` 中排除
- [ ] 移除所有手写的 getter/setter/constructor/toString/equals/hashCode
- [ ] IDE 安装并启用 Lombok 插件
- [ ] 执行 `mvnd clean compile -DskipTests` 编译通过

---

## 完整示例参考

- **PO 类**：[UserPO.java](../../apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/persistence/po/UserPO.java)
- **DTO 类**：[LoginRequest.java](../../apex-application/src/main/java/com/apex/scaffold/application/dto/LoginRequest.java)
- **Service 类**：[AuthService.java](../../apex-application/src/main/java/com/apex/scaffold/application/service/AuthService.java)
- **Repository 实现**：[UserRepositoryImpl.java](../../apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/persistence/repository/UserRepositoryImpl.java)

---

**最后更新**：2026-09-02  
**维护者**：Apex Team

如有疑问或建议，请联系团队负责人或在项目 Issue 中提出。
