# Phase 2 修复示例文档

本文档总结了 Phase 2A（示例驱动修复）中完成的所有代表性文件修复，为团队提供可参考的标准模板。

---

## 📊 修复概览

| 类型 | 修复数量 | 主要改进 | 状态 |
|------|---------|---------|------|
| **DTO 类** | 5 个 | 添加 @Builder + 构造器 + 完善 JavaDoc | ✅ 完成 |
| **PO 类** | 3 个 | 添加构造器注解 + 完善 JavaDoc | ✅ 完成 |
| **Mapper 接口** | 2 个 | 添加详细 JavaDoc | ✅ 完成 |
| **Service 类** | 2 个 | 已符合规范（参考） | ✅ 完成 |
| **编译验证** | 全部 | mvnd clean compile | ✅ 通过 |

---

## 1. DTO 类修复示例

### 1.1 LoginRequest.java ⭐ 推荐参考

**位置**: `apex-application/src/main/java/com/apex/scaffold/application/dto/LoginRequest.java`

**修复内容**:
- ✅ 添加 `@Builder` 注解
- ✅ 添加 `@NoArgsConstructor` 和 `@AllArgsConstructor`
- ✅ 完善类级 JavaDoc（5 个主要章节）
- ✅ 完善字段级 JavaDoc（每个字段都有详细说明）

**JavaDoc 结构**:
```java
/**
 * 登录请求 DTO
 *
 * <p>统一的登录请求模型，支持多种登录方式...
 *
 * <h2>支持的登录方式</h2>
 * <ul>
 *   <li>密码登录（手机号 + 密码）</li>
 *   <li>验证码登录（手机号 + 短信验证码）</li>
 *   <li>微信登录（临时授权码 code）</li>
 * </ul>
 *
 * <h2>字段说明</h2>
 * ...
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * // 密码登录
 * LoginRequest request = LoginRequest.builder()
 *     .phone("13800138000")
 *     .password("SecurePass123")
 *     .loginType(LoginType.PASSWORD)
 *     .build();
 * }</pre>
 *
 * <h2>验证规则</h2>
 * ...
 *
 * <h2>安全说明</h2>
 * ...
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
    
    /**
     * 手机号
     *
     * <p>用于密码登录和验证码登录。
     * 格式：中国大陆 11 位手机号。
     * 示例：{@code "13800138000"}
     */
    @Schema(description = "手机号", example = "13800138000")
    private String phone;
    
    // ... 其他字段
}
```

**关键改进点**:
1. 类级 JavaDoc 增加了 5 个章节（从 1 段增加到 5 章节）
2. 每个字段都有详细的三段式说明
3. 使用 HTML 标签（`<h2>`, `<ul>`, `<pre>`, `{@code}`）增强可读性
4. 添加了实际的使用示例代码

---

### 1.2 AccountDeletionAcceptedResponse.java

**位置**: `apex-application/src/main/java/com/apex/scaffold/application/dto/AccountDeletionAcceptedResponse.java`

**修复内容**:
- ✅ 添加 `import lombok.Builder;`（修复编译错误）
- ✅ 添加 `@Builder` 注解
- ✅ 添加构造器注解
- ✅ 完善 JavaDoc

**要点**: 这个文件展示了如何修复**缺少 import 导致的编译错误**。

---

### 1.3 RoleRequest.java

**位置**: `apex-application/src/main/java/com/apex/scaffold/application/dto/RoleRequest.java`

**修复内容**:
- ✅ 添加 `@Builder` 注解
- ✅ 添加构造器注解
- ✅ 完善 JavaDoc（包含权限管理说明）

---

### 1.4 SendSmsRequest.java

**位置**: `apex-application/src/main/java/com/apex/scaffold/application/dto/SendSmsRequest.java`

**修复内容**:
- ✅ 添加 `@Builder` 注解
- ✅ 添加构造器注解
- ✅ 完善 JavaDoc（包含短信类型说明）

---

### 1.5 CreditResponse.java

**位置**: `apex-application/src/main/java/com/apex/scaffold/application/dto/CreditResponse.java`

