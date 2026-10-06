# Apex Scaffold 项目注释规范

**版本**：1.0（2026-10-06 起生效）
**依据**：2026-10-06 全仓注释现状审计（`docs/04-development/javadoc-guide.md` 的姊妹规范：该指南讲 JavaDoc 写法，本规范讲语言决策、文件头、行内注释、坏注释红线与兼容性条款）

---

## 0. 适用范围与总原则

- 适用：全部 Maven 模块的 `src/main/java`；`src/test/java`、SQL、Shell 脚本按对应小节的宽松规则执行。
- 三条总原则：
  1. **注释解释"为什么"，代码表达"是什么"**——能从代码和签名直接读出的信息不写进注释。
  2. **注释必须与代码同步演进**——改签名必查 `@param`，删成员必删其注释。
  3. **宁缺毋滥**——一个准确的单行注释，优于一段复制粘贴的套话。

## 1. 注释语言：中文为主

- 注释读者是团队内部开发者，统一使用**中文**；代码标识符、框架/协议术语（JWT、WebFlux、Flyway）、HTTP 方法与状态码、SQL 关键字保留英文原文，中英混排时术语两侧加空格。
- `@author` 标签值例外，使用固定署名 **`Apex Team`**；个人贡献由 git 记录，不写个人署名。
- 注释**不承担**用户可见文案：面向最终用户的提示一律走 i18n（`messages_*.properties`）；对外 API 文档（`@Schema`/`@Operation` 的 `description`）同样用中文。

## 2. 文件头模板

### 2.1 Java：类级 JavaDoc 即文件头（维持现行约定）

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

### 2.2 SQL 迁移脚本

```sql
-- =============================================================================
-- V44__add_device_trust_tables.sql
-- 用途：认证中心设备信任模块建表与初始权限种子
-- 适用：mysql / postgresql（同版本号双库脚本必须同步维护）
-- 作者：Apex Team
-- 创建：2026-09-27
-- =============================================================================
```

### 2.3 Shell 脚本

```bash
#!/bin/bash
###############################################################################
# check-xxx.sh - 一句话用途
# 用法：./check-xxx.sh [--verbose]   （含参数说明与退出码含义）
# 作者：Apex Team    创建：2026-10-06
###############################################################################
```

### 2.4 其他类型

- `.ftl` 模板（apex-generator）、`pom.xml`、Grafana/OpenAPI 导出物：**不要求**文件头。
- `.yml` 应用配置：新增配置段用 `#` 段落注释说明每个新增 key 的含义，不要求整文件头。

## 3. 类 / 函数 / 方法文档模板

### 3.1 类与接口

简单类（DTO、PO、异常、枚举项少）：首句 + `@author` 系列即可，禁止堆砌空分节。
复杂服务类可用 `<h2>` 分节，**只允许**以下节名：`职责范围`、`线程安全性`、`设计说明`、`使用示例`。

### 3.2 方法（核心模板）

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

### 3.3 免文档白名单（写也只准写一行）

以下成员**不要求** JavaDoc；如确要写，只允许单行 `/** ... */`，且不得复述签名：

- getter/setter/is 方法；
- builder 模式的流式 setter；
- Lombok/框架生成成员——**严禁为它们手写注释**（注释会变成悬空孤儿）。

### 3.4 record 与枚举

- record：record 级 JavaDoc 用 `@param` 描述**组件**（JDK 标准写法）。
- 枚举：每个常量上一行 `/** 说明 */`；**删除常量必须同时删除其注释**。

## 4. 行内注释规则

**何时写（只写以下四类）**：

1. 业务背景/为什么这么做；
2. 坑与约束（WebFlux 链中不得阻塞、Y2038 类时间约束等）；
3. 魔法值解释（正则、常量取值来源、第三方接口参数）；
4. 外部契约（与前端/第三方约定的字段含义、Flyway 版本号联动关系）。

**如何写**：

- 独立行 `// ` + 空格 + 中文短句，与被注释代码**同缩进**，放在代码**上方**；行尾注释仅用于极短标注（参数单位等）；
- 单行 ≤ **120 字符**（与 palantirJavaFormat 行宽一致）；超过两行的说明升级为方法 JavaDoc；
- 分节横幅 `/* ==================== 概览 ==================== */` **允许**用于 500 行以上大文件分节，但横幅不是文档，不能替代 JavaDoc；
- `TODO`/`FIXME` 格式强制：`// TODO(Apex Team / 工单号): 〈可执行的下一步〉`。禁止没有负责人或不可执行的 TODO。

## 5. 禁止的"坏注释"（红线）

以下反例均来自 2026-10-06 全仓审计的真实出处（已修复，保留作为回归样本）：

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

## 6. 与现有正确注释的兼容性（Grandfather 条款）

1. **不翻动存量**：不新增 package 前置文件头块；既有 `@author/@version/@since` 无需调整，缺 `@created` 不回填。
2. **合法既有风格白名单**（明确不算违规，禁止"顺手改掉"）：record 组件 `@param`、`/* == */` 分节横幅、单行 `/** 字段说明 */`、字段级多行 JavaDoc、`@SuppressWarnings("PMD.ClassMustHaveAuthorRule")` 豁免、`@Override` 方法无文档、测试代码无类文档（测试类新增时才按"一行类级说明"要求）。
3. **署名渐进收敛**：现存多种 `@author` 写法仅在文件因其他原因被大改时顺手统一为 `Apex Team`，不做专项批量替换。
4. **工具链判据对齐**：`scripts/dev/check/scan-javadoc-issues.sh` 的判据（类级 JavaDoc、`@author`、`@since`、`@Override` 豁免、带参方法需 `@param`）即本规范的机器可查子集，该脚本输出必须保持全绿；新增检查项先改脚本再改码，脚本入库并使用 `scripts/lib/common.sh`。
5. **与 AGENTS.md 的关系**：本规范是 AGENTS.md "代码注释规范"一节的展开，冲突处以 AGENTS.md 为准。

## 7. 执行与校验

**提交前检查清单**（并入既有提交纪律）：

- [ ] 新增/修改的公开类型与方法 JavaDoc 符合第 2、3 节模板；
- [ ] `@param` 名称与签名逐字一致；`void` 方法无 `@return`；
- [ ] 无第 5 节九类坏注释；`grep -rn "TODO: Add method description" --include="*.java" apex-*/src/main` 为空；
- [ ] `mvnd spotless:check` 通过；`scripts/dev/check/scan-javadoc-issues.sh` 全绿；
- [ ] 一处声明只有一个 JavaDoc 块；删除成员时其注释已同步删除。
