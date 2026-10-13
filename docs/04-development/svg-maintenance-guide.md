# SVG 架构图表维护指南

> 版本: v1.0 | 发布日期: 2026-09-10 | 维护团队: 架构团队

本文档规范了 Apex Scaffold 项目中 29 张 SVG 架构图表的维护流程,确保图表与代码实现保持同步。

---

## 📋 维护原则

### 核心原则

1. **代码优先**: 架构图表必须反映代码的实际实现,而非理想设计
2. **版本同步**: 所有技术栈版本号必须与 `pom.xml`/`package.json` 保持一致
3. **准确统计**: 代码统计数据(Handler数量、Service数量等)基于实际Grep结果
4. **及时更新**: 架构变更后1周内完成图表更新
5. **向后兼容**: 更新图表时保持文件名不变,避免破坏已有引用

### 质量标准

- ✅ **跨平台兼容**: 使用静态颜色值,禁用CSS变量
- ✅ **专业配色**: 使用深色企业级配色方案
- ✅ **清晰标注**: 版本号、数量统计、配置参数必须准确
- ✅ **文档配套**: 每次图表更新同步更新ARCHITECTURE_INDEX.md

---

## 🔄 更新触发条件

### 必须更新的场景

| 触发条件 | 影响图表 | 更新时限 |
|---------|---------|---------|
| **技术栈升级** (如Spring Boot 3.5.15→3.6.0) | 03, 29 | 3天内 |
| **新增模块/组件** (如新增apex-ai模块) | 01, 03 | 1周内 |
| **架构调整** (如改用MVC替换WebFlux) | 01, 03, 21 | 2周内 |
| **部署方式变更** (如从Podman迁移到K8s) | 02, 13, 14 | 1周内 |
| **监控体系变更** (如新增面板/指标) | 24, 25 | 2周内 |
| **业务流程变更** (如登录流程调整) | 04, 05, 06, 07 | 1周内 |
| **重大重构** (如DDD四层调整为六边形架构) | 全部29张 | 1个月内 |

### 可延后更新的场景

| 场景 | 说明 | 更新建议 |
|-----|------|---------|
| **依赖小版本升级** (如Redis 7.4.2→7.4.3) | 补丁版本升级 | 季度统一更新 |
| **配置参数微调** (如线程池大小调整) | 不影响架构理解 | 下次大版本更新时一并处理 |
| **新增工具类/辅助模块** | 不改变核心架构 | 可不更新 |

---

## 🛠️ 更新流程

### 流程图

```
代码变更 → 评估影响 → 更新图表 → 同步文档 → 提交审核 → 合并发布
   ↓          ↓          ↓          ↓          ↓          ↓
 架构师    架构师    图表设计    文档同步    团队Review   发布通知
```

### 详细步骤

#### 步骤1: 评估影响范围 (10分钟)

```bash
# 1. 确认变更类型
变更类型: [ ] 技术栈升级  [ ] 架构调整  [ ] 新增模块  [ ] 业务流程变更

# 2. 识别受影响的图表
受影响图表: 01_logical_architecture.svg, 03_technical_architecture.svg

# 3. 评估更新工作量
预计工作量: [ ] 简单(1小时)  [ ] 中等(半天)  [ ] 复杂(1-2天)
```

#### 步骤2: 收集最新数据 (30分钟)

```bash
# 技术栈版本号
grep -A 10 "<properties>" pom.xml
grep "\"version\":" package.json

# 代码统计
cd apex-interfaces/src/main/java/com/apex/scaffold/interfaces/handler
ls -1 *Handler.java | wc -l  # Handler数量

cd apex-application/src/main/java/com/apex/scaffold/application/service
ls -1 *Service.java | wc -l  # Service数量

cd apex-domain/src/main/java/com/apex/scaffold/domain/repository
ls -1 *Repository.java | wc -l  # Repository数量

# Flyway迁移版本
ls -1 apex-interfaces/src/main/resources/db/migration/V*.sql | tail -1

# 监控面板数量
ls -1 monitoring/grafana/dashboards/*.json | wc -l
```

#### 步骤3: 更新SVG图表 (1-4小时)

**方式A: 手动编辑 (推荐简单更新)**

```bash
# 1. 用文本编辑器打开SVG文件
code docs/diagrams/03_technical_architecture.svg

# 2. 搜索需要修改的文本节点
# 例如: <text>Spring Boot 3.5.15</text>

# 3. 修改为新版本
# 例如: <text>Spring Boot 3.6.0</text>

# 4. 保存并验证
open docs/diagrams/03_technical_architecture.svg
```

**方式B: 重新生成 (推荐复杂更新)**

参考本次(2026-09-10)的生成流程,使用AI辅助批量重新生成图表。

#### 步骤4: 同步更新文档 (30分钟)

```bash
# 1. 更新ARCHITECTURE_INDEX.md版本号
# 修改版本号: v2.0 → v2.1
# 新增更新记录到"文档更新记录"表格

# 2. 更新相关配套文档
# 如果图表名称或内容有重大变更,同步更新:
# - architecture/01-logical-architecture-detail.md
# - architecture/03-technical-architecture-detail.md
# - 其他引用该图表的文档

# 3. 更新diagrams/README.md
# 如果新增图表或修改图表说明,同步更新
```

#### 步骤5: 提交审核 (1天)

```bash
# 1. 创建分支
git checkout -b docs/update-svg-diagrams

# 2. 提交变更
git add docs/diagrams/*.svg
git add docs/ARCHITECTURE_INDEX.md
git commit -m "docs: 更新架构图表-Spring Boot升级至3.6.0"

# 3. 推送并创建PR
git push origin docs/update-svg-diagrams
gh pr create --title "docs: 更新架构图表" --body "更新内容:\n- 技术栈版本号更新\n- 影响图表: 03, 29"

# 4. 请求Review
# @架构团队 进行Code Review
```