**修复内容**:
- ✅ 添加 `@Builder` 注解
- ✅ 添加构造器注解
- ✅ 完善 JavaDoc（包含积分系统说明）

---

## 2. PO 类修复示例

### 2.1 UserPO.java ⭐ 推荐参考

**位置**: `apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/persistence/po/UserPO.java`

**修复内容**:
- ✅ 添加 `@Builder` 注解
- ✅ 添加 `@NoArgsConstructor` 和 `@AllArgsConstructor`
- ✅ 完善类级 JavaDoc（6 个主要章节）
- ✅ 完善字段级 JavaDoc

**JavaDoc 结构**:
```java
/**
 * 用户持久化对象
 *
 * <p>对应数据库表 {@code sys_user}，存储用户的基础信息和认证凭证。
 * 继承 MyBatis-Plus {@link BasePO}，自动管理审计字段（创建时间、更新时间等）。
 *
 * <h2>表结构</h2>
 * <p>表名：{@code sys_user}<br>
 * 主键：{@code id}（Long 类型，雪花算法）<br>
 * 索引：
 * <ul>
 *   <li>{@code uk_phone}（唯一索引）</li>
 *   <li>{@code idx_openid}（普通索引）</li>
 * </ul>
 *
 * <h2>字段说明</h2>
 * ...
 *
 * <h2>关联关系</h2>
 * ...
 *
 * <h2>使用示例</h2>
 * ...
 *
 * <h2>注意事项</h2>
 * ...
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.0.0
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

**关键改进点**:
1. 明确了数据库表名和表结构
2. 列出了所有索引
3. 说明了继承关系和审计字段
4. 添加了使用示例
5. 强调了注意事项（密码安全、唯一性约束）

---

### 2.2 RolePO.java

**位置**: `apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/persistence/po/RolePO.java`

**修复内容**:
- ✅ 添加 `@Builder` 注解
- ✅ 添加构造器注解
- ✅ 完善 JavaDoc（包含 RBAC 模型说明）

---

### 2.3 PermissionPO.java

**位置**: `apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/persistence/po/PermissionPO.java`

**修复内容**:
- ✅ 添加 `@Builder` 注解
- ✅ 添加构造器注解
- ✅ 完善 JavaDoc（包含权限树结构说明）

---

## 3. Mapper 接口修复示例

### 3.1 RoleMapper.java ⭐ 推荐参考

**位置**: `apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/persistence/mapper/RoleMapper.java`

**修复内容**:
- ✅ 完善类级 JavaDoc（7 个主要章节）
- ✅ 添加通用方法说明
- ✅ 添加使用示例
- ✅ 添加自定义 SQL 示例

**JavaDoc 结构**:
```java
/**
 * 角色 Mapper 接口
 *
 * <p>负责 {@code sys_role} 表的持久化操作...
 *
 * <h2>表映射</h2>
 * <p>表名：{@code sys_role}<br>
 * 实体：{@link RolePO}<br>
 * 主键：{@code id}（Long 类型）
 *
 * <h2>通用方法</h2>
 * <p>继承自 {@link BaseMapper} 的常用方法：
 * <ul>
 *   <li>{@code insert(entity)} - 插入一条记录</li>
 *   <li>{@code deleteById(id)} - 根据 ID 删除</li>
 *   ...
 * </ul>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * // 插入角色
 * RolePO role = RolePO.builder()
 *     .name("内容管理员")
 *     .code("ROLE_CONTENT_ADMIN")
 *     .status(1)
 *     .build();
 * roleMapper.insert(role);
 * }</pre>
 *
 * <h2>自定义 SQL</h2>
 * ...
 *
 * <h2>注意事项</h2>
 * ...
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.0.0
 */
