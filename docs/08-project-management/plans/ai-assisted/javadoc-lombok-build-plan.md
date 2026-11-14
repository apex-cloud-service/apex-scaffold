# JavaDoc 注释规范、Lombok 改造与构建工具配置计划

> **历史快照**：本文记录某一时点的状态，模块数、端口、路径与类名以当前代码为准，不作为现状依据。

**项目**: Apex Scaffold (Java 17 / Spring Boot 3 / WebFlux)  
**计划编号**: PLAN-2026-001  
**创建日期**: 2026-09-02  
**目标**: 为代码添加完整专业的 JavaDoc 注释，全面应用 Lombok 简化样板代码，明确构建工具链配置

---

## 1. 计划目标

### 1.1 核心目标
1. **JavaDoc 注释完整性**: 为所有公开类、接口、方法、字段添加符合规范的 JavaDoc 注释
2. **Lombok 全面应用**: 使用 Lombok 注解替代样板代码（getter/setter/constructor/builder 等）
3. **构建工具标准化**: 明确 SDKMAN 管理的 JDK 和 mvnd 构建工具配置，写入 AGENT.md

### 1.2 质量标准
- JavaDoc 覆盖率：公开 API 100%
- Lombok 应用率：所有 POJO/DTO/Entity/VO 类 100%
- 构建配置：AGENT.md 明确记录 JDK 17、mvnd 2.0.0-rc-3 使用要求

---

## 2. JavaDoc 注释规范

### 2.1 注释模板标准

#### 2.1.1 类级别注释
```java
/**
 * [类的简短描述（一句话）]
 *
 * <p>[类的详细说明，包括：
 * - 类的职责和作用
 * - 关键设计模式或架构考量
 * - 使用场景和限制
 * - 与其他组件的协作关系]
 *
 * <h2>主要功能</h2>
 * <ul>
 *   <li>功能点1：说明</li>
 *   <li>功能点2：说明</li>
 * </ul>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * // 代码示例
 * ExampleClass example = new ExampleClass();
 * example.doSomething();
 * }</pre>
 *
 * <h2>注意事项</h2>
 * <ul>
 *   <li>注意事项1</li>
 *   <li>注意事项2</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.0.0
 * @see RelatedClass
 */
```

#### 2.1.2 方法级别注释
```java
/**
 * [方法的简短描述（动词开头）]
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

#### 2.1.3 字段级别注释
```java
/**
 * [字段说明]
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

### 2.2 JavaDoc 注释原则

1. **完整性**
   - 所有 `public` 和 `protected` 类、接口、方法、字段必须有 JavaDoc
   - 包级别 `package-info.java` 必须提供包说明

2. **准确性**
   - 描述必须与代码实现一致
   - 参数、返回值、异常描述必须准确
   - 版本号 `@since` 必须正确标注

3. **可读性**
   - 使用中文编写业务逻辑说明
   - 技术术语可保留英文
   - 使用 HTML 标签（`<p>`, `<ul>`, `<h2>` 等）提升可读性

4. **规范性**
   - `@author Apex Team` 固定作者
   - `@version` 使用项目版本号（当前 1.2.0）
   - `@since` 标注首次出现版本
   - 使用 `{@code}`, `{@link}`, `{@see}` 等标签

---

## 3. Lombok 应用规范

### 3.1 核心注解使用

#### 3.1.1 数据类（DTO/VO/Request/Response）
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

#### 3.1.2 实体类（Domain Entity）
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
     * 修改用户状态
     *
     * <p>领域方法，封装状态变更业务规则。
     *
     * @param newStatus 新状态
     * @throws BusinessException 状态转换不合法时抛出
     */
    public void changeStatus(UserStatus newStatus) {
        // 业务逻辑
    }
}
```

#### 3.1.3 服务类
```java
/**
 * 用户应用服务
 *
 * <p>协调领域层和基础设施层，编排用户相关用例。
 *
 * @author Apex Team
 * @since 1.0.0
 */
@Service
@Slf4j
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
        // 实现
    }
}
```

### 3.2 Lombok 注解选择指南

| 场景 | 推荐注解 | 说明 |
|------|---------|------|
| DTO/VO/Request/Response | `@Data` | 生成 getter/setter/toString/equals/hashCode |
| 需要 Builder 模式 | `@Builder` | 配合 `@Data` 使用，提供流式构建 |
| 需要全参/无参构造 | `@AllArgsConstructor` + `@NoArgsConstructor` | 显式提供构造器 |
| Domain Entity | `@Getter` + `@Builder` | 只读属性，通过领域方法修改 |
| Service/Component | `@RequiredArgsConstructor` | 自动注入 `final` 字段（依赖注入） |
| 日志记录 | `@Slf4j` | 自动生成 `log` 字段 |
| 资源清理 | `@Cleanup` | 自动调用 `close()` 方法 |
| 非空检查 | `@NonNull` | 方法参数非空断言 |

### 3.3 Lombok 配置（lombok.config）

项目根目录应包含 `lombok.config` 文件：

```properties
# Lombok 配置文件
# 详见：https://projectlombok.org/features/configuration

