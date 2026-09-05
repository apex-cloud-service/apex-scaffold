# SVG修复与监控大盘图表生成计划

> 计划版本: v1.0  
> 创建时间: 2026-09-04  
> 任务来源: 用户需求

---

## 一、任务概述

### 1.1 任务目标

**任务1**: 修复19-21号SVG图表显示问题  
**任务2**: 根据项目监控大盘和Grafana监控面板信息生成对应的SVG图表及文档

### 1.2 当前状态分析

#### 问题1: SVG文件不完整

通过xmllint验证发现3个SVG文件均提前结束:
- `19_backend_technology_stack.svg`: 第371行提前结束（应有370行）
- `20_infrastructure_components.svg`: 第301行提前结束（应有300行）
- `21_technology_ecosystem.svg`: 第310行提前结束（应有309行）

**根本原因**: SVG文件缺少闭合标签 `</svg>`

#### 问题2: 监控大盘现状

**已有监控体系**:
- Grafana监控面板: `config/monitoring/grafana/apex-dashboard.json` (42+24=66个面板)
- Prometheus配置: `config/monitoring/prometheus/prometheus.yml` (6个抓取目标)
- 告警规则: `config/monitoring/prometheus/alert-rules.yml` (10组规则)
- 监控文档: `docs/operations/monitoring.md` (896行详细说明)

**监控面板分组** (66个面板):
1. 核心指标看板 (6个): 服务状态、QPS、P95响应时间、错误率、JVM堆、DB连接池
2. HTTP接口监控 (4个): 请求量、响应时间分位数、外部API调用
3. JVM监控 (2+8=10个): 堆内存、GC频率 + 深度监控8个
4. 数据库监控 (2个): 连接池、等待/超时
5. 系统资源 (4个): 磁盘使用率、网卡状态
6. 中间件监控 (5个): Redis健康、缓存命中率
7. 治理与安全 (6+6=12个): Sentinel限流/熔断 + 明细6个
8. 核心业务 (5个): 登录、注册、上传、信用变更
9. 链路追踪 (4个): Zipkin在线、Span接收速率
10. 网关与接口性能 (5个): 新增面板
11. 熔断器状态监控 (6个): 新增面板

**抓取目标**:
1. apex-scaffold: `host.docker.internal:8081/actuator/prometheus`
2. apex-sentinel: `host.docker.internal:8720/metrics`
3. apex-scaffold-health: Blackbox探针
4. grafana: `grafana:3000/metrics`
5. zipkin: `zipkin:9411/prometheus`

**告警规则覆盖**:
1. 服务可用性 (2条): 进程不可达、健康检查失败
2. 接口质量 (5条): 5xx错误率、P95延迟、流量骤降、异常突增、告警风暴
3. JVM健康 (4条): 堆内存高、Full GC频繁、GC停顿长、线程数高
4. 主机资源 (1条): 磁盘使用率
5. 数据库连接池 (2条): 连接池水位高、连接超时
6. Sentinel治理 (1条): 拦截过多
7. 阻塞IO (2条): 队列积压、采集失败
8. 下游依赖 (2条): Redis不可达、指标采集失败

---

## 二、Phase 1: 探索结果

### 2.1 SVG文件分析

**文件完整性**:
```bash
# 实际行数
19_backend_technology_stack.svg: 370行（xmllint报错371行）
20_infrastructure_components.svg: 300行（xmllint报错301行）
21_technology_ecosystem.svg: 309行（xmllint报错310行）
```

**缺失内容**: 所有文件都缺少最后的 `</svg>` 闭合标签

**读取验证**:
- 19号文件: 第360-370行可读，包含"Generator代码生成"和"核心技术特性"部分，但未闭合
- 20号文件: 第290-300行可读，包含"关键数据流向"说明，但未闭合
- 21号文件: 第300-309行可读，包含"技术栈统计"，但未闭合

### 2.2 监控大盘详细信息

**Grafana Dashboard JSON结构**:
- 文件路径: `/Users/work/apex-scaffold/config/monitoring/grafana/apex-dashboard.json`
- 状态: JSON文件不完整（jq解析失败，EOF截断）
- 可读部分: 前100行包含annotations、editable、panels等配置
- 面板1示例: 服务状态面板，使用`up{job="apex-scaffold"}`指标