@Mapper
public interface RoleMapper extends BaseMapper<RolePO> {}
```

**关键改进点**:
1. 详细列出了 BaseMapper 提供的通用方法
2. 提供了完整的使用示例（CRUD 操作）
3. 说明了如何编写自定义 SQL
4. 强调了注意事项（事务、唯一索引、级联删除）

---

### 3.2 DeptMapper.java

**位置**: `apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/persistence/mapper/DeptMapper.java`

**修复内容**:
- ✅ 完善类级 JavaDoc（包含树形结构说明）
- ✅ 添加树形查询示例
- ✅ 添加数据权限查询示例

**特色**: 详细说明了树形结构的处理方法和 `ancestors` 字段的使用。

---

## 4. Service 类参考示例

### 4.1 UserService.java ⭐ 已符合规范

**位置**: `apex-application/src/main/java/com/apex/scaffold/application/service/UserService.java`

**现状**: 已有完善的 Lombok 注解和 JavaDoc，可直接作为参考。

**优点**:
- ✅ 使用 `@Slf4j` 和 `@RequiredArgsConstructor`
- ✅ 详细的类级 JavaDoc（6 个章节）
- ✅ 完善的方法级 JavaDoc
- ✅ 清晰的职责划分说明

---

### 4.2 RoleService.java ⭐ 已符合规范

**位置**: `apex-application/src/main/java/com/apex/scaffold/application/service/RoleService.java`

**现状**: 已有完善的 Lombok 注解和 JavaDoc，可直接作为参考。

**特色**: 详细说明了线程模型和阻塞 I/O 处理。

---

## 5. Converter 接口参考示例

### 5.1 UserPoConverter.java ⭐ 已符合规范

**位置**: `apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/persistence/converter/UserPoConverter.java`

**现状**: 已有完善的 JavaDoc，可直接作为参考。

**优点**:
- ✅ 详细的 MapStruct 配置说明
- ✅ 完整的使用示例
- ✅ 列出了所有映射方法

---

## 6. 修复模式总结

### 6.1 DTO 类标准模式

```java
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * [类名简述]
 *
 * <p>[详细说明]
 *
 * <h2>[功能章节]</h2>
 * <ul>
 *   <li>功能点1</li>
 *   <li>功能点2</li>
 * </ul>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * // 示例代码
 * }</pre>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class XxxDTO {
    
    /**
     * [字段简述]
     *
     * <p>[详细说明]
     * [格式/示例]
     */
    private String field;
}
```

---

### 6.2 PO 类标准模式

```java
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * [实体名] 持久化对象
 *
 * <p>对应数据库表 {@code table_name}...
 *
 * <h2>表结构</h2>
 * <p>表名：{@code table_name}<br>
 * 主键：{@code id}<br>
 * 索引：...
 *
 * <h2>字段说明</h2>
 * ...
 *
 * <h2>使用示例</h2>
 * ...
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("table_name")
public class XxxPO extends BasePO {
    // ...
}
```

---

### 6.3 Mapper 接口标准模式

```java
/**
 * [实体名] Mapper 接口
 *
 * <p>负责 {@code table_name} 表的持久化操作...
 *
 * <h2>表映射</h2>
 * <p>表名：{@code table_name}<br>
 * 实体：{@link XxxPO}
 *
 * <h2>通用方法</h2>
 * <ul>
 *   <li>{@code insert(entity)}</li>
 *   <li>{@code selectById(id)}</li>
 *   ...
 * </ul>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * // 示例代码
 * }</pre>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Mapper
public interface XxxMapper extends BaseMapper<XxxPO> {}
```

---

### 6.4 Service 类标准模式

```java
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * [服务名]
 *
 * <p>[服务职责说明]
 *
 * <h2>主要功能</h2>
 * <ul>
 *   <li>功能1</li>
 *   <li>功能2</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class XxxService {
    
    private final XxxRepository repository;
    
    /**
     * [方法简述]
     *
     * <p>[详细说明]
     *
     * @param param 参数说明
     * @return 返回值说明
     */
    public Mono<Result> method(String param) {
        // ...
    }
}
```

---

## 7. 批量修复工具

### 7.1 批量添加 Lombok 注解

**脚本**: `scripts/batch-add-lombok.sh`

**功能**:
- 自动为 DTO 类添加 `@Builder` + 构造器注解
- 自动为 PO 类添加构造器注解
- 自动添加必要的 import 语句
- 自动备份原文件（.bak）

**使用方法**:
```bash
cd /Users/work/apex-scaffold
./scripts/batch-add-lombok.sh
```

---

### 7.2 批量添加 JavaDoc 框架

**脚本**: `scripts/batch-add-javadoc.sh`

**功能**:
- 自动为缺少 JavaDoc 的类添加基础框架
- 包含 @author、@version、@since 标签
- 自动备份原文件（.bak）

**使用方法**:
```bash
cd /Users/work/apex-scaffold
./scripts/batch-add-javadoc.sh
```

**⚠️ 重要**: 脚本只添加基础框架，具体内容需要手工完善！

---

## 8. 验证方法

### 8.1 编译验证

```bash
cd /Users/work/apex-scaffold
mvnd clean compile -DskipTests
```

**预期结果**: `BUILD SUCCESS`

---

### 8.2 JavaDoc 生成验证

```bash
mvnd javadoc:javadoc -DskipTests
```

**检查**: `target/site/apidocs` 目录下的 HTML 文件

---

### 8.3 Lombok 警告检查

编译时注意 WARNING 信息：
- `Use of @NoArgsConstructor is flagged` - 这是 lombok.config 的提醒，不影响功能
- `@Builder will ignore the initializing expression` - 需要添加 `@Builder.Default`

---

## 9. 常见问题与解决方案

### 9.1 编译错误：cannot find symbol: class Builder

**原因**: 缺少 `import lombok.Builder;`

**解决**: 在 import 区域添加：
```java
import lombok.Builder;
```

---

### 9.2 Lombok 注解不生效

**原因**: IDE 插件未安装或未启用

**解决**:
1. IDEA: Settings → Plugins → 安装 Lombok
2. 启用 Annotation Processing: Settings → Build → Compiler → Annotation Processors → ✅ Enable

---

### 9.3 JavaDoc 生成警告

**原因**: 使用了 HTML 标签但未闭合

**解决**: 
- 使用 `{@code}` 包裹代码片段
- 使用 `<p>` 标签分段
- 使用 `<ul>` 和 `<li>` 创建列表

---

## 10. 下一步行动

### 10.1 立即可做

1. **查看示例文件**（30 分钟）
   - 阅读本文档中的 5 个 ⭐ 推荐文件
   - 理解 JavaDoc 的结构和风格

2. **试用批量工具**（30 分钟）
   - 在测试分支运行脚本
   - 检查生成结果
   - 手工完善 1-2 个文件

3. **阅读指南文档**（1 小时）
   - `docs/JAVADOC_GUIDE.md`
   - `docs/LOMBOK_GUIDE.md`

---

### 10.2 团队协作

**分工建议**（4-5 天完成）:

| 成员 | 负责模块 | 预计工时 | 优先级 |
|------|---------|---------|--------|
| A | apex-infrastructure（PO/Mapper） | 12-15h | 🔴 最高 |
| B | apex-application（DTO/Service） | 10-12h | 🔴 最高 |
| C | apex-domain + apex-common | 8-10h | 🟡 中等 |
| D | apex-interfaces + apex-example | 3-5h | 🟢 较低 |

---

### 10.3 质量检查

**每完成一个模块**:
1. ✅ 运行编译：`mvnd clean compile -DskipTests`
2. ✅ 检查警告：review WARNING 信息
3. ✅ 生成 JavaDoc：`mvnd javadoc:javadoc`
4. ✅ Code Review：2 人互审

**全部完成后**:
1. ✅ 全量编译：`mvnd clean compile -DskipTests`
2. ✅ 运行测试：`mvnd clean test -P basic`
3. ✅ 生成聚合 JavaDoc：`mvnd javadoc:aggregate -DskipTests`
4. ✅ 重新扫描：运行 `scripts/scan-lombok-issues.sh` 和 `scan-javadoc-issues.sh`

---

## 11. 成功标准

### 11.1 量化指标

- ✅ Lombok 问题文件 < 10 个（从 310 减少 97%）
- ✅ JavaDoc 问题文件 < 50 个（从 675 减少 93%）
- ✅ 编译通过率 = 100%
- ✅ JavaDoc 生成无错误

---

### 11.2 质量标准

- ✅ 所有 DTO 都有 `@Builder` 和构造器
- ✅ 所有 PO 都有构造器注解
- ✅ 所有 Service 都使用 `@Slf4j` + `@RequiredArgsConstructor`
- ✅ 所有 public 类都有完整的类级 JavaDoc
- ✅ 所有 public 方法都有方法级 JavaDoc

---

### 11.3 验收标准

- ✅ 新成员能通过 JavaDoc 快速理解代码
- ✅ IDE 能正确提示 Builder 模式
- ✅ 生成的 JavaDoc 网站结构清晰、内容完整
- ✅ 代码通过 SonarQube 质量扫描（如有）

---

## 12. 参考资源

### 12.1 内部文档

- `docs/JAVADOC_GUIDE.md` - JavaDoc 编写完整指南（59 页）
- `docs/LOMBOK_GUIDE.md` - Lombok 使用完整指南（45 页）
- `docs/EXAMPLES_SUMMARY.md` - 示例代码汇总
- `AGENT.md` - 项目开发规范

---

### 12.2 扫描报告

- `.trae/plans/SCAN_RESULTS_REPORT.md` - 详细的扫描结果分析
- `.trae/plans/EXECUTION_SUMMARY.md` - 执行方案建议
- `lombok-issues-*.txt` - 各类 Lombok 问题清单
- `javadoc-issues-*.txt` - 各类 JavaDoc 问题清单

---

### 12.3 外部参考

- [Oracle JavaDoc 规范](https://www.oracle.com/technical-resources/articles/java/javadoc-tool.html)
- [Lombok 官方文档](https://projectlombok.org/features/)
- [MyBatis-Plus 文档](https://baomidou.com/)
- [MapStruct 文档](https://mapstruct.org/)

---

## 附录：修复文件清单

### A. 已修复的 DTO 文件（5 个）

1. ✅ `LoginRequest.java` - 登录请求（⭐ 推荐）
2. ✅ `AccountDeletionAcceptedResponse.java` - 账号注销响应
3. ✅ `RoleRequest.java` - 角色请求
4. ✅ `SendSmsRequest.java` - 短信发送请求
5. ✅ `CreditResponse.java` - 积分响应

### B. 已修复的 PO 文件（3 个）

1. ✅ `UserPO.java` - 用户持久化对象（⭐ 推荐）
2. ✅ `RolePO.java` - 角色持久化对象
3. ✅ `PermissionPO.java` - 权限持久化对象

### C. 已修复的 Mapper 文件（2 个）

1. ✅ `RoleMapper.java` - 角色 Mapper（⭐ 推荐）
2. ✅ `DeptMapper.java` - 部门 Mapper

### D. 参考 Service 文件（2 个）

1. ✅ `UserService.java` - 用户服务（⭐ 推荐）
2. ✅ `RoleService.java` - 角色服务

### E. 参考 Converter 文件（1 个）

1. ✅ `UserPoConverter.java` - 用户转换器（⭐ 推荐）

---

## 结语

本次 Phase 2A 示例驱动修复工作已完成，为团队提供了：

✅ **10 个高质量示例文件**（修复 + 参考）  
✅ **2 个批量修复脚本**（Lombok + JavaDoc）  
✅ **4 套标准模式模板**（DTO/PO/Mapper/Service）  
✅ **完整的验证和质量标准**  

团队现在可以基于这些示例和工具，高效完成剩余 **975 个问题文件**的改造工作。

预计 **3-4 人并行工作 4-5 天**即可完成全部改造，让项目代码质量达到企业级标准！

---

**文档版本**: v1.0.0  
**创建日期**: 2026-09-02  
**作者**: Apex Team  
**最后更新**: 2026-09-02 23:10