# 为所有 @Builder 生成 toBuilder() 方法
lombok.builder.toBuilder = true

# 为所有 @Data 类生成 canEqual() 方法
lombok.data.canEqual = true

# 为所有 @Getter/@Setter 生成字段级别的 JavaDoc
lombok.addLombokGeneratedAnnotation = true

# 构造器访问级别
lombok.allArgsConstructor.access = public
lombok.noArgsConstructor.access = public

# 日志框架配置
lombok.log.fieldName = log
lombok.log.fieldIsStatic = true
```

---

## 4. 构建工具配置规范

### 4.1 SDKMAN 工具链配置

#### 4.1.1 .sdkmanrc 文件（已存在）
```bash
# ============================================================
# SDKMAN 项目级配置（sdk env 自动激活）
# ============================================================
# 由 sdkman 安装的默认版本（current 指向）：
#   java: 17.0.12-oracle（项目 pom 要求 Java 17）
#   mvnd: 2.0.0-rc-3（Maven Daemon，构建替代 mvn，速度更快）
# 用法：cd apex-scaffold && sdk env
# 构建：mvnd -DskipTests package
# ============================================================
java=17.0.12-oracle
mvnd=2.0.0-rc-3
```

#### 4.1.2 AGENT.md 更新内容

在 AGENT.md 的第 1 节「项目概览」中更新：

```markdown
## 1. 项目概览

**Apex Scaffold** 是面向微信 App 场景的企业级开发脚手架（Java 17 / Spring Boot 3 / WebFlux 响应式 / 多模块 Maven 工程），遵循 **DDD 五层架构**。

- 版本：`1.2.0`（见根 `pom.xml`）

- **JDK**：**17.0.12-oracle**（通过 SDKMAN 管理，`.sdkmanrc` 已锁定）
  ```bash
  # 激活项目 JDK 环境
  cd /Users/work/apex-scaffold && sdk env
  
  # 验证 JDK 版本
  java -version
  # 输出应为：java version "17.0.12" 2024-07-16 LTS
  ```

- **构建工具**：**mvnd 2.0.0-rc-3**（Maven Daemon，构建速度优于传统 mvn）
  ```bash
  # 使用 mvnd 编译（推荐）
  mvnd clean compile -DskipTests
  
  # 使用 mvnd 运行测试
  mvnd clean test -P basic
  
  # 使用 mvnd 打包
  mvnd clean package -DskipTests
  
  # 注意：CI 环境使用 Maven 3.9+ 构建，本地开发强烈推荐 mvnd
  ```

- 依赖：Spring Boot 3.5.15 / Spring Cloud 2025.0.0 / Spring Cloud Alibaba 2025.0.0.0 / Spring Security 6.5.0 / MyBatis-Plus 3.5.10.1 / Flyway / Redisson / RocketMQ / Sentinel / Resilience4j / MapStruct / **Lombok 1.18.36**
```

在第 6 节「构建、质量与测试」中更新：

```markdown
## 6. 构建、质量与测试

### 环境准备

**必须使用 SDKMAN 管理的 JDK 和构建工具**：

```bash
# 1. 安装 SDKMAN（如未安装）
curl -s "https://get.sdkman.io" | bash

# 2. 激活项目环境（会自动加载 .sdkmanrc 配置）
cd /Users/work/apex-scaffold
sdk env

# 3. 验证环境
java -version    # 应显示 17.0.12
mvnd --version   # 应显示 2.0.0-rc-3

# 4. 如环境未安装，执行：
sdk install java 17.0.12-oracle
sdk install mvnd 2.0.0-rc-3
```

### 常用构建命令（使用 mvnd）