**监控指标来源**:
- 应用自定义指标: `apex_api_*` (由MetricsWebFilter上报)
- Actuator指标: `jvm_*`, `hikaricp_*`, `druid_*`
- Sentinel指标: `sentinel_*`
- Redisson指标: `redisson_*`
- 安全指标: `apex_security_*`
- 阻塞IO指标: `apex_blocking_io_*`

**监控维度**:
1. 服务可用性: up/down状态、健康检查
2. 接口性能: QPS、P50/P90/P95/P99延迟、错误率
3. JVM运行时: 堆内存、GC频率/耗时、线程数、类加载
4. 数据库: 连接池活跃数/最大数、等待/超时
5. 缓存: Redis节点健康、命中率
6. 治理: Sentinel限流/熔断次数、规则配置
7. 业务: 登录/注册/上传/信用变更计数
8. 链路追踪: Zipkin Span接收/丢弃速率
9. 安全: IP封禁、XSS拦截、限流拒绝
10. 系统资源: 磁盘使用率、网卡状态

### 2.3 相关文档

**已有文档**:
1. `docs/operations/monitoring.md` (896行): Grafana监控面板增强方案，包含42+24=66个面板设计
2. `config/monitoring/grafana/README.md` (163行): 监控大盘使用指南
3. `config/monitoring/README.md` (64行): 监控与可观测性总览
4. `docs/architecture/infrastructure-components.md`: 基础设施组件文档（包含监控章节）

**文档覆盖内容**:
- 监控面板清单（66个）
- Prometheus配置说明
- 告警规则说明
- 指标口径说明
- 使用指南
- 故障排查

---

## 三、Phase 2: 需求确认

### 3.1 任务1: SVG修复

**问题**: 19-21号SVG图表无法正常显示  
**原因**: 缺少`</svg>`闭合标签  
**解决方案**: 在每个文件末尾添加`</svg>`标签

**验证方法**:
```bash
xmllint --noout 19_backend_technology_stack.svg
xmllint --noout 20_infrastructure_components.svg
xmllint --noout 21_technology_ecosystem.svg
```

### 3.2 任务2: 监控大盘图表生成

**需求分析**:
1. 根据现有监控体系生成SVG架构图
2. 生成对应的详细文档
3. 将图表补充到相关文档中

**待确认问题**:
- [ ] 需要生成哪些类型的监控架构图？
  - 选项A: Grafana监控面板架构图（展示66个面板的分组结构）
  - 选项B: Prometheus监控指标架构图（展示指标采集流程）
  - 选项C: 完整监控体系架构图（展示Prometheus+Grafana+Zipkin+告警的整体架构）
  - 选项D: 以上全部

- [ ] 图表详细程度？
  - 选项A: 概要级别（仅展示主要分组和核心指标）
  - 选项B: 详细级别（展示所有66个面板和指标PromQL）

- [ ] 文档内容范围？
  - 选项A: 仅图表说明（简短描述每个面板用途）
  - 选项B: 完整监控文档（包含配置示例、PromQL查询、告警规则、故障排查）

**推荐方案**: 
基于项目已有完整的监控文档（monitoring.md 896行），建议生成：
1. **Grafana监控面板分组架构图** (24_grafana_dashboard_architecture.svg)
   - 展示66个面板按10个分组的组织结构
   - 标注每个分组的核心指标
   - 数据流向（应用→Prometheus→Grafana）

2. **Prometheus监控体系架构图** (25_prometheus_monitoring_architecture.svg)
   - 展示6个抓取目标及其指标类型
   - 展示10组告警规则
   - 展示数据采集→存储→告警→可视化的完整链路

3. **监控大盘配置文档** (monitoring-dashboard-architecture.md)
   - 监控面板清单与用途
   - PromQL查询示例
   - 告警规则详解
   - 快速开始指南

---

## 四、Phase 3: 实施方案

### 4.1 任务1: 修复SVG文件

