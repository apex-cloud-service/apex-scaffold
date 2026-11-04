# 全项目代码评审与改造计划

> **历史快照**：本文记录某一时点的状态，模块数、端口、路径与类名以当前代码为准，不作为现状依据。

**目标**：系统性评审并修复所有不符合 JavaDoc 和 Lombok 规范的代码

---

## 📋 目录

1. [计划概述](#计划概述)
2. [评审标准](#评审标准)
3. [执行策略](#执行策略)
4. [分模块执行计划](#分模块执行计划)
5. [自动化工具](#自动化工具)
6. [验收标准](#验收标准)
7. [时间估算](#时间估算)

---

<a id="计划概述"></a>

## 计划概述

### 🎯 目标

**问题 1：Lombok 未规范使用**
- 存在手写的 getter/setter/constructor/toString/equals/hashCode
- 缺少 `@Builder`、`@NoArgsConstructor`、`@AllArgsConstructor`
- Service 类未使用 `@RequiredArgsConstructor`
- 缺少 `@Slf4j` 日志注解

**问题 2：JavaDoc 注释缺失或不完整**
- 类级 JavaDoc 过于简单或缺失
- 方法级 JavaDoc 缺少 `@param`、`@return`、`@throws`
- 字段级 JavaDoc 缺失
- 缺少 HTML 标签和代码示例

### 📊 项目规模

| 模块 | 预估 Java 文件数 | 预估问题文件数 | 优先级 |
|------|-----------------|---------------|--------|
| apex-infrastructure | ~200 | ~150 | 🔴 高 |
| apex-application | ~100 | ~80 | 🔴 高 |
| apex-domain | ~60 | ~40 | 🔴 高 |
| apex-common | ~80 | ~60 | 🟡 中 |
| apex-interfaces | ~50 | ~30 | 🟡 中 |
| apex-example | ~20 | ~10 | 🟢 低 |
| apex-generator | ~10 | ~5 | 🟢 低 |
| **总计** | **~520** | **~375** | - |

---

<a id="评审标准"></a>

## 评审标准

### ✅ Lombok 使用规范

#### 1. DTO/VO/Request/Response 类

**必须有**：
```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class XxxDTO {
    // 字段
}
```

**禁止**：
- ❌ 手写 getter/setter
- ❌ 手写 constructor
- ❌ 手写 toString/equals/hashCode

#### 2. PO（持久化对象）类

**必须有**：
```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)  // 如果有父类
@TableName("table_name")
public class XxxPO extends BasePO {
    // 字段
}
```

#### 3. Domain Entity（领域实体）

**必须有**：
```java
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class XxxEntity {
    // 字段
    
    // 领域方法（不使用 @Data，保持封装性）
}
```

#### 4. Service/Component 类

**必须有**：
```java
@Slf4j
@Service  // 或 @Component
@RequiredArgsConstructor
public class XxxService {
    private final Dependency1 dependency1;
    private final Dependency2 dependency2;
    
    // 方法
}
```

**禁止**：
- ❌ 手写构造器
- ❌ 手写 `private static final Logger log = ...`

#### 5. 配置类

**必须有**：
```java
@Data
@Configuration
@ConfigurationProperties(prefix = "xxx")
public class XxxProperties {
    // 配置字段
}
```

### ✅ JavaDoc 注释规范

#### 1. 类级 JavaDoc（必须）

**最低要求**：
```java
/**
 * [类的简短描述]
 *
 * <p>[类的详细说明，包括职责、使用场景]
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.0.0
 */
```

**完整示例**（推荐）：
```java
/**
 * [类的简短描述]
 *
 * <p>[详细说明]
 *
 * <h2>主要功能</h2>
 * <ul>
 *   <li>功能1</li>
 *   <li>功能2</li>
 * </ul>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * // 代码示例
 * }</pre>
 *
 * <h2>注意事项</h2>
 * <ul>
 *   <li>注意事项1</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.0.0
 * @see RelatedClass
 */
```

#### 2. 方法级 JavaDoc（public/protected 必须）

**最低要求**：
```java
/**
 * [方法简短描述]
 *
 * <p>[方法详细说明]
 *
 * @param param1 参数1说明
 * @return 返回值说明
 * @throws ExceptionType 异常说明
 */
```

#### 3. 字段级 JavaDoc（public/protected 必须）

**最低要求**：
```java
/**
 * [字段说明]
 *
 * <p>[字段详细描述：业务含义、取值范围、默认值]
 */
private String fieldName;
```

---

<a id="执行策略"></a>

## 执行策略

### 🔍 Phase 1：自动扫描（1 小时）

**目标**：识别所有不符合规范的文件

**工具**：编写扫描脚本

**输出**：
- `lombok-issues.txt`：Lombok 问题文件清单
- `javadoc-issues.txt`：JavaDoc 问题文件清单
- `priority-list.txt`：按优先级排序的待修复文件列表

**扫描规则**：

1. **Lombok 问题检测**：
   - DTO/VO 类没有 `@Builder`
   - PO 类没有 `@NoArgsConstructor` 或 `@AllArgsConstructor`
   - Service 类有手写构造器但没有 `@RequiredArgsConstructor`
   - 存在手写的 `public getXxx()` 或 `public setXxx()`

2. **JavaDoc 问题检测**：
   - `public` 类没有类级 JavaDoc
   - `public` 方法没有方法级 JavaDoc
   - JavaDoc 缺少 `@author` 或 `@since`
   - 方法 JavaDoc 缺少 `@param`、`@return`

### 🔧 Phase 2：批量修复（按模块）

**策略**：先易后难，先高优先级后低优先级

#### Step 1：apex-infrastructure（预计 6-8 小时）

**子任务**：
1. **PO 类**（~50 个文件）
   - 添加 `@Builder`、`@NoArgsConstructor`、`@AllArgsConstructor`
   - 移除手写构造器
   - 添加/完善类级和字段级 JavaDoc

2. **Mapper 接口**（~40 个文件）
   - 添加/完善类级 JavaDoc
   - 添加/完善方法级 JavaDoc（含 SQL 语义说明）

3. **Converter 接口**（~20 个文件）
   - 添加/完善类级 JavaDoc（MapStruct 说明）
   - 添加/完善方法级 JavaDoc（含转换规则）

4. **RepositoryImpl 类**（~30 个文件）
   - 添加 `@RequiredArgsConstructor`
   - 移除手写构造器
   - 添加/完善类级和方法级 JavaDoc

5. **Gateway 实现类**（~20 个文件）
   - 添加 `@Slf4j`、`@RequiredArgsConstructor`
   - 添加/完善 JavaDoc

#### Step 2：apex-application（预计 5-7 小时）

**子任务**：
1. **DTO 类**（~40 个文件）
   - 添加 `@Builder`、`@NoArgsConstructor`、`@AllArgsConstructor`
   - 添加/完善字段级 JavaDoc（含验证规则说明）

2. **Service 类**（~30 个文件）
   - 添加 `@Slf4j`、`@RequiredArgsConstructor`
   - 移除手写构造器和 Logger 字段
   - 添加/完善类级和方法级 JavaDoc

3. **Assembler 类**（~10 个文件）
   - 添加 `@RequiredArgsConstructor`
   - 添加/完善 JavaDoc

4. **Config/Properties 类**（~10 个文件）
   - 添加 `@Data`（如果是 `@ConfigurationProperties`）
   - 添加/完善 JavaDoc

#### Step 3：apex-domain（预计 4-5 小时）

**子任务**：
1. **Entity 类**（~20 个文件）
   - 使用 `@Getter` + `@Builder`（不使用 `@Data`）
   - 添加私有构造器注解
   - 添加/完善类级和方法级 JavaDoc

2. **Repository 接口**（~15 个文件）
   - 添加/完善类级和方法级 JavaDoc

3. **Domain Service 类**（~10 个文件）
   - 添加 `@Slf4j`、`@RequiredArgsConstructor`
   - 添加/完善 JavaDoc

4. **Event 类**（~10 个文件）
   - 添加 `@Data`、`@Builder`
   - 添加/完善 JavaDoc

#### Step 4：apex-common（预计 3-4 小时）

**子任务**：
1. **工具类**（~30 个文件）
   - 添加/完善类级和方法级 JavaDoc
   - 工具类通常不需要 Lombok

2. **常量类**（~20 个文件）
   - 添加/完善字段级 JavaDoc

3. **注解类**（~10 个文件）
   - 添加/完善类级 JavaDoc

4. **异常类**（~10 个文件）
   - 添加/完善类级 JavaDoc

5. **上下文类**（~10 个文件）
   - 添加 `@Data` 或 `@Getter`
   - 添加/完善 JavaDoc

#### Step 5：apex-interfaces（预计 3-4 小时）

**子任务**：
1. **Handler 类**（~30 个文件）
   - 添加 `@Slf4j`、`@RequiredArgsConstructor`
   - 添加/完善类级和方法级 JavaDoc

2. **Router 类**（~10 个文件）
   - 添加/完善 JavaDoc

3. **Filter/Aspect 类**（~10 个文件）
   - 添加 `@Slf4j`
   - 添加/完善 JavaDoc

#### Step 6：apex-example（预计 1-2 小时）

**子任务**：
1. 参考前面模块的改造方式
2. 确保示例代码最规范（作为最佳实践）

#### Step 7：apex-generator（预计 1-2 小时）

**子任务**：
1. 改造生成器代码本身
2. 更新模板文件（.ftl），生成符合规范的代码

### ✅ Phase 3：验证与修正（2-3 小时）

**验证步骤**：

1. **编译验证**：
   ```bash
   mvnd clean compile -DskipTests
   ```

2. **JavaDoc 生成验证**：
   ```bash
   mvnd javadoc:aggregate -DskipTests
   ```
   检查警告和错误

3. **代码格式验证**：
   ```bash
   mvnd spotless:check
   ```

4. **单元测试验证**：
   ```bash
   mvnd clean test -P basic
   ```

5. **人工抽查**：
   - 每个模块随机抽查 10-20 个文件
   - 检查 JavaDoc 准确性和 Lombok 使用正确性

---

<a id="分模块执行计划"></a>

## 分模块执行计划

### 📅 执行时间表（建议）

| 天数 | 任务 | 预计工时 | 负责人 | 状态 |
|------|------|---------|--------|------|
| **Day 1** | Phase 1: 自动扫描 + Step 1.1-1.2 | 6-8h | 2-3 人 | ⏳ 待开始 |
| **Day 2** | Step 1.3-1.5（完成 infrastructure） | 6-8h | 2-3 人 | ⏳ 待开始 |
| **Day 3** | Step 2（完成 application） | 5-7h | 2-3 人 | ⏳ 待开始 |
| **Day 4** | Step 3-4（完成 domain + common） | 7-9h | 2-3 人 | ⏳ 待开始 |
| **Day 5** | Step 5-7（完成 interfaces/example/generator） | 5-6h | 1-2 人 | ⏳ 待开始 |
| **Day 6** | Phase 3: 验证与修正 | 2-3h | 全员 | ⏳ 待开始 |

**总计**：**6 个工作日**（并行工作可缩短至 3-4 天）

---

<a id="自动化工具"></a>

## 自动化工具

### 🤖 Lombok 问题扫描脚本

```bash
#!/bin/bash
# scan-lombok-issues.sh

echo "扫描 Lombok 使用问题..."

# 1. 查找有 getter/setter 方法的类
echo "==> 查找手写 getter/setter..."
find apex-*/src/main/java -name "*.java" -exec grep -l "public.*get[A-Z]" {} \; > lombok-issues-getter.txt

# 2. 查找 DTO 类但没有 @Builder 的
echo "==> 查找缺少 @Builder 的 DTO 类..."
find apex-application/src/main/java -path "*/dto/*.java" -exec grep -L "@Builder" {} \; > lombok-issues-builder.txt

# 3. 查找 Service 类有手写构造器的
echo "==> 查找 Service 类手写构造器..."
find apex-*/src/main/java -path "*/service/*.java" -exec grep -l "public.*Service(" {} \; > lombok-issues-constructor.txt

# 4. 查找没有 @Slf4j 但有 Logger 字段的
echo "==> 查找手写 Logger 字段..."
find apex-*/src/main/java -name "*.java" -exec grep -l "private.*Logger.*log" {} \; | while read f; do
    if ! grep -q "@Slf4j" "$f"; then
        echo "$f" >> lombok-issues-logger.txt
    fi
done

echo "扫描完成，结果保存在 lombok-issues-*.txt"
```

### 🔍 JavaDoc 问题扫描脚本

```bash
#!/bin/bash
# scan-javadoc-issues.sh

echo "扫描 JavaDoc 注释问题..."

# 1. 查找 public 类没有类级 JavaDoc 的
echo "==> 查找缺少类级 JavaDoc..."
find apex-*/src/main/java -name "*.java" -exec sh -c '
    if grep -q "^public class\|^public interface\|^public enum" "$1"; then
        if ! grep -B5 "^public class\|^public interface\|^public enum" "$1" | grep -q "/\*\*"; then
            echo "$1"
        fi
    fi
' _ {} \; > javadoc-issues-class.txt

# 2. 查找 JavaDoc 缺少 @author 的
echo "==> 查找缺少 @author..."
find apex-*/src/main/java -name "*.java" -exec sh -c '
    if grep -q "/\*\*" "$1"; then
        if ! grep -q "@author" "$1"; then
            echo "$1"
        fi
    fi
' _ {} \; > javadoc-issues-author.txt

# 3. 查找 public 方法没有 JavaDoc 的
echo "==> 查找缺少方法级 JavaDoc..."
find apex-*/src/main/java -name "*.java" -exec sh -c '
    if grep -q "public.*(" "$1"; then
        # 简化检查：这里只是示例，实际需要更复杂的逻辑
        echo "$1"
    fi
' _ {} \; > javadoc-issues-method.txt

echo "扫描完成，结果保存在 javadoc-issues-*.txt"
```

### 🛠️ 批量修复辅助脚本

```bash
#!/bin/bash
# fix-lombok-simple.sh

# 示例：批量为 DTO 类添加 @Builder 注解
find apex-application/src/main/java -path "*/dto/*.java" | while read file; do
    if ! grep -q "@Builder" "$file"; then
        # 在 @Data 后添加 @Builder
        sed -i.bak '/^@Data$/a\
@Builder\
@NoArgsConstructor\
@AllArgsConstructor' "$file"
        echo "已修复: $file"
    fi
done
```

**注意**：批量脚本仅用于简单场景，复杂情况需人工处理。

---

<a id="验收标准"></a>

## 验收标准

### ✅ Lombok 验收标准

#### 定量指标
- [ ] DTO/VO 类 `@Builder` 使用率 ≥ 95%
- [ ] Service 类 `@RequiredArgsConstructor` 使用率 ≥ 90%
- [ ] Service 类 `@Slf4j` 使用率 ≥ 85%
- [ ] PO 类构造器注解完整率 ≥ 95%
- [ ] 手写 getter/setter 残留 ≤ 5%

#### 质量指标
- [ ] 无编译错误
- [ ] 无 Lombok 配置警告（除预期的 flagUsage）
- [ ] 所有样板代码已移除

### ✅ JavaDoc 验收标准

#### 定量指标
- [ ] `public` 类 JavaDoc 覆盖率 = 100%
- [ ] `public` 方法 JavaDoc 覆盖率 ≥ 95%
- [ ] `public` 字段 JavaDoc 覆盖率 ≥ 90%
- [ ] JavaDoc 包含 `@author` 比率 = 100%
- [ ] JavaDoc 包含 `@since` 比率 ≥ 95%

#### 质量指标
- [ ] JavaDoc 生成无错误
- [ ] JavaDoc 生成警告 ≤ 50 个
- [ ] 方法 JavaDoc 完整标注 `@param`、`@return`、`@throws`
- [ ] 使用 HTML 标签提升可读性

### 🎯 最终验收

**命令验证**：
```bash
# 1. 编译通过
mvnd clean compile -DskipTests
# 期望：BUILD SUCCESS

# 2. 测试通过
mvnd clean test -P basic
# 期望：所有测试通过

# 3. JavaDoc 生成
mvnd javadoc:aggregate -DskipTests
# 期望：无错误，警告 ≤ 50

# 4. 代码格式
mvnd spotless:check
# 期望：通过

# 5. 代码行数统计（可选）
cloc apex-*/src/main/java
# 对比改造前后代码行数，预期减少 5-10%
```

---

<a id="时间估算"></a>

## 时间估算

### 📊 工作量估算

| 模块 | Lombok 修复 | JavaDoc 添加 | 验证修正 | 小计 |
|------|------------|-------------|---------|------|
| apex-infrastructure | 3-4h | 3-4h | 1h | **7-9h** |
| apex-application | 2-3h | 3-4h | 1h | **6-8h** |
| apex-domain | 2h | 2-3h | 0.5h | **4.5-5.5h** |
| apex-common | 1h | 2-3h | 0.5h | **3.5-4.5h** |
| apex-interfaces | 1-2h | 2-3h | 0.5h | **3.5-5.5h** |
| apex-example | 0.5h | 0.5-1h | 0.5h | **1.5-2h** |
| apex-generator | 0.5h | 0.5-1h | 0.5h | **1.5-2h** |
| Phase 1 扫描 | - | - | 1h | **1h** |
| Phase 3 验证 | - | - | 2h | **2h** |
| **总计** | **10-13h** | **14-19h** | **7h** | **31-39h** |

### 👥 人力配置建议

**方案 A：3 人并行**（推荐）
- 人员 1：apex-infrastructure（2-3 天）
- 人员 2：apex-application + apex-domain（2-3 天）
- 人员 3：apex-common + apex-interfaces + apex-example + apex-generator（2-3 天）
- **总耗时**：**3-4 个工作日**

**方案 B：2 人并行**
- 人员 1：apex-infrastructure + apex-application（3-4 天）
- 人员 2：其他模块（3-4 天）
- **总耗时**：**4-5 个工作日**

**方案 C：1 人串行**
- **总耗时**：**6-8 个工作日**

---

## 风险与注意事项

### ⚠️ 风险

1. **编译失败风险**
   - **原因**：Lombok 注解使用不当
   - **缓解**：每修复一批文件就编译验证

2. **功能回归风险**
   - **原因**：误删或误改代码逻辑
   - **缓解**：运行完整测试套件，Code Review

3. **工作量低估风险**
   - **原因**：遇到复杂情况需人工处理
   - **缓解**：预留 20% 缓冲时间

### 🔒 注意事项

1. **Lombok 使用限制**
   - `@Data` 在 JPA Entity 上慎用
   - `@Builder` 与继承结合需使用 `@SuperBuilder`
   - 继承类必须设置 `@EqualsAndHashCode(callSuper = true)`

2. **JavaDoc 准确性**
   - 不要复制粘贴不准确的描述
   - 参数、返回值、异常说明必须准确
   - 代码修改后同步更新 JavaDoc

3. **提交策略**
   - 每完成一个模块就提交一次
   - Commit Message 格式：`docs: 完成 apex-infrastructure 模块 JavaDoc 和 Lombok 改造`
   - 避免一次性大量提交

4. **Code Review**
   - 改造完成后必须 Code Review
   - 重点检查 JavaDoc 准确性和 Lombok 使用正确性

---

## 检查清单

### 📋 开始前检查

- [ ] 阅读 `docs/JAVADOC_GUIDE.md`
- [ ] 阅读 `docs/LOMBOK_GUIDE.md`
- [ ] 阅读 `docs/EXAMPLES_SUMMARY.md`
- [ ] 安装 IDE Lombok 插件
- [ ] 激活 SDKMAN 环境：`cd /Users/work/apex-scaffold && sdk env`
- [ ] 执行 Phase 1 扫描，生成问题清单

### 📋 改造中检查（每个文件）

- [ ] 类级 JavaDoc 完整（含 `@author`、`@version`、`@since`）
- [ ] 方法级 JavaDoc 完整（含 `@param`、`@return`、`@throws`）
- [ ] 字段级 JavaDoc 完整（public/protected 字段）
- [ ] Lombok 注解正确（根据类型选择注解）
- [ ] 移除手写样板代码（getter/setter/constructor/toString/equals/hashCode）
- [ ] 敏感字段在 `@ToString` 中排除

### 📋 模块完成后检查

- [ ] `mvnd clean compile -DskipTests -pl <模块名> -am`：编译通过
- [ ] `mvnd clean test -P basic -pl <模块名> -am`：测试通过
- [ ] 随机抽查 10-20 个文件，确认符合规范
- [ ] Code Review 通过
- [ ] 提交代码

### 📋 全部完成后检查

- [ ] `mvnd clean compile -DskipTests`：全量编译通过
- [ ] `mvnd clean test -P basic`：全量测试通过
- [ ] `mvnd javadoc:aggregate -DskipTests`：JavaDoc 生成无错误
- [ ] `mvnd spotless:check`：代码格式通过
- [ ] 最终 Code Review
- [ ] 更新 README（如需要）

---

## 附录

### 📚 参考文档

- JavaDoc 编写指南：`docs/JAVADOC_GUIDE.md`
- Lombok 使用指南：`docs/LOMBOK_GUIDE.md`
- 示例代码汇总：`docs/EXAMPLES_SUMMARY.md`
- 项目开发规范：`AGENT.md`
- Lombok 配置：`lombok.config`

### 🛠️ 常用命令

```bash
# 激活环境
cd /Users/work/apex-scaffold && sdk env

# 编译单个模块
mvnd clean compile -DskipTests -pl apex-infrastructure -am

# 测试单个模块
mvnd clean test -P basic -pl apex-infrastructure -am

# 生成 JavaDoc
mvnd javadoc:aggregate -DskipTests

# 代码格式化
mvnd spotless:apply

# 代码格式检查
mvnd spotless:check

# 查看 Lombok 生成的代码
mvnd clean compile -DskipTests
# 查看 target/generated-sources/annotations/
```

### 📊 进度跟踪模板

| 模块 | Lombok 修复 | JavaDoc 添加 | 编译验证 | 测试验证 | 状态 |
|------|------------|-------------|---------|---------|------|
| apex-infrastructure | ⏳ 进行中 | ⏳ 进行中 | ⬜ 待开始 | ⬜ 待开始 | 🟡 |
| apex-application | ⬜ 待开始 | ⬜ 待开始 | ⬜ 待开始 | ⬜ 待开始 | ⬜ |
| apex-domain | ⬜ 待开始 | ⬜ 待开始 | ⬜ 待开始 | ⬜ 待开始 | ⬜ |
| apex-common | ⬜ 待开始 | ⬜ 待开始 | ⬜ 待开始 | ⬜ 待开始 | ⬜ |
| apex-interfaces | ⬜ 待开始 | ⬜ 待开始 | ⬜ 待开始 | ⬜ 待开始 | ⬜ |
| apex-example | ⬜ 待开始 | ⬜ 待开始 | ⬜ 待开始 | ⬜ 待开始 | ⬜ |
| apex-generator | ⬜ 待开始 | ⬜ 待开始 | ⬜ 待开始 | ⬜ 待开始 | ⬜ |

图例：
- ⬜ 待开始
- ⏳ 进行中
- ✅ 已完成
- ❌ 有问题
- 🟡 部分完成
- 🟢 已验收

---

**计划状态**：✅ 已完成，待审批  
**下一步**：审批通过后开始执行  
**预计完成时间**：3-6 个工作日（取决于人力投入）

**创建日期**：2026-09-02  
**创建者**：开发团队
