# 修复SVG图表显示问题及清理备份文件

## 任务概述

本计划将完成两个任务：
1. 删除SVG备份文件（*.svg.bak）
2. 修复13-17号SVG图表的XML解析错误，使其能够正常显示

## 当前状态分析

### 备份文件清单
通过探索发现以下6个备份文件需要删除：
- `/Users/work/apex-scaffold/docs/diagrams/13_deployment_architecture.svg.bak`
- `/Users/work/apex-scaffold/docs/diagrams/14_network_architecture.svg.bak`
- `/Users/work/apex-scaffold/docs/diagrams/15_data_flow_architecture.svg.bak`
- `/Users/work/apex-scaffold/docs/diagrams/16_mq_architecture.svg.bak`
- `/Users/work/apex-scaffold/docs/diagrams/17_monitoring_architecture.svg.bak`
- `/Users/work/apex-scaffold/docs/diagrams/18_microservice_governance.svg.bak`

### SVG文件状态
通过xmllint验证发现：
- ✅ 13_deployment_architecture.svg - Valid XML（无需修复）
- ❌ 14_network_architecture.svg - Invalid XML（需修复）
- ❌ 15_data_flow_architecture.svg - Invalid XML（需修复）
- ❌ 16_mq_architecture.svg - Invalid XML（需修复）
- ❌ 17_monitoring_architecture.svg - Invalid XML（需修复）
- ✅ 18_microservice_governance.svg - 未测试（应该也是Valid）

### XML解析错误详情

#### 错误类型1: `&` 符号未转义
**文件**: 14, 15, 16号
**位置**: subtitle文本中包含 `&` 符号（例如 "Architecture & Design"）
**错误信息**: `parser error : xmlParseEntityRef: no name`

**问题代码示例**:
```xml
<text>Network Architecture & Security Zones</text>
```

**修复方案**: 将 `&` 替换为 `&amp;`
```xml
<text>Network Architecture &amp; Security Zones</text>
```

#### 错误类型2: `<` 符号未转义
**文件**: 15, 16, 17号
**位置**: 文本内容中包含 `<` 符号（例如 "QPS < 正常值50%"）
**错误信息**: `parser error : StartTag: invalid element name`

**问题代码示例**:
```xml
<text>• P1: QPS异常 (QPS < 正常值50% 持续5分钟)</text>
```

**修复方案**: 将 `<` 替换为 `&lt;`
```xml
<text>• P1: QPS异常 (QPS &lt; 正常值50% 持续5分钟)</text>
```

### 受影响的具体位置

**14_network_architecture.svg** (第40行):
```
<text x="700" y="65" text-anchor="middle" class="subtitle">Network Architecture & Security Zones</text>
```

**15_data_flow_architecture.svg**:
- 第50行: `& Integration Patterns`
- 第154行左右: 包含 `<` 符号

**16_mq_architecture.svg**:
- 第43行: `& HA Design`
- 第279行左右: 包含 `<` 符号

**17_monitoring_architecture.svg**:
- 第242行: `QPS < 正常值50%`

## 实施方案

### 阶段1: 删除备份文件

**操作**: 使用 `DeleteFile` 工具删除所有6个 .bak 文件

**验证**: 运行 `ls -la *.bak` 确认文件已删除

### 阶段2: 修复XML特殊字符转义

需要修复的文件及具体操作：

#### 文件1: 14_network_architecture.svg
**修复内容**:
- 第40行: `Architecture &` → `Architecture &amp;`

#### 文件2: 15_data_flow_architecture.svg  
**修复内容**:
- 第50行: `Architecture &` → `Architecture &amp;`
- 第154行附近: 查找所有 `<` 符号并替换为 `&lt;`

#### 文件3: 16_mq_architecture.svg
**修复内容**:
- 第43行: `Architecture &` → `Architecture &amp;`
- 第279行附近: 查找所有 `<` 符号并替换为 `&lt;`

#### 文件4: 17_monitoring_architecture.svg
**修复内容**:
- 第242行: `QPS <` → `QPS &lt;`
- 全文搜索其他可能的 `<` 和 `>` 符号并转义

### 阶段3: 验证修复结果

**验证方法**:
1. 使用 xmllint 验证所有SVG文件的XML语法
2. 在浏览器中打开 test_svg.html 查看渲染效果
3. 运行 verify_svg.sh 脚本进行全面检查

**预期结果**: 所有4个文件通过 xmllint 验证，输出 "Valid XML"

## 技术细节

### XML特殊字符转义规则

在XML/SVG文本内容中，以下字符必须转义：

| 字符 | 转义形式 | 说明 |
|------|----------|------|
| `&` | `&amp;` | 必须转义，否则会被解析为实体引用 |
| `<` | `&lt;` | 必须转义，否则会被解析为标签开始 |
| `>` | `&gt;` | 推荐转义（在某些上下文中必须） |
| `"` | `&quot;` | 在属性值中必须转义 |
| `'` | `&apos;` | 在属性值中必须转义 |

### 为什么13号文件没有问题？

13_deployment_architecture.svg 已在之前的修复中正确处理了所有特殊字符，因此通过了XML验证。

### 为什么之前emoji替换后仍然无法显示？

emoji替换解决了兼容性问题，但XML语法错误（特殊字符未转义）导致SVG文件根本无法被XML解析器正确解析，因此在严格的XML环境（如某些浏览器或markdown渲染器）中完全无法显示。

## 风险评估

### 风险等级：低

**原因**:
1. 只修改文本内容，不涉及SVG结构和样式
2. 有备份文件可以恢复（删除前的.bak文件）
3. 修改内容明确，不会引入新问题

### 回滚方案

如果修复后出现问题，可以从备份恢复（在删除备份前）：
```bash
cp 14_network_architecture.svg.bak 14_network_architecture.svg
```

## 成功标准

✅ 所有6个备份文件被成功删除
✅ 所有4个SVG文件通过 xmllint 验证
✅ test_svg.html 中所有图表正常显示
✅ verify_svg.sh 脚本报告全部通过

## 预估时间

- 删除备份文件: 1分钟
- 修复XML转义问题: 10-15分钟
- 验证和测试: 5分钟
- **总计**: 约20分钟