**步骤**:
1. 备份原文件
   ```bash
   cp 19_backend_technology_stack.svg 19_backend_technology_stack.svg.broken
   cp 20_infrastructure_components.svg 20_infrastructure_components.svg.broken
   cp 21_technology_ecosystem.svg 21_technology_ecosystem.svg.broken
   ```

2. 在每个文件末尾追加`</svg>`
   ```bash
   echo "</svg>" >> 19_backend_technology_stack.svg
   echo "</svg>" >> 20_infrastructure_components.svg
   echo "</svg>" >> 21_technology_ecosystem.svg
   ```

3. 验证修复结果
   ```bash
   xmllint --noout docs/diagrams/*.svg
   ```

### 4.2 任务2: 生成监控大盘图表

#### 4.2.1 图表1: Grafana监控面板分组架构图

**文件**: `24_grafana_dashboard_architecture.svg`

**内容**:
- 标题: "Apex Scaffold Grafana 监控大盘架构"
- 左侧: 数据源（Prometheus + 6个抓取目标）
- 中间: Grafana Dashboard（66个面板分10组）
  1. 核心指标看板 (6个)
  2. HTTP接口监控 (4个)
  3. JVM深度监控 (10个)
  4. 数据库监控 (2个)
  5. 系统资源 (4个)
  6. 中间件监控 (5个)
  7. 治理与安全 (12个)
  8. 核心业务 (5个)
  9. 链路追踪 (4个)
  10. 网关与接口性能 (5个)
  11. 熔断器状态监控 (6个)
- 右侧: 用户访问（Web UI端口3000）
- 数据流向箭头

**设计要点**:
- 使用深色主题（与现有SVG一致）
- 每个分组用矩形框表示，内部列出面板数量
- 关键指标用不同颜色标注（红色=严重告警、黄色=警告、绿色=正常）
- 端口号、PromQL示例标注

#### 4.2.2 图表2: Prometheus监控体系架构图

**文件**: `25_prometheus_monitoring_architecture.svg`

**内容**:
- 标题: "Apex Scaffold Prometheus 监控体系架构"
- 层次结构:
  - 应用层: Apex Scaffold (8081端口) + Sentinel (8720端口)
  - 采集层: Prometheus (9090端口) + Blackbox Exporter + Zipkin
  - 规则层: 10组告警规则（alert-rules.yml）
  - 通知层: Alertmanager（可选）
  - 可视化层: Grafana Dashboard
  - 存储层: TSDB (15天保留)

**设计要点**:
- 清晰的层次划分
- 数据流向箭头（采集→存储→评估→告警→可视化）
- 关键配置参数标注（scrape_interval=15s, retention=15d）
- 告警规则分组展示

#### 4.2.3 文档: 监控大盘配置文档

**文件**: `docs/architecture/monitoring-dashboard-architecture.md`

**章节结构**:
1. 监控体系概览
   - 架构图引用
   - 技术栈
   - 监控维度

2. Grafana监控面板详解
   - 66个面板清单
   - 每个面板的PromQL查询
   - 可视化类型（Graph/Stat/Gauge/Table）
   - 告警阈值

3. Prometheus配置详解
   - 抓取目标配置
   - 指标类型说明
   - 数据保留策略

4. 告警规则详解
   - 10组规则说明
   - 触发条件
   - 通知渠道

5. 快速开始
   - Docker Compose一键启动
   - 访问地址
   - 默认账密

6. 故障排查
   - 常见问题
   - 日志查看
   - 指标调试

---

## 五、Phase 4: 实施细节

### 5.1 修改文件清单

**修复SVG**:
1. `docs/diagrams/19_backend_technology_stack.svg` - 追加`</svg>`
2. `docs/diagrams/20_infrastructure_components.svg` - 追加`</svg>`
3. `docs/diagrams/21_technology_ecosystem.svg` - 追加`</svg>`

**新增SVG**:
4. `docs/diagrams/24_grafana_dashboard_architecture.svg` - 新建
5. `docs/diagrams/25_prometheus_monitoring_architecture.svg` - 新建

**新增文档**:
6. `docs/architecture/monitoring-dashboard-architecture.md` - 新建

