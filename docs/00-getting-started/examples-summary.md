# JavaDoc 和 Lombok 示例代码汇总

**Apex Scaffold 项目规范示例参考**

本文档汇总了项目中已完成的 JavaDoc 和 Lombok 改造示例，供团队成员参考和学习。

---

## 📋 目录

1. [改造示例清单](#改造示例清单)
2. [基础设施层示例](#基础设施层示例)
3. [应用层示例](#应用层示例)
4. [关键改进点总结](#关键改进点总结)
5. [如何使用本文档](#如何使用本文档)

---

<a id="改造示例清单"></a>

## 改造示例清单

### ✅ 已完成改造的示例文件

| 类型 | 文件路径 | 改进内容 | 状态 |
|------|---------|---------|------|
| **PO 类** | `apex-system/.../infrastructure/persistence/po/UserPO.java` | 完整 JavaDoc + `@Builder` + 构造器 | ✅ 完成 |
| **Mapper 接口** | `apex-system/.../infrastructure/persistence/mapper/UserMapper.java` | 详细方法注释 + SQL 语义说明 | ✅ 完成 |
| **Converter 接口** | `apex-system/.../infrastructure/persistence/converter/UserPoConverter.java` | MapStruct 注释 + 使用示例 | ✅ 完成 |
| **RepositoryImpl 类** | `apex-system/.../infrastructure/persistence/repository/UserRepositoryImpl.java` | 已有良好注释（参考） | ✅ 参考 |
| **DTO 类** | `apex-system/.../application/dto/LoginRequest.java`（auth 侧另有同名类） | `@Builder` + 详细字段注释 | ✅ 完成 |
| **Service 类** | `apex-system/.../application/service/AuthService.java` | 已有良好注释（参考） | ✅ 参考 |

### 📚 配套文档

| 文档 | 路径 | 说明 |
|------|------|------|
| **开发规范（含 JavaDoc/Lombok）** | `docs/04-development/development-standards.md` | 注释与 JavaDoc 规范见 §4，Lombok 见 §3.2；原 JAVADOC_GUIDE/LOMBOK_GUIDE 已并入本文档 |

---

<a id="基础设施层示例"></a>

## 基础设施层示例

### 1. UserPO.java（持久化对象）

**文件路径**：`apex-components-parent/apex-system/src/main/java/com/apex/scaffold/infrastructure/persistence/po/UserPO.java`（原 `apex-interfaces/apex-infrastructure` 单体已拆分：`apex-infrastructure` 现仅为旧坐标兼容空聚合，持久化实现分布在各业务库的 `infrastructure/persistence` 包）

**关键改进**：

#### ✨ 类级别 JavaDoc（改进前 → 改进后）

**改进前**：
```java
/**
 * 用户持久化对象。
 *
 * <p>对应数据库中的sys_user表，存储用户的基本信息、微信认证信息和登录信息。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
public class UserPO extends BasePO {
    public UserPO() {}
    // ...
}
```

**改进后**：
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
 *   <li>状态信息：启用状态、注册引导状态、最后登录时间/IP、心跳时间</li>
 * </ul>
 *
 * <h2>表结构</h2>
 * <p>表名：{@code sys_user}<br>
 * 主键：{@code id}（继承自 {@link BasePO}）<br>
 * 唯一索引：{@code uk_open_id}（微信OpenID）、{@code uk_username}（登录账号）<br>
 * 普通索引：{@code idx_phone}（手机号）、{@code idx_dept_id}（部门ID）、{@code idx_tenant_id}（租户ID）
 *
 * <h2>使用场景</h2>
 * <ul>
 *   <li>微信 APP 登录：通过 OpenID/UnionID 查询或创建用户</li>
 *   <li>账号密码登录：通过 username 查询用户并验证密码</li>
 *   <li>手机号登录：通过 phone 查询用户</li>
 *   <li>数据权限控制：通过 deptId 和 tenantId 实现部门级、租户级数据隔离</li>
 * </ul>
 *
 * <h2>注意事项</h2>
 * <ul>
 *   <li>密码字段必须存储 BCrypt 哈希值，禁止存储明文</li>
 *   <li>OpenID 和 UnionID 来自微信授权，不可手动修改</li>
 *   <li>AccessToken 有效期 2 小时，过期后需使用 RefreshToken 刷新</li>
 *   <li>多租户场景下 tenantId 必填，单租户场景可为 NULL</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.3.1
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

#### ✨ Lombok 注解增强

**新增注解**：
- `@Builder`：支持流式构建
- `@NoArgsConstructor`：显式无参构造器（ORM 需要）
- `@AllArgsConstructor`：显式全参构造器（Builder 需要）

**移除内容**：
- 删除手写的 `public UserPO() {}` 构造器（由 `@NoArgsConstructor` 生成）

#### ✨ 字段级别 JavaDoc 改进

**改进前**：
```java
@TableField("tenant_id")
private String tenantId;
```

**改进后**：
```java
/**
 * 租户ID（多租户隔离）
 *
 * <p>对应 {@code sys_user.tenant_id} 列（V14+ 多租户迁移新增），
 * 实现租户级数据隔离。配合 {@code @TenantAware} 注解和 {@code TenantContext} 
 * 自动填充和过滤。多租户场景必填，单租户场景可为 {@code null}。
 *
 * @since 2.5.0
 */
@TableField("tenant_id")
private String tenantId;
```

---

### 2. UserMapper.java（Mapper 接口）

**文件路径**：`apex-components-parent/apex-system/src/main/java/com/apex/scaffold/infrastructure/persistence/mapper/UserMapper.java`

**关键改进**：

#### ✨ 类级别 JavaDoc（改进前 → 改进后）

**改进前**：
```java
/**
 * 用户数据 MyBatis Mapper 接口。
 *
 * <p>负责 {@code sys_user} 表的持久化操作，提供用户记录查询与行级锁读取能力。
 * 继承 MyBatis-Plus {@link BaseMapper} 获得通用单表 CRUD 操作。</p>
 *
 * <h2>核心能力</h2>
 * <ul>
 *   <li>按主键锁定用户记录（{@code SELECT ... FOR UPDATE}），供并发更新场景使用</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
```

**改进后**：
```java
/**
 * 用户数据 MyBatis Mapper 接口
 *
 * <p>负责 {@code sys_user} 表的持久化操作，提供用户记录的 CRUD 和特殊查询能力。
 * 继承 MyBatis-Plus {@link BaseMapper} 获得通用单表 CRUD 操作（insert、update、delete、selectById 等）。
 *
 * <h2>核心功能</h2>
 * <ul>
 *   <li>通用 CRUD：继承自 {@link BaseMapper}，支持单表增删改查</li>
 *   <li>行级锁查询：{@link #selectByIdForUpdate} 提供 {@code SELECT ... FOR UPDATE} 悲观锁支持</li>
 *   <li>账号封禁：{@link #banForPhotoReports} 因举报照片审核失败而封禁账号</li>
 *   <li>账号注销：{@link #deactivateForAccountDeletion} 清空用户敏感数据并停用账号</li>
 * </ul>
 *
 * <h2>使用场景</h2>
 * <ul>
 *   <li>并发更新：使用 {@code selectByIdForUpdate} 锁定记录后更新，防止并发冲突</li>
 *   <li>内容审核：照片举报审核通过后调用 {@code banForPhotoReports} 封禁违规用户</li>
 *   <li>GDPR 合规：用户申请注销时调用 {@code deactivateForAccountDeletion} 清除个人信息</li>
 * </ul>
 *
 * <h2>注意事项</h2>
 * <ul>
 *   <li>行级锁查询必须在事务中使用，否则锁立即释放</li>
 *   <li>封禁和注销操作会更新 {@code version} 字段（乐观锁）</li>
 *   <li>注销操作不可逆，会清空 OpenID/UnionID 等身份字段</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.0.0
 * @see UserPO
 * @see BaseMapper
 */
```

#### ✨ 方法级别 JavaDoc 改进

**改进前**：
```java
/** Disable an account as the result of a reported-photo review. */
@Update("UPDATE sys_user SET status=0,update_time=NOW(),version=version+1 "
        + "WHERE id=#{userId} AND deleted=0 AND status=1")
int banForPhotoReports(@Param("userId") Long userId);
```

**改进后**：
```java
/**
 * 因举报照片审核失败而封禁账号
 *
 * <p>将用户状态设置为禁用（{@code status=0}），并更新版本号。
 * 仅封禁当前正常状态的账号（{@code status=1}），已封禁账号忽略。
 *
 * <p><strong>SQL 语义</strong>：
 * <pre>{@code
 * UPDATE sys_user 
 * SET status=0, update_time=NOW(), version=version+1 
 * WHERE id=#{userId} AND deleted=0 AND status=1
 * }</pre>
 *
 * @param userId 用户ID，必须存在且未删除
 * @return 更新行数，成功返回 1，用户不存在或已封禁返回 0
 */
@Update("UPDATE sys_user SET status=0,update_time=NOW(),version=version+1 "
        + "WHERE id=#{userId} AND deleted=0 AND status=1")
int banForPhotoReports(@Param("userId") Long userId);
```

---

### 3. UserPoConverter.java（Converter 接口）

**文件路径**：`apex-components-parent/apex-system/src/main/java/com/apex/scaffold/infrastructure/persistence/converter/UserPoConverter.java`

**关键改进**：

#### ✨ 类级别 JavaDoc（改进后）

```java
/**
 * 用户实体与持久化对象转换器（MapStruct）
 *
 * <p>负责 {@link UserEntity}（领域层实体）与 {@link UserPO}（基础设施层持久化对象）
 * 之间的双向转换，使用 MapStruct 自动生成转换代码，确保类型安全和高性能。
 *
 * <h2>转换规则</h2>
 * <ul>
 *   <li>字段映射：同名字段自动映射（如 {@code id}、{@code openId}、{@code nickname} 等）</li>
 *   <li>忽略策略：{@code unmappedTargetPolicy = IGNORE} 忽略目标对象中未匹配的字段</li>
 *   <li>Spring 集成：{@code componentModel = "spring"} 生成 Spring Bean，支持依赖注入</li>
 *   <li>空值处理：{@code null} 输入返回 {@code null}，不抛出异常</li>
 * </ul>
 *
 * <h2>使用场景</h2>
 * <ul>
 *   <li>Repository 实现：{@code UserRepositoryImpl} 查询数据库后将 {@code UserPO} 转换为 {@code UserEntity}</li>
 *   <li>数据持久化：将内存中的 {@code UserEntity} 转换为 {@code UserPO} 后保存到数据库</li>
 *   <li>批量操作：{@code toPoList} 和 {@code toEntityList} 支持批量转换，减少循环代码</li>
 * </ul>
 *
 * <h2>MapStruct 编译时代码生成</h2>
 * <p>编译时 MapStruct 会自动生成 {@code UserPoConverterImpl} 实现类，包含具体的字段复制逻辑。
 * 生成的代码位于 {@code target/generated-sources/annotations/} 目录。
 *
 * <h2>注意事项</h2>
 * <ul>
 *   <li>不支持自定义映射：如需特殊转换逻辑（如日期格式、枚举转换），使用 {@code @Mapping} 注解</li>
 *   <li>性能优化：MapStruct 生成纯 Java 代码，性能接近手写，优于反射方式（如 BeanUtils）</li>
 *   <li>循环引用：避免实体间循环引用，否则可能导致 StackOverflowError</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.0.0
 * @see UserEntity
 * @see UserPO
 * @see Mapper
 */
```

#### ✨ 方法级别 JavaDoc（含使用示例）

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

---

<a id="应用层示例"></a>

## 应用层示例

### 4. LoginRequest.java（DTO 类）

**文件路径**：`apex-components-parent/apex-system/src/main/java/com/apex/scaffold/application/dto/LoginRequest.java`

**关键改进**：

#### ✨ 类级别 JavaDoc（改进后）

```java
/**
 * 登录请求 DTO
 *
 * <p>微信 APP 登录请求参数封装，支持多种登录方式：
 * <ul>
 *   <li>WX_CODE：微信授权码登录（OAuth 2.0 授权流程）</li>
 *   <li>WX_PHONE：微信一键登录（微信手机号快捷授权）</li>
 *   <li>WX_SMS：微信短信验证码登录（手机号 + 验证码）</li>
 *   <li>CL_PHONE：创蓝一键登录（闪验 SDK 集成）</li>
 *   <li>CL_SMS：创蓝短信验证码登录（手机号 + 验证码）</li>
 *   <li>PASSWORD：账号密码登录（用户名 + 密码）</li>
 * </ul>
 *
 * <h2>字段验证规则</h2>
 * <ul>
 *   <li>手机号：{@code ^1[3-9]\\d{9}$} 格式（中国大陆手机号）</li>
 *   <li>验证码：6 位数字</li>
 *   <li>用户名：1-50 字符</li>
 *   <li>密码：6-64 字符</li>
 * </ul>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * // 微信授权码登录
 * LoginRequest request = LoginRequest.builder()
 *     .loginType("WX_CODE")
 *     .code("wx_auth_code_123456")
 *     .deviceId("device_uuid_123")
 *     .build();
 *
 * // 短信验证码登录
 * LoginRequest request = LoginRequest.builder()
 *     .loginType("WX_SMS")
 *     .phone("13800138000")
 *     .smsCode("123456")
 *     .build();
 * }</pre>
 *
 * <h2>注意事项</h2>
 * <ul>
 *   <li>密码字段明文传输，服务端使用 BCrypt 比对哈希值</li>
 *   <li>{@code clientIp} 由 Handler 自动填充，无需客户端传递</li>
 *   <li>{@code deviceId} 用于 Token 刷新时的设备一致性校验</li>
 *   <li>图形验证码（{@code captchaId}/{@code captchaCode}）为可选功能，按需启用</li>
 * </ul>
 *
 * @author Apex Team
 * @version 3.2.0
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

#### ✨ Lombok 注解增强

**新增注解**：
- `@Builder`：支持流式构建
- `@NoArgsConstructor`：支持 Jackson 反序列化
- `@AllArgsConstructor`：支持全参构造

**移除内容**：
- 删除手写的 `public LoginRequest() {}` 构造器

---

<a id="关键改进点总结"></a>

## 关键改进点总结

### 📊 改进对比

| 改进项 | 改进前 | 改进后 | 提升 |
|--------|--------|--------|------|
| **类级 JavaDoc** | 简短描述 | 详细说明 + 功能列表 + 使用场景 + 注意事项 | **3-5 倍** |
| **方法级 JavaDoc** | 一句话描述 | 详细说明 + 参数约束 + 返回值说明 + 异常场景 + 使用示例 | **4-6 倍** |
| **字段级 JavaDoc** | 一句话或无注释 | 业务含义 + 取值范围 + 默认值 + 是否必填 | **2-3 倍** |
| **Lombok 应用** | 部分使用 | 完整使用 + 显式构造器 | **完整** |
| **HTML 标签** | 少量或无 | `<h2>`、`<ul>`、`<p>`、`<pre>`、`{@code}` | **可读性提升** |

### ✨ 改进亮点

1. **结构化文档**：使用 `<h2>` 标题组织内容，清晰易读
2. **列表展示**：使用 `<ul>/<li>` 列表展示功能点、场景、注意事项
3. **代码示例**：使用 `<pre>{@code}</pre>` 提供实际使用示例
4. **SQL 语义**：Mapper 方法展示完整 SQL，便于理解
5. **版本标注**：`@version` 更新为 1.3.1，`@since` 准确标注
6. **链接引用**：使用 `{@link}` 和 `@see` 关联相关类
7. **Lombok 完整**：显式声明 `@Builder`、`@NoArgsConstructor`、`@AllArgsConstructor`
8. **敏感字段**：在 `@ToString` 中排除密码、Token 等敏感字段（示例中已体现）

---

<a id="如何使用本文档"></a>

## 如何使用本文档

### 🎯 团队成员如何参考

1. **选择对应类型**：
   - 要改造 PO 类？参考 `UserPO.java`
   - 要改造 Mapper 接口？参考 `UserMapper.java`
   - 要改造 DTO 类？参考 `LoginRequest.java`
   - 要改造 Service 类？参考 `AuthService.java`

2. **复制模板结构**：
   - 复制类级 JavaDoc 结构（标题、列表、注意事项）
   - 复制方法级 JavaDoc 结构（参数、返回值、异常、示例）
   - 根据实际业务调整内容

3. **应用 Lombok 注解**：
   - DTO/VO：`@Data` + `@Builder` + `@NoArgsConstructor` + `@AllArgsConstructor`
   - PO：`@Data` + `@Builder` + `@NoArgsConstructor` + `@AllArgsConstructor` + `@EqualsAndHashCode(callSuper = true)`
   - Service：`@Slf4j` + `@RequiredArgsConstructor`
   - Domain Entity：`@Getter` + `@Builder` + 私有构造器

4. **移除样板代码**：
   - 删除手写的 getter/setter
   - 删除手写的 constructor
   - 删除手写的 toString/equals/hashCode

5. **验证改造效果**：
   ```bash
   # 编译验证
   mvnd clean compile -DskipTests
   
   # 生成 JavaDoc 验证
   mvnd javadoc:aggregate -DskipTests
   ```

### 📚 配套文档阅读顺序

1. **先读规范**：`docs/04-development/development-standards.md`（注释与 JavaDoc 见 §4，Lombok 见 §3.2）
2. **再读示例**：本文档（实际应用）
3. **参考示例**：本文档（实际应用）
4. **动手改造**：参考示例改造自己负责的模块

### 🔍 快速查找

- **JavaDoc 模板**：查看 `docs/04-development/development-standards.md` §4 的模板章节
- **Lombok 注解选择**：查看同文档 §3.2「Lombok」
- **IDE 配置**：查看同文档相应章节

---

## 下一步行动

### ✅ 已完成

- ✅ 创建 6 个代表性示例文件
- ✅ JavaDoc/Lombok 规范并入 `docs/04-development/development-standards.md`（原独立指南已下线）
- ✅ 创建示例代码汇总文档
- ✅ 更新 AGENT.md（JDK、mvnd、JavaDoc、Lombok 规范）
- ✅ 创建 lombok.config 配置文件

### 📋 团队需要做的

1. **阅读文档**：
   - 开发规范：`docs/04-development/development-standards.md`
   - 示例汇总：`docs/00-getting-started/examples-summary.md`（本文档）

2. **安装工具**：
   - 安装 SDKMAN（如未安装）
   - 激活项目环境：`cd /Users/work/apex-scaffold && sdk env`
   - 安装 IDE Lombok 插件（IntelliJ IDEA / Eclipse / VS Code）

3. **并行改造**：
   - 按照计划的 8 个 Phase 分工
   - 参考示例文件进行改造
   - 每完成一个模块就编译验证

4. **Code Review**：
   - 检查 JavaDoc 完整性（类/方法/字段）
   - 检查 Lombok 注解使用（是否符合场景）
   - 检查是否移除了手写的样板代码

5. **最终验证**：
   ```bash
   # 全量编译
   mvnd clean compile -DskipTests
   
   # 单元测试
   mvnd clean test -P basic
   
   # 代码格式
   mvnd spotless:check
   
   # 生成 JavaDoc
   mvnd javadoc:aggregate -DskipTests
   ```

---

**文档维护**：本文档应随着改造进度更新，添加新的示例和最佳实践。

**最后更新**：2026-09-02  
**维护者**：Apex Team

如有疑问或建议，请联系团队负责人或在项目 Issue 中提出。