#### 步骤6: 合并发布 (30分钟)

```bash
# 1. Review通过后合并到main分支
gh pr merge --squash

# 2. 在团队频道发布通知
团队通知模板:
📢 架构图表更新通知
- 更新日期: 2026-XX-XX
- 更新图表: 03_technical_architecture.svg, 29_technology_stack_overview.svg
- 更新内容: Spring Boot 3.5.15 → 3.6.0
- 查看地址: docs/ARCHITECTURE_INDEX.md
```

---

## 📐 SVG编辑规范

### 文件命名

```
格式: {序号}_{名称英文下划线分隔}.svg
示例: 01_logical_architecture.svg
禁止: 01-logical-architecture.svg (禁用短横线)
禁止: 01_逻辑架构.svg (禁用中文)
```

### 画布规格

```xml
<svg xmlns="http://www.w3.org/2000/svg" 
     viewBox="0 0 1400 800" 
     width="1400" 
     height="800">
```

- 宽度: 1400px (固定)
- 高度: 700\~900px (根据内容调整)
- 宽高比: 约2:1或16:9

### 颜色规范

```xml
<defs>
  <style>
    .title{font:700 32px sans-serif;fill:#0f172a;text-anchor:middle}
    .subtitle{font:500 14px sans-serif;fill:#64748b;text-anchor:middle}
    .box-title{font:600 14px sans-serif;fill:#0f172a}
    .text{font:400 11px sans-serif;fill:#475569}
    .code{font:400 10px monospace;fill:#2563eb}
  </style>
</defs>
```

**禁止使用CSS变量**:
```xml
❌ fill="var(--primary-color)"
✅ fill="#4f46e5"
```

### 版权信息

每张图表底部必须包含版权信息:

```xml
<text x="700" y="885" class="code" text-anchor="middle">
  © 2026 Apex Team · {图表名称} v3.0
</text>
```

---

## 🔍 质量检查清单

### 更新前检查

- [ ] 确认代码变更已合并到main分支
- [ ] 收集最新的版本号和统计数据
- [ ] 备份原SVG文件(可选)

### 更新中检查

- [ ] 所有版本号与代码配置一致
- [ ] 代码统计数据准确(Handler/Service/Repository数量)
- [ ] 配色符合企业级标准(深色配色)
- [ ] 无CSS变量,使用静态颜色值
- [ ] 文字清晰可读,无遮挡重叠
- [ ] 版权信息正确

### 更新后检查

- [ ] 在Chrome/Firefox/Safari三个浏览器中打开验证
- [ ] 在VSCode/Obsidian中预览验证
- [ ] ARCHITECTURE_INDEX.md引用路径正确
- [ ] 相关配套文档已同步更新
- [ ] 提交信息清晰,包含变更说明

---

## 🚨 常见问题与解决

### Q1: SVG在某些查看器中显示异常?

**原因**: 使用了CSS变量或复杂的CSS特性

**解决**:
```bash
# 检查是否使用了CSS变量
grep "var(--" docs/diagrams/*.svg

# 替换为静态颜色值
sed -i '' 's/var(--primary-color)/#4f46e5/g' xxx.svg
```

### Q2: 版本号不一致?

**原因**: 手动输入版本号,未从pom.xml读取

**解决**:
```bash
# 提取Spring Boot版本号
grep "<spring-boot.version>" pom.xml | sed 's/.*>\(.*\)<.*/\1/'

# 提取Vue版本号
grep '"vue":' package.json | sed 's/.*: "\(.*\)".*/\1/'
```

### Q3: 代码统计数量不准确?

**原因**: 手动估算,未使用Grep统计

**解决**:
```bash
# 使用Grep精确统计
find apex-interfaces/src/main/java -name "*Handler.java" | wc -l
find apex-application/src/main/java -name "*Service.java" -not -name "*ServiceImpl.java" | wc -l
```

### Q4: 图表与文档描述不一致?

**原因**: 只更新了图表,未同步更新配套文档

**解决**:
```bash
# 查找所有引用该图表的文档
grep -r "01_logical_architecture.svg" docs/ --include="*.md"

# 逐个检查并更新
```

---

## 📊 维护日志模板

每次更新后,在ARCHITECTURE_INDEX.md的"文档更新记录"表格中新增一行:

```markdown
| 2026-XX-XX | vX.X | 更新XX张图表:具体更新内容说明 | 架构团队 |
```

示例:
```markdown
| 2026-09-15 | v2.1 | 更新03/29号图表:Spring Boot 3.5.15→3.6.0,Vue 3.5.13→3.5.14 | 架构团队 |
```

---

## 🎯 季度维护计划

### 每季度第一周执行全面检查:

1. **技术栈版本审计** (2小时)
   - 对比pom.xml/package.json与图表中的所有版本号
   - 生成差异报告

2. **代码统计更新** (1小时)
   - 重新统计Handler/Service/Repository数量
   - 更新Flyway迁移版本号

3. **视觉优化** (按需)
   - 检查是否有更好的配色方案
   - 优化布局和排版

4. **文档同步** (1小时)
   - 检查所有引用链接是否有效
   - 更新ARCHITECTURE_INDEX.md

---

## 📞 联系与反馈

- **维护负责人**: 架构团队
- **问题反馈**: 提交Issue到项目仓库
- **改进建议**: 企业微信 Apex 开发群讨论
- **紧急联系**: @架构师

---

**文档维护者**: 架构团队  
**版本**: v1.0  
**发布日期**: 2026-09-10  
**下次审核**: 2026-12-10 (季度审核)

---

© 2026 Apex Team · 确保架构图表始终反映代码实际实现