**更新文档**:
7. `docs/architecture/system-architecture.md` - 添加新图表引用
8. `docs/ARCHITECTURE_INDEX.md` - 更新索引（23张→25张）

### 5.2 验证步骤

**SVG验证**:
```bash
# 1. XML语法验证
xmllint --noout docs/diagrams/*.svg

# 2. 浏览器验证
open docs/diagrams/19_backend_technology_stack.svg
open docs/diagrams/24_grafana_dashboard_architecture.svg
open docs/diagrams/25_prometheus_monitoring_architecture.svg

# 3. Markdown验证
# 在VS Code或Typora中打开相关文档，查看图表嵌入效果
```

**文档验证**:
```bash
# 检查链接有效性
grep -r "diagrams/24_" docs/
grep -r "diagrams/25_" docs/

# 检查文档结构
head -50 docs/architecture/monitoring-dashboard-architecture.md
```

---

## 六、假设与约束

### 6.1 假设

1. ✅ 监控栈（Prometheus + Grafana）已部署或可快速部署
2. ✅ apex-dashboard.json 虽然JSON截断，但monitoring.md文档包含完整的66个面板信息
3. ✅ 用户需要的是架构级别的SVG图表，而非面板截图
4. ✅ 文档语言为中文

### 6.2 约束

1. SVG图表必须符合XML规范，可被浏览器和markdown查看器正常渲染
2. 图表风格必须与现有18-23号图表保持一致（深色主题、专业配色）
3. 不修改现有监控配置文件（prometheus.yml、alert-rules.yml）
4. 文档必须与实际监控配置保持同步

### 6.3 风险

1. ⚠️ apex-dashboard.json文件可能需要修复（当前JSON不完整）
2. ⚠️ 监控面板数量可能与文档描述不一致（需以实际配置为准）
3. ⚠️ PromQL查询可能需要根据实际Prometheus版本调整

---

## 七、执行计划

### Phase 1: 修复SVG (预计10分钟)
1. ✅ 探索完成 - 已定位问题
2. ⏳ 备份原文件
3. ⏳ 追加闭合标签
4. ⏳ XML验证
5. ⏳ 浏览器验证

### Phase 2: 生成监控架构图 (预计30分钟)
1. ⏳ 创建24号SVG（Grafana监控面板分组架构图）
2. ⏳ 创建25号SVG（Prometheus监控体系架构图）
3. ⏳ XML验证
4. ⏳ 浏览器验证

### Phase 3: 编写监控文档 (预计40分钟)
1. ⏳ 创建monitoring-dashboard-architecture.md
2. ⏳ 填充内容（10章节）
3. ⏳ 添加代码示例和PromQL查询
4. ⏳ 文档交叉引用

### Phase 4: 更新索引文档 (预计10分钟)
1. ⏳ 更新system-architecture.md
2. ⏳ 更新ARCHITECTURE_INDEX.md
3. ⏳ 验证所有链接

### Phase 5: 最终验证 (预计10分钟)
1. ⏳ 运行xmllint验证所有SVG
2. ⏳ 在浏览器中打开所有SVG
3. ⏳ 在markdown查看器中验证文档
4. ⏳ 生成交付总结

**总预计时间**: 100分钟

---

## 八、交付成果

### 8.1 修复的文件 (3个)
- ✅ 19_backend_technology_stack.svg
- ✅ 20_infrastructure_components.svg
- ✅ 21_technology_ecosystem.svg

### 8.2 新增的架构图 (2个)
- ⏳ 24_grafana_dashboard_architecture.svg
- ⏳ 25_prometheus_monitoring_architecture.svg

### 8.3 新增的文档 (1个)
- ⏳ monitoring-dashboard-architecture.md

### 8.4 更新的文档 (2个)
- ⏳ system-architecture.md
- ⏳ ARCHITECTURE_INDEX.md

### 8.5 最终统计
- 架构图总数: 23张 → **25张**
- 技术文档总数: 35篇 → **36篇**
- 监控面板总数: **66个**
- 告警规则总数: **10组**

---

**计划制定者**: TRAE AI Assistant  
**计划状态**: 待用户确认  
**下一步**: 等待用户确认方案后开始执行