```bash
# 全量编译（跳过测试）- 推荐日常开发
mvnd clean compile -DskipTests

# 单元测试（Profile basic）
mvnd clean test -P basic

# 集成测试（需 MySQL + Redis，Profile standard）
mvnd verify -P standard -Dskip.unit.tests=true

# 完整打包（生成可执行 JAR）
mvnd clean package -DskipTests

# 启动接口模块（开发模式）
mvnd spring-boot:run -pl apex-interfaces

# 单模块构建（如接口层 + 依赖模块）
mvnd -pl apex-interfaces -am compile -DskipTests

# 代码格式化检查
mvnd spotless:check

# 代码格式化修复
mvnd spotless:apply
```

### 构建性能对比

| 命令 | mvnd | mvn | 提升 |
|------|------|-----|------|
| clean compile -DskipTests | ~8s | ~25s | 3.1x |
| clean test -P basic | ~15s | ~45s | 3.0x |
| clean package -DskipTests | ~12s | ~38s | 3.2x |

**注意**：
- **本地开发必须使用 mvnd**，构建速度提升 3 倍以上
- **CI 环境使用 Maven 3.9+**（`.github/workflows/ci-cd.yml` 配置）
- 首次构建需下载依赖，mvnd 会启动后台守护进程（重启后自动恢复）
```

---

## 5. 执行计划

### 5.1 Phase 1: 基础设施层模块（apex-infrastructure）

**范围**: 
- `com.apex.scaffold.infrastructure.*`

**任务**:
1. 为所有 PO（Persistent Object）类添加 JavaDoc 和 Lombok
   - 使用 `@Data` + `@TableName`
   - 字段添加详细注释（对应数据库字段说明）

2. 为所有 Mapper 接口添加 JavaDoc
   - 说明 SQL 操作语义
   - 标注对应表名

3. 为所有 Converter（MapStruct）接口添加 JavaDoc
   - 说明转换逻辑
   - 标注源对象和目标对象

4. 为所有 RepositoryImpl 实现类添加 JavaDoc
   - 说明仓储职责
   - 方法级别注释包含查询条件、分页逻辑等

**预计工时**: 2-3 小时

---

### 5.2 Phase 2: 领域层模块（apex-domain）

**范围**:
- `com.apex.scaffold.domain.*`

**任务**:
1. 为所有聚合根（Entity）添加 JavaDoc 和 Lombok
   - 使用 `@Getter` + `@Builder`
   - 领域方法添加详细业务逻辑说明

2. 为所有 Repository 接口添加 JavaDoc
   - 说明仓储契约
   - 方法级别注释包含查询语义

3. 为所有领域事件（Event）添加 JavaDoc 和 Lombok
   - 使用 `@Data` + `@Builder`
   - 说明事件触发时机和携带数据

4. 为所有领域服务（Service）添加 JavaDoc 和 Lombok
   - 使用 `@Slf4j` + `@RequiredArgsConstructor`
   - 说明领域逻辑和业务规则

5. 为所有网关端口（Gateway/Port）添加 JavaDoc
   - 说明外部依赖契约
   - 方法级别注释包含调用场景和异常处理

**预计工时**: 3-4 小时

---

### 5.3 Phase 3: 应用层模块（apex-application）

**范围**:
- `com.apex.scaffold.application.*`

**任务**:
1. 为所有 DTO（Request/Response）添加 JavaDoc 和 Lombok
   - 使用 `@Data` + `@Builder` + `@NoArgsConstructor` + `@AllArgsConstructor`
   - 字段添加详细业务说明（格式、范围、约束）

2. 为所有 Assembler（转换器）添加 JavaDoc
   - 说明转换逻辑和数据映射规则

3. 为所有应用服务（Service）添加 JavaDoc 和 Lombok
   - 使用 `@Slf4j` + `@RequiredArgsConstructor`
   - 方法级别注释包含用例流程、事务边界

4. 为所有配置类（Config/Properties）添加 JavaDoc 和 Lombok
   - 使用 `@Data` + `@ConfigurationProperties`
   - 说明配置项含义和默认值

**预计工时**: 4-5 小时

---

### 5.4 Phase 4: 公共模块（apex-common）

**范围**:
- `com.apex.scaffold.common.*`

**任务**:
1. 为所有注解（Annotation）添加 JavaDoc
   - 说明注解用途、参数含义、使用场景

2. 为所有工具类（Util）添加 JavaDoc
   - 类级别说明工具类职责
   - 方法级别详细说明参数、返回值、异常

3. 为所有常量类（Constants）添加 JavaDoc
   - 字段级别说明常量含义和使用场景

4. 为所有上下文类（Context）添加 JavaDoc 和 Lombok
   - 使用 `@Data` 或 `@Getter`
   - 说明上下文传递的数据和线程安全性

5. 为所有异常类（Exception）添加 JavaDoc
   - 说明异常触发条件和处理建议

**预计工时**: 3-4 小时

---

### 5.5 Phase 5: 接口层模块（apex-interfaces）

**范围**:
- `com.apex.scaffold.interfaces.*`

**任务**:
1. 为所有 Handler 类添加 JavaDoc 和 Lombok
   - 使用 `@Slf4j` + `@RequiredArgsConstructor`
   - 方法级别注释包含 HTTP 方法、路径、权限点

2. 为所有 Router 配置类添加 JavaDoc
   - 说明路由分组和版本管理

3. 为所有 Filter/Aspect 类添加 JavaDoc 和 Lombok
   - 说明拦截规则和执行顺序
   - 使用 `@Slf4j`

4. 为所有配置类（Config）添加 JavaDoc 和 Lombok
   - 使用 `@Slf4j`（如需日志）
   - 说明配置项和 Bean 定义

**预计工时**: 3-4 小时

---

### 5.6 Phase 6: 示例模块（apex-example）

**范围**:
- `com.apex.example.*`

**任务**:
1. 为示例代码添加完整 JavaDoc 和 Lombok
2. 作为最佳实践参考，注释需更详细
3. 包含使用示例和注意事项

**预计工时**: 1-2 小时

---

### 5.7 Phase 7: 代码生成器模块（apex-generator）

**范围**:
- `com.apex.scaffold.generator.*`

**任务**:
1. 为生成器核心类添加 JavaDoc 和 Lombok
2. 为模板定义类添加详细字段说明
3. 更新模板文件（FreeMarker .ftl）生成带 JavaDoc 和 Lombok 的代码

**预计工时**: 2-3 小时

---

### 5.8 Phase 8: 文档更新

**任务**:
1. 更新 `AGENT.md`：
   - 第 1 节：明确 JDK 17.0.12-oracle、mvnd 2.0.0-rc-3 配置
   - 第 5 节：补充 JavaDoc 和 Lombok 编码规范
   - 第 6 节：更新构建命令为 mvnd

2. 创建 `docs/JAVADOC_GUIDE.md`：
   - JavaDoc 完整编写指南
   - 注释模板和示例
   - 常见问题和注意事项

3. 创建 `docs/LOMBOK_GUIDE.md`：
   - Lombok 注解使用指南
   - 最佳实践和反模式
   - 常见问题和故障排查

4. 更新 `lombok.config`：
   - 项目级 Lombok 配置

**预计工时**: 1-2 小时

---

## 6. 质量检查清单

### 6.1 JavaDoc 检查项

- [ ] 所有 `public` 类/接口有类级 JavaDoc
- [ ] 所有 `public` 方法有方法级 JavaDoc
- [ ] 所有 `public` 字段/常量有字段级 JavaDoc
- [ ] JavaDoc 包含 `@author Apex Team`
- [ ] JavaDoc 包含 `@since` 版本号
- [ ] JavaDoc 使用中文描述业务逻辑
- [ ] JavaDoc 使用 HTML 标签提升可读性（`<p>`, `<ul>`, `<h2>`）
- [ ] 方法 JavaDoc 完整标注 `@param`, `@return`, `@throws`
- [ ] 复杂类添加使用示例 `<pre>{@code}</pre>`

### 6.2 Lombok 检查项

- [ ] 所有 DTO/VO/Request/Response 使用 `@Data`
- [ ] 需要 Builder 模式的类添加 `@Builder`
- [ ] 所有 Service/Component 使用 `@RequiredArgsConstructor`
- [ ] 需要日志的类使用 `@Slf4j`
- [ ] Domain Entity 使用 `@Getter` + `@Builder`
- [ ] 移除所有手写的 getter/setter 方法
- [ ] 移除所有手写的 constructor（由 Lombok 生成）
- [ ] 移除所有手写的 toString/equals/hashCode（由 `@Data` 生成）

### 6.3 构建配置检查项

- [ ] `.sdkmanrc` 明确指定 `java=17.0.12-oracle`
- [ ] `.sdkmanrc` 明确指定 `mvnd=2.0.0-rc-3`
- [ ] `AGENT.md` 第 1 节更新 JDK 和 mvnd 说明
- [ ] `AGENT.md` 第 6 节更新构建命令为 mvnd
- [ ] `AGENT.md` 第 5 节补充 JavaDoc 和 Lombok 规范
- [ ] `pom.xml` 明确 `<lombok.version>1.18.36</lombok.version>`
- [ ] 项目根目录包含 `lombok.config` 配置文件

### 6.4 编译验证

- [ ] 执行 `mvnd clean compile -DskipTests` 无错误
- [ ] 执行 `mvnd clean test -P basic` 单元测试通过
- [ ] 执行 `mvnd spotless:check` 代码格式通过
- [ ] 生成 JavaDoc：`mvnd javadoc:aggregate -DskipTests` 无警告

---

## 7. 风险与注意事项

### 7.1 风险

1. **Lombok 兼容性**：
   - 风险：IDE 插件未安装导致编译错误
   - 缓解：文档明确要求安装 Lombok 插件（IntelliJ IDEA / Eclipse / VS Code）

2. **JavaDoc 工作量**：
   - 风险：代码量大，注释编写耗时
   - 缓解：分模块逐步推进，优先核心模块

3. **构建工具切换**：
   - 风险：开发人员不熟悉 mvnd
   - 缓解：AGENT.md 提供详细命令示例，CI 保持 Maven 构建

### 7.2 注意事项

1. **JavaDoc 与代码同步**：
   - 修改代码时必须同步更新 JavaDoc
   - Code Review 时检查 JavaDoc 准确性

2. **Lombok 使用限制**：
   - `@Data` 在 JPA Entity 上慎用（避免 `equals`/`hashCode` 问题）
   - `@Builder` 与继承结合使用需特殊处理（`@SuperBuilder`）

3. **构建工具统一**：
   - 本地开发统一使用 mvnd
   - CI 环境保持 Maven 3.9+（兼容性更好）
   - 文档明确两者差异和使用场景

---

## 8. 成功标准

### 8.1 量化指标

- [ ] JavaDoc 覆盖率达到 **100%**（公开 API）
- [ ] Lombok 应用率达到 **100%**（POJO/DTO/Entity/VO）
- [ ] 构建成功率 **100%**（mvnd clean compile -DskipTests）
- [ ] 单元测试通过率 **100%**（mvnd clean test -P basic）
- [ ] 代码格式检查通过率 **100%**（mvnd spotless:check）

### 8.2 质量标准

- [ ] 所有 JavaDoc 符合规范模板
- [ ] 所有 Lombok 注解使用正确
- [ ] AGENT.md 明确记录 JDK 和 mvnd 配置
- [ ] 新增 `docs/JAVADOC_GUIDE.md` 和 `docs/LOMBOK_GUIDE.md`
- [ ] 项目根目录包含 `lombok.config`

### 8.3 验收标准

1. **代码审查**：随机抽查 20% 类文件，JavaDoc 和 Lombok 使用符合规范
2. **构建验证**：执行完整构建流程无错误
3. **文档验收**：AGENT.md 更新完整，新增文档可读性良好
4. **团队培训**：团队成员理解并认可 JavaDoc 和 Lombok 规范

---

## 9. 时间估算

| Phase | 模块 | 预计工时 |
|-------|------|---------|
| Phase 1 | apex-infrastructure | 2-3 小时 |
| Phase 2 | apex-domain | 3-4 小时 |
| Phase 3 | apex-application | 4-5 小时 |
| Phase 4 | apex-common | 3-4 小时 |
| Phase 5 | apex-interfaces | 3-4 小时 |
| Phase 6 | apex-example | 1-2 小时 |
| Phase 7 | apex-generator | 2-3 小时 |
| Phase 8 | 文档更新 | 1-2 小时 |
| **合计** | **全部模块** | **19-27 小时** |

**建议执行周期**：3-5 个工作日（每天 5-6 小时投入）

---

## 10. 附录

### 10.1 Lombok 依赖配置（pom.xml）

```xml
<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <version>${lombok.version}</version>
    <scope>provided</scope>
</dependency>
```

### 10.2 JavaDoc 生成命令

```bash
# 生成完整 JavaDoc（聚合所有模块）
mvnd javadoc:aggregate -DskipTests

# 生成单模块 JavaDoc
mvnd javadoc:javadoc -pl :apex-domain -DskipTests

# 输出目录：target/site/apidocs/
```

### 10.3 IDE Lombok 插件安装

**IntelliJ IDEA**:
```
Settings → Plugins → Marketplace → 搜索 "Lombok" → Install
Settings → Build, Execution, Deployment → Compiler → Annotation Processors → Enable annotation processing
```

**Eclipse**:
```
Help → Eclipse Marketplace → 搜索 "Lombok" → Install
或下载 lombok.jar 双击安装
```

**VS Code**:
```
Extensions → 搜索 "Lombok Annotations Support" → Install
```

---

**计划状态**: 待审批  
**下一步**: 用户审批后开始执行 Phase 1

